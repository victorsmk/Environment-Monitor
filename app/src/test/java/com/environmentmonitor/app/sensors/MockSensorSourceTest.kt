package com.environmentmonitor.app.sensors

import com.environmentmonitor.app.data.Measurement
import com.environmentmonitor.app.data.SensorType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MockSensorSourceTest {

    @Test
    fun `it starts out connecting and is connected once values flow`() = runTest {
        val source = MockSensorSource(backgroundScope, MutableStateFlow(MockScenario.QUIET_OFFICE))
        assertEquals(ConnectionState.CONNECTING, source.connection.value)

        var count = 0
        backgroundScope.launch { source.measurements.collect { count++ } }
        runCurrent()
        assertEquals(ConnectionState.CONNECTED, source.connection.value)
        assertEquals(1, count)
    }

    @Test
    fun `losing the signal stops the values and reports no signal`() = runTest {
        val online = MutableStateFlow(true)
        val source = MockSensorSource(backgroundScope, MutableStateFlow(MockScenario.QUIET_OFFICE), online)
        var count = 0
        backgroundScope.launch { source.measurements.collect { count++ } }
        advanceTimeBy(2_500)
        assertEquals(3, count)

        online.value = false
        advanceTimeBy(1_000)
        assertEquals(ConnectionState.NO_SIGNAL, source.connection.value)
        val before = count
        advanceTimeBy(30_000)
        assertEquals(before, count)
    }

    @Test
    fun `it recovers by itself when the signal returns`() = runTest {
        val online = MutableStateFlow(false)
        val source = MockSensorSource(backgroundScope, MutableStateFlow(MockScenario.QUIET_OFFICE), online)
        var count = 0
        backgroundScope.launch { source.measurements.collect { count++ } }
        advanceTimeBy(5_000)
        assertEquals(ConnectionState.NO_SIGNAL, source.connection.value)
        assertEquals(0, count)

        online.value = true
        advanceTimeBy(1_000)
        assertEquals(ConnectionState.CONNECTED, source.connection.value)
        assertEquals(true, count > 0)
    }

    @Test
    fun `a failed sensor reports no value while the others carry on`() = runTest {
        val missing = MutableStateFlow(setOf(SensorType.LIGHT))
        val source = MockSensorSource(
            backgroundScope,
            MutableStateFlow(MockScenario.QUIET_OFFICE),
            missing = missing,
        )
        val seen = mutableListOf<Measurement>()
        backgroundScope.launch { source.measurements.collect { seen += it } }
        advanceTimeBy(2_500)
        assertEquals(true, seen.isNotEmpty())
        assertEquals(true, seen.all { it.light == null && it.temperature != null && it.sound != null })
        assertEquals(ConnectionState.CONNECTED, source.connection.value)

        missing.value = emptySet()
        advanceTimeBy(1_000)
        assertEquals(true, seen.last().light != null)
    }
}
