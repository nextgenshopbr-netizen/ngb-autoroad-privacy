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
import com.ngbautoroad.data.model.DashboardData
import com.ngbautoroad.data.prefs.PrefsManager
import com.ngbautoroad.ui.features.FeaturesActivity
import com.ngbautoroad.ui.theme.*
import com.ngbautoroad.domain.ShiftManager
import com.ngbautoroad.domain.ShiftState
import com.ngbautoroad.service.OverlayService
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import java.util.*

@Composable
fun DashboardTab(prefsManager: PrefsManager, database: AppDatabase) {
    val scope = rememberCoroutineScope()
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
                averageScoreToday = todayRides.map { it.score }.let { if (it.isEmpty()) 0.0 else it.average() },
                averageScoreWeek = weekRides.map { it.score }.let { if (it.isEmpty()) 0.0 else it.average() },
                totalEarningsToday = todayRides.filter { it.status == "ACCEPTED" || it.status == "COMPLETED" }.sumOf { it.rideValue },
                totalEarningsWeek = weekRides.filter { it.status == "ACCEPTED" || it.status == "COMPLETED" }.sumOf { it.rideValue },
                totalEarningsMonth = monthRides.filter { it.status == "ACCEPTED" || it.status == "COMPLETED" }.sumOf { it.rideValue },
                bestRideToday = todayRides.maxOfOrNull { it.rideValue } ?: 0.0,
                averageValuePerKm = todayRides.filter { it.dropoffDistance > 0 }.map { it.valuePerKm }.let { if (it.isEmpty()) 0.0 else it.average() },
                topPlatform = todayRides.groupBy { it.platform }.maxByOrNull { it.value.size }?.key ?: "-",
                serviceActive = false,
                protectionActive = false
            )
        }
    }.collectAsState(initial = DashboardData())

    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // v6.3.0: Header com botão de ajuda
        val context = LocalContext.current
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Início", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            com.ngbautoroad.ui.tutorial.HelpButton(screenId = "dashboard")
        }
        Spacer(modifier = Modifier.height(8.dp))

        // v6.1.1: Seletor rápido de perfil antes de iniciar turno
        ProfileQuickSelector(prefsManager, scope)

        Spacer(modifier = Modifier.height(8.dp))

        // v5.2.0: Card de Turno integrado na Dashboard
        ShiftDashboardCard(context, scope, prefsManager)

        Spacer(modifier = Modifier.height(12.dp))

        // v6.3.2: Botão de acesso rápido a Recursos Avançados (IA)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { context.startActivity(Intent(context, FeaturesActivity::class.java)) },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Recursos Avançados",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1
                        )
                        Text(
                            "IA Local, Ranking, Relatórios, Exportar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            maxLines = 1
                        )
                    }
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

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
            horizontalArrangement = Arrangement.spacedBy(12.dp)
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
            horizontalArrangement = Arrangement.spacedBy(12.dp)
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
        FinancialSummarySection(database)
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
fun FinancialSummarySection(database: AppDatabase) {
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
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
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
                shiftState.isActive && shiftState.goalReached -> Color(0xFF1B5E20)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Turno",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Meta: R$ $goalInput",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Botão editar meta
                        IconButton(onClick = { showGoalEdit = !showGoalEdit }) {
                            Icon(Icons.Default.Edit, contentDescription = "Editar meta")
                        }
                        // Botão Iniciar Turno
                        Button(
                            onClick = {
                                val goalValue = goalInput.toDoubleOrNull()
                                // Verificar se meta foi configurada (obrigatório)
                                if (goalValue == null || goalValue <= 0.0) {
                                    showGoalRequiredDialog = true
                                    return@Button
                                }
                                shiftState = shiftManager.startShift(goalValue)
                                // Criar meta diária automática no módulo financeiro
                                scope.launch {
                                    try {
                                        val finDb = FinanceDatabase.getInstance(context)
                                        val goalDao = finDb.financialGoalDao()
                                        val today = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date())
                                        val existingGoals = goalDao.getActiveGoalsSync()
                                        val dailyGoal = existingGoals.firstOrNull { it.period == "DIA" }
                                        if (dailyGoal != null) {
                                            goalDao.update(dailyGoal.copy(targetAmount = goalValue))
                                        } else {
                                            goalDao.insert(
                                                com.ngbautoroad.data.db.FinancialGoalEntity(
                                                    title = "Meta Diária - $today",
                                                    targetAmount = goalValue,
                                                    period = "DIA",
                                                    isActive = true
                                                )
                                            )
                                        }
                                    } catch (_: Exception) {}
                                    // Ativar todos os serviços ao iniciar turno
                                    prefsManager.setServiceEnabled(true)
                                    prefsManager.setProtectionEnabled(true)
                                    try { OverlayService.start(context) } catch (_: Exception) {}
                                    try { com.ngbautoroad.service.BubbleService.start(context) } catch (_: Exception) {}
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ScoreGreen
                            )
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Iniciar")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Iniciar Turno", color = Color.White)
                        }
                    }
                }
                // Campo de edição de meta
                if (showGoalEdit) {
                    Spacer(modifier = Modifier.height(8.dp))
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
                                    val finDb = FinanceDatabase.getInstance(context)
                                    val goalDao = finDb.financialGoalDao()
                                    val goalValue = goalInput.toDoubleOrNull() ?: 200.0
                                    // Verificar se já existe meta diária
                                    val existingGoals = goalDao.getActiveGoalsSync()
                                    val dailyGoal = existingGoals.firstOrNull { it.period == "DIA" }
                                    if (dailyGoal != null) {
                                        goalDao.update(dailyGoal.copy(targetAmount = goalValue))
                                    } else {
                                        goalDao.insert(
                                            com.ngbautoroad.data.db.FinancialGoalEntity(
                                                title = "Meta Diária",
                                                targetAmount = goalValue,
                                                period = "DIA",
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
                }
            } else {
                // === TURNO ATIVO: Mostrar progresso ===
                val textColor = if (shiftState.goalReached) Color.White else Color.Unspecified
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
                    if (shiftState.goalReached) {
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
                    progress = shiftState.goalProgress.coerceIn(0f, 1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (shiftState.goalReached) Color(0xFF76FF03) else ScoreGreen,
                    trackColor = if (shiftState.goalReached) Color.White.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "R$ ${"%.2f".format(shiftState.totalEarned)} / R$ ${"%.0f".format(shiftState.goalValue)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (shiftState.goalReached) Color.White.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                // Métricas do turno
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Tempo", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.7f))
                        Text(
                            "${shiftState.elapsedMinutes} min",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("R$/h", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.7f))
                        Text(
                            "R$ ${"%.2f".format(shiftState.valuePerHour)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Corridas", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.7f))
                        Text(
                            "${shiftState.ridesCount}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Aceitas/Rec.", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.7f))
                        Text(
                            "${shiftState.ridesAccepted}/${shiftState.ridesRejected}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
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
                                    val finDb = FinanceDatabase.getInstance(context)
                                    val shiftHistoryPrefs = context.getSharedPreferences("shift_history", android.content.Context.MODE_PRIVATE)
                                    val historyCount = shiftHistoryPrefs.getInt("count", 0)
                                    shiftHistoryPrefs.edit()
                                        .putLong("shift_${historyCount}_start", shiftState.startTimeMs)
                                        .putLong("shift_${historyCount}_end", System.currentTimeMillis())
                                        .putFloat("shift_${historyCount}_earned", shiftState.totalEarned.toFloat())
                                        .putInt("shift_${historyCount}_rides", shiftState.ridesCount)
                                        .putInt("shift_${historyCount}_accepted", shiftState.ridesAccepted)
                                        .putInt("shift_${historyCount}_rejected", shiftState.ridesRejected)
                                        .putFloat("shift_${historyCount}_goal", shiftState.goalValue.toFloat())
                                        .putLong("shift_${historyCount}_elapsed", shiftState.elapsedMs)
                                        .putInt("count", historyCount + 1)
                                        .apply()
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
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val goalValue = goalInput.toDoubleOrNull()
                        if (goalValue != null && goalValue > 0.0) {
                            showGoalRequiredDialog = false
                            shiftState = shiftManager.startShift(goalValue)
                            scope.launch {
                                try {
                                    val finDb = FinanceDatabase.getInstance(context)
                                    val goalDao = finDb.financialGoalDao()
                                    val today = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date())
                                    val existingGoals = goalDao.getActiveGoalsSync()
                                    val dailyGoal = existingGoals.firstOrNull { it.period == "DIA" }
                                    if (dailyGoal != null) {
                                        goalDao.update(dailyGoal.copy(targetAmount = goalValue))
                                    } else {
                                        goalDao.insert(
                                            com.ngbautoroad.data.db.FinancialGoalEntity(
                                                title = "Meta Diária - $today",
                                                targetAmount = goalValue,
                                                period = "DIA",
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
            val parsed = kotlinx.serialization.json.Json.decodeFromString<List<kotlinx.serialization.json.JsonObject>>(profilesJson)
            parsed.mapIndexed { idx, obj ->
                val profileId = obj["id"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() } ?: (idx + 1)
                QuickProfile(
                    id = profileId,
                    name = obj["name"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { n -> n.isNotBlank() } } ?: "Perfil ${idx + 1}",
                    isFavorite = profileId in favoriteIds
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
        val parsed = kotlinx.serialization.json.Json.decodeFromString<List<kotlinx.serialization.json.JsonObject>>(profilesJson)
        val profileObj = parsed.getOrNull(profileId - 1)
        if (profileObj != null) {
            val mode = profileObj["autoPilotMode"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: "OFF"
            val minScore = profileObj["minAcceptScore"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() } ?: 75
            val maxRefuse = profileObj["maxRefuseScore"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() } ?: 40
            prefsManager.saveAutoPilotMode(mode)
            prefsManager.saveAutoPilotMinScore(minScore)
            prefsManager.saveAutoPilotMaxRefuseScore(maxRefuse)
        }
    } catch (_: Exception) {}
}
