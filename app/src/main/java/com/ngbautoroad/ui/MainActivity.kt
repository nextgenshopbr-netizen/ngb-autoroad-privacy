package com.ngbautoroad.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.ngbautoroad.data.prefs.PrefsManager
import com.ngbautoroad.ui.criteria.CriteriaTab
import com.ngbautoroad.ui.card.CardTab
import com.ngbautoroad.ui.history.HistoryTab
import com.ngbautoroad.ui.settings.SettingsTab
import com.ngbautoroad.ui.theme.NGBAutoRoadTheme

class MainActivity : ComponentActivity() {

    private lateinit var prefsManager: PrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefsManager = PrefsManager(applicationContext)

        // Check overlay permission
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        setContent {
            NGBAutoRoadTheme {
                MainScreen(prefsManager = prefsManager)
            }
        }
    }
}

enum class TabItem(val title: String, val icon: ImageVector) {
    CRITERIA("Critérios", Icons.Default.Tune),
    CARD("Card", Icons.Default.CreditCard),
    HISTORY("Histórico", Icons.Default.History),
    SETTINGS("Config", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(prefsManager: PrefsManager) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = TabItem.entries.toTypedArray()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("NGB AutoRoad v3.0")
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
                        label = { Text(tab.title) },
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
                0 -> CriteriaTab(prefsManager = prefsManager)
                1 -> CardTab(prefsManager = prefsManager)
                2 -> HistoryTab(prefsManager = prefsManager)
                3 -> SettingsTab(prefsManager = prefsManager)
            }
        }
    }
}
