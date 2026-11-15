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
}
