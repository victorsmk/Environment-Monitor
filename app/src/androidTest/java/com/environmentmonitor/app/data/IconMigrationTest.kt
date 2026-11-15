package com.environmentmonitor.app.data

import android.content.ContentValues
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the version 4 to 5 migration on a real SQLite database holding the old kind of data (emoji
 * icons), and checks nothing is lost and every icon is replaced. The migration only touches the
 * `icon` and `profileIcon` columns, so a database with just those tables is enough.
 */
@RunWith(AndroidJUnit4::class)
class IconMigrationTest {

    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
            .name(null) // in memory
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE profiles (id INTEGER PRIMARY KEY, name TEXT NOT NULL, icon TEXT NOT NULL)")
                    db.execSQL("CREATE TABLE sessions (id INTEGER PRIMARY KEY, name TEXT NOT NULL, profileIcon TEXT NOT NULL)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        helper = FrameworkSQLiteOpenHelperFactory().create(config)
        db = helper.writableDatabase
    }

    @After
    fun tearDown() = helper.close()

    private fun insertProfile(name: String, icon: String) {
        db.insert("profiles", 0, ContentValues().apply { put("name", name); put("icon", icon) })
    }

    private fun insertSession(name: String, icon: String) {
        db.insert("sessions", 0, ContentValues().apply { put("name", name); put("profileIcon", icon) })
    }

    private fun profileIcons(): Map<String, String> =
        db.query("SELECT name, icon FROM profiles").use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1)) }
        }

    private fun sessionIcons(): Map<String, String> =
        db.query("SELECT name, profileIcon FROM sessions").use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1)) }
        }

    @Test
    fun everyOldEmojiBecomesItsDrawnIcon() {
        ProfileIcon.LEGACY_EMOJI.forEach { (emoji, icon) ->
            insertProfile("profile ${icon.key}", emoji)
            insertSession("session ${icon.key}", emoji)
        }

        MIGRATION_4_5.migrate(db)

        ProfileIcon.LEGACY_EMOJI.values.forEach { icon ->
            assertEquals(icon.key, profileIcons()["profile ${icon.key}"])
            assertEquals(icon.key, sessionIcons()["session ${icon.key}"])
        }
    }

    @Test
    fun noRowsAreLostOrRenamed() {
        insertProfile("Sleep", ProfileIcon.LEGACY_EMOJI.keys.first())
        insertProfile("Mine", ProfileIcon.emoji(0x1F3A8))
        insertSession("Night", ProfileIcon.emoji(0x1F634))

        MIGRATION_4_5.migrate(db)

        assertEquals(setOf("Sleep", "Mine"), profileIcons().keys)
        assertEquals(setOf("Night"), sessionIcons().keys)
    }

    @Test
    fun anIconThatIsAlreadyAKeyIsKept() {
        insertProfile("Already new", "HEADPHONES")
        MIGRATION_4_5.migrate(db)
        assertEquals("HEADPHONES", profileIcons()["Already new"])
    }

    @Test
    fun anUnrecognisedIconBecomesTheDefault() {
        insertProfile("Odd", ProfileIcon.emoji(0x1F680)) // a rocket, which was never offered
        insertSession("Odd session", "")
        MIGRATION_4_5.migrate(db)
        assertEquals(ProfileIcon.DEFAULT.key, profileIcons()["Odd"])
        assertEquals(ProfileIcon.DEFAULT.key, sessionIcons()["Odd session"])
    }
}
