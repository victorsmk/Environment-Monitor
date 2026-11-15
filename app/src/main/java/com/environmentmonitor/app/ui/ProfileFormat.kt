package com.environmentmonitor.app.ui

import com.environmentmonitor.app.data.Importance
import com.environmentmonitor.app.data.Profile
import com.environmentmonitor.app.data.SensorType
import java.util.Locale

/** 20 -> "20", 20.5 -> "20.5" */
fun formatValue(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else String.format(Locale.US, "%.1f", value)

/** "16–19 °C" */
fun SensorType.formatRange(min: Float, max: Float): String =
    "${formatValue(min)}–${formatValue(max)} $unit"

/** One line listing the range of every sensor that counts, e.g. "16–19 °C · 40–60 % · 0–10 lux". */
fun Profile.summary(): String = SensorType.entries
    .filter { setting(it).importance != Importance.OFF }
    .joinToString(" · ") { sensor ->
        val setting = setting(sensor)
        sensor.formatRange(setting.idealMin, setting.idealMax)
    }

/** Emoji offered in the profile editor. */
val ProfileIcons = listOf("😴", "💼", "🏃", "📚", "🎮", "🧘", "🍳", "🌿", "🎨", "🎧")
