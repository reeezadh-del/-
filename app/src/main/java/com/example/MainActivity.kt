package com.example

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.model.ConnectionState
import com.example.ui.MainViewModel
import com.example.ui.screens.ConnectScreen
import com.example.ui.screens.ProfilesScreen
import com.example.ui.screens.SplitTunnelScreen
import com.example.ui.screens.ToolsScreen
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.SeparNetTheme
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

private sealed class NavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Connect : NavTab("connect", "اتصال", Icons.Filled.Shield, Icons.Outlined.Shield)
    data object Profiles : NavTab("profiles", "سرورها", Icons.Filled.Dns, Icons.Outlined.Dns)
    data object SplitTunnel : NavTab("split_tunnel", "برنامه‌ها", Icons.Filled.Apps, Icons.Outlined.Apps)
    data object Tools : NavTab("tools", "ابزارها", Icons.Filled.Build, Icons.Outlined.Build)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SeparNetTheme {
                SeparNetAppRoot()
            }
        }
    }
}

@Composable
fun SeparNetAppRoot(
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.startVpnService()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent != null) {
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            viewModel.startVpnService()
        }
    }

    val onToggleConnection: () -> Unit = {
        val currentState = uiState.telemetry.state
        if (currentState == ConnectionState.Connected || currentState == ConnectionState.Connecting) {
            viewModel.stopVpnService()
        } else {
            val needsNotifPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED

            if (needsNotifPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                val prepareIntent = VpnService.prepare(context)
                if (prepareIntent != null) {
                    vpnPermissionLauncher.launch(prepareIntent)
                } else {
                    viewModel.startVpnService()
                }
            }
        }
    }

    LaunchedEffect(uiState.bannerMessage) {
        val msg = uiState.bannerMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissBanner()
        }
    }

    val tabs = remember {
        listOf(NavTab.Connect, NavTab.Profiles, NavTab.SplitTunnel, NavTab.Tools)
    }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val navigateToTab: (NavTab) -> Unit = { tab ->
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = ObsidianBg,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(
                        containerColor = ObsidianSurface,
                        tonalElevation = 4.dp
                    ) {
                        tabs.forEach { tab ->
                            val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = EmeraldPrimary,
                                    selectedTextColor = EmeraldPrimary,
                                    indicatorColor = EmeraldContainer,
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                ),
                                modifier = Modifier.testTag("nav_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        containerColor = ObsidianSurface
                    ) {
                        tabs.forEach { tab ->
                            val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = EmeraldPrimary,
                                    selectedTextColor = EmeraldPrimary,
                                    indicatorColor = EmeraldContainer,
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                ),
                                modifier = Modifier.testTag("nav_${tab.route}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    NavHost(
                        navController = navController,
                        startDestination = NavTab.Connect.route
                    ) {
                        composable(NavTab.Connect.route) {
                            ConnectScreen(
                                uiState = uiState,
                                onToggleConnection = onToggleConnection,
                                onSelectMode = viewModel::setVpnMode,
                                onOpenProfiles = { navigateToTab(NavTab.Profiles) },
                                onOpenTools = { navigateToTab(NavTab.Tools) },
                                onCheckExitIp = viewModel::checkExitIp
                            )
                        }
                        composable(NavTab.Profiles.route) {
                            ProfilesScreen(
                                uiState = uiState,
                                onPasteImport = viewModel::importFromTextOrUrl,
                                onAddManualProxy = viewModel::addManualProxy,
                                onSelectProfile = viewModel::selectProfile,
                                onTestProfile = { p ->
                                    viewModel.testSingleProfile(p.id, p.host, p.port)
                                },
                                onTestAll = viewModel::testAllProfiles,
                                onDeleteProfile = viewModel::deleteProfile
                            )
                        }
                        composable(NavTab.SplitTunnel.route) {
                            SplitTunnelScreen(
                                uiState = uiState,
                                onToggleAppBypass = viewModel::toggleAppBypass,
                                onAutoBypassIranianApps = viewModel::autoBypassIranianApps,
                                onClearAllBypassed = viewModel::clearAllBypassedApps
                            )
                        }
                        composable(NavTab.Tools.route) {
                            ToolsScreen(
                                uiState = uiState,
                                onSelectDnsPreset = viewModel::setDnsPreset,
                                onSaveCustomDoh = viewModel::setCustomDohUrl,
                                onToggleTlsFragment = viewModel::setTlsFragmentEnabled,
                                onUpdateFragmentParams = viewModel::setFragmentParams,
                                onRunNetworkScan = viewModel::runCleanIpNetworkScan,
                                onClearLogs = viewModel::clearLogs
                            )
                        }
                    }
                }
            }
        }
    }
}
