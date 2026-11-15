package com.environmentmonitor.app.ui

import com.environmentmonitor.app.data.EnvironmentScore
import com.environmentmonitor.app.data.RangeStatus
import com.environmentmonitor.app.data.SensorScore

/** "Temperature is too high: 26.5 °C, ideal 20–24 °C" */
fun SensorScore.problemText(): String {
    val direction = if (status == RangeStatus.TOO_LOW) "too low" else "too high"
    return "${sensor.label} is $direction: ${formatValue(value)} ${sensor.unit}, " +
        "ideal ${sensor.formatRange(setting.idealMin, setting.idealMax)}"
}

/** The one line of advice shown under the score. */
fun EnvironmentScore.advice(): String =
    worst?.problemText() ?: "Everything is in the ideal range"
