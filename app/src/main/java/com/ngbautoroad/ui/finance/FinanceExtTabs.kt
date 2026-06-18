@file:OptIn(ExperimentalMaterial3Api::class)

package com.ngbautoroad.ui.finance

// ============================================================================
// ARQUIVO: FinanceExtTabs.kt
// LOCALIZAÇÃO: ui/finance/FinanceExtTabs.kt
// RESPONSABILIDADE: Novas abas do controle financeiro v4.3.0
//   - VehicleProfilesTab: Cadastro de múltiplos veículos com veículo ativo
//   - IndividualExpensesTab: Despesas individuais com rateio temporal
//   - ProjectionTab: Projeção inteligente + Simulação "E se?"
// ============================================================================

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import com.ngbautoroad.domain.ProjectionEngine
import com.ngbautoroad.ui.theme.*
import com.ngbautoroad.util.toDoubleLocale
import com.ngbautoroad.util.toDoubleLocaleOrNull
import kotlinx.coroutines.launch

// ============================================================================
// ABA: VEÍCULOS (Multi-veículos com ativo)
// ============================================================================

@Composable
fun VehicleProfilesTab(
    vehicleProfileDao: VehicleProfileDao?,
    snackbarHostState: SnackbarHostState
) {
    if (vehicleProfileDao == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Módulo de veículos não disponível")
        }
        return
    }

    val scope = rememberCoroutineScope()
    val vehicles by vehicleProfileDao.getAllVehicles().collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var editingVehicle by remember { mutableStateOf<VehicleProfileEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Button(
            onClick = { showAddDialog = true },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Cadastrar Veículo")
        }

        if (vehicles.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nenhum veículo cadastrado", color = Color.Gray)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                items(vehicles) { vehicle ->
                    VehicleProfileCard(
                        vehicle = vehicle,
                        onSetActive = {
                            scope.launch {
                                vehicleProfileDao.switchActiveVehicle(vehicle.id)
                                snackbarHostState.showSnackbar("${vehicle.brand} ${vehicle.model} ativado")
                            }
                        },
                        onEdit = { editingVehicle = vehicle; showAddDialog = true },
                        onDelete = {
                            scope.launch {
                                vehicleProfileDao.delete(vehicle)
                                snackbarHostState.showSnackbar("Veículo removido")
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }

    if (showAddDialog) {
        AddVehicleProfileDialog(
            existing = editingVehicle,
            onDismiss = { showAddDialog = false; editingVehicle = null },
            onSave = { vehicle ->
                scope.launch {
                    if (editingVehicle != null) {
                        vehicleProfileDao.update(vehicle)
                    } else {
                        vehicleProfileDao.insert(vehicle)
                    }
                    showAddDialog = false
                    editingVehicle = null
                    snackbarHostState.showSnackbar("Veículo salvo!")
                }
            }
        )
    }
}

@Composable
fun VehicleProfileCard(
    vehicle: VehicleProfileEntity,
    onSetActive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (vehicle.isActive) ScoreGreen.copy(alpha = 0.08f)
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (vehicle.isActive) BorderStroke(2.dp, ScoreGreen) else null
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DirectionsCar, contentDescription = null,
                    tint = if (vehicle.isActive) ScoreGreen else Color.Gray)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("${vehicle.brand} ${vehicle.model} ${vehicle.year}",
                        fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(vehicle.plate.ifEmpty { "Sem placa" }, fontSize = 12.sp, color = Color.Gray)
                }
                if (vehicle.isActive) {
                    Badge(containerColor = ScoreGreen) { Text("ATIVO", fontSize = 10.sp, color = Color.White) }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${vehicle.averageConsumption} km/L", fontSize = 11.sp)
                Text("R$ ${String.format("%.2f", vehicle.fuelPrice)}/L", fontSize = 11.sp)
                Text("R$ ${String.format("%.2f", vehicle.costPerKm)}/km", fontSize = 11.sp, color = ScoreOrange)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!vehicle.isActive) {
                    OutlinedButton(onClick = onSetActive, modifier = Modifier.height(32.dp)) {
                        Text("Ativar", fontSize = 11.sp)
                    }
                }
                OutlinedButton(onClick = onEdit, modifier = Modifier.height(32.dp)) {
                    Text("Editar", fontSize = 11.sp)
                }
                OutlinedButton(onClick = onDelete, modifier = Modifier.height(32.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ScoreRed)) {
                    Text("Excluir", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun AddVehicleProfileDialog(
    existing: VehicleProfileEntity?,
    onDismiss: () -> Unit,
    onSave: (VehicleProfileEntity) -> Unit
) {
    var brand by rememberSaveable { mutableStateOf(existing?.brand ?: "") }
    var model by rememberSaveable { mutableStateOf(existing?.model ?: "") }
    var year by rememberSaveable { mutableStateOf(existing?.year?.toString() ?: "") }
    var plate by rememberSaveable { mutableStateOf(existing?.plate ?: "") }
    var vehicleType by rememberSaveable { mutableStateOf(existing?.vehicleType ?: "COMBUSTION") }
    var fuelType by rememberSaveable { mutableStateOf(existing?.fuelType ?: "FLEX") }
    var consumption by rememberSaveable { mutableStateOf(existing?.averageConsumption?.toString() ?: "") }
    var fuelPrice by rememberSaveable { mutableStateOf(existing?.fuelPrice?.toString() ?: "") }
    var purchaseValue by rememberSaveable { mutableStateOf(existing?.purchaseValue?.toString() ?: "") }
    var tireLifeKm by rememberSaveable { mutableStateOf(existing?.tireLifeKm?.toString() ?: "40000") }
    var tireCost by rememberSaveable { mutableStateOf(existing?.tireCost?.toString() ?: "") }
    var brakepadLifeKm by rememberSaveable { mutableStateOf(existing?.brakepadLifeKm?.toString() ?: "30000") }
    var brakepadCost by rememberSaveable { mutableStateOf(existing?.brakepadCost?.toString() ?: "") }
    var oilChangeKm by rememberSaveable { mutableStateOf(existing?.oilChangeKm?.toString() ?: "10000") }
    var oilChangeCost by rememberSaveable { mutableStateOf(existing?.oilChangeCost?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing != null) "Editar Veículo" else "Cadastrar Veículo") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = brand, onValueChange = { brand = it }, label = { Text("Marca") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Modelo") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = year, onValueChange = { year = it }, label = { Text("Ano") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(value = plate, onValueChange = { plate = it }, label = { Text("Placa") }, modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Tipo de Veículo", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("COMBUSTION" to "Combustão", "HYBRID" to "Híbrido", "ELECTRIC" to "Elétrico").forEach { (key, label) ->
                        FilterChip(
                            selected = vehicleType == key,
                            onClick = {
                                vehicleType = key
                                if (key == "ELECTRIC") fuelType = "ELECTRIC"
                                else if (fuelType == "ELECTRIC") fuelType = "FLEX"
                            },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }
                if (vehicleType != "ELECTRIC") {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Combustível", fontSize = 12.sp, color = Color.Gray)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("FLEX" to "Flex", "GASOLINE" to "Gasolina", "ETHANOL" to "Etanol", "DIESEL" to "Diesel").forEach { (key, label) ->
                            FilterChip(
                                selected = fuelType == key,
                                onClick = { fuelType = key },
                                label = { Text(label, fontSize = 10.sp) }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                val consumptionLabel = if (vehicleType == "ELECTRIC") "km/kWh" else "km/L"
                val priceLabel = if (vehicleType == "ELECTRIC") "R$/kWh" else "R$/L"
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = consumption, onValueChange = { consumption = it }, label = { Text(consumptionLabel) }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(value = fuelPrice, onValueChange = { fuelPrice = it }, label = { Text(priceLabel) }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(value = purchaseValue, onValueChange = { purchaseValue = it }, label = { Text("Valor de compra (R$)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Desgaste", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = tireLifeKm, onValueChange = { tireLifeKm = it }, label = { Text("Pneu (km)") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(value = tireCost, onValueChange = { tireCost = it }, label = { Text("4 pneus (R$)") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = brakepadLifeKm, onValueChange = { brakepadLifeKm = it }, label = { Text("Pastilha (km)") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(value = brakepadCost, onValueChange = { brakepadCost = it }, label = { Text("Pastilha (R$)") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = oilChangeKm, onValueChange = { oilChangeKm = it }, label = { Text("Óleo (km)") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(value = oilChangeCost, onValueChange = { oilChangeCost = it }, label = { Text("Óleo (R$)") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val cons = consumption.toDoubleLocaleOrNull() ?: 0.0
                val fp = fuelPrice.toDoubleLocaleOrNull() ?: 0.0
                val cpk = if (cons > 0) fp / cons else 0.0
                val vehicle = (existing ?: VehicleProfileEntity()).copy(
                    brand = brand,
                    model = model,
                    year = year.toIntOrNull() ?: 0,
                    plate = plate,
                    vehicleType = vehicleType,
                    fuelType = fuelType,
                    averageConsumption = cons,
                    fuelPrice = fp,
                    costPerKm = cpk,
                    purchaseValue = purchaseValue.toDoubleLocaleOrNull() ?: 0.0,
                    tireLifeKm = tireLifeKm.toIntOrNull() ?: 40000,
                    tireCost = tireCost.toDoubleLocaleOrNull() ?: 0.0,
                    brakepadLifeKm = brakepadLifeKm.toIntOrNull() ?: 30000,
                    brakepadCost = brakepadCost.toDoubleLocaleOrNull() ?: 0.0,
                    oilChangeKm = oilChangeKm.toIntOrNull() ?: 10000,
                    oilChangeCost = oilChangeCost.toDoubleLocaleOrNull() ?: 0.0
                )
                onSave(vehicle)
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

// ============================================================================
// ABA: DESPESAS INDIVIDUAIS (com rateio temporal)
// ============================================================================

@Composable
fun IndividualExpensesTab(
    individualExpenseDao: IndividualExpenseDao?,
    vehicleProfileDao: VehicleProfileDao?,
    snackbarHostState: SnackbarHostState
) {
    if (individualExpenseDao == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Módulo de despesas não disponível")
        }
        return
    }

    val scope = rememberCoroutineScope()
    val expenses by individualExpenseDao.getAllExpenses().collectAsState(initial = emptyList())
    val totalMonthly by individualExpenseDao.getTotalMonthlyRated().collectAsState(initial = 0.0)
    var showAddDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Total mensal rateado
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = ScoreOrange.copy(alpha = 0.1f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Custo Fixo Mensal (rateado)", fontSize = 12.sp, color = Color.Gray)
                    Text("R$ %.2f".format(totalMonthly ?: 0.0), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = ScoreOrange)
                }
                Text("${expenses.count { it.isIncludedInCalc }} itens", fontSize = 11.sp, color = Color.Gray)
            }
        }

        Button(
            onClick = { showAddDialog = true },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Cadastrar Despesa")
        }

        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            items(expenses) { expense ->
                IndividualExpenseCard(
                    expense = expense,
                    onToggleCalc = {
                        scope.launch {
                            individualExpenseDao.update(expense.copy(isIncludedInCalc = !expense.isIncludedInCalc))
                        }
                    },
                    onDelete = {
                        scope.launch {
                            individualExpenseDao.delete(expense)
                            snackbarHostState.showSnackbar("Despesa removida")
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    if (showAddDialog) {
        AddIndividualExpenseDialog(
            onDismiss = { showAddDialog = false },
            onSave = { expense ->
                scope.launch {
                    individualExpenseDao.insert(expense)
                    showAddDialog = false
                    snackbarHostState.showSnackbar("Despesa cadastrada!")
                }
            }
        )
    }
}

@Composable
fun IndividualExpenseCard(
    expense: IndividualExpenseEntity,
    onToggleCalc: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (expense.isIncludedInCalc) MaterialTheme.colorScheme.surfaceVariant
            else Color.Gray.copy(alpha = 0.1f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Ícone da categoria
                Text(
                    ExpenseCategories.icons[expense.category] ?: "📦",
                    fontSize = 24.sp,
                    modifier = Modifier.padding(end = 10.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(expense.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "${ExpenseCategories.labels[expense.category] ?: expense.category} • ${expense.frequency}",
                        fontSize = 11.sp, color = Color.Gray
                    )
                }
                Text("R$ %.2f/mês".format(expense.monthlyAmount), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ScoreOrange)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Total: R$ %.2f".format(expense.totalAmount), fontSize = 11.sp)
                if (expense.installments > 1) {
                    Text("${expense.installmentsPaid}/${expense.installments} parcelas", fontSize = 11.sp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = expense.isIncludedInCalc, onCheckedChange = { onToggleCalc() })
                    Text("Incluir nos cálculos", fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Excluir", tint = ScoreRed, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun AddIndividualExpenseDialog(
    onDismiss: () -> Unit,
    onSave: (IndividualExpenseEntity) -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(ExpenseCategories.IPVA) }
    var totalAmount by rememberSaveable { mutableStateOf("") }
    var installments by rememberSaveable { mutableStateOf("12") }
    var frequency by rememberSaveable { mutableStateOf("MENSAL") }
    var includeInCalc by rememberSaveable { mutableStateOf(true) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cadastrar Despesa") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Título (ex: IPVA 2026)") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(4.dp))

                // Categoria dropdown
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = ExpenseCategories.labels[category] ?: category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Categoria") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        ExpenseCategories.all.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(ExpenseCategories.labels[cat] ?: cat) },
                                onClick = { category = cat; expanded = false }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(value = totalAmount, onValueChange = { totalAmount = it }, label = { Text("Valor Total (R$)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(value = installments, onValueChange = { installments = it }, label = { Text("Dividir em quantos meses?") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Spacer(modifier = Modifier.height(4.dp))

                // Preview do valor mensal
                val total = totalAmount.toDoubleLocaleOrNull() ?: 0.0
                val inst = installments.toIntOrNull() ?: 1
                val monthly = if (inst > 0) total / inst else 0.0
                if (total > 0) {
                    Text("Valor mensal: R$ %.2f".format(monthly), fontSize = 13.sp, color = ScoreOrange, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeInCalc, onCheckedChange = { includeInCalc = it })
                    Text("Incluir nos cálculos de custo", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val total = totalAmount.toDoubleLocaleOrNull() ?: 0.0
                val inst = installments.toIntOrNull()?.coerceAtLeast(1) ?: 1 // v5.0.0: Guard >=1
                // v5.0.0: Para despesas anuais, monthlyAmount = total / 12 (rateio anual)
                val isAnnual = category == ExpenseCategories.IPVA || category == ExpenseCategories.SEGURO || category == ExpenseCategories.LICENCIAMENTO
                val monthly = when {
                    frequency == "ANUAL" || isAnnual -> total / 12.0
                    inst > 0 -> total / inst
                    else -> 0.0
                }
                val expense = IndividualExpenseEntity(
                    title = title,
                    category = category,
                    totalAmount = total,
                    installments = inst,
                    monthlyAmount = monthly,
                    frequency = frequency,
                    isIncludedInCalc = includeInCalc,
                    isRecurringAnnual = isAnnual
                )
                onSave(expense)
            }, enabled = title.isNotBlank() && (totalAmount.toDoubleLocaleOrNull() ?: 0.0) > 0) {
                Text("Salvar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

// ============================================================================
// ABA: PROJEÇÃO INTELIGENTE + SIMULAÇÃO "E SE?"
// ============================================================================

@Composable
fun ProjectionTab(
    earningDao: EarningDao?,
    vehicleProfileDao: VehicleProfileDao?,
    individualExpenseDao: IndividualExpenseDao?,
    rideHistoryDao: RideHistoryDao?
) {
    if (earningDao == null || vehicleProfileDao == null || individualExpenseDao == null || rideHistoryDao == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Módulo de projeção não disponível")
        }
        return
    }

    val scope = rememberCoroutineScope()
    var selectedPeriod by remember { mutableStateOf("MES") }
    var projection by remember { mutableStateOf<FinancialProjection?>(null) }
    var whatIfResults by remember { mutableStateOf<List<WhatIfResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    val engine = remember {
        ProjectionEngine(earningDao, vehicleProfileDao, individualExpenseDao, rideHistoryDao)
    }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Carregar projeção ao mudar período
    LaunchedEffect(selectedPeriod) {
        isLoading = true
        errorMessage = null
        try {
            val result = engine.projectFinances(selectedPeriod)
            val whatIf = engine.simulateWhatIf(selectedPeriod)
            projection = result
            whatIfResults = whatIf
            if (result.projectedEarnings == 0.0 && result.projectedRides == 0) {
                errorMessage = "Sem dados suficientes. Registre corridas e ganhos para gerar projeções."
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // Não capturar CancellationException
        } catch (e: Exception) {
            errorMessage = "Erro ao calcular projeção: ${e.message ?: "desconhecido"}"
            android.util.Log.e("ProjectionTab", "Erro projeção", e)
        }
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Projeção Inteligente", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("Baseada nos seus dados dos últimos 30 dias", fontSize = 12.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(12.dp))

        // Filtro de período
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("DIA" to "Diário", "SEMANA" to "Semanal", "MES" to "Mensal", "ANO" to "Anual").forEach { (key, label) ->
                FilterChip(
                    selected = selectedPeriod == key,
                    onClick = { selectedPeriod = key },
                    label = { Text(label, fontSize = 11.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (errorMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ScoreYellow.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("⚠️ Atenção", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(errorMessage!!, fontSize = 12.sp, color = Color.Gray)
                }
            }
        } else if (projection != null) {
            val proj = projection!!

            // Confiança
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Confiança: ", fontSize = 12.sp)
                LinearProgressIndicator(
                    progress = (proj.confidenceLevel / 100.0).toFloat().coerceIn(0f, 1f),
                    modifier = Modifier.weight(1f).height(6.dp),
                    color = when {
                        proj.confidenceLevel >= 80 -> ScoreGreen
                        proj.confidenceLevel >= 50 -> ScoreYellow
                        else -> ScoreRed
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("${proj.confidenceLevel.toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Ganhos projetados
            ProjectionCard("Ganhos Projetados", "R$ %.2f".format(proj.projectedEarnings), ScoreGreen)
            Spacer(modifier = Modifier.height(8.dp))

            // Custos
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ProjectionMiniCard("Combustível", "R$ %.2f".format(proj.projectedFuelCost), ScoreOrange, Modifier.weight(1f))
                ProjectionMiniCard("Manutenção", "R$ %.2f".format(proj.projectedMaintenanceCost), ScoreOrange, Modifier.weight(1f))
                ProjectionMiniCard("Fixos", "R$ %.2f".format(proj.projectedFixedCosts), ScoreOrange, Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Lucros
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ProjectionMiniCard("Lucro Bruto", "R$ %.2f".format(proj.projectedGrossProfit),
                    if (proj.projectedGrossProfit >= 0) ScoreGreen else ScoreRed, Modifier.weight(1f))
                ProjectionMiniCard("Lucro Líquido", "R$ %.2f".format(proj.projectedNetProfit),
                    if (proj.projectedNetProfit >= 0) ScoreGreen else ScoreRed, Modifier.weight(1f))
                ProjectionMiniCard("Lucro Real", "R$ %.2f".format(proj.projectedRealProfit),
                    if (proj.projectedRealProfit >= 0) ScoreGreen else ScoreRed, Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Métricas
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ProjectionMiniCard("Corridas", "${proj.projectedRides}", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                ProjectionMiniCard("Km", "%.0f".format(proj.projectedKm), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                ProjectionMiniCard("Horas", "%.1f".format(proj.projectedHours), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ProjectionMiniCard("R$/Corrida", "%.2f".format(proj.avgEarningPerRide), Color(0xFF6366F1), Modifier.weight(1f))
                ProjectionMiniCard("R$/Km", "%.2f".format(proj.avgEarningPerKm), Color(0xFF6366F1), Modifier.weight(1f))
                ProjectionMiniCard("R$/Hora", "%.2f".format(proj.avgEarningPerHour), Color(0xFF6366F1), Modifier.weight(1f))
            }

            // Simulação "E se?"
            if (whatIfResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text("Simulação \"E se?\"", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("O que aconteceria se você aceitasse corridas diferentes", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(12.dp))

                whatIfResults.forEach { result ->
                    WhatIfCard(result)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun ProjectionCard(title: String, value: String, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 12.sp, color = Color.Gray)
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun ProjectionMiniCard(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f))) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 10.sp, color = Color.Gray, maxLines = 1)
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        }
    }
}

@Composable
fun WhatIfCard(result: WhatIfResult) {
    val isPositive = result.differenceFromActual >= 0
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isPositive) ScoreGreen.copy(alpha = 0.05f) else ScoreRed.copy(alpha = 0.05f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(result.scenarioName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    Text("Ganho", fontSize = 10.sp, color = Color.Gray)
                    Text("R$ %.2f".format(result.totalEarnings), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("Lucro Líq.", fontSize = 10.sp, color = Color.Gray)
                    Text("R$ %.2f".format(result.netProfit), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = if (result.netProfit >= 0) ScoreGreen else ScoreRed)
                }
                Column {
                    Text("Diferença", fontSize = 10.sp, color = Color.Gray)
                    Text(
                        "${if (isPositive) "+" else ""}R$ %.2f".format(result.differenceFromActual),
                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = if (isPositive) ScoreGreen else ScoreRed
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${result.totalRides} corridas", fontSize = 10.sp, color = Color.Gray)
                Text("%.0f km".format(result.totalKm), fontSize = 10.sp, color = Color.Gray)
                Text("%.1f h".format(result.totalHours), fontSize = 10.sp, color = Color.Gray)
                Text("Pneu: R$ %.2f".format(result.tireCost), fontSize = 10.sp, color = Color.Gray)
            }
        }
    }
}
