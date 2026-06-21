package com.ngbautoroad.ui.onboarding

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ngbautoroad.data.backup.BackupManager
import com.ngbautoroad.data.prefs.PrefsManager
import com.ngbautoroad.domain.PermissionManager
import kotlinx.coroutines.launch

/**
 * v6.8.0: Configuração Assistida (Setup Wizard)
 *
 * Fluxo:
 * 1. Tela de boas-vindas com 3 opções: Importar Backup, Configuração Limpa, Cancelar
 * 2. Se Importar Backup → importa e encerra
 * 3. Se Configuração Limpa → guia pelas etapas críticas:
 *    a) Permissões obrigatórias
 *    b) Cadastro de veículo + odômetro
 *    c) Configuração de plataformas (Uber, 99, etc.)
 * 4. Se Cancelar → avisa que sistema será menos eficiente, mas permite
 *
 * CUIDADO: Se chamado por usuário existente (Config > Mais), NÃO apaga dados.
 * Apenas reconfigura pontos críticos sem perder histórico.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupWizardDialog(
    prefsManager: PrefsManager,
    isFirstTime: Boolean = true, // true = primeiro uso, false = chamado de Config > Mais
    onDismiss: () -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentStep by remember { mutableIntStateOf(0) } // 0=welcome, 1=permissions, 2=vehicle, 3=done
    var showCancelWarning by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = {
            if (!isFirstTime) onDismiss()
            else showCancelWarning = true
        },
        properties = DialogProperties(
            dismissOnBackPress = !isFirstTime,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            when (currentStep) {
                0 -> WelcomeStep(
                    isFirstTime = isFirstTime,
                    onImportBackup = { currentStep = 10 }, // go to import
                    onCleanSetup = { currentStep = 1 },
                    onCancel = { showCancelWarning = true }
                )
                1 -> PermissionsStep(
                    onNext = { currentStep = 2 },
                    onBack = { currentStep = 0 }
                )
                2 -> VehicleStep(
                    onNext = { currentStep = 3 },
                    onBack = { currentStep = 1 }
                )
                3 -> CompletionStep(
                    onFinish = {
                        scope.launch {
                            prefsManager.setSetupWizardCompleted(true)
                        }
                        onComplete()
                    }
                )
                10 -> ImportBackupStep(
                    context = context,
                    onSuccess = {
                        scope.launch {
                            prefsManager.setSetupWizardCompleted(true)
                        }
                        onComplete()
                    },
                    onBack = { currentStep = 0 }
                )
            }
        }
    }

    // Cancel warning dialog
    if (showCancelWarning) {
        AlertDialog(
            onDismissRequest = { showCancelWarning = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Pular Configuração?") },
            text = {
                Text(
                    "O sistema será menos eficiente sem a configuração assistida.\n\n" +
                    "Sem ela:\n" +
                    "• Odômetro não será rastreado\n" +
                    "• Custos de manutenção serão imprecisos\n" +
                    "• Alertas de segurança podem não funcionar\n\n" +
                    "Você pode executar a configuração assistida a qualquer momento em Config > Mais."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showCancelWarning = false
                    scope.launch { prefsManager.setSetupWizardCompleted(true) }
                    onDismiss()
                }) {
                    Text("Pular mesmo assim")
                }
            },
            dismissButton = {
                Button(onClick = { showCancelWarning = false }) {
                    Text("Voltar e configurar")
                }
            }
        )
    }
}

@Composable
private fun WelcomeStep(
    isFirstTime: Boolean,
    onImportBackup: () -> Unit,
    onCleanSetup: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.RocketLaunch,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (isFirstTime) "Bem-vindo ao NGB AutoRoad!" else "Configuração Assistida",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isFirstTime)
                "Vamos configurar o essencial para que o sistema funcione com máxima eficiência."
            else
                "Reconfigure os pontos críticos do sistema. Seus dados existentes serão mantidos.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!isFirstTime) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Seus dados e histórico NÃO serão apagados",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Option 1: Import Backup
        Button(
            onClick = onImportBackup,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.CloudDownload, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Importar Backup Existente")
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Option 2: Clean Setup
        OutlinedButton(
            onClick = onCleanSetup,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Tune, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Configuração Limpa")
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Option 3: Cancel
        TextButton(onClick = onCancel) {
            Text("Pular por agora", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionsStep(
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }
    val permissions = remember(refreshKey) { PermissionManager.getAllPermissions(context) }
    val requiredGranted = permissions.count { it.isRequired && it.isGranted }
    val requiredTotal = permissions.count { it.isRequired }

    // Launchers para permissões runtime
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshKey++ }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshKey++ }

    val activityRecognitionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshKey++ }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshKey++ }

    // Lifecycle observer para refresh ao retornar de settings
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // Header
        Text(
            "Permissões Necessárias",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            "$requiredGranted/$requiredTotal obrigatórias ativas",
            style = MaterialTheme.typography.bodyMedium,
            color = if (requiredGranted == requiredTotal) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error
        )
        Text(
            "Toque em cada item para ativar. Ative na ordem de cima para baixo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (requiredGranted < requiredTotal) {
            Text(
                "Você pode pular e ativar depois em Config > App",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Permission list - CADA ITEM É CLICÁVEL
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            permissions.forEach { perm ->
                Card(
                    onClick = {
                        if (!perm.isGranted) {
                            // Abrir a permissão específica baseado no nome
                            when (perm.name) {
                                "Acessibilidade" -> {
                                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                }
                                "Sobreposição de Tela" -> {
                                    context.startActivity(
                                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                            data = android.net.Uri.parse("package:${context.packageName}")
                                        }
                                    )
                                }
                                "Notificações" -> {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                                "Localização Precisa" -> {
                                    locationLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
                                }
                                "Localização em Background" -> {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        // Precisa ter localização precisa primeiro
                                        val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(
                                            context, android.Manifest.permission.ACCESS_FINE_LOCATION
                                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                        if (hasFine) {
                                            backgroundLocationLauncher.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                                        } else {
                                            // Pede localização precisa primeiro
                                            locationLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
                                            Toast.makeText(context, "Ative a Localização Precisa primeiro", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                "Reconhecimento de Atividade" -> {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        activityRecognitionLauncher.launch(android.Manifest.permission.ACTIVITY_RECOGNITION)
                                    }
                                }
                                "Sem Restrição de Bateria" -> {
                                    context.startActivity(
                                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                            data = android.net.Uri.parse("package:${context.packageName}")
                                        }
                                    )
                                }
                                "Alarmes Exatos" -> {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                                    }
                                }
                            }
                        }
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = if (perm.isGranted)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else if (perm.isRequired)
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (perm.isGranted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (perm.isGranted) MaterialTheme.colorScheme.primary
                            else if (perm.isRequired) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                perm.name + if (perm.isRequired) " *" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                perm.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!perm.isGranted) {
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = "Ativar",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBack) { Text("Voltar") }
            Button(
                onClick = { onNext() },
                enabled = true // v6.9.3: Permitir avançar sempre (usuário pode ativar depois)
            ) {
                Text(
                    if (requiredGranted == requiredTotal) "Próximo ✓"
                    else "Pular ($requiredGranted/$requiredTotal)"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehicleStep(
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var vehicleName by remember { mutableStateOf("") }
    var odometer by remember { mutableStateOf("") }
    var fuelType by remember { mutableStateOf("Elétrico") }
    val fuelOptions = listOf("Elétrico", "Gasolina", "Etanol", "Flex", "Diesel", "Híbrido")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            "Veículo e Odômetro",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Informe os dados do veículo principal para cálculos precisos",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = vehicleName,
            onValueChange = { vehicleName = it },
            label = { Text("Nome do veículo (ex: BYD Dolphin GS)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = odometer,
            onValueChange = { odometer = it.filter { c -> c.isDigit() } },
            label = { Text("Odômetro atual (km)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = { Text("Olhe no painel do carro e informe o valor exato") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Tipo de combustível:", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            fuelOptions.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { option ->
                        FilterChip(
                            selected = fuelType == option,
                            onClick = { fuelType = option },
                            label = { Text(option, style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Fill remaining space if row has less than 3 items
                    repeat(3 - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Info card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "O odômetro é essencial para cálculos de manutenção e custo/km. A IA aprenderá seu padrão de uso com o tempo.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBack) { Text("Voltar") }
            Button(
                onClick = {
                    if (odometer.isNotBlank() && vehicleName.isNotBlank()) {
                        // Save to prefs for later DB insertion
                        val prefs = context.getSharedPreferences("ngb_autoroad_prefs", Context.MODE_PRIVATE)
                        prefs.edit()
                            .putString("setup_vehicle_name", vehicleName)
                            .putLong("setup_odometer", odometer.toLongOrNull() ?: 0L)
                            .putString("setup_fuel_type", fuelType)
                            .putBoolean("odometer_onboarding_done", true)
                            .apply()
                        onNext()
                    } else {
                        Toast.makeText(context, "Preencha o nome e o odômetro", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = vehicleName.isNotBlank() && odometer.isNotBlank()
            ) {
                Text("Próximo")
            }
        }
    }
}

@Composable
private fun CompletionStep(onFinish: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Tudo Pronto!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "O sistema está configurado e pronto para funcionar com máxima eficiência.\n\n" +
            "Dicas:\n" +
            "• Atualize o odômetro a cada 1-2 semanas\n" +
            "• A IA aprenderá seu padrão em 2-4 semanas\n" +
            "• Verifique as permissões em Config > App",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Começar a usar")
        }
    }
}

@Composable
private fun ImportBackupStep(
    context: Context,
    onSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val backupManager = remember { BackupManager(context) }
    var status by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isProcessing = true
            status = "Importando backup..."
            scope.launch {
                try {
                    val result = backupManager.importBackup(uri)
                    status = "Backup importado com sucesso! ${result.ridesCount} corridas restauradas."
                    kotlinx.coroutines.delay(1500)
                    onSuccess()
                } catch (e: Exception) {
                    status = "Erro: ${e.message}"
                    isProcessing = false
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.CloudDownload,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Importar Backup",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Selecione o arquivo .zip do backup exportado anteriormente.\n" +
            "Todas as configurações, corridas e dados financeiros serão restaurados.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (status.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (status.startsWith("Erro"))
                        MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    status,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (!isProcessing) {
            Button(
                onClick = { importLauncher.launch(arrayOf("application/zip")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Selecionar Arquivo de Backup")
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = onBack) { Text("Voltar") }
        } else {
            CircularProgressIndicator()
        }
    }
}
