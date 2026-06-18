// ============================================================================
// ARQUIVO: ZoneMapActivity.kt
// LOCALIZAÇÃO: ui/map/ZoneMapActivity.kt
// RESPONSABILIDADE: Mapa de zonas e bairros bloqueados
//   - Exibe mapa OSM com áreas de pickup/dropoff
//   - Permite marcar bairros como bloqueados
// DEPENDÊNCIAS:
//   - data/prefs/PrefsManager.kt → bairros bloqueados
//   - org.osmdroid → mapa
// ============================================================================
package com.ngbautoroad.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.ngbautoroad.data.prefs.PrefsManager
import com.ngbautoroad.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

@Serializable
data class ZoneGeoPoint(val lat: Double, val lng: Double)

@Serializable
data class BlockedZone(
    val id: String,
    val name: String,
    val type: String, // "PICKUP" or "DROPOFF"
    val points: List<ZoneGeoPoint>,
    val isEnabled: Boolean = true
)

@Serializable
data class ZoneMapData(
    val zones: List<BlockedZone> = emptyList()
)

class ZoneMapActivity : ComponentActivity() {

    private lateinit var prefsManager: PrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configurar OSMDroid
        Configuration.getInstance().load(applicationContext, PreferenceManager.getDefaultSharedPreferences(applicationContext))
        Configuration.getInstance().userAgentValue = packageName

        prefsManager = PrefsManager(applicationContext)

        // Solicitar permissão de localização
        val locationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { _ -> }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }

        setContent {
            NGBAutoRoadTheme {
                ZoneMapScreen(prefsManager = prefsManager, activity = this)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneMapScreen(prefsManager: PrefsManager, activity: ComponentActivity) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var mapData by remember { mutableStateOf(ZoneMapData()) }
    var isDrawing by remember { mutableStateOf(false) }
    var currentPoints by remember { mutableStateOf<List<ZoneGeoPoint>>(emptyList()) }
    // v5.1.0: undoSnapshots salva snapshot completo a cada ACTION_UP (dedo levantado)
    // Desfazer restaura o estado ANTES do último toque, não ponto a ponto
    var undoSnapshots by remember { mutableStateOf<List<List<ZoneGeoPoint>>>(emptyList()) }
    var drawMode by remember { mutableStateOf("DROPOFF") }
    var showNameDialog by remember { mutableStateOf(false) }
    var newZoneName by remember { mutableStateOf("") }
    var showZoneList by remember { mutableStateOf(false) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

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

    fun goToMyLocation() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val cancellationToken = CancellationTokenSource()
            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationToken.token)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        mapViewRef?.controller?.animateTo(OsmGeoPoint(location.latitude, location.longitude))
                        mapViewRef?.controller?.setZoom(15.0)
                    }
                }
        }
    }

    fun drawZonesOnMap(map: MapView) {
        // Remover polígonos antigos
        map.overlays.removeAll { it is Polygon }
        // Redesenhar zonas salvas
        mapData.zones.filter { it.isEnabled }.forEach { zone ->
            if (zone.points.size >= 3) {
                val polygon = Polygon().apply {
                    id = zone.id
                    title = zone.name
                    fillPaint.color = if (zone.type == "PICKUP")
                        AndroidColor.argb(60, 255, 152, 0) // Laranja translúcido
                    else
                        AndroidColor.argb(60, 244, 67, 54) // Vermelho translúcido
                    outlinePaint.color = if (zone.type == "PICKUP")
                        AndroidColor.parseColor("#FF9800")
                    else
                        AndroidColor.parseColor("#F44336")
                    outlinePaint.strokeWidth = 4f
                    points = zone.points.map { OsmGeoPoint(it.lat, it.lng) }
                }
                map.overlays.add(polygon)
            }
        }
        map.invalidate()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Zonas Bloqueadas") },
                navigationIcon = {
                    IconButton(onClick = { activity.finish() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = { goToMyLocation() }) {
                        Icon(Icons.Default.MyLocation, contentDescription = "Minha localização")
                    }
                    IconButton(onClick = { showZoneList = !showZoneList }) {
                        Icon(Icons.Default.List, contentDescription = "Lista de zonas")
                    }
                }
            )
        },
        floatingActionButton = {
            // v5.1.1: Ocultar FABs quando a lista de zonas está aberta
            if (!showZoneList) Column(horizontalAlignment = Alignment.End) {
                if (isDrawing) {
                    // Salvar zona
                    if (currentPoints.size >= 3) {
                        FloatingActionButton(
                            onClick = { showNameDialog = true },
                            containerColor = ScoreGreen
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Salvar", tint = androidx.compose.ui.graphics.Color.White)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    // v5.1.0: Desfazer restaura snapshot completo do último segmento
                    if (undoSnapshots.isNotEmpty()) {
                        FloatingActionButton(
                            onClick = {
                                // Restaurar o snapshot mais recente (estado antes do último toque)
                                val lastSnapshot = undoSnapshots.last()
                                undoSnapshots = undoSnapshots.dropLast(1)
                                currentPoints = lastSnapshot
                                // Atualizar polyline
                                mapViewRef?.let { map ->
                                    map.overlays.removeAll { it is Polyline }
                                    if (currentPoints.size >= 2) {
                                        val polyline = Polyline().apply {
                                            outlinePaint.color = if (drawMode == "PICKUP")
                                                AndroidColor.parseColor("#FF9800")
                                            else
                                                AndroidColor.parseColor("#F44336")
                                            outlinePaint.strokeWidth = 6f
                                            setPoints(currentPoints.map { OsmGeoPoint(it.lat, it.lng) })
                                        }
                                        map.overlays.add(polyline)
                                    } else {
                                        map.overlays.removeAll { it is Polyline }
                                    }
                                    map.invalidate()
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Undo, contentDescription = "Desfazer")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    // Cancelar
                    FloatingActionButton(
                        onClick = {
                            isDrawing = false
                            currentPoints = emptyList()
                            mapViewRef?.let { map ->
                                map.overlays.removeAll { it is Polyline }
                                map.invalidate()
                            }
                        },
                        containerColor = ScoreRed
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancelar", tint = androidx.compose.ui.graphics.Color.White)
                    }
                } else {
                    // Iniciar desenho - Embarque
                    FloatingActionButton(
                        onClick = {
                            drawMode = "PICKUP"
                            isDrawing = true
                            currentPoints = emptyList()
                        },
                        containerColor = ScoreOrange
                    ) {
                        Icon(Icons.Default.PersonPin, contentDescription = "Zona Embarque", tint = androidx.compose.ui.graphics.Color.White)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    // Iniciar desenho - Desembarque
                    FloatingActionButton(
                        onClick = {
                            drawMode = "DROPOFF"
                            isDrawing = true
                            currentPoints = emptyList()
                        },
                        containerColor = ScoreRed
                    ) {
                        Icon(Icons.Default.LocationOff, contentDescription = "Zona Desembarque", tint = androidx.compose.ui.graphics.Color.White)
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Mapa OSMDroid
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(14.0)
                        // Padrão: Salvador-BA (será atualizado pela localização)
                        controller.setCenter(OsmGeoPoint(-12.9714, -38.5124))

                        // Ir para localização real
                        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED) {
                            val fusedClient = LocationServices.getFusedLocationProviderClient(ctx)
                            val cancellationToken = CancellationTokenSource()
                            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationToken.token)
                                .addOnSuccessListener { location ->
                                    if (location != null) {
                                        controller.animateTo(OsmGeoPoint(location.latitude, location.longitude))
                                        controller.setZoom(15.0)
                                    }
                                }
                        }

                        mapViewRef = this
                    }
                },
                update = { map ->
                    drawZonesOnMap(map)

                    // Configurar touch listener para desenho livre
                    map.setOnTouchListener { _, event ->
                        if (isDrawing) {
                            when (event.action) {
                                MotionEvent.ACTION_DOWN -> {
                                    // v5.1.0: Salvar snapshot ANTES de iniciar novo segmento
                                    undoSnapshots = undoSnapshots + listOf(currentPoints.toList())
                                    // Manter no máximo 20 snapshots
                                    if (undoSnapshots.size > 20) undoSnapshots = undoSnapshots.drop(1)
                                    val projection = map.projection
                                    val geoPoint = projection.fromPixels(event.x.toInt(), event.y.toInt()) as OsmGeoPoint
                                    currentPoints = currentPoints + ZoneGeoPoint(geoPoint.latitude, geoPoint.longitude)
                                    map.invalidate()
                                    true
                                }
                                MotionEvent.ACTION_MOVE -> {
                                    val projection = map.projection
                                    val geoPoint = projection.fromPixels(event.x.toInt(), event.y.toInt()) as OsmGeoPoint
                                    val newPoint = ZoneGeoPoint(geoPoint.latitude, geoPoint.longitude)

                                    // Filtrar pontos muito próximos
                                    val lastPoint = currentPoints.lastOrNull()
                                    val minDistance = 0.00005 // ~5 metros
                                    if (lastPoint == null ||
                                        Math.abs(lastPoint.lat - newPoint.lat) > minDistance ||
                                        Math.abs(lastPoint.lng - newPoint.lng) > minDistance) {
                                        currentPoints = currentPoints + newPoint
                                    }

                                    // Atualizar polyline de desenho
                                    map.overlays.removeAll { it is Polyline }
                                    if (currentPoints.size >= 2) {
                                        val polyline = Polyline().apply {
                                            outlinePaint.color = if (drawMode == "PICKUP")
                                                AndroidColor.parseColor("#FF9800")
                                            else
                                                AndroidColor.parseColor("#F44336")
                                            outlinePaint.strokeWidth = 6f
                                            setPoints(currentPoints.map { OsmGeoPoint(it.lat, it.lng) })
                                        }
                                        map.overlays.add(polyline)
                                    }
                                    map.invalidate()
                                    true
                                }
                                else -> false
                            }
                        } else {
                            false // Deixar o mapa processar normalmente (pan/zoom)
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Indicador de modo de desenho
            if (isDrawing) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (drawMode == "PICKUP")
                            ScoreOrange.copy(alpha = 0.95f)
                        else
                            ScoreRed.copy(alpha = 0.95f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Draw,
                            contentDescription = "Ícone",
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Deslize o dedo para desenhar zona de ${if (drawMode == "PICKUP") "EMBARQUE" else "DESEMBARQUE"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = androidx.compose.ui.graphics.Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Lista de zonas (overlay)
            if (showZoneList) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.4f)
                        .padding(8.dp),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Zonas (${mapData.zones.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { showZoneList = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Fechar")
                            }
                        }

                        if (mapData.zones.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Nenhuma zona criada.\nUse os botões para desenhar.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(mapData.zones) { zone ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                                alpha = if (zone.isEnabled) 1f else 0.5f
                                            )
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(12.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            if (zone.type == "PICKUP") ScoreOrange else ScoreRed
                                                        )
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text(
                                                        zone.name,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        "${if (zone.type == "PICKUP") "Embarque" else "Desembarque"} • ${zone.points.size} pts",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            Row {
                                                // Centralizar no mapa
                                                IconButton(onClick = {
                                                    if (zone.points.isNotEmpty()) {
                                                        val avgLat = zone.points.map { it.lat }.average()
                                                        val avgLng = zone.points.map { it.lng }.average()
                                                        mapViewRef?.controller?.animateTo(OsmGeoPoint(avgLat, avgLng))
                                                    }
                                                }, modifier = Modifier.size(32.dp)) {
                                                    Icon(Icons.Default.CenterFocusStrong, contentDescription = "Ver", modifier = Modifier.size(16.dp))
                                                }
                                                // Toggle
                                                Switch(
                                                    checked = zone.isEnabled,
                                                    onCheckedChange = { enabled ->
                                                        val updated = mapData.zones.map {
                                                            if (it.id == zone.id) it.copy(isEnabled = enabled) else it
                                                        }
                                                        mapData = mapData.copy(zones = updated)
                                                        saveData(mapData)
                                                    },
                                                    modifier = Modifier.height(24.dp)
                                                )
                                                // Deletar
                                                IconButton(onClick = {
                                                    val updated = mapData.zones.filter { it.id != zone.id }
                                                    mapData = mapData.copy(zones = updated)
                                                    saveData(mapData)
                                                }, modifier = Modifier.size(32.dp)) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Excluir", modifier = Modifier.size(16.dp), tint = ScoreRed)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog para nomear a zona
    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("Nomear Zona") },
            text = {
                Column {
                    Text(
                        "Tipo: ${if (drawMode == "PICKUP") "Embarque" else "Desembarque"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (drawMode == "PICKUP") ScoreOrange else ScoreRed,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newZoneName,
                        onValueChange = { newZoneName = it },
                        label = { Text("Nome da zona") },
                        placeholder = { Text("Ex: Centro, Subúrbio...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val newZone = BlockedZone(
                        id = System.currentTimeMillis().toString(),
                        name = newZoneName.ifBlank { "Zona ${mapData.zones.size + 1}" },
                        type = drawMode,
                        points = currentPoints,
                        isEnabled = true
                    )
                    mapData = mapData.copy(zones = mapData.zones + newZone)
                    saveData(mapData)
                    isDrawing = false
                    currentPoints = emptyList()
                    newZoneName = ""
                    showNameDialog = false
                    // Limpar polyline de desenho
                    mapViewRef?.let { map ->
                        map.overlays.removeAll { it is Polyline }
                        map.invalidate()
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
