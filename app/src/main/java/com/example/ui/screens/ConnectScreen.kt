package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.ConnectionState
import com.example.model.ProxyProtocol
import com.example.model.VpnMode
import com.example.ui.MainUiState
import com.example.ui.components.FormatUtils
import com.example.ui.components.GlassCard
import com.example.ui.components.LtrText
import com.example.ui.components.RadarConnectOrb
import com.example.ui.theme.ConnectedGreen
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBlueDeep
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.SaffronAmber
import com.example.ui.theme.SkyCyanGlow
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
    uiState: MainUiState,
    onToggleConnection: () -> Unit,
    onSelectMode: (VpnMode) -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenTools: () -> Unit,
    onCheckExitIp: () -> Unit,
    onQuickWarpConnect: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showModeSheet by remember { mutableStateOf(false) }
    val connState = uiState.telemetry.state
    val isConnected = connState == ConnectionState.Connected
    val isConnecting = connState == ConnectionState.Connecting
    val isDisconnecting = connState == ConnectionState.Disconnecting

    LaunchedEffect(isConnected) {
        while (isConnected) {
            nowMs = System.currentTimeMillis()
            delay(1000L)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.img_cyber_persian_bg_1795145565659),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.07f)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0A1638).copy(alpha = 0.92f),
                            ObsidianBg.copy(alpha = 0.96f),
                            ObsidianBg
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .widthIn(max = 600.dp)
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Clean Top Header: Status Badge + Quick WARP Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val shieldBg = when (connState) {
                        ConnectionState.Connected -> Color(0xFF0E3330)
                        ConnectionState.Connecting, ConnectionState.Disconnecting -> Color(0xFF362A0B)
                        is ConnectionState.Error -> Color(0xFF3B1321)
                        ConnectionState.Disconnected -> Color(0xFF101C42)
                    }
                    val shieldTint = when (connState) {
                        ConnectionState.Connected -> ConnectedGreen
                        ConnectionState.Connecting, ConnectionState.Disconnecting -> SaffronAmber
                        is ConnectionState.Error -> CoralError
                        ConnectionState.Disconnected -> SkyCyanGlow
                    }
                    val statusHeadline = when (connState) {
                        ConnectionState.Connected -> "متصل و ایمن"
                        ConnectionState.Connecting -> "در حال اتصال…"
                        ConnectionState.Disconnecting -> "در حال قطع…"
                        is ConnectionState.Error -> "اتصال ناموفق"
                        ConnectionState.Disconnected -> "متصل نیست"
                    }

                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(shieldBg)
                            .border(1.dp, shieldTint.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isConnected) Icons.Rounded.VerifiedUser else Icons.Outlined.Shield,
                            contentDescription = "وضعیت سپر نت",
                            tint = shieldTint,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = statusHeadline,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isConnected) ConnectedGreen else TextPrimary
                        )
                        if (isConnected) {
                            LtrText(
                                text = FormatUtils.formatDuration(uiState.telemetry.connectedSinceMs, nowMs),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily
                                ),
                                color = TextSecondary
                            )
                        } else {
                            Text(
                                text = "سپر نت",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Surface(
                    onClick = onQuickWarpConnect,
                    enabled = !uiState.isGeneratingWarp,
                    shape = RoundedCornerShape(50),
                    color = Color.Transparent,
                    border = BorderStroke(
                        1.dp,
                        Brush.horizontalGradient(
                            colors = listOf(ElectricBlueDeep, SkyCyanGlow.copy(alpha = 0.6f))
                        )
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("quick_warp_connect_button")
                ) {
                    Row(
                        modifier = Modifier
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF0F1B42), Color(0xFF1A3170))
                                )
                            )
                            .padding(start = 12.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "WARP سریع",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(SkyCyanGlow, ElectricBlue, ElectricBlueDeep)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (uiState.isGeneratingWarp) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Bolt,
                                    contentDescription = "ساخت و اتصال فوری WARP",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Live Upload & Download Speed Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SpeedStatPillCard(
                    icon = Icons.Rounded.ArrowUpward,
                    accentColor = SkyCyanGlow,
                    label = "آپلود",
                    speedText = FormatUtils.formatSpeed(uiState.telemetry.txSpeedBps),
                    totalText = FormatUtils.formatBytes(uiState.telemetry.txBytes),
                    modifier = Modifier.weight(1f)
                )
                SpeedStatPillCard(
                    icon = Icons.Rounded.ArrowDownward,
                    accentColor = ConnectedGreen,
                    label = "دانلود",
                    speedText = FormatUtils.formatSpeed(uiState.telemetry.rxSpeedBps),
                    totalText = FormatUtils.formatBytes(uiState.telemetry.rxBytes),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 3. Hero Connect Orb + Compact Material 3 Switch Bar
            RadarConnectOrb(
                state = connState,
                onClick = onToggleConnection,
                orbSize = 224.dp
            )

            val switchChecked = isConnected || isConnecting
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(20.dp),
                color = ObsidianSurface.copy(alpha = 0.94f),
                border = BorderStroke(
                    1.dp,
                    when {
                        isConnected -> ConnectedGreen.copy(alpha = 0.55f)
                        isConnecting -> SaffronAmber.copy(alpha = 0.55f)
                        else -> ElectricBlue.copy(alpha = 0.45f)
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onToggleConnection,
                        enabled = !isDisconnecting,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when {
                                isConnected -> Color(0xFFB4233C)
                                isConnecting -> SaffronAmber
                                else -> ElectricBlue
                            },
                            contentColor = if (isConnecting) Color(0xFF1A1200) else Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("vpn_switch_button")
                    ) {
                        if (isConnecting || isDisconnecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = if (isConnecting) Color(0xFF1A1200) else Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isDisconnecting) "در حال قطع…" else "لغو اتصال",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.PowerSettingsNew,
                                contentDescription = if (isConnected) "قطع اتصال VPN" else "اتصال به VPN",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isConnected) "قطع اتصال" else "اتصال امن",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Switch(
                        checked = switchChecked,
                        onCheckedChange = { onToggleConnection() },
                        enabled = !isDisconnecting,
                        thumbContent = {
                            Icon(
                                imageVector = if (switchChecked) Icons.Rounded.Check else Icons.Rounded.Close,
                                contentDescription = if (switchChecked) "سرویس VPN روشن" else "سرویس VPN خاموش",
                                modifier = Modifier.size(SwitchDefaults.IconSize)
                            )
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = if (isConnected) ConnectedGreen else SaffronAmber,
                            checkedIconColor = if (isConnected) Color(0xFF063B2C) else Color(0xFF362A0B),
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = ObsidianSurfaceElevated,
                            uncheckedBorderColor = ObsidianCardBorder,
                            uncheckedIconColor = ObsidianBg
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("vpn_master_switch")
                    )
                }
            }

            // Error Banner (only visible when an error happens)
            AnimatedVisibility(visible = connState is ConnectionState.Error) {
                if (connState is ConnectionState.Error) {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        borderColor = CoralError.copy(alpha = 0.55f)
                    ) {
                        Text(
                            text = connState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CoralError
                        )
                    }
                }
            }

            // 4. Single Unified Connection Card (Server + Mode Selector + Ping / DNS / Exit IP)
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                containerColor = ObsidianSurface.copy(alpha = 0.92f)
            ) {
                // Active Server Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF15234B))
                        .border(1.dp, ObsidianCardBorder, RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenProfiles)
                        .minimumInteractiveComponentSize()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("quick_profile_chip"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        ElectricBlue.copy(alpha = 0.45f),
                                        ElectricBlueDeep.copy(alpha = 0.65f)
                                    )
                                )
                            )
                            .border(1.dp, SkyCyanGlow.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Public,
                            contentDescription = "انتخاب سرور",
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = uiState.selectedProfile?.name
                                ?: uiState.telemetry.activeServerLabel
                                ?: "انتخاب سرور (یا حالت خودکار)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val missingServerHint = uiState.settings.vpnMode == VpnMode.PROXY_CHAIN && uiState.selectedProfile == null
                        Text(
                            text = when {
                                uiState.selectedProfile != null -> "${uiState.selectedProfile.protocol.displayName} • ${uiState.selectedProfile.host}"
                                missingServerHint -> "برای حالت دستی، یک سرور اضافه کنید یا WARP سریع را بزنید"
                                else -> "کشف و اتصال خودکار از بهترین سرورها"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (missingServerHint) SaffronAmber else TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        contentDescription = "لیست سرورها",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Compact Mode Row (opens ModalBottomSheet instead of taking 6 cards on home screen)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ObsidianSurfaceElevated.copy(alpha = 0.5f))
                        .clickable { showModeSheet = true }
                        .minimumInteractiveComponentSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("vpn_mode_selector_row"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = "تغییر روش اتصال",
                        tint = SkyCyanGlow,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = uiState.settings.vpnMode.titleFa,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "تغییر",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = SkyCyanGlow
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = ObsidianCardBorder.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.height(10.dp))

                // Compact 3-Metric Footer: Ping | DNS | Exit IP
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Ping
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "پینگ",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        val latencyMs = uiState.selectedProfile?.latencyMs
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SignalBarsIndicator(latencyMs = latencyMs, isConnected = isConnected)
                            Spacer(modifier = Modifier.width(6.dp))
                            LtrText(
                                text = latencyMs?.takeIf { it > 0 }?.let { "$it MS" } ?: "-- MS",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = TextPrimary
                            )
                        }
                    }

                    // DNS Shortcut
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onOpenTools)
                            .minimumInteractiveComponentSize()
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag("quick_dns_chip"),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Dns,
                                contentDescription = "تنظیمات DNS",
                                tint = SkyCyanGlow,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "DNS",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = uiState.settings.resolveActiveDnsLabel()
                                .substringBefore(" (")
                                .trim(),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Exit IP Check
                    Column(
                        modifier = Modifier
                            .weight(1.1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(
                                enabled = !uiState.isCheckingExitIp,
                                onClick = onCheckExitIp
                            )
                            .minimumInteractiveComponentSize()
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                            .testTag("check_exit_ip_button"),
                        horizontalAlignment = Alignment.End
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "IP خروج",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            if (uiState.isCheckingExitIp) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(11.dp),
                                    strokeWidth = 1.6.dp,
                                    color = SkyCyanGlow
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "بررسی IP خروج",
                                    tint = SkyCyanGlow,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        val ipInfo = uiState.exitIpInfo
                        LtrText(
                            text = if (ipInfo != null) "${ipInfo.ip} (${ipInfo.countryCode})" else "بررسی IP",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = when {
                                ipInfo == null -> SkyCyanGlow
                                ipInfo.countryCode.equals("IR", ignoreCase = true) -> SaffronAmber
                                else -> ConnectedGreen
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (showModeSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showModeSheet = false },
            sheetState = sheetState,
            containerColor = ObsidianSurface,
            contentColor = TextPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    text = "انتخاب روش اتصال",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                VpnMode.entries.forEach { mode ->
                    val selected = uiState.settings.vpnMode == mode
                    val proxyWarning = (mode == VpnMode.PROXY_CHAIN && uiState.selectedProfile == null) ||
                        (mode == VpnMode.AUTO_BEST_SERVER && uiState.profiles.isEmpty()) ||
                        (mode == VpnMode.DOUBLE_WARP_CHAIN && uiState.profiles.none { it.protocol == ProxyProtocol.WIREGUARD })

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (selected) EmeraldContainer.copy(alpha = 0.72f)
                                else ObsidianSurfaceElevated.copy(alpha = 0.55f)
                            )
                            .border(
                                1.dp,
                                if (selected) ElectricBlue.copy(alpha = 0.55f) else Color.Transparent,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                onSelectMode(mode)
                                showModeSheet = false
                            }
                            .minimumInteractiveComponentSize()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("vpn_mode_${mode.name}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = {
                                onSelectMode(mode)
                                showModeSheet = false
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = SkyCyanGlow,
                                unselectedColor = TextMuted
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mode.titleFa,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (selected) SkyCyanGlow else TextPrimary
                            )
                            Text(
                                text = if (proxyWarning) {
                                    "نیازمند حداقل یک سرور در تب «سرورها»"
                                } else {
                                    mode.subtitleFa
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (proxyWarning) SaffronAmber else TextSecondary
                            )
                        }
                        if (selected) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "انتخاب شده",
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedStatPillCard(
    icon: ImageVector,
    accentColor: Color,
    label: String,
    speedText: String,
    totalText: String,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier,
        cornerRadius = 18.dp,
        containerColor = Color(0xFF0E1838).copy(alpha = 0.92f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF172756))
                    .border(1.dp, accentColor.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                LtrText(
                    text = speedText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = TextPrimary
                )
                LtrText(
                    text = totalText,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
private fun SignalBarsIndicator(
    latencyMs: Long?,
    isConnected: Boolean
) {
    val activeBars = when {
        latencyMs != null && latencyMs in 1..150 -> 5
        latencyMs != null && latencyMs in 151..350 -> 4
        latencyMs != null && latencyMs > 350 -> 2
        isConnected -> 4
        else -> 3
    }
    val activeColor = when {
        latencyMs != null && latencyMs > 400 -> SaffronAmber
        isConnected || (latencyMs != null && latencyMs > 0) -> ConnectedGreen
        else -> SkyCyanGlow
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        for (bar in 1..5) {
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height((6 + bar * 2).dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (bar <= activeBars) activeColor else TextMuted.copy(alpha = 0.45f)
                    )
            )
        }
    }
}
