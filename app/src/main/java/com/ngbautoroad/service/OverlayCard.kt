package com.ngbautoroad.service

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngbautoroad.data.model.*
import com.ngbautoroad.ui.theme.*

/**
 * Card overlay informativo (SEM botões aceitar/recusar)
 * Cada campo recebe a cor do score calculado para aquele critério
 */
@Composable
fun OverlayCard(
    ride: RideData?,
    score: RideScore?,
    onDismiss: () -> Unit
) {
    if (ride == null || score == null) return

    val totalScoreColor = getScoreColor(score.scoreColor)

    Box(
        modifier = Modifier
            .width(300.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, OverlayBorder, RoundedCornerShape(12.dp))
            .background(OverlayBackground)
            .padding(12.dp)
    ) {
        Column {
            // Header: Platform + Score + Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = ride.platform.displayName,
                    color = Color(0xFF4F6BFF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Score badge
                    Text(
                        text = "${score.totalScore.toInt()}",
                        color = totalScoreColor,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Fechar",
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { onDismiss() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Ride basic info
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "R$ ${"%.2f".format(ride.rideValue)}",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "${"%.1f".format(ride.dropoffDistance)} km",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${ride.rideDuration.toInt()} min",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
            }

            if (ride.passengerRating > 0) {
                Text(
                    text = "★ ${"%.2f".format(ride.passengerRating)}",
                    color = Color(0xFFFFD700),
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Divider(color = Color.White.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(8.dp))

            // Criteria scores com cores individuais
            score.criteriaScores.forEach { (_, criteria) ->
                CriteriaRow(criteria)
            }

            // Bairros (se disponíveis)
            if (ride.pickupNeighborhood.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "📍 ${ride.pickupNeighborhood} → ${ride.dropoffNeighborhood}",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun CriteriaRow(criteria: CriteriaScore) {
    val color = getScoreColor(criteria.level)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = criteria.name,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp
        )
        Row {
            Text(
                text = formatCriteriaValue(criteria),
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${criteria.weightedScore.toInt()}/${criteria.weight}",
                color = color.copy(alpha = 0.7f),
                fontSize = 10.sp
            )
        }
    }
}

private fun formatCriteriaValue(criteria: CriteriaScore): String {
    return when (criteria.name) {
        "Valor/KM" -> "R$ ${"%.2f".format(criteria.rawValue)}"
        "Valor/Hora" -> "R$ ${"%.0f".format(criteria.rawValue)}"
        "Paradas" -> "${criteria.rawValue.toInt()}"
        "Avaliação" -> "${"%.2f".format(criteria.rawValue)}"
        "Valor Corrida" -> "R$ ${"%.2f".format(criteria.rawValue)}"
        "Duração" -> "${criteria.rawValue.toInt()} min"
        "Dist. Embarque" -> "${"%.1f".format(criteria.rawValue)} km"
        "Dist. Destino" -> "${"%.1f".format(criteria.rawValue)} km"
        else -> "${"%.1f".format(criteria.rawValue)}"
    }
}

fun getScoreColor(level: ScoreLevel): Color {
    return when (level) {
        ScoreLevel.GREEN -> ScoreGreen
        ScoreLevel.YELLOW -> ScoreYellow
        ScoreLevel.ORANGE -> ScoreOrange
        ScoreLevel.RED -> ScoreRed
    }
}
