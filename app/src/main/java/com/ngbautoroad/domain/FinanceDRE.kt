package com.ngbautoroad.domain

// ============================================================================
// ARQUIVO: FinanceDRE.kt
// LOCALIZAÇÃO: domain/FinanceDRE.kt
// RESPONSABILIDADE: DRE (Demonstração de Resultado do Exercício) para motorista de app
//   - Calcula Receita Bruta, Custos Variáveis, Margem de Contribuição, Custos Fixos, Lucro Operacional
//   - Break-Even Calculator: quantas corridas/km faltam para cobrir custos fixos
//   - Detecção de Anomalias: Z-Score em gastos de combustível
//   - Tudo on-device, sem nuvem, usando SQL nativo do Room
// DEPENDÊNCIAS:
//   - data/db/FinanceDatabase.kt → EarningDao, ExpenseDao, IndividualExpenseDao
//   - data/db/FinanceExtensions.kt → VehicleProfileDao
// ============================================================================

import com.ngbautoroad.data.db.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * DRE simplificado para motoristas de app.
 * Segue a estrutura: Receita → (-) Custos Variáveis → Margem de Contribuição → (-) Custos Fixos → Lucro Operacional
 */
data class DREResult(
    val period: String,
    val receitaBruta: Double,               // Total de ganhos (faturamento)
    val custosVariaveis: Double,            // Combustível + desgaste proporcional ao km
    val margemContribuicao: Double,         // Receita - Custos Variáveis
    val margemContribuicaoPct: Double,      // % da receita
    val custosFixos: Double,               // IPVA, seguro, parcela, aluguel (rateados)
    val lucroOperacional: Double,           // Margem - Custos Fixos
    val lucroOperacionalPct: Double,        // % da receita
    val depreciacao: Double,               // Depreciação do veículo
    val lucroLiquido: Double,              // Lucro Operacional - Depreciação
    val lucroLiquidoPct: Double,           // % da receita
    // Detalhamento
    val combustivelCost: Double,
    val desgasteCost: Double,
    val totalKm: Double,
    val totalRides: Int,
    val totalHours: Double,
    // Indicadores
    val custoVariavelPorKm: Double,
    val receitaPorKm: Double,
    val receitaPorHora: Double,
    val receitaPorCorrida: Double,
    val lucroPorKm: Double,
    val lucroPorHora: Double,
    val lucroPorCorrida: Double
)

/**
 * Resultado do cálculo de Break-Even (ponto de equilíbrio).
 */
data class BreakEvenResult(
    val monthlyFixedCosts: Double,          // Total de custos fixos mensais
    val avgEarningPerRide: Double,          // Ganho médio por corrida
    val avgCostPerRide: Double,             // Custo variável médio por corrida
    val contributionPerRide: Double,        // Margem de contribuição por corrida
    val breakEvenRides: Int,                // Corridas necessárias para cobrir fixos
    val breakEvenKm: Double,               // Km necessários para cobrir fixos
    val breakEvenHours: Double,            // Horas necessárias para cobrir fixos
    val ridesCompletedThisMonth: Int,       // Corridas já feitas este mês
    val ridesRemaining: Int,               // Corridas que faltam
    val progressPct: Double,               // % do break-even atingido
    val estimatedDaysToBreakEven: Double    // Dias estimados para atingir break-even
)

/**
 * Resultado da detecção de anomalias em gastos.
 */
data class AnomalyAlert(
    val category: String,
    val currentWeekTotal: Double,
    val historicalAvg: Double,
    val historicalStdDev: Double,
    val zScore: Double,
    val severity: String,  // "INFO", "WARNING", "CRITICAL"
    val message: String
)

/**
 * Motor de DRE e análise financeira inteligente.
 */
class FinanceDREEngine(
    private val earningDao: EarningDao,
    private val expenseDao: ExpenseDao,
    private val individualExpenseDao: IndividualExpenseDao,
    private val vehicleProfileDao: VehicleProfileDao
) {

    /**
     * Gera o DRE para o período solicitado.
     */
    suspend fun generateDRE(period: String): DREResult = withContext(Dispatchers.IO) {
        val vehicle = vehicleProfileDao.getActiveVehicleSync()
        val costPerKm = vehicle?.costPerKm ?: 0.30

        val cal = Calendar.getInstance()
        val endDate = cal.timeInMillis
        val startDate = when (period) {
            "DIA" -> { cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.timeInMillis }
            "SEMANA" -> { cal.add(Calendar.DAY_OF_YEAR, -7); cal.timeInMillis }
            "MES" -> { cal.set(Calendar.DAY_OF_MONTH, 1); cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.timeInMillis }
            "ANO" -> { cal.set(Calendar.DAY_OF_YEAR, 1); cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.timeInMillis }
            else -> { cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.timeInMillis }
        }

        // Receita Bruta
        val receitaBruta = earningDao.getTotalEarningsSync(startDate, endDate) ?: 0.0
        val totalKm = earningDao.getTotalDistanceSync(startDate, endDate) ?: 0.0
        val totalRides = earningDao.getTotalRidesSync(startDate, endDate) ?: 0
        val totalDurationMin = earningDao.getTotalDurationSync(startDate, endDate) ?: 0
        val totalHours = totalDurationMin / 60.0

        // Custos Variáveis (combustível + desgaste por km)
        val combustivelCost = totalKm * costPerKm
        val desgastePorKm = calculateDesgastePorKm(vehicle)
        val desgasteCost = totalKm * desgastePorKm
        val custosVariaveis = combustivelCost + desgasteCost

        // Margem de Contribuição
        val margemContribuicao = receitaBruta - custosVariaveis
        val margemContribuicaoPct = if (receitaBruta > 0) (margemContribuicao / receitaBruta) * 100.0 else 0.0

        // Custos Fixos (rateados para o período)
        val monthlyFixed = individualExpenseDao.getTotalMonthlyRatedSync() ?: 0.0
        val custosFixos = when (period) {
            "DIA" -> monthlyFixed / 30.0
            "SEMANA" -> monthlyFixed * 7.0 / 30.0
            "MES" -> monthlyFixed
            "ANO" -> monthlyFixed * 12.0
            else -> monthlyFixed / 30.0
        }

        // Lucro Operacional
        val lucroOperacional = margemContribuicao - custosFixos
        val lucroOperacionalPct = if (receitaBruta > 0) (lucroOperacional / receitaBruta) * 100.0 else 0.0

        // Depreciação
        val depreciationPerKm = if (vehicle != null && vehicle.purchaseValue > 0) {
            vehicle.purchaseValue / 200000.0
        } else 0.0
        val depreciacao = totalKm * depreciationPerKm

        // Lucro Líquido
        val lucroLiquido = lucroOperacional - depreciacao
        val lucroLiquidoPct = if (receitaBruta > 0) (lucroLiquido / receitaBruta) * 100.0 else 0.0

        // Indicadores
        val custoVariavelPorKm = if (totalKm > 0) custosVariaveis / totalKm else 0.0
        val receitaPorKm = if (totalKm > 0) receitaBruta / totalKm else 0.0
        val receitaPorHora = if (totalHours > 0) receitaBruta / totalHours else 0.0
        val receitaPorCorrida = if (totalRides > 0) receitaBruta / totalRides else 0.0
        val lucroPorKm = if (totalKm > 0) lucroLiquido / totalKm else 0.0
        val lucroPorHora = if (totalHours > 0) lucroLiquido / totalHours else 0.0
        val lucroPorCorrida = if (totalRides > 0) lucroLiquido / totalRides else 0.0

        DREResult(
            period = period,
            receitaBruta = receitaBruta,
            custosVariaveis = custosVariaveis,
            margemContribuicao = margemContribuicao,
            margemContribuicaoPct = margemContribuicaoPct,
            custosFixos = custosFixos,
            lucroOperacional = lucroOperacional,
            lucroOperacionalPct = lucroOperacionalPct,
            depreciacao = depreciacao,
            lucroLiquido = lucroLiquido,
            lucroLiquidoPct = lucroLiquidoPct,
            combustivelCost = combustivelCost,
            desgasteCost = desgasteCost,
            totalKm = totalKm,
            totalRides = totalRides,
            totalHours = totalHours,
            custoVariavelPorKm = custoVariavelPorKm,
            receitaPorKm = receitaPorKm,
            receitaPorHora = receitaPorHora,
            receitaPorCorrida = receitaPorCorrida,
            lucroPorKm = lucroPorKm,
            lucroPorHora = lucroPorHora,
            lucroPorCorrida = lucroPorCorrida
        )
    }

    /**
     * Calcula o ponto de equilíbrio (Break-Even) mensal.
     * Responde: "Quantas corridas faltam para cobrir meus custos fixos?"
     */
    suspend fun calculateBreakEven(): BreakEvenResult = withContext(Dispatchers.IO) {
        val vehicle = vehicleProfileDao.getActiveVehicleSync()
        val costPerKm = vehicle?.costPerKm ?: 0.30

        // Custos fixos mensais
        val monthlyFixed = individualExpenseDao.getTotalMonthlyRatedSync() ?: 0.0

        // Dados dos últimos 30 dias para calcular médias
        val cal = Calendar.getInstance()
        val endDate = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, -30)
        val startDate30 = cal.timeInMillis

        val totalEarnings30 = earningDao.getTotalEarningsSync(startDate30, endDate) ?: 0.0
        val totalRides30 = earningDao.getTotalRidesSync(startDate30, endDate) ?: 0
        val totalKm30 = earningDao.getTotalDistanceSync(startDate30, endDate) ?: 0.0
        val totalDuration30 = earningDao.getTotalDurationSync(startDate30, endDate) ?: 0

        // Médias por corrida
        val avgEarningPerRide = if (totalRides30 > 0) totalEarnings30 / totalRides30 else 0.0
        val avgKmPerRide = if (totalRides30 > 0) totalKm30 / totalRides30 else 0.0
        val avgDurationPerRide = if (totalRides30 > 0) totalDuration30.toDouble() / totalRides30 else 0.0

        // Custo variável por corrida
        val desgastePorKm = calculateDesgastePorKm(vehicle)
        val avgCostPerRide = avgKmPerRide * (costPerKm + desgastePorKm)

        // Margem de contribuição por corrida
        val contributionPerRide = avgEarningPerRide - avgCostPerRide

        // Break-even = Custos Fixos / Margem de Contribuição por corrida
        val breakEvenRides = if (contributionPerRide > 0) {
            (monthlyFixed / contributionPerRide).toInt() + 1
        } else 0

        val breakEvenKm = breakEvenRides * avgKmPerRide
        val breakEvenHours = (breakEvenRides * avgDurationPerRide) / 60.0

        // Progresso este mês
        val monthCal = Calendar.getInstance()
        monthCal.set(Calendar.DAY_OF_MONTH, 1)
        monthCal.set(Calendar.HOUR_OF_DAY, 0)
        monthCal.set(Calendar.MINUTE, 0)
        monthCal.set(Calendar.SECOND, 0)
        val monthStart = monthCal.timeInMillis
        val ridesThisMonth = earningDao.getTotalRidesSync(monthStart, endDate) ?: 0

        val ridesRemaining = (breakEvenRides - ridesThisMonth).coerceAtLeast(0)
        val progressPct = if (breakEvenRides > 0) (ridesThisMonth.toDouble() / breakEvenRides * 100.0).coerceAtMost(100.0) else 0.0

        // Dias estimados para break-even
        val daysWorked = earningDao.countDistinctDaysSync(startDate30, endDate) ?: 1
        val avgRidesPerDay = if (daysWorked > 0) totalRides30.toDouble() / daysWorked else 0.0
        val estimatedDays = if (avgRidesPerDay > 0) ridesRemaining / avgRidesPerDay else 0.0

        BreakEvenResult(
            monthlyFixedCosts = monthlyFixed,
            avgEarningPerRide = avgEarningPerRide,
            avgCostPerRide = avgCostPerRide,
            contributionPerRide = contributionPerRide,
            breakEvenRides = breakEvenRides,
            breakEvenKm = breakEvenKm,
            breakEvenHours = breakEvenHours,
            ridesCompletedThisMonth = ridesThisMonth,
            ridesRemaining = ridesRemaining,
            progressPct = progressPct,
            estimatedDaysToBreakEven = estimatedDays
        )
    }

    /**
     * Detecta anomalias nos gastos de combustível da última semana.
     * Usa Z-Score: se o gasto semanal estiver > 2σ acima da média histórica, alerta.
     */
    suspend fun detectAnomalies(): List<AnomalyAlert> = withContext(Dispatchers.IO) {
        val alerts = mutableListOf<AnomalyAlert>()

        val cal = Calendar.getInstance()
        val endDate = cal.timeInMillis

        // Última semana
        cal.add(Calendar.DAY_OF_YEAR, -7)
        val weekStart = cal.timeInMillis

        // Últimas 8 semanas (para calcular média e desvio padrão)
        val weeklyTotals = mutableListOf<Double>()
        val tempCal = Calendar.getInstance()
        for (i in 1..8) {
            val wEnd = tempCal.timeInMillis
            tempCal.add(Calendar.DAY_OF_YEAR, -7)
            val wStart = tempCal.timeInMillis
            val total = expenseDao.getTotalByCategorySync("Combustível", wStart, wEnd) ?: 0.0
            weeklyTotals.add(total)
        }

        // Gasto da semana atual
        val currentWeekFuel = expenseDao.getTotalByCategorySync("Combustível", weekStart, endDate) ?: 0.0

        if (weeklyTotals.size >= 4 && weeklyTotals.any { it > 0 }) {
            val avg = weeklyTotals.average()
            val variance = weeklyTotals.map { (it - avg) * (it - avg) }.average()
            val stdDev = kotlin.math.sqrt(variance).coerceAtLeast(1.0)
            val zScore = if (stdDev > 0) (currentWeekFuel - avg) / stdDev else 0.0

            if (zScore > 1.5) {
                val severity = when {
                    zScore > 3.0 -> "CRITICAL"
                    zScore > 2.0 -> "WARNING"
                    else -> "INFO"
                }
                val pctAbove = ((currentWeekFuel - avg) / avg * 100).toInt()
                alerts.add(AnomalyAlert(
                    category = "Combustível",
                    currentWeekTotal = currentWeekFuel,
                    historicalAvg = avg,
                    historicalStdDev = stdDev,
                    zScore = zScore,
                    severity = severity,
                    message = "Gasto de combustível ${pctAbove}% acima da média semanal (R$ %.2f vs R$ %.2f)".format(currentWeekFuel, avg)
                ))
            }
        }

        alerts
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private fun calculateDesgastePorKm(vehicle: VehicleProfileEntity?): Double {
        if (vehicle == null) return 0.05 // Fallback: R$0.05/km

        var total = 0.0
        if (vehicle.tireLifeKm > 0 && vehicle.tireCost > 0) {
            total += vehicle.tireCost / vehicle.tireLifeKm
        }
        if (vehicle.brakepadLifeKm > 0 && vehicle.brakepadCost > 0) {
            total += vehicle.brakepadCost / vehicle.brakepadLifeKm
        }
        if (vehicle.oilChangeKm > 0 && vehicle.oilChangeCost > 0) {
            total += vehicle.oilChangeCost / vehicle.oilChangeKm
        }
        if (vehicle.maintenanceIntervalKm > 0 && vehicle.maintenanceCost > 0) {
            total += vehicle.maintenanceCost / vehicle.maintenanceIntervalKm
        }
        return if (total > 0) total else 0.05
    }
}
