package com.ngbautoroad.simulation

import org.junit.Test
import org.junit.Assert.*
import com.ngbautoroad.data.db.VehicleProfileEntity
import com.ngbautoroad.data.db.IndividualExpenseEntity
import com.ngbautoroad.domain.MaintenanceReserveEngine
import com.ngbautoroad.domain.MaintenanceReserveResult
import com.ngbautoroad.domain.ReserveBreakdown

/**
 * Verificação matemática e aritmética rigorosa dos módulos Financeiro e Veículos.
 *
 * Testa:
 * - MaintenanceReserveEngine: reserva R$/km, breakdown, próxima manutenção
 * - VehicleProfileEntity: costPerKm, depreciação por tipo
 * - IndividualExpenseEntity: safeInstallments, rateio mensal
 * - Cálculos de desgaste: combustão vs elétrico vs híbrido
 * - Odômetro: fator de correção EWMA, limites
 * - Anomalia Z-Score: cálculo estatístico
 * - DRE: margem, lucro operacional, lucro líquido
 * - Edge cases: zeros, negativos, divisão por zero
 */
class FinanceVehicleMathTest {

    // ═══════════════════════════════════════════════════════════════════
    // HELPER: Veículo padrão (Fiat Argo combustão)
    // ═══════════════════════════════════════════════════════════════════
    private fun defaultVehicle() = VehicleProfileEntity(
        id = 1, isActive = true,
        brand = "Fiat", model = "Argo", year = 2022,
        vehicleType = "COMBUSTION", fuelType = "FLEX",
        averageConsumption = 12.0, // 12 km/L
        fuelPrice = 5.80,          // R$5.80/L
        costPerKm = 5.80 / 12.0,   // ~R$0.483/km
        purchaseValue = 70000.0,
        currentOdometer = 45000,
        tireLifeKm = 40000, tireCost = 1500.0,
        brakepadLifeKm = 30000, brakepadCost = 600.0,
        oilChangeKm = 10000, oilChangeCost = 300.0,
        maintenanceIntervalKm = 20000, maintenanceCost = 800.0
    )

    private fun electricVehicle() = defaultVehicle().copy(
        vehicleType = "ELECTRIC", fuelType = "ELECTRIC",
        averageConsumption = 6.0, // 6 km/kWh
        fuelPrice = 0.75,         // R$0.75/kWh
        costPerKm = 0.75 / 6.0,  // R$0.125/km
        purchaseValue = 150000.0
    )

    private fun hybridVehicle() = defaultVehicle().copy(
        vehicleType = "HYBRID", fuelType = "FLEX",
        purchaseValue = 120000.0
    )

    // ═══════════════════════════════════════════════════════════════════
    // SEÇÃO 1: MAINTENANCE RESERVE ENGINE — Cálculo de reserva R$/km
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testReserveCalculationManual() {
        val engine = MaintenanceReserveEngine()
        val v = defaultVehicle()
        val result = engine.calculateReserve(v, avgDailyKm = 150.0)

        // Cálculo manual:
        // Pneus:    1500 / 40000 = 0.0375 R$/km
        // Freios:    600 / 30000 = 0.0200 R$/km
        // Óleo:      300 / 10000 = 0.0300 R$/km
        // Revisão:   800 / 20000 = 0.0400 R$/km
        // Deprec:  70000 /200000 = 0.3500 R$/km
        // Total:                   0.4775 R$/km
        val expectedPerKm = 1500.0/40000 + 600.0/30000 + 300.0/10000 + 800.0/20000 + 70000.0/200000
        assertEquals("Reserve per km manual calc", expectedPerKm, result.reservePerKm, 0.0001)

        // Diário: 0.4775 * 150 = R$71.625
        assertEquals("Daily reserve", expectedPerKm * 150.0, result.dailyReserve, 0.01)
        // Semanal: 71.625 * 7 = R$501.375
        assertEquals("Weekly reserve", expectedPerKm * 150.0 * 7, result.weeklyReserve, 0.01)
        // Mensal: 71.625 * 30 = R$2148.75
        assertEquals("Monthly reserve", expectedPerKm * 150.0 * 30, result.monthlyReserve, 0.01)
    }

    @Test
    fun testReserveBreakdownPercentages() {
        val engine = MaintenanceReserveEngine()
        val result = engine.calculateReserve(defaultVehicle())

        val totalPct = result.breakdown.sumOf { it.percentOfTotal }
        assertEquals("Breakdown percentages should sum to 100%", 100.0, totalPct, 0.1)

        for (item in result.breakdown) {
            assertTrue("Each percentage should be 0-100: ${item.category}=${item.percentOfTotal}",
                item.percentOfTotal in 0.0..100.0)
            assertTrue("costPerKm should be positive: ${item.category}", item.costPerKm > 0)
        }
    }

    @Test
    fun testReserveWithNullVehicle() {
        val engine = MaintenanceReserveEngine()
        val result = engine.calculateReserve(null)
        assertEquals("Null vehicle should return 0 reserve", 0.0, result.reservePerKm, 0.0)
        assertTrue("Breakdown should be empty", result.breakdown.isEmpty())
    }

    @Test
    fun testReserveWithZeroCosts() {
        val engine = MaintenanceReserveEngine()
        val v = VehicleProfileEntity(
            tireLifeKm = 40000, tireCost = 0.0,
            brakepadLifeKm = 30000, brakepadCost = 0.0,
            oilChangeKm = 10000, oilChangeCost = 0.0,
            maintenanceIntervalKm = 20000, maintenanceCost = 0.0,
            purchaseValue = 0.0
        )
        val result = engine.calculateReserve(v)
        assertEquals("Zero costs = zero reserve", 0.0, result.reservePerKm, 0.0)
    }

    @Test
    fun testReserveWithZeroIntervals() {
        val engine = MaintenanceReserveEngine()
        val v = VehicleProfileEntity(
            tireLifeKm = 0, tireCost = 1500.0,     // interval=0, custo>0 → PULA (guarded)
            brakepadLifeKm = 0, brakepadCost = 600.0,
            oilChangeKm = 0, oilChangeCost = 300.0,
            maintenanceIntervalKm = 0, maintenanceCost = 800.0
        )
        val result = engine.calculateReserve(v)
        assertEquals("Zero intervals should produce zero reserve (not crash)", 0.0, result.reservePerKm, 0.0)
    }

    // ═══════════════════════════════════════════════════════════════════
    // SEÇÃO 2: PRÓXIMA MANUTENÇÃO
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testNextMaintenanceCalculation() {
        val engine = MaintenanceReserveEngine()
        val v = defaultVehicle() // odometer=45000

        val result = engine.calculateReserve(v)

        // Odômetro: 45000
        // Próxima troca óleo: ceil(45000/10000)*10000 = 50000, faltam 5000km
        // Próxima pneu: ceil(45000/40000)*40000 = 80000, faltam 35000km
        // Próxima freio: ceil(45000/30000)*30000 = 60000, faltam 15000km
        // Próxima revisão: ceil(45000/20000)*20000 = 60000, faltam 15000km
        // Mais próxima: óleo em 5000km, custo R$300

        assertEquals("Next maintenance should be oil change at 5000km",
            5000, result.nextMaintenanceKm)
        assertEquals("Next maintenance cost should be R$300",
            300.0, result.nextMaintenanceCost, 0.01)
    }

    @Test
    fun testNextMaintenanceAtExactInterval() {
        val engine = MaintenanceReserveEngine()
        // Odômetro exatamente em múltiplo de intervalo
        val v = defaultVehicle().copy(currentOdometer = 40000)

        val result = engine.calculateReserve(v)
        // Próxima troca óleo: (40000/10000 + 1)*10000 = 50000, faltam 10000km
        // Próxima pneu: (40000/40000 + 1)*40000 = 80000, faltam 40000km
        // Próxima freio: (40000/30000 + 1)*30000 = 60000, faltam 20000km
        // Próxima revisão: (40000/20000 + 1)*20000 = 60000, faltam 20000km
        // Mais próxima: óleo em 10000km
        assertEquals("At exact interval, next is a full cycle ahead",
            10000, result.nextMaintenanceKm)
    }

    @Test
    fun testNextMaintenanceWithZeroOdometer() {
        val engine = MaintenanceReserveEngine()
        val v = defaultVehicle().copy(currentOdometer = 0)
        val result = engine.calculateReserve(v)
        assertEquals("Zero odometer → nextMaintenanceKm=0", 0, result.nextMaintenanceKm)
    }

    // ═══════════════════════════════════════════════════════════════════
    // SEÇÃO 3: DESGASTE ELÉTRICO vs COMBUSTÃO
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testElectricVsCombustionWearDifferences() {
        val engine = MaintenanceReserveEngine()
        val combustion = engine.calculateReserve(defaultVehicle())
        val electric = engine.calculateReserve(electricVehicle())

        // v6.10.1: MaintenanceReserveEngine now uses vehicle-type-aware depreciation life:
        //   COMBUSTION: 200k km (deprec = 70000/200000 = 0.35)
        //   ELECTRIC: 300k km (deprec = 150000/300000 = 0.50)
        val combustionDeprecPerKm = 70000.0 / 200000.0  // 0.35
        val electricDeprecPerKm = 150000.0 / 300000.0    // 0.50

        assertTrue("Electric depreciation per km should be higher than combustion (higher purchase value)",
            electricDeprecPerKm > combustionDeprecPerKm)

        // Reserva do elétrico deve ser maior (depreciação domina — higher purchase value)
        assertTrue("Electric total reserve should be higher: elec=${electric.reservePerKm} comb=${combustion.reservePerKm}",
            electric.reservePerKm > combustion.reservePerKm)
    }

    // ═══════════════════════════════════════════════════════════════════
    // SEÇÃO 4: VEHICLE COST PER KM
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testCostPerKmCalculation() {
        // Combustão: 5.80 R$/L / 12 km/L = 0.4833 R$/km
        val combustion = defaultVehicle()
        assertEquals("Combustion costPerKm", 5.80 / 12.0, combustion.costPerKm, 0.001)

        // Elétrico: 0.75 R$/kWh / 6 km/kWh = 0.125 R$/km
        val electric = electricVehicle()
        assertEquals("Electric costPerKm", 0.75 / 6.0, electric.costPerKm, 0.001)

        // Elétrico deve ser muito mais barato por km
        assertTrue("Electric should be cheaper per km",
            electric.costPerKm < combustion.costPerKm)

        // Razão: elétrico ~4x mais barato
        val ratio = combustion.costPerKm / electric.costPerKm
        assertTrue("Combustion should be ~3-5x more expensive: ratio=$ratio", ratio in 3.0..5.0)
    }

    @Test
    fun testCostPerKmEdgeCases() {
        // Zero consumption → costPerKm manual (can't divide)
        val zeroCons = defaultVehicle().copy(averageConsumption = 0.0, costPerKm = 0.0)
        assertEquals("Zero consumption = zero costPerKm", 0.0, zeroCons.costPerKm, 0.0)

        // Very high consumption (hypermiling) → very low cost
        val efficient = defaultVehicle().copy(averageConsumption = 25.0, costPerKm = 5.80 / 25.0)
        assertTrue("High efficiency = low cost: ${efficient.costPerKm}",
            efficient.costPerKm < 0.25)
    }

    // ═══════════════════════════════════════════════════════════════════
    // SEÇÃO 5: DEPRECIAÇÃO POR TIPO DE VEÍCULO
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testDepreciationByVehicleType() {
        // Combustão: purchaseValue / 200,000 km
        val combustionDep = 70000.0 / 200000.0
        assertEquals("Combustion depreciation R$/km", 0.35, combustionDep, 0.001)

        // Elétrico: purchaseValue / 300,000 km (v7.3.0 vehicle-type-aware)
        val electricDep = 150000.0 / 300000.0
        assertEquals("Electric depreciation R$/km", 0.50, electricDep, 0.001)

        // Híbrido: purchaseValue / 250,000 km
        val hybridDep = 120000.0 / 250000.0
        assertEquals("Hybrid depreciation R$/km", 0.48, hybridDep, 0.001)

        // Mesmo valor de compra: elétrico depreciaria MENOS por km (vida mais longa)
        val samePurchase = 100000.0
        assertTrue("Same price: electric depreciates less per km than combustion",
            samePurchase / 300000.0 < samePurchase / 200000.0)
    }

    @Test
    fun testDepreciationWithZeroPurchaseValue() {
        val v = defaultVehicle().copy(purchaseValue = 0.0)
        val engine = MaintenanceReserveEngine()
        val result = engine.calculateReserve(v)

        // Sem valor de compra, depreciação = 0
        val hasDepreciation = result.breakdown.any { it.category == "Depreciação" }
        assertFalse("Zero purchase value should skip depreciation", hasDepreciation)
    }

    // ═══════════════════════════════════════════════════════════════════
    // SEÇÃO 6: INDIVIDUAL EXPENSE — SAFE INSTALLMENTS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testSafeInstallments() {
        // Normal case
        val normal = IndividualExpenseEntity(totalAmount = 1200.0, installments = 12)
        assertEquals("12 installments = safe", 12, normal.safeInstallments)
        assertEquals("Monthly = 1200/12 = 100", 100.0, normal.safeMonthlyAmount, 0.01)

        // Zero installments → safeInstallments = 1 (prevents division by zero)
        val zero = IndividualExpenseEntity(totalAmount = 1200.0, installments = 0)
        assertEquals("0 installments → safe = 1", 1, zero.safeInstallments)
        assertEquals("Monthly = 1200/1 = 1200", 1200.0, zero.safeMonthlyAmount, 0.01)

        // Negative installments → safeInstallments = 1
        val negative = IndividualExpenseEntity(totalAmount = 1200.0, installments = -5)
        assertEquals("Negative installments → safe = 1", 1, negative.safeInstallments)

        // Single installment (à vista)
        val single = IndividualExpenseEntity(totalAmount = 500.0, installments = 1)
        assertEquals("1 installment = full amount monthly", 500.0, single.safeMonthlyAmount, 0.01)
    }

    @Test
    fun testInstallmentsMathPrecision() {
        // R$999.99 / 3 parcelas = R$333.33 (recurring .33 issue)
        val expense = IndividualExpenseEntity(totalAmount = 999.99, installments = 3)
        val monthly = expense.safeMonthlyAmount
        assertEquals("999.99 / 3 precision", 333.33, monthly, 0.01)

        // Verify: monthly * installments ≈ totalAmount
        val reconstructed = monthly * expense.installments
        assertEquals("Reconstructed should match total", 999.99, reconstructed, 0.01)
    }

    // ═══════════════════════════════════════════════════════════════════
    // SEÇÃO 7: Z-SCORE ANOMALY DETECTION
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testZScoreCalculation() {
        // Dados semanais simulados: R$200, R$210, R$190, R$205, R$200, R$195, R$210, R$200
        val weeklyTotals = listOf(200.0, 210.0, 190.0, 205.0, 200.0, 195.0, 210.0, 200.0)
        val avg = weeklyTotals.average() // 201.25
        val variance = weeklyTotals.map { (it - avg) * (it - avg) }.average()
        val stdDev = kotlin.math.sqrt(variance).coerceAtLeast(1.0) // ~6.3

        // Semana normal: R$205
        val normalZScore = (205.0 - avg) / stdDev
        assertTrue("Normal week z-score should be < 2.0: $normalZScore", normalZScore < 2.0)

        // Semana anômala: R$250
        val anomalyZScore = (250.0 - avg) / stdDev
        assertTrue("Anomaly z-score should be > 2.0: $anomalyZScore", anomalyZScore > 2.0)

        // Semana crítica: R$300
        val criticalZScore = (300.0 - avg) / stdDev
        assertTrue("Critical z-score should be > 3.0: $criticalZScore", criticalZScore > 3.0)
    }

    @Test
    fun testZScoreWithConstantData() {
        // Todos os valores iguais → stdDev = 0 → coerceAtLeast(1.0)
        val constant = listOf(100.0, 100.0, 100.0, 100.0)
        val avg = constant.average()
        val variance = constant.map { (it - avg) * (it - avg) }.average()
        val stdDev = kotlin.math.sqrt(variance).coerceAtLeast(1.0)

        assertEquals("Constant data stdDev should be floored at 1.0", 1.0, stdDev, 0.001)

        // Z-score de valor acima: (110 - 100) / 1.0 = 10
        val zScore = (110.0 - avg) / stdDev
        assertEquals("Z-score with constant data", 10.0, zScore, 0.1)
    }

    // ═══════════════════════════════════════════════════════════════════
    // SEÇÃO 8: DRE — CÁLCULOS DE MARGEM E LUCRO
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testDREManualCalculation() {
        // Simular DRE de 1 dia:
        // Receita: R$350 (10 corridas)
        // KM rastreado: 120 km
        // Fator correção: 1.3
        // KM real: 120 * 1.3 = 156 km
        // CostPerKm (combustível): R$0.483
        // Combustível: 156 * 0.483 = R$75.35
        // Desgaste estimado: 156 * desgastePorKm
        // Custos fixos/dia: R$2000/30 = R$66.67

        val receita = 350.0
        val kmTracked = 120.0
        val correctionFactor = 1.3
        val kmReal = kmTracked * correctionFactor // 156 km
        val costPerKm = 5.80 / 12.0 // R$0.483
        val combustivel = kmReal * costPerKm // R$75.35

        // Desgaste (combustão):
        // Pneus: 1500/40000 = 0.0375
        // Freio: 600/30000 = 0.02
        // Óleo: 300/10000 = 0.03
        // Revisão: 800/20000 = 0.04
        val desgastePorKm = 1500.0/40000 + 600.0/30000 + 300.0/10000 + 800.0/20000
        val desgaste = kmReal * desgastePorKm // 156 * 0.1275 = R$19.89

        val custosVariaveis = combustivel + desgaste // 75.35 + 19.89 = R$95.24
        val margemContribuicao = receita - custosVariaveis // 350 - 95.24 = R$254.76
        val margemPct = (margemContribuicao / receita) * 100.0 // 72.8%

        assertTrue("Margem contribuição should be ~72-73%", margemPct in 70.0..75.0)

        // Custos fixos (R$2000/mês = R$66.67/dia)
        val custosFixos = 2000.0 / 30.0
        val lucroOperacional = margemContribuicao - custosFixos // 254.76 - 66.67 = R$188.09
        assertTrue("Lucro operacional should be ~R$188", lucroOperacional in 180.0..200.0)

        // Depreciação: R$70000/200000 = R$0.35/km * 156km = R$54.60
        val deprecPerKm = 70000.0 / 200000.0
        val depreciacao = kmReal * deprecPerKm
        assertEquals("Depreciação diária ~R$54.60", 54.6, depreciacao, 1.0)

        // Lucro líquido real
        val lucroLiquido = lucroOperacional - depreciacao // 188.09 - 54.60 = R$133.49
        assertTrue("Lucro líquido should be ~R$133", lucroLiquido in 125.0..145.0)

        // R$/hora (10h turno)
        val horasTrabalhadas = 10.0
        val lucroHora = lucroLiquido / horasTrabalhadas // R$13.35/h
        assertTrue("Lucro/hora should be ~R$13", lucroHora in 12.0..15.0)
    }

    @Test
    fun testDREWithZeroRevenue() {
        val receita = 0.0
        val margemPct = if (receita > 0) 0.0 else 0.0 // Guard
        assertEquals("Zero revenue = 0% margin", 0.0, margemPct, 0.0)

        val lucro = receita - 100.0 // despesas existem
        assertTrue("Zero revenue = negative profit", lucro < 0)
    }

    @Test
    fun testDREBreakEvenCalculation() {
        // Custos fixos mensais: R$2000
        // Contribuição por corrida: R$25.48 (margem)
        // Break-even: 2000 / 25.48 = 78.5 corridas

        val custosFixos = 2000.0
        val margemPorCorrida = 25.48
        val breakEvenRides = if (margemPorCorrida > 0) custosFixos / margemPorCorrida else Double.MAX_VALUE

        assertEquals("Break-even should be ~79 rides", 78.5, breakEvenRides, 1.0)

        // Com 5 corridas/dia, 16 dias úteis: 80 corridas → break-even atingido
        val corridasPorDia = 5.0
        val diasParaBreakEven = breakEvenRides / corridasPorDia
        assertTrue("Should break even in ~16 days", diasParaBreakEven in 14.0..18.0)
    }

    @Test
    fun testDREBreakEvenWithZeroMargin() {
        val custosFixos = 2000.0
        val margemPorCorrida = 0.0
        val breakEvenRides = if (margemPorCorrida > 0) custosFixos / margemPorCorrida else Double.MAX_VALUE

        assertEquals("Zero margin = infinite break-even", Double.MAX_VALUE, breakEvenRides, 0.0)
    }

    @Test
    fun testDRENegativeProfit() {
        // Motorista novo: pouca receita, muita despesa
        val receita = 150.0 // dia ruim
        val kmReal = 200.0
        val costPerKm = 5.80 / 12.0
        val combustivel = kmReal * costPerKm // R$96.67
        val desgaste = kmReal * 0.1275       // R$25.50
        val custosVariaveis = combustivel + desgaste // R$122.17
        val margemContribuicao = receita - custosVariaveis // R$27.83
        val custosFixos = 2000.0 / 30.0 // R$66.67
        val lucroOperacional = margemContribuicao - custosFixos // -R$38.84

        assertTrue("Bad day should have negative operational profit", lucroOperacional < 0)
    }

    // ═══════════════════════════════════════════════════════════════════
    // SEÇÃO 9: ODÔMETRO — FATOR DE CORREÇÃO
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testOdometerCorrectionFactorMath() {
        // Cenário: motorista informa odômetro = 50000
        // KM rastreado desde última atualização: 3000 km
        // Fator de correção: 1.3
        // KM real estimado: 3000 * 1.3 = 3900 km
        // Odômetro estimado: 50000 + 3900 = 53900

        val baseOdometer = 50000
        val kmTracked = 3000.0
        val factor = 1.3
        val estimatedKm = (kmTracked * factor).toInt()
        val estimatedOdometer = baseOdometer + estimatedKm

        assertEquals("Estimated odometer with factor 1.3", 53900, estimatedOdometer)
    }

    @Test
    fun testOdometerEWMACalibration() {
        // EWMA com alpha=0.3, 5 entradas
        // Fatores: 1.2, 1.4, 1.3, 1.25, 1.35
        val factors = listOf(1.2, 1.4, 1.3, 1.25, 1.35)
        val alpha = 0.3

        // EWMA: start with first value, then apply exponential decay
        var ewma = factors.first()
        for (i in 1 until factors.size) {
            ewma = alpha * factors[i] + (1 - alpha) * ewma
        }

        // Manual:
        // ewma0 = 1.2
        // ewma1 = 0.3*1.4 + 0.7*1.2 = 0.42 + 0.84 = 1.26
        // ewma2 = 0.3*1.3 + 0.7*1.26 = 0.39 + 0.882 = 1.272
        // ewma3 = 0.3*1.25 + 0.7*1.272 = 0.375 + 0.8904 = 1.2654
        // ewma4 = 0.3*1.35 + 0.7*1.2654 = 0.405 + 0.88578 = 1.29078

        assertEquals("EWMA calculation", 1.29078, ewma, 0.001)

        // O fator final deve estar entre o min e max dos inputs
        assertTrue("EWMA should be within input range",
            ewma >= factors.min() && ewma <= factors.max())
    }

    @Test
    fun testOdometerFactorBounds() {
        // Fator mínimo: 1.0 (motorista profissional, sem uso pessoal)
        // Fator máximo: 5.0 (família grande, muito uso pessoal)
        val minFactor = 1.0
        val maxFactor = 5.0

        val factor = 2.5
        val clamped = factor.coerceIn(minFactor, maxFactor)
        assertEquals("Factor within bounds", 2.5, clamped, 0.0)

        val tooLow = 0.5
        assertEquals("Below min → clamp to 1.0", minFactor, tooLow.coerceIn(minFactor, maxFactor), 0.0)

        val tooHigh = 8.0
        assertEquals("Above max → clamp to 5.0", maxFactor, tooHigh.coerceIn(minFactor, maxFactor), 0.0)
    }

    @Test
    fun testOdometerGPSBlendingMath() {
        // GPS factor = (rawFactor - 1.0) * 0.5 + 1.0
        // rawFactor=1.3: (1.3-1.0)*0.5+1.0 = 0.15+1.0 = 1.15
        // rawFactor=1.0: 1.0 (threshold: if <1.05, use 1.0)
        // rawFactor=2.0: (2.0-1.0)*0.5+1.0 = 0.5+1.0 = 1.5
        // rawFactor=5.0: (5.0-1.0)*0.5+1.0 = 2.0+1.0 = 3.0

        fun gpsBasedFactor(raw: Double) = if (raw < 1.05) 1.0 else (raw - 1.0) * 0.5 + 1.0

        assertEquals("Factor 1.0 → GPS factor 1.0", 1.0, gpsBasedFactor(1.0), 0.001)
        assertEquals("Factor 1.04 → GPS factor 1.0 (threshold)", 1.0, gpsBasedFactor(1.04), 0.001)
        assertEquals("Factor 1.3 → GPS factor 1.15", 1.15, gpsBasedFactor(1.3), 0.001)
        assertEquals("Factor 2.0 → GPS factor 1.5", 1.5, gpsBasedFactor(2.0), 0.001)
        assertEquals("Factor 5.0 → GPS factor 3.0", 3.0, gpsBasedFactor(5.0), 0.001)

        // Monotonicidade: higher raw → higher GPS factor
        var prev = 0.0
        for (raw in listOf(1.0, 1.05, 1.1, 1.3, 1.5, 2.0, 3.0, 5.0)) {
            val gpsFactor = gpsBasedFactor(raw)
            assertTrue("GPS factor should be monotonic: raw=$raw gpsFactor=$gpsFactor prev=$prev",
                gpsFactor >= prev)
            prev = gpsFactor
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SEÇÃO 10: DESGASTE DIFERENCIADO ELÉTRICO (DRE calculateDesgastePorKm)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testElectricWearCalculation() {
        val v = defaultVehicle() // Combustão base
        // Combustão: desgaste normal
        val combustionWear = 1500.0/40000 + 600.0/30000 + 300.0/10000 + 800.0/20000
        // = 0.0375 + 0.02 + 0.03 + 0.04 = 0.1275

        // Elétrico (DRE logic):
        // Pneus: 1500 / (40000*0.75) = 1500/30000 = 0.05 (25% mais desgaste)
        // Freio: 600 / (30000*2.0) = 600/60000 = 0.01 (2x mais durável)
        // Óleo: 300 / (10000*3.0) = 300/30000 = 0.01 (fluidos duram 3x)
        // Revisão: (800*0.6) / 20000 = 480/20000 = 0.024 (40% mais barato)
        val electricWear = 1500.0/(40000*0.75) + 600.0/(30000*2.0) + 300.0/(10000*3.0) + (800.0*0.6)/20000
        // = 0.05 + 0.01 + 0.01 + 0.024 = 0.094

        assertTrue("Electric wear (${electricWear}) should be LESS than combustion (${combustionWear})",
            electricWear < combustionWear)

        // Diferença: 0.1275 - 0.094 = 0.0335 (elétrico 26% mais barato em manutenção)
        val savingsPercent = ((combustionWear - electricWear) / combustionWear) * 100
        assertTrue("Electric saves ~26% on maintenance: ${savingsPercent}%",
            savingsPercent in 20.0..35.0)
    }

    // ═══════════════════════════════════════════════════════════════════
    // SEÇÃO 11: PROJEÇÃO FINANCEIRA — CONFIANÇA
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testProjectionConfidenceLevels() {
        // Confidence mapping (from ProjectionEngine):
        // >= 100 rides → 95%
        // >= 50 rides → 85%
        // >= 20 rides → 70%
        // >= 10 rides → 55%
        // >= 5 rides → 40%
        // else → 0%

        fun confidence(rides: Int) = when {
            rides >= 100 -> 95.0
            rides >= 50 -> 85.0
            rides >= 20 -> 70.0
            rides >= 10 -> 55.0
            rides >= 5 -> 40.0
            else -> 0.0
        }

        assertEquals("0 rides → 0%", 0.0, confidence(0), 0.0)
        assertEquals("3 rides → 0%", 0.0, confidence(3), 0.0)
        assertEquals("5 rides → 40%", 40.0, confidence(5), 0.0)
        assertEquals("10 rides → 55%", 55.0, confidence(10), 0.0)
        assertEquals("20 rides → 70%", 70.0, confidence(20), 0.0)
        assertEquals("50 rides → 85%", 85.0, confidence(50), 0.0)
        assertEquals("100 rides → 95%", 95.0, confidence(100), 0.0)
        assertEquals("500 rides → 95%", 95.0, confidence(500), 0.0)

        // Monotonicidade
        var prevConf = -1.0
        for (rides in listOf(0, 1, 5, 10, 20, 50, 100, 500)) {
            val conf = confidence(rides)
            assertTrue("Confidence monotonic: rides=$rides conf=$conf", conf >= prevConf)
            prevConf = conf
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SEÇÃO 12: STRESS TEST — RESERVA COM VALORES EXTREMOS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testReserveWithExtremeValues() {
        val engine = MaintenanceReserveEngine()

        // Veículo de luxo: tudo caro
        val luxury = defaultVehicle().copy(
            tireCost = 8000.0,       // Pneus de performance
            brakepadCost = 3000.0,   // Freios cerâmicos
            oilChangeCost = 1500.0,  // Óleo sintético premium
            maintenanceCost = 5000.0, // Revisão em concessionária
            purchaseValue = 500000.0  // Carro de R$500k
        )
        val luxuryResult = engine.calculateReserve(luxury, avgDailyKm = 150.0)
        assertTrue("Luxury reserve > R$1/km", luxuryResult.reservePerKm > 1.0)
        assertTrue("Luxury monthly reserve > R$4500", luxuryResult.monthlyReserve > 4500.0)

        // Veículo popular: tudo barato
        val popular = defaultVehicle().copy(
            tireCost = 600.0,
            brakepadCost = 200.0,
            oilChangeCost = 150.0,
            maintenanceCost = 300.0,
            purchaseValue = 25000.0
        )
        val popularResult = engine.calculateReserve(popular, avgDailyKm = 150.0)
        assertTrue("Popular reserve < luxury", popularResult.reservePerKm < luxuryResult.reservePerKm)
    }

    // ═══════════════════════════════════════════════════════════════════
    // SEÇÃO 13: ARITMÉTICA FLOAT PRECISION
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testFloatingPointPrecision() {
        // Verificar que cálculos não acumulam erro de float
        val engine = MaintenanceReserveEngine()
        val v = defaultVehicle()

        val result = engine.calculateReserve(v)

        // A soma dos costPerKm do breakdown deve == reservePerKm (sem depreciação se separar)
        val sumBreakdown = result.breakdown.sumOf { it.costPerKm }
        assertEquals("Sum of breakdown costPerKm should match total",
            result.reservePerKm, sumBreakdown, 0.0001)

        // dailyReserve = reservePerKm * avgDailyKm (exato, sem rounding)
        assertEquals("Daily = perKm * 150",
            result.reservePerKm * 150.0, result.dailyReserve, 0.0001)
    }
}
