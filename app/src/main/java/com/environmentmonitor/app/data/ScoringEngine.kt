package com.environmentmonitor.app.data

import kotlin.math.roundToInt

enum class ScoreLabel(val text: String) {
    GOOD("Good"),
    FAIR("Fair"),
    POOR("Poor");

    companion object {
        const val GOOD_FROM = 80
        const val FAIR_FROM = 50

        fun of(score: Int): ScoreLabel = when {
            score >= GOOD_FROM -> GOOD
            score >= FAIR_FROM -> FAIR
            else -> POOR
        }
    }
}

enum class RangeStatus { TOO_LOW, IN_RANGE, TOO_HIGH }

/** How one sensor did against its ideal range. */
data class SensorScore(
    val sensor: SensorType,
    val value: Float,
    val setting: SensorSetting,
    /** 0–100 for this sensor alone. */
    val score: Float,
    val status: RangeStatus,
    /** Points this sensor takes off the overall score (0–100 scale). */
    val pointsLost: Float,
)

/** The verdict for one measurement. */
data class EnvironmentScore(
    /** 0–100, rounded. */
    val overall: Int,
    val label: ScoreLabel,
    /** One entry per sensor that counts (importance not Off), in sensor order. */
    val sensors: List<SensorScore>,
) {
    /** The sensor costing the most points, or null when everything is in range. */
    val worst: SensorScore?
        get() = sensors.filter { it.pointsLost > 0f }.maxByOrNull { it.pointsLost }
}

/**
 * Turns a measurement and a profile's settings into a score.
 *
 * Pure Kotlin on purpose: no Android classes, so it is fast to unit test.
 */
object ScoringEngine {

    /** Score of one sensor: 100 inside the range, falling straight to 0 at [SensorType.tolerance]. */
    fun sensorScore(sensor: SensorType, setting: SensorSetting, value: Float): Float {
        val distance = distanceOutside(setting, value)
        return (100f * (1f - distance / sensor.tolerance)).coerceIn(0f, 100f)
    }

    fun status(setting: SensorSetting, value: Float): RangeStatus = when {
        value < setting.idealMin -> RangeStatus.TOO_LOW
        value > setting.idealMax -> RangeStatus.TOO_HIGH
        else -> RangeStatus.IN_RANGE
    }

    /**
     * Weighted average of the sensors that count. Returns null when every sensor is Off,
     * because there is nothing to judge (the profile editor does not allow that).
     */
    fun evaluate(settings: Map<SensorType, SensorSetting>, measurement: Measurement): EnvironmentScore? {
        val active = SensorType.entries.filter {
            (settings[it]?.importance?.weight ?: 0) > 0
        }
        val totalWeight = active.sumOf { settings.getValue(it).importance.weight }
        if (totalWeight == 0) return null

        val scores = active.map { sensor ->
            val setting = settings.getValue(sensor)
            val value = measurement.value(sensor)
            val score = sensorScore(sensor, setting, value)
            SensorScore(
                sensor = sensor,
                value = value,
                setting = setting,
                score = score,
                status = status(setting, value),
                pointsLost = (100f - score) * setting.importance.weight / totalWeight,
            )
        }
        val overall = (100f - scores.sumOf { it.pointsLost.toDouble() }.toFloat())
            .roundToInt()
            .coerceIn(0, 100)
        return EnvironmentScore(overall, ScoreLabel.of(overall), scores)
    }

    private fun distanceOutside(setting: SensorSetting, value: Float): Float = when {
        value < setting.idealMin -> setting.idealMin - value
        value > setting.idealMax -> value - setting.idealMax
        else -> 0f
    }
}
