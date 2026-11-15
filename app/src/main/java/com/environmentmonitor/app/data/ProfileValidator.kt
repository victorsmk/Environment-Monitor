package com.environmentmonitor.app.data

data class ProfileValidation(
    val nameError: String? = null,
    val settingsError: String? = null,
) {
    val isValid: Boolean get() = nameError == null && settingsError == null
}

/** Rules a profile must satisfy before it can be saved. */
object ProfileValidator {

    fun validate(name: String, settings: Map<SensorType, SensorSetting>): ProfileValidation {
        val trimmed = name.trim()
        val nameError = when {
            trimmed.isEmpty() -> "Enter a name"
            trimmed.length > ProfileRepository.MAX_NAME_LENGTH ->
                "Use at most ${ProfileRepository.MAX_NAME_LENGTH} characters"
            else -> null
        }

        val active = settings.filterValues { it.importance != Importance.OFF }
        val settingsError = when {
            active.isEmpty() -> "Turn on at least one sensor"
            else -> active.entries
                .firstOrNull { (_, setting) -> setting.idealMax <= setting.idealMin }
                ?.let { (sensor, _) -> "Make the ${sensor.label.lowercase()} range wider than zero" }
        }

        return ProfileValidation(nameError, settingsError)
    }
}
