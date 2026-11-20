package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddLink
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.ProxyProfile
import com.example.model.ProxyProtocol
import com.example.ui.MainUiState
import com.example.ui.components.GlassCard
import com.example.ui.components.LtrText
import com.example.ui.theme.CoralError
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.OnEmerald
import com.example.ui.theme.SaffronAmber
import com.example.ui.theme.SaffronContainer
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ProfilesScreen(
    uiState: MainUiState,
    onPasteImport: (String) -> Unit,
    onAddManualProxy: (String, String, String, ProxyProtocol, String, String) -> Unit,
    onSelectProfile: (ProxyProfile) -> Unit,
    onTestProfile: (ProxyProfile) -> Unit,
    onTestAll: () -> Unit,
    onDeleteProfile: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    var showAddDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "سرورها و اشتراک‌ها",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary
            )
            Text(
                text = "پشتیبانی از SOCKS5، HTTP و وارد کردن لینک‌های VLESS / VMess / Trojan / SS و اشتراک",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val clip = clipboardManager.getText()?.text.orEmpty()
                        onPasteImport(clip)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("paste_clipboard_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldPrimary,
                        contentColor = OnEmerald
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentPaste,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("از کلیپ‌بورد", maxLines = 1)
                }

                OutlinedButton(
                    onClick = onTestAll,
                    enabled = !uiState.isTestingProfiles && uiState.profiles.isNotEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("test_all_pings_button"),
                    border = BorderStroke(1.dp, ObsidianCardBorder),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (uiState.isTestingProfiles) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = EmeraldPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Speed,
                            contentDescription = null,
                            tint = CyanSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تست پینگ همه", color = TextPrimary, maxLines = 1)
                }
            }

            if (uiState.isImportingSubscription) {
                Spacer(modifier = Modifier.height(10.dp))
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = EmeraldPrimary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "در حال دریافت و رمزگشایی لینک اشتراک…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (uiState.profiles.isEmpty()) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Router,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "هنوز سروری اضافه نشده است",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "می‌توانید لینک اشتراک، کانفیگ VLESS / VMess / Trojan / SS یا سرور SOCKS5 / HTTP خود را اضافه کنید. بدون سرور نیز حالت «ضد فیلتر هوشمند TLS + DoH» در صفحه اتصال کاملاً فعال است.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = { showAddDialog = true },
                            border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AddLink,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("افزودن لینک یا سرور دستی", color = EmeraldPrimary)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 84.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.profiles, key = { it.id }) { profile ->
                        ProfileCard(
                            profile = profile,
                            onSelect = { onSelectProfile(profile) },
                            onTest = { onTestProfile(profile) },
                            onDelete = { onDeleteProfile(profile.id) }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = EmeraldPrimary,
            contentColor = OnEmerald,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("add_profile_fab")
        ) {
            Icon(imageVector = Icons.Rounded.Add, contentDescription = "افزودن سرور یا اشتراک")
        }
    }

    if (showAddDialog) {
        AddProfileDialog(
            onDismiss = { showAddDialog = false },
            onImportLink = { text ->
                onPasteImport(text)
                showAddDialog = false
            },
            onAddManual = { name, host, port, proto, user, pass ->
                onAddManualProxy(name, host, port, proto, user, pass)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun ProfileCard(
    profile: ProxyProfile,
    onSelect: () -> Unit,
    onTest: () -> Unit,
    onDelete: () -> Unit
) {
    val borderColor = if (profile.isSelected) EmeraldPrimary else ObsidianCardBorder
    val bgColor = if (profile.isSelected) EmeraldContainer.copy(alpha = 0.35f) else ObsidianSurface

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .testTag("profile_card_${profile.id}"),
        borderColor = borderColor,
        containerColor = bgColor,
        cornerRadius = 18.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (profile.protocol.supportsDirectTunnel) EmeraldContainer else CyanContainer
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        LtrText(
                            text = profile.protocol.displayName,
                            color = if (profile.protocol.supportsDirectTunnel) EmeraldPrimary else CyanSecondary,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (profile.isSelected) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "فعال",
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                LtrText(
                    text = "${profile.host}:${profile.port}" +
                        if (profile.sni.isNotBlank()) "  •  SNI: ${profile.sni}" else "",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val badgeText = if (profile.protocol.supportsDirectTunnel) {
                        "تونل مستقیم در اپ"
                    } else {
                        "پروفایل Xray (${profile.transport}/${profile.security})"
                    }
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (profile.protocol.supportsDirectTunnel) EmeraldPrimary else TextMuted
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                val (pingText, pingColor, pingBg) = when {
                    profile.latencyMs == null -> Triple("-- ms", TextMuted, ObsidianSurfaceElevated)
                    profile.latencyMs < 0 -> Triple("Timeout", CoralError, ObsidianSurfaceElevated)
                    profile.latencyMs < 250 -> Triple("${profile.latencyMs} ms", EmeraldPrimary, EmeraldContainer)
                    else -> Triple("${profile.latencyMs} ms", SaffronAmber, SaffronContainer)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(pingBg)
                        .border(1.dp, pingColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .clickable(onClick = onTest)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                        .testTag("ping_button_${profile.id}")
                ) {
                    LtrText(
                        text = pingText,
                        color = pingColor,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_profile_${profile.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "حذف پروفایل",
                        tint = TextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun AddProfileDialog(
    onDismiss: () -> Unit,
    onImportLink: (String) -> Unit,
    onAddManual: (String, String, String, ProxyProtocol, String, String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var rawLinkInput by remember { mutableStateOf("") }

    var manualName by remember { mutableStateOf("") }
    var manualHost by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf("1080") }
    var manualProtocol by remember { mutableStateOf(ProxyProtocol.SOCKS5) }
    var manualUser by remember { mutableStateOf("") }
    var manualPass by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = {
            Text(
                text = "افزودن سرور یا اشتراک",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = ObsidianSurfaceElevated,
                    contentColor = EmeraldPrimary
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("لینک / اشتراک") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("سرور دستی") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (selectedTab == 0) {
                    OutlinedTextField(
                        value = rawLinkInput,
                        onValueChange = { rawLinkInput = it },
                        label = { Text("لینک کانفیگ یا Subscription URL") },
                        placeholder = { Text("vless://... یا https://sub.example.com/...") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_uri_input")
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = manualProtocol == ProxyProtocol.SOCKS5,
                            onClick = {
                                manualProtocol = ProxyProtocol.SOCKS5
                                if (manualPort == "8080") manualPort = "1080"
                            },
                            label = { Text("SOCKS5") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldContainer,
                                selectedLabelColor = EmeraldPrimary
                            )
                        )
                        FilterChip(
                            selected = manualProtocol == ProxyProtocol.HTTP,
                            onClick = {
                                manualProtocol = ProxyProtocol.HTTP
                                if (manualPort == "1080") manualPort = "8080"
                            },
                            label = { Text("HTTP") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldContainer,
                                selectedLabelColor = EmeraldPrimary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = manualName,
                        onValueChange = { manualName = it },
                        label = { Text("نام دلخواه (اختیاری)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = manualHost,
                            onValueChange = { manualHost = it },
                            label = { Text("آدرس IP یا دامنه") },
                            singleLine = true,
                            modifier = Modifier.weight(2f)
                        )
                        OutlinedTextField(
                            value = manualPort,
                            onValueChange = { manualPort = it },
                            label = { Text("پورت") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = manualUser,
                            onValueChange = { manualUser = it },
                            label = { Text("نام کاربری") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = manualPass,
                            onValueChange = { manualPass = it },
                            label = { Text("رمز عبور") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedTab == 0) {
                        onImportLink(rawLinkInput)
                    } else {
                        onAddManual(manualName, manualHost, manualPort, manualProtocol, manualUser, manualPass)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldPrimary,
                    contentColor = OnEmerald
                ),
                modifier = Modifier.testTag("confirm_add_profile")
            ) {
                Text("ذخیره")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف", color = TextSecondary)
            }
        }
    )
}
