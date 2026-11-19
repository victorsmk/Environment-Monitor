package com.environmentmonitor.app.recording

import com.environmentmonitor.app.data.EnvironmentScore
import com.environmentmonitor.app.data.Presets
import com.environmentmonitor.app.data.ScoreLabel
import com.environmentmonitor.app.data.Session
import com.environmentmonitor.app.sensors.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingTextTest {

    private val profile = Presets.all.first()
    private val minute = 60_000L
    private val score = EnvironmentScore(overall = 78, label = ScoreLabel.FAIR, sensors = emptyList())

    private fun text(
        session: Session? = Session.startedWith(profile, "Night", now = 0),
        now: Long = 133 * minute,
        score: EnvironmentScore? = this.score,
        connection: ConnectionState = ConnectionState.CONNECTED,
    ) = RecordingText.text(session, now, score, connection)

    @Test
    fun `before the session is known the text is generic`() {
        assertEquals("Recording a session", RecordingText.title(null))
        assertEquals("Starting", text(session = null))
    }

    @Test
    fun `the title is the session name`() {
        assertEquals("Night", RecordingText.title(Session.startedWith(profile, "Night", now = 0)))
    }

    @Test
    fun `a blank name falls back to the profile`() {
        assertEquals(profile.name, RecordingText.title(Session.startedWith(profile, " ", now = 0)))
    }

    @Test
    fun `while connected the text has the time and the score`() {
        assertEquals("${profile.name} · 2 h 13 min · Score 78, Fair", text())
    }

    @Test
    fun `without a score only the time is shown`() {
        assertEquals("${profile.name} · 2 h 13 min", text(score = null))
    }

    @Test
    fun `no signal is stated and the old score is not shown`() {
        assertEquals(
            "${profile.name} · 2 h 13 min · No signal from the sensors",
            text(connection = ConnectionState.NO_SIGNAL),
        )
    }

    @Test
    fun `while connecting only the time is shown`() {
        assertEquals("${profile.name} · 2 h 13 min", text(connection = ConnectionState.CONNECTING))
    }

    @Test
    fun `a brand new session says under 1 min`() {
        assertEquals("${profile.name} · under 1 min · Score 78, Fair", text(now = 5_000))
    }
}
