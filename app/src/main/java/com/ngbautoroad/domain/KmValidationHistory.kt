package com.ngbautoroad.domain

// ============================================================================
// ARQUIVO: KmValidationHistory.kt
// v6.7.0: Ruptura #4 - Persistir discrepâncias de KM da Uber
// Quando o GPS do app mede X km e a Uber informa Y km, a diferença é registrada.
// Gera relatório mensal mostrando quanto o motorista perdeu por KM subreportado.
// ============================================================================

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Registro de uma discrepância de KM entre GPS e plataforma.
 */
data class KmDiscrepancy(
    val timestamp: Long,
    val platform: String,
    val platformKm: Double,      // KM informado pela plataforma
    val gpsKm: Double,           // KM medido pelo GPS do app
    val differenceKm: Double,    // gpsKm - platformKm (positivo = Uber subreportou)
    val differencePct: Double,   // % de diferença
    val estimatedLoss: Double    // Perda estimada em R$ (diferença × R$/km médio)
)

/**
 * Relatório mensal de discrepâncias de KM.
 */
data class KmValidationReport(
    val month: String,           // "2026-06"
    val totalRidesValidated: Int,
    val ridesWithDiscrepancy: Int,
    val avgDiscrepancyPct: Double,
    val totalExtraKmNotPaid: Double,
    val estimatedTotalLoss: Double,
    val worstDiscrepancy: KmDiscrepancy?,
    val discrepancies: List<KmDiscrepancy>
)

/**
 * Engine de validação e histórico de KM.
 * Persiste discrepâncias em SharedPreferences (JSON) para gerar relatórios.
 */
class KmValidationHistory(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "km_validation_history", Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_DISCREPANCIES = "discrepancies_json"
        private const val KEY_TOTAL_RIDES_VALIDATED = "total_rides_validated"
        private const val KEY_AVG_VALUE_PER_KM = "avg_value_per_km"
        // Threshold: diferença > 10% é considerada discrepância significativa
        const val DISCREPANCY_THRESHOLD_PCT = 10.0
    }

    /**
     * Registra uma validação de KM (chamado pelo GpsTrackingEngine ao final de cada corrida).
     * @param platform Nome da plataforma (Uber, 99, etc.)
     * @param platformKm KM informado pela plataforma (card)
     * @param gpsKm KM medido pelo GPS do app
     * @param avgValuePerKm R$/km médio atual (para calcular perda)
     */
    fun recordValidation(
        platform: String,
        platformKm: Double,
        gpsKm: Double,
        avgValuePerKm: Double
    ) {
        // Incrementar total de corridas validadas
        val totalValidated = prefs.getInt(KEY_TOTAL_RIDES_VALIDATED, 0) + 1
        prefs.edit().putInt(KEY_TOTAL_RIDES_VALIDATED, totalValidated).apply()

        // Atualizar média de R$/km (EWMA)
        val currentAvg = prefs.getFloat(KEY_AVG_VALUE_PER_KM, avgValuePerKm.toFloat()).toDouble()
        val newAvg = currentAvg * 0.9 + avgValuePerKm * 0.1
        prefs.edit().putFloat(KEY_AVG_VALUE_PER_KM, newAvg.toFloat()).apply()

        // Calcular diferença
        val differenceKm = gpsKm - platformKm
        val differencePct = if (platformKm > 0) (differenceKm / platformKm) * 100.0 else 0.0

        // Só registrar se diferença > threshold (evitar ruído de GPS)
        if (differencePct > DISCREPANCY_THRESHOLD_PCT && differenceKm > 0.3) {
            val estimatedLoss = differenceKm * newAvg
            val discrepancy = KmDiscrepancy(
                timestamp = System.currentTimeMillis(),
                platform = platform,
                platformKm = platformKm,
                gpsKm = gpsKm,
                differenceKm = differenceKm,
                differencePct = differencePct,
                estimatedLoss = estimatedLoss
            )
            saveDiscrepancy(discrepancy)
        }
    }

    /**
     * Gera relatório mensal de discrepâncias.
     * @param month Mês no formato "2026-06" (null = mês atual)
     */
    fun generateMonthlyReport(month: String? = null): KmValidationReport {
        val targetMonth = month ?: SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        val allDiscrepancies = loadDiscrepancies()

        // Filtrar pelo mês
        val sdf = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        val monthDiscrepancies = allDiscrepancies.filter {
            sdf.format(Date(it.timestamp)) == targetMonth
        }

        val totalValidated = prefs.getInt(KEY_TOTAL_RIDES_VALIDATED, 0)
        val avgDiscrepancy = if (monthDiscrepancies.isNotEmpty()) {
            monthDiscrepancies.map { it.differencePct }.average()
        } else 0.0
        val totalExtraKm = monthDiscrepancies.sumOf { it.differenceKm }
        val totalLoss = monthDiscrepancies.sumOf { it.estimatedLoss }
        val worst = monthDiscrepancies.maxByOrNull { it.differencePct }

        return KmValidationReport(
            month = targetMonth,
            totalRidesValidated = totalValidated,
            ridesWithDiscrepancy = monthDiscrepancies.size,
            avgDiscrepancyPct = avgDiscrepancy,
            totalExtraKmNotPaid = totalExtraKm,
            estimatedTotalLoss = totalLoss,
            worstDiscrepancy = worst,
            discrepancies = monthDiscrepancies
        )
    }

    /**
     * Retorna resumo rápido para exibição na Dashboard.
     */
    fun getQuickSummary(): String {
        val report = generateMonthlyReport()
        return if (report.ridesWithDiscrepancy > 0) {
            "⚠️ ${report.ridesWithDiscrepancy} corridas com KM subreportado este mês. " +
                "Perda estimada: R$ %.2f".format(report.estimatedTotalLoss)
        } else {
            "✅ Nenhuma discrepância de KM significativa este mês."
        }
    }

    // ========================================================================
    // Persistência (JSON em SharedPreferences)
    // ========================================================================

    private fun saveDiscrepancy(discrepancy: KmDiscrepancy) {
        val array = loadDiscrepanciesJson()
        val obj = JSONObject().apply {
            put("timestamp", discrepancy.timestamp)
            put("platform", discrepancy.platform)
            put("platformKm", discrepancy.platformKm)
            put("gpsKm", discrepancy.gpsKm)
            put("differenceKm", discrepancy.differenceKm)
            put("differencePct", discrepancy.differencePct)
            put("estimatedLoss", discrepancy.estimatedLoss)
        }
        array.put(obj)

        // Manter apenas últimos 6 meses (limpar antigos)
        val sixMonthsAgo = System.currentTimeMillis() - (180L * 24 * 60 * 60 * 1000)
        val filtered = JSONArray()
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            if (item.getLong("timestamp") > sixMonthsAgo) {
                filtered.put(item)
            }
        }

        prefs.edit().putString(KEY_DISCREPANCIES, filtered.toString()).apply()
    }

    private fun loadDiscrepancies(): List<KmDiscrepancy> {
        val array = loadDiscrepanciesJson()
        val list = mutableListOf<KmDiscrepancy>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(KmDiscrepancy(
                timestamp = obj.getLong("timestamp"),
                platform = obj.getString("platform"),
                platformKm = obj.getDouble("platformKm"),
                gpsKm = obj.getDouble("gpsKm"),
                differenceKm = obj.getDouble("differenceKm"),
                differencePct = obj.getDouble("differencePct"),
                estimatedLoss = obj.getDouble("estimatedLoss")
            ))
        }
        return list
    }

    private fun loadDiscrepanciesJson(): JSONArray {
        val json = prefs.getString(KEY_DISCREPANCIES, "[]") ?: "[]"
        return try { JSONArray(json) } catch (e: Exception) { JSONArray() }
    }
}
