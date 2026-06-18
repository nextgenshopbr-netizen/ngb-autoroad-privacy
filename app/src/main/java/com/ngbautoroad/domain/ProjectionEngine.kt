package com.ngbautoroad.domain

// ============================================================================
// ARQUIVO: ProjectionEngine.kt
// LOCALIZAÇÃO: domain/ProjectionEngine.kt
// RESPONSABILIDADE: Algoritmo inteligente de projeção financeira
//   - Projeta ganhos/custos/lucro com base nos dados históricos
//   - Filtros: diário, semanal, mensal, anual
//   - Simulação "E se?": cenários com todas boas/médias/ruins/mescla
//   - Considera desgaste do veículo (pneus, pastilhas, óleo, manutenção)
//   - Considera despesas individuais com rateio temporal
// DEPENDÊNCIAS:
//   - data/db/FinanceDatabase.kt → EarningDao, ExpenseDao
//   - data/db/FinanceExtensions.kt → VehicleProfileDao, IndividualExpenseDao
//   - data/db/RideHistoryEntity.kt → histórico de corridas
// ============================================================================

import com.ngbautoroad.data.db.*
import java.util.Calendar

/**
 * Motor de projeção financeira inteligente.
 * Usa dados históricos para projetar ganhos futuros e simular cenários.
 */
class ProjectionEngine(
    private val earningDao: EarningDao,
    private val vehicleProfileDao: VehicleProfileDao,
    private val individualExpenseDao: IndividualExpenseDao,
    private val rideHistoryDao: RideHistoryDao
) {

    /**
     * Projeta ganhos/custos/lucro para o período solicitado.
     * Usa média ponderada dos últimos 30 dias como base.
     */
    suspend fun projectFinances(period: String): FinancialProjection {
        val vehicle = vehicleProfileDao.getActiveVehicleSync()
        val costPerKm = vehicle?.costPerKm ?: 0.30 // Fallback: R$0.30/km

        // Buscar dados dos últimos 30 dias para calcular médias
        val cal = Calendar.getInstance()
        val endDate = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, -30)
        val startDate30 = cal.timeInMillis

        val totalEarnings30 = earningDao.getTotalEarningsSync(startDate30, endDate) ?: 0.0
        val totalRides30 = earningDao.getTotalRidesSync(startDate30, endDate) ?: 0
        val totalDistance30 = earningDao.getTotalDistanceSync(startDate30, endDate) ?: 0.0
        val totalDuration30 = earningDao.getTotalDurationSync(startDate30, endDate) ?: 0

        // Calcular médias diárias
        val daysWithData = 30.0.coerceAtLeast(1.0)
        val avgDailyEarnings = totalEarnings30 / daysWithData
        val avgDailyKm = totalDistance30 / daysWithData
        val avgDailyRides = (totalRides30 / daysWithData).toInt().coerceAtLeast(1)
        val avgDailyHours = (totalDuration30 / 60.0) / daysWithData

        // Multiplicador por período
        val multiplier = when (period) {
            "DIA" -> 1.0
            "SEMANA" -> 7.0
            "MES" -> 30.0
            "ANO" -> 365.0
            else -> 1.0
        }

        val projEarnings = avgDailyEarnings * multiplier
        val projKm = avgDailyKm * multiplier
        val projRides = (avgDailyRides * multiplier).toInt()
        val projHours = avgDailyHours * multiplier

        // Custos de combustível
        val projFuelCost = projKm * costPerKm

        // Custos de manutenção/desgaste baseados no veículo
        val projMaintenanceCost = calculateWearCost(vehicle, projKm)

        // Custos fixos (despesas individuais rateadas)
        val monthlyFixed = individualExpenseDao.getTotalMonthlyRatedSync() ?: 0.0
        val projFixedCosts = when (period) {
            "DIA" -> monthlyFixed / 30.0
            "SEMANA" -> monthlyFixed / 4.3
            "MES" -> monthlyFixed
            "ANO" -> monthlyFixed * 12.0
            else -> monthlyFixed / 30.0
        }

        val projTotalCosts = projFuelCost + projMaintenanceCost + projFixedCosts
        val projGrossProfit = projEarnings - projFuelCost
        val projNetProfit = projEarnings - projTotalCosts
        // Lucro real = líquido - depreciação do veículo
        val depreciationPerKm = if (vehicle != null && vehicle.purchaseValue > 0) {
            vehicle.purchaseValue / 200000.0 // Depreciação em 200.000 km
        } else 0.0
        val projRealProfit = projNetProfit - (projKm * depreciationPerKm)

        // Nível de confiança baseado na quantidade de dados
        val confidence = when {
            totalRides30 >= 100 -> 95.0
            totalRides30 >= 50 -> 85.0
            totalRides30 >= 20 -> 70.0
            totalRides30 >= 10 -> 55.0
            totalRides30 >= 5 -> 40.0
            else -> 20.0
        }

        return FinancialProjection(
            period = period,
            projectedEarnings = projEarnings,
            projectedFuelCost = projFuelCost,
            projectedMaintenanceCost = projMaintenanceCost,
            projectedFixedCosts = projFixedCosts,
            projectedTotalCosts = projTotalCosts,
            projectedGrossProfit = projGrossProfit,
            projectedNetProfit = projNetProfit,
            projectedRealProfit = projRealProfit,
            projectedKm = projKm,
            projectedRides = projRides,
            projectedHours = projHours,
            avgEarningPerRide = if (projRides > 0) projEarnings / projRides else 0.0,
            avgEarningPerKm = if (projKm > 0) projEarnings / projKm else 0.0,
            avgEarningPerHour = if (projHours > 0) projEarnings / projHours else 0.0,
            confidenceLevel = confidence
        )
    }

    /**
     * Simulação "E se?" — Projeta cenários alternativos.
     * Usa o histórico de corridas classificadas (boas/médias/ruins) para calcular
     * o que teria acontecido se o motorista tivesse aceitado todas de um tipo.
     */
    suspend fun simulateWhatIf(period: String): List<WhatIfResult> {
        val vehicle = vehicleProfileDao.getActiveVehicleSync()
        val costPerKm = vehicle?.costPerKm ?: 0.30

        // Buscar histórico de corridas dos últimos 30 dias
        val cal = Calendar.getInstance()
        val endDate = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, -30)
        val startDate = cal.timeInMillis

        val allRides = rideHistoryDao.getRidesByPeriodSync(startDate, endDate)
        if (allRides.isEmpty()) return emptyList()

        // Classificar corridas por qualidade (score)
        val goodRides = allRides.filter { it.score >= 70 }
        val avgRides = allRides.filter { it.score in 30.0..69.9 }
        val badRides = allRides.filter { it.score < 30 }

        // Total real (o que realmente aconteceu)
        val actualEarnings = allRides.sumOf { it.rideValue }
        val actualKm = allRides.sumOf { it.dropoffDistance }
        val actualHours = allRides.sumOf { it.rideDuration } / 60.0

        // Multiplicador por período
        val multiplier = when (period) {
            "DIA" -> 1.0 / 30.0
            "SEMANA" -> 7.0 / 30.0
            "MES" -> 1.0
            "ANO" -> 12.0
            else -> 1.0
        }

        // Calcular cenários
        val scenarios = mutableListOf<WhatIfResult>()

        // Cenário 1: Todas boas
        if (goodRides.isNotEmpty()) {
            scenarios.add(buildScenario(
                name = "Todas Boas (score ≥70)",
                rides = goodRides,
                totalRidesCount = allRides.size,
                multiplier = multiplier,
                costPerKm = costPerKm,
                vehicle = vehicle,
                actualEarnings = actualEarnings * multiplier,
                actualKm = actualKm * multiplier,
                actualHours = actualHours * multiplier
            ))
        }

        // Cenário 2: Todas médias
        if (avgRides.isNotEmpty()) {
            scenarios.add(buildScenario(
                name = "Todas Médias (score 30-69)",
                rides = avgRides,
                totalRidesCount = allRides.size,
                multiplier = multiplier,
                costPerKm = costPerKm,
                vehicle = vehicle,
                actualEarnings = actualEarnings * multiplier,
                actualKm = actualKm * multiplier,
                actualHours = actualHours * multiplier
            ))
        }

        // Cenário 3: Todas ruins
        if (badRides.isNotEmpty()) {
            scenarios.add(buildScenario(
                name = "Todas Ruins (score <30)",
                rides = badRides,
                totalRidesCount = allRides.size,
                multiplier = multiplier,
                costPerKm = costPerKm,
                vehicle = vehicle,
                actualEarnings = actualEarnings * multiplier,
                actualKm = actualKm * multiplier,
                actualHours = actualHours * multiplier
            ))
        }

        // Cenário 4: Mescla (70% boas + 20% médias + 10% ruins)
        val mixedAvgValue = (goodRides.map { it.rideValue }.average() * 0.7) +
            (avgRides.map { it.rideValue }.averageOrZero() * 0.2) +
            (badRides.map { it.rideValue }.averageOrZero() * 0.1)
        val mixedAvgKm = (goodRides.map { it.dropoffDistance }.average() * 0.7) +
            (avgRides.map { it.dropoffDistance }.averageOrZero() * 0.2) +
            (badRides.map { it.dropoffDistance }.averageOrZero() * 0.1)
        val mixedAvgDuration = (goodRides.map { it.rideDuration }.average() * 0.7) +
            (avgRides.map { it.rideDuration }.averageOrZero() * 0.2) +
            (badRides.map { it.rideDuration }.averageOrZero() * 0.1)

        val mixTotalRides = (allRides.size * multiplier).toInt().coerceAtLeast(1)
        val mixTotalEarnings = mixedAvgValue * mixTotalRides
        val mixTotalKm = mixedAvgKm * mixTotalRides
        val mixTotalHours = (mixedAvgDuration * mixTotalRides) / 60.0
        val mixFuelCost = mixTotalKm * costPerKm
        val mixWear = calculateWearCost(vehicle, mixTotalKm)
        val mixTireCost = calculateTireCost(vehicle, mixTotalKm)
        val mixBrakeCost = calculateBrakeCost(vehicle, mixTotalKm)
        val mixOilCost = calculateOilCost(vehicle, mixTotalKm)
        val mixTotalCosts = mixFuelCost + mixWear
        val monthlyFixed = individualExpenseDao.getTotalMonthlyRatedSync() ?: 0.0
        val fixedForPeriod = monthlyFixed * multiplier

        scenarios.add(WhatIfResult(
            scenarioName = "Mescla Ideal (70% boas + 20% médias + 10% ruins)",
            totalRides = mixTotalRides,
            totalEarnings = mixTotalEarnings,
            totalKm = mixTotalKm,
            totalHours = mixTotalHours,
            fuelCost = mixFuelCost,
            maintenanceCost = mixWear,
            tireCost = mixTireCost,
            brakepadCost = mixBrakeCost,
            oilChangeCost = mixOilCost,
            totalCosts = mixTotalCosts + fixedForPeriod,
            grossProfit = mixTotalEarnings - mixFuelCost,
            netProfit = mixTotalEarnings - mixTotalCosts - fixedForPeriod,
            realProfit = mixTotalEarnings - mixTotalCosts - fixedForPeriod - (mixTotalKm * getDepreciationPerKm(vehicle)),
            differenceFromActual = mixTotalEarnings - (actualEarnings * multiplier),
            differenceKm = mixTotalKm - (actualKm * multiplier),
            differenceHours = mixTotalHours - (actualHours * multiplier),
            avgPerRide = if (mixTotalRides > 0) mixTotalEarnings / mixTotalRides else 0.0,
            avgPerKm = if (mixTotalKm > 0) mixTotalEarnings / mixTotalKm else 0.0,
            avgPerHour = if (mixTotalHours > 0) mixTotalEarnings / mixTotalHours else 0.0
        ))

        return scenarios
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private suspend fun buildScenario(
        name: String,
        rides: List<RideHistoryEntity>,
        totalRidesCount: Int,
        multiplier: Double,
        costPerKm: Double,
        vehicle: VehicleProfileEntity?,
        actualEarnings: Double,
        actualKm: Double,
        actualHours: Double
    ): WhatIfResult {
        val avgValue = rides.map { it.rideValue }.average()
        val avgKm = rides.map { it.dropoffDistance }.average()
        val avgDuration = rides.map { it.rideDuration }.average()

        val totalRides = (totalRidesCount * multiplier).toInt().coerceAtLeast(1)
        val totalEarnings = avgValue * totalRides
        val totalKm = avgKm * totalRides
        val totalHours = (avgDuration * totalRides) / 60.0
        val fuelCost = totalKm * costPerKm
        val wearCost = calculateWearCost(vehicle, totalKm)
        val tireCost = calculateTireCost(vehicle, totalKm)
        val brakeCost = calculateBrakeCost(vehicle, totalKm)
        val oilCost = calculateOilCost(vehicle, totalKm)
        val monthlyFixed = individualExpenseDao.getTotalMonthlyRatedSync() ?: 0.0
        val fixedForPeriod = monthlyFixed * multiplier
        val totalCosts = fuelCost + wearCost + fixedForPeriod

        return WhatIfResult(
            scenarioName = name,
            totalRides = totalRides,
            totalEarnings = totalEarnings,
            totalKm = totalKm,
            totalHours = totalHours,
            fuelCost = fuelCost,
            maintenanceCost = wearCost,
            tireCost = tireCost,
            brakepadCost = brakeCost,
            oilChangeCost = oilCost,
            totalCosts = totalCosts,
            grossProfit = totalEarnings - fuelCost,
            netProfit = totalEarnings - totalCosts,
            realProfit = totalEarnings - totalCosts - (totalKm * getDepreciationPerKm(vehicle)),
            differenceFromActual = totalEarnings - actualEarnings,
            differenceKm = totalKm - actualKm,
            differenceHours = totalHours - actualHours,
            avgPerRide = if (totalRides > 0) totalEarnings / totalRides else 0.0,
            avgPerKm = if (totalKm > 0) totalEarnings / totalKm else 0.0,
            avgPerHour = if (totalHours > 0) totalEarnings / totalHours else 0.0
        )
    }

    private fun calculateWearCost(vehicle: VehicleProfileEntity?, km: Double): Double {
        if (vehicle == null) return km * 0.05 // Fallback: R$0.05/km de desgaste
        return calculateTireCost(vehicle, km) +
            calculateBrakeCost(vehicle, km) +
            calculateOilCost(vehicle, km) +
            calculateMaintenanceCost(vehicle, km)
    }

    private fun calculateTireCost(vehicle: VehicleProfileEntity?, km: Double): Double {
        if (vehicle == null || vehicle.tireLifeKm <= 0 || vehicle.tireCost <= 0) return 0.0
        return (km / vehicle.tireLifeKm) * vehicle.tireCost
    }

    private fun calculateBrakeCost(vehicle: VehicleProfileEntity?, km: Double): Double {
        if (vehicle == null || vehicle.brakepadLifeKm <= 0 || vehicle.brakepadCost <= 0) return 0.0
        return (km / vehicle.brakepadLifeKm) * vehicle.brakepadCost
    }

    private fun calculateOilCost(vehicle: VehicleProfileEntity?, km: Double): Double {
        if (vehicle == null || vehicle.oilChangeKm <= 0 || vehicle.oilChangeCost <= 0) return 0.0
        return (km / vehicle.oilChangeKm) * vehicle.oilChangeCost
    }

    private fun calculateMaintenanceCost(vehicle: VehicleProfileEntity?, km: Double): Double {
        if (vehicle == null || vehicle.maintenanceIntervalKm <= 0 || vehicle.maintenanceCost <= 0) return 0.0
        return (km / vehicle.maintenanceIntervalKm) * vehicle.maintenanceCost
    }

    private fun getDepreciationPerKm(vehicle: VehicleProfileEntity?): Double {
        if (vehicle == null || vehicle.purchaseValue <= 0) return 0.0
        return vehicle.purchaseValue / 200000.0
    }

    private fun List<Double>.averageOrZero(): Double =
        if (isEmpty()) 0.0 else average()
}
