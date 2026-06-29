package com.ngbautoroad.domain

import android.content.Context
import android.content.SharedPreferences
import com.ngbautoroad.data.db.VehicleProfileEntity
import com.ngbautoroad.data.prefs.PrefsManager
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first

// ============================================================================
// ARQUIVO: MaintenanceReserveAdvisor.kt
// VERSÃO: v6.9.0
// RESPONSABILIDADE: Sugestão automática de ajuste da taxa de reserva
//   - Analisa se a reserva atual cobrirá a próxima manutenção prevista
//   - Se não cobrir, sugere automaticamente um aumento da taxa R$/km
//   - Mostra ao motorista: "Com R$0.03/km você terá R$X em Y meses.
//     Para cobrir troca de pneus (R$2.400), aumente para R$0.05/km."
//   - Não é intrusivo: mostra como card informativo no dashboard
// DEPENDENTES:
//   - MaintenanceReserveEngine.kt → dados de reserva atual
//   - DashboardTab.kt → exibe sugestão como card
//   - OdometerEngine.kt → KM estimado para calcular tempo até manutenção
// ============================================================================

/**
 * Sugestão de ajuste da taxa de reserva de manutenção.
 */
data class ReserveAdjustmentSuggestion(
    val currentRatePerKm: Double,          // Taxa atual (ex: R$0.03/km)
    val suggestedRatePerKm: Double,        // Taxa sugerida (ex: R$0.05/km)
    val currentReserve: Double,            // Reserva acumulada atual
    val nextMaintenanceName: String,       // "Troca de pneus"
    val nextMaintenanceCost: Double,       // R$2.400
    val kmUntilMaintenance: Int,           // KM até a próxima manutenção
    val monthsUntilMaintenance: Double,    // Meses até a próxima manutenção
    val projectedReserveAtMaintenance: Double,  // Quanto terá com taxa atual
    val shortfall: Double,                 // Quanto faltará (déficit)
    val message: String,                   // Mensagem formatada para o motorista
    val isUrgent: Boolean                  // Se faltam menos de 2 meses
)

/**
 * Advisor que analisa e sugere ajustes na taxa de reserva de manutenção.
 * Funciona de forma proativa: detecta automaticamente quando a reserva
 * não será suficiente e sugere o ajuste necessário.
 */
class MaintenanceReserveAdvisor(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "maintenance_advisor_prefs"
        private const val DEFAULT_DAILY_KM = 150.0  // KM médio diário motorista de app

        // Tabela de manutenções por tipo de veículo (KM e custo médio)
        // Elétrico tem menos manutenções mas pneus são mais caros (peso da bateria)
        val MAINTENANCE_TABLE_ELECTRIC = listOf(
            MaintenanceItem("Revisão geral", 15_000, 800.0),
            MaintenanceItem("Pastilhas de freio", 40_000, 600.0),
            MaintenanceItem("Fluido de freio", 30_000, 200.0),
            MaintenanceItem("Troca de pneus", 40_000, 2_400.0),
            MaintenanceItem("Filtro de cabine", 15_000, 150.0),
            MaintenanceItem("Alinhamento e balanceamento", 10_000, 200.0)
        )

        val MAINTENANCE_TABLE_COMBUSTION = listOf(
            MaintenanceItem("Troca de óleo", 10_000, 350.0),
            MaintenanceItem("Filtros (ar+óleo+combustível)", 20_000, 400.0),
            MaintenanceItem("Pastilhas de freio", 30_000, 500.0),
            MaintenanceItem("Troca de pneus", 40_000, 1_800.0),
            MaintenanceItem("Correia dentada", 60_000, 1_200.0),
            MaintenanceItem("Velas de ignição", 30_000, 300.0),
            MaintenanceItem("Fluido de freio", 20_000, 150.0),
            MaintenanceItem("Alinhamento e balanceamento", 10_000, 180.0)
        )

        val MAINTENANCE_TABLE_HYBRID = listOf(
            MaintenanceItem("Troca de óleo", 15_000, 350.0),
            MaintenanceItem("Filtros", 20_000, 350.0),
            MaintenanceItem("Pastilhas de freio", 50_000, 550.0),
            MaintenanceItem("Troca de pneus", 40_000, 2_200.0),
            MaintenanceItem("Fluido de freio", 25_000, 180.0),
            MaintenanceItem("Alinhamento e balanceamento", 10_000, 190.0)
        )
    }

    data class MaintenanceItem(
        val name: String,
        val intervalKm: Int,
        val estimatedCost: Double
    )

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // v6.9.18: Adicionar suporte para atualizar a taxa de reserva de manutenção
    fun updateReserveRate(newRate: Double) {
        prefs.edit().putFloat("maintenance_reserve_per_km", newRate.toFloat()).apply()
    }

    fun getReserveRate(): Double {
        return prefs.getFloat("maintenance_reserve_per_km", 0.03f).toDouble()
    }

    /**
     * Analisa a situação atual e gera sugestão se necessário.
     * @param vehicle Perfil do veículo ativo
     * @param currentOdometerKm Odômetro estimado atual
     * @param currentReserve Valor acumulado na reserva
     * @param avgDailyKm KM médio diário real (do EWMA)
     * @return Sugestão de ajuste ou null se a reserva está adequada
     */
    fun analyze(
        vehicle: VehicleProfileEntity?,
        currentOdometerKm: Int,
        currentReserve: Double,
        avgDailyKm: Double = DEFAULT_DAILY_KM,
        records: List<com.ngbautoroad.data.db.MaintenanceRecordEntity> = emptyList()
    ): ReserveAdjustmentSuggestion? {
        if (vehicle == null) return null

        val baseTable = getMaintenanceTable(vehicle.fuelType)

        // v6.9.18: Filtrar os itens de manutenção baseando-se nas escolhas do motorista
        val prefsManager = PrefsManager(context)
        // runBlocking ok: this method is called from IO dispatcher contexts only
        val monitorTires = runBlocking { prefsManager.monitorTiresFlow.first() }
        val monitorBrakes = runBlocking { prefsManager.monitorBrakesFlow.first() }
        val monitorOil = runBlocking { prefsManager.monitorOilFlow.first() }
        val monitorGeneral = runBlocking { prefsManager.monitorGeneralFlow.first() }

        val filteredBaseTable = baseTable.filter { item ->
            when (item.name) {
                "Troca de pneus" -> monitorTires
                "Pastilhas de freio", "Fluido de freio" -> monitorBrakes
                "Troca de óleo" -> monitorOil
                "Revisão geral" -> monitorGeneral
                else -> true
            }
        }
        
        // v6.9.15: Calibrar estimativas usando os custos REAIS registrados pelo motorista (Sprint 3)
        val maintenanceTable = if (records.isNotEmpty()) {
            filteredBaseTable.map { item ->
                val matched = records.filter { 
                    it.replacedParts.contains(item.name, ignoreCase = true) || 
                    it.notes.contains(item.name, ignoreCase = true) 
                }
                if (matched.isNotEmpty()) {
                    val realAvg = matched.map { it.totalCost }.average()
                    item.copy(estimatedCost = realAvg)
                } else item
            }
        } else filteredBaseTable

        // v6.9.18: Ler taxa de reserva salva do SharedPreferences
        val currentRate = getReserveRate()

        // Encontrar a próxima manutenção mais cara que a reserva não cobrirá
        val nextMaintenance = findNextUnfundedMaintenance(
            maintenanceTable, currentOdometerKm, currentReserve, currentRate, avgDailyKm
        ) ?: return null // Reserva adequada para tudo!

        // Calcular taxa sugerida
        val kmUntilMaintenance = nextMaintenance.kmRemaining
        val monthsUntil = kmUntilMaintenance / (avgDailyKm * 30.0)
        val projectedReserve = currentReserve + (kmUntilMaintenance * currentRate)
        val shortfall = nextMaintenance.item.estimatedCost - projectedReserve

        if (shortfall <= 0) return null // Vai cobrir com a taxa atual

        // Calcular taxa necessária para cobrir
        val neededExtra = shortfall / kmUntilMaintenance.coerceAtLeast(1)
        val suggestedRate = (currentRate + neededExtra).let {
            // Arredondar para centavos (0.01)
            (Math.ceil(it * 100) / 100.0).coerceAtMost(0.20) // Max R$0.20/km
        }

        val message = buildString {
            append("Com R$ ${String.format("%.2f", currentRate)}/km, ")
            append("você terá R$ ${String.format("%.0f", projectedReserve)} ")
            append("quando precisar de ${nextMaintenance.item.name} ")
            append("(R$ ${String.format("%.0f", nextMaintenance.item.estimatedCost)}). ")
            append("Faltarão R$ ${String.format("%.0f", shortfall)}. ")
            append("Sugestão: aumente para R$ ${String.format("%.2f", suggestedRate)}/km.")
        }

        return ReserveAdjustmentSuggestion(
            currentRatePerKm = currentRate,
            suggestedRatePerKm = suggestedRate,
            currentReserve = currentReserve,
            nextMaintenanceName = nextMaintenance.item.name,
            nextMaintenanceCost = nextMaintenance.item.estimatedCost,
            kmUntilMaintenance = kmUntilMaintenance,
            monthsUntilMaintenance = monthsUntil,
            projectedReserveAtMaintenance = projectedReserve,
            shortfall = shortfall,
            message = message,
            isUrgent = monthsUntil < 2.0
        )
    }

    /**
     * Encontra a próxima manutenção que a reserva não cobrirá.
     */
    private fun findNextUnfundedMaintenance(
        table: List<MaintenanceItem>,
        currentKm: Int,
        currentReserve: Double,
        ratePerKm: Double,
        avgDailyKm: Double
    ): NextMaintenance? {
        // Para cada item, calcular quando será a próxima ocorrência
        val upcoming = table.map { item ->
            val lastDone = (currentKm / item.intervalKm) * item.intervalKm
            val nextDue = lastDone + item.intervalKm
            val kmRemaining = (nextDue - currentKm).coerceAtLeast(0)
            NextMaintenance(item, kmRemaining)
        }.filter { it.kmRemaining > 0 }
         .sortedBy { it.kmRemaining }

        // Encontrar a primeira que não será coberta
        for (next in upcoming) {
            val projectedReserve = currentReserve + (next.kmRemaining * ratePerKm)
            if (projectedReserve < next.item.estimatedCost) {
                return next
            }
        }
        return null // Todas cobertas!
    }

    private data class NextMaintenance(
        val item: MaintenanceItem,
        val kmRemaining: Int
    )

    /**
     * Retorna a tabela de manutenção baseada no tipo de combustível.
     */
    private fun getMaintenanceTable(fuelType: String): List<MaintenanceItem> {
        return when (fuelType.lowercase()) {
            "electric", "eletrico", "elétrico" -> MAINTENANCE_TABLE_ELECTRIC
            "hybrid", "hibrido", "híbrido" -> MAINTENANCE_TABLE_HYBRID
            else -> MAINTENANCE_TABLE_COMBUSTION
        }
    }

    /**
     * Salva que o motorista aceitou a sugestão (para não repetir).
     */
    fun markSuggestionAccepted(maintenanceName: String) {
        prefs.edit()
            .putLong("accepted_${maintenanceName}", System.currentTimeMillis())
            .apply()
    }

    /**
     * Verifica se já sugeriu recentemente (não repetir em menos de 7 dias).
     */
    fun shouldShowSuggestion(maintenanceName: String): Boolean {
        val lastAccepted = prefs.getLong("accepted_${maintenanceName}", 0L)
        val sevenDaysMs = 7 * 24 * 60 * 60 * 1000L
        return System.currentTimeMillis() - lastAccepted > sevenDaysMs
    }
}
