package com.environmentmonitor.app.data

import android.content.ContentValues
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the version 6 to 7 migration on a real SQLite database holding readings of the old kind
 * (all four values required), and checks that no reading is lost, that the values can now be
 * empty, and that the rebuilt table has exactly the shape Room itself creates for [Reading].
 */
@RunWith(AndroidJUnit4::class)
class ReadingsMigrationTest {

    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
            .name(null) // in memory
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL)")
                    // The readings table exactly as version 6 had it.
                    db.execSQL(
                        "CREATE TABLE readings (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "sessionId INTEGER NOT NULL, timestamp INTEGER NOT NULL, " +
                            "temperature REAL NOT NULL, humidity REAL NOT NULL, light REAL NOT NULL, sound REAL NOT NULL, " +
                            "FOREIGN KEY(sessionId) REFERENCES sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
                    )
                    db.execSQL("CREATE INDEX index_readings_sessionId_timestamp ON readings (sessionId, timestamp)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        helper = FrameworkSQLiteOpenHelperFactory().create(config)
        db = helper.writableDatabase
        db.execSQL("PRAGMA foreign_keys = ON")
    }

    @After
    fun tearDown() = helper.close()

    private fun addSession(name: String): Long =
        db.insert("sessions", 0, ContentValues().apply { put("name", name) })

    private fun addReading(session: Long, at: Long, t: Float, h: Float, l: Float, s: Float) {
        db.insert(
            "readings", 0,
            ContentValues().apply {
                put("sessionId", session); put("timestamp", at)
                put("temperature", t); put("humidity", h); put("light", l); put("sound", s)
            },
        )
    }

    private fun migrate() = MIGRATION_6_7.migrate(db)

    @Test
    fun everyExistingReadingKeepsItsValues() {
        val a = addSession("A")
        val b = addSession("B")
        addReading(a, 1_000, 17.5f, 50f, 3f, 8f)
        addReading(a, 2_000, 18f, 49f, 4f, 9f)
        addReading(b, 3_000, 21f, 40f, 300f, 30f)

        migrate()

        db.query("SELECT sessionId, timestamp, temperature, humidity, light, sound FROM readings ORDER BY id").use { c ->
            assertEquals(3, c.count)
            c.moveToNext()
            assertEquals(a, c.getLong(0)); assertEquals(1_000L, c.getLong(1))
            assertEquals(17.5f, c.getFloat(2), 0.001f); assertEquals(50f, c.getFloat(3), 0.001f)
            assertEquals(3f, c.getFloat(4), 0.001f); assertEquals(8f, c.getFloat(5), 0.001f)
            c.moveToNext(); c.moveToNext()
            assertEquals(b, c.getLong(0)); assertEquals(300f, c.getFloat(4), 0.001f)
        }
    }

    @Test
    fun valuesCanBeEmptyAfterwards() {
        val a = addSession("A")
        addReading(a, 1_000, 17.5f, 50f, 3f, 8f)
        migrate()

        db.insert(
            "readings", 0,
            ContentValues().apply {
                put("sessionId", a); put("timestamp", 2_000)
                put("temperature", 18f); putNull("humidity"); putNull("light"); put("sound", 9f)
            },
        )
        db.query("SELECT humidity, light, temperature FROM readings WHERE timestamp = 2000").use { c ->
            c.moveToNext()
            assertEquals(true, c.isNull(0)); assertEquals(true, c.isNull(1))
            assertEquals(18f, c.getFloat(2), 0.001f)
        }
    }

    @Test
    fun deletingASessionStillDeletesItsReadings() {
        val a = addSession("A")
        val b = addSession("B")
        addReading(a, 1_000, 17f, 50f, 3f, 8f)
        addReading(b, 2_000, 17f, 50f, 3f, 8f)
        migrate()

        db.execSQL("DELETE FROM sessions WHERE id = $a")
        db.query("SELECT COUNT(*) FROM readings").use { c ->
            c.moveToNext()
            assertEquals(1, c.getInt(0))
        }
    }

    @Test
    fun newReadingsKeepCountingFromTheOldIds() {
        val a = addSession("A")
        addReading(a, 1_000, 17f, 50f, 3f, 8f)
        addReading(a, 2_000, 17f, 50f, 3f, 8f)
        migrate()

        addReading(a, 3_000, 17f, 50f, 3f, 8f)
        db.query("SELECT MAX(id) FROM readings").use { c ->
            c.moveToNext()
            assertEquals(3, c.getInt(0))
        }
    }

    /** The migrated table must look exactly like the one Room builds from the [Reading] class. */
    @Test
    fun theRebuiltTableMatchesWhatRoomExpects() {
        migrate()
        val room = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val expected = room.openHelper.writableDatabase
            assertEquals(describe(expected), describe(db))
        } finally {
            room.close()
        }
    }

    private fun describe(database: SupportSQLiteDatabase): List<String> {
        val result = mutableListOf<String>()
        database.query("PRAGMA table_info(readings)").use { c ->
            while (c.moveToNext()) {
                result += "column ${c.getString(1)} ${c.getString(2)} notnull=${c.getInt(3)} pk=${c.getInt(5)}"
            }
        }
        database.query("PRAGMA foreign_key_list(readings)").use { c ->
            while (c.moveToNext()) {
                result += "fk ${c.getString(2)} ${c.getString(3)} -> ${c.getString(4)} " +
                    "update=${c.getString(5)} delete=${c.getString(6)}"
            }
        }
        database.query("PRAGMA index_list(readings)").use { c ->
            while (c.moveToNext()) result += "index ${c.getString(1)} unique=${c.getInt(2)}"
        }
        database.query("PRAGMA index_info(index_readings_sessionId_timestamp)").use { c ->
            while (c.moveToNext()) result += "index column ${c.getString(2)}"
        }
        return result
    }
}
