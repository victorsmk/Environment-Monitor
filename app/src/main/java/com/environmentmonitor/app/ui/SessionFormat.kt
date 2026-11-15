package com.environmentmonitor.app.ui

import com.environmentmonitor.app.data.Importance
import com.environmentmonitor.app.data.Session
import com.environmentmonitor.app.data.SensorType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** 0 -> "under 1 min", 42 min -> "42 min", 65 min -> "1 h 05 min", 3 h -> "3 h 00 min". */
fun formatDuration(millis: Long): String {
    val minutes = (millis.coerceAtLeast(0) / 60_000).toInt()
    return when {
        minutes < 1 -> "under 1 min"
        minutes < 60 -> "$minutes min"
        else -> "${minutes / 60} h ${(minutes % 60).toString().padStart(2, '0')} min"
    }
}

private val dateTimeFormat: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

/** Just the clock time in the phone's locale, e.g. "9:30 PM". Used on chart axes. */
fun formatTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    timeFormat.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

/** A start time in the phone's locale and time zone, e.g. "Nov 15, 2026, 9:30 PM". */
fun formatDateTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    dateTimeFormat.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

/** The ranges the session was judged against, e.g. "16–19 °C · 40–60 %". */
fun Session.summary(): String = SensorType.entries
    .filter { setting(it).importance != Importance.OFF }
    .joinToString(" · ") { sensor ->
        val setting = setting(sensor)
        sensor.formatRange(setting.idealMin, setting.idealMax)
    }
