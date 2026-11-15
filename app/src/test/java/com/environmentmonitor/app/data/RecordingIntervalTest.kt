package com.environmentmonitor.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingIntervalTest {

    @Test
    fun `the default is every 10 seconds`() {
        assertEquals(10, RecordingInterval.DEFAULT.seconds)
    }

    @Test
    fun `options go from fastest to slowest`() {
        val seconds = RecordingInterval.entries.map { it.seconds }
        assertEquals(seconds.sorted(), seconds)
        assertEquals(seconds.distinct(), seconds)
        assertTrue(seconds.all { it > 0 })
    }

    @Test
    fun `millis match seconds`() {
        assertEquals(1_000L, RecordingInterval.EVERY_SECOND.millis)
        assertEquals(300_000L, RecordingInterval.EVERY_5_MINUTES.millis)
    }

    @Test
    fun `an interval can be found from its seconds`() {
        assertEquals(RecordingInterval.EVERY_30_SECONDS, RecordingInterval.fromSeconds(30))
        assertNull(RecordingInterval.fromSeconds(7))
    }

    @Test
    fun `intervals are described in plain words`() {
        assertEquals("every second", RecordingInterval.describe(1))
        assertEquals("every 5 seconds", RecordingInterval.describe(5))
        assertEquals("every 10 seconds", RecordingInterval.describe(10))
        assertEquals("every minute", RecordingInterval.describe(60))
        assertEquals("every 5 minutes", RecordingInterval.describe(300))
    }

    @Test
    fun `a value that is not on the list is still described`() {
        assertEquals("every 90 seconds", RecordingInterval.describe(90))
    }

    @Test
    fun `a new session keeps the chosen interval`() {
        val session = Session.startedWith(
            Presets.all.first(), "Night", now = 0, interval = RecordingInterval.EVERY_MINUTE,
        )
        assertEquals(60, session.recordingIntervalSeconds)
        assertEquals(10, Session.startedWith(Presets.all.first(), "Night", now = 0).recordingIntervalSeconds)
    }
}

class EditorSnapshotTest {

    @Test
    fun `a fresh new-profile form has no changes`() {
        val state = com.environmentmonitor.app.ui.profiles.EditorUiState(isLoading = false)
        assertEquals(false, state.hasChanges)
    }

    @Test
    fun `typing a name counts as a change`() {
        val state = com.environmentmonitor.app.ui.profiles.EditorUiState(isLoading = false, name = "Study")
        assertEquals(true, state.hasChanges)
    }

    @Test
    fun `spaces around an unchanged name do not count`() {
        val base = com.environmentmonitor.app.ui.profiles.EditorSnapshot(name = "Study")
        val state = com.environmentmonitor.app.ui.profiles.EditorUiState(
            isLoading = false, isNew = false, name = "  Study ", baseline = base,
        )
        assertEquals(false, state.hasChanges)
    }

    @Test
    fun `a preset is never changed`() {
        val state = com.environmentmonitor.app.ui.profiles.EditorUiState(
            isLoading = false, isNew = false, isPreset = true, name = "Other",
        )
        assertEquals(false, state.hasChanges)
    }
}
