package com.ngbautoroad.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.ngbautoroad.data.db.AppDatabase
import com.ngbautoroad.data.prefs.PrefsManager
import com.ngbautoroad.ui.card.CardTab
import com.ngbautoroad.ui.criteria.CriteriaTab
import com.ngbautoroad.ui.dashboard.DashboardTab
import com.ngbautoroad.ui.history.HistoryTab
import com.ngbautoroad.ui.settings.SettingsTab
import com.ngbautoroad.ui.theme.NGBAutoRoadTheme

class MainActivity : ComponentActivity() {

    private lateinit var prefsManager: PrefsManager
    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefsManager = PrefsManager(applicationContext)
        database = AppDatabase.getInstance(applicationContext)

        // Check overlay permission
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        // Keep screen on
        val keepScreenOn = runBlocking { prefsManager.keepScreenOnFlow.first() }
        if (keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        setContent {
            NGBAutoRoadTheme {
                MainScreen(prefsManager = prefsManager, database = database)
            }
        }
    }
}

enum class TabItem(val title: String, val icon: ImageVector) {
    DASHBOARD("", Icons.Default.Dashboard),
    CRITERIA("Critérios", Icons.Default.Tune),
    CARD("Cards", Icons.Default.CreditCard),
    HISTORY("Histórico", Icons.Default.History),
    SETTINGS("Config", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(prefsManager: PrefsManager, database: AppDatabase) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = TabItem.entries.toTypedArray()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("NGB AutoRoad v4.0.1")
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = if (tab.title.isNotEmpty()) { { Text(tab.title) } } else null,
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> DashboardTab(prefsManager = prefsManager, database = database)
                1 -> CriteriaTab(prefsManager = prefsManager)
                2 -> CardTab(prefsManager = prefsManager)
                3 -> HistoryTab(prefsManager = prefsManager, database = database)
                4 -> SettingsTab(prefsManager = prefsManager)
            }
        }
    }
}
