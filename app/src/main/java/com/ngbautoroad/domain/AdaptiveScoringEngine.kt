package com.ngbautoroad.domain

// ============================================================================
// ARQUIVO: AdaptiveScoringEngine.kt
// VERSÃO: v6.3.7
// RESPONSABILIDADE: Calibração adaptativa dos thresholds do RideScorer usando
//   EWMA (Exponentially Weighted Moving Average).
//   - Aprende os limites reais (min/max) de cada critério com base no histórico
//   - Dá mais peso às corridas recentes (alpha = 0.2)
//   - Persiste em SharedPreferences (< 1KB)
//   - Zero dependência de nuvem, zero aumento de APK, zero consumo extra de RAM
// DEPENDÊNCIAS:
//   - domain/RideScorer.kt → ScoringThresholds (substituído dinamicamente)
//   - data/db/RideHistoryEntity.kt → dados para calibração
// ============================================================================

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.max
import kotlin.math.min

/**
 * Motor de calibração adaptativa via EWMA.
 *
 * Em vez de usar limites fixos (ex: R$0.50-2.50/km), o engine calcula
 * os percentis P10 e P90 do histórico real do motorista e aplica EWMA
 * para suavizar transições e dar peso maior a dados recentes.
 *
 * Resultado: O RideScorer se adapta automaticamente à cidade, horário e
 * perfil de trabalho do motorista sem configuração manual.
 *
 * Consumo: ~200 bytes em SharedPreferences, 0 alocações extras em runtime.
 */
class AdaptiveScoringEngine(context: Context? = null) {

    companion object {
        private const val PREFS_NAME = "adaptive_scoring"
        // Alpha = fator de suavização EWMA (0.2 = 20% peso para novo valor, 80% para histórico)
        // Quanto maior, mais rápido se adapta (mas mais volátil)
        private const val ALPHA = 0.2
        // Mínimo de corridas para começar a adaptar (antes disso, usa defaults)
        private const val MIN_RIDES_TO_ADAPT = 30
    }

    private val prefs: SharedPreferences? = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Estado EWMA persistido
    private var ewmaValuePerKmMin: Double = 0.50
    private var ewmaValuePerKmMax: Double = 2.50
    private var ewmaValuePerHourMin: Double = 10.0
    private var ewmaValuePerHourMax: Double = 40.0
    private var ewmaRideValueMin: Double = 5.0
    private var ewmaRideValueMax: Double = 50.0
    private var ewmaPickupDistMin: Double = 0.5
    private var ewmaPickupDistMax: Double = 5.0
    private var ewmaDropoffDistMin: Double = 2.0
    private var ewmaDropoffDistMax: Double = 20.0
    private var ewmaDurationMin: Double = 10.0
    private var ewmaDurationMax: Double = 60.0
    private var calibratedRideCount: Int = 0

    init {
        loadFromPrefs()
    }

    /**
     * Calibra os thresholds com base em uma lista de valores históricos.
     * Deve ser chamado periodicamente (ex: a cada 50 corridas novas ou ao abrir a aba IA).
     *
     * @param valuesPerKm Lista de R$/km das últimas corridas COMPLETED
     * @param valuesPerHour Lista de R$/hora
     * @param rideValues Lista de valores absolutos
     * @param pickupDistances Lista de distâncias de embarque
     * @param dropoffDistances Lista de distâncias de desembarque
     * @param durations Lista de durações em minutos
     */
    fun calibrate(
        valuesPerKm: List<Double>,
        valuesPerHour: List<Double>,
        rideValues: List<Double>,
        pickupDistances: List<Double>,
        dropoffDistances: List<Double>,
        durations: List<Double>
    ) {
        if (valuesPerKm.size < MIN_RIDES_TO_ADAPT) return

        // Calcular percentis P10 e P90 para cada métrica
        ewmaValuePerKmMin = ewmaUpdate(ewmaValuePerKmMin, percentile(valuesPerKm, 10))
        ewmaValuePerKmMax = ewmaUpdate(ewmaValuePerKmMax, percentile(valuesPerKm, 90))

        if (valuesPerHour.isNotEmpty()) {
            ewmaValuePerHourMin = ewmaUpdate(ewmaValuePerHourMin, percentile(valuesPerHour, 10))
            ewmaValuePerHourMax = ewmaUpdate(ewmaValuePerHourMax, percentile(valuesPerHour, 90))
        }

        if (rideValues.isNotEmpty()) {
            ewmaRideValueMin = ewmaUpdate(ewmaRideValueMin, percentile(rideValues, 10))
            ewmaRideValueMax = ewmaUpdate(ewmaRideValueMax, percentile(rideValues, 90))
        }

        if (pickupDistances.isNotEmpty()) {
            ewmaPickupDistMin = ewmaUpdate(ewmaPickupDistMin, percentile(pickupDistances, 10))
            ewmaPickupDistMax = ewmaUpdate(ewmaPickupDistMax, percentile(pickupDistances, 90))
        }

        if (dropoffDistances.isNotEmpty()) {
            ewmaDropoffDistMin = ewmaUpdate(ewmaDropoffDistMin, percentile(dropoffDistances, 10))
            ewmaDropoffDistMax = ewmaUpdate(ewmaDropoffDistMax, percentile(dropoffDistances, 90))
        }

        if (durations.isNotEmpty()) {
            ewmaDurationMin = ewmaUpdate(ewmaDurationMin, percentile(durations, 10))
            ewmaDurationMax = ewmaUpdate(ewmaDurationMax, percentile(durations, 90))
        }

        calibratedRideCount = valuesPerKm.size
        saveToPrefs()
    }

    /**
     * Retorna ScoringThresholds adaptados.
     * Se não há dados suficientes, retorna os defaults.
     */
    fun getAdaptiveThresholds(): ScoringThresholds {
        if (calibratedRideCount < MIN_RIDES_TO_ADAPT) {
            return ScoringThresholds() // Defaults fixos
        }

        return ScoringThresholds(
            minValuePerKm = ensureRange(ewmaValuePerKmMin, 0.10, ewmaValuePerKmMax - 0.10),
            maxValuePerKm = ensureRange(ewmaValuePerKmMax, ewmaValuePerKmMin + 0.10, 10.0),
            minValuePerHour = ensureRange(ewmaValuePerHourMin, 1.0, ewmaValuePerHourMax - 1.0),
            maxValuePerHour = ensureRange(ewmaValuePerHourMax, ewmaValuePerHourMin + 1.0, 200.0),
            minRideValue = ensureRange(ewmaRideValueMin, 1.0, ewmaRideValueMax - 1.0),
            maxRideValue = ensureRange(ewmaRideValueMax, ewmaRideValueMin + 1.0, 500.0),
            minDuration = ensureRange(ewmaDurationMin, 1.0, ewmaDurationMax - 1.0),
            maxDuration = ensureRange(ewmaDurationMax, ewmaDurationMin + 1.0, 180.0),
            minPickupDistance = ensureRange(ewmaPickupDistMin, 0.1, ewmaPickupDistMax - 0.1),
            maxPickupDistance = ensureRange(ewmaPickupDistMax, ewmaPickupDistMin + 0.1, 30.0),
            minDropoffDistance = ensureRange(ewmaDropoffDistMin, 0.1, ewmaDropoffDistMax - 0.1),
            maxDropoffDistance = ensureRange(ewmaDropoffDistMax, ewmaDropoffDistMin + 0.1, 100.0)
        )
    }

    /**
     * Retorna true se a calibração está ativa (dados suficientes).
     */
    fun isCalibrated(): Boolean = calibratedRideCount >= MIN_RIDES_TO_ADAPT

    fun getCalibratedRideCount(): Int = calibratedRideCount

    // ========================================================================
    // Helpers
    // ========================================================================

    /**
     * Atualização EWMA: novo_valor = alpha * observação + (1 - alpha) * valor_anterior
     */
    private fun ewmaUpdate(current: Double, observation: Double): Double {
        return ALPHA * observation + (1 - ALPHA) * current
    }

    /**
     * Calcula o percentil P de uma lista ordenada.
     * Ex: percentile(lista, 10) = P10 (10% dos valores estão abaixo)
     */
    private fun percentile(data: List<Double>, p: Int): Double {
        if (data.isEmpty()) return 0.0
        val sorted = data.sorted()
        val index = (p / 100.0 * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)
        return sorted[index]
    }

    /**
     * Garante que o valor está dentro de [min, max]
     */
    private fun ensureRange(value: Double, minVal: Double, maxVal: Double): Double {
        return max(minVal, min(maxVal, value))
    }

    // ========================================================================
    // Persistência (SharedPreferences — < 200 bytes)
    // ========================================================================

    private fun loadFromPrefs() {
        prefs ?: return
        ewmaValuePerKmMin = prefs.getFloat("vkm_min", 0.50f).toDouble()
        ewmaValuePerKmMax = prefs.getFloat("vkm_max", 2.50f).toDouble()
        ewmaValuePerHourMin = prefs.getFloat("vph_min", 10.0f).toDouble()
        ewmaValuePerHourMax = prefs.getFloat("vph_max", 40.0f).toDouble()
        ewmaRideValueMin = prefs.getFloat("rv_min", 5.0f).toDouble()
        ewmaRideValueMax = prefs.getFloat("rv_max", 50.0f).toDouble()
        ewmaPickupDistMin = prefs.getFloat("pd_min", 0.5f).toDouble()
        ewmaPickupDistMax = prefs.getFloat("pd_max", 5.0f).toDouble()
        ewmaDropoffDistMin = prefs.getFloat("dd_min", 2.0f).toDouble()
        ewmaDropoffDistMax = prefs.getFloat("dd_max", 20.0f).toDouble()
        ewmaDurationMin = prefs.getFloat("dur_min", 10.0f).toDouble()
        ewmaDurationMax = prefs.getFloat("dur_max", 60.0f).toDouble()
        calibratedRideCount = prefs.getInt("ride_count", 0)
    }

    private fun saveToPrefs() {
        prefs?.edit()?.apply {
            putFloat("vkm_min", ewmaValuePerKmMin.toFloat())
            putFloat("vkm_max", ewmaValuePerKmMax.toFloat())
            putFloat("vph_min", ewmaValuePerHourMin.toFloat())
            putFloat("vph_max", ewmaValuePerHourMax.toFloat())
            putFloat("rv_min", ewmaRideValueMin.toFloat())
            putFloat("rv_max", ewmaRideValueMax.toFloat())
            putFloat("pd_min", ewmaPickupDistMin.toFloat())
            putFloat("pd_max", ewmaPickupDistMax.toFloat())
            putFloat("dd_min", ewmaDropoffDistMin.toFloat())
            putFloat("dd_max", ewmaDropoffDistMax.toFloat())
            putFloat("dur_min", ewmaDurationMin.toFloat())
            putFloat("dur_max", ewmaDurationMax.toFloat())
            putInt("ride_count", calibratedRideCount)
            apply()
        }
    }
}
