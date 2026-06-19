// ============================================================================
// ARQUIVO: NGBAutoRoadApp.kt
// VERSÃO: v6.0.0
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

class NGBAutoRoadApp : Application() {

    companion object {
        const val CHANNEL_OVERLAY = "overlay_channel"
        const val CHANNEL_OCR = "ocr_channel"
        const val CHANNEL_GHOST = "ghost_channel" // v6.0.0: Alertas durante Ghost Mode
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
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

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(overlayChannel)
        manager.createNotificationChannel(ocrChannel)
        manager.createNotificationChannel(ghostChannel)
    }
}
