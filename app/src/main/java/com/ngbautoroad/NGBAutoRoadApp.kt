package com.ngbautoroad

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class NGBAutoRoadApp : Application() {

    companion object {
        const val CHANNEL_OVERLAY = "overlay_channel"
        const val CHANNEL_OCR = "ocr_channel"
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

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(overlayChannel)
        manager.createNotificationChannel(ocrChannel)
    }
}
