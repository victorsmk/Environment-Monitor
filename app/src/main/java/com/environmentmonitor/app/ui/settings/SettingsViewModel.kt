package com.environmentmonitor.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.environmentmonitor.app.AppSettings
import com.environmentmonitor.app.data.RecordingInterval
import com.environmentmonitor.app.data.SensorType
import com.environmentmonitor.app.data.SessionRepository
import com.environmentmonitor.app.sensors.MockScenario
import com.environmentmonitor.app.sensors.PicoDiscovery
import com.environmentmonitor.app.sensors.PicoProtocol
import com.environmentmonitor.app.sensors.PicoSensorSource
import com.environmentmonitor.app.sensors.PicoTransport
import com.environmentmonitor.app.sensors.SensorSourceKind
import com.environmentmonitor.app.ui.theme.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: AppSettings,
    sessions: SessionRepository,
    private val discovery: PicoDiscovery,
    private val transport: PicoTransport,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
    val recordingInterval: StateFlow<RecordingInterval> = settings.recordingInterval
    val mockScenario: StateFlow<MockScenario> = settings.mockScenario
    val sourceKind: StateFlow<SensorSourceKind> = settings.sourceKind
    val picoAddress: StateFlow<String> = settings.picoAddress

    /** While a session records, the source cannot be changed: the session would get readings from two places. */
    val sessionActive: StateFlow<Boolean> = sessions.activeSession
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _picoCheck = MutableStateFlow<PicoCheck>(PicoCheck.Idle)

    /** What "Find Pico" and "Test connection" last did. */
    val picoCheck: StateFlow<PicoCheck> = _picoCheck

    val mockOnline: StateFlow<Boolean> = settings.mockOnline

    fun setMockOnline(online: Boolean) = settings.setMockOnline(online)

    val mockMissingSensors: StateFlow<Set<SensorType>> = settings.mockMissingSensors

    fun setMockSensorFailed(sensor: SensorType, failed: Boolean) =
        settings.setMockSensorFailed(sensor, failed)

    fun selectThemeMode(mode: ThemeMode) = settings.setThemeMode(mode)

    fun selectRecordingInterval(interval: RecordingInterval) = settings.setRecordingInterval(interval)

    fun selectMockScenario(scenario: MockScenario) = settings.setMockScenario(scenario)

    fun selectSourceKind(kind: SensorSourceKind) {
        if (sessionActive.value) return
        settings.setSourceKind(kind)
        _picoCheck.value = PicoCheck.Idle
    }

    fun setPicoAddress(address: String) {
        settings.setPicoAddress(address)
        _picoCheck.value = PicoCheck.Idle
    }

    /** Shouts on the network and saves the address of the Pico that answers. */
    fun findPico() {
        if (_picoCheck.value is PicoCheck.Working) return
        _picoCheck.value = PicoCheck.Working("Looking for the Pico…")
        viewModelScope.launch {
            _picoCheck.value = try {
                val found = discovery.find()
                if (found == null) {
                    PicoCheck.Failure(
                        "No Pico answered. Check that it is switched on and on the same network as " +
                            "the phone, or type its address.",
                    )
                } else {
                    settings.setPicoAddress(found.address)
                    PicoCheck.Success("Found ${found.name} at ${found.address}.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PicoCheck.Failure("The search failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /** Asks the Pico once and says what came back. */
    fun testConnection() {
        if (_picoCheck.value is PicoCheck.Working) return
        val url = PicoSensorSource.urlFor(settings.picoAddress.value)
        _picoCheck.value = PicoCheck.Working("Asking the Pico…")
        viewModelScope.launch {
            _picoCheck.value = try {
                PicoCheck.Success(PicoProtocol.parse(transport.get(url)).summary())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PicoCheck.Failure("No answer from $url: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }
}
