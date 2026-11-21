package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ai.AiCoreEngine
import com.example.ai.AiExpertPersona
import com.example.ai.AiServicePortal
import com.example.model.ConnectionState
import com.example.model.VpnMode
import com.example.ui.AiChatMessage
import com.example.ui.MainUiState
import com.example.ui.components.GlassCard
import com.example.ui.components.LtrText
import com.example.ui.theme.ConnectedGreen
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBlueDeep
import com.example.ui.theme.EmeraldContainer
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
fun AiHubScreen(
    uiState: MainUiState,
    onRunSelfUpgrade: () -> Unit,
    onAutoDiscoverConnect: () -> Unit,
    onSelectPersona: (AiExpertPersona) -> Unit,
    onSendChatMessage: (String) -> Unit,
    onClearChat: () -> Unit,
    onDismissAiError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var promptText by rememberSaveable { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A1638),
                        ObsidianBg,
                        ObsidianBg
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .widthIn(max = 620.dp)
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Header Banner
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(SkyCyanGlow, ElectricBlue, ElectricBlueDeep)
                                )
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "هسته هوش مصنوعی",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "هسته هوش مصنوعی سپر نت",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Text(
                            text = "خودارتقایی هسته VPN و دسترسی یکپارچه به تمام هوش مصنوعی‌ها",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            // 2. Self-Upgrading VPN AI Core Card
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                containerColor = ObsidianSurface.copy(alpha = 0.94f),
                borderColor = SkyCyanGlow.copy(alpha = 0.38f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Psychology,
                            contentDescription = null,
                            tint = SkyCyanGlow,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "موتور کشف و خودارتقایی VPN",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (uiState.settings.vpnMode == VpnMode.AI_AUTO_DISCOVERY) {
                            EmeraldContainer
                        } else {
                            ObsidianSurfaceElevated
                        },
                        border = BorderStroke(
                            1.dp,
                            if (uiState.settings.vpnMode == VpnMode.AI_AUTO_DISCOVERY) {
                                ConnectedGreen.copy(alpha = 0.55f)
                            } else {
                                ObsidianCardBorder
                            }
                        )
                    ) {
                        Text(
                            text = if (uiState.settings.vpnMode == VpnMode.AI_AUTO_DISCOVERY) "کشف خودکار فعال" else uiState.settings.vpnMode.name,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (uiState.settings.vpnMode == VpnMode.AI_AUTO_DISCOVERY) ConnectedGreen else TextSecondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "هوش مصنوعی وضعیت فیلترینگ و لاگ‌های هسته sing-box را تحلیل کرده و بهترین پروتکل، سایز TLS Fragment و DoH را خودکار تنظیم و آپدیت می‌کند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onRunSelfUpgrade,
                        enabled = !uiState.isAiSelfUpgrading,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricBlue,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("ai_self_upgrade_button")
                    ) {
                        if (uiState.isAiSelfUpgrading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("در حال ارتقا…", maxLines = 1)
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تحلیل و ارتقای هسته", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    OutlinedButton(
                        onClick = onAutoDiscoverConnect,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, SkyCyanGlow.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("ai_auto_connect_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = null,
                            tint = SkyCyanGlow,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val isBusy = uiState.telemetry.state == ConnectionState.Connecting ||
                            uiState.telemetry.state == ConnectionState.Connected
                        Text(
                            text = if (isBusy) "متصل / در حال کشف" else "کشف روش و اتصال",
                            color = SkyCyanGlow,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Applied AI Self-Upgrade Plan Details
                val plan = uiState.lastAiUpgradePlan
                AnimatedVisibility(visible = plan != null) {
                    if (plan != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF0C1A3D))
                                .border(1.dp, ConnectedGreen.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                    text = "آخرین ارتقای هوشمند اعمال‌شده",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = ConnectedGreen
                                )
                            }
                            Text(
                                text = plan.diagnosisFa,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary
                            )
                            LtrText(
                                text = "Mode: ${plan.recommendedMode} | Frag: ${plan.recommendedFragmentSize}B/${plan.recommendedFragmentDelayMs}ms | DNS: ${plan.recommendedDnsId} | H2Mux: ${plan.recommendedMultiplex}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = SkyCyanGlow
                            )
                            plan.upgradeActionsFa.forEach { actionItem ->
                                Text(
                                    text = "• $actionItem",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // 3. Built-in Streaming Multi-Persona AI Core Chat
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                containerColor = ObsidianSurface.copy(alpha = 0.94f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = ElectricBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "دستیار چندمدله داخلی",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                            Text(
                                text = uiState.selectedAiPersona.subtitleFa,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (uiState.aiMessages.isNotEmpty()) {
                        IconButton(
                            onClick = onClearChat,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("ai_clear_chat_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "پاک کردن گفتگو",
                                tint = TextMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Persona / Model Selector Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AiExpertPersona.entries.forEach { persona ->
                        val selected = uiState.selectedAiPersona == persona
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectPersona(persona) },
                            label = {
                                Text(
                                    text = persona.titleFa,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
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
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("ai_persona_${persona.id}")
                        )
                    }
                }

                // Error Banner (always propagated transparently)
                AnimatedVisibility(visible = uiState.aiErrorMessage != null) {
                    val err = uiState.aiErrorMessage
                    if (err != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CoralError.copy(alpha = 0.14f))
                                .border(1.dp, CoralError.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("ai_error_banner"),
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
                                onClick = onDismissAiError,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "بستن خطا",
                                    tint = CoralError,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Quick Prompt Suggestions when chat is empty
                if (uiState.aiMessages.isEmpty() && !uiState.isAiLoading) {
                    val suggestions = listOf(
                        "بهترین روش برای عبور از فیلترینگ الان چیه؟",
                        "تفاوت VLESS Reality و Hysteria2 چیه؟",
                        "یک پرامپت حرفه‌ای برای برنامه‌نویسی بنویس"
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        suggestions.forEach { sample ->
                            Surface(
                                onClick = { onSendChatMessage(sample) },
                                shape = RoundedCornerShape(12.dp),
                                color = ObsidianSurfaceElevated.copy(alpha = 0.7f),
                                border = BorderStroke(1.dp, ObsidianCardBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                            ) {
                                Text(
                                    text = sample,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SkyCyanGlow,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.aiMessages.forEach { msg ->
                            AiChatBubble(message = msg)
                        }
                        if (uiState.isAiLoading) {
                            AiStreamingBubble(
                                streamingText = uiState.aiStreamingText.ifBlank { "در حال تفکر و تحلیل…" },
                                personaTitle = uiState.selectedAiPersona.titleFa
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Input Box & Send Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = promptText,
                        onValueChange = { promptText = it },
                        placeholder = {
                            Text(
                                text = "سؤال خود را از هوش مصنوعی بپرسید…",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        },
                        singleLine = false,
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (promptText.isNotBlank() && !uiState.isAiLoading) {
                                    val toSend = promptText
                                    promptText = ""
                                    onSendChatMessage(toSend)
                                }
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SkyCyanGlow,
                            unfocusedBorderColor = ObsidianCardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = SkyCyanGlow,
                            focusedContainerColor = Color(0xFF0A1432),
                            unfocusedContainerColor = Color(0xFF0A1432)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_chat_input")
                    )

                    Surface(
                        onClick = {
                            if (promptText.isNotBlank() && !uiState.isAiLoading) {
                                val toSend = promptText
                                promptText = ""
                                onSendChatMessage(toSend)
                            }
                        },
                        enabled = promptText.isNotBlank() && !uiState.isAiLoading,
                        shape = CircleShape,
                        color = if (promptText.isNotBlank() && !uiState.isAiLoading) ElectricBlue else ObsidianSurfaceElevated,
                        modifier = Modifier
                            .size(48.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("ai_send_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (uiState.isAiLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = SkyCyanGlow
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = "ارسال پیام به هوش مصنوعی",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 4. Global AI Services Access Grid (through encrypted tunnel)
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                containerColor = ObsidianSurface.copy(alpha = 0.92f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Language,
                        contentDescription = null,
                        tint = SkyCyanGlow,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "دسترسی مستقیم به تمام هوش مصنوعی‌های جهان",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Text(
                            text = if (uiState.telemetry.state == ConnectionState.Connected) {
                                "تونل امن فعال است — هر سرویس مستقیم از VPN عبور می‌کند"
                            } else {
                                "برای باز شدن بدون محدودیت، ابتدا VPN را متصل کنید"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (uiState.telemetry.state == ConnectionState.Connected) ConnectedGreen else SaffronAmber
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val chunkedPortals = AiCoreEngine.GLOBAL_AI_PORTALS.chunked(2)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    chunkedPortals.forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowItems.forEach { portal ->
                                AiPortalTile(
                                    portal = portal,
                                    onClick = {
                                        runCatching {
                                            val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(portal.url))
                                            context.startActivity(viewIntent)
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun AiChatBubble(message: AiChatMessage) {
    val bgColor = if (message.isUser) {
        ElectricBlueDeep.copy(alpha = 0.85f)
    } else {
        Color(0xFF101D44)
    }
    val borderColor = if (message.isUser) {
        ElectricBlue.copy(alpha = 0.7f)
    } else {
        ObsidianCardBorder
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Text(
            text = if (message.isUser) "شما" else message.personaTitle,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = if (message.isUser) SkyCyanGlow else ConnectedGreen
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = message.text,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary
        )
    }
}

@Composable
private fun AiStreamingBubble(
    streamingText: String,
    personaTitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF101D44))
            .border(1.dp, SkyCyanGlow.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.8.dp,
                color = SkyCyanGlow
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = personaTitle,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = SkyCyanGlow
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = streamingText,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary
        )
    }
}

@Composable
private fun AiPortalTile(
    portal: AiServicePortal,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF122048))
            .border(1.dp, ObsidianCardBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("ai_portal_${portal.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            LtrText(
                text = portal.name,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = portal.subtitleFa,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
            contentDescription = "باز کردن ${portal.name}",
            tint = SkyCyanGlow,
            modifier = Modifier.size(16.dp)
        )
    }
}
