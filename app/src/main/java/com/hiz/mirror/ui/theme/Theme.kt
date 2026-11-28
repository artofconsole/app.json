package com.hiz.mirror.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object HizColors {
    val Background = Color(0xFF090C11)
    val Surface = Color(0xFF11161F)
    val SurfaceElevated = Color(0xFF171E2A)
    val SurfaceBorder = Color(0xFF232C3C)

    val TextPrimary = Color(0xFFF1F5F9)
    val TextSecondary = Color(0xFF94A3B8)
    val TextMuted = Color(0xFF64748B)

    val AccentCyan = Color(0xFF00D4FF)
    val AccentCyanSoft = Color(0x2400D4FF)

    val StatusGreen = Color(0xFF10B981)
    val StatusGreenSoft = Color(0x1E10B981)

    val StatusAmber = Color(0xFFF59E0B)
    val StatusAmberSoft = Color(0x1EF59E0B)

    val StatusCoral = Color(0xFFF43F5E)
    val StatusCoralSoft = Color(0x1EF43F5E)
}

private val HizDarkColorScheme = darkColorScheme(
    primary = HizColors.AccentCyan,
    onPrimary = Color(0xFF051018),
    secondary = HizColors.StatusGreen,
    background = HizColors.Background,
    onBackground = HizColors.TextPrimary,
    surface = HizColors.Surface,
    onSurface = HizColors.TextPrimary,
    surfaceVariant = HizColors.SurfaceElevated,
    onSurfaceVariant = HizColors.TextSecondary,
    error = HizColors.StatusCoral,
    outline = HizColors.SurfaceBorder
)

val HizTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 44.sp,
        lineHeight = 52.sp,
        color = HizColors.TextPrimary
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 34.sp,
        color = HizColors.TextPrimary
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        color = HizColors.TextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = HizColors.TextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = HizColors.TextSecondary
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = HizColors.TextPrimary
    )
)

@Composable
fun HizTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HizDarkColorScheme,
        typography = HizTypography,
        content = content
    )
}
