package com.environmentmonitor.app.sensors

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Keeps the Pico's address up to date by itself. While the Pico is selected and its signal is lost,
 * it looks for the Pico on the network every [retryMillis] and saves the address when it has moved
 * (a hotspot hands out a new one each time). Does nothing while the signal is fine.
 */
class PicoAddressFinder(
    private val scope: CoroutineScope,
    private val kind: StateFlow<SensorSourceKind>,
    private val connection: StateFlow<ConnectionState>,
    private val address: StateFlow<String>,
    private val discovery: PicoDiscovery,
    private val onFound: (String) -> Unit,
    private val retryMillis: Long = RETRY_MILLIS,
) {
    /** Starts watching. Call once. */
    fun start() {
        scope.launch {
            combine(kind, connection) { chosen, state ->
                chosen == SensorSourceKind.PICO && state == ConnectionState.NO_SIGNAL
            }.distinctUntilChanged().collectLatest { lost ->
                if (lost) searchUntilCancelled()
            }
        }
    }

    private suspend fun searchUntilCancelled() {
        while (true) {
            try {
                val found = discovery.find()
                if (found != null && found.address != address.value) onFound(found.address)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A failed search is just a search that found nothing; try again later.
            }
            delay(retryMillis)
        }
    }

    companion object {
        const val RETRY_MILLIS = 10_000L
    }
}
