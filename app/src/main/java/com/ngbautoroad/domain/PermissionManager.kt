package com.ngbautoroad.domain

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * v6.8.0: Gerenciador centralizado de permissões.
 * Verifica todas as permissões necessárias e retorna status detalhado.
 */
data class PermissionStatus(
    val name: String,
    val description: String,
    val isGranted: Boolean,
    val isRequired: Boolean, // true = app não funciona sem ela
    val category: PermissionCategory
)

enum class PermissionCategory {
    CORE,       // Essenciais para funcionar (Acessibilidade, Overlay, Notificação)
    LOCATION,   // GPS e localização
    SENSORS,    // Acelerômetro, Activity Recognition
    SYSTEM,     // Bateria, Boot, Alarmes
    STORAGE     // Armazenamento (backup)
}

object PermissionManager {

    /**
     * Retorna status completo de todas as permissões.
     */
    fun getAllPermissions(context: Context): List<PermissionStatus> {
        val permissions = mutableListOf<PermissionStatus>()

        // === CORE ===
        permissions.add(PermissionStatus(
            name = "Acessibilidade",
            description = "Detectar corridas nos apps (Uber, 99, inDrive)",
            isGranted = isAccessibilityEnabled(context),
            isRequired = true,
            category = PermissionCategory.CORE
        ))

        permissions.add(PermissionStatus(
            name = "Sobreposição de Tela",
            description = "Exibir card de análise sobre outros apps",
            isGranted = Settings.canDrawOverlays(context),
            isRequired = true,
            category = PermissionCategory.CORE
        ))

        permissions.add(PermissionStatus(
            name = "Notificações",
            description = "Manter serviço ativo em background",
            isGranted = isNotificationGranted(context),
            isRequired = true,
            category = PermissionCategory.CORE
        ))

        // === LOCATION ===
        permissions.add(PermissionStatus(
            name = "Localização Precisa",
            description = "GPS para rastreamento de KM e validação de distância",
            isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED,
            isRequired = true,
            category = PermissionCategory.LOCATION
        ))

        permissions.add(PermissionStatus(
            name = "Localização em Background",
            description = "GPS ativo durante todo o turno (mesmo com tela desligada)",
            isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
            } else true,
            isRequired = true,
            category = PermissionCategory.LOCATION
        ))

        // === SENSORS ===
        permissions.add(PermissionStatus(
            name = "Reconhecimento de Atividade",
            description = "Detectar se está dirigindo, parado ou caminhando",
            isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
            } else true,
            isRequired = false,
            category = PermissionCategory.SENSORS
        ))

        // === SYSTEM ===
        permissions.add(PermissionStatus(
            name = "Sem Restrição de Bateria",
            description = "Impedir que o sistema mate o serviço",
            isGranted = isBatteryOptimizationIgnored(context),
            isRequired = true,
            category = PermissionCategory.SYSTEM
        ))

        permissions.add(PermissionStatus(
            name = "Iniciar após Reiniciar",
            description = "Auto-iniciar o app quando o celular reinicia",
            isGranted = true, // Sempre concedida se RECEIVE_BOOT_COMPLETED está no manifest
            isRequired = false,
            category = PermissionCategory.SYSTEM
        ))

        permissions.add(PermissionStatus(
            name = "Alarmes Exatos",
            description = "Lembretes precisos de manutenção e odômetro",
            isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                    alarmManager.canScheduleExactAlarms()
                } catch (_: Exception) { true }
            } else true,
            isRequired = false,
            category = PermissionCategory.SYSTEM
        ))

        return permissions
    }

    /**
     * Retorna quantas permissões obrigatórias estão faltando.
     */
    fun getMissingRequiredCount(context: Context): Int {
        return getAllPermissions(context).count { it.isRequired && !it.isGranted }
    }

    /**
     * Retorna se todas as permissões obrigatórias estão concedidas.
     */
    fun isFullyConfigured(context: Context): Boolean {
        return getMissingRequiredCount(context) == 0
    }

    /**
     * Retorna resumo textual do status.
     */
    fun getStatusSummary(context: Context): String {
        val all = getAllPermissions(context)
        val granted = all.count { it.isGranted }
        val total = all.size
        val missingRequired = all.count { it.isRequired && !it.isGranted }
        return if (missingRequired > 0) {
            "$granted/$total ativas ($missingRequired obrigatórias faltando)"
        } else {
            "$granted/$total ativas ✓"
        }
    }

    // === Helpers ===

    private fun isAccessibilityEnabled(context: Context): Boolean {
        return try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            enabledServices.contains(context.packageName)
        } catch (_: Exception) { false }
    }

    private fun isNotificationGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }
}
