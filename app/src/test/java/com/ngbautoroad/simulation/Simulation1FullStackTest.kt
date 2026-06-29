package com.ngbautoroad.simulation

import org.junit.Test
import org.junit.Assert.*
import com.ngbautoroad.data.model.*
import com.ngbautoroad.domain.RideScorer
import com.ngbautoroad.domain.ScoringThresholds
import com.ngbautoroad.domain.AdaptiveScoringEngine
import kotlin.random.Random

/**
 * Simulation 1: "Carlos" -- experienced Uber driver in Sao Paulo, 30 days.
 *
 * Exercises the domain layer (RideScorer, AdaptiveScoringEngine) with realistic
 * ride data covering all scoring criteria, threshold violations, edge cases,
 * and adaptive calibration.
 *
 * Fixed seed ensures reproducibility across runs.
 */
class Simulation1FullStackTest {

    private val neighborhoods = listOf(
        "Centro", "Pinheiros", "Moema", "Itaim Bibi", "Vila Mariana",
        "Santana", "Lapa", "Perdizes", "Brooklin", "Butanta", "Jardim America",
        "Campo Belo", "Santo Amaro", "Sacoma", "Penha", "Sao Miguel"
    )

    // ========================================================================
    // MAIN SIMULATION: 30 days of realistic driver activity
    // ========================================================================
    @Test
    fun simulate30DaysExperiencedDriver() {
        val random = Random(42)

        // Setup: driver profile with default weights and thresholds
        val weights = CriteriaWeights() // valuePerKm=30, valuePerHour=30, stops=25, rating=15
        val thresholds = DriverThresholds() // minValuePerKm=2.00, minValuePerHour=42.00, etc.
        val scorer = RideScorer(weights = weights, driverThresholds = thresholds)

        var totalEarnings = 0.0
        var totalRides = 0
        var acceptedRides = 0
        var refusedRides = 0
        var totalKm = 0.0
        var totalMinutes = 0.0
        val scoreHistory = mutableListOf<Double>()
        val platformCounts = mutableMapOf<Platform, Int>()

        // Data collectors for AdaptiveScoringEngine calibration
        val allValuesPerKm = mutableListOf<Double>()
        val allValuesPerHour = mutableListOf<Double>()
        val allRideValues = mutableListOf<Double>()
        val allPickupDistances = mutableListOf<Double>()
        val allDropoffDistances = mutableListOf<Double>()
        val allDurations = mutableListOf<Double>()

        // === 30 DAYS OF SIMULATION ===
        for (day in 1..30) {
            val isWeekend = day % 7 == 0 || day % 7 == 6
            val ridesPerDay = if (isWeekend) random.nextInt(8, 15) else random.nextInt(12, 22)

            val shiftHours = if (isWeekend) random.nextDouble(8.0, 12.0) else random.nextDouble(6.0, 10.0)
            var dayEarnings = 0.0
            var dayRides = 0

            for (rideNum in 1..ridesPerDay) {
                // Platform distribution: 60% Uber, 30% 99, 10% inDrive
                val platform = when (random.nextInt(10)) {
                    in 0..5 -> Platform.UBER
                    in 6..8 -> Platform.NINETY_NINE
                    else -> Platform.INDRIVE
                }
                platformCounts[platform] = (platformCounts[platform] ?: 0) + 1

                val hour = 6 + (rideNum * shiftHours / ridesPerDay).toInt()
                val isPeakHour = hour in 7..9 || hour in 17..20

                val baseValue = when {
                    isPeakHour -> random.nextDouble(15.0, 45.0)
                    hour >= 22 || hour <= 5 -> random.nextDouble(20.0, 60.0)
                    else -> random.nextDouble(8.0, 30.0)
                }

                val dropoffKm = random.nextDouble(2.0, 18.0)
                val pickupKm = random.nextDouble(0.5, 4.0)
                val duration = (dropoffKm * random.nextDouble(2.0, 4.5)).coerceAtLeast(5.0)
                val rating = when (random.nextInt(20)) {
                    0 -> random.nextDouble(3.5, 4.0)        // 5% bad
                    in 1..3 -> random.nextDouble(4.0, 4.5)  // 15% low
                    else -> random.nextDouble(4.5, 5.0)      // 80% good
                }
                val stops = if (random.nextInt(10) == 0) random.nextInt(1, 3) else 0

                val rideType = when (platform) {
                    Platform.UBER -> RideType.UBER_X
                    Platform.NINETY_NINE -> RideType.NINETY_NINE_POP
                    Platform.INDRIVE -> RideType.INDRIVE_STANDARD
                    else -> RideType.UNKNOWN
                }

                // Include fatigue metadata for late-shift rides
                val currentShiftHours = (rideNum.toDouble() / ridesPerDay) * shiftHours
                val metadata = if (currentShiftHours > 5.0) {
                    mapOf("shiftHours" to "%.1f".format(currentShiftHours))
                } else null

                val ride = RideData(
                    platform = platform,
                    rideType = rideType,
                    rideValue = baseValue,
                    rideDuration = duration,
                    pickupDistance = pickupKm,
                    dropoffDistance = dropoffKm,
                    passengerRating = rating,
                    intermediateStops = stops,
                    pickupNeighborhood = neighborhoods[random.nextInt(neighborhoods.size)],
                    dropoffNeighborhood = neighborhoods[random.nextInt(neighborhoods.size)],
                    isSimulation = true,
                    metadata = metadata
                )

                // Score the ride
                val score = scorer.calculateScore(ride)
                assertNotNull("Score should not be null", score)
                assertTrue(
                    "Score should be 0-100, got ${score.totalScore}",
                    score.totalScore in 0.0..100.0
                )

                // Verify criteria breakdown is non-empty for rides with valid data
                if (ride.dropoffDistance > 0 && ride.rideDuration > 0) {
                    assertTrue(
                        "Criteria scores should not be empty for valid ride",
                        score.criteriaScores.isNotEmpty()
                    )
                }

                scoreHistory.add(score.totalScore)

                // Collect data for adaptive calibration
                if (ride.dropoffDistance > 0) allValuesPerKm.add(ride.valuePerKm)
                if (ride.rideDuration > 0) allValuesPerHour.add(ride.valuePerHour)
                allRideValues.add(ride.rideValue)
                allPickupDistances.add(ride.pickupDistance)
                allDropoffDistances.add(ride.dropoffDistance)
                allDurations.add(ride.rideDuration)

                // AutoPilot decision simulation
                val accepted = when {
                    score.totalScore >= 85 -> true
                    score.totalScore <= 40 -> false
                    else -> random.nextBoolean()
                }

                totalRides++
                if (accepted) {
                    acceptedRides++
                    dayRides++
                    dayEarnings += baseValue
                    totalEarnings += baseValue
                    totalKm += dropoffKm + pickupKm
                    totalMinutes += duration
                } else {
                    refusedRides++
                }
            }

            assertTrue("Day $day should have non-negative rides", dayRides >= 0)
        }

        // === FINAL ASSERTIONS ===
        assertTrue("Should have > 300 total rides in 30 days, got $totalRides", totalRides > 300)
        assertTrue("Should have accepted some rides", acceptedRides > 0)
        assertTrue("Should have refused some rides", refusedRides > 0)

        val acceptanceRate = acceptedRides.toDouble() / totalRides
        assertTrue(
            "Acceptance rate should be 20-95%, got ${"%.1f".format(acceptanceRate * 100)}%",
            acceptanceRate in 0.20..0.95
        )
        assertTrue("Total earnings should be > R$2000, got R$${"%.2f".format(totalEarnings)}", totalEarnings > 2000.0)

        val avgScore = scoreHistory.average()
        assertTrue("Average score should be 10-90, got $avgScore", avgScore in 10.0..90.0)

        // Score distribution -- expect a mix
        val greenRides = scoreHistory.count { it >= 70 }
        val yellowRides = scoreHistory.count { it in 50.0..69.9 }
        val redRides = scoreHistory.count { it < 50 }
        assertTrue(
            "Should have mix of scores: green=$greenRides yellow=$yellowRides red=$redRides",
            greenRides > 0 && redRides > 0
        )

        // Platform distribution
        assertTrue(
            "Uber should be majority: ${platformCounts[Platform.UBER]}/$totalRides",
            (platformCounts[Platform.UBER] ?: 0) > totalRides * 0.4
        )

        // === ADAPTIVE SCORING ENGINE CALIBRATION TEST ===
        // AdaptiveScoringEngine with null context (no SharedPreferences, uses defaults)
        val adaptiveEngine = AdaptiveScoringEngine(null)
        assertFalse("Should not be calibrated initially", adaptiveEngine.isCalibrated())
        assertEquals("Initial ride count should be 0", 0, adaptiveEngine.getCalibratedRideCount())

        // Calibrate with simulation data
        adaptiveEngine.calibrate(
            valuesPerKm = allValuesPerKm,
            valuesPerHour = allValuesPerHour,
            rideValues = allRideValues,
            pickupDistances = allPickupDistances,
            dropoffDistances = allDropoffDistances,
            durations = allDurations
        )

        // After calibration with 300+ rides, engine should be calibrated
        assertTrue(
            "Should be calibrated after ${allValuesPerKm.size} rides",
            adaptiveEngine.isCalibrated()
        )

        val adaptiveThresholds = adaptiveEngine.getAdaptiveThresholds()
        assertTrue("Adaptive minValuePerKm should be positive", adaptiveThresholds.minValuePerKm > 0)
        assertTrue(
            "Adaptive max should be > min for valuePerKm",
            adaptiveThresholds.maxValuePerKm > adaptiveThresholds.minValuePerKm
        )
        assertTrue(
            "Adaptive max should be > min for valuePerHour",
            adaptiveThresholds.maxValuePerHour > adaptiveThresholds.minValuePerHour
        )

        // Score with adaptive thresholds and compare
        val adaptiveScorer = RideScorer(
            weights = weights,
            driverThresholds = thresholds,
            thresholds = adaptiveThresholds
        )
        val sampleRide = RideData(
            platform = Platform.UBER,
            rideType = RideType.UBER_X,
            rideValue = 25.0,
            rideDuration = 20.0,
            pickupDistance = 2.0,
            dropoffDistance = 10.0,
            passengerRating = 4.8,
            isSimulation = true
        )
        val defaultScore = scorer.calculateScore(sampleRide)
        val adaptiveScore = adaptiveScorer.calculateScore(sampleRide)
        // Both should produce valid scores (may differ due to calibrated thresholds)
        assertTrue("Default score valid", defaultScore.totalScore in 0.0..100.0)
        assertTrue("Adaptive score valid", adaptiveScore.totalScore in 0.0..100.0)

        println("=== SIMULATION 1 RESULTS ===")
        println("Days: 30 | Total rides: $totalRides")
        println("Accepted: $acceptedRides | Refused: $refusedRides | Rate: ${"%.1f".format(acceptanceRate * 100)}%")
        println("Earnings: R$ ${"%.2f".format(totalEarnings)} | Avg/day: R$ ${"%.2f".format(totalEarnings / 30)}")
        println("Total KM: ${"%.1f".format(totalKm)} | Avg R$/km: ${"%.2f".format(totalEarnings / totalKm)}")
        println("Avg score: ${"%.1f".format(avgScore)} | Green: $greenRides | Yellow: $yellowRides | Red: $redRides")
        println("Platforms: ${platformCounts.entries.joinToString { "${it.key.displayName}=${it.value}" }}")
        println("Adaptive calibration: ${adaptiveEngine.getCalibratedRideCount()} rides")
        println("Adaptive thresholds: R$/km=[${adaptiveThresholds.minValuePerKm}, ${adaptiveThresholds.maxValuePerKm}]")
        println("Default sample score: ${defaultScore.totalScore} | Adaptive sample score: ${adaptiveScore.totalScore}")
    }

    // ========================================================================
    // EDGE CASES
    // ========================================================================

    @Test
    fun edgeCase_rideWithZeroValue() {
        val scorer = RideScorer(weights = CriteriaWeights())
        val ride = RideData(
            rideValue = 0.0,
            rideDuration = 10.0,
            pickupDistance = 1.0,
            dropoffDistance = 5.0,
            passengerRating = 4.8,
            isSimulation = true
        )
        val score = scorer.calculateScore(ride)
        assertTrue("Zero-value ride score should be 0-100, got ${score.totalScore}", score.totalScore in 0.0..100.0)
        // With rideValue=0, valuePerKm=0 and valuePerHour=0, those criteria should score low
        println("Edge case zero value: score=${score.totalScore}, criteria=${score.criteriaScores.keys}")
    }

    @Test
    fun edgeCase_rideWithAllZeros() {
        val scorer = RideScorer(weights = CriteriaWeights())
        val ride = RideData(isSimulation = true)
        val score = scorer.calculateScore(ride)
        assertTrue("All-zeros ride score should be 0-100, got ${score.totalScore}", score.totalScore in 0.0..100.0)
        // With all zeros, most criteria are skipped (dropoffDistance=0, rideDuration=0, rating=0)
        // Only intermediateStops (weight=25) should be calculated: 0 stops = 100 normalized
        println("Edge case all zeros: score=${score.totalScore}, criteria=${score.criteriaScores.size}")
    }

    @Test
    fun edgeCase_extremelyHighValues() {
        val scorer = RideScorer(weights = CriteriaWeights())
        val ride = RideData(
            platform = Platform.UBER,
            rideType = RideType.UBER_BLACK,
            rideValue = 500.0,
            rideDuration = 120.0,
            pickupDistance = 0.1,
            dropoffDistance = 50.0,
            passengerRating = 5.0,
            intermediateStops = 0,
            isSimulation = true
        )
        val score = scorer.calculateScore(ride)
        assertTrue("High-value ride score should be 0-100, got ${score.totalScore}", score.totalScore in 0.0..100.0)
        // R$500 / 50km = R$10/km, R$500/120min*60 = R$250/hr -- both should cap at 100 normalized
        assertTrue("High-value ride should score well (>50), got ${score.totalScore}", score.totalScore > 50)
        println("Edge case extreme high: score=${score.totalScore}")
    }

    @Test
    fun edgeCase_veryLowRating() {
        val scorer = RideScorer(weights = CriteriaWeights(), driverThresholds = DriverThresholds())
        val ride = RideData(
            rideValue = 30.0,
            rideDuration = 15.0,
            dropoffDistance = 10.0,
            pickupDistance = 1.0,
            passengerRating = 3.0, // Very low rating
            isSimulation = true
        )
        val score = scorer.calculateScore(ride)
        assertTrue("Low-rating ride score should be 0-100, got ${score.totalScore}", score.totalScore in 0.0..100.0)
        // Rating 3.0 should trigger heavy penalty (multiplier 4.0x on weight 15)
        assertTrue("Low-rating ride should have violations", score.hasViolations)
        println("Edge case low rating: score=${score.totalScore}, violations=${score.thresholdViolations.size}")
    }

    @Test
    fun edgeCase_manyStops() {
        val scorer = RideScorer(weights = CriteriaWeights(), driverThresholds = DriverThresholds())
        val ride = RideData(
            rideValue = 20.0,
            rideDuration = 30.0,
            dropoffDistance = 8.0,
            pickupDistance = 2.0,
            passengerRating = 4.9,
            intermediateStops = 5, // Many stops
            isSimulation = true
        )
        val score = scorer.calculateScore(ride)
        assertTrue("Many-stops ride score should be 0-100, got ${score.totalScore}", score.totalScore in 0.0..100.0)
        // 5 stops > maxStops(1) should trigger violation
        val stopsViolation = score.thresholdViolations.find { it.criteriaName == "Paradas" }
        assertNotNull("Should have stops violation", stopsViolation)
        println("Edge case many stops: score=${score.totalScore}, stops penalty=${stopsViolation?.penaltyApplied}")
    }

    @Test
    fun edgeCase_fatigueMetadata() {
        val scorer = RideScorer(weights = CriteriaWeights())
        val ride = RideData(
            rideValue = 25.0,
            rideDuration = 15.0,
            dropoffDistance = 8.0,
            pickupDistance = 1.5,
            passengerRating = 4.9,
            isSimulation = true,
            metadata = mapOf("shiftHours" to "12.0") // Extreme fatigue
        )
        val score = scorer.calculateScore(ride)
        assertTrue("Fatigued ride score should be 0-100, got ${score.totalScore}", score.totalScore in 0.0..100.0)
        val fatigueViolation = score.thresholdViolations.find { it.criteriaName == "Fadiga" }
        assertNotNull("Should have fatigue violation at 12h shift", fatigueViolation)
        assertEquals("Fatigue penalty at 12h should be 40.0", 40.0, fatigueViolation!!.penaltyApplied, 0.01)
        println("Edge case fatigue: score=${score.totalScore}, fatigue penalty=${fatigueViolation.penaltyApplied}")
    }

    @Test
    fun edgeCase_blockedNeighborhood() {
        val blocked = listOf(
            BlockedNeighborhood("Penha", NeighborhoodType.DROPOFF, penaltyWeight = 30),
            BlockedNeighborhood("Sacoma", NeighborhoodType.PICKUP, penaltyWeight = 25)
        )
        val scorer = RideScorer(
            weights = CriteriaWeights(),
            driverThresholds = DriverThresholds(),
            blockedNeighborhoods = blocked
        )

        // Ride going TO a blocked dropoff neighborhood
        val ride1 = RideData(
            rideValue = 25.0,
            rideDuration = 15.0,
            dropoffDistance = 8.0,
            pickupDistance = 1.5,
            passengerRating = 4.9,
            pickupNeighborhood = "Pinheiros",
            dropoffNeighborhood = "Penha",
            isSimulation = true
        )
        val score1 = scorer.calculateScore(ride1)

        // Same ride but to non-blocked neighborhood
        val ride2 = ride1.copy(dropoffNeighborhood = "Moema")
        val score2 = scorer.calculateScore(ride2)

        assertTrue(
            "Blocked dropoff should score lower: blocked=${score1.totalScore} vs normal=${score2.totalScore}",
            score1.totalScore < score2.totalScore
        )
        println("Edge case blocked neighborhood: blocked=${score1.totalScore}, normal=${score2.totalScore}, diff=${score2.totalScore - score1.totalScore}")
    }

    @Test
    fun edgeCase_costPerKmProfitCalculation() {
        // Test profit/km criterion with vehicle cost configured
        val scorer = RideScorer(
            weights = CriteriaWeights(),
            driverThresholds = DriverThresholds(),
            costPerKm = 0.80 // R$0.80/km vehicle cost
        )

        // Profitable ride: R$25/10km = R$2.50/km, profit = R$1.70/km
        val profitableRide = RideData(
            rideValue = 25.0,
            rideDuration = 15.0,
            dropoffDistance = 10.0,
            pickupDistance = 1.0,
            passengerRating = 4.9,
            isSimulation = true
        )
        val profitScore = scorer.calculateScore(profitableRide)
        assertTrue("Profitable ride should have profitPerKm criteria",
            profitScore.criteriaScores.containsKey("profitPerKm"))

        // Loss ride: R$3/10km = R$0.30/km, profit = -R$0.50/km
        val lossRide = RideData(
            rideValue = 3.0,
            rideDuration = 15.0,
            dropoffDistance = 10.0,
            pickupDistance = 1.0,
            passengerRating = 4.9,
            isSimulation = true
        )
        val lossScore = scorer.calculateScore(lossRide)
        val lossViolation = lossScore.thresholdViolations.find { it.criteriaName == "Lucro/KM" }
        assertNotNull("Loss ride should have profitPerKm violation", lossViolation)
        println("Edge case profit: profitable=${profitScore.totalScore}, loss=${lossScore.totalScore}")
    }

    @Test
    fun edgeCase_allPlatforms() {
        val scorer = RideScorer(weights = CriteriaWeights())
        for (platform in Platform.values()) {
            val ride = RideData(
                platform = platform,
                rideValue = 20.0,
                rideDuration = 15.0,
                dropoffDistance = 8.0,
                pickupDistance = 1.5,
                passengerRating = 4.8,
                isSimulation = true
            )
            val score = scorer.calculateScore(ride)
            assertTrue(
                "Score for ${platform.displayName} should be 0-100, got ${score.totalScore}",
                score.totalScore in 0.0..100.0
            )
        }
        println("Edge case all platforms: all scored successfully")
    }
}
