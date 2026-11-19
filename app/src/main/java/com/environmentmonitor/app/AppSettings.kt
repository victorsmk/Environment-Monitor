package com.environmentmonitor.app

import android.content.Context
import com.environmentmonitor.app.data.RecordingInterval
import com.environmentmonitor.app.data.SensorType
import com.environmentmonitor.app.sensors.MockScenario
import com.environmentmonitor.app.sensors.SensorSourceKind
import com.environmentmonitor.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Small choices that survive restarts but do not belong in the database. */
class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _selectedProfileId = MutableStateFlow(
        prefs.getLong(KEY_PROFILE, NONE).takeIf { it != NONE },
    )

    /** The profile the dashboard judges against, or null before the first choice. */
    val selectedProfileId: StateFlow<Long?> = _selectedProfileId

    fun selectProfile(id: Long) {
        prefs.edit().putLong(KEY_PROFILE, id).apply()
        _selectedProfileId.value = id
    }

    private val _sourceKind = MutableStateFlow(SensorSourceKind.fromName(prefs.getString(KEY_SOURCE, null)))

    /** Where the readings come from: made-up demo data or the real Pico W. */
    val sourceKind: StateFlow<SensorSourceKind> = _sourceKind

    fun setSourceKind(kind: SensorSourceKind) {
        prefs.edit().putString(KEY_SOURCE, kind.name).apply()
        _sourceKind.value = kind
    }

    private val _picoAddress = MutableStateFlow(prefs.getString(KEY_PICO_ADDRESS, null) ?: DEFAULT_PICO_ADDRESS)

    /** The Pico's address, for example "192.168.4.1". Saved, so the last one that worked is tried first. */
    val picoAddress: StateFlow<String> = _picoAddress

    fun setPicoAddress(address: String) {
        prefs.edit().putString(KEY_PICO_ADDRESS, address).apply()
        _picoAddress.value = address
    }

    private val _mockScenario = MutableStateFlow(
        runCatching { MockScenario.valueOf(prefs.getString(KEY_SCENARIO, null) ?: "") }
            .getOrDefault(MockScenario.COMFORTABLE_BEDROOM),
    )

    /** Which pretend room the mock sensors are in. */
    val mockScenario: StateFlow<MockScenario> = _mockScenario

    fun setMockScenario(scenario: MockScenario) {
        prefs.edit().putString(KEY_SCENARIO, scenario.name).apply()
        _mockScenario.value = scenario
    }

    private val _mockOnline = MutableStateFlow(true)

    /**
     * Testing switch: false makes the demo sensors stop answering. Not saved, so a restart
     * always begins with working sensors.
     */
    val mockOnline: StateFlow<Boolean> = _mockOnline

    fun setMockOnline(online: Boolean) {
        _mockOnline.value = online
    }

    private val _mockMissingSensors = MutableStateFlow<Set<SensorType>>(emptySet())

    /** Testing switches: the demo sensors in this set report no value. Not saved. */
    val mockMissingSensors: StateFlow<Set<SensorType>> = _mockMissingSensors

    fun setMockSensorFailed(sensor: SensorType, failed: Boolean) {
        _mockMissingSensors.value = if (failed) _mockMissingSensors.value + sensor else _mockMissingSensors.value - sensor
    }

    private val _themeMode = MutableStateFlow(ThemeMode.fromName(prefs.getString(KEY_THEME, null)))

    /** Light, dark, or follow the phone. */
    val themeMode: StateFlow<ThemeMode> = _themeMode

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _themeMode.value = mode
    }

    private val _recordingInterval = MutableStateFlow(
        runCatching { RecordingInterval.valueOf(prefs.getString(KEY_INTERVAL, null) ?: "") }
            .getOrDefault(RecordingInterval.DEFAULT),
    )

    /** How often new sessions store a reading. Running sessions keep the value they started with. */
    val recordingInterval: StateFlow<RecordingInterval> = _recordingInterval

    fun setRecordingInterval(interval: RecordingInterval) {
        prefs.edit().putString(KEY_INTERVAL, interval.name).apply()
        _recordingInterval.value = interval
    }

    /** True once the notification permission has been asked for, so the question is not repeated. */
    var notificationPermissionAsked: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION_ASKED, false)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATION_ASKED, value).apply()

    private companion object {
        const val KEY_NOTIFICATION_ASKED = "notification_permission_asked"
        const val KEY_THEME = "theme_mode"
        const val KEY_INTERVAL = "recording_interval"
        const val KEY_PROFILE = "selected_profile_id"
        const val KEY_SCENARIO = "mock_scenario"
        const val KEY_SOURCE = "sensor_source"
        const val KEY_PICO_ADDRESS = "pico_address"
        const val NONE = -1L

        /** Where the Pico's own network puts it (see docs/pico-protocol.md). */
        const val DEFAULT_PICO_ADDRESS = "192.168.4.1"
    }
}
