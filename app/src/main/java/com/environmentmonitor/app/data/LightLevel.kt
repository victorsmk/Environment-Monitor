package com.environmentmonitor.app.data

/**
 * A plain word for how bright a room is, shown next to the lux number because most people have
 * no feel for lux. Only for display: scores, profiles and stored readings all stay in lux.
 *
 * | Lux | Word |
 * |---|---|
 * | 0–10 | Dark |
 * | 10–50 | Dim |
 * | 50–200 | Soft |
 * | 200–500 | Bright |
 * | above 500 | Very bright |
 */
enum class LightLevel(val label: String, val fromLux: Float) {
    DARK("Dark", 0f),
    DIM("Dim", 10f),
    SOFT("Soft", 50f),
    BRIGHT("Bright", 200f),
    VERY_BRIGHT("Very bright", 500f);

    companion object {
        /** The word for a reading: 10 lux is already Dim, 200 lux already Bright. */
        fun of(lux: Float): LightLevel = entries.lastOrNull { lux >= it.fromLux } ?: DARK

        /**
         * The word for the top end of a range: 500 lux is still Bright. This keeps an office range
         * of 300–500 lux as "Bright" and a sleep range of 0–10 lux as "Dark".
         */
        fun ofUpperEnd(lux: Float): LightLevel = entries.lastOrNull { lux > it.fromLux } ?: DARK

        /** "Bright" for a range inside one level, otherwise "Dim to Bright". */
        fun describeRange(minLux: Float, maxLux: Float): String {
            val low = of(minLux)
            val high = ofUpperEnd(maxLux)
            return if (low == high || high.ordinal < low.ordinal) low.label else "${low.label} to ${high.label}"
        }
    }
}
