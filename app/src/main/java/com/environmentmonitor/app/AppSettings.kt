package com.environmentmonitor.app

import android.content.Context
import com.environmentmonitor.app.sensors.MockScenario
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

    private companion object {
        const val KEY_PROFILE = "selected_profile_id"
        const val KEY_SCENARIO = "mock_scenario"
        const val NONE = -1L
    }
}
