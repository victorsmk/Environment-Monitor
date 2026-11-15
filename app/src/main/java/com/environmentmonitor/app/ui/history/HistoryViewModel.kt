package com.environmentmonitor.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.environmentmonitor.app.data.Profile
import com.environmentmonitor.app.data.ProfileRepository
import com.environmentmonitor.app.data.Session
import com.environmentmonitor.app.data.SessionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val sessions: SessionRepository,
    profiles: ProfileRepository,
) : ViewModel() {

    val allSessions: StateFlow<List<Session>> = sessions.sessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allProfiles: StateFlow<List<Profile>> = profiles.profiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Starts a session and reports its id (null if one is already running). */
    fun start(profile: Profile, name: String, onStarted: (Long?) -> Unit) {
        viewModelScope.launch { onStarted(sessions.start(profile, name)) }
    }

    fun stop(session: Session) {
        viewModelScope.launch { sessions.stop(session.id) }
    }

    fun delete(session: Session) {
        viewModelScope.launch { sessions.delete(session.id) }
    }
}
