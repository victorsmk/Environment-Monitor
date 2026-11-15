package com.environmentmonitor.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileIconTest {

    @Test
    fun `a saved key gives back its icon`() {
        ProfileIcon.entries.forEach { assertEquals(it, ProfileIcon.fromKey(it.key)) }
    }

    @Test
    fun `a missing or unknown key gives the default icon`() {
        assertEquals(ProfileIcon.DEFAULT, ProfileIcon.fromKey(null))
        assertEquals(ProfileIcon.DEFAULT, ProfileIcon.fromKey(""))
        assertEquals(ProfileIcon.DEFAULT, ProfileIcon.fromKey("ROCKET"))
    }

    @Test
    fun `every icon has a readable name`() {
        assertTrue(ProfileIcon.entries.all { it.label.isNotBlank() })
        assertEquals(ProfileIcon.entries.size, ProfileIcon.entries.map { it.label }.toSet().size)
    }

    @Test
    fun `every old emoji has a replacement and every icon replaces one`() {
        // The ten emoji the profile editor used to offer, one per icon.
        assertEquals(10, ProfileIcon.LEGACY_EMOJI.size)
        assertEquals(ProfileIcon.entries.toSet(), ProfileIcon.LEGACY_EMOJI.values.toSet())
    }

    @Test
    fun `old emoji are mapped to the matching icon`() {
        assertEquals(ProfileIcon.MOON, ProfileIcon.fromSaved(ProfileIcon.emoji(0x1F634)))
        assertEquals(ProfileIcon.LAPTOP, ProfileIcon.fromSaved(ProfileIcon.emoji(0x1F4BC)))
        assertEquals(ProfileIcon.DUMBBELL, ProfileIcon.fromSaved(ProfileIcon.emoji(0x1F3C3)))
    }

    @Test
    fun `a current key is recognised as it is`() {
        assertEquals(ProfileIcon.BOOK, ProfileIcon.fromSaved("BOOK"))
    }

    @Test
    fun `an unknown saved value is not guessed`() {
        assertNull(ProfileIcon.fromSaved("something else"))
    }

    @Test
    fun `the presets use drawn icons`() {
        assertEquals(
            listOf("MOON", "LAPTOP", "DUMBBELL"),
            Presets.all.map { it.icon },
        )
        assertTrue(Presets.all.all { ProfileIcon.fromSaved(it.icon) != null })
    }

    @Test
    fun `the migration SQL covers every old emoji and every key`() {
        val sql = iconCase("icon")
        ProfileIcon.LEGACY_EMOJI.forEach { (emoji, icon) ->
            assertTrue("missing $emoji", sql.contains("WHEN '$emoji' THEN '${icon.key}'"))
        }
        ProfileIcon.entries.forEach { assertTrue(sql.contains("'${it.key}'")) }
        assertTrue(sql.contains("ELSE '${ProfileIcon.DEFAULT.key}'"))
        assertFalse(sql.contains("\n"))
    }
}
