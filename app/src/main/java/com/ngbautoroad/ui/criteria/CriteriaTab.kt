package com.ngbautoroad.ui.criteria

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
    val scope = rememberCoroutineScope()
    val weights by prefsManager.criteriaWeightsFlow.collectAsState(initial = CriteriaWeights())
    val thresholds by prefsManager.driverThresholdsFlow.collectAsState(initial = DriverThresholds())

    var showThresholds by remember { mutableStateOf(true) }
    val scrollState = rememberScrollState()

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
            "Defina individualmente o peso de cada critério (0-100)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Sliders INDIVIDUAIS para cada critério (sem redistribuição)
        CriteriaSlider(
            label = "Valor por KM",
            value = weights.valuePerKm,
            onValueChange = { newVal ->
                scope.launch {
                    prefsManager.saveCriteriaWeights(weights.copy(valuePerKm = newVal))
                }
            }
        )

        CriteriaSlider(
            label = "Valor por Hora",
            value = weights.valuePerHour,
            onValueChange = { newVal ->
                scope.launch {
                    prefsManager.saveCriteriaWeights(weights.copy(valuePerHour = newVal))
                }
            }
        )

        CriteriaSlider(
            label = "Paradas Intermediárias",
            value = weights.intermediateStops,
            onValueChange = { newVal ->
                scope.launch {
                    prefsManager.saveCriteriaWeights(weights.copy(intermediateStops = newVal))
                }
            }
        )

        CriteriaSlider(
            label = "Avaliação do Passageiro",
            value = weights.passengerRating,
            onValueChange = { newVal ->
                scope.launch {
                    prefsManager.saveCriteriaWeights(weights.copy(passengerRating = newVal))
                }
            }
        )

        CriteriaSlider(
            label = "Avaliação de Usuários",
            value = weights.userRating,
            onValueChange = { newVal ->
                scope.launch {
                    prefsManager.saveCriteriaWeights(weights.copy(userRating = newVal))
                }
            }
        )

        CriteriaSlider(
            label = "Valor da Corrida",
            value = weights.rideValue,
            onValueChange = { newVal ->
                scope.launch {
                    prefsManager.saveCriteriaWeights(weights.copy(rideValue = newVal))
                }
            }
        )

        CriteriaSlider(
            label = "Duração da Corrida",
            value = weights.rideDuration,
            onValueChange = { newVal ->
                scope.launch {
                    prefsManager.saveCriteriaWeights(weights.copy(rideDuration = newVal))
                }
            }
        )

        CriteriaSlider(
            label = "Distância até Embarque",
            value = weights.pickupDistance,
            onValueChange = { newVal ->
                scope.launch {
                    prefsManager.saveCriteriaWeights(weights.copy(pickupDistance = newVal))
                }
            }
        )

        CriteriaSlider(
            label = "Distância até Desembarque",
            value = weights.dropoffDistance,
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
                onValueChange = { newVal ->
                    scope.launch {
                        prefsManager.saveDriverThresholds(thresholds.copy(minPassengerRating = newVal.coerceIn(0.0, 5.0)))
                    }
                }
            )

            ThresholdField(
                label = "Avaliação mínima de usuários",
                value = thresholds.minUserRating,
                suffix = "estrelas",
                onValueChange = { newVal ->
                    scope.launch {
                        prefsManager.saveDriverThresholds(thresholds.copy(minUserRating = newVal.coerceIn(0.0, 5.0)))
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
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Salvar Critérios", color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
fun PointsCounter(weights: CriteriaWeights) {
    val total = weights.totalUsed
    val statusText = if (total == 0) "Nenhum peso configurado" else "$total pontos distribuídos"
    val usedColor = when {
        total >= 80 -> ScoreGreen
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
                text = "$total",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = usedColor
            )
        }
    }
}

@Composable
fun CriteriaSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
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
            Text(
                text = "$value pts",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (value > 0) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..100f,
            steps = 99,
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
    onValueChange: (Double) -> Unit
) {
    var textValue by remember(value) {
        mutableStateOf(if (value == 0.0) "" else String.format("%.2f", value))
    }

    OutlinedTextField(
        value = textValue,
        onValueChange = { input ->
            textValue = input
            val parsed = input.replace(",", ".").toDoubleOrNull()
            if (parsed != null) {
                onValueChange(parsed)
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
