package com.environmentmonitor.app.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.environmentmonitor.app.data.Profile
import com.environmentmonitor.app.data.ProfileRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfilesViewModel(private val repository: ProfileRepository) : ViewModel() {

    val profiles: StateFlow<List<Profile>> = repository.profiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Copies [profile] and reports the id of the copy so the editor can open it. */
    fun duplicate(profile: Profile, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            repository.duplicate(profile.id)?.let(onCreated)
        }
    }

    fun delete(profile: Profile) {
        viewModelScope.launch { repository.delete(profile.id) }
    }
}
