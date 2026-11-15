package com.environmentmonitor.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Same palette as the specification slides: deep teal ink, warm paper, teal and amber accents.
private val LightColors = lightColorScheme(
    primary = Color(0xFF1F7A70),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDEBE4),
    onPrimaryContainer = Color(0xFF0F4F47),
    secondary = Color(0xFF3E5254),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE8E3),
    onSecondaryContainer = Color(0xFF1B3A40),
    tertiary = Color(0xFF8A5F00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFBE3B0),
    onTertiaryContainer = Color(0xFF3B2800),
    background = Color(0xFFF4F6F1),
    onBackground = Color(0xFF12282D),
    surface = Color(0xFFF4F6F1),
    onSurface = Color(0xFF12282D),
    surfaceVariant = Color(0xFFE3EAE5),
    onSurfaceVariant = Color(0xFF3E5254),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F4EE),
    surfaceContainer = Color(0xFFEAF0EB),
    surfaceContainerHigh = Color(0xFFE4EBE6),
    surfaceContainerHighest = Color(0xFFDEE6E0),
    outline = Color(0xFF6B7F80),
    outlineVariant = Color(0xFFC3D0CB),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD1C4),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF14504A),
    onPrimaryContainer = Color(0xFFCDEBE4),
    secondary = Color(0xFFBBD3CF),
    onSecondary = Color(0xFF1B3A40),
    secondaryContainer = Color(0xFF2A4A50),
    onSecondaryContainer = Color(0xFFDCE8E3),
    tertiary = Color(0xFFF2B84B),
    onTertiary = Color(0xFF3B2800),
    tertiaryContainer = Color(0xFF5C4200),
    onTertiaryContainer = Color(0xFFFBE3B0),
    background = Color(0xFF12282D),
    onBackground = Color(0xFFF4F6F1),
    surface = Color(0xFF12282D),
    onSurface = Color(0xFFF4F6F1),
    surfaceVariant = Color(0xFF2A4A50),
    onSurfaceVariant = Color(0xFFBBD3CF),
    surfaceContainerLowest = Color(0xFF0D1F23),
    surfaceContainerLow = Color(0xFF162E33),
    surfaceContainer = Color(0xFF1B3A40),
    surfaceContainerHigh = Color(0xFF22444B),
    surfaceContainerHighest = Color(0xFF2A4F56),
    outline = Color(0xFF8FB2AD),
    outlineVariant = Color(0xFF3E5E62),
)

/**
 * Whether the dark colours are in use right now. Read this (not the phone's own setting) wherever a
 * colour depends on light or dark, because the user can pick a mode in Settings that differs from
 * the phone's.
 */
val LocalUseDarkTheme = staticCompositionLocalOf { false }

@Composable
fun EnvironmentMonitorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalUseDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            content = content,
        )
    }
}
