package com.ngbautoroad.service

// ============================================================================
// ARQUIVO: UncertainReceiver.kt
// VERSÃO: v6.1.1
// LOCALIZAÇÃO: service/UncertainReceiver.kt
// RESPONSABILIDADE: Receber ações da notificação UNCERTAIN
//   - ACTION_UNCERTAIN_YES → corrida foi concluída
//   - ACTION_UNCERTAIN_NO → corrida foi cancelada
//   - v6.1.1: Fallback direto ao banco quando lifecycle não está disponível
// DEPENDENTES:
//   - RideLifecycleManager.kt → cria PendingIntents apontando para cá
//   - AndroidManifest.xml → registra este receiver
// TAGS DE DEBUG: NGB_LIFECYCLE
// ============================================================================

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ngbautoroad.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver para ações da notificação de corrida UNCERTAIN.
 * Quando o lifecycle não consegue detectar automaticamente se a corrida
 * foi concluída ou cancelada, exibe notificação com botões [Sim] [Não].
 * Este receiver processa a resposta do motorista.
 *
 * v6.1.1: Inclui fallback direto ao banco caso o AccessibilityService
 * tenha sido destruído entre a notificação e a resposta do motorista.
 */
class UncertainReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NGB_LIFECYCLE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val rideId = intent.getLongExtra("ride_id", 0L)

        Log.i(TAG, "├─ UncertainReceiver: action=$action, rideId=$rideId")

        // Cancelar notificação
        val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notifManager?.cancel(9999) // ID da notificação UNCERTAIN

        // Obter instância do LifecycleManager via RideAccessibilityService
        val service = RideAccessibilityService.instance
        val lifecycleManager = service?.lifecycleManager

        if (lifecycleManager != null) {
            // Caminho principal: lifecycle disponível
            when (action) {
                "ACTION_UNCERTAIN_YES" -> {
                    Log.i(TAG, "│  ✓ Motorista confirmou: CORRIDA CONCLUÍDA")
                    lifecycleManager.onUncertainConfirmed(completed = true)
                }
                "ACTION_UNCERTAIN_NO" -> {
                    Log.i(TAG, "│  ✗ Motorista confirmou: CORRIDA NÃO CONCLUÍDA")
                    lifecycleManager.onUncertainConfirmed(completed = false)
                }
                else -> {
                    Log.w(TAG, "│  ⚠ Ação desconhecida: $action")
                }
            }
        } else {
            // v6.1.1: Fallback — atualizar banco diretamente
            Log.w(TAG, "│  ⚠ LifecycleManager não disponível — usando fallback direto ao banco")
            if (rideId > 0L) {
                val scope = CoroutineScope(Dispatchers.IO)
                scope.launch {
                    try {
                        val db = AppDatabase.getInstance(context)
                        val dao = db.rideHistoryDao()
                        val entity = dao.getAll().firstOrNull { it.id == rideId }
                        if (entity != null) {
                            val newStatus = when (action) {
                                "ACTION_UNCERTAIN_YES" -> "COMPLETED"
                                "ACTION_UNCERTAIN_NO" -> "CANCELLED"
                                else -> return@launch
                            }
                            dao.update(entity.copy(status = newStatus))
                            Log.i(TAG, "│  DB Fallback: rideId=$rideId → status=$newStatus")
                        } else {
                            Log.w(TAG, "│  ⚠ Ride não encontrada no banco: id=$rideId")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "│  ✖ Erro no fallback: ${e.message}")
                    }
                }
            } else {
                Log.w(TAG, "│  ⚠ rideId inválido ($rideId) — não é possível fazer fallback")
            }
        }
    }
}
