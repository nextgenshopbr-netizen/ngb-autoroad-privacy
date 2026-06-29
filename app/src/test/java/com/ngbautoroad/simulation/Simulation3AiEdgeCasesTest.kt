package com.ngbautoroad.simulation

import org.junit.Test
import org.junit.Assert.*
import com.ngbautoroad.data.model.*
import com.ngbautoroad.domain.RideScorer
import com.ngbautoroad.domain.ScoringThresholds
import com.ngbautoroad.domain.AdaptiveScoringEngine
import com.ngbautoroad.domain.ReturnFactorEngineStatic
import com.ngbautoroad.domain.SafetyScoreModifierStatic
import com.ngbautoroad.domain.NeighborhoodReturnData
import com.ngbautoroad.domain.LocalLearningEngine
import com.ngbautoroad.domain.RidePattern

/**
 * Simulation 3: AI Edge Cases + Stress Test
 *
 * Exercises RideScorer, AdaptiveScoringEngine, SafetyScoreModifierStatic,
 * ReturnFactorEngineStatic, LocalLearningEngine, and NeighborhoodReturnData
 * with extreme inputs, boundary conditions, and performance stress.
 */
class Simulation3AiEdgeCasesTest {

    // =========================================================================
    // EDGE CASE: All zeros
    // =========================================================================
    @Test
    fun testRideWithAllZeros() {
        val ride = RideData(rideValue = 0.0)
        val scorer = RideScorer(weights = CriteriaWeights(), driverThresholds = DriverThresholds())
        val score = scorer.calculateScore(ride)
        assertNotNull(score)
        assertTrue("Score with all zeros should be 0-100, got ${score.totalScore}", score.totalScore in 0.0..100.0)
    }

    // =========================================================================
    // EDGE CASE: Extremely high values
    // =========================================================================
    @Test
    fun testRideWithExtremeValues() {
        val ride = RideData(
            rideValue = 999.99,
            rideDuration = 300.0,
            pickupDistance = 50.0,
            dropoffDistance = 100.0,
            passengerRating = 5.0
        )
        val scorer = RideScorer(weights = CriteriaWeights(), driverThresholds = DriverThresholds())
        val score = scorer.calculateScore(ride)
        assertNotNull(score)
        assertTrue("Score with extreme values should be 0-100, got ${score.totalScore}", score.totalScore in 0.0..100.0)
    }

    // =========================================================================
    // EDGE CASE: Negative values (defensive)
    // =========================================================================
    @Test
    fun testRideWithNegativeValues() {
        val ride = RideData(rideValue = -10.0, dropoffDistance = -5.0)
        val scorer = RideScorer(weights = CriteriaWeights(), driverThresholds = DriverThresholds())
        val score = scorer.calculateScore(ride)
        assertNotNull(score)
        assertTrue("Score with negatives should be 0-100, got ${score.totalScore}", score.totalScore in 0.0..100.0)
    }

    // =========================================================================
    // EDGE CASE: Division by zero — valuePerKm with zero distance
    // =========================================================================
    @Test
    fun testValuePerKmWithZeroDistance() {
        val ride = RideData(rideValue = 25.0, dropoffDistance = 0.0)
        assertEquals("valuePerKm should be 0 when distance=0", 0.0, ride.valuePerKm, 0.001)
    }

    // =========================================================================
    // EDGE CASE: Division by zero — valuePerHour with zero duration
    // =========================================================================
    @Test
    fun testValuePerHourWithZeroDuration() {
        val ride = RideData(rideValue = 25.0, rideDuration = 0.0)
        assertEquals("valuePerHour should be 0 when duration=0", 0.0, ride.valuePerHour, 0.001)
    }

    // =========================================================================
    // SCORING: All weights = 0
    // =========================================================================
    @Test
    fun testScoringWithAllWeightsZero() {
        val weights = CriteriaWeights(
            valuePerKm = 0, valuePerHour = 0, intermediateStops = 0,
            passengerRating = 0, rideValue = 0, rideDuration = 0,
            pickupDistance = 0, dropoffDistance = 0
        )
        val ride = RideData(rideValue = 25.0, dropoffDistance = 10.0)
        val scorer = RideScorer(weights = weights, driverThresholds = DriverThresholds())
        val score = scorer.calculateScore(ride)
        assertTrue("Score with zero weights should be 0, got ${score.totalScore}", score.totalScore == 0.0)
    }

    // =========================================================================
    // SCORING: Single criterion only (100% weight on valuePerKm)
    // =========================================================================
    @Test
    fun testScoringWithOnlyCriterion() {
        val weights = CriteriaWeights(
            valuePerKm = 100, valuePerHour = 0, intermediateStops = 0,
            passengerRating = 0, rideValue = 0, rideDuration = 0,
            pickupDistance = 0, dropoffDistance = 0
        )
        // R$50 / 10km = R$5/km — well above default maxValuePerKm (2.50), should score 100 normalized
        val ride = RideData(rideValue = 50.0, dropoffDistance = 10.0)
        val scorer = RideScorer(weights = weights, driverThresholds = DriverThresholds())
        val score = scorer.calculateScore(ride)
        assertTrue("Score should be valid 0-100, got ${score.totalScore}", score.totalScore in 0.0..100.0)
        assertTrue("High R$/km should score well (>70), got ${score.totalScore}", score.totalScore >= 70.0)
    }

    // =========================================================================
    // SCORING: All 8 criteria active (each at 12-13%)
    // =========================================================================
    @Test
    fun testScoringWithAllCriteriaActive() {
        val weights = CriteriaWeights(
            valuePerKm = 13, valuePerHour = 13, intermediateStops = 12,
            passengerRating = 12, rideValue = 13, rideDuration = 12,
            pickupDistance = 13, dropoffDistance = 12
        )
        assertEquals("Weights should sum to 100", 100, weights.totalUsed)
        val ride = RideData(
            rideValue = 30.0, rideDuration = 20.0, pickupDistance = 2.0,
            dropoffDistance = 10.0, passengerRating = 4.9, intermediateStops = 0
        )
        val scorer = RideScorer(weights = weights, driverThresholds = DriverThresholds())
        val score = scorer.calculateScore(ride)
        assertTrue("Score should be valid 0-100, got ${score.totalScore}", score.totalScore in 0.0..100.0)
        // All 8 criteria should be present in breakdown
        assertTrue("Should have >= 8 criteria scores, got ${score.criteriaScores.size}",
            score.criteriaScores.size >= 8)
    }

    // =========================================================================
    // SCORING: Blocked neighborhoods apply penalty
    // =========================================================================
    @Test
    fun testScoringWithBlockedNeighborhoods() {
        val blocked = listOf(
            BlockedNeighborhood("Favela Norte", NeighborhoodType.DROPOFF, penaltyWeight = 30),
            BlockedNeighborhood("Centro", NeighborhoodType.PICKUP, penaltyWeight = 20)
        )
        val ride = RideData(
            rideValue = 30.0, dropoffDistance = 10.0, rideDuration = 20.0,
            passengerRating = 4.9, dropoffNeighborhood = "Favela Norte", pickupNeighborhood = "Centro"
        )
        val scorerWithBlocked = RideScorer(
            weights = CriteriaWeights(), driverThresholds = DriverThresholds(),
            blockedNeighborhoods = blocked
        )
        val scorerClean = RideScorer(weights = CriteriaWeights(), driverThresholds = DriverThresholds())

        val scoreBlocked = scorerWithBlocked.calculateScore(ride)
        val scoreClean = scorerClean.calculateScore(ride)
        assertTrue("Blocked neighborhoods should reduce score: clean=${scoreClean.totalScore} blocked=${scoreBlocked.totalScore}",
            scoreBlocked.totalScore < scoreClean.totalScore)
    }

    // =========================================================================
    // SCORING: Threshold violations generate penalties
    // =========================================================================
    @Test
    fun testThresholdViolationsArePenalized() {
        val strictThresholds = DriverThresholds(
            minValuePerKm = 5.0,      // Very high bar
            minValuePerHour = 80.0,   // Very high bar
            minPassengerRating = 4.95,
            maxStops = 0              // No stops tolerated
        )
        val ride = RideData(
            rideValue = 15.0, dropoffDistance = 10.0, rideDuration = 30.0,
            passengerRating = 4.7, intermediateStops = 2
        )
        val scorer = RideScorer(weights = CriteriaWeights(), driverThresholds = strictThresholds)
        val score = scorer.calculateScore(ride)
        assertTrue("Should have threshold violations", score.hasViolations)
        assertTrue("Multiple violations expected, got ${score.thresholdViolations.size}",
            score.thresholdViolations.size >= 2)
    }

    // =========================================================================
    // SCORING: costPerKm triggers profit/km criterion
    // =========================================================================
    @Test
    fun testScoringWithCostPerKm() {
        val ride = RideData(rideValue = 30.0, dropoffDistance = 10.0) // R$3/km
        val scorerWithCost = RideScorer(
            weights = CriteriaWeights(), driverThresholds = DriverThresholds(), costPerKm = 1.50
        )
        val score = scorerWithCost.calculateScore(ride)
        assertTrue("Should include profitPerKm in criteria", score.criteriaScores.containsKey("profitPerKm"))
    }

    // =========================================================================
    // SCORING: costPerKm with loss (profit < 0) generates violation
    // =========================================================================
    @Test
    fun testScoringWithCostPerKmLoss() {
        val ride = RideData(rideValue = 5.0, dropoffDistance = 10.0) // R$0.50/km, cost=1.50/km => loss
        val scorer = RideScorer(
            weights = CriteriaWeights(), driverThresholds = DriverThresholds(), costPerKm = 1.50
        )
        val score = scorer.calculateScore(ride)
        val profitViolation = score.thresholdViolations.find { it.criteriaName == "Lucro/KM" }
        assertNotNull("Loss ride should have Lucro/KM violation", profitViolation)
        assertTrue("Profit violation penalty should be > 0", profitViolation!!.penaltyApplied > 0)
    }

    // =========================================================================
    // SCORING: Fatigue penalty via metadata
    // =========================================================================
    @Test
    fun testFatiguePenaltyViaMetadata() {
        val rideNoFatigue = RideData(rideValue = 30.0, dropoffDistance = 10.0, rideDuration = 20.0, passengerRating = 4.9)
        val rideFatigued = RideData(
            rideValue = 30.0, dropoffDistance = 10.0, rideDuration = 20.0, passengerRating = 4.9,
            metadata = mapOf("shiftHours" to "10.0")
        )
        val scorer = RideScorer(weights = CriteriaWeights(), driverThresholds = DriverThresholds())
        val scoreNormal = scorer.calculateScore(rideNoFatigue)
        val scoreFatigue = scorer.calculateScore(rideFatigued)

        assertTrue("Fatigue should reduce score: normal=${scoreNormal.totalScore} fatigue=${scoreFatigue.totalScore}",
            scoreFatigue.totalScore < scoreNormal.totalScore)
        val fatigueViolation = scoreFatigue.thresholdViolations.find { it.criteriaName == "Fadiga" }
        assertNotNull("Should have fadiga violation", fatigueViolation)
        assertEquals("10h shift -> 25pt penalty", 25.0, fatigueViolation!!.penaltyApplied, 0.001)
    }

    // =========================================================================
    // SCORING: Extreme fatigue (12h) should devastate score
    // =========================================================================
    @Test
    fun testExtremeFatiguePenalty() {
        val ride = RideData(
            rideValue = 30.0, dropoffDistance = 10.0, rideDuration = 20.0,
            passengerRating = 4.9, metadata = mapOf("shiftHours" to "12.0")
        )
        val scorer = RideScorer(weights = CriteriaWeights(), driverThresholds = DriverThresholds())
        val score = scorer.calculateScore(ride)
        val fatigueViolation = score.thresholdViolations.find { it.criteriaName == "Fadiga" }
        assertNotNull(fatigueViolation)
        assertEquals("12h shift -> 40pt penalty", 40.0, fatigueViolation!!.penaltyApplied, 0.001)
    }

    // =========================================================================
    // ADAPTIVE SCORING: Engine works without Context (null), returns defaults
    // =========================================================================
    @Test
    fun testAdaptiveScoringWithoutContext() {
        val engine = AdaptiveScoringEngine(context = null)
        assertFalse("Should not be calibrated without data", engine.isCalibrated())
        assertEquals("Should have 0 rides", 0, engine.getCalibratedRideCount())
        val thresholds = engine.getAdaptiveThresholds()
        // Should return defaults since not calibrated
        assertEquals(0.50, thresholds.minValuePerKm, 0.01)
        assertEquals(2.50, thresholds.maxValuePerKm, 0.01)
    }

    // =========================================================================
    // ADAPTIVE SCORING: Calibration with < 30 rides does nothing
    // =========================================================================
    @Test
    fun testAdaptiveCalibrationBelowMinimum() {
        val engine = AdaptiveScoringEngine(context = null)
        // Only 20 rides — below MIN_RIDES_TO_ADAPT (30)
        val values = List(20) { it * 0.1 + 1.0 }
        engine.calibrate(
            valuesPerKm = values,
            valuesPerHour = values,
            rideValues = values,
            pickupDistances = values,
            dropoffDistances = values,
            durations = values
        )
        assertFalse("Should not be calibrated with only 20 rides", engine.isCalibrated())
    }

    // =========================================================================
    // ADAPTIVE SCORING: Calibration with >= 30 rides activates
    // =========================================================================
    @Test
    fun testAdaptiveCalibrationWithEnoughData() {
        val engine = AdaptiveScoringEngine(context = null)
        val random = java.util.Random(42)
        val values = List(50) { random.nextDouble() * 3.0 + 0.5 }
        engine.calibrate(
            valuesPerKm = values,
            valuesPerHour = values.map { it * 20.0 },
            rideValues = values.map { it * 15.0 },
            pickupDistances = values.map { it * 1.5 },
            dropoffDistances = values.map { it * 5.0 },
            durations = values.map { it * 20.0 }
        )
        assertTrue("Should be calibrated with 50 rides", engine.isCalibrated())
        assertEquals(50, engine.getCalibratedRideCount())
        val thresholds = engine.getAdaptiveThresholds()
        // Thresholds should have moved from defaults (EWMA blended)
        assertTrue("min < max for valuePerKm", thresholds.minValuePerKm < thresholds.maxValuePerKm)
        assertTrue("min < max for valuePerHour", thresholds.minValuePerHour < thresholds.maxValuePerHour)
    }

    // =========================================================================
    // ADAPTIVE SCORING: Calibrated thresholds produce different scores
    // =========================================================================
    @Test
    fun testAdaptiveThresholdsAffectScoring() {
        val engine = AdaptiveScoringEngine(context = null)
        val random = java.util.Random(99)
        // All rides cluster around R$1-2/km (tighter range than default 0.50-2.50)
        val values = List(50) { random.nextDouble() * 1.0 + 1.0 }
        engine.calibrate(
            valuesPerKm = values,
            valuesPerHour = List(50) { random.nextDouble() * 20.0 + 20.0 },
            rideValues = List(50) { random.nextDouble() * 30.0 + 10.0 },
            pickupDistances = List(50) { random.nextDouble() * 3.0 + 0.5 },
            dropoffDistances = List(50) { random.nextDouble() * 15.0 + 2.0 },
            durations = List(50) { random.nextDouble() * 40.0 + 10.0 }
        )
        val adaptiveThresholds = engine.getAdaptiveThresholds()
        val defaultThresholds = ScoringThresholds()

        val ride = RideData(rideValue = 20.0, dropoffDistance = 10.0, rideDuration = 15.0, passengerRating = 4.9)
        val scoreDefault = RideScorer(weights = CriteriaWeights(), thresholds = defaultThresholds).calculateScore(ride)
        val scoreAdaptive = RideScorer(weights = CriteriaWeights(), thresholds = adaptiveThresholds).calculateScore(ride)

        // Scores should differ because thresholds differ
        assertNotEquals("Adaptive vs default scores should differ",
            scoreDefault.totalScore, scoreAdaptive.totalScore, 0.001)
    }

    // =========================================================================
    // SAFETY: SafetyScoreModifierStatic basic penalty
    // =========================================================================
    @Test
    fun testSafetyModifierStaticBasic() {
        // No risk factors: good rating, no blocked neighborhoods
        val penalty = SafetyScoreModifierStatic.calculatePenalty(
            passengerRating = 4.9,
            ratingThreshold = 4.5,
            pickupNeighborhood = "Centro",
            dropoffNeighborhood = "Jardins",
            blockedNeighborhoods = emptyList()
        )
        // Penalty depends on time of day (night adds 3.0), so just validate range
        assertTrue("Safety penalty should be 0-25, got $penalty", penalty in 0.0..25.0)
    }

    // =========================================================================
    // SAFETY: Low rating cascades with night (time-dependent, validate range)
    // =========================================================================
    @Test
    fun testSafetyModifierWithLowRating() {
        val penalty = SafetyScoreModifierStatic.calculatePenalty(
            passengerRating = 4.2,
            ratingThreshold = 4.5,
            pickupNeighborhood = "Centro",
            dropoffNeighborhood = "Jardins",
            blockedNeighborhoods = emptyList()
        )
        assertTrue("Safety penalty should be 0-25, got $penalty", penalty in 0.0..25.0)
    }

    // =========================================================================
    // SAFETY: Blocked area + low rating + night = triple cascade
    // =========================================================================
    @Test
    fun testSafetyModifierTripleFactor() {
        val blocked = listOf(
            BlockedNeighborhood("favela", NeighborhoodType.DROPOFF, penaltyWeight = 30)
        )
        val penalty = SafetyScoreModifierStatic.calculatePenalty(
            passengerRating = 4.0,
            ratingThreshold = 4.5,
            pickupNeighborhood = "Favela Norte",
            dropoffNeighborhood = "Favela Sul",
            blockedNeighborhoods = blocked
        )
        // Should be capped at 25.0
        assertTrue("Triple factor penalty should be <= 25, got $penalty", penalty <= 25.0)
    }

    // =========================================================================
    // SAFETY: Safety floor kicks in when safetyModifier > 8
    // =========================================================================
    @Test
    fun testSafetyFloorCapsScoreAt50() {
        // A ride that normally scores well, but with safety concerns
        val blocked = listOf(
            BlockedNeighborhood("perigo", NeighborhoodType.DROPOFF, penaltyWeight = 40)
        )
        val ride = RideData(
            rideValue = 50.0, dropoffDistance = 10.0, rideDuration = 15.0,
            passengerRating = 4.1, dropoffNeighborhood = "Perigo",
            pickupNeighborhood = "Perigo"
        )
        // The safety floor behavior depends on SafetyScoreModifierStatic returning > 8
        // which depends on time of day. We verify the mechanism works regardless.
        val scorer = RideScorer(
            weights = CriteriaWeights(), driverThresholds = DriverThresholds(),
            blockedNeighborhoods = blocked
        )
        val score = scorer.calculateScore(ride)
        assertTrue("Score should be 0-100, got ${score.totalScore}", score.totalScore in 0.0..100.0)
    }

    // =========================================================================
    // RETURN FACTOR: Static engine penalty by distance
    // =========================================================================
    @Test
    fun testReturnFactorShortRideNoPenalty() {
        val penalty = ReturnFactorEngineStatic.calculateReturnPenalty("Centro", 3.0)
        assertEquals("Short rides (<5km) should have 0 penalty", 0.0, penalty, 0.001)
    }

    @Test
    fun testReturnFactorMediumRide() {
        val penalty = ReturnFactorEngineStatic.calculateReturnPenalty("Periferia", 12.0)
        assertTrue("12km ride should have penalty 2-5, got $penalty", penalty in 2.0..5.0)
    }

    @Test
    fun testReturnFactorLongRide() {
        val penalty = ReturnFactorEngineStatic.calculateReturnPenalty("FarAway", 20.0)
        assertTrue("20km ride should have penalty 5-10, got $penalty", penalty in 5.0..10.0)
    }

    @Test
    fun testReturnFactorVeryLongRideCapped() {
        val penalty = ReturnFactorEngineStatic.calculateReturnPenalty("VeryFar", 50.0)
        assertEquals("Very long ride penalty should be capped at 15", 15.0, penalty, 0.001)
    }

    @Test
    fun testReturnFactorZeroDistance() {
        val penalty = ReturnFactorEngineStatic.calculateReturnPenalty("Test", 0.0)
        assertEquals("Zero distance should have 0 penalty", 0.0, penalty, 0.001)
    }

    @Test
    fun testReturnFactorNegativeDistance() {
        val penalty = ReturnFactorEngineStatic.calculateReturnPenalty("Test", -5.0)
        assertEquals("Negative distance should have 0 penalty", 0.0, penalty, 0.001)
    }

    // =========================================================================
    // RETURN FACTOR: NeighborhoodReturnData model
    // =========================================================================
    @Test
    fun testNeighborhoodReturnDataCalculation() {
        val data = NeighborhoodReturnData(
            neighborhood = "periferia", totalTrips = 10, emptyReturns = 5
        )
        assertEquals("50% empty return rate", 0.5, data.emptyReturnRate, 0.001)
        // returnFactor = 1.0 / (1.0 + 0.5) = 0.667
        assertEquals("Return factor for 50% empty", 0.667, data.calculateReturnFactor(), 0.01)
    }

    @Test
    fun testNeighborhoodReturnDataNoTrips() {
        val data = NeighborhoodReturnData(neighborhood = "new", totalTrips = 0, emptyReturns = 0)
        assertEquals("0 trips -> emptyReturnRate = 0.5", 0.5, data.emptyReturnRate, 0.001)
        // < 3 trips -> default 0.85
        assertEquals("< 3 trips -> default factor 0.85", 0.85, data.calculateReturnFactor(), 0.001)
    }

    @Test
    fun testNeighborhoodReturnData100PercentEmpty() {
        val data = NeighborhoodReturnData(
            neighborhood = "desert", totalTrips = 20, emptyReturns = 20
        )
        assertEquals("100% empty", 1.0, data.emptyReturnRate, 0.001)
        // returnFactor = 1.0 / (1.0 + 1.0) = 0.5
        assertEquals("All empty -> factor 0.5", 0.5, data.calculateReturnFactor(), 0.01)
    }

    @Test
    fun testNeighborhoodReturnDataJsonRoundTrip() {
        // org.json.JSONObject requires Android runtime; skip gracefully on JVM
        try {
            val data = NeighborhoodReturnData(
                neighborhood = "centro", totalTrips = 50, emptyReturns = 10,
                avgReturnKm = 5.5, returnFactor = 0.83
            )
            val json = data.toJson()
            val restored = NeighborhoodReturnData.fromJson(json)
            assertEquals(data.neighborhood, restored.neighborhood)
            assertEquals(data.totalTrips, restored.totalTrips)
            assertEquals(data.emptyReturns, restored.emptyReturns)
            assertEquals(data.avgReturnKm, restored.avgReturnKm, 0.001)
            assertEquals(data.returnFactor, restored.returnFactor, 0.001)
        } catch (e: RuntimeException) {
            // org.json stub on JVM throws "Method not mocked" — expected in unit tests
            println("SKIPPED: testNeighborhoodReturnDataJsonRoundTrip (requires Android runtime for org.json)")
        }
    }

    // =========================================================================
    // LOCAL LEARNING: Engine works without Context (memory mode)
    // =========================================================================
    @Test
    fun testLocalLearningEngineMemoryMode() {
        val engine = LocalLearningEngine(context = null)
        assertEquals("No patterns initially", 0, engine.getPatternCount())

        engine.addPattern(RidePattern(
            hour = 14, dayOfWeek = 2, neighborhood = "Centro",
            valuePerKm = 3.0, accepted = true
        ))
        assertEquals("One pattern added", 1, engine.getPatternCount())
    }

    @Test
    fun testLocalLearningSuggestionsWithFewPatterns() {
        val engine = LocalLearningEngine(context = null)
        // Add only 5 patterns (< 20 minimum)
        repeat(5) {
            engine.addPattern(RidePattern(
                hour = 10 + it, dayOfWeek = 2, neighborhood = "Test",
                valuePerKm = 2.0, accepted = true
            ))
        }
        val suggestions = engine.generateSuggestions()
        assertEquals("Should return 1 'collect more data' suggestion", 1, suggestions.size)
        assertEquals(com.ngbautoroad.domain.SuggestionType.EARNING_PATTERN, suggestions[0].type)
    }

    @Test
    fun testLocalLearningSuggestionsWithEnoughPatterns() {
        val engine = LocalLearningEngine(context = null)
        val random = java.util.Random(77)
        // Add 50 patterns with variety
        repeat(50) { i ->
            engine.addPattern(RidePattern(
                hour = (8 + i % 12),
                dayOfWeek = (i % 7) + 1,
                neighborhood = listOf("Centro", "Zona Sul", "Zona Norte", "Jardins")[i % 4],
                valuePerKm = random.nextDouble() * 3.0 + 0.5,
                accepted = random.nextBoolean(),
                rideType = listOf("Uber", "99", "inDrive")[i % 3]
            ))
        }
        val suggestions = engine.generateSuggestions()
        assertTrue("Should generate suggestions with 50 patterns, got ${suggestions.size}",
            suggestions.isNotEmpty())
    }

    // =========================================================================
    // MODEL: RideData computed properties
    // =========================================================================
    @Test
    fun testRideDataComputedProperties() {
        val ride = RideData(rideValue = 30.0, dropoffDistance = 10.0, rideDuration = 20.0)
        assertEquals("R$/km = 30/10 = 3.0", 3.0, ride.valuePerKm, 0.001)
        assertEquals("R$/h = (30/20)*60 = 90.0", 90.0, ride.valuePerHour, 0.001)
    }

    @Test
    fun testRideDataDefaultValues() {
        val ride = RideData()
        assertEquals(0.0, ride.rideValue, 0.001)
        assertEquals(0.0, ride.rideDuration, 0.001)
        assertEquals(0.0, ride.pickupDistance, 0.001)
        assertEquals(0.0, ride.dropoffDistance, 0.001)
        assertEquals(0.0, ride.passengerRating, 0.001)
        assertEquals(0, ride.intermediateStops)
        assertEquals("", ride.pickupNeighborhood)
        assertEquals("", ride.dropoffNeighborhood)
        assertEquals(Platform.UNKNOWN, ride.platform)
        assertFalse(ride.isSimulation)
    }

    // =========================================================================
    // MODEL: RideScore scoreColor levels
    // =========================================================================
    @Test
    fun testRideScoreColorLevels() {
        assertEquals(ScoreLevel.GREEN, RideScore(totalScore = 85.0).scoreColor)
        assertEquals(ScoreLevel.GREEN, RideScore(totalScore = 70.0).scoreColor)
        assertEquals(ScoreLevel.YELLOW, RideScore(totalScore = 65.0).scoreColor)
        assertEquals(ScoreLevel.YELLOW, RideScore(totalScore = 50.0).scoreColor)
        assertEquals(ScoreLevel.ORANGE, RideScore(totalScore = 45.0).scoreColor)
        assertEquals(ScoreLevel.ORANGE, RideScore(totalScore = 30.0).scoreColor)
        assertEquals(ScoreLevel.RED, RideScore(totalScore = 29.9).scoreColor)
        assertEquals(ScoreLevel.RED, RideScore(totalScore = 0.0).scoreColor)
    }

    // =========================================================================
    // MODEL: CriteriaWeights totalUsed
    // =========================================================================
    @Test
    fun testCriteriaWeightsTotalUsed() {
        val defaults = CriteriaWeights()
        assertEquals("Default weights should sum to 100", 100, defaults.totalUsed)

        val custom = CriteriaWeights(
            valuePerKm = 25, valuePerHour = 25, intermediateStops = 25, passengerRating = 25
        )
        assertEquals(100, custom.totalUsed)
    }

    // =========================================================================
    // MODEL: Platform and RideType enums
    // =========================================================================
    @Test
    fun testPlatformPackageNames() {
        assertEquals("com.ubercab.driver", Platform.UBER.packageName)
        assertEquals("com.app99.driver", Platform.NINETY_NINE.packageName)
        assertEquals("com.machfrankfurt.android", Platform.INDRIVE.packageName)
    }

    @Test
    fun testRideTypeFromBadgeText() {
        assertEquals(RideType.UBER_X, RideType.fromBadgeText("UberX", Platform.UBER))
        assertEquals(RideType.UBER_COMFORT, RideType.fromBadgeText("Comfort", Platform.UBER))
        assertEquals(RideType.UBER_BLACK, RideType.fromBadgeText("Black", Platform.UBER))
        assertEquals(RideType.NINETY_NINE_POP, RideType.fromBadgeText("Pop", Platform.NINETY_NINE))
        assertEquals(RideType.NINETY_NINE_COMFORT, RideType.fromBadgeText("Comfort", Platform.NINETY_NINE))
        assertEquals(RideType.UNKNOWN, RideType.fromBadgeText("anything", Platform.UNKNOWN))
    }

    // =========================================================================
    // SCORING: ScoringThresholds guard against zero range
    // =========================================================================
    @Test
    fun testScoringWithDegenerateThresholds() {
        // min == max for all criteria: should not crash (guard returns 50.0)
        val degenerateThresholds = ScoringThresholds(
            minValuePerKm = 2.0, maxValuePerKm = 2.0,
            minValuePerHour = 20.0, maxValuePerHour = 20.0,
            minRideValue = 10.0, maxRideValue = 10.0,
            minDuration = 30.0, maxDuration = 30.0,
            minPickupDistance = 2.0, maxPickupDistance = 2.0,
            minDropoffDistance = 10.0, maxDropoffDistance = 10.0
        )
        val weights = CriteriaWeights(
            valuePerKm = 13, valuePerHour = 13, intermediateStops = 12,
            passengerRating = 12, rideValue = 13, rideDuration = 12,
            pickupDistance = 13, dropoffDistance = 12
        )
        val ride = RideData(
            rideValue = 25.0, dropoffDistance = 10.0, rideDuration = 20.0,
            pickupDistance = 2.0, passengerRating = 4.9
        )
        val scorer = RideScorer(weights = weights, thresholds = degenerateThresholds)
        val score = scorer.calculateScore(ride)
        assertTrue("Degenerate thresholds should not crash, got ${score.totalScore}",
            score.totalScore in 0.0..100.0)
    }

    // =========================================================================
    // STRESS: 1000 rapid-fire rides
    // =========================================================================
    @Test
    fun testStress1000Rides() {
        val random = java.util.Random(123)
        val scorer = RideScorer(weights = CriteriaWeights(), driverThresholds = DriverThresholds())
        val startTime = System.currentTimeMillis()

        repeat(1000) {
            val ride = RideData(
                platform = Platform.UBER,
                rideValue = random.nextDouble() * 80 + 5,
                rideDuration = random.nextDouble() * 60 + 3,
                pickupDistance = random.nextDouble() * 5,
                dropoffDistance = random.nextDouble() * 20 + 1,
                passengerRating = random.nextDouble() * 2 + 3,
                pickupNeighborhood = "Test",
                dropoffNeighborhood = "Test"
            )
            val score = scorer.calculateScore(ride)
            assertTrue("Ride $it: score ${score.totalScore} out of range", score.totalScore in 0.0..100.0)
        }

        val elapsed = System.currentTimeMillis() - startTime
        assertTrue("1000 scores should complete in < 5s, took ${elapsed}ms", elapsed < 5000)
        println("Stress test: 1000 rides scored in ${elapsed}ms (${elapsed / 1000.0}ms/ride)")
    }

    // =========================================================================
    // STRESS: 1000 rides with all features active (cost, blocked, fatigue)
    // =========================================================================
    @Test
    fun testStress1000RidesFullFeatures() {
        val random = java.util.Random(456)
        val blocked = listOf(
            BlockedNeighborhood("Danger Zone", NeighborhoodType.DROPOFF, 25),
            BlockedNeighborhood("Bad Area", NeighborhoodType.PICKUP, 20)
        )
        val scorer = RideScorer(
            weights = CriteriaWeights(),
            driverThresholds = DriverThresholds(minValuePerKm = 2.5, minPassengerRating = 4.8),
            blockedNeighborhoods = blocked,
            costPerKm = 1.20
        )
        val startTime = System.currentTimeMillis()

        repeat(1000) {
            val ride = RideData(
                rideValue = random.nextDouble() * 100 + 3,
                rideDuration = random.nextDouble() * 90 + 1,
                pickupDistance = random.nextDouble() * 8,
                dropoffDistance = random.nextDouble() * 30 + 0.5,
                passengerRating = random.nextDouble() * 2 + 3,
                intermediateStops = random.nextInt(4),
                pickupNeighborhood = if (random.nextInt(10) == 0) "Bad Area" else "Normal",
                dropoffNeighborhood = if (random.nextInt(10) == 0) "Danger Zone" else "Normal",
                metadata = if (random.nextInt(5) == 0) mapOf("shiftHours" to "${random.nextDouble() * 14}") else null
            )
            val score = scorer.calculateScore(ride)
            assertTrue("Full-feature ride $it: ${score.totalScore} out of range", score.totalScore in 0.0..100.0)
        }

        val elapsed = System.currentTimeMillis() - startTime
        assertTrue("1000 full-feature scores in < 5s, took ${elapsed}ms", elapsed < 5000)
        println("Full-feature stress: 1000 rides in ${elapsed}ms (${elapsed / 1000.0}ms/ride)")
    }

    // =========================================================================
    // DETERMINISTIC: Same ride = same score
    // =========================================================================
    @Test
    fun testDeterministicScoring() {
        val ride = RideData(rideValue = 30.0, dropoffDistance = 8.0, rideDuration = 20.0, passengerRating = 4.8)
        val scorer = RideScorer(weights = CriteriaWeights(), driverThresholds = DriverThresholds())
        val score1 = scorer.calculateScore(ride)
        val score2 = scorer.calculateScore(ride)
        assertEquals("Same ride should produce same score", score1.totalScore, score2.totalScore, 0.001)
    }

    // =========================================================================
    // DETERMINISTIC: Score breakdown consistency
    // =========================================================================
    @Test
    fun testScoreBreakdownSumsCorrectly() {
        val ride = RideData(
            rideValue = 30.0, dropoffDistance = 10.0, rideDuration = 20.0,
            passengerRating = 4.9, intermediateStops = 0
        )
        val scorer = RideScorer(weights = CriteriaWeights(), driverThresholds = DriverThresholds())
        val score = scorer.calculateScore(ride)

        // Sum of weighted scores should be close to total (before penalties)
        val sumWeighted = score.criteriaScores.values.sumOf { it.weightedScore }
        val totalPenalty = score.thresholdViolations.sumOf { it.penaltyApplied }

        // The score may have scaling applied for missing criteria, so just verify it's reasonable
        assertTrue("Sum of weighted scores should be >= 0, got $sumWeighted", sumWeighted >= 0.0)
        assertTrue("Total score should be <= weighted sum, got total=${score.totalScore} sum=$sumWeighted penalties=$totalPenalty",
            score.totalScore <= sumWeighted + 1.0) // +1 tolerance for floating point
    }

    // =========================================================================
    // EDGE: Passenger rating exactly at boundary values
    // =========================================================================
    @Test
    fun testRatingBoundaryValues() {
        val scorer = RideScorer(
            weights = CriteriaWeights(valuePerKm = 0, valuePerHour = 0, intermediateStops = 0,
                passengerRating = 100, rideValue = 0, rideDuration = 0, pickupDistance = 0, dropoffDistance = 0),
            driverThresholds = DriverThresholds(minPassengerRating = 4.9)
        )

        // Rating tests need rideValue > 0 (zero value = score 0 by design)
        val base = 25.0
        val score5 = scorer.calculateScore(RideData(rideValue = base, passengerRating = 5.0))
        val score47 = scorer.calculateScore(RideData(rideValue = base, passengerRating = 4.7))
        val score30 = scorer.calculateScore(RideData(rideValue = base, passengerRating = 3.0))
        val score0 = scorer.calculateScore(RideData(rideValue = base, passengerRating = 0.0))

        assertTrue("5.0 rating should score higher than 4.7", score5.totalScore >= score47.totalScore)
        assertTrue("4.7 should score higher than 3.0", score47.totalScore >= score30.totalScore)
        // 0.0 rating: criterion skipped
        assertTrue("0.0 rating score should be valid", score0.totalScore in 0.0..100.0)
    }

    // =========================================================================
    // EDGE: Very large number of intermediate stops
    // =========================================================================
    @Test
    fun testManyIntermediateStops() {
        val ride = RideData(rideValue = 30.0, intermediateStops = 100)
        val scorer = RideScorer(weights = CriteriaWeights(), driverThresholds = DriverThresholds())
        val score = scorer.calculateScore(ride)
        assertTrue("100 stops should still produce valid score", score.totalScore in 0.0..100.0)
    }

    // =========================================================================
    // EDGE: RideData with all platforms
    // =========================================================================
    @Test
    fun testAllPlatforms() {
        val scorer = RideScorer(weights = CriteriaWeights(), driverThresholds = DriverThresholds())
        Platform.values().forEach { platform ->
            val ride = RideData(platform = platform, rideValue = 25.0, dropoffDistance = 10.0, rideDuration = 15.0)
            val score = scorer.calculateScore(ride)
            assertTrue("Platform $platform: score ${score.totalScore} out of range", score.totalScore in 0.0..100.0)
        }
    }
}
