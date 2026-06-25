@file:OptIn(ExperimentalMaterial3Api::class)

package com.ngbautoroad.ui.ai

// ============================================================================
// ARQUIVO: AiTab.kt
// LOCALIZAÇÃO: ui/ai/AiTab.kt
// RESPONSABILIDADE: Aba IA inline na navegação principal (v6.3.4)
// CONTEXTO: Substitui o antigo FeaturesActivity que abria como Activity separada.
//           Agora é inline com sub-abas: Turno | Ranking | IA Local | Projeção | Histórico | Relatório | Exportar
// DEPENDÊNCIAS:
//   - ui/features/FeaturesActivity.kt → ShiftTab, RankingTab, LearningTab, ReportTab, ExportTab
//   - ui/finance/FinanceExtTabs.kt → ProjectionTab
//   - ui/history/HistoryTab.kt → HistoryTab
//   - data/db/AppDatabase.kt → rideHistoryDao
//   - data/db/FinanceDatabase.kt → earningDao, vehicleProfileDao, individualExpenseDao
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
import com.ngbautoroad.ui.features.ShiftTab
import com.ngbautoroad.ui.features.RankingTab
import com.ngbautoroad.ui.features.LearningTab
import com.ngbautoroad.ui.features.ReportTab
import com.ngbautoroad.ui.features.ExportTab
import com.ngbautoroad.ui.finance.ProjectionTab
import com.ngbautoroad.ui.history.HistoryTab
import com.ngbautoroad.ui.features.HeatmapTab

@Composable
fun AiTab(prefsManager: PrefsManager, database: AppDatabase) {
    val context = LocalContext.current
    val financeDb = remember { FinanceDatabase.getInstance(context) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Turno", "Ranking", "IA Local", "Projeção", "Histórico", "Heatmap", "Relatório", "Exportar")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Header
        Text(
            text = "Inteligência Artificial",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )

        // Sub-abas
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 0.dp
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontSize = 12.sp, maxLines = 1) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Conteúdo por aba
        when (selectedTab) {
            0 -> ShiftTab()
            1 -> RankingTab()
            2 -> LearningTab()
            3 -> {
                val rideHistoryDao = remember { database.rideHistoryDao() }
                ProjectionTab(
                    earningDao = financeDb.earningDao(),
                    vehicleProfileDao = financeDb.vehicleProfileDao(),
                    individualExpenseDao = financeDb.individualExpenseDao(),
                    rideHistoryDao = rideHistoryDao
                )
            }
            4 -> HistoryTab(prefsManager = prefsManager, database = database)
            5 -> HeatmapTab(database = database)
            6 -> ReportTab()
            7 -> ExportTab()
        }
    }
}
