package com.ngbautoroad.domain

import android.content.Context
import android.content.SharedPreferences
import com.ngbautoroad.data.model.RideData
import java.util.Calendar

// ============================================================================
// ARQUIVO: SafetyScoreModifier.kt
// VERSÃO: v6.6.0
// RESPONSABILIDADE: Ruptura #5 — Análise de segurança multi-fator
//   Cruza: horário + bairro + rating do passageiro para gerar penalidade
//   Cada fator isolado pode parecer OK, mas combinados representam risco.
// DEPENDENTES:
//   - RideScorer.kt → aplica penalidade de segurança no score final
//   - OverlayCard.kt → exibe alerta visual de segurança
// ============================================================================

/**
 * Resultado da análise de segurança multi-fator.
 * @param totalPenalty Penalidade total a subtrair do score
 * @param riskLevel Nível de risco geral
 * @param factors Lista de fatores de risco identificados
 */
data class SafetyAnalysis(
    val totalPenalty: Double = 0.0,
    val riskLevel: RiskLevel = RiskLevel.SAFE,
    val factors: List<SafetyFactor> = emptyList()
) {
    val hasRisk: Boolean get() = riskLevel != RiskLevel.SAFE
    val alertMessage: String
        get() = when (riskLevel) {
            RiskLevel.SAFE -> ""
            RiskLevel.LOW -> "⚠️ Atenção: ${factors.size} fator(es) de risco"
            RiskLevel.MODERATE -> "🟠 Risco moderado: ${factors.joinToString(", ") { it.label }}"
            RiskLevel.HIGH -> "🔴 RISCO ALTO: ${factors.joinToString(", ") { it.label }}"
            RiskLevel.CRITICAL -> "⛔ RISCO CRÍTICO: ${factors.joinToString(", ") { it.label }}"
        }
}

enum class RiskLevel(val label: String) {
    SAFE("Seguro"),
    LOW("Baixo"),
    MODERATE("Moderado"),
    HIGH("Alto"),
    CRITICAL("Crítico")
}

enum class SafetyFactor(val label: String, val basePenalty: Double) {
    LATE_NIGHT("Horário noturno", 5.0),          // 23h-5h
    EARLY_MORNING("Madrugada", 3.0),             // 5h-6h
    LOW_RATING("Passageiro mal avaliado", 8.0),  // < 4.5
    VERY_LOW_RATING("Passageiro perigoso", 15.0),// < 4.3
    DANGEROUS_AREA("Área de risco", 10.0),       // Bairro marcado como perigoso
    LONG_PICKUP("Pickup distante + noite", 5.0), // > 3km + noturno
    FATIGUE("Motorista fatigado", 7.0),          // > 6h de turno
    NEW_PASSENGER("Passageiro novo", 3.0)        // Rating = 0 (sem histórico)
}

/**
 * Motor de análise de segurança multi-fator.
 * Combina múltiplos indicadores para gerar uma penalidade composta.
 * A penalidade é MULTIPLICADA quando múltiplos fatores coexistem (efeito cascata).
 */
class SafetyScoreModifier(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("safety_prefs", Context.MODE_PRIVATE)

    // Bairros marcados como perigosos pelo motorista (configurável)
    private val dangerousAreas: Set<String>
        get() = prefs.getStringSet("dangerous_areas", emptySet()) ?: emptySet()

    /**
     * Analisa uma corrida e retorna a penalidade de segurança.
     * @param ride Dados da corrida
     * @param shiftState Estado atual do turno (para fadiga)
     * @return SafetyAnalysis com penalidade e fatores identificados
     */
    fun analyze(ride: RideData, shiftState: ShiftState? = null): SafetyAnalysis {
        val factors = mutableListOf<SafetyFactor>()

        // === Fator 1: Horário ===
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val isLateNight = hour in 23..23 || hour in 0..4
        val isEarlyMorning = hour == 5
        if (isLateNight) factors.add(SafetyFactor.LATE_NIGHT)
        else if (isEarlyMorning) factors.add(SafetyFactor.EARLY_MORNING)

        // === Fator 2: Rating do passageiro ===
        val rating = ride.passengerRating
        when {
            rating == 0.0 -> factors.add(SafetyFactor.NEW_PASSENGER) // Sem rating
            rating < 4.3 -> factors.add(SafetyFactor.VERY_LOW_RATING)
            rating < 4.5 -> factors.add(SafetyFactor.LOW_RATING)
        }

        // === Fator 3: Bairro perigoso ===
        val pickup = ride.pickupNeighborhood.lowercase().trim()
        val dropoff = ride.dropoffNeighborhood.lowercase().trim()
        val isDangerousArea = dangerousAreas.any { area ->
            pickup.contains(area.lowercase()) || dropoff.contains(area.lowercase())
        }
        if (isDangerousArea) factors.add(SafetyFactor.DANGEROUS_AREA)

        // === Fator 4: Pickup distante em horário noturno ===
        if (isLateNight && ride.pickupDistance > 3.0) {
            factors.add(SafetyFactor.LONG_PICKUP)
        }

        // === Fator 5: Fadiga do motorista ===
        if (shiftState != null && shiftState.fatigueLevel.ordinal >= FatigueLevel.MODERATE.ordinal) {
            factors.add(SafetyFactor.FATIGUE)
        }

        // === Calcular penalidade composta ===
        if (factors.isEmpty()) return SafetyAnalysis()

        // Penalidade base: soma dos fatores
        val basePenalty = factors.sumOf { it.basePenalty }

        // Multiplicador cascata: cada fator adicional aumenta 30% a penalidade
        val cascadeMultiplier = 1.0 + (factors.size - 1) * 0.3
        val totalPenalty = (basePenalty * cascadeMultiplier).coerceAtMost(50.0)

        // Nível de risco
        val riskLevel = when {
            totalPenalty >= 35.0 -> RiskLevel.CRITICAL
            totalPenalty >= 25.0 -> RiskLevel.HIGH
            totalPenalty >= 15.0 -> RiskLevel.MODERATE
            totalPenalty >= 5.0 -> RiskLevel.LOW
            else -> RiskLevel.SAFE
        }

        return SafetyAnalysis(
            totalPenalty = totalPenalty,
            riskLevel = riskLevel,
            factors = factors
        )
    }

    // === Gerenciamento de áreas perigosas ===

    fun addDangerousArea(area: String) {
        val current = dangerousAreas.toMutableSet()
        current.add(area.lowercase().trim())
        prefs.edit().putStringSet("dangerous_areas", current).apply()
    }

    fun removeDangerousArea(area: String) {
        val current = dangerousAreas.toMutableSet()
        current.remove(area.lowercase().trim())
        prefs.edit().putStringSet("dangerous_areas", current).apply()
    }

    fun listDangerousAreas(): Set<String> = dangerousAreas

    /**
     * Verifica se a análise de segurança está habilitada
     */
    fun isEnabled(): Boolean = prefs.getBoolean("safety_analysis_enabled", true)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("safety_analysis_enabled", enabled).apply()
    }
}

/**
 * Extensão estática para uso direto no RideScorer (sem Context).
 * Calcula penalidade simplificada baseada em rating + bairros bloqueados + horário.
 */
object SafetyScoreModifierStatic {
    fun calculatePenalty(
        passengerRating: Double,
        ratingThreshold: Double,
        pickupNeighborhood: String,
        dropoffNeighborhood: String,
        blockedNeighborhoods: List<Any>
    ): Double {
        var penalty = 0.0

        // Fator horário
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val isNight = hour in 23..23 || hour in 0..5
        if (isNight) penalty += 3.0

        // Fator rating (já penalizado pelo multiplicador, aqui é o efeito cascata)
        val isLowRating = passengerRating > 0 && passengerRating < 4.5
        if (isLowRating && isNight) penalty += 8.0 // Cascata: rating baixo + noite

        // Fator bairro perigoso + noite
        val pickup = pickupNeighborhood.lowercase().trim()
        val dropoff = dropoffNeighborhood.lowercase().trim()
        val isInBlockedArea = blockedNeighborhoods.any {
            val blockedName = (it as? com.ngbautoroad.data.model.BlockedNeighborhood)?.name?.lowercase()?.trim() ?: it.toString().lowercase().trim()
            if (blockedName.isNotEmpty()) {
                pickup.contains(blockedName) || dropoff.contains(blockedName)
            } else false
        }
        if (isInBlockedArea && isNight && isLowRating) penalty += 10.0 // Triplo fator

        return penalty.coerceAtMost(25.0) // Cap para não duplicar com penalidade de bairro
    }
}
