package com.environmentmonitor.app.data

/**
 * One snapshot of all four sensors, in the app's units (°C, %, lux, sound level 0–100).
 *
 * A value is null when that sensor has no value right now (for example it has failed). The other
 * sensors still count: see [ScoringEngine].
 */
data class Measurement(
    val temperature: Float?,
    val humidity: Float?,
    val light: Float?,
    val sound: Float?,
) {
    fun value(sensor: SensorType): Float? = when (sensor) {
        SensorType.TEMPERATURE -> temperature
        SensorType.HUMIDITY -> humidity
        SensorType.LIGHT -> light
        SensorType.SOUND -> sound
    }

    /** No sensor has a value, so there is nothing worth storing or scoring. */
    val isEmpty: Boolean
        get() = SensorType.entries.all { value(it) == null }

    /** The same snapshot with the values of [sensors] taken away. */
    fun without(sensors: Set<SensorType>): Measurement = Measurement(
        temperature = temperature.takeUnless { SensorType.TEMPERATURE in sensors },
        humidity = humidity.takeUnless { SensorType.HUMIDITY in sensors },
        light = light.takeUnless { SensorType.LIGHT in sensors },
        sound = sound.takeUnless { SensorType.SOUND in sensors },
    )
}
