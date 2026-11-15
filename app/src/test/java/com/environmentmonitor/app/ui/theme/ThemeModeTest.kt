package com.environmentmonitor.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {

    @Test
    fun `system follows the phone`() {
        assertTrue(ThemeMode.SYSTEM.isDark(systemIsDark = true))
        assertFalse(ThemeMode.SYSTEM.isDark(systemIsDark = false))
    }

    @Test
    fun `light and dark ignore the phone`() {
        assertFalse(ThemeMode.LIGHT.isDark(systemIsDark = true))
        assertFalse(ThemeMode.LIGHT.isDark(systemIsDark = false))
        assertTrue(ThemeMode.DARK.isDark(systemIsDark = true))
        assertTrue(ThemeMode.DARK.isDark(systemIsDark = false))
    }

    @Test
    fun `the default is to follow the system`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.DEFAULT)
    }

    @Test
    fun `a saved name is turned back into its mode`() {
        ThemeMode.entries.forEach { assertEquals(it, ThemeMode.fromName(it.name)) }
    }

    @Test
    fun `a missing or unknown saved name falls back to the default`() {
        assertEquals(ThemeMode.DEFAULT, ThemeMode.fromName(null))
        assertEquals(ThemeMode.DEFAULT, ThemeMode.fromName(""))
        assertEquals(ThemeMode.DEFAULT, ThemeMode.fromName("SEPIA"))
    }

    @Test
    fun `there are exactly three options`() {
        assertEquals(listOf("SYSTEM", "LIGHT", "DARK"), ThemeMode.entries.map { it.name })
    }
}
