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
    private val earningDao: EarningDao,
    private val shiftHistoryDao: com.ngbautoroad.data.db.ShiftHistoryDao? = null
) {

    // v6.6.0: Referência ao GpsTrackingEngine para dados GPS reais
    private var gpsTrackingEngine: GpsTrackingEngine? = null

    /**
     * v6.6.0: Conecta o GpsTrackingEngine para usar dados GPS reais.
     */
    fun setGpsTrackingEngine(gps: GpsTrackingEngine) {
        this.gpsTrackingEngine = gps
    }

    /**
     * Calcula o odômetro estimado atual do veículo ativo.
     * v6.6.0: Agora usa GPS quando disponível para maior precisão.
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

        // v6.6.0: Se GPS está ativo, usar KM do GPS (mais preciso que earnings)
        // Corrigido: Soma o GPS acumulado em turnos passados com o GPS do turno atual
        val pastShiftsGpsKm = shiftHistoryDao?.getTotalKmGps(lastUpdate) ?: 0.0
        val currentShiftGpsKm = gpsTrackingEngine?.getTotalDistanceKm() ?: 0.0
        val totalGpsKm = pastShiftsGpsKm + currentShiftGpsKm

        val effectiveKm = if (totalGpsKm > 0 && totalGpsKm > kmTracked) {
            // GPS captura tudo: corridas + KM morto + reposicionamento
            // Usar GPS diretamente (fator de correção menor, pois GPS já inclui tudo)
            // Apenas adicionar estimativa de uso pessoal fora do turno
            val gpsBasedFactor = (vehicle.odometerCorrectionFactor - 1.0) * 0.5 + 1.0 // Reduz fator pela metade
            totalGpsKm * gpsBasedFactor
        } else if (kmTracked > 0) {
            // Fallback: usar earnings + fator de correção completo
            kmTracked * vehicle.odometerCorrectionFactor
        } else {
            0.0
        }

        if (effectiveKm <= 0) {
            return Pair(vehicle.currentOdometer, false)
        }

        val estimatedOdometer = vehicle.currentOdometer + effectiveKm.toInt()

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
                calibrationFactor.coerceIn(1.0, 5.0)
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
     *
     * v6.7.0: Ruptura #8 - Detecção de outlier (férias, viagem longa)
     *   Se um fator é > 2× a média dos anteriores, é marcado como outlier
     *   e recebe peso reduzido (alpha/3) no EWMA.
     *
     * v6.7.0: Ruptura #10 - Max factor aumentado para 5.0
     *   Famílias com uso pessoal alto podem ter fator real > 3.0
     */
    private fun calculateEWMA(entries: List<OdometerHistoryEntity>): Double {
        // v6.9.0: Cold Start - nos primeiros 30 dias, usar alpha mais alto
        // para aprender rápido o padrão do motorista
        val daysSinceFirst = if (entries.isNotEmpty()) {
            val firstEntry = entries.minByOrNull { it.timestamp }?.timestamp ?: 0L
            ((System.currentTimeMillis() - firstEntry) / 86_400_000L).toInt()
        } else 0
        val isColdStart = daysSinceFirst < 30
        val alpha = if (isColdStart) 0.5 else 0.3  // Aprendizado acelerado no 1º mês

        // v6.9.0: Cold Start - aceitar entries com menos KM no primeiro mês
        val minKmThreshold = if (isColdStart) 20.0 else 50.0
        val validEntries = entries.filter { it.calibrationFactor > 0 && it.kmTrackedSinceLast > minKmThreshold }
        if (validEntries.isEmpty()) return 1.3

        // Calcular média para detecção de outlier
        val avgFactor = validEntries.map { it.calibrationFactor }.average()

        // Entries vêm DESC (mais recente primeiro), precisamos processar do mais antigo
        var ewma = validEntries.last().calibrationFactor
        validEntries.reversed().drop(1).forEach { entry ->
            // Ruptura #8: Outlier detection
            val isOutlier = entry.calibrationFactor > avgFactor * 2.0 ||
                           entry.calibrationFactor < avgFactor * 0.3
            val effectiveAlpha = if (isOutlier) alpha / 3.0 else alpha
            ewma = effectiveAlpha * entry.calibrationFactor + (1 - effectiveAlpha) * ewma
        }

        // Ruptura #10: Limitar entre 1.0 e 5.0 (antes era 3.0)
        // Famílias com uso pessoal intenso podem ter fator real de 3-4x
        return ewma.coerceIn(1.0, 5.0)
    }

    /**
     * v6.7.0 Ruptura #11: Fator sazonal para predição de uso familiar.
     * Férias (dez-jan) e feriados prolongados têm uso familiar 40-80% maior.
     * Meses normais têm uso padrão.
     */
    fun getSeasonalMultiplier(): Double {
        val month = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH)
        return when (month) {
            // Dezembro e Janeiro: férias escolares, viagens
            java.util.Calendar.DECEMBER, java.util.Calendar.JANUARY -> 1.6
            // Julho: férias de meio de ano
            java.util.Calendar.JULY -> 1.4
            // Carnaval (fevereiro), Semana Santa (março/abril)
            java.util.Calendar.FEBRUARY -> 1.3
            java.util.Calendar.MARCH -> 1.15
            // Meses normais
            else -> 1.0
        }
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

    // ========================================================================
    // v6.6.0: PREVISÃO DE USO FAMILIAR
    // A IA aprende o padrão de uso da família e prevê KM mesmo quando
    // o motorista esquece de atualizar o odômetro.
    // ========================================================================

    /**
     * Calcula a previsão de uso familiar baseada no histórico de atualizações.
     * Aprende: KM família/dia = (KM real - KM rastreado) / dias entre atualizações
     *
     * Com o tempo, a IA identifica:
     * - Média diária de uso familiar
     * - Padrão semanal (dias úteis vs fim de semana)
     * - Tendência (aumentando ou diminuindo)
     */
    suspend fun predictFamilyUsage(vehicle: VehicleProfileEntity): FamilyUsagePrediction {
        val entries = odometerHistoryDao.getLastEntries(vehicle.id)
        if (entries.size < 2) {
            // Sem dados suficientes — usar estimativa padrão
            return FamilyUsagePrediction(
                avgDailyFamilyKm = 15.0, // Padrão: 15 km/dia de uso familiar
                confidence = 0.2,
                dataPoints = 0,
                trend = UsageTrend.STABLE,
                weekdayAvg = 12.0,
                weekendAvg = 25.0
            )
        }

        // Calcular KM familiar para cada período entre atualizações
        val familyKmPerDay = mutableListOf<Double>()

        for (i in 0 until entries.size - 1) {
            val newer = entries[i]   // Mais recente
            val older = entries[i + 1] // Mais antigo

            val daysBetween = ((newer.timestamp - older.timestamp) / (1000.0 * 60 * 60 * 24)).coerceAtLeast(1.0)
            val realKm = (newer.odometerValue - older.odometerValue).toDouble()
            val trackedKm = newer.kmTrackedSinceLast

            if (realKm > 0 && trackedKm >= 0) {
                val familyKm = (realKm - trackedKm).coerceAtLeast(0.0)
                val dailyFamily = familyKm / daysBetween
                familyKmPerDay.add(dailyFamily)
            }
        }

        if (familyKmPerDay.isEmpty()) {
            return FamilyUsagePrediction(
                avgDailyFamilyKm = 15.0,
                confidence = 0.3,
                dataPoints = entries.size,
                trend = UsageTrend.STABLE,
                weekdayAvg = 12.0,
                weekendAvg = 25.0
            )
        }

        // Média ponderada (mais peso nos dados recentes)
        var weightedSum = 0.0
        var weightTotal = 0.0
        familyKmPerDay.forEachIndexed { index, km ->
            val weight = index + 1.0 // Mais recente = maior peso
            weightedSum += km * weight
            weightTotal += weight
        }
        val avgDaily = weightedSum / weightTotal

        // Tendência: comparar primeira metade vs segunda metade
        val trend = if (familyKmPerDay.size >= 4) {
            val firstHalf = familyKmPerDay.takeLast(familyKmPerDay.size / 2).average()
            val secondHalf = familyKmPerDay.take(familyKmPerDay.size / 2).average()
            when {
                secondHalf > firstHalf * 1.15 -> UsageTrend.INCREASING
                secondHalf < firstHalf * 0.85 -> UsageTrend.DECREASING
                else -> UsageTrend.STABLE
            }
        } else UsageTrend.STABLE

        // Confiança baseada na quantidade de dados
        val confidence = (familyKmPerDay.size.toDouble() / 10.0).coerceAtMost(1.0)

        // Estimativa dia útil vs fim de semana (heurística: fim de semana = 2x)
        val weekdayAvg = avgDaily * 0.8
        val weekendAvg = avgDaily * 1.6

        return FamilyUsagePrediction(
            avgDailyFamilyKm = avgDaily,
            confidence = confidence,
            dataPoints = familyKmPerDay.size,
            trend = trend,
            weekdayAvg = weekdayAvg,
            weekendAvg = weekendAvg
        )
    }

    /**
     * Calcula o odômetro previsto quando o motorista esquece de atualizar.
     * Usa: KM GPS + previsão de uso familiar × dias sem atualização.
     */
    suspend fun getPredictedOdometerWithFamily(vehicle: VehicleProfileEntity): PredictedOdometer {
        val (baseEstimate, isEstimated) = getEstimatedOdometer(vehicle)
        if (!isEstimated || vehicle.currentOdometer == 0) {
            return PredictedOdometer(
                value = baseEstimate,
                familyKmAdded = 0.0,
                confidence = 1.0,
                daysSinceUpdate = 0,
                source = "real"
            )
        }

        val now = System.currentTimeMillis()
        val daysSinceUpdate = ((now - vehicle.lastOdometerUpdate) / (1000.0 * 60 * 60 * 24)).toInt()

        val familyPrediction = predictFamilyUsage(vehicle)

        // Adicionar KM familiar previsto ao odômetro estimado
        // v6.7.0 Ruptura #11: Aplicar fator sazonal (férias = mais uso familiar)
        val seasonalFactor = getSeasonalMultiplier()
        val familyKmToAdd = familyPrediction.avgDailyFamilyKm * daysSinceUpdate * seasonalFactor
        val predictedOdometer = baseEstimate + familyKmToAdd.toInt()

        // Confiança diminui com o tempo sem atualização
        val timeDecay = 1.0 / (1.0 + daysSinceUpdate * 0.05) // Decai ~5% por dia
        val totalConfidence = familyPrediction.confidence * timeDecay

        return PredictedOdometer(
            value = predictedOdometer,
            familyKmAdded = familyKmToAdd,
            confidence = totalConfidence,
            daysSinceUpdate = daysSinceUpdate,
            source = if (totalConfidence > 0.6) "ia_previsto" else "estimativa_baixa_confiança"
        )
    }
}

// ============================================================================
// DATA CLASSES
// ============================================================================

data class FamilyUsagePrediction(
    val avgDailyFamilyKm: Double,     // Média diária de uso familiar (km)
    val confidence: Double,            // 0.0 a 1.0 — quão confiável é a previsão
    val dataPoints: Int,               // Quantas atualizações de odômetro foram usadas
    val trend: UsageTrend,             // Tendência de uso
    val weekdayAvg: Double,            // Média dia útil
    val weekendAvg: Double             // Média fim de semana
)

data class PredictedOdometer(
    val value: Int,                    // Odômetro previsto
    val familyKmAdded: Double,         // KM familiar adicionado à previsão
    val confidence: Double,            // Confiança na previsão (0-1)
    val daysSinceUpdate: Int,          // Dias desde última atualização
    val source: String                 // "real", "ia_previsto", "estimativa_baixa_confiança"
)

enum class UsageTrend {
    INCREASING,  // Família está usando mais o carro
    STABLE,      // Uso estável
    DECREASING   // Família está usando menos
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
