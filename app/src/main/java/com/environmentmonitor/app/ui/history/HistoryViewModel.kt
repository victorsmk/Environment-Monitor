package com.environmentmonitor.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.environmentmonitor.app.data.ReadingRepository
import com.environmentmonitor.app.data.Session
import com.environmentmonitor.app.data.SessionAnalyzer
import com.environmentmonitor.app.data.SessionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val sessions: SessionRepository,
    private val readings: ReadingRepository,
) : ViewModel() {

    val allSessions: StateFlow<List<Session>> = sessions.sessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Sessions already looked at, so one without readings is not re-read every time the list changes. */
    private val checked = mutableSetOf<Long>()

    init {
        // A finished session gets its average score worked out once and stored, so the list can show
        // it without loading every reading. This also fills in sessions recorded before the badge existed.
        viewModelScope.launch {
            sessions.sessions.collect { list ->
                list.filter { !it.isActive && it.averageScore == null && checked.add(it.id) }.forEach { session ->
                    val analysis = SessionAnalyzer.analyze(session.settings(), readings.getAll(session.id))
                    if (analysis != null) sessions.saveAverageScore(session.id, analysis.overall)
                }
            }
        }
    }

    fun stop(session: Session) {
        viewModelScope.launch { sessions.stop(session.id) }
    }

    fun delete(session: Session) {
        viewModelScope.launch { sessions.delete(session.id) }
    }
}