package com.environmentmonitor.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.environmentmonitor.app.data.SensorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorColorsTest {

    // The card colours of the app's light and dark themes (see Theme.kt).
    private val lightCard = Color(0xFFDEE6E0)
    private val darkCard = Color(0xFF2A4F56)

    /** WCAG contrast ratio between two colours. */
    private fun contrast(a: Color, b: Color): Float {
        val hi = maxOf(a.luminance(), b.luminance())
        val lo = minOf(a.luminance(), b.luminance())
        return (hi + 0.05f) / (lo + 0.05f)
    }

    @Test
    fun `every sensor has its own colour`() {
        val light = SensorType.entries.map { it.colors.light }
        val dark = SensorType.entries.map { it.colors.dark }
        assertEquals(light.size, light.toSet().size)
        assertEquals(dark.size, dark.toSet().size)
    }

    @Test
    fun `colours are readable on a light card`() {
        SensorType.entries.forEach {
            val ratio = contrast(it.colors.light, lightCard)
            assertTrue("${it.label} light mode contrast is $ratio", ratio >= 3f)
        }
    }

    @Test
    fun `colours are readable on a dark card`() {
        SensorType.entries.forEach {
            val ratio = contrast(it.colors.dark, darkCard)
            assertTrue("${it.label} dark mode contrast is $ratio", ratio >= 3f)
        }
    }
}
