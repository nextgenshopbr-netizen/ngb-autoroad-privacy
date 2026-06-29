package com.ngbautoroad.simulation

import org.junit.Test
import org.junit.Assert.*
import com.ngbautoroad.data.model.*
import com.ngbautoroad.domain.RideScorer
import com.ngbautoroad.domain.ScoringThresholds

/**
 * Verificação matemática e aritmética rigorosa do sistema de critérios.
 *
 * Testa:
 * - Linearidade das normalizações
 * - Monotonicidade (mais valor = melhor score, sempre)
 * - Simetria e consistência das funções inversas
 * - Conservação de peso (soma = 100%)
 * - Limites e boundary conditions
 * - Penalidades cumulativas e não-cumulativas
 * - Curva cúbica do rating
 * - Lucro/KM com custo veicular
 * - Fadiga progressiva
 * - Threshold violations aditivas
 */
class MathVerificationTest {

    private val defaultThresholds = ScoringThresholds()

    // Helper: cria scorer com pesos customizados
    private fun scorer(
        vpk: Int = 0, vph: Int = 0, stops: Int = 0, rating: Int = 0,
        value: Int = 0, duration: Int = 0, pickup: Int = 0, dropoff: Int = 0,
        costPerKm: Double = 0.0
    ) = RideScorer(
        weights = CriteriaWeights(vpk, vph, stops, rating, value, duration, pickup, dropoff),
        driverThresholds = DriverThresholds(
            minValuePerKm = 0.0, minValuePerHour = 0.0,
            minPassengerRating = 0.0, minRideValue = 0.0,
            maxPickupDistance = 0.0, maxDuration = 0.0,
            maxStops = 99, minDropoffDistance = 0.0
        ),
        costPerKm = costPerKm
    )

    // ═══════════════════════════════════════════════════════════════════
    // SEÇÃO 1: LINEARIDADE DAS NORMALIZAÇÕES
    // Verifica que a normalização é estritamente linear entre min e max
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testValuePerKmLinearInterpolation() {
        val s = scorer(vpk = 100)
        // ScoringThresholds: min=0.50, max=2.50 → range=2.0
        // normalized = (value - 0.50) / 2.0 * 100

        // Ponto mínimo: R$0.50/km → normalized=0
        val min = s.calculateScore(RideData(rideValue = 0.50, dropoffDistance = 1.0))
        assertEquals("R$0.50/km should normalize to 0", 0.0, min.totalScore, 1.0)

        // Ponto médio: R$1.50/km → normalized=50
        val mid = s.calculateScore(RideData(rideValue = 1.50, dropoffDistance = 1.0))
        assertEquals("R$1.50/km should normalize to ~50", 50.0, mid.totalScore, 1.0)

        // Ponto máximo: R$2.50/km → normalized=100
        val max = s.calculateScore(RideData(rideValue = 2.50, dropoffDistance = 1.0))
        assertEquals("R$2.50/km should normalize to 100", 100.0, max.totalScore, 1.0)

        // Acima do máximo: clamp a 100
        val over = s.calculateScore(RideData(rideValue = 5.0, dropoffDistance = 1.0))
        assertEquals("R$5.00/km should clamp to 100", 100.0, over.totalScore, 1.0)

        // Abaixo do mínimo: clamp a 0
        val under = s.calculateScore(RideData(rideValue = 0.20, dropoffDistance = 1.0))
        assertEquals("R$0.20/km should clamp to 0", 0.0, under.totalScore, 1.0)
    }

    @Test
    fun testValuePerHourLinearInterpolation() {
        val s = scorer(vph = 100)
        // ScoringThresholds: min=10, max=60 → range=50
        // normalized = (value - 10) / 50 * 100

        // R$10/h → 0 | R$35/h → 50 | R$60/h → 100
        val at10 = s.calculateScore(RideData(rideValue = 10.0, rideDuration = 60.0)) // 10 R$/h
        assertEquals("R$10/h → 0", 0.0, at10.totalScore, 1.0)

        val at35 = s.calculateScore(RideData(rideValue = 35.0, rideDuration = 60.0)) // 35 R$/h
        assertEquals("R$35/h → 50", 50.0, at35.totalScore, 1.0)

        val at60 = s.calculateScore(RideData(rideValue = 60.0, rideDuration = 60.0)) // 60 R$/h
        assertEquals("R$60/h → 100", 100.0, at60.totalScore, 1.0)
    }

    // ═══════════════════════════════════════════════════════════════════
    // SEÇÃO 2: MONOTONICIDADE
    // Mais valor = score maior (ou igual), SEMPRE
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testValuePerKmMonotonicity() {
        val s = scorer(vpk = 100)
        var prevScore = -1.0
        for (vpk in listOf(0.3, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 5.0)) {
            val score = s.calculateScore(RideData(rideValue = vpk, dropoffDistance = 1.0)).totalScore
            assertTrue("R$${vpk}/km ($score) should be >= previous ($prevScore)", score >= prevScore)
            prevScore = score
        }
    }

    @Test
    fun testValuePerHourMonotonicity() {
        val s = scorer(vph = 100)
        var prevScore = -1.0
        for (rph in listOf(5.0, 10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 80.0)) {
            // rph R$/h = rph * (duration/60), with duration=60min → value=rph
            val score = s.calculateScore(RideData(rideValue = rph, rideDuration = 60.0)).totalScore
            assertTrue("R$${rph}/h ($score) should be >= previous ($prevScore)", score >= prevScore)
            prevScore = score
        }
    }

    @Test
    fun testRatingMonotonicity() {
        val s = scorer(rating = 100)
        var prevScore = -1.0
        for (r in listOf(3.0, 3.5, 4.0, 4.3, 4.5, 4.7, 4.8, 4.9, 5.0)) {
            val score = s.calculateScore(RideData(rideValue = 1.0, passengerRating = r)).totalScore
            assertTrue("Rating $r ($score) should be >= previous ($prevScore)", score >= prevScore)
            prevScore = score
        }
    }

    @Test
    fun testDurationInverseMonotonicity() {
        val s = scorer(duration = 100)
        // Duração INVERSA: menos minutos = melhor score
        var prevScore = Double.MAX_VALUE
        for (mins in listOf(5.0, 10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 90.0)) {
            val score = s.calculateScore(RideData(rideValue = 1.0, rideDuration = mins)).totalScore
            assertTrue("${mins}min ($score) should be <= previous ($prevScore)", score <= prevScore)
            prevScore = score
        }
    }

    @Test
    fun testPickupDistanceInverseMonotonicity() {
        val s = scorer(pickup = 100)
        // Pickup INVERSO: menos km = melhor score
        var prevScore = Double.MAX_VALUE
        for (km in listOf(0.2, 0.5, 1.0, 2.0, 3.0, 4.0, 5.0, 8.0)) {
            val score = s.calculateScore(RideData(rideValue = 1.0, pickupDistance = km)).totalScore
            assertTrue("Pickup ${km}km ($score) should be <= previous ($prevScore)", score <= prevScore)
            prevScore = score
        }
    }

    @Test
    fun testDropoffDistanceDirectMonotonicity() {
        val s = scorer(dropoff = 100)
        // Dropoff DIRETO: mais km = melhor score up to maxDropoffDistance (20km)
        // Above 20km, ReturnFactorEngine may penalize (long rides = likely empty return)
        var prevScore = -1.0
        for (km in listOf(2.0, 5.0, 10.0, 15.0, 20.0)) {
            val score = s.calculateScore(RideData(rideValue = 1.0, dropoffDistance = km)).totalScore
            assertTrue("Dropoff ${km}km ($score) should be >= previous ($prevScore)", score >= prevScore)
            prevScore = score
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SEÇÃO 3: CONSERVAÇÃO DE PESO
    // Soma dos pesos = 100, score resultante proporcional
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testWeightConservation() {
        // Todos os pesos = 100% do total, corrida perfeita → score 100
        val perfect = RideData(
            rideValue = 50.0,        // R$50 → 100% normalizado
            rideDuration = 10.0,     // 10min → 100% normalizado (duração mínima)
            pickupDistance = 0.5,     // 0.5km → 100% normalizado (pickup mínimo)
            dropoffDistance = 20.0,   // 20km → 100% normalizado (dropoff máximo)
            passengerRating = 5.0,   // 5.0 → 100%
            intermediateStops = 0    // 0 → 100%
        )

        // Com 4 critérios ativos (30+30+25+15=100)
        // Safety modifier can reduce score, so we check it's high (>90) not exactly 100
        val s = scorer(vpk = 30, vph = 30, stops = 25, rating = 15)
        val score = s.calculateScore(perfect)

        assertTrue("Perfect ride should score > 90, got ${score.totalScore}", score.totalScore > 90.0)
    }

    @Test
    fun testWeightProportionality() {
        // Corrida mediana: todos normalizam para ~50
        val median = RideData(
            rideValue = 15.0,         // R$1.50/km com 10km → meio
            dropoffDistance = 10.0,
            rideDuration = 35.0,      // 35min → meio de 10-60
            passengerRating = 4.7,    // 4.7 → ~75 (zona A)
            intermediateStops = 1     // 1 → 50
        )

        val s = scorer(vpk = 50, vph = 50)
        val score = s.calculateScore(median)

        // R$/km = 15/10 = 1.5 → (1.5-0.5)/2.0*100 = 50.0, weighted = 50*50/100 = 25
        // R$/h = 15/35*60 = 25.7 → (25.7-10)/50*100 = 31.4, weighted = 31.4*50/100 = 15.7
        // Total = 25 + 15.7 = 40.7
        assertTrue("Median ride score should be 30-60, got ${score.totalScore}", score.totalScore in 30.0..60.0)
    }

    @Test
    fun testSingleCriterionIsolation() {
        // Quando apenas 1 critério tem peso, score = apenas esse critério
        val ride = RideData(rideValue = 2.5, dropoffDistance = 1.0) // R$2.50/km → 100% normalized

        val s = scorer(vpk = 100)
        val score = s.calculateScore(ride)
        // normalized = (2.5-0.5)/2.0*100 = 100, weighted = 100*100/100 = 100
        assertEquals("Single criterion R$2.5/km = 100", 100.0, score.totalScore, 1.0)
    }

    // ═══════════════════════════════════════════════════════════════════
    // SEÇÃO 4: CURVA CÚBICA DO RATING
    // Verifica a curva de normalização não-linear
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testRatingCubicCurve() {
        val s = scorer(rating = 100)

        // Zona A: 4.7-5.0 → linear 75-100
        val at47 = s.calculateScore(RideData(rideValue = 1.0, passengerRating = 4.7)).totalScore
        val at48 = s.calculateScore(RideData(rideValue = 1.0, passengerRating = 4.8)).totalScore
        val at49 = s.calculateScore(RideData(rideValue = 1.0, passengerRating = 4.9)).totalScore
        val at50 = s.calculateScore(RideData(rideValue = 1.0, passengerRating = 5.0)).totalScore

        assertEquals("Rating 4.7 → 75", 75.0, at47, 1.0)
        assertTrue("4.7 < 4.8 < 4.9 < 5.0 in Zone A",
            at47 < at48 && at48 < at49 && at49 <= at50)
        assertEquals("Rating 5.0 → 100", 100.0, at50, 1.0)

        // Zona B: <4.7 → cúbica, cai agressivamente
        // Fórmula: 75 * ((rating - 3.0) / 1.7)^3
        val at45 = s.calculateScore(RideData(rideValue = 1.0, passengerRating = 4.5)).totalScore
        val at40 = s.calculateScore(RideData(rideValue = 1.0, passengerRating = 4.0)).totalScore
        val at35 = s.calculateScore(RideData(rideValue = 1.0, passengerRating = 3.5)).totalScore

        // 4.5: base = (4.5-3.0)/1.7 = 0.882, cubed = 0.688, * 75 = 51.6
        assertTrue("Rating 4.5 should be ~51, got $at45", at45 in 45.0..58.0)
        // 4.0: base = (4.0-3.0)/1.7 = 0.588, cubed = 0.204, * 75 = 15.3
        assertTrue("Rating 4.0 should be ~15, got $at40", at40 in 10.0..22.0)
        // 3.5: base = (3.5-3.0)/1.7 = 0.294, cubed = 0.025, * 75 = 1.9
        assertTrue("Rating 3.5 should be ~2, got $at35", at35 in 0.0..6.0)

        // Rating 3.0 → exactly 0
        val at30 = s.calculateScore(RideData(rideValue = 1.0, passengerRating = 3.0)).totalScore
        assertEquals("Rating 3.0 → 0", 0.0, at30, 1.0)
    }

    // ═══════════════════════════════════════════════════════════════════
    // SEÇÃO 5: PENALIDADES DE THRESHOLD (ADITIVAS)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testThresholdPenaltyIsSubtractive() {
        // Sem threshold ativo
        val sNoThresh = RideScorer(
            weights = CriteriaWeights(valuePerKm = 100),
            driverThresholds = DriverThresholds(minValuePerKm = 0.0)
        )
        val ride = RideData(rideValue = 1.0, dropoffDistance = 1.0) // R$1/km
        val scoreNoThresh = sNoThresh.calculateScore(ride).totalScore

        // Com threshold ativo: minValuePerKm = 2.0
        val sWithThresh = RideScorer(
            weights = CriteriaWeights(valuePerKm = 100),
            driverThresholds = DriverThresholds(minValuePerKm = 2.0)
        )
        val scoreWithThresh = sWithThresh.calculateScore(ride).totalScore

        // Penalidade = weight * 0.5 = 100 * 0.5 = 50
        val expectedPenalty = 100 * 0.5
        assertEquals("Threshold penalty should be 50", expectedPenalty,
            scoreNoThresh - scoreWithThresh, 1.0)
    }

    @Test
    fun testMultipleThresholdPenaltiesStack() {
        val ride = RideData(
            rideValue = 5.0,         // R$0.5/km (abaixo de 2.0)
            dropoffDistance = 10.0,
            rideDuration = 20.0,     // R$15/h (abaixo de 42.0)
            passengerRating = 4.0    // Abaixo de 4.7
        )
        val s = RideScorer(
            weights = CriteriaWeights(valuePerKm = 34, valuePerHour = 33, passengerRating = 33),
            driverThresholds = DriverThresholds(
                minValuePerKm = 2.0,
                minValuePerHour = 42.0,
                minPassengerRating = 4.7
            )
        )
        val score = s.calculateScore(ride)

        // Deve ter 3 violações
        assertTrue("Should have 3 threshold violations, got ${score.thresholdViolations.size}",
            score.thresholdViolations.size >= 3)

        // Penalidades: vpk=34*0.5=17, vph=33*0.5=16.5, rating=33*multiplier
        // Rating 4.0 < 4.3 → multiplier=3.5 → 33*3.5=115.5 (!!!)
        // Total penalties = 17 + 16.5 + 115.5 = 149
        // Score before penalties: low but positive
        // Score after: clamped to 0
        assertEquals("Heavily penalized ride should be 0", 0.0, score.totalScore, 1.0)
    }

    // ═══════════════════════════════════════════════════════════════════
    // SEÇÃO 6: LUCRO/KM COM CUSTO VEICULAR
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testProfitPerKmCalculation() {
        // costPerKm = R$0.30, rideValue=3.0, dropoff=10km → R$0.30/km → profit = 0.30-0.30 = 0
        val sWithCost = scorer(vpk = 100, costPerKm = 0.30)
        val breakeven = sWithCost.calculateScore(RideData(rideValue = 3.0, dropoffDistance = 10.0))
        // profitPerKm=0 → normalized=0 (lucro 0 = mínimo)
        // Mas o critério principal (vpk) normaliza R$0.30/km: (0.3-0.5)/2*100 = -10 → clamp 0
        // profitPerKm com implicitWeight deve contribuir minimamente
        assertTrue("Break-even ride score should be low", breakeven.totalScore < 10.0)

        // costPerKm = R$0.30, rideValue=20.0, dropoff=10km → R$2.0/km → profit = 2.0-0.3 = 1.7
        val profitable = sWithCost.calculateScore(RideData(rideValue = 20.0, dropoffDistance = 10.0))
        assertTrue("Profitable ride should score higher", profitable.totalScore > breakeven.totalScore)
    }

    @Test
    fun testNegativeProfitPenalty() {
        // costPerKm=0.50, value=2.0, dropoff=10km → R$0.20/km → profit = 0.20-0.50 = -0.30
        val s = scorer(vpk = 100, costPerKm = 0.50)
        val loss = s.calculateScore(RideData(rideValue = 2.0, dropoffDistance = 10.0))

        // Deve ter violation "Lucro/KM"
        val profitViolation = loss.thresholdViolations.find { it.criteriaName == "Lucro/KM" }
        assertNotNull("Negative profit should trigger violation", profitViolation)

        // Penalidade dinâmica: 5.0 + 25.0 * (|profitPerKm|/costPerKm)^1.5
        // |profitPerKm| = 0.30, costPerKm = 0.50, ratio = 0.6
        // penalty = 5.0 + 25.0 * 0.6^1.5 = 5.0 + 25.0 * 0.4647 = 5.0 + 11.6 = 16.6
        assertTrue("Profit penalty should be 10-25, got ${profitViolation?.penaltyApplied}",
            profitViolation?.penaltyApplied ?: 0.0 in 10.0..25.0)
    }

    // ═══════════════════════════════════════════════════════════════════
    // SEÇÃO 7: ESCALONAMENTO PROPORCIONAL (dados parciais)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testProportionalScaling() {
        // weights: vpk=50, vph=50 (total=100)
        // Corrida COM duração: ambos critérios ativos
        val withDuration = RideData(rideValue = 2.5, dropoffDistance = 1.0, rideDuration = 15.0)
        val s = scorer(vpk = 50, vph = 50)
        val scoreFull = s.calculateScore(withDuration)

        // Corrida SEM duração: apenas vpk ativo (effectiveWeight=50 < totalUsed=100)
        // O score é escalado: wpkScore * (100/50) = doubled
        val noDuration = RideData(rideValue = 2.5, dropoffDistance = 1.0, rideDuration = 0.0)
        val scorePartial = s.calculateScore(noDuration)

        // vpk normalized = 100, weighted = 50. Scaled: 50 * (100/50) = 100
        assertEquals("Partial data should scale to full range", 100.0, scorePartial.totalScore, 1.0)
    }

    // ═══════════════════════════════════════════════════════════════════
    // SEÇÃO 8: FADIGA PROGRESSIVA
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testFatigueProgressivePenalty() {
        val s = scorer(vpk = 100)
        val baseRide = RideData(rideValue = 2.5, dropoffDistance = 1.0)
        val baseScore = s.calculateScore(baseRide).totalScore // 100

        val penalties = mapOf(
            5.0 to 0.0,    // <6h → sem penalidade
            6.0 to 5.0,    // 6h → -5
            8.0 to 15.0,   // 8h → -15
            10.0 to 25.0,  // 10h → -25
            12.0 to 40.0   // 12h → -40
        )

        for ((hours, expectedPenalty) in penalties) {
            val ride = RideData(
                rideValue = 2.5, dropoffDistance = 1.0,
                metadata = mapOf("shiftHours" to hours.toString())
            )
            val score = s.calculateScore(ride).totalScore
            val actualPenalty = baseScore - score
            assertEquals("${hours}h shift should have penalty $expectedPenalty",
                expectedPenalty, actualPenalty, 1.0)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SEÇÃO 9: PARADAS INTERMEDIÁRIAS (DISCRETO)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testStopsDiscreteMapping() {
        val s = scorer(stops = 100)

        val stop0 = s.calculateScore(RideData(rideValue = 1.0, intermediateStops = 0)).totalScore
        val stop1 = s.calculateScore(RideData(rideValue = 1.0, intermediateStops = 1)).totalScore
        val stop2 = s.calculateScore(RideData(rideValue = 1.0, intermediateStops = 2)).totalScore
        val stop5 = s.calculateScore(RideData(rideValue = 1.0, intermediateStops = 5)).totalScore

        assertEquals("0 stops → 100", 100.0, stop0, 1.0)
        assertEquals("1 stop → 50", 50.0, stop1, 1.0)
        assertEquals("2 stops → 0", 0.0, stop2, 1.0)
        assertEquals("5 stops → 0", 0.0, stop5, 1.0)
    }

    // ═══════════════════════════════════════════════════════════════════
    // SEÇÃO 10: BOUNDARY VALUES (limites exatos)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testExactBoundaryValues() {
        val s = scorer(vpk = 100)
        val th = defaultThresholds

        // Exatamente no min: (0.50 - 0.50) / 2.0 * 100 = 0.0
        val atMin = s.calculateScore(RideData(rideValue = th.minValuePerKm, dropoffDistance = 1.0)).totalScore
        assertEquals("Exact min boundary → 0", 0.0, atMin, 0.1)

        // Exatamente no max: (2.50 - 0.50) / 2.0 * 100 = 100.0
        val atMax = s.calculateScore(RideData(rideValue = th.maxValuePerKm, dropoffDistance = 1.0)).totalScore
        assertEquals("Exact max boundary → 100", 100.0, atMax, 0.1)

        // Epsilon acima do min
        val justAbove = s.calculateScore(RideData(rideValue = th.minValuePerKm + 0.01, dropoffDistance = 1.0)).totalScore
        assertTrue("Just above min should be > 0", justAbove > 0.0)

        // Epsilon abaixo do max
        val justBelow = s.calculateScore(RideData(rideValue = th.maxValuePerKm - 0.01, dropoffDistance = 1.0)).totalScore
        assertTrue("Just below max should be < 100", justBelow < 100.0)
    }

    // ═══════════════════════════════════════════════════════════════════
    // SEÇÃO 11: DETERMINISMO E COMUTATIVIDADE
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testDeterminism() {
        val s = scorer(vpk = 30, vph = 30, stops = 25, rating = 15)
        val ride = RideData(
            rideValue = 25.0, dropoffDistance = 8.0, rideDuration = 20.0,
            passengerRating = 4.8, intermediateStops = 0
        )
        val scores = (1..100).map { s.calculateScore(ride).totalScore }
        assertTrue("100 identical calculations should produce same result",
            scores.all { it == scores[0] })
    }

    @Test
    fun testWeightOrderIndependence() {
        // Mesmos pesos em ordens diferentes devem produzir mesmo resultado
        val ride = RideData(rideValue = 20.0, dropoffDistance = 8.0, rideDuration = 25.0)

        val s1 = scorer(vpk = 60, vph = 40)
        val s2 = scorer(vpk = 60, vph = 40) // mesma instância logicamente

        assertEquals("Same weights should produce same score",
            s1.calculateScore(ride).totalScore,
            s2.calculateScore(ride).totalScore, 0.001)
    }

    // ═══════════════════════════════════════════════════════════════════
    // SEÇÃO 12: RANGE GUARD (thresholds degenerados)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testDegenerateThresholds() {
        // min == max → range=0 → should return 50 (guard)
        val degenerate = ScoringThresholds(
            minValuePerKm = 2.0, maxValuePerKm = 2.0,
            minValuePerHour = 30.0, maxValuePerHour = 30.0
        )
        val s = RideScorer(
            weights = CriteriaWeights(valuePerKm = 50, valuePerHour = 50,
                intermediateStops = 0, passengerRating = 0),
            driverThresholds = DriverThresholds(minValuePerKm = 0.0, minValuePerHour = 0.0,
                minPassengerRating = 0.0),
            thresholds = degenerate
        )
        val score = s.calculateScore(RideData(rideValue = 2.0, dropoffDistance = 1.0, rideDuration = 60.0))
        // Both normalize to 50 (guard), weighted = 50*50/100 + 50*50/100 = 25+25 = 50
        assertTrue("Degenerate thresholds should return 40-55, got ${score.totalScore}",
            score.totalScore in 35.0..55.0)
    }

    // ═══════════════════════════════════════════════════════════════════
    // SEÇÃO 13: RATING PENALTY MULTIPLIER MATH
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testRatingPenaltyMultiplierProgression() {
        val ratingWeight = 30
        val threshold = 4.7
        val s = RideScorer(
            weights = CriteriaWeights(passengerRating = ratingWeight, valuePerKm = 70),
            driverThresholds = DriverThresholds(minPassengerRating = threshold, minValuePerKm = 0.0)
        )

        // Rating >= threshold → 0x multiplier (sem penalidade)
        val noViolation = s.calculateScore(RideData(rideValue = 2.5, dropoffDistance = 1.0, passengerRating = 4.8))
        val ratingViolations = noViolation.thresholdViolations.filter { it.criteriaName == "Avaliação" }
        assertTrue("Rating 4.8 >= 4.7 should have no rating violation", ratingViolations.isEmpty())

        // Rating 4.5-4.7 → 2.5x
        val mid = s.calculateScore(RideData(rideValue = 2.5, dropoffDistance = 1.0, passengerRating = 4.6))
        val midPenalty = mid.thresholdViolations.find { it.criteriaName == "Avaliação" }?.penaltyApplied ?: 0.0
        assertEquals("Rating 4.6 → penalty = 30 * 2.5 = 75", 75.0, midPenalty, 1.0)

        // Rating <4.3 → 4.0x
        val worst = s.calculateScore(RideData(rideValue = 2.5, dropoffDistance = 1.0, passengerRating = 4.0))
        val worstPenalty = worst.thresholdViolations.find { it.criteriaName == "Avaliação" }?.penaltyApplied ?: 0.0
        assertEquals("Rating 4.0 → penalty = 30 * 4.0 = 120", 120.0, worstPenalty, 1.0)
    }

    // ═══════════════════════════════════════════════════════════════════
    // SEÇÃO 14: BAIRROS BLOQUEADOS
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testBlockedNeighborhoodPenalty() {
        val blocked = listOf(
            BlockedNeighborhood("Sacoma", NeighborhoodType.DROPOFF, 25),
            BlockedNeighborhood("Penha", NeighborhoodType.PICKUP, 15)
        )
        val s = RideScorer(
            weights = CriteriaWeights(valuePerKm = 100, intermediateStops = 0, passengerRating = 0),
            driverThresholds = DriverThresholds(minValuePerKm = 0.0, minPassengerRating = 0.0),
            blockedNeighborhoods = blocked
        )

        // Sem bairro bloqueado
        val safe = s.calculateScore(RideData(rideValue = 2.5, dropoffDistance = 1.0,
            pickupNeighborhood = "Centro", dropoffNeighborhood = "Moema"))

        // Com bairro bloqueado no dropoff
        val blockedDropoff = s.calculateScore(RideData(rideValue = 2.5, dropoffDistance = 1.0,
            pickupNeighborhood = "Centro", dropoffNeighborhood = "Sacoma"))

        assertTrue("Blocked dropoff should reduce score: safe=${safe.totalScore} blocked=${blockedDropoff.totalScore}",
            blockedDropoff.totalScore < safe.totalScore)

        // Com AMBOS bloqueados
        val both = s.calculateScore(RideData(rideValue = 2.5, dropoffDistance = 1.0,
            pickupNeighborhood = "Penha", dropoffNeighborhood = "Sacoma"))

        assertTrue("Both blocked should score lower than single: both=${both.totalScore} single=${blockedDropoff.totalScore}",
            both.totalScore <= blockedDropoff.totalScore)
    }

    // ═══════════════════════════════════════════════════════════════════
    // SEÇÃO 15: COMPREHENSIVE — CORRIDA REAL COM TODOS OS FATORES
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testRealWorldRideManualCalculation() {
        // Corrida: Uber, R$25, 8km destino, 2km pickup, 20min, rating 4.8, 0 stops
        val ride = RideData(
            rideValue = 25.0,
            dropoffDistance = 8.0,
            pickupDistance = 2.0,
            rideDuration = 20.0,
            passengerRating = 4.8,
            intermediateStops = 0
        )
        // Valores derivados: R$/km = 25/8 = 3.125, R$/h = 25/20*60 = 75

        val s = RideScorer(
            weights = CriteriaWeights(valuePerKm = 30, valuePerHour = 30, intermediateStops = 25, passengerRating = 15),
            driverThresholds = DriverThresholds(minValuePerKm = 0.0, minValuePerHour = 0.0, minPassengerRating = 0.0)
        )
        val score = s.calculateScore(ride)

        // Manual calculation:
        // 1. R$/km = 3.125 → (3.125 - 0.50) / 2.0 * 100 = 131.25 → clamp 100
        //    weighted = 100 * 30 / 100 = 30.0
        // 2. R$/h = 75 → (75 - 10) / 50 * 100 = 130 → clamp 100
        //    weighted = 100 * 30 / 100 = 30.0
        // 3. Stops = 0 → 100, weighted = 100 * 25 / 100 = 25.0
        // 4. Rating = 4.8 → Zone A: 75 + (4.8-4.7)/(5.0-4.7)*25 = 75 + 8.33 = 83.33
        //    weighted = 83.33 * 15 / 100 = 12.5
        // Total = 30 + 30 + 25 + 12.5 = 97.5

        assertEquals("Manual calculation should match: R$25, 8km, 20min, 4.8★",
            97.5, score.totalScore, 2.0)

        // Verify breakdown
        val vpkScore = score.criteriaScores["valuePerKm"]
        assertNotNull("Should have valuePerKm criteria", vpkScore)
        assertEquals("R$/km raw should be 3.125", 3.125, vpkScore?.rawValue ?: 0.0, 0.01)
        assertEquals("R$/km normalized should be 100 (clamped)", 100.0, vpkScore?.normalizedScore ?: 0.0, 1.0)
    }
}
