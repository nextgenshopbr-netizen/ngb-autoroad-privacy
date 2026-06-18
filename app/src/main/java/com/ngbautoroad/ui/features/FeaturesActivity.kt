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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngbautoroad.domain.*
import com.ngbautoroad.ui.theme.NGBAutoRoadTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FeaturesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NGBAutoRoadTheme { FeaturesScreen() } }
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
    val shiftManager = remember { ShiftManager(context) }
    var state by remember { mutableStateOf(shiftManager.loadState()) }
    var goalInput by remember { mutableStateOf("200") }

    LaunchedEffect(state.isActive) {
        while (state.isActive && !state.isPaused) { delay(60_000L); state = shiftManager.loadState() }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            if (!state.isActive) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Iniciar Turno", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        OutlinedTextField(value = goalInput, onValueChange = { goalInput = it },
                            label = { Text("Meta (R$)") }, modifier = Modifier.fillMaxWidth())
                        Button(onClick = {
                            val goal = goalInput.toDoubleOrNull() ?: 200.0
                            state = shiftManager.startShift(goal)
                        }, modifier = Modifier.fillMaxWidth()) { Text("Iniciar") }
                    }
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                    containerColor = if (state.goalReached) Color(0xFF1B5E20) else MaterialTheme.colorScheme.primaryContainer
                )) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Turno Ativo", fontWeight = FontWeight.Bold, fontSize = 20.sp,
                            color = if (state.goalReached) Color.White else Color.Unspecified)
                        if (state.goalReached) Text("META ATINGIDA!", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        LinearProgressIndicator(progress = state.goalProgress, modifier = Modifier.fillMaxWidth().height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column { Text("Tempo"); Text("${state.elapsedMinutes} min", fontWeight = FontWeight.Bold) }
                            Column { Text("Ganho"); Text("R$ ${"%.2f".format(state.totalEarned)}", fontWeight = FontWeight.Bold) }
                            Column { Text("R$/h"); Text("R$ ${"%.2f".format(state.valuePerHour)}", fontWeight = FontWeight.Bold) }
                            Column { Text("Corridas"); Text("${state.ridesCount}", fontWeight = FontWeight.Bold) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (state.isPaused) {
                                Button(onClick = { state = shiftManager.resumeShift(state) }) { Text("Retomar") }
                            } else {
                                OutlinedButton(onClick = { state = shiftManager.pauseShift(state) }) { Text("Pausar") }
                            }
                            Button(onClick = { state = shiftManager.endShift() }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Encerrar", color = Color.White) }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingTab() {
    val ranker = remember { NeighborhoodRanker() }
    var sortBy by remember { mutableStateOf("valuePerKm") }
    val sampleData = remember {
        ranker.rank(listOf(
            mapOf("neighborhood" to "Centro", "valuePerKm" to 2.5, "valuePerHour" to 55.0, "rating" to 4.9),
            mapOf("neighborhood" to "Centro", "valuePerKm" to 2.3, "valuePerHour" to 50.0, "rating" to 4.8),
            mapOf("neighborhood" to "Líder", "valuePerKm" to 1.8, "valuePerHour" to 42.0, "rating" to 4.7),
            mapOf("neighborhood" to "Efapi", "valuePerKm" to 1.5, "valuePerHour" to 35.0, "rating" to 4.5),
            mapOf("neighborhood" to "Passo dos Fortes", "valuePerKm" to 2.1, "valuePerHour" to 48.0, "rating" to 4.6)
        ), sortBy)
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Ranking de Bairros", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Baseado no histórico de corridas", color = Color.Gray, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = sortBy == "valuePerKm", onClick = { sortBy = "valuePerKm" }, label = { Text("R$/Km") })
                FilterChip(selected = sortBy == "valuePerHour", onClick = { sortBy = "valuePerHour" }, label = { Text("R$/Hora") })
                FilterChip(selected = sortBy == "rides", onClick = { sortBy = "rides" }, label = { Text("Corridas") })
            }
        }
        items(sampleData) { hood ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(hood.name, fontWeight = FontWeight.Bold)
                        Text("${hood.totalRides} corridas", fontSize = 12.sp, color = Color.Gray)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("R$ ${"%.2f".format(hood.avgValuePerKm)}/km", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                        Text("R$ ${"%.0f".format(hood.avgValuePerHour)}/h", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun LearningTab() {
    val engine = remember { LocalLearningEngine() }
    val suggestions = remember {
        // Add sample patterns for demo
        repeat(50) { i ->
            engine.addPattern(RidePattern(hour = (6 + i % 18), dayOfWeek = i % 7, neighborhood = listOf("Centro", "Líder", "Efapi")[i % 3],
                valuePerKm = 1.5 + (i % 10) * 0.2, accepted = i % 4 != 0))
        }
        engine.generateSuggestions()
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Aprendizado Local", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Sugestões baseadas nos seus padrões (100% offline)", color = Color.Gray, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (suggestions.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📊", fontSize = 48.sp)
                        Text("Dados insuficientes", fontWeight = FontWeight.Bold)
                        Text("Registre pelo menos 20 corridas para receber sugestões.", textAlign = TextAlign.Center, color = Color.Gray)
                    }
                }
            }
        } else {
            items(suggestions) { s ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(s.type.icon, fontSize = 24.sp)
                            Column {
                                Text(s.title, fontWeight = FontWeight.Bold)
                                Text(s.description, fontSize = 13.sp, color = Color.Gray)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(progress = s.confidence.toFloat(), modifier = Modifier.fillMaxWidth())
                        Text("Confiança: ${(s.confidence * 100).toInt()}% (${s.basedOnRides} corridas)", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}

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
            Text("Gere relatórios financeiros para controle fiscal", color = Color.Gray, fontSize = 12.sp)
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
                    val gen = ReportGenerator(context)
                    val data = ReportData("Junho 2026", 4500.0, 1200.0, 180, 2800.0, 120.0,
                        mapOf("Uber" to 3200.0, "99" to 1300.0), listOf("Centro" to 2.5, "Líder" to 2.1))
                    val file = gen.generatePdf(data)
                    lastReport = file?.absolutePath
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
                        Text("✅ Relatório gerado!", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                        Text(lastReport ?: "", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Exportar Dados", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Exporte para CSV e compartilhe", color = Color.Gray, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(16.dp))
        }
        item {
            ExportOptionCard("Corridas", "Histórico completo de corridas", Icons.Default.DirectionsCar) {
                val exporter = DataExporter(context)
                val file = exporter.exportToCsv("corridas", listOf("Data", "Valor", "Km", "Bairro", "Score"),
                    listOf(listOf("18/06/2026", "16.60", "7.2", "Centro", "78")))
                if (file != null) exporter.shareFile(file)
            }
        }
        item {
            ExportOptionCard("Financeiro", "Ganhos, despesas e lucros", Icons.Default.AttachMoney) {
                val exporter = DataExporter(context)
                val file = exporter.exportToCsv("financeiro", listOf("Mês", "Ganhos", "Despesas", "Lucro"),
                    listOf(listOf("Jun/2026", "4500.00", "1200.00", "3300.00")))
                if (file != null) exporter.shareFile(file)
            }
        }
        item {
            ExportOptionCard("Veículos", "Dados de veículos e custos", Icons.Default.DirectionsCar) {
                val exporter = DataExporter(context)
                val file = exporter.exportToCsv("veiculos", listOf("Modelo", "Km/L", "Custo/Km"),
                    listOf(listOf("Onix 2022", "12.5", "0.85")))
                if (file != null) exporter.shareFile(file)
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
