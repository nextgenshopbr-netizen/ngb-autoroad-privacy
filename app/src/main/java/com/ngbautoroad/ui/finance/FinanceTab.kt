package com.ngbautoroad.ui.finance

// ============================================================================
// ARQUIVO: FinanceTab.kt
// LOCALIZAÇÃO: ui/finance/FinanceTab.kt
// RESPONSABILIDADE: Aba Financeiro inline na navegação principal (v6.3.0)
// CONTEXTO: Antes era acessado via botão no Dashboard que abria FinanceActivity.
//           Agora é uma aba direta na barra de navegação inferior.
// ============================================================================

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
import androidx.compose.ui.unit.sp
import com.ngbautoroad.data.db.AppDatabase
import com.ngbautoroad.data.db.FinanceDatabase
import com.ngbautoroad.data.prefs.PrefsManager
import com.ngbautoroad.ui.finance.ProjectionTab
import kotlinx.coroutines.flow.map
import java.text.NumberFormat
import java.util.*

@Composable
fun FinanceTab(prefsManager: PrefsManager, database: AppDatabase) {
    val context = LocalContext.current
    val financeDb = remember { FinanceDatabase.getInstance(context) }
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }

    // Dados de ganhos
    val allEarnings by financeDb.earningDao().getAllEarnings().collectAsState(initial = emptyList())
    val allExpenses by financeDb.expenseDao().getAllExpenses().collectAsState(initial = emptyList())

    val totalEarnings = remember(allEarnings) { allEarnings.sumOf { it.amount } }
    val totalExpenses = remember(allExpenses) { allExpenses.sumOf { it.amount } }
    val netProfit = totalEarnings - totalExpenses

    // Abas internas: Resumo | Ganhos | Despesas | Veículo | Projeção
    var selectedInternalTab by remember { mutableIntStateOf(0) }
    val internalTabs = listOf("Resumo", "Ganhos", "Despesas", "Veículo", "Projeção")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Header
        Text(
            text = "Controle Financeiro",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )

        // Abas internas
        ScrollableTabRow(
            selectedTabIndex = selectedInternalTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 0.dp
        ) {
            internalTabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedInternalTab == index,
                    onClick = { selectedInternalTab = index },
                    text = { Text(title, fontSize = 12.sp, maxLines = 1) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Conteúdo por aba
        when (selectedInternalTab) {
            0 -> FinanceResumoContent(
                totalEarnings = totalEarnings,
                totalExpenses = totalExpenses,
                netProfit = netProfit,
                currencyFormat = currencyFormat,
                rideCount = allEarnings.size
            )
            1 -> FinanceGanhosContent(
                earnings = allEarnings,
                currencyFormat = currencyFormat,
                financeDb = financeDb
            )
            2 -> FinanceDespesasContent(
                expenses = allExpenses,
                currencyFormat = currencyFormat,
                financeDb = financeDb
            )
            3 -> FinanceVeiculoContent(prefsManager = prefsManager)
            4 -> {
                // v6.3.2: Aba Projeção restaurada de FinanceExtTabs.kt
                val rideHistoryDao = remember { database.rideHistoryDao() }
                ProjectionTab(
                    earningDao = financeDb.earningDao(),
                    vehicleProfileDao = financeDb.vehicleProfileDao(),
                    individualExpenseDao = financeDb.individualExpenseDao(),
                    rideHistoryDao = rideHistoryDao
                )
            }
        }
    }
}

@Composable
private fun FinanceResumoContent(
    totalEarnings: Double,
    totalExpenses: Double,
    netProfit: Double,
    currencyFormat: NumberFormat,
    rideCount: Int
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Card Lucro Líquido
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (netProfit >= 0)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Lucro Líquido",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = currencyFormat.format(netProfit),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (netProfit >= 0)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )
            }
        }

        // Cards de Ganhos e Despesas lado a lado
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Ganhos
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.TrendingUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ganhos", fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = currencyFormat.format(totalEarnings),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "$rideCount corridas",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Despesas
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.TrendingDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Despesas", fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = currencyFormat.format(totalExpenses),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Projeção mensal
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Projeção Mensal",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Baseado nos últimos 7 dias de atividade",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Projeção simples: média diária * 30
                val avgDaily = if (rideCount > 0) totalEarnings / maxOf(rideCount / 3.0, 1.0) else 0.0
                Text(
                    text = currencyFormat.format(avgDaily * 30),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun FinanceGanhosContent(
    earnings: List<com.ngbautoroad.data.db.EarningEntity>,
    currencyFormat: NumberFormat,
    financeDb: FinanceDatabase
) {
    if (earnings.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Nenhum ganho registrado",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Os ganhos serão importados automaticamente ao concluir corridas",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp)
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            earnings.sortedByDescending { it.date }.take(50).forEach { earning ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = earning.platform.ifEmpty { "Corrida" },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
                                    .format(Date(earning.date)),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = currencyFormat.format(earning.amount),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FinanceDespesasContent(
    expenses: List<com.ngbautoroad.data.db.ExpenseEntity>,
    currencyFormat: NumberFormat,
    financeDb: FinanceDatabase
) {
    if (expenses.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Receipt,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Nenhuma despesa registrada",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            expenses.sortedByDescending { it.date }.take(50).forEach { expense ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = expense.category.ifEmpty { "Despesa" },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
                                    .format(Date(expense.date)),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "- ${currencyFormat.format(expense.amount)}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FinanceVeiculoContent(prefsManager: PrefsManager) {
    val context = LocalContext.current
    val financeDb = remember { FinanceDatabase.getInstance(context) }
    val vehicleConfig by financeDb.vehicleConfigDao().getConfig().collectAsState(initial = null)
    val vehicleName = vehicleConfig?.let { "${it.brand} ${it.model}".trim() } ?: ""
    val fuelType = vehicleConfig?.fuelType ?: "Gasolina"
    val fuelConsumption = vehicleConfig?.averageConsumption ?: 10.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = vehicleName.ifEmpty { "Veículo não configurado" },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Combustível", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(fuelType, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                    Column {
                        Text("Consumo", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${String.format("%.1f", fuelConsumption)} km/L", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        Text(
            text = "Configure seu veículo nas Configurações para cálculos precisos de custo por km.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}
