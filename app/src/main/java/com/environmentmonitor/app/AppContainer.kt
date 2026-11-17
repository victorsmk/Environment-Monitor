package com.environmentmonitor.app

import android.content.Context
import androidx.room.Room
import com.environmentmonitor.app.data.AppDatabase
import com.environmentmonitor.app.data.MIGRATION_4_5
import com.environmentmonitor.app.data.MIGRATION_5_6
import com.environmentmonitor.app.data.MIGRATION_6_7
import com.environmentmonitor.app.data.ProfileRepository
import com.environmentmonitor.app.data.ReadingRepository
import com.environmentmonitor.app.data.SessionRepository
import com.environmentmonitor.app.sensors.HttpPicoTransport
import com.environmentmonitor.app.sensors.MockSensorSource
import com.environmentmonitor.app.sensors.PicoAddressFinder
import com.environmentmonitor.app.sensors.PicoDiscovery
import com.environmentmonitor.app.sensors.PicoSensorSource
import com.environmentmonitor.app.sensors.PicoTransport
import com.environmentmonitor.app.sensors.SensorSourceKind
import com.environmentmonitor.app.sensors.SwitchableSensorSource
import com.environmentmonitor.app.sensors.UdpPicoDiscovery
import com.environmentmonitor.app.sensors.WifiNetworks
import com.environmentmonitor.app.sensors.WifiUrlOpener
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
        .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
        // Development only: older versions have no migration and are wiped. Remove before release.
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

    val settings: AppSettings = AppSettings(context)

    val profileRepository: ProfileRepository = ProfileRepository(database.profileDao())
    val sessionRepository: SessionRepository = SessionRepository(database.sessionDao())
    val readingRepository: ReadingRepository = ReadingRepository(database.readingDao())

    private val wifi = WifiNetworks(context)

    /** How the app talks to the Pico: plain HTTP, over the Wi-Fi network when there is one. */
    val picoTransport: PicoTransport = HttpPicoTransport(open = WifiUrlOpener(wifi))

    /** Looks for the Pico on the local network. */
    val picoDiscovery: PicoDiscovery = UdpPicoDiscovery(bind = { socket -> wifi.current()?.bindSocket(socket) })

    private val demoSource = MockSensorSource(
        appScope,
        settings.mockScenario,
        settings.mockOnline,
        settings.mockMissingSensors,
    )

    private val picoSource = PicoSensorSource(appScope, settings.picoAddress, picoTransport)

    /** Whichever source is chosen in Settings; the rest of the app only talks to this. */
    val sensorSource: SensorSource = SwitchableSensorSource(
        scope = appScope,
        kind = settings.sourceKind,
        sources = mapOf(SensorSourceKind.DEMO to demoSource, SensorSourceKind.PICO to picoSource),
    )

    /** Follows the Pico when its address changes (a hotspot hands out a new one each time). */
    private val picoAddressFinder = PicoAddressFinder(
        scope = appScope,
        kind = settings.sourceKind,
        connection = sensorSource.connection,
        address = settings.picoAddress,
        discovery = picoDiscovery,
        onFound = settings::setPicoAddress,
    ).also { it.start() }

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
