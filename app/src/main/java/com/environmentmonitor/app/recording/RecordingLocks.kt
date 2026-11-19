package com.environmentmonitor.app.recording

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager

/**
 * Keeps the CPU and the Wi-Fi radio awake while a session records, so polling the Pico does not
 * stop when the screen is off. The wake lock has a timeout that [hold] renews, so if the service
 * ever dies without releasing it, the phone is not kept awake for long.
 */
class RecordingLocks(context: Context) {

    private val wake = context.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EnvironmentMonitor:recording")
        .apply { setReferenceCounted(false) }

    @Suppress("DEPRECATION")
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        .createWifiLock(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            },
            "EnvironmentMonitor:recording",
        )
        .apply { setReferenceCounted(false) }

    /** Takes the locks, or renews the wake lock's timeout when they are already held. */
    fun hold() {
        wake.acquire(WAKE_TIMEOUT_MILLIS)
        if (!wifi.isHeld) wifi.acquire()
    }

    fun release() {
        if (wake.isHeld) wake.release()
        if (wifi.isHeld) wifi.release()
    }

    val isHeld: Boolean get() = wake.isHeld && wifi.isHeld

    private companion object {
        /** Renewed every 30 s by the service, so this only matters if the service dies. */
        const val WAKE_TIMEOUT_MILLIS = 10 * 60 * 1000L
    }
}
