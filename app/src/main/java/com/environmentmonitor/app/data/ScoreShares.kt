package com.environmentmonitor.app.data

import kotlin.math.roundToInt

/** How a profile's importance settings divide up the score. */
object ScoreShares {

    /**
     * Each sensor's share of the score, in whole percent: its weight out of the total of every
     * sensor's weight. A sensor set to Ignore has no share and is left out.
     *
     * Each share is rounded on its own, so sensors with equal importance always show equal shares
     * (three equal sensors out of four show 27, 27 and 27 beside 18, which adds up to 99, not 100).
     */
    fun of(importance: Map<SensorType, Importance>): Map<SensorType, Int> {
        val weights = SensorType.entries
            .associateWith { importance[it]?.weight ?: 0 }
            .filterValues { it > 0 }
        val total = weights.values.sum()
        if (total == 0) return emptyMap()
        return weights.mapValues { (_, weight) -> (100f * weight / total).roundToInt() }
    }

    /** The same, read from a profile's or session's settings. */
    fun ofSettings(settings: Map<SensorType, SensorSetting>): Map<SensorType, Int> =
        of(settings.mapValues { it.value.importance })
}
