@file:OptIn(ExperimentalMaterial3Api::class)

package com.ngbautoroad.ui.finance

// ============================================================================
// ARQUIVO: FinanceActivity.kt
// LOCALIZAÇÃO: ui/finance/FinanceActivity.kt
// RESPONSABILIDADE: Controle financeiro completo do motorista
// COMPOSABLES (blocos principais):
//   - FinanceScreen (L58-107): Scaffold com 5 abas (Resumo, Ganhos, Gastos, Veículo, Metas)
//   - FinanceSummaryTab (L110-263): DRE simplificado + progresso de metas
//   - EarningsTab (L266-347): Lista de ganhos com auto-import toggle
//   - EarningCard (L350-404): Card individual de ganho com editar/excluir
//   - AddEarningDialog (L407-530): Dialog para adicionar/editar ganho
//   - ExpensesTab (L533-604): Lista de gastos com recorrência
//   - ExpenseCard (L607-647): Card individual de gasto
//   - AddExpenseDialog (L650-801): Dialog para adicionar gasto (com recorrência)
//   - VehicleTab (L804-1011): Config do veículo + cálculo de custo/km
//   - GoalsTab (L1014-1113): Metas financeiras com progresso real
//   - AddGoalDialog (L1116-1170): Dialog para criar meta
//   - FinanceInfoCard (L1173-1188): Card reutilizável de info
//   - getPeriodRange (L1190-fim): Utilitário de range de datas
// DEPENDÊNCIAS:
//   - data/db/FinanceDatabase.kt → DAOs (ExpenseDao, EarningDao, etc.)
//   - data/prefs/PrefsManager.kt → autoImportEarningsFlow
//   - util/Extensions.kt → toDoubleLocale(), toDoubleLocaleOrNull()
// PROTEÇÕES:
//   - Todos os campos numéricos usam toDoubleLocale() (aceita vírgula BR)
//   - Botões de salvar desabilitados quando valor <= 0
//   - VehicleTab usa rememberSaveable (sobrevive troca de aba)
//   - Validação de ano (4 dígitos, 1990-2030)
// ============================================================================

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
// AutoMirrored removido para compatibilidade
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngbautoroad.data.db.*
import com.ngbautoroad.data.model.*
import com.ngbautoroad.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.ngbautoroad.util.toDoubleLocale
import com.ngbautoroad.util.toDoubleLocaleOrNull

class FinanceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = FinanceDatabase.getInstance(applicationContext)
        val appDb = AppDatabase.getInstance(applicationContext)
        val prefsManager = com.ngbautoroad.data.prefs.PrefsManager(applicationContext)
        setContent {
            NGBAutoRoadTheme {
                FinanceScreen(
                    expenseDao = db.expenseDao(),
                    earningDao = db.earningDao(),
                    reminderDao = db.reminderDao(),
                    vehicleConfigDao = db.vehicleConfigDao(),
                    financialGoalDao = db.financialGoalDao(),
                    vehicleProfileDao = db.vehicleProfileDao(),
                    individualExpenseDao = db.individualExpenseDao(),
                    maintenanceRecordDao = db.maintenanceRecordDao(),
                    rideHistoryDao = appDb.rideHistoryDao(),
                    prefsManager = prefsManager,
                    onBack = { finish() }
                )
            }
        }
    }
    override fun onResume() {
        super.onResume()
        com.ngbautoroad.service.BubbleService.setAppInForeground(true)
    }
    override fun onPause() {
        super.onPause()
        com.ngbautoroad.service.BubbleService.setAppInForeground(false)
    }
}

@Composable
fun FinanceScreen(
    expenseDao: ExpenseDao,
    earningDao: EarningDao,
    reminderDao: ReminderDao,
    vehicleConfigDao: VehicleConfigDao,
    financialGoalDao: FinancialGoalDao,
    vehicleProfileDao: VehicleProfileDao? = null,
    individualExpenseDao: IndividualExpenseDao? = null,
    maintenanceRecordDao: com.ngbautoroad.data.db.MaintenanceRecordDao? = null,
    rideHistoryDao: RideHistoryDao? = null,
    prefsManager: com.ngbautoroad.data.prefs.PrefsManager? = null,
    onBack: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf("Resumo", "Ganhos", "Despesas", "Veículos", "Desp. Fixas", "Projeção", "Metas", "Manutenção")
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Controle Financeiro", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 8.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0 -> FinanceSummaryTab(expenseDao, earningDao, financialGoalDao, vehicleProfileDao = vehicleProfileDao)
                1 -> EarningsTab(earningDao, prefsManager, snackbarHostState)
                2 -> ExpensesTab(expenseDao, snackbarHostState)
                3 -> VehicleProfilesTab(vehicleProfileDao, snackbarHostState)
                4 -> IndividualExpensesTab(individualExpenseDao, vehicleProfileDao, snackbarHostState)
                5 -> ProjectionTab(earningDao, vehicleProfileDao, individualExpenseDao, rideHistoryDao)
                6 -> GoalsTab(earningDao, expenseDao, financialGoalDao, individualExpenseDao, snackbarHostState)
                7 -> if (maintenanceRecordDao != null) MaintenanceRecordsTab(maintenanceRecordDao, snackbarHostState)
            }
        }
    }
}

// === ABA RESUMO ===

@Composable
fun FinanceSummaryTab(expenseDao: ExpenseDao, earningDao: EarningDao, financialGoalDao: FinancialGoalDao, individualExpenseDao: IndividualExpenseDao? = null, vehicleProfileDao: VehicleProfileDao? = null) {
    var period by remember { mutableStateOf(FinancePeriod.TODAY) }

    val (startDate, endDate) = remember(period) { getPeriodRange(period) }

    val totalEarnings by earningDao.getTotalEarnings(startDate, endDate).collectAsState(initial = 0.0)
    val totalExpenses by expenseDao.getTotalExpenses(startDate, endDate).collectAsState(initial = 0.0)
    val totalDistance by earningDao.getTotalDistance(startDate, endDate).collectAsState(initial = 0.0)
    val totalDuration by earningDao.getTotalDuration(startDate, endDate).collectAsState(initial = 0)
    val totalRides by earningDao.getTotalRides(startDate, endDate).collectAsState(initial = 0)
    val activeGoals by financialGoalDao.getActiveGoals().collectAsState(initial = emptyList())
    // Despesas fixas rateadas (IPVA, seguro, parcela) — mensal
    val monthlyFixedCosts by (individualExpenseDao?.getTotalMonthlyRated() ?: kotlinx.coroutines.flow.flowOf(0.0)).collectAsState(initial = 0.0)

    // v6.10: Carregar veículo ativo para custos reais (item 9)
    val activeVehicle by (vehicleProfileDao?.getActiveVehicle() ?: kotlinx.coroutines.flow.flowOf(null)).collectAsState(initial = null)

    // Calcular ganhos por período para metas (igual ao GoalsTab)
    val (todayStart, todayEnd) = remember { getPeriodRange(FinancePeriod.TODAY) }
    val (weekStart, weekEnd) = remember { getPeriodRange(FinancePeriod.WEEK) }
    val (monthStart, monthEnd) = remember { getPeriodRange(FinancePeriod.MONTH) }
    val todayEarningsForGoals by earningDao.getTotalEarnings(todayStart, todayEnd).collectAsState(initial = 0.0)
    val weekEarningsForGoals by earningDao.getTotalEarnings(weekStart, weekEnd).collectAsState(initial = 0.0)
    val monthEarningsForGoals by earningDao.getTotalEarnings(monthStart, monthEnd).collectAsState(initial = 0.0)

    val earnings = totalEarnings ?: 0.0
    val expenses = totalExpenses ?: 0.0
    // Ratear custos fixos mensais proporcionalmente ao período selecionado
    val fixedCostsForPeriod = (monthlyFixedCosts ?: 0.0) * when (period) {
        FinancePeriod.TODAY -> 1.0 / 30.0
        FinancePeriod.WEEK -> 7.0 / 30.0
        FinancePeriod.MONTH -> 1.0
        FinancePeriod.YEAR -> 12.0
        FinancePeriod.ALL -> 12.0 // Aproximação
    }
    val netProfit = earnings - expenses - fixedCostsForPeriod
    val distance = totalDistance ?: 0.0
    val duration = totalDuration ?: 0
    val rides = totalRides ?: 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Period selector - single scrollable row
        Text("Período", fontSize = 12.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FinancePeriod.entries.forEach { p ->
                FilterChip(
                    selected = period == p,
                    onClick = { period = p },
                    label = { Text(p.displayName, fontSize = 11.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Lucro líquido
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (netProfit >= 0) ScoreGreen.copy(alpha = 0.1f) else ScoreRed.copy(alpha = 0.1f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Lucro Líquido", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "R$ %.2f".format(netProfit),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (netProfit >= 0) ScoreGreen else ScoreRed
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Ganhos vs Gastos
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FinanceInfoCard("Ganhos", "R$ %.2f".format(earnings), ScoreGreen, Modifier.weight(1f))
            FinanceInfoCard("Despesas", "R$ %.2f".format(expenses), ScoreRed, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Métricas
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FinanceInfoCard("Corridas", "$rides", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            FinanceInfoCard("Km", "%.1f".format(distance), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            FinanceInfoCard("Horas", "%.1f".format(duration / 60.0), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Indicadores por unidade
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val profitPerKm = if (distance > 0) netProfit / distance else 0.0
            val profitPerHour = if (duration > 0) (netProfit / duration) * 60.0 else 0.0
            val profitPerRide = if (rides > 0) netProfit / rides else 0.0

            FinanceInfoCard("R$/km", "%.2f".format(profitPerKm), if (profitPerKm >= 0) ScoreGreen else ScoreRed, Modifier.weight(1f))
            FinanceInfoCard("R$/h", "%.2f".format(profitPerHour), if (profitPerHour >= 0) ScoreGreen else ScoreRed, Modifier.weight(1f))
            FinanceInfoCard("R$/corrida", "%.2f".format(profitPerRide), if (profitPerRide >= 0) ScoreGreen else ScoreRed, Modifier.weight(1f))
        }

        // Metas ativas resumo
        if (activeGoals.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Metas Ativas", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            activeGoals.take(3).forEach { goal ->
                val goalCurrent = when (goal.period) {
                    "DIA" -> todayEarningsForGoals ?: 0.0
                    "SEMANA" -> weekEarningsForGoals ?: 0.0
                    "MES" -> monthEarningsForGoals ?: 0.0
                    else -> 0.0
                }
                val progress = if (goal.targetAmount > 0) (goalCurrent / goal.targetAmount).coerceIn(0.0, 1.0) else 0.0
                val progressColor = when {
                    progress >= 1.0 -> ScoreGreen
                    progress >= 0.7 -> ScoreYellow
                    progress >= 0.4 -> ScoreOrange
                    else -> ScoreRed
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(goal.title, fontSize = 12.sp)
                        LinearProgressIndicator(
                            progress = progress.toFloat(),
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = progressColor,
                            trackColor = progressColor.copy(alpha = 0.2f)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${(progress * 100).toInt()}%", fontSize = 12.sp, color = progressColor, fontWeight = FontWeight.Bold)
                }
            }
        }

        // === DRE SIMPLIFICADO ===
        Spacer(modifier = Modifier.height(16.dp))
        Text("DRE - Demonstração de Resultado", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Visão real do seu negócio", fontSize = 11.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))

        // DRE: custos do veículo ativo (ou fallback conservador) — hoisted for reuse in break-even
        val costPerKm = activeVehicle?.costPerKm?.takeIf { it > 0 } ?: 0.30
        val wearPerKm = activeVehicle?.let { v ->
            var total = 0.0
            if (v.tireLifeKm > 0 && v.tireCost > 0) total += v.tireCost / v.tireLifeKm
            if (v.brakepadLifeKm > 0 && v.brakepadCost > 0) total += v.brakepadCost / v.brakepadLifeKm
            if (v.oilChangeKm > 0 && v.oilChangeCost > 0) total += v.oilChangeCost / v.oilChangeKm
            if (v.maintenanceIntervalKm > 0 && v.maintenanceCost > 0) total += v.maintenanceCost / v.maintenanceIntervalKm
            if (total > 0) total else null
        } ?: 0.05

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val combustivelEstimado = distance * costPerKm
                val desgasteEstimado = distance * wearPerKm
                val custosVariaveis = combustivelEstimado + desgasteEstimado
                val margemContrib = earnings - custosVariaveis
                val lucroOp = margemContrib - fixedCostsForPeriod
                val margemPct = if (earnings > 0) (margemContrib / earnings * 100) else 0.0
                val lucroPct = if (earnings > 0) (lucroOp / earnings * 100) else 0.0

                DRERow("(+) Receita Bruta", earnings, Color.Unspecified)
                DRERow("(-) Combustível", -combustivelEstimado, ScoreRed)
                DRERow("(-) Desgaste", -desgasteEstimado, ScoreRed)
                DRERow("(=) Margem Contribuição", margemContrib, if (margemContrib >= 0) ScoreGreen else ScoreRed, bold = true, pct = margemPct)
                DRERow("(-) Custos Fixos", -fixedCostsForPeriod, ScoreOrange)
                DRERow("(=) Lucro Operacional", lucroOp, if (lucroOp >= 0) ScoreGreen else ScoreRed, bold = true, pct = lucroPct)
            }
        }

        // === BREAK-EVEN ===
        if (fixedCostsForPeriod > 0 && rides > 0) {
            Spacer(modifier = Modifier.height(12.dp))
            val avgPerRide = if (rides > 0) earnings / rides else 0.0
            val avgCostPerRide = if (rides > 0) (distance * (costPerKm + wearPerKm)) / rides else 0.0
            val contribPerRide = avgPerRide - avgCostPerRide
            val breakEvenRides = if (contribPerRide > 0) ((monthlyFixedCosts ?: 0.0) / contribPerRide).toInt() + 1 else 0

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Ponto de Equilíbrio (Break-Even)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Corridas para cobrir custos fixos: $breakEvenRides/mês", fontSize = 12.sp)
                    Text("Margem por corrida: R$ %.2f".format(contribPerRide), fontSize = 12.sp, color = Color.Gray)
                }
            }
        }
    }
}

// === ABA GANHOS ===

@Composable
fun EarningsTab(earningDao: EarningDao, prefsManager: com.ngbautoroad.data.prefs.PrefsManager? = null, snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }) {
    val scope = rememberCoroutineScope()
    // v6.10: Default to last 90 days for performance instead of loading ALL records
    val last90DaysMs = remember { System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000) }
    val allEarnings by earningDao.getEarningsByPeriod(last90DaysMs, Long.MAX_VALUE).collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var editingEarning by remember { mutableStateOf<EarningEntity?>(null) }
    val autoImportEnabled by (prefsManager?.autoImportEarningsFlow
        ?: kotlinx.coroutines.flow.flowOf(false)).collectAsState(initial = false)

    Column(modifier = Modifier.fillMaxSize()) {
        // Auto-import toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text("Lançamento automático", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("Registra corridas concluídas automaticamente", fontSize = 11.sp, color = Color.Gray)
            }
            Switch(
                checked = autoImportEnabled,
                onCheckedChange = { enabled ->
                    scope.launch { prefsManager?.setAutoImportEarnings(enabled) }
                }
            )
        }

        // Botão adicionar
        Button(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Ícone")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Registrar Ganho")
        }

        // Lista de ganhos
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(allEarnings, key = { it.id }) { earning ->
                EarningCard(
                    earning = earning,
                    onEdit = { editingEarning = earning },
                    onDelete = { scope.launch { earningDao.delete(earning); snackbarHostState.showSnackbar("Ganho excluído") } }
                )
            }
        }
    }

    if (showAddDialog) {
        AddEarningDialog(
            existingEarning = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { earning ->
                scope.launch {
                    earningDao.insert(earning)
                    showAddDialog = false
                    snackbarHostState.showSnackbar("Ganho salvo ✓")
                }
            }
        )
    }

    if (editingEarning != null) {
        AddEarningDialog(
            existingEarning = editingEarning,
            onDismiss = { editingEarning = null },
            onConfirm = { earning ->
                scope.launch {
                    earningDao.update(earning)
                    editingEarning = null
                }
            }
        )
    }
}

@Composable
fun EarningCard(earning: EarningEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(earning.platform, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (earning.period != "DIA") {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("(${earning.period})", fontSize = 10.sp, color = Color.Gray)
                    }
                    if (earning.isAutoImported) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("AUTO", fontSize = 9.sp, color = ScoreGreen)
                    }
                }
                Text(dateFormat.format(Date(earning.date)), fontSize = 11.sp, color = Color.Gray)
                if (earning.description.isNotBlank()) {
                    Text(earning.description, fontSize = 11.sp)
                }
                if (earning.ridesCount > 0 || earning.distance > 0) {
                    Text(
                        run {
                            val h = earning.duration / 60
                            val m = earning.duration % 60
                            val timeStr = if (h > 0) "${h}h${m.toString().padStart(2,'0')}" else "${m}min"
                            "${earning.ridesCount} corrida(s) • %.1f km • $timeStr".format(earning.distance)
                        },
                        fontSize = 11.sp, color = Color.Gray
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "R$ %.2f".format(earning.amount + earning.tips + earning.bonus),
                    fontWeight = FontWeight.Bold,
                    color = ScoreGreen
                )
                if (earning.tips > 0) {
                    Text("Gorjeta: R$ %.2f".format(earning.tips), fontSize = 10.sp, color = Color.Gray)
                }
            }
            Column {
                IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar", modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Excluir", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun AddEarningDialog(existingEarning: EarningEntity?, onDismiss: () -> Unit, onConfirm: (EarningEntity) -> Unit) {
    val isEditing = existingEarning != null
    var platform by remember { mutableStateOf(existingEarning?.platform ?: "Uber") }
    val e = existingEarning
    var amount by remember { mutableStateOf(if (e != null) "%.2f".format(e.amount) else "") }
    var tips by remember { mutableStateOf(if (e != null && e.tips > 0) "%.2f".format(e.tips) else "") }
    var bonus by remember { mutableStateOf(if (e != null && e.bonus > 0) "%.2f".format(e.bonus) else "") }
    var distance by remember { mutableStateOf(if (e != null && e.distance > 0) "%.1f".format(e.distance) else "") }
    var duration by remember {
        mutableStateOf(
            if (e != null && e.duration > 0) {
                val h = e.duration / 60
                val m = e.duration % 60
                if (h > 0) "$h:${m.toString().padStart(2, '0')}" else "0:${m.toString().padStart(2, '0')}"
            } else ""
        )
    }
    var rides by remember { mutableStateOf(if (e != null && e.ridesCount > 0) "${e.ridesCount}" else "") }
    var description by remember { mutableStateOf(existingEarning?.description ?: "") }
    var period by remember { mutableStateOf(existingEarning?.period ?: "DIA") }

    val platforms = listOf("Uber", "99", "inDrive", "Cabify", "Outros")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Editar Ganho" else "Registrar Ganho") },
        text = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                // Platform selector
                Text("Plataforma", fontSize = 12.sp, color = Color.Gray)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    platforms.forEach { p ->
                        FilterChip(
                            selected = platform == p,
                            onClick = { platform = p },
                            label = { Text(p, fontSize = 10.sp) }
                        )
                    }
                }

                // Período
                Text("Período do ganho", fontSize = 12.sp, color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("DIA", "SEMANA", "MES").forEach { p ->
                        FilterChip(
                            selected = period == p,
                            onClick = { period = p },
                            label = { Text(when(p) { "DIA" -> "Dia"; "SEMANA" -> "Semana"; else -> "Mês" }, fontSize = 10.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = amount, onValueChange = { amount = it },
                    label = { Text("Valor (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(
                    value = tips, onValueChange = { tips = it },
                    label = { Text("Gorjetas (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(
                    value = bonus, onValueChange = { bonus = it },
                    label = { Text("Bônus (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = distance, onValueChange = { distance = it },
                        label = { Text("Km") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f), singleLine = true
                    )
                    OutlinedTextField(
                        value = duration,
                        onValueChange = { raw ->
                            // Aceita dígitos e ':', auto-formata para H:mm
                            val digits = raw.filter { it.isDigit() }
                            duration = when {
                                digits.length <= 2 -> digits
                                else -> {
                                    val h = digits.dropLast(2).trimStart('0').ifEmpty { "0" }
                                    val m = digits.takeLast(2)
                                    "$h:$m"
                                }
                            }
                        },
                        label = { Text("Horas:min", fontSize = 11.sp, maxLines = 1) },
                        placeholder = { Text("0:00") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f), singleLine = true
                    )
                    OutlinedTextField(
                        value = rides, onValueChange = { rides = it },
                        label = { Text("Corridas", fontSize = 11.sp, maxLines = 1) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f), singleLine = true
                    )
                }
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("Observação") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
            }
          }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsedAmount = amount.toDoubleLocale()
                    if (parsedAmount <= 0.0) return@TextButton // Validação: valor obrigatório e positivo
                    val entity = EarningEntity(
                        id = existingEarning?.id ?: 0,
                        platform = platform,
                        amount = parsedAmount,
                        tips = tips.toDoubleLocale(),
                        bonus = bonus.toDoubleLocale(),
                        distance = distance.toDoubleLocale(),
                        duration = run {
                            val raw = duration.trim()
                            if (raw.contains(':')) {
                                val parts = raw.split(':')
                                val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
                                val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                                h * 60 + m
                            } else {
                                raw.toIntOrNull() ?: 0
                            }
                        },
                        ridesCount = rides.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0,
                        description = description.trim(),
                        period = period,
                        date = existingEarning?.date ?: System.currentTimeMillis(),
                        isAutoImported = existingEarning?.isAutoImported ?: false
                    )
                    onConfirm(entity)
                },
                enabled = amount.toDoubleLocale() > 0.0
            ) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

// === ABA GASTOS ===

@Composable
fun ExpensesTab(expenseDao: ExpenseDao, snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }) {
    val scope = rememberCoroutineScope()
    // v6.10: Default to last 90 days for performance instead of loading ALL records
    val last90DaysMs = remember { System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000) }
    val allExpenses by expenseDao.getExpensesByPeriod(last90DaysMs, Long.MAX_VALUE).collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<ExpenseEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Button(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Ícone")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Registrar Despesa")
        }

        // Resumo por categoria
        val totalByCategory = allExpenses.groupBy { it.category }
            .mapValues { (_, v) -> v.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }

        if (totalByCategory.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Despesas por Categoria", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    totalByCategory.take(5).forEach { (cat, total) ->
                        val catEnum = try { ExpenseCategory.valueOf(cat) } catch (e: Exception) { null }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(catEnum?.let { "${it.icon} ${it.displayName}" } ?: cat, fontSize = 12.sp)
                            Text("R$ %.2f".format(total), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Lista de gastos
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(allExpenses, key = { it.id }) { expense ->
                ExpenseCard(
                    expense = expense,
                    onEdit = {
                        editingExpense = expense
                        showEditDialog = true
                    },
                    onDelete = {
                        scope.launch { expenseDao.delete(expense); snackbarHostState.showSnackbar("Despesa excluída") }
                    }
                )
            }
        }
    }

    if (showAddDialog) {
        AddExpenseDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { expense ->
                scope.launch {
                    expenseDao.insert(expense)
                    snackbarHostState.showSnackbar("Despesa salva \u2713")
                    showAddDialog = false
                }
            }
        )
    }

    // Dialog de edição de gasto
    if (showEditDialog && editingExpense != null) {
        EditExpenseDialog(
            expense = editingExpense ?: return,
            onDismiss = { showEditDialog = false; editingExpense = null },
            onConfirm = { updated ->
                scope.launch {
                    expenseDao.update(updated)
                    snackbarHostState.showSnackbar("Despesa atualizada \u2713")
                    showEditDialog = false
                    editingExpense = null
                }
            }
        )
    }
}

@Composable
fun ExpenseCard(expense: ExpenseEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val category = try { ExpenseCategory.valueOf(expense.category) } catch (e: Exception) { ExpenseCategory.OTHER }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${category.icon} ${category.displayName}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(dateFormat.format(Date(expense.date)), fontSize = 11.sp, color = Color.Gray)
                if (expense.description.isNotBlank()) {
                    Text(expense.description, fontSize = 11.sp)
                }
                if (expense.liters != null) {
                    Text("${expense.liters}L × R$${expense.pricePerLiter}/L", fontSize = 11.sp, color = Color.Gray)
                }
                if (expense.isRecurring) {
                    val daysText = if (expense.recurringDays.isNotBlank()) {
                        val dayNames = mapOf("1" to "Seg", "2" to "Ter", "3" to "Qua", "4" to "Qui", "5" to "Sex", "6" to "Sáb", "7" to "Dom")
                        expense.recurringDays.split(",").mapNotNull { dayNames[it.trim()] }.joinToString(", ")
                    } else "Dia ${expense.recurringDay}"
                    val durationText = if (expense.recurringDuration > 0) " (${expense.recurringDuration} dias)" else ""
                    Text("🔄 Recorrente: $daysText$durationText", fontSize = 10.sp, color = ScoreYellow)
                }
            }
            Text(
                "- R$ %.2f".format(expense.amount),
                fontWeight = FontWeight.Bold,
                color = ScoreRed
            )
            IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Editar", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Excluir", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun AddExpenseDialog(onDismiss: () -> Unit, onConfirm: (ExpenseEntity) -> Unit, existingExpense: ExpenseEntity? = null) {
    val existingCat = existingExpense?.let { try { ExpenseCategory.valueOf(it.category) } catch (_: Exception) { ExpenseCategory.FUEL } }
    var selectedCategory by remember { mutableStateOf(existingCat ?: ExpenseCategory.FUEL) }
    var amount by remember { mutableStateOf(existingExpense?.amount?.let { "%.2f".format(it) } ?: "") }
    var description by remember { mutableStateOf(existingExpense?.description ?: "") }
    var isRecurring by remember { mutableStateOf(existingExpense?.isRecurring ?: false) }
    var recurringDay by remember { mutableStateOf(existingExpense?.recurringDay?.toString() ?: "1") }
    var recurringDuration by remember { mutableStateOf(existingExpense?.recurringDuration?.let { if (it > 0) it.toString() else "" } ?: "") }
    var selectedDays by remember { mutableStateOf(existingExpense?.recurringDays?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.toSet() ?: setOf<Int>()) }
    var liters by remember { mutableStateOf(existingExpense?.liters?.let { "%.1f".format(it) } ?: "") }
    var pricePerLiter by remember { mutableStateOf(existingExpense?.pricePerLiter?.let { "%.2f".format(it) } ?: "") }
    var showCategoryPicker by remember { mutableStateOf(false) }

    val weekDays = listOf("Seg" to 1, "Ter" to 2, "Qua" to 3, "Qui" to 4, "Sex" to 5, "Sáb" to 6, "Dom" to 7)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingExpense != null) "Editar Despesa" else "Registrar Despesa") },
        text = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                // Category selector
                OutlinedButton(
                    onClick = { showCategoryPicker = !showCategoryPicker },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("${selectedCategory.icon} ${selectedCategory.displayName}")
                }

                if (showCategoryPicker) {
                    Card(modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)) {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(8.dp)) {
                            ExpenseCategoryGroup.values().forEach { group ->
                                Text(group.displayName, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                                    modifier = Modifier.padding(vertical = 4.dp))
                                group.categories.forEach { cat ->
                                    Text(
                                        "${cat.icon} ${cat.displayName}",
                                        fontSize = 12.sp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedCategory = cat
                                                showCategoryPicker = false
                                            }
                                            .padding(vertical = 4.dp, horizontal = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = amount, onValueChange = { amount = it },
                    label = { Text("Valor (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )

                // Campos extras para combustível
                if (selectedCategory == ExpenseCategory.FUEL) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = liters, onValueChange = { liters = it },
                            label = { Text("Litros") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f), singleLine = true
                        )
                        OutlinedTextField(
                            value = pricePerLiter, onValueChange = { pricePerLiter = it },
                            label = { Text("R$/Litro") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f), singleLine = true
                        )
                    }
                }

                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("Descrição") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )

                // Recorrência
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isRecurring, onCheckedChange = { isRecurring = it })
                    Text("Despesa recorrente", fontSize = 13.sp)
                }

                if (isRecurring) {
                    // Dias da semana - visual calendário
                    Text("Dias que se repete:", fontSize = 12.sp, color = Color.Gray)
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        weekDays.forEach { (name, day) ->
                            val isSelected = selectedDays.contains(day)
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable {
                                        selectedDays = if (isSelected)
                                            selectedDays - day else selectedDays + day
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    name.take(1),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Duração
                    OutlinedTextField(
                        value = recurringDuration, onValueChange = { recurringDuration = it },
                        label = { Text("Por quantos dias? (vazio = indefinido)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                }
            }
          }
        },
        confirmButton = {
            val parsedExpenseAmount = amount.toDoubleLocale().let { if (it > 0) it else {
                val l = liters.toDoubleLocale()
                val p = pricePerLiter.toDoubleLocale()
                l * p
            }}
            TextButton(
                onClick = {
                    if (parsedExpenseAmount <= 0.0) return@TextButton // Validação: valor obrigatório
                    onConfirm(ExpenseEntity(
                        id = existingExpense?.id ?: 0,
                        category = selectedCategory.name,
                        amount = parsedExpenseAmount,
                        description = description.trim(),
                        isRecurring = isRecurring,
                        recurringDay = recurringDay.trim().toIntOrNull()?.coerceIn(1, 31) ?: 1,
                        recurringDays = selectedDays.sorted().joinToString(","),
                        recurringDuration = recurringDuration.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0,
                        liters = liters.toDoubleLocaleOrNull()?.coerceAtLeast(0.0),
                        pricePerLiter = pricePerLiter.toDoubleLocaleOrNull()?.coerceAtLeast(0.0),
                        date = existingExpense?.date ?: System.currentTimeMillis()
                    ))
                },
                enabled = parsedExpenseAmount > 0.0
            ) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

// === ABA VEÍCULO ===

@Composable
fun VehicleTab(vehicleConfigDao: VehicleConfigDao, snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }) {
    val scope = rememberCoroutineScope()
    val savedConfig by vehicleConfigDao.getConfig().collectAsState(initial = null)

    // rememberSaveable garante que os dados sobrevivem à troca de aba
    var vehicleType by rememberSaveable { mutableStateOf("COMBUSTION") }
    var fuelType by rememberSaveable { mutableStateOf("FLEX") }
    var consumption by rememberSaveable { mutableStateOf("") }
    var fuelPrice by rememberSaveable { mutableStateOf("") }
    var brand by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var year by rememberSaveable { mutableStateOf("") }
    var plate by rememberSaveable { mutableStateOf("") }
    var monthlyFixed by rememberSaveable { mutableStateOf("") }
    var isOwned by rememberSaveable { mutableStateOf(true) }
    var rentalCost by rememberSaveable { mutableStateOf("") }
    var loaded by rememberSaveable { mutableStateOf(false) }

    // LaunchedEffect por id: dispara apenas quando o registro é carregado pela primeira vez
    LaunchedEffect(savedConfig?.id) {
        val cfg = savedConfig ?: return@LaunchedEffect
        if (!loaded) {
            vehicleType = cfg.vehicleType
            fuelType = cfg.fuelType
            brand = cfg.brand
            model = cfg.model
            year = if (cfg.year > 0) "${cfg.year}" else ""
            plate = cfg.plate
            consumption = if (cfg.averageConsumption > 0) "%.1f".format(cfg.averageConsumption) else ""
            fuelPrice = if (cfg.fuelPrice > 0) "%.2f".format(cfg.fuelPrice) else ""
            monthlyFixed = if (cfg.monthlyFixedCosts > 0) "%.2f".format(cfg.monthlyFixedCosts) else ""
            isOwned = cfg.isOwned
            rentalCost = if (cfg.rentalCost > 0) "%.2f".format(cfg.rentalCost) else ""
            loaded = true
        }
    }

    // Usa extensão global com.ngbautoroad.util.toDoubleLocale()

    val avgConsumption = consumption.toDoubleLocale()
    val price = fuelPrice.toDoubleLocale()
    // Fórmula: R$/L ÷ km/L = R$/km
    val costPerKm = if (avgConsumption > 0.0) price / avgConsumption else 0.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Configuração do Veículo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        // Tipo de veículo
        Text("Tipo de Veículo", fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("COMBUSTION" to "Combustão", "HYBRID" to "Híbrido", "ELECTRIC" to "Elétrico").forEach { (type, name) ->
                FilterChip(
                    selected = vehicleType == type,
                    onClick = { vehicleType = type },
                    label = { Text(name, fontSize = 11.sp) }
                )
            }
        }

        // Tipo de combustível
        if (vehicleType != "ELECTRIC") {
            Text("Combustível", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                val fuels = when (vehicleType) {
                    "COMBUSTION" -> listOf("GASOLINE" to "Gasolina", "ETHANOL" to "Etanol", "FLEX" to "Flex", "DIESEL" to "Diesel", "GNV" to "GNV")
                    "HYBRID" -> listOf("HYBRID_GAS" to "Híb. Gasolina", "HYBRID_ETHANOL" to "Híb. Etanol")
                    else -> emptyList()
                }
                fuels.forEach { (ft, name) ->
                    FilterChip(
                        selected = fuelType == ft,
                        onClick = { fuelType = ft },
                        label = { Text(name, fontSize = 10.sp) }
                    )
                }
            }
        }

        // Dados do veículo
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = brand, onValueChange = { brand = it },
                label = { Text("Marca") },
                modifier = Modifier.weight(1f), singleLine = true
            )
            OutlinedTextField(
                value = model, onValueChange = { model = it },
                label = { Text("Modelo") },
                modifier = Modifier.weight(1f), singleLine = true
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = year, onValueChange = { if (it.length <= 4) year = it.filter { c -> c.isDigit() } },
                label = { Text("Ano") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f), singleLine = true,
                isError = year.isNotBlank() && (year.toIntOrNull() ?: 0) !in 1990..2030
            )
            OutlinedTextField(
                value = plate, onValueChange = { plate = it },
                label = { Text("Placa") },
                modifier = Modifier.weight(1f), singleLine = true
            )
        }

        // Consumo e preço
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = consumption, onValueChange = { consumption = it },
                label = { Text(if (vehicleType == "ELECTRIC") "km/kWh" else "km/L") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f), singleLine = true,
                isError = consumption.isNotBlank() && consumption.toDoubleLocale() <= 0.0
            )
            OutlinedTextField(
                value = fuelPrice, onValueChange = { fuelPrice = it },
                label = { Text(if (vehicleType == "ELECTRIC") "R$/kWh" else "R$/L") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f), singleLine = true
            )
        }

        // Propriedade
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = isOwned, onCheckedChange = { isOwned = it })
            Text("Veículo próprio", fontSize = 13.sp)
        }

        if (!isOwned) {
            OutlinedTextField(
                value = rentalCost, onValueChange = { rentalCost = it },
                label = { Text("Custo aluguel mensal (R$)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
        }

        OutlinedTextField(
            value = monthlyFixed, onValueChange = { monthlyFixed = it },
            label = { Text("Custos fixos mensais (parcela+seguro+IPVA) R$") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )

        // Resultado
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Custo por km calculado:", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "R$ %.4f/km".format(costPerKm),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                val monthly = monthlyFixed.toDoubleLocale()
                val rental = rentalCost.toDoubleLocale()
                if (monthly > 0 || rental > 0) {
                    Text(
                        "Custo fixo diário: R$ %.2f".format((monthly + rental) / 30),
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Botão Salvar
        Button(
            onClick = {
                scope.launch {
                    vehicleConfigDao.save(VehicleConfigEntity(
                        vehicleType = vehicleType,
                        fuelType = fuelType,
                        brand = brand,
                        model = model,
                        year = year.replace(",",".").trim().toDoubleOrNull()?.toInt() ?: year.toIntOrNull() ?: 0,
                        plate = plate,
                        averageConsumption = avgConsumption,
                        fuelPrice = price,
                        costPerKm = costPerKm,
                        monthlyFixedCosts = monthlyFixed.replace(",",".").trim().toDoubleOrNull() ?: 0.0,
                        isOwned = isOwned,
                        rentalCost = rentalCost.replace(",",".").trim().toDoubleOrNull() ?: 0.0
                    ))
                    snackbarHostState.showSnackbar("Veículo salvo ✓")
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Check, contentDescription = "Ícone")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Salvar Configuração")
        }
    }
}

// === ABA METAS ===

@Composable
fun GoalsTab(earningDao: EarningDao, expenseDao: ExpenseDao, financialGoalDao: FinancialGoalDao, individualExpenseDao: IndividualExpenseDao? = null, snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }) {
    val scope = rememberCoroutineScope()
    val activeGoals by financialGoalDao.getActiveGoals().collectAsState(initial = emptyList())
    var showAddGoal by remember { mutableStateOf(false) }

    val (todayStart, todayEnd) = remember { getPeriodRange(FinancePeriod.TODAY) }
    val (weekStart, weekEnd) = remember { getPeriodRange(FinancePeriod.WEEK) }
    val (monthStart, monthEnd) = remember { getPeriodRange(FinancePeriod.MONTH) }

    val todayEarnings by earningDao.getTotalEarnings(todayStart, todayEnd).collectAsState(initial = 0.0)
    val weekEarnings by earningDao.getTotalEarnings(weekStart, weekEnd).collectAsState(initial = 0.0)
    val monthEarnings by earningDao.getTotalEarnings(monthStart, monthEnd).collectAsState(initial = 0.0)

    // Despesas por período (para metas tipo LÍQUIDO)
    val todayExpenses by expenseDao.getTotalExpenses(todayStart, todayEnd).collectAsState(initial = 0.0)
    val weekExpenses by expenseDao.getTotalExpenses(weekStart, weekEnd).collectAsState(initial = 0.0)
    val monthExpenses by expenseDao.getTotalExpenses(monthStart, monthEnd).collectAsState(initial = 0.0)
    // Despesas fixas rateadas
    val monthlyFixedCosts by (individualExpenseDao?.getTotalMonthlyRated() ?: kotlinx.coroutines.flow.flowOf(0.0)).collectAsState(initial = 0.0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Metas Financeiras", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = { showAddGoal = true }) {
                Icon(Icons.Default.Add, contentDescription = "Adicionar meta")
            }
        }

        // Metas salvas no DB
        if (activeGoals.isEmpty()) {
            Text("Nenhuma meta cadastrada. Toque + para adicionar.", fontSize = 13.sp, color = Color.Gray)
        }

        activeGoals.forEach { goal ->
            val fixedMonthly = monthlyFixedCosts ?: 0.0
            val current = if (goal.goalType == "LIQUIDO") {
                // Lucro líquido = ganhos - despesas variáveis - fixos rateados
                when (goal.period) {
                    "DIA" -> (todayEarnings ?: 0.0) - (todayExpenses ?: 0.0) - (fixedMonthly / 30.0)
                    "SEMANA" -> (weekEarnings ?: 0.0) - (weekExpenses ?: 0.0) - (fixedMonthly * 7.0 / 30.0)
                    "MES" -> (monthEarnings ?: 0.0) - (monthExpenses ?: 0.0) - fixedMonthly
                    else -> 0.0
                }
            } else {
                // Ganho bruto
                when (goal.period) {
                    "DIA" -> todayEarnings ?: 0.0
                    "SEMANA" -> weekEarnings ?: 0.0
                    "MES" -> monthEarnings ?: 0.0
                    else -> 0.0
                }
            }
            val progress = if (goal.targetAmount > 0) (current / goal.targetAmount).coerceIn(0.0, 1.5) else 0.0
            val progressColor = when {
                progress >= 1.0 -> ScoreGreen
                progress >= 0.7 -> ScoreYellow
                progress >= 0.4 -> ScoreOrange
                else -> ScoreRed
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(goal.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            val typeLabel = if (goal.goalType == "LIQUIDO") "Líquido" else "Bruto"
                            Text("Meta: R$ %.2f (${goal.period}) • $typeLabel".format(goal.targetAmount), fontSize = 11.sp, color = Color.Gray)
                        }
                        Text(
                            "${(progress * 100).toInt().coerceAtMost(100)}%",
                            color = progressColor,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { scope.launch { financialGoalDao.delete(goal); snackbarHostState.showSnackbar("Meta excluída") } },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Excluir", modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = progress.toFloat().coerceAtMost(1f),
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = progressColor,
                        trackColor = progressColor.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Atual: R$ %.2f".format(current), fontSize = 12.sp)
                }
            }
        }
    }

    if (showAddGoal) {
        AddGoalDialog(
            onDismiss = { showAddGoal = false },
            onConfirm = { goal ->
                scope.launch {
                    try {
                        financialGoalDao.insert(goal)
                        showAddGoal = false
                        snackbarHostState.showSnackbar("Meta criada ✓")
                    } catch (e: Exception) {
                        android.util.Log.e("FinanceActivity", "Erro ao salvar meta", e)
                        showAddGoal = false
                        snackbarHostState.showSnackbar("Erro ao salvar meta: ${e.localizedMessage ?: e.message}")
                    }
                }
            }
        )
    }
}

@Composable
fun AddGoalDialog(onDismiss: () -> Unit, onConfirm: (FinancialGoalEntity) -> Unit) {
    var title by remember { mutableStateOf("") }
    var targetAmount by remember { mutableStateOf("") }
    var period by remember { mutableStateOf("MES") }
    var goalType by remember { mutableStateOf("BRUTO") }
    // BUGFIX: evita múltiplos inserts ao clicar rapidamente em Salvar
    var isSaving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Nova Meta") },
        text = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Nome da meta") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(
                    value = targetAmount, onValueChange = { targetAmount = it },
                    label = { Text("Valor alvo (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Text("Período", fontSize = 12.sp, color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("DIA" to "Diária", "SEMANA" to "Semanal", "MES" to "Mensal").forEach { (p, name) ->
                        FilterChip(
                            selected = period == p,
                            onClick = { period = p },
                            label = { Text(name, fontSize = 11.sp) }
                        )
                    }
                }
                Text("Tipo de Meta", fontSize = 12.sp, color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = goalType == "BRUTO",
                        onClick = { goalType = "BRUTO" },
                        label = { Text("Ganho Bruto", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = goalType == "LIQUIDO",
                        onClick = { goalType = "LIQUIDO" },
                        label = { Text("Lucro Líquido", fontSize = 11.sp) }
                    )
                }
                if (goalType == "LIQUIDO") {
                    Text("Desconta despesas variáveis + custos fixos rateados", fontSize = 10.sp, color = Color.Gray)
                }
            }
          }
        },
        confirmButton = {
            val parsedTarget = targetAmount.toDoubleLocale()
            val canSave = title.isNotBlank() && parsedTarget > 0.0 && !isSaving
            TextButton(
                onClick = {
                    if (canSave) {
                        isSaving = true // Bloqueia cliques adicionais imediatamente
                        onConfirm(FinancialGoalEntity(
                            title = title.trim(),
                            targetAmount = parsedTarget,
                            period = period,
                            goalType = goalType
                        ))
                    }
                },
                enabled = canSave
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Salvar")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancelar") }
        }
    )
}

// === COMPONENTES AUXILIARES ===

@Composable
fun FinanceInfoCard(title: String, value: String, color: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontSize = 11.sp, color = Color.Gray)
            Text(value, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = color)
        }
    }
}

// === UTILITÁRIOS ===

fun getPeriodRange(period: FinancePeriod): Pair<Long, Long> {
    val cal = Calendar.getInstance()
    val end = cal.timeInMillis

    when (period) {
        FinancePeriod.TODAY -> {
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
        }
        FinancePeriod.WEEK -> {
            // Sempre começar na segunda-feira (padrão brasileiro)
            cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            // Se hoje for domingo e Monday ficou no futuro, voltar 7 dias
            if (cal.timeInMillis > end) {
                cal.add(Calendar.DAY_OF_YEAR, -7)
            }
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
        }
        FinancePeriod.MONTH -> {
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
        }
        FinancePeriod.YEAR -> {
            cal.set(Calendar.DAY_OF_YEAR, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
        }
        FinancePeriod.ALL -> {
            cal.timeInMillis = 0
        }
    }

    return Pair(cal.timeInMillis, end)
}

@Composable
fun EditExpenseDialog(expense: ExpenseEntity, onDismiss: () -> Unit, onConfirm: (ExpenseEntity) -> Unit) {
    AddExpenseDialog(onDismiss = onDismiss, onConfirm = onConfirm, existingExpense = expense)
}


@Composable
fun DRERow(label: String, value: Double, color: Color, bold: Boolean = false, pct: Double = -1.0) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "R$ %.2f".format(value),
                fontSize = 12.sp,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                color = if (color != Color.Unspecified) color else Color.Unspecified
            )
            if (pct >= 0) {
                Text(
                    " (%.0f%%)".format(pct),
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }
        }
    }
}


// === ABA DE MANUTENÇÕES REAIS (Sprint 3) ===

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceRecordsTab(
    maintenanceRecordDao: com.ngbautoroad.data.db.MaintenanceRecordDao,
    snackbarHostState: SnackbarHostState
) {
    val coroutineScope = rememberCoroutineScope()
    val records by maintenanceRecordDao.getAllRecords().collectAsState(initial = emptyList())

    var showDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = { showDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Registrar Manutenção Real")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (records.isEmpty()) {
            Text("Nenhum registro de manutenção real.", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(records) { record ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(record.maintenanceType, fontWeight = FontWeight.Bold)
                                Text("R$ ${"%.2f".format(record.totalCost)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Text("Peças: ${record.replacedParts}", style = MaterialTheme.typography.bodyMedium)
                            Text("Odômetro: ${record.odometer} km", style = MaterialTheme.typography.bodyMedium)
                            if (record.notes.isNotEmpty()) {
                                Text("Nota: ${record.notes}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        maintenanceRecordDao.delete(record)
                                        snackbarHostState.showSnackbar("Registro removido")
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Remover")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        var maintenanceType by remember { mutableStateOf("PREVENTIVA") }
        var totalCost by remember { mutableStateOf("") }
        var replacedParts by remember { mutableStateOf("") }
        var odometer by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Nova Manutenção Real") },
            text = {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = maintenanceType == "PREVENTIVA", onClick = { maintenanceType = "PREVENTIVA" })
                        Text("Preventiva", modifier = Modifier.padding(end = 8.dp))
                        RadioButton(selected = maintenanceType == "CORRETIVA", onClick = { maintenanceType = "CORRETIVA" })
                        Text("Corretiva")
                    }
                    OutlinedTextField(
                        value = replacedParts,
                        onValueChange = { replacedParts = it },
                        label = { Text("Peças/Serviço (ex: Troca de pneus)") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                    OutlinedTextField(
                        value = totalCost,
                        onValueChange = { totalCost = it },
                        label = { Text("Custo Total (R$)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                    OutlinedTextField(
                        value = odometer,
                        onValueChange = { odometer = it },
                        label = { Text("Odômetro atual (km)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Observações (opcional)") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                }
              }
            },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch {
                        val cost = totalCost.toDoubleLocaleOrNull() ?: 0.0
                        val odo = odometer.toIntOrNull() ?: 0
                        if (replacedParts.isNotBlank() && cost > 0.0) {
                            maintenanceRecordDao.insert(
                                com.ngbautoroad.data.db.MaintenanceRecordEntity(
                                    maintenanceType = maintenanceType,
                                    replacedParts = replacedParts,
                                    totalCost = cost,
                                    odometer = odo,
                                    notes = notes
                                )
                            )
                            snackbarHostState.showSnackbar("Manutenção salva!")
                            showDialog = false
                        } else {
                            snackbarHostState.showSnackbar("Preencha peça e valor válido.")
                        }
                    }
                }) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

