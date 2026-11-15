package com.environmentmonitor.app.data

/** One snapshot of all four sensors, in the app's units (°C, %, lux, sound level 0–100). */
data class Measurement(
    val temperature: Float,
    val humidity: Float,
    val light: Float,
    val sound: Float,
) {
    fun value(sensor: SensorType): Float = when (sensor) {
        SensorType.TEMPERATURE -> temperature
        SensorType.HUMIDITY -> humidity
        SensorType.LIGHT -> light
        SensorType.SOUND -> sound
    }
}
