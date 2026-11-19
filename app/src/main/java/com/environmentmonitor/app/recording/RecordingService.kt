package com.environmentmonitor.app.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.environmentmonitor.app.EnvironmentMonitorApplication
import com.environmentmonitor.app.MainActivity
import com.environmentmonitor.app.R
import com.environmentmonitor.app.data.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import com.environmentmonitor.app.data.Measurement
import com.environmentmonitor.app.data.ScoringEngine
import com.environmentmonitor.app.sensors.ConnectionState

/**
 * Keeps the app alive while a session records, with an ongoing notification. It does not record
 * anything itself: the recorder in AppContainer does, and stays running as long as the process does.
 * The service exists exactly while a session runs and stops itself when the session ends.
 */
class RecordingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watching: Job? = null
    private var current: Session? = null
    private val locks by lazy { RecordingLocks(this) }

    private val container get() = (application as EnvironmentMonitorApplication).container

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android gives only a few seconds to show the notification, so it comes first.
        createChannel()
        locks.hold()
        startForeground(
            NOTIFICATION_ID,
            notification(current, RecordingText.text(current, System.currentTimeMillis(), null, ConnectionState.CONNECTING)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
        if (intent?.action == ACTION_STOP) {
            val id = current?.id
            scope.launch {
                val running = id ?: container.sessionRepository.activeId()
                if (running != null) container.sessionRepository.stop(running)
            }
        }
        if (watching == null) {
            watching = scope.launch {
                var latest: Measurement? = null
                launch { container.sensorSource.measurements.collect { latest = it } }
                // The text is refreshed twice a minute and whenever the connection changes, so the
                // notification does not wake the phone for nothing.
                val ticker = flow { while (true) { emit(Unit); delay(REFRESH_MILLIS) } }
                combine(
                    container.sessionRepository.activeSession,
                    container.sensorSource.connection,
                    ticker,
                ) { session, connection, _ -> session to connection }.collect { (session, connection) ->
                    current = session
                    if (session == null) {
                        locks.release()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        locks.hold()
                        val score = latest
                            ?.takeIf { connection == ConnectionState.CONNECTED }
                            ?.let { ScoringEngine.evaluate(session.settings(), it) }
                        val text = RecordingText.text(session, System.currentTimeMillis(), score, connection)
                        getSystemService(NotificationManager::class.java)
                            .notify(NOTIFICATION_ID, notification(session, text))
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        locks.release()
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Recording", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while a session is recording in the background"
                setShowBadge(false)
            },
        )
    }

    private fun notification(session: Session?, text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(RecordingText.title(session))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(Notification.Action.Builder(null, "Stop session", stop).build())
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 1
        private const val REFRESH_MILLIS = 30_000L
        private const val ACTION_STOP = "com.environmentmonitor.app.STOP_SESSION"

        /** Starts the service. Must be called while the app is visible (Android forbids it otherwise). */
        fun start(context: Context) {
            context.startForegroundService(Intent(context, RecordingService::class.java))
        }
    }
}
