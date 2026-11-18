package com.environmentmonitor.app.ui

import com.environmentmonitor.app.data.DataGap
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

/**
 * "Sensors out of reach once, for 12 min." or "Sensors out of reach 3 times, 1 h 05 min in all."
 * Null when there was no gap.
 */
fun List<DataGap>.gapSummary(): String? {
    if (isEmpty()) return null
    val total = formatDuration(sumOf { it.durationMillis })
    return if (size == 1) "Sensors out of reach once, for $total." else "Sensors out of reach $size times, $total in all."
}

private val dateTimeFormat: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

/** Just the clock time in the phone's locale, e.g. "9:30 PM". Used on chart axes. */
fun formatTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    timeFormat.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

private val timeWithSecondsFormat: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM)

/** A chart time label: with seconds when the labels are less than a minute apart. */
fun formatTimeTick(epochMillis: Long, stepMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    if (stepMillis < 60_000L) {
        timeWithSecondsFormat.format(Instant.ofEpochMilli(epochMillis).atZone(zone))
    } else {
        formatTime(epochMillis, zone)
    }

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
