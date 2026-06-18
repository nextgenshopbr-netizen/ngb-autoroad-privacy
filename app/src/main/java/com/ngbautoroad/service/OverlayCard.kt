package com.ngbautoroad.service

// ============================================================================
// ARQUIVO: OverlayCard.kt
// LOCALIZAÇÃO: service/OverlayCard.kt
// RESPONSABILIDADE: Composable do card visual que aparece no overlay flutuante
// LÓGICA:
//   - Recebe GalleryCard (template visual) + RideData + RideScore
//   - Renderiza apenas os campos definidos em galleryCard.fields
//   - Cores dinâmicas baseadas no ScoreLevel (verde/amarelo/laranja/vermelho)
// DEPENDÊNCIAS:
//   - data/model/CardGallery.kt → GalleryCard, CardField, EditorField
//   - data/model/RideData.kt → RideData, RideScore, ScoreLevel
// DEPENDENTES:
//   - service/OverlayService.kt → usa este composable no ComposeView
// ============================================================================

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngbautoroad.data.model.*
import com.ngbautoroad.data.model.CardGallery.CardField
import com.ngbautoroad.ui.theme.*
import kotlin.math.roundToInt

/**
 * Card overlay informativo (SEM botões aceitar/recusar)
 * Usa o card da galeria selecionado pelo motorista.
 * Suporta resize e acessibilidade (A+/A-).
 */
@Composable
fun OverlayCard(
    ride: RideData?,
    score: RideScore?,
    galleryCard: CardGallery.GalleryCard?,
    fontScale: Float = 1.0f,
    onDismiss: () -> Unit,
    onFontScaleChange: (Float) -> Unit = {}
) {
    if (ride == null || score == null) return

    val totalScoreColor = getScoreColor(score.scoreColor)
    val card = galleryCard

    // Cores do card (usa galeria ou fallback)
    val bgColor = Color(card?.backgroundColor ?: 0xFF101830)
    val textColor = Color(card?.textColor ?: 0xFFFFFFFF)
    val accentColor = Color(card?.accentColor ?: 0xFF4F6BFF)
    // BORDA DINÂMICA: cor muda conforme o score (verde/amarelo/laranja/vermelho)
    val borderColor = totalScoreColor
    val borderRadius = (card?.borderRadius ?: 12).dp
    val showBorder = card?.showBorder ?: true

    // Campos do card (usa galeria ou todos)
    val fields = card?.fields ?: CardField.entries

    // Font sizes com escala de acessibilidade
    val baseFontSize = (card?.fontSize ?: 14).sp
    val scaledTitle = (22 * fontScale).sp
    val scaledBody = (baseFontSize.value * fontScale).sp
    val scaledSmall = (11 * fontScale).sp
    val scaledLabel = (12 * fontScale).sp

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(borderRadius))
            .then(
                if (showBorder) Modifier.border(2.dp, borderColor, RoundedCornerShape(borderRadius))
                else Modifier
            )
            .background(bgColor)
            .padding(12.dp)
    ) {
        Column {
            // Header: Platform + Score + A+/A- + Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Platform
                if (fields.contains(CardField.PLATFORM)) {
                    Text(
                        text = ride.platform.displayName,
                        color = accentColor,
                        fontSize = scaledBody,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Score
                    if (fields.contains(CardField.SCORE)) {
                        Text(
                            text = "${score.totalScore.toInt()}",
                            color = totalScoreColor,
                            fontSize = scaledTitle,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    // Acessibilidade A- / A+
                    Icon(
                        Icons.Default.TextDecrease,
                        contentDescription = "Diminuir fonte",
                        tint = textColor.copy(alpha = 0.5f),
                        modifier = Modifier
                            .size(18.dp)
                            .clickable {
                                val newScale = (fontScale - 0.1f).coerceIn(1.0f, 2.5f)
                                onFontScaleChange(newScale)
                            }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.TextIncrease,
                        contentDescription = "Aumentar fonte",
                        tint = textColor.copy(alpha = 0.5f),
                        modifier = Modifier
                            .size(18.dp)
                            .clickable {
                                val newScale = (fontScale + 0.1f).coerceIn(1.0f, 2.5f)
                                onFontScaleChange(newScale)
                            }
                    )
                    Spacer(modifier = Modifier.width(6.dp))

                    // Close
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Fechar",
                        tint = textColor.copy(alpha = 0.6f),
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onDismiss() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Campos dinâmicos baseados no card da galeria
            fields.forEach { field ->
                when (field) {
                    CardField.SCORE -> {} // Já exibido no header
                    CardField.PLATFORM -> {} // Já exibido no header
                    CardField.RIDE_TYPE -> {
                        if (ride.rideType != RideType.UNKNOWN) {
                            OverlayCriteriaRow(
                                label = field.shortLabel,
                                value = ride.rideType.displayName,
                                textColor = textColor,
                                valueColor = accentColor,
                                fontSize = scaledLabel
                            )
                        }
                    }
                    CardField.RIDE_VALUE -> {
                        OverlayCriteriaRow(
                            label = field.shortLabel,
                            value = "R$ ${"%.2f".format(ride.rideValue)}",
                            textColor = textColor,
                            valueColor = getCriteriaColor(score, "rideValue", accentColor),
                            fontSize = scaledLabel
                        )
                    }
                    CardField.VALUE_PER_KM -> {
                        OverlayCriteriaRow(
                            label = field.shortLabel,
                            value = "R$ ${"%.2f".format(ride.valuePerKm)}",
                            textColor = textColor,
                            valueColor = getCriteriaColor(score, "valuePerKm", accentColor),
                            fontSize = scaledLabel
                        )
                    }
                    CardField.VALUE_PER_HOUR -> {
                        OverlayCriteriaRow(
                            label = field.shortLabel,
                            value = "R$ ${"%.0f".format(ride.valuePerHour)}",
                            textColor = textColor,
                            valueColor = getCriteriaColor(score, "valuePerHour", accentColor),
                            fontSize = scaledLabel
                        )
                    }
                    CardField.PICKUP_DISTANCE -> {
                        OverlayCriteriaRow(
                            label = field.shortLabel,
                            value = "${"%.1f".format(ride.pickupDistance)} km",
                            textColor = textColor,
                            valueColor = getCriteriaColor(score, "pickupDistance", accentColor),
                            fontSize = scaledLabel
                        )
                    }
                    CardField.DROPOFF_DISTANCE -> {
                        OverlayCriteriaRow(
                            label = field.shortLabel,
                            value = "${"%.1f".format(ride.dropoffDistance)} km",
                            textColor = textColor,
                            valueColor = getCriteriaColor(score, "dropoffDistance", accentColor),
                            fontSize = scaledLabel
                        )
                    }
                    CardField.DURATION -> {
                        OverlayCriteriaRow(
                            label = field.shortLabel,
                            value = "${ride.rideDuration.toInt()} min",
                            textColor = textColor,
                            valueColor = getCriteriaColor(score, "rideDuration", accentColor),
                            fontSize = scaledLabel
                        )
                    }
                    CardField.PASSENGER_RATING -> {
                        if (ride.passengerRating > 0) {
                            OverlayCriteriaRow(
                                label = field.shortLabel,
                                value = "★ ${"%.1f".format(ride.passengerRating)}",
                                textColor = textColor,
                                valueColor = getCriteriaColor(score, "passengerRating", accentColor),
                                fontSize = scaledLabel
                            )
                        }
                    }
                    CardField.STOPS -> {
                        if (ride.intermediateStops > 0) {
                            OverlayCriteriaRow(
                                label = field.shortLabel,
                                value = "${ride.intermediateStops}",
                                textColor = textColor,
                                valueColor = getCriteriaColor(score, "intermediateStops", accentColor),
                                fontSize = scaledLabel
                            )
                        }
                    }
                    CardField.PICKUP_NEIGHBORHOOD -> {
                        if (ride.pickupNeighborhood.isNotBlank()) {
                            OverlayCriteriaRow(
                                label = field.shortLabel,
                                value = ride.pickupNeighborhood,
                                textColor = textColor,
                                valueColor = textColor.copy(alpha = 0.9f),
                                fontSize = scaledLabel
                            )
                        }
                    }
                    CardField.DROPOFF_NEIGHBORHOOD -> {
                        if (ride.dropoffNeighborhood.isNotBlank()) {
                            OverlayCriteriaRow(
                                label = field.shortLabel,
                                value = ride.dropoffNeighborhood,
                                textColor = textColor,
                                valueColor = textColor.copy(alpha = 0.9f),
                                fontSize = scaledLabel
                            )
                        }
                    }
                    CardField.SCORE_BAR -> {
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = (score.totalScore / 100.0).toFloat().coerceIn(0f, 1f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = totalScoreColor,
                            trackColor = textColor.copy(alpha = 0.15f),
                        )
                    }
                }
            }

            // Cores nos campos já comunicam violações visualmente
        }
    }
}

@Composable
fun OverlayCriteriaRow(
    label: String,
    value: String,
    textColor: Color,
    valueColor: Color,
    fontSize: androidx.compose.ui.unit.TextUnit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = textColor.copy(alpha = 0.6f),
            fontSize = fontSize
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            color = valueColor,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

/**
 * Obtém a cor de um critério específico baseado no score calculado
 */
private fun getCriteriaColor(score: RideScore, criteriaKey: String, fallback: Color): Color {
    val criteria = score.criteriaScores[criteriaKey]
    return if (criteria != null) {
        getScoreColor(criteria.level)
    } else {
        fallback
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
