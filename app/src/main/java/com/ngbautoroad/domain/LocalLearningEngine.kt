package com.ngbautoroad.domain

// ============================================================================
// ARQUIVO: LocalLearningEngine.kt
// VERSÃO: v6.3.7 — Reescrito com Time Decay, segmentação por dia, EWMA calibration
// RESPONSABILIDADE: IA local — padrões estatísticos, sugestões sem nuvem/IA paga
// MELHORIAS v6.3.7:
//   - Time Decay: corridas recentes pesam mais que antigas (e^(-k*dias))
//   - Segmentação por dia da semana (semana vs fim de semana)
//   - Sugestão BEST_RIDE_TYPES implementada
//   - seedFromDatabase síncrono (sem race condition de delay fixo)
//   - Calibra AdaptiveScoringEngine ao carregar dados
//   - Persistência otimizada (apenas últimos 1000 padrões com timestamp)
// ============================================================================

import android.content.Context
import android.content.SharedPreferences
import com.ngbautoroad.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import kotlin.math.exp

enum class SuggestionType(val label: String, val icon: String) {
    BEST_HOURS("Melhores Horários", "🕐"),
    BEST_NEIGHBORHOODS("Melhores Bairros", "📍"),
    BEST_RIDE_TYPES("Melhores Tipos", "🚗"),
    STOP_SUGGESTION("Parar Agora", "🛑"),
    AVOID_AREA("Evitar Área", "⚠️"),
    EARNING_PATTERN("Padrão de Ganho", "📈"),
    FATIGUE_WARNING("Alerta Fadiga", "😴"),
    BEST_DAY("Melhor Dia", "📅")
}

data class LearningSuggestion(
    val type: SuggestionType, val title: String, val description: String,
    val confidence: Double, val basedOnRides: Int
)

data class RidePattern(
    val hour: Int,
    val dayOfWeek: Int,
    val neighborhood: String,
    val valuePerKm: Double,
    val accepted: Boolean,
    val timestamp: Long = System.currentTimeMillis(), // v6.3.7: para Time Decay
    val rideType: String = "" // v6.3.7: plataforma/tipo (Uber, 99, etc)
)

/**
 * Motor de aprendizado local — 100% offline.
 * v6.3.7: Reescrito com Time Decay e segmentação temporal.
 * Construtor sem Context = modo memória (para testes/preview).
 */
class LocalLearningEngine(context: Context? = null) {
    private val prefs: SharedPreferences? = context?.getSharedPreferences("learning_engine", Context.MODE_PRIVATE)
    private val appContext: Context? = context
    private val patterns = mutableListOf<RidePattern>()
    private val MAX_PATTERNS = 5000

    // v6.3.9: Custo/km do veículo para sugestões de lucro líquido
    private var vehicleCostPerKm: Double = 0.0

    fun setCostPerKm(cost: Double) { vehicleCostPerKm = cost }

    // v6.3.7: Constante de decaimento temporal
    // k = 0.03 → corrida de 7 dias atrás pesa ~81%, 30 dias ~41%, 90 dias ~7%
    private val DECAY_K = 0.03

    init {
        prefs?.getString("patterns_json_v2", null)?.let { json ->
            try {
                val arr = JSONArray(json)
                for (i in 0 until arr.length().coerceAtMost(MAX_PATTERNS)) {
                    val obj = arr.getJSONObject(i)
                    patterns.add(RidePattern(
                        hour = obj.getInt("h"),
                        dayOfWeek = obj.getInt("d"),
                        neighborhood = obj.getString("n"),
                        valuePerKm = obj.getDouble("v"),
                        accepted = obj.getBoolean("a"),
                        timestamp = obj.optLong("t", System.currentTimeMillis()),
                        rideType = obj.optString("rt", "")
                    ))
                }
            } catch (_: Exception) {}
        }
    }

    fun addPattern(p: RidePattern) {
        synchronized(patterns) {
            patterns.add(p)
            if (patterns.size > MAX_PATTERNS) patterns.removeAt(0)
        }
        persistPatterns()
    }

    fun getPatternCount(): Int = patterns.size

    /**
     * v6.3.7: Carrega histórico real do banco — SÍNCRONO (sem race condition).
     * Deve ser chamado dentro de withContext(Dispatchers.IO) pelo caller.
     * Também calibra o AdaptiveScoringEngine com os dados carregados.
     */
    suspend fun seedFromDatabase(context: Context) = withContext(Dispatchers.IO) {
        try {
            val dao = AppDatabase.getInstance(context).rideHistoryDao()
            // Usar getSince com timestamp de 90 dias atrás (evita carregar histórico inteiro)
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -90)
            val since90Days = cal.timeInMillis

            val rides = dao.getSince(since90Days).filter {
                it.status == "COMPLETED" || it.status == "ACCEPTED" || it.status == "REFUSED"
            }.takeLast(500)

            if (rides.isEmpty()) return@withContext

            // Evitar duplicar se já temos padrões suficientes no cache
            if (patterns.size >= rides.size) return@withContext

            val calLocal = Calendar.getInstance()
            val newPatterns = rides.map { ride ->
                calLocal.timeInMillis = ride.timestamp
                RidePattern(
                    hour = calLocal.get(Calendar.HOUR_OF_DAY),
                    dayOfWeek = calLocal.get(Calendar.DAY_OF_WEEK),
                    neighborhood = ride.pickupNeighborhood.ifBlank { ride.dropoffNeighborhood },
                    valuePerKm = ride.valuePerKm,
                    accepted = ride.status == "COMPLETED" || ride.status == "ACCEPTED",
                    timestamp = ride.timestamp,
                    rideType = ride.platform
                )
            }

            synchronized(patterns) {
                patterns.clear()
                patterns.addAll(newPatterns)
            }
            persistPatterns()

            // v6.3.7: Calibrar AdaptiveScoringEngine com os dados carregados
            val completedRides = rides.filter { it.status == "COMPLETED" || it.status == "ACCEPTED" }
            if (completedRides.size >= 30) {
                val adaptiveEngine = AdaptiveScoringEngine(context)
                adaptiveEngine.calibrate(
                    valuesPerKm = completedRides.filter { it.valuePerKm > 0 }.map { it.valuePerKm },
                    valuesPerHour = completedRides.filter { it.rideDuration > 0 }.map { it.rideValue / (it.rideDuration / 60.0) },
                    rideValues = completedRides.map { it.rideValue },
                    pickupDistances = completedRides.filter { it.pickupDistance > 0 }.map { it.pickupDistance },
                    dropoffDistances = completedRides.filter { it.dropoffDistance > 0 }.map { it.dropoffDistance },
                    durations = completedRides.filter { it.rideDuration > 0 }.map { it.rideDuration }
                )
            }
        } catch (_: Exception) {}
    }

    private fun persistPatterns() {
        prefs ?: return
        try {
            val arr = JSONArray()
            val toSave = synchronized(patterns) { patterns.takeLast(1000) }
            for (p in toSave) {
                arr.put(JSONObject().apply {
                    put("h", p.hour); put("d", p.dayOfWeek)
                    put("n", p.neighborhood); put("v", p.valuePerKm)
                    put("a", p.accepted); put("t", p.timestamp)
                    put("rt", p.rideType)
                })
            }
            prefs.edit().putString("patterns_json_v2", arr.toString()).apply()
        } catch (_: Exception) {}
    }

    /**
     * v6.3.7: Gera sugestões com Time Decay e segmentação por dia da semana.
     */
    fun generateSuggestions(): List<LearningSuggestion> {
        if (patterns.size < 20) return listOf(
            LearningSuggestion(
                SuggestionType.EARNING_PATTERN,
                "Colete mais dados",
                "Registre pelo menos 20 corridas para receber sugestões personalizadas. Atual: ${patterns.size}/20.",
                0.0,
                patterns.size
            )
        )

        val now = System.currentTimeMillis()
        val suggestions = mutableListOf<LearningSuggestion>()
        val accepted = synchronized(patterns) { patterns.filter { it.accepted }.toList() }

        // ═══════════════════════════════════════════════════════════════
        // SUGESTÃO 1: Melhores Horários (com Time Decay)
        // ═══════════════════════════════════════════════════════════════
        val weightedByHour = accepted.groupBy { it.hour }.mapValues { (_, rides) ->
            weightedAverage(rides.map { Pair(it.valuePerKm, timeDecayWeight(now, it.timestamp)) })
        }
        val bestHours = weightedByHour.entries.sortedByDescending { it.value }.take(3)
        if (bestHours.isNotEmpty()) {
            val globalAvg = weightedAverage(accepted.map { Pair(it.valuePerKm, timeDecayWeight(now, it.timestamp)) })
            if (globalAvg > 0) {
                val improvement = ((bestHours.first().value / globalAvg - 1) * 100).toInt()
                if (improvement > 5) {
                    suggestions.add(LearningSuggestion(
                        SuggestionType.BEST_HOURS,
                        "Melhores horários: ${bestHours.joinToString(", ") { "${it.key}h" }}",
                        "R\$/km ${improvement}% acima da média nesses horários (dados recentes pesam mais).",
                        0.7 + (patterns.size / 1000.0).coerceAtMost(0.25),
                        patterns.size
                    ))
                }
            }
        }

        // ═══════════════════════════════════════════════════════════════
        // SUGESTÃO 2: Melhores Bairros (com Time Decay)
        // ═══════════════════════════════════════════════════════════════
        val weightedByHood = accepted.filter { it.neighborhood.isNotBlank() }
            .groupBy { it.neighborhood }
            .filter { it.value.size >= 3 }
            .mapValues { (_, rides) ->
                weightedAverage(rides.map { Pair(it.valuePerKm, timeDecayWeight(now, it.timestamp)) })
            }
        val bestHoods = weightedByHood.entries.sortedByDescending { it.value }.take(3)
        if (bestHoods.size >= 2) {
            // v6.3.9: Se custo/km disponível, mostrar lucro líquido por bairro
            val profitInfo = if (vehicleCostPerKm > 0) {
                val bestProfit = bestHoods.first().value - vehicleCostPerKm
                " (lucro líq. ~R\$${String.format("%.2f", bestProfit)}/km)"
            } else ""
            suggestions.add(LearningSuggestion(
                SuggestionType.BEST_NEIGHBORHOODS,
                "Bairros mais rentáveis: ${bestHoods.joinToString(", ") { it.key }}",
                "Posicione-se próximo a esses bairros para maximizar ganhos$profitInfo.",
                0.65 + (patterns.size / 2000.0).coerceAtMost(0.25),
                patterns.size
            ))
        }

        // ═══════════════════════════════════════════════════════════════
        // SUGESTÃO 3: Melhores Tipos de Corrida (NOVO v6.3.7)
        // ═══════════════════════════════════════════════════════════════
        val byType = accepted.filter { it.rideType.isNotBlank() }
            .groupBy { it.rideType }
            .filter { it.value.size >= 5 }
            .mapValues { (_, rides) ->
                weightedAverage(rides.map { Pair(it.valuePerKm, timeDecayWeight(now, it.timestamp)) })
            }
        val bestTypes = byType.entries.sortedByDescending { it.value }.take(2)
        if (bestTypes.isNotEmpty() && byType.size >= 2) {
            suggestions.add(LearningSuggestion(
                SuggestionType.BEST_RIDE_TYPES,
                "Tipos mais rentáveis: ${bestTypes.joinToString(", ") { it.key }}",
                "Esses tipos de corrida rendem mais R\$/km no seu histórico recente.",
                0.6 + (patterns.size / 2000.0).coerceAtMost(0.2),
                accepted.size
            ))
        }

        // ═══════════════════════════════════════════════════════════════
        // SUGESTÃO 4: Melhor Dia da Semana (NOVO v6.3.7)
        // ═══════════════════════════════════════════════════════════════
        val dayNames = mapOf(1 to "Domingo", 2 to "Segunda", 3 to "Terça", 4 to "Quarta", 5 to "Quinta", 6 to "Sexta", 7 to "Sábado")
        val weightedByDay = accepted.groupBy { it.dayOfWeek }.mapValues { (_, rides) ->
            weightedAverage(rides.map { Pair(it.valuePerKm, timeDecayWeight(now, it.timestamp)) })
        }
        val bestDay = weightedByDay.entries.maxByOrNull { it.value }
        val worstDay = weightedByDay.entries.minByOrNull { it.value }
        if (bestDay != null && worstDay != null && weightedByDay.size >= 4) {
            val diff = ((bestDay.value / worstDay.value - 1) * 100).toInt()
            if (diff > 10) {
                suggestions.add(LearningSuggestion(
                    SuggestionType.BEST_DAY,
                    "Melhor dia: ${dayNames[bestDay.key] ?: "?"} (+${diff}% vs ${dayNames[worstDay.key] ?: "?"})",
                    "${dayNames[bestDay.key]} rende mais R\$/km que ${dayNames[worstDay.key]} no seu histórico.",
                    0.6,
                    accepted.size
                ))
            }
        }

        // ═══════════════════════════════════════════════════════════════
        // SUGESTÃO 5: Alerta de Fadiga (corrigido: por turno real, não por posição no array)
        // ═══════════════════════════════════════════════════════════════
        // Agrupar corridas do mesmo dia e comparar primeira vs segunda metade do turno
        val today = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY, 0); today.set(Calendar.MINUTE, 0); today.set(Calendar.SECOND, 0)
        val todayStart = today.timeInMillis
        val todayRides = accepted.filter { it.timestamp >= todayStart }.sortedBy { it.timestamp }
        if (todayRides.size >= 6) {
            val firstHalf = todayRides.take(todayRides.size / 2).map { it.valuePerKm }.averageOrZero()
            val secondHalf = todayRides.drop(todayRides.size / 2).map { it.valuePerKm }.averageOrZero()
            if (firstHalf > 0 && secondHalf < firstHalf * 0.80) {
                val drop = ((1 - secondHalf / firstHalf) * 100).toInt()
                suggestions.add(LearningSuggestion(
                    SuggestionType.FATIGUE_WARNING,
                    "Performance caiu ${drop}% no turno de hoje",
                    "Suas corridas recentes rendem menos que as primeiras. Considere uma pausa.",
                    0.75,
                    todayRides.size
                ))
            }
        }

        // ═══════════════════════════════════════════════════════════════
        // SUGESTÃO 6: Bairros a Evitar (com Time Decay)
        // ═══════════════════════════════════════════════════════════════
        val allPatterns = synchronized(patterns) { patterns.toList() }
        val allByHood = allPatterns.filter { it.neighborhood.isNotBlank() }
            .groupBy { it.neighborhood }
            .filter { it.value.size >= 5 }
        val highRefusalHoods = allByHood.filter { (_, rides) ->
            // Ponderar recusas recentes mais que antigas
            val weightedRefusal = rides.sumOf { r ->
                val w = timeDecayWeight(now, r.timestamp)
                if (!r.accepted) w else 0.0
            }
            val totalWeight = rides.sumOf { timeDecayWeight(now, it.timestamp) }
            totalWeight > 0 && (weightedRefusal / totalWeight) >= 0.65
        }.keys.take(3)

        if (highRefusalHoods.isNotEmpty()) {
            suggestions.add(LearningSuggestion(
                SuggestionType.AVOID_AREA,
                "Considere bloquear: ${highRefusalHoods.joinToString(", ")}",
                "Você recusa 65%+ das corridas nesses bairros recentemente. Bloqueie-os para evitar interrupções.",
                0.80,
                patterns.size
            ))
        }

        return suggestions
    }

    // ========================================================================
    // Helpers — Time Decay e Média Ponderada
    // ========================================================================

    /**
     * Calcula o peso de decaimento temporal: e^(-k * dias_passados)
     * Corrida de hoje = 1.0, corrida de 7 dias = 0.81, 30 dias = 0.41, 90 dias = 0.07
     */
    private fun timeDecayWeight(now: Long, timestamp: Long): Double {
        val daysPassed = ((now - timestamp) / 86_400_000.0).coerceAtLeast(0.0)
        return exp(-DECAY_K * daysPassed)
    }

    /**
     * Média ponderada: sum(valor * peso) / sum(peso)
     */
    private fun weightedAverage(data: List<Pair<Double, Double>>): Double {
        if (data.isEmpty()) return 0.0
        val totalWeight = data.sumOf { it.second }
        if (totalWeight <= 0) return 0.0
        return data.sumOf { it.first * it.second } / totalWeight
    }

    private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()
}
