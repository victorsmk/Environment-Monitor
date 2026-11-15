package com.environmentmonitor.app.data

/** How often a running session stores a reading. The user picks one in Settings. */
enum class RecordingInterval(val seconds: Int, val label: String) {
    EVERY_SECOND(1, "Every second"),
    EVERY_5_SECONDS(5, "Every 5 seconds"),
    EVERY_10_SECONDS(10, "Every 10 seconds"),
    EVERY_30_SECONDS(30, "Every 30 seconds"),
    EVERY_MINUTE(60, "Every minute"),
    EVERY_5_MINUTES(300, "Every 5 minutes");

    val millis: Long get() = seconds * 1_000L

    companion object {
        val DEFAULT = EVERY_10_SECONDS

        /** The option for [seconds], or null for a value that is not on the list. */
        fun fromSeconds(seconds: Int): RecordingInterval? = entries.firstOrNull { it.seconds == seconds }

        /** "every 10 seconds", also for values that are not on the list. */
        fun describe(seconds: Int): String = when {
            seconds == 1 -> "every second"
            seconds < 60 -> "every $seconds seconds"
            seconds == 60 -> "every minute"
            seconds % 60 == 0 -> "every ${seconds / 60} minutes"
            else -> "every $seconds seconds"
        }
    }
}
