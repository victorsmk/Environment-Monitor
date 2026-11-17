package com.environmentmonitor.app.sensors

import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PicoAddressFinderTest {

    private val kind = MutableStateFlow(SensorSourceKind.PICO)
    private val connection = MutableStateFlow(ConnectionState.CONNECTED)
    private val address = MutableStateFlow("192.168.4.1")
    private var searches = 0
    private var result: () -> PicoLocation? = { null }
    private val saved = mutableListOf<String>()

    private val discovery = PicoDiscovery {
        searches++
        result()
    }

    private fun TestScope.finder() = PicoAddressFinder(
        scope = backgroundScope,
        kind = kind,
        connection = connection,
        address = address,
        discovery = discovery,
        onFound = { saved += it; address.value = it },
        retryMillis = 10_000,
    ).also { it.start() }

    @Test
    fun `it does nothing while the signal is fine`() = runTest {
        finder()
        advanceTimeBy(60_000)
        assertEquals(0, searches)
    }

    @Test
    fun `it searches as soon as the signal is lost`() = runTest {
        finder()
        runCurrent()
        connection.value = ConnectionState.NO_SIGNAL
        runCurrent()
        assertEquals(1, searches)
    }

    @Test
    fun `it saves a new address when the Pico has moved`() = runTest {
        result = { PicoLocation("192.168.43.17", 80, "pico-meteo") }
        finder()
        connection.value = ConnectionState.NO_SIGNAL
        runCurrent()
        assertEquals(listOf("192.168.43.17"), saved)
    }

    @Test
    fun `it does not save the address it already has`() = runTest {
        result = { PicoLocation("192.168.4.1", 80, "pico-meteo") }
        finder()
        connection.value = ConnectionState.NO_SIGNAL
        runCurrent()
        assertTrue(saved.isEmpty())
    }

    @Test
    fun `it keeps searching every ten seconds while the signal stays lost`() = runTest {
        finder()
        connection.value = ConnectionState.NO_SIGNAL
        runCurrent()
        advanceTimeBy(25_000)
        assertEquals(3, searches) // at 0, 10 and 20 seconds
    }

    @Test
    fun `it stops searching when the signal returns`() = runTest {
        finder()
        connection.value = ConnectionState.NO_SIGNAL
        runCurrent()
        connection.value = ConnectionState.CONNECTED
        advanceTimeBy(60_000)
        assertEquals(1, searches)
    }

    @Test
    fun `it does not search for the demo source`() = runTest {
        kind.value = SensorSourceKind.DEMO
        finder()
        connection.value = ConnectionState.NO_SIGNAL
        advanceTimeBy(60_000)
        assertEquals(0, searches)
    }

    @Test
    fun `it starts searching when the Pico is chosen while the signal is already lost`() = runTest {
        kind.value = SensorSourceKind.DEMO
        connection.value = ConnectionState.NO_SIGNAL
        finder()
        runCurrent()
        assertEquals(0, searches)

        kind.value = SensorSourceKind.PICO
        runCurrent()
        assertEquals(1, searches)
    }

    @Test
    fun `a failing search does not stop later ones`() = runTest {
        result = { throw IOException("network unreachable") }
        finder()
        connection.value = ConnectionState.NO_SIGNAL
        runCurrent()
        advanceTimeBy(10_500)
        assertEquals(2, searches)
    }
}
