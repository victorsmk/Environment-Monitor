package com.environmentmonitor.app.sensors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PicoProtocolTest {

    private fun body(
        v: String = "1",
        uptime: String = "5321000",
        temperature: String = "22.1",
        humidity: String = "48.2",
        light: String = "312.5",
        sound: String = "17",
        extra: String = "",
    ) = """{"v":$v,"uptime_ms":$uptime,"temperature":$temperature,"humidity":$humidity,""" +
        """"light":$light,"sound":$sound$extra}"""

    private fun assertRejected(text: String, mention: String? = null) {
        try {
            PicoProtocol.parse(text)
            fail("expected the answer to be rejected: $text")
        } catch (e: PicoException) {
            if (mention != null) assertTrue(e.message, e.message!!.contains(mention))
        }
    }

    @Test
    fun `a good answer is read`() {
        val sample = PicoProtocol.parse(body())
        assertEquals(22.1f, sample.measurement.temperature!!, 0.001f)
        assertEquals(48.2f, sample.measurement.humidity!!, 0.001f)
        assertEquals(312.5f, sample.measurement.light!!, 0.001f)
        assertEquals(17f, sample.measurement.sound!!, 0.001f)
        assertEquals(5_321_000L, sample.uptimeMs)
    }

    @Test
    fun `whole numbers and decimals both work`() {
        val sample = PicoProtocol.parse(body(temperature = "22", sound = "17.0"))
        assertEquals(22f, sample.measurement.temperature!!, 0.001f)
        assertEquals(17f, sample.measurement.sound!!, 0.001f)
    }

    @Test
    fun `null means that sensor has no value`() {
        val sample = PicoProtocol.parse(body(light = "null", humidity = "null"))
        assertNull(sample.measurement.light)
        assertNull(sample.measurement.humidity)
        assertEquals(22.1f, sample.measurement.temperature!!, 0.001f)
        assertEquals(17f, sample.measurement.sound!!, 0.001f)
    }

    @Test
    fun `every sensor can be null at once`() {
        val sample = PicoProtocol.parse(body(temperature = "null", humidity = "null", light = "null", sound = "null"))
        assertTrue(sample.measurement.isEmpty)
    }

    @Test
    fun `diagnostic and unknown fields are ignored`() {
        val sample = PicoProtocol.parse(
            body(extra = ""","pressure_hpa":1013.2,"temperature_dht":21.4,"noise_rms":143.7,"something_new":"x""""),
        )
        assertEquals(22.1f, sample.measurement.temperature!!, 0.001f)
    }

    @Test
    fun `an unknown version is rejected`() {
        assertRejected(body(v = "2"), "version")
        assertRejected(body(v = "0"), "version")
        assertRejected(body(v = "\"1\""), "version")
    }

    @Test
    fun `a missing version is rejected`() {
        assertRejected("""{"uptime_ms":1,"temperature":1,"humidity":1,"light":1,"sound":1}""", "version")
    }

    @Test
    fun `a missing sensor field is rejected`() {
        // Missing is not the same as null: the Pico must always say something about every sensor.
        assertRejected("""{"v":1,"uptime_ms":1,"temperature":1,"humidity":1,"light":1}""", "sound")
        assertRejected("""{"v":1,"temperature":1,"humidity":1,"light":1,"sound":1}""", "uptime_ms")
    }

    @Test
    fun `text where a number belongs is rejected`() {
        assertRejected(body(temperature = "\"warm\""), "temperature")
        assertRejected(body(sound = "true"), "sound")
        assertRejected(body(uptime = "\"soon\""), "uptime_ms")
    }

    @Test
    fun `text that is not JSON is rejected`() {
        assertRejected("<html>router login</html>", "JSON")
        assertRejected("", "JSON")
    }

    @Test
    fun `JSON that is not an object is rejected`() {
        assertRejected("[1,2,3]", "JSON")
    }
}
