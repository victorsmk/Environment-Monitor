package com.environmentmonitor.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileValidatorTest {

    private val valid: Map<SensorType, SensorSetting> = Presets.all.first().settings()

    @Test
    fun `a preset passes validation`() {
        val result = ProfileValidator.validate("My room", valid)
        assertTrue(result.isValid)
    }

    @Test
    fun `blank name is rejected`() {
        val result = ProfileValidator.validate("   ", valid)
        assertEquals("Enter a name", result.nameError)
        assertFalse(result.isValid)
    }

    @Test
    fun `name longer than the limit is rejected`() {
        val tooLong = "x".repeat(ProfileRepository.MAX_NAME_LENGTH + 1)
        assertNotNull(ProfileValidator.validate(tooLong, valid).nameError)
        val exactly = "x".repeat(ProfileRepository.MAX_NAME_LENGTH)
        assertNull(ProfileValidator.validate(exactly, valid).nameError)
    }

    @Test
    fun `at least one sensor must be on`() {
        val allOff = valid.mapValues { it.value.copy(importance = Importance.OFF) }
        val result = ProfileValidator.validate("Quiet", allOff)
        assertEquals("Turn on at least one sensor", result.settingsError)
    }

    @Test
    fun `an active sensor needs a range wider than zero`() {
        val flat = valid + (SensorType.LIGHT to SensorSetting(100f, 100f, Importance.HIGH))
        val result = ProfileValidator.validate("Flat", flat)
        assertEquals("Make the light range wider than zero", result.settingsError)
    }

    @Test
    fun `a sensor that is off may have any range`() {
        val ignored = valid + (SensorType.LIGHT to SensorSetting(100f, 100f, Importance.OFF))
        assertTrue(ProfileValidator.validate("Ignored", ignored).isValid)
    }
}

class CopyNameTest {

    @Test
    fun `first copy is named copy`() {
        assertEquals("Sleep copy", ProfileRepository.uniqueCopyName("Sleep", listOf("Sleep")))
    }

    @Test
    fun `later copies are numbered`() {
        val existing = listOf("Sleep", "Sleep copy", "Sleep copy 2")
        assertEquals("Sleep copy 3", ProfileRepository.uniqueCopyName("Sleep", existing))
    }

    @Test
    fun `comparison ignores case`() {
        assertEquals("Sleep copy 2", ProfileRepository.uniqueCopyName("Sleep", listOf("sleep COPY")))
    }

    @Test
    fun `copy names never exceed the maximum length`() {
        val long = "x".repeat(ProfileRepository.MAX_NAME_LENGTH)
        val name = ProfileRepository.uniqueCopyName(long, listOf(long, long.take(32) + " copy"))
        assertTrue(name.length <= ProfileRepository.MAX_NAME_LENGTH)
    }
}
