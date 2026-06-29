package com.ngbautoroad.service

import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.car.app.*
import androidx.car.app.model.*
import androidx.car.app.validation.HostValidator
import com.ngbautoroad.data.db.AppDatabase
import com.ngbautoroad.data.db.RideHistoryEntity
import com.ngbautoroad.data.model.RideData
import kotlinx.coroutines.*

class NgbCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator {
        return if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }
    }

    override fun onCreateSession(): Session {
        return NgbCarSession()
    }
}

class NgbCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        return NgbCarScreen(carContext)
    }
}

class NgbCarScreen(carContext: CarContext) : Screen(carContext) {
    private var recentRides: List<RideHistoryEntity> = emptyList()
    private var activeRide: RideData? = null
    private var activeScore: Double = 0.0
    private var activePhase: RideLifecycleManager.RidePhase = RideLifecycleManager.RidePhase.IDLE
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        // Obter estados ativos iniciais
        val manager = RideAccessibilityService.instance?.lifecycleManager
        if (manager != null) {
            activeRide = manager.getCurrentRide()
            activePhase = manager.getCurrentPhase()
            activeScore = manager.getCurrentRideScore()
        }

        // Inscrever no lifecycle para atualizar dinamicamente a tela
        manager?.let { m ->
            val originalCallback = m.onPhaseChanged
            m.onPhaseChanged = { phase, ride ->
                originalCallback?.invoke(phase, ride)
                activePhase = phase
                activeRide = ride
                activeScore = m.getCurrentRideScore()
                
                // Recarregar histórico caso tenha finalizado uma corrida
                if (phase == RideLifecycleManager.RidePhase.COMPLETED || phase == RideLifecycleManager.RidePhase.IDLE) {
                    loadRecentRides()
                }
                
                invalidate()
            }
        }

        loadRecentRides()
    }

    private fun loadRecentRides() {
        scope.launch {
            try {
                val db = AppDatabase.getInstance(carContext.applicationContext)
                val list = withContext(Dispatchers.IO) {
                    db.rideHistoryDao().getRecent(5)
                }
                recentRides = list
                invalidate()
            } catch (_: Exception) {}
        }
    }

    override fun onGetTemplate(): Template {
        // Se houver uma corrida ativa (PENDING, ACCEPTED, etc.), mostrar o painel da corrida
        val ride = activeRide
        if (ride != null && activePhase != RideLifecycleManager.RidePhase.IDLE) {
            val scoreText = "%.0f".format(activeScore)
            val valueText = "R$ %.2f".format(ride.rideValue)
            val kmText = "R$ %.2f/km".format(ride.valuePerKm)
            val statusText = when (activePhase) {
                RideLifecycleManager.RidePhase.PENDING -> "PENDENTE"
                RideLifecycleManager.RidePhase.ACCEPTED -> "EM CURSO"
                RideLifecycleManager.RidePhase.COMPLETED -> "CONCLUÍDA"
                RideLifecycleManager.RidePhase.CANCELLED -> "CANCELADA"
                RideLifecycleManager.RidePhase.REFUSED -> "RECUSADA"
                RideLifecycleManager.RidePhase.EXPIRED -> "EXPIRADA"
                else -> activePhase.name
            }

            val paneBuilder = Pane.Builder()
                .addRow(
                    Row.Builder()
                        .setTitle("Origem/Bairro")
                        .addText((ride.pickupNeighborhood.takeIf { it.isNotBlank() } ?: "Não detectado") + " ➔ (Toque para navegar)")
                        .setOnClickListener {
                            if (ride.pickupNeighborhood.isNotBlank()) {
                                try {
                                    val intent = Intent(CarContext.ACTION_NAVIGATE).apply {
                                        data = android.net.Uri.parse("geo:0,0?q=${android.net.Uri.encode(ride.pickupNeighborhood)}")
                                    }
                                    carContext.startCarApp(intent)
                                } catch (e: Exception) {
                                    android.util.Log.e("NgbCarScreen", "Erro ao iniciar navegação de origem: ${e.message}")
                                }
                            }
                        }
                        .build()
                )
                .addRow(
                    Row.Builder()
                        .setTitle("Destino/Bairro")
                        .addText((ride.dropoffNeighborhood.takeIf { it.isNotBlank() } ?: "Não detectado") + " ➔ (Toque para navegar)")
                        .setOnClickListener {
                            if (ride.dropoffNeighborhood.isNotBlank()) {
                                try {
                                    val intent = Intent(CarContext.ACTION_NAVIGATE).apply {
                                        data = android.net.Uri.parse("geo:0,0?q=${android.net.Uri.encode(ride.dropoffNeighborhood)}")
                                    }
                                    carContext.startCarApp(intent)
                                } catch (e: Exception) {
                                    android.util.Log.e("NgbCarScreen", "Erro ao iniciar navegação de destino: ${e.message}")
                                }
                            }
                        }
                        .build()
                )
                .addRow(Row.Builder().setTitle("Faturamento").addText("$valueText | $kmText").build())
                .addRow(Row.Builder().setTitle("Dados Viagem").addText("${"%.1f".format(ride.pickupDistance)}km embarque | ${"%.1f".format(ride.dropoffDistance)}km viagem (${ride.rideDuration.toInt()}min)").build())
                .addRow(Row.Builder().setTitle("IA Score").addText("$scoreText pontos").build())

            // Ações rápidas dependendo da fase
            if (activePhase == RideLifecycleManager.RidePhase.PENDING) {
                paneBuilder.addAction(
                    Action.Builder()
                        .setTitle("Aceitar")
                        .setOnClickListener {
                            val service = RideAccessibilityService.instance
                            val currentId = service?.lifecycleManager?.getCurrentRideDbId() ?: 0L
                            service?.autoPilotEngine?.performAccept(ride, currentId)
                        }
                        .build()
                )
                paneBuilder.addAction(
                    Action.Builder()
                        .setTitle("Recusar")
                        .setOnClickListener {
                            val service = RideAccessibilityService.instance
                            val currentId = service?.lifecycleManager?.getCurrentRideDbId() ?: 0L
                            service?.autoPilotEngine?.performRefuse(ride.platform, currentId)
                        }
                        .build()
                )
            }

            return PaneTemplate.Builder(paneBuilder.build())
                .setHeaderAction(Action.APP_ICON)
                .setTitle("NGB AutoRoad: Corrida $statusText")
                .build()
        }

        // Caso contrário (Idle), mostrar a lista de últimas corridas recebidas no dia
        val listBuilder = ItemList.Builder()
        if (recentRides.isEmpty()) {
            listBuilder.setNoItemsMessage("Nenhuma oferta registrada recentemente.")
        } else {
            recentRides.forEach { r ->
                val platform = r.platform
                val value = "R$ %.2f".format(r.rideValue)
                val score = "${r.score.toInt()} pts"
                val route = "${r.pickupNeighborhood} ➔ ${r.dropoffNeighborhood}"
                val detail = "$route | $score | ${r.status}"
                
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle("$platform: $value")
                        .addText(detail)
                        .build()
                )
            }
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeaderAction(Action.APP_ICON)
            .setTitle("Últimas Ofertas (NGB AutoRoad)")
            .build()
    }
}
