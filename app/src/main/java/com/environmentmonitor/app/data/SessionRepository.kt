package com.environmentmonitor.app.data

import kotlinx.coroutines.flow.Flow

/** Create, read, update and delete for monitoring sessions. */
class SessionRepository(
    private val dao: SessionDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** All sessions, newest first. */
    val sessions: Flow<List<Session>> = dao.observeAll()

    /** The running session, or null when nothing is recording. */
    val activeSession: Flow<Session?> = dao.observeActive()

    fun observe(id: Long): Flow<Session?> = dao.observe(id)

    suspend fun get(id: Long): Session? = dao.getById(id)

    /**
     * Starts a session for [profile] and returns its id, or null when another session is already
     * running (there is only one device, so only one session records at a time).
     * A blank [name] falls back to "<profile> session". The session keeps [interval] for its whole
     * life, whatever the setting is later.
     */
    suspend fun start(
        profile: Profile,
        name: String,
        interval: RecordingInterval = RecordingInterval.DEFAULT,
    ): Long? {
        if (dao.activeCount() > 0) return null
        val finalName = name.trim().ifEmpty { "${profile.name} session" }.take(MAX_NAME_LENGTH)
        return dao.insert(Session.startedWith(profile, finalName, clock(), interval))
    }

    /** Ends a running session. Returns false when it does not exist or already ended. */
    suspend fun stop(id: Long): Boolean = dao.end(id, clock()) > 0

    /** Saves the name and notes. Returns false for an invalid name or an unknown session. */
    suspend fun updateDetails(id: Long, name: String, notes: String): Boolean {
        if (SessionValidator.nameError(name) != null) return false
        return dao.updateDetails(id, name.trim(), notes.trim().take(MAX_NOTES_LENGTH)) > 0
    }

    suspend fun delete(id: Long): Boolean = dao.delete(id) > 0

    companion object {
        const val MAX_NAME_LENGTH = 60
        const val MAX_NOTES_LENGTH = 500
    }
}

object SessionValidator {
    /** An error message for [name], or null when it is fine. */
    fun nameError(name: String): String? = when {
        name.isBlank() -> "Enter a name"
        name.trim().length > SessionRepository.MAX_NAME_LENGTH ->
            "Use at most ${SessionRepository.MAX_NAME_LENGTH} characters"
        else -> null
    }
}
