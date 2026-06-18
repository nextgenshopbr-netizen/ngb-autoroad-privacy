package com.ngbautoroad.domain

data class SurgeAlert(val neighborhood: String, val confidence: Double, val estimatedMultiplier: Double, val timestamp: Long)

class SurgeDetector {
    private val recentRides = mutableListOf<Triple<String, Double, Long>>()

    fun recordRide(neighborhood: String, valuePerKm: Double) {
        recentRides.add(Triple(neighborhood, valuePerKm, System.currentTimeMillis()))
        if (recentRides.size > 100) recentRides.removeAt(0)
    }

    fun detectSurge(baselinePerKm: Map<String, Double>): List<SurgeAlert> {
        val now = System.currentTimeMillis()
        val recent = recentRides.filter { now - it.third < 30 * 60_000L }
        if (recent.size < 3) return emptyList()

        return recent.groupBy { it.first }.mapNotNull { (hood, rides) ->
            val baseline = baselinePerKm[hood] ?: return@mapNotNull null
            val avgRecent = rides.map { it.second }.average()
            val multiplier = avgRecent / baseline
            if (multiplier > 1.3) SurgeAlert(hood, (multiplier - 1.0).coerceIn(0.0, 1.0), multiplier, now)
            else null
        }
    }
}
