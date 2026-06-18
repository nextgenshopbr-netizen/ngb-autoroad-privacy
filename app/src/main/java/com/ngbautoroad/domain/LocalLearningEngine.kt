package com.ngbautoroad.domain

enum class SuggestionType(val label: String, val icon: String) {
    BEST_HOURS("Melhores Horários", "🕐"),
    BEST_NEIGHBORHOODS("Melhores Bairros", "📍"),
    BEST_RIDE_TYPES("Melhores Tipos", "🚗"),
    STOP_SUGGESTION("Parar Agora", "🛑"),
    AVOID_AREA("Evitar Área", "⚠️"),
    EARNING_PATTERN("Padrão de Ganho", "📈"),
    FATIGUE_WARNING("Alerta Fadiga", "😴")
}

data class LearningSuggestion(
    val type: SuggestionType, val title: String, val description: String,
    val confidence: Double, val basedOnRides: Int
)

data class RidePattern(val hour: Int, val dayOfWeek: Int, val neighborhood: String, val valuePerKm: Double, val accepted: Boolean)

class LocalLearningEngine {
    private val patterns = mutableListOf<RidePattern>()

    fun addPattern(p: RidePattern) { patterns.add(p); if (patterns.size > 5000) patterns.removeAt(0) }

    fun generateSuggestions(): List<LearningSuggestion> {
        if (patterns.size < 20) return emptyList()
        val suggestions = mutableListOf<LearningSuggestion>()

        // Best hours
        val byHour = patterns.filter { it.accepted }.groupBy { it.hour }
        val avgByHour = byHour.mapValues { (_, v) -> v.map { it.valuePerKm }.average() }
        val bestHours = avgByHour.entries.sortedByDescending { it.value }.take(3)
        if (bestHours.isNotEmpty()) {
            val globalAvg = patterns.filter { it.accepted }.map { it.valuePerKm }.average()
            val improvement = ((bestHours.first().value / globalAvg - 1) * 100).toInt()
            suggestions.add(LearningSuggestion(
                SuggestionType.BEST_HOURS,
                "Melhores horários: ${bestHours.joinToString(", ") { "${it.key}h" }}",
                "R\$/km ${improvement}% acima da média nesses horários.",
                0.7 + (patterns.size / 1000.0).coerceAtMost(0.25),
                patterns.size
            ))
        }

        // Best neighborhoods
        val byHood = patterns.filter { it.accepted }.groupBy { it.neighborhood }
        val avgByHood = byHood.mapValues { (_, v) -> v.map { it.valuePerKm }.average() }
        val bestHoods = avgByHood.entries.sortedByDescending { it.value }.take(3)
        if (bestHoods.size >= 2) {
            suggestions.add(LearningSuggestion(
                SuggestionType.BEST_NEIGHBORHOODS,
                "Bairros mais rentáveis: ${bestHoods.joinToString(", ") { it.key }}",
                "Posicione-se próximo a esses bairros para maximizar ganhos.",
                0.65 + (patterns.size / 2000.0).coerceAtMost(0.25),
                patterns.size
            ))
        }

        // Fatigue detection (performance drops after X hours)
        val accepted = patterns.filter { it.accepted }
        if (accepted.size > 50) {
            val firstHalf = accepted.take(accepted.size / 2).map { it.valuePerKm }.average()
            val secondHalf = accepted.drop(accepted.size / 2).map { it.valuePerKm }.average()
            if (secondHalf < firstHalf * 0.85) {
                val drop = ((1 - secondHalf / firstHalf) * 100).toInt()
                suggestions.add(LearningSuggestion(
                    SuggestionType.FATIGUE_WARNING,
                    "Performance cai ${drop}% na segunda metade",
                    "Considere pausas mais frequentes ou turnos mais curtos.",
                    0.6,
                    accepted.size
                ))
            }
        }

        return suggestions
    }
}
