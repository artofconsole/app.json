package com.hiz.mirror.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hiz.mirror.ui.theme.HizColors

/**
 * Android TV D-Pad & dokunmatik uyumlu odak vurgusu:
 * Odaklanıldığında canlı elektrik mavisi kenarlık, parlama (glow) ve yumuşak ölçek artışı uygular.
 */
fun Modifier.hizDpadFocusable(
    isSelected: Boolean = false,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(16.dp),
    unfocusedBorderColor: Color = HizColors.SurfaceBorder,
    selectedBorderColor: Color = HizColors.AccentCyan,
    onClick: () -> Unit
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.03f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "dpadScale"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            isFocused -> HizColors.AccentCyan
            isSelected -> selectedBorderColor
            else -> unfocusedBorderColor
        },
        label = "dpadBorderColor"
    )

    val borderWidth: Dp by animateDpAsState(
        targetValue = when {
            isFocused -> 2.5.dp
            isSelected -> 1.5.dp
            else -> 1.dp
        },
        label = "dpadBorderWidth"
    )

    val glowElevation: Dp by animateDpAsState(
        targetValue = if (isFocused) 14.dp else 0.dp,
        label = "dpadGlow"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .shadow(
            elevation = glowElevation,
            shape = shape,
            ambientColor = HizColors.AccentCyan,
            spotColor = HizColors.AccentCyan
        )
        .clip(shape)
        .border(BorderStroke(borderWidth, borderColor), shape)
        .onFocusChanged { isFocused = it.isFocused }
        .focusable(enabled = enabled, interactionSource = interactionSource)
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}
