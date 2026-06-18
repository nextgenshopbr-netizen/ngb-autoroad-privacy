package com.ngbautoroad.domain

import com.ngbautoroad.data.model.*

// ============================================================================
// ARQUIVO: RideScorer.kt
// LOCALIZAÇÃO: domain/RideScorer.kt
// RESPONSABILIDADE: Algoritmo central de pontuação de corridas (0-100 pontos)
// DEPENDÊNCIAS:
//   - data/model/RideData.kt → RideData, CriteriaWeights, DriverThresholds,
//     BlockedNeighborhood, NeighborhoodType, RideScore, CriteriaScore,
//     ThresholdViolation, ScoreLevel
// DEPENDENTES (quem usa este arquivo):
//   - service/OverlayService.kt → calcula score em tempo real para overlay
//   - ui/card/CardTab.kt → PreviewDialog usa para simulação
//   - ui/history/HistoryTab.kt → exibe scoreBreakdown salvo
//   - ui/editor/CardEditorActivity.kt → RealCardPreview
// LÓGICA PRINCIPAL:
//   1. Recebe RideData (dados da corrida) + configurações do motorista
//   2. Para cada critério com peso > 0, normaliza o valor bruto para 0-100
//   3. Multiplica pelo peso e soma → score bruto
//   4. Aplica penalidades por violação de thresholds do motorista
//   5. Aplica penalidades por bairros bloqueados
//   6. Resultado final: 0-100 (clamped)
// PROTEÇÕES:
//   - Dados parciais: pula critérios sem dados (ex: dropoffDistance=0 → pula R$/km)
//   - Normalização adaptativa: se critérios foram pulados, escala pelo peso efetivo
//   - Rating=0: plataforma não forneceu → pula critério de avaliação
// ============================================================================

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

    // ========================================================================
    // BLOCO: calculateScore — Método principal de cálculo
    // LINHAS: ~50-235
    // LÓGICA: Itera 8 critérios, normaliza, aplica pesos, penalidades e retorna RideScore
    // DEPENDÊNCIA: normalizeXxx() (abaixo), getLevel()
    // ========================================================================
    fun calculateScore(ride: RideData): RideScore {
        val criteriaScores = mutableMapOf<String, CriteriaScore>()
        val violations = mutableListOf<ThresholdViolation>()

        // --- CRITÉRIO 1: Valor por KM ---
        // Pula se dropoffDistance=0 (OCR não capturou distância → R$/km seria infinito)
        // Fórmula: ride.rideValue / ride.dropoffDistance
        // Normalização: linear entre ScoringThresholds.minValuePerKm e maxValuePerKm
        if (weights.valuePerKm > 0 && ride.dropoffDistance > 0) {
            val normalized = normalizeValuePerKm(ride.valuePerKm)
            criteriaScores["valuePerKm"] = CriteriaScore(
                name = "Valor/KM",
                rawValue = ride.valuePerKm,
                normalizedScore = normalized,
                weight = weights.valuePerKm,
                weightedScore = normalized * weights.valuePerKm / 100.0,
                level = getLevel(normalized)
            )
            // Penalidade: se abaixo do mínimo desejado pelo motorista
            if (driverThresholds.isValuePerKmActive() && ride.valuePerKm < driverThresholds.minValuePerKm) {
                val penalty = weights.valuePerKm * 0.5
                violations.add(ThresholdViolation(
                    criteriaName = "Valor/KM",
                    currentValue = ride.valuePerKm,
                    minimumRequired = driverThresholds.minValuePerKm,
                    penaltyApplied = penalty
                ))
            }
        }

        // --- CRITÉRIO 2: Valor por Hora ---
        // Pula se rideDuration=0 (OCR não capturou duração → R$/h seria infinito)
        // Fórmula: ride.rideValue / (ride.rideDuration / 60)
        // Normalização: linear entre ScoringThresholds.minValuePerHour e maxValuePerHour
        if (weights.valuePerHour > 0 && ride.rideDuration > 0) {
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

        // --- CRITÉRIO 3: Paradas Intermediárias ---
        // Sempre disponível (default=0 se não informado)
        // Normalização: 0 paradas=100, 1 parada=50, 2+=0
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

        // --- CRITÉRIO 4: Avaliação do Passageiro ---
        // Pula se rating=0.0 (plataforma não forneceu, ex: inDrive)
        // Normalização: <4.0=0, 4.0-5.0=linear 0-100
        if (weights.passengerRating > 0 && ride.passengerRating > 0.0) {
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

        // --- CRITÉRIO 5: Valor da Corrida (absoluto) ---
        // Sempre disponível (campo obrigatório no OCR)
        // Normalização: linear entre ScoringThresholds.minRideValue e maxRideValue
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

        // --- CRITÉRIO 6: Duração da Corrida ---
        // Normalização INVERSA: menos tempo = melhor score
        // Linear entre ScoringThresholds.minDuration e maxDuration
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

        // --- CRITÉRIO 7: Distância até Embarque ---
        // Normalização INVERSA: menos distância = melhor score
        // Linear entre ScoringThresholds.minPickupDistance e maxPickupDistance
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

        // --- CRITÉRIO 8: Distância até Desembarque ---
        // Normalização DIRETA: mais distância = melhor score (corrida mais longa = mais $)
        // Linear entre ScoringThresholds.minDropoffDistance e maxDropoffDistance
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

        // ====================================================================
        // BLOCO: Cálculo do Score Total
        // LÓGICA:
        //   1. Soma pesos efetivos (critérios que foram calculados)
        //   2. Se peso efetivo < peso total configurado → normaliza proporcionalmente
        //      (evita penalizar motorista por dados que o OCR não capturou)
        //   3. Subtrai penalidades de thresholds violados
        //   4. Subtrai penalidades de bairros bloqueados
        //   5. Clamp final: 0-100
        // ====================================================================
        val effectiveWeight = criteriaScores.values.sumOf { it.weight }
        var totalScore = if (effectiveWeight > 0 && effectiveWeight < weights.totalUsed) {
            // Escalar proporcionalmente para compensar critérios sem dados
            criteriaScores.values.sumOf { it.weightedScore } * (weights.totalUsed.toDouble() / effectiveWeight)
        } else {
            criteriaScores.values.sumOf { it.weightedScore }
        }

        // Subtrair penalidades de thresholds violados
        val thresholdPenalty = violations.sumOf { it.penaltyApplied }
        totalScore -= thresholdPenalty

        // Subtrair penalidades de bairros bloqueados (configurados no ZoneMapActivity)
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

    // ========================================================================
    // BLOCO: Funções de Normalização (privadas)
    // LÓGICA: Cada função mapeia um valor bruto para 0-100 usando interpolação linear
    // DEPENDÊNCIA: ScoringThresholds (valores min/max configuráveis)
    // NOTA: Critérios "inversos" (duração, pickup) usam (max - valor) no numerador
    // ========================================================================

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
            0 -> 100.0   // Sem paradas = score máximo
            1 -> 50.0    // 1 parada = metade
            else -> 0.0  // 2+ paradas = score zero
        }
    }

    private fun normalizeRating(rating: Double): Double {
        return when {
            rating >= 5.0 -> 100.0                     // 5 estrelas = perfeito
            rating >= 4.0 -> ((rating - 4.0) / 1.0 * 100.0)  // 4.0-4.99 = linear
            else -> 0.0                                 // <4.0 = score zero
        }
    }

    private fun normalizeRideValue(value: Double): Double {
        return ((value - thresholds.minRideValue) / (thresholds.maxRideValue - thresholds.minRideValue) * 100)
            .coerceIn(0.0, 100.0)
    }

    // INVERSO: menos tempo = melhor
    private fun normalizeDuration(minutes: Double): Double {
        return ((thresholds.maxDuration - minutes) / (thresholds.maxDuration - thresholds.minDuration) * 100)
            .coerceIn(0.0, 100.0)
    }

    // INVERSO: menos distância até embarque = melhor
    private fun normalizePickupDistance(km: Double): Double {
        return ((thresholds.maxPickupDistance - km) / (thresholds.maxPickupDistance - thresholds.minPickupDistance) * 100)
            .coerceIn(0.0, 100.0)
    }

    // DIRETO: mais distância até destino = melhor (corrida mais longa)
    private fun normalizeDropoffDistance(km: Double): Double {
        return ((km - thresholds.minDropoffDistance) / (thresholds.maxDropoffDistance - thresholds.minDropoffDistance) * 100)
            .coerceIn(0.0, 100.0)
    }

    // ========================================================================
    // BLOCO: Classificação de nível por cor
    // LÓGICA: Mapeia score normalizado (0-100) para 4 níveis visuais
    // DEPENDENTE: OverlayCard.kt usa ScoreLevel para colorir o card
    // ========================================================================
    private fun getLevel(score: Double): ScoreLevel {
        return when {
            score >= 70 -> ScoreLevel.GREEN   // Excelente
            score >= 50 -> ScoreLevel.YELLOW  // Bom
            score >= 30 -> ScoreLevel.ORANGE  // Regular
            else -> ScoreLevel.RED            // Ruim
        }
    }
}

// ============================================================================
// BLOCO: ScoringThresholds — Valores de referência para normalização
// LÓGICA: Define os limites min/max usados na interpolação linear
// NOTA: Valores padrão baseados em médias de mercado brasileiro (2024)
// DEPENDENTE: RideScorer.normalizeXxx() usa estes valores
// ============================================================================
data class ScoringThresholds(
    val minValuePerKm: Double = 0.50,     // R$0,50/km = péssimo
    val maxValuePerKm: Double = 2.50,     // R$2,50/km = excelente
    val minValuePerHour: Double = 10.0,   // R$10/h = péssimo
    val maxValuePerHour: Double = 40.0,   // R$40/h = excelente
    val minRideValue: Double = 5.0,       // R$5 = corrida mínima
    val maxRideValue: Double = 50.0,      // R$50 = corrida premium
    val minDuration: Double = 10.0,       // 10min = corrida curta ideal
    val maxDuration: Double = 60.0,       // 60min = corrida muito longa
    val minPickupDistance: Double = 0.5,  // 500m = embarque próximo
    val maxPickupDistance: Double = 5.0,  // 5km = embarque longe demais
    val minDropoffDistance: Double = 2.0, // 2km = corrida curta
    val maxDropoffDistance: Double = 20.0 // 20km = corrida longa ideal
)
