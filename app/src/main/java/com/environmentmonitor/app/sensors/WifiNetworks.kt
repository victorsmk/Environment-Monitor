package com.environmentmonitor.app.sensors

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

/**
 * Finds the Wi-Fi network the phone is connected to, if any.
 *
 * Why it matters: when the phone joins the Pico's own network (which has no internet), Android may
 * send app traffic over mobile data instead, and the Pico would never be reached. Steering requests
 * onto the Wi-Fi network avoids that. When the phone is the hotspot there is no Wi-Fi network to
 * find, and the normal route already leads to the Pico.
 */
class WifiNetworks(context: Context) {

    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    /** The connected Wi-Fi network, or null (not on Wi-Fi, or the phone is the hotspot). */
    @Suppress("DEPRECATION") // allNetworks is the simplest way to look for it; still supported.
    fun current(): Network? = connectivity?.allNetworks?.firstOrNull { network ->
        connectivity.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }
}
