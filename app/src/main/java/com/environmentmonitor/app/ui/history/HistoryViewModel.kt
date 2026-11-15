package com.environmentmonitor.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.environmentmonitor.app.data.Session
import com.environmentmonitor.app.data.SessionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(private val sessions: SessionRepository) : ViewModel() {

    val allSessions: StateFlow<List<Session>> = sessions.sessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun stop(session: Session) {
        viewModelScope.launch { sessions.stop(session.id) }
    }

    fun delete(session: Session) {
        viewModelScope.launch { sessions.delete(session.id) }
    }
}
