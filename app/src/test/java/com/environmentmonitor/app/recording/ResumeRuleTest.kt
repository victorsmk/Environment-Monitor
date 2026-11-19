package com.environmentmonitor.app.recording

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeRuleTest {

    @Test
    fun `a reboot or an app update triggers the check`() {
        assertTrue(ResumeRule.shouldCheck(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(ResumeRule.shouldCheck(Intent.ACTION_MY_PACKAGE_REPLACED))
    }

    @Test
    fun `anything else is ignored`() {
        assertFalse(ResumeRule.shouldCheck(null))
        assertFalse(ResumeRule.shouldCheck("com.example.SOMETHING"))
        assertFalse(ResumeRule.shouldCheck(Intent.ACTION_SCREEN_ON))
    }
}
