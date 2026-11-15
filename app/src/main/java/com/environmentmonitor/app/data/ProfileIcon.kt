package com.environmentmonitor.app.data

/**
 * The picture shown for a profile. A profile stores the [key] (the enum name); the drawing for each
 * one lives in the UI layer, so this file has no Android or Compose code.
 */
enum class ProfileIcon(val label: String) {
    MOON("Sleep"),
    LAPTOP("Work"),
    DUMBBELL("Sport"),
    BOOK("Study"),
    GAMEPAD("Games"),
    LOTUS("Relax"),
    POT("Cooking"),
    LEAF("Nature"),
    PALETTE("Art"),
    HEADPHONES("Music");

    /** What is saved in the database. */
    val key: String get() = name

    companion object {
        /** Shown for a profile whose saved icon is missing or not recognised. */
        val DEFAULT = LEAF

        fun fromKey(key: String?): ProfileIcon = entries.firstOrNull { it.name == key } ?: DEFAULT

        /** The emoji with this Unicode code point, built from the number so none is typed into the source. */
        fun emoji(codePoint: Int): String = String(Character.toChars(codePoint))

        /**
         * Earlier versions of the app showed emoji, and profiles and sessions saved before the
         * drawn icons still hold one. This maps each old emoji to its replacement.
         */
        val LEGACY_EMOJI: Map<String, ProfileIcon> = mapOf(
            emoji(0x1F634) to MOON, // sleeping face
            emoji(0x1F4BC) to LAPTOP, // briefcase
            emoji(0x1F3C3) to DUMBBELL, // runner
            emoji(0x1F4DA) to BOOK, // books
            emoji(0x1F3AE) to GAMEPAD, // video game
            emoji(0x1F9D8) to LOTUS, // person in lotus position
            emoji(0x1F373) to POT, // cooking
            emoji(0x1F33F) to LEAF, // herb
            emoji(0x1F3A8) to PALETTE, // artist palette
            emoji(0x1F3A7) to HEADPHONES, // headphone
        )

        /** The icon for a value that may be an old emoji or a current key; null if it is neither. */
        fun fromSaved(value: String): ProfileIcon? =
            LEGACY_EMOJI[value] ?: entries.firstOrNull { it.name == value }
    }
}
