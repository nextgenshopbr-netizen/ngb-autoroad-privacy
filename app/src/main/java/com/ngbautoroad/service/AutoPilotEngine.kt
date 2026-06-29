package com.ngbautoroad.service

// ============================================================================
// ARQUIVO: AutoPilotEngine.kt
// VERSÃO: v7.2.0
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
//   Score 60-74:  Zona neutra -- NAO age (motorista decide)
//   Score 40-59:  Recusa em 4-6s (corrida ruim, "hesita" antes de recusar)
//   Score 0-39:   Recusa em 1-2s (corrida pessima, recusa rapido)
// ============================================================================
// DEPENDENTES:
//   - RideAccessibilityService.kt -> instancia e chama evaluateRide()
//   - OverlayService.kt -> notifica sobre corrida detectada
//   - PrefsManager.kt -> le configuracoes de AutoPilot
// TAGS DE DEBUG: NGB_AUTOPILOT
// ============================================================================

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Build
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
import kotlin.math.*

class AutoPilotEngine(
    private val context: Context,
    private val accessibilityService: AccessibilityService
) {

    companion object {
        private const val TAG = "NGB_AUTOPILOT"

        const val MODE_OFF = "OFF"
        const val MODE_ACCEPT_ONLY = "ACCEPT_ONLY"
        const val MODE_REFUSE_ONLY = "REFUSE_ONLY"
        const val MODE_FULL = "FULL"
        const val MODE_ACCEPT = "ACCEPT"
        const val MODE_REFUSE = "REFUSE"
        const val MODE_BOTH = "BOTH"

        // v7.2.0: Arrays expandidos com variacoes de A/B test e locale PT-BR
        private val UBER_ACCEPT_TEXTS = listOf(
            "aceitar", "selecionar", "aceitar corrida", "pegar corrida",
            "ir buscar", "accept", "accept trip", "select", "aceptar", "aceptar viaje"
        )
        private val UBER_REFUSE_TEXTS = listOf(
            "recusar", "negar", "passar", "nao quero",
            "✕", "×", "x", "fechar", "refuse", "decline", "skip", "rechazar"
        )
        private val NINETY_NINE_ACCEPT_TEXTS = listOf(
            "aceitar", "aceitar corrida", "confirmar", "pegar", "accept", "aceptar"
        )
        private val NINETY_NINE_REFUSE_TEXTS = listOf(
            "recusar", "recusar corrida", "passar", "negar",
            "✕", "×", "fechar", "refuse"
        )
        private val INDRIVE_ACCEPT_TEXTS = listOf(
            "aceitar", "aceitar oferta", "confirmar", "fazer oferta",
            "accept", "accept offer", "aceptar", "aceptar oferta"
        )
        private val INDRIVE_REFUSE_TEXTS = listOf(
            "recusar", "recusar oferta", "ignorar", "passar",
            "✕", "×", "fechar", "refuse", "rechazar"
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val prefsManager = PrefsManager(context)
    @Volatile private var pendingAction: Runnable? = null
    @Volatile private var isProcessing = false
    private val processingLock = Any()
    @Volatile private var lastActionTimestamp = 0L
    private var lastAutoAcceptedDbId: Long = -1L
    private val PROCESSING_TIMEOUT_MS = 8_000L

    fun evaluateRide(ride: RideData, score: Double, rideDbId: Long) {
        synchronized(processingLock) {
            if (isProcessing) {
                val elapsed = System.currentTimeMillis() - lastActionTimestamp
                if (elapsed > PROCESSING_TIMEOUT_MS) {
                    Log.w(TAG, "Timeout de seguranca: isProcessing preso por ${elapsed}ms -- resetando")
                    isProcessing = false
                    pendingAction = null
                } else {
                    Log.d(TAG, "AutoPilot ja processando outra corrida -- ignorando")
                    return
                }
            }
        }

        scope.launch {
            try {
                val telemetry = TelemetryLogger.getInstance(context)
                val mode = prefsManager.autoPilotModeFlow.first()
                if (mode == MODE_OFF) {
                    Log.d(TAG, "AutoPilot DESLIGADO -- motorista decide")
                    telemetry.log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.INFO,
                        "AutoPilot: DESLIGADO (modo=OFF, rideId=$rideDbId)")
                    return@launch
                }

                val minAcceptScoreRaw = prefsManager.autoPilotMinScoreFlow.first()
                val maxRefuseScore = prefsManager.autoPilotMaxRefuseScoreFlow.first()
                val geoFiltersEnabled = prefsManager.autoPilotGeoFiltersEnabledFlow.first()

                val profitAware = com.ngbautoroad.domain.ProfitAwareAutoPilot(context)
                val financialCtx = profitAware.getFinancialContext()
                val minAcceptScore = profitAware.adjustMinScore(minAcceptScoreRaw, financialCtx)

                Log.i(TAG, "AUTOPILOT | Mode=$mode Score=${String.format("%.1f", score)} Accept>=$minAcceptScore Refuse<=$maxRefuseScore")
                telemetry.log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.INFO,
                    "AutoPilot: Avaliando rideId=$rideDbId Modo=$mode Score=${String.format("%.1f", score)}")

                val canAccept = mode in listOf(MODE_ACCEPT_ONLY, MODE_FULL, MODE_ACCEPT, MODE_BOTH)
                val canRefuse = mode in listOf(MODE_REFUSE_ONLY, MODE_FULL, MODE_REFUSE, MODE_BOTH)

                val decision = when {
                    score >= minAcceptScore && canAccept -> AutoPilotDecision.ACCEPT
                    score <= maxRefuseScore && canRefuse -> AutoPilotDecision.REFUSE
                    else -> AutoPilotDecision.NEUTRAL
                }

                Log.i(TAG, "Decisao: ${decision.name}")

                when (decision) {
                    AutoPilotDecision.ACCEPT -> {
                        val delay = calculateAcceptDelay(score)
                        Log.i(TAG, "AUTO-ACEITAR em ${delay}ms")
                        telemetry.log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.INFO,
                            "AutoPilot: Decisao ACEITAR em ${delay}ms para rideId=$rideDbId")
                        scheduleAction(delay) { performAccept(ride, rideDbId) }
                    }
                    AutoPilotDecision.REFUSE -> {
                        val delay = calculateRefuseDelay(score)
                        Log.i(TAG, "AUTO-RECUSAR em ${delay}ms")
                        telemetry.log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.INFO,
                            "AutoPilot: Decisao RECUSAR em ${delay}ms para rideId=$rideDbId")
                        scheduleAction(delay) { performRefuse(ride.platform, rideDbId) }
                    }
                    AutoPilotDecision.NEUTRAL -> {
                        Log.i(TAG, "ZONA NEUTRA -- motorista decide")
                        telemetry.log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.INFO,
                            "AutoPilot: Decisao NEUTRA para rideId=$rideDbId")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro no AutoPilot: ${e.message}")
            }
        }
    }

    // =========================================================================
    // BLOCO: Delay Humanizado (v7.2.0: distribuicao log-normal)
    // =========================================================================

    // Score 90-100: media ~1.5s | Score 75-89: media ~4.0s | fallback: media ~3.0s
    private fun calculateAcceptDelay(score: Double): Long = when {
        score >= 90 -> logNormalDelay(meanMs = 1500.0, stdDevMs = 300.0)
        score >= 75 -> logNormalDelay(meanMs = 4000.0, stdDevMs = 800.0)
        else        -> logNormalDelay(meanMs = 3000.0, stdDevMs = 600.0)
    }

    // Score 0-20: media ~1.1s | 21-39: ~1.5s | 40-59: ~5.0s | fallback: ~3.5s
    private fun calculateRefuseDelay(score: Double): Long = when {
        score <= 20 -> logNormalDelay(meanMs = 1100.0, stdDevMs = 250.0)
        score <= 39 -> logNormalDelay(meanMs = 1500.0, stdDevMs = 350.0)
        score <= 59 -> logNormalDelay(meanMs = 5000.0, stdDevMs = 900.0)
        else        -> logNormalDelay(meanMs = 3500.0, stdDevMs = 700.0)
    }

    /**
     * v7.2.0: Delay com distribuicao log-normal (imita tempo de reacao humana real).
     * Distribuicao uniforme e detectavel por analise estatistica de timestamps.
     * Log-normal tem cauda longa a direita, identica a distribuicao de reacao humana.
     * Box-Muller transform para Gaussiana sem nextGaussian() do Java.
     * Resultado clampado entre 500ms e 10s.
     */
    private fun logNormalDelay(meanMs: Double, stdDevMs: Double): Long {
        val variance = stdDevMs * stdDevMs
        val mu    = ln(meanMs * meanMs / sqrt(variance + meanMs * meanMs))
        val sigma = sqrt(ln(1.0 + variance / (meanMs * meanMs)))
        val u1 = Random.nextDouble().coerceIn(1e-10, 1.0)
        val u2 = Random.nextDouble()
        val z  = sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
        return exp(mu + sigma * z).toLong().coerceIn(500L, 10_000L)
    }

    // =========================================================================
    // BLOCO: Execucao de Acao
    // =========================================================================

    // v7.2.0: Notifica sucesso em unico ponto — fecha overlay + atualiza lifecycle
    private fun notifyAcceptSuccess(ride: RideData, rideDbId: Long) {
        RideAccessibilityService.instance?.lifecycleManager?.onRideAccepted()
        RideAccessibilityService.instance?.userActionDetector?.stopMonitoring()
        OverlayService.onRideAccepted?.invoke()
        Log.i(TAG, "Lifecycle: corrida $rideDbId ACEITA -- overlay fechado")
        TelemetryLogger.getInstance(context).autopilot("ACEITAR CONFIRMADO rideId=$rideDbId plataforma=${ride.platform.displayName}")
    }

    private fun notifyRefuseSuccess(rideDbId: Long) {
        RideAccessibilityService.instance?.lifecycleManager?.onRideRefused()
        OverlayService.onRideAccepted?.invoke()
        Log.i(TAG, "Lifecycle: corrida $rideDbId RECUSADA -- overlay fechado")
        TelemetryLogger.getInstance(context).autopilot("RECUSAR CONFIRMADO rideId=$rideDbId")
    }

    // v7.2.0: Root da janela do app alvo (auxiliar para confirmacao)
    private fun getWindowRoot(targetPackage: String): AccessibilityNodeInfo? {
        val windows = try { accessibilityService.windows } catch (_: Exception) { null } ?: return null
        for (window in windows) {
            val root = window.root ?: continue
            val pkg  = root.packageName?.toString() ?: ""
            if (pkg == targetPackage || pkg.isEmpty()) return root
            try { root.recycle() } catch (_: Exception) {}
        }
        return null
    }

    /**
     * v7.2.0: Gesture Confirmation Loop.
     *
     * Verifica [delayMs] apos disparo se o botao ACEITAR ainda esta visivelna tela.
     * - Nao visivel: acao confirmada, chama [onConfirmed]
     * - Ainda visivel: retry (tap central) + nova verificacao em 600ms
     * - Ainda visivel apos retry: falha silenciosa, overlay fecha por auto-dismiss
     *
     * Verifica ausencia do ACEITAR tanto para aceitar quanto para recusar porque
     * apos qualquer acao a tela da oferta desaparece com seus dois botoes.
     */
    private fun scheduleGestureConfirmation(
        delayMs: Long,
        acceptTexts: List<String>,
        pkgName: String,
        onConfirmed: () -> Unit
    ) {
        handler.postDelayed({
            val root = getWindowRoot(pkgName)
            val buttonNode = root?.let { findClickableNode(it, acceptTexts) }
            val buttonStillPresent = buttonNode != null
            try { buttonNode?.recycle() } catch (_: Exception) {}
            try { root?.recycle() } catch (_: Exception) {}

            if (!buttonStillPresent) {
                Log.i(TAG, "Confirmacao OK: botao ACEITAR nao encontrado -- acao executada")
                onConfirmed()
            } else {
                Log.w(TAG, "Confirmacao: botao ACEITAR ainda visivel -- disparando retry")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    try {
                        val sw = context.resources.displayMetrics.widthPixels.toFloat()
                        val sh = context.resources.displayMetrics.heightPixels.toFloat()
                        val path = android.graphics.Path().apply { moveTo(sw * 0.5f, sh * 0.87f) }
                        val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0L, 50L)
                        val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()
                        accessibilityService.dispatchGesture(gesture, null, null)
                    } catch (_: Exception) {}
                }
                handler.postDelayed({
                    val root2 = getWindowRoot(pkgName)
                    val node2 = root2?.let { findClickableNode(it, acceptTexts) }
                    val stillPresent = node2 != null
                    try { node2?.recycle() } catch (_: Exception) {}
                    try { root2?.recycle() } catch (_: Exception) {}
                    if (!stillPresent) {
                        Log.i(TAG, "Confirmacao pos-retry OK: acao executada")
                        onConfirmed()
                    } else {
                        Log.w(TAG, "Confirmacao: retry falhou -- overlay fecha por auto-dismiss")
                    }
                }, 600L)
            }
        }, delayMs)
    }

    private fun scheduleAction(delayMs: Long, action: () -> Unit) {
        cancelPendingAction()
        synchronized(processingLock) {
            isProcessing = true
            lastActionTimestamp = System.currentTimeMillis()
        }
        pendingAction = Runnable {
            try {
                action()
            } catch (e: Exception) {
                Log.e(TAG, "Excecao na acao agendada: ${e.message}", e)
            } finally {
                synchronized(processingLock) {
                    isProcessing = false
                    pendingAction = null
                }
            }
        }
        pendingAction?.let { handler.postDelayed(it, delayMs) }
    }

    fun cancelPendingAction() {
        pendingAction?.let {
            handler.removeCallbacks(it)
            synchronized(processingLock) {
                pendingAction = null
                isProcessing = false
            }
            Log.d(TAG, "Acao pendente cancelada")
        }
    }

    fun onAutoAcceptedRideCancelled(rideDbId: Long) {
        Log.w(TAG, "FEEDBACK: Corrida $rideDbId auto-aceita foi CANCELADA pelo motorista")
        val feedbackPrefs = context.getSharedPreferences("autopilot_feedback", Context.MODE_PRIVATE)
        val cancelCount = feedbackPrefs.getInt("cancel_count", 0) + 1
        feedbackPrefs.edit().putInt("cancel_count", cancelCount).apply()
        Log.i(TAG, "Feedback registrado: $cancelCount corridas auto-aceitas canceladas no total")
    }

    fun setLastAutoAcceptedId(dbId: Long) { lastAutoAcceptedDbId = dbId }
    fun getLastAutoAcceptedId(): Long = lastAutoAcceptedDbId

    /**
     * Executa click no botao ACEITAR.
     * v7.2.0: useBoundsDetection=true (fallback geometrico) + gesture confirmation loop.
     * Lifecycle notificado somente apos confirmar que o botao desapareceu.
     */
    fun performAccept(ride: RideData, rideDbId: Long) {
        val platform = ride.platform
        Log.i(TAG, "Executando ACEITAR para ${platform.displayName}")
        val telemetry = TelemetryLogger.getInstance(context)

        val acceptTexts = when (platform) {
            Platform.UBER -> UBER_ACCEPT_TEXTS
            Platform.NINETY_NINE -> NINETY_NINE_ACCEPT_TEXTS
            Platform.INDRIVE -> INDRIVE_ACCEPT_TEXTS
            else -> UBER_ACCEPT_TEXTS + NINETY_NINE_ACCEPT_TEXTS + INDRIVE_ACCEPT_TEXTS
        }

        val clicked = findAndClickButton(acceptTexts, platform.packageName, useBoundsDetection = true)
        if (clicked) {
            Log.i(TAG, "Botao ACEITAR clicado -- aguardando confirmacao em 800ms")
            telemetry.log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.INFO,
                "AutoPilot: Botao ACEITAR clicado para rideId=$rideDbId (aguardando confirmacao)")
            scheduleGestureConfirmation(800L, acceptTexts, platform.packageName) {
                notifyAcceptSuccess(ride, rideDbId)
                telemetry.log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.INFO,
                    "AutoPilot: ACEITAR confirmado para rideId=$rideDbId")
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val isRadar = (platform == Platform.UBER) && (ride.metadata?.get("isRadar")?.toBoolean() ?: false)
                val fallbackResult = if (isRadar) {
                    Log.i(TAG, "Radar Uber -- disparando swipe")
                    performSwipeAccept()
                } else {
                    Log.i(TAG, "Fallback tap para ${platform.displayName} -- 5 candidatos")
                    performTapAccept()
                }
                if (fallbackResult) {
                    Log.i(TAG, "Fallback ACEITAR disparado -- confirmacao em 1600ms")
                    telemetry.log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.INFO,
                        "AutoPilot: Fallback ACEITAR (${if (isRadar) "swipe" else "tap"}) para rideId=$rideDbId")
                    scheduleGestureConfirmation(1600L, acceptTexts, platform.packageName) {
                        notifyAcceptSuccess(ride, rideDbId)
                        telemetry.log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.INFO,
                            "AutoPilot: Fallback ACEITAR confirmado para rideId=$rideDbId")
                    }
                } else {
                    Log.w(TAG, "Fallback ACEITAR falhou (excecao ao montar gesture)")
                    telemetry.log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.WARN,
                        "AutoPilot: Fallback ACEITAR falhou para rideId=$rideDbId")
                    synchronized(processingLock) { isProcessing = false }
                }
            } else {
                Log.w(TAG, "Botao ACEITAR nao encontrado -- API < 24, sem fallback")
                telemetry.log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.WARN,
                    "AutoPilot: Botao ACEITAR nao encontrado para rideId=$rideDbId")
                synchronized(processingLock) { isProcessing = false }
            }
        }
    }

    /**
     * Executa RECUSAR com estrategia especifica por plataforma:
     *
     * UBER:  Nao tem botao "Recusar". Duas opcoes:
     *        1) Tap no X no canto superior ESQUERDO do card de corrida
     *        2) Deixar o timer de 15s expirar (fallback passivo)
     *        O X e um icone sem texto acessivel — busca por accessibility primeiro,
     *        depois fallback por gesture no canto sup. esquerdo.
     *
     * 99:    Tap no X no canto superior DIREITO da tela.
     *        Nao tem botao "Recusar" explicito.
     *
     * inDrive: Tem botao "Recusar" explicito ou pode ignorar a oferta.
     */
    fun performRefuse(platform: Platform, rideDbId: Long) {
        Log.i(TAG, "Executando RECUSAR para ${platform.displayName}")
        val telemetry = TelemetryLogger.getInstance(context)

        val acceptTexts = when (platform) {
            Platform.UBER -> UBER_ACCEPT_TEXTS
            Platform.NINETY_NINE -> NINETY_NINE_ACCEPT_TEXTS
            Platform.INDRIVE -> INDRIVE_ACCEPT_TEXTS
            else -> UBER_ACCEPT_TEXTS + NINETY_NINE_ACCEPT_TEXTS + INDRIVE_ACCEPT_TEXTS
        }

        val refused = when (platform) {
            Platform.UBER -> performRefuseUber(rideDbId)
            Platform.NINETY_NINE -> performRefuse99(rideDbId)
            else -> performRefuseGeneric(platform, rideDbId)
        }

        if (refused) {
            telemetry.log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.INFO,
                "AutoPilot: Tap RECUSAR (fallback) para rideId=$rideDbId")
            scheduleGestureConfirmation(800L, acceptTexts, platform.packageName) {
                notifyRefuseSuccess(rideDbId)
                telemetry.log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.INFO,
                    "AutoPilot: Fallback RECUSAR confirmado para rideId=$rideDbId")
            }
        } else {
            Log.w(TAG, "RECUSAR: nenhuma estrategia funcionou -- motorista deve agir")
            TelemetryLogger.getInstance(context).log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.WARN,
                "AutoPilot: Botao RECUSAR nao encontrado para rideId=$rideDbId")
            synchronized(processingLock) { isProcessing = false }
        }
    }

    /**
     * Uber: X no canto superior ESQUERDO do card.
     * Estrategia: 1) Buscar node dismiss por contentDescription (close/fechar/dismiss)
     *             2) Buscar node pequeno clicavel no canto sup. esquerdo
     *             3) Fallback: tap geometrico no canto sup. esquerdo
     */
    private fun performRefuseUber(rideDbId: Long): Boolean {
        val dismissTexts = listOf("✕", "×", "x", "close", "fechar", "dismiss", "cancelar")
        val clicked = findDismissNode(dismissTexts, Platform.UBER.packageName)
        if (clicked) {
            Log.i(TAG, "Uber: X dismiss encontrado via accessibility")
            return true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val dm = context.resources.displayMetrics
            // Uber X: canto superior esquerdo (~10% da largura, ~8% da altura)
            val candidates = listOf(
                Pair(0.08f, 0.07f), Pair(0.12f, 0.07f),
                Pair(0.08f, 0.10f), Pair(0.05f, 0.07f)
            )
            try {
                candidates.forEachIndexed { index, (xr, yr) ->
                    handler.postDelayed({
                        try {
                            val path = android.graphics.Path().apply {
                                moveTo(dm.widthPixels * xr, dm.heightPixels * yr)
                            }
                            val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0L, 50L)
                            val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()
                            accessibilityService.dispatchGesture(gesture, null, null)
                            Log.d(TAG, "Uber X tap candidato $index: (${(xr*100).toInt()}%,${(yr*100).toInt()}%)")
                        } catch (e: Exception) {
                            Log.w(TAG, "Uber X tap candidato $index falhou: ${e.message}")
                        }
                    }, index * 250L)
                }
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao agendar Uber X taps: ${e.message}")
            }
        }
        return false
    }

    /**
     * 99: X no canto superior DIREITO da tela.
     * Estrategia: 1) Buscar node dismiss por texto/contentDescription
     *             2) Fallback: tap geometrico no canto sup. direito
     */
    private fun performRefuse99(rideDbId: Long): Boolean {
        val dismissTexts = listOf("✕", "×", "x", "close", "fechar", "cancelar")
        val clicked = findDismissNode(dismissTexts, Platform.NINETY_NINE.packageName)
        if (clicked) {
            Log.i(TAG, "99: X dismiss encontrado via accessibility")
            return true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val dm = context.resources.displayMetrics
            // 99 X: canto superior direito (~90% da largura, ~8% da altura)
            val candidates = listOf(
                Pair(0.92f, 0.07f), Pair(0.88f, 0.07f),
                Pair(0.92f, 0.10f), Pair(0.95f, 0.07f)
            )
            try {
                candidates.forEachIndexed { index, (xr, yr) ->
                    handler.postDelayed({
                        try {
                            val path = android.graphics.Path().apply {
                                moveTo(dm.widthPixels * xr, dm.heightPixels * yr)
                            }
                            val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0L, 50L)
                            val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()
                            accessibilityService.dispatchGesture(gesture, null, null)
                            Log.d(TAG, "99 X tap candidato $index: (${(xr*100).toInt()}%,${(yr*100).toInt()}%)")
                        } catch (e: Exception) {
                            Log.w(TAG, "99 X tap candidato $index falhou: ${e.message}")
                        }
                    }, index * 250L)
                }
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao agendar 99 X taps: ${e.message}")
            }
        }
        return false
    }

    /**
     * inDrive e outros: tem botao "Recusar" explicito.
     * Fallback para busca generica + tap geometrico.
     */
    private fun performRefuseGeneric(platform: Platform, rideDbId: Long): Boolean {
        val refuseTexts = when (platform) {
            Platform.INDRIVE -> INDRIVE_REFUSE_TEXTS
            else -> UBER_REFUSE_TEXTS + NINETY_NINE_REFUSE_TEXTS + INDRIVE_REFUSE_TEXTS
        }
        val clicked = findAndClickButton(refuseTexts, platform.packageName)
        if (clicked) {
            Log.i(TAG, "${platform.displayName}: Botao RECUSAR clicado via accessibility")
            return true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return performTapRefuse()
        }
        return false
    }

    /**
     * Busca node dismiss (X/close) por texto ou contentDescription.
     * Diferente de findAndClickButton: tambem busca nodes pequenos (icones)
     * que nao tem texto mas tem contentDescription com dismiss/close/fechar.
     */
    private fun findDismissNode(dismissTexts: List<String>, targetPackage: String): Boolean {
        try {
            val windows = accessibilityService.windows ?: return false
            for (window in windows) {
                val root = window.root ?: continue
                val windowPackage = root.packageName?.toString() ?: ""
                if (windowPackage != targetPackage && windowPackage.isNotEmpty()) {
                    try { root.recycle() } catch (_: Exception) {}
                    continue
                }
                val node = findDismissNodeRecursive(root, dismissTexts, 0)
                if (node != null) {
                    val desc = node.contentDescription ?: node.text ?: "?"
                    Log.d(TAG, "Dismiss node encontrado: \"$desc\"")
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        try {
                            val bounds = android.graphics.Rect()
                            node.getBoundsInScreen(bounds)
                            if (!bounds.isEmpty) {
                                val path = android.graphics.Path().apply {
                                    moveTo(bounds.centerX().toFloat(), bounds.centerY().toFloat())
                                }
                                val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0L, 50L)
                                val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()
                                accessibilityService.dispatchGesture(gesture, null, null)
                            }
                        } catch (_: Exception) {}
                    }
                    try { node.recycle() } catch (_: Exception) {}
                    try { root.recycle() } catch (_: Exception) {}
                    return true
                }
                try { root.recycle() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao buscar dismiss node: ${e.message}")
        }
        return false
    }

    private fun findDismissNodeRecursive(
        node: AccessibilityNodeInfo, targets: List<String>, depth: Int
    ): AccessibilityNodeInfo? {
        if (depth > 25) return null
        val text = (node.text?.toString() ?: "").lowercase().trim()
        val desc = (node.contentDescription?.toString() ?: "").lowercase().trim()
        for (target in targets) {
            if (text == target || desc.contains(target, ignoreCase = true)) {
                if (node.isClickable) return AccessibilityNodeInfo.obtain(node)
                val parent = node.parent
                if (parent != null && parent.isClickable) return parent
                parent?.recycle()
                return AccessibilityNodeInfo.obtain(node)
            }
        }
        for (i in 0 until node.childCount) {
            try {
                val child = node.getChild(i) ?: continue
                val result = findDismissNodeRecursive(child, targets, depth + 1)
                if (result != null) {
                    try { child.recycle() } catch (_: Exception) {}
                    return result
                }
                try { child.recycle() } catch (_: Exception) {}
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * v7.1.0: Dual-Click (ACTION_CLICK + dispatchGesture).
     * v7.2.0: useBoundsDetection -- fallback geometrico para botao ACEITAR.
     * Uber usa Compose: ACTION_CLICK pode silenciosamente falhar.
     * dispatchGesture funciona em Compose. Ambos disparados sempre.
     */
    private fun findAndClickButton(
        buttonTexts: List<String>,
        targetPackage: String,
        useBoundsDetection: Boolean = false
    ): Boolean {
        try {
            val windows = accessibilityService.windows ?: return false
            for (window in windows) {
                val root = window.root ?: continue
                val windowPackage = root.packageName?.toString() ?: ""
                if (windowPackage != targetPackage && windowPackage.isNotEmpty()) {
                    try { root.recycle() } catch (_: Exception) {}
                    continue
                }

                val buttonNode = findClickableNode(root, buttonTexts)
                if (buttonNode != null) {
                    val nodeText = buttonNode.text ?: buttonNode.contentDescription ?: "?"
                    Log.d(TAG, "Botao encontrado por texto: \"$nodeText\"")
                    val clickResult = buttonNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.d(TAG, "ACTION_CLICK result: $clickResult")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        try {
                            val bounds = android.graphics.Rect()
                            buttonNode.getBoundsInScreen(bounds)
                            if (!bounds.isEmpty) {
                                val path = android.graphics.Path().apply {
                                    moveTo(bounds.centerX().toFloat(), bounds.centerY().toFloat())
                                }
                                val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0L, 50L)
                                val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()
                                accessibilityService.dispatchGesture(gesture, null, null)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "dispatchGesture falhou: ${e.message}")
                        }
                    }
                    try { buttonNode.recycle() } catch (_: Exception) {}
                    try { root.recycle() } catch (_: Exception) {}
                    return true
                }

                // v7.2.0: Bounds-based detection -- fallback quando texto nao encontrou nada
                // Detecta CTA primario por posicao: terco inferior + largura >= 50% da tela
                // Usar apenas para ACEITAR -- botao X do recusar e pequeno e ambiguo
                if (useBoundsDetection) {
                    val boundsNode = findClickableNodeByBounds(root)
                    if (boundsNode != null) {
                        val bounds = android.graphics.Rect()
                        boundsNode.getBoundsInScreen(bounds)
                        Log.d(TAG, "Botao por bounds: centro=(${bounds.centerX()},${bounds.centerY()}) w=${bounds.width()}")
                        boundsNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !bounds.isEmpty) {
                            try {
                                val path = android.graphics.Path().apply {
                                    moveTo(bounds.centerX().toFloat(), bounds.centerY().toFloat())
                                }
                                val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0L, 50L)
                                val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()
                                accessibilityService.dispatchGesture(gesture, null, null)
                            } catch (_: Exception) {}
                        }
                        try { boundsNode.recycle() } catch (_: Exception) {}
                        try { root.recycle() } catch (_: Exception) {}
                        return true
                    }
                }
                try { root.recycle() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao buscar botao: ${e.message}")
        }
        return false
    }

    /**
     * v7.2.0: Detecta CTA primario (botao ACEITAR) por geometria.
     * Criterios: isClickable + largura >= 50% da tela + centerY entre 60%-95%.
     * Nao usar para RECUSAR -- X e pequeno e os criterios ficam ambiguos.
     */
    private fun findClickableNodeByBounds(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val metrics = context.resources.displayMetrics
        val minWidth = metrics.widthPixels * 0.5f
        val minY     = metrics.heightPixels * 0.60f
        val maxY     = metrics.heightPixels * 0.95f
        return findNodeByBoundsRecursive(root, minWidth, minY, maxY, 0)
    }

    private fun findNodeByBoundsRecursive(
        node: AccessibilityNodeInfo,
        minWidth: Float, minY: Float, maxY: Float, depth: Int
    ): AccessibilityNodeInfo? {
        if (depth > 30) return null
        if (node.isClickable) {
            val b = android.graphics.Rect()
            node.getBoundsInScreen(b)
            if (!b.isEmpty && b.width() >= minWidth && b.centerY() >= minY && b.centerY() <= maxY)
                return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            try {
                val child = node.getChild(i) ?: continue
                val result = findNodeByBoundsRecursive(child, minWidth, minY, maxY, depth + 1)
                if (result != null) {
                    try { child.recycle() } catch (_: Exception) {}
                    return result
                }
                try { child.recycle() } catch (_: Exception) {}
            } catch (_: Exception) {}
        }
        return null
    }

    private fun findClickableNode(node: AccessibilityNodeInfo, targetTexts: List<String>, depth: Int = 0): AccessibilityNodeInfo? {
        if (depth > 30) return null
        val nodeText = (node.text?.toString() ?: "").lowercase().trim()
        val nodeDesc = (node.contentDescription?.toString() ?: "").lowercase().trim()
        for (target in targetTexts) {
            if (nodeText.contains(target, ignoreCase = true) || nodeDesc.contains(target, ignoreCase = true)) {
                if (node.isClickable) return AccessibilityNodeInfo.obtain(node)
                val parent = node.parent
                if (parent != null && parent.isClickable) return parent
                parent?.recycle()
                return AccessibilityNodeInfo.obtain(node)
            }
        }
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
    // v7.1.1: Gestures de fallback -- Fire-and-forget
    // =========================================================================

    @android.annotation.TargetApi(Build.VERSION_CODES.N)
    private fun performSwipeAccept(): Boolean {
        // CountDownLatch REMOVIDO v7.1.1 -- causava deadlock no main thread
        try {
            val dm = context.resources.displayMetrics
            val startX = dm.widthPixels * 0.15f
            val startY = dm.heightPixels * 0.85f
            val endX   = dm.widthPixels * 0.85f
            val path = android.graphics.Path().apply { moveTo(startX, startY); lineTo(endX, startY) }
            val stroke  = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0L, 300L)
            val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()
            accessibilityService.dispatchGesture(gesture, null, null)
            Log.d(TAG, "Swipe accept: (${ (startX).toInt()},${startY.toInt()}) -> (${endX.toInt()},${startY.toInt()})")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao executar swipe: ${e.message}")
            return false
        }
    }

    // 2 candidatos: canto sup. direito (Uber atual) + canto sup. esquerdo (Uber antigo / 99 / inDrive)
    @android.annotation.TargetApi(Build.VERSION_CODES.N)
    private fun performTapRefuse(): Boolean {
        val candidates = listOf(Pair(0.90f, 0.08f), Pair(0.10f, 0.08f))
        try {
            val dm = context.resources.displayMetrics
            candidates.forEachIndexed { index, (xr, yr) ->
                handler.postDelayed({
                    try {
                        val path = android.graphics.Path().apply {
                            moveTo(dm.widthPixels * xr, dm.heightPixels * yr)
                        }
                        val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0L, 50L)
                        val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()
                        accessibilityService.dispatchGesture(gesture, null, null)
                        Log.d(TAG, "Tap refuse candidato $index")
                    } catch (e: Exception) {
                        Log.w(TAG, "Tap refuse candidato $index falhou: ${e.message}")
                    }
                }, index * 300L)
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao agendar tap refuse: ${e.message}")
            return false
        }
    }

    // 5 candidatos (0.87, 0.82, 0.75, 0.90, 0.70) disparados a cada 250ms
    @android.annotation.TargetApi(Build.VERSION_CODES.N)
    private fun performTapAccept(): Boolean {
        val candidates = listOf(
            Pair(0.50f, 0.87f), Pair(0.50f, 0.82f), Pair(0.50f, 0.75f),
            Pair(0.50f, 0.90f), Pair(0.50f, 0.70f)
        )
        try {
            val dm = context.resources.displayMetrics
            candidates.forEachIndexed { index, (xr, yr) ->
                handler.postDelayed({
                    try {
                        val path = android.graphics.Path().apply {
                            moveTo(dm.widthPixels * xr, dm.heightPixels * yr)
                        }
                        val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0L, 50L)
                        val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()
                        accessibilityService.dispatchGesture(gesture, null, null)
                        Log.d(TAG, "Tap accept candidato $index")
                    } catch (e: Exception) {
                        Log.w(TAG, "Tap accept candidato $index falhou: ${e.message}")
                    }
                }, index * 250L)
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao agendar tap accept: ${e.message}")
            return false
        }
    }

    enum class AutoPilotDecision { ACCEPT, REFUSE, NEUTRAL }

    fun destroy() {
        cancelPendingAction()
        scope.cancel()
        Log.d(TAG, "AutoPilotEngine destruido")
    }
}
