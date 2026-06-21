package com.ngbautoroad.ui

// ============================================================================
// ARQUIVO: MainActivity.kt
// LOCALIZAÇÃO: ui/MainActivity.kt
// RESPONSABILIDADE: Activity principal com navegação por abas (Bottom Navigation)
// v6.3.0 REDESIGN:
//   - Nova ordem: CRITÉRIOS | CARDS | INICIO | FINANCEIRO | CONFIG
//   - App sempre inicia na aba INICIO (centro, índice 2)
//   - Ícones de mercado com significado imediato
//   - Rótulos em português
//   - Removido: aba Histórico (movido para Config > Adicionais)
//   - Adicionado: aba Financeiro (antes era botão no Dashboard)
// DEPENDÊNCIAS:
//   - Todas as *Tab.kt (DashboardTab, CriteriaTab, CardTab, SettingsTab)
//   - ui/finance/FinanceActivity.kt (agora inline como FinanceTab)
//   - data/prefs/PrefsManager.kt
//   - data/db/AppDatabase.kt
// ============================================================================

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import kotlinx.coroutines.flow.first
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.ngbautoroad.data.db.AppDatabase
import com.ngbautoroad.data.prefs.PrefsManager
import com.ngbautoroad.ui.criteria.CriteriaTab
import com.ngbautoroad.ui.dashboard.DashboardTab
import com.ngbautoroad.ui.finance.FinanceTab
import com.ngbautoroad.ui.settings.SettingsTab
import com.ngbautoroad.ui.ai.AiTab
import androidx.compose.foundation.isSystemInDarkTheme
import com.ngbautoroad.ui.theme.NGBAutoRoadTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ngbautoroad.ui.onboarding.SetupWizardDialog

class MainActivity : ComponentActivity() {

    private lateinit var prefsManager: PrefsManager
    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefsManager = PrefsManager(applicationContext)
        database = AppDatabase.getInstance(applicationContext)

        // Agendar worker de despesas recorrentes (1x/dia)
        com.ngbautoroad.service.RecurringExpenseWorker.schedule(applicationContext)

        // Check overlay permission
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        // v5.0.0: Mover leituras de DataStore para lifecycleScope (evita ANR)
        lifecycleScope.launch {
            // Keep screen on (aplicação inicial)
            val keepScreenOn = prefsManager.keepScreenOnFlow.first()
            if (keepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }

            // Auto-iniciar OverlayService se o serviço estava habilitado
            val serviceEnabled = prefsManager.serviceEnabledFlow.first()
            if (serviceEnabled && Settings.canDrawOverlays(this@MainActivity)) {
                com.ngbautoroad.service.OverlayService.start(this@MainActivity)
            }
        }

        setContent {
            // Observar mudanças no keepScreenOn em tempo real
            val keepScreenState = prefsManager.keepScreenOnFlow.collectAsState(initial = false)
            LaunchedEffect(keepScreenState.value) {
                if (keepScreenState.value) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            // v5.2.3: Aplicar idioma ao iniciar
            val languagePref by prefsManager.languageFlow.collectAsState(initial = "pt")
            LaunchedEffect(languagePref) {
                val locale = when (languagePref) {
                    "en" -> java.util.Locale.ENGLISH
                    "es" -> java.util.Locale("es")
                    else -> java.util.Locale("pt", "BR")
                }
                val config = android.content.res.Configuration(resources.configuration)
                config.setLocale(locale)
                @Suppress("DEPRECATION")
                resources.updateConfiguration(config, resources.displayMetrics)
            }

            // v5.1.0: Dark mode dinâmico baseado na preferência do usuário
            val darkModePref by prefsManager.darkModeFlow.collectAsState(initial = "system")
            val isDark = when (darkModePref) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            NGBAutoRoadTheme(darkTheme = isDark) {
                MainScreen(prefsManager = prefsManager, database = database)
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

// v6.3.4: Nova ordem de abas — CRITÉRIOS | IA | INICIO | FINANÇAS | CONFIG
// Cards foi movido para Config > Cards
enum class TabItem(val title: String, val icon: ImageVector) {
    CRITERIA("Critérios", Icons.Default.Tune),
    AI("IA", Icons.Default.AutoAwesome),
    HOME("Início", Icons.Default.Home),
    FINANCE("Finanças", Icons.Default.AccountBalanceWallet),
    SETTINGS("Config", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(prefsManager: PrefsManager, database: AppDatabase) {
    // v6.3.0: App sempre inicia na aba INICIO (índice 2 = centro)
    var selectedTab by remember { mutableStateOf(2) }
    val tabs = TabItem.entries.toTypedArray()

    // v6.8.0: Setup Wizard (primeiro uso)
    val setupWizardCompleted by prefsManager.setupWizardCompletedFlow.collectAsState(initial = true)
    var showSetupWizard by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(setupWizardCompleted) {
        if (!setupWizardCompleted) {
            showSetupWizard = true
        }
    }

    if (showSetupWizard) {
        SetupWizardDialog(
            prefsManager = prefsManager,
            isFirstTime = true,
            onDismiss = { showSetupWizard = false },
            onComplete = { showSetupWizard = false }
        )
    }

    // v6.7.0: Onboarding obrigatório de odômetro (Ruptura #1 - Cold Start)
    val odometerOnboardingDone by prefsManager.odometerOnboardingDoneFlow.collectAsState(initial = true)
    var showOnboarding by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val financeDatabase = remember { com.ngbautoroad.data.db.FinanceDatabase.getInstance(ctx) }

    LaunchedEffect(odometerOnboardingDone) {
        if (!odometerOnboardingDone) {
            // Verificar se já tem veículo com odômetro > 0
            val vehicle = financeDatabase.vehicleProfileDao().getActiveVehicleSync()
            showOnboarding = vehicle == null || vehicle.currentOdometer == 0
        }
    }

    if (showOnboarding) {
        com.ngbautoroad.ui.onboarding.OdometerOnboardingDialog(
            prefsManager = prefsManager,
            financeDb = financeDatabase,
            onComplete = { showOnboarding = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("NGB AutoRoad v${com.ngbautoroad.BuildConfig.VERSION_NAME}")
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
            val ctx = LocalContext.current
            when (selectedTab) {
                0 -> CriteriaTab(prefsManager = prefsManager)
                1 -> AiTab(prefsManager = prefsManager, database = database)
                2 -> DashboardTab(prefsManager = prefsManager, database = database)
                3 -> FinanceTab(prefsManager = prefsManager, database = database)
                4 -> SettingsTab(prefsManager = prefsManager, database = database)
            }
        }
    }
}
