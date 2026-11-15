package com.environmentmonitor.app.ui.charts

import com.environmentmonitor.app.data.SensorType
import com.environmentmonitor.app.data.TimedValue
import kotlin.math.ceil

/** The value range a chart's vertical axis covers. */
data class ValueRange(val low: Float, val high: Float)

/**
 * The vertical range for a sensor chart: wide enough to show every reading and the whole ideal
 * range, with a little room above and below, and never outside what the sensor can measure.
 */
fun chartRange(
    sensor: SensorType,
    values: List<Float>,
    idealMin: Float,
    idealMax: Float,
): ValueRange {
    var low = minOf(idealMin, values.minOrNull() ?: idealMin)
    var high = maxOf(idealMax, values.maxOrNull() ?: idealMax)
    // Never squash everything into a flat line.
    val minimumSpan = sensor.tolerance / 2f
    if (high - low < minimumSpan) {
        val extra = (minimumSpan - (high - low)) / 2f
        low -= extra
        high += extra
    }
    val padding = (high - low) * 0.08f
    return ValueRange(
        low = (low - padding).coerceAtLeast(sensor.min),
        high = (high + padding).coerceAtMost(sensor.max),
    )
}

/**
 * Keeps a chart cheap to draw: at most about [maxPoints] points, each the average of a run of
 * neighbours. Short series are returned unchanged.
 */
fun downsample(points: List<TimedValue>, maxPoints: Int): List<TimedValue> {
    require(maxPoints > 0) { "maxPoints must be positive" }
    if (points.size <= maxPoints) return points
    val bucketSize = ceil(points.size / maxPoints.toDouble()).toInt()
    return points.chunked(bucketSize).map { bucket ->
        TimedValue(
            timestamp = bucket.map { it.timestamp }.average().toLong(),
            value = bucket.map { it.value }.average().toFloat(),
        )
    }
}

/** Where [value] sits between [start] and [end], 0..1. A zero-length span puts everything at 0. */
fun fractionOf(value: Long, start: Long, end: Long): Float =
    if (end <= start) 0f else ((value - start).toFloat() / (end - start)).coerceIn(0f, 1f)
