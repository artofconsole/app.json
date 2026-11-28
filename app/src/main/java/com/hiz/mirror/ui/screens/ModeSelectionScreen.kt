package com.hiz.mirror.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hiz.mirror.model.AppMode
import com.hiz.mirror.ui.components.hizDpadFocusable
import com.hiz.mirror.ui.theme.HizColors

@Composable
fun ModeSelectionScreen(
    lastSavedMode: AppMode?,
    onSelectMode: (AppMode) -> Unit
) {
    val transmitterFocus = remember { FocusRequester() }
    val receiverFocus = remember { FocusRequester() }

    LaunchedEffect(lastSavedMode) {
        if (lastSavedMode == AppMode.RECEIVER) {
            runCatching { receiverFocus.requestFocus() }
        } else {
            runCatching { transmitterFocus.requestFocus() }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(HizColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp)
    ) {
        val isWide = maxWidth >= 680.dp

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Yansıtma Modunu Seçin",
                color = Color.White,
                fontSize = if (isWide) 36.sp else 28.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Aynı yerel ağ (LAN) üzerinde ekranınızı paylaşmak için Verici Modu'nu, büyük ekranda yayını izlemek için Alıcı Modu'nu açın.",
                color = HizColors.TextSecondary,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )

            if (lastSavedMode != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Kayıtlı son mod: ${lastSavedMode.displayTitle}",
                    color = HizColors.AccentCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            if (isWide) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    ModeCard(
                        title = "Verici Modu (Ekran Gönder)",
                        description = "Yerel ağdaki alıcı TV'leri otomatik tarayın veya IP/Port ile bağlanarak ekranınızı düşük gecikmeyle aktarın.",
                        actionLabel = "Vericiyi Başlat",
                        icon = Icons.Rounded.Smartphone,
                        iconAccent = HizColors.AccentCyan,
                        isLastSaved = lastSavedMode == AppMode.TRANSMITTER,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(transmitterFocus),
                        onClick = { onSelectMode(AppMode.TRANSMITTER) }
                    )

                    ModeCard(
                        title = "Alıcı Modu (TV Ekranı)",
                        description = "Yerel IP adresinizi ve dinlenen portu TV'de büyük puntolarla gösterin; yayın başladığında tam ekran karşılayın.",
                        actionLabel = "Alıcıyı Başlat",
                        icon = Icons.Rounded.Tv,
                        iconAccent = HizColors.StatusGreen,
                        isLastSaved = lastSavedMode == AppMode.RECEIVER,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(receiverFocus),
                        onClick = { onSelectMode(AppMode.RECEIVER) }
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ModeCard(
                        title = "Verici Modu (Ekran Gönder)",
                        description = "Yerel ağdaki alıcı TV'leri otomatik tarayın veya IP/Port ile bağlanarak ekranınızı düşük gecikmeyle aktarın.",
                        actionLabel = "Vericiyi Başlat",
                        icon = Icons.Rounded.Smartphone,
                        iconAccent = HizColors.AccentCyan,
                        isLastSaved = lastSavedMode == AppMode.TRANSMITTER,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(transmitterFocus),
                        onClick = { onSelectMode(AppMode.TRANSMITTER) }
                    )

                    ModeCard(
                        title = "Alıcı Modu (TV Ekranı)",
                        description = "Yerel IP adresinizi ve dinlenen portu TV'de büyük puntolarla gösterin; yayın başladığında tam ekran karşılayın.",
                        actionLabel = "Alıcıyı Başlat",
                        icon = Icons.Rounded.Tv,
                        iconAccent = HizColors.StatusGreen,
                        isLastSaved = lastSavedMode == AppMode.RECEIVER,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(receiverFocus),
                        onClick = { onSelectMode(AppMode.RECEIVER) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = "TV kumandasının yön tuşlarıyla kartlar arasında gezinebilir, OK/Enter ile seçim yapabilirsiniz.",
                color = HizColors.TextMuted,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun ModeCard(
    title: String,
    description: String,
    actionLabel: String,
    icon: ImageVector,
    iconAccent: Color,
    isLastSaved: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(HizColors.Surface)
            .hizDpadFocusable(
                isSelected = isLastSaved,
                shape = RoundedCornerShape(20.dp),
                onClick = onClick
            )
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(HizColors.SurfaceElevated)
                        .border(1.dp, iconAccent.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconAccent,
                        modifier = Modifier.size(28.dp)
                    )
                }

                if (isLastSaved) {
                    Text(
                        text = "Son Kullanılan",
                        color = HizColors.AccentCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = title,
                color = Color.White,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = description,
                color = HizColors.TextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = actionLabel,
                color = HizColors.AccentCyan,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = HizColors.AccentCyan,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
