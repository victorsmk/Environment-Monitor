package com.environmentmonitor.app.sensors

import com.environmentmonitor.app.data.Measurement
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PicoSensorSourceTest {

    /** A Pico whose answers the test decides. Each poll takes the next queued answer, or the default. */
    private class FakeTransport : PicoTransport {
        val requested = mutableListOf<String>()
        var answer: () -> String = { good() }

        override suspend fun get(url: String): String {
            requested += url
            return answer()
        }
    }

    private val transport = FakeTransport()
    private val address = MutableStateFlow("192.168.4.1")

    private fun TestScope.source(failuresBeforeNoSignal: Int = 3) =
        PicoSensorSource(backgroundScope, address, transport, failuresBeforeNoSignal = failuresBeforeNoSignal)

    private fun TestScope.listen(source: PicoSensorSource): MutableList<Measurement> {
        val seen = mutableListOf<Measurement>()
        backgroundScope.launch { source.measurements.collect { seen += it } }
        return seen
    }

    private companion object {
        fun good(temperature: String = "22.1", light: String = "300") =
            """{"v":1,"uptime_ms":1000,"temperature":$temperature,"humidity":48,"light":$light,"sound":17}"""
    }

    @Test
    fun `it starts out connecting and is connected after the first good answer`() = runTest {
        val source = source()
        assertEquals(ConnectionState.CONNECTING, source.connection.value)

        val seen = listen(source)
        runCurrent()
        assertEquals(ConnectionState.CONNECTED, source.connection.value)
        assertEquals(1, seen.size)
        assertEquals(22.1f, seen.single().temperature!!, 0.001f)
    }

    @Test
    fun `it asks again every second`() = runTest {
        val source = source()
        listen(source)
        advanceTimeBy(3_500)
        assertEquals(4, transport.requested.size)
    }

    @Test
    fun `it asks the address it is given`() = runTest {
        address.value = "192.168.43.17:8080"
        val source = source()
        listen(source)
        runCurrent()
        assertEquals("http://192.168.43.17:8080/data", transport.requested.single())
    }

    @Test
    fun `a changed address is used at the next poll`() = runTest {
        val source = source()
        listen(source)
        runCurrent()
        address.value = "10.0.0.5"
        advanceTimeBy(1_100)
        assertEquals(listOf("http://192.168.4.1/data", "http://10.0.0.5/data"), transport.requested)
    }

    @Test
    fun `a sensor reported as null comes through as no value`() = runTest {
        transport.answer = { good(light = "null") }
        val source = source()
        val seen = listen(source)
        runCurrent()
        assertNull(seen.single().light)
        assertEquals(22.1f, seen.single().temperature!!, 0.001f)
        assertEquals(ConnectionState.CONNECTED, source.connection.value)
    }

    @Test
    fun `one failed poll is not yet a loss of signal`() = runTest {
        val source = source()
        listen(source)
        runCurrent()
        transport.answer = { throw IOException("timeout") }
        advanceTimeBy(1_001) // one failed poll
        assertEquals(ConnectionState.CONNECTED, source.connection.value)
        advanceTimeBy(1_000) // two failed polls
        assertEquals(ConnectionState.CONNECTED, source.connection.value)
    }

    @Test
    fun `three failed polls in a row mean no signal`() = runTest {
        val source = source()
        listen(source)
        runCurrent()
        transport.answer = { throw IOException("timeout") }
        advanceTimeBy(3_100)
        assertEquals(ConnectionState.NO_SIGNAL, source.connection.value)
    }

    @Test
    fun `a good answer in between resets the count`() = runTest {
        val source = source()
        listen(source)
        runCurrent()
        // Two failures, one success, two failures: never three in a row.
        val script = ArrayDeque(listOf(false, false, true, false, false))
        transport.answer = { if (script.removeFirstOrNull() == true) good() else throw IOException("timeout") }
        advanceTimeBy(5_100)
        assertEquals(ConnectionState.CONNECTED, source.connection.value)
    }

    @Test
    fun `it recovers by itself when the Pico answers again`() = runTest {
        val source = source()
        val seen = listen(source)
        runCurrent()
        transport.answer = { throw IOException("timeout") }
        advanceTimeBy(5_000)
        assertEquals(ConnectionState.NO_SIGNAL, source.connection.value)
        val before = seen.size

        transport.answer = { good(temperature = "19.5") }
        advanceTimeBy(1_000) // runs exactly one more poll
        assertEquals(ConnectionState.CONNECTED, source.connection.value)
        assertEquals(before + 1, seen.size)
        assertEquals(19.5f, seen.last().temperature!!, 0.001f)
    }

    @Test
    fun `a Pico that never answers goes from connecting to no signal`() = runTest {
        transport.answer = { throw IOException("no route to host") }
        val source = source()
        listen(source)
        runCurrent()
        assertEquals(ConnectionState.CONNECTING, source.connection.value)
        advanceTimeBy(2_100)
        assertEquals(ConnectionState.NO_SIGNAL, source.connection.value)
    }

    @Test
    fun `bad JSON an unknown version and missing fields count as failures`() = runTest {
        val source = source(failuresBeforeNoSignal = 1)
        listen(source)
        runCurrent()
        assertEquals(ConnectionState.CONNECTED, source.connection.value)

        for (bad in listOf(
            "<html>login</html>",
            """{"v":2,"uptime_ms":1,"temperature":1,"humidity":1,"light":1,"sound":1}""",
            """{"v":1,"uptime_ms":1}""",
        )) {
            transport.answer = { good() }
            advanceTimeBy(1_001) // back to good
            assertEquals(ConnectionState.CONNECTED, source.connection.value)

            transport.answer = { bad }
            advanceTimeBy(1_000)
            assertEquals(bad, ConnectionState.NO_SIGNAL, source.connection.value)
        }
    }

    @Test
    fun `the reason for the last failure is kept and cleared on success`() = runTest {
        val source = source()
        listen(source)
        runCurrent()
        assertNull(source.lastError.value)

        transport.answer = { throw IOException("timeout") }
        advanceTimeBy(1_001)
        assertEquals("timeout", source.lastError.value)

        transport.answer = { "not json" }
        advanceTimeBy(1_000)
        assertTrue(source.lastError.value!!.contains("JSON"))

        transport.answer = { good() }
        advanceTimeBy(1_000)
        assertNull(source.lastError.value)
    }

    @Test
    fun `it does not poll while nobody listens`() = runTest {
        val source = source()
        advanceTimeBy(10_000)
        assertTrue(transport.requested.isEmpty())
        assertEquals(ConnectionState.CONNECTING, source.connection.value)
    }

    @Test
    fun `addresses typed by a person become the right web address`() {
        assertEquals("http://192.168.4.1/data", PicoSensorSource.urlFor("192.168.4.1"))
        assertEquals("http://192.168.4.1/data", PicoSensorSource.urlFor("  192.168.4.1  "))
        assertEquals("http://192.168.4.1/data", PicoSensorSource.urlFor("192.168.4.1/"))
        assertEquals("http://192.168.4.1/data", PicoSensorSource.urlFor("http://192.168.4.1"))
        assertEquals("http://pico.local:8080/data", PicoSensorSource.urlFor("pico.local:8080"))
    }
}
