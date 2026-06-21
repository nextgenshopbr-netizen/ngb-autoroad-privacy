package com.ngbautoroad.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * v6.8.0: Auto-inicia o serviço de acessibilidade após reboot do celular.
 * Isso garante que o motorista não perca corridas por esquecer de abrir o app.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {

            Log.i("BootReceiver", "Device booted - checking if auto-start is enabled")

            val prefs = context.getSharedPreferences("ngb_autoroad_prefs", Context.MODE_PRIVATE)
            val autoStartEnabled = prefs.getBoolean("auto_start_after_boot", true)

            if (autoStartEnabled) {
                Log.i("BootReceiver", "Auto-start enabled - launching main activity")
                try {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    launchIntent?.let {
                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(it)
                    }
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Failed to auto-start: ${e.message}")
                }
            }
        }
    }
}
