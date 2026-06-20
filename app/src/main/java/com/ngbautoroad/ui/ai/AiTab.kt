package com.ngbautoroad.ui.ai

// ============================================================================
// ARQUIVO: AiTab.kt
// LOCALIZAÇÃO: ui/ai/AiTab.kt
// RESPONSABILIDADE: Aba IA inline na navegação principal (v6.3.3)
//   - Dashboard com cards de resumo (Turno, IA Local, Ranking)
//   - Sub-abas: Resumo | Projeção | Histórico | Relatório | Exportar
//   - Projeção movida do Financeiro para cá
//   - Histórico movido do Config/Adicionais para cá
// ============================================================================

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngbautoroad.data.db.AppDatabase
import com.ngbautoroad.data.db.FinanceDatabase
import com.ngbautoroad.data.db.RideHistoryEntity
import com.ngbautoroad.data.prefs.PrefsManager
import com.ngbautoroad.domain.LocalLearningEngine
import com.ngbautoroad.domain.DataExporter
import com.ngbautoroad.ui.finance.ProjectionTab
import com.ngbautoroad.ui.history.HistoryTab
import com.ngbautoroad.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiTab(prefsManager: PrefsManager, database: AppDatabase) {
    val context = LocalContext.current
    val financeDb = remember { FinanceDatabase.getInstance(context) }

    // Sub-abas
    var selectedSubTab by remember { mutableIntStateOf(0) }
    val subTabs = listOf("Resumo", "Projeção", "Histórico", "Relatório", "Exportar")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Header
        Text(
            text = "Inteligência Artificial",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )

        // Sub-abas
        ScrollableTabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 0.dp
        ) {
            subTabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedSubTab == index,
                    onClick = { selectedSubTab = index },
                    text = { Text(title, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Conteúdo por aba
        when (selectedSubTab) {
            0 -> AiResumoContent(database = database)
            1 -> {
                // Projeção movida do Financeiro
                val rideHistoryDao = remember { database.rideHistoryDao() }
                ProjectionTab(
                    earningDao = financeDb.earningDao(),
                    vehicleProfileDao = financeDb.vehicleProfileDao(),
                    individualExpenseDao = financeDb.individualExpenseDao(),
                    rideHistoryDao = rideHistoryDao
                )
            }
            2 -> HistoryTab(prefsManager = prefsManager, database = database)
            3 -> AiReportContent(database = database)
            4 -> AiExportContent(database = database)
        }
    }
}

// ============================================================================
// ABA RESUMO: Cards de resumo com métricas da IA
// ============================================================================
@Composable
private fun AiResumoContent(database: AppDatabase) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Usar getAllFlow() que retorna Flow<List<RideHistoryEntity>>
    val rideHistory by database.rideHistoryDao().getAllFlow().collectAsState(initial = emptyList())
    val totalRides = rideHistory.size
    val acceptedRides = rideHistory.count { ride -> ride.status == "ACCEPTED" || ride.status == "COMPLETED" }
    val refusedRides = rideHistory.count { ride -> ride.status == "REFUSED" }
    val acceptRate = if (totalRides > 0) (acceptedRides * 100 / totalRides) else 0

    // Inicializar engine para sugestões
    val engine = remember { LocalLearningEngine(context) }
    var suggestionsCount by remember { mutableStateOf(0) }

    LaunchedEffect(rideHistory.size) {
        if (rideHistory.isNotEmpty()) {
            engine.seedFromDatabase(context, scope)
            suggestionsCount = engine.generateSuggestions().size
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Card: Corridas Analisadas
        AiSummaryCard(
            icon = Icons.Default.Timeline,
            title = "Corridas Analisadas",
            value = "$totalRides",
            subtitle = "Total no histórico",
            color = MaterialTheme.colorScheme.primary
        )

        // Cards em Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AiSummaryCard(
                icon = Icons.Default.ThumbUp,
                title = "Aceitas",
                value = "$acceptedRides",
                subtitle = "$acceptRate% taxa",
                color = ScoreGreen,
                modifier = Modifier.weight(1f)
            )
            AiSummaryCard(
                icon = Icons.Default.ThumbDown,
                title = "Recusadas",
                value = "$refusedRides",
                subtitle = "${if (totalRides > 0) (refusedRides * 100 / totalRides) else 0}% taxa",
                color = ScoreRed,
                modifier = Modifier.weight(1f)
            )
        }

        // Card: IA Local
        AiSummaryCard(
            icon = Icons.Default.AutoAwesome,
            title = "IA Local",
            value = "$suggestionsCount sugestões",
            subtitle = "Padrões identificados no seu histórico",
            color = MaterialTheme.colorScheme.tertiary
        )

        // Card: Dica
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "Dica",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        "Use as abas Projeção e Histórico para análises detalhadas baseadas em IA.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ============================================================================
// Card de resumo reutilizável
// ============================================================================
@Composable
private fun AiSummaryCard(
    icon: ImageVector,
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ============================================================================
// ABA RELATÓRIO: Gera relatório com dados reais
// ============================================================================
@Composable
private fun AiReportContent(database: AppDatabase) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rideHistory by database.rideHistoryDao().getAllFlow().collectAsState(initial = emptyList())
    var reportText by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Relatório de Desempenho", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("Gere um resumo analítico baseado no seu histórico de corridas.", fontSize = 12.sp, color = Color.Gray)

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                scope.launch {
                    isLoading = true
                    withContext(Dispatchers.IO) {
                        try {
                            val rides = rideHistory
                            val total = rides.size
                            val completed = rides.count { it.status == "COMPLETED" }
                            val avgScore = if (rides.isNotEmpty()) rides.map { it.score }.average() else 0.0
                            val topPlatform = rides.groupBy { it.platform }
                                .maxByOrNull { it.value.size }?.key ?: "N/A"
                            val totalValue = rides.sumOf { it.rideValue }

                            reportText = buildString {
                                appendLine("📊 RELATÓRIO DE DESEMPENHO")
                                appendLine("═══════════════════════════")
                                appendLine()
                                appendLine("Total de corridas: $total")
                                appendLine("Corridas completadas: $completed")
                                appendLine("Score médio: ${"%.1f".format(avgScore)}")
                                appendLine("Plataforma principal: $topPlatform")
                                appendLine("Valor total: R$ ${"%.2f".format(totalValue)}")
                                appendLine()
                                appendLine("═══════════════════════════")
                                appendLine("Gerado em: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date())}")
                            }
                        } catch (e: Exception) {
                            reportText = "Erro ao gerar relatório: ${e.message}"
                        }
                    }
                    isLoading = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading && rideHistory.isNotEmpty()
        ) {
            Icon(Icons.Default.Assessment, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Gerar Relatório")
        }

        if (isLoading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        if (rideHistory.isEmpty()) {
            Text(
                "Sem dados suficientes. Registre corridas para gerar relatórios.",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }

        reportText?.let { text ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    text = text,
                    modifier = Modifier.padding(16.dp),
                    fontSize = 13.sp
                )
            }
        }
    }
}

// ============================================================================
// ABA EXPORTAR: Exportar dados para CSV
// ============================================================================
@Composable
private fun AiExportContent(database: AppDatabase) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rideHistory by database.rideHistoryDao().getAllFlow().collectAsState(initial = emptyList())
    var exportStatus by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Exportar Dados",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Text(
            "Exporte seu histórico de corridas e análises da IA em formato CSV.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        try {
                            val rides = database.rideHistoryDao().getAll()
                            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
                            val headers = listOf("Data", "Plataforma", "Valor", "Km", "Bairro Destino", "Score", "Status")
                            val rows = rides.map { ride ->
                                listOf(
                                    dateFormat.format(Date(ride.timestamp)),
                                    ride.platform,
                                    "%.2f".format(ride.rideValue),
                                    "%.1f".format(ride.dropoffDistance),
                                    ride.dropoffNeighborhood,
                                    "%.0f".format(ride.score),
                                    ride.status
                                )
                            }
                            val exporter = DataExporter(context)
                            val file = exporter.exportToCsv("corridas_ia_${System.currentTimeMillis()}", headers, rows)
                            if (file != null) exporter.shareFile(file)
                            exportStatus = if (file != null) "Exportado: ${rides.size} corridas" else "Erro ao exportar"
                        } catch (e: Exception) {
                            exportStatus = "Erro: ${e.message}"
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = rideHistory.isNotEmpty()
        ) {
            Icon(Icons.Default.FileDownload, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Exportar CSV")
        }

        if (rideHistory.isEmpty()) {
            Text(
                "Sem dados para exportar. Registre corridas primeiro.",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }

        exportStatus?.let { status ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (status.startsWith("Erro")) ScoreRed.copy(alpha = 0.1f)
                    else ScoreGreen.copy(alpha = 0.1f)
                )
            ) {
                Text(
                    text = status,
                    modifier = Modifier.padding(16.dp),
                    fontSize = 13.sp
                )
            }
        }
    }
}
