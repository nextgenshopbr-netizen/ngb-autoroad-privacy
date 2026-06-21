package com.ngbautoroad.domain

import android.content.Context
import android.content.SharedPreferences
import com.ngbautoroad.data.model.RideData
import java.util.Calendar

// ============================================================================
// ARQUIVO: SmartRoutingEngine.kt
// VERSÃO: v6.6.0
// RESPONSABILIDADE: Rupturas #9, #11, #12
//   #9:  Direção de casa — priorizar corridas na direção de casa no fim do turno
//   #11: Detecção de padrão da plataforma — Uber "testa" com corridas ruins
//   #12: Multi-plataforma — preferência quando múltiplas plataformas estão ativas
// DEPENDENTES:
//   - RideScorer.kt → aplica bonus/penalidade de direção
//   - ProfitAwareAutoPilot.kt → usa para decisão multi-plataforma
//   - LocalLearningEngine.kt → detecta padrões de plataforma
// ============================================================================

/**
 * Ruptura #9: Configuração de "casa" do motorista.
 * Permite configurar bairro/região de casa para priorizar corridas
 * na direção correta no fim do turno.
 */
data class HomeConfig(
    val neighborhood: String = "",
    val isEnabled: Boolean = false,
    val activateAfterHours: Double = 6.0, // Ativar após X horas de turno
    val bonusPoints: Int = 10             // Bonus no score se corrida vai para casa
)

/**
 * Ruptura #11: Detecção de padrão de "teste" da plataforma.
 * Uber frequentemente manda corridas ruins em sequência antes de uma boa.
 * Se o motorista recusa muitas, pode ser penalizado pelo algoritmo.
 */
data class PlatformPatternAnalysis(
    val consecutiveRejects: Int = 0,
    val isTestingPattern: Boolean = false,
    val suggestion: String = "",
    val riskOfPenalty: PenaltyRisk = PenaltyRisk.NONE
)

enum class PenaltyRisk(val label: String) {
    NONE("Nenhum"),
    LOW("Baixo"),
    MODERATE("Moderado"),
    HIGH("Alto — considere aceitar a próxima")
}

/**
 * Ruptura #12: Preferência multi-plataforma.
 * Quando o motorista usa Uber + 99 + inDrive simultaneamente.
 */
data class PlatformPreference(
    val platform: String,
    val priority: Int = 1,        // 1 = mais alta
    val minScoreToAccept: Int = 60,
    val isActive: Boolean = true
)

/**
 * Motor de roteamento inteligente.
 * Combina direção de casa, padrões de plataforma e multi-plataforma.
 */
class SmartRoutingEngine(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("smart_routing_prefs", Context.MODE_PRIVATE)

    // ========================================================================
    // Ruptura #9: Direção de casa
    // ========================================================================

    fun getHomeConfig(): HomeConfig {
        return HomeConfig(
            neighborhood = prefs.getString("home_neighborhood", "") ?: "",
            isEnabled = prefs.getBoolean("home_enabled", false),
            activateAfterHours = prefs.getFloat("home_activate_hours", 6.0f).toDouble(),
            bonusPoints = prefs.getInt("home_bonus_points", 10)
        )
    }

    fun setHomeConfig(config: HomeConfig) {
        prefs.edit()
            .putString("home_neighborhood", config.neighborhood)
            .putBoolean("home_enabled", config.isEnabled)
            .putFloat("home_activate_hours", config.activateAfterHours.toFloat())
            .putInt("home_bonus_points", config.bonusPoints)
            .apply()
    }

    /**
     * Calcula bonus de direção de casa.
     * Retorna pontos extras se a corrida vai na direção de casa E o turno já passou do threshold.
     */
    fun getHomeDirectionBonus(ride: RideData, shiftState: ShiftState): Int {
        val config = getHomeConfig()
        if (!config.isEnabled || config.neighborhood.isBlank()) return 0
        if (shiftState.elapsedHours < config.activateAfterHours) return 0

        // Verificar se o destino da corrida é o bairro de casa ou próximo
        val dropoff = ride.dropoffNeighborhood.lowercase().trim()
        val home = config.neighborhood.lowercase().trim()

        return if (dropoff.contains(home) || home.contains(dropoff)) {
            config.bonusPoints
        } else 0
    }

    // ========================================================================
    // Ruptura #11: Detecção de padrão da plataforma
    // ========================================================================

    /**
     * Analisa se a plataforma está "testando" o motorista.
     * Padrão: 3+ rejeições consecutivas → próxima corrida pode ser boa
     * ou a plataforma pode penalizar.
     */
    fun analyzePlatformPattern(shiftState: ShiftState): PlatformPatternAnalysis {
        val consecutiveRejects = prefs.getInt("consecutive_rejects", 0)

        val isTestingPattern = consecutiveRejects >= 3
        val risk = when {
            consecutiveRejects >= 5 -> PenaltyRisk.HIGH
            consecutiveRejects >= 3 -> PenaltyRisk.MODERATE
            consecutiveRejects >= 2 -> PenaltyRisk.LOW
            else -> PenaltyRisk.NONE
        }

        val suggestion = when {
            consecutiveRejects >= 5 -> "⚠️ ${consecutiveRejects} recusas seguidas. A plataforma pode reduzir ofertas. Considere aceitar a próxima corrida razoável."
            consecutiveRejects >= 3 -> "🟡 ${consecutiveRejects} recusas seguidas. Possível teste da plataforma."
            else -> ""
        }

        return PlatformPatternAnalysis(
            consecutiveRejects = consecutiveRejects,
            isTestingPattern = isTestingPattern,
            suggestion = suggestion,
            riskOfPenalty = risk
        )
    }

    /**
     * Registra uma aceitação (reseta contador de rejeições).
     */
    fun registerAcceptance() {
        prefs.edit().putInt("consecutive_rejects", 0).apply()
    }

    /**
     * Registra uma rejeição (incrementa contador).
     */
    fun registerRejection() {
        val current = prefs.getInt("consecutive_rejects", 0)
        prefs.edit().putInt("consecutive_rejects", current + 1).apply()
    }

    // ========================================================================
    // Ruptura #12: Multi-plataforma
    // ========================================================================

    /**
     * Obtém as preferências de plataforma configuradas.
     */
    fun getPlatformPreferences(): List<PlatformPreference> {
        val platforms = prefs.getStringSet("active_platforms", setOf("Uber")) ?: setOf("Uber")
        return platforms.mapIndexed { index, platform ->
            PlatformPreference(
                platform = platform,
                priority = prefs.getInt("platform_priority_$platform", index + 1),
                minScoreToAccept = prefs.getInt("platform_min_score_$platform", 60),
                isActive = prefs.getBoolean("platform_active_$platform", true)
            )
        }.sortedBy { it.priority }
    }

    fun setPlatformPreference(pref: PlatformPreference) {
        prefs.edit()
            .putInt("platform_priority_${pref.platform}", pref.priority)
            .putInt("platform_min_score_${pref.platform}", pref.minScoreToAccept)
            .putBoolean("platform_active_${pref.platform}", pref.isActive)
            .apply()
    }

    /**
     * Verifica se deve aceitar uma corrida baseado na prioridade da plataforma.
     * Se a plataforma tem prioridade mais baixa, exige score mais alto.
     */
    fun getMinScoreForPlatform(platformName: String): Int {
        return prefs.getInt("platform_min_score_$platformName", 60)
    }

    /**
     * Verifica se é horário de pico (onde multi-plataforma é mais relevante).
     */
    fun isPeakHour(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour in 7..9 || hour in 11..13 || hour in 17..20
    }
}
