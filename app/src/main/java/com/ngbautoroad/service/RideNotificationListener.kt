package com.ngbautoroad.service

// ============================================================================
// ARQUIVO: RideNotificationListener.kt
// VERSÃO: v6.2.0 — PROMOVIDO A CANAL PRIMÁRIO (Plano B para AAPM Android 17)
// LOCALIZAÇÃO: service/RideNotificationListener.kt
// DATA: 20/06/2026
// ============================================================================
// RESPONSABILIDADE:
//   v6.2.0: Canal PRIMÁRIO de detecção quando AccessibilityService está
//   bloqueado pelo Advanced Protection Mode (AAPM) do Android 16/17.
//
//   Hierarquia de canais:
//   1. AccessibilityService (primário quando AAPM inativo)
//   2. NotificationListenerService (primário quando AAPM ativo ou AS bloqueado)
//   3. Ambos ativos simultaneamente = cobertura máxima
//
//   Quando o Ghost Mode está ativo (motorista no banco), o AccessibilityService
//   está hibernado e não pode ler a tela. Este serviço captura notificações
//   que chegam com dados da corrida e alerta o motorista.
// ============================================================================
// VANTAGENS:
//   - NÃO é detectado por bancos (não é AccessibilityService)
//   - Funciona em background sem overlay
//   - Permissão concedida uma vez nas configurações (sem ADB)
//   - Captura notificações mesmo com tela bloqueada
//   - NÃO é bloqueado pelo AAPM (não é AccessibilityService)
// ============================================================================
// LIMITAÇÕES:
//   - Depende do app de corrida enviar notificação (nem sempre envia)
//   - Dados da notificação são limitados (geralmente só valor + endereço)
//   - Parsing menos rico que AccessibilityService (sem coordenadas exatas)
// ============================================================================
// BLOCOS:
//   - onNotificationPosted (L~60): Recebe notificação e filtra por package
//   - parseUberNotification (L~110): Extrai dados de notificação Uber
//   - parse99Notification (L~160): Extrai dados de notificação 99
//   - alertDriver (L~200): Alerta motorista durante Ghost Mode
// ============================================================================
// DEPENDÊNCIAS:
//   - data/model/RideData.kt → RideData, Platform
//   - service/OverlayService.kt → onRideDetected (quando Ghost Mode OFF)
//   - service/RideAccessibilityService.kt → stealthModeActive
// ============================================================================
// DEBUG TAGS:
//   - NGB_NOTIF: Eventos do NotificationListener
// ============================================================================

import android.app.Notification
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.widget.Toast
import com.ngbautoroad.data.model.Platform
import com.ngbautoroad.data.model.RideData
import com.ngbautoroad.data.model.RideType

class RideNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "NGB_NOTIF"

        // Packages monitorados
        private val MONITORED_PACKAGES = setOf(
            "com.ubercab.driver",
            "com.ubercab",
            "com.app99.driver",          // 99 Motorista (package real Play Store)
            "com.machfrankfurt.android",
            "com.cabify.driver"
        )

        // Estado: se o listener está ativo
        @Volatile
        var isListenerConnected = false
            private set

        // v6.1.1: Instância para acessar pendingGhostRide
        @Volatile
        var instance: RideNotificationListener? = null
            private set

        // Última corrida detectada via notificação (para evitar duplicatas)
        private var lastNotifRideHash = 0
        private var lastNotifRideTime = 0L
        private const val DEDUP_WINDOW_MS = 15_000L // 15s

        /**
         * v6.2.0: Flag que indica se o NotificationListener deve atuar como
         * canal PRIMÁRIO de detecção (quando AccessibilityService está bloqueado
         * pelo AAPM do Android 16/17 ou indisponível por qualquer motivo).
         *
         * Quando true: processa corridas MESMO com Ghost Mode inativo.
         * Quando false: comportamento legado (só atua durante Ghost Mode).
         */
        @Volatile
        var isPrimaryChannel: Boolean = false
    }

    // =========================================================================
    // BLOCO: Lifecycle
    // =========================================================================
    override fun onListenerConnected() {
        super.onListenerConnected()
        isListenerConnected = true
        instance = this
        Log.i(TAG, "═══════════════════════════════════════════════════")
        Log.i(TAG, "║ RideNotificationListener CONECTADO (v6.0.0)    ║")
        Log.i(TAG, "║ Monitorando: ${MONITORED_PACKAGES.size} packages              ║")
        Log.i(TAG, "║ Função: Backup durante Ghost Mode              ║")
        Log.i(TAG, "═══════════════════════════════════════════════════")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isListenerConnected = false
        instance = null
        Log.i(TAG, "NotificationListener DESCONECTADO")
    }

    // =========================================================================
    // BLOCO: onNotificationPosted — Recebe e filtra notificações
    // =========================================================================
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val packageName = sbn.packageName ?: return

        // Filtrar apenas packages de apps de corrida
        if (packageName !in MONITORED_PACKAGES) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        // Extrair texto da notificação
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""

        // Combinar todos os textos
        val fullText = "$title $text $bigText $subText".trim()

        if (fullText.isBlank()) return

        Log.d(TAG, "┌─ Notificação de $packageName")
        Log.d(TAG, "│  Title: \"$title\"")
        Log.d(TAG, "│  Text: \"$text\"")
        Log.d(TAG, "│  BigText: \"${bigText.take(100)}\"")

        // Tentar parsear como corrida
        val rideData = when {
            packageName.contains("ubercab") -> parseUberNotification(fullText)
            packageName.contains("app99") || packageName.contains("ninety9") -> parse99Notification(fullText)
            packageName.contains("machfrankfurt") -> parseInDriveNotification(fullText)
            packageName.contains("cabify") -> parseCabifyNotification(fullText)
            else -> null
        }

        if (rideData != null && rideData.rideValue > 0) {
            // Deduplicação
            val hash = "${rideData.platform}_${rideData.rideValue}_${rideData.dropoffDistance}".hashCode()
            val now = System.currentTimeMillis()

            if (hash == lastNotifRideHash && (now - lastNotifRideTime) < DEDUP_WINDOW_MS) {
                Log.d(TAG, "│  ⊘ Duplicata via notificação (hash=$hash)")
                return
            }

            lastNotifRideHash = hash
            lastNotifRideTime = now

            Log.i(TAG, "├─ ✅ CORRIDA VIA NOTIFICAÇÃO!")
            Log.i(TAG, "│  Valor: R$ ${String.format("%.2f", rideData.rideValue)}")
            Log.i(TAG, "│  Ghost Mode ativo: ${RideAccessibilityService.stealthModeActive}")

            val accessibilityAvailable = RideAccessibilityService.instance != null &&
                !RideAccessibilityService.stealthModeActive

            when {
                RideAccessibilityService.stealthModeActive -> {
                    // Ghost Mode ativo — alertar motorista discretamente
                    Log.d(TAG, "├─ Modo Ghost ativo: alertando discretamente")
                    alertDriverDuringGhostMode(rideData)
                }
                isPrimaryChannel && !accessibilityAvailable -> {
                    // v6.2.0: Canal primário ativo e AccessibilityService indisponível
                    // (bloqueado pelo AAPM ou não concedido)
                    Log.i(TAG, "├─ ★ CANAL PRIMÁRIO: AccessibilityService indisponível, processando via NotificationListener")
                    if (!OverlayService.isRunning()) {
                        Log.w(TAG, "│  ⚠️ OverlayService não está rodando. Auto-iniciando...")
                        OverlayService.start(this, rideData)
                    } else {
                        OverlayService.onRideDetected?.invoke(rideData)
                    }
                }
                isPrimaryChannel -> {
                    // v6.2.0: Canal primário ativo mas AccessibilityService também está ativo
                    // Deixar AccessibilityService processar para evitar duplicata
                    // (ele tem dados mais ricos: coordenadas, tipo de corrida, etc.)
                    Log.d(TAG, "├─ Canal primário ativo mas AS também disponível: ignorando (AS tem prioridade)")
                }
                else -> {
                    if (accessibilityAvailable) {
                        Log.d(TAG, "├─ AccessibilityService ativo e Ghost Mode OFF: ignorando notificação (AS tem prioridade)")
                    } else {
                        // Ghost Mode inativo e canal secundário — enviar normalmente para overlay
                        if (!OverlayService.isRunning()) {
                            Log.w(TAG, "│  ⚠️ OverlayService não está rodando. Auto-iniciando...")
                            OverlayService.start(this, rideData)
                        } else {
                            OverlayService.onRideDetected?.invoke(rideData)
                        }
                    }
                }
            }

            Log.d(TAG, "└─ Processado com sucesso")
        } else {
            Log.d(TAG, "└─ Sem dados de corrida na notificação")
        }
    }

    // =========================================================================
    // BLOCO: Parsers de Notificação
    // =========================================================================

    /**
     * Parser para notificações do Uber Driver.
     * Formatos conhecidos:
     * - "Nova viagem disponível" / "New trip request"
     * - "R$ 12,50 • 3,2 km • 8 min"
     * - "Viagem para Bairro X"
     */
    private fun parseUberNotification(fullText: String): RideData? {
        var rideValue = 0.0
        var distance = 0.0
        var duration = 0.0
        var pickupNeighborhood = ""

        // Valor
        val valueMatch = Regex("""R\$\s*(\d{1,4}[.,]\d{2})""").find(fullText)
        if (valueMatch != null) {
            rideValue = valueMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
        }

        // Distância
        val distMatch = Regex("""(\d+[.,]\d+)\s*km""", RegexOption.IGNORE_CASE).find(fullText)
        if (distMatch != null) {
            distance = distMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
        }

        // Duração
        val durMatch = Regex("""(\d+)\s*min""", RegexOption.IGNORE_CASE).find(fullText)
        if (durMatch != null) {
            duration = durMatch.groupValues[1].toDoubleOrNull() ?: 0.0
        }

        // Bairro (formato "para Bairro X" ou "to Neighborhood")
        val neighborMatch = Regex("""(?:para|to|em)\s+([A-ZÀ-Ú][a-zà-ú]+(?:\s+[A-ZÀ-Ú][a-zà-ú]+){0,2})""", RegexOption.IGNORE_CASE).find(fullText)
        if (neighborMatch != null) {
            pickupNeighborhood = neighborMatch.groupValues[1].trim().take(30)
        }

        if (rideValue == 0.0 && distance == 0.0) return null

        return RideData(
            platform = Platform.UBER,
            rideType = RideType.UBER_X,
            rideValue = rideValue,
            dropoffDistance = distance,
            rideDuration = duration,
            pickupNeighborhood = pickupNeighborhood
        )
    }

    /**
     * Parser para notificações do 99 Driver.
     */
    private fun parse99Notification(fullText: String): RideData? {
        var rideValue = 0.0
        var distance = 0.0
        var duration = 0.0

        val valueMatch = Regex("""R\$\s*(\d{1,4}[.,]\d{2})""").find(fullText)
        if (valueMatch != null) {
            rideValue = valueMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
        }

        val distMatch = Regex("""(\d+[.,]\d+)\s*km""", RegexOption.IGNORE_CASE).find(fullText)
        if (distMatch != null) {
            distance = distMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
        }

        val durMatch = Regex("""(\d+)\s*min""", RegexOption.IGNORE_CASE).find(fullText)
        if (durMatch != null) {
            duration = durMatch.groupValues[1].toDoubleOrNull() ?: 0.0
        }

        if (rideValue == 0.0) return null

        return RideData(
            platform = Platform.NINETY_NINE,
            rideValue = rideValue,
            dropoffDistance = distance,
            rideDuration = duration
        )
    }

    /**
     * Parser para notificações do inDrive.
     */
    private fun parseInDriveNotification(fullText: String): RideData? {
        var rideValue = 0.0
        var distance = 0.0

        val valueMatch = Regex("""R\$\s*(\d{1,4}[.,]\d{2})""").find(fullText)
        if (valueMatch != null) {
            rideValue = valueMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
        }

        val distMatch = Regex("""(\d+[.,]\d+)\s*km""", RegexOption.IGNORE_CASE).find(fullText)
        if (distMatch != null) {
            distance = distMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
        }

        if (rideValue == 0.0) return null

        return RideData(
            platform = Platform.INDRIVE,
            rideValue = rideValue,
            dropoffDistance = distance
        )
    }

    /**
     * Parser para notificações do Cabify.
     */
    private fun parseCabifyNotification(fullText: String): RideData? {
        var rideValue = 0.0
        var distance = 0.0

        val valueMatch = Regex("""R\$\s*(\d{1,4}[.,]\d{2})""").find(fullText)
        if (valueMatch != null) {
            rideValue = valueMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
        }

        val distMatch = Regex("""(\d+[.,]\d+)\s*km""", RegexOption.IGNORE_CASE).find(fullText)
        if (distMatch != null) {
            distance = distMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
        }

        if (rideValue == 0.0) return null

        return RideData(
            platform = Platform.CABIFY,
            rideValue = rideValue,
            dropoffDistance = distance
        )
    }

    // =========================================================================
    // BLOCO: Alerta durante Ghost Mode
    // =========================================================================

    /**
     * Alerta o motorista que uma corrida chegou enquanto ele está no banco.
     * Usa vibração especial (padrão "corrida") + toast discreto.
     * NÃO mostra overlay (banco detectaria).
     */
    private fun alertDriverDuringGhostMode(rideData: RideData) {
        Log.i(TAG, "│  🔔 Alertando motorista (Ghost Mode ativo)")

        // Vibração padrão "corrida chegou" — 3 pulsos rápidos
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                val effect = VibrationEffect.createWaveform(
                    longArrayOf(0, 200, 100, 200, 100, 400), // padrão: curto-curto-longo
                    -1 // não repetir
                )
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= 26) {
                    val effect = VibrationEffect.createWaveform(
                        longArrayOf(0, 200, 100, 200, 100, 400),
                        -1
                    )
                    vibrator.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 200, 100, 200, 100, 400), -1)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "│  ⚠ Vibração falhou: ${e.message}")
        }

        // Toast discreto (não é overlay, banco não detecta)
        try {
            val msg = "🚗 Corrida: R$ ${String.format("%.2f", rideData.rideValue)} | ${rideData.platform.displayName}"
            Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show()
            Log.d(TAG, "│  Toast exibido: \"$msg\"")
        } catch (e: Exception) {
            Log.w(TAG, "│  ⚠ Toast falhou: ${e.message}")
        }

        // Guardar corrida para quando Ghost Mode desativar
        // O OverlayService pode mostrar depois
        pendingGhostRide = rideData
    }

    /**
     * Corrida pendente detectada durante Ghost Mode.
     * Será enviada ao OverlayService quando Ghost Mode desativar.
     */
    @Volatile
    var pendingGhostRide: RideData? = null

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Não precisamos fazer nada quando notificação é removida
        super.onNotificationRemoved(sbn)
    }
}
