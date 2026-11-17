package com.environmentmonitor.app.sensors

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Pico client on a real Android phone: Android's own HTTP stack, the app's network settings
 * (plain HTTP must be allowed) and the Wi-Fi lookup. The "Pico" is a tiny server inside the test.
 */
@RunWith(AndroidJUnit4::class)
class PicoTransportDeviceTest {

    private lateinit var server: ServerSocket
    private val body = """{"v":1,"uptime_ms":7,"temperature":20.5,"humidity":45,"light":80,"sound":9}"""

    @Before
    fun setUp() {
        server = ServerSocket(0, 5, InetAddress.getByName("127.0.0.1"))
        thread(isDaemon = true) {
            while (!server.isClosed) {
                try {
                    server.accept().use { socket ->
                        // Read the request up to the blank line, then answer.
                        val reader = socket.getInputStream().bufferedReader()
                        while (reader.readLine().let { it != null && it.isNotEmpty() }) { /* skip headers */ }
                        val bytes = body.toByteArray()
                        socket.getOutputStream().apply {
                            write(
                                ("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" +
                                    "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray(),
                            )
                            write(bytes)
                            flush()
                        }
                    }
                } catch (_: Exception) {
                    // the test closed the server
                }
            }
        }
    }

    @After
    fun tearDown() = server.close()

    private val url get() = "http://127.0.0.1:${server.localPort}/data"

    @Test
    fun plainHttpToALocalAddressIsAllowedAndAnswered() = runBlocking {
        val sample = PicoProtocol.parse(HttpPicoTransport().get(url))
        assertEquals(20.5f, sample.measurement.temperature!!, 0.001f)
        assertEquals(45f, sample.measurement.humidity!!, 0.001f)
        assertEquals(7L, sample.uptimeMs)
    }

    @Test
    fun theWifiLookupWorksWithTheAppsPermissions() {
        val opener = WifiUrlOpener(ApplicationProvider.getApplicationContext())
        // Opening a connection does not send anything; it must simply not fail (for example with a
        // SecurityException from a missing permission), whether or not the phone is on Wi-Fi.
        val connection = opener(URL(url))
        assertNotNull(connection)
        assertTrue(connection is HttpURLConnection)
    }

    @Test
    fun theSourceReadsTheServerEndToEnd() = runBlocking<Unit> {
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            val source = PicoSensorSource(
                scope = scope,
                address = MutableStateFlow("127.0.0.1:${server.localPort}"),
                transport = HttpPicoTransport(),
            )
            val measurement = withTimeout(5_000) { source.measurements.first() }
            assertEquals(20.5f, measurement.temperature!!, 0.001f)
            // The state follows the value a moment later, so wait for it.
            withTimeout(5_000) { source.connection.first { it == ConnectionState.CONNECTED } }
        } finally {
            scope.cancel()
        }
    }
}
