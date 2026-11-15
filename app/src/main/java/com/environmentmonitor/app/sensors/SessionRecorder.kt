package com.environmentmonitor.app.sensors

import com.environmentmonitor.app.data.ReadingSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Saves a reading every [intervalMillis] while a session is running.
 *
 * It belongs to the app, not to a screen, so recording carries on when the user switches tabs or
 * closes the dashboard. (Keeping it alive when the app is closed is phase 3.)
 */
class SessionRecorder(
    private val scope: CoroutineScope,
    private val sensors: SensorSource,
    /** The id of the running session, or null when nothing is recording. */
    private val activeSessionId: Flow<Long?>,
    private val sink: ReadingSink,
    private val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var job: Job? = null

    /** Starts watching for sessions. Safe to call more than once. */
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            activeSessionId.distinctUntilChanged().collectLatest { sessionId ->
                if (sessionId != null) recordWhileRunning(sessionId)
            }
        }
    }

    /** Runs until the session stops (collectLatest cancels it): a reading now, then one per interval. */
    private suspend fun recordWhileRunning(sessionId: Long) {
        while (true) {
            // Waits for the first value if the sensors have not produced one yet.
            val measurement = sensors.measurements.first()
            sink.record(sessionId, measurement, clock())
            delay(intervalMillis)
        }
    }

    companion object {
        const val DEFAULT_INTERVAL_MILLIS = 10_000L
    }
}
