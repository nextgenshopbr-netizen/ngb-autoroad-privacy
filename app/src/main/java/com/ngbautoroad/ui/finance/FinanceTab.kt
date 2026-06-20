package com.ngbautoroad.ui.finance

// ============================================================================
// ARQUIVO: FinanceTab.kt
// LOCALIZAÇÃO: ui/finance/FinanceTab.kt
// RESPONSABILIDADE: Aba Finanças inline na navegação principal (v6.3.4)
// CONTEXTO: Usa os composables completos com CRUD do FinanceActivity e FinanceExtTabs.
//           Abas: Resumo | Ganhos | Despesas | Veículos | Desp. Fixas | Metas
//           Projeção foi movida para a aba IA.
// DEPENDÊNCIAS:
//   - ui/finance/FinanceActivity.kt → FinanceSummaryTab, EarningsTab, ExpensesTab, VehicleTab, GoalsTab
//   - ui/finance/FinanceExtTabs.kt → VehicleProfilesTab, IndividualExpensesTab
//   - data/db/FinanceDatabase.kt
//   - data/prefs/PrefsManager.kt
// ============================================================================

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngbautoroad.data.db.AppDatabase
import com.ngbautoroad.data.db.FinanceDatabase
import com.ngbautoroad.data.prefs.PrefsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceTab(prefsManager: PrefsManager, database: AppDatabase) {
    val context = LocalContext.current
    val financeDb = remember { FinanceDatabase.getInstance(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Abas internas: Resumo | Ganhos | Despesas | Veículos | Desp. Fixas | Metas
    var selectedInternalTab by remember { mutableIntStateOf(0) }
    val internalTabs = listOf("Resumo", "Ganhos", "Despesas", "Veículos", "Desp. Fixas", "Metas")

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

        // Abas internas com scroll horizontal
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

        // Conteúdo por aba — usando composables completos com CRUD
        when (selectedInternalTab) {
            0 -> FinanceSummaryTab(
                expenseDao = financeDb.expenseDao(),
                earningDao = financeDb.earningDao(),
                financialGoalDao = financeDb.financialGoalDao()
            )
            1 -> EarningsTab(
                earningDao = financeDb.earningDao(),
                prefsManager = prefsManager,
                snackbarHostState = snackbarHostState
            )
            2 -> ExpensesTab(
                expenseDao = financeDb.expenseDao(),
                snackbarHostState = snackbarHostState
            )
            3 -> VehicleProfilesTab(
                vehicleProfileDao = financeDb.vehicleProfileDao(),
                snackbarHostState = snackbarHostState
            )
            4 -> IndividualExpensesTab(
                individualExpenseDao = financeDb.individualExpenseDao(),
                vehicleProfileDao = financeDb.vehicleProfileDao(),
                snackbarHostState = snackbarHostState
            )
            5 -> GoalsTab(
                earningDao = financeDb.earningDao(),
                expenseDao = financeDb.expenseDao(),
                financialGoalDao = financeDb.financialGoalDao(),
                snackbarHostState = snackbarHostState
            )
        }
    }
}
