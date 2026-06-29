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
import com.ngbautoroad.domain.AdaptiveScoringEngine
import com.ngbautoroad.domain.RideScorer
import com.ngbautoroad.domain.ShiftManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import com.ngbautoroad.data.db.AppDatabase
import com.ngbautoroad.data.db.RideHistoryEntity
import com.ngbautoroad.domain.TelemetryLogger
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

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
    private var overlayWidth = 320
    private var overlayHeight = 0  // 0 = WRAP_CONTENT; >0 = saved height in dp
    private var currentFontScale = 1.0f
    private var naturalOverlayHeight = 0

    // Supressão de duplicatas (item 2.2)
    private var lastRideHash: Int = 0
    private var lastRideTime: Long = 0L
    private val DUPLICATE_WINDOW_MS = 30000L // v6.9.14: 30 segundos (era 3s)

    // v7.1.0: Debounce inteligente por hash — só cancela se for a MESMA corrida aguardando destino
    private var debounceJob: Job? = null
    private var pendingRideHash: Int = 0

    private lateinit var prefsManager: PrefsManager
    private val telemetry by lazy { TelemetryLogger.getInstance(applicationContext) }
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    // v5.0.0: Auto-dismiss timer
    private var autoDismissJob: Job? = null

    companion object {
        const val NOTIFICATION_ID = 1001
        const val RIDE_NOTIFICATION_ID = 1002
        const val RIDE_CHANNEL_ID = "ngb_ride_alerts"
        const val ACTION_STOP = "com.ngbautoroad.STOP_SERVICE"
        var onRideDetected: ((RideData) -> Unit)? = null
        var onStealthModeChanged: ((Boolean) -> Unit)? = null
        // v6.9.8: Callback para fechar overlay quando corrida é aceita
        // O serviço continua ativo — novas ofertas reais abrem novo overlay
        var onRideAccepted: (() -> Unit)? = null

        // Referência ao serviço ativo para resize ao vivo e MemoryMonitor
        var instance: OverlayService? = null
            private set

        fun start(context: Context, rideData: RideData? = null) {
            val intent = Intent(context, OverlayService::class.java).apply {
                if (rideData != null) {
                    try {
                        val json = kotlinx.serialization.json.Json.encodeToString(rideData)
                        putExtra("EXTRA_RIDE_DATA_JSON", json)
                    } catch (e: Exception) {
                        com.ngbautoroad.domain.TelemetryLogger.getInstance(context.applicationContext).log(
                            com.ngbautoroad.domain.TelemetryLogger.Category.LIFECYCLE,
                            com.ngbautoroad.domain.TelemetryLogger.Level.ERROR,
                            "OverlayService start crash: ${e.message}"
                        )
                    }
                }
            }
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
        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            android.util.Log.e("NGB_OVERLAY", "CRASH NO OVERLAY: ${exception.message}", exception)
            com.ngbautoroad.domain.TelemetryLogger.getInstance(applicationContext).log(
                com.ngbautoroad.domain.TelemetryLogger.Category.LIFECYCLE, 
                com.ngbautoroad.domain.TelemetryLogger.Level.ERROR, 
                "Overlay Crash: ${exception.message}"
            )
            oldHandler?.uncaughtException(thread, exception)
        }

        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        prefsManager = PrefsManager(applicationContext)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        instance = this

        // Carregar configurações iniciais
        serviceScope.launch {
            overlayWidth = prefsManager.overlayWidthFlow.first()
            overlayHeight = prefsManager.overlayHeightFlow.first()
            currentFontScale = prefsManager.overlayFontScaleFlow.first()
        }

        // v7.1.0: Debounce inteligente — delega para queueRide()
        onRideDetected = { ride -> queueRide(ride) }

        // v6.9.16: Se o serviço for reiniciado e houver uma corrida ativa em fase PENDING, exibi-la imediatamente
        val activeManager = RideAccessibilityService.instance?.lifecycleManager
        val activeRide = activeManager?.getCurrentRide()
        if (activeRide != null && activeManager.getCurrentPhase() == RideLifecycleManager.RidePhase.PENDING) {
            android.util.Log.i("NGB_OVERLAY", "[OnCreate] Restaurando overlay para corrida ativa pendente...")
            serviceScope.launch {
                showOverlay(activeRide)
            }
        }

        // v6.9.8: Fechar overlay quando corrida é aceita (não recalcular com dados da corrida ativa)
        onRideAccepted = {
            serviceScope.launch {
                hideOverlay()
                android.util.Log.d("NGB_OVERLAY", "Overlay fechado após aceitação — serviço continua ativo para novas ofertas")
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

        // Processar corrida passada pelo Intent (caso o serviço tenha sido auto-iniciado agora)
        val json = intent?.getStringExtra("EXTRA_RIDE_DATA_JSON")
        if (!json.isNullOrBlank()) {
            try {
                val ride = kotlinx.serialization.json.Json.decodeFromString<RideData>(json)
                android.util.Log.i("NGB_OVERLAY", "Corrida recebida via Intent: R$ ${ride.rideValue}")
                // v7.1.0: Chama queueRide diretamente — evita race condition do callback null
                serviceScope.launch { queueRide(ride) }
            } catch (e: Exception) {
                android.util.Log.e("NGB_OVERLAY", "Erro ao deserializar RideData do Intent: ${e.message}")
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        hideOverlay()
        onRideDetected = null
        onStealthModeChanged = null
        onRideAccepted = null
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

    // =========================================================================
    // v7.1.0: queueRide — Debounce inteligente + deduplicação
    //
    // Comportamento:
    //   - MESMA corrida (mesmo hash, destino ainda vazio): adia 600ms aguardando
    //     o bairro de destino carregar antes de exibir (evita overlay incompleto)
    //   - MESMA corrida com destino já presente: exibe imediatamente (100ms mínimo)
    //   - CORRIDA DIFERENTE: cancela debounce anterior e exibe imediatamente
    //   - DUPLICATA confirmada (30s window): ignora silenciosamente
    // =========================================================================
    private fun queueRide(ride: RideData) {
        // Deduplicação por hash (30s window)
        // v7.1.2: rideType REMOVIDO do hash — o parser pode atualizá-lo entre eventos
        // (ex: UBER_X → UBER_COMFORT ao ler o badge), gerando hash diferente e re-exibindo o overlay.
        // Hash estável: plataforma + valor + bairro destino (suficiente para identificar a oferta)
        val rideHash = "${ride.platform}_${ride.rideValue}_${ride.dropoffNeighborhood}".hashCode()
        val now = System.currentTimeMillis()
        if (!ride.isSimulation && rideHash == lastRideHash && (now - lastRideTime) < DUPLICATE_WINDOW_MS) {
            android.util.Log.d("NGB_OVERLAY", "[Queue] Duplicata ignorada (hash=$rideHash, Δt=${now - lastRideTime}ms)")
            return
        }

        // v7.2.1: Simulações sempre tratadas como corrida nova — permite repetir sem reiniciar o serviço
        val isNewRide = ride.isSimulation || rideHash != pendingRideHash

        if (isNewRide) {
            // Corrida diferente: cancela debounce anterior e inicia imediatamente
            debounceJob?.cancel()
            pendingRideHash = rideHash
            val delay = if (ride.dropoffNeighborhood.isBlank()) 400L else 0L
            debounceJob = serviceScope.launch {
                if (delay > 0) kotlinx.coroutines.delay(delay)
                if (!ride.isSimulation) { lastRideHash = rideHash; lastRideTime = System.currentTimeMillis() }
                showOverlay(ride)
            }
            android.util.Log.d("NGB_OVERLAY", "[Queue] Nova corrida R$${ride.rideValue} | delay=${delay}ms")
            telemetry.overlay("Queue: nova corrida R$${ride.rideValue} plataforma=${ride.platform.displayName} delay=${delay}ms")
        } else {
            // Mesma corrida chegando de novo (destino atualizou ou releitura do AS):
            // só atualiza se o destino ficou mais completo (tem bairro agora E job ainda está aguardando)
            val hasNeighborhoodUpdate = ride.dropoffNeighborhood.isNotBlank() && (debounceJob?.isActive == true)
            if (hasNeighborhoodUpdate) {
                debounceJob?.cancel()
                pendingRideHash = rideHash
                debounceJob = serviceScope.launch {
                    kotlinx.coroutines.delay(100L) // mínimo para evitar flash
                    if (!ride.isSimulation) { lastRideHash = rideHash; lastRideTime = System.currentTimeMillis() }
                    showOverlay(ride)
                }
                android.util.Log.d("NGB_OVERLAY", "[Queue] Mesma corrida com destino atualizado — exibindo")
            } else {
                android.util.Log.d("NGB_OVERLAY", "[Queue] Releitura da mesma corrida pendente — ignorando")
            }
        }
    }

    private suspend fun showOverlay(ride: RideData) {
      try { // v7.1.0: try-catch global — qualquer exceção é logada e não mata silenciosamente o overlay
        currentRide = ride

        // Ler critérios reais do DataStore
        val weights = prefsManager.criteriaWeightsFlow.first()
        val thresholds = prefsManager.driverThresholdsFlow.first()
        currentFontScale = prefsManager.overlayFontScaleFlow.first()
        overlayWidth = prefsManager.overlayWidthFlow.first()
        overlayHeight = prefsManager.overlayHeightFlow.first()

        // Cards configuration loaded (if needed in the future)

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

        // v6.3.7: Usar thresholds adaptativos (EWMA) se calibrados
        val adaptiveEngine = AdaptiveScoringEngine(this)
        val adaptiveThresholds = adaptiveEngine.getAdaptiveThresholds()

        // v6.3.9: Buscar custo/km do veículo ativo para score de lucro líquido
        val vehicleCostPerKm = try {
            val finDb = com.ngbautoroad.data.db.FinanceDatabase.getInstance(this)
            finDb.vehicleProfileDao().getActiveVehicleSync()?.costPerKm ?: 0.0
        } catch (_: Exception) { 0.0 }

        // Calcular score com critérios reais + bairros bloqueados + thresholds adaptativos + custo/km
        val scorer = RideScorer(
            weights = weights,
            driverThresholds = thresholds,
            blockedNeighborhoods = blockedNeighborhoods,
            thresholds = adaptiveThresholds,
            costPerKm = vehicleCostPerKm
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
                    lifecycleManager?.onRideDetected(ride, rideId, scoreResult.totalScore)

                    // v6.9.9: Iniciar UserActionDetector para detectar cliques do motorista
                    val service = RideAccessibilityService.instance
                    if (service?.userActionDetector == null) {
                        service?.initUserActionDetector()
                    }
                    service?.userActionDetector?.startMonitoring(ride.platform)

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

        // v7.2.0: Pipe Mode — ativa FLAG_NOT_TOUCHABLE quando AutoPilot está no modo de aceitar.
        // Com FLAG_NOT_TOUCHABLE, toques do usuário passam DIRETO para o app alvo sem
        // receber FLAG_WINDOW_IS_OBSCURED — remove o vetor de detecção mais trivial.
        // Quando AutoPilot está OFF ou REFUSE_ONLY, o overlay mantém interatividade normal.
        val autoPilotMode = withContext(Dispatchers.IO) {
            kotlinx.coroutines.withTimeoutOrNull(500L) {
                prefsManager.autoPilotModeFlow.first()
            } ?: AutoPilotEngine.MODE_OFF
        }
        // v7.2.1: Simulações NUNCA ativam Pipe Mode — motorista precisa interagir com o overlay de teste
        val isPipeMode = !ride.isSimulation && autoPilotMode in listOf(
            AutoPilotEngine.MODE_ACCEPT, AutoPilotEngine.MODE_ACCEPT_ONLY,
            AutoPilotEngine.MODE_FULL, AutoPilotEngine.MODE_BOTH
        )
        telemetry.overlay("showOverlay: R$${ride.rideValue} score=${currentScore?.totalScore?.toInt()} pipe=$isPipeMode plataforma=${ride.platform.displayName}")

        // v6.9.9: Notificação de corrida para Android Auto e tela de bloqueio
        val androidAutoEnabled = prefsManager.androidAutoEnabledFlow.first()
        val androidAutoAutoDetect = prefsManager.androidAutoAutoDetectFlow.first()
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? android.app.UiModeManager
        val isCarMode = uiModeManager?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_CAR
        if (androidAutoEnabled || (androidAutoAutoDetect && isCarMode)) {
            showRideNotification(ride, currentScore)
        }

        withContext(Dispatchers.Main) {
            // v5.0.0: Se overlay já visível, remover antes de recriar (evita duplicata)
            if (isOverlayVisible) {
                hideOverlay(isReplacing = true)
            }
            try {
                // v7.2.0: isPipeMode passado para aplicar FLAG_NOT_TOUCHABLE quando AutoPilot ativo
                createOverlay(isPipeMode = isPipeMode)
                // v5.0.0: Auto-dismiss configurável (30s padrão)
                startAutoDismissTimer()
            } catch (e: Exception) {
                android.util.Log.e("OverlayService", "Erro ao criar overlay: ${e.message}", e)
            }
        }
      } catch (e: Exception) {
          // v7.1.0: Catch global — garante que falhas em DataStore/DB/Score não matam o overlay silenciosamente
          android.util.Log.e("NGB_OVERLAY", "[showOverlay] ERRO CRÍTICO — overlay não exibido: ${e.message}", e)
          telemetry.log(
              com.ngbautoroad.domain.TelemetryLogger.Category.LIFECYCLE,
              com.ngbautoroad.domain.TelemetryLogger.Level.ERROR,
              "showOverlay FALHOU: ${e.message}"
          )
      }
    }

    fun hideOverlay(isReplacing: Boolean = false) {
        autoDismissJob?.cancel() // v5.0.0: Cancelar timer ao fechar
        dismissRideNotification() // v7.1.1: Limpar notificação de corrida ao fechar overlay
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
        if (!isReplacing && ride != null && !ride.isSimulation) {
            com.ngbautoroad.service.RideAccessibilityService.instance?.lifecycleManager?.onOverlayDismissed()
        }

        // v6.0.0: Se era simulação (editor de cards), trazer o app de volta ao foco
        // Isso resolve o bug onde o app ficava minimizado após fechar o card de teste
        if (!isReplacing && ride != null && ride.isSimulation) {
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

    // v7.3.0: Auto-dismiss inteligente por plataforma
    // Cada app mantém o card na tela por um tempo diferente:
    //   Uber: ~15s | 99: ~15s | inDrive: sem timer fixo (bidding)
    // O overlay NGB deve permanecer visível pelo menos até o card da plataforma expirar,
    // para que o motorista tenha a referência do score durante toda a janela de decisão.
    private fun startAutoDismissTimer() {
        autoDismissJob?.cancel()
        autoDismissJob = serviceScope.launch {
            try {
                val userDismissSeconds = kotlinx.coroutines.withTimeoutOrNull(3000L) {
                    prefsManager.autoDismissSecondsFlow.first()
                } ?: 30

                // Tempo mínimo por plataforma (em segundos) — baseado no timer real de cada app
                val platformMinSeconds = when (currentRide?.platform) {
                    com.ngbautoroad.data.model.Platform.UBER -> 10        // Uber: cards 11-16s, fechar 1s antes
                    com.ngbautoroad.data.model.Platform.NINETY_NINE -> 14  // 99: cards ~15s, fechar 1s antes
                    com.ngbautoroad.data.model.Platform.INDRIVE -> 25      // inDrive: sem timer fixo
                    else -> 12
                }

                // Usar o MAIOR entre: config do motorista e mínimo da plataforma
                val effectiveSeconds = if (userDismissSeconds == 0) 0 else maxOf(userDismissSeconds, platformMinSeconds)
                val dismissMs = if (effectiveSeconds > 0) effectiveSeconds * 1000L else 0L

                val platformName = currentRide?.platform?.displayName ?: "?"
                android.util.Log.d("NGB_OVERLAY", "[AutoDismiss] $platformName: ${effectiveSeconds}s (user=${userDismissSeconds}s, plataforma min=${platformMinSeconds}s)")

                if (dismissMs > 0) {
                    delay(dismissMs)
                    android.util.Log.d("NGB_OVERLAY", "[AutoDismiss] Fechando overlay após ${effectiveSeconds}s")
                    telemetry.overlay("AutoDismiss: fechando após ${effectiveSeconds}s plataforma=$platformName")
                    hideOverlay()
                } else {
                    android.util.Log.d("NGB_OVERLAY", "[AutoDismiss] Desativado (0s configurado)")
                }
            } catch (e: Exception) {
                android.util.Log.e("NGB_OVERLAY", "[AutoDismiss] Erro no timer: ${e.message}")
            }
        }
    }

    /**
     * v7.2.0: Parâmetro [isPipeMode].
     *
     * Pipe Mode (FLAG_NOT_TOUCHABLE):
     *   Quando o AutoPilot está no modo aceitar (ACCEPT / FULL / BOTH / ACCEPT_ONLY),
     *   o overlay é declarado como não-tocável. Toques do usuário passam diretamente
     *   para o app alvo SEM receber FLAG_WINDOW_IS_OBSCURED — eliminando o vetor
     *   de detecção mais básico usado por Uber/99/inDrive.
     *   O AutoPilot usa dispatchGesture (AccessibilityService) que não carrega essa flag.
     *
     * Modo normal (sem FLAG_NOT_TOUCHABLE):
     *   Overlay interativo — motorista pode arrastar e fechar manualmente.
     *   Usado quando AutoPilot está OFF, REFUSE ou REFUSE_ONLY.
     *
     * @param isPipeMode true = FLAG_NOT_TOUCHABLE ativado, sem touch listener
     */
    private fun createOverlay(isPipeMode: Boolean = false) {
        val density = resources.displayMetrics.density
        val widthPx = (overlayWidth * density).toInt()

        // Item 4.2: Restaurar posição salva
        // v5.2.0: Removido runBlocking que causava crash - usar posição padrão (0,0)
        // A posição será carregada assincronamente após criação
        val savedX = 0
        val savedY = 0

        // v7.2.0: Flags condicionais por modo
        val overlayFlags = if (isPipeMode) {
            android.util.Log.i("NGB_OVERLAY", "[PipeMode] ATIVADO — FLAG_NOT_TOUCHABLE aplicado")
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }

        val heightParam = WindowManager.LayoutParams.WRAP_CONTENT
        val params = WindowManager.LayoutParams(
            widthPx,
            heightParam,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            overlayFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX
            y = savedY
            alpha = 0f // Inicie o card invisível até o X/Y carregar
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
                        // val shiftState = ShiftManager(applicationContext).loadState() // (Removed because it was unused)
                        val showScore by prefsManager.overlayShowScoreFlow.collectAsState(initial = true)
                        val showValuePerKm by prefsManager.overlayShowValuePerKmFlow.collectAsState(initial = true)
                        val showValuePerHour by prefsManager.overlayShowValuePerHourFlow.collectAsState(initial = true)
                        val showRideValue by prefsManager.overlayShowRideValueFlow.collectAsState(initial = true)
                        val showDuration by prefsManager.overlayShowDurationFlow.collectAsState(initial = true)
                        val showTotalKm by prefsManager.overlayShowTotalKmFlow.collectAsState(initial = true)
                        val showNeighborhoods by prefsManager.overlayShowNeighborhoodsFlow.collectAsState(initial = true)
                        val overlayCardType by prefsManager.overlayCardTypeFlow.collectAsState(initial = "STANDARD")
                        
                        val isPinned by prefsManager.overlayPinnedFlow.collectAsState(initial = false)

                        LaunchedEffect(isPinned) {
                            if (isPinned) {
                                autoDismissJob?.cancel()
                            } else {
                                startAutoDismissTimer()
                            }
                        }

                        OverlayCard(
                            ride = ride,
                            score = score,
                            fontScale = currentFontScale,
                            cardType = overlayCardType,
                            showScore = showScore,
                            showValuePerKm = showValuePerKm,
                            showValuePerHour = showValuePerHour,
                            showRideValue = showRideValue,
                            showDuration = showDuration,
                            showTotalKm = showTotalKm,
                            showNeighborhoods = showNeighborhoods,
                            onDismiss = { hideOverlay() },
                            onDrag = { deltaX, deltaY ->
                                val screenWidth = resources.displayMetrics.widthPixels
                                val screenHeight = resources.displayMetrics.heightPixels

                                var newX = params.x + deltaX.toInt()
                                var newY = params.y + deltaY.toInt()

                                // fix: params.width e params.height podem ser negativos (WRAP_CONTENT = -2)
                                // usar naturalOverlayHeight medido ou fallback seguro de 200dp
                                val safeWidth = if (params.width > 0) params.width else (overlayWidth * density).toInt()
                                val safeHeight = if (naturalOverlayHeight > 0) naturalOverlayHeight
                                                 else if (params.height > 0) params.height
                                                 else (200 * density).toInt()

                                val maxX = (screenWidth - safeWidth).coerceAtLeast(0)
                                val maxY = (screenHeight - safeHeight).coerceAtLeast(0)

                                newX = newX.coerceIn(0, maxX)
                                newY = newY.coerceIn(0, maxY)

                                params.x = newX
                                params.y = newY

                                try {
                                    windowManager?.updateViewLayout(overlayView, params)
                                } catch (_: Exception) {}

                                // v7.2.1: Salvar posição via onDrag (Compose consome os toques
                                // antes do setOnTouchListener — esse é o único ponto que funciona)
                                serviceScope.launch {
                                    prefsManager.saveOverlayPosition(newX, newY)
                                }
                            }
                        )
                    }
                }
            }
        }

        // v7.2.0: Touch listener só em modo normal (não em Pipe Mode).
        // Em Pipe Mode (FLAG_NOT_TOUCHABLE), eventos de toque passam direto para o
        // app alvo — o listener nunca seria chamado de qualquer forma.
        // Drag livre (v6.3.4 — pinch-to-zoom removido, usar resize handle em vez disso)
        if (!isPipeMode) {
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

                            // fix: params.width/height podem ser negativos (WRAP_CONTENT = -2)
                            val safeWidth = if (params.width > 0) params.width else (overlayWidth * density).toInt()
                            val safeHeight = if (naturalOverlayHeight > 0) naturalOverlayHeight
                                             else if (params.height > 0) params.height
                                             else (200 * density).toInt()

                            val maxX = (screenWidth - safeWidth).coerceAtLeast(0)
                            val maxY = (screenHeight - safeHeight).coerceAtLeast(0)

                            newX = newX.coerceIn(0, maxX)
                            newY = newY.coerceIn(0, maxY)
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
        }

        // Registrar ViewModelStoreOwner para Compose (necessário no Compose 1.5+)
        view.setViewTreeViewModelStoreOwner(this@OverlayService)

        // fix: carregar posição ANTES de addView para evitar flash no centro (0,0)
        // Usa runBlocking com timeout curto (200ms) — não bloqueia UI perceptivelmente
        // v7.2.1: Default y=8dp do topo (não centro) quando sem posição salva
        val defaultY = (8 * resources.displayMetrics.density).toInt()
        try {
            val posX = kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.withTimeoutOrNull(200L) { prefsManager.overlayPositionXFlow.first() } ?: 0
            }
            val posY = kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.withTimeoutOrNull(200L) { prefsManager.overlayPositionYFlow.first() } ?: defaultY
            }
            params.x = posX
            params.y = posY
            params.alpha = 1f // visível já na posição correta, sem flash
        } catch (e: Exception) {
            android.util.Log.w("OverlayService", "Posição não carregada, usando topo: ${e.message}")
            params.x = 0
            params.y = 0
            params.alpha = 1f
        }

        windowManager?.addView(view, params)
        android.util.Log.d("NGB_TESTAR_CARD", "[OVERLAY] View adicionada ao WindowManager com sucesso")
        telemetry.overlay("createOverlay: view adicionada pipe=$isPipeMode pos=(${params.x},${params.y}) width=${overlayWidth}dp")
        overlayView = view
        isOverlayVisible = true

        // v7.1.1: Medir altura real do card após primeiro layout — corrige drag bounds
        // naturalOverlayHeight era sempre 0 porque nunca era medido, forçando fallback de 200dp
        view.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (view.height > 0) {
                    naturalOverlayHeight = view.height
                    view.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    android.util.Log.d("NGB_OVERLAY", "[createOverlay] naturalOverlayHeight = ${naturalOverlayHeight}px")
                }
            }
        })

        // Fallback assíncrono: confirmar posição após render (garante precisão)
        serviceScope.launch {
            try {
                val posX = prefsManager.overlayPositionXFlow.first()
                val posY = prefsManager.overlayPositionYFlow.first()
                withContext(Dispatchers.Main) {
                    // Só atualizar se a view ainda for a mesma — proteção contra rajada de corridas
                    if (overlayView == view) {
                        params.x = posX
                        params.y = posY
                        try {
                            windowManager?.updateViewLayout(view, params)
                        } catch (e: Exception) {
                            android.util.Log.e("OverlayService", "Erro ao atualizar layout: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("OverlayService", "Fallback posição falhou: ${e.message}")
            }
        }
    }

    /**
     * v7.2.0: Atualiza o Pipe Mode do overlay em tempo real sem recriar a view.
     *
     * Chamado externamente quando o motorista muda o modo do AutoPilot nas configurações
     * enquanto o overlay já está visível. Usa updateViewLayout para aplicar as novas
     * flags sem flickering.
     *
     * @param isPipe true = ativar FLAG_NOT_TOUCHABLE (Pipe Mode), false = remover
     */
    fun updatePipeMode(isPipe: Boolean) {
        val view = overlayView ?: return
        val layoutParams = view.layoutParams as? WindowManager.LayoutParams ?: return
        val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        layoutParams.flags = if (isPipe) {
            baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            baseFlags
        }
        try {
            windowManager?.updateViewLayout(view, layoutParams)
            android.util.Log.i("NGB_OVERLAY", "[PipeMode] ${if (isPipe) "ATIVADO" else "DESATIVADO"} em tempo real")
            telemetry.overlay("PipeMode: ${if (isPipe) "ATIVADO" else "DESATIVADO"} em tempo real")
        } catch (e: Exception) {
            android.util.Log.w("NGB_OVERLAY", "[PipeMode] Erro ao atualizar flags: ${e.message}")
        }
    }

    // v7.1.1: updateOverlayContent() REMOVIDO — era código morto (nunca chamado).
    // O conteúdo do overlay é definido uma vez no createOverlay() e atualizado via
    // recomposição automática do Compose (currentRide/currentScore são lidos diretamente).

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

    // =========================================================================
    // v6.9.9: Notificação de corrida (visível no Android Auto e tela de bloqueio)
    // =========================================================================

    private fun showRideNotification(ride: RideData, score: com.ngbautoroad.data.model.RideScore?) {
        try {
            // Criar canal de notificação para corridas (alta prioridade para Android Auto)
            val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    RIDE_CHANNEL_ID,
                    "Alertas de Corrida",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notificações de novas corridas detectadas"
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                notifManager.createNotificationChannel(channel)
            }

            val scoreText = if (score != null) "%.0f pts".format(score.totalScore) else "--"
            val valueText = "R$ %.2f".format(ride.rideValue)
            val platformName = ride.platform.displayName

            // Resumo compacto para Android Auto
            val title = "🚗 $platformName — $valueText ($scoreText)"
            val body = buildString {
                if (ride.pickupNeighborhood.isNotBlank()) append("↑ ${ride.pickupNeighborhood}")
                if (ride.dropoffNeighborhood.isNotBlank()) append(" → ${ride.dropoffNeighborhood}")
                if (ride.pickupDistance > 0) append(" | ${"%.1f".format(ride.pickupDistance)}km")
                if (ride.rideDuration > 0) append(" | ${ride.rideDuration}min")
            }

            val notification = NotificationCompat.Builder(this, RIDE_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(android.R.drawable.ic_menu_directions)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE) // Aparece no Android Auto
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setTimeoutAfter(30_000) // Auto-dismiss após 30s
                .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
                .build()

            notifManager.notify(RIDE_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            android.util.Log.e("OverlayService", "Erro ao mostrar notificação de corrida: ${e.message}")
        }
    }

    private fun dismissRideNotification() {
        try {
            val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notifManager.cancel(RIDE_NOTIFICATION_ID)
        } catch (_: Exception) {}
    }
}
