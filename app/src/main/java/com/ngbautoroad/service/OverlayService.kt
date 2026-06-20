package com.ngbautoroad.service

// ============================================================================
// ARQUIVO: OverlayService.kt
// LOCALIZAÇÃO: service/OverlayService.kt
// RESPONSABILIDADE: Serviço foreground que exibe card flutuante sobre outros apps
// BLOCOS PRINCIPAIS:
//   - companion object (L71-95): start/stop/resize estáticos
//   - onCreate (L96-131): Inicializa PrefsManager, carrega config, registra lifecycle
//   - onStartCommand (L132-137): Recebe RideData via Intent e chama showOverlay
//   - showOverlay (L148-240): Calcula score, salva histórico, auto-import, exibe card
//   - hideOverlay (L241-250): Remove overlay com animação
//   - createOverlay (L251-332): Cria WindowManager.LayoutParams + ComposeView
//   - updateOverlayContent (L333-350): Atualiza conteúdo do Compose
//   - resizeOverlay (L351-364): Redimensiona card ao vivo
//   - createNotification (L365-fim): Notificação do foreground service
// DEPENDÊNCIAS:
//   - domain/RideScorer.kt → calcula score
//   - service/OverlayCard.kt → composable do card visual
//   - data/prefs/PrefsManager.kt → config de pesos, thresholds, posição
//   - data/db/AppDatabase.kt → salva histórico
//   - data/db/FinanceDatabase.kt → auto-import de ganhos
//   - data/model/CardGallery.kt → GalleryCard ativo
// DEPENDENTES:
//   - ui/settings/SettingsTab.kt → chama start/stop/resize
//   - service/RideAccessibilityService.kt → envia RideData via Intent
//   - service/OcrCaptureService.kt → envia RideData via Intent
// PROTEÇÕES:
//   - CoroutineScope cancelado no onDestroy (sem memory leak)
//   - Posição do overlay persistida no DataStore
//   - Deduplicação: não salva mesma corrida 2x (verifica timestamp)
// ============================================================================

import android.content.Context
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import com.ngbautoroad.R
import com.ngbautoroad.data.model.*
import com.ngbautoroad.data.prefs.PrefsManager
import com.ngbautoroad.domain.RideScorer
import com.ngbautoroad.domain.ShiftManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import com.ngbautoroad.data.db.AppDatabase
import com.ngbautoroad.data.db.RideHistoryEntity
import com.ngbautoroad.ui.editor.CustomCardLayout

/**
 * Serviço de overlay flutuante.
 * Exibe o card de avaliação de corrida sobre outros apps.
 *
 * Melhorias v4.0:
 * - Zonas bloqueadas (bairros e polígonos) integradas ao score em tempo real (item 1.1)
 * - Posição do overlay persistida no DataStore (item 4.2)
 * - Supressão de corridas duplicadas (item 2.2)
 * - Modo de proteção real: intervalo OCR randomizado (item 2.6)
 */
class OverlayService : Service(),
    LifecycleOwner,
    SavedStateRegistryOwner,
    ViewModelStoreOwner,
    OnBackPressedDispatcherOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val _viewModelStore = ViewModelStore()
    private val _onBackPressedDispatcher = OnBackPressedDispatcher(null)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = _viewModelStore
    override val onBackPressedDispatcher: OnBackPressedDispatcher get() = _onBackPressedDispatcher

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var isOverlayVisible = false
    private var currentRide: RideData? = null
    private var currentScore: RideScore? = null
    private var currentGalleryCard: CardGallery.GalleryCard? = null
    private var currentCustomLayout: com.ngbautoroad.ui.editor.CustomCardLayout? = null
    private var overlayWidth = 320
    private var currentFontScale = 1.0f
    private var naturalOverlayHeight = 0

    // Supressão de duplicatas (item 2.2)
    private var lastRideHash: Int = 0
    private var lastRideTime: Long = 0L
    private val DUPLICATE_WINDOW_MS = 3000L // 3 segundos

    private lateinit var prefsManager: PrefsManager
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    // v5.0.0: Auto-dismiss timer
    private var autoDismissJob: Job? = null

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.ngbautoroad.STOP_SERVICE"
        var onRideDetected: ((RideData) -> Unit)? = null
        var onStealthModeChanged: ((Boolean) -> Unit)? = null

        // Referência ao serviço ativo para resize ao vivo e MemoryMonitor
        var instance: OverlayService? = null
            private set

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }

        fun resizeFromOutside(newWidth: Int) {
            instance?.resizeOverlay(newWidth)
        }

        fun isRunning(): Boolean = instance != null
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        prefsManager = PrefsManager(applicationContext)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        instance = this

        // Carregar configurações iniciais
        serviceScope.launch {
            overlayWidth = prefsManager.overlayWidthFlow.first()
            currentFontScale = prefsManager.overlayFontScaleFlow.first()
        }

        onRideDetected = { ride ->
            serviceScope.launch {
                // Supressão de duplicatas (item 2.2)
                val rideHash = "${ride.platform}_${ride.rideValue}_${ride.dropoffDistance}".hashCode()
                val now = System.currentTimeMillis()
                if (rideHash == lastRideHash && (now - lastRideTime) < DUPLICATE_WINDOW_MS) {
                    return@launch // Ignorar duplicata
                }
                lastRideHash = rideHash
                lastRideTime = now
                showOverlay(ride)
            }
        }

        // Stealth mode: remover/restaurar overlay quando app bancário está ativo
        onStealthModeChanged = { stealthActive ->
            serviceScope.launch {
                if (stealthActive) {
                    hideOverlay()
                    // Também esconder o bubble
                    BubbleService.stop(this@OverlayService)
                } else {
                    // Restaurar bubble se turno estiver ativo
                    BubbleService.start(this@OverlayService)
                }
            }
        }

        // Limpar referência ao destruir
        lifecycleRegistry.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                if (instance === this@OverlayService) instance = null
            }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Tratar ação de desligar da notificação
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        return START_STICKY
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        hideOverlay()
        onRideDetected = null
        onStealthModeChanged = null
        serviceScope.cancel()
        _viewModelStore.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * v6.2.0: Override do onLowMemory do sistema + chamado pelo MemoryMonitor.
     * Libera recursos não essenciais do overlay para evitar kill por OOM (Android 17).
     */
    override fun onLowMemory() {
        super.onLowMemory()
        // Se não há overlay ativo, não há nada a liberar
        if (currentRide == null) return
        // Forçar GC hint
        System.gc()
    }

    private suspend fun showOverlay(ride: RideData) {
        currentRide = ride

        // Ler critérios reais do DataStore
        val weights = prefsManager.criteriaWeightsFlow.first()
        val thresholds = prefsManager.driverThresholdsFlow.first()
        val activeSlot = prefsManager.activeCardSlotFlow.first()
        currentFontScale = prefsManager.overlayFontScaleFlow.first()
        overlayWidth = prefsManager.overlayWidthFlow.first()

        // Obter card da galeria baseado no slot ativo (tipo correto: GalleryCard)
        currentCustomLayout = null // limpar por padrão
        currentGalleryCard = when (activeSlot) {
            1 -> CardGallery.getById(prefsManager.card1ModelIdFlow.first())
            2 -> CardGallery.getById(prefsManager.card2ModelIdFlow.first())
            3 -> {
                // Slot 3 = Custom: carregar CustomCardLayout COMPLETO (posições, fontes, estilos)
                val card3 = prefsManager.card3CustomFlow.first()
                val layoutJson = prefsManager.card3LayoutJsonFlow.first()
                val jsonParser = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

                // Salvar layout completo para o OverlayCard usar no modo custom
                currentCustomLayout = try {
                    if (layoutJson.isNotBlank()) jsonParser.decodeFromString<CustomCardLayout>(layoutJson)
                    else null
                } catch (_: Exception) { null }

                // GalleryCard como fallback (caso customLayout seja nulo)
                val fields = currentCustomLayout?.fields
                    ?.mapNotNull { f -> try { CardGallery.CardField.valueOf(f.fieldType) } catch (_: Exception) { null } }
                    ?.ifEmpty { CardGallery.CardField.entries }
                    ?: CardGallery.CardField.entries

                CardGallery.GalleryCard(
                    id = -1,
                    name = "Custom",
                    description = "Card customizado",
                    category = CardGallery.CardCategory.STANDARD,
                    fields = fields,
                    backgroundColor = card3.backgroundColor,
                    textColor = card3.textColor,
                    accentColor = card3.accentColor,
                    borderColor = card3.borderColor,
                    borderRadius = card3.borderRadius,
                    fontSize = card3.fontSize,
                    showBorder = true
                )
            }
            else -> null
        }

        // Item 1.1: Carregar bairros bloqueados do DataStore
        val blockedPickup = prefsManager.blockedPickupFlow.first()
            .map { (name, penalty) -> BlockedNeighborhood(name, NeighborhoodType.PICKUP, penalty) }
        val blockedDropoff = prefsManager.blockedDropoffFlow.first()
            .map { (name, penalty) -> BlockedNeighborhood(name, NeighborhoodType.DROPOFF, penalty) }

        // v6.3.0: Integrar zonas desenhadas no mapa (ZoneMapData)
        // Cada zona nomeada é tratada como bairro bloqueado adicional com penalidade fixa de 25pts
        val zoneMapJson = prefsManager.zoneMapDataFlow.first()
        val zoneNeighborhoods = if (zoneMapJson.isNotBlank()) {
            try {
                val mapData = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                    .decodeFromString<com.ngbautoroad.ui.map.ZoneMapData>(zoneMapJson)
                mapData.zones.filter { it.isEnabled }.map { zone ->
                    BlockedNeighborhood(
                        name = zone.name,
                        type = if (zone.type == "PICKUP") NeighborhoodType.PICKUP else NeighborhoodType.DROPOFF,
                        penaltyWeight = 25
                    )
                }
            } catch (_: Exception) { emptyList() }
        } else emptyList()

        val blockedNeighborhoods = blockedPickup + blockedDropoff + zoneNeighborhoods

        // Calcular score com critérios reais + bairros bloqueados
        val scorer = RideScorer(
            weights = weights,
            driverThresholds = thresholds,
            blockedNeighborhoods = blockedNeighborhoods
        )
        currentScore = scorer.calculateScore(ride)

        // v6.1.0: Salvar no histórico como PENDING (NÃO registra ganho!)
        // O ganho só será registrado quando RideLifecycleManager detectar COMPLETED
        // PROTEÇÃO: Não salvar corridas simuladas no banco de dados
        val scoreResult = currentScore
        if (scoreResult != null && !ride.isSimulation) {
            withContext(Dispatchers.IO) {
                try {
                    val appDb = AppDatabase.getInstance(applicationContext)
                    val breakdown = scoreResult.criteriaScores.entries.joinToString(" | ") {
                        "${it.value.name}: ${String.format("%.0f", it.value.normalizedScore)} (x${it.value.weight}%)"
                    }
                    val entity = RideHistoryEntity(
                        platform = ride.platform.displayName,
                        rideValue = ride.rideValue,
                        rideDuration = ride.rideDuration,
                        pickupDistance = ride.pickupDistance,
                        dropoffDistance = ride.dropoffDistance,
                        passengerRating = ride.passengerRating,
                        intermediateStops = ride.intermediateStops,
                        pickupNeighborhood = ride.pickupNeighborhood,
                        dropoffNeighborhood = ride.dropoffNeighborhood,
                        score = scoreResult.totalScore,
                        status = "PENDING", // v6.1.0: Status PENDING (não registra ganho)
                        scoreBreakdown = breakdown,
                        criteriaUsed = scoreResult.criteriaScores.size,
                        totalCriteria = weights.totalUsed.let { if (it == 0) 1 else it },
                        hasViolations = scoreResult.thresholdViolations.isNotEmpty()
                    )
                    val rideId = appDb.rideHistoryDao().insert(entity)

                    // v6.1.0: Notificar LifecycleManager sobre a corrida detectada
                    // O LifecycleManager rastreará aceitação/conclusão/cancelamento
                    // e só registrará ganho quando COMPLETED
                    val lifecycleManager = RideAccessibilityService.instance?.lifecycleManager
                    lifecycleManager?.onRideDetected(ride, rideId)

                    // v6.1.0: Notificar AutoPilot para avaliar auto-aceitar/recusar
                    val autoPilot = RideAccessibilityService.instance?.autoPilotEngine
                    autoPilot?.evaluateRide(ride, scoreResult.totalScore, rideId)

                    // REMOVIDO v6.1.0: Auto-import de ganhos imediato
                    // Ganho agora só é registrado pelo RideLifecycleManager.onRideCompleted()

                } catch (e: Exception) {
                    android.util.Log.e("OverlayService", "Erro ao salvar histórico: ${e.message}")
                }
            }
        }

        withContext(Dispatchers.Main) {
            // v5.0.0: Se overlay já visível, remover antes de recriar (evita duplicata)
            if (isOverlayVisible) {
                hideOverlay()
            }
            try {
                createOverlay()
                // v5.0.0: Auto-dismiss configurável (30s padrão)
                startAutoDismissTimer()
            } catch (e: Exception) {
                android.util.Log.e("OverlayService", "Erro ao criar overlay: ${e.message}", e)
            }
        }
    }

    fun hideOverlay() {
        autoDismissJob?.cancel() // v5.0.0: Cancelar timer ao fechar
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {}
        }
        overlayView = null
        isOverlayVisible = false
        naturalOverlayHeight = 0

        // v6.1.1: Notificar lifecycle que overlay foi fechado sem ação do motorista
        val ride = currentRide
        if (ride != null && !ride.isSimulation) {
            com.ngbautoroad.service.RideAccessibilityService.instance?.lifecycleManager?.onOverlayDismissed()
        }

        // v6.0.0: Se era simulação (editor de cards), trazer o app de volta ao foco
        // Isso resolve o bug onde o app ficava minimizado após fechar o card de teste
        if (ride != null && ride.isSimulation) {
            try {
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    intent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                    intent.putExtra("return_to_editor", true)
                    startActivity(intent)
                }
            } catch (e: Exception) {
                android.util.Log.w("OverlayService", "Erro ao trazer app de volta: ${e.message}")
            }
            currentRide = null // Limpar referência de simulação
        }
    }

    // v5.0.0: Auto-dismiss configurável — fecha overlay após X segundos
    private fun startAutoDismissTimer() {
        autoDismissJob?.cancel()
        autoDismissJob = serviceScope.launch {
            val dismissMs = prefsManager.autoDismissSecondsFlow.first() * 1000L
            if (dismissMs > 0) { // 0 = nunca auto-dismiss
                delay(dismissMs)
                hideOverlay()
            }
        }
    }

    private fun createOverlay() {
        val density = resources.displayMetrics.density
        val widthPx = (overlayWidth * density).toInt()

        // Item 4.2: Restaurar posição salva
        // v5.2.0: Removido runBlocking que causava crash - usar posição padrão (0,0)
        // A posição será carregada assincronamente após criação
        val savedX = 0
        val savedY = 0

        val params = WindowManager.LayoutParams(
            widthPx,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX
            y = savedY
        }

        // Usar o Service diretamente como contexto (Theme.kt já protege contra cast para Activity)
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            // Registrar SavedStateRegistryOwner para Compose
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            // Registrar OnBackPressedDispatcherOwner para evitar ClassCastException
            setViewTreeOnBackPressedDispatcherOwner(this@OverlayService)
            setContent {
                com.ngbautoroad.ui.theme.NGBAutoRoadTheme {
                    val ride = currentRide
                    val score = currentScore
                    if (ride != null && score != null) {
                        val shiftState = ShiftManager(applicationContext).loadState()
                        OverlayCard(
                            ride = ride,
                            score = score,
                            galleryCard = currentGalleryCard,
                            fontScale = currentFontScale,
                            goalProgress = shiftState.goalProgress,
                            goalEarned = shiftState.totalEarned,
                            goalTarget = shiftState.goalValue,
                            customLayout = currentCustomLayout,
                            onDismiss = { hideOverlay() },
                            onFontScaleChange = { newScale ->
                                currentFontScale = newScale
                                serviceScope.launch { prefsManager.saveOverlayFontScale(newScale) }
                                updateOverlayContent()
                            },
                            onResize = { deltaX, deltaY ->
                                val screenWidth = resources.displayMetrics.widthPixels
                                val screenHeight = resources.displayMetrics.heightPixels

                                // Capturar altura natural na PRIMEIRA interação
                                if (naturalOverlayHeight == 0) {
                                    val measured = overlayView?.height ?: 0
                                    naturalOverlayHeight = if (measured > 50) measured else (250 * density).toInt()
                                }

                                // Largura: mínimo 100dp, máximo = tela
                                val minW = (100 * density).toInt()
                                val newWidth = (params.width + deltaX.toInt()).coerceIn(minW, screenWidth)
                                params.width = newWidth

                                // Altura: mínimo 60dp, máximo = tela
                                val minH = (60 * density).toInt()
                                val currentH = if (params.height <= 0 || params.height == WindowManager.LayoutParams.WRAP_CONTENT) {
                                    overlayView?.height?.takeIf { it > 50 } ?: naturalOverlayHeight
                                } else {
                                    params.height
                                }
                                val newHeight = (currentH + deltaY.toInt()).coerceIn(minH, screenHeight)
                                params.height = newHeight

                                // Auto-zoom: ajustar fontScale proporcionalmente à altura
                                if (naturalOverlayHeight > 0) {
                                    val heightRatio = newHeight.toFloat() / naturalOverlayHeight.toFloat()
                                    currentFontScale = heightRatio.coerceIn(0.5f, 1.8f)
                                }

                                try {
                                    windowManager?.updateViewLayout(overlayView, params)
                                } catch (_: Exception) {}
                                overlayWidth = (newWidth / density).toInt()
                                serviceScope.launch {
                                    prefsManager.saveOverlayFontScale(currentFontScale)
                                    prefsManager.saveOverlaySize(overlayWidth, (newHeight / density).toInt())
                                }
                                updateOverlayContent()
                            }
                        )
                    }
                }
            }
        }

        // Drag livre (v6.3.4 — pinch-to-zoom removido, usar resize handle em vez disso)
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount == 1) {
                        // Drag livre: mover card
                        var newX = initialX + (event.rawX - touchX).toInt()
                        var newY = initialY + (event.rawY - touchY).toInt()
                        // Respeitar bordas do celular
                        newX = newX.coerceIn(0, screenWidth - params.width)
                        newY = newY.coerceIn(0, screenHeight - (params.height.takeIf { it > 0 } ?: (200 * density).toInt()))
                        params.x = newX
                        params.y = newY
                        windowManager?.updateViewLayout(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // Persistir posição
                    serviceScope.launch {
                        prefsManager.saveOverlayPosition(params.x, params.y)
                    }
                    true
                }
                else -> false
            }
        }

        // Registrar ViewModelStoreOwner para Compose (necessário no Compose 1.5+)
        view.setViewTreeViewModelStoreOwner(this@OverlayService)
        windowManager?.addView(view, params)
        android.util.Log.d("NGB_TESTAR_CARD", "[OVERLAY] View adicionada ao WindowManager com sucesso")
        overlayView = view
        isOverlayVisible = true
    }

    private fun updateOverlayContent() {
        val density = resources.displayMetrics.density
        overlayView?.setContent {
            com.ngbautoroad.ui.theme.NGBAutoRoadTheme {
                val ride = currentRide
                val score = currentScore
                if (ride != null && score != null) {
                    val shiftState = ShiftManager(applicationContext).loadState()
                    OverlayCard(
                        ride = ride,
                        score = score,
                        galleryCard = currentGalleryCard,
                        fontScale = currentFontScale,
                        goalProgress = shiftState.goalProgress,
                        goalEarned = shiftState.totalEarned,
                        goalTarget = shiftState.goalValue,
                        customLayout = currentCustomLayout,
                        onDismiss = { hideOverlay() },
                        onFontScaleChange = { newScale ->
                            currentFontScale = newScale
                            serviceScope.launch { prefsManager.saveOverlayFontScale(newScale) }
                            updateOverlayContent()
                        },
                        onResize = { deltaX, deltaY ->
                            val screenWidth = resources.displayMetrics.widthPixels
                            val screenHeight = resources.displayMetrics.heightPixels
                            val view = overlayView ?: return@OverlayCard
                            val lp = view.layoutParams as? WindowManager.LayoutParams ?: return@OverlayCard

                            // Capturar altura natural na PRIMEIRA interação
                            if (naturalOverlayHeight == 0) {
                                val measured = view.height
                                naturalOverlayHeight = if (measured > 50) measured else (250 * density).toInt()
                            }

                            // Largura: mínimo 100dp, máximo = tela
                            val minW = (100 * density).toInt()
                            val newWidth = (lp.width + deltaX.toInt()).coerceIn(minW, screenWidth)
                            lp.width = newWidth

                            // Altura: mínimo 60dp, máximo = tela
                            val minH = (60 * density).toInt()
                            val currentH = if (lp.height <= 0 || lp.height == WindowManager.LayoutParams.WRAP_CONTENT) {
                                view.height.takeIf { it > 50 } ?: naturalOverlayHeight
                            } else {
                                lp.height
                            }
                            val newHeight = (currentH + deltaY.toInt()).coerceIn(minH, screenHeight)
                            lp.height = newHeight

                            // Auto-zoom: ajustar fontScale proporcionalmente à altura
                            if (naturalOverlayHeight > 0) {
                                val heightRatio = newHeight.toFloat() / naturalOverlayHeight.toFloat()
                                currentFontScale = heightRatio.coerceIn(0.5f, 1.8f)
                            }

                            try {
                                windowManager?.updateViewLayout(view, lp)
                            } catch (_: Exception) {}
                            overlayWidth = (newWidth / density).toInt()
                            serviceScope.launch {
                                prefsManager.saveOverlayFontScale(currentFontScale)
                                prefsManager.saveOverlaySize(overlayWidth, (newHeight / density).toInt())
                            }
                            updateOverlayContent()
                        }
                    )
                }
            }
        }
    }

    fun resizeOverlay(newWidth: Int) {
        overlayWidth = newWidth
        val density = resources.displayMetrics.density
        val widthPx = (newWidth * density).toInt()
        overlayView?.let { view ->
            val params = view.layoutParams as? WindowManager.LayoutParams ?: return
            params.width = widthPx
            // Preservar altura atual (não zerar)
            val currentHeightDp = if (params.height > 0) (params.height / density).toInt() else 0
            try {
                windowManager?.updateViewLayout(view, params)
                serviceScope.launch { prefsManager.saveOverlaySize(newWidth, currentHeightDp) }
            } catch (_: Exception) {}
        }
    }

    private fun createNotification(): Notification {
        val channelId = "overlay_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "NGB AutoRoad - Copiloto",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificação persistente do serviço de monitoramento"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        // Intent para abrir o app ao tocar na notificação
        val openIntent = packageManager.getLaunchIntentForPackage(packageName)
        val openPendingIntent = android.app.PendingIntent.getActivity(
            this, 0, openIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        // Intent para desligar o serviço
        val stopIntent = Intent(this, OverlayService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = android.app.PendingIntent.getService(
            this, 1, stopIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("NGB AutoRoad — Copiloto")
            .setContentText("Monitorando corridas. Toque para abrir.")
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "DESLIGAR",
                stopPendingIntent
            )
            .build()
    }
}
