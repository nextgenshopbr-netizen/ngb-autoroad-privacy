package com.ngbautoroad.service

// ============================================================================
// ARQUIVO: AutoPilotEngine.kt
// VERSÃO: v6.1.0
// LOCALIZAÇÃO: service/AutoPilotEngine.kt
// RESPONSABILIDADE: Motor de decisão automática para aceitar/recusar corridas
//   - Avalia score vs critérios do perfil ativo
//   - Calcula delay humanizado baseado no score
//   - Executa click no botão Aceitar/Recusar via AccessibilityService
//   - Suporta modos: OFF, ACCEPT_ONLY, REFUSE_ONLY, FULL
// ============================================================================
// DELAY INTELIGENTE (não configurável pelo motorista):
//   Score 90-100: Aceita em 1-2s (corrida excelente, aceita rápido)
//   Score 75-89:  Aceita em 3-5s (corrida boa, "pensa" um pouco)
//   Score 60-74:  Zona neutra — NÃO age (motorista decide)
//   Score 40-59:  Recusa em 4-6s (corrida ruim, "hesita" antes de recusar)
//   Score 0-39:   Recusa em 1-2s (corrida péssima, recusa rápido)
// ============================================================================
// DEPENDENTES:
//   - RideAccessibilityService.kt → instancia e chama evaluateRide()
//   - OverlayService.kt → notifica sobre corrida detectada
//   - PrefsManager.kt → lê configurações de AutoPilot
// TAGS DE DEBUG: NGB_AUTOPILOT
// ============================================================================

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.ngbautoroad.data.model.Platform
import com.ngbautoroad.data.model.RideData
import com.ngbautoroad.data.prefs.PrefsManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.random.Random

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║  AutoPilotEngine — Decisão Automática de Corridas                  ║
 * ║                                                                      ║
 * ║  Modos:                                                              ║
 * ║  • OFF: Desligado (padrão) — motorista decide manualmente            ║
 * ║  • ACCEPT_ONLY: Só auto-aceita (score >= minScore)                   ║
 * ║  • REFUSE_ONLY: Só auto-recusa (score <= maxRefuseScore)             ║
 * ║  • FULL: Aceita E recusa automaticamente                             ║
 * ║                                                                      ║
 * ║  Delay humanizado por faixa de score:                                ║
 * ║  90-100 → 1.0-2.0s | 75-89 → 3.0-5.0s                              ║
 * ║  40-59  → 4.0-6.0s | 0-39  → 1.0-2.0s                              ║
 * ║  60-74  → ZONA NEUTRA (não age)                                      ║
 * ╚══════════════════════════════════════════════════════════════════════╝
 */
class AutoPilotEngine(
    private val context: Context,
    private val accessibilityService: AccessibilityService
) {

    companion object {
        private const val TAG = "NGB_AUTOPILOT"

        // ── Modos de operação ──
        const val MODE_OFF = "OFF"
        const val MODE_ACCEPT_ONLY = "ACCEPT_ONLY"
        const val MODE_REFUSE_ONLY = "REFUSE_ONLY"
        const val MODE_FULL = "FULL"

        // ── Textos de botões por plataforma ──
        // Uber Driver
        private val UBER_ACCEPT_TEXTS = listOf(
            "aceitar", "selecionar", "accept", "select", "aceptar"
        )
        private val UBER_REFUSE_TEXTS = listOf(
            "recusar", "✕", "×", "x", "fechar", "refuse", "decline", "rechazar"
        )

        // 99 Driver
        private val NINETY_NINE_ACCEPT_TEXTS = listOf(
            "aceitar", "accept", "aceptar"
        )
        private val NINETY_NINE_REFUSE_TEXTS = listOf(
            "recusar", "✕", "×", "fechar", "refuse"
        )

        // inDrive
        private val INDRIVE_ACCEPT_TEXTS = listOf(
            "aceitar", "accept", "confirmar", "aceptar"
        )
        private val INDRIVE_REFUSE_TEXTS = listOf(
            "recusar", "✕", "×", "fechar", "refuse", "rechazar"
        )
    }

    // ── Estado ──
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val prefsManager = PrefsManager(context)
    private var pendingAction: Runnable? = null
    private var isProcessing = false

    /**
     * ═══════════════════════════════════════════════════════════════════
     * MÉTODO PRINCIPAL: Avalia corrida e decide ação automática
     * ═══════════════════════════════════════════════════════════════════
     *
     * Chamado pelo OverlayService após calcular o score.
     * Verifica se AutoPilot está ativo e se deve agir.
     *
     * @param ride Dados da corrida
     * @param score Score calculado (0-100)
     * @param rideDbId ID da corrida no banco
     */
    fun evaluateRide(ride: RideData, score: Double, rideDbId: Long) {
        if (isProcessing) {
            Log.d(TAG, "│  ⊘ AutoPilot já processando outra corrida — ignorando")
            return
        }

        scope.launch {
            try {
                val mode = prefsManager.autoPilotModeFlow.first()
                if (mode == MODE_OFF) {
                    Log.d(TAG, "│  AutoPilot DESLIGADO — motorista decide")
                    return@launch
                }

                val minAcceptScore = prefsManager.autoPilotMinScoreFlow.first()
                val maxRefuseScore = prefsManager.autoPilotMaxRefuseScoreFlow.first()
                val geoFiltersEnabled = prefsManager.autoPilotGeoFiltersEnabledFlow.first()

                Log.i(TAG, "╔══════════════════════════════════════════════════╗")
                Log.i(TAG, "║  🤖 AUTOPILOT AVALIANDO CORRIDA                  ║")
                Log.i(TAG, "║  Mode: $mode | Score: ${String.format("%.1f", score)}")
                Log.i(TAG, "║  Accept ≥ $minAcceptScore | Refuse ≤ $maxRefuseScore")
                Log.i(TAG, "║  GeoFilters: ${if (geoFiltersEnabled) "ON" else "OFF"}")
                Log.i(TAG, "╚══════════════════════════════════════════════════╝")

                // ── Decisão ──
                val decision = when {
                    // Score alto → ACEITAR (se modo permite)
                    score >= minAcceptScore && (mode == MODE_ACCEPT_ONLY || mode == MODE_FULL) -> {
                        AutoPilotDecision.ACCEPT
                    }
                    // Score baixo → RECUSAR (se modo permite)
                    score <= maxRefuseScore && (mode == MODE_REFUSE_ONLY || mode == MODE_FULL) -> {
                        AutoPilotDecision.REFUSE
                    }
                    // Zona neutra → NÃO AGIR
                    else -> {
                        AutoPilotDecision.NEUTRAL
                    }
                }

                Log.i(TAG, "├─ Decisão: ${decision.name}")

                when (decision) {
                    AutoPilotDecision.ACCEPT -> {
                        val delay = calculateAcceptDelay(score)
                        Log.i(TAG, "├─ ✅ AUTO-ACEITAR em ${delay}ms (score=${String.format("%.1f", score)})")
                        scheduleAction(delay) {
                            performAccept(ride.platform)
                        }
                    }
                    AutoPilotDecision.REFUSE -> {
                        val delay = calculateRefuseDelay(score)
                        Log.i(TAG, "├─ ❌ AUTO-RECUSAR em ${delay}ms (score=${String.format("%.1f", score)})")
                        scheduleAction(delay) {
                            performRefuse(ride.platform)
                        }
                    }
                    AutoPilotDecision.NEUTRAL -> {
                        Log.i(TAG, "├─ ⚖ ZONA NEUTRA — motorista decide")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "├─ ✖ Erro no AutoPilot: ${e.message}")
            }
        }
    }

    // =========================================================================
    // BLOCO: Cálculo de Delay Humanizado
    // =========================================================================

    /**
     * Calcula delay para ACEITAR baseado no score.
     * Score mais alto = aceita mais rápido (motorista "animado")
     *
     * 90-100: 1000-2000ms (aceita rápido, corrida excelente)
     * 75-89:  3000-5000ms (aceita com calma, corrida boa)
     */
    private fun calculateAcceptDelay(score: Double): Long {
        return when {
            score >= 90 -> Random.nextLong(1000, 2001)   // 1-2s
            score >= 75 -> Random.nextLong(3000, 5001)   // 3-5s
            else -> Random.nextLong(2000, 4001)          // 2-4s (fallback)
        }
    }

    /**
     * Calcula delay para RECUSAR baseado no score.
     * Score mais baixo = recusa mais rápido (motorista "nem olha")
     *
     * 0-39:  1000-2000ms (recusa rápido, corrida péssima)
     * 40-59: 4000-6000ms (recusa com hesitação, corrida ruim)
     */
    private fun calculateRefuseDelay(score: Double): Long {
        return when {
            score <= 20 -> Random.nextLong(800, 1501)    // 0.8-1.5s
            score <= 39 -> Random.nextLong(1000, 2001)   // 1-2s
            score <= 59 -> Random.nextLong(4000, 6001)   // 4-6s
            else -> Random.nextLong(3000, 5001)          // 3-5s (fallback)
        }
    }

    // =========================================================================
    // BLOCO: Execução de Ação (Click nos botões)
    // =========================================================================

    /**
     * Agenda execução de ação com delay humanizado
     */
    private fun scheduleAction(delayMs: Long, action: () -> Unit) {
        cancelPendingAction()
        isProcessing = true

        pendingAction = Runnable {
            action()
            isProcessing = false
            pendingAction = null
        }
        handler.postDelayed(pendingAction!!, delayMs)
    }

    /**
     * Cancela ação pendente (se motorista agir antes)
     */
    fun cancelPendingAction() {
        pendingAction?.let {
            handler.removeCallbacks(it)
            pendingAction = null
            isProcessing = false
            Log.d(TAG, "│  ⊘ Ação pendente cancelada")
        }
    }

    /**
     * Executa click no botão ACEITAR da plataforma
     */
    private fun performAccept(platform: Platform) {
        Log.i(TAG, "├─ 🖱 Executando ACEITAR para ${platform.displayName}")

        val acceptTexts = when (platform) {
            Platform.UBER -> UBER_ACCEPT_TEXTS
            Platform.NINETY_NINE -> NINETY_NINE_ACCEPT_TEXTS
            Platform.INDRIVE -> INDRIVE_ACCEPT_TEXTS
            else -> UBER_ACCEPT_TEXTS + NINETY_NINE_ACCEPT_TEXTS + INDRIVE_ACCEPT_TEXTS
        }

        val clicked = findAndClickButton(acceptTexts, platform.packageName)
        if (clicked) {
            Log.i(TAG, "│  ✅ Botão ACEITAR clicado com sucesso!")
            // Notificar lifecycle que corrida foi aceita
            RideAccessibilityService.instance?.lifecycleManager?.onRideAccepted()
        } else {
            Log.w(TAG, "│  ⚠ Botão ACEITAR não encontrado — motorista deve agir manualmente")
        }
    }

    /**
     * Executa click no botão RECUSAR da plataforma
     */
    private fun performRefuse(platform: Platform) {
        Log.i(TAG, "├─ 🖱 Executando RECUSAR para ${platform.displayName}")

        val refuseTexts = when (platform) {
            Platform.UBER -> UBER_REFUSE_TEXTS
            Platform.NINETY_NINE -> NINETY_NINE_REFUSE_TEXTS
            Platform.INDRIVE -> INDRIVE_REFUSE_TEXTS
            else -> UBER_REFUSE_TEXTS + NINETY_NINE_REFUSE_TEXTS + INDRIVE_REFUSE_TEXTS
        }

        val clicked = findAndClickButton(refuseTexts, platform.packageName)
        if (clicked) {
            Log.i(TAG, "│  ✅ Botão RECUSAR clicado com sucesso!")
            // Notificar lifecycle que corrida foi recusada
            RideAccessibilityService.instance?.lifecycleManager?.onRideRefused()
        } else {
            Log.w(TAG, "│  ⚠ Botão RECUSAR não encontrado — motorista deve agir manualmente")
        }
    }

    /**
     * Busca botão na árvore de acessibilidade e executa click.
     * Percorre TODAS as janelas (getWindows) procurando nós clicáveis
     * cujo texto corresponda aos padrões esperados.
     *
     * @param buttonTexts Lista de textos possíveis do botão
     * @param targetPackage Package do app alvo
     * @return true se encontrou e clicou com sucesso
     */
    private fun findAndClickButton(buttonTexts: List<String>, targetPackage: String): Boolean {
        try {
            // Percorrer todas as janelas
            val windows = accessibilityService.windows ?: return false

            for (window in windows) {
                val root = window.root ?: continue
                val windowPackage = root.packageName?.toString() ?: ""

                // Só processar janelas do app alvo ou do sistema
                if (windowPackage != targetPackage && windowPackage.isNotEmpty()) {
                    try { root.recycle() } catch (_: Exception) {}
                    continue
                }

                // Buscar botão recursivamente
                val buttonNode = findClickableNode(root, buttonTexts)
                if (buttonNode != null) {
                    Log.d(TAG, "│    Botão encontrado: \"${buttonNode.text ?: buttonNode.contentDescription}\"")

                    // Executar click
                    val result = buttonNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.d(TAG, "│    Click result: $result")

                    try { buttonNode.recycle() } catch (_: Exception) {}
                    try { root.recycle() } catch (_: Exception) {}
                    return result
                }

                try { root.recycle() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "│  ✖ Erro ao buscar botão: ${e.message}")
        }
        return false
    }

    /**
     * Busca recursivamente um nó clicável cujo texto corresponda aos padrões.
     * Prioriza nós com isClickable=true, mas também tenta o pai se necessário.
     */
    private fun findClickableNode(node: AccessibilityNodeInfo, targetTexts: List<String>, depth: Int = 0): AccessibilityNodeInfo? {
        if (depth > 30) return null

        // Verificar texto do nó atual
        val nodeText = (node.text?.toString() ?: "").lowercase().trim()
        val nodeDesc = (node.contentDescription?.toString() ?: "").lowercase().trim()

        for (target in targetTexts) {
            if (nodeText.contains(target, ignoreCase = true) ||
                nodeDesc.contains(target, ignoreCase = true)) {

                // Se o nó é clicável, retornar ele
                if (node.isClickable) {
                    return AccessibilityNodeInfo.obtain(node)
                }

                // Se não é clicável, tentar o pai
                val parent = node.parent
                if (parent != null && parent.isClickable) {
                    return parent // Não reciclar, será usado pelo caller
                }
                parent?.recycle()

                // Último recurso: forçar click mesmo sem isClickable
                return AccessibilityNodeInfo.obtain(node)
            }
        }

        // Percorrer filhos
        for (i in 0 until node.childCount) {
            try {
                val child = node.getChild(i) ?: continue
                val result = findClickableNode(child, targetTexts, depth + 1)
                if (result != null) {
                    try { child.recycle() } catch (_: Exception) {}
                    return result
                }
                try { child.recycle() } catch (_: Exception) {}
            } catch (_: Exception) {}
        }

        return null
    }

    // =========================================================================
    // BLOCO: Enum de decisão
    // =========================================================================
    enum class AutoPilotDecision {
        ACCEPT,     // Auto-aceitar
        REFUSE,     // Auto-recusar
        NEUTRAL     // Zona neutra — não agir
    }

    /**
     * Cleanup ao destruir
     */
    fun destroy() {
        cancelPendingAction()
        scope.cancel()
        Log.d(TAG, "AutoPilotEngine destruído")
    }
}
