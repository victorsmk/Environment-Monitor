package com.environmentmonitor.app.sensors

import com.environmentmonitor.app.data.Measurement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn

/**
 * The real sensors: asks the Raspberry Pi Pico W for its readings about once a second over HTTP
 * (see docs/pico-protocol.md).
 *
 * It only polls while something is listening, and it keeps trying after a failure, so it recovers
 * by itself when the Pico comes back.
 *
 * @param address the Pico's address, for example "192.168.4.1" or "192.168.43.17:8080". Read again
 * before every poll, so changing it takes effect at once.
 */
class PicoSensorSource(
    scope: CoroutineScope,
    private val address: StateFlow<String>,
    private val transport: PicoTransport,
    private val pollMillis: Long = POLL_MILLIS,
    private val failuresBeforeNoSignal: Int = FAILURES_BEFORE_NO_SIGNAL,
) : SensorSource {

    private val _connection = MutableStateFlow(ConnectionState.CONNECTING)
    override val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)

    /** Why the last poll failed, or null when it worked. For the "Test connection" button. */
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    override val measurements: Flow<Measurement> = flow {
        var failures = 0
        while (true) {
            val sample = poll()
            if (sample != null) {
                failures = 0
                _lastError.value = null
                // The measurement first, then the state: a listener that sees "connected" can rely
                // on having received the value (see SensorSource).
                emit(sample.measurement)
                _connection.value = ConnectionState.CONNECTED
            } else {
                failures++
                if (failures >= failuresBeforeNoSignal) _connection.value = ConnectionState.NO_SIGNAL
            }
            delay(pollMillis)
        }
    }.shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), replay = 1)

    /** One request. Null when it failed for any reason (the reason goes to [lastError]). */
    private suspend fun poll(): PicoSample? = try {
        PicoProtocol.parse(transport.get(urlFor(address.value)))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        _lastError.value = e.message ?: e.javaClass.simpleName
        null
    }

    companion object {
        const val POLL_MILLIS = 1_000L

        /** Failed polls in a row before the connection counts as lost (about 6 seconds). */
        const val FAILURES_BEFORE_NO_SIGNAL = 3

        /** The address of the readings, for an address typed by a person (with or without "http://"). */
        fun urlFor(address: String): String {
            val trimmed = address.trim().trimEnd('/')
            val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "http://$trimmed"
            return withScheme + PicoProtocol.DATA_PATH
        }
    }
}
