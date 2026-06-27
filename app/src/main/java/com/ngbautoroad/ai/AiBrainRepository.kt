package com.ngbautoroad.ai

import com.ngbautoroad.data.db.FinanceDatabase
import com.ngbautoroad.domain.FatigueInsightEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * AiBrainRepository é o "Cérebro Central" do aplicativo.
 * Ele cruza (em O(1) com cache) todos os módulos vitais (Ganhos, Despesas, Metas, Fadiga e Veículo).
 * 
 * Foi projetado de acordo com a premissa de máxima economia de hardware e compatibilidade,
 * utilizando apenas consultas assíncronas padrão (coroutine suspensas terminadas em Sync) do Room.
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

    private var lastCalculationMs: Long = 0
    private val CACHE_DURATION_MS = 30000L // 30 segundos de cache para poupar bateria e SQLite

    /**
     * Calcula ou retorna o estado cognitivo cruzado atual.
     * Esta função é chamada pelo AutoPilot ou UI, sem gerar gargalos.
     */
    suspend fun getCognitiveStateNow(): BrainState = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = _cognitiveStateFlow.value

        if (cached != null && (now - lastCalculationMs) < CACHE_DURATION_MS) {
            return@withContext cached
        }

        // 1. Fadiga
        val quickInsight = fatigueEngine.getQuickInsight()
        val isFatigued = quickInsight?.type?.name == "REST_BENEFIT" || quickInsight?.type?.name == "DIMINISHING_RETURNS"

        // 2. Ganhos de Hoje
        val todayStartMs = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // Usar getTotalEarningsSync diretamente (presente no EarningDao)
        val earnedToday = financeDb.earningDao().getTotalEarningsSync(todayStartMs, now) ?: 0.0

        // 3. Metas Ativas
        val goals = financeDb.financialGoalDao().getActiveGoalsSync()
        val dailyGoal = goals.find { it.period.equals("Diária", ignoreCase = true) || it.period.equals("DIA", ignoreCase = true) }?.targetAmount ?: 0.0
        val goalProgress = if (dailyGoal > 0) (earnedToday / dailyGoal) * 100 else 0.0

        // 4. Custo do Veículo
        val vehicleProfile = financeDb.vehicleProfileDao().getActiveVehicleSync()
        val costPerKm = vehicleProfile?.costPerKm ?: 0.30

        // 5. Fallback Heurístico Rápido (Caso não usemos Gemini Nano via AICore)
        val fallbackSuggestion = when {
            isFatigued -> "Sua fadiga está alta e a rentabilidade caiu. Sugestão: Pausa estratégica."
            goalProgress >= 100.0 -> "Meta financeira diária batida! Bom momento para finalizar."
            goalProgress in 0.1..50.0 && Calendar.getInstance().get(Calendar.HOUR_OF_DAY) > 18 -> "Sua meta está atrasada. Foque em bairros de alta liquidez para fechar rápido."
            else -> "Você está no ritmo perfeito. Mantenha os ganhos acima de R\$ ${String.format("%.2f", costPerKm + 1.50)}/km."
        }

        val newState = BrainState(
            isFatigued = isFatigued,
            totalEarnedToday = earnedToday,
            goalProgressPercentage = goalProgress,
            currentVehicleCostPerKm = costPerKm,
            suggestion = fallbackSuggestion
        )

        lastCalculationMs = now
        _cognitiveStateFlow.value = newState
        newState
    }
}
