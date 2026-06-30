package com.ngbautoroad.service

import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngbautoroad.data.model.*
import com.ngbautoroad.ui.theme.*

// ============================================================================
// ARQUIVO: OverlayCard.kt
// RESPONSABILIDADE: Card flutuante de avaliação de corrida (redesign v2.0)
// DESIGN: Inspirado nas melhores práticas de legibilidade para motoristas.
//   - 3 métricas principais em destaque: R$/km, R$/h, Lucro estimado
//   - Código de cor unificado: Verde=boa, Amarelo=ok, Vermelho=ruim
//   - Score NGB como diferencial: barra + número bem visível
//   - Bairros de origem e destino com seta clara
//   - Card compacto: informação máxima no menor espaço
// DEPENDÊNCIAS:
//   - data/model/RideData.kt, RideScore.kt
//   - ui/theme/Color.kt (ScoreGreen, ScoreYellow, ScoreOrange, ScoreRed)
// DEPENDENTES:
//   - service/OverlayService.kt → instancia este composable
// ============================================================================

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

    // ── Cores baseadas no score global ──────────────────────────────────────
    val scoreColor = when {
        score.totalScore >= 85 -> ScoreGreen
        score.totalScore >= 75 -> Color(0xFFDAA520)  // Gold for "Razoável"
        score.totalScore >= 60 -> ScoreYellow
        score.totalScore >= 40 -> ScoreOrange
        else -> ScoreRed
    }
    val bgColor = Color(0xFF121318)
    val surfaceColor = Color(0xFF1C2030)       // Superfície dos cards internos
    val textPrimary = Color.White
    val textSecondary = Color(0xFF8A93A8)      // Cinza azulado para labels

    // ── Escala de fonte com limite ───────────────────────────────────────────
    val fs = fontScale.coerceIn(0.8f, 1.4f)
    val fsLarge  = (22 * fs).sp   // Números principais (R$/km, R$/h, lucro)
    val fsMedium = (12 * fs).sp   // Valores secundários
    val fsSmall  = (10 * fs).sp   // Labels e bairros
    val fsTiny   = (9  * fs).sp   // Sub-labels

    // ── Calcular lucro estimado ──────────────────────────────────────────────
    // Depende de: ride.rideValue, ride.pickupDistance, ride.dropoffDistance
    val costPerKm = score.criteriaScores["costPerKm"]?.rawValue ?: 0.35
    val totalKm = ride.pickupDistance + ride.dropoffDistance
    val estimatedCost = totalKm * costPerKm
    val estimatedProfit = ride.rideValue - estimatedCost

    // ── Label de qualidade ───────────────────────────────────────────────────
    val qualityLabel = when {
        score.totalScore >= 85 -> "✦ Oferta Boa"        // Green
        score.totalScore >= 75 -> "● Oferta Razoável"    // Yellow-gold (neutra alta)
        score.totalScore >= 60 -> "▽ Oferta Neutra"      // Orange (neutra baixa)
        score.totalScore >= 40 -> "▼ Oferta Fraca"        // Orange-red
        else -> "✕ Oferta Ruim"                           // Red
    }

    // ── Cores por critério ────────────────────────────────────────────────────
    // Cada campo reflete a cor do seu critério QUANDO ele está afetando o score
    // (ou seja, quando o critério existe no breakdown). Se o critério não está ativo
    // (peso 0 ou dado ausente), o campo fica neutro (branco) — sinalizando que não
    // está puxando o score para cima nem para baixo.
    val durationColor = getCriteriaColor(score, "rideDuration", textPrimary)
    val rideValueColor = getCriteriaColor(score, "rideValue", textPrimary)
    // Total km combina pickup+dropoff: prioriza a distância de destino (motor principal
    // do score), caindo para a de embarque se só ela estiver ativa.
    val kmColor = score.criteriaScores["dropoffDistance"]?.let { getScoreColor(it.level) }
        ?: score.criteriaScores["pickupDistance"]?.let { getScoreColor(it.level) }
        ?: textPrimary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(
                // v7.5.0: Bordas mais grossas e sempre na cor do score (verde um pouco
                // mais destacado por ser a oferta boa)
                width = if (score.scoreColor == ScoreLevel.GREEN) 3.5.dp else 3.dp,
                color = scoreColor,
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // ── FAIXA SUPERIOR: qualidade + plataforma + fechar ─────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                scoreColor.copy(alpha = 0.6f),
                                scoreColor.copy(alpha = 0.15f)
                            )
                        )
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Label de qualidade colorida
                    Text(
                        text = qualityLabel,
                        color = scoreColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = fsSmall
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Plataforma
                        Text(
                            text = ride.platform.displayName,
                            color = textSecondary,
                            fontSize = fsTiny
                        )
                        // Bairro destino no header
                        if (ride.dropoffNeighborhood.isNotBlank()) {
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "→ ${ride.dropoffNeighborhood}",
                                color = scoreColor,
                                fontSize = fsSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        // Fechar
                        Text(
                            text = "✕",
                            color = textSecondary,
                            fontSize = fsMedium,
                            modifier = Modifier
                                .clickable { onDismiss() }
                                .padding(4.dp)
                        )
                    }
                }
            }

            // ── MÉTRICAS PRINCIPAIS: R$/km | R$/h | Lucro ───────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // R$/km
                if (cardType == "STANDARD" || showValuePerKm) {
                    MetricBlock(
                        label = "R$/km",
                        value = "%.2f".format(ride.valuePerKm),
                        valueColor = getCriteriaColor(score, "valuePerKm", scoreColor),
                        labelColor = textSecondary,
                        fsLarge = fsLarge,
                        fsTiny = fsTiny,
                        modifier = Modifier.weight(1f)
                    )
                    MetricDivider()
                }

                // R$/h
                if (cardType == "STANDARD" || showValuePerHour) {
                    MetricBlock(
                        label = "R$/hora",
                        value = "%.0f".format(ride.valuePerHour),
                        valueColor = getCriteriaColor(score, "valuePerHour", scoreColor),
                        labelColor = textSecondary,
                        fsLarge = fsLarge,
                        fsTiny = fsTiny,
                        modifier = Modifier.weight(1f)
                    )
                    MetricDivider()
                }

                // Lucro estimado — diferencial NGB vs Gigu
                MetricBlock(
                    label = "Lucro",
                    value = "%.2f".format(estimatedProfit),
                    // Reflete o critério Lucro/KM quando ativo; senão, cor pelo sinal do lucro
                    valueColor = getCriteriaColor(score, "profitPerKm", if (estimatedProfit >= 0) ScoreGreen else ScoreRed),
                    labelColor = textSecondary,
                    fsLarge = fsLarge,
                    fsTiny = fsTiny,
                    modifier = Modifier.weight(1f)
                )
            }

            // ── LINHA DE DETALHES: tempo + km + valor bruto ─────────────────
            if (cardType == "STANDARD" || showDuration || showTotalKm || showRideValue) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    if (cardType == "STANDARD" || showDuration) {
                        ChipDetail(
                            icon = "⏱",
                            text = "${ride.rideDuration}min",
                            textColor = durationColor,
                            fontSize = fsMedium
                        )
                    }
                    if (cardType == "STANDARD" || showTotalKm) {
                        ChipDetail(
                            icon = "📍",
                            text = "${"%.1f".format(totalKm)}km",
                            textColor = kmColor,
                            fontSize = fsMedium
                        )
                    }
                    if (cardType == "STANDARD" || showRideValue) {
                        ChipDetail(
                            icon = "R$",
                            text = "%.2f".format(ride.rideValue),
                            textColor = rideValueColor,
                            fontSize = fsMedium
                        )
                    }
                }
            }

            // ── BAIRROS: origem → destino ────────────────────────────────────
            // v7.5.0: Bairro é informação crítica — exibido em TODOS os cards exceto o
            // Customizável (onde o motorista pode desligar via toggle). Bairro bloqueado
            // (penalidade > 0) aparece em vermelho, pois está puxando o score para baixo.
            if ((cardType != "CUSTOMIZABLE" || showNeighborhoods) &&
                (ride.pickupNeighborhood.isNotBlank() || ride.dropoffNeighborhood.isNotBlank())) {

                val pickupColor = if (score.pickupPenalty > 0) ScoreRed else textPrimary
                val dropoffColor = if (score.dropoffPenalty > 0) ScoreRed else textPrimary

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(surfaceColor)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Origem (vermelho se bairro bloqueado)
                    Text(
                        text = ride.pickupNeighborhood.ifBlank { "—" } + if (score.pickupPenalty > 0) " ⛔" else "",
                        color = pickupColor,
                        fontSize = fsMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    // Seta
                    Text(
                        text = "  →  ",
                        color = scoreColor,
                        fontSize = fsMedium,
                        fontWeight = FontWeight.Bold
                    )
                    // Destino (vermelho se bairro bloqueado)
                    Text(
                        text = ride.dropoffNeighborhood.ifBlank { "—" } + if (score.dropoffPenalty > 0) " ⛔" else "",
                        color = dropoffColor,
                        fontSize = fsMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End
                    )
                }
            }

            // ── BARRA DE SCORE NGB ───────────────────────────────────────────
            if (cardType == "STANDARD" || showScore) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Barra
                    LinearProgressIndicator(
                        progress = (score.totalScore / 100.0).toFloat().coerceIn(0f, 1f),
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = scoreColor,
                        trackColor = textSecondary.copy(alpha = 0.2f)
                    )
                    Spacer(Modifier.width(8.dp))
                    // Score numérico
                    Text(
                        text = "${score.totalScore.toInt()} pts",
                        color = scoreColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = fsSmall
                    )
                }
            }
        }
    }
}

// ── Componentes auxiliares ───────────────────────────────────────────────────

/**
 * Bloco de métrica principal: label em cima, valor grande embaixo.
 * Usado para R$/km, R$/h e Lucro.
 */
@Composable
private fun MetricBlock(
    label: String,
    value: String,
    valueColor: Color,
    labelColor: Color,
    fsLarge: androidx.compose.ui.unit.TextUnit,
    fsTiny: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = labelColor,
            fontSize = fsTiny,
            fontWeight = FontWeight.Normal
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            color = valueColor,
            fontWeight = FontWeight.Black,
            fontSize = fsLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Divisor vertical entre métricas principais.
 */
@Composable
private fun MetricDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(40.dp)
            .background(Color.White.copy(alpha = 0.15f))
    )
}

/**
 * Chip de detalhe secundário com ícone + texto.
 * Usado para tempo, km e valor bruto.
 */
@Composable
private fun ChipDetail(
    icon: String,
    text: String,
    textColor: Color,
    fontSize: androidx.compose.ui.unit.TextUnit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = icon, fontSize = fontSize, color = textColor.copy(alpha = 0.9f))
        Spacer(Modifier.width(3.dp))
        Text(
            text = text,
            color = textColor.copy(alpha = 0.9f),
            fontWeight = FontWeight.SemiBold,
            fontSize = fontSize
        )
    }
}

// ── Helpers de cor ───────────────────────────────────────────────────────────

private fun getCriteriaColor(score: RideScore, criteriaKey: String, fallback: Color): Color {
    val criteria = score.criteriaScores[criteriaKey]
    return if (criteria != null) getScoreColor(criteria.level) else fallback
}

fun getScoreColor(level: ScoreLevel): Color {
    return when (level) {
        ScoreLevel.GREEN  -> ScoreGreen
        ScoreLevel.YELLOW -> ScoreYellow
        ScoreLevel.ORANGE -> ScoreOrange
        ScoreLevel.RED    -> ScoreRed
    }
}
