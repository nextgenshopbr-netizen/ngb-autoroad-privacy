package com.ngbautoroad.domain

import com.ngbautoroad.data.db.VehicleProfileEntity

// ============================================================================
// ARQUIVO: MaintenanceReserveEngine.kt
// VERSÃO: v6.6.0
// RESPONSABILIDADE: Ruptura #7 — Reserva de manutenção sugerida (R$/km)
//   Problema: App calcula custos mas não sugere quanto separar por km.
//   Solução: Calcula R$/km de reserva baseado nos custos de manutenção
//   configurados e sugere valor diário/semanal/mensal.
// DEPENDENTES:
//   - DashboardTab.kt → exibe card com sugestão de reserva
//   - FinanceDRE.kt → usa para calcular custo real
// ============================================================================

/**
 * Resultado do cálculo de reserva de manutenção.
 */
data class MaintenanceReserveResult(
    val reservePerKm: Double = 0.0,        // R$/km que deve ser reservado
    val dailyReserve: Double = 0.0,        // R$/dia (baseado em KM médio diário)
    val weeklyReserve: Double = 0.0,       // R$/semana
    val monthlyReserve: Double = 0.0,      // R$/mês
    val breakdown: List<ReserveBreakdown> = emptyList(),
    val nextMaintenanceCost: Double = 0.0, // Próximo custo estimado
    val nextMaintenanceKm: Int = 0         // KM até próxima manutenção
)

data class ReserveBreakdown(
    val category: String,
    val costPerKm: Double,
    val intervalKm: Int,
    val totalCost: Double,
    val percentOfTotal: Double
)

/**
 * Motor de cálculo de reserva de manutenção.
 * Calcula quanto o motorista deve separar por km rodado para cobrir
 * todas as manutenções futuras sem surpresas financeiras.
 */
class MaintenanceReserveEngine {

    companion object {
        const val DEFAULT_DAILY_KM = 150.0 // KM médio diário de motorista de app
    }

    /**
     * Calcula a reserva de manutenção baseada no perfil do veículo.
     * @param vehicle Perfil do veículo com custos configurados
     * @param avgDailyKm KM médio diário (padrão: 150km)
     * @return MaintenanceReserveResult com todos os valores calculados
     */
    fun calculateReserve(vehicle: VehicleProfileEntity?, avgDailyKm: Double = DEFAULT_DAILY_KM): MaintenanceReserveResult {
        if (vehicle == null) return MaintenanceReserveResult()

        val breakdown = mutableListOf<ReserveBreakdown>()
        var totalReservePerKm = 0.0

        // Pneus
        if (vehicle.tireLifeKm > 0 && vehicle.tireCost > 0) {
            val costPerKm = vehicle.tireCost / vehicle.tireLifeKm
            totalReservePerKm += costPerKm
            breakdown.add(ReserveBreakdown(
                category = "Pneus",
                costPerKm = costPerKm,
                intervalKm = vehicle.tireLifeKm,
                totalCost = vehicle.tireCost,
                percentOfTotal = 0.0 // Calculado depois
            ))
        }

        // Pastilhas de freio
        if (vehicle.brakepadLifeKm > 0 && vehicle.brakepadCost > 0) {
            val costPerKm = vehicle.brakepadCost / vehicle.brakepadLifeKm
            totalReservePerKm += costPerKm
            breakdown.add(ReserveBreakdown(
                category = "Pastilhas de freio",
                costPerKm = costPerKm,
                intervalKm = vehicle.brakepadLifeKm,
                totalCost = vehicle.brakepadCost,
                percentOfTotal = 0.0
            ))
        }

        // Troca de óleo (ou fluidos para elétrico)
        if (vehicle.oilChangeKm > 0 && vehicle.oilChangeCost > 0) {
            val costPerKm = vehicle.oilChangeCost / vehicle.oilChangeKm
            totalReservePerKm += costPerKm
            val label = if (vehicle.vehicleType == "ELECTRIC") "Fluidos/Filtros" else "Troca de óleo"
            breakdown.add(ReserveBreakdown(
                category = label,
                costPerKm = costPerKm,
                intervalKm = vehicle.oilChangeKm,
                totalCost = vehicle.oilChangeCost,
                percentOfTotal = 0.0
            ))
        }

        // Revisão geral
        if (vehicle.maintenanceIntervalKm > 0 && vehicle.maintenanceCost > 0) {
            val costPerKm = vehicle.maintenanceCost / vehicle.maintenanceIntervalKm
            totalReservePerKm += costPerKm
            breakdown.add(ReserveBreakdown(
                category = "Revisão geral",
                costPerKm = costPerKm,
                intervalKm = vehicle.maintenanceIntervalKm,
                totalCost = vehicle.maintenanceCost,
                percentOfTotal = 0.0
            ))
        }

        // Depreciação (se valor de compra informado)
        if (vehicle.purchaseValue > 0) {
            val depPerKm = vehicle.purchaseValue / 200_000.0 // Vida útil estimada: 200k km
            totalReservePerKm += depPerKm
            breakdown.add(ReserveBreakdown(
                category = "Depreciação",
                costPerKm = depPerKm,
                intervalKm = 200_000,
                totalCost = vehicle.purchaseValue,
                percentOfTotal = 0.0
            ))
        }

        // Calcular percentuais
        val finalBreakdown = if (totalReservePerKm > 0) {
            breakdown.map { it.copy(percentOfTotal = (it.costPerKm / totalReservePerKm) * 100.0) }
        } else breakdown

        // Calcular reservas por período
        val dailyReserve = totalReservePerKm * avgDailyKm
        val weeklyReserve = dailyReserve * 7
        val monthlyReserve = dailyReserve * 30

        // Próxima manutenção (a mais próxima)
        val nextMaintenance = findNextMaintenance(vehicle)

        return MaintenanceReserveResult(
            reservePerKm = totalReservePerKm,
            dailyReserve = dailyReserve,
            weeklyReserve = weeklyReserve,
            monthlyReserve = monthlyReserve,
            breakdown = finalBreakdown,
            nextMaintenanceCost = nextMaintenance.first,
            nextMaintenanceKm = nextMaintenance.second
        )
    }

    /**
     * Encontra a próxima manutenção mais próxima baseada no odômetro.
     */
    private fun findNextMaintenance(vehicle: VehicleProfileEntity): Pair<Double, Int> {
        val currentOdometer = vehicle.currentOdometer
        if (currentOdometer <= 0) return Pair(0.0, 0)

        val maintenances = mutableListOf<Pair<Double, Int>>() // (custo, km restante)

        if (vehicle.oilChangeKm > 0 && vehicle.oilChangeCost > 0) {
            val nextAt = ((currentOdometer / vehicle.oilChangeKm) + 1) * vehicle.oilChangeKm
            maintenances.add(Pair(vehicle.oilChangeCost, nextAt - currentOdometer))
        }
        if (vehicle.tireLifeKm > 0 && vehicle.tireCost > 0) {
            val nextAt = ((currentOdometer / vehicle.tireLifeKm) + 1) * vehicle.tireLifeKm
            maintenances.add(Pair(vehicle.tireCost, nextAt - currentOdometer))
        }
        if (vehicle.brakepadLifeKm > 0 && vehicle.brakepadCost > 0) {
            val nextAt = ((currentOdometer / vehicle.brakepadLifeKm) + 1) * vehicle.brakepadLifeKm
            maintenances.add(Pair(vehicle.brakepadCost, nextAt - currentOdometer))
        }
        if (vehicle.maintenanceIntervalKm > 0 && vehicle.maintenanceCost > 0) {
            val nextAt = ((currentOdometer / vehicle.maintenanceIntervalKm) + 1) * vehicle.maintenanceIntervalKm
            maintenances.add(Pair(vehicle.maintenanceCost, nextAt - currentOdometer))
        }

        return maintenances.minByOrNull { it.second } ?: Pair(0.0, 0)
    }
}
