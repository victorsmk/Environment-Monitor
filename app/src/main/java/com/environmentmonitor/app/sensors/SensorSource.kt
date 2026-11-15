package com.environmentmonitor.app.sensors

import com.environmentmonitor.app.data.Measurement
import kotlinx.coroutines.flow.Flow

/**
 * Where live measurements come from.
 *
 * The app only knows this interface. [MockSensorSource] feeds it fake values now; a source that
 * talks to the Raspberry Pi Pico W replaces it in phase 2 without touching any screen.
 */
interface SensorSource {
    /** A new measurement about once a second. Shared: every collector sees the same values. */
    val measurements: Flow<Measurement>
}
