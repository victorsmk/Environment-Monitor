package com.environmentmonitor.app.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundRecordingStatusTest {

    @Test
    fun `everything in place is all good`() {
        val status = BackgroundRecordingStatus(notificationsAllowed = true, batteryUnrestricted = true)
        assertTrue(status.allGood)
        assertTrue(status.notificationsText.contains("allowed"))
        assertTrue(status.batteryText.contains("not restricted"))
    }

    @Test
    fun `blocked notifications are explained and do not stop recording`() {
        val status = BackgroundRecordingStatus(notificationsAllowed = false, batteryUnrestricted = true)
        assertFalse(status.allGood)
        assertTrue(status.notificationsText.contains("blocked"))
        assertTrue(status.notificationsText.contains("Recording still works"))
    }

    @Test
    fun `battery optimisation says what to do`() {
        val status = BackgroundRecordingStatus(notificationsAllowed = true, batteryUnrestricted = false)
        assertFalse(status.allGood)
        assertTrue(status.batteryText.contains("Don't optimise"))
    }
}
