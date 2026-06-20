package com.ngbautoroad.service

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Debug
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * v6.2.0 — MemoryMonitor
 *
 * Gerencia o consumo de memória do app para sobreviver aos limites do Android 17.
 * O Android 17 impõe limites rígidos de RAM por app baseados na memória total do dispositivo.
 * Em celulares com 4 GB de RAM (comuns entre motoristas), o app pode ser encerrado abruptamente.
 *
 * Responsabilidades:
 * 1. Monitorar consumo de RAM em background a cada 30 segundos
 * 2. Detectar se o app foi morto por OOM na sessão anterior (ApplicationExitInfo)
 * 3. Emitir alertas quando o consumo ultrapassar 80% do limite estimado
 * 4. Acionar limpeza de cache quando necessário
 * 5. Registrar histórico de kills em arquivo de log para diagnóstico
 */
class MemoryMonitor(private val context: Context) {

    companion object {
        private const val TAG = "MemoryMonitor"

        // Limites estimados por faixa de RAM do dispositivo (Android 17 guidelines)
        private const val LIMIT_4GB_MB = 256   // dispositivos com 4 GB RAM
        private const val LIMIT_6GB_MB = 384   // dispositivos com 6 GB RAM
        private const val LIMIT_8GB_MB = 512   // dispositivos com 8+ GB RAM

        // Threshold de alerta: 80% do limite estimado
        private const val ALERT_THRESHOLD_PERCENT = 0.80f

        // Intervalo de monitoramento em background
        private const val MONITOR_INTERVAL_MS = 30_000L
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    private val _memoryState = MutableStateFlow(MemoryState())
    val memoryState: StateFlow<MemoryState> = _memoryState.asStateFlow()

    private var monitorJob: Job? = null

    // ─── Inicialização ────────────────────────────────────────────────────────

    fun start() {
        checkPreviousExitReason()
        monitorJob = scope.launch {
            while (isActive) {
                measureAndReport()
                delay(MONITOR_INTERVAL_MS)
            }
        }
        Log.d(TAG, "MemoryMonitor iniciado. Limite estimado: ${estimateMemoryLimitMb()} MB")
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
    }

    // ─── Medição de Memória ───────────────────────────────────────────────────

    private fun measureAndReport() {
        val memInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memInfo)

        val usedMb = memInfo.totalPss / 1024
        val limitMb = estimateMemoryLimitMb()
        val usagePercent = usedMb.toFloat() / limitMb.toFloat()
        val isWarning = usagePercent >= ALERT_THRESHOLD_PERCENT

        val state = MemoryState(
            usedMb = usedMb,
            limitMb = limitMb,
            usagePercent = usagePercent,
            isWarning = isWarning,
            timestamp = System.currentTimeMillis()
        )

        _memoryState.value = state

        if (isWarning) {
            Log.w(TAG, "⚠️ Memória alta: ${usedMb}MB / ${limitMb}MB (${(usagePercent * 100).toInt()}%)")
            triggerMemoryCleanup()
        } else {
            Log.v(TAG, "Memória OK: ${usedMb}MB / ${limitMb}MB (${(usagePercent * 100).toInt()}%)")
        }
    }

    // ─── Limpeza de Cache ─────────────────────────────────────────────────────

    /**
     * Aciona limpeza de recursos não essenciais quando a memória está alta.
     * O OverlayService e o CardGallery são os maiores consumidores.
     */
    private fun triggerMemoryCleanup() {
        Log.d(TAG, "Acionando limpeza de memória...")

        // Solicita GC explícito (Android pode ignorar, mas é uma dica)
        System.gc()

        // Notifica serviços que podem liberar cache
        OverlayService.instance?.onLowMemory()
        RideAccessibilityService.instance?.onLowMemory()

        Log.d(TAG, "Limpeza de memória concluída")
    }

    // ─── Detecção de Kill por OOM ─────────────────────────────────────────────

    /**
     * Verifica se o app foi morto por excesso de memória na sessão anterior.
     * Disponível apenas no Android 11+ via ApplicationExitInfo API.
     * Registra em log para diagnóstico e análise futura.
     */
    private fun checkPreviousExitReason() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        try {
            val exitInfoList = activityManager.getHistoricalProcessExitReasons(
                context.packageName, 0, 5
            )

            exitInfoList.forEach { exitInfo ->
                val reason = exitInfo.reason
                val reasonName = getExitReasonName(reason)
                val timestamp = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                    .format(Date(exitInfo.timestamp))

                if (            reason == ApplicationExitInfo.REASON_LOW_MEMORY) {
                    Log.e(TAG, "🔴 App foi morto por memória em $timestamp — Razão: $reasonName")
                    writeKillLog(timestamp, reasonName, exitInfo.importance)
                } else {
                    Log.d(TAG, "Saída anterior em $timestamp — Razão: $reasonName")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao verificar histórico de saída: ${e.message}")
        }
    }

    private fun getExitReasonName(reason: Int): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return "UNKNOWN"
        return when (reason) {
            ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
            ApplicationExitInfo.REASON_LOW_MEMORY + 1 -> "OUT_OF_MEMORY (OOM)"
            ApplicationExitInfo.REASON_ANR -> "ANR (App Not Responding)"
            ApplicationExitInfo.REASON_CRASH -> "CRASH"
            ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
            ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
            else -> "OTHER ($reason)"
        }
    }

    private fun writeKillLog(timestamp: String, reason: String, importance: Int) {
        try {
            val logFile = File(context.filesDir, "memory_kills.log")
            logFile.appendText("[$timestamp] Kill: $reason | Importance: $importance\n")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao escrever log de kill: ${e.message}")
        }
    }

    // ─── Estimativa de Limite ─────────────────────────────────────────────────

    /**
     * Estima o limite de RAM disponível para o app com base na RAM total do dispositivo.
     * Android 17 aplica limites proporcionais à memória total.
     */
    private fun estimateMemoryLimitMb(): Int {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val totalRamGb = memInfo.totalMem / (1024 * 1024 * 1024)

        return when {
            totalRamGb <= 4 -> LIMIT_4GB_MB
            totalRamGb <= 6 -> LIMIT_6GB_MB
            else -> LIMIT_8GB_MB
        }
    }

    // ─── Estado Público ───────────────────────────────────────────────────────

    data class MemoryState(
        val usedMb: Int = 0,
        val limitMb: Int = LIMIT_6GB_MB,
        val usagePercent: Float = 0f,
        val isWarning: Boolean = false,
        val timestamp: Long = 0L
    )
}
