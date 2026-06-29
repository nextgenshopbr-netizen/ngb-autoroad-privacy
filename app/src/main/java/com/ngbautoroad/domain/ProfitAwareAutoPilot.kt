package com.ngbautoroad.domain

// ============================================================================
// ARQUIVO: ProfitAwareAutoPilot.kt
// VERSÃO: v6.3.9 — Integração IA↔Finanças
// RESPONSABILIDADE: Ajusta decisões do AutoPilot baseado na situação financeira
// DEPENDÊNCIAS:
//   - data/db/FinanceDatabase.kt → EarningDao, IndividualExpenseDao
//   - domain/FinanceDRE.kt → BreakEvenResult
// LÓGICA:
//   - Se motorista NÃO atingiu break-even do mês → reduz minAcceptScore (mais permissivo)
//   - Se motorista JÁ atingiu break-even → pode ser mais seletivo
//   - Calcula "urgência financeira" (0.0 a 1.0) baseado em quanto falta para break-even
// ============================================================================

import android.content.Context
import com.ngbautoroad.data.db.FinanceDatabase
import java.util.Calendar

/**
 * Resultado da análise de contexto financeiro para o AutoPilot.
 *
 * @param urgency Urgência financeira (0.0 = tranquilo, 1.0 = precisa muito aceitar)
 * @param scoreAdjustment Ajuste a aplicar no minAcceptScore (negativo = mais permissivo)
 * @param breakEvenReached Se o break-even do mês já foi atingido
 * @param ridesRemaining Corridas restantes para atingir break-even (0 se já atingiu)
 * @param reason Texto explicativo para logs
 */
data class FinancialContext(
    val urgency: Double,
    val scoreAdjustment: Int,
    val breakEvenReached: Boolean,
    val ridesRemaining: Int,
    val reason: String
)

class ProfitAwareAutoPilot(private val context: Context) {

    companion object {
        // Ajuste máximo: reduz até 15 pontos do minAcceptScore quando urgência = 1.0
        private const val MAX_SCORE_REDUCTION = 15
        // Bônus quando já atingiu break-even: pode ser 5 pontos mais seletivo
        private const val SELECTIVITY_BONUS = 5
    }

    /**
     * Calcula o contexto financeiro atual para ajustar decisões do AutoPilot.
     * Execução rápida: apenas 2 queries SQL simples.
     *
     * @return FinancialContext com urgência e ajuste de score
     */
    suspend fun getFinancialContext(): FinancialContext {
        return try {
            val finDb = FinanceDatabase.getInstance(context)
            val earningDao = finDb.earningDao()
            val individualExpenseDao = finDb.individualExpenseDao()

            // Período: início do mês atual até agora
            val cal = Calendar.getInstance()
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val monthStartMs = cal.timeInMillis
            val nowMs = System.currentTimeMillis()

            // Ganhos do mês
            val monthEarnings = earningDao.getTotalEarningsSync(monthStartMs, nowMs) ?: 0.0

            // Custos fixos mensais (rateados)
            val monthlyFixed = individualExpenseDao.getTotalMonthlyRatedSync() ?: 0.0

            // Dias restantes no mês
            val calNow = Calendar.getInstance()
            val totalDaysInMonth = calNow.getActualMaximum(Calendar.DAY_OF_MONTH)
            val currentDay = calNow.get(Calendar.DAY_OF_MONTH)
            val daysRemaining = totalDaysInMonth - currentDay

            // Break-even: quanto falta para cobrir custos fixos
            val deficit = (monthlyFixed - monthEarnings).coerceAtLeast(0.0)
            val breakEvenReached = deficit <= 0.0

            if (breakEvenReached) {
                // Já cobriu custos fixos → pode ser mais seletivo
                FinancialContext(
                    urgency = 0.0,
                    scoreAdjustment = SELECTIVITY_BONUS,
                    breakEvenReached = true,
                    ridesRemaining = 0,
                    reason = "Break-even atingido (R$${String.format("%.0f", monthEarnings)} > R$${String.format("%.0f", monthlyFixed)})"
                )
            } else {
                // Ainda não cobriu custos → calcular urgência
                // Urgência = (deficit / monthlyFixed) * (1 + daysRemainingPenalty)
                val baseUrgency = if (monthlyFixed > 0) deficit / monthlyFixed else 0.5
                // Quanto menos dias restam, mais urgente
                val timePressure = if (daysRemaining <= 5) 1.5 else if (daysRemaining <= 10) 1.2 else 1.0
                val urgency = (baseUrgency * timePressure).coerceIn(0.0, 1.0)

                // Estimar corridas restantes (baseado na média de ganho por corrida)
                val avgPerRide = earningDao.getAverageAmountSync(monthStartMs, nowMs) ?: 15.0
                val ridesRemaining = if (avgPerRide > 0) (deficit / avgPerRide).toInt() + 1 else 99

                // Ajuste de score: proporcional à urgência
                val scoreReduction = -(urgency * MAX_SCORE_REDUCTION).toInt()

                FinancialContext(
                    urgency = urgency,
                    scoreAdjustment = scoreReduction,
                    breakEvenReached = false,
                    ridesRemaining = ridesRemaining,
                    reason = "Faltam R$${String.format("%.0f", deficit)} (~$ridesRemaining corridas) para break-even"
                )
            }
        } catch (e: Exception) {
            // Fallback: sem ajuste
            FinancialContext(
                urgency = 0.0,
                scoreAdjustment = 0,
                breakEvenReached = false,
                ridesRemaining = 0,
                reason = "Erro ao calcular contexto: ${e.message}"
            )
        }
    }

    /**
     * Aplica o ajuste financeiro ao minAcceptScore do AutoPilot.
     *
     * @param originalMinScore Score mínimo configurado pelo motorista
     * @return Score mínimo ajustado (nunca abaixo de 30 para segurança)
     */
    fun adjustMinScore(originalMinScore: Int, financialContext: FinancialContext): Int {
        val adjusted = originalMinScore + financialContext.scoreAdjustment
        return adjusted.coerceIn(30, 100) // Nunca aceitar corrida com score < 30
    }
}
