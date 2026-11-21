package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.model.ConnectionState
import com.example.ui.theme.ConnectedGreen
import com.example.ui.theme.CoralContainer
import com.example.ui.theme.CoralError
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBlueDeep
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.SaffronAmber
import com.example.ui.theme.SaffronContainer
import com.example.ui.theme.SkyCyanGlow
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object FormatUtils {
    fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format(Locale.US, "%.2f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2f GB", gb)
    }

    fun formatSpeed(bps: Long): String {
        if (bps < 1024L) return "0 KB/S"
        val kb = bps / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.0f KB/S", kb)
        val mb = kb / 1024.0
        return String.format(Locale.US, "%.1f MB/S", mb)
    }

    fun formatDuration(connectedSinceMs: Long, nowMs: Long): String {
        if (connectedSinceMs <= 0L) return "00:00:00"
        val totalSec = ((nowMs - connectedSinceMs) / 1000L).coerceAtLeast(0L)
        val hours = totalSec / 3600L
        val mins = (totalSec % 3600L) / 60L
        val secs = totalSec % 60L
        return String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs)
    }
}

@Composable
fun LtrText(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelSmall,
    color: Color = TextPrimary,
    maxLines: Int = 1
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(
            text = text,
            modifier = modifier,
            style = style,
            color = color,
            maxLines = maxLines
        )
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    borderColor: Color = ObsidianCardBorder,
    containerColor: Color = ObsidianSurface.copy(alpha = 0.88f),
    cornerRadius: Dp = 22.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.04f),
                            Color.Transparent
                        )
                    )
                )
                .padding(16.dp),
            content = content
        )
    }
}

@Composable
fun StatusPill(
    state: ConnectionState,
    modifier: Modifier = Modifier
) {
    val (bgColor, fgColor, label) = when (state) {
        ConnectionState.Connected -> Triple(Color(0xFF0E3330), ConnectedGreen, "متصل و ایمن")
        ConnectionState.Connecting -> Triple(SaffronContainer, SaffronAmber, "در حال بررسی اتصال…")
        ConnectionState.Disconnecting -> Triple(SaffronContainer, SaffronAmber, "در حال قطع…")
        is ConnectionState.Error -> Triple(CoralContainer, CoralError, "اتصال ناموفق")
        ConnectionState.Disconnected -> Triple(ObsidianSurfaceElevated, TextSecondary, "متصل نیست")
    }

    val animatedBg by animateColorAsState(bgColor, label = "pillBg")
    val animatedFg by animateColorAsState(fgColor, label = "pillFg")

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(animatedBg)
            .border(1.dp, animatedFg.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(animatedFg)
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = animatedFg
        )
    }
}

@Composable
fun RadarConnectOrb(
    state: ConnectionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "orbScale"
    )

    val isConnected = state == ConnectionState.Connected
    val isBusy = state == ConnectionState.Connecting || state == ConnectionState.Disconnecting
    val isError = state is ConnectionState.Error

    val primaryAccent by animateColorAsState(
        targetValue = when {
            isConnected -> SkyCyanGlow
            isBusy -> SaffronAmber
            isError -> CoralError
            else -> ElectricBlue
        },
        animationSpec = tween(350),
        label = "orbAccent"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "mandala")
    val meshPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isBusy) 4500 else 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "meshPhase"
    )
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isBusy) 1200 else 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )

    Box(
        modifier = modifier.size(276.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val innerEdge = size.minDimension * 0.255f
            val outerEdge = size.minDimension * 0.485f

            // Soft deep blue radial aura behind the spirograph mesh
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryAccent.copy(alpha = if (isConnected) 0.34f else 0.16f),
                        ElectricBlueDeep.copy(alpha = if (isConnected) 0.16f else 0.06f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = outerEdge * 1.05f
                ),
                radius = outerEdge * 1.05f,
                center = center
            )

            // Procedural dot-matrix harmonic spirograph mandala (8-petal & 12-petal interference mesh)
            val rings = 11
            val steps = 84
            val baseAlpha = when {
                isConnected -> 0.85f
                isBusy -> 0.72f
                else -> 0.45f
            }

            for (ring in 0 until rings) {
                val tRing = ring / (rings - 1f)
                val rBase = innerEdge + (outerEdge - innerEdge) * tRing
                val petals = if (ring % 2 == 0) 8f else 12f
                val direction = if (ring % 2 == 0) 1f else -0.7f
                val phaseOffset = meshPhase * direction + ring * 0.32f
                val dotRadius = (1.15f + (1f - tRing) * 0.7f).dp.toPx() * 0.68f
                val ringAlpha = baseAlpha * (1f - tRing * 0.55f)
                val dotColor = if (ring % 3 == 0) SkyCyanGlow.copy(alpha = ringAlpha)
                else primaryAccent.copy(alpha = ringAlpha)

                for (s in 0 until steps) {
                    val theta = (s.toFloat() / steps) * (2.0 * PI).toFloat()
                    val wave = 0.80f + 0.20f * cos(petals * theta + phaseOffset)
                    val r = rBase * wave
                    val px = center.x + r * cos(theta + ring * 0.04f)
                    val py = center.y + r * sin(theta + ring * 0.04f)
                    drawCircle(
                        color = dotColor,
                        radius = dotRadius,
                        center = Offset(px, py)
                    )
                }
            }

            // Expanding pulse ring when active/connecting
            if (isConnected || isBusy) {
                val pulseR = innerEdge + (outerEdge - innerEdge) * pulseProgress
                drawCircle(
                    color = primaryAccent.copy(alpha = (1f - pulseProgress) * 0.42f),
                    radius = pulseR,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // Spinning progress arc when connecting
            if (isBusy) {
                val arcRadius = innerEdge + 4.dp.toPx()
                drawArc(
                    color = primaryAccent,
                    startAngle = sweepAngle,
                    sweepAngle = 105f,
                    useCenter = false,
                    topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                    size = androidx.compose.ui.geometry.Size(arcRadius * 2f, arcRadius * 2f),
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // Tactile Central Power Orb (Midnight glass when off, Radiant Cobalt-Cyan when connected)
        Box(
            modifier = Modifier
                .scale(pressScale)
                .size(134.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = when {
                            isConnected -> listOf(
                                SkyCyanGlow,
                                ElectricBlue,
                                ElectricBlueDeep
                            )
                            isError -> listOf(
                                Color(0xFF3E1929),
                                Color(0xFF1D0D19)
                            )
                            else -> listOf(
                                Color(0xFF21325C),
                                Color(0xFF131D3D),
                                Color(0xFF0C142B)
                            )
                        }
                    )
                )
                .border(
                    width = 2.dp,
                    brush = Brush.verticalGradient(
                        colors = when {
                            isConnected -> listOf(Color(0xFFA6EBFF), ElectricBlue)
                            isBusy -> listOf(SaffronAmber, SaffronAmber.copy(alpha = 0.4f))
                            isError -> listOf(CoralError, CoralError.copy(alpha = 0.4f))
                            else -> listOf(Color(0xFF8FB8F6), Color(0xFF3A5289))
                        }
                    ),
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true, color = SkyCyanGlow),
                    onClick = onClick
                )
                .testTag("vpn_connect_button"),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = when {
                        isConnected -> Icons.Rounded.PowerSettingsNew
                        isBusy -> Icons.Rounded.Sync
                        else -> Icons.Rounded.PowerSettingsNew
                    },
                    contentDescription = if (isConnected) "قطع اتصال VPN" else "شروع اتصال VPN",
                    tint = if (isConnected) Color.White else if (isBusy) SaffronAmber else TextPrimary,
                    modifier = Modifier.size(34.dp)
                )
                Text(
                    text = when {
                        isConnected -> "قطع اتصال"
                        isBusy -> "صبر کنید…"
                        isError -> "تلاش مجدد"
                        else -> "لمس برای اتصال"
                    },
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isConnected) Color.White else TextSecondary,
                    modifier = Modifier.padding(top = 6.dp, start = 10.dp, end = 10.dp),
                    maxLines = 1
                )
            }
        }
    }
}

