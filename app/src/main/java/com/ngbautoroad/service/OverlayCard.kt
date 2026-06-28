package com.ngbautoroad.service

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngbautoroad.data.model.*
import com.ngbautoroad.ui.theme.*

@Composable
fun OverlayCard(
    ride: RideData?,
    score: RideScore?,
    fontScale: Float = 1.0f,
    cardType: String = "STANDARD",
    showScore: Boolean = true,
    showValuePerKm: Boolean = true,
    showValuePerHour: Boolean = true,
    showRideValue: Boolean = true,
    showDuration: Boolean = true,
    showTotalKm: Boolean = true,
    showNeighborhoods: Boolean = true,
    onDismiss: () -> Unit,
    onDrag: (Float, Float) -> Unit
) {
    if (ride == null || score == null) return

    val totalScoreColor = getScoreColor(score.scoreColor)
    // Se o score final for amarelo (alerta) ou vermelho (ruim), a borda será mais grossa para destacar a penalidade global
    val borderWidth = if (score.scoreColor == com.ngbautoroad.data.model.ScoreLevel.YELLOW || score.scoreColor == com.ngbautoroad.data.model.ScoreLevel.RED) 4.dp else 1.5.dp
    
    // Fundo premium translúcido simulando glassmorphism
    val bgColor = Color(0xEB11141E) 
    val textColor = Color.White
    val accentColor = Color(0xFF4F6BFF)
    
    // Se fontScale muito alto, reduz o título para não transbordar na coluna
    val effectiveTitleScale = if (fontScale > 1.2f) 1.2f else fontScale
    val scaledTitle = (22 * effectiveTitleScale).sp
    val scaledBody = (14 * fontScale).sp
    val scaledSmall = (12 * fontScale).sp
    val scaledTiny = (10 * fontScale).sp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .border(borderWidth, totalScoreColor.copy(alpha = 0.9f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            
            // CABEÇALHO (Plataforma, Score e Fechar)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (cardType == "STANDARD" || showScore) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(totalScoreColor.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${score.totalScore.toInt()} PTS",
                                color = totalScoreColor,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = scaledSmall
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = ride.platform.displayName,
                        color = textColor.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Bold,
                        fontSize = scaledBody
                    )
                }
                
                Text(
                    text = "✕",
                    color = textColor.copy(alpha = 0.5f),
                    fontSize = (16 * fontScale).sp,
                    modifier = Modifier
                        .clickable { onDismiss() }
                        .padding(4.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // MÉTRICAS PRINCIPAIS (R$/km e R$/h)
            if (cardType == "STANDARD" || showValuePerKm || showValuePerHour) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.Top
                ) {
                    if (cardType == "STANDARD" || showValuePerKm) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text("Ganhos por KM", color = textColor.copy(alpha = 0.5f), fontSize = scaledTiny)
                            Text(
                                text = "R$ ${"%.2f".format(ride.valuePerKm)}",
                                color = getCriteriaColor(score, "valuePerKm", accentColor),
                                fontWeight = FontWeight.Black,
                                fontSize = scaledTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    // Separador vertical entre as colunas
                    if ((cardType == "STANDARD" || showValuePerKm) && (cardType == "STANDARD" || showValuePerHour)) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height((scaledTitle.value * 1.5f).dp)
                                .background(textColor.copy(alpha = 0.1f))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (cardType == "STANDARD" || showValuePerHour) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text("Ganhos por Hora", color = textColor.copy(alpha = 0.5f), fontSize = scaledTiny)
                            Text(
                                text = "R$ ${"%.2f".format(ride.valuePerHour)}",
                                color = getCriteriaColor(score, "valuePerHour", accentColor),
                                fontWeight = FontWeight.Black,
                                fontSize = scaledTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // DETALHES MENORES (Valor, Tempo, KM) — peso igual para cada item visível
            if (cardType == "STANDARD" || showRideValue || showDuration || showTotalKm) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (cardType == "STANDARD" || showRideValue) {
                        DetailItem(
                            "Valor", "R$ ${"%.2f".format(ride.rideValue)}",
                            textColor, scaledBody, modifier = Modifier.weight(1f)
                        )
                    }
                    if (cardType == "STANDARD" || showDuration) {
                        DetailItem(
                            "Tempo", "${ride.rideDuration} min",
                            textColor, scaledBody, modifier = Modifier.weight(1f)
                        )
                    }
                    if (cardType == "STANDARD" || showTotalKm) {
                        DetailItem(
                            "KM Total", "${"%.1f".format(ride.pickupDistance + ride.dropoffDistance)} km",
                            textColor, scaledBody, modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
            
            // BAIRROS — permite 2 linhas para nomes longos não serem cortados
            if (cardType == "STANDARD" || showNeighborhoods) {
                if (ride.pickupNeighborhood.isNotBlank() || ride.dropoffNeighborhood.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "${ride.pickupNeighborhood} ➔ ${ride.dropoffNeighborhood}",
                            color = textColor.copy(alpha = 0.85f),
                            fontSize = scaledSmall,
                            maxLines = 2,  // Permite quebrar linha para nomes longos
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = (scaledSmall.value * 1.3f).sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
            
            // PROGRESS BAR DE SCORE
            LinearProgressIndicator(
                progress = (score.totalScore / 100.0).toFloat().coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp)),
                color = totalScoreColor,
                trackColor = textColor.copy(alpha = 0.1f)
            )
        }
    }
}

@Composable
fun DetailItem(
    label: String,
    value: String,
    textColor: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Text(text = label, color = textColor.copy(alpha = 0.5f), fontSize = 10.sp)
        Text(
            text = value,
            color = textColor.copy(alpha = 0.9f),
            fontWeight = FontWeight.SemiBold,
            fontSize = fontSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

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
