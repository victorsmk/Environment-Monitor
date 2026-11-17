package com.environmentmonitor.app.sensors

import com.environmentmonitor.app.data.Measurement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn

/**
 * Passes on whichever of [sources] is selected in [kind], and follows the choice when it changes
 * while the app runs. Everything else in the app talks to this one source.
 *
 * When the choice changes, a listener that joins afterwards does not get the old source's last
 * value: that value belongs to a different source.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SwitchableSensorSource(
    scope: CoroutineScope,
    kind: StateFlow<SensorSourceKind>,
    sources: Map<SensorSourceKind, SensorSource>,
) : SensorSource {

    override val measurements: Flow<Measurement> = kind
        .flatMapLatest { chosen ->
            // The null marks the switch, so the replayed value is cleared; it is filtered out below.
            sources.getValue(chosen).measurements.map<Measurement, Measurement?> { it }.onStart { emit(null) }
        }
        .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), replay = 1)
        .filterNotNull()

    override val connection: StateFlow<ConnectionState> = kind
        .flatMapLatest { chosen -> sources.getValue(chosen).connection }
        .stateIn(scope, SharingStarted.Eagerly, sources.getValue(kind.value).connection.value)
}
