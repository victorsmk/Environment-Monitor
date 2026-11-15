package com.environmentmonitor.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F7A70),
    secondary = Color(0xFF3E5254),
    tertiary = Color(0xFFF2B84B),
    background = Color(0xFFF4F6F1),
    surface = Color(0xFFF4F6F1),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD1C4),
    secondary = Color(0xFFBBD3CF),
    tertiary = Color(0xFFF2B84B),
    background = Color(0xFF12282D),
    surface = Color(0xFF12282D),
)

@Composable
fun EnvironmentMonitorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
