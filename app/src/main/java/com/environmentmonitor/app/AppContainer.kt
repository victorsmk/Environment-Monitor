package com.environmentmonitor.app

import android.content.Context
import androidx.room.Room
import com.environmentmonitor.app.data.AppDatabase
import com.environmentmonitor.app.data.MIGRATION_4_5
import com.environmentmonitor.app.data.ProfileRepository
import com.environmentmonitor.app.data.ReadingRepository
import com.environmentmonitor.app.data.SessionRepository
import com.environmentmonitor.app.sensors.MockSensorSource
import com.environmentmonitor.app.sensors.RecordingTarget
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
        // Version 4 to 5 keeps all data (emoji icons become drawn icon keys).
        .addMigrations(MIGRATION_4_5)
        // Development only: older versions have no migration and are wiped. Remove before release.
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

    val settings: AppSettings = AppSettings(context)

    val profileRepository: ProfileRepository = ProfileRepository(database.profileDao())
    val sessionRepository: SessionRepository = SessionRepository(database.sessionDao())
    val readingRepository: ReadingRepository = ReadingRepository(database.readingDao())

    /** Fake sensors for now; the Pico W source replaces this in phase 2. */
    val sensorSource: SensorSource = MockSensorSource(appScope, settings.mockScenario)

    /** Stores readings at each running session's own interval. */
    val sessionRecorder: SessionRecorder = SessionRecorder(
        scope = appScope,
        sensors = sensorSource,
        activeTarget = sessionRepository.activeSession.map { session ->
            session?.let { RecordingTarget(it.id, it.recordingIntervalSeconds * 1_000L) }
        },
        sink = readingRepository,
    )
}
