package com.ngbautoroad.ui.dashboard

import android.content.Intent
import androidx.compose.foundation.background
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
import com.ngbautoroad.data.model.DashboardData
import com.ngbautoroad.data.prefs.PrefsManager
import com.ngbautoroad.ui.finance.FinanceActivity
import com.ngbautoroad.ui.theme.*
import kotlinx.coroutines.launch
import java.util.*

@Composable
fun DashboardTab(prefsManager: PrefsManager, database: AppDatabase) {
    val scope = rememberCoroutineScope()
    var dashData by remember { mutableStateOf(DashboardData()) }
    val serviceEnabled by prefsManager.serviceEnabledFlow.collectAsState(initial = false)
    val protectionEnabled by prefsManager.protectionEnabledFlow.collectAsState(initial = false)

    // Calcular timestamps
    val calendar = Calendar.getInstance()
    val now = calendar.timeInMillis

    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    val startOfDay = calendar.timeInMillis

    calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
    val startOfWeek = calendar.timeInMillis

    calendar.set(Calendar.DAY_OF_MONTH, 1)
    val startOfMonth = calendar.timeInMillis

    // Carregar dados do dashboard
    LaunchedEffect(Unit) {
        val dao = database.rideHistoryDao()
        dashData = DashboardData(
            totalRidesToday = dao.countSince(startOfDay),
            totalRidesWeek = dao.countSince(startOfWeek),
            totalRidesMonth = dao.countSince(startOfMonth),
            acceptedToday = dao.countSinceWithStatus(startOfDay, "ACCEPTED"),
            refusedToday = dao.countSinceWithStatus(startOfDay, "REFUSED"),
            cancelledToday = dao.countSinceWithStatus(startOfDay, "CANCELLED"),
            averageScoreToday = dao.averageScoreSince(startOfDay) ?: 0.0,
            averageScoreWeek = dao.averageScoreSince(startOfWeek) ?: 0.0,
            totalEarningsToday = dao.totalEarningsSince(startOfDay) ?: 0.0,
            totalEarningsWeek = dao.totalEarningsSince(startOfWeek) ?: 0.0,
            totalEarningsMonth = dao.totalEarningsSince(startOfMonth) ?: 0.0,
            bestRideToday = dao.bestRideSince(startOfDay) ?: 0.0,
            averageValuePerKm = dao.averageValuePerKmSince(startOfDay) ?: 0.0,
            topPlatform = dao.topPlatformSince(startOfDay) ?: "-",
            serviceActive = serviceEnabled,
            protectionActive = protectionEnabled
        )
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Status bar
        StatusBar(
            serviceActive = serviceEnabled,
            protectionActive = protectionEnabled,
            onToggleService = {
                scope.launch { prefsManager.setServiceEnabled(!serviceEnabled) }
            },
            onToggleProtection = {
                scope.launch { prefsManager.setProtectionEnabled(!protectionEnabled) }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

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

        // Botão Controle Financeiro
        val context = LocalContext.current
        Button(
            onClick = {
                context.startActivity(Intent(context, FinanceActivity::class.java))
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(Icons.Default.AccountBalance, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Controle Financeiro")
        }

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
                        contentDescription = null,
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
                    contentDescription = null,
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
            Icon(icon, contentDescription = null, tint = color)
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
