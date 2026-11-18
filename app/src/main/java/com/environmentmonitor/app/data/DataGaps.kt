package com.environmentmonitor.app.data

/** A stretch of a session with no readings at all: the sensors were out of reach (signal lost, Pico off). */
data class DataGap(
    /** Time of the last reading before the gap, epoch milliseconds. */
    val start: Long,
    /** Time of the first reading after the gap, epoch milliseconds. */
    val end: Long,
) {
    val durationMillis: Long get() = end - start
}

/** Which stretches of a session count as gaps. The charts and the summary use the same rule. */
object DataGaps {

    /** Never call a gap shorter than this: the clocks of the phone and the readings are not that exact. */
    const val MIN_THRESHOLD_MILLIS = 10_000L

    /**
     * Two readings further apart than this have a gap between them: three recording intervals, but at
     * least 10 seconds. (A session recording every 10 seconds shows a gap after 30 seconds of silence.)
     */
    fun thresholdMillis(recordingIntervalSeconds: Int): Long =
        maxOf(3L * recordingIntervalSeconds * 1_000L, MIN_THRESHOLD_MILLIS)

    /** The gaps in [timestamps], which must be in time order. A distance exactly at the threshold is not a gap. */
    fun find(timestamps: List<Long>, thresholdMillis: Long): List<DataGap> =
        timestamps.zipWithNext()
            .filter { (before, after) -> after - before > thresholdMillis }
            .map { (before, after) -> DataGap(before, after) }
}
