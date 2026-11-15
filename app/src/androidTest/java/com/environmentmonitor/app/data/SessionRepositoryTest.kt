package com.environmentmonitor.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Create, read, update and delete for sessions against a real (in-memory) Room database. */
@RunWith(AndroidJUnit4::class)
class SessionRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var profiles: ProfileRepository
    private lateinit var sessions: SessionRepository
    private var now = 1_000_000L

    private val sleep get() = Presets.all.first()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        profiles = ProfileRepository(database.profileDao())
        sessions = SessionRepository(database.sessionDao()) { now }
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun startCreatesARunningSessionWithAProfileSnapshot() = runBlocking {
        val id = sessions.start(sleep, "Tuesday night")!!
        val stored = sessions.get(id)!!
        assertEquals("Tuesday night", stored.name)
        assertEquals(now, stored.startedAt)
        assertNull(stored.endedAt)
        assertEquals("Sleep", stored.profileName)
        assertEquals(sleep.settings(), stored.settings())
    }

    @Test
    fun aBlankNameFallsBackToTheProfileName() = runBlocking {
        val id = sessions.start(sleep, "   ")!!
        assertEquals("Sleep session", sessions.get(id)!!.name)
    }

    @Test
    fun onlyOneSessionCanRunAtATime() = runBlocking {
        val first = sessions.start(sleep, "A")!!
        assertNull(sessions.start(sleep, "B"))
        sessions.stop(first)
        assertNotNull(sessions.start(sleep, "B"))
    }

    @Test
    fun stopRecordsTheEndTimeOnce() = runBlocking {
        val id = sessions.start(sleep, "A")!!
        now += 3_600_000
        assertTrue(sessions.stop(id))
        val ended = sessions.get(id)!!.endedAt
        assertEquals(now, ended)
        now += 1_000
        assertFalse(sessions.stop(id))
        assertEquals(ended, sessions.get(id)!!.endedAt)
    }

    @Test
    fun updateDetailsChangesNameAndNotes() = runBlocking {
        val id = sessions.start(sleep, "A")!!
        assertTrue(sessions.updateDetails(id, "  Better name ", " window was open "))
        val stored = sessions.get(id)!!
        assertEquals("Better name", stored.name)
        assertEquals("window was open", stored.notes)
    }

    @Test
    fun updateDetailsRejectsABlankName() = runBlocking {
        val id = sessions.start(sleep, "A")!!
        assertFalse(sessions.updateDetails(id, "  ", "x"))
        assertEquals("A", sessions.get(id)!!.name)
    }

    @Test
    fun activeSessionFollowsStartAndStop() = runBlocking {
        assertNull(sessions.activeSession.first())
        val id = sessions.start(sleep, "A")!!
        assertEquals(id, sessions.activeSession.first()!!.id)
        sessions.stop(id)
        assertNull(sessions.activeSession.first())
    }

    @Test
    fun deleteRemovesTheSession() = runBlocking {
        val id = sessions.start(sleep, "A")!!
        assertTrue(sessions.delete(id))
        assertNull(sessions.get(id))
        assertFalse(sessions.delete(id))
    }

    @Test
    fun listShowsNewestFirst() = runBlocking {
        val a = sessions.start(sleep, "A")!!
        sessions.stop(a)
        now += 1_000
        val b = sessions.start(sleep, "B")!!
        assertEquals(listOf("B", "A"), sessions.sessions.first().map { it.name })
        assertEquals(b, sessions.sessions.first().first().id)
    }

    @Test
    fun editingOrDeletingAProfileDoesNotChangeOldSessions() = runBlocking {
        profiles.seedPresetsIfNeeded()
        val custom = profiles.get(profiles.create(sleep.copy(name = "Mine", isPreset = false)))!!
        val id = sessions.start(custom, "A")!!
        profiles.update(custom.copy(name = "Renamed", light = SensorSetting(500f, 600f, Importance.LOW)))
        profiles.delete(custom.id)
        val stored = sessions.get(id)!!
        assertEquals("Mine", stored.profileName)
        assertEquals(custom.light, stored.light)
    }
}
