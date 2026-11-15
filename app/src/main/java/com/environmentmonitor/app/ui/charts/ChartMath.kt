package com.environmentmonitor.app.ui.charts

import com.environmentmonitor.app.data.LightLevel
import com.environmentmonitor.app.data.SensorType
import com.environmentmonitor.app.data.TimedValue
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round

/** The value range a chart's vertical axis covers. */
data class ValueRange(val low: Float, val high: Float)

/** A labelled line across a chart, such as "18" at the height of 18 degrees. */
data class AxisTick(val value: Float, val label: String)

/**
 * The vertical axis of a chart: which values get a labelled line, and how a value maps to a height
 * (0 is the bottom, 1 the top). Most charts are a straight scale; the light chart is not.
 */
class YAxis(val ticks: List<AxisTick>, val toFraction: (Float) -> Float)

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
 * Round numbers to label an axis between [low] and [high]: about [target] of them, in steps of
 * 1, 2 or 5 times a power of ten (so 16, 17, 18, 19 rather than 16.12, 17.04, ...).
 */
fun niceTicks(low: Float, high: Float, target: Int = 4): List<Float> {
    if (high <= low || target < 1) return listOf(low)
    val raw = (high - low).toDouble() / target
    val magnitude = 10.0.pow(floor(log10(raw)))
    val normalized = raw / magnitude
    val step = magnitude * when {
        normalized < 1.5 -> 1.0
        normalized < 3.0 -> 2.0
        normalized < 7.0 -> 5.0
        else -> 10.0
    }
    return ticksEvery(low, high, step)
}

/**
 * Labels for an axis between [low] and [high] from a fixed list of [steps] (smallest first): the
 * smallest step that still gives no more than [maxTicks] labels. A narrow range gets fine steps
 * and a wide one coarse steps, but the labels always land on the same familiar numbers.
 */
fun stepTicks(low: Float, high: Float, steps: List<Double>, maxTicks: Int = 8): List<Float> {
    if (high <= low) return listOf(low)
    val step = steps.firstOrNull { ticksEvery(low, high, it).size <= maxTicks } ?: steps.last()
    return ticksEvery(low, high, step)
}

/** Every multiple of [step] that lies between [low] and [high]. */
private fun ticksEvery(low: Float, high: Float, step: Double): List<Float> {
    val ticks = mutableListOf<Float>()
    var index = ceil(low / step - 1e-9).toLong()
    // A hair of tolerance, because a float like 17.4f is really 17.39999962.
    while (index * step <= high + step * 1e-4) {
        // Multiplying the whole index keeps 0.1 + 0.1 + 0.1 style rounding noise out.
        ticks += (round(index * step * 1_000_000) / 1_000_000).toFloat()
        index++
    }
    return ticks
}

/** Temperature labels: every half degree for a narrow range, up to every 10 degrees for a wide one. */
val TEMPERATURE_STEPS = listOf(0.5, 1.0, 2.0, 5.0, 10.0)

/** Humidity labels: always multiples of 5, widening to 10, 20 or 25 when the range is big. */
val HUMIDITY_STEPS = listOf(5.0, 10.0, 20.0, 25.0, 50.0)

/**
 * The boundaries of alternating stripes behind a chart: the lines between the labels, plus the two
 * edges of the chart, so even the part above the top label and below the bottom label is striped.
 */
fun stripeEdges(range: ValueRange, ticks: List<Float>): List<Float> =
    (listOf(range.low) + ticks.filter { it > range.low && it < range.high } + range.high).distinct()

/** A straight vertical scale from [range] with a labelled line at each of [ticks]. */
fun linearAxis(range: ValueRange, ticks: List<Float>, format: (Float) -> String): YAxis {
    val span = (range.high - range.low).coerceAtLeast(0.0001f)
    return YAxis(
        ticks = ticks.map { AxisTick(it, format(it)) },
        toFraction = { ((it - range.low) / span).coerceIn(0f, 1f) },
    )
}

/**
 * The light chart's scale: the five light levels get equal heights (see [LightLevel.position]),
 * with a labelled line at each level boundary in lux.
 */
fun lightAxis(format: (Float) -> String): YAxis {
    val boundaries = LightLevel.entries.map { it.fromLux } + SensorType.LIGHT.max
    return YAxis(
        ticks = boundaries.map { AxisTick(it, format(it)) },
        toFraction = { LightLevel.position(it) },
    )
}

/**
 * The score chart: 0 to 100 with a labelled line at each of [values]. The lines are denser in the
 * lower zones, where telling a poor score from a very poor one matters.
 */
fun scoreAxis(values: List<Int>): YAxis = YAxis(
    ticks = values.map { AxisTick(it.toFloat(), it.toString()) },
    toFraction = { (it / 100f).coerceIn(0f, 1f) },
)

/** The labelled score values: the zone edges (50 and 80) plus 65, 30, 10 and the two ends. */
val SCORE_LABELS = listOf(0, 10, 30, 50, 65, 80, 100)

/** Labelled moments along the horizontal axis, [stepMillis] apart. */
data class TimeTicks(val stepMillis: Long, val times: List<Long>)

private const val SECOND = 1_000L
private const val MINUTE = 60 * SECOND
private const val HOUR = 60 * MINUTE

private val TIME_STEPS = listOf(
    1 * SECOND, 2 * SECOND, 5 * SECOND, 10 * SECOND, 15 * SECOND, 30 * SECOND,
    1 * MINUTE, 2 * MINUTE, 5 * MINUTE, 10 * MINUTE, 15 * MINUTE, 30 * MINUTE,
    1 * HOUR, 2 * HOUR, 3 * HOUR, 6 * HOUR, 12 * HOUR, 24 * HOUR,
)

/**
 * Round clock times between [start] and [end] to label the time axis: at most about [target] of
 * them, on whole seconds, minutes or hours of the local clock ([offsetMillis] is the time zone's
 * offset from UTC, so 18:00 lands on 18:00 and not on 17:30 in a half-hour zone).
 */
fun timeTicks(start: Long, end: Long, target: Int = 4, offsetMillis: Long = 0): TimeTicks {
    if (end <= start) return TimeTicks(SECOND, listOf(start))
    val span = end - start
    val step = TIME_STEPS.firstOrNull { span / it <= target } ?: TIME_STEPS.last()
    val first = Math.floorDiv(start + offsetMillis + step - 1, step) * step - offsetMillis
    val times = generateSequence(first) { it + step }.takeWhile { it <= end }.toList()
    // A very short span may have no round time inside it: fall back to its two ends.
    return TimeTicks(step, times.ifEmpty { listOf(start, end) })
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
