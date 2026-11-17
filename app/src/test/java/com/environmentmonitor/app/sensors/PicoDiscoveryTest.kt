package com.environmentmonitor.app.sensors

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import kotlin.concurrent.thread
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PicoDiscoveryProtocolTest {

    private fun parse(text: String, sender: String = "192.168.43.17") =
        PicoDiscoveryProtocol.parseReply(text.toByteArray(), sender)

    @Test
    fun `a good answer gives the senders address`() {
        val found = parse("""{"v":1,"name":"pico-meteo","ip":"1.2.3.4","port":80}""")!!
        assertEquals("192.168.43.17", found.host) // the sender, not the "ip" written in the answer
        assertEquals(80, found.port)
        assertEquals("pico-meteo", found.name)
        assertEquals("192.168.43.17", found.address)
    }

    @Test
    fun `another port is part of the address`() {
        assertEquals("192.168.43.17:8080", parse("""{"v":1,"port":8080}""")!!.address)
    }

    @Test
    fun `port and name are optional`() {
        val found = parse("""{"v":1}""")!!
        assertEquals(80, found.port)
        assertEquals("Pico", found.name)
    }

    @Test
    fun `answers that are not valid are ignored`() {
        assertNull(parse("hello"))
        assertNull(parse("""{"v":2,"port":80}"""))
        assertNull(parse("""{"port":80}"""))
        assertNull(parse("""{"v":1,"port":0}"""))
        assertNull(parse("""{"v":1,"port":70000}"""))
        assertNull(parse("""{"v":1,"port":"eighty"}"""))
        assertNull(parse("[1]"))
    }
}

/** The discovery against a fake Pico that listens on this computer. */
class UdpPicoDiscoveryTest {

    private lateinit var fake: DatagramSocket
    private val loopback = InetAddress.getByName("127.0.0.1")
    private val requests = mutableListOf<String>()

    /** What the fake Pico sends back for the n-th request (1 = first), or nothing for null. */
    private var replyTo: (Int) -> List<String> = { listOf(GOOD) }

    private companion object {
        const val GOOD = """{"v":1,"name":"pico-meteo","ip":"127.0.0.1","port":80}"""
    }

    @Before
    fun setUp() {
        fake = DatagramSocket(0, loopback)
        thread(isDaemon = true) {
            val buffer = ByteArray(512)
            while (!fake.isClosed) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    fake.receive(packet)
                    requests += String(packet.data, 0, packet.length)
                    replyTo(requests.size).forEach { text ->
                        val bytes = text.toByteArray()
                        fake.send(DatagramPacket(bytes, bytes.size, packet.address, packet.port))
                    }
                } catch (_: Exception) {
                    // closed by the test
                }
            }
        }
    }

    @After
    fun tearDown() = fake.close()

    private fun discovery(timeoutMillis: Long = 2_000, resendMillis: Long = 100) = UdpPicoDiscovery(
        port = fake.localPort,
        timeoutMillis = timeoutMillis,
        resendMillis = resendMillis,
        targets = { listOf(loopback) },
    )

    @Test
    fun `it finds a Pico that answers`() = runBlocking {
        val found = discovery().find()
        assertNotNull(found)
        assertEquals("127.0.0.1", found!!.address)
        assertEquals("pico-meteo", found.name)
        assertEquals(listOf(PicoDiscoveryProtocol.REQUEST), requests.take(1))
    }

    @Test
    fun `it gives up when nobody answers`() = runBlocking {
        replyTo = { emptyList() }
        val started = System.currentTimeMillis()
        assertNull(discovery(timeoutMillis = 400).find())
        val took = System.currentTimeMillis() - started
        assertTrue("took $took ms", took in 350..1_500)
    }

    @Test
    fun `it asks again when the first question is lost`() = runBlocking {
        replyTo = { n -> if (n < 3) emptyList() else listOf(GOOD) }
        assertNotNull(discovery().find())
        assertTrue("asked ${requests.size} times", requests.size >= 3)
    }

    @Test
    fun `it ignores answers that make no sense and takes the next good one`() = runBlocking {
        replyTo = { listOf("not json", """{"v":9}""", GOOD) }
        assertEquals("127.0.0.1", discovery().find()!!.address)
    }

    @Test
    fun `it can be cancelled while waiting`() = runBlocking {
        replyTo = { emptyList() }
        val search = async { discovery(timeoutMillis = 30_000).find() }
        delay(300)
        val started = System.currentTimeMillis()
        search.cancel()
        search.join()
        assertTrue("took too long to stop", System.currentTimeMillis() - started < 1_000)
    }

    @Test
    fun `the broadcast addresses always include the general one`() {
        assertTrue(UdpPicoDiscovery.broadcastAddresses().contains(InetAddress.getByName("255.255.255.255")))
    }
}
