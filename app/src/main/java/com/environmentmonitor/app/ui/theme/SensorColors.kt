package com.environmentmonitor.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.environmentmonitor.app.data.SensorType

/**
 * One colour per sensor, used for its chart line, the dot next to its name and the shaded ideal
 * range, so a sensor looks the same everywhere. Each has a darker variant for light mode and a
 * lighter one for dark mode; both keep at least a 3:1 contrast against the card colours
 * (checked by a unit test).
 */
data class SensorColorPair(val light: Color, val dark: Color)

val SensorType.colors: SensorColorPair
    get() = when (this) {
        SensorType.TEMPERATURE -> SensorColorPair(light = Color(0xFFC4511D), dark = Color(0xFFFF9A6B)) // warm orange
        SensorType.HUMIDITY -> SensorColorPair(light = Color(0xFF2B7BC4), dark = Color(0xFF7DB8F0)) // blue
        SensorType.LIGHT -> SensorColorPair(light = Color(0xFF9A7400), dark = Color(0xFFF5D547)) // gold
        SensorType.SOUND -> SensorColorPair(light = Color(0xFF7A4FB5), dark = Color(0xFFBFA2F0)) // purple
    }

/** This sensor's colour for the theme in use (the app's, which may differ from the phone's). */
@Composable
fun SensorType.color(): Color = if (LocalUseDarkTheme.current) colors.dark else colors.light

/** A small filled circle in a sensor's colour, shown before its name. */
@Composable
fun SensorDot(sensor: SensorType, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    Box(modifier.size(size).background(sensor.color(), CircleShape))
}
