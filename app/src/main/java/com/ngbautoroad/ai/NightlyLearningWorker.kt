package com.ngbautoroad.ai

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ngbautoroad.data.db.AppDatabase
import com.ngbautoroad.data.db.FinanceDatabase
import com.ngbautoroad.domain.AdaptiveScoringEngine
import com.ngbautoroad.domain.LocalLearningEngine
import com.ngbautoroad.domain.MemoryConsolidationEngine
import com.ngbautoroad.domain.TelemetryLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Worker do Android projetado para "Idle Training" (Treinamento Ocioso).
 *
 * Rodara exclusivamente quando:
 * - O dispositivo estiver conectado no CARREGADOR.
 * - A bateria nao estiver baixa.
 * - O dispositivo estiver ocioso (Idle).
 *
 * Responsabilidade:
 * Mapear as corridas aceitas/recusadas no banco e recalibrar a matematica
 * fina do AdaptiveScoringEngine sem consumir bateria durante o turno de trabalho.
 */
class NightlyLearningWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val telemetry = TelemetryLogger.getInstance(applicationContext)
        try {
            telemetry.system("NightlyLearningWorker: iniciando calibracao noturna")

            val appDb = AppDatabase.getInstance(applicationContext)
            val financeDb = FinanceDatabase.getInstance(applicationContext)

            // 1. Load last 90 days of COMPLETED rides
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -90)
            val since90Days = cal.timeInMillis

            val rides = appDb.rideHistoryDao().getSince(since90Days)
                .filter { it.status == "COMPLETED" }

            telemetry.system("NightlyLearningWorker: ${rides.size} corridas COMPLETED nos ultimos 90 dias")

            if (rides.isEmpty()) {
                telemetry.system("NightlyLearningWorker: sem corridas para calibrar, abortando")
                saveTimestamp()
                return@withContext Result.success()
            }

            // 2. Seed LocalLearningEngine with ride patterns
            val localLearning = LocalLearningEngine(applicationContext)
            localLearning.seedFromDatabase(applicationContext)
            telemetry.system("NightlyLearningWorker: LocalLearningEngine seeded com ${localLearning.getPatternCount()} padroes")

            // 3. Calibrate AdaptiveScoringEngine with completed rides
            val completedWithData = rides.filter { it.valuePerKm > 0 }
            if (completedWithData.size >= 30) {
                val adaptiveEngine = AdaptiveScoringEngine(applicationContext)
                adaptiveEngine.calibrate(
                    valuesPerKm = completedWithData.map { it.valuePerKm },
                    valuesPerHour = completedWithData.filter { it.rideDuration > 0 }
                        .map { it.rideValue / (it.rideDuration / 60.0) },
                    rideValues = completedWithData.map { it.rideValue },
                    pickupDistances = completedWithData.filter { it.pickupDistance > 0 }
                        .map { it.pickupDistance },
                    dropoffDistances = completedWithData.filter { it.dropoffDistance > 0 }
                        .map { it.dropoffDistance },
                    durations = completedWithData.filter { it.rideDuration > 0 }
                        .map { it.rideDuration }
                )
                telemetry.system("NightlyLearningWorker: AdaptiveScoringEngine calibrado com ${completedWithData.size} corridas")
            }

            // 4. MemoryConsolidationEngine: analyze market changes
            try {
                val memoryEngine = MemoryConsolidationEngine(applicationContext)
                val cal3 = Calendar.getInstance()
                cal3.add(Calendar.DAY_OF_YEAR, -3)
                val since3Days = cal3.timeInMillis

                val recentRides = rides.filter { it.timestamp >= since3Days && it.valuePerKm > 0 }
                val allRidesWithKm = rides.filter { it.valuePerKm > 0 }

                if (recentRides.isNotEmpty() && allRidesWithKm.size >= 10) {
                    val recentAvg = recentRides.map { it.valuePerKm }.average()
                    val historicalAvg = allRidesWithKm.map { it.valuePerKm }.average()
                    memoryEngine.analyzeMarketChanges(recentAvg, historicalAvg)
                    telemetry.system("NightlyLearningWorker: MemoryConsolidation analisou mercado (recent=${"%.2f".format(recentAvg)}, hist=${"%.2f".format(historicalAvg)})")
                }
            } catch (e: Exception) {
                telemetry.system("NightlyLearningWorker: MemoryConsolidation falhou: ${e.message}")
            }

            // 5. Save timestamp
            saveTimestamp()
            telemetry.system("NightlyLearningWorker: calibracao concluida com sucesso")

            Result.success()
        } catch (e: Exception) {
            telemetry.system("NightlyLearningWorker: erro no aprendizado noturno: ${e.message}")
            android.util.Log.e("NightlyLearningWorker", "Erro no aprendizado noturno", e)
            Result.retry()
        }
    }

    private fun saveTimestamp() {
        val prefs = applicationContext.getSharedPreferences("ai_brain", Context.MODE_PRIVATE)
        prefs.edit().putLong("last_nightly_training", System.currentTimeMillis()).apply()
    }

    companion object {
        const val WORK_NAME = "NightlyDeepLearningWork"

        fun schedule(context: Context) {
            val constraints = androidx.work.Constraints.Builder()
                .setRequiresCharging(true)
                .setRequiresBatteryNotLow(true)
                .setRequiresDeviceIdle(true)
                .build()

            val request = androidx.work.PeriodicWorkRequestBuilder<NightlyLearningWorker>(
                24, java.util.concurrent.TimeUnit.HOURS,
                6, java.util.concurrent.TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setInitialDelay(2, java.util.concurrent.TimeUnit.HOURS)
                .build()

            androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
