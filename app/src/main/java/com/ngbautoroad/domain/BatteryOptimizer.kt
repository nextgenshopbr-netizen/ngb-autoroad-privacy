package com.ngbautoroad.domain

import android.content.Context
import android.os.BatteryManager

enum class BatteryMode { NORMAL, ECO, ULTRA_ECO }

class BatteryOptimizer(private val context: Context) {
    fun getCurrentLevel(): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    fun getRecommendedMode(): BatteryMode {
        val level = getCurrentLevel()
        return when {
            level <= 10 -> BatteryMode.ULTRA_ECO
            level <= 20 -> BatteryMode.ECO
            else -> BatteryMode.NORMAL
        }
    }

    fun getPollingIntervalMs(mode: BatteryMode): Long = when (mode) {
        BatteryMode.NORMAL -> 100L
        BatteryMode.ECO -> 500L
        BatteryMode.ULTRA_ECO -> 1000L
    }
}
