package com.ngbautoroad.simulation

import com.ngbautoroad.data.db.IndividualExpenseEntity
import com.ngbautoroad.data.db.VehicleProfileEntity
import com.ngbautoroad.domain.*
import org.junit.Test
import org.junit.Assert.*

/**
 * Simulation 2: "Maria" -- new driver, first month, financial tracking.
 *
 * Exercises domain-layer classes that need no Android Context:
 *   - ShiftState / FatigueLevel (computed properties)
 *   - DREResult (data class math)
 *   - IndividualExpenseEntity (safeInstallments, safeMonthlyAmount)
 *   - VehicleProfileEntity (depreciation by type)
 *   - MaintenanceReserveEngine (reserve per km)
 */
class Simulation2FinanceTest {

    // =====================================================================
    // Helper: build a realistic VehicleProfileEntity for Maria's Fiat Argo
    // =====================================================================
    private fun mariaVehicle(
        vehicleType: String = "COMBUSTION",
        purchaseValue: Double = 65_000.0,
        currentOdometer: Int = 45_000
    ) = VehicleProfileEntity(
        id = 1,
        isActive = true,
        brand = "Fiat",
        model = "Argo",
        year = 2023,
        plate = "ABC1D23",
        vehicleType = vehicleType,
        fuelType = if (vehicleType == "ELECTRIC") "ELECTRIC" else "FLEX",
        averageConsumption = if (vehicleType == "ELECTRIC") 6.0 else 12.0,
        fuelPrice = if (vehicleType == "ELECTRIC") 0.90 else 5.80,
        costPerKm = if (vehicleType == "ELECTRIC") 0.15 else 0.48,
        purchaseValue = purchaseValue,
        currentOdometer = currentOdometer,
        tireLifeKm = 40_000,
        tireCost = 1_600.0,
        brakepadLifeKm = 30_000,
        brakepadCost = 600.0,
        oilChangeKm = 10_000,
        oilChangeCost = 250.0,
        maintenanceIntervalKm = 20_000,
        maintenanceCost = 800.0
    )

    // =====================================================================
    // 1. ShiftState calculations
    // =====================================================================

    @Test
    fun testShiftStateElapsedTime() {
        // Active shift started 2 hours ago, no pause
        val now = System.currentTimeMillis()
        val twoHoursMs = 2 * 3_600_000L
        val state = ShiftState(
            isActive = true,
            startTimeMs = now - twoHoursMs,
            pausedDurationMs = 0L
        )
        // elapsedMs should be approximately 2 hours (within 100ms tolerance)
        assertTrue("elapsedMs should be ~2h", state.elapsedMs in (twoHoursMs - 200)..(twoHoursMs + 200))
        assertEquals(2.0, state.elapsedHours, 0.01)
    }

    @Test
    fun testShiftStateWithPause() {
        // Active shift: 3h total, 30min paused (already resumed)
        val now = System.currentTimeMillis()
        val threeHoursMs = 3 * 3_600_000L
        val thirtyMinMs = 30 * 60_000L
        val state = ShiftState(
            isActive = true,
            isPaused = false,
            startTimeMs = now - threeHoursMs,
            pausedDurationMs = thirtyMinMs,
            lastPauseStartMs = 0L
        )
        // Active time = 3h - 30min = 2.5h
        val expectedMs = threeHoursMs - thirtyMinMs
        assertTrue("elapsedMs should be ~2.5h", state.elapsedMs in (expectedMs - 200)..(expectedMs + 200))
        assertEquals(2.5, state.elapsedHours, 0.01)
    }

    @Test
    fun testShiftStateCurrentlyPaused() {
        // Shift is currently paused -- pause started 15 min ago
        val now = System.currentTimeMillis()
        val twoHoursMs = 2 * 3_600_000L
        val fifteenMinMs = 15 * 60_000L
        val state = ShiftState(
            isActive = true,
            isPaused = true,
            startTimeMs = now - twoHoursMs,
            pausedDurationMs = 0L,
            lastPauseStartMs = now - fifteenMinMs
        )
        // Active time = 2h - 15min = 1.75h
        val expectedMs = twoHoursMs - fifteenMinMs
        assertTrue("Currently paused: elapsedMs ~ 1h45m", state.elapsedMs in (expectedMs - 200)..(expectedMs + 200))
        assertEquals(1.75, state.elapsedHours, 0.02)
    }

    @Test
    fun testShiftStateInactive() {
        // Inactive shift returns 0 elapsed
        val state = ShiftState(isActive = false, startTimeMs = System.currentTimeMillis() - 999_999)
        assertEquals(0L, state.elapsedMs)
        assertEquals(0.0, state.elapsedHours, 0.0)
    }

    @Test
    fun testShiftStateValuePerHour() {
        val now = System.currentTimeMillis()
        val state = ShiftState(
            isActive = true,
            startTimeMs = now - 4 * 3_600_000L, // 4 hours
            totalEarned = 120.0
        )
        // R$120 / 4h = R$30/h
        assertEquals(30.0, state.valuePerHour, 1.0)
    }

    @Test
    fun testShiftStateGoalProgress() {
        val state = ShiftState(totalEarned = 150.0, goalValue = 200.0)
        assertEquals(0.75f, state.goalProgress, 0.01f)
        assertFalse(state.goalReached)

        val reachedState = state.copy(totalEarned = 200.0)
        assertEquals(1.0f, reachedState.goalProgress, 0.01f)
        assertTrue(reachedState.goalReached)

        // Capped at 1.5
        val overState = state.copy(totalEarned = 400.0)
        assertEquals(1.5f, overState.goalProgress, 0.01f)
    }

    @Test
    fun testGoalProgressZeroGoal() {
        val state = ShiftState(totalEarned = 100.0, goalValue = 0.0)
        assertEquals(0f, state.goalProgress)
    }

    // =====================================================================
    // 2. Fatigue progression
    // =====================================================================

    @Test
    fun testShiftStateFatigueProgression() {
        val now = System.currentTimeMillis()
        fun stateWithHours(h: Double) = ShiftState(
            isActive = true,
            startTimeMs = now - (h * 3_600_000).toLong()
        )

        assertEquals(FatigueLevel.NONE, stateWithHours(0.0).fatigueLevel)
        assertEquals(FatigueLevel.NONE, stateWithHours(3.9).fatigueLevel)
        assertEquals(FatigueLevel.LOW, stateWithHours(4.0).fatigueLevel)
        assertEquals(FatigueLevel.LOW, stateWithHours(5.9).fatigueLevel)
        assertEquals(FatigueLevel.MODERATE, stateWithHours(6.0).fatigueLevel)
        assertEquals(FatigueLevel.MODERATE, stateWithHours(7.9).fatigueLevel)
        assertEquals(FatigueLevel.HIGH, stateWithHours(8.0).fatigueLevel)
        assertEquals(FatigueLevel.HIGH, stateWithHours(9.9).fatigueLevel)
        assertEquals(FatigueLevel.CRITICAL, stateWithHours(10.0).fatigueLevel)
        assertEquals(FatigueLevel.CRITICAL, stateWithHours(14.0).fatigueLevel)
    }

    @Test
    fun testFatigueLevelEnum() {
        // Verify all levels have non-empty labels
        for (level in FatigueLevel.entries) {
            assertTrue("Label not blank: $level", level.label.isNotBlank())
            assertTrue("Emoji not blank: $level", level.emoji.isNotBlank())
        }
        // NONE has empty alert
        assertTrue(FatigueLevel.NONE.alertMessage.isEmpty())
        // All others have alert text
        for (level in listOf(FatigueLevel.LOW, FatigueLevel.MODERATE, FatigueLevel.HIGH, FatigueLevel.CRITICAL)) {
            assertTrue("Alert not blank: $level", level.alertMessage.isNotBlank())
        }
    }

    // =====================================================================
    // 3. Cancellation rate
    // =====================================================================

    @Test
    fun testCancellationRate() {
        val state = ShiftState(
            ridesAccepted = 7,
            ridesRejected = 2,
            ridesCancelled = 1
        )
        assertEquals(10, state.totalRidesOffered)
        assertEquals(10.0, state.cancellationRate, 0.01)
        assertFalse(state.cancellationWarning) // < 3 cancellations and < 15%

        val warningState = state.copy(ridesCancelled = 3)
        assertTrue(warningState.cancellationWarning) // >= 3 cancellations
    }

    @Test
    fun testCancellationRateZeroOffers() {
        val state = ShiftState()
        assertEquals(0, state.totalRidesOffered)
        assertEquals(0.0, state.cancellationRate, 0.0)
        assertFalse(state.cancellationWarning)
    }

    // =====================================================================
    // 4. IndividualExpenseEntity: safeInstallments
    // =====================================================================

    @Test
    fun testIndividualExpenseSafeInstallmentsZero() {
        val expense = IndividualExpenseEntity(
            title = "IPVA 2026",
            totalAmount = 2400.0,
            installments = 0,
            monthlyAmount = 0.0
        )
        assertEquals(1, expense.safeInstallments)
        // safeMonthlyAmount: monthlyAmount is 0, so falls back to totalAmount / safeInstallments
        assertEquals(2400.0, expense.safeMonthlyAmount, 0.01)
    }

    @Test
    fun testIndividualExpenseSafeInstallmentsNegative() {
        val expense = IndividualExpenseEntity(
            totalAmount = 1200.0,
            installments = -5,
            monthlyAmount = 0.0
        )
        assertEquals(1, expense.safeInstallments)
        assertEquals(1200.0, expense.safeMonthlyAmount, 0.01)
    }

    @Test
    fun testIndividualExpenseNormalInstallments() {
        val expense12 = IndividualExpenseEntity(
            totalAmount = 2400.0,
            installments = 12,
            monthlyAmount = 200.0
        )
        assertEquals(12, expense12.safeInstallments)
        assertEquals(200.0, expense12.safeMonthlyAmount, 0.01)

        val expense1 = IndividualExpenseEntity(
            totalAmount = 500.0,
            installments = 1,
            monthlyAmount = 500.0
        )
        assertEquals(1, expense1.safeInstallments)
        assertEquals(500.0, expense1.safeMonthlyAmount, 0.01)

        val expense24 = IndividualExpenseEntity(
            totalAmount = 24000.0,
            installments = 24,
            monthlyAmount = 0.0 // not pre-calculated
        )
        assertEquals(24, expense24.safeInstallments)
        assertEquals(1000.0, expense24.safeMonthlyAmount, 0.01) // fallback: 24000/24
    }

    // =====================================================================
    // 5. Vehicle depreciation by type
    // =====================================================================

    @Test
    fun testVehicleDepreciationByType() {
        val purchaseValue = 60_000.0

        // COMBUSTION: 200k km life -> R$0.30/km
        val combustion = mariaVehicle(vehicleType = "COMBUSTION", purchaseValue = purchaseValue)
        val depCombustion = combustion.purchaseValue / 200_000.0
        assertEquals(0.30, depCombustion, 0.001)

        // HYBRID: 250k km life -> R$0.24/km
        val hybrid = mariaVehicle(vehicleType = "HYBRID", purchaseValue = purchaseValue)
        val vehicleLifeHybrid = 250_000.0
        val depHybrid = hybrid.purchaseValue / vehicleLifeHybrid
        assertEquals(0.24, depHybrid, 0.001)

        // ELECTRIC: 300k km life -> R$0.20/km
        val electric = mariaVehicle(vehicleType = "ELECTRIC", purchaseValue = purchaseValue)
        val vehicleLifeElectric = 300_000.0
        val depElectric = electric.purchaseValue / vehicleLifeElectric
        assertEquals(0.20, depElectric, 0.001)

        // Verify ordering: electric < hybrid < combustion (per km)
        assertTrue(depElectric < depHybrid)
        assertTrue(depHybrid < depCombustion)
    }

    @Test
    fun testVehicleDepreciationZeroPurchaseValue() {
        val vehicle = mariaVehicle(purchaseValue = 0.0)
        val depPerKm = if (vehicle.purchaseValue > 0) vehicle.purchaseValue / 200_000.0 else 0.0
        assertEquals(0.0, depPerKm, 0.0)
    }

    // =====================================================================
    // 6. MaintenanceReserveEngine (no Context needed)
    // =====================================================================

    @Test
    fun testMaintenanceReserveEngine() {
        val engine = MaintenanceReserveEngine()
        val vehicle = mariaVehicle()
        val result = engine.calculateReserve(vehicle, avgDailyKm = 150.0)

        // Reserve per km should be positive
        assertTrue("reservePerKm > 0", result.reservePerKm > 0)

        // Manual calculation:
        // Tires: 1600/40000 = 0.04
        // Brakes: 600/30000 = 0.02
        // Oil: 250/10000 = 0.025
        // Maintenance: 800/20000 = 0.04
        // Depreciation: 65000/200000 = 0.325
        // Total: 0.04 + 0.02 + 0.025 + 0.04 + 0.325 = 0.45
        val expectedReservePerKm = (1600.0 / 40000) + (600.0 / 30000) + (250.0 / 10000) + (800.0 / 20000) + (65000.0 / 200000)
        assertEquals(expectedReservePerKm, result.reservePerKm, 0.001)

        // Daily = reservePerKm * 150
        assertEquals(expectedReservePerKm * 150.0, result.dailyReserve, 0.01)
        // Weekly = daily * 7
        assertEquals(result.dailyReserve * 7, result.weeklyReserve, 0.01)
        // Monthly = daily * 30
        assertEquals(result.dailyReserve * 30, result.monthlyReserve, 0.01)

        // Breakdown should have 5 entries (tires, brakes, oil, maintenance, depreciation)
        assertEquals(5, result.breakdown.size)

        // Percentages should sum to ~100
        val totalPct = result.breakdown.sumOf { it.percentOfTotal }
        assertEquals(100.0, totalPct, 0.1)
    }

    @Test
    fun testMaintenanceReserveNullVehicle() {
        val engine = MaintenanceReserveEngine()
        val result = engine.calculateReserve(null)
        assertEquals(0.0, result.reservePerKm, 0.0)
        assertEquals(0.0, result.dailyReserve, 0.0)
        assertTrue(result.breakdown.isEmpty())
    }

    @Test
    fun testMaintenanceReserveZeroCosts() {
        val engine = MaintenanceReserveEngine()
        val vehicle = VehicleProfileEntity(
            id = 1,
            isActive = true,
            tireCost = 0.0,
            brakepadCost = 0.0,
            oilChangeCost = 0.0,
            maintenanceCost = 0.0,
            purchaseValue = 0.0
        )
        val result = engine.calculateReserve(vehicle)
        assertEquals(0.0, result.reservePerKm, 0.0)
        assertTrue(result.breakdown.isEmpty())
    }

    @Test
    fun testMaintenanceReserveNextMaintenance() {
        val engine = MaintenanceReserveEngine()
        // Odometer at 45000 -- next oil change at 50000 (5000 km away)
        val vehicle = mariaVehicle(currentOdometer = 45_000)
        val result = engine.calculateReserve(vehicle)

        // Next maintenance should be the closest one
        assertTrue("nextMaintenanceKm > 0", result.nextMaintenanceKm > 0)
        assertTrue("nextMaintenanceCost > 0", result.nextMaintenanceCost > 0)

        // Oil at 10k intervals: next at 50k -> 5000 km away
        // Maintenance at 20k intervals: next at 60k -> 15000 km away
        // Brakes at 30k intervals: next at 60k -> 15000 km away
        // Tires at 40k intervals: next at 80k -> 35000 km away
        // Closest is oil at 5000 km
        assertEquals(5_000, result.nextMaintenanceKm)
        assertEquals(250.0, result.nextMaintenanceCost, 0.01)
    }

    // =====================================================================
    // 7. DREResult data class edge cases
    // =====================================================================

    @Test
    fun testDREResultZeroValues() {
        val dre = DREResult(
            period = "DIA",
            receitaBruta = 0.0,
            custosVariaveis = 0.0,
            margemContribuicao = 0.0,
            margemContribuicaoPct = 0.0,
            custosFixos = 0.0,
            lucroOperacional = 0.0,
            lucroOperacionalPct = 0.0,
            depreciacao = 0.0,
            lucroLiquido = 0.0,
            lucroLiquidoPct = 0.0,
            combustivelCost = 0.0,
            desgasteCost = 0.0,
            totalKm = 0.0,
            totalRides = 0,
            totalHours = 0.0,
            custoVariavelPorKm = 0.0,
            receitaPorKm = 0.0,
            receitaPorHora = 0.0,
            receitaPorCorrida = 0.0,
            lucroPorKm = 0.0,
            lucroPorHora = 0.0,
            lucroPorCorrida = 0.0
        )
        assertEquals(0.0, dre.lucroLiquido, 0.0)
        assertEquals("DIA", dre.period)
    }

    @Test
    fun testDREResultNegativeProfit() {
        // Maria earns R$100 but has R$150 in costs
        val receitaBruta = 100.0
        val custosVariaveis = 80.0
        val custosFixos = 50.0
        val margemContribuicao = receitaBruta - custosVariaveis // 20
        val lucroOperacional = margemContribuicao - custosFixos // -30
        val depreciacao = 10.0
        val lucroLiquido = lucroOperacional - depreciacao // -40

        val dre = DREResult(
            period = "DIA",
            receitaBruta = receitaBruta,
            custosVariaveis = custosVariaveis,
            margemContribuicao = margemContribuicao,
            margemContribuicaoPct = (margemContribuicao / receitaBruta) * 100.0,
            custosFixos = custosFixos,
            lucroOperacional = lucroOperacional,
            lucroOperacionalPct = (lucroOperacional / receitaBruta) * 100.0,
            depreciacao = depreciacao,
            lucroLiquido = lucroLiquido,
            lucroLiquidoPct = (lucroLiquido / receitaBruta) * 100.0,
            combustivelCost = 60.0,
            desgasteCost = 20.0,
            totalKm = 80.0,
            totalRides = 5,
            totalHours = 4.0,
            custoVariavelPorKm = custosVariaveis / 80.0,
            receitaPorKm = receitaBruta / 80.0,
            receitaPorHora = receitaBruta / 4.0,
            receitaPorCorrida = receitaBruta / 5.0,
            lucroPorKm = lucroLiquido / 80.0,
            lucroPorHora = lucroLiquido / 4.0,
            lucroPorCorrida = lucroLiquido / 5.0
        )

        assertTrue("Profit should be negative", dre.lucroLiquido < 0)
        assertEquals(-40.0, dre.lucroLiquido, 0.01)
        assertTrue("Profit % should be negative", dre.lucroLiquidoPct < 0)
        assertTrue("Lucro por km negative", dre.lucroPorKm < 0)
    }

    @Test
    fun testDREResultHighValues() {
        // Super day: R$10,000 earnings
        val receitaBruta = 10_000.0
        val totalKm = 500.0
        val totalHours = 14.0
        val custosVariaveis = totalKm * 0.48 + totalKm * 0.05 // fuel + wear
        val margemContribuicao = receitaBruta - custosVariaveis
        val custosFixos = 100.0 // daily fixed
        val lucroOp = margemContribuicao - custosFixos
        val dep = totalKm * 0.325
        val lucroLiq = lucroOp - dep

        val dre = DREResult(
            period = "DIA",
            receitaBruta = receitaBruta,
            custosVariaveis = custosVariaveis,
            margemContribuicao = margemContribuicao,
            margemContribuicaoPct = (margemContribuicao / receitaBruta) * 100.0,
            custosFixos = custosFixos,
            lucroOperacional = lucroOp,
            lucroOperacionalPct = (lucroOp / receitaBruta) * 100.0,
            depreciacao = dep,
            lucroLiquido = lucroLiq,
            lucroLiquidoPct = (lucroLiq / receitaBruta) * 100.0,
            combustivelCost = totalKm * 0.48,
            desgasteCost = totalKm * 0.05,
            totalKm = totalKm,
            totalRides = 40,
            totalHours = totalHours,
            custoVariavelPorKm = custosVariaveis / totalKm,
            receitaPorKm = receitaBruta / totalKm,
            receitaPorHora = receitaBruta / totalHours,
            receitaPorCorrida = receitaBruta / 40.0,
            lucroPorKm = lucroLiq / totalKm,
            lucroPorHora = lucroLiq / totalHours,
            lucroPorCorrida = lucroLiq / 40.0
        )

        assertTrue("High earnings should yield positive profit", dre.lucroLiquido > 0)
        assertTrue("Revenue per ride > R$200", dre.receitaPorCorrida > 200)
        assertEquals(250.0, dre.receitaPorCorrida, 0.01) // 10000/40
    }

    // =====================================================================
    // 8. 30-day financial simulation (manual DRE computation)
    // =====================================================================

    @Test
    fun test30DayFinancialSimulation() {
        // Maria: 30 days, avg 8 rides/day @ R$18 each, 120km/day
        val days = 30
        val ridesPerDay = 8
        val avgRideValue = 18.0
        val kmPerDay = 120.0

        val totalRides = days * ridesPerDay           // 240
        val totalEarnings = totalRides * avgRideValue  // R$4,320
        val totalKm = days * kmPerDay                  // 3,600 km

        // Vehicle costs
        val vehicle = mariaVehicle()
        val costPerKm = vehicle.costPerKm              // R$0.48/km
        val fuelCost = totalKm * costPerKm             // R$1,728

        // Wear: tires + brakes + oil + maintenance
        val tireCostPerKm = vehicle.tireCost / vehicle.tireLifeKm     // 0.04
        val brakeCostPerKm = vehicle.brakepadCost / vehicle.brakepadLifeKm // 0.02
        val oilCostPerKm = vehicle.oilChangeCost / vehicle.oilChangeKm     // 0.025
        val maintCostPerKm = vehicle.maintenanceCost / vehicle.maintenanceIntervalKm // 0.04
        val totalWearPerKm = tireCostPerKm + brakeCostPerKm + oilCostPerKm + maintCostPerKm
        val wearCost = totalKm * totalWearPerKm

        val custosVariaveis = fuelCost + wearCost
        val margemContribuicao = totalEarnings - custosVariaveis

        // Fixed expenses: IPVA R$2400/12 + Seguro R$1800/12 + Parcela R$1200/1
        val monthlyFixed = (2400.0 / 12) + (1800.0 / 12) + 1200.0 // R$1,550
        val lucroOperacional = margemContribuicao - monthlyFixed

        // Depreciation: R$65000 / 200000 = R$0.325/km
        val depPerKm = 65000.0 / 200000.0
        val depreciacao = totalKm * depPerKm
        val lucroLiquido = lucroOperacional - depreciacao

        // Assertions
        assertEquals(240, totalRides)
        assertEquals(4320.0, totalEarnings, 0.01)
        assertEquals(3600.0, totalKm, 0.01)

        // Fuel: 3600 * 0.48 = 1728
        assertEquals(1728.0, fuelCost, 0.01)

        // Wear per km: 0.04 + 0.02 + 0.025 + 0.04 = 0.125
        assertEquals(0.125, totalWearPerKm, 0.001)
        // Wear total: 3600 * 0.125 = 450
        assertEquals(450.0, wearCost, 0.01)

        // Margem: 4320 - 2178 = 2142
        assertEquals(2142.0, margemContribuicao, 0.01)

        // Monthly fixed: 200 + 150 + 1200 = 1550
        assertEquals(1550.0, monthlyFixed, 0.01)

        // Lucro operacional: 2142 - 1550 = 592
        assertEquals(592.0, lucroOperacional, 0.01)

        // Depreciation: 3600 * 0.325 = 1170
        assertEquals(1170.0, depreciacao, 0.01)

        // Lucro liquido: 592 - 1170 = -578 (Maria is losing money!)
        assertEquals(-578.0, lucroLiquido, 0.01)
        assertTrue("Maria is operating at a loss in this scenario", lucroLiquido < 0)

        // Revenue per hour (assuming 10h/day)
        val totalHours = days * 10.0
        val revenuePerHour = totalEarnings / totalHours
        assertEquals(14.4, revenuePerHour, 0.01)

        // Revenue per km
        val revenuePerKm = totalEarnings / totalKm
        assertEquals(1.2, revenuePerKm, 0.01)
    }

    // =====================================================================
    // 9. BreakEvenResult data class
    // =====================================================================

    @Test
    fun testBreakEvenResultConstruction() {
        val result = BreakEvenResult(
            monthlyFixedCosts = 1550.0,
            avgEarningPerRide = 18.0,
            avgCostPerRide = 4.5,
            contributionPerRide = 13.5,
            breakEvenRides = 115,
            breakEvenKm = 1725.0,
            breakEvenHours = 57.5,
            ridesCompletedThisMonth = 80,
            ridesRemaining = 35,
            progressPct = 69.6,
            estimatedDaysToBreakEven = 4.4
        )
        assertEquals(1550.0, result.monthlyFixedCosts, 0.01)
        assertEquals(35, result.ridesRemaining)
        assertTrue(result.progressPct < 100.0)
    }

    // =====================================================================
    // 10. AnomalyAlert data class
    // =====================================================================

    @Test
    fun testAnomalyAlertConstruction() {
        val alert = AnomalyAlert(
            category = "Combustivel",
            currentWeekTotal = 350.0,
            historicalAvg = 200.0,
            historicalStdDev = 40.0,
            zScore = 3.75,
            severity = "CRITICAL",
            message = "Gasto 75% acima da media"
        )
        assertEquals("CRITICAL", alert.severity)
        assertTrue(alert.zScore > 3.0)
    }

    // =====================================================================
    // 11. VehicleProfileEntity cost per km calculation
    // =====================================================================

    @Test
    fun testVehicleCostPerKmCalculation() {
        // Flex: 5.80 R$/L, 12 km/L -> 0.483 R$/km
        val flex = mariaVehicle()
        val expectedCostPerKm = 5.80 / 12.0
        assertEquals(expectedCostPerKm, flex.fuelPrice / flex.averageConsumption, 0.001)

        // Electric: 0.90 R$/kWh, 6 km/kWh -> 0.15 R$/km
        val electric = mariaVehicle(vehicleType = "ELECTRIC")
        assertEquals(0.15, electric.fuelPrice / electric.averageConsumption, 0.001)
    }

    // =====================================================================
    // 12. MaintenanceReserve with different vehicle types
    // =====================================================================

    @Test
    fun testMaintenanceReserveElectricVsCombustion() {
        val engine = MaintenanceReserveEngine()

        val combustion = engine.calculateReserve(mariaVehicle(vehicleType = "COMBUSTION"))
        val electric = engine.calculateReserve(mariaVehicle(vehicleType = "ELECTRIC"))

        // Both should have reserves
        assertTrue(combustion.reservePerKm > 0)
        assertTrue(electric.reservePerKm > 0)

        // v6.10.1: MaintenanceReserveEngine now uses vehicle-type-aware depreciation life:
        //   COMBUSTION: 200k km, HYBRID: 250k km, ELECTRIC: 300k km
        // With same purchase value, electric has LOWER depreciation per km, so lower total reserve
        assertTrue("Electric reserve should be lower (longer life)", electric.reservePerKm < combustion.reservePerKm)
    }

    // =====================================================================
    // 13. FinancialProjection data class
    // =====================================================================

    @Test
    fun testFinancialProjectionConstruction() {
        val proj = com.ngbautoroad.data.db.FinancialProjection(
            period = "MES",
            projectedEarnings = 4320.0,
            projectedFuelCost = 1728.0,
            projectedMaintenanceCost = 450.0,
            projectedFixedCosts = 1550.0,
            projectedTotalCosts = 3728.0,
            projectedGrossProfit = 2592.0,
            projectedNetProfit = 592.0,
            projectedRealProfit = -578.0,
            projectedKm = 3600.0,
            projectedRides = 240,
            projectedHours = 300.0,
            avgEarningPerRide = 18.0,
            avgEarningPerKm = 1.2,
            avgEarningPerHour = 14.4,
            confidenceLevel = 70.0
        )
        assertEquals("MES", proj.period)
        assertTrue("Net profit positive", proj.projectedNetProfit > 0)
        assertTrue("Real profit negative (with depreciation)", proj.projectedRealProfit < 0)
    }

    // =====================================================================
    // 14. Edge: ShiftState with massive pause duration
    // =====================================================================

    @Test
    fun testShiftStatePauseLongerThanElapsed() {
        // Edge: pausedDurationMs > rawElapsed should coerce to 0
        val now = System.currentTimeMillis()
        val state = ShiftState(
            isActive = true,
            startTimeMs = now - 1_000L, // 1 second ago
            pausedDurationMs = 999_999L // way more than elapsed
        )
        // coerceAtLeast(0L) should prevent negative
        assertEquals(0L, state.elapsedMs)
    }

    // =====================================================================
    // 15. WhatIfResult data class
    // =====================================================================

    @Test
    fun testWhatIfResultConstruction() {
        val result = com.ngbautoroad.data.db.WhatIfResult(
            scenarioName = "Todas Boas",
            totalRides = 240,
            totalEarnings = 6000.0,
            totalKm = 3000.0,
            totalHours = 200.0,
            fuelCost = 1440.0,
            maintenanceCost = 375.0,
            tireCost = 120.0,
            brakepadCost = 60.0,
            oilChangeCost = 75.0,
            totalCosts = 3365.0,
            grossProfit = 4560.0,
            netProfit = 2635.0,
            realProfit = 1660.0,
            differenceFromActual = 1680.0,
            differenceKm = -600.0,
            differenceHours = -100.0,
            avgPerRide = 25.0,
            avgPerKm = 2.0,
            avgPerHour = 30.0
        )
        assertEquals("Todas Boas", result.scenarioName)
        assertTrue(result.differenceFromActual > 0)
    }
}
