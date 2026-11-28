package com.hiz.mirror.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hiz.mirror.model.AppMode
import com.hiz.mirror.model.ConnectionState
import com.hiz.mirror.ui.theme.HizColors

@Composable
fun HizTopBar(
    mode: AppMode?,
    connectionState: ConnectionState,
    onChangeMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = HizColors.Surface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, HizColors.SurfaceBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(HizColors.SurfaceElevated)
                        .border(1.dp, HizColors.AccentCyan.copy(alpha = 0.45f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = "Hız",
                        tint = HizColors.AccentCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Hız",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                if (mode != null) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = mode.displayTitle,
                        color = HizColors.TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }

            if (mode != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ConnectionStatusChip(state = connectionState)

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(HizColors.SurfaceElevated)
                            .hizDpadFocusable(
                                shape = RoundedCornerShape(12.dp),
                                onClick = onChangeMode
                            )
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.SwapHoriz,
                                contentDescription = null,
                                tint = HizColors.AccentCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Mod Değiştir",
                                color = HizColors.TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectionStatusChip(
    state: ConnectionState,
    modifier: Modifier = Modifier
) {
    val (bgColor, fgColor, borderColor) = when (state) {
        ConnectionState.CONNECTED -> Triple(
            HizColors.StatusGreenSoft,
            HizColors.StatusGreen,
            HizColors.StatusGreen.copy(alpha = 0.45f)
        )
        ConnectionState.CONNECTING,
        ConnectionState.RECONNECTING -> Triple(
            HizColors.StatusAmberSoft,
            HizColors.StatusAmber,
            HizColors.StatusAmber.copy(alpha = 0.45f)
        )
        ConnectionState.DROPPED -> Triple(
            HizColors.StatusCoralSoft,
            HizColors.StatusCoral,
            HizColors.StatusCoral.copy(alpha = 0.45f)
        )
        ConnectionState.DISCONNECTED -> Triple(
            HizColors.SurfaceElevated,
            HizColors.TextSecondary,
            HizColors.SurfaceBorder
        )
    }

    val pulseAlpha = if (state.isPulsing) {
        val transition = rememberInfiniteTransition(label = "statusPulse")
        val alpha by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 850),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dotPulse"
        )
        alpha
    } else {
        1f
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .alpha(pulseAlpha)
                .clip(CircleShape)
                .background(fgColor)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = state.label,
            color = fgColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
