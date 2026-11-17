package com.environmentmonitor.app.ui.settings

import com.environmentmonitor.app.data.SensorType
import com.environmentmonitor.app.sensors.PicoSample

/** What the "Find Pico" and "Test connection" buttons are doing, and what they last found out. */
sealed interface PicoCheck {
    data object Idle : PicoCheck

    data class Working(val text: String) : PicoCheck

    data class Success(val text: String) : PicoCheck

    data class Failure(val text: String) : PicoCheck
}

/** "Connected. Temperature 21.5 °C, humidity 48 %, light 300 lux, sound 17." (a missing value says so). */
fun PicoSample.summary(): String {
    val parts = SensorType.entries.map { sensor ->
        val value = measurement.value(sensor)
        val text = if (value == null) "no value" else "${format(sensor, value)} ${sensor.unit}"
        "${sensor.label.lowercase()} $text"
    }
    return "Connected. " + parts.joinToString(", ").replaceFirstChar { it.uppercase() } + "."
}

private fun format(sensor: SensorType, value: Float): String =
    if (sensor == SensorType.TEMPERATURE || sensor == SensorType.HUMIDITY) {
        String.format(java.util.Locale.US, "%.1f", value)
    } else {
        Math.round(value).toString()
    }
