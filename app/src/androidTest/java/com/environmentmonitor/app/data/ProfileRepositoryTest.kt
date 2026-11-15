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

/** Create, read, update and delete against a real (in-memory) Room database. */
@RunWith(AndroidJUnit4::class)
class ProfileRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: ProfileRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        repository = ProfileRepository(database.profileDao())
    }

    @After
    fun tearDown() = database.close()

    private fun custom(name: String) = Presets.all.first().copy(name = name, isPreset = false)

    @Test
    fun seedingInsertsThePresetsOnce() = runBlocking {
        repository.seedPresetsIfNeeded()
        repository.seedPresetsIfNeeded()
        val profiles = repository.profiles.first()
        assertEquals(listOf("Sleep", "Office", "Sport"), profiles.map { it.name })
        assertTrue(profiles.all { it.isPreset })
    }

    @Test
    fun createThenRead() = runBlocking {
        val id = repository.create(custom("Study"))
        val stored = repository.get(id)
        assertNotNull(stored)
        assertEquals("Study", stored!!.name)
        assertFalse(stored.isPreset)
        assertEquals(Presets.all.first().temperature, stored.temperature)
    }

    @Test
    fun updateChangesTheStoredProfile() = runBlocking {
        val id = repository.create(custom("Study"))
        val edited = repository.get(id)!!.copy(
            name = "Deep work",
            light = SensorSetting(400f, 600f, Importance.HIGH),
        )
        repository.update(edited)
        val stored = repository.get(id)!!
        assertEquals("Deep work", stored.name)
        assertEquals(SensorSetting(400f, 600f, Importance.HIGH), stored.light)
    }

    @Test
    fun deleteRemovesACustomProfile() = runBlocking {
        val id = repository.create(custom("Study"))
        assertTrue(repository.delete(id))
        assertNull(repository.get(id))
    }

    @Test
    fun presetsCannotBeDeletedOrUpdated() = runBlocking {
        repository.seedPresetsIfNeeded()
        val preset = repository.profiles.first().first()
        assertFalse(repository.delete(preset.id))
        assertNotNull(repository.get(preset.id))
        val failed = runCatching { repository.update(preset.copy(name = "Hacked")) }
        assertTrue(failed.isFailure)
        assertEquals("Sleep", repository.get(preset.id)!!.name)
    }

    @Test
    fun duplicatingAPresetMakesAnEditableCopy() = runBlocking {
        repository.seedPresetsIfNeeded()
        val preset = repository.profiles.first().first()
        val copyId = repository.duplicate(preset.id)!!
        val copy = repository.get(copyId)!!
        assertEquals("Sleep copy", copy.name)
        assertFalse(copy.isPreset)
        assertEquals(preset.settings(), copy.settings())
    }

    @Test
    fun listShowsPresetsFirstThenCustomProfilesAlphabetically() = runBlocking {
        repository.seedPresetsIfNeeded()
        repository.create(custom("Zen"))
        repository.create(custom("Atelier"))
        val names = repository.profiles.first().map { it.name }
        assertEquals(listOf("Sleep", "Office", "Sport", "Atelier", "Zen"), names)
    }
}
