package com.environmentmonitor.app.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.environmentmonitor.app.data.LightLevel
import com.environmentmonitor.app.data.ScoreLabel
import com.environmentmonitor.app.data.SensorSetting
import com.environmentmonitor.app.data.SensorType
import com.environmentmonitor.app.data.TimedValue
import com.environmentmonitor.app.ui.formatRange
import com.environmentmonitor.app.ui.formatTimeTick
import com.environmentmonitor.app.ui.formatValue
import com.environmentmonitor.app.ui.theme.color
import java.time.Instant
import java.time.ZoneId

/**
 * A shaded horizontal stripe behind the line, from value [from] up to [to]. A [label] is written
 * inside it at the right edge (the light levels, or Good / Fair / Poor on the score chart).
 */
data class ChartBand(val from: Float, val to: Float, val color: Color, val label: String? = null)

private const val MAX_DRAWN_POINTS = 400

/**
 * Time on the horizontal axis, a value on the vertical one, plus shaded bands. Both axes carry
 * several labelled lines, so the in-between values can be read, not just the extremes.
 * Drawn by hand on a Canvas, so no chart library is needed.
 */
@Composable
fun LineChart(
    points: List<TimedValue>,
    timeStart: Long,
    timeEnd: Long,
    axis: YAxis,
    bands: List<ChartBand>,
    lineColor: Color,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val scheme = MaterialTheme.colorScheme
    val gridColor = scheme.outlineVariant
    val axisStyle = TextStyle(fontSize = 11.sp, color = scheme.onSurfaceVariant)
    val bandStyle = TextStyle(fontSize = 10.sp, color = scheme.onSurfaceVariant.copy(alpha = 0.85f))
    val drawn = remember(points) { downsample(points, MAX_DRAWN_POINTS) }
    val timeAxis = remember(timeStart, timeEnd) {
        val offset = ZoneId.systemDefault().rules
            .getOffset(Instant.ofEpochMilli(timeStart)).totalSeconds * 1_000L
        timeTicks(timeStart, timeEnd, target = 4, offsetMillis = offset)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp),
    ) {
        val left = 44.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 24.dp.toPx()
        val right = size.width - 10.dp.toPx()
        val plotWidth = right - left
        val plotHeight = bottom - top

        fun x(timestamp: Long) = left + plotWidth * fractionOf(timestamp, timeStart, timeEnd)
        fun y(value: Float) = bottom - plotHeight * axis.toFraction(value)

        // Bands first, so everything else sits on top of them.
        bands.forEach { band ->
            val upper = y(band.to)
            val lower = y(band.from)
            if (lower > upper) {
                drawRect(band.color, Offset(left, upper), Size(plotWidth, lower - upper))
                if (band.label != null) {
                    val layout = measurer.measure(band.label, bandStyle)
                    if (layout.size.height <= lower - upper) {
                        drawText(
                            layout,
                            topLeft = Offset(
                                right - 4.dp.toPx() - layout.size.width,
                                upper + (lower - upper - layout.size.height) / 2f,
                            ),
                        )
                    }
                }
            }
        }

        // Horizontal lines with their values on the left.
        axis.ticks.forEach { tick ->
            val ty = y(tick.value)
            drawLine(gridColor, Offset(left, ty), Offset(right, ty), strokeWidth = 1.dp.toPx())
            val layout = measurer.measure(tick.label, axisStyle)
            val labelY = (ty - layout.size.height / 2f).coerceIn(0f, size.height - layout.size.height)
            drawText(layout, topLeft = Offset((left - 6.dp.toPx() - layout.size.width).coerceAtLeast(0f), labelY))
        }

        // Vertical lines with their times underneath.
        timeAxis.times.forEach { time ->
            val tx = x(time)
            drawLine(
                gridColor.copy(alpha = 0.6f),
                Offset(tx, top),
                Offset(tx, bottom),
                strokeWidth = 1.dp.toPx(),
            )
            val layout = measurer.measure(formatTimeTick(time, timeAxis.stepMillis), axisStyle)
            val labelX = (tx - layout.size.width / 2f).coerceIn(left - 6.dp.toPx(), size.width - layout.size.width)
            drawText(layout, topLeft = Offset(labelX, bottom + 4.dp.toPx()))
        }

        when {
            drawn.isEmpty() -> Unit
            drawn.size == 1 -> drawCircle(lineColor, 4.dp.toPx(), Offset(x(drawn[0].timestamp), y(drawn[0].value)))
            else -> {
                val path = Path().apply {
                    moveTo(x(drawn[0].timestamp), y(drawn[0].value))
                    for (i in 1 until drawn.size) lineTo(x(drawn[i].timestamp), y(drawn[i].value))
                }
                drawPath(
                    path,
                    lineColor,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                // Few points: mark each one, so a short session does not look like a smooth curve.
                if (drawn.size <= 40) {
                    drawn.forEach {
                        drawCircle(lineColor, 2.5.dp.toPx(), Offset(x(it.timestamp), y(it.value)))
                    }
                }
            }
        }
    }
}

/** A small coloured square and a caption, used under a chart. */
@Composable
fun ChartLegend(color: Color, text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(12.dp)
                .background(color, RoundedCornerShape(3.dp)),
        )
        Box(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The score over time, with Good, Fair and Poor zones shaded and named. */
@Composable
fun ScoreChart(points: List<TimedValue>, timeStart: Long, timeEnd: Long, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val fair = ScoreLabel.FAIR_FROM.toFloat()
    val good = ScoreLabel.GOOD_FROM.toFloat()
    LineChart(
        points = points,
        timeStart = timeStart,
        timeEnd = timeEnd,
        axis = scoreAxis(poorBelow = ScoreLabel.FAIR_FROM, goodFrom = ScoreLabel.GOOD_FROM),
        bands = listOf(
            ChartBand(good, 100f, scheme.primary.copy(alpha = 0.16f), "Good"),
            ChartBand(fair, good, scheme.tertiary.copy(alpha = 0.16f), "Fair"),
            ChartBand(0f, fair, scheme.error.copy(alpha = 0.14f), "Poor"),
        ),
        lineColor = scheme.onSurface,
        modifier = modifier,
    )
}

/**
 * One sensor over time, with its ideal range shaded. The light chart is different: its height is
 * split into the five light levels (Dark to Very bright), each named, so the chart reads like the
 * level picker in the profile editor.
 */
@Composable
fun SensorChart(
    sensor: SensorType,
    setting: SensorSetting,
    points: List<TimedValue>,
    timeStart: Long,
    timeEnd: Long,
    modifier: Modifier = Modifier,
) {
    val color = sensor.color()
    val scheme = MaterialTheme.colorScheme
    val ideal = ChartBand(setting.idealMin, setting.idealMax, color.copy(alpha = 0.22f))

    val axis: YAxis
    val bands: List<ChartBand>
    if (sensor == SensorType.LIGHT) {
        axis = lightAxis { formatValue(it) }
        // Alternate faint stripes keep the five levels apart; the ideal range is drawn over them.
        val stripes = LightLevel.entries.map { level ->
            ChartBand(
                from = level.fromLux,
                to = level.upperLux,
                color = scheme.onSurface.copy(alpha = if (level.ordinal % 2 == 0) 0.05f else 0.0f),
                label = level.label,
            )
        }
        bands = stripes + ideal
    } else {
        val range = chartRange(sensor, points.map { it.value }, setting.idealMin, setting.idealMax)
        axis = linearAxis(range, niceTicks(range.low, range.high, target = 4)) { formatValue(it) }
        bands = listOf(ideal)
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LineChart(
            points = points,
            timeStart = timeStart,
            timeEnd = timeEnd,
            axis = axis,
            bands = bands,
            lineColor = color,
        )
        ChartLegend(
            color = color.copy(alpha = 0.45f),
            text = "Ideal range ${sensor.formatRange(setting.idealMin, setting.idealMax)}",
            modifier = Modifier.padding(start = 44.dp),
        )
    }
}
