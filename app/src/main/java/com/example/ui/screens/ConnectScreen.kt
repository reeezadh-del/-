package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.ConnectionState
import com.example.model.VpnMode
import com.example.ui.MainUiState
import com.example.ui.components.FormatUtils
import com.example.ui.components.GlassCard
import com.example.ui.components.LtrText
import com.example.ui.components.RadarConnectOrb
import com.example.ui.components.StatusPill
import com.example.ui.theme.CoralError
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.SaffronAmber
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun ConnectScreen(
    uiState: MainUiState,
    onToggleConnection: () -> Unit,
    onSelectMode: (VpnMode) -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenTools: () -> Unit,
    onCheckExitIp: () -> Unit,
    modifier: Modifier = Modifier
) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val isConnected = uiState.telemetry.state == ConnectionState.Connected

    LaunchedEffect(isConnected) {
        while (isConnected) {
            nowMs = System.currentTimeMillis()
            delay(1000L)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Subtle generated Persian geometric backdrop
        Image(
            painter = painterResource(id = R.drawable.img_cyber_persian_bg_1795145565659),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.32f)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ObsidianBg.copy(alpha = 0.55f),
                            ObsidianBg.copy(alpha = 0.85f),
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
            // Header Brand + Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(EmeraldContainer)
                            .border(1.dp, EmeraldPrimary.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "سپر نت",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = "تونل هوشمند ضد محدودیت",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                StatusPill(state = uiState.telemetry.state)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Central Animated Orb
            RadarConnectOrb(
                state = uiState.telemetry.state,
                onClick = onToggleConnection
            )

            val connState = uiState.telemetry.state
            AnimatedVisibility(visible = connState is ConnectionState.Error) {
                if (connState is ConnectionState.Error) {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        borderColor = CoralError.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = connState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CoralError
                        )
                    }
                }
            }

            // Active Endpoint / DNS Quick Bar
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 18.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onOpenTools)
                            .padding(vertical = 4.dp)
                            .testTag("quick_dns_chip")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Dns,
                            contentDescription = "تنظیمات DNS",
                            tint = CyanSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "DNS رمزنگاری‌شده",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                            Text(
                                text = uiState.settings.resolveActiveDnsLabel(),
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(34.dp)
                            .background(ObsidianCardBorder)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onOpenProfiles)
                            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
                            .testTag("quick_profile_chip")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Router,
                            contentDescription = "انتخاب سرور",
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "پروفایل سرور",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                            Text(
                                text = uiState.selectedProfile?.name ?: "بدون سرور (مستقیم)",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Real Exit IP & Location Verification Card
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 18.dp,
                borderColor = if (uiState.selectedProfile == null && uiState.settings.vpnMode != VpnMode.PROXY_CHAIN) {
                    SaffronAmber.copy(alpha = 0.45f)
                } else {
                    ObsidianCardBorder
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "آی‌پی و موقعیت فعلی خروج",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        val ipInfo = uiState.exitIpInfo
                        if (ipInfo != null) {
                            LtrText(
                                text = "${ipInfo.ip}  •  ${ipInfo.country} (${ipInfo.countryCode})",
                                color = if (ipInfo.countryCode.equals("IR", ignoreCase = true)) SaffronAmber else EmeraldPrimary,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            if (ipInfo.isp.isNotBlank()) {
                                LtrText(
                                    text = ipInfo.isp,
                                    color = TextMuted,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        } else {
                            Text(
                                text = "برای اطمینان از تغییر کشور و IP روی دکمه بررسی بزنید",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedButton(
                        onClick = onCheckExitIp,
                        enabled = !uiState.isCheckingExitIp,
                        border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("check_exit_ip_button")
                    ) {
                        if (uiState.isCheckingExitIp) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = EmeraldPrimary
                            )
                        } else {
                            Text("بررسی IP", color = EmeraldPrimary)
                        }
                    }
                }

                if (uiState.selectedProfile == null || uiState.settings.vpnMode != VpnMode.PROXY_CHAIN) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ObsidianSurfaceElevated)
                            .clickable(onClick = onOpenProfiles)
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = SaffronAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "سرور خارجی انتخاب نشده است — در حالت فعلی لوکیشن ایران باقی می‌ماند و سایت‌های دارای مسدودیت IP باز نمی‌شوند.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SaffronAmber
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onOpenProfiles,
                            border = BorderStroke(1.dp, SaffronAmber.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("go_to_servers_cta")
                        ) {
                            Text("افزودن یا انتخاب سرور خارجی (VLESS / VMess / Trojan)", color = SaffronAmber)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2x2 Live Telemetry Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TelemetryTile(
                    icon = Icons.Rounded.ArrowDownward,
                    iconTint = EmeraldPrimary,
                    label = "دریافت",
                    value = FormatUtils.formatBytes(uiState.telemetry.rxBytes),
                    subValue = FormatUtils.formatSpeed(uiState.telemetry.rxSpeedBps),
                    modifier = Modifier.weight(1f)
                )
                TelemetryTile(
                    icon = Icons.Rounded.ArrowUpward,
                    iconTint = CyanSecondary,
                    label = "ارسال",
                    value = FormatUtils.formatBytes(uiState.telemetry.txBytes),
                    subValue = FormatUtils.formatSpeed(uiState.telemetry.txSpeedBps),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TelemetryTile(
                    icon = Icons.Outlined.Security,
                    iconTint = SaffronAmber,
                    label = "درخواست DoH / TLS",
                    value = "${uiState.telemetry.dnsQueriesResolved} DNS",
                    subValue = "${uiState.telemetry.fragmentedConnections} TLS Fragment",
                    modifier = Modifier.weight(1f)
                )
                TelemetryTile(
                    icon = Icons.Rounded.Timer,
                    iconTint = TextSecondary,
                    label = "مدت اتصال",
                    value = FormatUtils.formatDuration(uiState.telemetry.connectedSinceMs, nowMs),
                    subValue = "استثنا: ${uiState.settings.bypassedPackages.size} اپ",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Connection Mode Picker
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Public,
                        contentDescription = null,
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "حالت عبور از فیلترینگ",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }

                VpnMode.entries.forEach { mode ->
                    val selected = uiState.settings.vpnMode == mode
                    val proxyWarning = mode == VpnMode.PROXY_CHAIN &&
                        (uiState.selectedProfile == null || !uiState.selectedProfile.protocol.supportsDirectTunnel)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (selected) EmeraldContainer.copy(alpha = 0.55f)
                                else ObsidianSurfaceElevated.copy(alpha = 0.45f)
                            )
                            .border(
                                1.dp,
                                if (selected) EmeraldPrimary.copy(alpha = 0.5f) else Color.Transparent,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { onSelectMode(mode) }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("vpn_mode_${mode.name}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { onSelectMode(mode) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = EmeraldPrimary,
                                unselectedColor = TextMuted
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mode.titleFa,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (selected) EmeraldPrimary else TextPrimary
                            )
                            Text(
                                text = if (proxyWarning) {
                                    "ابتدا یک سرور VLESS / VMess / Trojan / SS / SOCKS5 از تب «سرورها» انتخاب کنید"
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

            Spacer(modifier = Modifier.height(12.dp))

            // Helpful Iran Usage Tip
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = ObsidianSurfaceElevated.copy(alpha = 0.65f)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = CyanSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "نکته کاربردی: در تب «برنامه‌ها» می‌توانید اپ‌های بانکی، اسنپ، بلد و پیام‌رسان‌های داخلی را با یک کلیک استثنا کنید تا هم‌زمان با VPN بدون اختلال کار کنند.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TelemetryTile(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    value: String,
    subValue: String,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier,
        cornerRadius = 16.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LtrText(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = com.example.ui.theme.JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold
            ),
            color = TextPrimary
        )
        LtrText(
            text = subValue,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
    }
}
