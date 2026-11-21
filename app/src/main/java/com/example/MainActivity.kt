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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ai.configureAppCheck
import com.example.model.ConnectionState
import com.example.ui.MainViewModel
import com.example.ui.StartupActionRecommendation
import com.example.ui.screens.AiHubScreen
import com.example.ui.screens.ConnectScreen
import com.example.ui.screens.GitHubServerScreen
import com.example.ui.screens.ProfilesScreen
import com.example.ui.screens.SplitTunnelScreen
import com.example.ui.screens.StartupLoadingScreen
import com.example.ui.screens.ToolsScreen
import com.example.ui.screens.WelcomeOnboardingScreen
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBlueDeep
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.SeparNetTheme
import com.example.ui.theme.SkyCyanGlow
import com.example.ui.theme.TextMuted

private sealed class NavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Connect : NavTab("connect", "اتصال", Icons.Filled.Home, Icons.Outlined.Home)
    data object AiHub : NavTab("ai_hub", "هوش مصنوعی", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome)
    data object Profiles : NavTab("profiles", "سرورها", Icons.Filled.Dns, Icons.Outlined.Dns)
    data object SplitTunnel : NavTab("split_tunnel", "برنامه‌ها", Icons.Filled.Apps, Icons.Outlined.Apps)
    data object Tools : NavTab("tools", "ابزارها", Icons.Filled.Build, Icons.Outlined.Build)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
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

    if (!uiState.settingsLoaded) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBg)
        )
        return
    }

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

    val latestConnState by rememberUpdatedState(uiState.telemetry.state)
    val latestToggleConnection by rememberUpdatedState(onToggleConnection)

    val rootStage = when {
        !uiState.startupDismissed -> 0
        !uiState.settings.onboardingSeen -> 1
        else -> 2
    }

    AnimatedContent(
        targetState = rootStage,
        transitionSpec = {
            fadeIn(animationSpec = tween(260)) togetherWith fadeOut(animationSpec = tween(200))
        },
        label = "rootStageTransition"
    ) { stage ->
        when (stage) {
            0 -> {
                StartupLoadingScreen(
                    diagnostic = uiState.startupDiagnostic,
                    onRetryScan = viewModel::runStartupInternetDiagnostic,
                    onSmartConnectAndEnter = {
                        val rec = uiState.startupDiagnostic.recommendation
                        viewModel.dismissStartupLoading(markOnboardingSeen = true)
                        if (rec == StartupActionRecommendation.GENERATE_AND_CONNECT_WARP) {
                            viewModel.generateAndSelectWarp {
                                val s = latestConnState
                                if (s == ConnectionState.Disconnected || s is ConnectionState.Error) {
                                    latestToggleConnection()
                                }
                            }
                        } else if (rec == StartupActionRecommendation.CONNECT_SELECTED_SERVER ||
                            rec == StartupActionRecommendation.CONNECT_ANTI_DPI_DOH
                        ) {
                            val s = latestConnState
                            if (s == ConnectionState.Disconnected || s is ConnectionState.Error) {
                                latestToggleConnection()
                            }
                        }
                    },
                    onEnterAppOnly = {
                        viewModel.dismissStartupLoading(markOnboardingSeen = false)
                    }
                )
                return@AnimatedContent
            }
            1 -> {
                WelcomeOnboardingScreen(
                    onGetStarted = viewModel::dismissOnboarding
                )
                return@AnimatedContent
            }
        }

        val navController = rememberNavController()
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(uiState.bannerMessage) {
            val msg = uiState.bannerMessage
            if (msg != null) {
                snackbarHostState.showSnackbar(msg)
                viewModel.dismissBanner()
            }
        }

        val tabs = remember {
            listOf(NavTab.Connect, NavTab.AiHub, NavTab.Profiles, NavTab.SplitTunnel, NavTab.Tools)
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
                        SeparGlassBottomBar(
                            tabs = tabs,
                            currentRoute = currentDestination,
                            onSelectTab = navigateToTab
                        )
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
                                    onCheckExitIp = viewModel::checkExitIp,
                                    onQuickWarpConnect = {
                                        viewModel.generateAndSelectWarp {
                                            val s = latestConnState
                                            if (s == ConnectionState.Disconnected || s is ConnectionState.Error) {
                                                latestToggleConnection()
                                            }
                                        }
                                    }
                                )
                            }
                            composable(NavTab.AiHub.route) {
                                AiHubScreen(
                                    uiState = uiState,
                                    onRunSelfUpgrade = viewModel::runAiSelfUpgrade,
                                    onAutoDiscoverConnect = {
                                        viewModel.prepareAiAutoDiscoveryMode()
                                        val s = latestConnState
                                        if (s == ConnectionState.Disconnected || s is ConnectionState.Error) {
                                            latestToggleConnection()
                                        }
                                    },
                                    onSelectPersona = viewModel::selectAiPersona,
                                    onSendChatMessage = viewModel::sendAiChatMessage,
                                    onClearChat = viewModel::clearAiChat,
                                    onDismissAiError = viewModel::dismissAiError
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
                                    onDeleteProfile = viewModel::deleteProfile,
                                    onGenerateWarp = { viewModel.generateAndSelectWarp() },
                                    onImportPublicFeed = viewModel::importPublicCommunityFeed,
                                    onOpenGitHubBuilder = {
                                        navController.navigate("github_builder") {
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }
                            composable("github_builder") {
                                GitHubServerScreen(
                                    uiState = uiState,
                                    onBack = { navController.popBackStack() },
                                    onDeployServer = viewModel::deployAndStartGitHubVpnServer,
                                    onFetchExistingServer = viewModel::fetchExistingGitHubLiveServer,
                                    onStopServer = viewModel::stopGitHubVpnServer,
                                    onConnectNow = {
                                        val s = latestConnState
                                        if (s == ConnectionState.Disconnected || s is ConnectionState.Error) {
                                            latestToggleConnection()
                                        }
                                        navController.navigate(NavTab.Connect.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    onCreateGist = viewModel::createOrUpdateGitHubGist,
                                    onSearchRepos = viewModel::searchAndImportFromGitHub,
                                    onDismissError = viewModel::dismissGitHubError
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
                                    onToggleMultiplex = viewModel::setMultiplexEnabled,
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
}

@Composable
private fun SeparGlassBottomBar(
    tabs: List<NavTab>,
    currentRoute: androidx.navigation.NavDestination?,
    onSelectTab: (NavTab) -> Unit
) {
    val barShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(barShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF101C42),
                        Color(0xFF070D21)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        ObsidianCardBorder.copy(alpha = 0.4f),
                        SkyCyanGlow.copy(alpha = 0.45f),
                        ObsidianCardBorder.copy(alpha = 0.4f)
                    )
                ),
                shape = barShape
            )
            .navigationBarsPadding()
            .padding(horizontal = 6.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val selected = currentRoute?.hierarchy?.any { it.route == tab.route } == true
                val interactionSource = remember { MutableInteractionSource() }
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = ripple(bounded = true, color = SkyCyanGlow),
                            onClick = { onSelectTab(tab) }
                        )
                        .minimumInteractiveComponentSize()
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .testTag("nav_${tab.route}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .then(
                                if (selected) {
                                    Modifier
                                        .background(
                                            Brush.radialGradient(
                                                colors = listOf(
                                                    SkyCyanGlow.copy(alpha = 0.9f),
                                                    ElectricBlue.copy(alpha = 0.78f),
                                                    ElectricBlueDeep.copy(alpha = 0.65f)
                                                )
                                            )
                                        )
                                        .border(1.5.dp, Color(0xFFA8ECFF), CircleShape)
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = tab.label,
                            tint = if (selected) Color.White else TextMuted,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (selected) Color.White else TextMuted,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
