package com.environmentmonitor.app.sensors

import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** The real HTTP client against a small real server on this computer, acting as the Pico. */
class HttpPicoTransportTest {

    private lateinit var server: HttpServer
    private var handlerDelayMillis = 0L
    private var status = 200
    private var responseBody = """{"v":1,"uptime_ms":42,"temperature":21.5,"humidity":50,"light":100,"sound":12}"""
    private val paths = mutableListOf<String>()

    private val base get() = "http://127.0.0.1:${server.address.port}"

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/") { exchange ->
            paths += exchange.requestMethod + " " + exchange.requestURI.path
            if (handlerDelayMillis > 0) Thread.sleep(handlerDelayMillis)
            val bytes = responseBody.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, if (status == 200) bytes.size.toLong() else -1L)
            if (status == 200) exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
    }

    @After
    fun tearDown() = server.stop(0)

    private fun transport(timeoutMillis: Int = 2_000) = HttpPicoTransport(timeoutMillis)

    @Test
    fun `a good answer comes back as text`() = runBlocking {
        val text = transport().get(PicoSensorSource.urlFor("127.0.0.1:${server.address.port}"))
        assertEquals(responseBody, text)
        assertEquals(listOf("GET /data"), paths)
    }

    @Test
    fun `the whole path from the server to a measurement works`() = runBlocking {
        val sample = PicoProtocol.parse(transport().get("$base/data"))
        assertEquals(21.5f, sample.measurement.temperature!!, 0.001f)
        assertEquals(42L, sample.uptimeMs)
    }

    @Test
    fun `a status other than 200 is an error`() = runBlocking {
        status = 404
        try {
            transport().get("$base/data")
            fail("expected an error")
        } catch (e: PicoException) {
            assertTrue(e.message, e.message!!.contains("404"))
        }
    }

    @Test
    fun `a server that answers too slowly is given up on`() = runBlocking {
        handlerDelayMillis = 1_500
        val started = System.currentTimeMillis()
        try {
            transport(timeoutMillis = 300).get("$base/data")
            fail("expected a timeout")
        } catch (e: java.net.SocketTimeoutException) {
            // expected
        }
        assertTrue("took too long", System.currentTimeMillis() - started < 1_200)
    }

    @Test
    fun `nothing listening is an error and not a hang`() = runBlocking {
        val closedPort = server.address.port
        server.stop(0)
        try {
            transport(timeoutMillis = 500).get("http://127.0.0.1:$closedPort/data")
            fail("expected an error")
        } catch (e: java.io.IOException) {
            // expected: connection refused
        }
    }

    @Test
    fun `a nonsense address is reported clearly`() = runBlocking {
        try {
            transport().get("not a web address")
            fail("expected an error")
        } catch (e: PicoException) {
            assertTrue(e.message, e.message!!.contains("address"))
        }
    }
}
