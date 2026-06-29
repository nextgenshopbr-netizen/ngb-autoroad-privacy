package com.ngbautoroad.ui.dashboard

// ============================================================================
// ARQUIVO: DashboardTab.kt
// LOCALIZAÇÃO: ui/dashboard/DashboardTab.kt
// RESPONSABILIDADE: Painel principal com resumo de corridas, ganhos e metas
// COMPOSABLES:
//   - DashboardTab (L34): Tela principal com dados reativos via Flow
//   - FinancialSummarySection (L233): Resumo financeiro + progresso de metas
//   - StatusBar (L363): Barra de status dos serviços
//   - EarningsCard (L442): Card de ganhos do dia/semana/mês
//   - StatCard (L503): Card genérico de estatística
// DEPENDÊNCIAS:
//   - data/db/RideHistoryEntity.kt → countSinceFlow, queries de histórico
//   - data/db/FinanceDatabase.kt → EarningDao, FinancialGoalDao
//   - data/prefs/PrefsManager.kt → serviceActive, protectionActive
// PROTEÇÕES:
//   - Flow reativo: atualiza em tempo real quando corridas são salvas
//   - Cálculo de progresso de metas usa ganhos reais por período
// ============================================================================

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngbautoroad.data.db.AppDatabase
import com.ngbautoroad.data.db.FinanceDatabase
import com.ngbautoroad.data.db.VehicleProfileEntity
import com.ngbautoroad.data.db.OdometerHistoryEntity
import com.ngbautoroad.data.model.DashboardData
import com.ngbautoroad.data.prefs.PrefsManager
import com.ngbautoroad.ui.criteria.SavedProfile
import com.ngbautoroad.ui.features.FeaturesActivity
import com.ngbautoroad.ui.theme.*
import com.ngbautoroad.domain.OdometerEngine
import com.ngbautoroad.domain.PermissionManager
import com.ngbautoroad.domain.ShiftManager
import com.ngbautoroad.domain.ShiftState
import com.ngbautoroad.service.OverlayService
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.activity.compose.rememberLauncherForActivityResult
import java.util.*

@Composable
fun DashboardTab(prefsManager: PrefsManager, database: AppDatabase) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    // Calcular timestamps
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    val startOfDay = calendar.timeInMillis
    calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
    val startOfWeek = calendar.timeInMillis
    calendar.set(Calendar.DAY_OF_MONTH, 1)
    val startOfMonth = calendar.timeInMillis

    // Item 5.1: Dashboard reativo via Flow — atualiza automaticamente quando novas corridas chegam
    val dao = remember { database.rideHistoryDao() }
    val dashData by remember {
        combine(
            dao.countSinceFlow(startOfDay),
            dao.countSinceFlow(startOfWeek),
            dao.countSinceFlow(startOfMonth),
            dao.getAllFlow()
        ) { todayCount, weekCount, monthCount, allRides ->
            val todayRides = allRides.filter { it.timestamp >= startOfDay }
            val weekRides = allRides.filter { it.timestamp >= startOfWeek }
            val monthRides = allRides.filter { it.timestamp >= startOfMonth }
            DashboardData(
                totalRidesToday = todayCount,
                totalRidesWeek = weekCount,
                totalRidesMonth = monthCount,
                acceptedToday = todayRides.count { it.status == "ACCEPTED" || it.status == "COMPLETED" },
                refusedToday = todayRides.count { it.status == "REFUSED" },
                cancelledToday = todayRides.count { it.status == "CANCELLED" },
                // v6.3.5: Filtrar apenas COMPLETED/ACCEPTED para médias de score
                averageScoreToday = todayRides.filter { it.status == "ACCEPTED" || it.status == "COMPLETED" }.map { it.score }.let { if (it.isEmpty()) 0.0 else it.average() },
                averageScoreWeek = weekRides.filter { it.status == "ACCEPTED" || it.status == "COMPLETED" }.map { it.score }.let { if (it.isEmpty()) 0.0 else it.average() },
                totalEarningsToday = todayRides.filter { it.status == "ACCEPTED" || it.status == "COMPLETED" }.sumOf { it.rideValue },
                totalEarningsWeek = weekRides.filter { it.status == "ACCEPTED" || it.status == "COMPLETED" }.sumOf { it.rideValue },
                totalEarningsMonth = monthRides.filter { it.status == "ACCEPTED" || it.status == "COMPLETED" }.sumOf { it.rideValue },
                // v6.3.5: Filtrar apenas corridas relevantes para métricas financeiras
                bestRideToday = todayRides.filter { it.status == "ACCEPTED" || it.status == "COMPLETED" }.maxOfOrNull { it.rideValue } ?: 0.0,
                averageValuePerKm = todayRides.filter { it.dropoffDistance > 0 && (it.status == "ACCEPTED" || it.status == "COMPLETED") }.map { it.valuePerKm }.let { if (it.isEmpty()) 0.0 else it.average() },
                topPlatform = todayRides.groupBy { it.platform }.maxByOrNull { it.value.size }?.key ?: "-",
                serviceActive = false,
                protectionActive = false
            )
        }
    }.collectAsState(initial = DashboardData())

    // NGB Assistant AI State
    var aiState by remember { mutableStateOf<com.ngbautoroad.ai.AiBrainRepository.BrainState?>(null) }
    LaunchedEffect(Unit) {
        try {
            val dbF = com.ngbautoroad.data.db.FinanceDatabase.getInstance(context)
            val fatigue = com.ngbautoroad.domain.FatigueInsightEngine(context)
            val repo = com.ngbautoroad.ai.AiBrainRepository(dbF, fatigue)
            aiState = repo.getCognitiveStateNow()
        } catch (e: Exception) { e.printStackTrace() }
    }

    // Active Ride for Dashboard
    val activeRideJson by prefsManager.activeRideJsonFlow.collectAsState(initial = null)
    val activeRide = remember(activeRideJson) {
        if (activeRideJson.isNullOrBlank()) null else {
            try { activeRideJson?.let { kotlinx.serialization.json.Json.decodeFromString(com.ngbautoroad.data.model.RideData.serializer(), it) } } catch(e:Exception) { null }
        }
    }

    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // v6.9.3: Banner compacto de permissões pendentes com ação inline
        var permRefreshKey by remember { mutableIntStateOf(0) }
        val allPermissions = remember(permRefreshKey) { PermissionManager.getAllPermissions(context) }
        val missingPerms = allPermissions.filter { it.isRequired && !it.isGranted }
        var showPermBanner by remember { mutableStateOf(true) }

        // Lifecycle observer para refresh ao retornar de settings
        val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    permRefreshKey++
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        // Launchers para permissões runtime (Dashboard)
        val dashLocationLauncher = rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { permRefreshKey++ }
        val dashBgLocationLauncher = rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { permRefreshKey++ }
        val dashNotifLauncher = rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { permRefreshKey++ }
        val dashActivityLauncher = rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { permRefreshKey++ }

        if (missingPerms.isNotEmpty() && showPermBanner) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "${missingPerms.size} permiss${if (missingPerms.size == 1) "ão" else "ões"} pendente${if (missingPerms.size > 1) "s" else ""}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    // Lista clicável de cada permissão faltante
                    missingPerms.forEach { perm ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    when (perm.name) {
                                        "Acessibilidade" -> context.startActivity(
                                            android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                        )
                                        "Sobreposição de Tela" -> context.startActivity(
                                            android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                                data = android.net.Uri.parse("package:${context.packageName}")
                                            }
                                        )
                                        "Notificações" -> {
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                                dashNotifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                        }
                                        "Localização Precisa" -> dashLocationLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
                                        "Localização em Background" -> {
                                            val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(
                                                context, android.Manifest.permission.ACCESS_FINE_LOCATION
                                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                            if (hasFine) {
                                                dashBgLocationLauncher.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                                            } else {
                                                dashLocationLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
                                            }
                                        }
                                        "Reconhecimento de Atividade" -> {
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                                dashActivityLauncher.launch(android.Manifest.permission.ACTIVITY_RECOGNITION)
                                            }
                                        }
                                        "Sem Restrição de Bateria" -> context.startActivity(
                                            android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                                data = android.net.Uri.parse("package:${context.packageName}")
                                            }
                                        )
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${perm.name} — toque para ativar",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // v6.1.1: Seletor rápido de perfil antes de iniciar turno
        ProfileQuickSelector(prefsManager, scope)

        Spacer(modifier = Modifier.height(8.dp))

        // v5.2.0: Card de Turno integrado na Dashboard
        ShiftDashboardCard(context, scope, prefsManager)

        Spacer(modifier = Modifier.height(8.dp))

        // v6.9.18: Painel de Corridas Vigente e Próxima (ActiveRidesHub)
        ActiveRidesHub(database)

        Spacer(modifier = Modifier.height(8.dp))



        // Estado vazio: sem corridas
        if (dashData.totalRidesToday == 0 && dashData.totalRidesWeek == 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.DirectionsCar,
                        contentDescription = "Sem corridas",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Nenhuma corrida registrada",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "Ative o servi\u00e7o e comece a dirigir. Os dados aparecer\u00e3o aqui automaticamente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Ganhos do dia
        EarningsCard(dashData)

        Spacer(modifier = Modifier.height(12.dp))

        // Stats grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.TrendingUp,
                label = "Score Médio",
                value = String.format("%.0f", dashData.averageScoreToday),
                color = when {
                    dashData.averageScoreToday >= 70 -> ScoreGreen
                    dashData.averageScoreToday >= 50 -> ScoreYellow
                    dashData.averageScoreToday >= 30 -> ScoreOrange
                    else -> ScoreRed
                }
            )
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Speed,
                label = "R$/KM Médio",
                value = String.format("R$%.2f", dashData.averageValuePerKm),
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Corridas do dia
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MiniStatCard(
                modifier = Modifier.weight(1f),
                label = "Aceitas",
                value = "${dashData.acceptedToday}",
                color = ScoreGreen
            )
            MiniStatCard(
                modifier = Modifier.weight(1f),
                label = "Recusadas",
                value = "${dashData.refusedToday}",
                color = ScoreRed
            )
            MiniStatCard(
                modifier = Modifier.weight(1f),
                label = "Canceladas",
                value = "${dashData.cancelledToday}",
                color = ScoreOrange
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Resumo semanal/mensal
        PeriodSummaryCard(dashData)

        Spacer(modifier = Modifier.height(12.dp))

        // Plataforma mais usada
        if (dashData.topPlatform.isNotBlank() && dashData.topPlatform != "-") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "Ícone",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Plataforma Principal Hoje",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            dashData.topPlatform,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

                Spacer(modifier = Modifier.height(12.dp))
        // Resumo financeiro e metas
        FinancialSummarySection()

        Spacer(modifier = Modifier.height(12.dp))
        // v6.9.0: Card de insight de fadiga (não intrusivo, apenas dados)
        FatigueInsightCard(context)

        Spacer(modifier = Modifier.height(12.dp))
        // v6.9.0: Card de estado de atividade (dirigindo/parado/caminhando)
        ActivityStateCard(context)

        Spacer(modifier = Modifier.height(12.dp))
        // v6.9.0: Card de sugestão de reserva de manutenção
        MaintenanceAdvisorCard(context)

        Spacer(modifier = Modifier.height(12.dp))
        // v6.5.0: Card de alerta de odômetro com dialog inline
        OdometerAlertCard()
    }
    // v6.3.0: Tutorial guiado no primeiro acesso
    com.ngbautoroad.ui.tutorial.TutorialOverlay(
        screenId = "dashboard",
        steps = com.ngbautoroad.ui.tutorial.TutorialContent.dashboardSteps,
        prefsManager = prefsManager
    )
    } // Box
}

@Composable
fun FinancialSummarySection() {
    val context = LocalContext.current
    val financeDb = remember {
        com.ngbautoroad.data.db.FinanceDatabase.getInstance(context)
    }
    val earningDao = financeDb.earningDao()
    val expenseDao = financeDb.expenseDao()
    val goalDao = financeDb.financialGoalDao()

    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    val todayStart = calendar.timeInMillis
    val todayEnd = System.currentTimeMillis()

    calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
    val weekStart = calendar.timeInMillis

    calendar.set(Calendar.DAY_OF_MONTH, 1)
    val monthStart = calendar.timeInMillis

    val todayEarnings by earningDao.getTotalEarnings(todayStart, todayEnd).collectAsState(initial = 0.0)
    val todayExpenses by expenseDao.getTotalExpenses(todayStart, todayEnd).collectAsState(initial = 0.0)
    val weekEarnings by earningDao.getTotalEarnings(weekStart, todayEnd).collectAsState(initial = 0.0)
    val monthEarnings by earningDao.getTotalEarnings(monthStart, todayEnd).collectAsState(initial = 0.0)
    val monthExpenses by expenseDao.getTotalExpenses(monthStart, todayEnd).collectAsState(initial = 0.0)
    val activeGoals by goalDao.getActiveGoals().collectAsState(initial = emptyList())

    val netToday = (todayEarnings ?: 0.0) - (todayExpenses ?: 0.0)
    val netMonth = (monthEarnings ?: 0.0) - (monthExpenses ?: 0.0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "Resumo Financeiro",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Lucro Hoje", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        String.format("R$ %.2f", netToday),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (netToday >= 0) ScoreGreen else ScoreRed
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Lucro Mês", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        String.format("R$ %.2f", netMonth),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (netMonth >= 0) ScoreGreen else ScoreRed
                    )
                }
            }

            // Alerta de Break-Even
            if (netToday < 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Atenção: Sua margem está negativa. Você ainda não atingiu o break-even diário.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Metas ativas
            if (activeGoals.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Metas",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                activeGoals.take(3).forEach { goal ->
                    val progress = when (goal.period) {
                        "DIA" -> (todayEarnings ?: 0.0) / goal.targetAmount.coerceAtLeast(1.0)
                        "SEMANA" -> (weekEarnings ?: 0.0) / goal.targetAmount.coerceAtLeast(1.0)
                        "MES" -> (monthEarnings ?: 0.0) / goal.targetAmount.coerceAtLeast(1.0)
                        else -> 0.0
                    }.coerceIn(0.0, 1.0)
                    val progressColor = when {
                        progress >= 1.0 -> ScoreGreen
                        progress >= 0.7 -> ScoreYellow
                        progress >= 0.4 -> ScoreOrange
                        else -> ScoreRed
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${goal.title} (${goal.period})",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = progressColor
                        )
                    }
                    LinearProgressIndicator(
                        progress = progress.toFloat(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = progressColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
fun StatusBar(
    serviceActive: Boolean,
    protectionActive: Boolean,
    onToggleService: () -> Unit,
    onToggleProtection: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (serviceActive)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (serviceActive) Icons.Default.PlayCircle else Icons.Default.PauseCircle,
                    contentDescription = "Ícone",
                    tint = if (serviceActive)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        if (serviceActive) "Monitorando" else "Pausado",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (serviceActive)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                    if (protectionActive) {
                        Text(
                            "Proteção ativa",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (serviceActive)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Row {
                IconButton(onClick = onToggleProtection) {
                    Icon(
                        if (protectionActive) Icons.Default.Shield else Icons.Default.GppBad,
                        contentDescription = "Proteção",
                        tint = if (protectionActive) ScoreGreen else ScoreRed
                    )
                }
                IconButton(onClick = onToggleService) {
                    Icon(
                        if (serviceActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = "Serviço",
                        tint = if (serviceActive)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
fun EarningsCard(data: DashboardData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "Ganhos Hoje",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                String.format("R$ %.2f", data.totalEarningsToday),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = ScoreGreen
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Corridas",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${data.totalRidesToday}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "Melhor Corrida",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        String.format("R$ %.2f", data.bestRideToday),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = label, tint = color)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MiniStatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PeriodSummaryCard(data: DashboardData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "Resumo",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PeriodColumn("Semana", data.totalRidesWeek, data.totalEarningsWeek, data.averageScoreWeek)
                PeriodColumn("Mês", data.totalRidesMonth, data.totalEarningsMonth, 0.0)
            }
        }
    }
}

@Composable
fun PeriodColumn(period: String, rides: Int, earnings: Double, avgScore: Double) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            period,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "$rides corridas",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            String.format("R$ %.2f", earnings),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = ScoreGreen
        )
        if (avgScore > 0) {
            Text(
                String.format("Score: %.0f", avgScore),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ============================================================================
// v5.2.0: CARD DE TURNO NA DASHBOARD
// Integrado com ShiftManager e meta financeira
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftDashboardCard(
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    prefsManager: PrefsManager
) {
    val shiftManager = remember { ShiftManager(context) }
    var shiftState by remember { mutableStateOf(shiftManager.loadState()) }
    var goalInput by remember { mutableStateOf(shiftState.goalValue.let { if (it > 0) it.toInt().toString() else "" }) }
    var showGoalEdit by remember { mutableStateOf(false) }
    var showGoalRequiredDialog by remember { mutableStateOf(false) }

    val finDb = remember { FinanceDatabase.getInstance(context) }
    val goalDao = remember { finDb.financialGoalDao() }
    val earningDao = remember { finDb.earningDao() }
    val expenseDao = remember { finDb.expenseDao() }
    val individualExpenseDao = remember { finDb.individualExpenseDao() }

    val activeGoals by goalDao.getActiveGoals().collectAsState(initial = emptyList())
    val dailyGoal = activeGoals.firstOrNull { it.period == "DIA" }

    // Timestamps do dia de hoje para cálculo de lucro líquido / ganho bruto
    val todayStart = remember {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }
    val todayEnd = remember {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        cal.timeInMillis
    }

    val todayEarnings by earningDao.getTotalEarnings(todayStart, todayEnd).collectAsState(initial = 0.0)
    val todayExpenses by expenseDao.getTotalExpenses(todayStart, todayEnd).collectAsState(initial = 0.0)
    val monthlyFixedCosts by individualExpenseDao.getTotalMonthlyRated().collectAsState(initial = 0.0)
    val fixedMonthly = monthlyFixedCosts ?: 0.0

    var goalTypeInput by remember { mutableStateOf("BRUTO") }
    
    // Sincronizar inputs quando a meta diária for carregada do DB
    LaunchedEffect(dailyGoal) {
        dailyGoal?.let {
            goalInput = it.targetAmount.toInt().toString()
            goalTypeInput = it.goalType
        }
    }

    val isLiquido = dailyGoal?.goalType == "LIQUIDO"
    val goalValue = dailyGoal?.targetAmount ?: shiftState.goalValue
    val currentGoalAmount = if (isLiquido) {
        (todayEarnings ?: 0.0) - (todayExpenses ?: 0.0) - (fixedMonthly / 30.0)
    } else {
        todayEarnings ?: 0.0
    }
    val goalProgress = if (goalValue > 0) (currentGoalAmount / goalValue).coerceIn(0.0, 1.5).toFloat() else 0f
    val goalReached = currentGoalAmount >= goalValue

    // Atualizar estado a cada 30s quando turno ativo
    LaunchedEffect(shiftState.isActive, shiftState.isPaused) {
        while (shiftState.isActive && !shiftState.isPaused) {
            delay(30_000L)
            shiftState = shiftManager.loadState()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                shiftState.isActive && goalReached -> Color(0xFF1B5E20)
                shiftState.isActive -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            if (!shiftState.isActive) {
                // === TURNO INATIVO: Botão Iniciar ===
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Turno",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val goalTypeLabel = if (goalTypeInput == "LIQUIDO") "Líquido" else "Bruto"
                            Text(
                                "Meta: R$ $goalInput ($goalTypeLabel)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { showGoalEdit = !showGoalEdit },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Editar meta",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val parsedGoalValue = goalInput.toDoubleOrNull()
                            if (parsedGoalValue == null || parsedGoalValue <= 0.0) {
                                showGoalRequiredDialog = true
                                return@Button
                            }
                            shiftState = shiftManager.startShift(parsedGoalValue)
                            scope.launch {
                                try {
                                    val today = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date())
                                    val existingGoals = goalDao.getActiveGoalsSync()
                                    val existingDailyGoal = existingGoals.firstOrNull { it.period == "DIA" }
                                    if (existingDailyGoal != null) {
                                        goalDao.update(existingDailyGoal.copy(targetAmount = parsedGoalValue, goalType = goalTypeInput))
                                    } else {
                                        goalDao.insert(
                                            com.ngbautoroad.data.db.FinancialGoalEntity(
                                                title = "Meta Diária - $today",
                                                targetAmount = parsedGoalValue,
                                                period = "DIA",
                                                goalType = goalTypeInput,
                                                isActive = true
                                            )
                                        )
                                    }
                                } catch (_: Exception) {}
                                prefsManager.setServiceEnabled(true)
                                prefsManager.setProtectionEnabled(true)
                                try { OverlayService.start(context) } catch (_: Exception) {}
                                try { com.ngbautoroad.service.BubbleService.start(context) } catch (_: Exception) {}
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ScoreGreen)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Iniciar")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Iniciar Turno", color = Color.White)
                    }
                }
                // Campo de edição de meta
                if (showGoalEdit) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = goalInput,
                                onValueChange = { goalInput = it.filter { c -> c.isDigit() || c == '.' } },
                                label = { Text("Meta do dia (R$)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            Button(onClick = {
                                showGoalEdit = false
                                // Sincronizar meta com financeiro
                                scope.launch {
                                    try {
                                        val parsedGoalValueEdit = goalInput.toDoubleOrNull() ?: 200.0
                                        // Verificar se já existe meta diária
                                        val existingGoals = goalDao.getActiveGoalsSync()
                                        val existingDailyGoalEdit = existingGoals.firstOrNull { it.period == "DIA" }
                                        if (existingDailyGoalEdit != null) {
                                            goalDao.update(existingDailyGoalEdit.copy(targetAmount = parsedGoalValueEdit, goalType = goalTypeInput))
                                        } else {
                                            val today = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date())
                                            goalDao.insert(
                                                com.ngbautoroad.data.db.FinancialGoalEntity(
                                                    title = "Meta Diária - $today",
                                                    targetAmount = parsedGoalValueEdit,
                                                    period = "DIA",
                                                    goalType = goalTypeInput,
                                                    isActive = true
                                                )
                                            )
                                        }
                                    } catch (_: Exception) {}
                                }
                            }) {
                                Text("Salvar")
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = goalTypeInput == "BRUTO",
                                onClick = { goalTypeInput = "BRUTO" },
                                label = { Text("Ganho Bruto", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = goalTypeInput == "LIQUIDO",
                                onClick = { goalTypeInput = "LIQUIDO" },
                                label = { Text("Lucro Líquido", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            } else {
                // === TURNO ATIVO: Mostrar progresso ===
                val textColor = if (goalReached) Color.White else Color.Unspecified
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (shiftState.isPaused) "Turno Pausado" else "Turno Ativo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    if (goalReached) {
                        Text(
                            "META ATINGIDA!",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF76FF03)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                // Barra de progresso da meta
                LinearProgressIndicator(
                    progress = goalProgress.coerceIn(0f, 1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (goalReached) Color(0xFF76FF03) else ScoreGreen,
                    trackColor = if (goalReached) Color.White.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                val currentLabel = if (isLiquido) "Líquido" else "Bruto"
                Text(
                    "R$ ${"%.2f".format(currentGoalAmount)} / R$ ${"%.0f".format(goalValue)} ($currentLabel)",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (goalReached) Color.White.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                // Métricas do turno
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Tempo", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, maxLines = 1, color = textColor.copy(alpha = 0.7f))
                        Text(
                            "${shiftState.elapsedMinutes} min",
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            color = textColor
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1.1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("R$/h", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, maxLines = 1, color = textColor.copy(alpha = 0.7f))
                        Text(
                            "R$ ${"%.2f".format(shiftState.valuePerHour)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            color = textColor
                        )
                    }
                    Column(
                        modifier = Modifier.weight(0.9f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Corridas", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, maxLines = 1, color = textColor.copy(alpha = 0.7f))
                        Text(
                            "${shiftState.ridesCount}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            color = textColor
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1.1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Aceitas/Rec.", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, maxLines = 1, color = textColor.copy(alpha = 0.7f))
                        Text(
                            "${shiftState.ridesAccepted}/${shiftState.ridesRejected}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            color = textColor
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                // Botões de controle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (shiftState.isPaused) {
                        Button(
                            onClick = { shiftState = shiftManager.resumeShift(shiftState) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = ScoreGreen)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Retomar")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retomar", color = Color.White)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { shiftState = shiftManager.pauseShift(shiftState) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = "Pausar")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pausar")
                        }
                    }
                    Button(
                        onClick = {
                            // Salvar histórico do turno antes de encerrar
                            scope.launch {
                                try {
                                    // Salvar no Room DB via ShiftHistoryManager (dados completos + GPS)
                                    com.ngbautoroad.domain.ShiftHistoryManager(context).saveShiftToHistory(shiftState)
                                } catch (_: Exception) {}
                            }
                            shiftState = shiftManager.endShift()
                            // Parar todos os serviços ao encerrar turno
                            try { OverlayService.stop(context) } catch (_: Exception) {}
                            try { com.ngbautoroad.service.BubbleService.stop(context) } catch (_: Exception) {}
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Encerrar", tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Fim", color = Color.White, maxLines = 1, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    // Dialog: Meta obrigatória
    if (showGoalRequiredDialog) {
        AlertDialog(
            onDismissRequest = { showGoalRequiredDialog = false },
            title = { Text("⚠️ Meta não configurada") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Para iniciar o turno, você precisa definir uma meta diária de ganho.")
                    Text("Informe o valor que deseja ganhar hoje:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = goalInput,
                        onValueChange = { goalInput = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Meta do dia (R$)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Tipo da meta:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = goalTypeInput == "BRUTO",
                            onClick = { goalTypeInput = "BRUTO" },
                            label = { Text("Ganho Bruto", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = goalTypeInput == "LIQUIDO",
                            onClick = { goalTypeInput = "LIQUIDO" },
                            label = { Text("Lucro Líquido", fontSize = 11.sp) }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsedGoalValueDialog = goalInput.toDoubleOrNull()
                        if (parsedGoalValueDialog != null && parsedGoalValueDialog > 0.0) {
                            showGoalRequiredDialog = false
                            shiftState = shiftManager.startShift(parsedGoalValueDialog)
                            scope.launch {
                                try {
                                    val finDbDialog = FinanceDatabase.getInstance(context)
                                    val goalDaoDialog = finDbDialog.financialGoalDao()
                                    val today = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date())
                                    val existingGoals = goalDaoDialog.getActiveGoalsSync()
                                    val existingDailyGoalDialog = existingGoals.firstOrNull { it.period == "DIA" }
                                    if (existingDailyGoalDialog != null) {
                                        goalDaoDialog.update(existingDailyGoalDialog.copy(targetAmount = parsedGoalValueDialog, goalType = goalTypeInput))
                                    } else {
                                        goalDaoDialog.insert(
                                            com.ngbautoroad.data.db.FinancialGoalEntity(
                                                title = "Meta Diária - $today",
                                                targetAmount = parsedGoalValueDialog,
                                                period = "DIA",
                                                goalType = goalTypeInput,
                                                isActive = true
                                            )
                                        )
                                    }
                                } catch (_: Exception) {}
                                prefsManager.setServiceEnabled(true)
                                prefsManager.setProtectionEnabled(true)
                                try { OverlayService.start(context) } catch (_: Exception) {}
                                try { com.ngbautoroad.service.BubbleService.start(context) } catch (_: Exception) {}
                            }
                        }
                    },
                    enabled = goalInput.toDoubleOrNull()?.let { it > 0.0 } == true
                ) { Text("Iniciar Turno") }
            },
            dismissButton = {
                TextButton(onClick = { showGoalRequiredDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

// ============================================================================
// v6.3.0: Card de Perfis com janela flutuante e favoritos (até 3)
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileQuickSelector(prefsManager: PrefsManager, scope: kotlinx.coroutines.CoroutineScope) {
    val profilesJson by prefsManager.profilesJsonFlow.collectAsState(initial = "[]")
    val activeProfileId by prefsManager.activeProfileIdFlow.collectAsState(initial = 0)
    val favoritesJson by prefsManager.favoriteProfilesFlow.collectAsState(initial = "[]")
    var showDialog by remember { mutableStateOf(false) }

    data class QuickProfile(val id: Int, val name: String, val isFavorite: Boolean)

    val favoriteIds = remember(favoritesJson) {
        try {
            kotlinx.serialization.json.Json.decodeFromString<List<Int>>(favoritesJson)
        } catch (_: Exception) { emptyList() }
    }

    val allProfiles = remember(profilesJson, favoriteIds) {
        try {
            val jsonParser = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            // v6.9.2: Usar SavedProfile.serializer() para garantir que o nome personalizado
            // seja lido corretamente (fix definitivo do bug de nome no Dashboard)
            val savedProfiles = jsonParser.decodeFromString<List<SavedProfile>>(profilesJson)
            savedProfiles.map { sp ->
                QuickProfile(
                    id = sp.id,
                    name = sp.name.ifBlank { "Perfil ${sp.id}" },
                    isFavorite = sp.id in favoriteIds
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    val favoriteProfiles = allProfiles.filter { it.isFavorite }.take(3)
    val activeProfile = allProfiles.find { it.id == activeProfileId }

    if (allProfiles.isNotEmpty()) {
        Card(
            onClick = { showDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                "Perfil Ativo",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                activeProfile?.name ?: "Nenhum selecionado",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Icon(
                        Icons.Default.UnfoldMore,
                        contentDescription = "Selecionar perfil",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Favoritos (até 3) como chips rápidos
                if (favoriteProfiles.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        favoriteProfiles.forEach { profile ->
                            FilterChip(
                                selected = activeProfileId == profile.id,
                                onClick = {
                                    scope.launch {
                                        applyProfile(prefsManager, profilesJson, profile.id)
                                    }
                                },
                                label = { Text(profile.name, fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // Dialog flutuante com lista completa de perfis
        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("Selecionar Perfil", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        allProfiles.forEach { profile ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (activeProfileId == profile.id)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else
                                            Color.Transparent
                                    )
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            scope.launch {
                                                applyProfile(prefsManager, profilesJson, profile.id)
                                            }
                                            showDialog = false
                                        }
                                ) {
                                    if (activeProfileId == profile.id) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.RadioButtonUnchecked,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        profile.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (activeProfileId == profile.id) FontWeight.Bold else FontWeight.Normal
                                    )
                                }

                                // Botão favoritar
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            val currentFavs = favoriteIds.toMutableList()
                                            if (profile.id in currentFavs) {
                                                currentFavs.remove(profile.id)
                                            } else if (currentFavs.size < 3) {
                                                currentFavs.add(profile.id)
                                            }
                                            prefsManager.saveFavoriteProfiles(
                                                "[" + currentFavs.joinToString(",") + "]"
                                            )
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        if (profile.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = "Favoritar",
                                        tint = if (profile.isFavorite) ScoreYellow else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text("Fechar")
                    }
                }
            )
        }
    }
}

private suspend fun applyProfile(prefsManager: PrefsManager, profilesJson: String, profileId: Int) {
    prefsManager.saveActiveProfileId(profileId)
    try {
        val jsonParser = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val profiles = jsonParser.decodeFromString<List<com.ngbautoroad.ui.criteria.SavedProfile>>(profilesJson)
        // v6.9.3: Buscar pelo ID real, não pela posição na lista
        val profile = profiles.find { it.id == profileId }
        if (profile != null) {
            prefsManager.saveAutoPilotMode(profile.autoPilotMode)
            prefsManager.saveAutoPilotMinScore(profile.minAcceptScore)
            prefsManager.saveAutoPilotMaxRefuseScore(profile.maxRefuseScore)
            // Aplicar pesos e thresholds também
            prefsManager.saveCriteriaWeights(profile.weights)
            prefsManager.saveDriverThresholds(profile.thresholds)
        }
    } catch (_: Exception) {}
}

// ============================================================================
// v6.5.0: Card de Alerta de Odômetro com Dialog Inline
// ============================================================================

@Composable
fun OdometerAlertCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val financeDb = remember { FinanceDatabase.getInstance(context) }
    val vehicleProfileDao = remember { financeDb.vehicleProfileDao() }
    val odometerHistoryDao = remember { financeDb.odometerHistoryDao() }

    val activeVehicle by vehicleProfileDao.getActiveVehicle().collectAsState(initial = null)

    // Estado do dialog inline
    var showOdometerDialog by remember { mutableStateOf(false) }
    var odometerInput by remember { mutableStateOf("") }

    val vehicle = activeVehicle ?: return // Sem veículo ativo, não mostra nada

    // Verificar se precisa alertar
    val daysSinceUpdate = if (vehicle.lastOdometerUpdate > 0) {
        ((System.currentTimeMillis() - vehicle.lastOdometerUpdate) / (1000 * 60 * 60 * 24)).toInt()
    } else {
        -1 // Nunca atualizado
    }

    val shouldAlert = vehicle.currentOdometer == 0 || daysSinceUpdate < 0 || daysSinceUpdate >= vehicle.odometerAlertDays

    if (!shouldAlert) return // Odômetro atualizado recentemente, não mostra

    // Determinar mensagem e cor
    val (alertMessage, alertColor) = when {
        vehicle.currentOdometer == 0 -> Pair(
            "Informe o odômetro do seu ${vehicle.brand} ${vehicle.model} para cálculos precisos de manutenção.",
            MaterialTheme.colorScheme.error
        )
        daysSinceUpdate >= vehicle.odometerAlertDays * 2 -> Pair(
            "Odômetro desatualizado há ${daysSinceUpdate} dias. Atualize para manter a precisão.",
            MaterialTheme.colorScheme.error
        )
        else -> Pair(
            "Última atualização há ${daysSinceUpdate} dias. Toque para atualizar o odômetro.",
            ScoreOrange
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { 
                odometerInput = if (vehicle.currentOdometer > 0) vehicle.currentOdometer.toString() else ""
                showOdometerDialog = true 
            },
        colors = CardDefaults.cardColors(
            containerColor = alertColor.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Speed,
                contentDescription = "Odômetro",
                tint = alertColor,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Atualizar Odômetro",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = alertColor
                )
                Text(
                    alertMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                if (vehicle.currentOdometer > 0) {
                    Text(
                        "Atual: ${String.format("%,d", vehicle.currentOdometer)} km",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
            Icon(
                Icons.Default.Edit,
                contentDescription = "Editar",
                tint = alertColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }

    // Dialog inline para atualizar odômetro
    if (showOdometerDialog) {
        AlertDialog(
            onDismissRequest = { showOdometerDialog = false },
            icon = { Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Atualizar Odômetro") },
            text = {
                Column {
                    Text(
                        "${vehicle.brand} ${vehicle.model} ${vehicle.year}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (vehicle.currentOdometer > 0) {
                        Text(
                            "Último registro: ${String.format("%,d", vehicle.currentOdometer)} km",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = odometerInput,
                        onValueChange = { odometerInput = it.filter { c -> c.isDigit() } },
                        label = { Text("Quilometragem atual (km)") },
                        placeholder = { Text("Ex: 15000") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Consulte o painel do veículo e informe o valor exato.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newOdometer = odometerInput.toIntOrNull() ?: 0
                        if (newOdometer > 0) {
                            scope.launch {
                                // v6.7.0: Delegar toda lógica ao OdometerEngine
                                // Isso inclui: histórico, EWMA, outlier detection, max factor 5.0
                                val odometerEngine = OdometerEngine(
                                    vehicleProfileDao = vehicleProfileDao,
                                    odometerHistoryDao = odometerHistoryDao,
                                    earningDao = financeDb.earningDao()
                                )
                                odometerEngine.processOdometerUpdate(vehicle, newOdometer)
                            }
                            showOdometerDialog = false
                        }
                    },
                    enabled = (odometerInput.toIntOrNull() ?: 0) > 0 && 
                              (odometerInput.toIntOrNull() ?: 0) >= vehicle.currentOdometer
                ) { Text("Salvar") }
                // v6.7.0 Ruptura #5: Alerta se valor informado é menor que estimado
                val inputVal = odometerInput.toIntOrNull() ?: 0
                if (inputVal > 0 && vehicle.currentOdometer > 0 && inputVal < vehicle.currentOdometer) {
                    Text(
                        "⚠️ Valor menor que o último registro (${String.format("%,d", vehicle.currentOdometer)} km). Verifique.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showOdometerDialog = false }) { Text("Depois") }
            }
        )
    }
}

// ============================================================================
// v6.9.0: Cards de IA para Dashboard (não intrusivos)
// ============================================================================

@Composable
private fun FatigueInsightCard(context: android.content.Context) {
    val scope = rememberCoroutineScope()
    var insight by remember { mutableStateOf<com.ngbautoroad.domain.FatigueInsight?>(null) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val engine = com.ngbautoroad.domain.FatigueInsightEngine(context)
            insight = engine.getQuickInsight()
        }
    }

    insight?.let { data ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            data.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                if (expanded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        data.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    data.dataComparison?.let { comp ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            comp.conclusion,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityStateCard(context: android.content.Context) {
    val detector = remember { com.ngbautoroad.domain.ActivityStateDetector(context) }
    val state = detector.currentState

    // Só mostra se o turno está ativo e o estado é conhecido
    if (state == com.ngbautoroad.domain.DriverActivityState.UNKNOWN) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (state) {
                com.ngbautoroad.domain.DriverActivityState.DRIVING -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                com.ngbautoroad.domain.DriverActivityState.STILL -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                state.emoji,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    state.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    when (state.gpsMode) {
                        com.ngbautoroad.domain.GpsMode.ACTIVE -> "GPS ativo | Contando KM"
                        com.ngbautoroad.domain.GpsMode.ECONOMY -> "GPS economia | Aguardando"
                        com.ngbautoroad.domain.GpsMode.OFF -> "GPS pausado | KM não contado"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MaintenanceAdvisorCard(context: android.content.Context) {
    val scope = rememberCoroutineScope()
    var suggestion by remember { mutableStateOf<com.ngbautoroad.domain.ReserveAdjustmentSuggestion?>(null) }
    var vehicleCostPerKm by remember { mutableStateOf(0.0) }
    var visible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val db = FinanceDatabase.getInstance(context)
                val active = db.vehicleProfileDao().getActiveVehicleSync()
                if (active != null) {
                    vehicleCostPerKm = active.costPerKm
                    val advisor = com.ngbautoroad.domain.MaintenanceReserveAdvisor(context)
                    val odometer = active.currentOdometer
                    // v6.9.18: Usar a taxa de reserva real salva em SharedPreferences
                    val currentRate = advisor.getReserveRate()
                    val reserve = currentRate * odometer
                    suggestion = advisor.analyze(active, odometer, reserve)
                }
            } catch (_: Exception) { }
        }
    }

    if (!visible) return

    suggestion?.let { data ->
        val advisor = remember { com.ngbautoroad.domain.MaintenanceReserveAdvisor(context) }
        if (!advisor.shouldShowSuggestion(data.nextMaintenanceName)) return

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, ScoreOrange)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Build,
                        contentDescription = null,
                        tint = if (data.isUrgent) MaterialTheme.colorScheme.error
                               else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (data.isUrgent) "Reserva de manutenção insuficiente" else "💡 IA Advisor — Reserva de Manutenção",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                // Aviso se custo/km não configurado (usando valor padrão R$0,03)
                if (vehicleCostPerKm <= 0.0) {
                    Text(
                        "⚠ Custo/km do veículo não configurado. Configure em Finanças → Veículos para cálculos de combustível precisos.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                Text(
                    data.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Explicação do que é a reserva de manutenção
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "A reserva de manutenção é uma provisão por km rodado para cobrir despesas futuras com o veículo.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            advisor.markSuggestionAccepted(data.nextMaintenanceName)
                        }
                        visible = false
                    }) {
                        Text("Ignorar", color = MaterialTheme.colorScheme.outline)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                advisor.updateReserveRate(data.suggestedRatePerKm)
                                advisor.markSuggestionAccepted(data.nextMaintenanceName)
                            }
                            visible = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Aplicar R$ ${"%.2f".format(data.suggestedRatePerKm)}/km", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveRidesHub(database: AppDatabase) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { database.rideHistoryDao() }

    val allRides by dao.getAllFlow().collectAsState(initial = emptyList())
    val activeRides = remember(allRides) {
        val sinceTime = System.currentTimeMillis() - 12 * 60 * 60 * 1000L
        allRides.filter { it.status == "ACCEPTED" && it.timestamp >= sinceTime }
            .sortedBy { it.timestamp }
    }

    // v7.4.0: Clean stale ACCEPTED rides older than 2 hours
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val staleTime = System.currentTimeMillis() - 2 * 60 * 60 * 1000L
            dao.expireStaleAcceptedRides(staleTime)
        }
    }

    // v7.3.0: Notificação expandida com dados da corrida ativa
    LaunchedEffect(activeRides) {
        if (activeRides.isNotEmpty()) {
            showActiveRideNotification(context, activeRides.first())
        } else {
            val nm = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.cancel(1003)
        }
    }

    if (activeRides.isEmpty()) return

    val currentRide = activeRides.firstOrNull()
    val nextRide = if (activeRides.size > 1) activeRides[1] else null

    var showCurrentDialog by remember { mutableStateOf(false) }
    var showNextDialog by remember { mutableStateOf(false) }

    // v7.4.0: Two buttons side by side instead of stacked cards
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (currentRide != null) {
            Button(
                onClick = { showCurrentDialog = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Icon(
                    Icons.Default.DirectionsCar,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Corrida Atual",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (nextRide != null) {
            OutlinedButton(
                onClick = { showNextDialog = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.Default.Queue,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Próxima Corrida",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    // Popup dialogs
    if (showCurrentDialog && currentRide != null) {
        RideDetailDialog(
            ride = currentRide,
            title = "Corrida em Andamento",
            showFinishButton = true,
            dao = dao,
            scope = scope,
            onDismiss = { showCurrentDialog = false }
        )
    }
    if (showNextDialog && nextRide != null) {
        RideDetailDialog(
            ride = nextRide,
            title = "Próxima Corrida",
            showFinishButton = false,
            dao = dao,
            scope = scope,
            onDismiss = { showNextDialog = false }
        )
    }
}

/**
 * v7.4.0: Dialog showing ride details with X to close.
 * Shows neighborhoods instead of km distances for better readability.
 */
@Composable
fun RideDetailDialog(
    ride: com.ngbautoroad.data.db.RideHistoryEntity,
    title: String,
    showFinishButton: Boolean,
    dao: com.ngbautoroad.data.db.RideHistoryDao,
    scope: kotlinx.coroutines.CoroutineScope,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Fechar")
                }
            }
        },
        text = {
            Column {
                // Platform
                Text(
                    ride.platform,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(12.dp))

                // Values - large fonts
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Valor", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "R$ ${"%.2f".format(ride.rideValue)}",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("R$/KM", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${"%.2f".format(ride.valuePerKm)}",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("R$/Hora", style = MaterialTheme.typography.bodyMedium)
                        val rpH = if (ride.rideDuration > 0) (ride.rideValue / ride.rideDuration) * 60.0 else 0.0
                        Text(
                            "${"%.0f".format(rpH)}",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                @Suppress("DEPRECATION")
                Divider()
                Spacer(Modifier.height(12.dp))

                // Neighborhoods instead of km
                if (ride.pickupNeighborhood.isNotBlank() || ride.dropoffNeighborhood.isNotBlank()) {
                    Box(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            "${ride.pickupNeighborhood.ifBlank { "—" }} ➤ ${ride.dropoffNeighborhood.ifBlank { "—" }}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // Details row
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${ride.rideDuration.toInt()} min",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "⭐ ${"%.1f".format(ride.passengerRating)}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "${ride.score.toInt()} pts",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = when {
                            ride.score >= 70 -> ScoreGreen
                            ride.score >= 50 -> ScoreYellow
                            else -> ScoreRed
                        }
                    )
                }

                // Finish button
                if (showFinishButton) {
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                dao.updateStatusById(ride.id, "COMPLETED")
                                com.ngbautoroad.service.RideAccessibilityService.instance?.lifecycleManager?.onRideCompleted(ride.rideValue)
                            }
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Finalizar Viagem")
                    }
                }
            }
        },
        confirmButton = {} // No confirm button, X in title handles close
    )
}

/**
 * v7.3.0: Notificação expandida com dados da corrida ativa.
 * Visível na tela de bloqueio, Android Auto e shade de notificações.
 * Texto grande e legível sem precisar abrir o app.
 */
private fun showActiveRideNotification(context: android.content.Context, ride: com.ngbautoroad.data.db.RideHistoryEntity) {
    val channelId = "ngb_active_ride"
    val notifManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        val channel = android.app.NotificationChannel(
            channelId, "Corrida Ativa", android.app.NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Informações da corrida em andamento"
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        notifManager.createNotificationChannel(channel)
    }

    val route = buildString {
        if (ride.pickupNeighborhood.isNotBlank()) append(ride.pickupNeighborhood.trim())
        if (ride.dropoffNeighborhood.isNotBlank()) append(" → ${ride.dropoffNeighborhood.trim()}")
    }

    val rpH = if (ride.rideDuration > 0) (ride.rideValue / ride.rideDuration) * 60.0 else 0.0
    val details = buildString {
        append("R$ ${"%.2f".format(ride.rideValue)}")
        append("  •  ${"%.2f".format(ride.valuePerKm)} R$/km")
        append("  •  ${"%.0f".format(rpH)} R$/h")
        append("\n${"%.1f".format(ride.pickupDistance)}km busca  •  ${"%.1f".format(ride.dropoffDistance)}km destino  •  ${ride.rideDuration.toInt()}min")
        append("\n⭐ ${"%.1f".format(ride.passengerRating)}  •  Score: ${ride.score.toInt()} pts")
    }

    val openIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    val pendingIntent = android.app.PendingIntent.getActivity(
        context, 0, openIntent,
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
    )

    val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
        .setContentTitle("${ride.platform} — $route")
        .setContentText("R$ ${"%.2f".format(ride.rideValue)}  •  ${"%.2f".format(ride.valuePerKm)} R$/km")
        .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(details))
        .setSmallIcon(android.R.drawable.ic_menu_directions)
        .setOngoing(true)
        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
        .setCategory(androidx.core.app.NotificationCompat.CATEGORY_NAVIGATION)
        .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
        .setContentIntent(pendingIntent)
        .build()

    notifManager.notify(1003, notification)
}
