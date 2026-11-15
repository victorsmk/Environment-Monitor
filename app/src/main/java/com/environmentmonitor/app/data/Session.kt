package com.environmentmonitor.app.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One monitoring session: a stretch of time spent in an environment, judged against one profile.
 *
 * The profile's settings are copied into the session when it starts, so editing or deleting the
 * profile later never changes how an old session was scored.
 */
@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val notes: String = "",
    /** Epoch milliseconds. */
    val startedAt: Long,
    /** Epoch milliseconds, or null while the session is still running. */
    val endedAt: Long? = null,
    val profileName: String,
    val profileIcon: String,
    @Embedded(prefix = "temperature_") val temperature: SensorSetting,
    @Embedded(prefix = "humidity_") val humidity: SensorSetting,
    @Embedded(prefix = "light_") val light: SensorSetting,
    @Embedded(prefix = "sound_") val sound: SensorSetting,
) {
    val isActive: Boolean get() = endedAt == null

    fun setting(sensor: SensorType): SensorSetting = when (sensor) {
        SensorType.TEMPERATURE -> temperature
        SensorType.HUMIDITY -> humidity
        SensorType.LIGHT -> light
        SensorType.SOUND -> sound
    }

    fun settings(): Map<SensorType, SensorSetting> =
        SensorType.entries.associateWith { setting(it) }

    /** Length so far for a running session, total length for a finished one. */
    fun durationMillis(now: Long): Long = (endedAt ?: now) - startedAt

    companion object {
        /** A new running session that carries a snapshot of [profile]. */
        fun startedWith(profile: Profile, name: String, now: Long) = Session(
            name = name,
            startedAt = now,
            profileName = profile.name,
            profileIcon = profile.icon,
            temperature = profile.temperature,
            humidity = profile.humidity,
            light = profile.light,
            sound = profile.sound,
        )
    }
}
