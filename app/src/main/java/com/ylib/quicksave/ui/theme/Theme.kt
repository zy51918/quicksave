package com.ylib.quicksave.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 品牌强调色：浅色底上的强调文字与描边。
 *
 * 主色 [Lime] 亮度极高（白字在其上仅 1.46:1，它自己作小字落在 Paper 上仅 1.36:1），
 * 所以它只当填充用。凡是「把主色当文字/描边画在浅色底上」的地方都改用本对象：
 * 浅色主题走深橄榄 [LimeDark]（Paper 上 7.6:1），深色主题直接走 [Lime]（Night 上 11.9:1）。
 */
@Immutable
data class BrandColors(
    val accent: Color
)

private val LightBrandColors = BrandColors(accent = LimeDark)

private val DarkBrandColors = BrandColors(accent = Lime)

private val LocalBrandColors = staticCompositionLocalOf { LightBrandColors }

/** 当前主题下可读的品牌强调色，用法：`MaterialTheme.brandColors.accent` */
val MaterialTheme.brandColors: BrandColors
    @Composable
    @ReadOnlyComposable
    get() = LocalBrandColors.current

private val DarkColorScheme = darkColorScheme(
    primary = Lime,
    onPrimary = LimeInk,
    primaryContainer = LimeDark,
    onPrimaryContainer = LimePale,
    secondary = Color(0xFFC9E370),
    onSecondary = Night,
    secondaryContainer = NightVariant,
    onSecondaryContainer = Color(0xFFF5FAE5),
    tertiary = Color(0xFFFFB4A6),
    onTertiary = Color(0xFF3B0904),
    tertiaryContainer = Color(0xFF6F2E26),
    onTertiaryContainer = Color(0xFFFFDAD3),
    background = Night,
    onBackground = Color(0xFFE3F0F1),
    surface = NightSurface,
    onSurface = Color(0xFFE3F0F1),
    surfaceVariant = NightVariant,
    onSurfaceVariant = Color(0xFFB9CED0),
    outline = Color(0xFF829A9D),
    outlineVariant = Color(0xFF405A61),
    error = Color(0xFFFFB4A8),
    onError = Color(0xFF680F07)
)

private val LightColorScheme = lightColorScheme(
    primary = Lime,
    onPrimary = LimeInk,
    primaryContainer = LimePale,
    onPrimaryContainer = LimeDark,
    secondary = LimeDark,
    onSecondary = Color.White,
    secondaryContainer = LimePale,
    onSecondaryContainer = LimeDark,
    tertiary = Coral,
    onTertiary = Color.White,
    tertiaryContainer = CoralPale,
    onTertiaryContainer = Color(0xFF4A160F),
    background = Paper,
    onBackground = Ink,
    surface = SurfaceWhite,
    onSurface = Ink,
    surfaceVariant = Mist,
    onSurfaceVariant = InkSoft,
    outline = Color(0xFF71888D),
    outlineVariant = Color(0xFFC5D2D4),
    error = Coral,
    onError = Color.White
)

@Composable
fun QuickSaveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val brandColors = if (darkTheme) DarkBrandColors else LightBrandColors
    CompositionLocalProvider(LocalBrandColors provides brandColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}
