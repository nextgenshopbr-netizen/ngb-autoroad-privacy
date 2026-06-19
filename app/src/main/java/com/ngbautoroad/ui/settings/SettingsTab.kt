package com.ngbautoroad.ui.settings

// ============================================================================
// ARQUIVO: SettingsTab.kt
// LOCALIZAÇÃO: ui/settings/SettingsTab.kt
// RESPONSABILIDADE: Configurações do app (serviço, overlay, OCR, bairros)
// COMPOSABLES:
//   - SettingsTab (L27): Tela com toggles de serviço, tamanho do overlay, etc.
//   - NeighborhoodInput (L611): Input de bairros bloqueados
// DEPENDÊNCIAS:
//   - service/OverlayService.kt → start/stop/resize
//   - service/OcrCaptureService.kt → start/stop
//   - data/prefs/PrefsManager.kt → todas as configurações
// PROTEÇÕES:
//   - Verifica permissões antes de ativar serviços
//   - Slider de tamanho com range fixo (200-600dp)
// ============================================================================

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ngbautoroad.data.prefs.PrefsManager
import com.ngbautoroad.service.BubbleService
import com.ngbautoroad.service.OcrCaptureService
import com.ngbautoroad.service.OverlayService
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import com.ngbautoroad.ui.theme.*
import com.ngbautoroad.data.backup.BackupManager
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(prefsManager: PrefsManager) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val serviceEnabled by prefsManager.serviceEnabledFlow.collectAsState(initial = false)
    val ocrEnabled by prefsManager.ocrEnabledFlow.collectAsState(initial = true)
    val protectionEnabled by prefsManager.protectionEnabledFlow.collectAsState(initial = false)
    val overlayWidth by prefsManager.overlayWidthFlow.collectAsState(initial = 320)
    val overlayFontScale by prefsManager.overlayFontScaleFlow.collectAsState(initial = 1.0f)
    val keepScreenOn by prefsManager.keepScreenOnFlow.collectAsState(initial = false)


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

        // === TEMA (DARK MODE) v5.1.0 ===
        val darkMode by prefsManager.darkModeFlow.collectAsState(initial = "system")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.DarkMode,
                        contentDescription = "Tema",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tema do App", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("system" to "Sistema", "light" to "Claro", "dark" to "Escuro").forEach { (key, label) ->
                        FilterChip(
                            selected = darkMode == key,
                            onClick = { scope.launch { prefsManager.saveDarkMode(key) } },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // === IDIOMA (v5.2.3) ===
        val language by prefsManager.languageFlow.collectAsState(initial = "pt")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Language,
                        contentDescription = "Idioma",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Idioma do App", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("pt" to "🇧🇷 Português", "en" to "🇺🇸 English", "es" to "🇪🇸 Español").forEach { (key, label) ->
                        FilterChip(
                            selected = language == key,
                            onClick = { scope.launch { prefsManager.saveLanguage(key) } },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }
                if (language != "pt") {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "ℹ️ Reinicie o app para aplicar o idioma completamente.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // === MANTER TELA LIGADA ===
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Brightness7,
                        contentDescription = "Ícone",
                        tint = if (keepScreenOn) ScoreYellow else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            "Manter Tela Ligada",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (keepScreenOn) "Tela não apagará automaticamente" else "Comportamento padrão do sistema",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = keepScreenOn,
                    onCheckedChange = { enabled ->
                        scope.launch { prefsManager.setKeepScreenOn(enabled) }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

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
                            contentDescription = "Ícone",
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
                            if (enabled && !Settings.canDrawOverlays(context)) {
                                Toast.makeText(context, "Permissão de overlay necessária", Toast.LENGTH_LONG).show()
                                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                context.startActivity(intent)
                            } else {
                                scope.launch {
                                    prefsManager.setServiceEnabled(enabled)
                                    if (enabled) {
                                        OverlayService.start(context)
                                    } else {
                                        OverlayService.stop(context)
                                    }
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
                            if (enabled && !Settings.canDrawOverlays(context)) {
                                Toast.makeText(context, "Permissão de overlay necessária para captura OCR", Toast.LENGTH_LONG).show()
                                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                context.startActivity(intent)
                            } else {
                                scope.launch {
                                    prefsManager.setOcrEnabled(enabled)
                                    if (enabled) {
                                        OcrCaptureService.start(context)
                                        Toast.makeText(context, "OCR ativado", Toast.LENGTH_SHORT).show()
                                    } else {
                                        OcrCaptureService.stop(context)
                                    }
                                }
                            }
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
                        scope.launch {
                            prefsManager.saveOverlaySize(newWidth.toInt(), 0)
                            // Aplicar resize ao vivo no overlay ativo (item 4.3)
                            OverlayService.resizeFromOutside(newWidth.toInt())
                        }
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

                Spacer(modifier = Modifier.height(12.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))

                // Transparência do overlay
                val overlayOpacity by prefsManager.overlayOpacityFlow.collectAsState(initial = 1.0f)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Transparência: ${String.format("%.0f", overlayOpacity * 100)}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Slider(
                    value = overlayOpacity,
                    onValueChange = { newOpacity ->
                        scope.launch { prefsManager.saveOverlayOpacity(newOpacity) }
                    },
                    valueRange = 0.3f..1.0f,
                    steps = 6
                )

                Spacer(modifier = Modifier.height(12.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))

                // Botão Flutuante (Bubble)
                Text(
                    text = "Botão Flutuante Lateral",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Aparece na lateral quando o app não está ativo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                val bubbleEnabled by prefsManager.bubbleEnabledFlow.collectAsState(initial = true)
                val bubbleSide by prefsManager.bubbleSideFlow.collectAsState(initial = "right")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Ativar bubble", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = bubbleEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                prefsManager.setBubbleEnabled(enabled)
                                if (enabled) {
                                    BubbleService.start(context)
                                } else {
                                    BubbleService.stop(context)
                                }
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text("Arraste o botão livremente pela tela", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // === PERMISSÕES ===
        // === CHECKLIST DE PERMISSÕES COMPLETO ===
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Permissões Necessárias",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Ative todas para funcionamento completo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // v5.2.0: Permissões reativas - atualizam ao voltar do settings
                val lifecycleOwner = LocalLifecycleOwner.current
                var permissionRefreshKey by remember { mutableIntStateOf(0) }
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            permissionRefreshKey++
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                // 1. Acessibilidade
                val isAccessibilityEnabled = remember(permissionRefreshKey) {
                    try {
                        val enabledServices = Settings.Secure.getString(
                            context.contentResolver,
                            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                        ) ?: ""
                        enabledServices.contains(context.packageName)
                    } catch (_: Exception) { false }
                }
                PermissionCheckItem(
                    title = "Acessibilidade",
                    description = "Detectar corridas nos apps (Uber, 99, inDrive)",
                    isGranted = isAccessibilityEnabled,
                    icon = Icons.Default.Accessibility,
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 2. Sobreposição de tela
                val isOverlayEnabled = remember(permissionRefreshKey) { Settings.canDrawOverlays(context) }
                PermissionCheckItem(
                    title = "Sobreposição de Tela",
                    description = "Exibir card sobre outros apps",
                    isGranted = isOverlayEnabled,
                    icon = Icons.Default.Layers,
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 3. Notificações
                val isNotificationEnabled = remember(permissionRefreshKey) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        androidx.core.content.ContextCompat.checkSelfPermission(
                            context, android.Manifest.permission.POST_NOTIFICATIONS
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    } else true
                }
                PermissionCheckItem(
                    title = "Notificações",
                    description = "Manter serviço ativo em background (obrigatório)",
                    isGranted = isNotificationEnabled,
                    icon = Icons.Default.Notifications,
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            context.startActivity(intent)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 4. Otimização de bateria
                val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                val isIgnoringBattery = remember(permissionRefreshKey) { pm.isIgnoringBatteryOptimizations(context.packageName) }
                PermissionCheckItem(
                    title = "Sem Restrição de Bateria",
                    description = "Impedir que o sistema mate o serviço",
                    isGranted = isIgnoringBattery,
                    icon = Icons.Default.BatteryChargingFull,
                    onClick = {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = android.net.Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Resumo
                val totalGranted = listOf(isAccessibilityEnabled, isOverlayEnabled, isNotificationEnabled, isIgnoringBattery).count { it }
                val statusColor = when (totalGranted) {
                    4 -> MaterialTheme.colorScheme.primary
                    3 -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.error
                }
                Text(
                    text = "$totalGranted/4 permissões ativas",
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )


            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // === STATUS DO SISTEMA ===
        SystemStatusCard(context, scope, prefsManager)

        Spacer(modifier = Modifier.height(16.dp))

        // === BACKUP & RESTORE (v6.1.1: movido para o final) ===
        BackupRestoreSection(context, scope, prefsManager)

        Spacer(modifier = Modifier.height(24.dp))

        // App Info - Gesto secreto: 7 toques abre Admin (SEMPRE no final da tela)
        var tapCount by remember { mutableIntStateOf(0) }
        var lastTapTime by remember { mutableLongStateOf(0L) }

        Text(
            text = "NGB AutoRoad v${com.ngbautoroad.BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable {
                    val now = System.currentTimeMillis()
                    if (now - lastTapTime > 3000) tapCount = 0
                    lastTapTime = now
                    tapCount++
                    if (tapCount == 5) {
                        Toast.makeText(context, "Mais 2 toques...", Toast.LENGTH_SHORT).show()
                    }
                    if (tapCount >= 7) {
                        tapCount = 0
                        context.startActivity(
                            Intent(context, com.ngbautoroad.ui.admin.AdminActivity::class.java)
                        )
                    }
                }
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun BackupRestoreSection(
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    prefsManager: PrefsManager
) {
    val backupManager = remember { BackupManager(context) }
    var backupStatus by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    // Launcher para exportar (criar arquivo)
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            isProcessing = true
            backupStatus = "Exportando..."
            scope.launch {
                try {
                    val metadata = backupManager.exportBackup(uri)
                    backupStatus = "✅ Backup exportado!\n" +
                        "${metadata.ridesCount} corridas, " +
                        "${metadata.financeRecords} registros financeiros\n" +
                        "Data: ${metadata.backupDate}"
                } catch (e: Exception) {
                    backupStatus = "❌ Erro ao exportar: ${e.message}"
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    // Launcher para importar (abrir arquivo)
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isProcessing = true
            backupStatus = "Importando..."
            scope.launch {
                try {
                    val metadata = backupManager.importBackup(uri)
                    backupStatus = "✅ Backup importado!\n" +
                        "Versão: v${metadata.appVersion}\n" +
                        "${metadata.ridesCount} corridas, " +
                        "${metadata.financeRecords} registros financeiros\n" +
                        "Reinicie o app para aplicar todas as configurações."
                } catch (e: Exception) {
                    backupStatus = "❌ Erro ao importar: ${e.message}"
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CloudUpload,
                    contentDescription = "Backup",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Backup & Restauração",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Exporte ou importe todas as configurações, corridas, dados financeiros e cards customizados.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val fileName = backupManager.suggestedFileName()
                        exportLauncher.launch(fileName)
                    },
                    enabled = !isProcessing,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Upload, contentDescription = "Exportar", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Exportar", fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = {
                        importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                    },
                    enabled = !isProcessing,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Download, contentDescription = "Importar", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Importar", fontSize = 13.sp)
                }
            }

            if (isProcessing) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (backupStatus.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = backupStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (backupStatus.startsWith("✅"))
                        MaterialTheme.colorScheme.primary
                    else if (backupStatus.startsWith("❌"))
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SystemStatusCard(context: android.content.Context, scope: kotlinx.coroutines.CoroutineScope, prefsManager: PrefsManager) {
    // Logs em memória por serviço
    var overlayLogs by remember { mutableStateOf(listOf<String>()) }
    var bubbleLogs by remember { mutableStateOf(listOf<String>()) }
    var showOverlayLogs by remember { mutableStateOf(false) }
    var showBubbleLogs by remember { mutableStateOf(false) }

    val overlayRunning = remember { mutableStateOf(OverlayService.isRunning()) }
    val bubbleRunning = remember { mutableStateOf(com.ngbautoroad.service.BubbleService.isRunning()) }

    // Atualizar status a cada 2s
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(2000L)
            overlayRunning.value = OverlayService.isRunning()
            bubbleRunning.value = com.ngbautoroad.service.BubbleService.isRunning()
        }
    }

    fun addLog(logs: List<String>, msg: String): List<String> {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        return (listOf("[$ts] $msg") + logs).take(50)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Status do Sistema",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Controle e logs dos serviços em execução",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            // === Overlay Service ===
            ServiceStatusRow(
                name = "Overlay (Card)",
                description = "Exibe o card sobre outros apps",
                isRunning = overlayRunning.value,
                showLogs = showOverlayLogs,
                logs = overlayLogs,
                onToggleLogs = { showOverlayLogs = !showOverlayLogs },
                onStart = {
                    try {
                        OverlayService.start(context)
                        overlayLogs = addLog(overlayLogs, "Serviço iniciado")
                        overlayRunning.value = true
                    } catch (e: Exception) {
                        overlayLogs = addLog(overlayLogs, "ERRO ao iniciar: ${e.message}")
                    }
                },
                onStop = {
                    try {
                        OverlayService.stop(context)
                        overlayLogs = addLog(overlayLogs, "Serviço parado")
                        overlayRunning.value = false
                    } catch (e: Exception) {
                        overlayLogs = addLog(overlayLogs, "ERRO ao parar: ${e.message}")
                    }
                },
                onRestart = {
                    try {
                        OverlayService.stop(context)
                        kotlinx.coroutines.GlobalScope.launch {
                            kotlinx.coroutines.delay(500L)
                            OverlayService.start(context)
                        }
                        overlayLogs = addLog(overlayLogs, "Serviço reiniciado")
                    } catch (e: Exception) {
                        overlayLogs = addLog(overlayLogs, "ERRO ao reiniciar: ${e.message}")
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))

            // === Bubble Service ===
            ServiceStatusRow(
                name = "Botão Flutuante",
                description = "Ícone lateral de acesso rápido",
                isRunning = bubbleRunning.value,
                showLogs = showBubbleLogs,
                logs = bubbleLogs,
                onToggleLogs = { showBubbleLogs = !showBubbleLogs },
                onStart = {
                    try {
                        com.ngbautoroad.service.BubbleService.start(context)
                        bubbleLogs = addLog(bubbleLogs, "Serviço iniciado")
                        bubbleRunning.value = true
                    } catch (e: Exception) {
                        bubbleLogs = addLog(bubbleLogs, "ERRO ao iniciar: ${e.message}")
                    }
                },
                onStop = {
                    try {
                        com.ngbautoroad.service.BubbleService.stop(context)
                        bubbleLogs = addLog(bubbleLogs, "Serviço parado")
                        bubbleRunning.value = false
                    } catch (e: Exception) {
                        bubbleLogs = addLog(bubbleLogs, "ERRO ao parar: ${e.message}")
                    }
                },
                onRestart = {
                    try {
                        com.ngbautoroad.service.BubbleService.stop(context)
                        kotlinx.coroutines.GlobalScope.launch {
                            kotlinx.coroutines.delay(500L)
                            com.ngbautoroad.service.BubbleService.start(context)
                        }
                        bubbleLogs = addLog(bubbleLogs, "Serviço reiniciado")
                    } catch (e: Exception) {
                        bubbleLogs = addLog(bubbleLogs, "ERRO ao reiniciar: ${e.message}")
                    }
                }
            )
        }
    }
}

@Composable
fun ServiceStatusRow(
    name: String,
    description: String,
    isRunning: Boolean,
    showLogs: Boolean,
    logs: List<String>,
    onToggleLogs: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isRunning) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null,
                        tint = if (isRunning) ScoreGreen else ScoreRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (isRunning) "● Rodando" else "○ Parado",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isRunning) ScoreGreen else ScoreRed
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        // Botões de controle
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (!isRunning) {
                OutlinedButton(
                    onClick = onStart,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Iniciar", fontSize = 12.sp)
                }
            } else {
                OutlinedButton(
                    onClick = onStop,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Parar", fontSize = 12.sp)
                }
            }
            OutlinedButton(
                onClick = onRestart,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reiniciar", fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = onToggleLogs,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Logs", fontSize = 12.sp)
            }
        }
        // Painel de logs
        if (showLogs) {
            Spacer(modifier = Modifier.height(6.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    if (logs.isEmpty()) {
                        Text(
                            "Nenhum log registrado",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        logs.forEach { log ->
                            Text(
                                log,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (log.contains("ERRO")) ScoreRed
                                    else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
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

@Composable
private fun PermissionCheckItem(
    title: String,
    description: String,
    isGranted: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (isGranted) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = if (isGranted) "Ativo" else "Inativo",
            tint = if (isGranted) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp)
        )
    }
}
