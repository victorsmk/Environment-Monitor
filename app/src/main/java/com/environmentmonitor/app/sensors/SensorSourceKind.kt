package com.environmentmonitor.app.sensors

/** Where the app takes its readings from. The choice is saved in [com.environmentmonitor.app.AppSettings]. */
enum class SensorSourceKind(val label: String, val description: String) {
    DEMO("Demo data", "Made-up readings. Needs no hardware."),
    PICO("Raspberry Pi Pico W", "The real sensors, over Wi-Fi."),
    ;

    companion object {
        val DEFAULT = DEMO

        fun fromName(name: String?): SensorSourceKind = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
