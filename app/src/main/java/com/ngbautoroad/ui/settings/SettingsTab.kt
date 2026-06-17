package com.ngbautoroad.ui.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ngbautoroad.data.prefs.PrefsManager
import com.ngbautoroad.service.OverlayService
import com.ngbautoroad.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SettingsTab(prefsManager: PrefsManager) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val serviceEnabled by prefsManager.serviceEnabledFlow.collectAsState(initial = false)
    val ocrEnabled by prefsManager.ocrEnabledFlow.collectAsState(initial = true)
    val protectionEnabled by prefsManager.protectionEnabledFlow.collectAsState(initial = false)
    val blockedPickup by prefsManager.blockedPickupFlow.collectAsState(initial = emptyList())
    val blockedDropoff by prefsManager.blockedDropoffFlow.collectAsState(initial = emptyList())
    val overlayWidth by prefsManager.overlayWidthFlow.collectAsState(initial = 320)
    val overlayFontScale by prefsManager.overlayFontScaleFlow.collectAsState(initial = 1.0f)

    var showAddPickup by remember { mutableStateOf(false) }
    var showAddDropoff by remember { mutableStateOf(false) }
    var newNeighborhoodName by remember { mutableStateOf("") }
    var newNeighborhoodWeight by remember { mutableIntStateOf(20) }
    var showZoneMapInfo by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = "Configurações",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(16.dp))

        // === PROTEÇÃO ANTI-DETECÇÃO ===
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (protectionEnabled)
                    ScoreGreen.copy(alpha = 0.08f)
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = null,
                            tint = if (protectionEnabled) ScoreGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Proteção",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (protectionEnabled) "Ativa — modo furtivo" else "Desativada",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (protectionEnabled) ScoreGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = protectionEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { prefsManager.setProtectionEnabled(enabled) }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ScoreGreen,
                            checkedTrackColor = ScoreGreen.copy(alpha = 0.3f)
                        )
                    )
                }

                if (protectionEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Randomiza intervalos de leitura OCR\n" +
                                "• Oculta overlay durante screenshots\n" +
                                "• Desativa AccessibilityService periodicamente\n" +
                                "• Simula comportamento humano nos tempos de resposta",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // === SERVIÇOS ===
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Serviços",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Overlay Service Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Overlay", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Exibir card sobre apps",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = serviceEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                prefsManager.setServiceEnabled(enabled)
                                if (enabled) {
                                    OverlayService.start(context)
                                } else {
                                    OverlayService.stop(context)
                                }
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // OCR Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("OCR (ML Kit)", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Leitura de tela via captura",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = ocrEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { prefsManager.setOcrEnabled(enabled) }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // === OVERLAY - TAMANHO E ACESSIBILIDADE ===
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Overlay - Aparência",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Largura do overlay
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Largura: ${overlayWidth}dp", style = MaterialTheme.typography.bodyMedium)
                }
                Slider(
                    value = overlayWidth.toFloat(),
                    onValueChange = { newWidth ->
                        scope.launch { prefsManager.saveOverlaySize(newWidth.toInt(), 0) }
                    },
                    valueRange = 200f..500f,
                    steps = 14
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Escala de fonte
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Fonte: ${String.format("%.0f", overlayFontScale * 100)}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Slider(
                    value = overlayFontScale,
                    onValueChange = { newScale ->
                        scope.launch { prefsManager.saveOverlayFontScale(newScale) }
                    },
                    valueRange = 0.7f..2.0f,
                    steps = 12
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // === PERMISSÕES ===
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Permissões",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Accessibility, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Acessibilidade")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Layers, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sobreposição de Tela")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // === ZONAS BLOQUEADAS (MAPA) ===
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Map, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Zonas Bloqueadas",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = { showZoneMapInfo = !showZoneMapInfo }) {
                        Icon(Icons.Default.Info, contentDescription = "Info")
                    }
                }

                if (showZoneMapInfo) {
                    Text(
                        text = "Desenhe áreas no mapa para bloquear embarque ou desembarque. " +
                                "Use os botões rápidos abaixo para ativar/desativar zonas sem excluí-las.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Botão para abrir mapa
                Button(
                    onClick = {
                        val intent = Intent(context, com.ngbautoroad.ui.map.ZoneMapActivity::class.java)
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.EditLocation, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Abrir Mapa de Zonas")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // === BAIRROS BLOQUEADOS - EMBARQUE ===
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Bairros Bloqueados (Embarque)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { showAddPickup = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Adicionar")
                    }
                }

                if (blockedPickup.isEmpty()) {
                    Text(
                        "Nenhum bairro bloqueado",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                blockedPickup.forEach { (name, weight) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(name, style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "-${weight}pts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            IconButton(onClick = {
                                scope.launch {
                                    prefsManager.saveBlockedPickup(blockedPickup.filter { it.first != name })
                                }
                            }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remover",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                if (showAddPickup) {
                    NeighborhoodInput(
                        name = newNeighborhoodName,
                        weight = newNeighborhoodWeight,
                        onNameChange = { newNeighborhoodName = it },
                        onWeightChange = { newNeighborhoodWeight = it },
                        onAdd = {
                            if (newNeighborhoodName.isNotBlank()) {
                                scope.launch {
                                    prefsManager.saveBlockedPickup(
                                        blockedPickup + (newNeighborhoodName to newNeighborhoodWeight)
                                    )
                                }
                                newNeighborhoodName = ""
                                newNeighborhoodWeight = 20
                                showAddPickup = false
                            }
                        },
                        onCancel = { showAddPickup = false }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // === BAIRROS BLOQUEADOS - DESTINO ===
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Bairros Bloqueados (Destino)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { showAddDropoff = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Adicionar")
                    }
                }

                if (blockedDropoff.isEmpty()) {
                    Text(
                        "Nenhum bairro bloqueado",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                blockedDropoff.forEach { (name, weight) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(name, style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "-${weight}pts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            IconButton(onClick = {
                                scope.launch {
                                    prefsManager.saveBlockedDropoff(blockedDropoff.filter { it.first != name })
                                }
                            }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remover",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                if (showAddDropoff) {
                    NeighborhoodInput(
                        name = newNeighborhoodName,
                        weight = newNeighborhoodWeight,
                        onNameChange = { newNeighborhoodName = it },
                        onWeightChange = { newNeighborhoodWeight = it },
                        onAdd = {
                            if (newNeighborhoodName.isNotBlank()) {
                                scope.launch {
                                    prefsManager.saveBlockedDropoff(
                                        blockedDropoff + (newNeighborhoodName to newNeighborhoodWeight)
                                    )
                                }
                                newNeighborhoodName = ""
                                newNeighborhoodWeight = 20
                                showAddDropoff = false
                            }
                        },
                        onCancel = { showAddDropoff = false }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // App Info
        Text(
            text = "NGB AutoRoad v3.1.0",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun NeighborhoodInput(
    name: String,
    weight: Int,
    onNameChange: (String) -> Unit,
    onWeightChange: (Int) -> Unit,
    onAdd: () -> Unit,
    onCancel: () -> Unit
) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Nome do bairro") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text("Penalidade: ${weight} pts", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = weight.toFloat(),
            onValueChange = { onWeightChange(it.toInt()) },
            valueRange = 5f..50f
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onCancel) { Text("Cancelar") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onAdd) { Text("Adicionar") }
        }
    }
}
