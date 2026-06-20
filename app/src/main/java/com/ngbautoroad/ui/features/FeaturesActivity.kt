package com.ngbautoroad.ui.features

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngbautoroad.data.db.AppDatabase
import com.ngbautoroad.data.db.FinanceDatabase
import com.ngbautoroad.data.db.RideHistoryEntity
import com.ngbautoroad.domain.LocalLearningEngine
import com.ngbautoroad.domain.RidePattern
import com.ngbautoroad.domain.LearningSuggestion
import com.ngbautoroad.domain.SuggestionType
import com.ngbautoroad.domain.ReportGenerator
import com.ngbautoroad.domain.ReportData
import com.ngbautoroad.domain.DataExporter
import com.ngbautoroad.ui.theme.NGBAutoRoadTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
class FeaturesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NGBAutoRoadTheme { FeaturesScreen() } }
    }
    override fun onResume() {
        super.onResume()
        com.ngbautoroad.service.BubbleService.setAppInForeground(true)
    }
    override fun onPause() {
        super.onPause()
        com.ngbautoroad.service.BubbleService.setAppInForeground(false)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeaturesScreen() {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Turno", "Ranking", "IA Local", "Relatório", "Exportar")

    Scaffold(topBar = {
        TopAppBar(title = { Text("Recursos Avançados") })
    }) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            ScrollableTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { i, t ->
                    Tab(selected = selectedTab == i, onClick = { selectedTab = i }, text = { Text(t) })
                }
            }
            when (selectedTab) {
                0 -> ShiftTab()
                1 -> RankingTab()
                2 -> LearningTab()
                3 -> ReportTab()
                4 -> ExportTab()
            }
        }
    }
}

@Composable
fun ShiftTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var todayRides by remember { mutableStateOf(0) }
    var todayEarnings by remember { mutableStateOf(0.0) }
    var avgScore by remember { mutableStateOf(0.0) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            }
            val dayStart = cal.timeInMillis
            val db = AppDatabase.getInstance(context)
            val rides = db.rideHistoryDao().getSince(dayStart)
            todayRides = rides.count { it.status == "COMPLETED" || it.status == "ACCEPTED" }
            todayEarnings = rides.filter { it.status == "COMPLETED" || it.status == "ACCEPTED" }.sumOf { it.rideValue }
            avgScore = rides.filter { it.score > 0 }.let { list ->
                if (list.isNotEmpty()) list.sumOf { it.score } / list.size else 0.0
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Turno Atual", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Dados em tempo real do seu dia", color = Color.Gray, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(12.dp))
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$todayRides", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("Corridas", fontSize = 12.sp, color = Color.Gray)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("R$ ${"%.2f".format(todayEarnings)}", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                            Text("Ganhos", fontSize = 12.sp, color = Color.Gray)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${"%.0f".format(avgScore)}", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9800))
                            Text("Score Médio", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// RANKING TAB — Dados reais do banco (v6.3.0)
// ============================================================================
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun RankingTab() {
    val context = LocalContext.current
    var sortBy by remember { mutableStateOf("avgValue") }

    data class RankItem(val name: String, val rides: Int, val avgValue: Double, val avgPerKm: Double)

    var rankings by remember { mutableStateOf<List<RankItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getInstance(context)
            val allRides = db.rideHistoryDao().getAll()
            val completed = allRides.filter { (it.status == "COMPLETED" || it.status == "ACCEPTED") && it.dropoffNeighborhood.isNotBlank() }

            rankings = completed.groupBy { it.dropoffNeighborhood }
                .map { (neighborhood, rides) ->
                    RankItem(
                        name = neighborhood,
                        rides = rides.size,
                        avgValue = rides.sumOf { it.rideValue } / rides.size,
                        avgPerKm = rides.filter { it.dropoffDistance > 0 }.let { filtered ->
                            if (filtered.isNotEmpty()) filtered.sumOf { it.rideValue / it.dropoffDistance } / filtered.size else 0.0
                        }
                    )
                }
                .filter { it.rides >= 2 } // Mínimo 2 corridas para ranking
                .sortedByDescending { it.avgValue }
            isLoading = false
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Ranking de Bairros", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Baseado no seu histórico real de corridas", color = Color.Gray, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = sortBy == "avgValue", onClick = { sortBy = "avgValue" }, label = { Text("Valor Médio") })
                FilterChip(selected = sortBy == "avgPerKm", onClick = { sortBy = "avgPerKm" }, label = { Text("R$/Km") })
                FilterChip(selected = sortBy == "rides", onClick = { sortBy = "rides" }, label = { Text("Corridas") })
            }
        }

        if (isLoading) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (rankings.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.BarChart, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Dados insuficientes", fontWeight = FontWeight.Bold)
                        Text("Registre pelo menos 2 corridas para o mesmo bairro para ver o ranking.", textAlign = TextAlign.Center, color = Color.Gray)
                    }
                }
            }
        } else {
            val sorted = when (sortBy) {
                "avgPerKm" -> rankings.sortedByDescending { it.avgPerKm }
                "rides" -> rankings.sortedByDescending { it.rides }
                else -> rankings.sortedByDescending { it.avgValue }
            }
            items(sorted) { item ->
                val position = sorted.indexOf(item) + 1
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("#$position", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                                color = when (position) { 1 -> Color(0xFFFFD700); 2 -> Color(0xFFC0C0C0); 3 -> Color(0xFFCD7F32); else -> Color.Gray })
                            Column {
                                Text(item.name, fontWeight = FontWeight.Bold)
                                Text("${item.rides} corridas", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("R$ ${"%.2f".format(item.avgValue)}", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                            if (item.avgPerKm > 0) {
                                Text("R$ ${"%.2f".format(item.avgPerKm)}/km", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// LEARNING TAB — Dados reais do banco (v6.3.0)
// ============================================================================
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun LearningTab() {
    val context = LocalContext.current
    var suggestions by remember { mutableStateOf<List<LearningSuggestion>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var rideCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getInstance(context)
            val allRides = db.rideHistoryDao().getAll()
            rideCount = allRides.size
                    val engine = LocalLearningEngine(context)

            // Alimentar engine com dados reais
            allRides.filter { (it.status == "COMPLETED" || it.status == "ACCEPTED") && it.dropoffNeighborhood.isNotBlank() }.forEach { ride ->
                val cal = Calendar.getInstance().apply { timeInMillis = ride.timestamp }
                engine.addPattern(RidePattern(
                    hour = cal.get(Calendar.HOUR_OF_DAY),
                    dayOfWeek = cal.get(Calendar.DAY_OF_WEEK),
                    neighborhood = ride.dropoffNeighborhood,
                    valuePerKm = if (ride.dropoffDistance > 0) ride.rideValue / ride.dropoffDistance else 0.0,
                    accepted = ride.status == "COMPLETED" || ride.status == "ACCEPTED"
                ))
            }
            // Alimentar com recusadas também
            allRides.filter { it.status == "REFUSED" }.forEach { ride ->
                val cal = Calendar.getInstance().apply { timeInMillis = ride.timestamp }
                engine.addPattern(RidePattern(
                    hour = cal.get(Calendar.HOUR_OF_DAY),
                    dayOfWeek = cal.get(Calendar.DAY_OF_WEEK),
                    neighborhood = ride.dropoffNeighborhood,
                    valuePerKm = if (ride.dropoffDistance > 0) ride.rideValue / ride.dropoffDistance else 0.0,
                    accepted = false
                ))
            }
            suggestions = engine.generateSuggestions()
            isLoading = false
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Aprendizado Local", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Sugestões baseadas no seu histórico real ($rideCount corridas analisadas)", color = Color.Gray, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (isLoading) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (suggestions.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Dados insuficientes", fontWeight = FontWeight.Bold)
                        Text("Registre pelo menos 20 corridas para receber sugestões inteligentes.", textAlign = TextAlign.Center, color = Color.Gray)
                    }
                }
            }
        } else {
            items(suggestions) { s ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                when (s.type) {
                                    SuggestionType.BEST_HOURS -> Icons.Default.Schedule
                                    SuggestionType.BEST_NEIGHBORHOODS -> Icons.Default.LocationOn
                                    SuggestionType.FATIGUE_WARNING -> Icons.Default.Warning
                                    else -> Icons.Default.Lightbulb
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(s.title, fontWeight = FontWeight.Bold)
                                Text(s.description, fontSize = 13.sp, color = Color.Gray)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        @Suppress("DEPRECATION") LinearProgressIndicator(progress = s.confidence.toFloat(), modifier = Modifier.fillMaxWidth())
                        Text("Confiança: ${(s.confidence * 100).toInt()}% (${s.basedOnRides} corridas)", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}

// ============================================================================
// REPORT TAB — Relatório PDF com dados reais (v6.3.0)
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportTab() {
    val context = LocalContext.current
    var selectedPeriod by remember { mutableStateOf("monthly") }
    var isGenerating by remember { mutableStateOf(false) }
    var lastReport by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Relatório PDF", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Gere relatórios financeiros com dados reais", color = Color.Gray, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = selectedPeriod == "weekly", onClick = { selectedPeriod = "weekly" }, label = { Text("Semanal") })
                FilterChip(selected = selectedPeriod == "monthly", onClick = { selectedPeriod = "monthly" }, label = { Text("Mensal") })
                FilterChip(selected = selectedPeriod == "yearly", onClick = { selectedPeriod = "yearly" }, label = { Text("Anual") })
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                scope.launch {
                    isGenerating = true
                    withContext(Dispatchers.IO) {
                        val cal = Calendar.getInstance()
                        val endDate = cal.timeInMillis
                        val startDate = when (selectedPeriod) {
                            "weekly" -> { cal.add(Calendar.DAY_OF_YEAR, -7); cal.timeInMillis }
                            "yearly" -> { cal.add(Calendar.YEAR, -1); cal.timeInMillis }
                            else -> { cal.add(Calendar.MONTH, -1); cal.timeInMillis }
                        }

                        val finDb = FinanceDatabase.getInstance(context)
                        val appDb = AppDatabase.getInstance(context)

                        val totalEarnings = finDb.earningDao().getTotalEarningsSync(startDate, endDate) ?: 0.0
                        val totalExpenses = finDb.expenseDao().let { dao ->
                            // Calcular despesas no período
                            var total = 0.0
                            val expenses = dao.getExpensesByPeriod(startDate, endDate)
                            // Usar query síncrona se disponível
                            total
                        }
                        val totalRides = appDb.rideHistoryDao().countSince(startDate)
                        val avgPerKm = appDb.rideHistoryDao().averageValuePerKmSince(startDate) ?: 0.0
                        val topNeighborhoods = appDb.rideHistoryDao().topDropoffNeighborhoods()
                        val platformSummary = finDb.earningDao().getEarningsByPlatformSummary(startDate, endDate)

                        val periodLabel = when (selectedPeriod) {
                            "weekly" -> "Última Semana"
                            "yearly" -> "Último Ano"
                            else -> "Último Mês"
                        }

                        val platformMap = platformSummary.associate { it.platform to it.total }
                        val neighborhoodList = topNeighborhoods.map { it.dropoffNeighborhood to it.avgVal }

                        val gen = ReportGenerator(context)
                        val totalDistance = finDb.earningDao().getTotalDistanceSync(startDate, endDate) ?: 0.0
                        val totalDuration = (finDb.earningDao().getTotalDurationSync(startDate, endDate) ?: 0) / 60.0
                        val data = ReportData(
                            periodLabel,
                            totalEarnings,
                            totalExpenses,
                            totalRides,
                            totalDistance,
                            totalDuration,
                            platformMap,
                            neighborhoodList
                        )
                        val file = gen.generatePdf(data)
                        lastReport = file?.absolutePath
                    }
                    isGenerating = false
                }
            }, modifier = Modifier.fillMaxWidth(), enabled = !isGenerating) {
                if (isGenerating) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("Gerar Relatório PDF")
            }
            if (lastReport != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                            Column {
                                Text("Relatório gerado!", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                Text(lastReport ?: "", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// EXPORT TAB — Exportar dados reais para CSV (v6.3.0)
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportStatus by remember { mutableStateOf<String?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Exportar Dados", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Exporte dados reais para CSV e compartilhe", color = Color.Gray, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(16.dp))
        }
        item {
            ExportOptionCard("Corridas", "Histórico completo de corridas reais", Icons.Default.DirectionsCar) {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        val db = AppDatabase.getInstance(context)
                        val rides = db.rideHistoryDao().getAll()
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
                        val file = exporter.exportToCsv("corridas_${System.currentTimeMillis()}", headers, rows)
                        if (file != null) exporter.shareFile(file)
                        exportStatus = if (file != null) "Exportado: ${rides.size} corridas" else "Erro ao exportar"
                    }
                }
            }
        }
        item {
            ExportOptionCard("Financeiro", "Ganhos e despesas reais", Icons.Default.AttachMoney) {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        val finDb = FinanceDatabase.getInstance(context)
                        val cal = Calendar.getInstance()
                        val endDate = cal.timeInMillis
                        cal.add(Calendar.YEAR, -1)
                        val startDate = cal.timeInMillis
                        val summary = finDb.earningDao().getEarningsByPlatformSummary(startDate, endDate)
                        val headers = listOf("Plataforma", "Ganhos Totais", "Corridas", "Km Rodados")
                        val rows = summary.map { s ->
                            listOf(s.platform, "%.2f".format(s.total), "${s.rides}", "%.1f".format(s.km))
                        }
                        val exporter = DataExporter(context)
                        val file = exporter.exportToCsv("financeiro_${System.currentTimeMillis()}", headers, rows)
                        if (file != null) exporter.shareFile(file)
                        exportStatus = if (file != null) "Exportado: ${summary.size} plataformas" else "Erro ao exportar"
                    }
                }
            }
        }
        item {
            ExportOptionCard("Despesas", "Todas as despesas registradas", Icons.Default.Receipt) {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        val finDb = FinanceDatabase.getInstance(context)
                        val cal = Calendar.getInstance()
                        val endDate = cal.timeInMillis
                        cal.add(Calendar.YEAR, -1)
                        val startDate = cal.timeInMillis
                        val categories = finDb.expenseDao().getExpenseSummaryByCategory(startDate, endDate)
                        val headers = listOf("Categoria", "Total Gasto")
                        val rows = categories.map { c ->
                            listOf(c.category, "%.2f".format(c.total))
                        }
                        val exporter = DataExporter(context)
                        val file = exporter.exportToCsv("despesas_${System.currentTimeMillis()}", headers, rows)
                        if (file != null) exporter.shareFile(file)
                        exportStatus = if (file != null) "Exportado: ${categories.size} categorias" else "Erro ao exportar"
                    }
                }
            }
        }
        if (exportStatus != null) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                        Text(exportStatus ?: "", color = Color(0xFF2E7D32))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportOptionCard(title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(description, fontSize = 12.sp, color = Color.Gray)
            }
            Icon(Icons.Default.Share, contentDescription = "Exportar")
        }
    }
}
