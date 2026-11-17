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
    /** The raw readings, for drawing. Only the moments when this sensor had a value. */
    val points: List<TimedValue>,
    /** Share of the session's readings where this sensor had no value, 0..1. */
    val missingFraction: Float = 0f,
) {
    val inRangePercent: Int get() = (inRangeFraction * 100f).roundToInt()

    val missingPercent: Int get() = (missingFraction * 100f).roundToInt()

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
    /** Sensors that count for this profile but never had a value during the whole session. */
    val noDataSensors: List<SensorType> = emptyList(),
)

/** Works out a session's results from its stored readings and its settings snapshot. */
object SessionAnalyzer {

    /** Below this many points lost on average, a sensor is not worth calling out. */
    const val WORST_THRESHOLD = 0.5f

    /**
     * Returns null when there are no readings, when every sensor is Off, or when no reading had a
     * value for any sensor that counts. A sensor with no value in a reading is left out of that
     * reading's score, and its own statistics use only the readings where it had one.
     */
    fun analyze(settings: Map<SensorType, SensorSetting>, readings: List<Reading>): SessionAnalysis? {
        if (readings.isEmpty()) return null
        val scored = readings.mapNotNull { reading ->
            ScoringEngine.evaluate(settings, reading.toMeasurement())?.let { reading to it }
        }
        if (scored.isEmpty()) return null

        val counted = SensorType.entries.filter { (settings[it]?.importance?.weight ?: 0) > 0 }
        val stats = counted.mapNotNull { sensor ->
            val perReading = scored.mapNotNull { (reading, score) ->
                score.sensors.firstOrNull { it.sensor == sensor }?.let { reading to it }
            }
            if (perReading.isEmpty()) return@mapNotNull null
            val count = perReading.size.toFloat()
            val values = perReading.map { it.second.value }
            SensorStats(
                sensor = sensor,
                setting = settings.getValue(sensor),
                min = values.min(),
                average = values.average().toFloat(),
                max = values.max(),
                inRangeFraction = perReading.count { it.second.status == RangeStatus.IN_RANGE } / count,
                tooLowFraction = perReading.count { it.second.status == RangeStatus.TOO_LOW } / count,
                tooHighFraction = perReading.count { it.second.status == RangeStatus.TOO_HIGH } / count,
                averagePointsLost = perReading.map { it.second.pointsLost }.average().toFloat(),
                points = perReading.map { (reading, score) -> TimedValue(reading.timestamp, score.value) },
                missingFraction = 1f - perReading.size / readings.size.toFloat(),
            )
        }

        val overall = scored.map { it.second.overall }.average().roundToInt().coerceIn(0, 100)
        return SessionAnalysis(
            readingCount = readings.size,
            overall = overall,
            label = ScoreLabel.of(overall),
            sensors = stats,
            scorePoints = scored.map { (reading, score) ->
                TimedValue(reading.timestamp, score.overall.toFloat())
            },
            worst = stats.maxByOrNull { it.averagePointsLost }
                ?.takeIf { it.averagePointsLost >= WORST_THRESHOLD },
            noDataSensors = counted.filter { sensor -> stats.none { it.sensor == sensor } },
        )
    }
}
