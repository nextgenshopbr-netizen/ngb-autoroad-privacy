package com.ngbautoroad.domain

import android.content.Context
import com.ngbautoroad.data.db.FinanceDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ============================================================================
// ARQUIVO: ShiftHistoryManager.kt
// VERSÃO: v6.6.0
// RESPONSABILIDADE: Ruptura #10 — Persistir histórico de turnos no banco
//   Problema: ShiftManager usa SharedPreferences, sobrescreve a cada turno.
//   Solução: Ao encerrar turno, salva no banco shift_history para análise.
// DEPENDENTES:
//   - ShiftManager.kt → chama saveToHistory ao encerrar turno
//   - DashboardTab.kt → exibe estatísticas de turnos
//   - LocalLearningEngine.kt → analisa padrões de turno
// ============================================================================

/**
 * Dados de um turno encerrado (para persistência no banco).
 * Corresponde à tabela shift_history criada na MIGRATION_7_8.
 */
data class ShiftHistoryRecord(
    val id: Long = 0,
    val startTime: Long = 0,
    val endTime: Long = 0,
    val durationMinutes: Int = 0,
    val totalEarned: Double = 0.0,
    val ridesAccepted: Int = 0,
    val ridesRejected: Int = 0,
    val ridesCancelled: Int = 0,
    val valuePerHour: Double = 0.0,
    val goalValue: Double = 0.0,
    val goalReached: Boolean = false
)

/**
 * Gerencia a persistência e consulta do histórico de turnos.
 */
class ShiftHistoryManager(private val context: Context) {

    /**
     * Salva o turno encerrado no banco de dados.
     * Chamado pelo ShiftManager.endShift() ou pelo RideLifecycleManager.
     */
    suspend fun saveShiftToHistory(state: ShiftState) = withContext(Dispatchers.IO) {
        if (!state.isActive && state.startTimeMs == 0L) return@withContext // Estado vazio, não salvar

        val db = FinanceDatabase.getInstance(context)
        val endTime = System.currentTimeMillis()
        val durationMin = ((endTime - state.startTimeMs - state.pausedDurationMs) / 60_000L).toInt()

        db.openHelper.writableDatabase.execSQL(
            """INSERT INTO shift_history (startTime, endTime, durationMinutes, totalEarned, 
               ridesAccepted, ridesRejected, ridesCancelled, valuePerHour, goalValue, goalReached)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            arrayOf(
                state.startTimeMs,
                endTime,
                durationMin,
                state.totalEarned,
                state.ridesAccepted,
                state.ridesRejected,
                state.ridesCancelled,
                state.valuePerHour,
                state.goalValue,
                if (state.goalReached) 1 else 0
            )
        )
    }

    /**
     * Obtém histórico de turnos dos últimos N dias.
     */
    suspend fun getHistory(days: Int = 30): List<ShiftHistoryRecord> = withContext(Dispatchers.IO) {
        val db = FinanceDatabase.getInstance(context)
        val cutoff = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
        val cursor = db.openHelper.readableDatabase.query(
            "SELECT * FROM shift_history WHERE startTime >= ? ORDER BY startTime DESC",
            arrayOf(cutoff.toString())
        )

        val records = mutableListOf<ShiftHistoryRecord>()
        while (cursor.moveToNext()) {
            records.add(ShiftHistoryRecord(
                id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                startTime = cursor.getLong(cursor.getColumnIndexOrThrow("startTime")),
                endTime = cursor.getLong(cursor.getColumnIndexOrThrow("endTime")),
                durationMinutes = cursor.getInt(cursor.getColumnIndexOrThrow("durationMinutes")),
                totalEarned = cursor.getDouble(cursor.getColumnIndexOrThrow("totalEarned")),
                ridesAccepted = cursor.getInt(cursor.getColumnIndexOrThrow("ridesAccepted")),
                ridesRejected = cursor.getInt(cursor.getColumnIndexOrThrow("ridesRejected")),
                ridesCancelled = cursor.getInt(cursor.getColumnIndexOrThrow("ridesCancelled")),
                valuePerHour = cursor.getDouble(cursor.getColumnIndexOrThrow("valuePerHour")),
                goalValue = cursor.getDouble(cursor.getColumnIndexOrThrow("goalValue")),
                goalReached = cursor.getInt(cursor.getColumnIndexOrThrow("goalReached")) == 1
            ))
        }
        cursor.close()
        records
    }

    /**
     * Estatísticas resumidas dos últimos N dias.
     */
    suspend fun getStats(days: Int = 30): ShiftStats = withContext(Dispatchers.IO) {
        val history = getHistory(days)
        if (history.isEmpty()) return@withContext ShiftStats()

        ShiftStats(
            totalShifts = history.size,
            totalHours = history.sumOf { it.durationMinutes } / 60.0,
            totalEarned = history.sumOf { it.totalEarned },
            avgValuePerHour = history.map { it.valuePerHour }.filter { it > 0 }.average().takeIf { !it.isNaN() } ?: 0.0,
            avgDurationHours = history.map { it.durationMinutes }.average() / 60.0,
            goalsReached = history.count { it.goalReached },
            totalRidesAccepted = history.sumOf { it.ridesAccepted },
            totalRidesRejected = history.sumOf { it.ridesRejected },
            totalRidesCancelled = history.sumOf { it.ridesCancelled },
            bestShiftEarned = history.maxOfOrNull { it.totalEarned } ?: 0.0,
            bestValuePerHour = history.maxOfOrNull { it.valuePerHour } ?: 0.0
        )
    }
}

data class ShiftStats(
    val totalShifts: Int = 0,
    val totalHours: Double = 0.0,
    val totalEarned: Double = 0.0,
    val avgValuePerHour: Double = 0.0,
    val avgDurationHours: Double = 0.0,
    val goalsReached: Int = 0,
    val totalRidesAccepted: Int = 0,
    val totalRidesRejected: Int = 0,
    val totalRidesCancelled: Int = 0,
    val bestShiftEarned: Double = 0.0,
    val bestValuePerHour: Double = 0.0
) {
    val acceptanceRate: Double
        get() {
            val total = totalRidesAccepted + totalRidesRejected + totalRidesCancelled
            return if (total > 0) (totalRidesAccepted.toDouble() / total) * 100.0 else 0.0
        }
    val cancellationRate: Double
        get() {
            val total = totalRidesAccepted + totalRidesRejected + totalRidesCancelled
            return if (total > 0) (totalRidesCancelled.toDouble() / total) * 100.0 else 0.0
        }
}
