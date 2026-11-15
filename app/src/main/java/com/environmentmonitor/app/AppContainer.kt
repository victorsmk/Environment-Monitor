package com.environmentmonitor.app

import android.content.Context
import androidx.room.Room
import com.environmentmonitor.app.data.AppDatabase
import com.environmentmonitor.app.data.ProfileRepository
import com.environmentmonitor.app.data.SessionRepository
import com.environmentmonitor.app.sensors.MockSensorSource
import com.environmentmonitor.app.sensors.SensorSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hand-wired dependencies, created once per process. */
class AppContainer(context: Context) {

    /** Lives as long as the process; for work that must not stop when a screen closes. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database: AppDatabase = Room
        .databaseBuilder(context, AppDatabase::class.java, "environment-monitor.db")
        // Development only: the schema still changes between steps. Remove before release.
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

    val settings: AppSettings = AppSettings(context)

    val profileRepository: ProfileRepository = ProfileRepository(database.profileDao())
    val sessionRepository: SessionRepository = SessionRepository(database.sessionDao())

    /** Fake sensors for now; the Pico W source replaces this in phase 2. */
    val sensorSource: SensorSource = MockSensorSource(appScope, settings.mockScenario)
}
