package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SignalWifiOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.StartupActionRecommendation
import com.example.ui.StartupDiagnosticState
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
import com.example.ui.theme.SaffronAmber
import com.example.ui.theme.SkyCyanGlow
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun StartupLoadingScreen(
    diagnostic: StartupDiagnosticState,
    onRetryScan: () -> Unit,
    onSmartConnectAndEnter: () -> Unit,
    onEnterAppOnly: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(diagnostic.isCompleted, diagnostic.recommendation) {
        if (diagnostic.isCompleted && diagnostic.recommendation == StartupActionRecommendation.ALREADY_CONNECTED) {
            delay(950L)
            onEnterAppOnly()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "startupScanner")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (diagnostic.isScanning) 1600 else 6500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweepAngle"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val rippleProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rippleProgress"
    )

    val accentColor by animateColorAsState(
        targetValue = when {
            diagnostic.isScanning -> SkyCyanGlow
            !diagnostic.isOnline -> CoralError
            diagnostic.recommendation == StartupActionRecommendation.ALREADY_CONNECTED -> ConnectedGreen
            else -> ElectricBlue
        },
        animationSpec = tween(400),
        label = "accentColor"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D2359),
                        Color(0xFF081434),
                        ObsidianBg,
                        ObsidianBg
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 20.dp)
                .widthIn(max = 540.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Brand Pill
            Surface(
                shape = RoundedCornerShape(50),
                color = ObsidianSurface.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, ObsidianCardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "سپر نت • بررسی هوشمند اتصال",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Animated Radar + Orbital Loading Core
            Box(
                modifier = Modifier.size(230.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxRadius = size.minDimension * 0.46f
                    val innerRadius = size.minDimension * 0.24f

                    // Ambient radial glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.28f * pulseScale),
                                ElectricBlueDeep.copy(alpha = 0.12f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = maxRadius * 1.15f
                        ),
                        radius = maxRadius * 1.15f,
                        center = center
                    )

                    // Concentric radar rings
                    for (i in 1..3) {
                        val r = innerRadius + (maxRadius - innerRadius) * (i / 3f)
                        drawCircle(
                            color = accentColor.copy(alpha = 0.26f - i * 0.05f),
                            radius = r,
                            center = center,
                            style = Stroke(width = 1.2.dp.toPx())
                        )
                    }

                    // Rotating harmonic dot constellation
                    val dotCount = 24
                    val dotRingR = innerRadius + (maxRadius - innerRadius) * 0.62f
                    val angleBase = sweepAngle * (PI / 180.0)
                    for (d in 0 until dotCount) {
                        val theta = angleBase + d * (2.0 * PI / dotCount)
                        val wave = 1.0 + 0.08 * sin(d * 3.0 + angleBase * 2.0)
                        val px = center.x + (dotRingR * wave * cos(theta)).toFloat()
                        val py = center.y + (dotRingR * wave * sin(theta)).toFloat()
                        val dotAlpha = if (d % 3 == 0) 0.85f else 0.35f
                        drawCircle(
                            color = accentColor.copy(alpha = dotAlpha),
                            radius = if (d % 3 == 0) 2.6.dp.toPx() else 1.6.dp.toPx(),
                            center = Offset(px, py)
                        )
                    }

                    // Expanding wave pulse
                    val waveR = innerRadius + (maxRadius - innerRadius) * rippleProgress
                    drawCircle(
                        color = accentColor.copy(alpha = (1f - rippleProgress) * 0.45f),
                        radius = waveR,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // Spinning orbital sweep arc
                    val orbitR = innerRadius + 10.dp.toPx()
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color.Transparent,
                                accentColor.copy(alpha = 0.45f),
                                accentColor
                            )
                        ),
                        startAngle = sweepAngle,
                        sweepAngle = 140f,
                        useCenter = false,
                        topLeft = Offset(center.x - orbitR, center.y - orbitR),
                        size = androidx.compose.ui.geometry.Size(orbitR * 2f, orbitR * 2f),
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Glowing Center Orb
                Box(
                    modifier = Modifier
                        .size(102.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    accentColor.copy(alpha = 0.85f),
                                    ElectricBlueDeep,
                                    Color(0xFF0C1536)
                                )
                            )
                        )
                        .border(1.8.dp, Color.White.copy(alpha = 0.45f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            diagnostic.isScanning -> Icons.Rounded.Wifi
                            !diagnostic.isOnline -> Icons.Rounded.SignalWifiOff
                            diagnostic.recommendation == StartupActionRecommendation.ALREADY_CONNECTED -> Icons.Rounded.CheckCircle
                            else -> Icons.Rounded.Bolt
                        },
                        contentDescription = "وضعیت شبکه",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Dynamic Status Title & Message
            AnimatedContent(
                targetState = diagnostic.isScanning,
                transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(180)) },
                label = "diagnosticPhase"
            ) { scanning ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (scanning) {
                            "در حال بررسی وضعیت اینترنت…"
                        } else if (!diagnostic.isOnline) {
                            "اتصال اینترنت برقرار نیست"
                        } else {
                            "بررسی شبکه کامل شد"
                        },
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = diagnostic.summaryMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Live Checklist / Diagnostic Card
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                containerColor = ObsidianSurface.copy(alpha = 0.92f),
                borderColor = accentColor.copy(alpha = 0.35f)
            ) {
                if (diagnostic.isScanning) {
                    DiagnosticStepRow(
                        label = "شناسایی اینترنت و نوع شبکه (Wi-Fi / 4G / 5G)",
                        done = diagnostic.activeStepIndex > 0,
                        active = diagnostic.activeStepIndex == 0
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    DiagnosticStepRow(
                        label = "بررسی فیلترینگ اینستاگرام و پینگ DNS امن",
                        done = diagnostic.activeStepIndex > 1,
                        active = diagnostic.activeStepIndex == 1
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    DiagnosticStepRow(
                        label = "انتخاب بهترین روش یا سریع‌ترین سرور VPN",
                        done = diagnostic.activeStepIndex > 2,
                        active = diagnostic.activeStepIndex == 2
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "وضعیت شبکه",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                            Text(
                                text = diagnostic.networkTypeLabel,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (diagnostic.isOnline) ConnectedGreen else CoralError
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "پینگ شبکه",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                            LtrText(
                                text = diagnostic.directLatencyMs?.let { "$it MS" } ?: "-- MS",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = SkyCyanGlow
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(ObsidianCardBorder)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "دسترسی مستقیم اینستاگرام",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                            val igText = when {
                                diagnostic.recommendation == StartupActionRecommendation.ALREADY_CONNECTED -> "از طریق تونل باز است"
                                diagnostic.instagramBlocked == true -> "مسدود (نیازمند VPN)"
                                diagnostic.instagramBlocked == false -> "در دسترس"
                                else -> "نامشخص"
                            }
                            val igColor = when {
                                diagnostic.recommendation == StartupActionRecommendation.ALREADY_CONNECTED -> ConnectedGreen
                                diagnostic.instagramBlocked == true -> SaffronAmber
                                diagnostic.instagramBlocked == false -> ConnectedGreen
                                else -> TextSecondary
                            }
                            Text(
                                text = igText,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = igColor
                            )
                        }

                        if (diagnostic.fastestProfileName != null) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "سریع‌ترین سرور",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                                Text(
                                    text = diagnostic.fastestProfileName,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = SkyCyanGlow
                                )
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "مسیر پیشنهادی",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                                Text(
                                    text = when (diagnostic.recommendation) {
                                        StartupActionRecommendation.GENERATE_AND_CONNECT_WARP -> "Cloudflare WARP"
                                        StartupActionRecommendation.CONNECT_ANTI_DPI_DOH -> "TLS Fragment + DoH"
                                        StartupActionRecommendation.ALREADY_CONNECTED -> "متصل"
                                        else -> "آفلاین"
                                    },
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = SkyCyanGlow
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action Buttons (Helper CTA + Enter App)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AnimatedVisibility(visible = diagnostic.isCompleted) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val primaryLabel = when (diagnostic.recommendation) {
                            StartupActionRecommendation.ALREADY_CONNECTED -> "ورود به داشبورد"
                            StartupActionRecommendation.CONNECT_SELECTED_SERVER -> "اتصال به سریع‌ترین سرور و ورود"
                            StartupActionRecommendation.GENERATE_AND_CONNECT_WARP -> "ساخت WARP و اتصال خودکار VPN"
                            StartupActionRecommendation.CONNECT_ANTI_DPI_DOH -> "فعال‌سازی ضد فیلتر و ورود"
                            StartupActionRecommendation.OFFLINE -> "بررسی مجدد اینترنت"
                        }

                        Surface(
                            onClick = {
                                when (diagnostic.recommendation) {
                                    StartupActionRecommendation.OFFLINE -> onRetryScan()
                                    StartupActionRecommendation.ALREADY_CONNECTED -> onEnterAppOnly()
                                    else -> onSmartConnectAndEnter()
                                }
                            },
                            shape = RoundedCornerShape(50),
                            color = Color.Transparent,
                            border = BorderStroke(
                                1.dp,
                                Brush.horizontalGradient(
                                    colors = listOf(ElectricBlue, SkyCyanGlow)
                                )
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("startup_smart_connect_button")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(ElectricBlueDeep, ElectricBlue)
                                        )
                                    )
                                    .padding(horizontal = 18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (diagnostic.recommendation == StartupActionRecommendation.OFFLINE) {
                                        Icons.Rounded.Refresh
                                    } else {
                                        Icons.Rounded.Bolt
                                    },
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = primaryLabel,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }
                        }

                        if (diagnostic.recommendation != StartupActionRecommendation.ALREADY_CONNECTED) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onEnterAppOnly,
                                    border = BorderStroke(1.dp, ObsidianCardBorder),
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .minimumInteractiveComponentSize()
                                        .testTag("startup_enter_app_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Public,
                                        contentDescription = null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("ورود به برنامه", color = TextPrimary)
                                }

                                OutlinedButton(
                                    onClick = onRetryScan,
                                    border = BorderStroke(1.dp, ObsidianCardBorder),
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = "تست مجدد اینترنت",
                                        tint = SkyCyanGlow,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (diagnostic.isScanning) {
                    OutlinedButton(
                        onClick = onEnterAppOnly,
                        border = BorderStroke(1.dp, ObsidianCardBorder.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("startup_skip_button")
                    ) {
                        Text("رد شدن و ورود به برنامه", color = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticStepRow(
    label: String,
    done: Boolean,
    active: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(22.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                done -> Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = ConnectedGreen,
                    modifier = Modifier.size(18.dp)
                )
                active -> CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = SkyCyanGlow
                )
                else -> Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(TextMuted.copy(alpha = 0.5f))
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                done -> ConnectedGreen
                active -> TextPrimary
                else -> TextMuted
            }
        )
    }
}
