package com.environmentmonitor.app.recording

import com.environmentmonitor.app.data.EnvironmentScore
import com.environmentmonitor.app.data.Session
import com.environmentmonitor.app.sensors.ConnectionState
import com.environmentmonitor.app.ui.formatDuration

/** The words of the recording notification, kept apart from Android so they can be tested. */
object RecordingText {

    fun title(session: Session?): String = when {
        session == null -> "Recording a session"
        session.name.isBlank() -> session.profileName
        else -> session.name
    }

    /**
     * "2 h 13 min · Score 78, Good", or "... · No signal from the sensors". The score is shown only
     * while fresh readings arrive; while connecting there is nothing to add to the time.
     */
    fun text(
        session: Session?,
        now: Long,
        score: EnvironmentScore?,
        connection: ConnectionState,
    ): String {
        if (session == null) return "Starting"
        val parts = mutableListOf("${session.profileName} · ${formatDuration(now - session.startedAt)}")
        when (connection) {
            ConnectionState.NO_SIGNAL -> parts += "No signal from the sensors"
            ConnectionState.CONNECTED -> if (score != null) parts += "Score ${score.overall}, ${score.label.text}"
            ConnectionState.CONNECTING -> Unit
        }
        return parts.joinToString(" · ")
    }
}
