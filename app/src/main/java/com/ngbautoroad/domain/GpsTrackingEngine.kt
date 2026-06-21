package com.ngbautoroad.domain

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log

// ============================================================================
// ARQUIVO: GpsTrackingEngine.kt
// VERSÃO: v6.6.0
// RESPONSABILIDADE: Odômetro real via GPS + Acelerômetro + Validação KM
//   - Rastreia KM real durante o turno (GPS já ativo por causa do Uber)
//   - Usa acelerômetro para detectar movimento sem GPS (túneis, garagens)
//   - Compara KM medido pelo GPS vs KM informado pela Uber (validação)
//   - Sem custo extra de bateria: GPS já está ativo para o Uber funcionar
// DEPENDENTES:
//   - OdometerEngine.kt → alimenta odômetro estimado com dados reais
//   - RideLifecycleManager.kt → registra KM real por corrida
//   - FinanceDRE.kt → usa KM real para cálculos financeiros
//   - DashboardTab.kt → exibe comparação GPS vs Uber
// ============================================================================

/**
 * Estado do rastreamento GPS durante o turno.
 */
data class GpsTrackingState(
    val isTracking: Boolean = false,
    val totalDistanceKm: Double = 0.0,        // KM total do turno (GPS)
    val rideDistanceKm: Double = 0.0,         // KM da corrida atual (GPS)
    val deadDistanceKm: Double = 0.0,         // KM morto (entre corridas)
    val lastLatitude: Double = 0.0,
    val lastLongitude: Double = 0.0,
    val lastUpdateMs: Long = 0L,
    val pointsCollected: Int = 0,
    val isInRide: Boolean = false,            // Se está em corrida ativa
    val avgSpeedKmh: Double = 0.0,
    val maxSpeedKmh: Double = 0.0,
    val isMoving: Boolean = false             // Detectado pelo acelerômetro
)

/**
 * Resultado da validação de KM (GPS vs Uber).
 */
data class KmValidation(
    val gpsDistanceKm: Double = 0.0,
    val uberReportedKm: Double = 0.0,
    val differenceKm: Double = 0.0,
    val differencePercent: Double = 0.0,
    val isUberUnderreporting: Boolean = false,
    val confidence: Double = 0.0  // 0-1, baseado em qualidade do GPS
) {
    val summary: String
        get() = when {
            differencePercent > 10.0 -> "⚠️ Uber reportou ${String.format("%.1f", differencePercent)}% MENOS que o GPS"
            differencePercent < -10.0 -> "✅ Uber reportou ${String.format("%.1f", -differencePercent)}% MAIS que o GPS"
            else -> "✅ KM consistente (diferença < 10%)"
        }
}

/**
 * Motor de rastreamento GPS para odômetro real.
 * Usa LocationManager (passivo quando possível) + acelerômetro.
 * NÃO consome bateria extra porque o GPS já está ativo para Uber/99.
 */
class GpsTrackingEngine(private val context: Context) : LocationListener, SensorEventListener {

    companion object {
        private const val TAG = "GpsTrackingEngine"
        private const val MIN_DISTANCE_METERS = 10f    // Mínimo 10m entre pontos
        private const val MIN_TIME_MS = 3000L          // Mínimo 3s entre updates
        private const val MIN_ACCURACY_METERS = 50f    // Ignorar pontos com accuracy > 50m
        private const val SPEED_THRESHOLD_MS = 1.5f    // 1.5 m/s = 5.4 km/h (andando rápido)
        private const val ACCEL_MOVEMENT_THRESHOLD = 1.2f // m/s² acima de gravidade
    }

    private val prefs: SharedPreferences = context.getSharedPreferences("gps_tracking_prefs", Context.MODE_PRIVATE)
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    private var state = GpsTrackingState()
    private var rideStartDistanceKm = 0.0  // Distância total quando a corrida começou
    private val handler = Handler(Looper.getMainLooper())

    // Histórico de validações
    private val validations = mutableListOf<KmValidation>()

    /**
     * Inicia o rastreamento GPS (chamado ao iniciar turno).
     * Usa PASSIVE_PROVIDER primeiro (zero bateria) e GPS_PROVIDER como fallback.
     */
    fun startTracking() {
        if (state.isTracking) return

        try {
            // Tentar provider passivo primeiro (usa GPS do Uber sem custo)
            locationManager?.requestLocationUpdates(
                LocationManager.PASSIVE_PROVIDER,
                MIN_TIME_MS,
                MIN_DISTANCE_METERS,
                this,
                Looper.getMainLooper()
            )

            // Fallback: GPS ativo (baixo consumo com intervalo de 3s)
            if (locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    MIN_TIME_MS,
                    MIN_DISTANCE_METERS,
                    this,
                    Looper.getMainLooper()
                )
            }

            // Acelerômetro para detectar movimento em túneis/garagens
            accelerometer?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }

            state = state.copy(isTracking = true)
            saveState()
            Log.i(TAG, "📍 GPS Tracking iniciado (passivo + acelerômetro)")
        } catch (e: SecurityException) {
            Log.e(TAG, "Sem permissão de localização: ${e.message}")
        }
    }

    /**
     * Para o rastreamento (chamado ao encerrar turno).
     */
    fun stopTracking() {
        locationManager?.removeUpdates(this)
        sensorManager?.unregisterListener(this)
        state = state.copy(isTracking = false)
        saveState()
        Log.i(TAG, "📍 GPS Tracking encerrado. Total: ${String.format("%.2f", state.totalDistanceKm)} km")
    }

    // v6.9.0: Controle automático por ActivityStateDetector
    private var isPaused = false
    private var isEconomyMode = false
    private val ECONOMY_TIME_MS = 30_000L  // 30s entre updates no modo economia
    private val ECONOMY_DISTANCE_M = 50f   // 50m entre pontos no modo economia

    /**
     * v6.9.0: Pausa o GPS (motorista caminhando/correndo/exercício).
     * Não conta KM mas mantém estado para retomar.
     */
    fun pauseTracking() {
        if (isPaused) return
        isPaused = true
        locationManager?.removeUpdates(this)
        Log.d(TAG, "⏸️ GPS pausado (motorista fora do veículo)")
    }

    /**
     * v6.9.0: Retoma o GPS (motorista voltou a dirigir).
     */
    @Suppress("MissingPermission")
    fun resumeTracking() {
        if (!isPaused && !isEconomyMode) return
        isPaused = false
        isEconomyMode = false
        try {
            locationManager?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                MIN_TIME_MS,
                MIN_DISTANCE_METERS,
                this,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Sem permissão GPS ao resumir: ${e.message}")
        }
        Log.d(TAG, "▶️ GPS retomado (motorista dirigindo)")
    }

    /**
     * v6.9.0: Modo economia (motorista parado, esperando corrida).
     * GPS coleta a cada 30s/50m em vez de 3s/10m.
     */
    @Suppress("MissingPermission")
    fun setEconomyMode(enabled: Boolean) {
        if (enabled == isEconomyMode) return
        isEconomyMode = enabled
        isPaused = false
        locationManager?.removeUpdates(this)
        val timeMs = if (enabled) ECONOMY_TIME_MS else MIN_TIME_MS
        val distM = if (enabled) ECONOMY_DISTANCE_M else MIN_DISTANCE_METERS
        try {
            locationManager?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                timeMs,
                distM,
                this,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Sem permissão GPS ao mudar modo: ${e.message}")
        }
        Log.d(TAG, if (enabled) "🔋 GPS modo economia" else "📍 GPS modo ativo")
    }

    /**
     * Marca início de uma corrida (para separar KM de corrida vs KM morto).
     */
    fun startRide() {
        rideStartDistanceKm = state.totalDistanceKm
        state = state.copy(isInRide = true, rideDistanceKm = 0.0)
        saveState()
    }

    /**
     * Marca fim de uma corrida e retorna o KM medido pelo GPS.
     */
    fun endRide(): Double {
        val rideKm = state.totalDistanceKm - rideStartDistanceKm
        state = state.copy(
            isInRide = false,
            rideDistanceKm = rideKm,
            deadDistanceKm = state.deadDistanceKm // Acumula entre corridas
        )
        saveState()
        return rideKm
    }

    /**
     * Valida o KM informado pela Uber contra o GPS.
     * @param uberReportedKm KM que a Uber informou no card
     * @return KmValidation com a comparação
     */
    fun validateRideKm(uberReportedKm: Double): KmValidation {
        val gpsKm = state.rideDistanceKm
        if (gpsKm <= 0.0) return KmValidation(confidence = 0.0)

        val difference = gpsKm - uberReportedKm
        val differencePercent = if (uberReportedKm > 0) (difference / uberReportedKm) * 100.0 else 0.0

        // Confiança baseada em quantidade de pontos GPS coletados
        val confidence = (state.pointsCollected.toDouble() / 20.0).coerceIn(0.0, 1.0)

        val validation = KmValidation(
            gpsDistanceKm = gpsKm,
            uberReportedKm = uberReportedKm,
            differenceKm = difference,
            differencePercent = differencePercent,
            isUberUnderreporting = differencePercent > 10.0 && confidence > 0.7,
            confidence = confidence
        )

        validations.add(validation)
        saveValidationHistory(validation)
        return validation
    }

    /**
     * Obtém estatísticas de validação acumuladas.
     */
    fun getValidationStats(): ValidationStats {
        val history = loadValidationHistory()
        if (history.isEmpty()) return ValidationStats()

        val highConfidence = history.filter { it.confidence > 0.7 }
        val underreporting = highConfidence.filter { it.isUberUnderreporting }

        return ValidationStats(
            totalValidations = history.size,
            highConfidenceCount = highConfidence.size,
            underreportingCount = underreporting.size,
            avgDifferencePercent = highConfidence.map { it.differencePercent }.average().takeIf { !it.isNaN() } ?: 0.0,
            maxDifferencePercent = highConfidence.maxOfOrNull { it.differencePercent } ?: 0.0,
            totalKmLost = underreporting.sumOf { it.differenceKm }
        )
    }

    /**
     * Obtém o KM total rastreado no turno atual.
     */
    fun getTotalDistanceKm(): Double = state.totalDistanceKm

    /**
     * Obtém o KM morto (entre corridas) do turno atual.
     */
    fun getDeadDistanceKm(): Double = state.deadDistanceKm

    /**
     * Obtém a velocidade média do turno.
     */
    fun getAvgSpeedKmh(): Double = state.avgSpeedKmh

    /**
     * Verifica se o veículo está em movimento (via acelerômetro).
     */
    fun isMoving(): Boolean = state.isMoving

    /**
     * Reseta as distâncias (chamado ao iniciar novo turno).
     */
    fun reset() {
        state = GpsTrackingState()
        rideStartDistanceKm = 0.0
        saveState()
    }

    // ========================================================================
    // LocationListener
    // ========================================================================

    // v6.7.0 Ruptura #12: Filtro Kalman simples para suavizar GPS ruidoso
    private var kalmanLat: Double = 0.0
    private var kalmanLon: Double = 0.0
    private var kalmanVariance: Double = 1.0  // Incerteza inicial alta
    private var kalmanInitialized: Boolean = false

    /**
     * Aplica filtro Kalman 1D em cada eixo (lat/lon separadamente).
     * Reduz ruído de GPS em túneis, áreas urbanas densas, e multi-path.
     * Resultado: distâncias mais precisas, menos "zig-zag" fictício.
     */
    private fun kalmanFilter(measuredLat: Double, measuredLon: Double, accuracy: Float): Pair<Double, Double> {
        val measurementVariance = (accuracy * accuracy).toDouble().coerceAtLeast(1.0)

        if (!kalmanInitialized) {
            kalmanLat = measuredLat
            kalmanLon = measuredLon
            kalmanVariance = measurementVariance
            kalmanInitialized = true
            return Pair(measuredLat, measuredLon)
        }

        // Kalman gain: quanto confiar na nova medição vs estado anterior
        val kalmanGain = kalmanVariance / (kalmanVariance + measurementVariance)

        // Atualizar estado
        kalmanLat = kalmanLat + kalmanGain * (measuredLat - kalmanLat)
        kalmanLon = kalmanLon + kalmanGain * (measuredLon - kalmanLon)

        // Atualizar variância (incerteza diminui com cada medição)
        kalmanVariance = (1 - kalmanGain) * kalmanVariance + 0.5 // Process noise

        return Pair(kalmanLat, kalmanLon)
    }

    override fun onLocationChanged(location: Location) {
        // Filtrar pontos com baixa precisão
        if (location.accuracy > MIN_ACCURACY_METERS) return

        // v6.7.0: Aplicar filtro Kalman para suavizar GPS
        val (filteredLat, filteredLon) = kalmanFilter(
            location.latitude, location.longitude, location.accuracy
        )

        val prevLat = state.lastLatitude
        val prevLon = state.lastLongitude

        if (prevLat != 0.0 && prevLon != 0.0) {
            // Calcular distância usando coordenadas filtradas pelo Kalman
            val results = FloatArray(1)
            Location.distanceBetween(prevLat, prevLon, filteredLat, filteredLon, results)
            val distanceMeters = results[0]

            // Filtrar saltos de GPS (teleportação > 500m em 3s = impossível)
            if (distanceMeters > 500) {
                Log.w(TAG, "GPS jump detectado: ${distanceMeters}m — ignorando")
                return
            }

            // Filtro adicional: ignorar micro-movimentos < 3m (ruído GPS parado)
            if (distanceMeters < 3 && !state.isMoving) return

            val distanceKm = distanceMeters / 1000.0
            val newTotal = state.totalDistanceKm + distanceKm

            // Atualizar KM morto (quando não está em corrida)
            val newDeadKm = if (!state.isInRide) state.deadDistanceKm + distanceKm else state.deadDistanceKm

            // Velocidade instantânea
            val speedKmh = if (location.hasSpeed()) location.speed * 3.6 else 0.0

            // Média móvel da velocidade
            val newAvgSpeed = if (state.avgSpeedKmh == 0.0) speedKmh
            else state.avgSpeedKmh * 0.9 + speedKmh * 0.1

            state = state.copy(
                totalDistanceKm = newTotal,
                deadDistanceKm = newDeadKm,
                lastLatitude = filteredLat,  // v6.7.0: Usar coordenadas filtradas pelo Kalman
                lastLongitude = filteredLon,
                lastUpdateMs = System.currentTimeMillis(),
                pointsCollected = state.pointsCollected + 1,
                avgSpeedKmh = newAvgSpeed,
                maxSpeedKmh = maxOf(state.maxSpeedKmh, speedKmh),
                isMoving = speedKmh > SPEED_THRESHOLD_MS * 3.6
            )
        } else {
            // Primeiro ponto
            state = state.copy(
                lastLatitude = filteredLat,  // v6.7.0: Usar coordenadas filtradas
                lastLongitude = filteredLon,
                lastUpdateMs = System.currentTimeMillis(),
                pointsCollected = 1
            )
        }

        saveState()
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    // ========================================================================
    // SensorEventListener (Acelerômetro)
    // ========================================================================

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_LINEAR_ACCELERATION) return

        // Magnitude da aceleração linear (sem gravidade)
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()

        // Detectar movimento: aceleração > threshold indica veículo em movimento
        val isMoving = magnitude > ACCEL_MOVEMENT_THRESHOLD
        if (state.isMoving != isMoving) {
            state = state.copy(isMoving = isMoving)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ========================================================================
    // Persistência
    // ========================================================================

    private fun saveState() {
        prefs.edit()
            .putBoolean("gps_tracking", state.isTracking)
            .putFloat("gps_total_km", state.totalDistanceKm.toFloat())
            .putFloat("gps_ride_km", state.rideDistanceKm.toFloat())
            .putFloat("gps_dead_km", state.deadDistanceKm.toFloat())
            .putFloat("gps_last_lat", state.lastLatitude.toFloat())
            .putFloat("gps_last_lon", state.lastLongitude.toFloat())
            .putLong("gps_last_update", state.lastUpdateMs)
            .putInt("gps_points", state.pointsCollected)
            .putBoolean("gps_in_ride", state.isInRide)
            .putFloat("gps_avg_speed", state.avgSpeedKmh.toFloat())
            .putFloat("gps_max_speed", state.maxSpeedKmh.toFloat())
            .apply()
    }

    fun loadState(): GpsTrackingState {
        state = GpsTrackingState(
            isTracking = prefs.getBoolean("gps_tracking", false),
            totalDistanceKm = prefs.getFloat("gps_total_km", 0f).toDouble(),
            rideDistanceKm = prefs.getFloat("gps_ride_km", 0f).toDouble(),
            deadDistanceKm = prefs.getFloat("gps_dead_km", 0f).toDouble(),
            lastLatitude = prefs.getFloat("gps_last_lat", 0f).toDouble(),
            lastLongitude = prefs.getFloat("gps_last_lon", 0f).toDouble(),
            lastUpdateMs = prefs.getLong("gps_last_update", 0L),
            pointsCollected = prefs.getInt("gps_points", 0),
            isInRide = prefs.getBoolean("gps_in_ride", false),
            avgSpeedKmh = prefs.getFloat("gps_avg_speed", 0f).toDouble(),
            maxSpeedKmh = prefs.getFloat("gps_max_speed", 0f).toDouble()
        )
        return state
    }

    private fun saveValidationHistory(validation: KmValidation) {
        val count = prefs.getInt("validation_count", 0)
        prefs.edit()
            .putFloat("validation_${count}_gps", validation.gpsDistanceKm.toFloat())
            .putFloat("validation_${count}_uber", validation.uberReportedKm.toFloat())
            .putFloat("validation_${count}_diff", validation.differencePercent.toFloat())
            .putFloat("validation_${count}_conf", validation.confidence.toFloat())
            .putBoolean("validation_${count}_under", validation.isUberUnderreporting)
            .putInt("validation_count", count + 1)
            .apply()
    }

    private fun loadValidationHistory(): List<KmValidation> {
        val count = prefs.getInt("validation_count", 0)
        return (0 until count).map { i ->
            KmValidation(
                gpsDistanceKm = prefs.getFloat("validation_${i}_gps", 0f).toDouble(),
                uberReportedKm = prefs.getFloat("validation_${i}_uber", 0f).toDouble(),
                differencePercent = prefs.getFloat("validation_${i}_diff", 0f).toDouble(),
                confidence = prefs.getFloat("validation_${i}_conf", 0f).toDouble(),
                isUberUnderreporting = prefs.getBoolean("validation_${i}_under", false)
            )
        }
    }
}

/**
 * Estatísticas de validação GPS vs Uber acumuladas.
 */
data class ValidationStats(
    val totalValidations: Int = 0,
    val highConfidenceCount: Int = 0,
    val underreportingCount: Int = 0,
    val avgDifferencePercent: Double = 0.0,
    val maxDifferencePercent: Double = 0.0,
    val totalKmLost: Double = 0.0
) {
    val underreportingRate: Double
        get() = if (highConfidenceCount > 0) (underreportingCount.toDouble() / highConfidenceCount) * 100.0 else 0.0

    val summary: String
        get() = when {
            highConfidenceCount < 5 -> "📊 Dados insuficientes (${highConfidenceCount}/5 corridas com GPS confiável)"
            underreportingRate > 50.0 -> "⚠️ Uber subreporta KM em ${String.format("%.0f", underreportingRate)}% das corridas (média: ${String.format("%.1f", avgDifferencePercent)}% a menos)"
            underreportingRate > 20.0 -> "🟡 Possível subreporte em ${String.format("%.0f", underreportingRate)}% das corridas"
            else -> "✅ KM da Uber consistente com GPS (diferença média: ${String.format("%.1f", avgDifferencePercent)}%)"
        }
}
