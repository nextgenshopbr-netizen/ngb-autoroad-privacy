package com.ngbautoroad.ui.admin

// ============================================================================
// ARQUIVO: AdminActivity.kt
// RESPONSABILIDADE: Área administrativa oculta com simulador de corridas
// ACESSO: Tocar 7x na versão do app → PIN 250696 (padrão fixo)
// BLOCOS:
//   - AdminScreen (L60): Controle de autenticação por PIN
//   - PinScreen (L130): Tela de digitação do PIN
//   - AdminPanel (L200): Painel principal com simulador e controles
//   - UberStyleCard (L420): Card visual idêntico ao overlay real (como GigU)
//   - simulateRide (L580): Geração de dados simulados com score real
// DEPENDÊNCIAS:
//   - domain/RideScorer.kt → cálculo de score real
//   - data/prefs/PrefsManager.kt → critérios e thresholds do motorista
// ============================================================================

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngbautoroad.data.model.*
import com.ngbautoroad.data.prefs.PrefsManager
import com.ngbautoroad.domain.RideScorer
import com.ngbautoroad.ui.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.random.Random

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

// ============================================================================
// BLOCO: AdminScreen — Controle de autenticação por PIN
// PIN padrão fixo: 250696
// Se o usuário nunca alterou, usa o padrão. Se alterou, usa o salvo.
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    prefsManager: PrefsManager,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isAuthenticated by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
    var adminPin by remember { mutableStateOf("250696") } // PIN padrão fixo

    // Carregar PIN salvo (se o usuário alterou)
    LaunchedEffect(Unit) {
        val savedPin = prefsManager.adminPinFlow.first()
        if (savedPin.isNotBlank()) {
            adminPin = savedPin
        } else {
            // Primeiro acesso: salvar PIN padrão
            prefsManager.saveAdminPin("250696")
        }
    }

    if (!isAuthenticated) {
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
    } else {
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

// ============================================================================
// BLOCO: PinScreen — Tela de digitação do PIN
// ============================================================================
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
            contentDescription = "Admin",
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

// ============================================================================
// BLOCO: AdminPanel — Painel principal
// Contém: Simulador de corridas, botão Alterar PIN, controles do sistema
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanel(
    prefsManager: PrefsManager,
    currentPin: String,
    onPinChanged: (String) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var simulationResult by remember { mutableStateOf<SimulationResult?>(null) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Administração", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A2E),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
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
            // === BOTÃO ALTERAR PIN (sempre visível no topo) ===
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("PIN de Acesso", fontWeight = FontWeight.Bold)
                        Text(
                            "Atual: ${"•".repeat(currentPin.length)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { showChangePinDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "Alterar PIN", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Alterar PIN")
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // === SEÇÃO: SIMULAÇÃO DE CORRIDAS ===
            AdminSectionHeader("Simulador de Corridas", Icons.Default.PlayArrow)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Simula corridas com dados realistas e mostra o card exatamente como aparece no overlay",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Botões de simulação
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SimulationButton(
                    text = "Boa",
                    color = ScoreGreen,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        scope.launch {
                            simulationResult = simulateRide(prefsManager, RideQuality.GOOD)
                        }
                    }
                )
                SimulationButton(
                    text = "Média",
                    color = ScoreYellow,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        scope.launch {
                            simulationResult = simulateRide(prefsManager, RideQuality.AVERAGE)
                        }
                    }
                )
                SimulationButton(
                    text = "Ruim",
                    color = ScoreRed,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        scope.launch {
                            simulationResult = simulateRide(prefsManager, RideQuality.BAD)
                        }
                    }
                )
                SimulationButton(
                    text = "Aleatória",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        scope.launch {
                            simulationResult = simulateRide(prefsManager, RideQuality.RANDOM)
                        }
                    }
                )
            }

            // Card visual idêntico ao overlay real (como GigU)
            simulationResult?.let { result ->
                Spacer(modifier = Modifier.height(16.dp))
                UberStyleCard(result)
                Spacer(modifier = Modifier.height(12.dp))
                SimulationDetailsCard(result)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // === SEÇÃO: CONTROLES DO SISTEMA ===
            AdminSectionHeader("Controles do Sistema", Icons.Default.Settings)
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { showResetDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ScoreRed)
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = "Resetar")
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
                    InfoRow("Versão", "4.3.0")
                    InfoRow("Build", "release")
                    InfoRow("Package", "com.ngbautoroad")
                    InfoRow("SDK Target", "34")
                    InfoRow("Compose BOM", "2024.01.00")
                    InfoRow("Room", "2.6.1")
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

// ============================================================================
// BLOCO: UberStyleCard — Card visual IDÊNTICO ao overlay real
// Layout baseado na imagem do GigU:
//   ┌─────────────────────────────────────────────┐
//   │  R$/Km    R$/Hora   Lucro/hr    Nota        │
//   │  |1,11    |30,60    |1,11       |4,95       │  (barras de cor)
//   │  [UBER] 0h19m • 8.70km                     │
//   └─────────────────────────────────────────────┘
// Borda: vermelha (ruim), amarela (média), verde (boa)
// ============================================================================
@Composable
fun UberStyleCard(result: SimulationResult) {
    val scoreColor = when {
        result.score >= 70 -> ScoreGreen
        result.score >= 50 -> ScoreYellow
        result.score >= 30 -> ScoreOrange
        else -> ScoreRed
    }

    // Calcular lucro/hora (valor - custo estimado por km * distância) / tempo * 60
    val lucroHora = result.ratePerHour * 0.7 // Estimativa de lucro líquido (70% do bruto)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(3.dp, scoreColor),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Linha principal com métricas (como GigU)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricColumn(
                    label = "R$/Km",
                    value = "%.2f".format(result.ratePerKm),
                    threshold = result.thresholdRatePerKm,
                    actual = result.ratePerKm
                )
                MetricColumn(
                    label = "R$/Hora",
                    value = "%.2f".format(result.ratePerHour),
                    threshold = result.thresholdRatePerHour,
                    actual = result.ratePerHour
                )
                MetricColumn(
                    label = "Lucro/hr",
                    value = "%.2f".format(lucroHora),
                    threshold = result.thresholdRatePerHour * 0.7,
                    actual = lucroHora
                )
                MetricColumn(
                    label = "Nota",
                    value = "%.1f".format(result.passengerRating),
                    threshold = result.thresholdRating,
                    actual = result.passengerRating
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Linha inferior com plataforma, tempo e distância
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF5F5F5), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Logo da plataforma (badge)
                Box(
                    modifier = Modifier
                        .background(
                            when (result.platform) {
                                "Uber" -> Color.Black
                                "99" -> Color(0xFF00B74F)
                                "inDrive" -> Color(0xFF6C3FBF)
                                else -> Color(0xFF7B2FF7)
                            },
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = when (result.platform) {
                            "Uber" -> "UBER"
                            "99" -> "99"
                            "inDrive" -> "iDRV"
                            else -> "CBF"
                        },
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Tipo de corrida (UberX, Comfort, Black, etc.)
                Text(
                    text = result.rideType,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF555555)
                )

                // Tempo e distância
                Text(
                    text = "${result.estimatedMinutes / 60}h${result.estimatedMinutes % 60}m • %.2fkm".format(result.distance),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF333333)
                )

                Spacer(modifier = Modifier.weight(1f))

                // Score badge
                Box(
                    modifier = Modifier
                        .background(scoreColor, CircleShape)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${result.score.toInt()}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ============================================================================
// BLOCO: MetricColumn — Coluna individual de métrica no card
// Mostra: label, barra de cor (verde se acima do threshold, vermelho se abaixo), valor
// ============================================================================
@Composable
fun MetricColumn(label: String, value: String, threshold: Double, actual: Double) {
    val isGood = actual >= threshold
    val barColor = if (isGood) ScoreGreen else ScoreRed

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(70.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color(0xFF888888),
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Barra de cor indicadora
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(24.dp)
                    .background(barColor, RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A1A)
            )
        }
    }
}

// ============================================================================
// BLOCO: SimulationDetailsCard — Detalhes expandidos da simulação
// Mostra dados internos: bairros, surge, violações
// ============================================================================
@Composable
fun SimulationDetailsCard(result: SimulationResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Detalhes da Simulação", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Divider()
            Spacer(modifier = Modifier.height(6.dp))

            InfoRow("Valor da corrida", "R$ %.2f".format(result.value))
            InfoRow("Distância total", "%.1f km".format(result.distance))
            InfoRow("Dist. até embarque", "%.1f km".format(result.pickupDistance))
            InfoRow("Tempo estimado", "${result.estimatedMinutes} min")
            InfoRow("Embarque", result.pickupNeighborhood)
            InfoRow("Destino", result.dropoffNeighborhood)
            InfoRow("Avaliação passageiro", "★ %.2f".format(result.passengerRating))
            InfoRow("Surge/Dinâmica", "%.1fx".format(result.surgeMultiplier))
            InfoRow("Score final", "${result.score.toInt()} pontos")

            if (result.violations.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("⚠️ Violações de threshold:", color = ScoreOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                result.violations.forEach { violation ->
                    Text("  • $violation", color = ScoreOrange, fontSize = 11.sp)
                }
            }
        }
    }
}

// ============================================================================
// BLOCO: Componentes auxiliares
// ============================================================================
@Composable
fun AdminSectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
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
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 12.dp)
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Text(
                    "Digite o novo PIN de 6 dígitos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) newPin = it },
                    label = { Text("Novo PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) confirmPin = it },
                    label = { Text("Confirmar novo PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = error.isNotBlank(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
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

// ============================================================================
// BLOCO: Simulação de corridas
// Gera dados realistas baseados na qualidade selecionada
// Usa o RideScorer REAL com os critérios configurados pelo motorista
// ============================================================================

enum class RideQuality { GOOD, AVERAGE, BAD, RANDOM }

data class SimulationResult(
    val platform: String,
    val rideType: String, // Tipo da corrida (UberX, Comfort, Black, 99Pop, etc.)
    val value: Double,
    val distance: Double,
    val pickupDistance: Double,
    val ratePerKm: Double,
    val estimatedMinutes: Int,
    val ratePerHour: Double,
    val pickupNeighborhood: String,
    val dropoffNeighborhood: String,
    val passengerRating: Double,
    val surgeMultiplier: Double,
    val score: Double,
    val violations: List<String>,
    // Thresholds do motorista para comparação visual no card
    val thresholdRatePerKm: Double,
    val thresholdRatePerHour: Double,
    val thresholdRating: Double
)

private val platforms = listOf("Uber", "99", "inDrive", "Cabify")
private val uberTypes = listOf("UberX", "Comfort", "Black", "Flash", "Green")
private val ninetyNineTypes = listOf("99Pop", "99Comfort")
private val neighborhoods = listOf(
    "Centro", "Líder", "Vila Real", "Eldorado", "Belvedere",
    "Efapi", "Passo dos Fortes", "São Cristóvão", "Jardim Itália",
    "Presidente Médici", "Santa Maria", "Seminário", "Universitário",
    "Palmital", "Parque das Palmeiras", "Maria Goretti", "Bela Vista",
    "Esplanada", "Quedas do Palmital", "Trevo", "Alvorada",
    "Jardim América", "São Pedro", "Paraíso"
)

suspend fun simulateRide(prefsManager: PrefsManager, quality: RideQuality): SimulationResult {
    val random = Random(System.currentTimeMillis())

    // Gerar dados baseados na qualidade
    val (valueRange, distanceRange, surgeRange, ratingRange) = when (quality) {
        RideQuality.GOOD -> Quadruple(20.0..60.0, 4.0..15.0, 1.2..2.5, 4.7..5.0)
        RideQuality.AVERAGE -> Quadruple(10.0..25.0, 3.0..10.0, 1.0..1.3, 4.3..4.8)
        RideQuality.BAD -> Quadruple(5.0..12.0, 1.0..5.0, 1.0..1.0, 3.5..4.5)
        RideQuality.RANDOM -> Quadruple(5.0..80.0, 1.0..25.0, 1.0..3.0, 3.5..5.0)
    }

    val value = random.nextDouble(valueRange.start, valueRange.endInclusive)
    val distance = random.nextDouble(distanceRange.start, distanceRange.endInclusive)
    val surge = random.nextDouble(surgeRange.start, surgeRange.endInclusive)
    val rating = random.nextDouble(ratingRange.start, ratingRange.endInclusive)
    val ratePerKm = value / distance
    val estimatedMinutes = (distance * random.nextDouble(2.0, 4.0)).toInt().coerceAtLeast(3)
    val ratePerHour = (value / estimatedMinutes) * 60
    val pickupDist = random.nextDouble(0.5, 4.0)
    val stops = if (quality == RideQuality.BAD) random.nextInt(0, 3) else 0

    val platform = platforms[random.nextInt(platforms.size)]
    val rideTypeStr = when (platform) {
        "Uber" -> uberTypes[random.nextInt(uberTypes.size)]
        "99" -> ninetyNineTypes[random.nextInt(ninetyNineTypes.size)]
        else -> "Padrão"
    }
    val pickup = neighborhoods[random.nextInt(neighborhoods.size)]
    val dropoff = neighborhoods[random.nextInt(neighborhoods.size)]

    // Detectar enum RideType
    val platformEnum = when (platform) {
        "Uber" -> Platform.UBER
        "99" -> Platform.NINETY_NINE
        "inDrive" -> Platform.INDRIVE
        else -> Platform.CABIFY
    }
    val rideTypeEnum = RideType.fromBadgeText(rideTypeStr, platformEnum)

    // Calcular score usando o RideScorer real
    val rideData = RideData(
        platform = platformEnum,
        rideType = rideTypeEnum,
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
        rideType = rideTypeStr,
        value = value,
        distance = distance,
        pickupDistance = pickupDist,
        ratePerKm = ratePerKm,
        estimatedMinutes = estimatedMinutes,
        ratePerHour = ratePerHour,
        pickupNeighborhood = pickup,
        dropoffNeighborhood = dropoff,
        passengerRating = rating,
        surgeMultiplier = surge,
        score = scoreResult.totalScore,
        violations = violations,
        thresholdRatePerKm = thresholds.minValuePerKm,
        thresholdRatePerHour = thresholds.minValuePerHour,
        thresholdRating = thresholds.minPassengerRating
    )
}

// Helper para destructuring de 4 valores
private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

private fun Double.format(): String = "%.2f".format(this)
