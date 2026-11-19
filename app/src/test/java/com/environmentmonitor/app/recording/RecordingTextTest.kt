package com.environmentmonitor.app.recording

import com.environmentmonitor.app.data.Presets
import com.environmentmonitor.app.data.RecordingInterval
import com.environmentmonitor.app.data.Session
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingTextTest {

    private val profile = Presets.all.first()

    @Test
    fun `before the session is known the text is generic`() {
        assertEquals("Recording a session", RecordingText.title(null))
        assertEquals("Starting", RecordingText.text(null))
    }

    @Test
    fun `the title is the session name`() {
        val session = Session.startedWith(profile, "Night", now = 0)
        assertEquals("Night", RecordingText.title(session))
    }

    @Test
    fun `a blank name falls back to the profile`() {
        val session = Session.startedWith(profile, " ", now = 0)
        assertEquals(profile.name, RecordingText.title(session))
    }

    @Test
    fun `the text names the profile and the interval`() {
        val session = Session.startedWith(profile, "Night", now = 0, interval = RecordingInterval.DEFAULT)
        val expected = "${profile.name} · a reading every ${RecordingInterval.DEFAULT.seconds} s"
        if (RecordingInterval.DEFAULT.seconds < 60) assertEquals(expected, RecordingText.text(session))
    }
}
