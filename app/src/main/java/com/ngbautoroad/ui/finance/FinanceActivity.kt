@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.ngbautoroad.ui.finance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngbautoroad.data.db.*
import com.ngbautoroad.data.model.*
import com.ngbautoroad.ui.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class FinanceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = FinanceDatabase.getInstance(applicationContext)
        setContent {
            NGBAutoRoadTheme {
                FinanceScreen(
                    expenseDao = db.expenseDao(),
                    earningDao = db.earningDao(),
                    reminderDao = db.reminderDao(),
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceScreen(
    expenseDao: ExpenseDao,
    earningDao: EarningDao,
    reminderDao: ReminderDao,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Resumo", "Ganhos", "Gastos", "Veículo", "Metas")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Controle Financeiro", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Tabs
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
                0 -> FinanceSummaryTab(expenseDao, earningDao)
                1 -> EarningsTab(earningDao)
                2 -> ExpensesTab(expenseDao)
                3 -> VehicleTab()
                4 -> GoalsTab(earningDao, expenseDao)
            }
        }
    }
}

// === ABA RESUMO ===

@Composable
fun FinanceSummaryTab(expenseDao: ExpenseDao, earningDao: EarningDao) {
    val scope = rememberCoroutineScope()
    var period by remember { mutableStateOf(FinancePeriod.TODAY) }

    val (startDate, endDate) = remember(period) { getPeriodRange(period) }

    val totalEarnings by earningDao.getTotalEarnings(startDate, endDate).collectAsState(initial = 0.0)
    val totalExpenses by expenseDao.getTotalExpenses(startDate, endDate).collectAsState(initial = 0.0)
    val totalDistance by earningDao.getTotalDistance(startDate, endDate).collectAsState(initial = 0.0)
    val totalDuration by earningDao.getTotalDuration(startDate, endDate).collectAsState(initial = 0)
    val totalRides by earningDao.getTotalRides(startDate, endDate).collectAsState(initial = 0)

    val earnings = totalEarnings ?: 0.0
    val expenses = totalExpenses ?: 0.0
    val netProfit = earnings - expenses
    val distance = totalDistance ?: 0.0
    val duration = totalDuration ?: 0
    val rides = totalRides ?: 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Period selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FinancePeriod.values().forEach { p ->
                FilterChip(
                    selected = period == p,
                    onClick = { period = p },
                    label = { Text(p.displayName, fontSize = 11.sp) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Lucro líquido grande
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
            FinanceInfoCard(
                title = "Ganhos",
                value = "R$ %.2f".format(earnings),
                color = ScoreGreen,
                modifier = Modifier.weight(1f)
            )
            FinanceInfoCard(
                title = "Gastos",
                value = "R$ %.2f".format(expenses),
                color = ScoreRed,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Métricas
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FinanceInfoCard(
                title = "Corridas",
                value = "$rides",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            FinanceInfoCard(
                title = "Km Rodados",
                value = "%.1f".format(distance),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            FinanceInfoCard(
                title = "Horas",
                value = "%.1f".format(duration / 60.0),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
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

            FinanceInfoCard(
                title = "R$/km",
                value = "%.2f".format(profitPerKm),
                color = if (profitPerKm >= 0) ScoreGreen else ScoreRed,
                modifier = Modifier.weight(1f)
            )
            FinanceInfoCard(
                title = "R$/hora",
                value = "%.2f".format(profitPerHour),
                color = if (profitPerHour >= 0) ScoreGreen else ScoreRed,
                modifier = Modifier.weight(1f)
            )
            FinanceInfoCard(
                title = "R$/corrida",
                value = "%.2f".format(profitPerRide),
                color = if (profitPerRide >= 0) ScoreGreen else ScoreRed,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// === ABA GANHOS ===

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EarningsTab(earningDao: EarningDao) {
    val scope = rememberCoroutineScope()
    val allEarnings by earningDao.getAllEarnings().collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Botão adicionar
        Button(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Registrar Ganho")
        }

        // Lista de ganhos
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(allEarnings) { earning ->
                EarningCard(earning) {
                    scope.launch { earningDao.delete(earning) }
                }
            }
        }
    }

    if (showAddDialog) {
        AddEarningDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { earning ->
                scope.launch {
                    earningDao.insert(earning)
                    showAddDialog = false
                }
            }
        )
    }
}

@Composable
fun EarningCard(earning: EarningEntity, onDelete: () -> Unit) {
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
                Text(earning.platform, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(dateFormat.format(Date(earning.date)), fontSize = 11.sp, color = Color.Gray)
                if (earning.description.isNotBlank()) {
                    Text(earning.description, fontSize = 11.sp)
                }
                Text(
                    "${earning.ridesCount} corrida(s) • %.1f km • ${earning.duration} min".format(earning.distance),
                    fontSize = 11.sp, color = Color.Gray
                )
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
            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Excluir", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun AddEarningDialog(onDismiss: () -> Unit, onConfirm: (EarningEntity) -> Unit) {
    var platform by remember { mutableStateOf("Uber") }
    var amount by remember { mutableStateOf("") }
    var tips by remember { mutableStateOf("") }
    var bonus by remember { mutableStateOf("") }
    var distance by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }
    var rides by remember { mutableStateOf("1") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar Ganho") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Platform selector
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Uber", "99", "inDrive", "Cabify").forEach { p ->
                        FilterChip(
                            selected = platform == p,
                            onClick = { platform = p },
                            label = { Text(p, fontSize = 10.sp) }
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
                        value = duration, onValueChange = { duration = it },
                        label = { Text("Min") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f), singleLine = true
                    )
                    OutlinedTextField(
                        value = rides, onValueChange = { rides = it },
                        label = { Text("Corridas") },
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
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(EarningEntity(
                    platform = platform,
                    amount = amount.toDoubleOrNull() ?: 0.0,
                    tips = tips.toDoubleOrNull() ?: 0.0,
                    bonus = bonus.toDoubleOrNull() ?: 0.0,
                    distance = distance.toDoubleOrNull() ?: 0.0,
                    duration = duration.toIntOrNull() ?: 0,
                    ridesCount = rides.toIntOrNull() ?: 1,
                    description = description
                ))
            }) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

// === ABA GASTOS ===

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesTab(expenseDao: ExpenseDao) {
    val scope = rememberCoroutineScope()
    val allExpenses by expenseDao.getAllExpenses().collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Button(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Registrar Gasto")
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
                    Text("Gastos por Categoria", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    totalByCategory.take(5).forEach { (cat, total) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(cat, fontSize = 12.sp)
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
            items(allExpenses) { expense ->
                ExpenseCard(expense) {
                    scope.launch { expenseDao.delete(expense) }
                }
            }
        }
    }

    if (showAddDialog) {
        AddExpenseDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { expense ->
                scope.launch {
                    expenseDao.insert(expense)
                    showAddDialog = false
                }
            }
        )
    }
}

@Composable
fun ExpenseCard(expense: ExpenseEntity, onDelete: () -> Unit) {
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
                    Text("🔄 Recorrente (dia ${expense.recurringDay})", fontSize = 10.sp, color = ScoreYellow)
                }
            }
            Text(
                "- R$ %.2f".format(expense.amount),
                fontWeight = FontWeight.Bold,
                color = ScoreRed
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Excluir", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseDialog(onDismiss: () -> Unit, onConfirm: (ExpenseEntity) -> Unit) {
    var selectedCategory by remember { mutableStateOf(ExpenseCategory.FUEL) }
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isRecurring by remember { mutableStateOf(false) }
    var recurringDay by remember { mutableStateOf("1") }
    var liters by remember { mutableStateOf("") }
    var pricePerLiter by remember { mutableStateOf("") }
    var showCategoryPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar Gasto") },
        text = {
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isRecurring, onCheckedChange = { isRecurring = it })
                    Text("Gasto fixo mensal", fontSize = 13.sp)
                    if (isRecurring) {
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = recurringDay, onValueChange = { recurringDay = it },
                            label = { Text("Dia") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(60.dp), singleLine = true
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val finalAmount = amount.toDoubleOrNull() ?: run {
                    val l = liters.toDoubleOrNull() ?: 0.0
                    val p = pricePerLiter.toDoubleOrNull() ?: 0.0
                    l * p
                }
                onConfirm(ExpenseEntity(
                    category = selectedCategory.name,
                    amount = finalAmount,
                    description = description,
                    isRecurring = isRecurring,
                    recurringDay = recurringDay.toIntOrNull() ?: 1,
                    liters = liters.toDoubleOrNull(),
                    pricePerLiter = pricePerLiter.toDoubleOrNull()
                ))
            }) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

// === ABA VEÍCULO ===

@Composable
fun VehicleTab() {
    var vehicleType by remember { mutableStateOf(VehicleType.COMBUSTION) }
    var fuelType by remember { mutableStateOf(FuelType.FLEX) }
    var consumption by remember { mutableStateOf("10.0") }
    var fuelPrice by remember { mutableStateOf("5.50") }
    var vehicleName by remember { mutableStateOf("") }
    var monthlyFixed by remember { mutableStateOf("") }

    val avgConsumption = consumption.toDoubleOrNull() ?: 10.0
    val price = fuelPrice.toDoubleOrNull() ?: 5.50
    val costPerKm = if (avgConsumption > 0) price / avgConsumption else 0.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Configuração do Veículo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        // Tipo de veículo
        Text("Tipo de Veículo", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Column {
            VehicleType.values().forEach { type ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vehicleType = type }
                        .padding(vertical = 4.dp)
                ) {
                    RadioButton(selected = vehicleType == type, onClick = { vehicleType = type })
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(type.displayName, fontSize = 14.sp)
                        Text("Custo médio: R$ %.2f/km".format(type.costPerKmDefault), fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }
        }

        // Tipo de combustível
        if (vehicleType != VehicleType.ELECTRIC) {
            Text("Tipo de Combustível", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                val fuels = when (vehicleType) {
                    VehicleType.COMBUSTION -> listOf(FuelType.GASOLINE, FuelType.ETHANOL, FuelType.FLEX, FuelType.DIESEL, FuelType.GNV)
                    VehicleType.HYBRID -> listOf(FuelType.HYBRID_GAS, FuelType.HYBRID_ETHANOL)
                    else -> emptyList()
                }
                fuels.forEach { ft ->
                    FilterChip(
                        selected = fuelType == ft,
                        onClick = { fuelType = ft },
                        label = { Text(ft.displayName, fontSize = 11.sp) }
                    )
                }
            }
        }

        OutlinedTextField(
            value = vehicleName, onValueChange = { vehicleName = it },
            label = { Text("Nome do veículo (ex: Onix 2023)") },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = consumption, onValueChange = { consumption = it },
                label = { Text(if (vehicleType == VehicleType.ELECTRIC) "km/kWh" else "km/L") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f), singleLine = true
            )
            OutlinedTextField(
                value = fuelPrice, onValueChange = { fuelPrice = it },
                label = { Text(if (vehicleType == VehicleType.ELECTRIC) "R$/kWh" else "R$/L") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f), singleLine = true
            )
        }

        OutlinedTextField(
            value = monthlyFixed, onValueChange = { monthlyFixed = it },
            label = { Text("Custos fixos mensais (R$)") },
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
                Spacer(modifier = Modifier.height(4.dp))
                val monthly = monthlyFixed.toDoubleOrNull() ?: 0.0
                if (monthly > 0) {
                    Text(
                        "Custo fixo diário: R$ %.2f".format(monthly / 30),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

// === ABA METAS ===

@Composable
fun GoalsTab(earningDao: EarningDao, expenseDao: ExpenseDao) {
    var dailyGoal by remember { mutableStateOf("200") }
    var weeklyGoal by remember { mutableStateOf("1200") }
    var monthlyGoal by remember { mutableStateOf("5000") }

    val (todayStart, todayEnd) = remember { getPeriodRange(FinancePeriod.TODAY) }
    val (weekStart, weekEnd) = remember { getPeriodRange(FinancePeriod.WEEK) }
    val (monthStart, monthEnd) = remember { getPeriodRange(FinancePeriod.MONTH) }

    val todayEarnings by earningDao.getTotalEarnings(todayStart, todayEnd).collectAsState(initial = 0.0)
    val weekEarnings by earningDao.getTotalEarnings(weekStart, weekEnd).collectAsState(initial = 0.0)
    val monthEarnings by earningDao.getTotalEarnings(monthStart, monthEnd).collectAsState(initial = 0.0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Metas de Ganho", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        // Meta diária
        GoalCard(
            title = "Meta Diária",
            current = todayEarnings ?: 0.0,
            target = dailyGoal.toDoubleOrNull() ?: 200.0,
            onTargetChange = { dailyGoal = it }
        )

        // Meta semanal
        GoalCard(
            title = "Meta Semanal",
            current = weekEarnings ?: 0.0,
            target = weeklyGoal.toDoubleOrNull() ?: 1200.0,
            onTargetChange = { weeklyGoal = it }
        )

        // Meta mensal
        GoalCard(
            title = "Meta Mensal",
            current = monthEarnings ?: 0.0,
            target = monthlyGoal.toDoubleOrNull() ?: 5000.0,
            onTargetChange = { monthlyGoal = it }
        )
    }
}

@Composable
fun GoalCard(title: String, current: Double, target: Double, onTargetChange: (String) -> Unit) {
    val progress = if (target > 0) (current / target).coerceIn(0.0, 1.0) else 0.0
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
                Text(title, fontWeight = FontWeight.Bold)
                Text(
                    "${(progress * 100).toInt()}%",
                    color = progressColor,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = progress.toFloat(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = progressColor,
                trackColor = progressColor.copy(alpha = 0.2f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("R$ %.2f".format(current), fontSize = 12.sp)
                OutlinedTextField(
                    value = "%.0f".format(target),
                    onValueChange = onTargetChange,
                    label = { Text("Meta R$") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(100.dp),
                    singleLine = true
                )
            }
        }
    }
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
            cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
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
