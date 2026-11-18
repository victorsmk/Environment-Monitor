package com.environmentmonitor.app.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.environmentmonitor.app.data.LightLevel
import com.environmentmonitor.app.data.RangeStatus
import com.environmentmonitor.app.data.ScoreLabel
import com.environmentmonitor.app.data.ScoringEngine
import com.environmentmonitor.app.data.SensorSetting
import com.environmentmonitor.app.data.SensorType
import com.environmentmonitor.app.data.TimedValue
import com.environmentmonitor.app.ui.formatRange
import com.environmentmonitor.app.ui.formatReading
import com.environmentmonitor.app.ui.formatTimeTick
import com.environmentmonitor.app.ui.formatValue
import com.environmentmonitor.app.ui.theme.color
import java.time.Instant
import java.time.ZoneId

/**
 * A shaded horizontal stripe behind the line, from value [from] up to [to]. A [label] is written
 * inside it at the right edge (the light levels, or Good / Fair / Poor on the score chart).
 */
data class ChartBand(
    val from: Float,
    val to: Float,
    val color: Color,
    val label: String? = null,
    /** Write the label just under the top edge instead of in the middle (keeps it clear of a line in the middle). */
    val labelAtTop: Boolean = false,
)

/** What the bubble shows for the reading the user touched: a [value] line and an optional [detail] line. */
data class ChartTooltip(val value: String, val detail: String? = null)

private const val MAX_DRAWN_POINTS = 400

/** Space left of the plot for the value labels, and the margins around it. */
private val PLOT_LEFT = 44.dp
private val PLOT_RIGHT_MARGIN = 10.dp

/**
 * Time on the horizontal axis, a value on the vertical one, plus shaded bands. Both axes carry
 * several labelled lines, so the in-between values can be read, not just the extremes.
 *
 * Touch:
 * - **Tap or drag** one finger along the chart to read the exact value and time of the closest
 *   reading, in a bubble (tap the same reading again to hide it).
 * - **Pinch** with two fingers to zoom in on a stretch of time; the readings in view are redrawn
 *   in finer detail. Two-finger drags pan, and the arrow buttons step earlier or later.
 * - A one-finger drag that goes mostly up or down still scrolls the page.
 *
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
    tooltip: ((TimedValue) -> ChartTooltip)? = null,
    /** Two readings further apart than this have a gap between them: the line stops and the gap is shaded. */
    gapMillis: Long = Long.MAX_VALUE,
) {
    val measurer = rememberTextMeasurer()
    val scheme = MaterialTheme.colorScheme
    val gridColor = scheme.outlineVariant
    val gapColor = scheme.onSurface.copy(alpha = 0.08f)
    val axisStyle = TextStyle(fontSize = 11.sp, color = scheme.onSurfaceVariant)
    val bandStyle = TextStyle(fontSize = 10.sp, color = scheme.onSurfaceVariant.copy(alpha = 0.85f))
    val tipSmallStyle = TextStyle(fontSize = 11.sp, color = scheme.onSurfaceVariant)
    val tipValueStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)

    // What the user has done to the chart: a zoomed window (null = everything) and a touched reading.
    val full = TimeWindow(timeStart, timeEnd)
    var zoomed by remember { mutableStateOf<TimeWindow?>(null) }
    var selectedTime by remember { mutableStateOf<Long?>(null) }
    val view = zoomed?.within(full) ?: full
    val canZoom = points.size >= 4

    // The gesture code runs for a long time; these keep it looking at the latest values.
    val latestPoints by rememberUpdatedState(points)
    val latestFull by rememberUpdatedState(full)
    val latestView by rememberUpdatedState(view)

    // The readings in view, in stretches: a gap in the data ends one stretch and starts the next.
    val stretches = remember(points, view, gapMillis) {
        splitAtGaps(visibleSlice(points, view), gapMillis).map { downsample(it, MAX_DRAWN_POINTS) }
    }
    val timeAxis = remember(view) {
        val offset = ZoneId.systemDefault().rules
            .getOffset(Instant.ofEpochMilli(view.start)).totalSeconds * 1_000L
        timeTicks(view.start, view.end, target = 4, offsetMillis = offset)
    }

    /** Touches the reading nearest to horizontal position [x]; a touch outside the plot clears it. */
    fun touch(x: Float, left: Float, right: Float, toggle: Boolean) {
        val current = latestPoints
        if (current.isEmpty() || x < left || x > right) {
            selectedTime = null
            return
        }
        val fraction = ((x - left) / (right - left)).coerceIn(0f, 1f)
        val time = latestView.start + (fraction * latestView.span).toLong()
        val reading = current[nearestIndex(current, time)]
        selectedTime = if (toggle && selectedTime == reading.timestamp) null else reading.timestamp
    }

    Column(modifier) {
        ZoomBar(
            canZoom = canZoom,
            isZoomed = zoomed != null,
            onEarlier = { zoomed = panWindow(view, full, -0.5f).takeIf { it.span < full.span } },
            onLater = { zoomed = panWindow(view, full, 0.5f).takeIf { it.span < full.span } },
            onReset = { zoomed = null },
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                // Tap: read one reading.
                .pointerInput(Unit) {
                    detectTapGestures {
                        touch(it.x, PLOT_LEFT.toPx(), size.width - PLOT_RIGHT_MARGIN.toPx(), toggle = true)
                    }
                }
                // One-finger horizontal drag: slide along the chart reading values. Vertical drags
                // are not taken, so the page still scrolls.
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            touch(it.x, PLOT_LEFT.toPx(), size.width - PLOT_RIGHT_MARGIN.toPx(), toggle = false)
                        },
                        onHorizontalDrag = { change, _ ->
                            touch(
                                change.position.x,
                                PLOT_LEFT.toPx(),
                                size.width - PLOT_RIGHT_MARGIN.toPx(),
                                toggle = false,
                            )
                            change.consume()
                        },
                    )
                }
                // Two fingers: pinch to zoom and drag to pan.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val fingers = event.changes.count { it.pressed }
                            val minSpan = minZoomSpan(latestPoints)
                            if (fingers >= 2 && minSpan != Long.MAX_VALUE) {
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                if (zoom != 1f || pan != Offset.Zero) {
                                    val left = PLOT_LEFT.toPx()
                                    val plotWidth = (size.width - PLOT_RIGHT_MARGIN.toPx() - left).coerceAtLeast(1f)
                                    val anchor = ((event.calculateCentroid().x - left) / plotWidth).coerceIn(0f, 1f)
                                    val currentFull = latestFull
                                    val zoomedIn = zoomWindow(latestView, currentFull, zoom, anchor, minSpan)
                                    // Dragging the chart to the right shows earlier moments.
                                    val next = panWindow(zoomedIn, currentFull, -pan.x / plotWidth)
                                    zoomed = next.takeIf { it.span < currentFull.span }
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                },
        ) {
            val left = PLOT_LEFT.toPx()
            val top = 8.dp.toPx()
            val bottom = size.height - 24.dp.toPx()
            val right = size.width - PLOT_RIGHT_MARGIN.toPx()
            val plotWidth = right - left
            val plotHeight = bottom - top

            fun x(timestamp: Long) = left + plotWidth * fractionOf(timestamp, view.start, view.end)
            fun y(value: Float) = bottom - plotHeight * axis.toFraction(value)

            // Bands first, so everything else sits on top of them.
            bands.forEach { band ->
                val upper = y(band.to)
                val lower = y(band.from)
                if (lower > upper) {
                    drawRect(band.color, Offset(left, upper), Size(plotWidth, lower - upper))
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

            // The line, kept inside the plot when zoomed in. Where the sensors were out of reach the line
            // stops, and the gap is shaded so it cannot be mistaken for a flat stretch.
            clipRect(left, top, right, bottom) {
                stretches.zipWithNext().forEach { (before, after) ->
                    val from = x(before.last().timestamp)
                    val to = x(after.first().timestamp)
                    drawRect(gapColor, Offset(from, top), Size(to - from, bottom - top))
                }
                // Few points: mark each one, so a short stretch does not look like a smooth curve.
                val dotted = stretches.sumOf { it.size } <= 40
                stretches.forEach { stretch ->
                    when {
                        stretch.isEmpty() -> Unit
                        stretch.size == 1 ->
                            drawCircle(lineColor, 4.dp.toPx(), Offset(x(stretch[0].timestamp), y(stretch[0].value)))
                        else -> {
                            val path = Path().apply {
                                moveTo(x(stretch[0].timestamp), y(stretch[0].value))
                                for (i in 1 until stretch.size) lineTo(x(stretch[i].timestamp), y(stretch[i].value))
                            }
                            drawPath(
                                path,
                                lineColor,
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                            )
                            if (dotted) {
                                stretch.forEach {
                                    drawCircle(lineColor, 2.5.dp.toPx(), Offset(x(it.timestamp), y(it.value)))
                                }
                            }
                        }
                    }
                }
            }

            // Band names go on top of the line, each on a small backing so the line never hides one.
            bands.forEach { band ->
                val upper = y(band.to)
                val lower = y(band.from)
                if (band.label != null && lower > upper) {
                    val layout = measurer.measure(band.label, bandStyle)
                    if (layout.size.height <= lower - upper) {
                        val tx = right - 4.dp.toPx() - layout.size.width
                        val ty = if (band.labelAtTop) {
                            upper + 3.dp.toPx()
                        } else {
                            upper + (lower - upper - layout.size.height) / 2f
                        }
                        val padX = 4.dp.toPx()
                        val padY = 1.dp.toPx()
                        drawRoundRect(
                            scheme.surface.copy(alpha = 0.75f),
                            Offset(tx - padX, ty - padY),
                            Size(layout.size.width + 2 * padX, layout.size.height + 2 * padY),
                            CornerRadius(6.dp.toPx()),
                        )
                        drawText(layout, topLeft = Offset(tx, ty))
                    }
                }
            }
            // The touched reading: a dashed line, a dot and a bubble with the exact value.
            val touchedTime = selectedTime
            val touched = touchedTime?.let { time ->
                points.getOrNull(if (points.isEmpty()) -1 else nearestIndex(points, time))
                    ?.takeIf { it.timestamp == time && it.timestamp in view.start..view.end }
            }
            if (touched != null) {
                val tx = x(touched.timestamp)
                val ty = y(touched.value)
                drawLine(
                    scheme.onSurface.copy(alpha = 0.55f),
                    Offset(tx, top),
                    Offset(tx, bottom),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                )
                drawCircle(scheme.surface, 7.dp.toPx(), Offset(tx, ty))
                drawCircle(lineColor, 5.dp.toPx(), Offset(tx, ty))

                val tip = tooltip?.invoke(touched)
                if (tip != null) {
                    val timeLayout = measurer.measure(formatTimeTick(touched.timestamp, 1_000L), tipSmallStyle)
                    val valueLayout = measurer.measure(tip.value, tipValueStyle)
                    val detailLayout = tip.detail?.let { measurer.measure(it, tipSmallStyle) }
                    val padding = 8.dp.toPx()
                    val gap = 2.dp.toPx()
                    val contentWidth = maxOf(
                        timeLayout.size.width,
                        valueLayout.size.width,
                        detailLayout?.size?.width ?: 0,
                    )
                    val boxWidth = contentWidth + 2 * padding
                    val boxHeight = 2 * padding + timeLayout.size.height + gap + valueLayout.size.height +
                        (detailLayout?.let { gap + it.size.height } ?: 0f)
                    val boxX = (tx - boxWidth / 2f).coerceIn(left, (right - boxWidth).coerceAtLeast(left))
                    // On the side of the plot away from the reading, so the bubble does not cover it.
                    val boxY = if (ty < (top + bottom) / 2f) bottom - boxHeight - 6.dp.toPx() else top + 6.dp.toPx()
                    drawRoundRect(
                        scheme.surface.copy(alpha = 0.96f),
                        Offset(boxX, boxY),
                        Size(boxWidth, boxHeight),
                        CornerRadius(8.dp.toPx()),
                    )
                    drawRoundRect(
                        scheme.outline,
                        Offset(boxX, boxY),
                        Size(boxWidth, boxHeight),
                        CornerRadius(8.dp.toPx()),
                        style = Stroke(1.dp.toPx()),
                    )
                    var textY = boxY + padding
                    drawText(timeLayout, topLeft = Offset(boxX + padding, textY))
                    textY += timeLayout.size.height + gap
                    drawText(valueLayout, topLeft = Offset(boxX + padding, textY))
                    if (detailLayout != null) {
                        textY += valueLayout.size.height + gap
                        drawText(detailLayout, topLeft = Offset(boxX + padding, textY))
                    }
                }
            }
        }
    }
}

/** The line above a chart: the zoom hint, or (once zoomed in) arrows to step along and a way back. */
@Composable
private fun ZoomBar(
    canZoom: Boolean,
    isZoomed: Boolean,
    onEarlier: () -> Unit,
    onLater: () -> Unit,
    onReset: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            isZoomed -> {
                IconButton(onClick = onEarlier, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Earlier")
                }
                IconButton(onClick = onLater, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Later")
                }
                TextButton(onClick = onReset) { Text("Reset zoom") }
            }
            canZoom -> Text(
                text = "Pinch to zoom",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 6.dp),
            )
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
fun ScoreChart(
    points: List<TimedValue>,
    timeStart: Long,
    timeEnd: Long,
    modifier: Modifier = Modifier,
    gapMillis: Long = Long.MAX_VALUE,
) {
    val scheme = MaterialTheme.colorScheme
    val fair = ScoreLabel.FAIR_FROM.toFloat()
    val good = ScoreLabel.GOOD_FROM.toFloat()
    LineChart(
        points = points,
        timeStart = timeStart,
        timeEnd = timeEnd,
        axis = scoreAxis(SCORE_LABELS),
        bands = listOf(
            ChartBand(good, 100f, scheme.primary.copy(alpha = 0.16f), "Good", labelAtTop = true),
            ChartBand(fair, good, scheme.tertiary.copy(alpha = 0.16f), "Fair", labelAtTop = true),
            ChartBand(0f, fair, scheme.error.copy(alpha = 0.14f), "Poor", labelAtTop = true),
        ),
        lineColor = scheme.onSurface,
        modifier = modifier,
        gapMillis = gapMillis,
        tooltip = { reading ->
            val score = reading.value.toInt()
            ChartTooltip(value = "Score $score", detail = ScoreLabel.of(score).text)
        },
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
    gapMillis: Long = Long.MAX_VALUE,
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
        // Temperature and humidity get many round labels (and stripes between them, like the light
        // chart); sound keeps the lighter set of labels.
        val ticks = when (sensor) {
            SensorType.TEMPERATURE -> stepTicks(range.low, range.high, TEMPERATURE_STEPS)
            SensorType.HUMIDITY -> stepTicks(range.low, range.high, HUMIDITY_STEPS)
            else -> niceTicks(range.low, range.high, target = 4)
        }
        axis = linearAxis(range, ticks) { formatValue(it) }
        val striped = sensor == SensorType.TEMPERATURE || sensor == SensorType.HUMIDITY
        val stripes = if (striped) {
            stripeEdges(range, ticks).zipWithNext().mapIndexed { index, (from, to) ->
                ChartBand(from, to, scheme.onSurface.copy(alpha = if (index % 2 == 0) 0.05f else 0f))
            }
        } else {
            emptyList()
        }
        bands = stripes + ideal
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LineChart(
            points = points,
            timeStart = timeStart,
            timeEnd = timeEnd,
            axis = axis,
            bands = bands,
            lineColor = color,
            gapMillis = gapMillis,
            tooltip = { reading ->
                val status = when (ScoringEngine.status(setting, reading.value)) {
                    RangeStatus.IN_RANGE -> "In range"
                    RangeStatus.TOO_LOW -> "Too low"
                    RangeStatus.TOO_HIGH -> "Too high"
                }
                // Light also gets its plain word, as everywhere else.
                val detail = if (sensor == SensorType.LIGHT) {
                    "${LightLevel.of(reading.value).label} · $status"
                } else {
                    status
                }
                ChartTooltip(value = sensor.formatReading(reading.value), detail = detail)
            },
        )
        ChartLegend(
            color = color.copy(alpha = 0.45f),
            text = "Ideal range ${sensor.formatRange(setting.idealMin, setting.idealMax)}",
            modifier = Modifier.padding(start = 44.dp),
        )
    }
}
