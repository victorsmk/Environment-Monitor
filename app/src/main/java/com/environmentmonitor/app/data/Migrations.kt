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

/**
 * Version 6 to 7: the four sensor columns of a reading may now be empty (a failed sensor must not
 * throw the rest of the reading away). SQLite cannot drop NOT NULL from a column, so the table is
 * rebuilt: every existing reading is copied across unchanged.
 */
val MIGRATION_6_7: Migration = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        MIGRATION_6_7_SQL.forEach { db.execSQL(it) }
    }
}

/** The statements of [MIGRATION_6_7], in order. Kept apart so a test can run them on their own. */
internal val MIGRATION_6_7_SQL = listOf(
    "CREATE TABLE readings_new (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "sessionId INTEGER NOT NULL, " +
        "timestamp INTEGER NOT NULL, " +
        "temperature REAL, humidity REAL, light REAL, sound REAL, " +
        "FOREIGN KEY(sessionId) REFERENCES sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
    "INSERT INTO readings_new (id, sessionId, timestamp, temperature, humidity, light, sound) " +
        "SELECT id, sessionId, timestamp, temperature, humidity, light, sound FROM readings",
    "DROP TABLE readings",
    "ALTER TABLE readings_new RENAME TO readings",
    "CREATE INDEX index_readings_sessionId_timestamp ON readings (sessionId, timestamp)",
)
