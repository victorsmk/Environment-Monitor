package com.environmentmonitor.app.sensors

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Fetches the text of a web address. A small interface so the polling can be tested without a network. */
fun interface PicoTransport {
    /** The response body of `GET [url]`. Throws when there is no answer in time or the status is not 200. */
    suspend fun get(url: String): String
}

/**
 * Plain HTTP with the platform's own client, so the app needs no extra library.
 *
 * [open] decides which network the request travels over. The default is the phone's normal
 * choice; on Android it is replaced by one that prefers the Wi-Fi network (see [WifiUrlOpener]).
 */
class HttpPicoTransport(
    private val timeoutMillis: Int = TIMEOUT_MILLIS,
    private val open: (URL) -> URLConnection = { it.openConnection() },
) : PicoTransport {

    override suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val connection = try {
            open(URL(url)) as HttpURLConnection
        } catch (e: java.net.MalformedURLException) {
            throw PicoException("Not a valid address: $url", e)
        }
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = timeoutMillis
            connection.readTimeout = timeoutMillis
            connection.useCaches = false
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) throw PicoException("The Pico answered with status $status")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        /** How long to wait for the Pico before giving up on one poll. */
        const val TIMEOUT_MILLIS = 2_000
    }
}
