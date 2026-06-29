package com.ngbautoroad.service

// ============================================================================
// ARQUIVO: RideLifecycleManager.kt
// VERSÃO: v6.1.0
// LOCALIZAÇÃO: service/RideLifecycleManager.kt
// RESPONSABILIDADE: Gerenciar ciclo de vida completo de corridas detectadas
//   - Rastreia fases: PENDING → ACCEPTED → COMPLETED/CANCELLED/UNCERTAIN
//   - Detecta aceitação via textos "A caminho" / "Dirigir até"
//   - Detecta conclusão via "Viagem concluída" / valor final
//   - Detecta cancelamento via "Cancelada" / "Cancelled"
//   - Detecta expiração (card sumiu sem aceitar)
//   - Timeout → UNCERTAIN → notificação para motorista confirmar
//   - Registra ganho APENAS quando COMPLETED
//   - Reverte ganho se corrida for CANCELLED após ACCEPTED
// DEPENDENTES:
//   - RideAccessibilityService.kt → chama onTextsDetected() após parsing
//   - OverlayService.kt → chama onRideShown() e onRideDismissed()
//   - AutoPilotEngine.kt → chama onAutoPilotAction()
// TAGS DE DEBUG: NGB_LIFECYCLE
// ============================================================================

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ngbautoroad.NGBAutoRoadApp
import com.ngbautoroad.data.db.AppDatabase
import com.ngbautoroad.data.db.FinanceDatabase
import com.ngbautoroad.data.db.EarningEntity
import com.ngbautoroad.data.model.RideData
import com.ngbautoroad.data.prefs.PrefsManager
import com.ngbautoroad.domain.ShiftManager
import com.ngbautoroad.domain.GpsTrackingEngine
import com.ngbautoroad.domain.SmartRoutingEngine
import com.ngbautoroad.domain.TelemetryLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║  RideLifecycleManager — Ciclo de Vida Completo de Corridas         ║
 * ║                                                                      ║
 * ║  Fluxo:                                                              ║
 * ║  1. Corrida detectada → status PENDING (NÃO registra ganho)          ║
 * ║  2. Motorista aceita → status ACCEPTED (ainda sem ganho)             ║
 * ║  3a. Viagem concluída → status COMPLETED (registra ganho real)       ║
 * ║  3b. Cancelada → status CANCELLED (sem ganho ou taxa parcial)        ║
 * ║  3c. Timeout sem conclusão → UNCERTAIN (notifica motorista)          ║
 * ║  4. Card expirou sem aceitar → status EXPIRED (sem ganho)            ║
 * ║  5. Motorista recusou → status REFUSED (sem ganho)                   ║
 * ╚══════════════════════════════════════════════════════════════════════╝
 */
class RideLifecycleManager(private val context: Context) {

    companion object {
        private const val TAG = "NGB_LIFECYCLE"

        // ── Timeouts ──
        private const val ACCEPTANCE_DETECTION_TIMEOUT_MS = 60_000L   // 60s para detectar aceitação (Uber card fica 15s + margem para motorista aceitar)
        private const val COMPLETION_DETECTION_TIMEOUT_MS = 180_000L // 3min (não usado diretamente)
        private const val UNCERTAIN_TIMEOUT_MS = 2_700_000L          // 45min → UNCERTAIN (corridas podem levar 30+ min)

        // ── Notification ──
        private const val NOTIFICATION_ID_UNCERTAIN = 9001

        // ── Textos de detecção por plataforma ──
        // Uber
        private val UBER_ACCEPTED_TEXTS = listOf(
            "a caminho do passageiro", "dirigir até", "a caminho",
            "heading to rider", "drive to", "en camino al pasajero",
            "navegando até", "navegando para",
            "encontro com" // v7.3.1: Uber BR shows "Encontro com [Name]" on accept
        )
        private val UBER_COMPLETED_TEXTS = listOf(
            "viagem concluída", "trip completed", "viaje completado",
            "corrida finalizada", "avalie o passageiro", "rate rider",
            "como foi a viagem", "how was the trip",
            "avaliar usuário", "avaliar o usuário", // v7.3.1: Uber BR rate screen
            "encerrar uberx", "encerrar uber", "encerrar comfort" // v7.3.1: end-ride button texts
        )
        private val UBER_CANCELLED_TEXTS = listOf(
            "viagem cancelada", "trip cancelled", "corrida cancelada",
            "cancelada pelo passageiro", "cancelled by rider",
            "cancelamento", "cancellation"
        )

        // 99
        private val NINETY_NINE_ACCEPTED_TEXTS = listOf(
            "a caminho", "indo buscar", "navegando"
        )
        private val NINETY_NINE_COMPLETED_TEXTS = listOf(
            "corrida finalizada", "avalie", "viagem concluída"
        )
        private val NINETY_NINE_CANCELLED_TEXTS = listOf(
            "cancelada", "corrida cancelada"
        )

        // inDrive
        private val INDRIVE_ACCEPTED_TEXTS = listOf(
            "a caminho", "indo buscar", "en camino"
        )
        private val INDRIVE_COMPLETED_TEXTS = listOf(
            "concluída", "finalizada", "completed"
        )
        private val INDRIVE_CANCELLED_TEXTS = listOf(
            "cancelada", "cancelled"
        )

        // v7.3.1: In-trip texts — confirm ride is actively in progress (reset UNCERTAIN timeout)
        private val UBER_IN_TRIP_TEXTS = listOf(
            "destino de",                    // "Destino de [Name]" during navigation
            "iniciar uberx", "iniciar uber", "iniciar comfort", // passenger picked up
            "encerrar uberx", "encerrar comfort",               // actively in trip, has end button
            "usuário notificado",            // waiting for passenger
            "a caminho da primeira parada"   // multi-stop ride
        )
        private val NINETY_NINE_IN_TRIP_TEXTS = listOf(
            "iniciar corrida", "encerrar corrida", "destino"
        )
        private val INDRIVE_IN_TRIP_TEXTS = listOf(
            "em viagem", "destino", "encerrar"
        )
    }

    // ── Estado atual ──
    private var currentRide: RideData? = null
    private var currentRideDbId: Long = 0L
    private var currentRideScore: Double = 0.0
    private var currentPhase: RidePhase = RidePhase.IDLE
    private var phaseStartTime: Long = 0L

    // ── Handler para timeouts ──
    private val handler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    // ── Coroutine scope ──
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ── Telemetria ──
    private val telemetry = TelemetryLogger.getInstance(context)

    // ── Callback para notificar OverlayService de mudanças de status ──
    var onPhaseChanged: ((RidePhase, RideData?) -> Unit)? = null

    /**
     * ═══════════════════════════════════════════════════════════════════
     * ENUM: Fases do ciclo de vida da corrida
     * ═══════════════════════════════════════════════════════════════════
     */
    enum class RidePhase {
        IDLE,       // Sem corrida ativa
        PENDING,    // Corrida detectada, aguardando decisão do motorista
        ACCEPTED,   // Motorista aceitou, aguardando conclusão
        COMPLETED,  // Viagem concluída com sucesso
        CANCELLED,  // Viagem cancelada (por motorista ou passageiro)
        REFUSED,    // Motorista recusou explicitamente
        EXPIRED,    // Card expirou sem ação
        UNCERTAIN   // Não conseguiu detectar resultado → perguntar ao motorista
    }

    // =========================================================================
    // BLOCO: onRideDetected — Corrida nova detectada pelo engine
    // =========================================================================
    /**
     * Chamado quando RideAccessibilityService detecta uma nova corrida.
     * NÃO registra ganho — apenas salva como PENDING no histórico.
     *
     * @param ride Dados da corrida detectada
     * @param dbId ID da corrida no banco (já inserida pelo OverlayService como PENDING)
     */
    fun onRideDetected(ride: RideData, dbId: Long, score: Double = 0.0) {
        Log.i(TAG, "╔══════════════════════════════════════════════════╗")
        Log.i(TAG, "║  🆕 CORRIDA DETECTADA — Iniciando Lifecycle      ║")
        Log.i(TAG, "║  ID: $dbId | R$ ${String.format("%.2f", ride.rideValue)}")
        Log.i(TAG, "║  Platform: ${ride.platform.displayName}")
        Log.i(TAG, "╚══════════════════════════════════════════════════╝")

        // Cancelar lifecycle anterior se houver
        cancelCurrentLifecycle()

        currentRide = ride
        currentRideDbId = dbId
        currentRideScore = score
        transitionTo(RidePhase.PENDING)
        telemetry.lifecycle("Corrida detectada rideId=$dbId score=$score plataforma=${ride.platform.displayName}")

        // Iniciar timeout de aceitação
        // Se em 20s não detectar aceitação, verificar se card ainda está visível
        startTimeout(ACCEPTANCE_DETECTION_TIMEOUT_MS) {
            Log.d(TAG, "├─ Timeout de aceitação (${ACCEPTANCE_DETECTION_TIMEOUT_MS / 1000}s)")
            // Se ainda está PENDING após timeout, card provavelmente expirou
            if (currentPhase == RidePhase.PENDING) {
                transitionTo(RidePhase.EXPIRED)
                // v7.3.1: Fechar overlay quando corrida expira por timeout
                OverlayService.onRideAccepted?.invoke()
            }
        }
    }

    // =========================================================================
    // BLOCO: onTextsDetected — Monitoramento pós-detecção
    // =========================================================================
    /**
     * Chamado pelo RideAccessibilityService a cada evento de texto detectado
     * APÓS uma corrida ter sido detectada. Busca textos que indicam:
     * - Aceitação ("A caminho do passageiro")
     * - Conclusão ("Viagem concluída")
     * - Cancelamento ("Cancelada")
     *
     * @param texts Lista de textos coletados da árvore de acessibilidade
     * @param packageName Package do app que gerou o evento
     */
    fun onTextsDetected(texts: List<String>, packageName: String) {
        if (currentPhase == RidePhase.IDLE) return
        val ride = currentRide ?: return

        val platform = ride.platform
        val lowerTexts = texts.map { it.lowercase().trim() }

        when (currentPhase) {
            RidePhase.PENDING -> {
                // Procurar sinais de ACEITAÇÃO
                if (matchesAnyText(lowerTexts, getAcceptedTexts(platform))) {
                    Log.i(TAG, "├─ ✅ ACEITAÇÃO DETECTADA via texto!")
                    onRideAccepted()
                }
            }
            RidePhase.ACCEPTED -> {
                // Procurar sinais de CONCLUSÃO
                if (matchesAnyText(lowerTexts, getCompletedTexts(platform))) {
                    Log.i(TAG, "├─ ✅ CONCLUSÃO DETECTADA via texto!")
                    // Tentar extrair valor final real
                    val finalValue = extractFinalValue(texts)
                    onRideCompleted(finalValue)
                }
                // Procurar sinais de CANCELAMENTO
                else if (matchesAnyText(lowerTexts, getCancelledTexts(platform))) {
                    Log.i(TAG, "├─ ❌ CANCELAMENTO DETECTADO via texto!")
                    onRideCancelled()
                }
                // v7.3.1: If in-trip text detected, reset UNCERTAIN timeout (ride still active)
                else if (matchesAnyText(lowerTexts, getInTripTexts(platform))) {
                    Log.d(TAG, "├─ 🚗 In-trip text detected — resetting UNCERTAIN timeout")
                    cancelTimeout()
                    startTimeout(UNCERTAIN_TIMEOUT_MS) {
                        if (currentPhase == RidePhase.ACCEPTED) {
                            transitionTo(RidePhase.UNCERTAIN)
                            showUncertainNotification()
                        }
                    }
                }
            }
            else -> { /* Nada a fazer em outros estados */ }
        }
    }

    // =========================================================================
    // BLOCO: Ações explícitas (chamadas pelo AutoPilot ou overlay)
    // =========================================================================

    /**
     * Motorista aceitou a corrida (via AutoPilot ou manualmente detectado)
     */
    fun onRideAccepted() {
        if (currentPhase != RidePhase.PENDING) {
            Log.w(TAG, "│  ⚠ onRideAccepted chamado em fase ${currentPhase.name} — ignorando")
            return
        }

        transitionTo(RidePhase.ACCEPTED)
        telemetry.lifecycle("Corrida ACEITA rideId=$currentRideDbId")

        // v6.9.8: Fechar overlay do card atual (não recalcular com dados da corrida ativa)
        // O serviço continua ativo — se nova oferta real chegar (com botão aceitar), abre novo overlay
        OverlayService.onRideAccepted?.invoke()

        // v6.6.0: Iniciar rastreamento GPS da corrida
        try {
            val gps = GpsTrackingEngine(context)
            gps.startRide()
            Log.d(TAG, "│  📍 GPS: Rastreamento da corrida iniciado")
        } catch (e: Exception) {
            Log.w(TAG, "│  GPS startRide falhou: ${e.message}")
        }

        // v6.6.0: Registrar aceitação no SmartRoutingEngine (reseta contador de recusas)
        try {
            SmartRoutingEngine(context).registerAcceptance()
        } catch (e: Exception) { /* non-critical */ }

        // v6.3.5: Atualizar status via query direta (sem carregar tabela inteira)
        scope.launch {
            try {
                val db = AppDatabase.getInstance(context)
                db.rideHistoryDao().updateStatusById(currentRideDbId, "ACCEPTED")
                Log.d(TAG, "│  DB: Status atualizado para ACCEPTED (id=$currentRideDbId)")
            } catch (e: Exception) {
                Log.e(TAG, "│  ✖ Erro ao atualizar status ACCEPTED: ${e.message}")
            }
        }

        // Cancelar timeout de aceitação e iniciar timeout de conclusão
        cancelTimeout()
        startTimeout(UNCERTAIN_TIMEOUT_MS) {
            Log.d(TAG, "├─ Timeout de conclusão (${UNCERTAIN_TIMEOUT_MS / 1000}s)")
            if (currentPhase == RidePhase.ACCEPTED) {
                transitionTo(RidePhase.UNCERTAIN)
                showUncertainNotification()
            }
        }
    }

    /**
     * Motorista recusou a corrida (via AutoPilot ou card expirou)
     */
    fun onRideRefused() {
        if (currentPhase != RidePhase.PENDING) {
            Log.w(TAG, "│  ⚠ onRideRefused chamado em fase ${currentPhase.name} — ignorando")
            return
        }

        transitionTo(RidePhase.REFUSED)

        val rideId = currentRideDbId
        telemetry.lifecycle("Corrida RECUSADA rideId=$rideId")

        // v7.3.1: Fechar overlay quando corrida é recusada/expirada
        OverlayService.onRideAccepted?.invoke()

        // v6.6.0: Registrar recusa no SmartRoutingEngine (incrementa contador de recusas consecutivas)
        try {
            SmartRoutingEngine(context).registerRejection()
        } catch (e: Exception) { /* non-critical */ }

        // v6.3.5: Atualizar status via query direta (sem carregar tabela inteira)
        scope.launch {
            try {
                val db = AppDatabase.getInstance(context)
                db.rideHistoryDao().updateStatusById(rideId, "REFUSED")
                Log.d(TAG, "│  DB: Status atualizado para REFUSED (id=$rideId)")
            } catch (e: Exception) {
                Log.e(TAG, "│  ✖ Erro ao atualizar status REFUSED: ${e.message}")
            }
        }

        finishLifecycle()
    }

    /**
     * Corrida concluída com sucesso
     * @param finalValue Valor real da corrida (pode diferir da oferta inicial)
     */
    fun onRideCompleted(finalValue: Double? = null) {
        if (currentPhase != RidePhase.ACCEPTED && currentPhase != RidePhase.UNCERTAIN) {
            Log.w(TAG, "│  ⚠ onRideCompleted chamado em fase ${currentPhase.name} — ignorando")
            return
        }

        transitionTo(RidePhase.COMPLETED)

        val rideId = currentRideDbId
        telemetry.lifecycle("Corrida CONCLUÍDA rideId=$rideId valor=${finalValue ?: currentRide?.rideValue}")

        // v6.6.0: Encerrar rastreamento GPS e validar KM
        var gpsRideKm = 0.0
        try {
            val gps = GpsTrackingEngine(context)
            gpsRideKm = gps.endRide()
            Log.d(TAG, "│  📍 GPS: Corrida encerrada. KM GPS: ${String.format("%.2f", gpsRideKm)}")
        } catch (e: Exception) {
            Log.w(TAG, "│  GPS endRide falhou: ${e.message}")
        }

        val ride = currentRide ?: return
        val actualValue = finalValue ?: ride.rideValue

        // v6.6.0: Validar KM da Uber vs GPS
        if (gpsRideKm > 0.5) { // Só validar se GPS mediu algo significativo
            try {
                val gps = GpsTrackingEngine(context)
                val validation = gps.validateRideKm(ride.dropoffDistance)
                if (validation.isUberUnderreporting) {
                    Log.w(TAG, "│  ⚠️ KM DIVERGENTE: GPS=${String.format("%.1f", validation.gpsDistanceKm)}km vs Uber=${String.format("%.1f", validation.uberReportedKm)}km (${String.format("%.1f", validation.differencePercent)}% a menos)")
                }
            } catch (e: Exception) { /* non-critical */ }
        }

        // v6.3.5: Atualizar status + valor via query direta (sem carregar tabela inteira)
        scope.launch {
            try {
                val db = AppDatabase.getInstance(context)
                db.rideHistoryDao().updateStatusAndValueById(rideId, "COMPLETED", actualValue)
                Log.d(TAG, "│  DB: Status atualizado para COMPLETED (id=$rideId, valor=R$$actualValue)")

                // ═══ REGISTRAR GANHO — SÓ AQUI! ═══
                // v6.1.1: Respeitar toggle de auto-import
                val prefs = PrefsManager(context)
                val autoImportEnabled = prefs.autoImportEarningsFlow.first()
                if (autoImportEnabled) {
                    registerEarning(ride, actualValue, rideId)
                } else {
                    Log.d(TAG, "│  ⊜ Auto-import desativado — ganho NÃO registrado")
                }

                // v6.1.1: Atualizar turno ativo
                val shiftManager = ShiftManager(context)
                val shiftState = shiftManager.loadState()
                if (shiftState.isActive) {
                    val updated = shiftManager.addRide(shiftState, actualValue, true)
                    shiftManager.saveState(updated)
                    Log.d(TAG, "│  📦 Turno atualizado: +R$$actualValue (total: R$${updated.totalEarned})")
                }

            } catch (e: Exception) {
                Log.e(TAG, "│  ✖ Erro ao completar corrida: ${e.message}")
            }
        }

        // v7.3.0: Invalidar cache da IA para que dashboard mostre dados atualizados imediatamente
        try { com.ngbautoroad.ai.AiBrainRepository.invalidateActiveCache() } catch (_: Exception) {}
        // v7.3.0: Limpar notificação de corrida ativa
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(1003)
        } catch (_: Exception) {}

        finishLifecycle()
    }

    /**
     * Corrida cancelada (por motorista ou passageiro)
     */
    fun onRideCancelled() {
        if (currentPhase == RidePhase.IDLE || currentPhase == RidePhase.COMPLETED) {
            Log.w(TAG, "│  ⚠ onRideCancelled chamado em fase ${currentPhase.name} — ignorando")
            return
        }

        transitionTo(RidePhase.CANCELLED)

        val rideId = currentRideDbId
        telemetry.lifecycle("Corrida CANCELADA rideId=$rideId")

        // v6.3.5: Atualizar status via query direta (sem carregar tabela inteira)
        scope.launch {
            try {
                val db = AppDatabase.getInstance(context)
                db.rideHistoryDao().updateStatusById(rideId, "CANCELLED")
                Log.d(TAG, "│  DB: Status atualizado para CANCELLED (id=$rideId)")

                // Se já havia ganho registrado (caso raro), reverter
                val financeDb = FinanceDatabase.getInstance(context)
                val existingEarning = financeDb.earningDao().countAutoImportedByRideId(rideId)
                if (existingEarning > 0) {
                    financeDb.earningDao().deleteByRideHistoryId(rideId)
                    Log.d(TAG, "│  DB: Ganho revertido para corrida cancelada (id=$rideId)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "│  ✖ Erro ao cancelar corrida: ${e.message}")
            }
        }

        finishLifecycle()
    }

    /**
     * Motorista confirma via notificação que corrida foi concluída
     */
    fun onUncertainConfirmed(completed: Boolean) {
        Log.i(TAG, "├─ Motorista confirmou UNCERTAIN: ${if (completed) "CONCLUÍDA" else "NÃO CONCLUÍDA"}")

        if (completed) {
            onRideCompleted()
        } else {
            // Tratar como cancelada
            transitionTo(RidePhase.CANCELLED)
            scope.launch {
                try {
                    val db = AppDatabase.getInstance(context)
                    db.rideHistoryDao().updateStatusById(currentRideDbId, "CANCELLED")
                } catch (e: Exception) {
                    Log.e(TAG, "│  ✖ Erro ao confirmar UNCERTAIN: ${e.message}")
                }
            }
            finishLifecycle()
        }

        // Cancelar notificação
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID_UNCERTAIN)
    }

    /**
     * Card do overlay foi fechado/expirou sem ação do motorista
     */
    fun onOverlayDismissed() {
        if (currentPhase == RidePhase.PENDING) {
            Log.d(TAG, "├─ Overlay fechado em PENDING — marcando como EXPIRED")
            telemetry.lifecycle("Corrida EXPIRADA rideId=$currentRideDbId")
            transitionTo(RidePhase.EXPIRED)

            // v6.3.5: Query direta
            scope.launch {
                try {
                    val db = AppDatabase.getInstance(context)
                    db.rideHistoryDao().updateStatusById(currentRideDbId, "EXPIRED")
                } catch (e: Exception) {
                    Log.e(TAG, "│  ✖ Erro ao marcar EXPIRED: ${e.message}")
                }
            }

            finishLifecycle()
        }
    }

    // =========================================================================
    // BLOCO: Registrar ganho financeiro
    // =========================================================================
    /**
     * Registra ganho no FinanceDatabase.
     * SÓ é chamado quando corrida é COMPLETED.
     * Verifica duplicatas antes de inserir.
     */
    private suspend fun registerEarning(ride: RideData, actualValue: Double, rideDbId: Long) {
        try {
            val financeDb = FinanceDatabase.getInstance(context)

            // Verificar se já foi importado (evitar duplicata)
            val alreadyImported = financeDb.earningDao().countAutoImportedByRideId(rideDbId)
            if (alreadyImported > 0) {
                Log.d(TAG, "│  ⊊ Ganho já registrado para id=$rideDbId — ignorando duplicata")
                return
            }

            val earning = EarningEntity(
                platform = ride.platform.displayName,
                amount = actualValue,
                distance = ride.dropoffDistance,
                duration = ride.rideDuration.toInt(),
                ridesCount = 1,
                description = "Auto-import (lifecycle)",
                period = "DIA",
                isAutoImported = true,
                rideHistoryId = rideDbId,
                score = currentRideScore,
                pickupDistance = ride.pickupDistance // v6.6.0: KM morto
            )
            financeDb.earningDao().insert(earning)

            Log.i(TAG, "│  💰 GANHO REGISTRADO: R$ ${String.format("%.2f", actualValue)} (${ride.platform.displayName})")
        } catch (e: Exception) {
            Log.e(TAG, "│  ✖ Erro ao registrar ganho: ${e.message}")
        }
    }

    // =========================================================================
    // BLOCO: Notificação UNCERTAIN
    // =========================================================================
    /**
     * Exibe notificação perguntando ao motorista se a corrida foi concluída.
     * Ações: [Sim, concluída] [Não, cancelada]
     */
    private fun showUncertainNotification() {
        val ride = currentRide ?: return

        Log.i(TAG, "├─ 🔔 Exibindo notificação UNCERTAIN para motorista")

        // Intent para "Sim, concluída"
        val yesIntent = Intent(context, UncertainReceiver::class.java).apply {
            action = "ACTION_UNCERTAIN_YES"
            putExtra("ride_id", currentRideDbId)
        }
        val yesPending = PendingIntent.getBroadcast(
            context, 1001, yesIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent para "Não, cancelada"
        val noIntent = Intent(context, UncertainReceiver::class.java).apply {
            action = "ACTION_UNCERTAIN_NO"
            putExtra("ride_id", currentRideDbId)
        }
        val noPending = PendingIntent.getBroadcast(
            context, 1002, noIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NGBAutoRoadApp.CHANNEL_LIFECYCLE)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Corrida R$ ${String.format("%.2f", ride.rideValue)} — foi concluída?")
            .setContentText("${ride.platform.displayName} • Não detectamos o resultado automaticamente")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Corrida de R$ ${String.format("%.2f", ride.rideValue)} (${ride.platform.displayName})\n" +
                        "Não conseguimos detectar se foi concluída ou cancelada.\n" +
                        "Confirme para registrar corretamente seus ganhos."))
            .addAction(android.R.drawable.ic_menu_send, "✓ Concluída", yesPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "✗ Cancelada", noPending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(false)
            .setOngoing(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID_UNCERTAIN, notification)
    }

    // =========================================================================
    // BLOCO: Helpers internos
    // =========================================================================

    /**
     * Transição de fase com log detalhado
     */
    private fun transitionTo(newPhase: RidePhase) {
        val oldPhase = currentPhase
        currentPhase = newPhase
        phaseStartTime = System.currentTimeMillis()

        Log.i(TAG, "│  ⟹ Transição: ${oldPhase.name} → ${newPhase.name}")
        telemetry.lifecycle("Transição ${oldPhase.name} → ${newPhase.name} rideId=$currentRideDbId")

        // Notificar observers
        onPhaseChanged?.invoke(newPhase, currentRide)
    }

    /**
     * Inicia timeout com callback
     */
    private fun startTimeout(delayMs: Long, onTimeout: () -> Unit) {
        cancelTimeout()
        timeoutRunnable = Runnable { onTimeout() }
        timeoutRunnable?.let { handler.postDelayed(it, delayMs) }
    }

    /**
     * Cancela timeout ativo
     */
    private fun cancelTimeout() {
        timeoutRunnable?.let {
            handler.removeCallbacks(it)
            timeoutRunnable = null
        }
    }

    /**
     * Finaliza lifecycle atual e volta ao estado IDLE
     */
    private fun finishLifecycle() {
        Log.i(TAG, "└─ Lifecycle finalizado (fase final: ${currentPhase.name})")
        cancelTimeout()
        RideAccessibilityService.instance?.userActionDetector?.stopMonitoring()
        currentRide = null
        currentRideDbId = 0L
        currentPhase = RidePhase.IDLE
    }

    /**
     * Cancela lifecycle em andamento (para nova corrida)
     */
    private fun cancelCurrentLifecycle() {
        if (currentPhase != RidePhase.IDLE) {
            Log.w(TAG, "│  ⚠ Cancelando lifecycle anterior (fase: ${currentPhase.name})")
            cancelTimeout()
            // v6.3.5: Se estava PENDING, marcar como EXPIRED via query direta
            if (currentPhase == RidePhase.PENDING) {
                scope.launch {
                    try {
                        val db = AppDatabase.getInstance(context)
                        db.rideHistoryDao().updateStatusById(currentRideDbId, "EXPIRED")
                    } catch (_: Exception) {}
                }
            }
            RideAccessibilityService.instance?.userActionDetector?.stopMonitoring()
            currentRide = null
            currentRideDbId = 0L
            currentPhase = RidePhase.IDLE
        }
    }

    /**
     * Verifica se algum texto da lista corresponde aos padrões esperados
     */
    private fun matchesAnyText(detectedTexts: List<String>, patterns: List<String>): Boolean {
        for (detected in detectedTexts) {
            for (pattern in patterns) {
                if (detected.contains(pattern, ignoreCase = true)) {
                    Log.d(TAG, "│    Match: \"$detected\" contém \"$pattern\"")
                    return true
                }
            }
        }
        return false
    }

    /**
     * Retorna textos de aceitação por plataforma
     */
    private fun getAcceptedTexts(platform: com.ngbautoroad.data.model.Platform): List<String> {
        return when (platform) {
            com.ngbautoroad.data.model.Platform.UBER -> UBER_ACCEPTED_TEXTS
            com.ngbautoroad.data.model.Platform.NINETY_NINE -> NINETY_NINE_ACCEPTED_TEXTS
            com.ngbautoroad.data.model.Platform.INDRIVE -> INDRIVE_ACCEPTED_TEXTS
            else -> UBER_ACCEPTED_TEXTS + NINETY_NINE_ACCEPTED_TEXTS + INDRIVE_ACCEPTED_TEXTS
        }
    }

    /**
     * Retorna textos de conclusão por plataforma
     */
    private fun getCompletedTexts(platform: com.ngbautoroad.data.model.Platform): List<String> {
        return when (platform) {
            com.ngbautoroad.data.model.Platform.UBER -> UBER_COMPLETED_TEXTS
            com.ngbautoroad.data.model.Platform.NINETY_NINE -> NINETY_NINE_COMPLETED_TEXTS
            com.ngbautoroad.data.model.Platform.INDRIVE -> INDRIVE_COMPLETED_TEXTS
            else -> UBER_COMPLETED_TEXTS + NINETY_NINE_COMPLETED_TEXTS + INDRIVE_COMPLETED_TEXTS
        }
    }

    /**
     * Retorna textos de cancelamento por plataforma
     */
    private fun getCancelledTexts(platform: com.ngbautoroad.data.model.Platform): List<String> {
        return when (platform) {
            com.ngbautoroad.data.model.Platform.UBER -> UBER_CANCELLED_TEXTS
            com.ngbautoroad.data.model.Platform.NINETY_NINE -> NINETY_NINE_CANCELLED_TEXTS
            com.ngbautoroad.data.model.Platform.INDRIVE -> INDRIVE_CANCELLED_TEXTS
            else -> UBER_CANCELLED_TEXTS + NINETY_NINE_CANCELLED_TEXTS + INDRIVE_CANCELLED_TEXTS
        }
    }

    /**
     * v7.3.1: Retorna textos que confirmam corrida em andamento (in-trip)
     * Usado para resetar timeout UNCERTAIN quando corrida está ativamente em progresso
     */
    private fun getInTripTexts(platform: com.ngbautoroad.data.model.Platform): List<String> {
        return when (platform) {
            com.ngbautoroad.data.model.Platform.UBER -> UBER_IN_TRIP_TEXTS
            com.ngbautoroad.data.model.Platform.NINETY_NINE -> NINETY_NINE_IN_TRIP_TEXTS
            com.ngbautoroad.data.model.Platform.INDRIVE -> INDRIVE_IN_TRIP_TEXTS
            else -> UBER_IN_TRIP_TEXTS + NINETY_NINE_IN_TRIP_TEXTS + INDRIVE_IN_TRIP_TEXTS
        }
    }

    /**
     * Tenta extrair valor final real dos textos (pode diferir da oferta)
     * Uber mostra valor final na tela "Viagem concluída"
     */
    private fun extractFinalValue(texts: List<String>): Double? {
        for (text in texts) {
            val match = Regex("""R\$\s*(\d{1,4}[.,]\d{2})""").find(text)
            if (match != null) {
                val value = match.groupValues[1].replace(",", ".").toDoubleOrNull()
                if (value != null && value > 0 && value < 999) {
                    Log.d(TAG, "│    Valor final extraído: R$ $value")
                    return value
                }
            }
        }
        return null
    }

    /**
     * Verifica se há lifecycle ativo
     */
    fun isActive(): Boolean = currentPhase != RidePhase.IDLE

    /**
     * Retorna fase atual
     */
    fun getCurrentPhase(): RidePhase = currentPhase

    /**
     * Retorna corrida atual
     */
    fun getCurrentRide(): RideData? = currentRide

    /**
     * Retorna ID da corrida atual no banco
     */
    fun getCurrentRideDbId(): Long = currentRideDbId

    fun getCurrentRideScore(): Double = currentRideScore

    /**
     * Cleanup ao destruir
     */
    fun destroy() {
        cancelTimeout()
        scope.cancel()
        Log.d(TAG, "RideLifecycleManager destruído")
    }
}
