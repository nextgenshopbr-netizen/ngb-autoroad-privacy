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
import kotlin.math.*
import kotlin.random.Random

/**
 * ThreeMonthScoringSimTest -- 90-day simulation of realistic driver activity.
 *
 * Simulates ~1350 rides over 3 months with:
 *   - Weekday patterns: 12-18 rides/day, 6am-6pm
 *   - Weekend patterns: 8-12 rides/day, 8am-2pm
 *   - Platform mix: 70% Uber, 25% 99, 5% inDrive
 *   - Values: R$7-50, normal distribution centered on R$15
 *   - Distances: 1-20km
 *   - Ratings: 80% good (4.5-5.0), 15% medium (4.0-4.5), 5% low (3.0-4.0)
 *   - 15 real Chapeco neighborhoods
 *   - Blocked neighborhoods (EFAPI during peak hours)
 *   - Driver decisions: accept if score>=72 OR value>R$25 (unless EFAPI at peak)
 *
 * Validates: score bounds, distribution, adaptive calibration, blocked penalties,
 * safety modifiers, return factor, performance (<3s for 1350 rides), determinism.
 */
class ThreeMonthScoringSimTest {

    companion object {
        // 15 real Chapeco neighborhoods
        val NEIGHBORHOODS = listOf(
            "Centro", "Efapi", "Palmital", "Presidente Medici",
            "Passo dos Fortes", "Jardim Italia", "Sao Cristovao",
            "Maria Goretti", "Bela Vista", "Santo Antonio",
            "Pinheirinho", "Santa Maria", "Quedas do Palmital",
            "Seminario", "Universitario"
        )

        // Peak hours: 7-9 AM, 17-19 PM
        fun isPeakHour(hour: Int): Boolean = hour in 7..9 || hour in 17..19

        // Standard weights (sum=100)
        val STANDARD_WEIGHTS = CriteriaWeights(
            valuePerKm = 25,
            valuePerHour = 25,
            intermediateStops = 15,
            passengerRating = 15,
            rideValue = 10,
            rideDuration = 5,
            pickupDistance = 5,
            dropoffDistance = 0
        )

        // Standard driver thresholds
        val STANDARD_THRESHOLDS = DriverThresholds(
            minValuePerKm = 2.00,
            minValuePerHour = 42.00,
            minPassengerRating = 4.70,
            maxStops = 1
        )

        // Blocked neighborhoods
        val BLOCKED_NEIGHBORHOODS = listOf(
            BlockedNeighborhood("Efapi", NeighborhoodType.DROPOFF, 25),
            BlockedNeighborhood("Quedas do Palmital", NeighborhoodType.DROPOFF, 15)
        )
    }

    // =========================================================================
    // DATA GENERATION: Deterministic ride generator with realistic distributions
    // =========================================================================

    data class SimulatedRide(
        val day: Int,            // 1-90
        val isWeekday: Boolean,
        val hour: Int,           // 0-23
        val ride: RideData,
        val shiftHours: Double
    )

    /**
     * Generates a Gaussian value using Box-Muller transform (deterministic with seed).
     * Returns value clamped to [min, max].
     */
    private fun gaussianValue(rng: Random, mean: Double, stdDev: Double, min: Double, max: Double): Double {
        val u1 = rng.nextDouble().coerceIn(1e-10, 1.0)
        val u2 = rng.nextDouble()
        val z = sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
        return (mean + stdDev * z).coerceIn(min, max)
    }

    /**
     * Selects a platform based on weighted distribution.
     * 70% Uber, 25% 99, 5% inDrive
     */
    private fun selectPlatform(rng: Random): Platform {
        val roll = rng.nextDouble()
        return when {
            roll < 0.70 -> Platform.UBER
            roll < 0.95 -> Platform.NINETY_NINE
            else -> Platform.INDRIVE
        }
    }

    /**
     * Selects a passenger rating based on distribution.
     * 80% good (4.5-5.0), 15% medium (4.0-4.5), 5% low (3.0-4.0)
     * inDrive: 30% chance of rating=0.0 (not provided)
     */
    private fun selectRating(rng: Random, platform: Platform): Double {
        // inDrive often does not provide ratings
        if (platform == Platform.INDRIVE && rng.nextDouble() < 0.30) return 0.0

        val roll = rng.nextDouble()
        return when {
            roll < 0.80 -> gaussianValue(rng, 4.8, 0.12, 4.5, 5.0)   // Good
            roll < 0.95 -> gaussianValue(rng, 4.25, 0.12, 4.0, 4.49)  // Medium
            else -> gaussianValue(rng, 3.5, 0.3, 3.0, 3.99)           // Low
        }
    }

    /**
     * Generates all rides for the simulation.
     * Deterministic: same seed = same results.
     */
    private fun generateRides(seed: Long = 42L): List<SimulatedRide> {
        val rng = Random(seed)
        val rides = mutableListOf<SimulatedRide>()

        for (day in 1..90) {
            // Day of week: day 1 = Monday (days 6,7,13,14,... are weekends)
            val dayOfWeek = ((day - 1) % 7) + 1 // 1=Mon, 7=Sun
            val isWeekday = dayOfWeek <= 5

            val numRides = if (isWeekday) {
                rng.nextInt(12, 19) // 12-18 rides
            } else {
                rng.nextInt(8, 13)  // 8-12 rides
            }

            // Shift start
            val shiftStartHour = if (isWeekday) 6 else 8
            val shiftEndHour = if (isWeekday) 18 else 14

            for (r in 0 until numRides) {
                // Distribute rides across shift
                val hourFraction = shiftStartHour + (shiftEndHour - shiftStartHour).toDouble() * r / numRides
                val hour = hourFraction.toInt().coerceIn(shiftStartHour, shiftEndHour - 1)
                val shiftHours = (hourFraction - shiftStartHour).coerceAtLeast(0.0)

                val platform = selectPlatform(rng)

                // Value: normal distribution centered on R$15, stddev R$8, range R$7-50
                val rideValue = gaussianValue(rng, 15.0, 8.0, 7.0, 50.0)

                // Duration: correlated with value (higher value = longer ride, roughly)
                val baseDuration = rideValue * 1.2 + rng.nextDouble() * 10.0
                val rideDuration = baseDuration.coerceIn(5.0, 60.0)

                // Distance: correlated with duration
                val dropoffDistance = gaussianValue(rng, rideDuration / 5.0, 3.0, 1.0, 20.0)
                val pickupDistance = gaussianValue(rng, 1.5, 1.0, 0.3, 5.0)

                // Rating
                val rating = selectRating(rng, platform)

                // Intermediate stops: 85% none, 10% one, 5% two
                val stops = when {
                    rng.nextDouble() < 0.85 -> 0
                    rng.nextDouble() < 0.67 -> 1
                    else -> 2
                }

                // Neighborhoods
                val pickupNeighborhood = NEIGHBORHOODS[rng.nextInt(NEIGHBORHOODS.size)]
                val dropoffNeighborhood = NEIGHBORHOODS[rng.nextInt(NEIGHBORHOODS.size)]

                // Metadata with shift hours
                val metadata = mapOf("shiftHours" to String.format("%.1f", shiftHours))

                val ride = RideData(
                    platform = platform,
                    rideValue = (rideValue * 100).toLong() / 100.0, // 2 decimal places
                    rideDuration = rideDuration.toLong().toDouble(),
                    pickupDistance = (pickupDistance * 10).toLong() / 10.0, // 1 decimal
                    dropoffDistance = (dropoffDistance * 10).toLong() / 10.0,
                    passengerRating = (rating * 100).toLong() / 100.0,
                    intermediateStops = stops,
                    pickupNeighborhood = pickupNeighborhood,
                    dropoffNeighborhood = dropoffNeighborhood,
                    metadata = metadata
                )

                rides.add(SimulatedRide(
                    day = day,
                    isWeekday = isWeekday,
                    hour = hour,
                    ride = ride,
                    shiftHours = shiftHours
                ))
            }
        }

        return rides
    }

    // =========================================================================
    // TEST 1: All scores are in range 0-100
    // =========================================================================
    @Test
    fun test01_allScoresInRange() {
        val rides = generateRides()
        assertTrue("Should generate at least 1200 rides, got ${rides.size}", rides.size >= 1200)

        val scorer = RideScorer(
            weights = STANDARD_WEIGHTS,
            driverThresholds = STANDARD_THRESHOLDS,
            blockedNeighborhoods = BLOCKED_NEIGHBORHOODS
        )

        var outOfRange = 0
        rides.forEach { sim ->
            val score = scorer.calculateScore(sim.ride)
            if (score.totalScore < 0.0 || score.totalScore > 100.0) {
                outOfRange++
            }
        }

        assertEquals(
            "All ${rides.size} scores must be in [0, 100] -- $outOfRange were out of range",
            0, outOfRange
        )
    }

    // =========================================================================
    // TEST 2: Score distribution has all expected levels (green, yellow, orange, red)
    // =========================================================================
    @Test
    fun test02_scoreDistributionCoversAllLevels() {
        val rides = generateRides()
        val scorer = RideScorer(
            weights = STANDARD_WEIGHTS,
            driverThresholds = STANDARD_THRESHOLDS,
            blockedNeighborhoods = BLOCKED_NEIGHBORHOODS
        )

        val scores = rides.map { scorer.calculateScore(it.ride) }

        // Count by ScoreLevel (using RideScore.scoreColor which uses 70/50/30 thresholds)
        val green = scores.count { it.scoreColor == ScoreLevel.GREEN }
        val yellow = scores.count { it.scoreColor == ScoreLevel.YELLOW }
        val orange = scores.count { it.scoreColor == ScoreLevel.ORANGE }
        val red = scores.count { it.scoreColor == ScoreLevel.RED }

        // OverlayCard uses 5-tier (85/75/60/40) but ScoreLevel has 4 tiers.
        // We also check the overlay 5-tier distribution.
        val overlayGreen = scores.count { it.totalScore >= 85 }
        val overlayGold = scores.count { it.totalScore in 75.0..84.99 }
        val overlayYellow = scores.count { it.totalScore in 60.0..74.99 }
        val overlayOrange = scores.count { it.totalScore in 40.0..59.99 }
        val overlayRed = scores.count { it.totalScore < 40 }

        println("=== Score Distribution (4-tier ScoreLevel) ===")
        println("GREEN  (>=70): $green (${percentStr(green, scores.size)})")
        println("YELLOW (50-69): $yellow (${percentStr(yellow, scores.size)})")
        println("ORANGE (30-49): $orange (${percentStr(orange, scores.size)})")
        println("RED    (<30):  $red (${percentStr(red, scores.size)})")

        println("\n=== Score Distribution (5-tier OverlayCard) ===")
        println("Green  (>=85): $overlayGreen (${percentStr(overlayGreen, scores.size)})")
        println("Gold   (75-84): $overlayGold (${percentStr(overlayGold, scores.size)})")
        println("Yellow (60-74): $overlayYellow (${percentStr(overlayYellow, scores.size)})")
        println("Orange (40-59): $overlayOrange (${percentStr(overlayOrange, scores.size)})")
        println("Red    (<40):  $overlayRed (${percentStr(overlayRed, scores.size)})")

        // Each ScoreLevel should have at least some representation
        assertTrue("Should have GREEN scores (>=70), got $green", green > 0)
        assertTrue("Should have YELLOW scores (50-69), got $yellow", yellow > 0)
        assertTrue("Should have ORANGE scores (30-49), got $orange", orange > 0)
        assertTrue("Should have RED scores (<30), got $red", red > 0)

        // Distribution should not be degenerate (no single level > 80%)
        val maxPercent = maxOf(green, yellow, orange, red).toDouble() / scores.size * 100
        assertTrue(
            "No single level should dominate >80% of scores (max=${"%.1f".format(maxPercent)}%)",
            maxPercent < 80.0
        )
    }

    // =========================================================================
    // TEST 3: AdaptiveScoringEngine calibrates correctly after 30+ rides
    // =========================================================================
    @Test
    fun test03_adaptiveEngineCalibration() {
        val rides = generateRides()

        // Collect ride metrics for calibration
        val valuesPerKm = mutableListOf<Double>()
        val valuesPerHour = mutableListOf<Double>()
        val rideValues = mutableListOf<Double>()
        val pickupDistances = mutableListOf<Double>()
        val dropoffDistances = mutableListOf<Double>()
        val durations = mutableListOf<Double>()

        rides.forEach { sim ->
            val ride = sim.ride
            if (ride.dropoffDistance > 0) valuesPerKm.add(ride.valuePerKm)
            if (ride.rideDuration > 0) valuesPerHour.add(ride.valuePerHour)
            rideValues.add(ride.rideValue)
            pickupDistances.add(ride.pickupDistance)
            dropoffDistances.add(ride.dropoffDistance)
            durations.add(ride.rideDuration)
        }

        // Create engine without Context (uses null prefs, starts from defaults)
        val engine = AdaptiveScoringEngine()

        // Before calibration: should not be calibrated
        assertFalse("Engine should not be calibrated before first call", engine.isCalibrated())
        assertEquals(0, engine.getCalibratedRideCount())

        // Calibrate with first 20 rides (below threshold)
        engine.calibrate(
            valuesPerKm.take(20),
            valuesPerHour.take(20),
            rideValues.take(20),
            pickupDistances.take(20),
            dropoffDistances.take(20),
            durations.take(20)
        )
        assertFalse("Engine should not calibrate with only 20 rides", engine.isCalibrated())

        // Calibrate with 100 rides (above threshold of 30)
        engine.calibrate(
            valuesPerKm.take(100),
            valuesPerHour.take(100),
            rideValues.take(100),
            pickupDistances.take(100),
            dropoffDistances.take(100),
            durations.take(100)
        )
        assertTrue("Engine should be calibrated after 100 rides", engine.isCalibrated())
        assertEquals(100, engine.getCalibratedRideCount())

        // Adaptive thresholds should differ from defaults
        val defaults = ScoringThresholds()
        val adaptive = engine.getAdaptiveThresholds()

        println("\n=== Adaptive vs Default Thresholds ===")
        println("ValuePerKm: [${defaults.minValuePerKm}-${defaults.maxValuePerKm}] -> [${fmt(adaptive.minValuePerKm)}-${fmt(adaptive.maxValuePerKm)}]")
        println("ValuePerHour: [${defaults.minValuePerHour}-${defaults.maxValuePerHour}] -> [${fmt(adaptive.minValuePerHour)}-${fmt(adaptive.maxValuePerHour)}]")
        println("RideValue: [${defaults.minRideValue}-${defaults.maxRideValue}] -> [${fmt(adaptive.minRideValue)}-${fmt(adaptive.maxRideValue)}]")
        println("Duration: [${defaults.minDuration}-${defaults.maxDuration}] -> [${fmt(adaptive.minDuration)}-${fmt(adaptive.maxDuration)}]")

        // Adaptive thresholds should produce valid ScoringThresholds (min < max always)
        assertTrue("Adaptive minValuePerKm < maxValuePerKm",
            adaptive.minValuePerKm < adaptive.maxValuePerKm)
        assertTrue("Adaptive minValuePerHour < maxValuePerHour",
            adaptive.minValuePerHour < adaptive.maxValuePerHour)
        assertTrue("Adaptive minRideValue < maxRideValue",
            adaptive.minRideValue < adaptive.maxRideValue)
        assertTrue("Adaptive minDuration < maxDuration",
            adaptive.minDuration < adaptive.maxDuration)

        // Score with adaptive thresholds should still be in range
        val adaptiveScorer = RideScorer(
            weights = STANDARD_WEIGHTS,
            driverThresholds = STANDARD_THRESHOLDS,
            thresholds = adaptive
        )
        rides.take(50).forEach { sim ->
            val score = adaptiveScorer.calculateScore(sim.ride)
            assertTrue(
                "Adaptive score out of range: ${score.totalScore}",
                score.totalScore in 0.0..100.0
            )
        }

        // Calibrate again with all rides (EWMA should smooth further)
        engine.calibrate(
            valuesPerKm, valuesPerHour, rideValues,
            pickupDistances, dropoffDistances, durations
        )
        assertEquals(valuesPerKm.size, engine.getCalibratedRideCount())
        val fullyAdaptive = engine.getAdaptiveThresholds()
        assertTrue("Fully calibrated minValuePerKm < maxValuePerKm",
            fullyAdaptive.minValuePerKm < fullyAdaptive.maxValuePerKm)
    }

    // =========================================================================
    // TEST 4: Blocked neighborhood penalty works
    // =========================================================================
    @Test
    fun test04_blockedNeighborhoodPenaltyWorks() {
        val rideToEfapi = RideData(
            platform = Platform.UBER,
            rideValue = 20.0,
            rideDuration = 15.0,
            pickupDistance = 1.0,
            dropoffDistance = 8.0,
            passengerRating = 4.9,
            pickupNeighborhood = "Centro",
            dropoffNeighborhood = "Efapi"
        )

        val rideToCenter = rideToEfapi.copy(
            dropoffNeighborhood = "Centro",
            pickupNeighborhood = "Jardim Italia"
        )

        // Scorer without blocked neighborhoods
        val scorerNoBlock = RideScorer(
            weights = STANDARD_WEIGHTS,
            driverThresholds = STANDARD_THRESHOLDS,
            blockedNeighborhoods = emptyList()
        )

        // Scorer with EFAPI blocked
        val scorerBlocked = RideScorer(
            weights = STANDARD_WEIGHTS,
            driverThresholds = STANDARD_THRESHOLDS,
            blockedNeighborhoods = BLOCKED_NEIGHBORHOODS
        )

        val scoreEfapiUnblocked = scorerNoBlock.calculateScore(rideToEfapi)
        val scoreEfapiBlocked = scorerBlocked.calculateScore(rideToEfapi)
        val scoreCenterBlocked = scorerBlocked.calculateScore(rideToCenter)

        println("\n=== Blocked Neighborhood Test ===")
        println("Efapi (unblocked): ${fmt(scoreEfapiUnblocked.totalScore)}")
        println("Efapi (blocked):   ${fmt(scoreEfapiBlocked.totalScore)}")
        println("Centro (blocked scorer): ${fmt(scoreCenterBlocked.totalScore)}")

        // EFAPI ride should score lower when blocked
        assertTrue(
            "Blocked EFAPI ride (${fmt(scoreEfapiBlocked.totalScore)}) should score lower than unblocked (${fmt(scoreEfapiUnblocked.totalScore)})",
            scoreEfapiBlocked.totalScore < scoreEfapiUnblocked.totalScore
        )

        // The penalty should be at least 15 points (EFAPI has penalty 25)
        val penalty = scoreEfapiUnblocked.totalScore - scoreEfapiBlocked.totalScore
        assertTrue(
            "EFAPI penalty should be at least 15 points, got ${fmt(penalty)}",
            penalty >= 15.0
        )

        // Centro ride should not be affected by the blocked EFAPI dropoff rule
        val centerUnblocked = scorerNoBlock.calculateScore(rideToCenter)
        assertEquals(
            "Centro ride should not be penalized by EFAPI block",
            centerUnblocked.totalScore,
            scoreCenterBlocked.totalScore,
            0.01
        )
    }

    // =========================================================================
    // TEST 5: Safety modifier works for night rides
    // =========================================================================
    @Test
    fun test05_safetyModifierNightRides() {
        // SafetyScoreModifierStatic uses Calendar.getInstance() for hour detection,
        // so we test the static method directly with known inputs.

        // Test with low rating and blocked area
        val penaltyLowRatingBlocked = SafetyScoreModifierStatic.calculatePenalty(
            passengerRating = 4.2,
            ratingThreshold = 4.5,
            pickupNeighborhood = "Efapi",
            dropoffNeighborhood = "Centro",
            blockedNeighborhoods = BLOCKED_NEIGHBORHOODS
        )

        // Test with good rating and no blocked area
        val penaltyGoodRating = SafetyScoreModifierStatic.calculatePenalty(
            passengerRating = 4.9,
            ratingThreshold = 4.5,
            pickupNeighborhood = "Centro",
            dropoffNeighborhood = "Jardim Italia",
            blockedNeighborhoods = emptyList()
        )

        println("\n=== Safety Modifier Tests ===")
        println("Low rating + blocked area penalty: ${fmt(penaltyLowRatingBlocked)}")
        println("Good rating + safe area penalty: ${fmt(penaltyGoodRating)}")

        // Good rating should have less or equal penalty
        assertTrue(
            "Good rating penalty (${fmt(penaltyGoodRating)}) should be <= low rating penalty (${fmt(penaltyLowRatingBlocked)})",
            penaltyGoodRating <= penaltyLowRatingBlocked
        )

        // Safety penalty should always be capped at 25
        assertTrue(
            "Safety penalty should be capped at 25, got ${fmt(penaltyLowRatingBlocked)}",
            penaltyLowRatingBlocked <= 25.0
        )

        // Zero-rating (no rating available) should have zero penalty from safety
        // since the condition checks passengerRating > 0
        val penaltyNoRating = SafetyScoreModifierStatic.calculatePenalty(
            passengerRating = 0.0,
            ratingThreshold = 4.5,
            pickupNeighborhood = "Centro",
            dropoffNeighborhood = "Jardim Italia",
            blockedNeighborhoods = emptyList()
        )
        // Rating=0 means "not provided", so isLowRating=false (rating > 0 check fails)
        // Only night penalty could apply (if running at night)
        assertTrue(
            "No-rating penalty should be <= 3 (night factor only), got ${fmt(penaltyNoRating)}",
            penaltyNoRating <= 3.0
        )
    }

    // =========================================================================
    // TEST 6: ReturnFactorEngine accumulates correctly
    // =========================================================================
    @Test
    fun test06_returnFactorAccumulation() {
        // Test ReturnFactorEngineStatic heuristic
        val penaltyShort = ReturnFactorEngineStatic.calculateReturnPenalty("Centro", 3.0)
        val penaltyMedium = ReturnFactorEngineStatic.calculateReturnPenalty("Efapi", 8.0)
        val penaltyLong = ReturnFactorEngineStatic.calculateReturnPenalty("Efapi", 18.0)
        val penaltyVeryLong = ReturnFactorEngineStatic.calculateReturnPenalty("Efapi", 30.0)

        println("\n=== Return Factor Penalty Tests ===")
        println("Short ride (3km):   ${fmt(penaltyShort)}")
        println("Medium ride (8km):  ${fmt(penaltyMedium)}")
        println("Long ride (18km):   ${fmt(penaltyLong)}")
        println("Very long (30km):   ${fmt(penaltyVeryLong)}")

        // Short rides (<= 5km) should have zero penalty
        assertEquals("Short rides should have zero return penalty", 0.0, penaltyShort, 0.001)

        // Penalty should increase with distance
        assertTrue("Medium > short", penaltyMedium > penaltyShort)
        assertTrue("Long > medium", penaltyLong > penaltyMedium)
        assertTrue("Very long > long", penaltyVeryLong > penaltyLong)

        // Penalty should be capped at 15
        assertTrue("Return penalty should be capped at 15, got ${fmt(penaltyVeryLong)}", penaltyVeryLong <= 15.0)

        // Test NeighborhoodReturnData calculation
        val data = NeighborhoodReturnData(
            neighborhood = "efapi",
            totalTrips = 10,
            emptyReturns = 8, // 80% empty return
            avgReturnKm = 12.0
        )
        val factor = data.calculateReturnFactor()
        println("Efapi (80% empty return) factor: ${fmt(factor)}")
        assertTrue("Return factor should be < 1.0 for high empty-return neighborhood", factor < 1.0)
        assertTrue("Return factor should be > 0.5", factor > 0.5)
        // 1.0 / (1.0 + 0.8) = 0.555...
        assertEquals("80% empty return -> factor ~0.556", 1.0 / 1.8, factor, 0.001)

        // Test with insufficient data (< 3 trips)
        val newData = NeighborhoodReturnData(neighborhood = "novo", totalTrips = 2)
        assertEquals("< 3 trips -> factor 0.85", 0.85, newData.calculateReturnFactor(), 0.001)
    }

    // =========================================================================
    // TEST 7: Performance -- 1350+ scores in under 3 seconds
    // =========================================================================
    @Test
    fun test07_performanceUnder3Seconds() {
        val rides = generateRides()
        assertTrue("Should have at least 1200 rides", rides.size >= 1200)

        val scorer = RideScorer(
            weights = STANDARD_WEIGHTS,
            driverThresholds = STANDARD_THRESHOLDS,
            blockedNeighborhoods = BLOCKED_NEIGHBORHOODS
        )

        val startNs = System.nanoTime()
        val scores = rides.map { scorer.calculateScore(it.ride) }
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000.0

        println("\n=== Performance ===")
        println("Rides scored: ${scores.size}")
        println("Total time: ${"%.2f".format(elapsedMs)}ms")
        println("Per ride: ${"%.3f".format(elapsedMs / scores.size)}ms")

        assertTrue(
            "Scoring ${scores.size} rides took ${"%.0f".format(elapsedMs)}ms -- must be < 3000ms",
            elapsedMs < 3000.0
        )
    }

    // =========================================================================
    // TEST 8: Deterministic -- same seed produces same results
    // =========================================================================
    @Test
    fun test08_deterministic() {
        val rides1 = generateRides(seed = 12345L)
        val rides2 = generateRides(seed = 12345L)

        assertEquals("Same seed should produce same number of rides", rides1.size, rides2.size)

        val scorer = RideScorer(
            weights = STANDARD_WEIGHTS,
            driverThresholds = STANDARD_THRESHOLDS,
            blockedNeighborhoods = BLOCKED_NEIGHBORHOODS
        )

        // Compare every score
        var mismatches = 0
        rides1.zip(rides2).forEach { (sim1, sim2) ->
            val score1 = scorer.calculateScore(sim1.ride)
            val score2 = scorer.calculateScore(sim2.ride)
            if (abs(score1.totalScore - score2.totalScore) > 0.001) {
                mismatches++
            }
        }

        assertEquals(
            "Same seed should produce identical scores -- $mismatches mismatches found",
            0, mismatches
        )

        // Different seed should produce different results
        val rides3 = generateRides(seed = 99999L)
        val score1First = scorer.calculateScore(rides1.first().ride)
        val score3First = scorer.calculateScore(rides3.first().ride)
        // Not guaranteed to differ, but statistically very likely with different seeds
        // We just check they both produce valid output
        assertTrue("Both seeds produce valid scores",
            score1First.totalScore in 0.0..100.0 && score3First.totalScore in 0.0..100.0)
    }

    // =========================================================================
    // TEST 9: Driver decision simulation
    // =========================================================================
    @Test
    fun test09_driverDecisionSimulation() {
        val rides = generateRides()
        val scorer = RideScorer(
            weights = STANDARD_WEIGHTS,
            driverThresholds = STANDARD_THRESHOLDS,
            blockedNeighborhoods = BLOCKED_NEIGHBORHOODS
        )

        var accepted = 0
        var rejected = 0
        var acceptedValue = 0.0
        var rejectedValue = 0.0

        rides.forEach { sim ->
            val score = scorer.calculateScore(sim.ride)
            val isEfapiAtPeak = sim.ride.dropoffNeighborhood.equals("Efapi", ignoreCase = true)
                && isPeakHour(sim.hour)

            // Driver decision: accept if score>=72 OR value>R$25 (unless EFAPI at peak)
            val accept = when {
                isEfapiAtPeak -> false // Always reject EFAPI during peak
                score.totalScore >= 72 -> true
                sim.ride.rideValue > 25.0 -> true
                else -> false
            }

            if (accept) {
                accepted++
                acceptedValue += sim.ride.rideValue
            } else {
                rejected++
                rejectedValue += sim.ride.rideValue
            }
        }

        val totalRides = rides.size
        val acceptRate = accepted.toDouble() / totalRides * 100

        println("\n=== Driver Decision Simulation ===")
        println("Total rides: $totalRides")
        println("Accepted: $accepted (${"%.1f".format(acceptRate)}%)")
        println("Rejected: $rejected (${"%.1f".format(100 - acceptRate)}%)")
        println("Avg accepted value: R$${"%.2f".format(acceptedValue / accepted.coerceAtLeast(1))}")
        println("Avg rejected value: R$${"%.2f".format(rejectedValue / rejected.coerceAtLeast(1))}")

        // Sanity checks
        assertTrue("Should accept some rides", accepted > 0)
        assertTrue("Should reject some rides", rejected > 0)
        assertTrue("Accept rate should be between 20-80%", acceptRate in 20.0..80.0)

        // Accepted rides should have higher average value than rejected
        val avgAccepted = acceptedValue / accepted
        val avgRejected = rejectedValue / rejected.coerceAtLeast(1)
        println("Avg accepted R$/ride: R$${"%.2f".format(avgAccepted)}")
        println("Avg rejected R$/ride: R$${"%.2f".format(avgRejected)}")
    }

    // =========================================================================
    // TEST 10: Fatigue penalty applies correctly
    // =========================================================================
    @Test
    fun test10_fatiguePenalty() {
        val baseRide = RideData(
            platform = Platform.UBER,
            rideValue = 20.0,
            rideDuration = 15.0,
            dropoffDistance = 8.0,
            pickupDistance = 1.0,
            passengerRating = 4.9,
            pickupNeighborhood = "Centro",
            dropoffNeighborhood = "Jardim Italia"
        )

        val scorer = RideScorer(
            weights = STANDARD_WEIGHTS,
            driverThresholds = STANDARD_THRESHOLDS
        )

        val scoreNoFatigue = scorer.calculateScore(baseRide)

        val ride6h = baseRide.copy(metadata = mapOf("shiftHours" to "6.5"))
        val ride8h = baseRide.copy(metadata = mapOf("shiftHours" to "8.5"))
        val ride10h = baseRide.copy(metadata = mapOf("shiftHours" to "10.5"))
        val ride12h = baseRide.copy(metadata = mapOf("shiftHours" to "12.5"))

        val score6h = scorer.calculateScore(ride6h)
        val score8h = scorer.calculateScore(ride8h)
        val score10h = scorer.calculateScore(ride10h)
        val score12h = scorer.calculateScore(ride12h)

        println("\n=== Fatigue Penalty ===")
        println("No fatigue: ${fmt(scoreNoFatigue.totalScore)}")
        println("6.5h shift: ${fmt(score6h.totalScore)} (penalty: ${fmt(scoreNoFatigue.totalScore - score6h.totalScore)})")
        println("8.5h shift: ${fmt(score8h.totalScore)} (penalty: ${fmt(scoreNoFatigue.totalScore - score8h.totalScore)})")
        println("10.5h shift: ${fmt(score10h.totalScore)} (penalty: ${fmt(scoreNoFatigue.totalScore - score10h.totalScore)})")
        println("12.5h shift: ${fmt(score12h.totalScore)} (penalty: ${fmt(scoreNoFatigue.totalScore - score12h.totalScore)})")

        // Each tier should be worse than the previous
        assertTrue("6h shift should penalize", score6h.totalScore < scoreNoFatigue.totalScore)
        assertTrue("8h < 6h", score8h.totalScore < score6h.totalScore)
        assertTrue("10h < 8h", score10h.totalScore < score8h.totalScore)
        assertTrue("12h < 10h", score12h.totalScore < score10h.totalScore)

        // 12h shift should have heavy penalty (40 points)
        val penalty12h = scoreNoFatigue.totalScore - score12h.totalScore
        assertTrue("12h penalty should be >= 35 pts, got ${fmt(penalty12h)}", penalty12h >= 35.0)
    }

    // =========================================================================
    // TEST 11: Profit/KM criterion with vehicle cost
    // =========================================================================
    @Test
    fun test11_profitPerKmCriterion() {
        val ride = RideData(
            platform = Platform.UBER,
            rideValue = 15.0,
            rideDuration = 12.0,
            dropoffDistance = 10.0,
            pickupDistance = 1.5,
            passengerRating = 4.8,
            pickupNeighborhood = "Centro",
            dropoffNeighborhood = "Palmital"
        )

        // Without cost (costPerKm=0 -> criterion skipped)
        val scorerNoCost = RideScorer(
            weights = STANDARD_WEIGHTS,
            driverThresholds = STANDARD_THRESHOLDS,
            costPerKm = 0.0
        )
        val scoreNoCost = scorerNoCost.calculateScore(ride)

        // With cost R$0.35/km (profitable: R$1.50/km - R$0.35 = R$1.15 profit)
        val scorerWithCost = RideScorer(
            weights = STANDARD_WEIGHTS,
            driverThresholds = STANDARD_THRESHOLDS,
            costPerKm = 0.35
        )
        val scoreWithCost = scorerWithCost.calculateScore(ride)

        // With high cost R$2.00/km (loss: R$1.50/km - R$2.00 = -R$0.50 loss)
        val scorerHighCost = RideScorer(
            weights = STANDARD_WEIGHTS,
            driverThresholds = STANDARD_THRESHOLDS,
            costPerKm = 2.00
        )
        val scoreHighCost = scorerHighCost.calculateScore(ride)

        println("\n=== Profit/KM Criterion ===")
        println("R$/km: ${fmt(ride.valuePerKm)}")
        println("No cost: ${fmt(scoreNoCost.totalScore)}")
        println("Cost R$0.35/km (profit): ${fmt(scoreWithCost.totalScore)}")
        println("Cost R$2.00/km (loss):   ${fmt(scoreHighCost.totalScore)}")

        // High cost (loss-making ride) should score lower
        assertTrue(
            "Loss-making ride should score lower: highCost=${fmt(scoreHighCost.totalScore)} vs withCost=${fmt(scoreWithCost.totalScore)}",
            scoreHighCost.totalScore < scoreWithCost.totalScore
        )

        // All scores still in range
        assertTrue("No cost score in range", scoreNoCost.totalScore in 0.0..100.0)
        assertTrue("With cost score in range", scoreWithCost.totalScore in 0.0..100.0)
        assertTrue("High cost score in range", scoreHighCost.totalScore in 0.0..100.0)

        // With profitPerKm < 0 there should be a ThresholdViolation for "Lucro/KM"
        val profitViolation = scoreHighCost.thresholdViolations.find { it.criteriaName == "Lucro/KM" }
        assertNotNull("Loss-making ride should have Lucro/KM violation", profitViolation)
    }

    // =========================================================================
    // TEST 12: Platform distribution in simulation
    // =========================================================================
    @Test
    fun test12_platformDistribution() {
        val rides = generateRides()

        val uberCount = rides.count { it.ride.platform == Platform.UBER }
        val nn99Count = rides.count { it.ride.platform == Platform.NINETY_NINE }
        val indriveCount = rides.count { it.ride.platform == Platform.INDRIVE }
        val total = rides.size.toDouble()

        val uberPct = uberCount / total * 100
        val nn99Pct = nn99Count / total * 100
        val indrivePct = indriveCount / total * 100

        println("\n=== Platform Distribution ===")
        println("Uber:    $uberCount (${"%.1f".format(uberPct)}%) [target: 70%]")
        println("99:      $nn99Count (${"%.1f".format(nn99Pct)}%) [target: 25%]")
        println("inDrive: $indriveCount (${"%.1f".format(indrivePct)}%) [target: 5%]")

        // Platform distribution should be roughly correct (within 10% tolerance)
        assertTrue("Uber should be 60-80%, got ${"%.1f".format(uberPct)}%", uberPct in 60.0..80.0)
        assertTrue("99 should be 15-35%, got ${"%.1f".format(nn99Pct)}%", nn99Pct in 15.0..35.0)
        assertTrue("inDrive should be 1-10%, got ${"%.1f".format(indrivePct)}%", indrivePct in 1.0..10.0)
    }

    // =========================================================================
    // TEST 13: Weekday vs weekend pattern differences
    // =========================================================================
    @Test
    fun test13_weekdayVsWeekendPatterns() {
        val rides = generateRides()

        val weekdayRides = rides.filter { it.isWeekday }
        val weekendRides = rides.filter { !it.isWeekday }

        // Count rides per day
        val weekdayDays = (1..90).count { day -> ((day - 1) % 7) + 1 <= 5 }
        val weekendDays = 90 - weekdayDays

        val avgWeekdayRides = weekdayRides.size.toDouble() / weekdayDays
        val avgWeekendRides = weekendRides.size.toDouble() / weekendDays

        println("\n=== Weekday vs Weekend ===")
        println("Weekday: $weekdayDays days, ${weekdayRides.size} rides, avg ${"%.1f".format(avgWeekdayRides)}/day [target: 12-18]")
        println("Weekend: $weekendDays days, ${weekendRides.size} rides, avg ${"%.1f".format(avgWeekendRides)}/day [target: 8-12]")

        // Weekdays should have more rides than weekends
        assertTrue("Weekdays should average more rides", avgWeekdayRides > avgWeekendRides)
        assertTrue("Weekday avg should be ~15", avgWeekdayRides in 12.0..18.0)
        assertTrue("Weekend avg should be ~10", avgWeekendRides in 8.0..12.0)
    }

    // =========================================================================
    // TEST 14: Score criteria breakdown is complete
    // =========================================================================
    @Test
    fun test14_criteriaBreakdownComplete() {
        val ride = RideData(
            platform = Platform.UBER,
            rideValue = 20.0,
            rideDuration = 15.0,
            pickupDistance = 2.0,
            dropoffDistance = 8.0,
            passengerRating = 4.8,
            intermediateStops = 0,
            pickupNeighborhood = "Centro",
            dropoffNeighborhood = "Palmital"
        )

        val scorer = RideScorer(
            weights = STANDARD_WEIGHTS,
            driverThresholds = STANDARD_THRESHOLDS
        )

        val score = scorer.calculateScore(ride)

        println("\n=== Criteria Breakdown ===")
        score.criteriaScores.forEach { (key, cs) ->
            println("$key: raw=${fmt(cs.rawValue)} normalized=${fmt(cs.normalizedScore)} " +
                "weight=${cs.weight} weighted=${fmt(cs.weightedScore)} level=${cs.level}")
        }

        // With STANDARD_WEIGHTS, active criteria are:
        // valuePerKm(25), valuePerHour(25), intermediateStops(15),
        // passengerRating(15), rideValue(10), rideDuration(5), pickupDistance(5)
        val expectedKeys = setOf("valuePerKm", "valuePerHour", "intermediateStops",
            "passengerRating", "rideValue", "rideDuration", "pickupDistance")

        expectedKeys.forEach { key ->
            assertTrue(
                "Criteria '$key' should be in breakdown (weights has it >0)",
                score.criteriaScores.containsKey(key)
            )
        }

        // Each criteria score should have normalized in [0, 100]
        score.criteriaScores.values.forEach { cs ->
            assertTrue(
                "${cs.name} normalizedScore ${cs.normalizedScore} should be 0-100",
                cs.normalizedScore in 0.0..100.0
            )
            assertTrue(
                "${cs.name} weight ${cs.weight} should be > 0",
                cs.weight > 0
            )
        }
    }

    // =========================================================================
    // TEST 15: Comprehensive 3-month statistics report
    // =========================================================================
    @Test
    fun test15_threeMonthStatisticsReport() {
        val rides = generateRides()
        val scorer = RideScorer(
            weights = STANDARD_WEIGHTS,
            driverThresholds = STANDARD_THRESHOLDS,
            blockedNeighborhoods = BLOCKED_NEIGHBORHOODS
        )

        val scores = rides.map { sim ->
            sim to scorer.calculateScore(sim.ride)
        }

        // Monthly breakdown
        for (month in 0..2) {
            val startDay = month * 30 + 1
            val endDay = (month + 1) * 30
            val monthRides = scores.filter { it.first.day in startDay..endDay }

            val avgScore = monthRides.map { it.second.totalScore }.average()
            val avgValue = monthRides.map { it.first.ride.rideValue }.average()
            val totalRevenue = monthRides.sumOf { it.first.ride.rideValue }
            val greenPct = monthRides.count { it.second.scoreColor == ScoreLevel.GREEN }.toDouble() / monthRides.size * 100

            println("Month ${month + 1}: ${monthRides.size} rides | avg score=${fmt(avgScore)} | " +
                "avg R$=${fmt(avgValue)} | total R$=${fmt(totalRevenue)} | green=${fmt(greenPct)}%")
        }

        // Total summary
        val allScores = scores.map { it.second.totalScore }
        val avgScore = allScores.average()
        val minScore = allScores.minOrNull() ?: 0.0
        val maxScore = allScores.maxOrNull() ?: 0.0
        val stdDev = sqrt(allScores.map { (it - avgScore).pow(2) }.average())

        println("\n=== 3-Month Summary ===")
        println("Total rides: ${rides.size}")
        println("Score: avg=${fmt(avgScore)} min=${fmt(minScore)} max=${fmt(maxScore)} stddev=${fmt(stdDev)}")
        println("Total revenue: R$${"%.2f".format(rides.sumOf { it.ride.rideValue })}")

        // Violations summary
        val totalViolations = scores.count { it.second.hasViolations }
        println("Rides with violations: $totalViolations (${percentStr(totalViolations, scores.size)})")

        // Final assertions
        assertTrue("Average score should be between 30-80", avgScore in 30.0..80.0)
        assertTrue("Min score should be >= 0", minScore >= 0.0)
        assertTrue("Max score should be <= 100", maxScore <= 100.0)
        assertTrue("Should have some violations", totalViolations > 0)
    }

    // =========================================================================
    // TEST 16: Zero-weight criteria are excluded
    // =========================================================================
    @Test
    fun test16_zeroWeightCriteriaExcluded() {
        val ride = RideData(
            platform = Platform.UBER,
            rideValue = 20.0,
            rideDuration = 15.0,
            pickupDistance = 2.0,
            dropoffDistance = 8.0,
            passengerRating = 4.8,
            pickupNeighborhood = "Centro",
            dropoffNeighborhood = "Palmital"
        )

        // Only value-per-km active
        val minimalWeights = CriteriaWeights(
            valuePerKm = 100,
            valuePerHour = 0,
            intermediateStops = 0,
            passengerRating = 0,
            rideValue = 0,
            rideDuration = 0,
            pickupDistance = 0,
            dropoffDistance = 0
        )

        val scorer = RideScorer(weights = minimalWeights, driverThresholds = DriverThresholds())
        val score = scorer.calculateScore(ride)

        assertEquals("Only valuePerKm should be in breakdown", 1, score.criteriaScores.size)
        assertTrue("valuePerKm should be present", score.criteriaScores.containsKey("valuePerKm"))
        assertTrue("Score should be in range", score.totalScore in 0.0..100.0)
    }

    // =========================================================================
    // TEST 17: Division-by-zero guards
    // =========================================================================
    @Test
    fun test17_divisionByZeroGuards() {
        // Ride with zero distance (valuePerKm would be infinite)
        val rideZeroDistance = RideData(
            rideValue = 20.0,
            dropoffDistance = 0.0,
            rideDuration = 15.0,
            passengerRating = 4.8
        )

        val scorer = RideScorer(
            weights = STANDARD_WEIGHTS,
            driverThresholds = STANDARD_THRESHOLDS
        )
        val score = scorer.calculateScore(rideZeroDistance)

        assertTrue("Score with zero distance should be valid", score.totalScore in 0.0..100.0)
        assertFalse("valuePerKm should NOT be in breakdown (distance=0)",
            score.criteriaScores.containsKey("valuePerKm"))

        // Ride with zero duration (valuePerHour would be infinite)
        val rideZeroDuration = RideData(
            rideValue = 20.0,
            dropoffDistance = 8.0,
            rideDuration = 0.0,
            passengerRating = 4.8
        )
        val score2 = scorer.calculateScore(rideZeroDuration)
        assertTrue("Score with zero duration should be valid", score2.totalScore in 0.0..100.0)
        assertFalse("valuePerHour should NOT be in breakdown (duration=0)",
            score2.criteriaScores.containsKey("valuePerHour"))

        // Ride with zero value (early return)
        val rideZeroValue = RideData(rideValue = 0.0, dropoffDistance = 8.0)
        val score3 = scorer.calculateScore(rideZeroValue)
        assertEquals("Zero value ride should score 0", 0.0, score3.totalScore, 0.001)

        // ScoringThresholds with zero range (min == max)
        val zeroRangeThresholds = ScoringThresholds(
            minValuePerKm = 1.0,
            maxValuePerKm = 1.0 // range = 0!
        )
        val scorerZeroRange = RideScorer(
            weights = STANDARD_WEIGHTS,
            driverThresholds = STANDARD_THRESHOLDS,
            thresholds = zeroRangeThresholds
        )
        val score4 = scorerZeroRange.calculateScore(RideData(rideValue = 15.0, dropoffDistance = 8.0, passengerRating = 4.8))
        assertTrue("Score with zero-range threshold should be valid (guard returns 50)",
            score4.totalScore in 0.0..100.0)
    }

    // =========================================================================
    // TEST 18: Stress test -- edge case rides en masse
    // =========================================================================
    @Test
    fun test18_stressTestEdgeCases() {
        val scorer = RideScorer(
            weights = STANDARD_WEIGHTS,
            driverThresholds = STANDARD_THRESHOLDS,
            blockedNeighborhoods = BLOCKED_NEIGHBORHOODS,
            costPerKm = 0.35
        )

        val edgeCases = listOf(
            // Minimum viable ride
            RideData(rideValue = 0.01, dropoffDistance = 0.1, passengerRating = 3.0),
            // Maximum ride
            RideData(rideValue = 999.0, dropoffDistance = 100.0, rideDuration = 300.0, passengerRating = 5.0),
            // All zeros except value
            RideData(rideValue = 10.0),
            // Tiny distance, huge value (extreme R$/km)
            RideData(rideValue = 50.0, dropoffDistance = 0.5),
            // Huge distance, tiny value (terrible R$/km)
            RideData(rideValue = 7.0, dropoffDistance = 50.0),
            // Rating exactly at thresholds
            RideData(rideValue = 15.0, dropoffDistance = 8.0, passengerRating = 4.7),
            RideData(rideValue = 15.0, dropoffDistance = 8.0, passengerRating = 4.5),
            RideData(rideValue = 15.0, dropoffDistance = 8.0, passengerRating = 4.3),
            RideData(rideValue = 15.0, dropoffDistance = 8.0, passengerRating = 3.0),
            // Blocked neighborhood
            RideData(rideValue = 30.0, dropoffDistance = 15.0, dropoffNeighborhood = "Efapi", passengerRating = 4.9),
            // Extreme fatigue
            RideData(rideValue = 20.0, dropoffDistance = 8.0, passengerRating = 4.9,
                metadata = mapOf("shiftHours" to "15.0")),
            // Many stops
            RideData(rideValue = 20.0, dropoffDistance = 8.0, passengerRating = 4.9, intermediateStops = 5),
            // inDrive with no rating
            RideData(platform = Platform.INDRIVE, rideValue = 15.0, dropoffDistance = 8.0, passengerRating = 0.0)
        )

        var failures = 0
        edgeCases.forEachIndexed { index, ride ->
            val score = scorer.calculateScore(ride)
            if (score.totalScore < 0.0 || score.totalScore > 100.0 || score.totalScore.isNaN()) {
                println("EDGE CASE $index FAILED: score=${score.totalScore}, ride=$ride")
                failures++
            }
        }

        assertEquals("All edge case scores must be valid (0-100, non-NaN)", 0, failures)
    }

    // =========================================================================
    // TEST 19: Threshold violation penalties are applied
    // =========================================================================
    @Test
    fun test19_thresholdViolationPenalties() {
        // Ride that violates multiple thresholds
        val badRide = RideData(
            platform = Platform.UBER,
            rideValue = 7.0,        // Below minRideValue if set
            rideDuration = 30.0,
            dropoffDistance = 3.0,   // valuePerKm = 7/3 = R$2.33 -- below R$2.00 threshold? No, above
            pickupDistance = 1.0,
            passengerRating = 4.2,  // Below 4.70 threshold
            intermediateStops = 2,  // Above 1 max
            pickupNeighborhood = "Centro",
            dropoffNeighborhood = "Palmital"
        )

        val scorer = RideScorer(
            weights = STANDARD_WEIGHTS,
            driverThresholds = STANDARD_THRESHOLDS
        )

        val score = scorer.calculateScore(badRide)

        println("\n=== Threshold Violations ===")
        score.thresholdViolations.forEach { v ->
            println("${v.criteriaName}: current=${fmt(v.currentValue)} required=${fmt(v.minimumRequired)} penalty=${fmt(v.penaltyApplied)}")
        }

        assertTrue("Bad ride should have violations", score.hasViolations)

        // Rating 4.2 < 4.70 threshold -> should trigger rating violation
        val ratingViolation = score.thresholdViolations.find { it.criteriaName == "Avaliacao" || it.criteriaName.contains("Avalia") }
        assertNotNull("Should have rating violation for 4.2 < 4.70", ratingViolation)

        // Stops = 2 > maxStops = 1 -> should trigger stops violation
        val stopsViolation = score.thresholdViolations.find { it.criteriaName.contains("Parada") }
        assertNotNull("Should have stops violation for 2 > 1", stopsViolation)

        // valuePerHour = 7/30*60 = R$14/h < R$42/h threshold
        val vphViolation = score.thresholdViolations.find { it.criteriaName.contains("Hora") }
        assertNotNull("Should have valuePerHour violation for R$14/h < R$42/h", vphViolation)
    }

    // =========================================================================
    // TEST 20: Ride value distribution in simulation
    // =========================================================================
    @Test
    fun test20_rideValueDistribution() {
        val rides = generateRides()
        val values = rides.map { it.ride.rideValue }

        val avg = values.average()
        val min = values.minOrNull() ?: 0.0
        val max = values.maxOrNull() ?: 0.0
        val stdDev = sqrt(values.map { (it - avg).pow(2) }.average())
        val median = values.sorted()[values.size / 2]

        println("\n=== Ride Value Distribution ===")
        println("Count: ${values.size}")
        println("Range: R$${fmt(min)} - R$${fmt(max)}")
        println("Mean: R$${fmt(avg)} (target: ~R$15)")
        println("Median: R$${fmt(median)}")
        println("StdDev: R$${fmt(stdDev)}")

        // Value stats
        assertTrue("Min value should be >= R$7", min >= 7.0)
        assertTrue("Max value should be <= R$50", max <= 50.0)
        assertTrue("Average should be close to R$15 (within R$5)", abs(avg - 15.0) < 5.0)
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private fun fmt(d: Double): String = "%.2f".format(d)

    private fun percentStr(count: Int, total: Int): String =
        "${"%.1f".format(count.toDouble() / total * 100)}%"
}
