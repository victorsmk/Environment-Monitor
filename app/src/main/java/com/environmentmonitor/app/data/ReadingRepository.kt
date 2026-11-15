package com.environmentmonitor.app.data

import kotlinx.coroutines.flow.Flow

/** Where the recorder puts measurements. A small interface so the recorder can be tested alone. */
fun interface ReadingSink {
    suspend fun record(sessionId: Long, measurement: Measurement, timestamp: Long)
}

/** Stores and reads the measurements of sessions. */
class ReadingRepository(private val dao: ReadingDao) : ReadingSink {

    override suspend fun record(sessionId: Long, measurement: Measurement, timestamp: Long) {
        dao.insert(Reading.of(sessionId, measurement, timestamp))
    }

    /** All readings of a session, oldest first. Emits again whenever one is added. */
    fun observe(sessionId: Long): Flow<List<Reading>> = dao.observeForSession(sessionId)

    fun observeCount(sessionId: Long): Flow<Int> = dao.observeCount(sessionId)

    suspend fun count(sessionId: Long): Int = dao.count(sessionId)
}
