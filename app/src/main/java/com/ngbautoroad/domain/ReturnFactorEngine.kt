package com.ngbautoroad.domain

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

// ============================================================================
// ARQUIVO: ReturnFactorEngine.kt
// VERSÃO: v6.6.0
// RESPONSABILIDADE: Ruptura #2 — Fator de retorno por bairro (volta vazia)
//   Problema: Corrida para periferia paga R$3/km ida, mas volta vazio.
//   R$/km real = metade. Score precisa penalizar isso.
//   Solução: Aprende com histórico quais bairros têm alta taxa de volta vazia
//   e aplica um fator de desconto no R$/km efetivo.
// DEPENDENTES:
//   - RideScorer.kt → ajusta o R$/km antes de normalizar
//   - LocalLearningEngine.kt → alimenta dados de retorno
// ============================================================================

/**
 * Dados de retorno por bairro.
 * @param neighborhood Nome do bairro de destino
 * @param totalTrips Total de corridas para esse bairro
 * @param emptyReturns Quantas vezes voltou vazio (sem corrida na volta)
 * @param avgReturnKm Distância média de retorno (estimada)
 * @param returnFactor Fator de ajuste (0.5 = volta vazia sempre, 1.0 = sempre pega corrida na volta)
 */
data class NeighborhoodReturnData(
    val neighborhood: String,
    var totalTrips: Int = 0,
    var emptyReturns: Int = 0,
    var avgReturnKm: Double = 0.0,
    var returnFactor: Double = 1.0
) {
    val emptyReturnRate: Double
        get() = if (totalTrips > 0) emptyReturns.toDouble() / totalTrips else 0.5

    /**
     * Calcula o fator de retorno.
     * returnFactor = 1.0 / (1.0 + emptyReturnRate)
     * Se 100% volta vazio → fator = 0.5 (R$/km efetivo = metade)
     * Se 0% volta vazio → fator = 1.0 (R$/km efetivo = integral)
     * Se 50% volta vazio → fator = 0.67
     */
    fun calculateReturnFactor(): Double {
        if (totalTrips < 3) return 0.85 // Dados insuficientes: assume 15% de desconto
        return 1.0 / (1.0 + emptyReturnRate)
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("neighborhood", neighborhood)
        put("totalTrips", totalTrips)
        put("emptyReturns", emptyReturns)
        put("avgReturnKm", avgReturnKm)
        put("returnFactor", returnFactor)
    }

    companion object {
        fun fromJson(json: JSONObject): NeighborhoodReturnData = NeighborhoodReturnData(
            neighborhood = json.optString("neighborhood", ""),
            totalTrips = json.optInt("totalTrips", 0),
            emptyReturns = json.optInt("emptyReturns", 0),
            avgReturnKm = json.optDouble("avgReturnKm", 0.0),
            returnFactor = json.optDouble("returnFactor", 1.0)
        )
    }
}

/**
 * Motor de aprendizado de fator de retorno por bairro.
 * Aprende com o histórico de corridas quais destinos têm alta taxa de volta vazia.
 */
class ReturnFactorEngine(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("return_factor_prefs", Context.MODE_PRIVATE)

    /**
     * Obtém o fator de retorno para um bairro de destino.
     * @param dropoffNeighborhood Bairro de destino da corrida
     * @param isNightShift Se é turno da noite (verdadeiro) ou dia (falso)
     * @return Fator entre 0.5 e 1.0 (1.0 = sem penalidade, 0.5 = volta vazia certa)
     */
    fun getReturnFactor(dropoffNeighborhood: String, isNightShift: Boolean = false): Double {
        if (dropoffNeighborhood.isBlank()) return 0.85 // Sem dados: assume 15% desconto
        val shiftStr = if (isNightShift) "noite" else "dia"
        val key = "${dropoffNeighborhood.lowercase().trim()}_$shiftStr"
        val data = loadNeighborhoodData(key)
        return data?.calculateReturnFactor() ?: 0.85
    }

    /**
     * Registra que uma corrida foi concluída para um bairro.
     * @param dropoffNeighborhood Bairro de destino
     * @param hadReturnRide Se o motorista pegou corrida na volta (true) ou voltou vazio (false)
     * @param returnKm Distância de retorno estimada
     * @param isNightShift Se é turno da noite (verdadeiro) ou dia (falso)
     */
    fun registerTrip(dropoffNeighborhood: String, hadReturnRide: Boolean, returnKm: Double = 0.0, isNightShift: Boolean = false) {
        if (dropoffNeighborhood.isBlank()) return
        val shiftStr = if (isNightShift) "noite" else "dia"
        val key = "${dropoffNeighborhood.lowercase().trim()}_$shiftStr"
        val data = loadNeighborhoodData(key) ?: NeighborhoodReturnData(neighborhood = key)

        data.totalTrips++
        if (!hadReturnRide) data.emptyReturns++
        if (returnKm > 0) {
            // Média móvel da distância de retorno
            data.avgReturnKm = if (data.avgReturnKm == 0.0) returnKm
            else data.avgReturnKm * 0.7 + returnKm * 0.3
        }
        data.returnFactor = data.calculateReturnFactor()

        saveNeighborhoodData(key, data)
    }

    /**
     * Calcula o R$/km efetivo considerando a volta vazia.
     * @param rideValuePerKm R$/km bruto da corrida
     * @param dropoffNeighborhood Bairro de destino
     * @return R$/km ajustado (menor se bairro tem alta taxa de volta vazia)
     */
    fun getEffectiveValuePerKm(rideValuePerKm: Double, dropoffNeighborhood: String): Double {
        val factor = getReturnFactor(dropoffNeighborhood)
        return rideValuePerKm * factor
    }

    /**
     * Obtém todos os bairros com dados de retorno.
     */
    fun getAllNeighborhoodData(): List<NeighborhoodReturnData> {
        val json = prefs.getString("neighborhood_return_data", null) ?: return emptyList()
        return try {
            val obj = JSONObject(json)
            val result = mutableListOf<NeighborhoodReturnData>()
            obj.keys().forEach { key ->
                val item = obj.optJSONObject(key)
                if (item != null) result.add(NeighborhoodReturnData.fromJson(item))
            }
            result.sortedByDescending { it.emptyReturnRate }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Verifica se o fator de retorno está habilitado
     */
    fun isEnabled(): Boolean = prefs.getBoolean("return_factor_enabled", true)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("return_factor_enabled", enabled).apply()
    }

    // === Persistência ===

    private fun loadNeighborhoodData(key: String): NeighborhoodReturnData? {
        val json = prefs.getString("neighborhood_return_data", null) ?: return null
        return try {
            val obj = JSONObject(json)
            val item = obj.optJSONObject(key) ?: return null
            NeighborhoodReturnData.fromJson(item)
        } catch (e: Exception) {
            null
        }
    }

    private fun saveNeighborhoodData(key: String, data: NeighborhoodReturnData) {
        val json = prefs.getString("neighborhood_return_data", null)
        val obj = if (json != null) {
            try { JSONObject(json) } catch (e: Exception) { JSONObject() }
        } else JSONObject()

        obj.put(key, data.toJson())
        prefs.edit().putString("neighborhood_return_data", obj.toString()).apply()
    }
}

/**
 * Extensão estática para uso direto no RideScorer (sem Context).
 * Calcula penalidade de retorno baseada na distância do destino.
 * Lógica: corridas longas para destinos distantes têm mais chance de volta vazia.
 * Sem dados históricos disponíveis no RideScorer, usa heurística por distância.
 */
object ReturnFactorEngineStatic {
    /**
     * Penalidade heurística por volta vazia.
     * Corridas > 15km para bairros desconhecidos: penalidade proporcional.
     * Corridas curtas (< 5km): sem penalidade (fácil voltar).
     */
    fun calculateReturnPenalty(dropoffNeighborhood: String, dropoffDistance: Double): Double {
        // Sem penalidade para corridas curtas
        if (dropoffDistance <= 5.0) return 0.0

        // Penalidade progressiva para corridas longas sem bairro conhecido
        // 5-10km: 0-2 pts, 10-15km: 2-5 pts, 15-25km: 5-10 pts, >25km: 10-15 pts
        return when {
            dropoffDistance <= 10.0 -> (dropoffDistance - 5.0) * 0.4  // 0-2 pts
            dropoffDistance <= 15.0 -> 2.0 + (dropoffDistance - 10.0) * 0.6  // 2-5 pts
            dropoffDistance <= 25.0 -> 5.0 + (dropoffDistance - 15.0) * 0.5  // 5-10 pts
            else -> 10.0 + (dropoffDistance - 25.0) * 0.2  // 10-15 pts (cap)
        }.coerceAtMost(15.0)
    }
}
