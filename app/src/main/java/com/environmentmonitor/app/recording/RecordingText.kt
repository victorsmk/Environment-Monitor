package com.environmentmonitor.app.recording

import com.environmentmonitor.app.data.Session

/** The words of the recording notification, kept apart from Android so they can be tested. */
object RecordingText {

    fun title(session: Session?): String = when {
        session == null -> "Recording a session"
        session.name.isBlank() -> session.profileName
        else -> session.name
    }

    fun text(session: Session?): String = when (session) {
        null -> "Starting"
        else -> "${session.profileName} · a reading every ${interval(session.recordingIntervalSeconds)}"
    }

    private fun interval(seconds: Int): String = when {
        seconds < 60 -> "$seconds s"
        seconds % 60 == 0 -> "${seconds / 60} min"
        else -> "$seconds s"
    }
}
