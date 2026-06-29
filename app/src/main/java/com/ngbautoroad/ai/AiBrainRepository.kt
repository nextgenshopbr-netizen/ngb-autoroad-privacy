package com.ngbautoroad.ai

import com.ngbautoroad.data.db.FinanceDatabase
import com.ngbautoroad.domain.FatigueInsightEngine
import com.ngbautoroad.domain.InsightType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * AiBrainRepository e o "Cerebro Central" do aplicativo.
 * Ele cruza (em O(1) com cache) todos os modulos vitais (Ganhos, Despesas, Metas, Fadiga e Veiculo).
 */
class AiBrainRepository(
    private val financeDb: FinanceDatabase,
    private val fatigueEngine: FatigueInsightEngine
) {

    data class BrainState(
        val isFatigued: Boolean,
        val totalEarnedToday: Double,
        val goalProgressPercentage: Double,
        val currentVehicleCostPerKm: Double,
        val suggestion: String
    )

    private val _cognitiveStateFlow = MutableStateFlow<BrainState?>(null)
    val cognitiveStateFlow: Flow<BrainState?> = _cognitiveStateFlow.asStateFlow()

    private var cachedState: BrainState? = null
    private var lastCalculationMs: Long = 0
    private val CACHE_DURATION_MS = 30000L

    init { activeInstance = this }

    companion object {
        @Volatile private var activeInstance: AiBrainRepository? = null
        fun invalidateActiveCache() { activeInstance?.invalidateCache() }
    }

    fun invalidateCache() {
        cachedState = null
        lastCalculationMs = 0
        _cognitiveStateFlow.value = null
    }

    /**
     * Calcula ou retorna o estado cognitivo cruzado atual.
     */
    suspend fun getCognitiveStateNow(): BrainState = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = cachedState ?: _cognitiveStateFlow.value

        if (cached != null && (now - lastCalculationMs) < CACHE_DURATION_MS) {
            return@withContext cached
        }

        // 1. Fadiga
        val quickInsight = fatigueEngine.getQuickInsight()
        val isFatigued = quickInsight?.type == InsightType.REST_BENEFIT ||
                         quickInsight?.type == InsightType.DIMINISHING_RETURNS

        // 2. Ganhos de Hoje
        val todayStartMs = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val earnedToday = financeDb.earningDao().getTotalEarningsSync(todayStartMs, now) ?: 0.0

        // 3. Metas Ativas
        val goals = financeDb.financialGoalDao().getActiveGoalsSync()
        val targetAmount = goals.find { it.period.equals("Diaria", ignoreCase = true) || it.period.equals("DIA", ignoreCase = true) }?.targetAmount ?: 0.0
        val goalProgress = if (targetAmount > 0) (earnedToday / targetAmount) * 100 else 0.0

        // 4. Custo do Veiculo
        val vehicleProfile = financeDb.vehicleProfileDao().getActiveVehicleSync()
        val costPerKm = vehicleProfile?.costPerKm ?: 0.30

        // 5. Fallback Heuristico Rapido
        val fallbackSuggestion = when {
            isFatigued -> "Sua fadiga esta alta e a rentabilidade caiu. Sugestao: Pausa estrategica."
            goalProgress >= 100.0 -> "Meta financeira diaria batida! Bom momento para finalizar."
            goalProgress in 0.1..50.0 && Calendar.getInstance().get(Calendar.HOUR_OF_DAY) > 18 -> "Sua meta esta atrasada. Foque em bairros de alta liquidez para fechar rapido."
            else -> "Voce esta no ritmo perfeito. Mantenha os ganhos acima de R\$ ${String.format("%.2f", costPerKm + 1.50)}/km."
        }

        val newState = BrainState(
            isFatigued = isFatigued,
            totalEarnedToday = earnedToday,
            goalProgressPercentage = goalProgress,
            currentVehicleCostPerKm = costPerKm,
            suggestion = fallbackSuggestion
        )

        lastCalculationMs = now
        cachedState = newState
        _cognitiveStateFlow.value = newState
        newState
    }
}
