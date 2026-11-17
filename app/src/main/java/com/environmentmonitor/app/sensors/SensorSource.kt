package com.environmentmonitor.app.sensors

import com.environmentmonitor.app.data.Measurement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Whether the sensor device is answering. */
enum class ConnectionState {
    /** No answer yet, for example right after the app starts. */
    CONNECTING,

    /** Fresh measurements are arriving. */
    CONNECTED,

    /** The device has stopped answering. The app keeps trying and recovers by itself. */
    NO_SIGNAL,
}

/**
 * Where live measurements come from.
 *
 * The app only knows this interface. [MockSensorSource] feeds it fake values now; a source that
 * talks to the Raspberry Pi Pico W replaces it in phase 2 without touching any screen.
 */
interface SensorSource {
    /**
     * A new measurement about once a second. Shared: every collector sees the same values.
     *
     * The last value may be replayed to a new collector even when [connection] is not
     * [ConnectionState.CONNECTED], so a value is only fresh while the connection says so. A source
     * emits a measurement first and only then reports [ConnectionState.CONNECTED].
     */
    val measurements: Flow<Measurement>

    /** Whether [measurements] can be trusted right now. */
    val connection: StateFlow<ConnectionState>
}
