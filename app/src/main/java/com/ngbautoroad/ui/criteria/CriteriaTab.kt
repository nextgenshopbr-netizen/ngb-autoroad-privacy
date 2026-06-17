package com.ngbautoroad.ui.criteria

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngbautoroad.data.model.CriteriaWeights
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

        // Sliders para cada critério
        CriteriaSlider(
            label = "Valor por KM",
            value = weights.valuePerKm,
            maxAvailable = weights.valuePerKm + weights.remaining,
            onValueChange = { newVal ->
                scope.launch {
                    prefsManager.saveCriteriaWeights(weights.copy(valuePerKm = newVal))
                }
            }
        )

        CriteriaSlider(
            label = "Valor por Hora",
            value = weights.valuePerHour,
            maxAvailable = weights.valuePerHour + weights.remaining,
            onValueChange = { newVal ->
                scope.launch {
                    prefsManager.saveCriteriaWeights(weights.copy(valuePerHour = newVal))
                }
            }
        )

        CriteriaSlider(
            label = "Paradas Intermediárias",
            value = weights.intermediateStops,
            maxAvailable = weights.intermediateStops + weights.remaining,
            onValueChange = { newVal ->
                scope.launch {
                    prefsManager.saveCriteriaWeights(weights.copy(intermediateStops = newVal))
                }
            }
        )

        CriteriaSlider(
            label = "Avaliação do Passageiro",
            value = weights.passengerRating,
            maxAvailable = weights.passengerRating + weights.remaining,
            onValueChange = { newVal ->
                scope.launch {
                    prefsManager.saveCriteriaWeights(weights.copy(passengerRating = newVal))
                }
            }
        )

        CriteriaSlider(
            label = "Valor da Corrida",
            value = weights.rideValue,
            maxAvailable = weights.rideValue + weights.remaining,
            onValueChange = { newVal ->
                scope.launch {
                    prefsManager.saveCriteriaWeights(weights.copy(rideValue = newVal))
                }
            }
        )

        CriteriaSlider(
            label = "Duração da Corrida",
            value = weights.rideDuration,
            maxAvailable = weights.rideDuration + weights.remaining,
            onValueChange = { newVal ->
                scope.launch {
                    prefsManager.saveCriteriaWeights(weights.copy(rideDuration = newVal))
                }
            }
        )

        CriteriaSlider(
            label = "Distância até Embarque",
            value = weights.pickupDistance,
            maxAvailable = weights.pickupDistance + weights.remaining,
            onValueChange = { newVal ->
                scope.launch {
                    prefsManager.saveCriteriaWeights(weights.copy(pickupDistance = newVal))
                }
            }
        )

        CriteriaSlider(
            label = "Distância até Desembarque",
            value = weights.dropoffDistance,
            maxAvailable = weights.dropoffDistance + weights.remaining,
            onValueChange = { newVal ->
                scope.launch {
                    prefsManager.saveCriteriaWeights(weights.copy(dropoffDistance = newVal))
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Botão Salvar
        Button(
            onClick = {
                scope.launch {
                    prefsManager.saveCriteriaWeights(weights)
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
    val usedColor = when {
        weights.totalUsed == 100 -> ScoreGreen
        weights.totalUsed > 80 -> ScoreYellow
        weights.totalUsed > 50 -> ScoreOrange
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
                    text = "Pontos Disponíveis",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${weights.remaining}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Usados",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${weights.totalUsed} / 100",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = usedColor
                )
            }
        }

        // Progress bar
        LinearProgressIndicator(
            progress = weights.totalUsed / 100f,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = usedColor,
            trackColor = MaterialTheme.colorScheme.surface,
        )
    }
}

@Composable
fun CriteriaSlider(
    label: String,
    value: Int,
    maxAvailable: Int,
    onValueChange: (Int) -> Unit
) {
    val effectiveMax = maxAvailable.coerceIn(0, 100)

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

        val sliderMax = effectiveMax.coerceAtLeast(1)
        val sliderSteps = (sliderMax - 1).coerceAtLeast(0)

        Slider(
            value = value.toFloat().coerceIn(0f, sliderMax.toFloat()),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..sliderMax.toFloat(),
            steps = sliderSteps,
            enabled = effectiveMax > 0 || value > 0,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}
