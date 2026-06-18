package com.ngbautoroad.service

// ============================================================================
// ARQUIVO: BubbleService.kt
// RESPONSABILIDADE: Botão flutuante lateral (bubble) que aparece na borda da tela
//   quando o app não está em primeiro plano. Ao tocar, abre o app.
//   Similar ao comportamento do GigU e Uber Driver.
// CONFIGURAÇÕES:
//   - Lado (esquerdo/direito) via PrefsManager.bubbleSideFlow
//   - Transparência via PrefsManager.overlayOpacityFlow
//   - Habilitado/desabilitado via PrefsManager.bubbleEnabledFlow
// DEPENDÊNCIAS:
//   - data/prefs/PrefsManager.kt → configurações
//   - OverlayService.kt → roda junto (mesmo foreground notification)
// DEPENDENTES:
//   - ui/settings/SettingsTab.kt → toggle e configuração
// ============================================================================

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import com.ngbautoroad.R
import com.ngbautoroad.data.prefs.PrefsManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/**
 * Serviço que exibe um botão flutuante (bubble) na lateral da tela.
 * 
 * Comportamento:
 * - Aparece como um pequeno ícone circular na borda esquerda ou direita
 * - Ao tocar, abre o app principal
 * - Transparência e lado configuráveis
 * - Desaparece quando o app está em primeiro plano (controlado pelo OverlayService)
 */
class BubbleService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "bubble_channel"
        private var instance: BubbleService? = null

        fun start(context: Context) {
            val intent = Intent(context, BubbleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BubbleService::class.java))
        }

        fun setAppInForeground(inForeground: Boolean) {
            if (inForeground) {
                instance?.setBubbleVisible(false)
            } else {
                instance?.setBubbleVisible(true)
            }
        }

        fun updateVisibility(visible: Boolean) {
            instance?.setBubbleVisible(visible)
        }
    }

    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var isBubbleVisible = false
    private lateinit var prefsManager: PrefsManager
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        prefsManager = PrefsManager(applicationContext)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        instance = this

        serviceScope.launch {
            val enabled = prefsManager.bubbleEnabledFlow.first()
            if (enabled) {
                createBubble()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        return START_STICKY
    }

    override fun onDestroy() {
        removeBubble()
        serviceScope.cancel()
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createBubble() {
        if (bubbleView != null) return

        serviceScope.launch {
            val side = prefsManager.bubbleSideFlow.first()
            val opacity = prefsManager.overlayOpacityFlow.first()

            val bubbleSize = 48 // dp
            val density = resources.displayMetrics.density
            val sizePx = (bubbleSize * density).toInt()

            val imageView = ImageView(this@BubbleService).apply {
                setImageResource(R.mipmap.ic_launcher_round)
                // Sem background - usar ícone do app diretamente
                alpha = opacity
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(8, 8, 8, 8)
            }

            val gravity = Gravity.TOP or Gravity.START

            val params = WindowManager.LayoutParams(
                sizePx,
                sizePx,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                this.gravity = gravity
                x = 0
                y = 0
            }

            // Touch listener para abrir o app e permitir arrastar livremente (X e Y)
            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f
            var moved = false

            // Posição inicial: lado direito, centro vertical
            val displayMetrics = resources.displayMetrics
            params.x = displayMetrics.widthPixels - sizePx - (8 * density).toInt()
            params.y = displayMetrics.heightPixels / 3

            imageView.setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        moved = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = (event.rawX - initialTouchX).toInt()
                        val deltaY = (event.rawY - initialTouchY).toInt()
                        if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                            moved = true
                            params.x = initialX + deltaX
                            params.y = initialY + deltaY
                            try {
                                windowManager?.updateViewLayout(view, params)
                            } catch (_: Exception) {}
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) {
                            // Tocar = abrir o app
                            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            }
                            launchIntent?.let { startActivity(it) }
                        }
                        true
                    }
                    else -> false
                }
            }

            try {
                windowManager?.addView(imageView, params)
                bubbleView = imageView
                isBubbleVisible = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun removeBubble() {
        bubbleView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {}
        }
        bubbleView = null
        isBubbleVisible = false
    }

    fun setBubbleVisible(visible: Boolean) {
        if (visible && bubbleView == null) {
            createBubble()
        } else if (!visible && bubbleView != null) {
            removeBubble()
        }
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "NGB AutoRoad - Bubble",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Botão flutuante lateral"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NGB AutoRoad")
            .setContentText("Botão flutuante ativo")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }
}
