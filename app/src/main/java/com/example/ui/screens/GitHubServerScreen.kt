package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.GitHubVpnManager
import com.example.ui.MainUiState
import com.example.ui.components.GlassCard
import com.example.ui.components.LtrText
import com.example.ui.theme.ConnectedGreen
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBlueDeep
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

@Composable
fun GitHubServerScreen(
    uiState: MainUiState,
    onBack: () -> Unit,
    onDeployServer: (String, String, GitHubVpnManager.GitHubServerProtocol, String) -> Unit,
    onFetchExistingServer: (String, String) -> Unit,
    onStopServer: (String) -> Unit,
    onConnectNow: () -> Unit,
    onCreateGist: (String, Boolean) -> Unit,
    onSearchRepos: (String) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var tokenInput by rememberSaveable(uiState.settings.githubToken) {
        mutableStateOf(uiState.settings.githubToken)
    }
    var tokenVisible by rememberSaveable { mutableStateOf(false) }
    var repoInput by rememberSaveable(uiState.settings.githubRepo) {
        mutableStateOf(uiState.settings.githubRepo.ifBlank { "separ-vpn-node" })
    }
    var cleanIpInput by rememberSaveable { mutableStateOf("162.159.192.1") }
    var selectedProtocol by rememberSaveable { mutableStateOf(GitHubVpnManager.GitHubServerProtocol.VLESS_WS) }
    var includeWarpInGist by rememberSaveable { mutableStateOf(true) }
    var searchQuery by rememberSaveable { mutableStateOf("v2ray config vless") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A1638), ObsidianBg, ObsidianBg)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .widthIn(max = 620.dp)
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("github_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "بازگشت",
                        tint = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(SkyCyanGlow, ElectricBlue, ElectricBlueDeep)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Code,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "سازنده سرور VPN با گیت‌هاب",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Text(
                        text = "راه‌اندازی واقعی sing-box روی GitHub Actions + ساخت اشتراک Gist",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            // Error Banner
            AnimatedVisibility(visible = uiState.github.errorMessage != null) {
                val err = uiState.github.errorMessage
                if (err != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(CoralError.copy(alpha = 0.14f))
                            .border(1.dp, CoralError.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("github_error_banner"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = CoralError,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = CoralError,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onDismissError,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "بستن",
                                tint = CoralError,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Card 1: Real Cloud VPN Server on GitHub Actions
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                containerColor = ObsidianSurface.copy(alpha = 0.94f),
                borderColor = SkyCyanGlow.copy(alpha = 0.35f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "۱. سرور ابری زنده روی GitHub Actions",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                val url = "https://github.com/settings/tokens/new?scopes=repo,workflow,gist&description=SeparNet-VPN"
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        },
                        border = BorderStroke(1.dp, SkyCyanGlow.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            tint = SkyCyanGlow,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ساخت توکن", style = MaterialTheme.typography.labelSmall, color = SkyCyanGlow)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "یک سرور واقعی sing-box با تونل رمزنگاری‌شده WebSocket+TLS روی سرورهای گیت‌هاب ساخته می‌شود و لینک اختصاصی آن مستقیم به لیست سرورهای شما اضافه می‌گردد.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    label = { Text("توکن گیت‌هاب (Personal Access Token)") },
                    placeholder = { Text("ghp_... یا github_pat_...") },
                    singleLine = true,
                    visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { tokenVisible = !tokenVisible }) {
                            Icon(
                                imageVector = if (tokenVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = "نمایش/مخفی کردن توکن",
                                tint = TextMuted
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SkyCyanGlow,
                        unfocusedBorderColor = ObsidianCardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("github_token_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = repoInput,
                        onValueChange = { repoInput = it },
                        label = { Text("مخزن (repo یا user/repo)") },
                        placeholder = { Text("separ-vpn-node") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SkyCyanGlow,
                            unfocusedBorderColor = ObsidianCardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1.1f)
                            .testTag("github_repo_input")
                    )

                    OutlinedTextField(
                        value = cleanIpInput,
                        onValueChange = { cleanIpInput = it },
                        label = { Text("آی‌پی تمیز CF") },
                        placeholder = { Text("162.159.192.1") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SkyCyanGlow,
                            unfocusedBorderColor = ObsidianCardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(0.9f)
                            .testTag("github_clean_ip_input")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "پروتکل سرور:",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GitHubVpnManager.GitHubServerProtocol.entries.forEach { proto ->
                        val selected = selectedProtocol == proto
                        FilterChip(
                            selected = selected,
                            onClick = { selectedProtocol = proto },
                            label = { LtrText(text = proto.labelFa, style = MaterialTheme.typography.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricBlueDeep,
                                selectedLabelColor = Color.White,
                                containerColor = ObsidianSurfaceElevated,
                                labelColor = TextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = ObsidianCardBorder,
                                selectedBorderColor = SkyCyanGlow
                            ),
                            modifier = Modifier.minimumInteractiveComponentSize()
                        )
                    }
                }

                if (uiState.github.buildProgressMessage.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0E1D44))
                            .border(1.dp, SkyCyanGlow.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uiState.github.isBuildingServer) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = SkyCyanGlow
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.CloudDone,
                                contentDescription = null,
                                tint = ConnectedGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.github.buildProgressMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            onDeployServer(tokenInput, repoInput, selectedProtocol, cleanIpInput)
                        },
                        enabled = !uiState.github.isBuildingServer,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricBlue,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("github_deploy_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ساخت و اجرای سرور", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    OutlinedButton(
                        onClick = { onFetchExistingServer(tokenInput, repoInput) },
                        enabled = !uiState.github.isBuildingServer,
                        border = BorderStroke(1.dp, SkyCyanGlow.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(0.9f)
                            .height(48.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("github_fetch_live_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            tint = SkyCyanGlow,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("دریافت فعال", color = SkyCyanGlow, maxLines = 1)
                    }
                }

                val live = uiState.github.liveServer
                AnimatedVisibility(visible = live != null) {
                    if (live != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF0A242D))
                                .border(1.dp, ConnectedGreen.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = ConnectedGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "سرور گیت‌هاب فعال است (${live.profiles.size} کانفیگ)",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = ConnectedGreen
                                )
                            }
                            LtrText(
                                text = "${live.repoFullName} • ${live.protocol}\nHost: ${live.tunnelHost}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = SkyCyanGlow
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onConnectNow,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ConnectedGreen,
                                        contentColor = Color(0xFF06161B)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .minimumInteractiveComponentSize()
                                        .testTag("github_connect_live_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Bolt,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("اتصال فوری", fontWeight = FontWeight.Bold)
                                }

                                IconButton(
                                    onClick = {
                                        val uris = live.profiles.joinToString("\n") { it.rawUri }
                                        clipboard.setText(AnnotatedString(uris))
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("github_copy_live_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ContentCopy,
                                        contentDescription = "کپی لینک کانفیگ",
                                        tint = SkyCyanGlow
                                    )
                                }

                                if (live.runId != null) {
                                    IconButton(
                                        onClick = { onStopServer(tokenInput) },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("github_stop_server_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.StopCircle,
                                            contentDescription = "خاموش کردن سرور گیت‌هاب",
                                            tint = CoralError
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Card 2: Personal GitHub Gist Subscription Builder
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                containerColor = ObsidianSurface.copy(alpha = 0.94f)
            ) {
                Text(
                    text = "۲. ساخت اشتراک شخصی (GitHub Gist Subscription)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "سرورهای ذخیره‌شده + یک اکانت تازه Cloudflare WARP را در یک Gist شخصی آپلود کنید و لینک اشتراک دائمی برای خود یا دستگاه‌های دیگر بسازید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "افزودن اکانت جدید Cloudflare WARP به اشتراک",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = includeWarpInGist,
                        onCheckedChange = { includeWarpInGist = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = ElectricBlue
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { onCreateGist(tokenInput, includeWarpInGist) },
                    enabled = !uiState.github.isCreatingGist,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricBlueDeep,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("github_create_gist_button")
                ) {
                    if (uiState.github.isCreatingGist) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("در حال انتشار روی GitHub Gist…")
                    } else {
                        Text("ساخت / بروزرسانی اشتراک GitHub Gist")
                    }
                }

                val gist = uiState.github.gistResult
                AnimatedVisibility(visible = gist != null) {
                    if (gist != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0E1D44))
                                .border(1.dp, ConnectedGreen.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "لینک اشتراک Gist (${gist.serverCount} سرور):",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = ConnectedGreen
                                )
                                LtrText(
                                    text = gist.rawUrl,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                    color = SkyCyanGlow,
                                    maxLines = 2
                                )
                            }
                            IconButton(
                                onClick = { clipboard.setText(AnnotatedString(gist.rawUrl)) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = "کپی لینک اشتراک Gist",
                                    tint = SkyCyanGlow
                                )
                            }
                        }
                    }
                }
            }

            // Card 3: No-Token Live GitHub Repo Search & Collector
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                containerColor = ObsidianSurface.copy(alpha = 0.94f)
            ) {
                Text(
                    text = "۳. جستجوی زنده سرور در گیت‌هاب (بدون نیاز به توکن)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "به‌صورت خودکار مخازن به‌روزرسانی‌شده گیت‌هاب را جستجو می‌کند، کانفیگ‌های سالم را تست پینگ می‌گیرد و به لیست سرورها اضافه می‌کند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("عبارت جستجو") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SkyCyanGlow,
                            unfocusedBorderColor = ObsidianCardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { onSearchRepos(searchQuery) },
                        enabled = !uiState.github.isSearchingRepos,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricBlue,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .height(54.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("github_search_button")
                    ) {
                        if (uiState.github.isSearchingRepos) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "جستجو",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("یافتن")
                        }
                    }
                }

                if (uiState.github.discoveredRepos.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.github.discoveredRepos.forEach { repo ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ObsidianSurfaceElevated)
                                    .border(1.dp, ObsidianCardBorder, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    LtrText(
                                        text = repo.repoFullName,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = SkyCyanGlow,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "${repo.profiles.size} سرور سالم • ★ ${repo.stars}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ConnectedGreen
                                    )
                                }
                                val fastest = repo.profiles.firstOrNull()?.latencyMs
                                if (fastest != null && fastest > 0) {
                                    LtrText(
                                        text = "${fastest}ms",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = SaffronAmber
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
