package com.ngbautoroad.ui.criteria

// ============================================================================
// ARQUIVO: CriteriaTab.kt
// LOCALIZAÇÃO: ui/criteria/CriteriaTab.kt
// RESPONSABILIDADE: Configuração dos 8 critérios de avaliação e thresholds
// COMPOSABLES:
//   - CriteriaTab (L29-321): Tela principal com sliders e thresholds
//   - PointsCounter (L323-387): Indicador visual de pontos usados/restantes
//   - CriteriaSlider (L390-447): Slider individual com maxValue dinâmico
//   - ThresholdField (L450-489): Campo de threshold Double com validação
//   - ThresholdIntField (L492-525): Campo de threshold Int
// DEPENDÊNCIAS:
//   - data/model/RideData.kt → CriteriaWeights, DriverThresholds
//   - data/prefs/PrefsManager.kt → persiste pesos e thresholds
// DEPENDENTES:
//   - domain/RideScorer.kt → usa CriteriaWeights e DriverThresholds
//   - service/OverlayService.kt → carrega config via PrefsManager
// LÓGICA DE VALIDAÇÃO:
//   - Soma dos pesos DEVE ser exatamente 100
//   - Cada slider tem maxValue = 100 - somaOutros (impede ultrapassar 100)
//   - ThresholdField com maxValue=5.0 para avaliação (não aceita >5 estrelas)
//   - Feedback visual: borda vermelha quando valor fora do range
// ============================================================================

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

@Composable
fun CriteriaTab(prefsManager: PrefsManager) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val weights by prefsManager.criteriaWeightsFlow.collectAsState(initial = CriteriaWeights())
    val thresholds by prefsManager.driverThresholdsFlow.collectAsState(initial = DriverThresholds())
    val blockedPickup by prefsManager.blockedPickupFlow.collectAsState(initial = emptyList())
    val blockedDropoff by prefsManager.blockedDropoffFlow.collectAsState(initial = emptyList())
    var showAddPickup by remember { mutableStateOf(false) }
    var showAddDropoff by remember { mutableStateOf(false) }
    var newNeighborhoodName by remember { mutableStateOf("") }
    var newNeighborhoodWeight by remember { mutableIntStateOf(20) }

    var showThresholds by remember { mutableStateOf(true) }
    val scrollState = rememberScrollState()

    // Calcula o total e o máximo disponível para cada critério
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
        // Header com contador de pontos
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
                "⚠️ Total excede 100 pontos! Reduza algum critério.",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = ScoreRed
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Sliders INDIVIDUAIS com limite baseado no restante disponível
        CriteriaSlider(
            label = "Valor por KM",
            value = weights.valuePerKm,
            maxValue = maxForCriteria(weights.valuePerKm),
            onValueChange = { newVal ->
                scope.launch {
                    prefsManager.saveCriteriaWeights(weights.copy(valuePerKm = newVal))
                }
            }
        )

        CriteriaSlider(
            label = "Valor por Hora",
            value = weights.valuePerHour,
            maxValue = maxForCriteria(weights.valuePerHour),
            onValueChange = { newVal ->
                scope.launch {
                    prefsManager.saveCriteriaWeights(weights.copy(valuePerHour = newVal))
                }
            }
        )

        CriteriaSlider(
            label = "Paradas Intermediárias",
            value = weights.intermediateStops,
            maxValue = maxForCriteria(weights.intermediateStops),
            onValueChange = { newVal ->
                scope.launch {
                    prefsManager.saveCriteriaWeights(weights.copy(intermediateStops = newVal))
                }
            }
        )

        CriteriaSlider(
            label = "Avaliação do Passageiro",
            value = weights.passengerRating,
            maxValue = maxForCriteria(weights.passengerRating),
            onValueChange = { newVal ->
                scope.launch {
                    prefsManager.saveCriteriaWeights(weights.copy(passengerRating = newVal))
                }
            }
        )

        CriteriaSlider(
            label = "Valor da Corrida",
            value = weights.rideValue,
            maxValue = maxForCriteria(weights.rideValue),
            onValueChange = { newVal ->
                scope.launch {
                    prefsManager.saveCriteriaWeights(weights.copy(rideValue = newVal))
                }
            }
        )

        CriteriaSlider(
            label = "Duração da Corrida",
            value = weights.rideDuration,
            maxValue = maxForCriteria(weights.rideDuration),
            onValueChange = { newVal ->
                scope.launch {
                    prefsManager.saveCriteriaWeights(weights.copy(rideDuration = newVal))
                }
            }
        )

        CriteriaSlider(
            label = "Distância até Embarque",
            value = weights.pickupDistance,
            maxValue = maxForCriteria(weights.pickupDistance),
            onValueChange = { newVal ->
                scope.launch {
                    prefsManager.saveCriteriaWeights(weights.copy(pickupDistance = newVal))
                }
            }
        )

        CriteriaSlider(
            label = "Distância até Desembarque",
            value = weights.dropoffDistance,
            maxValue = maxForCriteria(weights.dropoffDistance),
            onValueChange = { newVal ->
                scope.launch {
                    prefsManager.saveCriteriaWeights(weights.copy(dropoffDistance = newVal))
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

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
                    Icons.Default.Info,
                    contentDescription = "Info"
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (showThresholds) {
            ThresholdField(
                label = "R$/KM mínimo",
                value = thresholds.minValuePerKm,
                suffix = "R$/km",
                onValueChange = { newVal ->
                    scope.launch {
                        prefsManager.saveDriverThresholds(thresholds.copy(minValuePerKm = newVal))
                    }
                }
            )

            ThresholdField(
                label = "R$/Hora mínimo",
                value = thresholds.minValuePerHour,
                suffix = "R$/h",
                onValueChange = { newVal ->
                    scope.launch {
                        prefsManager.saveDriverThresholds(thresholds.copy(minValuePerHour = newVal))
                    }
                }
            )

            ThresholdField(
                label = "Valor mínimo da corrida",
                value = thresholds.minRideValue,
                suffix = "R$",
                onValueChange = { newVal ->
                    scope.launch {
                        prefsManager.saveDriverThresholds(thresholds.copy(minRideValue = newVal))
                    }
                }
            )

            ThresholdField(
                label = "Distância máx. até embarque",
                value = thresholds.maxPickupDistance,
                suffix = "km",
                onValueChange = { newVal ->
                    scope.launch {
                        prefsManager.saveDriverThresholds(thresholds.copy(maxPickupDistance = newVal))
                    }
                }
            )

            ThresholdField(
                label = "Avaliação mínima do passageiro",
                value = thresholds.minPassengerRating,
                suffix = "estrelas",
                maxValue = 5.0,
                onValueChange = { newVal ->
                    scope.launch {
                        prefsManager.saveDriverThresholds(thresholds.copy(minPassengerRating = newVal))
                    }
                }
            )

            ThresholdField(
                label = "Duração máxima aceitável",
                value = thresholds.maxDuration,
                suffix = "min",
                onValueChange = { newVal ->
                    scope.launch {
                        prefsManager.saveDriverThresholds(thresholds.copy(maxDuration = newVal))
                    }
                }
            )

            ThresholdIntField(
                label = "Máximo de paradas",
                value = thresholds.maxStops,
                suffix = "paradas",
                onValueChange = { newVal ->
                    scope.launch {
                        prefsManager.saveDriverThresholds(thresholds.copy(maxStops = newVal))
                    }
                }
            )

            ThresholdField(
                label = "Distância mín. do destino",
                value = thresholds.minDropoffDistance,
                suffix = "km",
                onValueChange = { newVal ->
                    scope.launch {
                        prefsManager.saveDriverThresholds(thresholds.copy(minDropoffDistance = newVal))
                    }
                }
            )
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
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
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

        Spacer(modifier = Modifier.height(24.dp))
        Divider()
        Spacer(modifier = Modifier.height(16.dp))

        // ================================================================
        // SEÇÃO: ZONAS BLOQUEADAS (Editor de Mapa)
        // v5.1.0: Movido de Config para Critérios (faz mais sentido aqui)
        // ================================================================
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
            Icon(Icons.Default.EditLocation, contentDescription = "Editar zona")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Abrir Editor de Zonas")
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ================================================================
        // SEÇÃO: BAIRROS BLOQUEADOS (Embarque + Destino)
        // v5.1.0: Movido de Config para Critérios
        // ================================================================
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
                                prefsManager.saveBlockedPickup(
                                    blockedPickup + names.map { it to newNeighborhoodWeight }
                                )
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
                                prefsManager.saveBlockedDropoff(
                                    blockedDropoff + names.map { it to newNeighborhoodWeight }
                                )
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
    }
}

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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
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

        // Progress bar
        LinearProgressIndicator(
            progress = (total.coerceAtMost(100) / 100f),
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
    // maxValue é o máximo real disponível (100 - soma dos outros)
    // O slider vai de 0 até maxValue, impedindo visualmente de ultrapassar
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
                // Limita estritamente ao máximo disponível (100 - soma dos outros)
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
