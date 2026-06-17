package com.ngbautoroad.ui.map

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ngbautoroad.data.prefs.PrefsManager
import com.ngbautoroad.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Mapa interativo para desenhar zonas bloqueadas.
 *
 * O motorista desenha polígonos no mapa para marcar áreas de embarque/desembarque
 * que deseja evitar. Cada zona pode ser ativada/desativada com botões rápidos.
 */
class ZoneMapActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            com.ngbautoroad.ui.theme.NGBAutoRoadTheme {
                ZoneMapScreen(
                    prefsManager = PrefsManager(applicationContext),
                    onBack = { finish() }
                )
            }
        }
    }
}

@Serializable
data class ZonePoint(val x: Float, val y: Float)

@Serializable
data class BlockedZone(
    val id: String,
    val name: String,
    val type: String, // "PICKUP" or "DROPOFF"
    val points: List<ZonePoint>,
    val isEnabled: Boolean = true,
    val color: String = "#FF5252"
)

@Serializable
data class ZoneMapData(
    val zones: List<BlockedZone> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneMapScreen(
    prefsManager: PrefsManager,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var mapData by remember { mutableStateOf(ZoneMapData()) }
    var isDrawing by remember { mutableStateOf(false) }
    var currentPoints by remember { mutableStateOf<List<ZonePoint>>(emptyList()) }
    var drawMode by remember { mutableStateOf("PICKUP") } // "PICKUP" or "DROPOFF"
    var showNameDialog by remember { mutableStateOf(false) }
    var newZoneName by remember { mutableStateOf("") }

    // Carregar dados salvos
    LaunchedEffect(Unit) {
        prefsManager.zoneMapDataFlow.collect { json ->
            if (json.isNotBlank()) {
                try {
                    mapData = Json.decodeFromString<ZoneMapData>(json)
                } catch (_: Exception) {}
            }
        }
    }

    fun saveData(data: ZoneMapData) {
        scope.launch {
            val json = Json.encodeToString(data)
            prefsManager.saveZoneMapData(json)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mapa de Zonas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    // Toggle draw mode
                    IconButton(onClick = { isDrawing = !isDrawing }) {
                        Icon(
                            if (isDrawing) Icons.Default.Done else Icons.Default.Draw,
                            contentDescription = if (isDrawing) "Finalizar" else "Desenhar",
                            tint = if (isDrawing) ScoreGreen else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Mode selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = drawMode == "PICKUP",
                    onClick = { drawMode = "PICKUP" },
                    label = { Text("Embarque") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ScoreRed.copy(alpha = 0.2f),
                        selectedLabelColor = ScoreRed
                    ),
                    leadingIcon = if (drawMode == "PICKUP") {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
                FilterChip(
                    selected = drawMode == "DROPOFF",
                    onClick = { drawMode = "DROPOFF" },
                    label = { Text("Desembarque") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ScoreOrange.copy(alpha = 0.2f),
                        selectedLabelColor = ScoreOrange
                    ),
                    leadingIcon = if (drawMode == "DROPOFF") {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )

                Spacer(modifier = Modifier.weight(1f))

                if (isDrawing && currentPoints.size >= 3) {
                    Button(
                        onClick = { showNameDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = ScoreGreen),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("Salvar Zona", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Mapa (Canvas interativo)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .background(Color(0xFF1A2332))
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(isDrawing) {
                            if (isDrawing) {
                                detectTapGestures { offset ->
                                    currentPoints = currentPoints + ZonePoint(offset.x, offset.y)
                                }
                            }
                        }
                ) {
                    // Desenhar grid de referência
                    val gridColor = Color.White.copy(alpha = 0.05f)
                    val gridSpacing = 50f
                    var x = 0f
                    while (x < size.width) {
                        drawLine(gridColor, Offset(x, 0f), Offset(x, size.height))
                        x += gridSpacing
                    }
                    var y = 0f
                    while (y < size.height) {
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y))
                        y += gridSpacing
                    }

                    // Desenhar zonas salvas
                    mapData.zones.forEach { zone ->
                        if (zone.points.size >= 3) {
                            val zoneColor = if (zone.type == "PICKUP") ScoreRed else ScoreOrange
                            val alpha = if (zone.isEnabled) 0.3f else 0.08f
                            val strokeAlpha = if (zone.isEnabled) 0.8f else 0.3f

                            val path = Path().apply {
                                moveTo(zone.points[0].x, zone.points[0].y)
                                zone.points.drop(1).forEach { point ->
                                    lineTo(point.x, point.y)
                                }
                                close()
                            }

                            drawPath(path, zoneColor.copy(alpha = alpha), style = Fill)
                            drawPath(path, zoneColor.copy(alpha = strokeAlpha), style = Stroke(width = 2f))
                        }
                    }

                    // Desenhar zona atual (em construção)
                    if (currentPoints.isNotEmpty()) {
                        val drawColor = if (drawMode == "PICKUP") ScoreRed else ScoreOrange

                        // Linhas
                        for (i in 0 until currentPoints.size - 1) {
                            drawLine(
                                drawColor,
                                Offset(currentPoints[i].x, currentPoints[i].y),
                                Offset(currentPoints[i + 1].x, currentPoints[i + 1].y),
                                strokeWidth = 3f
                            )
                        }

                        // Pontos
                        currentPoints.forEach { point ->
                            drawCircle(drawColor, 6f, Offset(point.x, point.y))
                            drawCircle(Color.White, 3f, Offset(point.x, point.y))
                        }

                        // Fechar polígono se >= 3 pontos (preview)
                        if (currentPoints.size >= 3) {
                            drawLine(
                                drawColor.copy(alpha = 0.5f),
                                Offset(currentPoints.last().x, currentPoints.last().y),
                                Offset(currentPoints.first().x, currentPoints.first().y),
                                strokeWidth = 2f
                            )
                        }
                    }
                }

                // Instrução
                if (isDrawing && currentPoints.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Toque para adicionar pontos da zona",
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Botão desfazer último ponto
                if (isDrawing && currentPoints.isNotEmpty()) {
                    IconButton(
                        onClick = { currentPoints = currentPoints.dropLast(1) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Undo,
                            contentDescription = "Desfazer",
                            tint = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Lista de zonas com botões rápidos
            Text(
                "Zonas (${mapData.zones.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(mapData.zones) { zone ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (zone.type == "PICKUP") ScoreRed.copy(alpha = if (zone.isEnabled) 1f else 0.3f)
                                        else ScoreOrange.copy(alpha = if (zone.isEnabled) 1f else 0.3f)
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    zone.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    if (zone.type == "PICKUP") "Embarque" else "Desembarque",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row {
                            // Toggle ativar/desativar
                            Switch(
                                checked = zone.isEnabled,
                                onCheckedChange = { enabled ->
                                    val updatedZones = mapData.zones.map {
                                        if (it.id == zone.id) it.copy(isEnabled = enabled) else it
                                    }
                                    mapData = mapData.copy(zones = updatedZones)
                                    saveData(mapData)
                                },
                                modifier = Modifier.height(24.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            // Deletar
                            IconButton(
                                onClick = {
                                    val updatedZones = mapData.zones.filter { it.id != zone.id }
                                    mapData = mapData.copy(zones = updatedZones)
                                    saveData(mapData)
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Remover",
                                    modifier = Modifier.size(16.dp),
                                    tint = ScoreRed
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Dialog para nomear a zona
    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("Nome da Zona") },
            text = {
                OutlinedTextField(
                    value = newZoneName,
                    onValueChange = { newZoneName = it },
                    label = { Text("Ex: Centro, Zona Norte...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newZoneName.isNotBlank() && currentPoints.size >= 3) {
                        val newZone = BlockedZone(
                            id = System.currentTimeMillis().toString(),
                            name = newZoneName,
                            type = drawMode,
                            points = currentPoints,
                            isEnabled = true
                        )
                        mapData = mapData.copy(zones = mapData.zones + newZone)
                        saveData(mapData)
                        currentPoints = emptyList()
                        newZoneName = ""
                        showNameDialog = false
                        isDrawing = false
                    }
                }) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
