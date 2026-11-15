package com.environmentmonitor.app.ui.theme

/** Which colours the app uses: follow the phone's setting, or always light or always dark. */
enum class ThemeMode(val label: String, val description: String) {
    SYSTEM("Use system setting", "Follows your phone's light or dark mode"),
    LIGHT("Light", "Always light"),
    DARK("Dark", "Always dark");

    /** Whether to draw the dark colours, given what the phone itself is currently set to. */
    fun isDark(systemIsDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemIsDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        val DEFAULT = SYSTEM

        /** The mode saved under [name], or the default for a missing or unknown name. */
        fun fromName(name: String?): ThemeMode =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
