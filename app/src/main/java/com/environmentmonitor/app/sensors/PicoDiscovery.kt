package com.environmentmonitor.app.sensors

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/** Where a Pico was found. */
data class PicoLocation(val host: String, val port: Int, val name: String) {
    /** The address as typed in Settings: "192.168.43.17", or "192.168.43.17:8080" for another port. */
    val address: String get() = if (port == PicoDiscoveryProtocol.DEFAULT_HTTP_PORT) host else "$host:$port"
}

/** Looks for a Pico on the network the phone is on. */
fun interface PicoDiscovery {
    /** The first Pico that answers, or null when none did in time. */
    suspend fun find(): PicoLocation?
}

/** The little UDP conversation described in docs/pico-protocol.md. */
object PicoDiscoveryProtocol {
    /** The UDP port the Pico listens on. */
    const val PORT = 4210

    /** What the phone sends. */
    const val REQUEST = "PICO?"

    const val DEFAULT_HTTP_PORT = 80

    /**
     * Reads a Pico's answer, for example `{"v":1,"name":"pico-meteo","ip":"192.168.43.17","port":80}`.
     * The address used is the one the answer came from ([senderHost]), not the one written in it:
     * it is the one that is known to work from here. Returns null for anything that is not a valid answer.
     */
    fun parseReply(bytes: ByteArray, senderHost: String): PicoLocation? {
        val json = try {
            JSONObject(String(bytes, Charsets.UTF_8))
        } catch (e: JSONException) {
            return null
        }
        val version = json.opt("v")
        if (version !is Number || version.toInt() != PicoProtocol.VERSION) return null
        val port = if (json.has("port")) (json.opt("port") as? Number)?.toInt() ?: return null else DEFAULT_HTTP_PORT
        if (port !in 1..65535) return null
        val name = json.optString("name", "").ifBlank { "Pico" }
        return PicoLocation(senderHost, port, name)
    }
}

/**
 * Finds a Pico by shouting [PicoDiscoveryProtocol.REQUEST] to the whole local network and waiting
 * for an answer. A hotspot hands out a different address each time, so this saves typing it.
 *
 * @param targets where to shout; by default every broadcast address of the phone.
 * @param bind lets the caller steer the socket onto a particular network (Wi-Fi), see [WifiNetworks].
 */
class UdpPicoDiscovery(
    private val port: Int = PicoDiscoveryProtocol.PORT,
    private val timeoutMillis: Long = TIMEOUT_MILLIS,
    private val resendMillis: Long = RESEND_MILLIS,
    private val targets: () -> List<InetAddress> = ::broadcastAddresses,
    private val bind: (DatagramSocket) -> Unit = {},
) : PicoDiscovery {

    override suspend fun find(): PicoLocation? = withContext(Dispatchers.IO) {
        DatagramSocket().use { socket ->
            bind(socket)
            socket.broadcast = true
            val request = PicoDiscoveryProtocol.REQUEST.toByteArray()
            val buffer = ByteArray(512)
            val deadline = System.nanoTime() + timeoutMillis * 1_000_000
            var found: PicoLocation? = null
            // UDP can lose a packet, so the question is repeated until the time is up.
            while (found == null && System.nanoTime() < deadline) {
                targets().forEach { target ->
                    try {
                        socket.send(DatagramPacket(request, request.size, target, port))
                    } catch (_: Exception) {
                        // This address cannot be reached from the chosen network; try the others.
                    }
                }
                val roundEnd = minOf(deadline, System.nanoTime() + resendMillis * 1_000_000)
                while (found == null && System.nanoTime() < roundEnd) {
                    ensureActive()
                    val left = ((roundEnd - System.nanoTime()) / 1_000_000).coerceAtLeast(1)
                    socket.soTimeout = left.toInt()
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        found = PicoDiscoveryProtocol.parseReply(
                            packet.data.copyOf(packet.length),
                            packet.address.hostAddress ?: continue,
                        )
                    } catch (_: SocketTimeoutException) {
                        // nothing yet
                    }
                }
            }
            found
        }
    }

    companion object {
        const val TIMEOUT_MILLIS = 3_000L
        const val RESEND_MILLIS = 700L

        /** The broadcast address of every network the phone is on, plus the general one. */
        fun broadcastAddresses(): List<InetAddress> {
            val perInterface = try {
                NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                    .filter { it.isUp && !it.isLoopback }
                    .flatMap { it.interfaceAddresses }
                    .mapNotNull { it.broadcast }
            } catch (_: Exception) {
                emptyList() // Newer Android versions may refuse to list networks; the general address still works.
            }
            return (perInterface + InetAddress.getByName("255.255.255.255")).distinct()
        }
    }
}
