package com.environmentmonitor.app.sensors

import com.environmentmonitor.app.data.Measurement
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SwitchableSensorSourceTest {

    private class FakeSource(state: ConnectionState = ConnectionState.CONNECTED) : SensorSource {
        val flow = MutableSharedFlow<Measurement>(replay = 1)
        val state = MutableStateFlow(state)
        override val measurements: Flow<Measurement> = flow
        override val connection: StateFlow<ConnectionState> = this.state
    }

    private val demo = FakeSource()
    private val pico = FakeSource(ConnectionState.CONNECTING)
    private val kind = MutableStateFlow(SensorSourceKind.DEMO)

    private fun m(temperature: Float) = Measurement(temperature, 50f, 100f, 20f)

    private fun TestScope.switchable() = SwitchableSensorSource(
        scope = backgroundScope,
        kind = kind,
        sources = mapOf(SensorSourceKind.DEMO to demo, SensorSourceKind.PICO to pico),
    )

    private fun TestScope.listen(source: SensorSource): MutableList<Measurement> {
        val seen = mutableListOf<Measurement>()
        backgroundScope.launch { source.measurements.collect { seen += it } }
        return seen
    }

    @Test
    fun `it passes on the chosen source`() = runTest {
        val source = switchable()
        val seen = listen(source)
        runCurrent()
        demo.flow.tryEmit(m(20f))
        pico.flow.tryEmit(m(99f))
        runCurrent()
        assertEquals(listOf(20f), seen.map { it.temperature })
    }

    @Test
    fun `it follows a change of the choice while running`() = runTest {
        val source = switchable()
        val seen = listen(source)
        runCurrent()
        demo.flow.tryEmit(m(20f))
        runCurrent()

        kind.value = SensorSourceKind.PICO
        runCurrent()
        pico.flow.tryEmit(m(30f))
        demo.flow.tryEmit(m(21f)) // no longer listened to
        runCurrent()
        assertEquals(listOf(20f, 30f), seen.map { it.temperature })

        kind.value = SensorSourceKind.DEMO
        runCurrent()
        demo.flow.tryEmit(m(22f))
        runCurrent()
        assertEquals(22f, seen.last().temperature)
    }

    @Test
    fun `the connection state is the chosen sources`() = runTest {
        val source = switchable()
        runCurrent()
        assertEquals(ConnectionState.CONNECTED, source.connection.value)

        kind.value = SensorSourceKind.PICO
        runCurrent()
        assertEquals(ConnectionState.CONNECTING, source.connection.value)

        pico.state.value = ConnectionState.NO_SIGNAL
        runCurrent()
        assertEquals(ConnectionState.NO_SIGNAL, source.connection.value)

        pico.state.value = ConnectionState.CONNECTED
        runCurrent()
        assertEquals(ConnectionState.CONNECTED, source.connection.value)
    }

    @Test
    fun `it starts on the source that is chosen when it is created`() = runTest {
        kind.value = SensorSourceKind.PICO
        val source = switchable()
        assertEquals(ConnectionState.CONNECTING, source.connection.value)
    }

    @Test
    fun `a listener that joins after a switch does not get the old sources last value`() = runTest {
        val source = switchable()
        val early = listen(source)
        runCurrent()
        demo.flow.tryEmit(m(20f))
        runCurrent()
        assertEquals(1, early.size)

        kind.value = SensorSourceKind.PICO
        runCurrent()
        // The Pico has not answered yet. A new listener must not be handed the demo value.
        val late = listen(source)
        advanceTimeBy(10)
        runCurrent()
        assertTrue(late.isEmpty())

        pico.flow.tryEmit(m(30f))
        runCurrent()
        assertEquals(listOf(30f), late.map { it.temperature })
    }
}
