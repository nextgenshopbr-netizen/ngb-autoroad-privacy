package com.ngbautoroad.domain

import android.content.Context
import android.content.SharedPreferences

data class ShiftState(
    val isActive: Boolean = false,
    val isPaused: Boolean = false,
    val startTimeMs: Long = 0L,
    val pausedDurationMs: Long = 0L,
    val lastPauseStartMs: Long = 0L,
    val totalEarned: Double = 0.0,
    val ridesCount: Int = 0,
    val goalValue: Double = 200.0,
    val ridesAccepted: Int = 0,
    val ridesRejected: Int = 0,
    // v6.6.0: Cancelamento e fadiga
    val ridesCancelled: Int = 0,
    val fatigueAlertShown: Boolean = false,
    val lastFatigueAlertMs: Long = 0L
) {
    val elapsedMs: Long
        get() {
            if (!isActive) return 0L
            val now = System.currentTimeMillis()
            val rawElapsed = now - startTimeMs
            val pauseTime = if (isPaused) pausedDurationMs + (now - lastPauseStartMs) else pausedDurationMs
            return (rawElapsed - pauseTime).coerceAtLeast(0L)
        }
    val elapsedMinutes: Long get() = elapsedMs / 60_000L
    val elapsedHours: Double get() = elapsedMs / 3_600_000.0
    val valuePerHour: Double get() = if (elapsedHours > 0.0) totalEarned / elapsedHours else 0.0
    val goalProgress: Float get() = if (goalValue > 0) (totalEarned / goalValue).coerceIn(0.0, 1.5).toFloat() else 0f
    val goalReached: Boolean get() = totalEarned >= goalValue

    // v6.6.0: Ruptura #3 — Alerta de fadiga por tempo
    val fatigueLevel: FatigueLevel
        get() = when {
            elapsedHours >= 10.0 -> FatigueLevel.CRITICAL
            elapsedHours >= 8.0 -> FatigueLevel.HIGH
            elapsedHours >= 6.0 -> FatigueLevel.MODERATE
            elapsedHours >= 4.0 -> FatigueLevel.LOW
            else -> FatigueLevel.NONE
        }

    // v6.6.0: Ruptura #4 — Taxa de cancelamento
    val totalRidesOffered: Int get() = ridesAccepted + ridesRejected + ridesCancelled
    val cancellationRate: Double
        get() = if (totalRidesOffered > 0) (ridesCancelled.toDouble() / totalRidesOffered) * 100.0 else 0.0
    val cancellationWarning: Boolean get() = ridesCancelled >= 3 || cancellationRate >= 15.0
}

/**
 * v6.6.0: Níveis de fadiga por tempo de turno
 */
enum class FatigueLevel(val label: String, val emoji: String, val alertMessage: String) {
    NONE("Normal", "✅", ""),
    LOW("Atenção", "🟡", "Já são 4h de turno. Considere uma pausa."),
    MODERATE("Moderado", "🟠", "6h de turno. Faça uma pausa de 15-30min."),
    HIGH("Alto", "🔴", "8h de turno! Risco de fadiga. Pare e descanse."),
    CRITICAL("Crítico", "⛔", "10h+ de turno! Risco grave à segurança. Encerre o turno.")
}

/**
 * v6.3.5: ShiftManager com sincronização thread-safe.
 * v6.6.0: Adicionado fadiga por tempo, cancelamento, e persistência de histórico.
 * Todas as operações de leitura+escrita são protegidas por lock
 * para evitar sobrescrita de valores em corridas simultâneas.
 */
class ShiftManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("shift_prefs", Context.MODE_PRIVATE)
    private val lock = Any() // v6.3.5: Lock para operações atômicas

    // v6.6.0: Configurações de fadiga (configuráveis pelo motorista)
    companion object {
        const val FATIGUE_ALERT_INTERVAL_MS = 30 * 60_000L // Re-alertar a cada 30min após primeiro alerta
        const val DEFAULT_FATIGUE_THRESHOLD_HOURS = 6.0     // Padrão: alertar a partir de 6h
    }

    fun loadState(): ShiftState = synchronized(lock) {
        ShiftState(
            isActive = prefs.getBoolean("shift_active", false),
            isPaused = prefs.getBoolean("shift_paused", false),
            startTimeMs = prefs.getLong("shift_start", 0L),
            pausedDurationMs = prefs.getLong("shift_paused_duration", 0L),
            lastPauseStartMs = prefs.getLong("shift_last_pause_start", 0L),
            totalEarned = prefs.getFloat("shift_earned", 0f).toDouble(),
            ridesCount = prefs.getInt("shift_rides", 0),
            goalValue = prefs.getFloat("shift_goal", 200f).toDouble(),
            ridesAccepted = prefs.getInt("shift_accepted", 0),
            ridesRejected = prefs.getInt("shift_rejected", 0),
            ridesCancelled = prefs.getInt("shift_cancelled", 0),
            fatigueAlertShown = prefs.getBoolean("shift_fatigue_shown", false),
            lastFatigueAlertMs = prefs.getLong("shift_last_fatigue_alert", 0L)
        )
    }

    fun saveState(state: ShiftState) = synchronized(lock) {
        prefs.edit()
            .putBoolean("shift_active", state.isActive)
            .putBoolean("shift_paused", state.isPaused)
            .putLong("shift_start", state.startTimeMs)
            .putLong("shift_paused_duration", state.pausedDurationMs)
            .putLong("shift_last_pause_start", state.lastPauseStartMs)
            .putFloat("shift_earned", state.totalEarned.toFloat())
            .putInt("shift_rides", state.ridesCount)
            .putFloat("shift_goal", state.goalValue.toFloat())
            .putInt("shift_accepted", state.ridesAccepted)
            .putInt("shift_rejected", state.ridesRejected)
            .putInt("shift_cancelled", state.ridesCancelled)
            .putBoolean("shift_fatigue_shown", state.fatigueAlertShown)
            .putLong("shift_last_fatigue_alert", state.lastFatigueAlertMs)
            .commit() // v6.3.5: commit() síncrono em vez de apply() para garantir atomicidade
    }

    // v5.0.0: Guard contra turno duplicado
    fun startShift(goal: Double): ShiftState = synchronized(lock) {
        val current = loadState()
        if (current.isActive) return current // Já tem turno ativo, não sobrescrever
        val s = ShiftState(isActive = true, startTimeMs = System.currentTimeMillis(), goalValue = goal)
        saveState(s); return s
    }

    fun pauseShift(c: ShiftState): ShiftState = synchronized(lock) {
        val u = c.copy(isPaused = true, lastPauseStartMs = System.currentTimeMillis())
        saveState(u); return u
    }

    fun resumeShift(c: ShiftState): ShiftState = synchronized(lock) {
        val now = System.currentTimeMillis()
        val u = c.copy(isPaused = false, pausedDurationMs = c.pausedDurationMs + (now - c.lastPauseStartMs), lastPauseStartMs = 0L)
        saveState(u); return u
    }

    fun endShift(): ShiftState = synchronized(lock) {
        val e = ShiftState(); saveState(e); return e
    }

    /**
     * v6.3.5: addRide agora faz load+save atômico dentro do lock.
     * Isso garante que corridas simultâneas não sobrescrevam o totalEarned.
     */
    fun addRide(c: ShiftState, value: Double, accepted: Boolean): ShiftState = synchronized(lock) {
        // Recarregar estado mais recente do disco (pode ter mudado desde o último load do caller)
        val freshState = loadState()
        val u = if (accepted) freshState.copy(
            totalEarned = freshState.totalEarned + value,
            ridesCount = freshState.ridesCount + 1,
            ridesAccepted = freshState.ridesAccepted + 1
        ) else freshState.copy(ridesRejected = freshState.ridesRejected + 1)
        saveState(u); return u
    }

    /**
     * v6.6.0: Ruptura #4 — Registrar cancelamento
     */
    fun addCancellation(): ShiftState = synchronized(lock) {
        val freshState = loadState()
        val u = freshState.copy(ridesCancelled = freshState.ridesCancelled + 1)
        saveState(u); return u
    }

    /**
     * v6.6.0: Ruptura #3 — Verificar se deve mostrar alerta de fadiga
     * Retorna true se deve alertar (baseado no tempo e intervalo de re-alerta)
     */
    fun shouldShowFatigueAlert(state: ShiftState): Boolean {
        if (!state.isActive || state.isPaused) return false
        if (state.fatigueLevel == FatigueLevel.NONE) return false

        val now = System.currentTimeMillis()
        // Primeiro alerta: nunca foi mostrado
        if (!state.fatigueAlertShown) return true
        // Re-alertas: a cada FATIGUE_ALERT_INTERVAL_MS (30min)
        return (now - state.lastFatigueAlertMs) >= FATIGUE_ALERT_INTERVAL_MS
    }

    /**
     * v6.6.0: Marcar que alerta de fadiga foi mostrado
     */
    fun markFatigueAlertShown(): ShiftState = synchronized(lock) {
        val freshState = loadState()
        val u = freshState.copy(
            fatigueAlertShown = true,
            lastFatigueAlertMs = System.currentTimeMillis()
        )
        saveState(u); return u
    }

    /**
     * v6.6.0: Obter threshold de fadiga configurado pelo motorista
     */
    fun getFatigueThresholdHours(): Double {
        return prefs.getFloat("fatigue_threshold_hours", DEFAULT_FATIGUE_THRESHOLD_HOURS.toFloat()).toDouble()
    }

    fun setFatigueThresholdHours(hours: Double) {
        prefs.edit().putFloat("fatigue_threshold_hours", hours.toFloat()).apply()
    }
}
