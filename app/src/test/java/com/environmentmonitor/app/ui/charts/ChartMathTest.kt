package com.environmentmonitor.app.ui.charts

import com.environmentmonitor.app.data.SensorType
import com.environmentmonitor.app.data.TimedValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartMathTest {

    @Test
    fun `the range covers the data and the whole ideal range with some room`() {
        val range = chartRange(SensorType.TEMPERATURE, listOf(17f, 18f), idealMin = 16f, idealMax = 19f)
        // Span 3, padding 8 %.
        assertEquals(15.76f, range.low, 0.001f)
        assertEquals(19.24f, range.high, 0.001f)
    }

    @Test
    fun `data outside the ideal range stretches the axis`() {
        val range = chartRange(SensorType.TEMPERATURE, listOf(14f, 27f), idealMin = 16f, idealMax = 19f)
        assertTrue(range.low < 14f)
        assertTrue(range.high > 27f)
    }

    @Test
    fun `a nearly flat range is widened so the line is not squashed`() {
        val range = chartRange(SensorType.TEMPERATURE, listOf(17.1f), idealMin = 17f, idealMax = 17.2f)
        assertTrue(range.high - range.low >= SensorType.TEMPERATURE.tolerance / 2f)
    }

    @Test
    fun `the axis never goes below what the sensor can measure`() {
        val range = chartRange(SensorType.LIGHT, listOf(0f, 5f), idealMin = 0f, idealMax = 10f)
        assertEquals(0f, range.low, 0.0001f)
        val top = chartRange(SensorType.HUMIDITY, listOf(99f, 100f), idealMin = 40f, idealMax = 100f)
        assertEquals(100f, top.high, 0.0001f)
    }

    @Test
    fun `an empty series still gives a usable range`() {
        val range = chartRange(SensorType.SOUND, emptyList(), idealMin = 0f, idealMax = 20f)
        assertTrue(range.high > range.low)
    }

    private fun series(count: Int) = List(count) { TimedValue(timestamp = it * 10_000L, value = it.toFloat()) }

    @Test
    fun `short series are left alone`() {
        val points = series(50)
        assertEquals(points, downsample(points, maxPoints = 100))
    }

    @Test
    fun `long series are reduced to about the maximum`() {
        val reduced = downsample(series(1_000), maxPoints = 100)
        assertTrue("got ${reduced.size}", reduced.size in 90..100)
    }

    @Test
    fun `each reduced point is the average of its neighbours`() {
        val reduced = downsample(series(1_000), maxPoints = 100)
        // Buckets of 10: the first averages values 0..9 and times 0..90 000.
        assertEquals(4.5f, reduced.first().value, 0.001f)
        assertEquals(45_000L, reduced.first().timestamp)
    }

    @Test
    fun `reduced points stay in time order`() {
        val reduced = downsample(series(777), maxPoints = 50)
        assertEquals(reduced.map { it.timestamp }.sorted(), reduced.map { it.timestamp })
    }

    @Test
    fun `fractionOf places a time between the start and the end`() {
        assertEquals(0f, fractionOf(100, 100, 200), 0.0001f)
        assertEquals(0.5f, fractionOf(150, 100, 200), 0.0001f)
        assertEquals(1f, fractionOf(200, 100, 200), 0.0001f)
        assertEquals(1f, fractionOf(999, 100, 200), 0.0001f)
        assertEquals(0f, fractionOf(5, 100, 200), 0.0001f)
    }

    @Test
    fun `fractionOf copes with a zero-length span`() {
        assertEquals(0f, fractionOf(100, 100, 100), 0.0001f)
    }

    // --- value labels ---

    @Test
    fun `temperature gets whole-degree labels`() {
        assertEquals(listOf(16f, 17f, 18f, 19f), niceTicks(15.76f, 19.24f, target = 4))
    }

    @Test
    fun `humidity gets labels every 5 percent`() {
        assertEquals(listOf(40f, 45f, 50f, 55f, 60f), niceTicks(38f, 62f, target = 4))
    }

    @Test
    fun `a tall range gets round labels`() {
        assertEquals(listOf(0f, 20f, 40f, 60f, 80f), niceTicks(0f, 87f, target = 4))
        assertEquals(listOf(0f, 100f, 200f, 300f, 400f, 500f), niceTicks(0f, 583f, target = 5))
    }

    @Test
    fun `a narrow range gets decimal labels without rounding noise`() {
        assertEquals(listOf(17.0f, 17.1f, 17.2f, 17.3f, 17.4f), niceTicks(17.0f, 17.4f, target = 4))
    }

    @Test
    fun `labels stay inside the range`() {
        val ticks = niceTicks(15.76f, 19.24f)
        assertTrue(ticks.all { it in 15.76f..19.24f })
    }

    @Test
    fun `a flat or backwards range gives a single label`() {
        assertEquals(listOf(5f), niceTicks(5f, 5f))
        assertEquals(listOf(9f), niceTicks(9f, 3f))
    }

    @Test
    fun `the linear axis maps the range onto 0 to 1`() {
        val axis = linearAxis(ValueRange(10f, 20f), listOf(10f, 15f, 20f)) { it.toInt().toString() }
        assertEquals(listOf("10", "15", "20"), axis.ticks.map { it.label })
        assertEquals(0f, axis.toFraction(10f), 0.0001f)
        assertEquals(0.5f, axis.toFraction(15f), 0.0001f)
        assertEquals(1f, axis.toFraction(20f), 0.0001f)
        assertEquals(1f, axis.toFraction(99f), 0.0001f)
    }

    @Test
    fun `the light axis has a line at every level boundary`() {
        val axis = lightAxis { it.toInt().toString() }
        assertEquals(listOf("0", "10", "50", "200", "500", "1000"), axis.ticks.map { it.label })
        val heights = axis.ticks.map { axis.toFraction(it.value) }
        assertEquals(listOf(0f, 0.2f, 0.4f, 0.6f, 0.8f, 1f), heights)
    }

    @Test
    fun `the score axis marks where each zone begins`() {
        val axis = scoreAxis(poorBelow = 50, goodFrom = 80)
        assertEquals(listOf("0", "50", "80", "100"), axis.ticks.map { it.label })
        assertEquals(0.8f, axis.toFraction(80f), 0.0001f)
    }

    // --- time labels ---

    private val minute = 60_000L
    private val hour = 60 * minute

    @Test
    fun `a short session is labelled in seconds`() {
        val ticks = timeTicks(start = 0, end = 40_000, target = 4)
        assertEquals(10_000L, ticks.stepMillis)
        assertEquals(listOf(0L, 10_000L, 20_000L, 30_000L, 40_000L), ticks.times)
    }

    @Test
    fun `a few minutes are labelled on whole minutes`() {
        // 17:03:20 to 17:08:50, with the clock starting at a whole hour.
        val start = 17 * hour + 3 * minute + 20_000
        val end = 17 * hour + 8 * minute + 50_000
        val ticks = timeTicks(start, end, target = 4)
        assertEquals(2 * minute, ticks.stepMillis)
        assertEquals(listOf(4, 6, 8).map { 17 * hour + it * minute }, ticks.times)
    }

    @Test
    fun `a night is labelled on whole hours`() {
        val start = 22 * hour + 17 * minute
        val end = start + 8 * hour
        val ticks = timeTicks(start, end, target = 4)
        assertEquals(2 * hour, ticks.stepMillis)
        // 22:17 to 06:17 the next day: labels at 00:00, 02:00, 04:00 and 06:00.
        assertEquals(listOf(24, 26, 28, 30).map { it * hour }, ticks.times)
    }

    @Test
    fun `labels follow the local clock in a time zone with a half-hour offset`() {
        val offset = 5 * hour + 30 * minute // like India
        // 02:00 to 06:00 local time is 20:30 to 00:30 UTC.
        val start = 2 * hour - offset
        val end = 6 * hour - offset
        val ticks = timeTicks(start, end, target = 4, offsetMillis = offset)
        // Every label must be a whole local hour: 02:00, 03:00, ... or 02:00, 04:00, 06:00.
        assertTrue(ticks.times.all { (it + offset) % hour == 0L })
        assertEquals(start, ticks.times.first())
    }

    @Test
    fun `there are never many more labels than asked for`() {
        for (span in listOf(7_000L, 45_000L, 3 * minute, 25 * minute, 3 * hour, 11 * hour, 30 * hour)) {
            val ticks = timeTicks(0, span, target = 4)
            assertTrue("span=$span got ${ticks.times.size}", ticks.times.size <= 6)
            assertTrue(ticks.times.all { it in 0..span })
        }
    }

    @Test
    fun `a tiny span still gets labels at both ends`() {
        val ticks = timeTicks(start = 1_100, end = 1_900, target = 4)
        assertEquals(listOf(1_100L, 1_900L), ticks.times)
    }

    @Test
    fun `an empty span gets one label`() {
        assertEquals(listOf(500L), timeTicks(500, 500).times)
    }
}
