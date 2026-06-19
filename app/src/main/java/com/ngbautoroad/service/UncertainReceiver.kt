package com.ngbautoroad.service

// ============================================================================
// ARQUIVO: UncertainReceiver.kt
// VERSÃO: v6.1.0
// LOCALIZAÇÃO: service/UncertainReceiver.kt
// RESPONSABILIDADE: Receber ações da notificação UNCERTAIN
//   - ACTION_UNCERTAIN_YES → corrida foi concluída
//   - ACTION_UNCERTAIN_NO → corrida foi cancelada
// DEPENDENTES:
//   - RideLifecycleManager.kt → cria PendingIntents apontando para cá
//   - AndroidManifest.xml → registra este receiver
// TAGS DE DEBUG: NGB_LIFECYCLE
// ============================================================================

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver para ações da notificação de corrida UNCERTAIN.
 * Quando o lifecycle não consegue detectar automaticamente se a corrida
 * foi concluída ou cancelada, exibe notificação com botões [Sim] [Não].
 * Este receiver processa a resposta do motorista.
 */
class UncertainReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NGB_LIFECYCLE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val rideId = intent.getLongExtra("ride_id", 0L)

        Log.i(TAG, "├─ UncertainReceiver: action=$action, rideId=$rideId")

        // Obter instância do LifecycleManager via RideAccessibilityService
        val service = RideAccessibilityService.instance
        val lifecycleManager = service?.lifecycleManager

        if (lifecycleManager == null) {
            Log.w(TAG, "│  ⚠ LifecycleManager não disponível — ignorando ação")
            return
        }

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
    }
}
