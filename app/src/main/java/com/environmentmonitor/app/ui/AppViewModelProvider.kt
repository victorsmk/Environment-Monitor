package com.environmentmonitor.app.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.environmentmonitor.app.EnvironmentMonitorApplication
import com.environmentmonitor.app.ui.profiles.NEW_PROFILE_ID
import com.environmentmonitor.app.ui.profiles.ProfileEditorViewModel
import com.environmentmonitor.app.ui.profiles.ProfilesViewModel

const val PROFILE_ID_ARG = "profileId"

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
    }
}

private fun CreationExtras.app(): EnvironmentMonitorApplication =
    this[APPLICATION_KEY] as EnvironmentMonitorApplication
