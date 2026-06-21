package com.ngbautoroad.ui.criteria

// ============================================================================
// ARQUIVO: CriteriaTab.kt (v6.3.0 — Redesign com 4 sub-abas)
// LOCALIZAÇÃO: ui/criteria/CriteriaTab.kt
// RESPONSABILIDADE: Configuração dos critérios, perfis, mapas e AutoPilot
// SUB-ABAS:
//   1. PAINEL — Perfis (destaque), card IA/Android 17, toggles rápidos
//   2. PESOS E VALORES — Sliders de critérios + valores mínimos
//   3. MAPAS E ZONAS — Editor de mapa + bairros bloqueados
//   4. AUTOPILOT — Configuração completa do AutoPilot
// ============================================================================

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngbautoroad.data.model.CriteriaWeights
import com.ngbautoroad.data.model.DriverThresholds
import com.ngbautoroad.data.prefs.PrefsManager
import com.ngbautoroad.ui.theme.ScoreGreen
import com.ngbautoroad.ui.theme.ScoreYellow
import com.ngbautoroad.ui.theme.ScoreOrange
import com.ngbautoroad.ui.theme.ScoreRed
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CriteriaTab(prefsManager: PrefsManager) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }

    val tabTitles = listOf("Painel", "Pesos", "Mapas", "AutoPilot")
    val tabIcons = listOf(
        Icons.Default.Dashboard,
        Icons.Default.Tune,
        Icons.Default.Map,
        Icons.Default.SmartToy
    )

    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Tab Row no topo
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontSize = 11.sp, maxLines = 1) },
                    icon = {
                        Icon(
                            tabIcons[index],
                            contentDescription = title,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
        }

        // Conteúdo da aba selecionada
        when (selectedTab) {
            0 -> PainelSubTab(prefsManager, scope)
            1 -> PesosSubTab(prefsManager, scope)
            2 -> MapasSubTab(prefsManager, scope)
            3 -> AutoPilotSubTab(prefsManager, scope)
        }
    }
    // v6.3.0: Tutorial guiado no primeiro acesso
    com.ngbautoroad.ui.tutorial.TutorialOverlay(
        screenId = "criteria",
        steps = com.ngbautoroad.ui.tutorial.TutorialContent.criteriaSteps,
        prefsManager = prefsManager
    )
    } // Box
}

// ============================================================================
// SUB-ABA 1: PAINEL — Perfis, card IA, toggles rápidos
// ============================================================================
@Composable
fun PainelSubTab(prefsManager: PrefsManager, scope: kotlinx.coroutines.CoroutineScope) {
    val scrollState = rememberScrollState()
    val weights by prefsManager.criteriaWeightsFlow.collectAsState(initial = CriteriaWeights())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Contador de pontos (resumo rápido)
        PointsCounter(weights = weights)

        Spacer(modifier = Modifier.height(16.dp))

        // PERFIS — destaque principal desta aba
        ProfilesSection(prefsManager = prefsManager, scope = scope)

        Spacer(modifier = Modifier.height(20.dp))

        // Card informativo: IA / Android 17
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Inteligência Adaptativa",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "O sistema aprende com suas decisões e ajusta os pesos automaticamente. " +
                    "Quanto mais corridas você avalia, mais preciso o Score se torna.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Resumo rápido do AutoPilot
        val autoPilotMode by prefsManager.autoPilotModeFlow.collectAsState(initial = "OFF")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (autoPilotMode != "OFF")
                    ScoreGreen.copy(alpha = 0.1f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = if (autoPilotMode != "OFF") ScoreGreen
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            "AutoPilot",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            when (autoPilotMode) {
                                "OFF" -> "Desativado"
                                "ACCEPT" -> "Aceitar ativo"
                                "REFUSE" -> "Recusar ativo"
                                "BOTH", "FULL" -> "Aceitar + Recusar ativos"
                                else -> autoPilotMode
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    if (autoPilotMode != "OFF") Icons.Default.CheckCircle
                    else Icons.Default.Cancel,
                    contentDescription = null,
                    tint = if (autoPilotMode != "OFF") ScoreGreen else ScoreRed
                )
            }
        }
    }
}

// ============================================================================
// SUB-ABA 2: PESOS E VALORES — Sliders + Thresholds
// ============================================================================
@Composable
fun PesosSubTab(prefsManager: PrefsManager, scope: kotlinx.coroutines.CoroutineScope) {
    val scrollState = rememberScrollState()
    val weights by prefsManager.criteriaWeightsFlow.collectAsState(initial = CriteriaWeights())
    val thresholds by prefsManager.driverThresholdsFlow.collectAsState(initial = DriverThresholds())
    var showThresholds by remember { mutableStateOf(true) }
    var showRatingPenaltyInfo by remember { mutableStateOf(false) }

    val totalUsed = weights.totalUsed

    fun maxForCriteria(currentValue: Int): Int {
        val othersSum = totalUsed - currentValue
        return (100 - othersSum).coerceAtLeast(0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Header com contador
        PointsCounter(weights = weights)

        Spacer(modifier = Modifier.height(16.dp))

        // Seção: Pesos dos Critérios
        Text(
            "Pesos dos Critérios",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Distribua 100 pontos entre os critérios. Soma atual: $totalUsed/100",
            style = MaterialTheme.typography.labelSmall,
            color = if (totalUsed > 100) ScoreRed else MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (totalUsed > 100) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Total excede 100 pontos! Reduza algum critério.",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = ScoreRed
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        CriteriaSlider("Valor por KM", weights.valuePerKm, maxForCriteria(weights.valuePerKm)) { newVal ->
            scope.launch { prefsManager.saveCriteriaWeights(weights.copy(valuePerKm = newVal)) }
        }
        CriteriaSlider("Valor por Hora", weights.valuePerHour, maxForCriteria(weights.valuePerHour)) { newVal ->
            scope.launch { prefsManager.saveCriteriaWeights(weights.copy(valuePerHour = newVal)) }
        }
        CriteriaSlider("Paradas Intermediárias", weights.intermediateStops, maxForCriteria(weights.intermediateStops)) { newVal ->
            scope.launch { prefsManager.saveCriteriaWeights(weights.copy(intermediateStops = newVal)) }
        }
        // v6.4.1: Slider de avaliação com ícone info (tabela de multiplicadores)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                CriteriaSlider("Avaliação do Passageiro", weights.passengerRating, maxForCriteria(weights.passengerRating)) { newVal ->
                    scope.launch { prefsManager.saveCriteriaWeights(weights.copy(passengerRating = newVal)) }
                }
            }
            IconButton(
                onClick = { showRatingPenaltyInfo = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Ver tabela de penalidades",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Dialog: Tabela de multiplicadores de penalidade por rating
        if (showRatingPenaltyInfo) {
            AlertDialog(
                onDismissRequest = { showRatingPenaltyInfo = false },
                icon = { Icon(Icons.Default.Shield, contentDescription = null, tint = ScoreRed) },
                title = { Text("Proteção por Avaliação", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            "Passageiros mal avaliados recebem penalidade progressiva no Score. " +
                            "Quanto pior a avaliação, maior o multiplicador aplicado ao peso configurado.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        // Tabela de faixas
                        val penaltyData = listOf(
                            Triple("4.9 – 5.0 ★", "Sem penalidade", ScoreGreen),
                            Triple("4.7 – 4.9 ★", "1.0× (${weights.passengerRating} pts)", ScoreYellow),
                            Triple("4.5 – 4.7 ★", "2.5× (${(weights.passengerRating * 2.5).toInt()} pts)", ScoreOrange),
                            Triple("4.3 – 4.5 ★", "3.5× (${(weights.passengerRating * 3.5).toInt()} pts)", ScoreRed),
                            Triple("< 4.3 ★", "4.0× (${weights.passengerRating * 4} pts)", ScoreRed)
                        )
                        penaltyData.forEach { (faixa, penalidade, cor) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(cor)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(faixa, style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    penalidade,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = cor
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Exemplo: passageiro com 4.2★ em corrida excelente → " +
                            "penalidade de ${weights.passengerRating * 4} pts derruba o Score abaixo de 50.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showRatingPenaltyInfo = false }) {
                        Text("Entendi")
                    }
                }
            )
        }
        CriteriaSlider("Valor da Corrida", weights.rideValue, maxForCriteria(weights.rideValue)) { newVal ->
            scope.launch { prefsManager.saveCriteriaWeights(weights.copy(rideValue = newVal)) }
        }
        CriteriaSlider("Duração da Corrida", weights.rideDuration, maxForCriteria(weights.rideDuration)) { newVal ->
            scope.launch { prefsManager.saveCriteriaWeights(weights.copy(rideDuration = newVal)) }
        }
        CriteriaSlider("Distância até Embarque", weights.pickupDistance, maxForCriteria(weights.pickupDistance)) { newVal ->
            scope.launch { prefsManager.saveCriteriaWeights(weights.copy(pickupDistance = newVal)) }
        }
        CriteriaSlider("Distância até Desembarque", weights.dropoffDistance, maxForCriteria(weights.dropoffDistance)) { newVal ->
            scope.launch { prefsManager.saveCriteriaWeights(weights.copy(dropoffDistance = newVal)) }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Divider()
        Spacer(modifier = Modifier.height(16.dp))

        // Seção: Valores Mínimos Desejados
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Valores Mínimos Desejados",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Corridas abaixo desses valores recebem penalidade",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { showThresholds = !showThresholds }) {
                Icon(
                    if (showThresholds) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Expandir"
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (showThresholds) {
            ThresholdField("R$/KM mínimo", thresholds.minValuePerKm, "R$/km") { newVal ->
                scope.launch { prefsManager.saveDriverThresholds(thresholds.copy(minValuePerKm = newVal)) }
            }
            ThresholdField("R$/Hora mínimo", thresholds.minValuePerHour, "R$/h") { newVal ->
                scope.launch { prefsManager.saveDriverThresholds(thresholds.copy(minValuePerHour = newVal)) }
            }
            ThresholdField("Valor mínimo da corrida", thresholds.minRideValue, "R$") { newVal ->
                scope.launch { prefsManager.saveDriverThresholds(thresholds.copy(minRideValue = newVal)) }
            }
            ThresholdField("Distância máx. até embarque", thresholds.maxPickupDistance, "km") { newVal ->
                scope.launch { prefsManager.saveDriverThresholds(thresholds.copy(maxPickupDistance = newVal)) }
            }
            ThresholdField("Avaliação mínima do passageiro", thresholds.minPassengerRating, "estrelas", maxValue = 5.0) { newVal ->
                scope.launch { prefsManager.saveDriverThresholds(thresholds.copy(minPassengerRating = newVal)) }
            }
            ThresholdField("Duração máxima aceitável", thresholds.maxDuration, "min") { newVal ->
                scope.launch { prefsManager.saveDriverThresholds(thresholds.copy(maxDuration = newVal)) }
            }
            ThresholdIntField("Máximo de paradas", thresholds.maxStops, "paradas") { newVal ->
                scope.launch { prefsManager.saveDriverThresholds(thresholds.copy(maxStops = newVal)) }
            }
            ThresholdField("Distância mín. do destino", thresholds.minDropoffDistance, "km") { newVal ->
                scope.launch { prefsManager.saveDriverThresholds(thresholds.copy(minDropoffDistance = newVal)) }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Botão Salvar
        Button(
            onClick = {
                scope.launch {
                    prefsManager.saveCriteriaWeights(weights)
                    prefsManager.saveDriverThresholds(thresholds)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = totalUsed <= 100,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Salvar Critérios", color = MaterialTheme.colorScheme.onPrimary)
        }

        if (totalUsed > 100) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Reduza os pesos para no máximo 100 pontos antes de salvar",
                style = MaterialTheme.typography.labelSmall,
                color = ScoreRed
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ============================================================================
// SUB-ABA 3: MAPAS E ZONAS — Editor de mapa + bairros bloqueados
// ============================================================================
@Composable
fun MapasSubTab(prefsManager: PrefsManager, scope: kotlinx.coroutines.CoroutineScope) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val blockedPickup by prefsManager.blockedPickupFlow.collectAsState(initial = emptyList())
    val blockedDropoff by prefsManager.blockedDropoffFlow.collectAsState(initial = emptyList())
    var showAddPickup by remember { mutableStateOf(false) }
    var showAddDropoff by remember { mutableStateOf(false) }
    var newNeighborhoodName by remember { mutableStateOf("") }
    var newNeighborhoodWeight by remember { mutableIntStateOf(20) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Seção: Editor de Zonas no Mapa
        Text(
            text = "Zonas Bloqueadas",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Desenhe áreas no mapa para bloquear embarque/desembarque no cálculo de Score",
            style = MaterialTheme.typography.bodySmall,
            color = ScoreOrange
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                val intent = Intent(context, com.ngbautoroad.ui.map.ZoneMapActivity::class.java)
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.EditLocation, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Abrir Editor de Zonas")
        }

        Spacer(modifier = Modifier.height(24.dp))
        Divider()
        Spacer(modifier = Modifier.height(16.dp))

        // Seção: Bairros Bloqueados
        Text(
            text = "Bairros Bloqueados",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Bairros que penalizam o Score. Separe múltiplos por vírgula.",
            style = MaterialTheme.typography.bodySmall,
            color = ScoreOrange
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Bairros Embarque
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Embarque", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    IconButton(onClick = { showAddPickup = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Adicionar")
                    }
                }
                if (blockedPickup.isEmpty()) {
                    Text("Nenhum bairro bloqueado", fontSize = 12.sp, color = ScoreOrange)
                } else {
                    blockedPickup.forEach { (name, weight) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("$name (-$weight pts)", fontSize = 12.sp)
                            IconButton(onClick = {
                                scope.launch {
                                    prefsManager.saveBlockedPickup(blockedPickup.filter { it.first != name })
                                }
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Remover", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Bairros Destino
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Destino", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    IconButton(onClick = { showAddDropoff = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Adicionar")
                    }
                }
                if (blockedDropoff.isEmpty()) {
                    Text("Nenhum bairro bloqueado", fontSize = 12.sp, color = ScoreOrange)
                } else {
                    blockedDropoff.forEach { (name, weight) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("$name (-$weight pts)", fontSize = 12.sp)
                            IconButton(onClick = {
                                scope.launch {
                                    prefsManager.saveBlockedDropoff(blockedDropoff.filter { it.first != name })
                                }
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Remover", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        // Dialog para adicionar bairro de embarque
        if (showAddPickup) {
            AlertDialog(
                onDismissRequest = { showAddPickup = false },
                title = { Text("Adicionar Bairro (Embarque)") },
                text = {
                    Column {
                        Text("Separe múltiplos bairros por vírgula", fontSize = 11.sp, color = ScoreOrange)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newNeighborhoodName,
                            onValueChange = { newNeighborhoodName = it },
                            label = { Text("Nome(s) do bairro") },
                            placeholder = { Text("Ex: Centro, Liberdade, Moema") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Penalidade: $newNeighborhoodWeight pts", fontSize = 12.sp)
                        Slider(
                            value = newNeighborhoodWeight.toFloat(),
                            onValueChange = { newNeighborhoodWeight = it.toInt() },
                            valueRange = 5f..50f,
                            steps = 8
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (newNeighborhoodName.isNotBlank()) {
                            val names = newNeighborhoodName.split(",").map { it.trim() }.filter { it.isNotBlank() }
                            scope.launch {
                                prefsManager.saveBlockedPickup(blockedPickup + names.map { it to newNeighborhoodWeight })
                            }
                            newNeighborhoodName = ""
                            newNeighborhoodWeight = 20
                            showAddPickup = false
                        }
                    }) { Text("Adicionar") }
                },
                dismissButton = {
                    TextButton(onClick = { showAddPickup = false }) { Text("Cancelar") }
                }
            )
        }

        // Dialog para adicionar bairro de destino
        if (showAddDropoff) {
            AlertDialog(
                onDismissRequest = { showAddDropoff = false },
                title = { Text("Adicionar Bairro (Destino)") },
                text = {
                    Column {
                        Text("Separe múltiplos bairros por vírgula", fontSize = 11.sp, color = ScoreOrange)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newNeighborhoodName,
                            onValueChange = { newNeighborhoodName = it },
                            label = { Text("Nome(s) do bairro") },
                            placeholder = { Text("Ex: Capão Redondo, Grajaú") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Penalidade: $newNeighborhoodWeight pts", fontSize = 12.sp)
                        Slider(
                            value = newNeighborhoodWeight.toFloat(),
                            onValueChange = { newNeighborhoodWeight = it.toInt() },
                            valueRange = 5f..50f,
                            steps = 8
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (newNeighborhoodName.isNotBlank()) {
                            val names = newNeighborhoodName.split(",").map { it.trim() }.filter { it.isNotBlank() }
                            scope.launch {
                                prefsManager.saveBlockedDropoff(blockedDropoff + names.map { it to newNeighborhoodWeight })
                            }
                            newNeighborhoodName = ""
                            newNeighborhoodWeight = 20
                            showAddDropoff = false
                        }
                    }) { Text("Adicionar") }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDropoff = false }) { Text("Cancelar") }
                }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ============================================================================
// SUB-ABA 4: AUTOPILOT — Configuração completa
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoPilotSubTab(prefsManager: PrefsManager, scope: kotlinx.coroutines.CoroutineScope) {
    val scrollState = rememberScrollState()
    val autoPilotMode by prefsManager.autoPilotModeFlow.collectAsState(initial = "OFF")
    val minScore by prefsManager.autoPilotMinScoreFlow.collectAsState(initial = 75)
    val maxRefuseScore by prefsManager.autoPilotMaxRefuseScoreFlow.collectAsState(initial = 40)
    val geoFilters by prefsManager.autoPilotGeoFiltersEnabledFlow.collectAsState(initial = true)

    // Derivar flags independentes
    val acceptEnabled = autoPilotMode in listOf("ACCEPT_ONLY", "FULL", "ACCEPT", "BOTH")
    val refuseEnabled = autoPilotMode in listOf("REFUSE_ONLY", "FULL", "REFUSE", "BOTH")

    fun computeMode(accept: Boolean, refuse: Boolean): String {
        return when {
            accept && refuse -> "BOTH"
            accept -> "ACCEPT"
            refuse -> "REFUSE"
            else -> "OFF"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.SmartToy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    "AutoPilot",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Aceita/recusa corridas automaticamente baseado no Score",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Status atual
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (acceptEnabled || refuseEnabled)
                    ScoreGreen.copy(alpha = 0.1f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Status: ${if (acceptEnabled || refuseEnabled) "ATIVO" else "DESATIVADO"}",
                    fontWeight = FontWeight.Bold,
                    color = if (acceptEnabled || refuseEnabled) ScoreGreen else ScoreRed
                )
                Text(
                    "Modo: $autoPilotMode",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Checkboxes independentes
        Text("Funções ativas:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = acceptEnabled,
                onCheckedChange = { checked ->
                    val newMode = computeMode(checked, refuseEnabled)
                    scope.launch { prefsManager.saveAutoPilotMode(newMode) }
                }
            )
            Text("Aceitar", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.width(32.dp))
            Checkbox(
                checked = refuseEnabled,
                onCheckedChange = { checked ->
                    val newMode = computeMode(acceptEnabled, checked)
                    scope.launch { prefsManager.saveAutoPilotMode(newMode) }
                }
            )
            Text("Recusar", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }

        if (acceptEnabled || refuseEnabled) {
            Spacer(modifier = Modifier.height(20.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))

            // Score mínimo para aceitar
            if (acceptEnabled) {
                Text(
                    "Score mínimo para aceitar automaticamente",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text("Valor atual: $minScore", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = minScore.toFloat(),
                    onValueChange = { scope.launch { prefsManager.saveAutoPilotMinScore(it.toInt()) } },
                    valueRange = 50f..100f,
                    steps = 9
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Score máximo para recusar
            if (refuseEnabled) {
                Text(
                    "Score máximo para recusar automaticamente",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text("Valor atual: $maxRefuseScore", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = maxRefuseScore.toFloat(),
                    onValueChange = { scope.launch { prefsManager.saveAutoPilotMaxRefuseScore(it.toInt()) } },
                    valueRange = 0f..60f,
                    steps = 11
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Zona neutra
            if (acceptEnabled && refuseEnabled) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ScoreYellow.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = ScoreYellow)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Zona neutra: Score entre $maxRefuseScore e $minScore — você decide manualmente",
                            fontSize = 12.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Filtros geográficos
            Divider()
            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = geoFilters,
                    onCheckedChange = { scope.launch { prefsManager.saveAutoPilotGeoFilters(it) } }
                )
                Column {
                    Text("Respeitar bairros/zonas bloqueadas", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "Recusa automaticamente se embarque/destino estiver em zona bloqueada",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Aviso de segurança
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = ScoreRed.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = ScoreRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "AutoPilot usa delay humanizado para simular comportamento natural. " +
                        "Use com responsabilidade.",
                        fontSize = 11.sp,
                        color = ScoreRed
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ============================================================================
// COMPOSABLES AUXILIARES
// ============================================================================

@Composable
fun PointsCounter(weights: CriteriaWeights) {
    val total = weights.totalUsed
    val remaining = 100 - total
    val statusText = when {
        total == 0 -> "Nenhum peso configurado"
        total > 100 -> "Excedeu! Reduza ${total - 100} pontos"
        total == 100 -> "Perfeito! 100 pontos distribuídos"
        else -> "$remaining pontos restantes"
    }
    val usedColor = when {
        total > 100 -> ScoreRed
        total == 100 -> ScoreGreen
        total >= 50 -> ScoreYellow
        total >= 20 -> ScoreOrange
        else -> ScoreRed
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Total de Pesos",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "$total/100",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = usedColor
            )
        }
        @Suppress("DEPRECATION")
        LinearProgressIndicator(
            progress = total.coerceAtMost(100) / 100f,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = usedColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
fun CriteriaSlider(
    label: String,
    value: Int,
    maxValue: Int,
    onValueChange: (Int) -> Unit
) {
    val sliderRange = maxValue.toFloat().coerceAtLeast(0f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row {
                Text(
                    text = "$value pts",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (value > 0) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (maxValue < 100) {
                    Text(
                        text = " (máx: $maxValue)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Slider(
            value = value.toFloat().coerceIn(0f, sliderRange),
            onValueChange = { newVal ->
                val clamped = newVal.toInt().coerceIn(0, maxValue)
                onValueChange(clamped)
            },
            valueRange = 0f..sliderRange.coerceAtLeast(1f),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
fun ThresholdField(
    label: String,
    value: Double,
    suffix: String,
    maxValue: Double = 9999.0,
    onValueChange: (Double) -> Unit
) {
    var textValue by remember(value) {
        mutableStateOf(if (value == 0.0) "" else String.format("%.2f", value))
    }

    val parsed = textValue.replace(",", ".").toDoubleOrNull()
    val isInvalid = parsed != null && (parsed < 0.0 || parsed > maxValue)

    OutlinedTextField(
        value = textValue,
        onValueChange = { input ->
            textValue = input
            val p = input.replace(",", ".").toDoubleOrNull()
            if (p != null && p >= 0.0 && p <= maxValue) {
                onValueChange(p)
            } else if (input.isBlank()) {
                onValueChange(0.0)
            }
        },
        label = { Text(label) },
        suffix = { Text(suffix, style = MaterialTheme.typography.labelSmall) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        singleLine = true,
        isError = isInvalid,
        supportingText = if (isInvalid) {{ Text("Máx: $maxValue", color = MaterialTheme.colorScheme.error) }} else null,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}

@Composable
fun ThresholdIntField(
    label: String,
    value: Int,
    suffix: String,
    onValueChange: (Int) -> Unit
) {
    var textValue by remember(value) {
        mutableStateOf(if (value >= 99) "" else value.toString())
    }

    OutlinedTextField(
        value = textValue,
        onValueChange = { input ->
            textValue = input
            val parsed = input.toIntOrNull()
            if (parsed != null) {
                onValueChange(parsed)
            } else if (input.isBlank()) {
                onValueChange(99)
            }
        },
        label = { Text(label) },
        suffix = { Text(suffix, style = MaterialTheme.typography.labelSmall) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}

// ============================================================================
// COMPOSABLE ProfilesSection
// ============================================================================
@Composable
fun ProfilesSection(prefsManager: PrefsManager, scope: kotlinx.coroutines.CoroutineScope) {
    val profilesJson by prefsManager.profilesJsonFlow.collectAsState(initial = "[]")
    val activeProfileId by prefsManager.activeProfileIdFlow.collectAsState(initial = 0)
    val weights by prefsManager.criteriaWeightsFlow.collectAsState(initial = CriteriaWeights())
    val thresholds by prefsManager.driverThresholdsFlow.collectAsState(initial = DriverThresholds())
    // v6.3.2: Flows para o botão Salvar perfil ativo
    val currentAutoPilotForSave by prefsManager.autoPilotModeFlow.collectAsState(initial = "OFF")
    val currentMinForSave by prefsManager.autoPilotMinScoreFlow.collectAsState(initial = 75)
    val currentMaxForSave by prefsManager.autoPilotMaxRefuseScoreFlow.collectAsState(initial = 40)

    var showSaveDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }

    val profiles = remember(profilesJson) {
        try {
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            json.decodeFromString<List<SavedProfile>>(profilesJson)
        } catch (_: Exception) { emptyList() }
    }

    Text(
        text = "Perfis de Critérios",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
    Text(
        text = "Salve até 5 configurações diferentes (ex: Dia, Noite, Fim de semana)",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(12.dp))

    if (profiles.isEmpty()) {
        Text(
            "Nenhum perfil salvo. Salve sua configuração atual como perfil.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        profiles.forEach { profile ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (profile.id == activeProfileId)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile.name.ifBlank { "Perfil ${profile.id}" },
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                        Text(
                            "AutoPilot: ${profile.autoPilotMode} | Min: ${profile.minAcceptScore}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row {
                        IconButton(onClick = {
                            scope.launch {
                                prefsManager.saveCriteriaWeights(profile.weights)
                                prefsManager.saveDriverThresholds(profile.thresholds)
                                prefsManager.saveAutoPilotMode(profile.autoPilotMode)
                                prefsManager.saveAutoPilotMinScore(profile.minAcceptScore)
                                prefsManager.saveAutoPilotMaxRefuseScore(profile.maxRefuseScore)
                                prefsManager.saveActiveProfileId(profile.id)
                            }
                        }) {
                            Icon(
                                if (profile.id == activeProfileId) Icons.Default.CheckCircle
                                else Icons.Default.PlayArrow,
                                contentDescription = "Carregar perfil",
                                tint = if (profile.id == activeProfileId) ScoreGreen
                                else MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = {
                            scope.launch {
                                val updated = profiles.filter { it.id != profile.id }
                                val json = kotlinx.serialization.json.Json.encodeToString(
                                    kotlinx.serialization.builtins.ListSerializer(SavedProfile.serializer()),
                                    updated
                                )
                                prefsManager.saveProfilesJson(json)
                                if (activeProfileId == profile.id) {
                                    prefsManager.saveActiveProfileId(0)
                                }
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Excluir", tint = ScoreRed)
                        }
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // v6.3.2: Botões de perfil — Salvar (perfil ativo) + Novo Perfil
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Botão Salvar: atualiza o perfil atualmente selecionado
        OutlinedButton(
            onClick = {
                if (activeProfileId > 0) {
                    scope.launch {
                        val updated = profiles.map { p ->
                            if (p.id == activeProfileId) {
                                p.copy(
                                    weights = weights,
                                    thresholds = thresholds,
                                    autoPilotMode = currentAutoPilotForSave,
                                    minAcceptScore = currentMinForSave,
                                    maxRefuseScore = currentMaxForSave
                                )
                            } else p
                        }
                        val json = kotlinx.serialization.json.Json.encodeToString(
                            kotlinx.serialization.builtins.ListSerializer(SavedProfile.serializer()),
                            updated
                        )
                        prefsManager.saveProfilesJson(json)
                    }
                }
            },
            modifier = Modifier.weight(1f),
            enabled = activeProfileId > 0
        ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Salvar", maxLines = 1)
        }

        // Botão Novo Perfil: cria um novo perfil
        Button(
            onClick = { showSaveDialog = true },
            modifier = Modifier.weight(1f),
            enabled = profiles.size < 5,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Novo Perfil", maxLines = 1)
        }
    }

    if (profiles.size >= 5) {
        Text("Máximo de 5 perfis atingido", fontSize = 11.sp, color = ScoreOrange)
    }

    if (showSaveDialog) {
        val autoPilotMode by prefsManager.autoPilotModeFlow.collectAsState(initial = "OFF")
        val minScore by prefsManager.autoPilotMinScoreFlow.collectAsState(initial = 75)
        val maxRefuse by prefsManager.autoPilotMaxRefuseScoreFlow.collectAsState(initial = 40)

        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Salvar Perfil") },
            text = {
                Column {
                    Text("Dê um nome para este perfil:", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newProfileName,
                        onValueChange = { newProfileName = it },
                        label = { Text("Nome do perfil") },
                        placeholder = { Text("Ex: Noturno, Fim de semana") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newProfileName.isNotBlank()) {
                        scope.launch {
                            val newId = (profiles.maxOfOrNull { it.id } ?: 0) + 1
                            val newProfile = SavedProfile(
                                id = newId,
                                name = newProfileName.trim(),
                                weights = weights,
                                thresholds = thresholds,
                                autoPilotMode = autoPilotMode,
                                minAcceptScore = minScore,
                                maxRefuseScore = maxRefuse
                            )
                            val updated = profiles + newProfile
                            val json = kotlinx.serialization.json.Json.encodeToString(
                                kotlinx.serialization.builtins.ListSerializer(SavedProfile.serializer()),
                                updated
                            )
                            prefsManager.saveProfilesJson(json)
                            prefsManager.saveActiveProfileId(newId)
                        }
                        newProfileName = ""
                        showSaveDialog = false
                    }
                }) { Text("Salvar") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

// ============================================================================
// Data class para perfis salvos (serializable)
// ============================================================================
@kotlinx.serialization.Serializable
data class SavedProfile(
    val id: Int,
    val name: String,
    val weights: CriteriaWeights,
    val thresholds: DriverThresholds,
    val autoPilotMode: String = "OFF",
    val minAcceptScore: Int = 75,
    val maxRefuseScore: Int = 40
)
