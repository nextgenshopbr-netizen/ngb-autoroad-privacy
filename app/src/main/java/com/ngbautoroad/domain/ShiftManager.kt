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
    val ridesRejected: Int = 0
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
}

class ShiftManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("shift_prefs", Context.MODE_PRIVATE)

    fun loadState(): ShiftState = ShiftState(
        isActive = prefs.getBoolean("shift_active", false),
        isPaused = prefs.getBoolean("shift_paused", false),
        startTimeMs = prefs.getLong("shift_start", 0L),
        pausedDurationMs = prefs.getLong("shift_paused_duration", 0L),
        lastPauseStartMs = prefs.getLong("shift_last_pause_start", 0L),
        totalEarned = prefs.getFloat("shift_earned", 0f).toDouble(),
        ridesCount = prefs.getInt("shift_rides", 0),
        goalValue = prefs.getFloat("shift_goal", 200f).toDouble(),
        ridesAccepted = prefs.getInt("shift_accepted", 0),
        ridesRejected = prefs.getInt("shift_rejected", 0)
    )

    fun saveState(state: ShiftState) {
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
            .apply()
    }

    // v5.0.0: Guard contra turno duplicado
    fun startShift(goal: Double): ShiftState {
        val current = loadState()
        if (current.isActive) return current // Já tem turno ativo, não sobrescrever
        val s = ShiftState(isActive = true, startTimeMs = System.currentTimeMillis(), goalValue = goal)
        saveState(s); return s
    }

    fun pauseShift(c: ShiftState): ShiftState {
        val u = c.copy(isPaused = true, lastPauseStartMs = System.currentTimeMillis())
        saveState(u); return u
    }

    fun resumeShift(c: ShiftState): ShiftState {
        val now = System.currentTimeMillis()
        val u = c.copy(isPaused = false, pausedDurationMs = c.pausedDurationMs + (now - c.lastPauseStartMs), lastPauseStartMs = 0L)
        saveState(u); return u
    }

    fun endShift(): ShiftState { val e = ShiftState(); saveState(e); return e }

    fun addRide(c: ShiftState, value: Double, accepted: Boolean): ShiftState {
        val u = if (accepted) c.copy(totalEarned = c.totalEarned + value, ridesCount = c.ridesCount + 1, ridesAccepted = c.ridesAccepted + 1)
                else c.copy(ridesRejected = c.ridesRejected + 1)
        saveState(u); return u
    }
}
