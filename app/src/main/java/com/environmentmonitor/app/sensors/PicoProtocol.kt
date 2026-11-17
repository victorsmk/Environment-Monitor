package com.environmentmonitor.app.sensors

import com.environmentmonitor.app.data.Measurement
import org.json.JSONException
import org.json.JSONObject

/** A response from the Pico that cannot be used: wrong status, not JSON, missing fields, wrong version. */
class PicoException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** One answer of the Pico: the readings it holds, and how long it has been running. */
data class PicoSample(
    val measurement: Measurement,
    /** Milliseconds since the Pico booted. Going backwards between two answers means it restarted. */
    val uptimeMs: Long,
)

/** Reads the JSON the Pico sends. The format is described in docs/pico-protocol.md. */
object PicoProtocol {

    /** The only protocol version this app understands. */
    const val VERSION = 1

    /** The path on the Pico that answers with the latest readings. */
    const val DATA_PATH = "/data"

    /** The fields that must be present. A sensor's field may be null, but may not be missing. */
    private val required = listOf("uptime_ms", "temperature", "humidity", "light", "sound")

    /**
     * Turns a response body into a [PicoSample], or throws [PicoException] when it cannot be used.
     * Fields the app does not know (diagnostics, anything added later) are ignored.
     */
    fun parse(body: String): PicoSample {
        val json = try {
            JSONObject(body)
        } catch (e: JSONException) {
            throw PicoException("The answer is not valid JSON", e)
        }

        val version = json.opt("v")
        if (version !is Number || version.toInt() != VERSION) {
            throw PicoException("Unsupported protocol version: $version")
        }
        required.forEach { key ->
            if (!json.has(key)) throw PicoException("The answer has no \"$key\" field")
        }

        val uptime = json.opt("uptime_ms")
        if (uptime !is Number) throw PicoException("\"uptime_ms\" is not a number")

        return PicoSample(
            measurement = Measurement(
                temperature = number(json, "temperature"),
                humidity = number(json, "humidity"),
                light = number(json, "light"),
                sound = number(json, "sound"),
            ),
            uptimeMs = uptime.toLong(),
        )
    }

    /** A number, or null for JSON null. Anything else (text, true, a list) is an error. */
    private fun number(json: JSONObject, key: String): Float? {
        if (json.isNull(key)) return null
        val value = json.opt(key)
        if (value !is Number) throw PicoException("\"$key\" is not a number")
        val result = value.toFloat()
        if (result.isNaN() || result.isInfinite()) throw PicoException("\"$key\" is not a finite number")
        return result
    }
}
