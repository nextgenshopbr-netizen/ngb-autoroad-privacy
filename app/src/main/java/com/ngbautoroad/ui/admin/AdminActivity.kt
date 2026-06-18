package com.ngbautoroad.ui.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngbautoroad.data.model.*
import com.ngbautoroad.data.prefs.PrefsManager
import com.ngbautoroad.domain.RideScorer
import com.ngbautoroad.domain.ScoringThresholds
import com.ngbautoroad.ui.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Área Administrativa oculta
 *
 * Acesso: Tocar 7x na versão do app em Configurações
 * Proteção: PIN de 6 dígitos (padrão: 147258)
 *
 * Funcionalidades:
 * - Simular corridas com dados aleatórios
 * - Ver logs internos do sistema
 * - Forçar estados do overlay
 * - Resetar configurações
 * - Testar OCR e Accessibility
 * - Estatísticas internas
 */
class AdminActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NGBAutoRoadTheme {
                AdminScreen(
                    prefsManager = PrefsManager(applicationContext),
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    prefsManager: PrefsManager,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isAuthenticated by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinConfirm by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
    var adminPin by remember { mutableStateOf("") }
    var isFirstAccess by remember { mutableStateOf(true) }
    var isSettingPin by remember { mutableStateOf(false) }
    var pinMismatch by remember { mutableStateOf(false) }

    // Carregar PIN salvo
    LaunchedEffect(Unit) {
        val savedPin = prefsManager.adminPinFlow.first()
        if (savedPin.isNotBlank()) {
            adminPin = savedPin
            isFirstAccess = false
        } else {
            isFirstAccess = true
            isSettingPin = true
        }
    }

    if (!isAuthenticated) {
        if (isSettingPin) {
            // Tela de CRIAR PIN (primeiro acesso)
            CreatePinScreen(
                pinInput = pinInput,
                pinConfirm = pinConfirm,
                pinMismatch = pinMismatch,
                onPinChange = { pinInput = it; pinMismatch = false },
                onConfirmChange = { pinConfirm = it; pinMismatch = false },
                onSubmit = {
                    if (pinInput == pinConfirm && pinInput.length == 6) {
                        adminPin = pinInput
                        scope.launch { prefsManager.saveAdminPin(pinInput) }
                        isAuthenticated = true
                        isSettingPin = false
                        isFirstAccess = false
                    } else {
                        pinMismatch = true
                        pinConfirm = ""
                    }
                },
                onBack = onBack
            )
        } else {
            // Tela de DIGITAR PIN (acessos seguintes)
            PinScreen(
                pinInput = pinInput,
                pinError = pinError,
                onPinChange = { pinInput = it; pinError = false },
                onSubmit = {
                    if (pinInput == adminPin) {
                        isAuthenticated = true
                        pinError = false
                    } else {
                        pinError = true
                        pinInput = ""
                    }
                },
                onBack = onBack
            )
        }
    } else {
        // Tela Admin
        AdminPanel(
            prefsManager = prefsManager,
            currentPin = adminPin,
            onPinChanged = { newPin ->
                adminPin = newPin
                scope.launch { prefsManager.saveAdminPin(newPin) }
            },
            onBack = onBack
        )
    }
}

@Composable
fun PinScreen(
    pinInput: String,
    pinError: Boolean,
    onPinChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.AdminPanelSettings,
            contentDescription = "Ícone",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Área Administrativa",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Digite o PIN de acesso",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = pinInput,
            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) onPinChange(it) },
            label = { Text("PIN (6 dígitos)") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            isError = pinError,
            supportingText = if (pinError) {{ Text("PIN incorreto", color = ScoreRed) }} else null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(0.7f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onSubmit,
            enabled = pinInput.length == 6,
            modifier = Modifier.fillMaxWidth(0.7f)
        ) {
            Text("Entrar")
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = onBack) {
            Text("Cancelar")
        }
    }
}

@Composable
fun CreatePinScreen(
    pinInput: String,
    pinConfirm: String,
    pinMismatch: Boolean,
    onPinChange: (String) -> Unit,
    onConfirmChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.AdminPanelSettings,
            contentDescription = "Ícone",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Primeiro Acesso",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Crie um PIN de 6 dígitos para proteger a área administrativa",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = pinInput,
            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) onPinChange(it) },
            label = { Text("Novo PIN (6 dígitos)") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(0.7f)
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = pinConfirm,
            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) onConfirmChange(it) },
            label = { Text("Confirme o PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            isError = pinMismatch,
            supportingText = if (pinMismatch) {{ Text("PINs não coincidem", color = ScoreRed) }} else null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(0.7f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onSubmit,
            enabled = pinInput.length == 6 && pinConfirm.length == 6,
            modifier = Modifier.fillMaxWidth(0.7f)
        ) {
            Text("Criar PIN e Entrar")
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = onBack) {
            Text("Cancelar")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanel(
    prefsManager: PrefsManager,
    currentPin: String,
    onPinChanged: (String) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedSection by remember { mutableIntStateOf(0) }
    var simulationResult by remember { mutableStateOf<SimulationResult?>(null) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Panel", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = { showChangePinDialog = true }) {
                        Icon(Icons.Default.Lock, contentDescription = "Alterar PIN")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A2E)
                )
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
            // === SEÇÃO: SIMULAÇÃO DE CORRIDAS ===
            AdminSectionHeader("Simulação de Corridas", Icons.Default.PlayArrow)
            Spacer(modifier = Modifier.height(8.dp))

            // Botões de simulação rápida
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SimulationButton(
                    text = "Corrida Boa",
                    color = ScoreGreen,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        scope.launch {
                            simulationResult = simulateRide(prefsManager, RideQuality.GOOD)
                        }
                    }
                )
                SimulationButton(
                    text = "Corrida Média",
                    color = ScoreYellow,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        scope.launch {
                            simulationResult = simulateRide(prefsManager, RideQuality.AVERAGE)
                        }
                    }
                )
                SimulationButton(
                    text = "Corrida Ruim",
                    color = ScoreRed,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        scope.launch {
                            simulationResult = simulateRide(prefsManager, RideQuality.BAD)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    scope.launch {
                        simulationResult = simulateRide(prefsManager, RideQuality.RANDOM)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Casino, contentDescription = "Simular")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Simular Corrida Aleatória")
            }

            // Resultado da simulação
            simulationResult?.let { result ->
                Spacer(modifier = Modifier.height(12.dp))
                SimulationResultCard(result)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // === SEÇÃO: CONTROLES DO SISTEMA ===
            AdminSectionHeader("Controles do Sistema", Icons.Default.Settings)
            Spacer(modifier = Modifier.height(8.dp))

            // Forçar overlay
            OutlinedButton(
                onClick = {
                    scope.launch {
                        simulationResult = simulateRide(prefsManager, RideQuality.RANDOM)
                        // Enviar intent para OverlayService com dados simulados
                        // Isso será implementado via broadcast
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Layers, contentDescription = "Ícone")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Forçar Exibição do Overlay")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { showResetDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ScoreRed)
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = "Limpar")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Resetar Todas as Configurações")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // === SEÇÃO: INFORMAÇÕES DO SISTEMA ===
            AdminSectionHeader("Informações do Sistema", Icons.Default.Info)
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    InfoRow("Versão", "3.2.0")
                    InfoRow("Build", "debug")
                    InfoRow("Package", "com.ngbautoroad")
                    InfoRow("PIN Admin", currentPin)
                    InfoRow("SDK Target", "34")
                    InfoRow("Compose BOM", "2024.01.00")
                }
            }
        }
    }

    // Dialog para alterar PIN
    if (showChangePinDialog) {
        ChangePinDialog(
            currentPin = currentPin,
            onDismiss = { showChangePinDialog = false },
            onConfirm = { newPin ->
                onPinChanged(newPin)
                showChangePinDialog = false
            }
        )
    }

    // Dialog de reset
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Resetar Configurações") },
            text = { Text("Isso vai apagar TODAS as configurações, critérios, cards e histórico. Esta ação não pode ser desfeita.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            prefsManager.resetAll()
                            showResetDialog = false
                        }
                    }
                ) {
                    Text("RESETAR", color = ScoreRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun AdminSectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = "Ícone", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SimulationButton(text: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SimulationResultCard(result: SimulationResult) {
    val scoreColor = when {
        result.score >= 70 -> ScoreGreen
        result.score >= 50 -> ScoreYellow
        result.score >= 30 -> ScoreOrange
        else -> ScoreRed
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1B2A)),
        border = BorderStroke(2.dp, scoreColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SIMULAÇÃO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(
                    "${result.score.toInt()} pts",
                    color = scoreColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Divider(color = Color.White.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(8.dp))

            SimResultRow("Plataforma", result.platform)
            SimResultRow("Valor", "R$ %.2f".format(result.value))
            SimResultRow("Distância", "%.1f km".format(result.distance))
            SimResultRow("R$/km", "%.2f".format(result.ratePerKm))
            SimResultRow("Tempo estimado", "${result.estimatedMinutes} min")
            SimResultRow("R$/hora", "%.2f".format(result.ratePerHour))
            SimResultRow("Embarque", result.pickupNeighborhood)
            SimResultRow("Destino", result.dropoffNeighborhood)
            SimResultRow("Surge/Dinâmica", "%.1fx".format(result.surgeMultiplier))

            if (result.violations.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("⚠️ Violações:", color = ScoreOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                result.violations.forEach { violation ->
                    Text("  • $violation", color = ScoreOrange, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun SimResultRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ChangePinDialog(
    currentPin: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Alterar PIN") },
        text = {
            Column {
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) newPin = it },
                    label = { Text("Novo PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) confirmPin = it },
                    label = { Text("Confirmar PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true
                )
                if (error.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(error, color = ScoreRed, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    newPin.length != 6 -> error = "PIN deve ter 6 dígitos"
                    newPin != confirmPin -> error = "PINs não conferem"
                    else -> onConfirm(newPin)
                }
            }) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

// === SIMULAÇÃO DE CORRIDAS ===

enum class RideQuality { GOOD, AVERAGE, BAD, RANDOM }

data class SimulationResult(
    val platform: String,
    val value: Double,
    val distance: Double,
    val ratePerKm: Double,
    val estimatedMinutes: Int,
    val ratePerHour: Double,
    val pickupNeighborhood: String,
    val dropoffNeighborhood: String,
    val surgeMultiplier: Double,
    val score: Double,
    val violations: List<String>
)

private val platforms = listOf("Uber", "99", "inDrive", "Cabify")
private val neighborhoods = listOf(
    "Centro", "Copacabana", "Ipanema", "Botafogo", "Tijuca",
    "Barra da Tijuca", "Méier", "Madureira", "Penha", "Bangu",
    "Campo Grande", "Santa Cruz", "Jacarepaguá", "Recreio",
    "Flamengo", "Laranjeiras", "Leblon", "Lapa", "São Cristóvão",
    "Vila Isabel", "Grajaú", "Andaraí", "Catete", "Glória"
)

suspend fun simulateRide(prefsManager: PrefsManager, quality: RideQuality): SimulationResult {
    val random = Random(System.currentTimeMillis())

    // Gerar dados baseados na qualidade
    val (valueRange, distanceRange, surgeRange) = when (quality) {
        RideQuality.GOOD -> Triple(25.0..80.0, 5.0..20.0, 1.2..2.5)
        RideQuality.AVERAGE -> Triple(12.0..30.0, 3.0..12.0, 1.0..1.5)
        RideQuality.BAD -> Triple(5.0..15.0, 1.0..8.0, 1.0..1.0)
        RideQuality.RANDOM -> Triple(5.0..100.0, 1.0..30.0, 1.0..3.0)
    }

    val value = random.nextDouble(valueRange.start, valueRange.endInclusive)
    val distance = random.nextDouble(distanceRange.start, distanceRange.endInclusive)
    val surge = random.nextDouble(surgeRange.start, surgeRange.endInclusive)
    val ratePerKm = value / distance
    val estimatedMinutes = (distance * random.nextDouble(2.5, 5.0)).toInt()
    val ratePerHour = (value / estimatedMinutes) * 60
    val pickupDist = random.nextDouble(0.5, 5.0)
    val rating = random.nextDouble(3.5, 5.0)
    val stops = if (quality == RideQuality.BAD) random.nextInt(0, 3) else 0

    val platform = platforms[random.nextInt(platforms.size)]
    val pickup = neighborhoods[random.nextInt(neighborhoods.size)]
    val dropoff = neighborhoods[random.nextInt(neighborhoods.size)]

    // Calcular score usando o RideScorer real com critérios do motorista
    val rideData = RideData(
        platform = when (platform) {
            "Uber" -> Platform.UBER
            "99" -> Platform.NINETY_NINE
            "inDrive" -> Platform.INDRIVE
            else -> Platform.CABIFY
        },
        rideValue = value,
        rideDuration = estimatedMinutes.toDouble(),
        pickupDistance = pickupDist,
        dropoffDistance = distance,
        passengerRating = rating,
        intermediateStops = stops,
        pickupNeighborhood = pickup,
        dropoffNeighborhood = dropoff
    )

    val weights = prefsManager.criteriaWeightsFlow.first()
    val thresholds = prefsManager.driverThresholdsFlow.first()
    val blockedPickup = prefsManager.blockedPickupFlow.first()
    val blockedDropoff = prefsManager.blockedDropoffFlow.first()

    // Converter bairros bloqueados para BlockedNeighborhood
    val blockedNeighborhoods = blockedPickup.map { BlockedNeighborhood(it.first, NeighborhoodType.PICKUP, it.second) } +
        blockedDropoff.map { BlockedNeighborhood(it.first, NeighborhoodType.DROPOFF, it.second) }

    val scorer = RideScorer(
        weights = weights,
        driverThresholds = thresholds,
        blockedNeighborhoods = blockedNeighborhoods
    )
    val scoreResult = scorer.calculateScore(rideData)

    val violations = scoreResult.thresholdViolations.map {
        "${it.criteriaName}: ${it.currentValue.format()} < ${it.minimumRequired.format()} (mín)"
    }

    return SimulationResult(
        platform = platform,
        value = value,
        distance = distance,
        ratePerKm = ratePerKm,
        estimatedMinutes = estimatedMinutes,
        ratePerHour = ratePerHour,
        pickupNeighborhood = pickup,
        dropoffNeighborhood = dropoff,
        surgeMultiplier = surge,
        score = scoreResult.totalScore,
        violations = violations
    )
}

private fun Double.format(): String = "%.2f".format(this)
