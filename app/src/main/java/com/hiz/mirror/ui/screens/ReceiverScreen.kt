package com.hiz.mirror.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hiz.mirror.model.ConnectionState
import com.hiz.mirror.ui.components.hizDpadFocusable
import com.hiz.mirror.ui.theme.HizColors
import kotlinx.coroutines.delay

@Composable
fun ReceiverScreen(
    localIp: String,
    listenPort: Int,
    receiverName: String,
    connectionState: ConnectionState,
    isMirroringActive: Boolean,
    currentFrame: Bitmap?,
    senderName: String,
    incomingFps: Int,
    incomingResolution: String,
    onReceiverNameChange: (String) -> Unit,
    onStopSession: () -> Unit,
    onChangeMode: () -> Unit
) {
    if (isMirroringActive) {
        ActiveReceiverStage(
            currentFrame = currentFrame,
            senderName = senderName,
            incomingFps = incomingFps,
            incomingResolution = incomingResolution,
            onStopSession = onStopSession,
            onChangeMode = onChangeMode
        )
    } else {
        ReceiverStandbyStage(
            localIp = localIp,
            listenPort = listenPort,
            receiverName = receiverName,
            connectionState = connectionState,
            onReceiverNameChange = onReceiverNameChange
        )
    }
}

@Composable
private fun ReceiverStandbyStage(
    localIp: String,
    listenPort: Int,
    receiverName: String,
    connectionState: ConnectionState,
    onReceiverNameChange: (String) -> Unit
) {
    val pulse = rememberInfiniteTransition(label = "tvWaitingPulse")
    val ringScale by pulse.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.32f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringScale"
    )
    val ringAlpha by pulse.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HizColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(HizColors.Surface)
                .border(1.dp, HizColors.SurfaceBorder, RoundedCornerShape(26.dp))
                .padding(horizontal = 28.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(104.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .scale(ringScale)
                        .alpha(ringAlpha)
                        .clip(CircleShape)
                        .border(2.dp, HizColors.AccentCyan, CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(HizColors.SurfaceElevated)
                        .border(1.5.dp, HizColors.AccentCyan.copy(alpha = 0.45f), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tv,
                        contentDescription = null,
                        tint = HizColors.AccentCyan,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = when (connectionState) {
                    ConnectionState.CONNECTING -> "Verici bağlanıyor..."
                    ConnectionState.RECONNECTING -> "Yeniden bağlanılıyor..."
                    ConnectionState.DROPPED -> "Bağlantı koptu · Bekleniyor"
                    else -> "Bağlantı bekleniyor"
                },
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Telefonunuzda Hız Verici Modu'nu açıp bu cihazı seçin veya aşağıdaki adres ile bağlanın.",
                color = HizColors.TextSecondary,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(HizColors.Background)
                        .border(1.dp, HizColors.SurfaceBorder, RoundedCornerShape(18.dp))
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Yerel Ağ IP Adresi",
                        color = HizColors.TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = localIp,
                        color = HizColors.AccentCyan,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(0.75f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(HizColors.Background)
                        .border(1.dp, HizColors.SurfaceBorder, RoundedCornerShape(18.dp))
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Dinlenen Port",
                        color = HizColors.TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = listenPort.toString(),
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = receiverName,
                onValueChange = onReceiverNameChange,
                label = { Text("TV Alıcı Adı") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HizColors.AccentCyan,
                    unfocusedBorderColor = HizColors.SurfaceBorder,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = HizColors.AccentCyan,
                    unfocusedLabelColor = HizColors.TextSecondary
                ),
                modifier = Modifier.fillMaxWidth(0.6f)
            )

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Yayın başladığında TV tam ekran moda geçer ve ekran yayın boyunca uyanık kalır.",
                color = HizColors.TextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ActiveReceiverStage(
    currentFrame: Bitmap?,
    senderName: String,
    incomingFps: Int,
    incomingResolution: String,
    onStopSession: () -> Unit,
    onChangeMode: () -> Unit
) {
    var hudVisible by remember { mutableStateOf(true) }

    LaunchedEffect(hudVisible) {
        if (hudVisible) {
            delay(4000L)
            hudVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { hudVisible = !hudVisible }
            )
    ) {
        if (currentFrame != null) {
            Image(
                bitmap = currentFrame.asImageBitmap(),
                contentDescription = "Yansıtılan Ekran",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = "İlk video karesi bekleniyor...",
                color = HizColors.TextSecondary,
                fontSize = 16.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        AnimatedVisibility(
            visible = hudVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                        )
                    )
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(HizColors.StatusGreen)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = senderName.ifBlank { "Verici Bağlı" },
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "$incomingFps FPS · $incomingResolution",
                        color = HizColors.AccentCyan,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(HizColors.StatusCoral)
                            .hizDpadFocusable(
                                shape = RoundedCornerShape(10.dp),
                                unfocusedBorderColor = HizColors.StatusCoral,
                                onClick = onStopSession
                            )
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Stop,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Durdur",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(HizColors.SurfaceElevated)
                            .hizDpadFocusable(
                                shape = RoundedCornerShape(10.dp),
                                onClick = onChangeMode
                            )
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.SwapHoriz,
                                contentDescription = null,
                                tint = HizColors.AccentCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Mod Değiştir",
                                color = Color.White,
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
