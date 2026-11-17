package com.environmentmonitor.app.sensors

import com.environmentmonitor.app.data.Measurement
import com.environmentmonitor.app.data.SensorType
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn

/** A kind of room the mock sensors pretend to be in. Handy for showing good and bad scores. */
enum class MockScenario(val label: String, val target: Measurement?) {
    COMFORTABLE_BEDROOM("Comfortable bedroom", Measurement(17.5f, 50f, 3f, 8f)),
    QUIET_OFFICE("Quiet office", Measurement(22f, 48f, 400f, 30f)),
    NOISY_ROOM("Noisy room", Measurement(21f, 55f, 250f, 80f)),
    HOT_AND_BRIGHT("Hot and bright", Measurement(28f, 35f, 800f, 40f)),

    /** Slowly moves through the four rooms above, so charts have something to show. */
    CHANGING("Changing", null);

    /** The values to drift towards at [tick] seconds after the model started. */
    fun targetAt(tick: Int): Measurement {
        if (target != null) return target
        val rooms = entries.filter { it.target != null }
        return rooms[(tick / CHANGE_EVERY_TICKS) % rooms.size].target!!
    }

    companion object {
        const val CHANGE_EVERY_TICKS = 45
    }
}

/**
 * Makes believable room readings: each value glides towards the scenario's target and wobbles
 * a little, instead of jumping around randomly. Plain Kotlin, so it is easy to test.
 */
class MockModel(
    private val random: Random = Random.Default,
    start: Measurement = MockScenario.COMFORTABLE_BEDROOM.target!!,
) {
    private var current = start
    private var tick = 0

    fun next(scenario: MockScenario): Measurement {
        tick++
        val target = scenario.targetAt(tick)
        current = Measurement(
            temperature = glide(SensorType.TEMPERATURE, current.temperature, target.temperature),
            humidity = glide(SensorType.HUMIDITY, current.humidity, target.humidity),
            light = glide(SensorType.LIGHT, current.light, target.light),
            sound = glide(SensorType.SOUND, current.sound, target.sound),
        )
        return current
    }

    private fun glide(sensor: SensorType, value: Float, target: Float): Float {
        val wobble = (random.nextFloat() - 0.5f) * 2f * sensor.tolerance * WOBBLE
        return (value + (target - value) * PULL + wobble).coerceIn(sensor.min, sensor.max)
    }

    private companion object {
        /** Share of the remaining distance covered each second. */
        const val PULL = 0.12f

        /** Random wobble per second, as a share of the sensor's tolerance. */
        const val WOBBLE = 0.02f
    }
}

/**
 * Fake sensors for phase 1. One model is shared by everything that listens.
 *
 * [online] is a switch for testing: while it is false the pretend device stops answering, the
 * connection becomes [ConnectionState.NO_SIGNAL] and the pretend room stands still.
 */
class MockSensorSource(
    scope: CoroutineScope,
    scenario: StateFlow<MockScenario>,
    online: StateFlow<Boolean> = MutableStateFlow(true),
    tickMillis: Long = 1_000,
    random: Random = Random.Default,
) : SensorSource {

    private val model = MockModel(random)

    private val _connection = MutableStateFlow(ConnectionState.CONNECTING)
    override val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    override val measurements: Flow<Measurement> = flow {
        while (true) {
            if (online.value) {
                emit(model.next(scenario.value))
                _connection.value = ConnectionState.CONNECTED
            } else {
                _connection.value = ConnectionState.NO_SIGNAL
            }
            delay(tickMillis)
        }
    }.shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), replay = 1)
}
