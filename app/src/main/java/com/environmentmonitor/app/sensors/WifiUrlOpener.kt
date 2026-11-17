package com.environmentmonitor.app.sensors

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.URL
import java.net.URLConnection

/**
 * Opens connections over the phone's Wi-Fi network when it is connected to one, and over the
 * phone's normal choice otherwise.
 *
 * Why: when the phone joins the Pico's own network (which has no internet), Android may send app
 * traffic over mobile data instead, and the Pico would never be reached. Asking the Wi-Fi network
 * directly avoids that. When the phone is the hotspot there is no Wi-Fi network to ask for, and the
 * normal route already leads to the Pico.
 */
class WifiUrlOpener(context: Context) : (URL) -> URLConnection {

    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    override fun invoke(url: URL): URLConnection {
        val wifi = wifiNetwork()
        return wifi?.openConnection(url) ?: url.openConnection()
    }

    @Suppress("DEPRECATION") // allNetworks is the simplest way to look for the Wi-Fi network; still supported.
    private fun wifiNetwork() = connectivity?.allNetworks?.firstOrNull { network ->
        val capabilities = connectivity.getNetworkCapabilities(network)
        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }
}
