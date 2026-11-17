package com.environmentmonitor.app.ui

import com.environmentmonitor.app.data.EnvironmentScore
import com.environmentmonitor.app.data.RangeStatus
import com.environmentmonitor.app.data.SensorScore
import com.environmentmonitor.app.data.SessionAnalysis

/** "Temperature is too high: 26.5 °C, ideal 20–24 °C" */
fun SensorScore.problemText(): String {
    val direction = if (status == RangeStatus.TOO_LOW) "too low" else "too high"
    return "${sensor.label} is $direction: ${formatValue(value)} ${sensor.unit}, " +
        "ideal ${sensor.formatRange(setting.idealMin, setting.idealMax)}"
}

/** One sentence about a finished (or running) session, e.g. "Light cost the most points, mostly too high". */
fun SessionAnalysis.headline(): String {
    val worst = worst ?: return "Everything stayed close to the ideal range"
    val direction = if (worst.mostlyTooHigh) "mostly too high" else "mostly too low"
    return "${worst.sensor.label} cost the most points, $direction"
}

/** The one line of advice shown under the score. */
fun EnvironmentScore.advice(): String =
    worst?.problemText() ?: if (missing.isEmpty()) {
        "Everything is in the ideal range"
    } else {
        "Everything measured is in the ideal range"
    }

/** "Score without light" or "Score without temperature and sound". Null when nothing is missing. */
fun EnvironmentScore.missingNote(): String? {
    if (missing.isEmpty()) return null
    val names = missing.map { it.label.lowercase() }
    val list = if (names.size == 1) names.single() else names.dropLast(1).joinToString(", ") + " and " + names.last()
    return "Score without $list: no data from " + if (names.size == 1) "that sensor" else "those sensors"
}
