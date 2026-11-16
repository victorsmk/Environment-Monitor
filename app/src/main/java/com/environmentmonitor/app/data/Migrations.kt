package com.environmentmonitor.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 4 to 5: profile and session icons stop being emoji and become icon keys ("MOON", "LEAF",
 * ...). Nothing is deleted: every saved profile and session keeps its data and gets the drawn icon
 * that replaces its old emoji. A value that is neither an old emoji nor a current key becomes the
 * default icon.
 */
val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE profiles SET icon = ${iconCase("icon")}")
        db.execSQL("UPDATE sessions SET profileIcon = ${iconCase("profileIcon")}")
    }
}

/** The SQL that turns the old value in [column] into an icon key. Built from [ProfileIcon], so the two cannot drift apart. */
internal fun iconCase(column: String): String {
    val legacy = ProfileIcon.LEGACY_EMOJI.entries.joinToString(" ") { (emoji, icon) ->
        "WHEN '$emoji' THEN '${icon.key}'"
    }
    val keys = ProfileIcon.entries.joinToString(", ") { "'${it.key}'" }
    return "CASE $column $legacy ELSE CASE WHEN $column IN ($keys) THEN $column ELSE '${ProfileIcon.DEFAULT.key}' END END"
}

/** Version 5 to 6: sessions gain an optional average score. Existing sessions get it filled in when History is opened. */
val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sessions ADD COLUMN averageScore INTEGER")
    }
}
