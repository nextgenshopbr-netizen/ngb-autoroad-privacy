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
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.ngbautoroad.data.model.Platform
import com.ngbautoroad.data.model.RideData
import com.ngbautoroad.data.prefs.PrefsManager
import com.ngbautoroad.domain.TelemetryLogger
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
        const val MODE_ACCEPT_ONLY = "ACCEPT_ONLY"  // legado
        const val MODE_REFUSE_ONLY = "REFUSE_ONLY"  // legado
        const val MODE_FULL = "FULL"                // legado
        // v6.1.1: Novos modos independentes
        const val MODE_ACCEPT = "ACCEPT"
        const val MODE_REFUSE = "REFUSE"
        const val MODE_BOTH = "BOTH"

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
    @Volatile private var isProcessing = false // v6.3.7: volatile para thread-safety
    private val processingLock = Any() // v6.3.7: lock para sincronização
    private var lastActionTimestamp = 0L // v6.3.7: timeout de segurança
    private var lastAutoAcceptedDbId: Long = -1L // v6.3.7: feedback loop

    // v6.3.7: Timeout de segurança — se isProcessing ficar true por mais de 15s, resetar
    private val PROCESSING_TIMEOUT_MS = 15_000L

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
        // v6.3.7: Timeout de segurança — resetar flag presa
        synchronized(processingLock) {
            if (isProcessing) {
                val elapsed = System.currentTimeMillis() - lastActionTimestamp
                if (elapsed > PROCESSING_TIMEOUT_MS) {
                    Log.w(TAG, "│  ⚠ Timeout de segurança: isProcessing preso por ${elapsed}ms — resetando")
                    isProcessing = false
                    pendingAction = null
                } else {
                    Log.d(TAG, "│  ⊘ AutoPilot já processando outra corrida — ignorando")
                    return
                }
            }
        }

        scope.launch {
            try {
                val telemetry = TelemetryLogger.getInstance(context)
                val mode = prefsManager.autoPilotModeFlow.first()
                if (mode == MODE_OFF) {
                    Log.d(TAG, "│  AutoPilot DESLIGADO — motorista decide")
                    telemetry.log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.INFO,
                        "AutoPilot: DESLIGADO (modo=OFF, rideId=$rideDbId)")
                    return@launch
                }

                val minAcceptScoreRaw = prefsManager.autoPilotMinScoreFlow.first()
                val maxRefuseScore = prefsManager.autoPilotMaxRefuseScoreFlow.first()
                val geoFiltersEnabled = prefsManager.autoPilotGeoFiltersEnabledFlow.first()

                // v6.3.9: Ajuste financeiro — Break-Even Aware
                val profitAware = com.ngbautoroad.domain.ProfitAwareAutoPilot(context)
                val financialCtx = profitAware.getFinancialContext()
                val minAcceptScore = profitAware.adjustMinScore(minAcceptScoreRaw, financialCtx)

                Log.i(TAG, "╔══════════════════════════════════════════════════╗")
                Log.i(TAG, "║  🤖 AUTOPILOT AVALIANDO CORRIDA                  ║")
                Log.i(TAG, "║  Mode: $mode | Score: ${String.format("%.1f", score)}")
                Log.i(TAG, "║  Accept ≥ $minAcceptScore (base=$minAcceptScoreRaw, adj=${financialCtx.scoreAdjustment})")
                Log.i(TAG, "║  Refuse ≤ $maxRefuseScore | GeoFilters: ${if (geoFiltersEnabled) "ON" else "OFF"}")
                Log.i(TAG, "║  💰 ${financialCtx.reason}")
                Log.i(TAG, "╚══════════════════════════════════════════════════╝")

                telemetry.log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.INFO,
                    "AutoPilot: Avaliando rideId=$rideDbId | Modo=$mode | Score=${String.format("%.1f", score)} | LimiteAceitar=$minAcceptScore | LimiteRecusar=$maxRefuseScore")

                // ── Decisão (v6.1.1: suporta modos combinados) ──
                val canAccept = mode in listOf(MODE_ACCEPT_ONLY, MODE_FULL, MODE_ACCEPT, MODE_BOTH)
                val canRefuse = mode in listOf(MODE_REFUSE_ONLY, MODE_FULL, MODE_REFUSE, MODE_BOTH)

                val decision = when {
                    // Score alto → ACEITAR (se modo permite)
                    score >= minAcceptScore && canAccept -> {
                        AutoPilotDecision.ACCEPT
                    }
                    // Score baixo → RECUSAR (se modo permite)
                    score <= maxRefuseScore && canRefuse -> {
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
                        telemetry.log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.INFO,
                            "AutoPilot: Decisão ACEITAR em ${delay}ms para rideId=$rideDbId")
                        scheduleAction(delay) {
                            performAccept(ride, rideDbId)
                        }
                    }
                    AutoPilotDecision.REFUSE -> {
                        val delay = calculateRefuseDelay(score)
                        Log.i(TAG, "├─ ❌ AUTO-RECUSAR em ${delay}ms (score=${String.format("%.1f", score)})")
                        telemetry.log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.INFO,
                            "AutoPilot: Decisão RECUSAR em ${delay}ms para rideId=$rideDbId")
                        scheduleAction(delay) {
                            performRefuse(ride.platform, rideDbId)
                        }
                    }
                    AutoPilotDecision.NEUTRAL -> {
                        Log.i(TAG, "├─ ⚖ ZONA NEUTRA — motorista decide")
                        telemetry.log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.INFO,
                            "AutoPilot: Decisão NEUTRA (zona neutra) para rideId=$rideDbId")
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
     * Agenda execução de ação com delay humanizado.
     * v6.3.7: Thread-safe com synchronized + timeout de segurança.
     */
    private fun scheduleAction(delayMs: Long, action: () -> Unit) {
        cancelPendingAction()
        synchronized(processingLock) {
            isProcessing = true
            lastActionTimestamp = System.currentTimeMillis()
        }

        pendingAction = Runnable {
            action()
            synchronized(processingLock) {
                isProcessing = false
                pendingAction = null
            }
        }
        handler.postDelayed(pendingAction!!, delayMs)
    }

    /**
     * Cancela ação pendente (se motorista agir antes).
     * v6.3.7: Thread-safe.
     */
    fun cancelPendingAction() {
        pendingAction?.let {
            handler.removeCallbacks(it)
            synchronized(processingLock) {
                pendingAction = null
                isProcessing = false
            }
            Log.d(TAG, "│  ⊘ Ação pendente cancelada")
        }
    }

    /**
     * v6.3.7: Feedback Loop — chamado pelo RideLifecycleManager quando o motorista
     * cancela manualmente uma corrida que foi auto-aceita pelo AutoPilot.
     * Registra o evento para que futuras decisões possam ser ajustadas.
     *
     * @param rideDbId ID da corrida que foi cancelada após auto-aceite
     */
    fun onAutoAcceptedRideCancelled(rideDbId: Long) {
        Log.w(TAG, "│  ⚠ FEEDBACK: Corrida $rideDbId auto-aceita foi CANCELADA pelo motorista")
        // Registrar em SharedPreferences para ajuste futuro da zona neutra
        val feedbackPrefs = context.getSharedPreferences("autopilot_feedback", Context.MODE_PRIVATE)
        val cancelCount = feedbackPrefs.getInt("cancel_count", 0) + 1
        feedbackPrefs.edit().putInt("cancel_count", cancelCount).apply()
        Log.i(TAG, "│  Feedback registrado: $cancelCount corridas auto-aceitas canceladas no total")
    }

    /**
     * v6.3.7: Registra o ID da corrida auto-aceita para feedback loop.
     */
    fun setLastAutoAcceptedId(dbId: Long) {
        lastAutoAcceptedDbId = dbId
    }

    fun getLastAutoAcceptedId(): Long = lastAutoAcceptedDbId

    /**
     * Executa click no botão ACEITAR da plataforma
     */
    fun performAccept(ride: RideData, rideDbId: Long) {
        val platform = ride.platform
        Log.i(TAG, "├─ 🖱 Executando ACEITAR para ${platform.displayName}")
        val telemetry = TelemetryLogger.getInstance(context)

        val acceptTexts = when (platform) {
            Platform.UBER -> UBER_ACCEPT_TEXTS
            Platform.NINETY_NINE -> NINETY_NINE_ACCEPT_TEXTS
            Platform.INDRIVE -> INDRIVE_ACCEPT_TEXTS
            else -> UBER_ACCEPT_TEXTS + NINETY_NINE_ACCEPT_TEXTS + INDRIVE_ACCEPT_TEXTS
        }

        val clicked = findAndClickButton(acceptTexts, platform.packageName)
        if (clicked) {
            Log.i(TAG, "│  ✅ Botão ACEITAR clicado com sucesso!")
            telemetry.log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.INFO,
                "AutoPilot: Botão ACEITAR clicado para rideId=$rideDbId")
            RideAccessibilityService.instance?.lifecycleManager?.onRideAccepted()
            RideAccessibilityService.instance?.userActionDetector?.stopMonitoring()
        } else {
            // v6.9.9: Fallback para Uber (swipe para radar / tap para corrida padrão)
            if (platform == Platform.UBER && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val isRadar = ride.metadata?.get("isRadar")?.toBoolean() ?: false
                if (isRadar) {
                    Log.i(TAG, "│  👆 Oferta detectada como RADAR. Tentando swipe (slider Uber)...")
                    val swipeResult = performSwipeAccept()
                    if (swipeResult) {
                        Log.i(TAG, "│  ✅ Swipe ACEITAR executado com sucesso!")
                        telemetry.log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.INFO,
                            "AutoPilot: Swipe ACEITAR executado para rideId=$rideDbId")
                        RideAccessibilityService.instance?.lifecycleManager?.onRideAccepted()
                        RideAccessibilityService.instance?.userActionDetector?.stopMonitoring()
                    } else {
                        Log.w(TAG, "│  ⚠ Swipe também falhou — motorista deve agir manualmente")
                        telemetry.log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.WARN,
                            "AutoPilot: Swipe falhou ao aceitar rideId=$rideDbId")
                        synchronized(processingLock) { isProcessing = false }
                    }
                } else {
                    Log.i(TAG, "│  👆 Oferta detectada como PADRÃO. Tentando tap/click (Card Uber)...")
                    val tapResult = performTapAccept()
                    if (tapResult) {
                        Log.i(TAG, "│  ✅ Toque/Tap ACEITAR executado com sucesso!")
                        telemetry.log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.INFO,
                            "AutoPilot: Toque ACEITAR executado para rideId=$rideDbId")
                        RideAccessibilityService.instance?.lifecycleManager?.onRideAccepted()
                        RideAccessibilityService.instance?.userActionDetector?.stopMonitoring()
                    } else {
                        Log.w(TAG, "│  ⚠ Toque/Tap também falhou — motorista deve agir manualmente")
                        telemetry.log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.WARN,
                            "AutoPilot: Toque ACEITAR falhou para rideId=$rideDbId")
                        synchronized(processingLock) { isProcessing = false }
                    }
                }
            } else {
                Log.w(TAG, "│  ⚠ Botão ACEITAR não encontrado — motorista deve agir manualmente")
                telemetry.log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.WARN,
                    "AutoPilot: Botão ACEITAR não encontrado para rideId=$rideDbId")
                synchronized(processingLock) { isProcessing = false }
            }
        }
    }

    /**
     * Executa click no botão RECUSAR da plataforma
     */
    fun performRefuse(platform: Platform, rideDbId: Long) {
        Log.i(TAG, "├─ 🖱 Executando RECUSAR para ${platform.displayName}")
        val telemetry = TelemetryLogger.getInstance(context)

        val refuseTexts = when (platform) {
            Platform.UBER -> UBER_REFUSE_TEXTS
            Platform.NINETY_NINE -> NINETY_NINE_REFUSE_TEXTS
            Platform.INDRIVE -> INDRIVE_REFUSE_TEXTS
            else -> UBER_REFUSE_TEXTS + NINETY_NINE_REFUSE_TEXTS + INDRIVE_REFUSE_TEXTS
        }

        val clicked = findAndClickButton(refuseTexts, platform.packageName)
        if (clicked) {
            Log.i(TAG, "│  ✅ Botão RECUSAR clicado com sucesso!")
            telemetry.log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.INFO,
                "AutoPilot: Botão RECUSAR clicado para rideId=$rideDbId")
            // Notificar lifecycle que corrida foi recusada
            RideAccessibilityService.instance?.lifecycleManager?.onRideRefused()
        } else {
            // Fallback tap for refuse (top-left X or top-right X depending on platform, usually top-right for Uber now or top-left)
            // Uber usually has X at top right (x=0.90, y=0.08) or top left (x=0.10, y=0.08)
            val tapResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) performTapRefuse() else false
            if (tapResult) {
                Log.i(TAG, "│  ✅ Toque/Tap RECUSAR executado (fallback) com sucesso!")
                telemetry.log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.INFO,
                    "AutoPilot: Toque RECUSAR executado (fallback) para rideId=$rideDbId")
                RideAccessibilityService.instance?.lifecycleManager?.onRideRefused()
            } else {
                Log.w(TAG, "│  ⚠ Botão RECUSAR não encontrado e Tap falhou — motorista deve agir manualmente")
                telemetry.log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.WARN,
                    "AutoPilot: Botão RECUSAR não encontrado para rideId=$rideDbId")
            }
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
    // v6.9.9: Swipe para aceitar (Uber slider)
    // =========================================================================
    @android.annotation.TargetApi(Build.VERSION_CODES.N)
    private fun performSwipeAccept(): Boolean {
        try {
            // O slider da Uber fica na parte inferior da tela
            // Swipe da esquerda para a direita (centro-baixo da tela)
            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels.toFloat()
            val screenHeight = displayMetrics.heightPixels.toFloat()

            // Ponto inicial: ~15% da largura, ~85% da altura (canto inferior esquerdo do slider)
            val startX = screenWidth * 0.15f
            val startY = screenHeight * 0.85f
            // Ponto final: ~85% da largura, mesma altura
            val endX = screenWidth * 0.85f
            val endY = startY

            val path = android.graphics.Path()
            path.moveTo(startX, startY)
            path.lineTo(endX, endY)

            val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(
                path, 0L, 300L // 300ms de duração para simular swipe natural
            )

            val gesture = android.accessibilityservice.GestureDescription.Builder()
                .addStroke(stroke)
                .build()

            var gestureResult = false
            val latch = java.util.concurrent.CountDownLatch(1)

            accessibilityService.dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                        gestureResult = true
                        latch.countDown()
                    }
                    override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) {
                        gestureResult = false
                        latch.countDown()
                    }
                },
                null
            )

            latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
            Log.d(TAG, "│    Swipe result: $gestureResult (${startX.toInt()},${startY.toInt()} -> ${endX.toInt()},${endY.toInt()})")
            return gestureResult
        } catch (e: Exception) {
            Log.e(TAG, "│  ✖ Erro ao executar swipe: ${e.message}")
            return false
        }
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.N)
    private fun performTapRefuse(): Boolean {
        // fix: O botão X do Uber muda de posição conforme versão do app e tamanho de tela.
        // Tentamos múltiplos candidatos em ordem de probabilidade:
        //   1. Canto superior direito (x=0.90, y=0.08) — Uber layout atual
        //   2. Canto superior esquerdo (x=0.10, y=0.08) — Uber layout antigo / 99
        //   3. Centro-superior (x=0.50, y=0.08) — layouts alternativos
        //   4. Canto superior direito mais baixo (x=0.90, y=0.12) — cards maiores
        val candidatePositions = listOf(
            Pair(0.90f, 0.08f),
            Pair(0.10f, 0.08f),
            Pair(0.50f, 0.08f),
            Pair(0.90f, 0.12f),
            Pair(0.10f, 0.12f)
        )

        try {
            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels.toFloat()
            val screenHeight = displayMetrics.heightPixels.toFloat()

            for ((xRatio, yRatio) in candidatePositions) {
                val clickX = screenWidth * xRatio
                val clickY = screenHeight * yRatio

                val path = android.graphics.Path()
                path.moveTo(clickX, clickY)

                val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(
                    path, 0L, 50L
                )

                val gesture = android.accessibilityservice.GestureDescription.Builder()
                    .addStroke(stroke)
                    .build()

                var gestureResult = false
                val latch = java.util.concurrent.CountDownLatch(1)

                accessibilityService.dispatchGesture(
                    gesture,
                    object : AccessibilityService.GestureResultCallback() {
                        override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                            gestureResult = true
                            latch.countDown()
                        }
                        override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) {
                            gestureResult = false
                            latch.countDown()
                        }
                    },
                    null
                )

                latch.await(1, java.util.concurrent.TimeUnit.SECONDS)
                Log.d(TAG, "│    Tap refuse em (${clickX.toInt()}, ${clickY.toInt()}): $gestureResult")

                if (gestureResult) return true

                // Aguardar 200ms entre tentativas para não sobrecarregar o sistema
                Thread.sleep(200)
            }

            Log.w(TAG, "│  ⚠ Todos os candidatos de Tap RECUSAR falharam")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "│  ✖ Erro ao executar tap refuse: ${e.message}")
            return false
        }
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.N)
    private fun performTapAccept(): Boolean {
        try {
            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels.toFloat()
            val screenHeight = displayMetrics.heightPixels.toFloat()

            // Toque rápido no centro-baixo da tela onde fica o card de oferta
            // Ajustado para 0.82f baseado no novo layout do Uber
            val clickX = screenWidth * 0.50f
            val clickY = screenHeight * 0.82f

            val path = android.graphics.Path()
            path.moveTo(clickX, clickY)

            val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(
                path, 0L, 50L // 50ms para simular toque rápido e limpo
            )

            val gesture = android.accessibilityservice.GestureDescription.Builder()
                .addStroke(stroke)
                .build()

            var gestureResult = false
            val latch = java.util.concurrent.CountDownLatch(1)

            accessibilityService.dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                        gestureResult = true
                        latch.countDown()
                    }
                    override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) {
                        gestureResult = false
                        latch.countDown()
                    }
                },
                null
            )

            latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
            Log.d(TAG, "│    Tap result: $gestureResult (${clickX.toInt()}, ${clickY.toInt()})")
            return gestureResult
        } catch (e: Exception) {
            Log.e(TAG, "│  ✖ Erro ao executar tap: ${e.message}")
            return false
        }
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
