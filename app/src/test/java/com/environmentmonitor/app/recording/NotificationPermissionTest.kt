package com.environmentmonitor.app.recording

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPermissionTest {

    @Test
    fun `asked on Android 13 when not granted and never asked`() {
        assertTrue(NotificationPermission.shouldAsk(sdk = 33, granted = false, alreadyAsked = false))
        assertTrue(NotificationPermission.shouldAsk(sdk = 37, granted = false, alreadyAsked = false))
    }

    @Test
    fun `not asked on older Android where no permission exists`() {
        assertFalse(NotificationPermission.shouldAsk(sdk = 32, granted = false, alreadyAsked = false))
    }

    @Test
    fun `not asked when already granted`() {
        assertFalse(NotificationPermission.shouldAsk(sdk = 36, granted = true, alreadyAsked = false))
    }

    @Test
    fun `not asked again after a refusal`() {
        assertFalse(NotificationPermission.shouldAsk(sdk = 36, granted = false, alreadyAsked = true))
    }
}
