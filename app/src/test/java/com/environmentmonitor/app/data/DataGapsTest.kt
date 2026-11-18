package com.environmentmonitor.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataGapsTest {

    @Test
    fun `the threshold is three recording intervals`() {
        assertEquals(30_000L, DataGaps.thresholdMillis(10))
        assertEquals(15_000L, DataGaps.thresholdMillis(5))
        assertEquals(180_000L, DataGaps.thresholdMillis(60))
        assertEquals(900_000L, DataGaps.thresholdMillis(300))
    }

    @Test
    fun `but never under ten seconds`() {
        assertEquals(10_000L, DataGaps.thresholdMillis(1))
        assertEquals(10_000L, DataGaps.thresholdMillis(2))
    }

    @Test
    fun `evenly spaced readings have no gaps`() {
        val times = (0..20).map { it * 10_000L }
        assertTrue(DataGaps.find(times, 30_000).isEmpty())
    }

    @Test
    fun `a long silence is a gap from the last reading before it to the first after it`() {
        val times = listOf(0L, 10_000, 20_000, 200_000, 210_000)
        val gaps = DataGaps.find(times, 30_000)
        assertEquals(listOf(DataGap(start = 20_000, end = 200_000)), gaps)
        assertEquals(180_000L, gaps.single().durationMillis)
    }

    @Test
    fun `a distance exactly at the threshold is not a gap`() {
        assertTrue(DataGaps.find(listOf(0L, 30_000), 30_000).isEmpty())
        assertEquals(1, DataGaps.find(listOf(0L, 30_001), 30_000).size)
    }

    @Test
    fun `several gaps are all found in order`() {
        val times = listOf(0L, 10_000, 100_000, 110_000, 500_000)
        val gaps = DataGaps.find(times, 30_000)
        assertEquals(listOf(DataGap(10_000, 100_000), DataGap(110_000, 500_000)), gaps)
    }

    @Test
    fun `no readings or one reading have no gaps`() {
        assertTrue(DataGaps.find(emptyList(), 30_000).isEmpty())
        assertTrue(DataGaps.find(listOf(5L), 30_000).isEmpty())
    }
}
