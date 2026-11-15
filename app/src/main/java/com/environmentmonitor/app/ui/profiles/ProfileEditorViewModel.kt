package com.environmentmonitor.app.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.environmentmonitor.app.data.Importance
import com.environmentmonitor.app.data.Profile
import com.environmentmonitor.app.data.ProfileRepository
import com.environmentmonitor.app.data.ProfileValidator
import com.environmentmonitor.app.data.SensorSetting
import com.environmentmonitor.app.data.SensorType
import com.environmentmonitor.app.ui.ProfileIcons
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val NEW_PROFILE_ID = -1L

data class EditorUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    /** Presets can be viewed but not changed. */
    val isPreset: Boolean = false,
    val name: String = "",
    val icon: String = ProfileIcons.first(),
    val settings: Map<SensorType, SensorSetting> = defaultSettings(),
    val nameError: String? = null,
    val settingsError: String? = null,
    /** What the form held when it opened, to tell whether the user changed anything. */
    val baseline: EditorSnapshot = EditorSnapshot(),
) {
    val hasChanges: Boolean
        get() = !isLoading && !isPreset && snapshot() != baseline

    fun snapshot() = EditorSnapshot(name.trim(), icon, settings)
}

/** The editable parts of a profile form. */
data class EditorSnapshot(
    val name: String = "",
    val icon: String = ProfileIcons.first(),
    val settings: Map<SensorType, SensorSetting> = defaultSettings(),
)

private fun defaultSettings(): Map<SensorType, SensorSetting> = mapOf(
    SensorType.TEMPERATURE to SensorSetting(20f, 24f, Importance.MEDIUM),
    SensorType.HUMIDITY to SensorSetting(40f, 60f, Importance.MEDIUM),
    SensorType.LIGHT to SensorSetting(300f, 500f, Importance.MEDIUM),
    SensorType.SOUND to SensorSetting(0f, 45f, Importance.MEDIUM),
)

/** Drives the create / edit / view screen for one profile ([NEW_PROFILE_ID] means create). */
class ProfileEditorViewModel(
    private val repository: ProfileRepository,
    private val profileId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    init {
        if (profileId == NEW_PROFILE_ID) {
            _state.update { it.copy(isLoading = false) }
        } else {
            viewModelScope.launch {
                val profile = repository.get(profileId)
                _state.update {
                    if (profile == null) it.copy(isLoading = false)
                    else it.copy(
                        isLoading = false,
                        isNew = false,
                        isPreset = profile.isPreset,
                        name = profile.name,
                        icon = profile.icon,
                        settings = profile.settings(),
                        baseline = EditorSnapshot(profile.name.trim(), profile.icon, profile.settings()),
                    )
                }
            }
        }
    }

    fun onNameChange(name: String) =
        _state.update { it.copy(name = name, nameError = null) }

    fun onIconChange(icon: String) = _state.update { it.copy(icon = icon) }

    fun onRangeChange(sensor: SensorType, min: Float, max: Float) = updateSetting(sensor) {
        it.copy(idealMin = min, idealMax = max)
    }

    fun onImportanceChange(sensor: SensorType, importance: Importance) = updateSetting(sensor) {
        it.copy(importance = importance)
    }

    private fun updateSetting(sensor: SensorType, change: (SensorSetting) -> SensorSetting) =
        _state.update {
            it.copy(
                settings = it.settings + (sensor to change(it.settings.getValue(sensor))),
                settingsError = null,
            )
        }

    /** Validates and saves; calls [onSaved] only when the profile was stored. */
    fun save(onSaved: () -> Unit) {
        val current = _state.value
        if (current.isPreset) return
        val validation = ProfileValidator.validate(current.name, current.settings)
        if (!validation.isValid) {
            _state.update {
                it.copy(nameError = validation.nameError, settingsError = validation.settingsError)
            }
            return
        }
        viewModelScope.launch {
            val profile = Profile(
                id = if (current.isNew) 0 else profileId,
                name = current.name.trim(),
                icon = current.icon,
                temperature = current.settings.getValue(SensorType.TEMPERATURE),
                humidity = current.settings.getValue(SensorType.HUMIDITY),
                light = current.settings.getValue(SensorType.LIGHT),
                sound = current.settings.getValue(SensorType.SOUND),
            )
            if (current.isNew) repository.create(profile) else repository.update(profile)
            onSaved()
        }
    }

    /** From the read-only preset view: copies the preset and reports the copy's id. */
    fun duplicate(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            repository.duplicate(profileId)?.let(onCreated)
        }
    }
}
