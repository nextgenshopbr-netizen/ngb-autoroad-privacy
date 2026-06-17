package com.ngbautoroad.ui.card

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngbautoroad.data.model.CardGallery
import com.ngbautoroad.data.model.CardModel
import com.ngbautoroad.data.prefs.PrefsManager
import com.ngbautoroad.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun CardTab(prefsManager: PrefsManager) {
    val scope = rememberCoroutineScope()
    val activeSlot by prefsManager.activeCardSlotFlow.collectAsState(initial = 1)
    val card1ModelId by prefsManager.card1ModelIdFlow.collectAsState(initial = 1)
    val card2ModelId by prefsManager.card2ModelIdFlow.collectAsState(initial = 2)
    val card3Custom by prefsManager.card3CustomFlow.collectAsState(initial = CardModel(0, "Custom", isCustom = true))

    var showGallery by remember { mutableStateOf(false) }
    var galleryTargetSlot by remember { mutableIntStateOf(1) }
    var showCustomEditor by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    if (showGallery) {
        CardGalleryDialog(
            onDismiss = { showGallery = false },
            onSelect = { modelId ->
                scope.launch {
                    prefsManager.setCardModelId(galleryTargetSlot, modelId)
                }
                showGallery = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = "Cards de Overlay",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Card Slot 1
        CardSlotItem(
            slotNumber = 1,
            isActive = activeSlot == 1,
            model = CardGallery.getModelById(card1ModelId),
            isCustom = false,
            onActivate = { scope.launch { prefsManager.setActiveCardSlot(1) } },
            onChangeModel = {
                galleryTargetSlot = 1
                showGallery = true
            },
            onEditCustom = {}
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Card Slot 2
        CardSlotItem(
            slotNumber = 2,
            isActive = activeSlot == 2,
            model = CardGallery.getModelById(card2ModelId),
            isCustom = false,
            onActivate = { scope.launch { prefsManager.setActiveCardSlot(2) } },
            onChangeModel = {
                galleryTargetSlot = 2
                showGallery = true
            },
            onEditCustom = {}
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Card Slot 3 (Custom - sem galeria)
        CardSlotItem(
            slotNumber = 3,
            isActive = activeSlot == 3,
            model = card3Custom,
            isCustom = true,
            onActivate = { scope.launch { prefsManager.setActiveCardSlot(3) } },
            onChangeModel = {},
            onEditCustom = { showCustomEditor = true }
        )

        if (showCustomEditor) {
            Spacer(modifier = Modifier.height(16.dp))
            CustomCardEditor(
                card = card3Custom,
                onSave = { updatedCard ->
                    scope.launch {
                        prefsManager.saveCard3Custom(updatedCard)
                    }
                    showCustomEditor = false
                },
                onDismiss = { showCustomEditor = false }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Preview do card ativo
        Text(
            text = "Preview",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))

        val activeModel = when (activeSlot) {
            1 -> CardGallery.getModelById(card1ModelId)
            2 -> CardGallery.getModelById(card2ModelId)
            3 -> card3Custom
            else -> CardGallery.getModelById(1)
        }
        CardPreview(model = activeModel)
    }
}

@Composable
fun CardSlotItem(
    slotNumber: Int,
    isActive: Boolean,
    model: CardModel,
    isCustom: Boolean,
    onActivate: () -> Unit,
    onChangeModel: () -> Unit,
    onEditCustom: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onActivate() },
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isActive) CardDefaults.outlinedCardBorder() else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Radio indicator
            RadioButton(
                selected = isActive,
                onClick = onActivate
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Card $slotNumber",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isCustom) "Custom (exclusivo)" else model.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isCustom) {
                IconButton(onClick = onEditCustom) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Editar",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                IconButton(onClick = onChangeModel) {
                    Icon(
                        Icons.Default.Palette,
                        contentDescription = "Galeria",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun CardPreview(model: CardModel) {
    val bgColor = Color(model.backgroundColor)
    val textColor = Color(model.textColor)
    val accentColor = Color(model.accentColor)
    val borderColor = Color(model.borderColor)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(model.borderRadius.dp))
            .border(2.dp, borderColor, RoundedCornerShape(model.borderRadius.dp))
            .background(bgColor)
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = "Uber • 4.92 ★",
                color = accentColor,
                fontSize = model.fontSize.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("R$ 18,50", color = textColor, fontSize = model.fontSize.sp)
                    Text("3.2 km • 12 min", color = textColor.copy(alpha = 0.7f), fontSize = (model.fontSize - 2).sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Score", color = accentColor, fontSize = (model.fontSize - 2).sp)
                    Text("78", color = ScoreGreen, fontSize = (model.fontSize + 4).sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Campos com cores
            ScoreFieldPreview("Valor/KM", "R$ 5.78", ScoreGreen, textColor, model.fontSize)
            ScoreFieldPreview("Valor/Hora", "R$ 92.50", ScoreGreen, textColor, model.fontSize)
            ScoreFieldPreview("Paradas", "0", ScoreGreen, textColor, model.fontSize)
            ScoreFieldPreview("Avaliação", "4.92", ScoreGreen, textColor, model.fontSize)
        }
    }
}

@Composable
fun ScoreFieldPreview(label: String, value: String, scoreColor: Color, textColor: Color, fontSize: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = textColor.copy(alpha = 0.8f), fontSize = (fontSize - 2).sp)
        Text(text = value, color = scoreColor, fontSize = (fontSize - 1).sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun CardGalleryDialog(
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Galeria de Cards") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                CardGallery.models.forEach { model ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onSelect(model.id) },
                        colors = CardDefaults.cardColors(
                            containerColor = Color(model.backgroundColor)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(model.accentColor))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = model.name,
                                color = Color(model.textColor),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun CustomCardEditor(
    card: CardModel,
    onSave: (CardModel) -> Unit,
    onDismiss: () -> Unit
) {
    var bgColor by remember { mutableLongStateOf(card.backgroundColor) }
    var textColor by remember { mutableLongStateOf(card.textColor) }
    var accentColor by remember { mutableLongStateOf(card.accentColor) }
    var borderColor by remember { mutableLongStateOf(card.borderColor) }
    var borderRadius by remember { mutableIntStateOf(card.borderRadius) }
    var fontSize by remember { mutableIntStateOf(card.fontSize) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Editor Custom (Card 3)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Border Radius
            Text("Borda: ${borderRadius}dp", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = borderRadius.toFloat(),
                onValueChange = { borderRadius = it.toInt() },
                valueRange = 0f..24f
            )

            // Font Size
            Text("Fonte: ${fontSize}sp", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = fontSize.toFloat(),
                onValueChange = { fontSize = it.toInt() },
                valueRange = 10f..20f
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
                Button(onClick = {
                    onSave(
                        card.copy(
                            backgroundColor = bgColor,
                            textColor = textColor,
                            accentColor = accentColor,
                            borderColor = borderColor,
                            borderRadius = borderRadius,
                            fontSize = fontSize
                        )
                    )
                }) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Salvar / Testar")
                }
            }
        }
    }
}
