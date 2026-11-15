package com.environmentmonitor.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.environmentmonitor.app.AppSettings
import com.environmentmonitor.app.data.EnvironmentScore
import com.environmentmonitor.app.data.Measurement
import com.environmentmonitor.app.data.Profile
import com.environmentmonitor.app.data.ProfileRepository
import com.environmentmonitor.app.data.ScoringEngine
import com.environmentmonitor.app.data.SensorSetting
import com.environmentmonitor.app.data.SensorType
import com.environmentmonitor.app.data.Session
import com.environmentmonitor.app.data.SessionRepository
import com.environmentmonitor.app.sensors.SensorSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the dashboard shows, in one immutable snapshot. */
data class DashboardUiState(
    val profiles: List<Profile> = emptyList(),
    /** The profile chosen in the picker (used when no session is running). */
    val selectedProfile: Profile? = null,
    /** The running session, if any. While it runs, it decides what the score is judged against. */
    val activeSession: Session? = null,
    val measurement: Measurement? = null,
    val score: EnvironmentScore? = null,
) {
    /** The ideal ranges in use: the session's snapshot while recording, else the picked profile. */
    val settings: Map<SensorType, SensorSetting>?
        get() = activeSession?.settings() ?: selectedProfile?.settings()

    val environmentName: String?
        get() = activeSession?.profileName ?: selectedProfile?.name

    val environmentIcon: String?
        get() = activeSession?.profileIcon ?: selectedProfile?.icon
}

class DashboardViewModel(
    sensors: SensorSource,
    profiles: ProfileRepository,
    private val sessions: SessionRepository,
    private val settings: AppSettings,
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        profiles.profiles,
        settings.selectedProfileId,
        sessions.activeSession,
        sensors.measurements.map<Measurement, Measurement?> { it }.onStart { emit(null) },
    ) { allProfiles, selectedId, active, measurement ->
        val selected = allProfiles.firstOrNull { it.id == selectedId } ?: allProfiles.firstOrNull()
        val judgedBy = active?.settings() ?: selected?.settings()
        DashboardUiState(
            profiles = allProfiles,
            selectedProfile = selected,
            activeSession = active,
            measurement = measurement,
            score = if (measurement != null && judgedBy != null) {
                ScoringEngine.evaluate(judgedBy, measurement)
            } else {
                null
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun selectProfile(profile: Profile) = settings.selectProfile(profile.id)

    fun startSession() {
        val profile = uiState.value.selectedProfile ?: return
        // The interval is read now and kept by the session, so later changes in Settings
        // do not affect it.
        val interval = settings.recordingInterval.value
        viewModelScope.launch { sessions.start(profile, name = "", interval = interval) }
    }

    fun stopSession() {
        val session = uiState.value.activeSession ?: return
        viewModelScope.launch { sessions.stop(session.id) }
    }
}
