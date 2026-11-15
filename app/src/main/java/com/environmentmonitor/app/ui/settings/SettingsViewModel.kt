package com.environmentmonitor.app.ui.settings

import androidx.lifecycle.ViewModel
import com.environmentmonitor.app.AppSettings
import com.environmentmonitor.app.data.RecordingInterval
import com.environmentmonitor.app.sensors.MockScenario
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(private val settings: AppSettings) : ViewModel() {

    val recordingInterval: StateFlow<RecordingInterval> = settings.recordingInterval
    val mockScenario: StateFlow<MockScenario> = settings.mockScenario

    fun selectRecordingInterval(interval: RecordingInterval) = settings.setRecordingInterval(interval)

    fun selectMockScenario(scenario: MockScenario) = settings.setMockScenario(scenario)
}
