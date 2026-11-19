package com.environmentmonitor.app.recording

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.environmentmonitor.app.EnvironmentMonitorApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * After the phone restarts or the app is updated, a running session has no service any more.
 * This starts it again. If Android refuses (it may, for a service started from the background),
 * the session simply resumes the next time the app is opened, with a gap in between.
 */
class ResumeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!ResumeRule.shouldCheck(intent.action)) return
        val pending = goAsync()
        val container = (context.applicationContext as EnvironmentMonitorApplication).container
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (container.sessionRepository.activeId() != null) {
                    RecordingService.start(context.applicationContext)
                    Log.i(TAG, "Resumed the recording service after ${intent.action}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not resume after ${intent.action}; it resumes when the app is opened", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "ResumeReceiver"
    }
}

/** Which system messages mean "a running session may have lost its service". */
object ResumeRule {
    fun shouldCheck(action: String?): Boolean =
        action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED
}
