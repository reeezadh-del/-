package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBlueDeep
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.SkyCyanGlow
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun WelcomeOnboardingScreen(
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier
) {
    var safeModeSelected by remember { mutableStateOf(true) }
    val infiniteTransition = rememberInfiniteTransition(label = "onboardingGlow")
    val wavePulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wavePulse"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF14429C),
                        Color(0xFF0A1B47),
                        ObsidianBg,
                        ObsidianBg
                    )
                )
            )
    ) {
        // Concentric top glowing blue arc waves Cone (matching left mockup screen)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .align(Alignment.TopCenter)
        ) {
            val w = size.width
            val h = size.height
            val focal = Offset(w / 2f, h * 0.72f)

            // Soft V-cone glow radiating upwards
            val conePath = Path().apply {
                moveTo(focal.x, focal.y)
                lineTo(-w * 0.12f, 0f)
                lineTo(w * 1.12f, 0f)
                close()
            }
            drawPath(
                path = conePath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        SkyCyanGlow.copy(alpha = 0.62f),
                        ElectricBlue.copy(alpha = 0.42f),
                        ElectricBlueDeep.copy(alpha = 0.14f),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = focal.y
                )
            )

            // Concentric radar wave arcs clipped cleanly inside upper area
            val arcCount = 6
            val maxR = h * 0.92f * wavePulse
            for (i in 1..arcCount) {
                val frac = i.toFloat() / arcCount
                val radius = maxR * frac
                val alpha = (0.48f - frac * 0.28f).coerceAtLeast(0.12f)
                drawArc(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF8BE5FF).copy(alpha = alpha),
                            ElectricBlue.copy(alpha = alpha * 0.6f),
                            Color.Transparent
                        )
                    ),
                    startAngle = 215f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(focal.x - radius, focal.y - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                    style = Stroke(width = 1.6.dp.toPx())
                )
                drawArc(
                    color = SkyCyanGlow.copy(alpha = alpha * 0.18f),
                    startAngle = 215f,
                    sweepAngle = 110f,
                    useCenter = true,
                    topLeft = Offset(focal.x - radius, focal.y - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .widthIn(max = 520.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(0.55f))

            // Central dark-cobalt power orb at focal point
            Box(
                modifier = Modifier
                    .size(98.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF1F387D),
                                Color(0xFF0E1A40)
                            )
                        )
                    )
                    .border(
                        width = 1.5.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                SkyCyanGlow.copy(alpha = 0.75f),
                                ElectricBlueDeep.copy(alpha = 0.45f)
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PowerSettingsNew,
                    contentDescription = "سپر نت",
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(modifier = Modifier.weight(0.45f))

            // Segmented glass pill: امنیت | مرور آزاد
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = ObsidianSurface.copy(alpha = 0.9f),
                    border = BorderStroke(1.dp, ObsidianCardBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val safeBg by animateColorAsState(
                            if (safeModeSelected) ElectricBlueDeep else Color.Transparent,
                            label = "safeBg"
                        )
                        val browseBg by animateColorAsState(
                            if (!safeModeSelected) ElectricBlueDeep else Color.Transparent,
                            label = "browseBg"
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(safeBg)
                                .clickable { safeModeSelected = true }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = "حالت امن",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (safeModeSelected) Color.White else TextSecondary
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(browseBg)
                                .clickable { safeModeSelected = false }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = "مرور آزاد",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (!safeModeSelected) Color.White else TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Hero Headline + Subtitle with vertical hairline accent
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "حریم خصوصی\nدر سطحی\nجدید!",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp,
                        lineHeight = 44.sp
                    ),
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(44.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(SkyCyanGlow, ElectricBlueDeep.copy(alpha = 0.2f))
                                )
                            )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (safeModeSelected) {
                            "اتصال رمزنگاری‌شده با ضد فیلتر هوشمند، DNS امن و تأیید واقعی دسترسی به اینترنت آزاد."
                        } else {
                            "پشتیبانی از جدیدترین پروتکل‌های VLESS Reality، Hysteria2، TUIC و Cloudflare WARP یک‌کلیکی."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Bottom Action Bar matching the mockup's circular + capsule CTA
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    onClick = onGetStarted,
                    shape = CircleShape,
                    color = ObsidianSurface,
                    border = BorderStroke(1.dp, ObsidianCardBorder),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = "ورود به سپر نت",
                            tint = SkyCyanGlow,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Surface(
                    onClick = onGetStarted,
                    shape = RoundedCornerShape(50),
                    color = Color.Transparent,
                    border = BorderStroke(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(
                            colors = listOf(ElectricBlueDeep, SkyCyanGlow.copy(alpha = 0.65f))
                        )
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("onboarding_get_started")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF101D47),
                                        Color(0xFF193275)
                                    )
                                )
                            )
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(SkyCyanGlow, ElectricBlue, ElectricBlueDeep)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Wifi,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Text(
                            text = "شروع سریع",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                contentDescription = null,
                                tint = SkyCyanGlow.copy(alpha = 0.55f),
                                modifier = Modifier.size(16.dp)
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                contentDescription = null,
                                tint = SkyCyanGlow.copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp)
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
