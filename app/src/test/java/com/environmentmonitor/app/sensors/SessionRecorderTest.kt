package com.environmentmonitor.app.sensors

import com.environmentmonitor.app.data.Measurement
import com.environmentmonitor.app.data.ReadingSink
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionRecorderTest {

    private data class Saved(val sessionId: Long, val measurement: Measurement, val at: Long)

    private class FakeSensors : SensorSource {
        val flow = MutableSharedFlow<Measurement>(replay = 1)
        override val measurements: Flow<Measurement> = flow

        val state = MutableStateFlow(ConnectionState.CONNECTED)
        override val connection: StateFlow<ConnectionState> = state
    }

    private val saved = mutableListOf<Saved>()
    private val sink = ReadingSink { id, m, at -> saved += Saved(id, m, at) }

    private fun reading(temperature: Float) = Measurement(temperature, 50f, 100f, 20f)

    private fun TestScope.recorder(
        sensors: SensorSource,
        active: Flow<Long?>,
        intervalMillis: Long = 10_000,
    ) = recorderFor(sensors, active.map { id -> id?.let { RecordingTarget(it, intervalMillis) } })

    private fun TestScope.recorderFor(
        sensors: SensorSource,
        target: Flow<RecordingTarget?>,
    ) = SessionRecorder(
        scope = backgroundScope,
        sensors = sensors,
        activeTarget = target,
        sink = sink,
        clock = { currentTime },
    ).also { it.start() }

    @Test
    fun `nothing is saved while no session is running`() = runTest {
        val sensors = FakeSensors().also { it.flow.tryEmit(reading(20f)) }
        recorder(sensors, MutableStateFlow(null))
        advanceTimeBy(120_000)
        assertTrue(saved.isEmpty())
    }

    @Test
    fun `a reading is saved at once and then every interval`() = runTest {
        val sensors = FakeSensors().also { it.flow.tryEmit(reading(20f)) }
        val active = MutableStateFlow<Long?>(null)
        recorder(sensors, active)
        runCurrent()

        active.value = 1L
        runCurrent()
        assertEquals(listOf(0L), saved.map { it.at })

        advanceTimeBy(25_000)
        assertEquals(listOf(0L, 10_000L, 20_000L), saved.map { it.at })
        assertTrue(saved.all { it.sessionId == 1L })
    }

    @Test
    fun `each saved reading holds the latest measurement`() = runTest {
        val sensors = FakeSensors().also { it.flow.tryEmit(reading(20f)) }
        val active = MutableStateFlow<Long?>(1L)
        recorder(sensors, active)
        runCurrent()

        sensors.flow.tryEmit(reading(21f))
        advanceTimeBy(10_001)
        assertEquals(listOf(20f, 21f), saved.map { it.measurement.temperature })
    }

    @Test
    fun `saving stops when the session ends`() = runTest {
        val sensors = FakeSensors().also { it.flow.tryEmit(reading(20f)) }
        val active = MutableStateFlow<Long?>(1L)
        recorder(sensors, active)
        advanceTimeBy(15_000)
        val before = saved.size

        active.value = null
        advanceTimeBy(120_000)
        assertEquals(before, saved.size)
    }

    @Test
    fun `a new session gets its own readings`() = runTest {
        val sensors = FakeSensors().also { it.flow.tryEmit(reading(20f)) }
        val active = MutableStateFlow<Long?>(1L)
        recorder(sensors, active)
        advanceTimeBy(5_000)

        active.value = null
        advanceTimeBy(5_000)
        active.value = 2L
        runCurrent()

        assertEquals(listOf(1L, 2L), saved.map { it.sessionId })
        // The second session starts recording immediately, not at the next interval.
        assertEquals(10_000L, saved.last().at)
    }

    @Test
    fun `recording waits for the first measurement when the sensors are silent`() = runTest {
        val sensors = FakeSensors()
        val active = MutableStateFlow<Long?>(1L)
        recorder(sensors, active)
        advanceTimeBy(30_000)
        assertTrue(saved.isEmpty())

        sensors.flow.tryEmit(reading(20f))
        runCurrent()
        assertEquals(1, saved.size)
        assertEquals(30_000L, saved.single().at)
    }

    @Test
    fun `a short interval records more often`() = runTest {
        val sensors = FakeSensors().also { it.flow.tryEmit(reading(20f)) }
        recorder(sensors, MutableStateFlow(1L), intervalMillis = 1_000)
        advanceTimeBy(3_500)
        assertEquals(listOf(0L, 1_000L, 2_000L, 3_000L), saved.map { it.at })
    }

    @Test
    fun `a long interval records rarely`() = runTest {
        val sensors = FakeSensors().also { it.flow.tryEmit(reading(20f)) }
        recorder(sensors, MutableStateFlow(1L), intervalMillis = 300_000)
        advanceTimeBy(650_000)
        assertEquals(listOf(0L, 300_000L, 600_000L), saved.map { it.at })
    }

    @Test
    fun `each session records at its own interval`() = runTest {
        val sensors = FakeSensors().also { it.flow.tryEmit(reading(20f)) }
        val target = MutableStateFlow<RecordingTarget?>(RecordingTarget(1L, 10_000))
        recorderFor(sensors, target)
        advanceTimeBy(15_000)
        assertEquals(listOf(0L, 10_000L), saved.map { it.at })

        target.value = null
        advanceTimeBy(5_000)
        target.value = RecordingTarget(2L, 60_000)
        runCurrent()
        advanceTimeBy(130_000)

        val second = saved.filter { it.sessionId == 2L }.map { it.at }
        assertEquals(listOf(20_000L, 80_000L, 140_000L), second)
    }

    @Test
    fun `starting twice does not record twice`() = runTest {
        val sensors = FakeSensors().also { it.flow.tryEmit(reading(20f)) }
        val recorder = recorder(sensors, MutableStateFlow(1L))
        recorder.start()
        runCurrent()
        assertEquals(1, saved.size)
    }

    @Test
    fun `nothing is saved while the signal is lost and recording resumes at once when it returns`() = runTest {
        val sensors = FakeSensors().also { it.flow.tryEmit(reading(20f)) }
        recorder(sensors, MutableStateFlow(1L))
        advanceTimeBy(15_000)
        assertEquals(listOf(0L, 10_000L), saved.map { it.at })

        sensors.state.value = ConnectionState.NO_SIGNAL
        advanceTimeBy(40_000)
        // The old value is still replayed by the source, but it must not be saved again.
        assertEquals(listOf(0L, 10_000L), saved.map { it.at })

        sensors.flow.tryEmit(reading(22f))
        sensors.state.value = ConnectionState.CONNECTED
        runCurrent()
        assertEquals(listOf(0L, 10_000L, 55_000L), saved.map { it.at })
        assertEquals(22f, saved.last().measurement.temperature)

        // The normal rhythm carries on from the first reading after the gap.
        advanceTimeBy(10_001)
        assertEquals(65_000L, saved.last().at)
    }

    @Test
    fun `nothing is saved while the sensors are still connecting`() = runTest {
        val sensors = FakeSensors().also { it.flow.tryEmit(reading(20f)) }
        sensors.state.value = ConnectionState.CONNECTING
        recorder(sensors, MutableStateFlow(1L))
        advanceTimeBy(30_000)
        assertTrue(saved.isEmpty())

        sensors.state.value = ConnectionState.CONNECTED
        runCurrent()
        assertEquals(listOf(30_000L), saved.map { it.at })
    }

    @Test
    fun `a session that starts while there is no signal waits for it`() = runTest {
        val sensors = FakeSensors().also { it.flow.tryEmit(reading(20f)) }
        sensors.state.value = ConnectionState.NO_SIGNAL
        val active = MutableStateFlow<Long?>(null)
        recorder(sensors, active)
        active.value = 1L
        advanceTimeBy(20_000)
        assertTrue(saved.isEmpty())

        sensors.state.value = ConnectionState.CONNECTED
        runCurrent()
        assertEquals(listOf(20_000L), saved.map { it.at })
    }

    @Test
    fun `a measurement with a missing sensor is saved with that value empty`() = runTest {
        val sensors = FakeSensors().also {
            it.flow.tryEmit(Measurement(temperature = 20f, humidity = null, light = 100f, sound = 20f))
        }
        recorder(sensors, MutableStateFlow(1L))
        runCurrent()
        assertEquals(1, saved.size)
        assertEquals(null, saved.single().measurement.humidity)
        assertEquals(20f, saved.single().measurement.temperature)
    }

    @Test
    fun `a measurement where every sensor is empty is not saved`() = runTest {
        val sensors = FakeSensors().also { it.flow.tryEmit(Measurement(null, null, null, null)) }
        recorder(sensors, MutableStateFlow(1L))
        advanceTimeBy(35_000)
        assertTrue(saved.isEmpty())

        sensors.flow.tryEmit(reading(20f))
        advanceTimeBy(10_000)
        assertEquals(1, saved.size)
    }
}
