package com.environmentmonitor.app.data

import kotlin.math.roundToInt

/** A value at a moment in time: one point of a chart. */
data class TimedValue(val timestamp: Long, val value: Float)

/** How one sensor behaved over a whole session. */
data class SensorStats(
    val sensor: SensorType,
    val setting: SensorSetting,
    val min: Float,
    val average: Float,
    val max: Float,
    /** Share of the readings inside the ideal range, 0..1. */
    val inRangeFraction: Float,
    val tooLowFraction: Float,
    val tooHighFraction: Float,
    /** Average points this sensor took off the overall score. */
    val averagePointsLost: Float,
    /** The raw readings, for drawing. */
    val points: List<TimedValue>,
) {
    val inRangePercent: Int get() = (inRangeFraction * 100f).roundToInt()

    /** Which way it mostly missed. Only meaningful when it was out of range at least once. */
    val mostlyTooHigh: Boolean get() = tooHighFraction >= tooLowFraction
}

/** Everything the results part of the session screen shows. */
data class SessionAnalysis(
    val readingCount: Int,
    /** Average of the score over the session, 0–100. */
    val overall: Int,
    val label: ScoreLabel,
    /** One entry per sensor that counts for this session's profile. */
    val sensors: List<SensorStats>,
    /** The overall score after each reading, for drawing. */
    val scorePoints: List<TimedValue>,
    /** The sensor that cost the most points on average, or null if none mattered. */
    val worst: SensorStats?,
)

/** Works out a session's results from its stored readings and its settings snapshot. */
object SessionAnalyzer {

    /** Below this many points lost on average, a sensor is not worth calling out. */
    const val WORST_THRESHOLD = 0.5f

    /** Returns null when there are no readings, or when every sensor is Off. */
    fun analyze(settings: Map<SensorType, SensorSetting>, readings: List<Reading>): SessionAnalysis? {
        if (readings.isEmpty()) return null
        val scores = readings.map {
            ScoringEngine.evaluate(settings, it.toMeasurement()) ?: return null
        }
        val count = readings.size.toFloat()

        val stats = scores.first().sensors.map { it.sensor }.map { sensor ->
            val perReading = scores.map { score -> score.sensors.first { it.sensor == sensor } }
            val values = perReading.map { it.value }
            SensorStats(
                sensor = sensor,
                setting = settings.getValue(sensor),
                min = values.min(),
                average = values.average().toFloat(),
                max = values.max(),
                inRangeFraction = perReading.count { it.status == RangeStatus.IN_RANGE } / count,
                tooLowFraction = perReading.count { it.status == RangeStatus.TOO_LOW } / count,
                tooHighFraction = perReading.count { it.status == RangeStatus.TOO_HIGH } / count,
                averagePointsLost = perReading.map { it.pointsLost }.average().toFloat(),
                points = readings.mapIndexed { i, r -> TimedValue(r.timestamp, values[i]) },
            )
        }

        val overall = scores.map { it.overall }.average().roundToInt().coerceIn(0, 100)
        return SessionAnalysis(
            readingCount = readings.size,
            overall = overall,
            label = ScoreLabel.of(overall),
            sensors = stats,
            scorePoints = readings.mapIndexed { i, r ->
                TimedValue(r.timestamp, scores[i].overall.toFloat())
            },
            worst = stats.maxByOrNull { it.averagePointsLost }
                ?.takeIf { it.averagePointsLost >= WORST_THRESHOLD },
        )
    }
}
