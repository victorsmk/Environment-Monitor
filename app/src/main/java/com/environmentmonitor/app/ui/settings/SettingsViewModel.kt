package com.environmentmonitor.app.ui.settings

import androidx.lifecycle.ViewModel
import com.environmentmonitor.app.AppSettings
import com.environmentmonitor.app.data.RecordingInterval
import com.environmentmonitor.app.data.SensorType
import com.environmentmonitor.app.sensors.MockScenario
import com.environmentmonitor.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(private val settings: AppSettings) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
    val recordingInterval: StateFlow<RecordingInterval> = settings.recordingInterval
    val mockScenario: StateFlow<MockScenario> = settings.mockScenario

    val mockOnline: StateFlow<Boolean> = settings.mockOnline

    fun setMockOnline(online: Boolean) = settings.setMockOnline(online)

    val mockMissingSensors: StateFlow<Set<SensorType>> = settings.mockMissingSensors

    fun setMockSensorFailed(sensor: SensorType, failed: Boolean) =
        settings.setMockSensorFailed(sensor, failed)

    fun selectThemeMode(mode: ThemeMode) = settings.setThemeMode(mode)

    fun selectRecordingInterval(interval: RecordingInterval) = settings.setRecordingInterval(interval)

    fun selectMockScenario(scenario: MockScenario) = settings.setMockScenario(scenario)
}
