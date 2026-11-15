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
    /**
     * How far outside the ideal range a reading may be before this sensor scores 0.
     * The score falls in a straight line from 100 at the edge of the range to 0 at this distance.
     */
    val tolerance: Float,
) {
    TEMPERATURE("Temperature", "°C", 0f, 40f, 0.5f, tolerance = 5f),
    HUMIDITY("Humidity", "%", 0f, 100f, 1f, tolerance = 20f),
    LIGHT("Light", "lux", 0f, 1000f, 10f, tolerance = 150f),
    SOUND("Sound", "level", 0f, 100f, 1f, tolerance = 25f),
}

/**
 * How much a sensor counts towards the overall score.
 *
 * The enum names (OFF, LOW, MEDIUM, HIGH) are what is saved in the database, so they must not
 * change. Only the [label] shown to people was renamed, to say what each level does.
 */
enum class Importance(
    val label: String,
    /** The sensor's share of the score is its weight out of the total of all sensors' weights. */
    val weight: Int,
    /** One sentence saying what this level means, shown under the buttons. */
    val meaning: String,
) {
    OFF("Ignore", 0, "Ignored. It won't affect the score."),
    LOW("Minor", 1, "Counts a little toward the score."),
    MEDIUM("Important", 2, "Counts normally toward the score."),
    HIGH("Critical", 3, "Matters most. A miss here costs the most points."),
}

/** Ideal range for one sensor, plus how much that sensor matters. */
data class SensorSetting(
    val idealMin: Float,
    val idealMax: Float,
    val importance: Importance,
)
