package com.ngbautoroad.service

// ============================================================================
// ARQUIVO: RecurringExpenseWorker.kt
// LOCALIZAÇÃO: service/RecurringExpenseWorker.kt
// RESPONSABILIDADE: Worker que gera instâncias de despesas recorrentes diariamente
//   - Roda 1x/dia via WorkManager (PeriodicWorkRequest)
//   - Verifica despesas com isRecurring=true e recurringDays contendo o dia atual
//   - Gera instância (ExpenseEntity com isGenerated=true, parentExpenseId=original)
//   - Evita duplicatas via countGeneratedForDay
// DEPENDÊNCIAS:
//   - data/db/FinanceDatabase.kt → ExpenseDao
//   - WorkManager (androidx.work)
// ============================================================================

import android.content.Context
import android.util.Log
import androidx.work.*
import com.ngbautoroad.data.db.ExpenseEntity
import com.ngbautoroad.data.db.FinanceDatabase
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Worker que gera automaticamente instâncias de despesas recorrentes.
 * Roda 1x/dia e verifica quais despesas recorrentes devem ser geradas hoje.
 */
class RecurringExpenseWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "RecurringExpenseWorker"
        private const val WORK_NAME = "recurring_expense_daily"

        /**
         * Agenda o Worker para rodar 1x/dia.
         * Deve ser chamado no Application.onCreate() ou na MainActivity.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<RecurringExpenseWorker>(
                1, TimeUnit.DAYS
            )
                .setConstraints(constraints)
                .setInitialDelay(calculateInitialDelay(), TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "RecurringExpenseWorker agendado (1x/dia)")
        }

        /**
         * Calcula delay até as 00:05 do próximo dia para garantir que rode no início do dia.
         */
        private fun calculateInitialDelay(): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 5)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return target.timeInMillis - now.timeInMillis
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val db = FinanceDatabase.getInstance(applicationContext)
            val expenseDao = db.expenseDao()

            val today = Calendar.getInstance()
            val dayOfWeek = today.get(Calendar.DAY_OF_WEEK) // 1=Dom, 2=Seg, ..., 7=Sáb
            // Converter para formato do app: 1=Seg, 2=Ter, ..., 7=Dom
            val appDayOfWeek = when (dayOfWeek) {
                Calendar.MONDAY -> 1
                Calendar.TUESDAY -> 2
                Calendar.WEDNESDAY -> 3
                Calendar.THURSDAY -> 4
                Calendar.FRIDAY -> 5
                Calendar.SATURDAY -> 6
                Calendar.SUNDAY -> 7
                else -> 1
            }

            // Buscar todas as despesas recorrentes ativas
            val recurringExpenses = expenseDao.getRecurringExpensesSync()

            var generated = 0

            for (expense in recurringExpenses) {
                // Verificar se hoje é um dos dias configurados
                val configuredDays = expense.recurringDays.split(",")
                    .mapNotNull { it.trim().toIntOrNull() }

                if (configuredDays.isEmpty() || !configuredDays.contains(appDayOfWeek)) {
                    continue // Hoje não é dia desta despesa
                }

                // Verificar duração (se configurada)
                if (expense.recurringDuration > 0) {
                    val daysSinceCreation = ((today.timeInMillis - expense.date) / 86_400_000L).toInt()
                    if (daysSinceCreation > expense.recurringDuration) {
                        continue // Expirou
                    }
                }

                // Verificar se já foi gerada hoje (evitar duplicata)
                val todayStart = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val todayEnd = todayStart + 86_400_000L

                val alreadyGenerated = expenseDao.countGeneratedForDay(expense.id, todayStart, todayEnd)
                if (alreadyGenerated > 0) {
                    continue // Já gerada hoje
                }

                // Gerar instância
                val instance = ExpenseEntity(
                    category = expense.category,
                    amount = expense.amount,
                    description = "${expense.description} (auto)",
                    date = today.timeInMillis,
                    isRecurring = false,
                    isGenerated = true,
                    parentExpenseId = expense.id,
                    recurringDay = 0,
                    recurringDays = "",
                    recurringDuration = 0,
                    liters = null,
                    pricePerLiter = null
                )
                expenseDao.insert(instance)
                generated++
            }

            Log.d(TAG, "RecurringExpenseWorker concluído: $generated despesas geradas")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Erro no RecurringExpenseWorker: ${e.message}", e)
            Result.retry()
        }
    }
}
