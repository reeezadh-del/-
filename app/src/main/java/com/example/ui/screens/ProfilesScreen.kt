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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
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
import com.example.data.WarpProvisioner
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
    onGenerateWarp: () -> Unit = {},
    onImportPublicFeed: (WarpProvisioner.PublicFeedPreset) -> Unit = {},
    onOpenGitHubBuilder: () -> Unit = {},
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
                text = "تونل کامل با پشتیبانی از VLESS، VMess، Trojan، Hysteria 1/2، TUIC، WireGuard/WARP، ShadowTLS، NaiveProxy، SSH و AnyTLS",
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

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onGenerateWarp,
                enabled = !uiState.isGeneratingWarp,
                border = BorderStroke(1.dp, CyanSecondary.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("generate_warp_button")
            ) {
                if (uiState.isGeneratingWarp) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = CyanSecondary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = null,
                        tint = CyanSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text("ساخت اکانت رایگان Cloudflare WARP + اسکن آی‌پی تمیز", color = CyanSecondary)
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onOpenGitHubBuilder,
                border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.55f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_github_builder_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Code,
                    contentDescription = null,
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("ساخت سرور VPN با گیت‌هاب (Actions / Gist / Search)", color = EmeraldPrimary)
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
                            text = "می‌توانید لینک اشتراک، کانفیگ VLESS / Hysteria2 / TUIC / WireGuard (.conf) / SSH / AnyTLS یا سرور SOCKS5 / HTTP اضافه کنید. بدون سرور نیز حالت «ضد فیلتر هوشمند» فعال است.",
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
            },
            onSelectPublicFeed = { feed ->
                onImportPublicFeed(feed)
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
                    val details = buildString {
                        append("تونل کامل (${profile.transport}")
                        if (profile.security != "none") append("/${profile.security}")
                        append(")")
                    }
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall,
                        color = EmeraldPrimary
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
    onAddManual: (String, String, String, ProxyProtocol, String, String) -> Unit,
    onSelectPublicFeed: (WarpProvisioner.PublicFeedPreset) -> Unit
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
                        text = { Text("لینک", maxLines = 1) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("دستی", maxLines = 1) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("سرور آزاد", maxLines = 1) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                when (selectedTab) {
                    0 -> {
                        OutlinedTextField(
                            value = rawLinkInput,
                            onValueChange = { rawLinkInput = it },
                            label = { Text("لینک کانفیگ، WireGuard .conf یا Subscription") },
                            placeholder = { Text("vless:// hy2:// hysteria:// tuic:// wireguard:// shadowtls:// naive+https:// ssh:// anytls:// [Interface]...") },
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("import_uri_input")
                        )
                    }
                    1 -> {
                        val manualProtocols = listOf(
                            ProxyProtocol.SSH to "22",
                            ProxyProtocol.HYSTERIA2 to "443",
                            ProxyProtocol.HYSTERIA to "443",
                            ProxyProtocol.NAIVE to "443",
                            ProxyProtocol.SHADOWTLS to "443",
                            ProxyProtocol.ANYTLS to "443",
                            ProxyProtocol.SOCKS5 to "1080",
                            ProxyProtocol.HTTP to "8080"
                        )
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(manualProtocols.size) { index ->
                                val (proto, defaultPort) = manualProtocols[index]
                                FilterChip(
                                    selected = manualProtocol == proto,
                                    onClick = {
                                        manualProtocol = proto
                                        manualPort = defaultPort
                                    },
                                    label = { Text(proto.displayName) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = EmeraldContainer,
                                        selectedLabelColor = EmeraldPrimary
                                    )
                                )
                            }
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
                                label = { Text("نام کاربری / SNI") },
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
                    else -> {
                        Text(
                            text = "دریافت خودکار سرورهای عمومی و رایگان از مخازن متن‌باز گیت‌هاب:",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            WarpProvisioner.PUBLIC_COMMUNITY_FEEDS.forEach { feed ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(ObsidianSurfaceElevated)
                                        .border(1.dp, ObsidianCardBorder, RoundedCornerShape(12.dp))
                                        .clickable { onSelectPublicFeed(feed) }
                                        .padding(12.dp)
                                        .testTag("public_feed_${feed.id}")
                                ) {
                                    Text(
                                        text = feed.titleFa,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = EmeraldPrimary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = feed.subtitleFa,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (selectedTab != 2) {
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
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف", color = TextSecondary)
            }
        }
    )
}
