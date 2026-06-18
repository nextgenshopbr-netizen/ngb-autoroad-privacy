package com.ngbautoroad.domain

data class NeighborhoodRankData(
    val name: String, val avgValuePerKm: Double, val avgValuePerHour: Double,
    val totalRides: Int, val avgRating: Double, val acceptRate: Double
)

class NeighborhoodRanker {
    fun rank(rides: List<Map<String, Any>>, sortBy: String = "valuePerKm"): List<NeighborhoodRankData> {
        if (rides.isEmpty()) return emptyList()
        val grouped = rides.groupBy { it["neighborhood"] as? String ?: "Desconhecido" }
        return grouped.map { (name, list) ->
            val avgVKm = list.mapNotNull { (it["valuePerKm"] as? Number)?.toDouble() }.average().takeIf { !it.isNaN() } ?: 0.0
            val avgVH = list.mapNotNull { (it["valuePerHour"] as? Number)?.toDouble() }.average().takeIf { !it.isNaN() } ?: 0.0
            val avgR = list.mapNotNull { (it["rating"] as? Number)?.toDouble() }.average().takeIf { !it.isNaN() } ?: 0.0
            NeighborhoodRankData(name, avgVKm, avgVH, list.size, avgR, 0.0)
        }.sortedByDescending { when(sortBy) { "valuePerHour" -> it.avgValuePerHour; "rides" -> it.totalRides.toDouble(); else -> it.avgValuePerKm } }
    }
}
