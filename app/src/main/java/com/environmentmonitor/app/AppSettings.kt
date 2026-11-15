package com.environmentmonitor.app

import android.content.Context
import com.environmentmonitor.app.data.RecordingInterval
import com.environmentmonitor.app.sensors.MockScenario
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

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_INTERVAL = "recording_interval"
        const val KEY_PROFILE = "selected_profile_id"
        const val KEY_SCENARIO = "mock_scenario"
        const val NONE = -1L
    }
}
