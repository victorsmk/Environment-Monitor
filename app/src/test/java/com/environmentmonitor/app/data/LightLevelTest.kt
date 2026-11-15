package com.environmentmonitor.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LightLevelTest {

    @Test
    fun `readings fall into the right word`() {
        assertEquals(LightLevel.DARK, LightLevel.of(0f))
        assertEquals(LightLevel.DARK, LightLevel.of(9.9f))
        assertEquals(LightLevel.DIM, LightLevel.of(10f))
        assertEquals(LightLevel.DIM, LightLevel.of(49f))
        assertEquals(LightLevel.SOFT, LightLevel.of(50f))
        assertEquals(LightLevel.SOFT, LightLevel.of(199f))
        assertEquals(LightLevel.BRIGHT, LightLevel.of(200f))
        assertEquals(LightLevel.BRIGHT, LightLevel.of(499f))
        assertEquals(LightLevel.VERY_BRIGHT, LightLevel.of(500f))
        assertEquals(LightLevel.VERY_BRIGHT, LightLevel.of(80_000f))
    }

    @Test
    fun `a negative reading counts as dark`() {
        assertEquals(LightLevel.DARK, LightLevel.of(-3f))
        assertEquals(LightLevel.DARK, LightLevel.ofUpperEnd(-3f))
    }

    @Test
    fun `the top end of a range belongs to the lower level at a boundary`() {
        assertEquals(LightLevel.DARK, LightLevel.ofUpperEnd(10f))
        assertEquals(LightLevel.BRIGHT, LightLevel.ofUpperEnd(500f))
        assertEquals(LightLevel.VERY_BRIGHT, LightLevel.ofUpperEnd(501f))
        assertEquals(LightLevel.VERY_BRIGHT, LightLevel.ofUpperEnd(1000f))
    }

    @Test
    fun `a range inside one level is named once`() {
        assertEquals("Bright", LightLevel.describeRange(300f, 500f))
        assertEquals("Dim", LightLevel.describeRange(20f, 40f))
    }

    @Test
    fun `a range across levels names both ends`() {
        assertEquals("Dim to Bright", LightLevel.describeRange(20f, 400f))
        assertEquals("Bright to Very bright", LightLevel.describeRange(200f, 1000f))
    }

    @Test
    fun `the presets read naturally`() {
        val sleep = Presets.all[0].light
        val office = Presets.all[1].light
        val sport = Presets.all[2].light
        assertEquals("Dark", LightLevel.describeRange(sleep.idealMin, sleep.idealMax))
        assertEquals("Bright", LightLevel.describeRange(office.idealMin, office.idealMax))
        assertEquals("Bright to Very bright", LightLevel.describeRange(sport.idealMin, sport.idealMax))
    }

    @Test
    fun `a flat range still gets a word`() {
        assertEquals("Soft", LightLevel.describeRange(100f, 100f))
    }

    @Test
    fun `levels go from darkest to brightest`() {
        val edges = LightLevel.entries.map { it.fromLux }
        assertEquals(edges.sorted(), edges)
    }
}
