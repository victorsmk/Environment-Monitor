package com.environmentmonitor.app

import android.content.Context
import androidx.room.Room
import com.environmentmonitor.app.data.AppDatabase
import com.environmentmonitor.app.data.ProfileRepository
import com.environmentmonitor.app.data.ReadingRepository
import com.environmentmonitor.app.data.SessionRepository
import com.environmentmonitor.app.sensors.MockSensorSource
import com.environmentmonitor.app.sensors.SensorSource
import com.environmentmonitor.app.sensors.SessionRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map

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
    val readingRepository: ReadingRepository = ReadingRepository(database.readingDao())

    /** Fake sensors for now; the Pico W source replaces this in phase 2. */
    val sensorSource: SensorSource = MockSensorSource(appScope, settings.mockScenario)

    /** Stores a reading every 10 seconds while a session is running. */
    val sessionRecorder: SessionRecorder = SessionRecorder(
        scope = appScope,
        sensors = sensorSource,
        activeSessionId = sessionRepository.activeSession.map { it?.id },
        sink = readingRepository,
    )
}
