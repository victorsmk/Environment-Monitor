package com.environmentmonitor.app.data

/**
 * A plain word for how bright a room is, shown next to the lux number because most people have
 * no feel for lux. Only for display and for picking a range: scores, profiles and stored readings
 * all stay in lux.
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

    /** Where this level ends: where the next one starts, or the top of the light sensor's scale. */
    val upperLux: Float
        get() = entries.getOrNull(ordinal + 1)?.fromLux ?: SensorType.LIGHT.max

    companion object {
        /** The word for a reading: 10 lux is already Dim, 200 lux already Bright. */
        fun of(lux: Float): LightLevel = entries.lastOrNull { lux >= it.fromLux } ?: DARK

        /**
         * The word for the top end of a range: 500 lux is still Bright. This keeps an office range
         * of 300–500 lux as "Bright" and a sleep range of 0–10 lux as "Dark".
         */
        fun ofUpperEnd(lux: Float): LightLevel = entries.lastOrNull { lux > it.fromLux } ?: DARK

        /** The levels a range covers, lowest to highest (always at least one). */
        fun covering(minLux: Float, maxLux: Float): List<LightLevel> {
            val low = of(minLux)
            val high = ofUpperEnd(maxLux)
            return if (high.ordinal <= low.ordinal) listOf(low) else entries.subList(low.ordinal, high.ordinal + 1)
        }

        /** "Bright" for a range inside one level, otherwise "Dim to Bright". */
        fun describeRange(minLux: Float, maxLux: Float): String {
            val levels = covering(minLux, maxLux)
            return if (levels.size == 1) levels.first().label else "${levels.first().label} to ${levels.last().label}"
        }

        /** The lux range that exactly covers [first] up to [last]. */
        fun rangeOf(first: LightLevel, last: LightLevel): Pair<Float, Float> {
            val low = if (first.ordinal <= last.ordinal) first else last
            val high = if (first.ordinal <= last.ordinal) last else first
            return low.fromLux to high.upperLux
        }

        /** The result of one tap in the level picker. */
        data class Tap(
            val minLux: Float,
            val maxLux: Float,
            /** The level the next tap will stretch the range from, or null when a pair is complete. */
            val anchor: LightLevel?,
        )

        /**
         * What the range becomes when the user taps [tapped] in the level picker. Taps come in
         * pairs, whatever range was there before:
         *
         * - The first tap of a pair selects just that level and remembers it as the anchor.
         * - The second tap selects everything from the anchor to the tapped level (Dark, then Dim:
         *   0–50 lux). Tapping the same level twice keeps that single level.
         *
         * The next tap then starts a new pair.
         */
        fun afterTap(anchor: LightLevel?, tapped: LightLevel): Tap {
            val (min, max) = rangeOf(anchor ?: tapped, tapped)
            return Tap(min, max, anchor = if (anchor == null) tapped else null)
        }

        /**
         * Where a reading sits on a chart axis that gives every level the same height, 0..1.
         * Dark and Dim would be thin slivers on a straight lux scale, so charts use this instead.
         */
        fun position(lux: Float): Float {
            val level = of(lux.coerceAtLeast(0f))
            val inside = ((lux - level.fromLux) / (level.upperLux - level.fromLux)).coerceIn(0f, 1f)
            return (level.ordinal + inside) / entries.size
        }
    }
}
