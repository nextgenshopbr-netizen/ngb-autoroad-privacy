package com.ngbautoroad.domain

import android.content.Context
import android.location.Geocoder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.util.Locale

// ============================================================================
// ARQUIVO: GeoEnrichmentEngine.kt
// VERSÃO: v6.6.0
// RESPONSABILIDADE: Enriquecimento de dados via Internet
//   - Geocodificação reversa (lat/lon → bairro, cidade, CEP)
//   - Classificação de área (residencial, comercial, industrial, perigosa)
//   - Dados de trânsito em tempo real (via APIs gratuitas)
//   - Enriquecimento do histórico de corridas com dados geográficos
//   - Motorista SEMPRE tem internet (requisito para trabalhar)
// DEPENDENTES:
//   - GpsTrackingEngine.kt → enriquece pontos GPS com dados de bairro
//   - SafetyScoreModifier.kt → usa classificação de área para risco
//   - ReturnFactorEngine.kt → identifica bairros com precisão
//   - LocalLearningEngine.kt → padrões por região/horário
// ============================================================================

/**
 * Dados geográficos enriquecidos de uma localização.
 */
data class GeoData(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val neighborhood: String = "",
    val city: String = "",
    val state: String = "",
    val zipCode: String = "",
    val streetName: String = "",
    val areaType: AreaType = AreaType.UNKNOWN,
    val isKnownDangerousArea: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

enum class AreaType(val label: String) {
    RESIDENTIAL("Residencial"),
    COMMERCIAL("Comercial"),
    INDUSTRIAL("Industrial"),
    PERIPHERAL("Periferia"),
    CENTRAL("Centro"),
    HIGHWAY("Rodovia"),
    UNKNOWN("Desconhecido")
}

/**
 * Dados de trânsito em tempo real.
 */
data class TrafficData(
    val congestionLevel: CongestionLevel = CongestionLevel.UNKNOWN,
    val estimatedSpeedKmh: Double = 0.0,
    val delayMinutes: Int = 0,
    val isRushHour: Boolean = false
)

enum class CongestionLevel(val label: String, val multiplier: Double) {
    FREE("Livre", 1.0),
    LIGHT("Leve", 0.9),
    MODERATE("Moderado", 0.75),
    HEAVY("Intenso", 0.6),
    GRIDLOCK("Parado", 0.4),
    UNKNOWN("Desconhecido", 0.8)
}

/**
 * Motor de enriquecimento geográfico via Internet.
 * Usa Geocoder do Android (gratuito) + cache local para evitar requests repetidos.
 */
class GeoEnrichmentEngine(private val context: Context) {

    companion object {
        private const val TAG = "GeoEnrichment"
        private const val CACHE_EXPIRY_MS = 24 * 60 * 60 * 1000L // 24h
        private const val MAX_CACHE_SIZE = 500
    }

    private val geocoder = Geocoder(context, Locale("pt", "BR"))
    private val cache = mutableMapOf<String, GeoData>() // key = "lat_lon" truncado

    /**
     * Obtém dados geográficos enriquecidos para uma coordenada.
     * Usa Geocoder do Android (gratuito, sem API key).
     */
    suspend fun enrichLocation(latitude: Double, longitude: Double): GeoData = withContext(Dispatchers.IO) {
        // Verificar cache (truncar para 3 casas decimais = ~100m de precisão)
        val cacheKey = "${String.format("%.3f", latitude)}_${String.format("%.3f", longitude)}"
        cache[cacheKey]?.let { return@withContext it }

        try {
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            if (addresses != null && addresses.isNotEmpty()) {
                val addr = addresses[0]
                val geoData = GeoData(
                    latitude = latitude,
                    longitude = longitude,
                    neighborhood = addr.subLocality ?: addr.locality ?: "",
                    city = addr.locality ?: addr.subAdminArea ?: "",
                    state = addr.adminArea ?: "",
                    zipCode = addr.postalCode ?: "",
                    streetName = addr.thoroughfare ?: "",
                    areaType = classifyArea(addr.thoroughfare, addr.subLocality),
                    timestamp = System.currentTimeMillis()
                )

                // Salvar no cache
                if (cache.size >= MAX_CACHE_SIZE) {
                    cache.entries.firstOrNull()?.let { cache.remove(it.key) }
                }
                cache[cacheKey] = geoData

                return@withContext geoData
            }
        } catch (e: Exception) {
            Log.w(TAG, "Geocoder falhou: ${e.message}")
        }

        GeoData(latitude = latitude, longitude = longitude)
    }

    /**
     * Enriquece os dados de uma corrida com informações geográficas.
     * Chamado quando GPS detecta início/fim de corrida.
     */
    suspend fun enrichRideData(
        pickupLat: Double, pickupLon: Double,
        dropoffLat: Double, dropoffLon: Double
    ): Pair<GeoData, GeoData> {
        val pickup = enrichLocation(pickupLat, pickupLon)
        val dropoff = enrichLocation(dropoffLat, dropoffLon)
        return Pair(pickup, dropoff)
    }

    /**
     * Estima o nível de trânsito baseado na velocidade GPS.
     * Não precisa de API externa — usa a velocidade medida pelo GPS.
     */
    fun estimateTraffic(currentSpeedKmh: Double, isUrbanArea: Boolean): TrafficData {
        val expectedSpeed = if (isUrbanArea) 40.0 else 80.0
        val ratio = currentSpeedKmh / expectedSpeed

        val congestion = when {
            ratio >= 0.9 -> CongestionLevel.FREE
            ratio >= 0.7 -> CongestionLevel.LIGHT
            ratio >= 0.5 -> CongestionLevel.MODERATE
            ratio >= 0.3 -> CongestionLevel.HEAVY
            else -> CongestionLevel.GRIDLOCK
        }

        val isRushHour = java.util.Calendar.getInstance().let { cal ->
            val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
            hour in 7..9 || hour in 17..19
        }

        return TrafficData(
            congestionLevel = congestion,
            estimatedSpeedKmh = currentSpeedKmh,
            isRushHour = isRushHour
        )
    }

    /**
     * Calcula o "valor real por hora" considerando trânsito.
     * Corrida de R$30 em 20min no trânsito livre = R$90/h
     * Mesma corrida em trânsito pesado (40min) = R$45/h
     * O score deve refletir isso.
     */
    fun getTrafficAdjustedValuePerHour(
        rideValue: Double,
        estimatedDurationMin: Int,
        trafficMultiplier: Double
    ): Double {
        val adjustedDuration = estimatedDurationMin / trafficMultiplier
        return if (adjustedDuration > 0) (rideValue / adjustedDuration) * 60.0 else 0.0
    }

    /**
     * Verifica se uma coordenada está em área conhecida como perigosa.
     * Usa a lista configurada pelo motorista no SafetyScoreModifier.
     */
    suspend fun isInDangerousArea(latitude: Double, longitude: Double, dangerousAreas: Set<String>): Boolean {
        val geo = enrichLocation(latitude, longitude)
        return dangerousAreas.any { area ->
            geo.neighborhood.lowercase().contains(area.lowercase()) ||
            geo.streetName.lowercase().contains(area.lowercase())
        }
    }

    /**
     * Obtém o bairro atual do motorista (para "direção de casa").
     */
    suspend fun getCurrentNeighborhood(latitude: Double, longitude: Double): String {
        val geo = enrichLocation(latitude, longitude)
        return geo.neighborhood
    }

    /**
     * Verifica se o destino da corrida está na direção de casa.
     * Usa distância euclidiana simples (suficiente para decisão rápida).
     */
    fun isTowardsHome(
        currentLat: Double, currentLon: Double,
        dropoffLat: Double, dropoffLon: Double,
        homeLat: Double, homeLon: Double
    ): Boolean {
        // Distância atual até casa
        val currentToHome = haversineDistance(currentLat, currentLon, homeLat, homeLon)
        // Distância do destino até casa
        val dropoffToHome = haversineDistance(dropoffLat, dropoffLon, homeLat, homeLon)
        // Se o destino está mais perto de casa que a posição atual → está indo para casa
        return dropoffToHome < currentToHome * 0.8 // 20% de margem
    }

    // === Helpers ===

    private fun classifyArea(street: String?, neighborhood: String?): AreaType {
        val s = (street ?: "").lowercase()
        val n = (neighborhood ?: "").lowercase()

        return when {
            s.contains("rodovia") || s.contains("br-") || s.contains("sp-") -> AreaType.HIGHWAY
            n.contains("centro") || n.contains("central") -> AreaType.CENTRAL
            n.contains("industrial") || n.contains("distrito") -> AreaType.INDUSTRIAL
            s.contains("avenida") && (n.contains("comerci") || n.contains("empres")) -> AreaType.COMMERCIAL
            else -> AreaType.RESIDENTIAL
        }
    }

    /**
     * Fórmula de Haversine para distância entre dois pontos (em km).
     */
    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0 // Raio da Terra em km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }
}
