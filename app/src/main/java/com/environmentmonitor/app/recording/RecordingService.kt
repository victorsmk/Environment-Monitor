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
import kotlinx.coroutines.launch

/**
 * Keeps the app alive while a session records, with an ongoing notification. It does not record
 * anything itself: the recorder in AppContainer does, and stays running as long as the process does.
 * The service exists exactly while a session runs and stops itself when the session ends.
 */
class RecordingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watching: Job? = null
    private var current: Session? = null

    private val container get() = (application as EnvironmentMonitorApplication).container

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android gives only a few seconds to show the notification, so it comes first.
        createChannel()
        startForeground(
            NOTIFICATION_ID,
            notification(current),
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
                container.sessionRepository.activeSession.collect { session ->
                    current = session
                    if (session == null) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        getSystemService(NotificationManager::class.java)
                            .notify(NOTIFICATION_ID, notification(session))
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
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

    private fun notification(session: Session?): Notification {
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
            .setContentText(RecordingText.text(session))
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
        private const val ACTION_STOP = "com.environmentmonitor.app.STOP_SESSION"

        /** Starts the service. Must be called while the app is visible (Android forbids it otherwise). */
        fun start(context: Context) {
            context.startForegroundService(Intent(context, RecordingService::class.java))
        }
    }
}
