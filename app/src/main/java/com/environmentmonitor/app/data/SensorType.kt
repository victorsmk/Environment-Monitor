package com.environmentmonitor.app.data

/**
 * The four things the device measures, with the units the app uses internally.
 * Raw device values are converted to these units by the device adapter (phase 2).
 */
enum class SensorType(
    val label: String,
    val unit: String,
    /** Lowest value the profile editor allows. */
    val min: Float,
    /** Highest value the profile editor allows. */
    val max: Float,
    /** Granularity of the profile editor slider. */
    val step: Float,
) {
    TEMPERATURE("Temperature", "°C", 0f, 40f, 0.5f),
    HUMIDITY("Humidity", "%", 0f, 100f, 1f),
    LIGHT("Light", "lux", 0f, 1000f, 10f),
    SOUND("Sound", "level", 0f, 100f, 1f),
}

/** How much a sensor counts towards the overall score. */
enum class Importance(val label: String, val weight: Int) {
    OFF("Off", 0),
    LOW("Low", 1),
    MEDIUM("Medium", 2),
    HIGH("High", 3),
}

/** Ideal range for one sensor, plus how much that sensor matters. */
data class SensorSetting(
    val idealMin: Float,
    val idealMax: Float,
    val importance: Importance,
)
