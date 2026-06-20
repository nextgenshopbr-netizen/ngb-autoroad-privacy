package com.ngbautoroad.domain

// ============================================================================
// ARQUIVO: LocalLearningEngine.kt
// VERSÃO: v6.3.1 — Alimentado com dados reais do RideHistoryDao
// RESPONSABILIDADE: IA local — padrões estatísticos, sugestões sem nuvem/IA paga
// CORREÇÕES v6.3.1:
//   - seedFromDatabase(): carrega histórico real do banco ao inicializar
//   - Sugestão de bloqueio automático de bairros com alta taxa de recusa
//   - Padrões ainda persistidos em SharedPreferences como cache
// ============================================================================

import android.content.Context
import android.content.SharedPreferences
import com.ngbautoroad.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

enum class SuggestionType(val label: String, val icon: String) {
    BEST_HOURS("Melhores Horários", "🕐"),
    BEST_NEIGHBORHOODS("Melhores Bairros", "📍"),
    BEST_RIDE_TYPES("Melhores Tipos", "🚗"),
    STOP_SUGGESTION("Parar Agora", "🛑"),
    AVOID_AREA("Evitar Área", "⚠️"),
    EARNING_PATTERN("Padrão de Ganho", "📈"),
    FATIGUE_WARNING("Alerta Fadiga", "😴")
}

data class LearningSuggestion(
    val type: SuggestionType, val title: String, val description: String,
    val confidence: Double, val basedOnRides: Int
)

data class RidePattern(val hour: Int, val dayOfWeek: Int, val neighborhood: String, val valuePerKm: Double, val accepted: Boolean)

/**
 * Motor de aprendizado local — 100% offline.
 * v5.0.0: Padrões persistidos em SharedPreferences para não perder ao fechar app.
 * Construtor sem Context = modo memória (para testes/preview no FeaturesActivity).
 */
class LocalLearningEngine(context: Context? = null) {
    private val prefs: SharedPreferences? = context?.getSharedPreferences("learning_engine", Context.MODE_PRIVATE)
    private val patterns = mutableListOf<RidePattern>()
    private val MAX_PATTERNS = 5000

    init {
        // v5.0.0: Carregar padrões persistidos ao inicializar
        prefs?.getString("patterns_json", null)?.let { json ->
            try {
                val arr = JSONArray(json)
                for (i in 0 until arr.length().coerceAtMost(MAX_PATTERNS)) {
                    val obj = arr.getJSONObject(i)
                    patterns.add(RidePattern(
                        hour = obj.getInt("h"),
                        dayOfWeek = obj.getInt("d"),
                        neighborhood = obj.getString("n"),
                        valuePerKm = obj.getDouble("v"),
                        accepted = obj.getBoolean("a")
                    ))
                }
            } catch (_: Exception) {}
        }
    }

    fun addPattern(p: RidePattern) {
        patterns.add(p)
        if (patterns.size > MAX_PATTERNS) patterns.removeAt(0)
        persistPatterns()
    }

    fun getPatternCount(): Int = patterns.size

    /**
     * v6.3.1: Carrega histórico real do banco e converte em RidePatterns.
     * Chamado uma vez na inicialização do serviço para alimentar o engine com dados reais.
     * Não duplica padrões já existentes no cache do SharedPreferences.
     */
    fun seedFromDatabase(context: Context, scope: CoroutineScope = CoroutineScope(Dispatchers.IO)) {
        scope.launch {
            try {
                val dao = AppDatabase.getInstance(context).rideHistoryDao()
                // Buscar últimas 500 corridas COMPLETED ou ACCEPTED do banco
                val rides = dao.getAll().filter {
                    it.status == "COMPLETED" || it.status == "ACCEPTED" || it.status == "REFUSED"
                }.takeLast(500)

                if (rides.isEmpty()) return@launch

                // Evitar duplicar se já temos padrões suficientes no cache
                if (patterns.size >= rides.size) return@launch

                val cal = Calendar.getInstance()
                val newPatterns = rides.map { ride ->
                    cal.timeInMillis = ride.timestamp
                    RidePattern(
                        hour = cal.get(Calendar.HOUR_OF_DAY),
                        dayOfWeek = cal.get(Calendar.DAY_OF_WEEK),
                        neighborhood = ride.pickupNeighborhood.ifBlank { ride.dropoffNeighborhood },
                        valuePerKm = ride.valuePerKm,
                        accepted = ride.status == "COMPLETED" || ride.status == "ACCEPTED"
                    )
                }

                // Inserir no início (mais antigos primeiro) sem ultrapassar MAX_PATTERNS
                synchronized(patterns) {
                    patterns.addAll(0, newPatterns)
                    while (patterns.size > MAX_PATTERNS) patterns.removeAt(0)
                }
                persistPatterns()
            } catch (_: Exception) {}
        }
    }

    private fun persistPatterns() {
        prefs ?: return
        try {
            val arr = JSONArray()
            // Salvar apenas os últimos 1000 para não sobrecarregar SharedPreferences
            val toSave = patterns.takeLast(1000)
            for (p in toSave) {
                arr.put(JSONObject().apply {
                    put("h", p.hour); put("d", p.dayOfWeek)
                    put("n", p.neighborhood); put("v", p.valuePerKm); put("a", p.accepted)
                })
            }
            prefs.edit().putString("patterns_json", arr.toString()).apply()
        } catch (_: Exception) {}
    }

    fun generateSuggestions(): List<LearningSuggestion> {
        // v5.0.0: Retornar feedback quando dados insuficientes
        if (patterns.size < 20) return listOf(
            LearningSuggestion(
                SuggestionType.EARNING_PATTERN,
                "Colete mais dados",
                "Registre pelo menos 20 corridas para receber sugestões personalizadas. Atual: ${patterns.size}/20.",
                0.0,
                patterns.size
            )
        )
        val suggestions = mutableListOf<LearningSuggestion>()

        // Best hours
        val byHour = patterns.filter { it.accepted }.groupBy { it.hour }
        val avgByHour = byHour.mapValues { (_, v) -> v.map { it.valuePerKm }.average() }
        val bestHours = avgByHour.entries.sortedByDescending { it.value }.take(3)
        if (bestHours.isNotEmpty()) {
            val globalAvg = patterns.filter { it.accepted }.map { it.valuePerKm }.averageOrZero()
            if (globalAvg > 0) {
                val improvement = ((bestHours.first().value / globalAvg - 1) * 100).toInt()
                suggestions.add(LearningSuggestion(
                    SuggestionType.BEST_HOURS,
                    "Melhores horários: ${bestHours.joinToString(", ") { "${it.key}h" }}",
                    "R\$/km ${improvement}% acima da média nesses horários.",
                    0.7 + (patterns.size / 1000.0).coerceAtMost(0.25),
                    patterns.size
                ))
            }
        }

        // Best neighborhoods
        val byHood = patterns.filter { it.accepted }.groupBy { it.neighborhood }
        val avgByHood = byHood.mapValues { (_, v) -> v.map { it.valuePerKm }.average() }
        val bestHoods = avgByHood.entries.sortedByDescending { it.value }.take(3)
        if (bestHoods.size >= 2) {
            suggestions.add(LearningSuggestion(
                SuggestionType.BEST_NEIGHBORHOODS,
                "Bairros mais rentáveis: ${bestHoods.joinToString(", ") { it.key }}",
                "Posicione-se próximo a esses bairros para maximizar ganhos.",
                0.65 + (patterns.size / 2000.0).coerceAtMost(0.25),
                patterns.size
            ))
        }

        // Fatigue detection (performance drops in second half of session)
        val accepted = patterns.filter { it.accepted }
        if (accepted.size > 50) {
            val firstHalf = accepted.take(accepted.size / 2).map { it.valuePerKm }.averageOrZero()
            val secondHalf = accepted.drop(accepted.size / 2).map { it.valuePerKm }.averageOrZero()
            if (firstHalf > 0 && secondHalf < firstHalf * 0.85) {
                val drop = ((1 - secondHalf / firstHalf) * 100).toInt()
                suggestions.add(LearningSuggestion(
                    SuggestionType.FATIGUE_WARNING,
                    "Performance cai ${drop}% na segunda metade",
                    "Considere pausas mais frequentes ou turnos mais curtos.",
                    0.6,
                    accepted.size
                ))
            }
        }

        // v6.3.1: Sugestão de bloqueio de bairros com alta taxa de recusa
        val allByHood = patterns.groupBy { it.neighborhood }.filter { it.key.isNotBlank() && it.value.size >= 5 }
        val highRefusalHoods = allByHood.filter { (_, rides) ->
            val refused = rides.count { !it.accepted }
            refused.toDouble() / rides.size >= 0.70 // 70%+ de recusa
        }.keys.take(3)
        if (highRefusalHoods.isNotEmpty()) {
            suggestions.add(LearningSuggestion(
                SuggestionType.AVOID_AREA,
                "Considere bloquear: ${highRefusalHoods.joinToString(", ")}",
                "Você recusa 70% ou mais das corridas nesses bairros. Bloqueie-os para evitar interrupções.",
                0.80,
                patterns.size
            ))
        }

        return suggestions
    }

    // v5.0.0: Guard contra lista vazia no average()
    private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()
}
