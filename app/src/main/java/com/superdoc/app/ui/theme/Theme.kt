package com.superdoc.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Tile colours all keep at least 4.5:1 contrast with white text. */
object SeniorColors {
    val Primary = Color(0xFF0B5394)
    val PrimaryDark = Color(0xFF073763)
    val Background = Color(0xFFFFFBF5)
    val Surface = Color(0xFFFFFFFF)
    val Text = Color(0xFF1A1A1A)
    val TextMuted = Color(0xFF4A4A4A)
    val Note = Color(0xFFB45309)
    val Scan = Color(0xFF15803D)
    val Documents = Color(0xFF1D4ED8)
    val Help = Color(0xFF6D28D9)
    val Tools = Color(0xFF0F766E)
    val Danger = Color(0xFFB91C1C)
    val WhatsApp = Color(0xFF128C4A)
    val Highlight = Color(0xFFFFD54F)
    val SavedGreen = Color(0xFF166534)
}

object Dimens {
    val TouchTarget = 64.dp
    val ScreenPadding = 16.dp
    val Gap = 12.dp
}

private val colors = lightColorScheme(
    primary = SeniorColors.Primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E6F7),
    onPrimaryContainer = SeniorColors.PrimaryDark,
    secondary = SeniorColors.Tools,
    onSecondary = Color.White,
    background = SeniorColors.Background,
    onBackground = SeniorColors.Text,
    surface = SeniorColors.Surface,
    onSurface = SeniorColors.Text,
    surfaceVariant = Color(0xFFF1ECE4),
    onSurfaceVariant = SeniorColors.TextMuted,
    error = SeniorColors.Danger,
    outline = Color(0xFF6B6B6B),
)

private val typography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 22.sp, lineHeight = 32.sp),
    bodyMedium = TextStyle(fontSize = 20.sp, lineHeight = 28.sp),
    bodySmall = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    labelLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
)

/** [textScale] is the in-app "text size" setting, applied on top of the phone's own font size. */
@Composable
fun SuperDocTheme(textScale: Float = 1f, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * textScale)) {
        MaterialTheme(colorScheme = colors, typography = typography, content = content)
    }
}
