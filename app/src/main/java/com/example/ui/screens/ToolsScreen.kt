package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.DnsPreset
import com.example.ui.MainUiState
import com.example.ui.components.GlassCard
import com.example.ui.components.LtrText
import com.example.ui.theme.CoralError
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.OnEmerald
import com.example.ui.theme.SaffronAmber
import com.example.ui.theme.SaffronContainer
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ToolsScreen(
    uiState: MainUiState,
    onSelectDnsPreset: (String) -> Unit,
    onSaveCustomDoh: (String) -> Unit,
    onToggleTlsFragment: (Boolean) -> Unit,
    onToggleMultiplex: (Boolean) -> Unit,
    onUpdateFragmentParams: (Int, Int) -> Unit,
    onRunNetworkScan: () -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    var customDohInput by remember(uiState.settings.customDohUrl) {
        mutableStateOf(uiState.settings.customDohUrl)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "ابزارهای شبکه و تنظیمات",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary
            )

            // 1. Anti-DPI TLS Fragmentation Controls
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Security,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "تکه‌تکه‌سازی TLS (Anti-DPI)",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "قطعه‌بندی بسته ClientHello برای عبور از فیلترینگ SNI",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    Switch(
                        checked = uiState.settings.tlsFragmentEnabled,
                        onCheckedChange = onToggleTlsFragment,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = OnEmerald,
                            checkedTrackColor = EmeraldPrimary
                        ),
                        modifier = Modifier.testTag("switch_tls_fragment")
                    )
                }

                if (uiState.settings.tlsFragmentEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "اندازه هر قطعه (Chunk Size)",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        LtrText(
                            text = "${uiState.settings.fragmentSizeBytes} bytes",
                            color = EmeraldPrimary
                        )
                    }
                    Slider(
                        value = uiState.settings.fragmentSizeBytes.toFloat(),
                        onValueChange = {
                            onUpdateFragmentParams(it.toInt(), uiState.settings.fragmentDelayMs)
                        },
                        valueRange = 8f..80f,
                        colors = SliderDefaults.colors(
                            thumbColor = EmeraldPrimary,
                            activeTrackColor = EmeraldPrimary
                        ),
                        modifier = Modifier.testTag("slider_fragment_size")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "تاخیر بین قطعات (Jitter Delay)",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        LtrText(
                            text = "${uiState.settings.fragmentDelayMs} ms",
                            color = CyanSecondary
                        )
                    }
                    Slider(
                        value = uiState.settings.fragmentDelayMs.toFloat(),
                        onValueChange = {
                            onUpdateFragmentParams(uiState.settings.fragmentSizeBytes, it.toInt())
                        },
                        valueRange = 0f..50f,
                        colors = SliderDefaults.colors(
                            thumbColor = CyanSecondary,
                            activeTrackColor = CyanSecondary
                        ),
                        modifier = Modifier.testTag("slider_fragment_delay")
                    )
                }
            }

            // 1.5 Multiplex (H2Mux + Padding) Control
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "تجمیع و پدینگ بسته‌ها (H2Mux + Padding)",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "تغییر الگوی اندازه بسته‌ها و کاهش تاخیر هندشیک روی VLESS / VMess / Trojan / SS",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = uiState.settings.multiplexEnabled,
                        onCheckedChange = onToggleMultiplex,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = OnEmerald,
                            checkedTrackColor = EmeraldPrimary
                        ),
                        modifier = Modifier.testTag("switch_multiplex")
                    )
                }
            }

            // 2. Clean IP & Network Health Scanner
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Outlined.NetworkCheck,
                            contentDescription = null,
                            tint = CyanSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "اسکنر واقعی آی‌پی تمیز و DoH",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "تست پینگ TCP و هندشیک TLS روی اینترنت فعلی شما",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    Button(
                        onClick = onRunNetworkScan,
                        enabled = !uiState.isScanningNetwork,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldPrimary,
                            contentColor = OnEmerald
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("run_clean_ip_scan_button")
                    ) {
                        if (uiState.isScanningNetwork) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = OnEmerald
                            )
                        } else {
                            Text("شروع تست")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                uiState.scanResults.forEach { result ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ObsidianSurfaceElevated.copy(alpha = 0.6f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            LtrText(
                                text = result.label,
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleSmall
                            )
                            LtrText(
                                text = "${result.host}:${result.port} • ${result.category}",
                                color = TextMuted,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        when {
                            result.isTesting -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = CyanSecondary
                                )
                            }
                            result.isReachable == true -> {
                                Column(horizontalAlignment = Alignment.End) {
                                    val tcp = result.tcpLatencyMs ?: 0L
                                    val tls = result.tlsLatencyMs ?: 0L
                                    LtrText(
                                        text = "TCP ${tcp}ms",
                                        color = if (tcp < 220) EmeraldPrimary else SaffronAmber,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    LtrText(
                                        text = "TLS ${tls}ms",
                                        color = CyanSecondary,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                            result.isReachable == false -> {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SaffronContainer)
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "مسدود / Timeout",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CoralError
                                    )
                                }
                            }
                            else -> {
                                LtrText(text = "آماده تست", color = TextMuted)
                            }
                        }
                    }
                }
            }

            // 3. DoH Resolver Selection
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Dns,
                        contentDescription = null,
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "انتخاب سرور DNS رمزنگاری‌شده (DoH)",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                DnsPreset.ALL.forEach { preset ->
                    val selected = uiState.settings.dnsPresetId == preset.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) EmeraldContainer.copy(alpha = 0.45f)
                                else ObsidianSurfaceElevated.copy(alpha = 0.4f)
                            )
                            .border(
                                1.dp,
                                if (selected) EmeraldPrimary.copy(alpha = 0.45f) else Color.Transparent,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelectDnsPreset(preset.id) }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag("dns_preset_${preset.id}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { onSelectDnsPreset(preset.id) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = EmeraldPrimary,
                                unselectedColor = TextMuted
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = preset.nameFa,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (selected) EmeraldPrimary else TextPrimary
                            )
                            Text(
                                text = preset.descriptionFa,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            LtrText(
                                text = preset.dohUrl,
                                color = TextMuted,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customDohInput,
                        onValueChange = { customDohInput = it },
                        label = { Text("آدرس DoH سفارشی") },
                        placeholder = { Text("https://...") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_doh_input")
                    )
                    OutlinedButton(
                        onClick = { onSaveCustomDoh(customDohInput) },
                        border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("save_custom_doh_button")
                    ) {
                        Text("ذخیره", color = EmeraldPrimary)
                    }
                }
            }

            // 4. Live Tunnel Diagnostics Log
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Terminal,
                            contentDescription = null,
                            tint = SaffronAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "گزارش لحظه‌ای تونل",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }

                    IconButton(
                        onClick = onClearLogs,
                        modifier = Modifier.testTag("clear_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteSweep,
                            contentDescription = "پاک کردن گزارش‌ها",
                            tint = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ObsidianBg)
                        .border(1.dp, ObsidianCardBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    if (uiState.logs.isEmpty()) {
                        Text(
                            text = "هنوز رویدادی ثبت نشده است. با برقراری اتصال، گزارش‌های DNS و تکه‌تکه‌سازی در اینجا نمایش داده می‌شوند.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    } else {
                        val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            uiState.logs.take(18).forEach { entry ->
                                val timeStr = timeFormatter.format(Date(entry.timestampMs))
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    LtrText(
                                        text = "[$timeStr][${entry.tag}]",
                                        color = if (entry.isError) CoralError else CyanSecondary,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = entry.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (entry.isError) CoralError else TextSecondary,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
