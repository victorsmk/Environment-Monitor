package com.environmentmonitor.app.sensors

import com.environmentmonitor.app.data.Measurement
import com.environmentmonitor.app.data.ReadingSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** What the recorder needs to know about the running session. */
data class RecordingTarget(val sessionId: Long, val intervalMillis: Long)

/**
 * Saves a reading at the running session's own interval (chosen in Settings when it started).
 * While the sensors have no signal nothing is saved (the session simply has a gap), and the first
 * reading after the signal returns is saved at once.
 *
 * It belongs to the app, not to a screen, so recording carries on when the user switches tabs or
 * closes the dashboard. (Keeping it alive when the app is closed is phase 3.)
 */
class SessionRecorder(
    private val scope: CoroutineScope,
    private val sensors: SensorSource,
    /** The running session, or null when nothing is recording. */
    private val activeTarget: Flow<RecordingTarget?>,
    private val sink: ReadingSink,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var job: Job? = null

    /** Starts watching for sessions. Safe to call more than once. */
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            activeTarget.distinctUntilChanged().collectLatest { target ->
                if (target != null) recordWhileRunning(target)
            }
        }
    }

    /** Runs until the session stops (collectLatest cancels it): a reading now, then one per interval. */
    private suspend fun recordWhileRunning(target: RecordingTarget) = coroutineScope {
        require(target.intervalMillis > 0) { "The recording interval must be positive" }
        // Listening for the whole session keeps the sensors running between readings.
        val latest = MutableStateFlow<Measurement?>(null)
        launch { sensors.measurements.collect { latest.value = it } }
        while (true) {
            // Waits for a measurement while the sensors are connected: right after the session
            // starts, or after a loss of signal.
            val measurement = combine(sensors.connection, latest) { state, measurement ->
                measurement.takeIf { state == ConnectionState.CONNECTED }
            }.filterNotNull().first()
            sink.record(target.sessionId, measurement, clock())
            delay(target.intervalMillis)
        }
    }
}
