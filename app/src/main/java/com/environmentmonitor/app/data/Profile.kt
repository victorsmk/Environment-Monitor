package com.environmentmonitor.app.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * An activity profile: the ideal range and importance of each sensor for one
 * kind of environment (sleep, office work, sport, ...).
 */
@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** An emoji shown next to the profile. */
    val icon: String,
    /** Presets are seeded by the app: read-only, but they can be duplicated. */
    val isPreset: Boolean = false,
    @Embedded(prefix = "temperature_") val temperature: SensorSetting,
    @Embedded(prefix = "humidity_") val humidity: SensorSetting,
    @Embedded(prefix = "light_") val light: SensorSetting,
    @Embedded(prefix = "sound_") val sound: SensorSetting,
) {
    fun setting(sensor: SensorType): SensorSetting = when (sensor) {
        SensorType.TEMPERATURE -> temperature
        SensorType.HUMIDITY -> humidity
        SensorType.LIGHT -> light
        SensorType.SOUND -> sound
    }

    fun settings(): Map<SensorType, SensorSetting> =
        SensorType.entries.associateWith { setting(it) }

    fun withSettings(settings: Map<SensorType, SensorSetting>): Profile = copy(
        temperature = settings.getValue(SensorType.TEMPERATURE),
        humidity = settings.getValue(SensorType.HUMIDITY),
        light = settings.getValue(SensorType.LIGHT),
        sound = settings.getValue(SensorType.SOUND),
    )
}
