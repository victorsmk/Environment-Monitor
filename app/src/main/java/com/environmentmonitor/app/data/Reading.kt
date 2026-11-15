package com.environmentmonitor.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One stored measurement of a session. A running session gets one about every 10 seconds
 * (roughly 2,900 rows for an 8 hour night).
 *
 * The score is not stored: it is worked out from the reading and the session's settings snapshot,
 * which never change, so it can always be recomputed.
 */
@Entity(
    tableName = "readings",
    foreignKeys = [
        ForeignKey(
            entity = Session::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            // Deleting a session removes its readings with it.
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["sessionId", "timestamp"])],
)
data class Reading(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    /** Epoch milliseconds. */
    val timestamp: Long,
    val temperature: Float,
    val humidity: Float,
    val light: Float,
    val sound: Float,
) {
    fun toMeasurement() = Measurement(temperature, humidity, light, sound)

    companion object {
        fun of(sessionId: Long, measurement: Measurement, timestamp: Long) = Reading(
            sessionId = sessionId,
            timestamp = timestamp,
            temperature = measurement.temperature,
            humidity = measurement.humidity,
            light = measurement.light,
            sound = measurement.sound,
        )
    }
}
