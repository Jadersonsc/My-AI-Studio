package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.data.model.AppItem
import com.example.ui.MainViewModel
import com.example.ui.components.AppHeader
import com.example.ui.components.BiometricLockOverlay
import com.example.ui.components.ExtractionProgressOverlay
import com.example.ui.components.ExtractionResultDialog
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.AppsScreen
import com.example.ui.screens.FilesScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.ApkExtractorTheme
import com.example.util.BiometricHelper
import com.example.util.ShareHelper
import java.io.File

class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Permission result handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val isBiometricAuthenticated by viewModel.isBiometricAuthenticated.collectAsState()
            val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()
            val isOnline by viewModel.isOnline.collectAsState()

            val installedApps by viewModel.installedApps.collectAsState()
            val isLoadingApps by viewModel.isLoadingApps.collectAsState()
            val searchQuery by viewModel.searchQuery.collectAsState()
            val appFilter by viewModel.appFilter.collectAsState()

            val extractedFiles by viewModel.extractedFiles.collectAsState()
            val isLoadingFiles by viewModel.isLoadingFiles.collectAsState()
            val historyRecords by viewModel.historyRecords.collectAsState()

            val progressState by viewModel.currentExtractionProgress.collectAsState()
            val lastResult by viewModel.lastExtractionResult.collectAsState()
            val metrics by viewModel.performanceMetric.collectAsState()

            val currentLang by viewModel.currentLanguage.collectAsState()
            val autoCloudSync by viewModel.autoCloudSync.collectAsState()
            val cloudProvider by viewModel.cloudProvider.collectAsState()
            val externalAnalytics by viewModel.externalAnalytics.collectAsState()

            var selectedTab by remember { mutableIntStateOf(0) }
            val context = LocalContext.current

            // Auto-trigger biometric prompt if locked
            LaunchedEffect(isBiometricEnabled, isBiometricAuthenticated) {
                if (isBiometricEnabled && !isBiometricAuthenticated) {
                    promptBiometric()
                }
            }

            ApkExtractorTheme(themeMode = themeMode) {
                if (isBiometricEnabled && !isBiometricAuthenticated) {
                    BiometricLockOverlay(
                        onAuthenticateClick = { promptBiometric() },
                        onBypassClick = {
                            // Device credential fallback
                            promptBiometric()
                        }
                    )
                } else {
                    Scaffold(
                        topBar = {
                            AppHeader(
                                isOnline = isOnline,
                                isBiometricEnabled = isBiometricEnabled,
                                onLockClick = { viewModel.lockBiometric() },
                                onSyncAllClick = {
                                    if (isOnline) {
                                        viewModel.syncAllPendingToCloud()
                                        Toast.makeText(context, "Sincronizando com a Nuvem...", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Dispositivo offline. Conecte-se para sincronizar.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            )
                        },
                        bottomBar = {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .windowInsetsPadding(WindowInsets.navigationBars)
                                    .testTag("main_navigation_bar")
                            ) {
                                NavigationBarItem(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    icon = { Icon(Icons.Default.Apps, contentDescription = null) },
                                    label = { Text("Apps", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                        indicatorColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.testTag("nav_tab_apps")
                                )

                                NavigationBarItem(
                                    selected = selectedTab == 1,
                                    onClick = {
                                        selectedTab = 1
                                        viewModel.loadExtractedFiles()
                                    },
                                    icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                                    label = { Text("Arquivos", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                        indicatorColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.testTag("nav_tab_files")
                                )

                                NavigationBarItem(
                                    selected = selectedTab == 2,
                                    onClick = { selectedTab = 2 },
                                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                                    label = { Text("Histórico", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                        indicatorColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.testTag("nav_tab_history")
                                )

                                NavigationBarItem(
                                    selected = selectedTab == 3,
                                    onClick = { selectedTab = 3 },
                                    icon = { Icon(Icons.Default.Analytics, contentDescription = null) },
                                    label = { Text("Métricas", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                        indicatorColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.testTag("nav_tab_analytics")
                                )

                                NavigationBarItem(
                                    selected = selectedTab == 4,
                                    onClick = { selectedTab = 4 },
                                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                    label = { Text("Ajustes", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                        indicatorColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.testTag("nav_tab_settings")
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            Crossfade(targetState = selectedTab, label = "tab_transition") { tab ->
                                when (tab) {
                                    0 -> AppsScreen(
                                        apps = installedApps,
                                        isLoading = isLoadingApps,
                                        searchQuery = searchQuery,
                                        filter = appFilter,
                                        currentExtractingApp = progressState?.first,
                                        onSearchChange = { viewModel.setSearchQuery(it) },
                                        onFilterChange = { viewModel.setAppFilter(it) },
                                        onExtractApp = { app -> viewModel.extractApk(app) },
                                        onRefresh = { viewModel.loadInstalledApps() }
                                    )

                                    1 -> FilesScreen(
                                        files = extractedFiles,
                                        isLoading = isLoadingFiles,
                                        isOnline = isOnline,
                                        onShareFile = { file, name ->
                                            ShareHelper.shareApkFile(context, file, name)
                                        },
                                        onShareSocial = { file, name, pkg ->
                                            ShareHelper.shareToSocialApp(context, file, name, pkg)
                                        },
                                        onInstallFile = { file ->
                                            ShareHelper.installApk(context, file)
                                        },
                                        onCloudSyncAll = {
                                            if (isOnline) {
                                                viewModel.syncAllPendingToCloud()
                                                Toast.makeText(context, "Sincronizando com a Nuvem...", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Offline: sincronização pausada.", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onRenameFile = { file, newName ->
                                            viewModel.renameExtractedFile(file, newName)
                                            Toast.makeText(context, "Arquivo renomeado!", Toast.LENGTH_SHORT).show()
                                        },
                                        onDeleteFile = { file ->
                                            viewModel.deleteExtractedFile(file)
                                            Toast.makeText(context, "Arquivo excluído!", Toast.LENGTH_SHORT).show()
                                        },
                                        onRefresh = { viewModel.loadExtractedFiles() }
                                    )

                                    2 -> HistoryScreen(
                                        records = historyRecords,
                                        isOnline = isOnline,
                                        onSyncItem = { record ->
                                            if (isOnline) {
                                                viewModel.syncRecordToCloud(record)
                                                Toast.makeText(context, "Sincronizando ${record.appName}...", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Dispositivo offline.", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onDeleteItem = { id ->
                                            viewModel.deleteHistoryItem(id)
                                        },
                                        onClearAll = {
                                            viewModel.clearHistory()
                                            Toast.makeText(context, "Histórico limpo!", Toast.LENGTH_SHORT).show()
                                        }
                                    )

                                    3 -> AnalyticsScreen(
                                        metric = metrics,
                                        externalAnalyticsEnabled = externalAnalytics,
                                        onToggleExternalAnalytics = { viewModel.setExternalAnalytics(it) },
                                        onExportReport = { format ->
                                            val report = viewModel.exportReport(format)
                                            ShareHelper.shareTextReport(
                                                context,
                                                "Relatório de Desempenho ($format)",
                                                report
                                            )
                                        }
                                    )

                                    4 -> SettingsScreen(
                                        currentTheme = themeMode,
                                        isBiometricEnabled = isBiometricEnabled,
                                        isBiometricSupported = BiometricHelper.canAuthenticate(context),
                                        autoCloudSync = autoCloudSync,
                                        currentLanguage = currentLang,
                                        cloudProvider = cloudProvider,
                                        externalAnalytics = externalAnalytics,
                                        onThemeChange = { viewModel.setThemeMode(it) },
                                        onBiometricChange = { enabled ->
                                            if (enabled) {
                                                BiometricHelper.showBiometricPrompt(
                                                    activity = this@MainActivity,
                                                    title = "Ativar Segurança Biométrica",
                                                    subtitle = "Confirme sua impressão digital ou senha",
                                                    onSuccess = {
                                                        viewModel.setBiometricEnabled(true)
                                                        Toast.makeText(context, "Proteção biométrica ativada!", Toast.LENGTH_SHORT).show()
                                                    },
                                                    onError = { err ->
                                                        Toast.makeText(context, "Erro: $err", Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                            } else {
                                                viewModel.setBiometricEnabled(false)
                                                Toast.makeText(context, "Proteção biométrica desativada", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onAutoSyncChange = { viewModel.setAutoCloudSync(it) },
                                        onLanguageChange = { viewModel.setLanguage(it) },
                                        onCloudProviderChange = { viewModel.setCloudProvider(it) },
                                        onExternalAnalyticsChange = { viewModel.setExternalAnalytics(it) }
                                    )
                                }
                            }

                            // Extraction progress modal
                            progressState?.let { (appName, progress) ->
                                ExtractionProgressOverlay(appName = appName, progress = progress)
                            }

                            // Extraction result modal
                            lastResult?.let { res ->
                                val appName = installedApps.find { it.apkSourceDir == res.filePath }?.appName ?: "Aplicativo"
                                ExtractionResultDialog(
                                    appName = appName,
                                    result = res,
                                    onDismiss = { viewModel.clearLastResult() },
                                    onShare = {
                                        viewModel.clearLastResult()
                                        res.filePath?.let { path ->
                                            val file = File(path)
                                            if (file.exists()) {
                                                ShareHelper.shareApkFile(context, file, appName)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun promptBiometric() {
        BiometricHelper.showBiometricPrompt(
            activity = this,
            title = "APK Extractor Pro",
            subtitle = "Autentique para desbloquear o aplicativo",
            onSuccess = {
                viewModel.authenticateBiometric()
            },
            onError = { err ->
                Toast.makeText(this, "Falha na autenticação: $err", Toast.LENGTH_SHORT).show()
            }
        )
    }
}
