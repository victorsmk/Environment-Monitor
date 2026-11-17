package com.environmentmonitor.app.sensors

import android.content.Context
import java.net.URL
import java.net.URLConnection

/**
 * Opens connections over the phone's Wi-Fi network when it is connected to one, and over the
 * phone's normal choice otherwise. See [WifiNetworks] for why.
 */
class WifiUrlOpener(private val wifi: WifiNetworks) : (URL) -> URLConnection {

    constructor(context: Context) : this(WifiNetworks(context))

    override fun invoke(url: URL): URLConnection = wifi.current()?.openConnection(url) ?: url.openConnection()
}
