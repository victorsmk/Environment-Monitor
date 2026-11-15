package com.environmentmonitor.app.sensors

import com.environmentmonitor.app.data.Measurement
import com.environmentmonitor.app.data.ReadingSink
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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
    }

    private val saved = mutableListOf<Saved>()
    private val sink = ReadingSink { id, m, at -> saved += Saved(id, m, at) }

    private fun reading(temperature: Float) = Measurement(temperature, 50f, 100f, 20f)

    private fun TestScope.recorder(
        sensors: SensorSource,
        active: Flow<Long?>,
    ) = SessionRecorder(
        scope = backgroundScope,
        sensors = sensors,
        activeSessionId = active,
        sink = sink,
        intervalMillis = 10_000,
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
    fun `starting twice does not record twice`() = runTest {
        val sensors = FakeSensors().also { it.flow.tryEmit(reading(20f)) }
        val recorder = recorder(sensors, MutableStateFlow(1L))
        recorder.start()
        runCurrent()
        assertEquals(1, saved.size)
    }
}
