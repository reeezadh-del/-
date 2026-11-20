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
import com.example.ui.theme.CoralContainer
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.SaffronAmber
import com.example.ui.theme.SaffronContainer
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

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
        if (bps < 1024L) return "$bps B/s"
        val kb = bps / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.1f KB/s", kb)
        val mb = kb / 1024.0
        return String.format(Locale.US, "%.2f MB/s", mb)
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
    containerColor: Color = ObsidianSurface.copy(alpha = 0.90f),
    cornerRadius: Dp = 20.dp,
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
            modifier = Modifier.padding(16.dp),
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
        ConnectionState.Connected -> Triple(EmeraldContainer, EmeraldPrimary, "متصل (اینستاگرام باز است)")
        ConnectionState.Connecting -> Triple(SaffronContainer, SaffronAmber, "در حال تست اینستاگرام…")
        ConnectionState.Disconnecting -> Triple(SaffronContainer, SaffronAmber, "در حال قطع…")
        is ConnectionState.Error -> Triple(CoralContainer, CoralError, "اتصال ناموفق")
        ConnectionState.Disconnected -> Triple(ObsidianSurfaceElevated, TextSecondary, "آماده اتصال")
    }

    val animatedBg by animateColorAsState(bgColor, label = "pillBg")
    val animatedFg by animateColorAsState(fgColor, label = "pillFg")

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(animatedBg)
            .border(1.dp, animatedFg.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(animatedFg)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
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
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "orbScale"
    )

    val isConnected = state == ConnectionState.Connected
    val isBusy = state == ConnectionState.Connecting || state == ConnectionState.Disconnecting
    val isError = state is ConnectionState.Error

    val primaryAccent by animateColorAsState(
        targetValue = when {
            isConnected -> EmeraldPrimary
            isBusy -> SaffronAmber
            isError -> CoralError
            else -> TextMuted
        },
        animationSpec = tween(350),
        label = "orbAccent"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isBusy) 1100 else 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )

    Box(
        modifier = modifier.size(230.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension * 0.33f
            val maxRadius = size.minDimension * 0.48f

            if (isConnected || isBusy) {
                val r1 = baseRadius + (maxRadius - baseRadius) * pulseProgress
                val a1 = (1f - pulseProgress) * 0.42f
                drawCircle(
                    color = primaryAccent.copy(alpha = a1),
                    radius = r1,
                    center = center,
                    style = Stroke(width = 3.dp.toPx())
                )

                val p2 = (pulseProgress + 0.5f) % 1f
                val r2 = baseRadius + (maxRadius - baseRadius) * p2
                val a2 = (1f - p2) * 0.28f
                drawCircle(
                    color = primaryAccent.copy(alpha = a2),
                    radius = r2,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // Ambient radial glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryAccent.copy(alpha = if (isConnected) 0.28f else 0.12f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxRadius
                ),
                radius = maxRadius,
                center = center
            )

            // Rotating arc when connecting
            if (isBusy) {
                drawArc(
                    color = primaryAccent,
                    startAngle = sweepAngle,
                    sweepAngle = 95f,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // Main Tactile Power Button
        Box(
            modifier = Modifier
                .scale(pressScale)
                .size(148.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        colors = if (isConnected) {
                            listOf(Color(0xFF103F36), Color(0xFF07221C))
                        } else {
                            listOf(ObsidianSurfaceElevated, ObsidianSurface)
                        }
                    )
                )
                .border(
                    width = 2.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            primaryAccent,
                            primaryAccent.copy(alpha = 0.35f)
                        )
                    ),
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true, color = primaryAccent),
                    onClick = onClick
                )
                .testTag("vpn_connect_button"),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = when {
                        isConnected -> Icons.Rounded.GppGood
                        isBusy -> Icons.Rounded.Sync
                        else -> Icons.Rounded.PowerSettingsNew
                    },
                    contentDescription = if (isConnected) "قطع اتصال VPN" else "شروع اتصال VPN",
                    tint = if (isConnected || isBusy) primaryAccent else TextPrimary,
                    modifier = Modifier.size(44.dp)
                )
                Text(
                    text = when {
                        isConnected -> "متصل"
                        isBusy -> "صبر کنید"
                        else -> "اتصال"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (isConnected || isBusy) primaryAccent else TextPrimary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}
