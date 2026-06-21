package com.ngbautoroad.domain

// ============================================================================
// ARQUIVO: OdometerEngine.kt
// LOCALIZAÇÃO: domain/OdometerEngine.kt
// RESPONSABILIDADE: Odômetro Inteligente v6.5.0
//   - Calcula odômetro estimado usando KM rastreado + fator de correção
//   - Auto-calibra o fator com EWMA quando motorista atualiza manualmente
//   - Fornece estimativa de KM real para manutenção, DRE e IA
// DEPENDÊNCIAS:
//   - data/db/FinanceExtensions.kt → VehicleProfileEntity, OdometerHistoryEntity
//   - data/db/FinanceDatabase.kt → EarningDao (getTotalDistanceSync)
// ============================================================================

import com.ngbautoroad.data.db.EarningDao
import com.ngbautoroad.data.db.OdometerHistoryDao
import com.ngbautoroad.data.db.OdometerHistoryEntity
import com.ngbautoroad.data.db.VehicleProfileDao
import com.ngbautoroad.data.db.VehicleProfileEntity

/**
 * OdometerEngine — Motor de cálculo do odômetro estimado.
 *
 * Fórmula principal:
 *   odometroEstimado = odometroBase + (kmRastreado × fatorCorrecao)
 *
 * Onde:
 *   - odometroBase = último valor informado pelo motorista (currentOdometer)
 *   - kmRastreado = soma de distance dos earnings desde lastOdometerUpdate
 *   - fatorCorrecao = multiplica o KM rastreado para compensar uso pessoal
 *     (padrão 1.3 = motorista roda 30% a mais que o rastreado)
 *
 * Auto-calibração (EWMA):
 *   Quando o motorista atualiza o odômetro, calculamos:
 *     fatorReal = (odomNovo - odomAnterior) / kmRastreadoNoPeriodo
 *   E atualizamos o fator com EWMA (alpha=0.3):
 *     novoFator = alpha × fatorReal + (1-alpha) × fatorAnterior
 */
class OdometerEngine(
    private val vehicleProfileDao: VehicleProfileDao,
    private val odometerHistoryDao: OdometerHistoryDao,
    private val earningDao: EarningDao
) {

    /**
     * Calcula o odômetro estimado atual do veículo ativo.
     * @return Pair(estimado, isEstimated) — isEstimated=true se é estimativa, false se é valor real recente
     */
    suspend fun getEstimatedOdometer(vehicle: VehicleProfileEntity): Pair<Int, Boolean> {
        if (vehicle.currentOdometer == 0) {
            // Nunca informou odômetro — não temos base para estimar
            return Pair(0, true)
        }

        val lastUpdate = vehicle.lastOdometerUpdate
        if (lastUpdate == 0L) {
            return Pair(vehicle.currentOdometer, false)
        }

        // KM rastreado desde a última atualização do odômetro
        val now = System.currentTimeMillis()
        val kmTracked = earningDao.getTotalDistanceSync(lastUpdate, now) ?: 0.0

        if (kmTracked <= 0) {
            return Pair(vehicle.currentOdometer, false)
        }

        // Aplicar fator de correção
        val estimatedAdditionalKm = kmTracked * vehicle.odometerCorrectionFactor
        val estimatedOdometer = vehicle.currentOdometer + estimatedAdditionalKm.toInt()

        // Se a atualização foi recente (< 3 dias), considerar como "real"
        val daysSinceUpdate = ((now - lastUpdate) / (1000 * 60 * 60 * 24)).toInt()
        val isEstimated = daysSinceUpdate >= 3

        return Pair(estimatedOdometer, isEstimated)
    }

    /**
     * Calcula o KM total estimado rodado no período (para DRE e projeções).
     * Inclui o fator de correção para compensar uso pessoal.
     */
    suspend fun getTotalKmEstimated(vehicle: VehicleProfileEntity, startDate: Long, endDate: Long): Double {
        val kmTracked = earningDao.getTotalDistanceSync(startDate, endDate) ?: 0.0
        return kmTracked * vehicle.odometerCorrectionFactor
    }

    /**
     * Calcula o KM/dia estimado (real, não apenas rastreado).
     */
    suspend fun getKmPerDayEstimated(vehicle: VehicleProfileEntity, periodDays: Int): Double {
        val now = System.currentTimeMillis()
        val startDate = now - (periodDays.toLong() * 24 * 60 * 60 * 1000)
        val totalKm = getTotalKmEstimated(vehicle, startDate, now)
        return if (periodDays > 0) totalKm / periodDays else 0.0
    }

    /**
     * Calcula quantos KM faltam para o próximo serviço de manutenção.
     * @param intervalKm Intervalo do serviço (ex: 10000 para óleo)
     * @return KM restantes (negativo = atrasado)
     */
    suspend fun getKmUntilService(vehicle: VehicleProfileEntity, intervalKm: Int): Int {
        val (estimatedOdometer, _) = getEstimatedOdometer(vehicle)
        if (estimatedOdometer == 0 || intervalKm == 0) return intervalKm

        // Próximo serviço = múltiplo de intervalKm mais próximo acima do odômetro
        val nextServiceOdometer = ((estimatedOdometer / intervalKm) + 1) * intervalKm
        return nextServiceOdometer - estimatedOdometer
    }

    /**
     * Processa uma atualização manual do odômetro e auto-calibra o fator.
     * Chamado quando o motorista informa um novo valor pelo dialog da Dashboard.
     */
    suspend fun processOdometerUpdate(
        vehicle: VehicleProfileEntity,
        newOdometerValue: Int
    ): Double {
        val now = System.currentTimeMillis()
        val lastUpdate = vehicle.lastOdometerUpdate

        // KM rastreado pelo app desde a última atualização
        val kmTracked = if (lastUpdate > 0) {
            earningDao.getTotalDistanceSync(lastUpdate, now) ?: 0.0
        } else 0.0

        // Calcular fator de calibração desta atualização
        val calibrationFactor = if (vehicle.currentOdometer > 0 && kmTracked > 100) {
            val realKmDriven = (newOdometerValue - vehicle.currentOdometer).toDouble()
            if (realKmDriven > 0) realKmDriven / kmTracked else vehicle.odometerCorrectionFactor
        } else {
            vehicle.odometerCorrectionFactor
        }

        // Salvar no histórico
        odometerHistoryDao.insert(
            OdometerHistoryEntity(
                vehicleId = vehicle.id,
                odometerValue = newOdometerValue,
                estimatedAtMoment = getEstimatedOdometer(vehicle).first,
                kmTrackedSinceLast = kmTracked,
                calibrationFactor = calibrationFactor,
                source = if (vehicle.currentOdometer == 0) "INITIAL" else "MANUAL",
                timestamp = now
            )
        )

        // Atualizar odômetro no veículo
        vehicleProfileDao.updateOdometer(vehicle.id, newOdometerValue, now)

        // Auto-calibração EWMA se temos dados suficientes
        val newFactor = if (kmTracked > 100) {
            val entries = odometerHistoryDao.getLastEntries(vehicle.id)
            if (entries.size >= 2) {
                calculateEWMA(entries)
            } else {
                calibrationFactor.coerceIn(1.0, 3.0)
            }
        } else {
            vehicle.odometerCorrectionFactor
        }

        // Atualizar fator no veículo
        vehicleProfileDao.updateCorrectionFactor(vehicle.id, newFactor)

        return newFactor
    }

    /**
     * Calcula EWMA (Exponentially Weighted Moving Average) do fator de correção.
     * Alpha = 0.3 → mais peso nos dados recentes.
     */
    private fun calculateEWMA(entries: List<OdometerHistoryEntity>): Double {
        val alpha = 0.3
        val validEntries = entries.filter { it.calibrationFactor > 0 && it.kmTrackedSinceLast > 50 }
        if (validEntries.isEmpty()) return 1.3

        // Entries vêm DESC (mais recente primeiro), precisamos processar do mais antigo
        var ewma = validEntries.last().calibrationFactor
        validEntries.reversed().drop(1).forEach { entry ->
            ewma = alpha * entry.calibrationFactor + (1 - alpha) * ewma
        }

        // Limitar entre 1.0 e 3.0 para evitar valores absurdos
        return ewma.coerceIn(1.0, 3.0)
    }

    /**
     * Verifica se algum serviço de manutenção está próximo ou atrasado.
     * @return Lista de alertas (nome do serviço, km restantes)
     */
    suspend fun getMaintenanceAlerts(vehicle: VehicleProfileEntity): List<MaintenanceAlert> {
        val alerts = mutableListOf<MaintenanceAlert>()
        val (estimatedOdometer, _) = getEstimatedOdometer(vehicle)
        if (estimatedOdometer == 0) return alerts

        // Verificar cada tipo de manutenção
        val services = listOf(
            Triple("Troca de Óleo", vehicle.oilChangeKm, vehicle.oilChangeCost),
            Triple("Pneus", vehicle.tireLifeKm, vehicle.tireCost),
            Triple("Pastilhas de Freio", vehicle.brakepadLifeKm, vehicle.brakepadCost),
            Triple("Revisão Geral", vehicle.maintenanceIntervalKm, vehicle.maintenanceCost)
        )

        for ((name, intervalKm, cost) in services) {
            if (intervalKm <= 0) continue
            val kmRemaining = getKmUntilService(vehicle, intervalKm)
            val urgency = when {
                kmRemaining <= 0 -> AlertUrgency.OVERDUE
                kmRemaining <= intervalKm * 0.1 -> AlertUrgency.URGENT // 10% restante
                kmRemaining <= intervalKm * 0.2 -> AlertUrgency.WARNING // 20% restante
                else -> AlertUrgency.OK
            }
            if (urgency != AlertUrgency.OK) {
                alerts.add(MaintenanceAlert(name, kmRemaining, cost, urgency))
            }
        }

        return alerts
    }
}

data class MaintenanceAlert(
    val serviceName: String,
    val kmRemaining: Int,
    val estimatedCost: Double,
    val urgency: AlertUrgency
)

enum class AlertUrgency {
    OK, WARNING, URGENT, OVERDUE
}
