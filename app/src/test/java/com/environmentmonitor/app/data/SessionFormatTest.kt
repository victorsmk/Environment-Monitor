package com.environmentmonitor.app.data

import com.environmentmonitor.app.ui.formatDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SessionFormatTest {

    private val minute = 60_000L

    @Test
    fun `durations are written in minutes and hours`() {
        assertEquals("under 1 min", formatDuration(0))
        assertEquals("under 1 min", formatDuration(59_999))
        assertEquals("1 min", formatDuration(minute))
        assertEquals("42 min", formatDuration(42 * minute))
        assertEquals("1 h 05 min", formatDuration(65 * minute))
        assertEquals("8 h 00 min", formatDuration(480 * minute))
    }

    @Test
    fun `a negative duration is treated as zero`() {
        assertEquals("under 1 min", formatDuration(-5 * minute))
    }
}

class SessionValidatorTest {

    @Test
    fun `blank names are rejected`() {
        assertEquals("Enter a name", SessionValidator.nameError("  "))
    }

    @Test
    fun `names up to the limit are accepted`() {
        assertNull(SessionValidator.nameError("x".repeat(SessionRepository.MAX_NAME_LENGTH)))
        assertNotNull(SessionValidator.nameError("x".repeat(SessionRepository.MAX_NAME_LENGTH + 1)))
    }
}

class SessionModelTest {

    private val profile = Presets.all.first()

    @Test
    fun `a new session copies the profile settings`() {
        val session = Session.startedWith(profile, "Night", now = 1_000)
        assertEquals(profile.settings(), session.settings())
        assertEquals(profile.name, session.profileName)
        assertEquals(true, session.isActive)
    }

    @Test
    fun `duration of a running session uses the current time`() {
        val session = Session.startedWith(profile, "Night", now = 1_000)
        assertEquals(4_000L, session.durationMillis(now = 5_000))
        assertEquals(2_000L, session.copy(endedAt = 3_000).durationMillis(now = 99_000))
    }
}
