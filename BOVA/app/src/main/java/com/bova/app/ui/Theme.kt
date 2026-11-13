package com.bova.app.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightScheme = lightColorScheme(
    primary = Color(0xFF18181B), onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E4E7), onPrimaryContainer = Color(0xFF18181B),
    secondary = Color(0xFF52525B), onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDEDF0), onSecondaryContainer = Color(0xFF27272A),
    tertiary = Color(0xFF2E8B57), onTertiary = Color.White,
    error = Color(0xFFC0392B), onError = Color.White,
    background = Color(0xFFF7F7F8), onBackground = Color(0xFF18181B),
    surface = Color.White, onSurface = Color(0xFF18181B),
    surfaceVariant = Color(0xFFF1F1F3), onSurfaceVariant = Color(0xFF71717A),
    outline = Color(0xFFD4D4D8), outlineVariant = Color(0xFFE8E8EB),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainer = Color(0xFFFFFFFF), surfaceContainerHigh = Color(0xFFF1F1F3),
    surfaceContainerHighest = Color(0xFFE9E9EC)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFF4F4F5), onPrimary = Color(0xFF18181B),
    primaryContainer = Color(0xFF3A3A40), onPrimaryContainer = Color(0xFFF4F4F5),
    secondary = Color(0xFFA1A1AA), onSecondary = Color(0xFF18181B),
    secondaryContainer = Color(0xFF2C2C31), onSecondaryContainer = Color(0xFFE4E4E7),
    tertiary = Color(0xFF5CC48D), onTertiary = Color(0xFF0B1F15),
    error = Color(0xFFFF8A80), onError = Color(0xFF2B0A07),
    background = Color(0xFF111113), onBackground = Color(0xFFF4F4F5),
    surface = Color(0xFF1B1B1E), onSurface = Color(0xFFF4F4F5),
    surfaceVariant = Color(0xFF26262A), onSurfaceVariant = Color(0xFFA1A1AA),
    outline = Color(0xFF3F3F46), outlineVariant = Color(0xFF2C2C30),
    surfaceContainerLowest = Color(0xFF111113), surfaceContainerLow = Color(0xFF17171A),
    surfaceContainer = Color(0xFF1B1B1E), surfaceContainerHigh = Color(0xFF26262A),
    surfaceContainerHighest = Color(0xFF303035)
)

private val BovaTypography = Typography(
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium)
)

private val BovaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun BovaTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = BovaTypography,
        shapes = BovaShapes,
        content = content
    )
}

/** Warna kartu catatan: 6 nuansa abu-abu/putih. */
val noteColorCount = 6
fun noteColor(index: Int, dark: Boolean): Color {
    val light = listOf(0xFFFFFFFF, 0xFFF1F1F3, 0xFFE7E8EB, 0xFFEFEDEA, 0xFFE4EAEF, 0xFFD9DDE2)
    val night = listOf(0xFF1B1B1E, 0xFF28282C, 0xFF323237, 0xFF2D2B29, 0xFF252A30, 0xFF3A3F46)
    val list = if (dark) night else light
    return Color(list[index.coerceIn(0, list.size - 1)])
}
