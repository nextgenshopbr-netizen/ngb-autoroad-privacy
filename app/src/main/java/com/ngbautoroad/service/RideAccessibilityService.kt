package com.ngbautoroad.service

// ============================================================================
// ARQUIVO: RideAccessibilityService.kt
// VERSÃO: v6.0.0 — REESCRITA TOTAL DO ENGINE
// LOCALIZAÇÃO: service/RideAccessibilityService.kt
// DATA: 19/06/2026
// ============================================================================
// RESPONSABILIDADE:
//   Detectar corridas de apps (Uber, 99, inDrive, Cabify) via AccessibilityService
//   com sistema Triple Engine (Árvore + Screenshot OCR + Notificação) e
//   Ghost Mode bancário automático.
// ============================================================================
// ARQUITETURA TRIPLE ENGINE:
//   CAMADA 1 (Primária): Árvore de Acessibilidade — leitura direta dos nós
//     - Zero throttle para TYPE_WINDOW_STATE_CHANGED
//     - getWindows() como fonte PRIMÁRIA (não fallback)
//     - Profundidade ilimitada (maxDepth=50 para Compose)
//     - Coleta text + contentDescription + hintText
//   CAMADA 2 (Fallback): takeScreenshot() + ML Kit OCR (Android 11+)
//     - Ativado automaticamente se Camada 1 não encontra dados em 1.5s
//     - Screenshot SILENCIOSO (sem popup, sem ícone, sem notificação)
//     - Processamento via ML Kit Text Recognition (on-device)
//   CAMADA 3 (Backup): RideNotificationListener (NotificationListenerService)
//     - Funciona DURANTE Ghost Mode (quando banco está aberto)
//     - Captura notificações da Uber/99 com dados da corrida
//     - NÃO é detectado por bancos (não é accessibility service)
// ============================================================================
// GHOST MODE (Stealth Bancário Automático):
//   NÍVEL 1: Invisibilidade Visual
//     - Remove overlay, bubble, notificação em <100ms
//   NÍVEL 2: Hibernação do Serviço
//     - packageNames = ["com.fantasma.inexistente"]
//     - eventTypes = 0 (não recebe mais eventos)
//     - Serviço "existe" mas está completamente inerte
//   NÍVEL 3: Restauração Automática
//     - UsageStatsManager polling (2s) detecta saída do banco
//     - Restaura configuração original instantaneamente
// ============================================================================
// BLOCOS:
//   - onServiceConnected (L~90): Configuração inicial do serviço
//   - onAccessibilityEvent (L~130): Dispatcher principal de eventos
//   - handleRideAppEvent (L~200): Processa eventos de apps de corrida
//   - collectTextsMultiWindow (L~250): Coleta texto de TODAS as janelas
//   - triggerScreenshotFallback (L~300): Fallback OCR via takeScreenshot
//   - activateGhostMode (L~350): Ativa stealth bancário
//   - deactivateGhostMode (L~400): Restaura serviço normal
//   - parseUberRide (L~450): Parser Uber Driver
//   - parse99Ride (L~550): Parser 99 Driver
//   - parseInDriveRide (L~620): Parser inDrive
//   - parseCabifyRide (L~670): Parser Cabify
//   - traverseNode (L~720): Travessia recursiva da árvore
//   - Utilidades (L~760): Helpers e constantes
// ============================================================================
// DEPENDÊNCIAS:
//   - data/model/RideData.kt → RideData, RideType, Platform
//   - service/OverlayService.kt → onRideDetected, onStealthModeChanged
//   - service/RideNotificationListener.kt → backup durante Ghost Mode
//   - service/BubbleService.kt → esconder bubble no Ghost Mode
//   - NGBAutoRoadApp.kt → canais de notificação
// ============================================================================
// PROTEÇÕES:
//   - Deduplicação por hash (janela de 10s)
//   - Guard contra árvore nula/vazia
//   - try-catch em toda travessia (nós podem ser reciclados)
//   - Timeout de screenshot (3s max)
//   - Ghost Mode não pode ficar preso (timeout 5min)
// ============================================================================
// DEBUG TAGS:
//   - NGB_ENGINE: Eventos do engine principal
//   - NGB_TREE: Travessia da árvore de acessibilidade
//   - NGB_OCR: Screenshot + OCR fallback
//   - NGB_GHOST: Ghost Mode bancário
//   - NGB_PARSE: Parsing de dados da corrida
//   - NGB_DEDUP: Deduplicação de corridas
// ============================================================================

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.usage.UsageStatsManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.ngbautoroad.data.model.Platform
import com.ngbautoroad.data.model.RideData
import com.ngbautoroad.data.model.RideType
import com.ngbautoroad.data.prefs.PrefsManager
import com.ngbautoroad.domain.TelemetryLogger

class RideAccessibilityService : AccessibilityService() {

    // =========================================================================
    // BLOCO: Companion Object — Constantes, estado compartilhado, packages
    // =========================================================================
    companion object {
        // --- DEBUG TAGS ---
        private const val TAG_ENGINE = "NGB_ENGINE"
        private const val TAG_TREE = "NGB_TREE"
        private const val TAG_OCR = "NGB_OCR"
        private const val TAG_GHOST = "NGB_GHOST"
        private const val TAG_PARSE = "NGB_PARSE"
        private const val TAG_DEDUP = "NGB_DEDUP"

        // --- PACKAGES DE APPS BANCÁRIOS (Ghost Mode) ---
        // Lista abrangente de bancos brasileiros que detectam AccessibilityService
        val BANK_PACKAGES = setOf(
            // Grandes bancos
            "com.itau", "com.itau.empresas",
            "br.com.bradesco", "com.bradesco.next",
            "br.com.bb.android", "br.com.bb.android.empresas",
            "br.com.santander.way", "br.com.santander.app",
            // Digitais
            "com.nu.production",
            "br.com.original.bank",
            "br.com.intermedium", "br.com.bancointer",
            "com.picpay",
            "br.com.c6bank.app",
            "com.neon",
            "com.mercadopago.wallet",
            // Investimentos
            "com.btgpactual.pangea",
            "br.com.xp.carteira",
            // Caixa
            "br.com.caixa.tem", "br.gov.caixa.tem",
            "br.com.gabba.Caixa",
            // Pagamentos
            "com.pagseguro.seller",
            "com.stone.conta",
            // Cooperativas
            "br.com.sicoob.app",
            "br.com.sicredi.app",
            // Outros
            "br.com.daycoval.app",
            "com.safra.pocket",
            "br.com.agibank",
            "br.com.bmg",
            "com.modalmais",
            "br.com.rico",
            "com.iti.itau"
        )

        // --- PACKAGES DE APPS DE CORRIDA ---
        val RIDE_PACKAGES = setOf(
            "com.ubercab.driver",       // Uber Driver
            "com.ubercab",              // Uber (rider, caso driver use)
            "com.app99.driver",          // 99 Motorista (package real Play Store)
            "com.machfrankfurt.android", // inDrive
            "com.cabify.driver",         // Cabify Driver
            "br.com.orb.taxi.drivermachine" // v7.6.0: Orb (táxi)
        )

        // --- ESTADO COMPARTILHADO (acessível por outros serviços) ---
        @Volatile
        var stealthModeActive = false
            private set

        @Volatile
        var isServiceAlive = false
            private set

        // Instância para acesso externo (screenshot trigger)
        @Volatile
        var instance: RideAccessibilityService? = null
            private set
    }

    // =========================================================================
    // BLOCO: Estado interno do serviço
    // =========================================================================

    // --- v6.1.0: Lifecycle Manager (ciclo de vida completo de corridas) ---
    var lifecycleManager: RideLifecycleManager? = null
        private set

    // --- v6.1.0: AutoPilot Engine ---
    var autoPilotEngine: AutoPilotEngine? = null
        private set

    // --- v6.9.9: UserActionDetector (detecta cliques do motorista) ---
    var userActionDetector: UserActionDetector? = null
        private set

    // --- Controle de deduplicação ---
    private var lastRideHash: Int = 0
    private var lastRideHashTime = 0L
    private val DUPLICATE_WINDOW_MS = 30_000L // v6.9.14: 30s janela anti-duplicata (era 60s)

    // v6.9.8: Debounce por valor — mesmo R$ dentro de 60s = duplicata
    private var lastRideValue: Double = 0.0
    private var lastRideValueTime = 0L
    private val VALUE_DEBOUNCE_MS = 120_000L // v6.9.14: 120s para mesmo valor (era 60s)

    // --- Controle de throttle ---
    private var lastProcessedTime = 0L
    private var lastForegroundPackage = ""

    // --- Ghost Mode ---
    private var ghostModeStartTime = 0L
    private val GHOST_MODE_TIMEOUT_MS = 5 * 60 * 1000L // 5 min timeout segurança
    private val ghostHandler = Handler(Looper.getMainLooper())
    private var ghostPollingRunnable: Runnable? = null

    // --- Screenshot OCR Fallback ---
    private var lastScreenshotTime = 0L
    private val SCREENSHOT_COOLDOWN_MS = 2000L // Mínimo 2s entre screenshots
    private var pendingScreenshotForPackage: String? = null
    private val screenshotHandler = Handler(Looper.getMainLooper())

    // --- Configuração original do serviço (para restaurar após Ghost Mode) ---
    private var originalEventTypes = 0
    private var originalFlags = 0

    // =========================================================================
    // BLOCO: onServiceConnected — Configuração inicial
    // =========================================================================
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isServiceAlive = true

        // v6.1.0: Inicializar Lifecycle Manager e AutoPilot Engine
        lifecycleManager = RideLifecycleManager(applicationContext)
        autoPilotEngine = AutoPilotEngine(applicationContext, this)

        // Configurar serviço com máxima capacidade de detecção
        serviceInfo = serviceInfo.apply {
            // Escutar TODOS os tipos de evento relevantes
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_SCROLLED or
                    AccessibilityEvent.TYPE_VIEW_CLICKED // v6.9.9: detectar cliques do motorista

            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC

            // Flags para máxima visibilidade da árvore
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS

            // ZERO timeout para não perder eventos rápidos
            notificationTimeout = 0

            // NÃO filtrar por package — precisamos detectar bancos para Ghost Mode
            // Filtragem é feita no código para máxima flexibilidade
            packageNames = null
        }

        // Salvar configuração original para restaurar após Ghost Mode
        originalEventTypes = serviceInfo.eventTypes
        originalFlags = serviceInfo.flags

        Log.i(TAG_ENGINE, "═══════════════════════════════════════════════════")
        Log.i(TAG_ENGINE, "║ RideAccessibilityService v6.2.0 CONECTADO      ║")
        Log.i(TAG_ENGINE, "║ Triple Engine: Árvore + OCR + Notificação      ║")
        Log.i(TAG_ENGINE, "║ Ghost Mode: Automático (${BANK_PACKAGES.size} bancos)    ║")
        Log.i(TAG_ENGINE, "║ Screenshot OCR: ${if (Build.VERSION.SDK_INT >= 30) "DISPONÍVEL" else "INDISPONÍVEL (API < 30)"}  ║")
        Log.i(TAG_ENGINE, "═════════════════════════════════════════════════")

        // v6.2.0: AS conectou com sucesso — NotificationListener volta ao papel secundário
        RideNotificationListener.isPrimaryChannel = false
    }

    // =========================================================================
    // v6.9.9: Inicializa UserActionDetector com callbacks para atualizar status
    // =========================================================================
    fun initUserActionDetector() {
        val context = applicationContext
        userActionDetector = UserActionDetector(
            context = context,
            onAccepted = {
                val currentId = lifecycleManager?.getCurrentRideDbId() ?: 0L
                Log.i("NGB_ACTION", "✅ CORRIDA ACEITA (UserActionDetector)")
                TelemetryLogger.getInstance(context).log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.INFO,
                    "UserActionDetector: ACEITA rideId=$currentId")
                lifecycleManager?.onRideAccepted()
                // Fechar overlay atual (mas serviço continua ativo para novas ofertas)
                OverlayService.onRideAccepted?.invoke()
            },
            onRefused = {
                val currentId = lifecycleManager?.getCurrentRideDbId() ?: 0L
                Log.i("NGB_ACTION", "❌ CORRIDA RECUSADA/EXPIRADA (UserActionDetector)")
                TelemetryLogger.getInstance(context).log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.INFO,
                    "UserActionDetector: RECUSADA/EXPIRADA rideId=$currentId")
                lifecycleManager?.onRideRefused()
                OverlayService.onRideAccepted?.invoke() // Fechar overlay também
                userActionDetector?.stopMonitoring()
            },
            onCompleted = {
                val currentId = lifecycleManager?.getCurrentRideDbId() ?: 0L
                Log.i("NGB_ACTION", "🏁 CORRIDA FINALIZADA (UserActionDetector)")
                TelemetryLogger.getInstance(context).log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.INFO,
                    "UserActionDetector: FINALIZADA rideId=$currentId")
                lifecycleManager?.onRideCompleted()
                userActionDetector?.stopMonitoring()
            },
            onCancelled = {
                val currentId = lifecycleManager?.getCurrentRideDbId() ?: 0L
                Log.i("NGB_ACTION", "🚫 CORRIDA CANCELADA (UserActionDetector)")
                TelemetryLogger.getInstance(context).log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.INFO,
                    "UserActionDetector: CANCELADA rideId=$currentId")
                lifecycleManager?.onRideCancelled()
                userActionDetector?.stopMonitoring()
            },
            onTripStarted = {
                val currentId = lifecycleManager?.getCurrentRideDbId() ?: 0L
                Log.i("NGB_ACTION", "🚗 VIAGEM INICIADA (UserActionDetector)")
                TelemetryLogger.getInstance(context).log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.INFO,
                    "UserActionDetector: VIAGEM INICIADA rideId=$currentId")
            },
            onArrived = {
                val currentId = lifecycleManager?.getCurrentRideDbId() ?: 0L
                Log.i("NGB_ACTION", "📍 CHEGOU NO LOCAL (UserActionDetector)")
                TelemetryLogger.getInstance(context).log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.INFO,
                    "UserActionDetector: CHEGOU NO LOCAL rideId=$currentId")
            }
        )
    }

    // =========================================================================
    // BLOCO: onAccessibilityEvent — Dispatcher principal
    // =========================================================================
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString() ?: return
        val eventType = event.eventType

        // ─────────────────────────────────────────────────────────────────────
        // GHOST MODE: Detectar entrada/saída de apps bancários
        // ─────────────────────────────────────────────────────────────────────
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (isBankApp(packageName)) {
                if (!stealthModeActive) {
                    activateGhostMode(packageName)
                }
                lastForegroundPackage = packageName
                return // NÃO processar NADA do banco
            } else if (stealthModeActive) {
                // v7.5.0: Saiu do banco — desativar Ghost Mode, MAS com proteção contra
                // janelas transitórias. Como o Ghost Mode agora escuta WINDOW_STATE de todos
                // os apps (packageNames=null), uma barra de notificações, teclado (IME) ou
                // diálogo do sistema aparecendo SOBRE o banco geraria um WINDOW_STATE não-banco
                // e desativaria o Ghost Mode prematuramente (expondo o banco). Só desativa se:
                //   (a) UsageStats confirma que o banco NÃO está mais em foreground, OU
                //   (b) sem UsageStats, a janela não é transitória do sistema (teclado/systemui).
                val fg = getForegroundPackage()
                val bankStillForeground = fg != null && isBankApp(fg)
                if (!bankStillForeground && !isTransientSystemWindow(packageName)) {
                    Log.d(TAG_GHOST, "│  Saída do banco confirmada (janela=$packageName, fg=${fg ?: "?"}) — desativando")
                    deactivateGhostMode(packageName)
                } else {
                    Log.d(TAG_GHOST, "│  WINDOW_STATE de '$packageName' sobre o banco — mantendo Ghost Mode (transitória ou banco ainda em foreground)")
                }
            }
            lastForegroundPackage = packageName
        }

        // Se Ghost Mode ativo, ignorar TUDO (serviço está "morto")
        if (stealthModeActive) return

        // ─────────────────────────────────────────────────────────────────────
        // FILTRO: Só processar apps de corrida
        // ─────────────────────────────────────────────────────────────────────
        if (!isRideApp(packageName)) return

        // ─────────────────────────────────────────────────────────────────────
        // THROTTLE INTELIGENTE:
        //   - TYPE_WINDOW_STATE_CHANGED: ZERO throttle (evento raro e importante)
        //   - TYPE_WINDOW_CONTENT_CHANGED: 80ms (frequente, precisa ser rápido)
        //   - TYPE_VIEW_TEXT_CHANGED: 150ms (muito frequente)
        //   - TYPE_VIEW_SCROLLED: 300ms (scroll gera muitos eventos)
        // ─────────────────────────────────────────────────────────────────────
        val now = System.currentTimeMillis()
        val throttle = when (eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> 0L
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> 80L
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> 150L
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> 300L
            else -> 200L
        }

        if (throttle > 0 && (now - lastProcessedTime) < throttle) return
        lastProcessedTime = now

        // ─────────────────────────────────────────────────────────────────────
        // v6.9.9: DETECÇÃO DE CLIQUES DO MOTORISTA (UserActionDetector)
        // TYPE_VIEW_CLICKED é processado ANTES do parser para detectar aceite/recusa
        // ─────────────────────────────────────────────────────────────────────
        if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED && userActionDetector?.isActive() == true) {
            val actionDetected = userActionDetector?.onViewClicked(event, packageName) ?: false
            if (actionDetected) {
                Log.d(TAG_ENGINE, "│  UserActionDetector: ação detectada via clique")
                return // Ação já processada, não precisa continuar
            }
        }

        // ─────────────────────────────────────────────────────────────────────
        // PROCESSAR EVENTO DE APP DE CORRIDA
        // ─────────────────────────────────────────────────────────────────────
        handleRideAppEvent(packageName, eventType, event)

        // ─────────────────────────────────────────────────────────────────────
        // v6.9.9: MONITORAMENTO PÓS-DETEÇÃO (UserActionDetector + Lifecycle)
        // Detecta mudança de contexto (oferta sumiu → aceite/timeout)
        // ─────────────────────────────────────────────────────────────────────
        if (userActionDetector?.isActive() == true || lifecycleManager?.isActive() == true) {
            val textsForLifecycle = collectTextsMultiWindow(packageName, event)
            if (textsForLifecycle.isNotEmpty()) {
                // Camada 2: Detecção por mudança de contexto da tela
                val contextAction = userActionDetector?.onScreenContentChanged(textsForLifecycle) ?: false
                if (!contextAction) {
                    // Camada 2b: Detecção de conclusão/cancelamento após aceite
                    userActionDetector?.onTextsAfterAccepted(textsForLifecycle)
                }
                // Legacy lifecycle (mantido para compatibilidade)
                lifecycleManager?.onTextsDetected(textsForLifecycle, event.packageName?.toString() ?: "")
            }
        }
    }

    // =========================================================================
    // BLOCO: handleRideAppEvent — Processa eventos de apps de corrida
    // =========================================================================
    private fun handleRideAppEvent(packageName: String, eventType: Int, event: AccessibilityEvent) {
        val platform = detectPlatform(packageName) ?: return
        val eventName = eventTypeToString(eventType)

        Log.d(TAG_ENGINE, "┌─ Evento: $eventName | Package: $packageName | Platform: ${platform.displayName}")

        // ─────────────────────────────────────────────────────────────────────
        // CAMADA 1: Coleta via Árvore de Acessibilidade (Multi-Window)
        // ─────────────────────────────────────────────────────────────────────
        val allTexts = collectTextsMultiWindow(packageName, event)

        if (allTexts.isEmpty()) {
            Log.d(TAG_TREE, "│  ⚠ Árvore VAZIA para $packageName — tentando screenshot fallback")
            // Agendar screenshot fallback se disponível
            triggerScreenshotFallback(packageName, platform)
            return
        }

        Log.d(TAG_TREE, "│  ✓ Coletados ${allTexts.size} textos da árvore")
        if (Log.isLoggable(TAG_TREE, Log.VERBOSE)) {
            allTexts.take(15).forEachIndexed { i, t ->
                Log.v(TAG_TREE, "│    [$i] \"$t\"")
            }
        }

        // ─────────────────────────────────────────────────────────────────────
        // PARSING: Extrair dados da corrida
        // ─────────────────────────────────────────────────────────────────────
        try {
            val allNodes = mutableListOf<NodeData>()
            try {
                windows?.forEach { w -> 
                    w.root?.let { r -> 
                        allNodes.addAll(GeometryParser.extractNodes(r))
                        // recycle() was deprecated
                    } 
                }
            } catch (e: Exception) {
                Log.w(TAG_TREE, "│  ⚠ Erro ao extrair nós geométricos: ${e.message}")
            }

            // v7.6.1: copy(platform=platform) — a plataforma REAL (do app em foreground) sempre
            // vence o que o parser fixou. Corrige o Orb (roteado ao parseInDriveRide) aparecendo
            // como "inDrive", e qualquer outro mislabel.
            val rideData = (when (platform) {
                Platform.UBER -> parseUberRide(allTexts, allNodes)
                Platform.NINETY_NINE -> parse99Ride(allTexts)
                Platform.INDRIVE -> parseInDriveRide(allTexts)
                Platform.CABIFY -> parseCabifyRide(allTexts)
                Platform.ORB -> parseInDriveRide(allTexts) // v7.6.0: parser genérico (calibrar com oferta real do Orb)
                else -> null
            })?.copy(platform = platform)

            // v7.6.1: Anti-falso-positivo. Telas de GANHOS/REPASSES/RECENTES/ocioso geram "corridas"
            // fantasma (ex: R$95,90 = total do dia, 0km) ou com R$/km absurdo (ex: R$/km 114).
            // Uma oferta REAL sempre tem distância de viagem > 0 e R$/km plausível. Exigir isso
            // descarta os fantasmas sem perder ofertas legítimas (validado com telemetria 2026-06-30).
            if (rideData != null && rideData.rideValue > 0 &&
                rideData.dropoffDistance > 0 && rideData.valuePerKm <= 20.0) {
                // v7.4.0: Log detalhado da corrida detectada (bairro + R$/km)
                TelemetryLogger.getInstance(this).log(TelemetryLogger.Category.PARSER, TelemetryLogger.Level.INFO,
                    "Corrida detectada: R$${rideData.rideValue} | R$/km=${String.format("%.2f", rideData.valuePerKm)} | " +
                    "Pickup=${rideData.pickupNeighborhood.ifBlank { "?" }} → Destino=${rideData.dropoffNeighborhood.ifBlank { "?" }} | " +
                    "Dist=${rideData.dropoffDistance}km | Pickup=${rideData.pickupDistance}km | " +
                    "Dur=${rideData.rideDuration}min | Rating=${rideData.passengerRating} | " +
                    "Plataforma=${rideData.platform.displayName}")

                // v7.3.0: Dump da árvore de acessibilidade para diagnóstico do AutoPilot
                dumpAccessibilityTree(packageName)

                val hash = generateRideHash(rideData)
                val now = System.currentTimeMillis()

                if (hash == lastRideHash && (now - lastRideHashTime) < DUPLICATE_WINDOW_MS) {
                    Log.d(TAG_DEDUP, "│  ⊊ Duplicata ignorada (hash=$hash, Δt=${now - lastRideHashTime}ms)")
                    TelemetryLogger.getInstance(this).logDuplicate(rideData.platform.displayName, rideData.rideValue, "hash_duplicado_${DUPLICATE_WINDOW_MS/1000}s")
                    return
                }

                lastRideHash = hash
                lastRideHashTime = now
                lastRideValue = rideData.rideValue
                lastRideValueTime = now

                Log.i(TAG_ENGINE, "├─ ✅ CORRIDA DETECTADA!")
                Log.i(TAG_ENGINE, "│  Platform: ${rideData.platform.displayName}")
                Log.i(TAG_ENGINE, "│  Tipo: ${rideData.rideType.displayName}")
                Log.i(TAG_ENGINE, "│  Valor: R$ ${String.format("%.2f", rideData.rideValue)}")
                Log.i(TAG_ENGINE, "│  Pickup: ${String.format("%.1f", rideData.pickupDistance)} km")
                Log.i(TAG_ENGINE, "│  Trip: ${String.format("%.1f", rideData.dropoffDistance)} km / ${rideData.rideDuration.toInt()} min")
                Log.i(TAG_ENGINE, "│  Rating: ★ ${String.format("%.2f", rideData.passengerRating)}")
                Log.i(TAG_ENGINE, "│  Bairro: ${rideData.pickupNeighborhood} → ${rideData.dropoffNeighborhood}")
                Log.i(TAG_ENGINE, "└─ Enviando para OverlayService...")

                // v6.9.8: Telemetria
                TelemetryLogger.getInstance(this).logRideDetected(
                    platform = rideData.platform.displayName,
                    value = rideData.rideValue,
                    pickupKm = rideData.pickupDistance,
                    dropoffKm = rideData.dropoffDistance,
                    duration = rideData.rideDuration,
                    rating = rideData.passengerRating,
                    stops = rideData.intermediateStops,
                    pickupNeighborhood = rideData.pickupNeighborhood,
                    dropoffNeighborhood = rideData.dropoffNeighborhood,
                    valuePerKm = rideData.valuePerKm,
                    valuePerHour = rideData.valuePerHour,
                    hasAcceptButton = true, // só chega aqui se hasAcceptButton=true
                    accepted = false,
                    hash = hash
                )

                // v7.1.0: Fallback robusto — usa Intent quando callback é null (service reiniciando)
                if (!OverlayService.isRunning() || OverlayService.onRideDetected == null) {
                    Log.w(TAG_ENGINE, "│  ⚠️ OverlayService não disponível (running=${OverlayService.isRunning()}, callback=${OverlayService.onRideDetected != null}). Iniciando via Intent...")
                    OverlayService.start(this, rideData)
                } else {
                    OverlayService.onRideDetected?.invoke(rideData)
                }
            } else {
                Log.d(TAG_PARSE, "│  ○ Sem dados suficientes para corrida (valor=${rideData?.rideValue ?: 0.0})")
                // Se não encontrou dados mas é TYPE_WINDOW_STATE_CHANGED, tentar screenshot
                if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    triggerScreenshotFallback(packageName, platform)
                }
                Log.d(TAG_ENGINE, "└─ Fim processamento (sem corrida)")
            }
        } catch (e: Exception) {
            Log.e(TAG_ENGINE, "└─ ✖ ERRO no parsing: ${e.message}", e)
            TelemetryLogger.getInstance(this).error("Erro no parsing", e, mapOf("platform" to platform.displayName))
        }
    }

    // =========================================================================
    // BLOCO: collectTextsMultiWindow — Coleta texto de TODAS as fontes
    // =========================================================================
    /**
     * Coleta textos de TODAS as fontes disponíveis, em ordem de prioridade:
     * 1. getWindows() — TODAS as janelas ativas (inclui popups/dialogs Compose)
     * 2. rootInActiveWindow — janela principal (fallback se getWindows falha)
     * 3. event.source — nó que gerou o evento (último recurso)
     *
     * IMPORTANTE: getWindows() é a fonte PRIMÁRIA porque Compose usa janelas
     * separadas para bottom sheets, dialogs e popups de oferta de corrida.
     * rootInActiveWindow pode apontar para a janela errada.
     */
    private fun collectTextsMultiWindow(targetPackage: String, event: AccessibilityEvent): List<String> {
        val allTexts = mutableSetOf<String>() // Set para evitar duplicatas
        var windowsProcessed = 0
        var nodesTraversed = 0

        // ── FONTE 1: getWindows() — Todas as janelas ativas ──
        try {
            val windowList = windows
            Log.d(TAG_TREE, "│  getWindows(): ${windowList.size} janelas disponíveis")

            for (window in windowList) {
                val windowRoot = window.root ?: continue
                val windowPackage = windowRoot.packageName?.toString() ?: ""

                // Coletar de janelas do package alvo OU janelas do sistema (popups)
                if (windowPackage == targetPackage || windowPackage.isEmpty() ||
                    window.type == AccessibilityWindowInfo.TYPE_APPLICATION) {

                    val windowTexts = mutableListOf<String>()
                    val count = traverseNode(windowRoot, windowTexts, 0)
                    nodesTraversed += count

                    if (windowTexts.isNotEmpty()) {
                        windowsProcessed++
                        allTexts.addAll(windowTexts)
                        Log.d(TAG_TREE, "│    Window[$windowsProcessed] pkg=$windowPackage: ${windowTexts.size} textos, $count nós")
                    }
                }
                // recycle() was deprecated
            }
        } catch (e: Exception) {
            Log.w(TAG_TREE, "│  ⚠ getWindows() falhou: ${e.message}")
        }

        // ── FONTE 2: rootInActiveWindow (fallback) ──
        if (allTexts.isEmpty()) {
            try {
                val root = rootInActiveWindow
                if (root != null) {
                    val rootTexts = mutableListOf<String>()
                    val count = traverseNode(root, rootTexts, 0)
                    nodesTraversed += count
                    allTexts.addAll(rootTexts)
                    Log.d(TAG_TREE, "│  rootInActiveWindow: ${rootTexts.size} textos, $count nós")
                    // recycle() was deprecated
                }
            } catch (e: Exception) {
                Log.w(TAG_TREE, "│  ⚠ rootInActiveWindow falhou: ${e.message}")
            }
        }

        // ── FONTE 3: event.source (último recurso) ──
        if (allTexts.isEmpty()) {
            try {
                val source = event.source
                if (source != null) {
                    val sourceTexts = mutableListOf<String>()
                    val count = traverseNode(source, sourceTexts, 0)
                    nodesTraversed += count
                    allTexts.addAll(sourceTexts)
                    Log.d(TAG_TREE, "│  event.source: ${sourceTexts.size} textos, $count nós")
                    // recycle() was deprecated
                }
            } catch (e: Exception) {
                Log.w(TAG_TREE, "│  ⚠ event.source falhou: ${e.message}")
            }
        }

        Log.d(TAG_TREE, "│  TOTAL: ${allTexts.size} textos únicos | $windowsProcessed janelas | $nodesTraversed nós")
        return allTexts.toList()
    }

    // =========================================================================
    // BLOCO: triggerScreenshotFallback — Camada 2: Screenshot + OCR
    // =========================================================================
    /**
     * Tira screenshot SILENCIOSO via AccessibilityService.takeScreenshot() (API 30+)
     * e processa com ML Kit OCR.
     *
     * VANTAGENS sobre MediaProjection:
     * - Não precisa de permissão extra do usuário
     * - Não mostra ícone de gravação na status bar
     * - Não mostra popup de confirmação
     * - Completamente invisível para o motorista
     *
     * LIMITAÇÕES:
     * - Requer Android 11+ (API 30)
     * - Cooldown de 2s entre screenshots
     * - Processamento OCR leva ~500-800ms
     */
    private fun triggerScreenshotFallback(packageName: String, platform: Platform) {
        // Verificar se API suporta takeScreenshot
        if (Build.VERSION.SDK_INT < 30) {
            Log.d(TAG_OCR, "│  ⊘ Screenshot indisponível (API ${Build.VERSION.SDK_INT} < 30)")
            return
        }

        // Cooldown para não sobrecarregar
        val now = System.currentTimeMillis()
        if (now - lastScreenshotTime < SCREENSHOT_COOLDOWN_MS) {
            Log.d(TAG_OCR, "│  ⊘ Screenshot em cooldown (${now - lastScreenshotTime}ms < ${SCREENSHOT_COOLDOWN_MS}ms)")
            return
        }

        lastScreenshotTime = now
        pendingScreenshotForPackage = packageName

        Log.d(TAG_OCR, "│  📸 Iniciando screenshot silencioso para OCR...")

        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        Log.d(TAG_OCR, "│  ✓ Screenshot capturado! Processando OCR...")
                        processScreenshotWithOcr(screenshot, platform)
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.w(TAG_OCR, "│  ✖ Screenshot falhou (errorCode=$errorCode)")
                        pendingScreenshotForPackage = null
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG_OCR, "│  ✖ Exceção no takeScreenshot: ${e.message}")
            pendingScreenshotForPackage = null
        }
    }

    /**
     * Processa o screenshot capturado com ML Kit OCR.
     * Extrai texto e tenta parsear como corrida.
     */
    private fun processScreenshotWithOcr(screenshot: ScreenshotResult, platform: Platform) {
        try {
            val hardwareBuffer = screenshot.hardwareBuffer
            val colorSpace = screenshot.colorSpace

            // Converter HardwareBuffer para Bitmap
            val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
            hardwareBuffer.close()

            if (bitmap == null) {
                Log.w(TAG_OCR, "│  ✖ Bitmap nulo após conversão")
                return
            }

            // Processar com ML Kit
            val inputImage = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
            val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
                com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS
            )

            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    val fullText = visionText.text
                    if (fullText.isNotBlank()) {
                        Log.d(TAG_OCR, "│  ✓ OCR extraiu ${fullText.length} chars")

                        // Converter texto OCR em lista de linhas para o parser
                        val lines = fullText.lines()
                            .map { it.trim() }
                            .filter { it.isNotBlank() }

                        val rideData = (when (platform) {
                            Platform.UBER -> parseUberRide(lines)
                            Platform.NINETY_NINE -> parse99Ride(lines)
                            Platform.INDRIVE -> parseInDriveRide(lines)
                            Platform.CABIFY -> parseCabifyRide(lines)
                            Platform.ORB -> parseInDriveRide(lines) // v7.6.0: parser genérico (calibrar depois)
                            else -> null
                        })?.copy(platform = platform) // v7.6.1: plataforma real sempre vence

                        // v7.6.1: mesmo anti-falso-positivo da árvore (distância real + R$/km plausível)
                        if (rideData != null && rideData.rideValue > 0 &&
                            rideData.dropoffDistance > 0 && rideData.valuePerKm <= 20.0) {
                            val hash = generateRideHash(rideData)
                            val now = System.currentTimeMillis()

                            if (hash != lastRideHash || (now - lastRideHashTime) >= DUPLICATE_WINDOW_MS) {
                                lastRideHash = hash
                                lastRideHashTime = now
                                lastRideValue = rideData.rideValue
                                lastRideValueTime = now
                                Log.i(TAG_OCR, "│  ✅ CORRIDA VIA OCR! R$${String.format("%.2f", rideData.rideValue)}")
                                // v7.1.0: Fallback robusto — usa Intent quando callback null
                                if (!OverlayService.isRunning() || OverlayService.onRideDetected == null) {
                                    Log.w(TAG_OCR, "│  ⚠️ OverlayService não disponível. Iniciando via Intent...")
                                    OverlayService.start(this@RideAccessibilityService, rideData)
                                } else {
                                    OverlayService.onRideDetected?.invoke(rideData)
                                }
                            }
                        }
                    } else {
                        Log.d(TAG_OCR, "│  ○ OCR: texto vazio")
                    }
                    bitmap.recycle()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG_OCR, "│  ✖ ML Kit falhou: ${e.message}")
                    bitmap.recycle()
                }
        } catch (e: Exception) {
            Log.e(TAG_OCR, "│  ✖ Erro processando screenshot: ${e.message}", e)
        } finally {
            pendingScreenshotForPackage = null
        }
    }

    // =========================================================================
    // BLOCO: Ghost Mode — Stealth Bancário Automático
    // =========================================================================

    /**
     * ATIVA Ghost Mode quando app bancário é detectado em foreground.
     *
     * Ações executadas em <100ms:
     * 1. Remove overlay e bubble (invisibilidade visual)
     * 2. Altera serviceInfo para não processar eventos (hibernação)
     * 3. Inicia polling para detectar saída do banco (restauração)
     *
     * O serviço continua "ativo" na lista do sistema, MAS:
     * - Não recebe eventos de nenhum app
     * - Não acessa nenhuma janela
     * - Não tem overlay/bubble/notificação visível
     * - Para bancos que checam "atividade" do serviço: está inerte
     */
    private fun activateGhostMode(bankPackage: String) {
        Log.i(TAG_GHOST, "╔══════════════════════════════════════════════════╗")
        Log.i(TAG_GHOST, "║  👻 GHOST MODE ATIVADO                          ║")
        Log.i(TAG_GHOST, "║  Banco: $bankPackage")
        Log.i(TAG_GHOST, "╚══════════════════════════════════════════════════╝")

        stealthModeActive = true
        ghostModeStartTime = System.currentTimeMillis()

        // v6.9.9: Log telemetria
        TelemetryLogger.getInstance(applicationContext).log(TelemetryLogger.Category.SYSTEM, TelemetryLogger.Level.INFO,
            "Ghost Mode ATIVADO para: $bankPackage")

        // ── NÍVEL 1: Invisibilidade Visual ──
        // Notificar OverlayService para remover overlay + bubble imediatamente
        OverlayService.onStealthModeChanged?.invoke(true)

        // ── NÍVEL 2: Hibernação do Serviço ──
        // Alterar serviceInfo para não receber mais eventos
        // NOTA: O Nubank e outros bancos detectam serviços de acessibilidade REGISTRADOS
        // no sistema. Não podemos nos desregistrar sem perder funcionalidade.
        // O que fazemos: parar de processar eventos + esconder toda UI.
        try {
            serviceInfo = serviceInfo.apply {
                // v7.5.0 CORREÇÃO: NÃO filtrar por package durante o Ghost Mode.
                // O filtro antigo (packageNames=[self]) deixava o serviço surdo a todos os
                // outros apps — então o evento de SAÍDA do banco (WINDOW_STATE de outro app)
                // nunca chegava, e a única saída era o polling de UsageStats (frágil, depende
                // de permissão) ou o timeout de 5min. Resultado: overlay morto por até 5min
                // após o motorista fechar o banco.
                // Com packageNames=null + flags=0, recebemos APENAS a notificação de troca de
                // janela (sem ler conteúdo de NENHUM app — flags=0 não puxa a árvore de nós),
                // o que basta para sair do Ghost Mode INSTANTANEAMENTE ao fechar o banco.
                // Banco não é exposto: nenhum conteúdo é lido; e bancos detectam serviços de
                // acessibilidade REGISTRADOS, não o fluxo de eventos (ver nota acima).
                packageNames = null
                // eventTypes mínimo: só WINDOW_STATE (necessário para detectar entrada/saída de apps)
                eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                // flags=0 → nenhuma leitura de conteúdo (não puxa árvore de nós de nenhum app)
                flags = 0
                // Timeout alto para reduzir processamento
                notificationTimeout = 1000
            }
            Log.d(TAG_GHOST, "│  ServiceInfo hibernado (packageNames=null, eventTypes=WINDOW_STATE only, flags=0 — saída instantânea ao fechar banco)")
        } catch (e: Exception) {
            Log.e(TAG_GHOST, "│  ✖ Erro ao hibernar serviceInfo: ${e.message}")
        }

        // ── NÍVEL 3: Polling para detectar saída do banco ──
        startGhostModePolling()
    }

    /**
     * DESATIVA Ghost Mode quando motorista sai do app bancário.
     * Restaura todas as configurações originais instantaneamente.
     */
    private fun deactivateGhostMode(currentPackage: String) {
        val duration = System.currentTimeMillis() - ghostModeStartTime
        Log.i(TAG_GHOST, "╔══════════════════════════════════════════════════╗")
        Log.i(TAG_GHOST, "║  🔄 GHOST MODE DESATIVADO                       ║")
        Log.i(TAG_GHOST, "║  Duração: ${duration / 1000}s | Agora: $currentPackage")
        Log.i(TAG_GHOST, "╚══════════════════════════════════════════════════╝")

        stealthModeActive = false
        stopGhostModePolling()

        // ── Restaurar ServiceInfo original ──
        try {
            serviceInfo = serviceInfo.apply {
                packageNames = null // Escutar todos
                eventTypes = originalEventTypes
                flags = originalFlags
                notificationTimeout = 0
            }
            Log.d(TAG_GHOST, "│  ServiceInfo restaurado (packageNames=null, all events)")
        } catch (e: Exception) {
            Log.e(TAG_GHOST, "│  ✖ Erro ao restaurar serviceInfo: ${e.message}")
        }

        // ── Restaurar overlay/bubble ──
        OverlayService.onStealthModeChanged?.invoke(false)

        // v6.1.1: Consumir corrida pendente detectada durante Ghost Mode
        val pending = RideNotificationListener.instance?.pendingGhostRide
        if (pending != null) {
            Log.i(TAG_GHOST, "│  📨 Corrida pendente do Ghost Mode — enviando ao OverlayService")
            RideNotificationListener.instance?.pendingGhostRide = null
            // v7.1.0: Fallback robusto
            if (!OverlayService.isRunning() || OverlayService.onRideDetected == null) {
                Log.w(TAG_GHOST, "│  ⚠️ OverlayService não disponível. Iniciando via Intent...")
                OverlayService.start(this@RideAccessibilityService, pending)
            } else {
                OverlayService.onRideDetected?.invoke(pending)
            }
        }
    }

    /**
     * Polling via UsageStatsManager para detectar quando o banco sai do foreground.
     * Isso é necessário porque durante Ghost Mode o serviceInfo está hibernado
     * e pode não receber TYPE_WINDOW_STATE_CHANGED de forma confiável.
     *
     * Intervalo: 2 segundos
     * Timeout: 5 minutos (segurança contra Ghost Mode preso)
     */
    private fun startGhostModePolling() {
        stopGhostModePolling() // Limpar polling anterior se houver

        ghostPollingRunnable = object : Runnable {
            override fun run() {
                // Timeout de segurança
                val elapsed = System.currentTimeMillis() - ghostModeStartTime
                if (elapsed > GHOST_MODE_TIMEOUT_MS) {
                    Log.w(TAG_GHOST, "│  ⚠ Ghost Mode TIMEOUT (${elapsed / 1000}s) — forçando desativação")
                    deactivateGhostMode("timeout_forced")
                    return
                }

                // Verificar app em foreground via UsageStatsManager
                val currentForeground = getForegroundPackage()
                if (currentForeground != null && !isBankApp(currentForeground)) {
                    Log.d(TAG_GHOST, "│  Banco saiu do foreground (atual: $currentForeground)")
                    deactivateGhostMode(currentForeground)
                    return
                }

                // Continuar polling
                ghostHandler.postDelayed(this, 2000L)
            }
        }

        ghostPollingRunnable?.let { ghostHandler.postDelayed(it, 2000L) }
        Log.d(TAG_GHOST, "│  Polling iniciado (intervalo=2s, timeout=5min)")
    }

    private fun stopGhostModePolling() {
        ghostPollingRunnable?.let {
            ghostHandler.removeCallbacks(it)
            ghostPollingRunnable = null
        }
    }

    /**
     * Obtém o package do app em foreground via UsageStatsManager.
     * Requer permissão PACKAGE_USAGE_STATS (concedida nas configurações).
     * Se não disponível, retorna null (fallback para evento de acessibilidade).
     */
    private fun getForegroundPackage(): String? {
        try {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return null

            val now = System.currentTimeMillis()
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 10_000L, // Últimos 10 segundos
                now
            )

            if (stats.isNullOrEmpty()) return null

            // Retornar o app com lastTimeUsed mais recente
            return stats.maxByOrNull { it.lastTimeUsed }?.packageName
        } catch (e: Exception) {
            Log.d(TAG_GHOST, "│  UsageStats indisponível: ${e.message}")
            return null
        }
    }

    // =========================================================================
    // BLOCO: Parser Uber Driver — v6.0.0
    // =========================================================================
    // Textos reais capturados da tela do Uber Driver (Brasil, 2026):
    //   "UberX" / "Comfort" / "Black" (badge tipo)
    //   "R$ 6,98" ou "R$16,60" (valor)
    //   "★ 5,00 (26)" ou "4,90 (275)" (rating)
    //   "8 minutos (3.4 km) de distância" (pickup)
    //   "Rua X, Bairro / Cidade" (endereço embarque)
    //   "Viagem de 3 minutos (1.0 km)" (viagem)
    //   "R. Y - Bairro, Cidade - UF, CEP, Brasil" (destino)
    //   "Selecionar" ou "Aceitar" (botão)
    //   "1 parada" (paradas intermediárias)
    // =========================================================================
    private fun parseUberRide(allText: List<String>, allNodes: List<NodeData> = emptyList()): RideData? {
        if (isEarningsScreen(allText)) {
            Log.d(TAG_PARSE, "│    [UBER] ✖ Tela de Ganhos detectada — descartando")
            return null
        }
        if (isNotificationShadeContent(allText)) {
            Log.d(TAG_PARSE, "│    [UBER] ✖ Tela de Notificações detectada — descartando")
            return null
        }
        
        // v6.9.15: Permitir o processamento se for uma oferta back-to-back (contém botão de aceitar)
        val hasAcceptButtonInText = allText.any { text ->
            val trimmed = text.trim().lowercase()
            trimmed == "aceitar" || trimmed == "selecionar" || trimmed == "accept" || trimmed == "select" || trimmed == "aceptar"
        }
        if (isNavigationScreen(allText) && !hasAcceptButtonInText) {
            Log.d(TAG_PARSE, "│    [UBER] ✖ Tela de Navegação detectada (sem botão Aceitar) — descartando")
            return null
        }

        var rideValue = 0.0
        var pickupDistance = 0.0
        var dropoffDistance = 0.0
        var duration = 0.0
        var rating = 0.0
        var stops = 0
        var pickupNeighborhood = ""
        var dropoffNeighborhood = ""
        var hasAcceptButton = false
        var rideTypeBadge = ""

        // Flags de contexto para bairros
        var foundPickupContext = false
        var foundTripContext = false

        for (text in allText) {
            val trimmed = text.trim()

            // ── BOTÃO ACEITAR/SELECIONAR ──
            if (trimmed.equals("Aceitar", ignoreCase = true) ||
                trimmed.equals("Selecionar", ignoreCase = true) ||
                trimmed.equals("Accept", ignoreCase = true) ||
                trimmed.equals("Select", ignoreCase = true) ||
                trimmed.equals("Aceptar", ignoreCase = true)) {
                hasAcceptButton = true
                Log.d(TAG_PARSE, "│    [UBER] Botão aceitar encontrado: \"$trimmed\"")
            }

            // ── TIPO DE CORRIDA (badge) ──
            if (rideTypeBadge.isBlank()) {
                val typeMatch = Regex("""(?i)^(UberX|Uber\s*X|Comfort|Black|Flash|Promo|Green|Prioridade|Priority|UberX\s*Share|Moto|Reserve|Connect|Pet)$""").find(trimmed)
                if (typeMatch != null) {
                    rideTypeBadge = typeMatch.value
                    Log.d(TAG_PARSE, "│    [UBER] Tipo: \"$rideTypeBadge\"")
                }
            }

            // ── VALOR DA CORRIDA ──
            // Formatos: "R$ 6,98", "R$16,60", "R$ 79,35", "R$6.98"
            if (rideValue == 0.0) {
                val valueMatch = Regex("""R\$\s*(\d{1,4}[.,]\d{2})""").find(text)
                if (valueMatch != null) {
                    val v = valueMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
                    if (v in 2.0..999.0) {
                        rideValue = v
                        Log.d(TAG_PARSE, "│    [UBER] Valor: R$ $v")
                    }
                }
            }

            // ── RATING DO PASSAGEIRO ──
            // Formatos: "★ 5,00 (26)", "4,90 (275)", "★5.00(26)", "5,00 ★"
            if (rating == 0.0) {
                val ratingMatch = Regex("""★?\s*([4-5][.,]\d{1,2})\s*\(?\d*\)?""").find(text)
                if (ratingMatch != null) {
                    val r = ratingMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
                    if (r in 3.0..5.0) {
                        rating = r
                        Log.d(TAG_PARSE, "│    [UBER] Rating: ★ $r")
                    }
                }
            }

            // ── DISTÂNCIA ATÉ EMBARQUE (pickup) ──
            // Formatos: "8 minutos (3.4 km) de distância", "5 min (2,1 km) away"
            if (pickupDistance == 0.0) {
                val pickupMatch = Regex("""(\d+)\s*(?:minutos?|min)\s*\((\d+[.,]\d+)\s*km\)\s*(?:de\s*dist|away|de\s*distância)""", RegexOption.IGNORE_CASE).find(text)
                if (pickupMatch != null) {
                    pickupDistance = pickupMatch.groupValues[2].replace(",", ".").toDoubleOrNull() ?: 0.0
                    foundPickupContext = true
                    Log.d(TAG_PARSE, "│    [UBER] Pickup: ${pickupMatch.groupValues[1]} min / $pickupDistance km")
                }
            }

            // ── VIAGEM (duração + distância da corrida) ──
            // Formatos: "Viagem de 3 minutos (1.0 km)", "Trip of 15 min (8.2 km)"
            if (dropoffDistance == 0.0) {
                val tripMatch = Regex("""(?:Viagem|Trip|Viaje)\s+de\s+(\d+)\s*(?:minutos?|min)\s*\((\d+[.,]\d+)\s*km\)""", RegexOption.IGNORE_CASE).find(text)
                if (tripMatch != null) {
                    duration = tripMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                    dropoffDistance = tripMatch.groupValues[2].replace(",", ".").toDoubleOrNull() ?: 0.0
                    foundTripContext = true
                    Log.d(TAG_PARSE, "│    [UBER] Trip: $duration min / $dropoffDistance km")
                }
            }

            // ── PARADAS ──
            if (stops == 0) {
                val stopMatch = Regex("""(\d+)\s*parada""", RegexOption.IGNORE_CASE).find(text)
                if (stopMatch != null) {
                    stops = stopMatch.groupValues[1].toIntOrNull() ?: 1
                    Log.d(TAG_PARSE, "│    [UBER] Paradas: $stops")
                } else if (text.contains("parada", ignoreCase = true) || text.contains("stop", ignoreCase = true)) {
                    stops = 1
                }
            }

            // ── BAIRROS (extrair do endereço) ──
            // Formato Uber: "Rua X, Bairro / Cidade" ou "R. X - Bairro, Cidade - UF" ou apenas "Efapi"
            if (pickupNeighborhood.isBlank() && foundPickupContext && !foundTripContext) {
                val neighborhoodMatch = Regex("""[/\-,]\s*([A-ZÀ-Ú][a-zà-ú]+(?:\s+[A-ZÀ-Ú][a-zà-ú]+){0,3})""").find(text)
                var candidate = neighborhoodMatch?.groupValues?.get(1)?.trim() ?: ""
                
                if (candidate.isEmpty() && text.trim().matches(Regex("""^[A-ZÀ-Úa-zà-ú\s]+$""")) && text.length in 3..40) {
                    candidate = text.trim()
                }

                if (candidate.isNotBlank() && !isCommonWord(candidate)) {
                    pickupNeighborhood = candidate.take(30)
                    Log.d(TAG_PARSE, "│    [UBER] Bairro pickup: \"$pickupNeighborhood\"")
                }
            }
            if (dropoffNeighborhood.isBlank() && foundTripContext) {
                val neighborhoodMatch = Regex("""[/\-,]\s*([A-ZÀ-Ú][a-zà-ú]+(?:\s+[A-ZÀ-Ú][a-zà-ú]+){0,3})""").find(text)
                var candidate = neighborhoodMatch?.groupValues?.get(1)?.trim() ?: ""
                
                if (candidate.isEmpty() && text.trim().matches(Regex("""^[A-ZÀ-Úa-zà-ú\s]+$""")) && text.length in 3..40) {
                    candidate = text.trim()
                }

                if (candidate.isNotBlank() && !isCommonWord(candidate) && candidate != pickupNeighborhood) {
                    dropoffNeighborhood = candidate.take(30)
                    Log.d(TAG_PARSE, "│    [UBER] Bairro dropoff: \"$dropoffNeighborhood\"")
                }
            }
        }

        // =====================================================================
        // FALLBACK GEOMÉTRICO (v7.0) - Anti-Detection da Uber
        // Se a Uber separou os textos em várias views, o Regex acima falha.
        // =====================================================================
        if (rideValue == 0.0 && allNodes.isNotEmpty()) {
            val geomValue = GeometryParser.findValueNearAnchor(allNodes, "R$")
            if (geomValue in 2.0..999.0) {
                rideValue = geomValue
                Log.d(TAG_PARSE, "│    [UBER] Valor (Geometria): R$ $geomValue")
            }
        }

        if (dropoffDistance == 0.0 && allNodes.isNotEmpty()) {
            val geomDist = GeometryParser.findDistanceNearAnchor(allNodes, "km")
            if (geomDist > 0.0) {
                dropoffDistance = geomDist
                Log.d(TAG_PARSE, "│    [UBER] Trip KM (Geometria): $geomDist km")
            }
        }

        if (duration == 0.0 && allNodes.isNotEmpty()) {
            val geomDur = GeometryParser.findDistanceNearAnchor(allNodes, "min")
            if (geomDur > 0.0) {
                duration = geomDur
                Log.d(TAG_PARSE, "│    [UBER] Trip Min (Geometria): $geomDur min")
            }
        }

        // ── FALLBACK: Distâncias soltas "X.X km" ──
        if (dropoffDistance == 0.0 || pickupDistance == 0.0) {
            val allDistances = mutableListOf<Double>()
            for (text in allText) {
                Regex("""(\d+[.,]\d+)\s*km""", RegexOption.IGNORE_CASE).findAll(text).forEach { m ->
                    val d = m.groupValues[1].replace(",", ".").toDoubleOrNull()
                    if (d != null && d > 0 && d < 100) allDistances.add(d)
                }
            }
            // Remover distâncias já encontradas
            allDistances.remove(pickupDistance)
            allDistances.remove(dropoffDistance)

            if (pickupDistance == 0.0 && dropoffDistance == 0.0 && allDistances.size >= 2) {
                pickupDistance = allDistances[0]
                dropoffDistance = allDistances[1]
                Log.d(TAG_PARSE, "│    [UBER] Fallback distâncias: pickup=$pickupDistance, trip=$dropoffDistance")
            } else if (dropoffDistance == 0.0 && allDistances.isNotEmpty()) {
                dropoffDistance = allDistances[0]
                Log.d(TAG_PARSE, "│    [UBER] Fallback trip: $dropoffDistance km")
            }
        }

        // ── FALLBACK: Duração solta "X min" ──
        if (duration == 0.0) {
            for (text in allText) {
                val durMatch = Regex("""(\d+)\s*min""", RegexOption.IGNORE_CASE).find(text)
                if (durMatch != null) {
                    val d = durMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                    if (d > 0 && d < 120) {
                        duration = d
                        Log.d(TAG_PARSE, "│    [UBER] Fallback duração: $d min")
                        break
                    }
                }
            }
        }

        // ── VALIDAÇÃO v6.9.8: EXIGIR botão aceitar para confirmar que é tela de OFERTA ──
        // Sem botão = tela de corrida ativa, resumo, ou histórico → IGNORAR
        // Isso elimina falsos positivos de releitura da corrida em andamento
        if (!hasAcceptButton) {
            Log.d(TAG_PARSE, "│    [UBER] ✖ Sem botão Aceitar/Selecionar — não é tela de oferta, descartando")
            return null
        }
        if (rideValue == 0.0) {
            Log.d(TAG_PARSE, "│    [UBER] ✖ Botão aceitar presente mas valor R$0 — descartando")
            return null
        }

        // ── MONTAR RideData ──
        val detectedType = if (rideTypeBadge.isNotBlank()) {
            RideType.fromBadgeText(rideTypeBadge, Platform.UBER)
        } else {
            RideType.UBER_X
        }

        val isRadar = allText.any { text ->
            val normalized = text.lowercase()
            normalized.contains("radar") || normalized.contains("viagens por perto") || normalized.contains("trip radar")
        }

        return RideData(
            platform = Platform.UBER,
            rideType = detectedType,
            rideValue = rideValue,
            rideDuration = duration,
            pickupDistance = pickupDistance,
            dropoffDistance = dropoffDistance,
            passengerRating = rating,
            intermediateStops = stops,
            pickupNeighborhood = pickupNeighborhood,
            dropoffNeighborhood = dropoffNeighborhood,
            metadata = mapOf("isRadar" to isRadar.toString())
        )
    }

    // =========================================================================
    // BLOCO: Parser 99 Motorista — v6.9.7
    // Layout real da oferta 99:
    //   Linha: "R$18,60"          → valor principal
    //   Linha: "R$1,74/km"        → ignorar (é R$/km, não valor)
    //   Linha: "R$3,86 Tarifa base dinâmica" → ignorar (é tarifa base)
    //   Linha: "4,89 • 353 corridas" → rating (sem ★, usa •)
    //   Linha: "5min (1,1km)"     → pickup: duração + distância
    //   Linha: "19min (9,6km)"    → dropoff: duração + distância
    //   Linha: "Rua X, Vila Rica" → bairro pickup
    //   Linha: "Rua Y, Bom Pastor" → bairro dropoff
    // =========================================================================
    private fun parse99Ride(allText: List<String>): RideData? {
        var rideValue = 0.0
        var dropoffDistance = 0.0
        var pickupDistance = 0.0
        var pickupDuration = 0.0
        var dropoffDuration = 0.0
        var rating = 0.0
        var stops = 0
        var pickupNeighborhood = ""
        var dropoffNeighborhood = ""
        var foundPickup = false
        var foundDropoff = false

        for (text in allText) {
            val trimmed = text.trim()

            // ── VALOR PRINCIPAL ──
            // Captura "R$18,60" mas ignora "R$1,74/km" e "R$3,86 Tarifa base"
            if (rideValue == 0.0) {
                // Ignora linhas com /km ou "tarifa" ou "base"
                val lowerText = trimmed.lowercase()
                if (!lowerText.contains("/km") && !lowerText.contains("tarifa") && !lowerText.contains("base")) {
                    val valueMatch = Regex("""^R\$\s*(\d{1,4}[.,]\d{2})$""").find(trimmed)
                        ?: Regex("""R\$\s*(\d{1,4}[.,]\d{2})(?:\s|$)""").find(trimmed)
                    if (valueMatch != null) {
                        val v = valueMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
                        if (v in 2.0..999.0) {
                            rideValue = v
                            Log.d(TAG_PARSE, "│    [99] Valor: R$ $v")
                        }
                    }
                }
            }

            // ── RATING ──
            // Formatos: "4,89 • 353 corridas", "4,89 ★", "★ 4,89", "4.89"
            if (rating == 0.0) {
                val ratingMatch =
                    Regex("""([4-5][.,]\d{1,2})\s*[•★·]""").find(trimmed)
                    ?: Regex("""[•★·]\s*([4-5][.,]\d{1,2})""").find(trimmed)
                    ?: Regex("""^([4-5][.,]\d{2})\s*•""").find(trimmed)
                if (ratingMatch != null) {
                    val r = ratingMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
                    if (r in 3.0..5.0) {
                        rating = r
                        Log.d(TAG_PARSE, "│    [99] Rating: $r")
                    }
                }
            }

            // ── PICKUP: "5min (1,1km)" ou "5 min (1.1km)" ──
            // Primeira ocorrência de "Xmin (Y,Zkm)" = embarque
            if (!foundPickup) {
                val pickupMatch = Regex("""(\d+)\s*min\s*\((\d+[.,]\d+)\s*km\)""", RegexOption.IGNORE_CASE).find(trimmed)
                if (pickupMatch != null) {
                    pickupDuration = pickupMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                    pickupDistance = pickupMatch.groupValues[2].replace(",", ".").toDoubleOrNull() ?: 0.0
                    foundPickup = true
                    Log.d(TAG_PARSE, "│    [99] Pickup: ${pickupDuration.toInt()} min / $pickupDistance km")
                }
            } else if (!foundDropoff) {
                // Segunda ocorrência = desembarque
                val dropoffMatch = Regex("""(\d+)\s*min\s*\((\d+[.,]\d+)\s*km\)""", RegexOption.IGNORE_CASE).find(trimmed)
                if (dropoffMatch != null) {
                    dropoffDuration = dropoffMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                    dropoffDistance = dropoffMatch.groupValues[2].replace(",", ".").toDoubleOrNull() ?: 0.0
                    foundDropoff = true
                    Log.d(TAG_PARSE, "│    [99] Dropoff: ${dropoffDuration.toInt()} min / $dropoffDistance km")
                }
            }

            // ── BAIRROS (endereços após pickup/dropoff) ──
            // Formato 99: "Rua Amaral Pedroso, 43, Vila Rica"
            if (foundPickup && pickupNeighborhood.isBlank() && !foundDropoff) {
                val neighborMatch = Regex(""",\s*([A-ZÀ-Ú][a-zà-ú]+(?:\s+[A-ZÀ-Ú][a-zà-ú]+){0,3})\s*$""").find(trimmed)
                if (neighborMatch != null && !isCommonWord(neighborMatch.groupValues[1])) {
                    pickupNeighborhood = neighborMatch.groupValues[1].trim().take(30)
                    Log.d(TAG_PARSE, "│    [99] Bairro pickup: \"$pickupNeighborhood\"")
                }
            }
            if (foundDropoff && dropoffNeighborhood.isBlank()) {
                val neighborMatch = Regex(""",\s*([A-ZÀ-Ú][a-zà-ú]+(?:\s+[A-ZÀ-Ú][a-zà-ú]+){0,3})\s*$""").find(trimmed)
                if (neighborMatch != null && !isCommonWord(neighborMatch.groupValues[1]) && neighborMatch.groupValues[1] != pickupNeighborhood) {
                    dropoffNeighborhood = neighborMatch.groupValues[1].trim().take(30)
                    Log.d(TAG_PARSE, "│    [99] Bairro dropoff: \"$dropoffNeighborhood\"")
                }
            }

            // ── PARADAS ──
            if (stops == 0 && (trimmed.contains("parada", ignoreCase = true) || trimmed.contains("stop", ignoreCase = true))) {
                val stopMatch = Regex("""(\d+)\s*parada""", RegexOption.IGNORE_CASE).find(trimmed)
                stops = stopMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
            }
        }

        if (rideValue == 0.0) {
            Log.d(TAG_PARSE, "│    [99] ✖ Sem valor — descartando")
            return null
        }

        // Duração total = pickup + dropoff
        val totalDuration = if (dropoffDuration > 0) dropoffDuration else pickupDuration

        Log.d(TAG_PARSE, "│    [99] ✅ Valor=R$$rideValue, Pickup=${pickupDistance}km, Dropoff=${dropoffDistance}km, Dur=${totalDuration}min, Rating=$rating")

        return RideData(
            platform = Platform.NINETY_NINE,
            rideType = RideType.NINETY_NINE_POP,
            rideValue = rideValue,
            pickupDistance = pickupDistance,
            dropoffDistance = dropoffDistance,
            rideDuration = totalDuration,
            passengerRating = rating,
            intermediateStops = stops,
            pickupNeighborhood = pickupNeighborhood,
            dropoffNeighborhood = dropoffNeighborhood
        )
    }

    // =========================================================================
    // BLOCO: Parser inDrive — v6.0.0
    // =========================================================================
    private fun parseInDriveRide(allText: List<String>): RideData? {
        var rideValue = 0.0
        var distance = 0.0
        var duration = 0.0
        var pickupNeighborhood = ""
        var dropoffNeighborhood = ""

        for (text in allText) {
            if (rideValue == 0.0) {
                val valueMatch = Regex("""R\$\s*(\d{1,4}[.,]\d{2})""").find(text)
                if (valueMatch != null) {
                    val v = valueMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
                    if (v in 2.0..999.0) rideValue = v
                }
            }

            if (distance == 0.0) {
                val distMatch = Regex("""(\d+[.,]\d+)\s*km""", RegexOption.IGNORE_CASE).find(text)
                if (distMatch != null) {
                    distance = distMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
                }
            }

            if (duration == 0.0) {
                val durMatch = Regex("""(\d+)\s*min""", RegexOption.IGNORE_CASE).find(text)
                if (durMatch != null) {
                    val d = durMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                    if (d > 0 && d < 120) duration = d
                }
            }

            if (pickupNeighborhood.isBlank() && rideValue > 0) {
                val m = Regex("""[/\-,]\s*([A-ZÀ-Ú][a-zà-ú]+(?:\s+[A-ZÀ-Ú][a-zà-ú]+){0,3})""").find(text)
                if (m != null && !isCommonWord(m.groupValues[1])) {
                    pickupNeighborhood = m.groupValues[1].trim().take(30)
                }
            }
        }

        if (rideValue == 0.0) return null

        Log.d(TAG_PARSE, "│    [inDrive] Valor=$rideValue, Dist=$distance, Dur=$duration")

        return RideData(
            platform = Platform.INDRIVE,
            rideValue = rideValue,
            dropoffDistance = distance,
            rideDuration = duration,
            pickupNeighborhood = pickupNeighborhood,
            dropoffNeighborhood = dropoffNeighborhood
        )
    }

    // =========================================================================
    // BLOCO: Parser Cabify — v6.0.0
    // =========================================================================
    private fun parseCabifyRide(allText: List<String>): RideData? {
        var rideValue = 0.0
        var distance = 0.0
        var duration = 0.0
        var rating = 0.0
        var pickupNeighborhood = ""
        var dropoffNeighborhood = ""

        for (text in allText) {
            if (rideValue == 0.0) {
                val valueMatch = Regex("""R\$\s*(\d{1,4}[.,]\d{2})""").find(text)
                if (valueMatch != null) {
                    val v = valueMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
                    if (v in 2.0..999.0) rideValue = v
                }
            }

            if (distance == 0.0) {
                val distMatch = Regex("""(\d+[.,]\d+)\s*km""", RegexOption.IGNORE_CASE).find(text)
                if (distMatch != null) distance = distMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
            }

            if (duration == 0.0) {
                val durMatch = Regex("""(\d+)\s*min""", RegexOption.IGNORE_CASE).find(text)
                if (durMatch != null) {
                    val d = durMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                    if (d > 0 && d < 120) duration = d
                }
            }

            if (rating == 0.0) {
                val ratingMatch = Regex("""([4-5][.,]\d{1,2})""").find(text)
                if (ratingMatch != null) {
                    val r = ratingMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
                    if (r in 3.0..5.0) rating = r
                }
            }
        }

        if (rideValue == 0.0) return null

        Log.d(TAG_PARSE, "│    [Cabify] Valor=$rideValue, Dist=$distance, Dur=$duration, Rating=$rating")

        return RideData(
            platform = Platform.CABIFY,
            rideValue = rideValue,
            dropoffDistance = distance,
            rideDuration = duration,
            passengerRating = rating,
            pickupNeighborhood = pickupNeighborhood,
            dropoffNeighborhood = dropoffNeighborhood
        )
    }

    // =========================================================================
    // BLOCO: traverseNode — Travessia recursiva da árvore de acessibilidade
    // =========================================================================
    /**
     * Percorre nós recursivamente extraindo TODOS os textos visíveis.
     * v6.0.0: maxDepth=50 (Compose pode ter 25+ níveis)
     *
     * Extrai de cada nó:
     * - text (texto principal)
     * - contentDescription (semântica Compose)
     * - hintText (placeholder, pode conter info útil)
     *
     * @return Número total de nós visitados (para debug)
     */
    private fun traverseNode(node: AccessibilityNodeInfo, texts: MutableList<String>, depth: Int): Int {
        if (depth > 50) return 0 // Compose pode ter até 25+ níveis, 50 é seguro

        var nodesVisited = 1

        // Extrair texto principal
        node.text?.toString()?.let { t ->
            val trimmed = t.trim()
            if (trimmed.isNotBlank() && trimmed.length < 500) {
                texts.add(trimmed)
            }
        }

        // Extrair contentDescription (Compose semantics usa isso)
        node.contentDescription?.toString()?.let { cd ->
            val trimmed = cd.trim()
            if (trimmed.isNotBlank() && trimmed.length < 500 && trimmed !in texts) {
                texts.add(trimmed)
            }
        }

        // Extrair hintText (API 26+) — pode conter info de placeholder
        if (Build.VERSION.SDK_INT >= 26) {
            node.hintText?.toString()?.let { ht ->
                val trimmed = ht.trim()
                if (trimmed.isNotBlank() && trimmed.length < 200 && trimmed !in texts) {
                    texts.add(trimmed)
                }
            }
        }

        // Percorrer filhos recursivamente
        for (i in 0 until node.childCount) {
            try {
                val child = node.getChild(i) ?: continue
                try {
                    nodesVisited += traverseNode(child, texts, depth + 1)
                } finally {
                    // recycle() was deprecated
                }
            } catch (_: Exception) {
                // Nó pode ter sido reciclado por outro thread — ignorar
            }
        }

        return nodesVisited
    }

    // =========================================================================
    // BLOCO: Accessibility Tree Dump — Diagnóstico do AutoPilot
    // v7.3.0: Captura árvore completa quando corrida detectada
    // Registra: texto, contentDescription, className, isClickable, bounds
    // Permite identificar exatamente o que o AutoPilot "vê" da Uber/99/inDrive
    // =========================================================================
    private fun dumpAccessibilityTree(packageFilter: String) {
        try {
            val telemetry = com.ngbautoroad.domain.TelemetryLogger.getInstance(applicationContext)
            val sb = StringBuilder()
            sb.appendLine("=== A11Y TREE DUMP: $packageFilter ===")

            val windowList = windows ?: return
            for (window in windowList) {
                val root = window.root ?: continue
                val pkg = root.packageName?.toString() ?: ""
                if (pkg != packageFilter && pkg.isNotEmpty()) {
                    try { root.recycle() } catch (_: Exception) {}
                    continue
                }
                sb.appendLine("Window: ${window.title} type=${window.type} layer=${window.layer}")
                dumpNode(root, sb, depth = 0, maxDepth = 15)
                try { root.recycle() } catch (_: Exception) {}
            }

            val dump = sb.toString()
            if (dump.length > 200) {
                telemetry.log(
                    com.ngbautoroad.domain.TelemetryLogger.Category.PARSER,
                    com.ngbautoroad.domain.TelemetryLogger.Level.INFO,
                    dump.take(8000)
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("NGB_A11Y_DUMP", "Dump failed: ${e.message}")
        }
    }

    private fun dumpNode(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int, maxDepth: Int) {
        if (depth > maxDepth) return
        val indent = "  ".repeat(depth)
        val text = node.text?.toString()?.take(80) ?: ""
        val desc = node.contentDescription?.toString()?.take(80) ?: ""
        val cls = node.className?.toString()?.substringAfterLast('.') ?: ""
        val clickable = if (node.isClickable) " [CLICK]" else ""
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)

        if (text.isNotBlank() || desc.isNotBlank() || node.isClickable) {
            sb.appendLine("${indent}[$cls]$clickable txt=\"$text\" desc=\"$desc\" bounds=${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}")
        }

        for (i in 0 until node.childCount) {
            try {
                val child = node.getChild(i) ?: continue
                dumpNode(child, sb, depth + 1, maxDepth)
                try { child.recycle() } catch (_: Exception) {}
            } catch (_: Exception) {}
        }
    }

    // =========================================================================
    // BLOCO: Utilidades
    // =========================================================================

    override fun onInterrupt() {
        Log.w(TAG_ENGINE, "AccessibilityService INTERROMPIDO")
    }

    /**
     * v6.2.0: Override do onLowMemory do sistema + chamado pelo MemoryMonitor.
     * Libera recursos não essenciais para evitar kill por OOM (Android 17).
     */
    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG_ENGINE, "onLowMemory: liberando cache do AccessibilityService")
        // Resetar estado de screenshot pendente para liberar referências
        pendingScreenshotForPackage = null
        // Cancelar handlers pendentes não essenciais
        screenshotHandler.removeCallbacksAndMessages(null)
        // Forçar GC hint
        System.gc()
        Log.d(TAG_ENGINE, "onLowMemory: cache liberado")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        isServiceAlive = false
        stealthModeActive = false
        stopGhostModePolling()
        // v6.1.0: Destruir lifecycle e autopilot
        lifecycleManager?.destroy()
        lifecycleManager = null
        autoPilotEngine?.destroy()
        autoPilotEngine = null
        // v6.2.0: AS foi destruído — promover NotificationListener a canal primário
        // Garante que corridas continuem sendo detectadas mesmo sem AccessibilityService
        RideNotificationListener.isPrimaryChannel = true
        Log.i(TAG_ENGINE, "AccessibilityService DESTRUÍDO — NotificationListener promovido a canal primário")
    }

    private fun isBankApp(packageName: String): Boolean {
        return BANK_PACKAGES.any { packageName.startsWith(it) }
    }

    /**
     * v7.5.0: Identifica janelas transitórias do sistema que podem aparecer SOBRE o app
     * de banco (barra de notificações, teclado/IME, diálogos do sistema, o próprio app).
     * Um WINDOW_STATE dessas janelas NÃO significa que o motorista saiu do banco — então
     * não deve desativar o Ghost Mode. Usado como salvaguarda quando o UsageStats não está
     * disponível para confirmar o app em foreground.
     */
    private fun isTransientSystemWindow(packageName: String): Boolean {
        if (packageName.isEmpty()) return true
        if (packageName == applicationContext.packageName) return true // nosso próprio app
        if (packageName == "com.android.systemui" || packageName == "android") return true
        // Teclados (IME): Gboard, Samsung Honeyboard, SwiftKey, etc.
        if (packageName.contains("inputmethod") || packageName.contains("honeyboard") ||
            packageName.contains("keyboard") || packageName.contains("latin") ||
            packageName.contains("swiftkey")) return true
        return false
    }

    private fun isRideApp(packageName: String): Boolean {
        return RIDE_PACKAGES.contains(packageName)
    }

    private fun detectPlatform(packageName: String): Platform? {
        return Platform.entries.find { it.packageName == packageName }
            ?: if (packageName == "com.ubercab") Platform.UBER else null
    }

    /**
     * Gera hash único para uma corrida (para deduplicação).
     * Combina: plataforma + valor + pickup km + dropoff km + bairro pickup
     */
    private fun generateRideHash(ride: RideData): Int {
        return "${ride.platform}_${String.format("%.2f", ride.rideValue)}_${String.format("%.1f", ride.pickupDistance)}_${String.format("%.1f", ride.dropoffDistance)}_${ride.pickupNeighborhood}".hashCode()
    }

    /**
     * v7.5.0: Limpa o estado de deduplicação do engine de detecção.
     *
     * Chamado pelo RideLifecycleManager quando a corrida atual é RESOLVIDA. Sem isso,
     * a janela de dedup de 30s bloquearia uma re-oferta (a Uber re-oferta a MESMA corrida
     * segundos após uma recusa) e o engine nunca enviaria a oferta ao OverlayService —
     * o motorista nunca veria o card. Limpar aqui garante que toda oferta válida
     * após uma resolução chegue ao overlay.
     */
    fun resetRideDedup() {
        lastRideHash = 0
        lastRideHashTime = 0L
        lastRideValue = 0.0
        lastRideValueTime = 0L
        Log.d(TAG_DEDUP, "│  Dedup do engine limpo — próxima oferta (mesmo re-oferta idêntica) será processada")
    }

    /**
     * Filtra palavras comuns que não são bairros.
     * Evita falsos positivos como "Brasil", "Rua", "Avenida" etc.
     */
    private fun isCommonWord(word: String): Boolean {
        // v6.9.14: Ignorar strings muito longas (provavelmente frases capturadas erroneamente)
        if (word.length > 30) return true

        val common = setOf(
            "Brasil", "Brazil", "Rua", "Avenida", "Alameda", "Travessa",
            "Rodovia", "Estrada", "Praça", "Largo", "Viela", "Beco",
            "Norte", "Sul", "Leste", "Oeste", "Centro",
            "Uber", "Viagem", "Trip", "Pickup", "Dropoff",
            "Selecionar", "Aceitar", "Cancelar", "Recusar",
            "Ganhos", "Saldo", "Carteira", "Transferir", "Resumo", "Rendimento"
        )
        return common.any { it.equals(word, ignoreCase = true) } || word.split(" ").size > 5
    }

    /**
     * Converte eventType int para string legível (para logs).
     */
    private fun eventTypeToString(eventType: Int): String {
        return when (eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "WINDOW_STATE"
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "WINDOW_CONTENT"
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "TEXT_CHANGED"
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> "SCROLLED"
            else -> "TYPE_$eventType"
        }
    }

    // Utilizado por: parseUberRide
    // Depende de: List<String> contendo a lista de todos os nós de texto visíveis na janela
    private fun isEarningsScreen(texts: List<String>): Boolean {
        val joined = texts.joinToString(" ").lowercase()
        val earningsIndicators = listOf(
            "última viagem", "ver histórico de ganhos", "ver resumo semanal",
            "viagens concluídas", "uber pro", "ver progresso",
            "começar", "você está offline"
        )
        val matchCount = earningsIndicators.count { joined.contains(it) }
        return matchCount >= 2 // 2+ indicadores = tela de ganhos
    }

    // Utilizado por: parseUberRide
    // Depende de: List<String> contendo a lista de todos os nós de texto visíveis na janela
    private fun isNotificationShadeContent(texts: List<String>): Boolean {
        val joined = texts.joinToString(" ").lowercase()
        val shadeIndicators = listOf(
            "recentes", "voltar", "início", "painéis edge", "silenciar",
            "notificações", "gerenciar", "limpar tudo"
        )
        return shadeIndicators.any { joined.contains(it) }
    }

    // Utilizado por: parseUberRide
    // Depende de: List<String> contendo a lista de todos os nós de texto visíveis na janela
    private fun isNavigationScreen(texts: List<String>): Boolean {
        val joined = texts.joinToString(" ").lowercase()
        val hasDestino = joined.contains("destino de")
        val hasCountdown = Regex("""(em|a)\s+\d+(?:[.,]\d+)?\s*(m|km)""").containsMatchIn(joined)
        return hasDestino || hasCountdown
    }
}
