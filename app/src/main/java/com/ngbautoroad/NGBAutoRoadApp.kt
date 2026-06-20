// ============================================================================
// ARQUIVO: NGBAutoRoadApp.kt
// VERSÃO: v6.2.0
// LOCALIZAÇÃO: NGBAutoRoadApp.kt
// RESPONSABILIDADE: Application class principal
//   - Cria canais de notificação (overlay, OCR, ghost mode)
//   - Inicialização global do app
// MUDANÇAS v6.0.0:
//   - Adicionado CHANNEL_GHOST para alertas durante Ghost Mode
// DEPENDÊNCIAS: Nenhuma externa
// ============================================================================
package com.ngbautoroad

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.ngbautoroad.service.MemoryMonitor

class NGBAutoRoadApp : Application() {

    companion object {
        const val CHANNEL_OVERLAY = "overlay_channel"
        const val CHANNEL_OCR = "ocr_channel"
        const val CHANNEL_GHOST = "ghost_channel" // v6.0.0: Alertas durante Ghost Mode
        const val CHANNEL_LIFECYCLE = "lifecycle_channel" // v6.1.0: Confirmação de corrida UNCERTAIN

        // v6.2.0: Monitor de memória global (Android 17 memory limits)
        lateinit var memoryMonitor: MemoryMonitor
            private set
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        // v6.2.0: Iniciar monitoramento de memória para Android 17
        memoryMonitor = MemoryMonitor(this)
        memoryMonitor.start()
    }

    private fun createNotificationChannels() {
        val overlayChannel = NotificationChannel(
            CHANNEL_OVERLAY,
            "Overlay Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notificação do serviço de overlay"
        }

        val ocrChannel = NotificationChannel(
            CHANNEL_OCR,
            "OCR Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notificação do serviço de captura OCR"
        }

        // v6.0.0: Canal para alertas de corrida durante Ghost Mode
        val ghostChannel = NotificationChannel(
            CHANNEL_GHOST,
            "Alertas Ghost Mode",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alertas de corrida quando app bancário está aberto"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 200, 100, 200, 100, 400)
        }

        // v6.1.0: Canal para confirmação de corrida UNCERTAIN
        val lifecycleChannel = NotificationChannel(
            CHANNEL_LIFECYCLE,
            "Confirmação de Corrida",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Pergunta ao motorista se corrida foi concluída"
            enableVibration(true)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(overlayChannel)
        manager.createNotificationChannel(ocrChannel)
        manager.createNotificationChannel(ghostChannel)
        manager.createNotificationChannel(lifecycleChannel)
    }
}
