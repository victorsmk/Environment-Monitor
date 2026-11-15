package com.environmentmonitor.app.ui.charts

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.environmentmonitor.app.data.ScoreLabel
import com.environmentmonitor.app.data.SensorSetting
import com.environmentmonitor.app.data.SensorType
import com.environmentmonitor.app.data.TimedValue
import com.environmentmonitor.app.ui.formatRange
import com.environmentmonitor.app.ui.formatTime
import com.environmentmonitor.app.ui.formatValue
import com.environmentmonitor.app.ui.theme.color

/** A shaded horizontal stripe behind the line, from value [from] up to [to]. */
data class ChartBand(val from: Float, val to: Float, val color: Color)

private const val MAX_DRAWN_POINTS = 400

/**
 * Time on the horizontal axis, a value on the vertical one, plus shaded bands.
 * Drawn by hand on a Canvas, so no chart library is needed.
 */
@Composable
fun LineChart(
    points: List<TimedValue>,
    timeStart: Long,
    timeEnd: Long,
    range: ValueRange,
    bands: List<ChartBand>,
    lineColor: Color,
    formatY: (Float) -> String,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = TextStyle(
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val drawn = downsample(points, MAX_DRAWN_POINTS)

    Column(modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp),
        ) {
            val left = 44.dp.toPx()
            val top = 6.dp.toPx()
            val bottom = size.height - 6.dp.toPx()
            val right = size.width - 6.dp.toPx()
            val plotWidth = right - left
            val plotHeight = bottom - top
            val span = (range.high - range.low).coerceAtLeast(0.0001f)

            fun x(timestamp: Long) = left + plotWidth * fractionOf(timestamp, timeStart, timeEnd)
            fun y(value: Float) = bottom - plotHeight * ((value - range.low) / span).coerceIn(0f, 1f)

            // Bands first, so the line sits on top of them.
            bands.forEach { band ->
                val upper = y(band.to)
                val lower = y(band.from)
                if (lower > upper) {
                    drawRect(band.color, Offset(left, upper), Size(plotWidth, lower - upper))
                }
            }

            // Frame and value labels at the top and bottom of the axis.
            drawLine(gridColor, Offset(left, top), Offset(right, top), strokeWidth = 1.dp.toPx())
            drawLine(gridColor, Offset(left, bottom), Offset(right, bottom), strokeWidth = 1.dp.toPx())
            drawAxisLabel(measurer, formatY(range.high), labelStyle, left, top, aboveLine = false)
            drawAxisLabel(measurer, formatY(range.low), labelStyle, left, bottom, aboveLine = true)

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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 44.dp, end = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTime(timeStart),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatTime(timeEnd),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun DrawScope.drawAxisLabel(
    measurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    style: TextStyle,
    plotLeft: Float,
    lineY: Float,
    aboveLine: Boolean,
) {
    val layout = measurer.measure(text, style)
    val x = (plotLeft - 6.dp.toPx() - layout.size.width).coerceAtLeast(0f)
    val y = if (aboveLine) lineY - layout.size.height else lineY
    drawText(layout, topLeft = Offset(x, y))
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

/** The score over time, with Good, Fair and Poor zones shaded. */
@Composable
fun ScoreChart(points: List<TimedValue>, timeStart: Long, timeEnd: Long, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    LineChart(
        points = points,
        timeStart = timeStart,
        timeEnd = timeEnd,
        range = ValueRange(0f, 100f),
        bands = listOf(
            ChartBand(ScoreLabel.GOOD_FROM.toFloat(), 100f, scheme.primary.copy(alpha = 0.16f)),
            ChartBand(ScoreLabel.FAIR_FROM.toFloat(), ScoreLabel.GOOD_FROM.toFloat(), scheme.tertiary.copy(alpha = 0.16f)),
            ChartBand(0f, ScoreLabel.FAIR_FROM.toFloat(), scheme.error.copy(alpha = 0.14f)),
        ),
        lineColor = scheme.onSurface,
        formatY = { it.toInt().toString() },
        modifier = modifier,
    )
}

/** One sensor over time, with its ideal range shaded. */
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
    val range = chartRange(sensor, points.map { it.value }, setting.idealMin, setting.idealMax)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LineChart(
            points = points,
            timeStart = timeStart,
            timeEnd = timeEnd,
            range = range,
            bands = listOf(ChartBand(setting.idealMin, setting.idealMax, color.copy(alpha = 0.2f))),
            lineColor = color,
            formatY = { formatValue(if (sensor == SensorType.TEMPERATURE) it else kotlin.math.round(it)) },
        )
        ChartLegend(
            color = color.copy(alpha = 0.4f),
            text = "Ideal range ${sensor.formatRange(setting.idealMin, setting.idealMax)}",
            modifier = Modifier.padding(start = 44.dp),
        )
    }
}
