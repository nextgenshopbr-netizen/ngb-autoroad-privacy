package com.ngbautoroad.ui.editor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngbautoroad.data.model.CardGallery.CardField
import com.ngbautoroad.data.prefs.PrefsManager
import com.ngbautoroad.ui.theme.NGBAutoRoadTheme
import com.ngbautoroad.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/**
 * Editor visual completo do Card 3 (Custom exclusivo)
 *
 * Permite ao motorista:
 * - Adicionar/remover campos
 * - Arrastar campos para posicionar
 * - Alterar fonte (tamanho, negrito, itálico)
 * - Alterar cores (fundo, texto, borda, destaque)
 * - Alterar tamanho do card (largura/altura)
 * - Alterar raio da borda
 * - Preview em tempo real
 */
class CardEditorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NGBAutoRoadTheme {
                CardEditorScreen(
                    prefsManager = PrefsManager(applicationContext),
                    onBack = { finish() }
                )
            }
        }
    }
}

/**
 * Modelo de um campo posicionado no card custom
 */
@Serializable
data class EditorField(
    val fieldType: String, // CardField.name
    val label: String,
    val x: Float = 0f,
    val y: Float = 0f,
    val fontSize: Int = 14,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val colorHex: String = "#FFFFFF"
)

/**
 * Layout completo do card custom
 */
@Serializable
data class CustomCardLayout(
    val fields: List<EditorField> = emptyList(),
    val backgroundColor: String = "#101830",
    val borderColor: String = "#4F6BFF",
    val borderRadius: Int = 12,
    val cardWidth: Int = 320,
    val cardHeight: Int = 200,
    val showBorder: Boolean = true
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardEditorScreen(
    prefsManager: PrefsManager,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()

    // Estado do layout
    var layout by remember { mutableStateOf(CustomCardLayout()) }
    var selectedFieldIndex by remember { mutableIntStateOf(-1) }
    var showAddFieldDialog by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var colorPickerTarget by remember { mutableStateOf("background") }

    // Carregar layout salvo
    LaunchedEffect(Unit) {
        prefsManager.card3LayoutJsonFlow.collect { json ->
            if (json.isNotBlank()) {
                try {
                    layout = Json.decodeFromString<CustomCardLayout>(json)
                } catch (_: Exception) {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Editor Custom") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    // Salvar
                    IconButton(onClick = {
                        scope.launch {
                            val json = Json.encodeToString(layout)
                            prefsManager.saveCard3LayoutJson(json)
                        }
                    }) {
                        Icon(Icons.Default.Save, contentDescription = "Salvar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // === PREVIEW DO CARD ===
            Text(
                "Preview",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            CardPreviewArea(
                layout = layout,
                selectedFieldIndex = selectedFieldIndex,
                onFieldSelected = { selectedFieldIndex = it },
                onFieldMoved = { index, newX, newY ->
                    val updatedFields = layout.fields.toMutableList()
                    updatedFields[index] = updatedFields[index].copy(x = newX, y = newY)
                    layout = layout.copy(fields = updatedFields)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // === TOOLBAR DE CAMPOS ===
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Campos (${layout.fields.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Row {
                    IconButton(onClick = { showAddFieldDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Adicionar campo")
                    }
                    if (selectedFieldIndex >= 0 && selectedFieldIndex < layout.fields.size) {
                        IconButton(onClick = {
                            val updatedFields = layout.fields.toMutableList()
                            updatedFields.removeAt(selectedFieldIndex)
                            layout = layout.copy(fields = updatedFields)
                            selectedFieldIndex = -1
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remover campo", tint = ScoreRed)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // === PROPRIEDADES DO CAMPO SELECIONADO ===
            if (selectedFieldIndex >= 0 && selectedFieldIndex < layout.fields.size) {
                val field = layout.fields[selectedFieldIndex]
                FieldPropertiesPanel(
                    field = field,
                    onUpdate = { updated ->
                        val updatedFields = layout.fields.toMutableList()
                        updatedFields[selectedFieldIndex] = updated
                        layout = layout.copy(fields = updatedFields)
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // === PROPRIEDADES DO CARD ===
            Text(
                "Propriedades do Card",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Largura
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Largura: ${layout.cardWidth}dp", style = MaterialTheme.typography.bodySmall)
                Row {
                    IconButton(onClick = {
                        layout = layout.copy(cardWidth = (layout.cardWidth - 20).coerceIn(200, 500))
                    }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Remove, contentDescription = "Diminuir", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = {
                        layout = layout.copy(cardWidth = (layout.cardWidth + 20).coerceIn(200, 500))
                    }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Add, contentDescription = "Aumentar", modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Altura
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Altura: ${layout.cardHeight}dp", style = MaterialTheme.typography.bodySmall)
                Row {
                    IconButton(onClick = {
                        layout = layout.copy(cardHeight = (layout.cardHeight - 20).coerceIn(100, 400))
                    }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Remove, contentDescription = "Diminuir", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = {
                        layout = layout.copy(cardHeight = (layout.cardHeight + 20).coerceIn(100, 400))
                    }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Add, contentDescription = "Aumentar", modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Border Radius
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Borda: ${layout.borderRadius}dp", style = MaterialTheme.typography.bodySmall)
                Row {
                    IconButton(onClick = {
                        layout = layout.copy(borderRadius = (layout.borderRadius - 2).coerceIn(0, 30))
                    }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Remove, contentDescription = "Diminuir", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = {
                        layout = layout.copy(borderRadius = (layout.borderRadius + 2).coerceIn(0, 30))
                    }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Add, contentDescription = "Aumentar", modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Show Border toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Mostrar borda", style = MaterialTheme.typography.bodySmall)
                Switch(
                    checked = layout.showBorder,
                    onCheckedChange = { layout = layout.copy(showBorder = it) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Cores do card
            Text("Cores", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ColorButton(
                    label = "Fundo",
                    color = parseColor(layout.backgroundColor),
                    onClick = { colorPickerTarget = "background"; showColorPicker = true }
                )
                ColorButton(
                    label = "Borda",
                    color = parseColor(layout.borderColor),
                    onClick = { colorPickerTarget = "border"; showColorPicker = true }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Botão Ver Card Real (simulação com dados aleatórios)
            var showRealPreview by remember { mutableStateOf(false) }
            OutlinedButton(
                onClick = { showRealPreview = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Visibility, contentDescription = "Preview")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ver Card Real (com dados)")
            }

            if (showRealPreview) {
                Spacer(modifier = Modifier.height(8.dp))
                RealCardPreview(layout = layout)
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { showRealPreview = false }) {
                    Text("Fechar Preview")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Botão Salvar
            Button(
                onClick = {
                    scope.launch {
                        val json = Json.encodeToString(layout)
                        prefsManager.saveCard3LayoutJson(json)
                        // Também atualizar o CardModel básico
                        val bgLong = parseColorToLong(layout.backgroundColor)
                        val borderLong = parseColorToLong(layout.borderColor)
                        prefsManager.saveCard3Custom(
                            com.ngbautoroad.data.model.CardModel(
                                id = 0,
                                name = "Custom",
                                backgroundColor = bgLong,
                                borderColor = borderLong,
                                borderRadius = layout.borderRadius,
                                isCustom = true
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ScoreGreen)
            ) {
                Icon(Icons.Default.Save, contentDescription = "Salvar")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Salvar Layout")
            }
        }
    }

    // Add Field Dialog
    if (showAddFieldDialog) {
        AddFieldDialog(
            existingFields = layout.fields.map { it.fieldType },
            onDismiss = { showAddFieldDialog = false },
            onAddField = { fieldType ->
                val newField = EditorField(
                    fieldType = fieldType.name,
                    label = fieldType.label,
                    x = 10f,
                    y = (layout.fields.size * 30f).coerceAtMost(layout.cardHeight - 30f)
                )
                layout = layout.copy(fields = layout.fields + newField)
                showAddFieldDialog = false
            }
        )
    }

    // Color Picker Dialog
    if (showColorPicker) {
        ColorPickerDialog(
            currentColor = when (colorPickerTarget) {
                "background" -> layout.backgroundColor
                "border" -> layout.borderColor
                else -> "#FFFFFF"
            },
            onDismiss = { showColorPicker = false },
            onColorSelected = { hex ->
                layout = when (colorPickerTarget) {
                    "background" -> layout.copy(backgroundColor = hex)
                    "border" -> layout.copy(borderColor = hex)
                    else -> layout
                }
                showColorPicker = false
            }
        )
    }
}

@Composable
fun CardPreviewArea(
    layout: CustomCardLayout,
    selectedFieldIndex: Int,
    onFieldSelected: (Int) -> Unit,
    onFieldMoved: (Int, Float, Float) -> Unit
) {
    val bgColor = parseColor(layout.backgroundColor)
    val borderColor = parseColor(layout.borderColor)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(layout.cardHeight.dp)
            .clip(RoundedCornerShape(layout.borderRadius.dp))
            .then(
                if (layout.showBorder) Modifier.border(2.dp, borderColor, RoundedCornerShape(layout.borderRadius.dp))
                else Modifier
            )
            .background(bgColor)
            .clickable { onFieldSelected(-1) }
    ) {
        layout.fields.forEachIndexed { index, field ->
            var offsetX by remember(field.x) { mutableFloatStateOf(field.x) }
            var offsetY by remember(field.y) { mutableFloatStateOf(field.y) }

            val isSelected = index == selectedFieldIndex
            val fieldColor = parseColor(field.colorHex)

            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                    .then(
                        if (isSelected) Modifier.border(1.dp, ScoreYellow, RoundedCornerShape(2.dp))
                        else Modifier
                    )
                    .clickable { onFieldSelected(index) }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount.x
                            offsetY += dragAmount.y
                            onFieldMoved(index, offsetX, offsetY)
                        }
                    }
                    .padding(2.dp)
            ) {
                Text(
                    text = field.label,
                    color = fieldColor,
                    fontSize = field.fontSize.sp,
                    fontWeight = if (field.isBold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (field.isItalic) FontStyle.Italic else FontStyle.Normal
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldPropertiesPanel(
    field: EditorField,
    onUpdate: (EditorField) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Campo: ${field.label}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Font Size
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Fonte: ${field.fontSize}sp", style = MaterialTheme.typography.bodySmall)
                Row {
                    IconButton(onClick = {
                        onUpdate(field.copy(fontSize = (field.fontSize - 1).coerceIn(8, 32)))
                    }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.TextDecrease, contentDescription = "Diminuir fonte", modifier = Modifier.size(14.dp))
                    }
                    IconButton(onClick = {
                        onUpdate(field.copy(fontSize = (field.fontSize + 1).coerceIn(8, 32)))
                    }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.TextIncrease, contentDescription = "Aumentar fonte", modifier = Modifier.size(14.dp))
                    }
                }
            }

            // Bold / Italic
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = field.isBold,
                    onClick = { onUpdate(field.copy(isBold = !field.isBold)) },
                    label = { Text("B", fontWeight = FontWeight.Bold) }
                )
                FilterChip(
                    selected = field.isItalic,
                    onClick = { onUpdate(field.copy(isItalic = !field.isItalic)) },
                    label = { Text("I", fontStyle = FontStyle.Italic) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Cor do campo
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Cor:", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val presetColors = listOf("#FFFFFF", "#4F6BFF", "#00E676", "#FFAB00", "#FF5252", "#B388FF", "#00BCD4")
                    presetColors.forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(parseColor(hex))
                                .border(
                                    if (field.colorHex == hex) 2.dp else 0.dp,
                                    Color.White,
                                    CircleShape
                                )
                                .clickable { onUpdate(field.copy(colorHex = hex)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddFieldDialog(
    existingFields: List<String>,
    onDismiss: () -> Unit,
    onAddField: (CardField) -> Unit
) {
    val availableFields = CardField.entries.filter { it.name !in existingFields }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar Campo") },
        text = {
            LazyColumn {
                items(availableFields) { field ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAddField(field) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Aumentar", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(field.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fechar") }
        }
    )
}

@Composable
fun ColorPickerDialog(
    currentColor: String,
    onDismiss: () -> Unit,
    onColorSelected: (String) -> Unit
) {
    val presetColors = listOf(
        "#101830", "#1A1A2E", "#16213E", "#0F3460", "#1B1B2F",
        "#2D2D44", "#000000", "#1E1E1E", "#2C3E50", "#34495E",
        "#4F6BFF", "#00B0FF", "#00E676", "#FFAB00", "#FF5252",
        "#B388FF", "#FF6E40", "#00BCD4", "#7C4DFF", "#64FFDA",
        "#FFFFFF", "#E0E0E0", "#BDBDBD", "#9E9E9E", "#757575"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Selecionar Cor") },
        text = {
            Column {
                // Grid de cores
                val rows = presetColors.chunked(5)
                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row.forEach { hex ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(parseColor(hex))
                                    .border(
                                        if (currentColor == hex) 3.dp else 1.dp,
                                        if (currentColor == hex) ScoreYellow else Color.Gray,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onColorSelected(hex) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fechar") }
        }
    )
}

@Composable
fun ColorButton(label: String, color: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, Color.Gray, CircleShape)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

// === Helpers ===

fun parseColor(hex: String): Color {
    return try {
        val cleanHex = hex.removePrefix("#")
        val colorLong = cleanHex.toLong(16)
        Color(
            red = ((colorLong shr 16) and 0xFF).toInt() / 255f,
            green = ((colorLong shr 8) and 0xFF).toInt() / 255f,
            blue = (colorLong and 0xFF).toInt() / 255f
        )
    } catch (_: Exception) {
        Color.White
    }
}

fun parseColorToLong(hex: String): Long {
    return try {
        val cleanHex = hex.removePrefix("#")
        0xFF000000 or cleanHex.toLong(16)
    } catch (_: Exception) {
        0xFF101830
    }
}

@Composable
fun RealCardPreview(layout: CustomCardLayout) {
    // Gerar dados aleatórios simulados
    val sampleData = remember {
        mapOf(
            "RIDE_VALUE" to "R$ 18,50",
            "VALUE_PER_KM" to "R$ 2,35/km",
            "VALUE_PER_HOUR" to "R$ 28,40/h",
            "PICKUP_DISTANCE" to "1,2 km",
            "DROPOFF_DISTANCE" to "7,8 km",
            "DURATION" to "22 min",
            "PASSENGER_RATING" to "4,8 ★",
            "INTERMEDIATE_STOPS" to "0 paradas",
            "PLATFORM" to "Uber",
            "PICKUP_NEIGHBORHOOD" to "Pituba",
            "DROPOFF_NEIGHBORHOOD" to "Barra",
            "SCORE" to "82 pts"
        )
    }

    val bgColor = try {
        Color(parseColorToLong(layout.backgroundColor))
    } catch (_: Exception) { Color(0xFF101830) }

    val borderColor = try {
        Color(parseColorToLong(layout.borderColor))
    } catch (_: Exception) { Color(0xFF4F6BFF) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(layout.cardHeight.dp)
            .clip(RoundedCornerShape(layout.borderRadius.dp))
            .border(2.dp, borderColor, RoundedCornerShape(layout.borderRadius.dp))
            .background(bgColor)
            .padding(8.dp)
    ) {
        for (field in layout.fields) {
            val value = sampleData[field.fieldType] ?: field.label
            val textColor = try {
                Color(parseColorToLong(field.colorHex))
            } catch (_: Exception) { Color.White }

            Text(
                text = "${field.label}: $value",
                color = textColor,
                fontSize = field.fontSize.sp,
                fontWeight = if (field.isBold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (field.isItalic) FontStyle.Italic else FontStyle.Normal,
                modifier = Modifier.offset(x = field.x.dp, y = field.y.dp)
            )
        }
    }
}
