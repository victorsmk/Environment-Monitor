package com.environmentmonitor.app.ui

import com.environmentmonitor.app.data.DataGap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GapSummaryTest {

    private val minute = 60_000L

    @Test
    fun `no gap says nothing`() {
        assertNull(emptyList<DataGap>().gapSummary())
    }

    @Test
    fun `one gap says how long it was`() {
        assertEquals("Sensors out of reach once, for 12 min.", listOf(DataGap(0, 12 * minute)).gapSummary())
    }

    @Test
    fun `several gaps say how many and the total`() {
        val gaps = listOf(DataGap(0, 30 * minute), DataGap(100 * minute, 135 * minute))
        assertEquals("Sensors out of reach 2 times, 1 h 05 min in all.", gaps.gapSummary())
    }

    @Test
    fun `a short gap reads as under a minute`() {
        assertEquals("Sensors out of reach once, for under 1 min.", listOf(DataGap(0, 40_000)).gapSummary())
    }

    @Test
    fun `a gap of hours`() {
        assertEquals("Sensors out of reach once, for 5 h 00 min.", listOf(DataGap(0, 300 * minute)).gapSummary())
    }
}
