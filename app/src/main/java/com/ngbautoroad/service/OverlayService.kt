package com.ngbautoroad.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ngbautoroad.NGBAutoRoadApp
import com.ngbautoroad.R
import com.ngbautoroad.data.model.*
import com.ngbautoroad.data.prefs.PrefsManager
import com.ngbautoroad.domain.RideScorer
import com.ngbautoroad.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class OverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private lateinit var prefsManager: PrefsManager
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var currentRide: RideData? = null
    private var currentScore: RideScore? = null
    private var currentGalleryCard: CardGallery.GalleryCard? = null
    private var currentFontScale: Float = 1.0f
    private var isOverlayVisible = false

    // Resize state
    private var overlayWidth: Int = 320
    private var overlayHeight: Int = WindowManager.LayoutParams.WRAP_CONTENT
    private var overlayParams: WindowManager.LayoutParams? = null

    // Drag state
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    companion object {
        private const val NOTIFICATION_ID = 1001

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

        // Chamado pelo AccessibilityService/OCR quando detecta uma corrida
        var onRideDetected: ((RideData) -> Unit)? = null
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        prefsManager = PrefsManager(applicationContext)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Carregar configurações iniciais
        serviceScope.launch {
            overlayWidth = prefsManager.overlayWidthFlow.first()
            currentFontScale = prefsManager.overlayFontScaleFlow.first()
        }

        onRideDetected = { ride ->
            serviceScope.launch {
                showOverlay(ride)
            }
        }
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

        // Obter card da galeria baseado no slot ativo
        currentGalleryCard = when (activeSlot) {
            1 -> {
                val modelId = prefsManager.card1ModelIdFlow.first()
                CardGallery.getById(modelId)
            }
            2 -> {
                val modelId = prefsManager.card2ModelIdFlow.first()
                CardGallery.getById(modelId)
            }
            else -> null // Card 3 = Custom (usa defaults)
        }

        // Calcular score com critérios reais
        val scorer = RideScorer(
            weights = weights,
            driverThresholds = thresholds
        )
        currentScore = scorer.calculateScore(ride)

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

        val params = WindowManager.LayoutParams(
            widthPx,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 100
        }

        overlayParams = params

        overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)

            // Touch listener para drag (mover o card)
            setOnTouchListener(object : View.OnTouchListener {
                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = params.x
                            initialY = params.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            return false // Permite que o click passe
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = event.rawX - initialTouchX
                            val dy = event.rawY - initialTouchY
                            // Só move se arrastou mais de 10px (evita conflito com click)
                            if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                                params.x = initialX + dx.toInt()
                                params.y = initialY + dy.toInt()
                                try {
                                    windowManager?.updateViewLayout(overlayView, params)
                                } catch (_: Exception) {}
                                return true
                            }
                            return false
                        }
                    }
                    return false
                }
            })

            setContent {
                OverlayCard(
                    ride = currentRide,
                    score = currentScore,
                    galleryCard = currentGalleryCard,
                    fontScale = currentFontScale,
                    onDismiss = { hideOverlay() },
                    onFontScaleChange = { newScale ->
                        currentFontScale = newScale
                        serviceScope.launch {
                            prefsManager.saveOverlayFontScale(newScale)
                        }
                        updateOverlayContent()
                    }
                )
            }
        }

        try {
            windowManager?.addView(overlayView, params)
            isOverlayVisible = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateOverlayContent() {
        overlayView?.setContent {
            OverlayCard(
                ride = currentRide,
                score = currentScore,
                galleryCard = currentGalleryCard,
                fontScale = currentFontScale,
                onDismiss = { hideOverlay() },
                onFontScaleChange = { newScale ->
                    currentFontScale = newScale
                    serviceScope.launch {
                        prefsManager.saveOverlayFontScale(newScale)
                    }
                    updateOverlayContent()
                }
            )
        }

        // Atualizar largura se mudou
        overlayParams?.let { params ->
            val density = resources.displayMetrics.density
            val widthPx = (overlayWidth * density).toInt()
            if (params.width != widthPx) {
                params.width = widthPx
                try {
                    windowManager?.updateViewLayout(overlayView, params)
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Chamado externamente para redimensionar o overlay
     */
    fun resizeOverlay(newWidthDp: Int) {
        overlayWidth = newWidthDp.coerceIn(200, 500)
        serviceScope.launch {
            prefsManager.saveOverlaySize(overlayWidth, 0)
        }
        overlayParams?.let { params ->
            val density = resources.displayMetrics.density
            params.width = (overlayWidth * density).toInt()
            try {
                windowManager?.updateViewLayout(overlayView, params)
            } catch (_: Exception) {}
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NGBAutoRoadApp.CHANNEL_OVERLAY)
            .setContentTitle("NGB AutoRoad")
            .setContentText("Monitorando corridas...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
