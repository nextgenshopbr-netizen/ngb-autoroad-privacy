package com.ngbautoroad.data.model

/**
 * Dados extraídos de uma corrida via OCR/Accessibility
 */
data class RideData(
    val platform: Platform = Platform.UNKNOWN,
    val rideValue: Double = 0.0,          // Valor da corrida (R$)
    val rideDuration: Double = 0.0,       // Duração da corrida (minutos)
    val pickupDistance: Double = 0.0,     // Distância até embarque (km)
    val dropoffDistance: Double = 0.0,    // Distância até desembarque (km)
    val passengerRating: Double = 0.0,   // Avaliação do passageiro (0-5)
    val intermediateStops: Int = 0,       // Número de paradas intermediárias
    val pickupNeighborhood: String = "",  // Bairro de embarque
    val dropoffNeighborhood: String = "", // Bairro de destino
    val timestamp: Long = System.currentTimeMillis()
) {
    val valuePerKm: Double
        get() = if (dropoffDistance > 0) rideValue / dropoffDistance else 0.0

    val valuePerHour: Double
        get() = if (rideDuration > 0) (rideValue / rideDuration) * 60.0 else 0.0
}

enum class Platform(val displayName: String, val packageName: String) {
    UBER("Uber", "com.ubercab.driver"),
    NINETY_NINE("99", "com.ninety9.driver"),
    INDRIVE("inDrive", "com.machfrankfurt.android"),
    CABIFY("Cabify", "com.cabify.driver"),
    UNKNOWN("Desconhecido", "")
}

/**
 * Critérios de avaliação com pesos configuráveis
 */
data class CriteriaWeights(
    val valuePerKm: Int = 30,
    val valuePerHour: Int = 30,
    val intermediateStops: Int = 25,
    val passengerRating: Int = 15,
    val rideValue: Int = 0,
    val rideDuration: Int = 0,
    val pickupDistance: Int = 0,
    val dropoffDistance: Int = 0
) {
    val totalUsed: Int
        get() = valuePerKm + valuePerHour + intermediateStops + passengerRating +
                rideValue + rideDuration + pickupDistance + dropoffDistance

    val totalAvailable: Int get() = 100
    val remaining: Int get() = totalAvailable - totalUsed
}

/**
 * Resultado do score de uma corrida
 */
data class RideScore(
    val totalScore: Double = 0.0,
    val criteriaScores: Map<String, CriteriaScore> = emptyMap()
) {
    val scoreColor: ScoreLevel
        get() = when {
            totalScore >= 70 -> ScoreLevel.GREEN
            totalScore >= 50 -> ScoreLevel.YELLOW
            totalScore >= 30 -> ScoreLevel.ORANGE
            else -> ScoreLevel.RED
        }
}

data class CriteriaScore(
    val name: String,
    val rawValue: Double,
    val normalizedScore: Double, // 0-100
    val weight: Int,
    val weightedScore: Double,
    val level: ScoreLevel
)

enum class ScoreLevel {
    GREEN, YELLOW, ORANGE, RED
}

/**
 * Configuração de bairro bloqueado
 */
data class BlockedNeighborhood(
    val name: String,
    val type: NeighborhoodType,
    val penaltyWeight: Int = 20 // Peso da penalidade (0-100)
)

enum class NeighborhoodType {
    PICKUP,   // Embarque
    DROPOFF   // Destino
}

/**
 * Modelo de card visual
 */
data class CardModel(
    val id: Int,
    val name: String,
    val backgroundColor: Long = 0xFF101830,
    val textColor: Long = 0xFFFFFFFF,
    val accentColor: Long = 0xFF4F6BFF,
    val borderColor: Long = 0xFF4F6BFF,
    val borderRadius: Int = 12,
    val fontSize: Int = 14,
    val showPlatformIcon: Boolean = true,
    val showScore: Boolean = true,
    val isCustom: Boolean = false
)
