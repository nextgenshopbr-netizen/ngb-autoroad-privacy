package com.ngbautoroad.domain

import com.ngbautoroad.data.model.*

/**
 * Algoritmo de pontuação de corridas — Sistema de 100 pontos
 *
 * Cada critério recebe um peso (0-100) configurável pelo usuário.
 * A soma dos pesos deve ser exatamente 100.
 * Cada critério é normalizado para 0-100 e depois multiplicado pelo peso.
 * Score final = soma(normalizado * peso) / 100
 *
 * Adicionalmente, os DriverThresholds (valores mínimos desejados) aplicam
 * penalidades quando a corrida não atinge os mínimos do motorista.
 */
class RideScorer(
    private val weights: CriteriaWeights,
    private val driverThresholds: DriverThresholds = DriverThresholds(),
    private val blockedNeighborhoods: List<BlockedNeighborhood> = emptyList(),
    private val thresholds: ScoringThresholds = ScoringThresholds()
) {

    fun calculateScore(ride: RideData): RideScore {
        val criteriaScores = mutableMapOf<String, CriteriaScore>()
        val violations = mutableListOf<ThresholdViolation>()

        // 1. Valor por KM
        if (weights.valuePerKm > 0) {
            val normalized = normalizeValuePerKm(ride.valuePerKm)
            criteriaScores["valuePerKm"] = CriteriaScore(
                name = "Valor/KM",
                rawValue = ride.valuePerKm,
                normalizedScore = normalized,
                weight = weights.valuePerKm,
                weightedScore = normalized * weights.valuePerKm / 100.0,
                level = getLevel(normalized)
            )
            // Verificar threshold do motorista
            if (driverThresholds.isValuePerKmActive() && ride.valuePerKm < driverThresholds.minValuePerKm) {
                val penalty = weights.valuePerKm * 0.5 // Penalidade de 50% do peso
                violations.add(ThresholdViolation(
                    criteriaName = "Valor/KM",
                    currentValue = ride.valuePerKm,
                    minimumRequired = driverThresholds.minValuePerKm,
                    penaltyApplied = penalty
                ))
            }
        }

        // 2. Valor por Hora
        if (weights.valuePerHour > 0) {
            val normalized = normalizeValuePerHour(ride.valuePerHour)
            criteriaScores["valuePerHour"] = CriteriaScore(
                name = "Valor/Hora",
                rawValue = ride.valuePerHour,
                normalizedScore = normalized,
                weight = weights.valuePerHour,
                weightedScore = normalized * weights.valuePerHour / 100.0,
                level = getLevel(normalized)
            )
            if (driverThresholds.isValuePerHourActive() && ride.valuePerHour < driverThresholds.minValuePerHour) {
                val penalty = weights.valuePerHour * 0.5
                violations.add(ThresholdViolation(
                    criteriaName = "Valor/Hora",
                    currentValue = ride.valuePerHour,
                    minimumRequired = driverThresholds.minValuePerHour,
                    penaltyApplied = penalty
                ))
            }
        }

        // 3. Paradas Intermediárias
        if (weights.intermediateStops > 0) {
            val normalized = normalizeStops(ride.intermediateStops)
            criteriaScores["intermediateStops"] = CriteriaScore(
                name = "Paradas",
                rawValue = ride.intermediateStops.toDouble(),
                normalizedScore = normalized,
                weight = weights.intermediateStops,
                weightedScore = normalized * weights.intermediateStops / 100.0,
                level = getLevel(normalized)
            )
            if (driverThresholds.isStopsActive() && ride.intermediateStops > driverThresholds.maxStops) {
                val penalty = weights.intermediateStops * 0.7
                violations.add(ThresholdViolation(
                    criteriaName = "Paradas",
                    currentValue = ride.intermediateStops.toDouble(),
                    minimumRequired = driverThresholds.maxStops.toDouble(),
                    penaltyApplied = penalty
                ))
            }
        }

        // 4. Avaliação do Passageiro
        if (weights.passengerRating > 0) {
            val normalized = normalizeRating(ride.passengerRating)
            criteriaScores["passengerRating"] = CriteriaScore(
                name = "Avaliação",
                rawValue = ride.passengerRating,
                normalizedScore = normalized,
                weight = weights.passengerRating,
                weightedScore = normalized * weights.passengerRating / 100.0,
                level = getLevel(normalized)
            )
            if (driverThresholds.isPassengerRatingActive() && ride.passengerRating < driverThresholds.minPassengerRating) {
                val penalty = weights.passengerRating * 0.6
                violations.add(ThresholdViolation(
                    criteriaName = "Avaliação",
                    currentValue = ride.passengerRating,
                    minimumRequired = driverThresholds.minPassengerRating,
                    penaltyApplied = penalty
                ))
            }
        }

        // 5. Valor da Corrida
        if (weights.rideValue > 0) {
            val normalized = normalizeRideValue(ride.rideValue)
            criteriaScores["rideValue"] = CriteriaScore(
                name = "Valor Corrida",
                rawValue = ride.rideValue,
                normalizedScore = normalized,
                weight = weights.rideValue,
                weightedScore = normalized * weights.rideValue / 100.0,
                level = getLevel(normalized)
            )
            if (driverThresholds.isRideValueActive() && ride.rideValue < driverThresholds.minRideValue) {
                val penalty = weights.rideValue * 0.5
                violations.add(ThresholdViolation(
                    criteriaName = "Valor Corrida",
                    currentValue = ride.rideValue,
                    minimumRequired = driverThresholds.minRideValue,
                    penaltyApplied = penalty
                ))
            }
        }

        // 6. Duração da Corrida
        if (weights.rideDuration > 0) {
            val normalized = normalizeDuration(ride.rideDuration)
            criteriaScores["rideDuration"] = CriteriaScore(
                name = "Duração",
                rawValue = ride.rideDuration,
                normalizedScore = normalized,
                weight = weights.rideDuration,
                weightedScore = normalized * weights.rideDuration / 100.0,
                level = getLevel(normalized)
            )
            if (driverThresholds.isDurationActive() && ride.rideDuration > driverThresholds.maxDuration) {
                val penalty = weights.rideDuration * 0.5
                violations.add(ThresholdViolation(
                    criteriaName = "Duração",
                    currentValue = ride.rideDuration,
                    minimumRequired = driverThresholds.maxDuration,
                    penaltyApplied = penalty
                ))
            }
        }

        // 7. Distância até Embarque
        if (weights.pickupDistance > 0) {
            val normalized = normalizePickupDistance(ride.pickupDistance)
            criteriaScores["pickupDistance"] = CriteriaScore(
                name = "Dist. Embarque",
                rawValue = ride.pickupDistance,
                normalizedScore = normalized,
                weight = weights.pickupDistance,
                weightedScore = normalized * weights.pickupDistance / 100.0,
                level = getLevel(normalized)
            )
            if (driverThresholds.isPickupDistanceActive() && ride.pickupDistance > driverThresholds.maxPickupDistance) {
                val penalty = weights.pickupDistance * 0.6
                violations.add(ThresholdViolation(
                    criteriaName = "Dist. Embarque",
                    currentValue = ride.pickupDistance,
                    minimumRequired = driverThresholds.maxPickupDistance,
                    penaltyApplied = penalty
                ))
            }
        }

        // 8. Avaliação de Usuários (rating do usuário no app)
        if (weights.userRating > 0) {
            val normalized = normalizeRating(ride.passengerRating) // Usa mesmo campo por ora
            criteriaScores["userRating"] = CriteriaScore(
                name = "Aval. Usuário",
                rawValue = ride.passengerRating,
                normalizedScore = normalized,
                weight = weights.userRating,
                weightedScore = normalized * weights.userRating / 100.0,
                level = getLevel(normalized)
            )
            if (driverThresholds.isUserRatingActive() && ride.passengerRating < driverThresholds.minUserRating) {
                val penalty = weights.userRating * 0.6
                violations.add(ThresholdViolation(
                    criteriaName = "Aval. Usuário",
                    currentValue = ride.passengerRating,
                    minimumRequired = driverThresholds.minUserRating,
                    penaltyApplied = penalty
                ))
            }
        }

        // 9. Distância até Desembarque
        if (weights.dropoffDistance > 0) {
            val normalized = normalizeDropoffDistance(ride.dropoffDistance)
            criteriaScores["dropoffDistance"] = CriteriaScore(
                name = "Dist. Destino",
                rawValue = ride.dropoffDistance,
                normalizedScore = normalized,
                weight = weights.dropoffDistance,
                weightedScore = normalized * weights.dropoffDistance / 100.0,
                level = getLevel(normalized)
            )
            if (driverThresholds.isDropoffDistanceActive() && ride.dropoffDistance < driverThresholds.minDropoffDistance) {
                val penalty = weights.dropoffDistance * 0.5
                violations.add(ThresholdViolation(
                    criteriaName = "Dist. Destino",
                    currentValue = ride.dropoffDistance,
                    minimumRequired = driverThresholds.minDropoffDistance,
                    penaltyApplied = penalty
                ))
            }
        }

        // Calcular score total
        var totalScore = criteriaScores.values.sumOf { it.weightedScore }

        // Aplicar penalidades de thresholds violados
        val thresholdPenalty = violations.sumOf { it.penaltyApplied }
        totalScore -= thresholdPenalty

        // Aplicar penalidade de bairros bloqueados
        val pickupPenalty = blockedNeighborhoods
            .filter { it.type == NeighborhoodType.PICKUP && it.name.equals(ride.pickupNeighborhood, ignoreCase = true) }
            .maxOfOrNull { it.penaltyWeight } ?: 0

        val dropoffPenalty = blockedNeighborhoods
            .filter { it.type == NeighborhoodType.DROPOFF && it.name.equals(ride.dropoffNeighborhood, ignoreCase = true) }
            .maxOfOrNull { it.penaltyWeight } ?: 0

        totalScore -= (pickupPenalty + dropoffPenalty)
        totalScore = totalScore.coerceIn(0.0, 100.0)

        return RideScore(
            totalScore = totalScore,
            criteriaScores = criteriaScores,
            thresholdViolations = violations
        )
    }

    // --- Normalização dos critérios (0-100) ---

    private fun normalizeValuePerKm(value: Double): Double {
        return ((value - thresholds.minValuePerKm) / (thresholds.maxValuePerKm - thresholds.minValuePerKm) * 100)
            .coerceIn(0.0, 100.0)
    }

    private fun normalizeValuePerHour(value: Double): Double {
        return ((value - thresholds.minValuePerHour) / (thresholds.maxValuePerHour - thresholds.minValuePerHour) * 100)
            .coerceIn(0.0, 100.0)
    }

    private fun normalizeStops(stops: Int): Double {
        return when (stops) {
            0 -> 100.0
            1 -> 50.0
            else -> 0.0
        }
    }

    private fun normalizeRating(rating: Double): Double {
        return when {
            rating >= 5.0 -> 100.0
            rating >= 4.0 -> ((rating - 4.0) / 1.0 * 100.0)
            else -> 0.0
        }
    }

    private fun normalizeRideValue(value: Double): Double {
        return ((value - thresholds.minRideValue) / (thresholds.maxRideValue - thresholds.minRideValue) * 100)
            .coerceIn(0.0, 100.0)
    }

    private fun normalizeDuration(minutes: Double): Double {
        return ((thresholds.maxDuration - minutes) / (thresholds.maxDuration - thresholds.minDuration) * 100)
            .coerceIn(0.0, 100.0)
    }

    private fun normalizePickupDistance(km: Double): Double {
        return ((thresholds.maxPickupDistance - km) / (thresholds.maxPickupDistance - thresholds.minPickupDistance) * 100)
            .coerceIn(0.0, 100.0)
    }

    private fun normalizeDropoffDistance(km: Double): Double {
        return ((km - thresholds.minDropoffDistance) / (thresholds.maxDropoffDistance - thresholds.minDropoffDistance) * 100)
            .coerceIn(0.0, 100.0)
    }

    private fun getLevel(score: Double): ScoreLevel {
        return when {
            score >= 70 -> ScoreLevel.GREEN
            score >= 50 -> ScoreLevel.YELLOW
            score >= 30 -> ScoreLevel.ORANGE
            else -> ScoreLevel.RED
        }
    }
}

/**
 * Thresholds para normalização dos critérios (escala do sistema)
 */
data class ScoringThresholds(
    val minValuePerKm: Double = 0.50,
    val maxValuePerKm: Double = 2.50,
    val minValuePerHour: Double = 10.0,
    val maxValuePerHour: Double = 40.0,
    val minRideValue: Double = 5.0,
    val maxRideValue: Double = 50.0,
    val minDuration: Double = 10.0,
    val maxDuration: Double = 60.0,
    val minPickupDistance: Double = 0.5,
    val maxPickupDistance: Double = 5.0,
    val minDropoffDistance: Double = 2.0,
    val maxDropoffDistance: Double = 20.0
)
