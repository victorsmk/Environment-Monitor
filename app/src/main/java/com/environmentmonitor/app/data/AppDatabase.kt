package com.environmentmonitor.app.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Profile::class, Session::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun sessionDao(): SessionDao
}
