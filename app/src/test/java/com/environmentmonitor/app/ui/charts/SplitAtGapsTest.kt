package com.environmentmonitor.app.ui.charts

import com.environmentmonitor.app.data.TimedValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitAtGapsTest {

    private fun points(vararg times: Long) = times.map { TimedValue(it, it.toFloat()) }

    private fun times(stretches: List<List<TimedValue>>) = stretches.map { stretch -> stretch.map { it.timestamp } }

    @Test
    fun `readings without a gap stay in one stretch`() {
        val result = splitAtGaps(points(0, 10_000, 20_000, 30_000), maxGapMillis = 30_000)
        assertEquals(listOf(listOf(0L, 10_000L, 20_000L, 30_000L)), times(result))
    }

    @Test
    fun `a gap cuts the line in two`() {
        val result = splitAtGaps(points(0, 10_000, 20_000, 300_000, 310_000), maxGapMillis = 30_000)
        assertEquals(listOf(listOf(0L, 10_000L, 20_000L), listOf(300_000L, 310_000L)), times(result))
    }

    @Test
    fun `several gaps give several stretches`() {
        val result = splitAtGaps(points(0, 10_000, 100_000, 110_000, 500_000), maxGapMillis = 30_000)
        assertEquals(3, result.size)
        assertEquals(listOf(listOf(0L, 10_000L), listOf(100_000L, 110_000L), listOf(500_000L)), times(result))
    }

    @Test
    fun `a distance exactly at the limit does not cut`() {
        assertEquals(1, splitAtGaps(points(0, 30_000), maxGapMillis = 30_000).size)
        assertEquals(2, splitAtGaps(points(0, 30_001), maxGapMillis = 30_000).size)
    }

    @Test
    fun `a lone reading between two gaps is a stretch of its own`() {
        val result = splitAtGaps(points(0, 100_000, 200_000), maxGapMillis = 30_000)
        assertEquals(listOf(listOf(0L), listOf(100_000L), listOf(200_000L)), times(result))
    }

    @Test
    fun `no readings gives no stretches`() {
        assertTrue(splitAtGaps(emptyList(), maxGapMillis = 30_000).isEmpty())
    }

    @Test
    fun `one reading gives one stretch`() {
        assertEquals(listOf(listOf(5L)), times(splitAtGaps(points(5), maxGapMillis = 30_000)))
    }

    @Test
    fun `with no limit nothing is ever cut`() {
        val result = splitAtGaps(points(0, 1_000_000_000), maxGapMillis = Long.MAX_VALUE)
        assertEquals(1, result.size)
    }

    @Test
    fun `values travel with their times`() {
        val result = splitAtGaps(listOf(TimedValue(0, 1f), TimedValue(100_000, 2f)), maxGapMillis = 30_000)
        assertEquals(1f, result[0][0].value, 0f)
        assertEquals(2f, result[1][0].value, 0f)
    }
}
