package com.environmentmonitor.app.ui.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.environmentmonitor.app.data.Session
import com.environmentmonitor.app.data.SessionRepository
import com.environmentmonitor.app.data.SessionValidator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SessionDetailViewModel(
    private val repository: SessionRepository,
    private val sessionId: Long,
) : ViewModel() {

    /** The stored session; changes while it runs or after an edit. Null if it does not exist. */
    val session: StateFlow<Session?> = repository.observe(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var name by mutableStateOf("")
        private set
    var notes by mutableStateOf("")
        private set
    var nameError by mutableStateOf<String?>(null)
        private set

    /** True once the fields hold the stored values. */
    var loaded by mutableStateOf(false)
        private set

    private var savedName = ""
    private var savedNotes = ""

    val hasChanges: Boolean get() = loaded && (name.trim() != savedName || notes.trim() != savedNotes)

    init {
        viewModelScope.launch {
            repository.get(sessionId)?.let {
                name = it.name
                notes = it.notes
                savedName = it.name
                savedNotes = it.notes
            }
            loaded = true
        }
    }

    fun onNameChange(value: String) {
        name = value
        nameError = null
    }

    fun onNotesChange(value: String) {
        notes = value.take(SessionRepository.MAX_NOTES_LENGTH)
    }

    fun save(onSaved: () -> Unit = {}) {
        val error = SessionValidator.nameError(name)
        if (error != null) {
            nameError = error
            return
        }
        viewModelScope.launch {
            if (repository.updateDetails(sessionId, name, notes)) {
                savedName = name.trim()
                savedNotes = notes.trim()
                name = savedName
                notes = savedNotes
                onSaved()
            }
        }
    }

    fun stop() {
        viewModelScope.launch { repository.stop(sessionId) }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.delete(sessionId)
            onDeleted()
        }
    }
}
