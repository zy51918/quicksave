package com.ylib.quicksave.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = TealPale,
    onPrimary = Night,
    primaryContainer = TealDark,
    onPrimaryContainer = TealPale,
    secondary = Color(0xFFA6C9C8),
    onSecondary = Night,
    secondaryContainer = NightVariant,
    onSecondaryContainer = Color(0xFFD5E9E8),
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
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = TealPale,
    onPrimaryContainer = Color(0xFF003735),
    secondary = InkSoft,
    onSecondary = Color.White,
    secondaryContainer = Mist,
    onSecondaryContainer = Ink,
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
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
