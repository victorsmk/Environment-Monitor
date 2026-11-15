package com.environmentmonitor.app.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.environmentmonitor.app.EnvironmentMonitorApplication
import com.environmentmonitor.app.ui.dashboard.DashboardViewModel
import com.environmentmonitor.app.ui.history.HistoryViewModel
import com.environmentmonitor.app.ui.history.SessionDetailViewModel
import com.environmentmonitor.app.ui.profiles.NEW_PROFILE_ID
import com.environmentmonitor.app.ui.profiles.ProfileEditorViewModel
import com.environmentmonitor.app.ui.profiles.ProfilesViewModel

const val PROFILE_ID_ARG = "profileId"
const val SESSION_ID_ARG = "sessionId"

/** Builds every ViewModel from the app-wide [com.environmentmonitor.app.AppContainer]. */
object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            ProfilesViewModel(app().container.profileRepository)
        }
        initializer {
            val handle: SavedStateHandle = createSavedStateHandle()
            ProfileEditorViewModel(
                repository = app().container.profileRepository,
                profileId = handle.get<Long>(PROFILE_ID_ARG) ?: NEW_PROFILE_ID,
            )
        }
        initializer {
            HistoryViewModel(sessions = app().container.sessionRepository)
        }
        initializer {
            DashboardViewModel(
                sensors = app().container.sensorSource,
                profiles = app().container.profileRepository,
                sessions = app().container.sessionRepository,
                settings = app().container.settings,
            )
        }
        initializer {
            val handle: SavedStateHandle = createSavedStateHandle()
            SessionDetailViewModel(
                repository = app().container.sessionRepository,
                readings = app().container.readingRepository,
                sessionId = handle.get<Long>(SESSION_ID_ARG) ?: -1L,
            )
        }
    }
}

private fun CreationExtras.app(): EnvironmentMonitorApplication =
    this[APPLICATION_KEY] as EnvironmentMonitorApplication
