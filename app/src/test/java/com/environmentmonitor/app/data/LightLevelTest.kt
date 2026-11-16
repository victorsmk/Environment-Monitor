package com.environmentmonitor.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    // --- the level picker ---

    @Test
    fun `each level ends where the next begins`() {
        assertEquals(10f, LightLevel.DARK.upperLux, 0f)
        assertEquals(50f, LightLevel.DIM.upperLux, 0f)
        assertEquals(200f, LightLevel.SOFT.upperLux, 0f)
        assertEquals(500f, LightLevel.BRIGHT.upperLux, 0f)
        assertEquals(SensorType.LIGHT.max, LightLevel.VERY_BRIGHT.upperLux, 0f)
    }

    @Test
    fun `covering lists every level a range touches`() {
        assertEquals(listOf(LightLevel.DARK), LightLevel.covering(0f, 10f))
        assertEquals(listOf(LightLevel.DARK, LightLevel.DIM), LightLevel.covering(0f, 50f))
        assertEquals(
            listOf(LightLevel.SOFT, LightLevel.BRIGHT, LightLevel.VERY_BRIGHT),
            LightLevel.covering(100f, 800f),
        )
        assertEquals(LightLevel.entries.toList(), LightLevel.covering(0f, 1000f))
    }

    @Test
    fun `the range of a stretch of levels runs from the first to the end of the last`() {
        assertEquals(0f to 50f, LightLevel.rangeOf(LightLevel.DARK, LightLevel.DIM))
        assertEquals(200f to 1000f, LightLevel.rangeOf(LightLevel.BRIGHT, LightLevel.VERY_BRIGHT))
        // The order does not matter.
        assertEquals(0f to 50f, LightLevel.rangeOf(LightLevel.DIM, LightLevel.DARK))
        assertEquals(10f to 50f, LightLevel.rangeOf(LightLevel.DIM, LightLevel.DIM))
    }

    @Test
    fun `the first tap of a pair selects just that level and becomes the anchor`() {
        val tap = LightLevel.afterTap(anchor = null, tapped = LightLevel.DARK)
        assertEquals(0f, tap.minLux, 0f)
        assertEquals(10f, tap.maxLux, 0f)
        assertEquals(LightLevel.DARK, tap.anchor)
    }

    @Test
    fun `tapping Dark and then Dim selects 0 to 50 lux`() {
        val first = LightLevel.afterTap(null, LightLevel.DARK)
        val second = LightLevel.afterTap(first.anchor, LightLevel.DIM)
        assertEquals(0f, second.minLux, 0f)
        assertEquals(50f, second.maxLux, 0f)
        assertEquals(null, second.anchor)
    }

    @Test
    fun `the order of the two taps does not matter`() {
        val first = LightLevel.afterTap(null, LightLevel.BRIGHT)
        val second = LightLevel.afterTap(first.anchor, LightLevel.SOFT)
        assertEquals(50f, second.minLux, 0f)
        assertEquals(500f, second.maxLux, 0f)
    }

    @Test
    fun `tapping a far level selects everything in between`() {
        val second = LightLevel.afterTap(LightLevel.DIM, LightLevel.BRIGHT)
        assertEquals(10f, second.minLux, 0f)
        assertEquals(500f, second.maxLux, 0f)
    }

    @Test
    fun `tapping the same level twice keeps that single level`() {
        val second = LightLevel.afterTap(LightLevel.SOFT, LightLevel.SOFT)
        assertEquals(50f, second.minLux, 0f)
        assertEquals(200f, second.maxLux, 0f)
        assertEquals(null, second.anchor)
    }

    @Test
    fun `after a completed pair the next tap starts a new one`() {
        val pair = LightLevel.afterTap(LightLevel.DARK, LightLevel.DIM)
        val next = LightLevel.afterTap(pair.anchor, LightLevel.VERY_BRIGHT)
        assertEquals(500f, next.minLux, 0f)
        assertEquals(1000f, next.maxLux, 0f)
        assertEquals(LightLevel.VERY_BRIGHT, next.anchor)
    }

    @Test
    fun `every boundary is a valid slider step`() {
        val step = SensorType.LIGHT.step
        LightLevel.entries.forEach {
            assertEquals(0f, it.fromLux % step, 0f)
            assertEquals(0f, it.upperLux % step, 0f)
        }
    }

    // --- the chart axis ---

    @Test
    fun `the chart axis gives every level the same height`() {
        assertEquals(0f, LightLevel.position(0f), 0.0001f)
        assertEquals(0.2f, LightLevel.position(10f), 0.0001f)
        assertEquals(0.4f, LightLevel.position(50f), 0.0001f)
        assertEquals(0.6f, LightLevel.position(200f), 0.0001f)
        assertEquals(0.8f, LightLevel.position(500f), 0.0001f)
        assertEquals(1f, LightLevel.position(1000f), 0.0001f)
    }

    @Test
    fun `the chart axis goes up smoothly inside a level`() {
        // Halfway through Dark (0-10) is a tenth of the way up.
        assertEquals(0.1f, LightLevel.position(5f), 0.0001f)
        // Halfway through Soft (50-200) is 125 lux.
        assertEquals(0.5f, LightLevel.position(125f), 0.0001f)
    }

    @Test
    fun `the chart axis never goes backwards or off the scale`() {
        var previous = -1f
        for (lux in 0..1200 step 5) {
            val position = LightLevel.position(lux.toFloat())
            assertTrue("lux=$lux position=$position", position >= previous && position in 0f..1f)
            previous = position
        }
        assertEquals(0f, LightLevel.position(-20f), 0f)
        assertEquals(1f, LightLevel.position(50_000f), 0f)
    }

    @Test
    fun `luxAt is the opposite of position`() {
        for (lux in 0..1000 step 10) {
            val back = LightLevel.luxAt(LightLevel.position(lux.toFloat()))
            assertEquals("lux=$lux", lux.toFloat(), back, 0.01f)
        }
        assertEquals(0f, LightLevel.luxAt(0f), 0f)
        assertEquals(1000f, LightLevel.luxAt(1f), 0f)
        assertEquals(50f, LightLevel.luxAt(0.4f), 0.01f)
    }
}
