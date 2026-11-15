package com.environmentmonitor.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Readings against a real (in-memory) Room database, including the link to sessions. */
@RunWith(AndroidJUnit4::class)
class ReadingRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var sessions: SessionRepository
    private lateinit var readings: ReadingRepository

    private val sleep get() = Presets.all.first()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        sessions = SessionRepository(database.sessionDao())
        readings = ReadingRepository(database.readingDao())
    }

    @After
    fun tearDown() = database.close()

    private fun measurement(temperature: Float) = Measurement(temperature, 50f, 100f, 20f)

    @Test
    fun recordedReadingsComeBackOldestFirst() = runBlocking {
        val id = sessions.start(sleep, "A")!!
        readings.record(id, measurement(21f), timestamp = 2_000)
        readings.record(id, measurement(20f), timestamp = 1_000)
        readings.record(id, measurement(22f), timestamp = 3_000)

        val stored = readings.observe(id).first()
        assertEquals(listOf(1_000L, 2_000L, 3_000L), stored.map { it.timestamp })
        assertEquals(listOf(20f, 21f, 22f), stored.map { it.temperature })
        assertEquals(measurement(20f), stored.first().toMeasurement())
    }

    @Test
    fun readingsBelongToTheirOwnSession() = runBlocking {
        val first = sessions.start(sleep, "A")!!
        sessions.stop(first)
        val second = sessions.start(sleep, "B")!!
        readings.record(first, measurement(20f), 1_000)
        readings.record(second, measurement(21f), 2_000)
        readings.record(second, measurement(22f), 3_000)

        assertEquals(1, readings.count(first))
        assertEquals(2, readings.count(second))
        assertEquals(2, readings.observeCount(second).first())
    }

    @Test
    fun deletingASessionDeletesItsReadings() = runBlocking {
        val first = sessions.start(sleep, "A")!!
        sessions.stop(first)
        val second = sessions.start(sleep, "B")!!
        readings.record(first, measurement(20f), 1_000)
        readings.record(second, measurement(21f), 2_000)

        assertTrue(sessions.delete(first))

        assertEquals(0, readings.count(first))
        assertEquals(1, readings.count(second))
    }

    @Test
    fun theCountUpdatesWhileASessionRuns() = runBlocking {
        val id = sessions.start(sleep, "A")!!
        assertEquals(0, readings.observeCount(id).first())
        readings.record(id, measurement(20f), 1_000)
        assertEquals(1, readings.observeCount(id).first())
    }
}
