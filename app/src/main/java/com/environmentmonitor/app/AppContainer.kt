package com.environmentmonitor.app

import android.content.Context
import androidx.room.Room
import com.environmentmonitor.app.data.AppDatabase
import com.environmentmonitor.app.data.ProfileRepository
import com.environmentmonitor.app.data.SessionRepository

/** Hand-wired dependencies, created once per process. */
class AppContainer(context: Context) {

    private val database: AppDatabase = Room
        .databaseBuilder(context, AppDatabase::class.java, "environment-monitor.db")
        // Development only: the schema still changes between steps. Remove before release.
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

    val profileRepository: ProfileRepository = ProfileRepository(database.profileDao())
    val sessionRepository: SessionRepository = SessionRepository(database.sessionDao())
}
