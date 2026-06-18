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
import com.ngbautoroad.R
import com.ngbautoroad.data.model.*
import com.ngbautoroad.data.prefs.PrefsManager
import com.ngbautoroad.domain.RideScorer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import com.ngbautoroad.data.db.AppDatabase
import com.ngbautoroad.data.db.RideHistoryEntity

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
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var isOverlayVisible = false
    private var currentRide: RideData? = null
    private var currentScore: RideScore? = null
    private var currentGalleryCard: CardGallery.GalleryCard? = null
    private var overlayWidth = 320
    private var currentFontScale = 1.0f

    // Supressão de duplicatas (item 2.2)
    private var lastRideHash: Int = 0
    private var lastRideTime: Long = 0L
    private val DUPLICATE_WINDOW_MS = 3000L // 3 segundos

    private lateinit var prefsManager: PrefsManager
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        const val NOTIFICATION_ID = 1001
        var onRideDetected: ((RideData) -> Unit)? = null

        // Referência ao serviço ativo para resize ao vivo
        private var instance: OverlayService? = null

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

        // Limpar referência ao destruir
        lifecycleRegistry.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                if (instance === this@OverlayService) instance = null
            }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        return START_STICKY
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        hideOverlay()
        onRideDetected = null
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun showOverlay(ride: RideData) {
        currentRide = ride

        // Ler critérios reais do DataStore
        val weights = prefsManager.criteriaWeightsFlow.first()
        val thresholds = prefsManager.driverThresholdsFlow.first()
        val activeSlot = prefsManager.activeCardSlotFlow.first()
        currentFontScale = prefsManager.overlayFontScaleFlow.first()
        overlayWidth = prefsManager.overlayWidthFlow.first()

        // Obter card da galeria baseado no slot ativo (tipo correto: GalleryCard)
        currentGalleryCard = when (activeSlot) {
            1 -> CardGallery.getById(prefsManager.card1ModelIdFlow.first())
            2 -> CardGallery.getById(prefsManager.card2ModelIdFlow.first())
            else -> null
        }

        // Item 1.1: Carregar bairros bloqueados do DataStore
        val blockedPickup = prefsManager.blockedPickupFlow.first()
            .map { (name, penalty) -> BlockedNeighborhood(name, NeighborhoodType.PICKUP, penalty) }
        val blockedDropoff = prefsManager.blockedDropoffFlow.first()
            .map { (name, penalty) -> BlockedNeighborhood(name, NeighborhoodType.DROPOFF, penalty) }
        val blockedNeighborhoods = blockedPickup + blockedDropoff

        // Calcular score com critérios reais + bairros bloqueados
        val scorer = RideScorer(
            weights = weights,
            driverThresholds = thresholds,
            blockedNeighborhoods = blockedNeighborhoods
        )
        currentScore = scorer.calculateScore(ride)

        // Salvar no histórico com scoreBreakdown e criteriaUsed (item 5.2, 1.3)
        val scoreResult = currentScore
        if (scoreResult != null) {
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
                        status = "REFUSED", // Status inicial; pode ser atualizado depois
                        scoreBreakdown = breakdown,
                        criteriaUsed = scoreResult.criteriaScores.size,
                        totalCriteria = weights.totalUsed.let { if (it == 0) 1 else it },
                        hasViolations = scoreResult.thresholdViolations.isNotEmpty()
                    )
                    val rideId = appDb.rideHistoryDao().insert(entity)

                    // Auto-import de ganhos (item 3.2)
                    val autoImport = prefsManager.autoImportEarningsFlow.first()
                    if (autoImport && ride.rideValue > 0) {
                        val financeDb = com.ngbautoroad.data.db.FinanceDatabase.getInstance(applicationContext)
                        val alreadyImported = financeDb.earningDao().countAutoImportedByRideId(rideId)
                        if (alreadyImported == 0) {
                            val earning = com.ngbautoroad.data.db.EarningEntity(
                                platform = ride.platform.displayName,
                                amount = ride.rideValue,
                                distance = ride.dropoffDistance,
                                duration = ride.rideDuration.toInt(),
                                ridesCount = 1,
                                description = "Auto-import",
                                period = "DIA",
                                isAutoImported = true,
                                rideHistoryId = rideId
                            )
                            financeDb.earningDao().insert(earning)
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        withContext(Dispatchers.Main) {
            if (isOverlayVisible) {
                updateOverlayContent()
            } else {
                createOverlay()
            }
        }
    }

    fun hideOverlay() {
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {}
        }
        overlayView = null
        isOverlayVisible = false
    }

    private fun createOverlay() {
        val density = resources.displayMetrics.density
        val widthPx = (overlayWidth * density).toInt()

        // Item 4.2: Restaurar posição salva
        val savedX = runBlocking { prefsManager.overlayPositionXFlow.first() }
        val savedY = runBlocking { prefsManager.overlayPositionYFlow.first() }

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

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            // Registrar SavedStateRegistryOwner para Compose
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setContent {
                com.ngbautoroad.ui.theme.NGBAutoRoadTheme {
                    val ride = currentRide
                    val score = currentScore
                    if (ride != null && score != null) {
                        OverlayCard(
                            ride = ride,
                            score = score,
                            galleryCard = currentGalleryCard,
                            fontScale = currentFontScale,
                            onDismiss = { hideOverlay() }
                        )
                    }
                }
            }
        }

        // Drag para mover (item 4.2: salvar posição ao soltar)
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - touchX).toInt()
                    params.y = initialY + (event.rawY - touchY).toInt()
                    windowManager?.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // Persistir posição (item 4.2)
                    serviceScope.launch {
                        prefsManager.saveOverlayPosition(params.x, params.y)
                    }
                    true
                }
                else -> false
            }
        }

        windowManager?.addView(view, params)
        overlayView = view
        isOverlayVisible = true
    }

    private fun updateOverlayContent() {
        overlayView?.setContent {
            com.ngbautoroad.ui.theme.NGBAutoRoadTheme {
                val ride = currentRide
                val score = currentScore
                if (ride != null && score != null) {
                    OverlayCard(
                        ride = ride,
                        score = score,
                        galleryCard = currentGalleryCard,
                        fontScale = currentFontScale,
                        onDismiss = { hideOverlay() }
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
            try {
                windowManager?.updateViewLayout(view, params)
                serviceScope.launch { prefsManager.saveOverlaySize(newWidth, 0) }
            } catch (_: Exception) {}
        }
    }

    private fun createNotification(): Notification {
        val channelId = "overlay_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "NGB AutoRoad Overlay",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("NGB AutoRoad")
            .setContentText("Monitorando corridas...")
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
