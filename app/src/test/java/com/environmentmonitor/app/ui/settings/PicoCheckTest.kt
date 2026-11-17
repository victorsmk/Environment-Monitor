package com.environmentmonitor.app.ui.settings

import com.environmentmonitor.app.data.Measurement
import com.environmentmonitor.app.sensors.PicoSample
import com.environmentmonitor.app.sensors.SensorSourceKind
import org.junit.Assert.assertEquals
import org.junit.Test

class PicoCheckTest {

    @Test
    fun `the summary lists every sensor`() {
        val sample = PicoSample(Measurement(21.46f, 48.2f, 312.5f, 17f), uptimeMs = 1)
        assertEquals(
            "Connected. Temperature 21.5 °C, humidity 48.2 %, light 313 lux, sound 17 level.",
            sample.summary(),
        )
    }

    @Test
    fun `a sensor with no value says so`() {
        val sample = PicoSample(Measurement(21f, null, 300f, null), uptimeMs = 1)
        assertEquals(
            "Connected. Temperature 21.0 °C, humidity no value, light 300 lux, sound no value.",
            sample.summary(),
        )
    }

    @Test
    fun `the saved source name is read back and falls back to demo data`() {
        assertEquals(SensorSourceKind.PICO, SensorSourceKind.fromName("PICO"))
        assertEquals(SensorSourceKind.DEMO, SensorSourceKind.fromName("DEMO"))
        assertEquals(SensorSourceKind.DEMO, SensorSourceKind.fromName(null))
        assertEquals(SensorSourceKind.DEMO, SensorSourceKind.fromName("something else"))
    }
}
