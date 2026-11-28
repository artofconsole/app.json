package com.hiz.mirror.ui.screens

import android.net.Uri
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.hiz.mirror.R
import com.hiz.mirror.ui.components.hizDpadFocusable
import com.hiz.mirror.ui.theme.HizColors
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun SplashScreen(
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    var useFallback by remember { mutableStateOf(false) }
    var hasCompleted by remember { mutableStateOf(false) }

    fun completeSplash() {
        if (!hasCompleted) {
            hasCompleted = true
            onFinished()
        }
    }

    val exoPlayer = remember {
        runCatching {
            val videoUri = Uri.parse("android.resource://${context.packageName}/${R.raw.splash_lightning}")
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(videoUri))
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = true
                prepare()
            }
        }.getOrNull()
    }

    if (exoPlayer == null) {
        LaunchedEffect(Unit) {
            useFallback = true
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    completeSplash()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                useFallback = true
            }
        }
        exoPlayer?.addListener(listener)
        onDispose {
            exoPlayer?.removeListener(listener)
            exoPlayer?.release()
        }
    }

    // Fallback safety timer when video cannot be played or is unavailable
    LaunchedEffect(useFallback) {
        if (useFallback) {
            delay(2800L)
            completeSplash()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07090D))
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    val code = keyEvent.nativeKeyEvent.keyCode
                    if (code == KeyEvent.KEYCODE_DPAD_CENTER ||
                        code == KeyEvent.KEYCODE_ENTER ||
                        code == KeyEvent.KEYCODE_SPACE ||
                        code == KeyEvent.KEYCODE_BACK
                    ) {
                        completeSplash()
                        return@onPreviewKeyEvent true
                    }
                }
                false
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { completeSplash() }
            )
    ) {
        if (!useFallback && exoPlayer != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        player = exoPlayer
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            FallbackLightningSplash()
        }

        // Skip button for touch or TV remote D-pad
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .safeDrawingPadding()
                .padding(20.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(HizColors.Surface.copy(alpha = 0.88f))
                .hizDpadFocusable(
                    shape = RoundedCornerShape(12.dp),
                    onClick = { completeSplash() }
                )
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text(
                text = "Atla",
                color = HizColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun FallbackLightningSplash() {
    val transition = rememberInfiniteTransition(label = "lightningFallback")
    val flashAlpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flashAlpha"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0E1B2B),
                        Color(0xFF090C11),
                        Color(0xFF05070A)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        val isCompact = maxWidth < 480.dp

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val boltPath = Path().apply {
                moveTo(w * 0.54f, h * 0.14f)
                lineTo(w * 0.45f, h * 0.34f)
                lineTo(w * 0.52f, h * 0.34f)
                lineTo(w * 0.47f, h * 0.48f)
            }
            drawPath(
                path = boltPath,
                color = HizColors.AccentCyan.copy(alpha = flashAlpha * 0.45f),
                style = Stroke(width = 6f)
            )
            drawCircle(
                color = HizColors.AccentCyan.copy(alpha = flashAlpha * 0.12f),
                radius = w.coerceAtMost(h) * 0.32f,
                center = Offset(w / 2f, h / 2f)
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Bolt,
                contentDescription = "Hız Şimşek",
                tint = HizColors.AccentCyan,
                modifier = Modifier
                    .size(if (isCompact) 72.dp else 84.dp)
                    .alpha(0.55f + flashAlpha * 0.45f)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Hız.",
                color = HizColors.AccentCyan,
                fontSize = if (isCompact) 46.sp else 54.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Ben hızım.",
                color = HizColors.TextPrimary,
                fontSize = if (isCompact) 18.sp else 20.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}
