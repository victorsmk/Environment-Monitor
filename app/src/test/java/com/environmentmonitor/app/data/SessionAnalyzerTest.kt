package com.environmentmonitor.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionAnalyzerTest {

    // Sleep: temperature 16–19 High, humidity 40–60 Medium, light 0–10 High, sound 0–20 High.
    private val sleep = Presets.all[0].settings()
    // Sport has the sound sensor switched Off.
    private val sport = Presets.all[2].settings()

    private fun reading(
        at: Long,
        temperature: Float? = 17f,
        humidity: Float? = 50f,
        light: Float? = 5f,
        sound: Float? = 10f,
    ) = Reading(
        sessionId = 1,
        timestamp = at,
        temperature = temperature,
        humidity = humidity,
        light = light,
        sound = sound,
    )

    private fun analyze(settings: Map<SensorType, SensorSetting>, vararg readings: Reading) =
        SessionAnalyzer.analyze(settings, readings.toList())!!

    @Test
    fun `no readings means no analysis`() {
        assertNull(SessionAnalyzer.analyze(sleep, emptyList()))
    }

    @Test
    fun `no analysis when every sensor is Off`() {
        val allOff = sleep.mapValues { it.value.copy(importance = Importance.OFF) }
        assertNull(SessionAnalyzer.analyze(allOff, listOf(reading(0))))
    }

    @Test
    fun `a perfect session scores 100 and has no worst sensor`() {
        val result = analyze(sleep, reading(0), reading(10_000), reading(20_000))
        assertEquals(100, result.overall)
        assertEquals(ScoreLabel.GOOD, result.label)
        assertNull(result.worst)
        assertEquals(3, result.readingCount)
        assertTrue(result.sensors.all { it.inRangePercent == 100 })
    }

    @Test
    fun `only sensors that count get stats`() {
        val result = analyze(sport, reading(0, temperature = 18f, light = 500f))
        assertEquals(
            listOf(SensorType.TEMPERATURE, SensorType.HUMIDITY, SensorType.LIGHT),
            result.sensors.map { it.sensor },
        )
    }

    @Test
    fun `min, average and max come from the readings`() {
        val result = analyze(
            sleep,
            reading(0, temperature = 17f),
            reading(10_000, temperature = 18f),
            reading(20_000, temperature = 19f),
        )
        val temperature = result.sensors.first { it.sensor == SensorType.TEMPERATURE }
        assertEquals(17f, temperature.min, 0.001f)
        assertEquals(18f, temperature.average, 0.001f)
        assertEquals(19f, temperature.max, 0.001f)
    }

    @Test
    fun `time in range is split into in range, too low and too high`() {
        val result = analyze(
            sleep,
            reading(0, temperature = 17f),
            reading(10_000, temperature = 18f),
            reading(20_000, temperature = 22f),
            reading(30_000, temperature = 12f),
        )
        val temperature = result.sensors.first { it.sensor == SensorType.TEMPERATURE }
        assertEquals(0.5f, temperature.inRangeFraction, 0.001f)
        assertEquals(0.25f, temperature.tooHighFraction, 0.001f)
        assertEquals(0.25f, temperature.tooLowFraction, 0.001f)
        assertEquals(50, temperature.inRangePercent)
    }

    @Test
    fun `the overall score is the average of the per-reading scores`() {
        // A perfect reading scores 100; temperature at 24 °C scores 73 (see the scoring tests).
        val result = analyze(sleep, reading(0), reading(10_000, temperature = 24f))
        assertEquals(87, result.overall)
        assertEquals(ScoreLabel.GOOD, result.label)
    }

    @Test
    fun `score points follow the readings in time`() {
        val result = analyze(sleep, reading(5_000), reading(15_000, temperature = 24f))
        assertEquals(listOf(5_000L, 15_000L), result.scorePoints.map { it.timestamp })
        assertEquals(listOf(100f, 73f), result.scorePoints.map { it.value })
    }

    @Test
    fun `the worst sensor is the one that lost the most points on average`() {
        val result = analyze(
            sleep,
            reading(0, temperature = 25f),
            reading(10_000, temperature = 25f),
        )
        val worst = result.worst
        assertNotNull(worst)
        assertEquals(SensorType.TEMPERATURE, worst!!.sensor)
        assertTrue(worst.mostlyTooHigh)
    }

    @Test
    fun `a sensor that was mostly too low is reported as too low`() {
        val result = analyze(
            sleep,
            reading(0, temperature = 12f),
            reading(10_000, temperature = 12f),
            reading(20_000, temperature = 25f),
        )
        assertFalse(result.worst!!.mostlyTooHigh)
    }

    @Test
    fun `a tiny miss is not worth calling out`() {
        val result = analyze(sleep, reading(0, temperature = 19.02f), reading(10_000))
        assertNull(result.worst)
    }

    @Test
    fun `each sensor keeps its readings for the chart`() {
        val result = analyze(sleep, reading(0, light = 1f), reading(10_000, light = 2f))
        val light = result.sensors.first { it.sensor == SensorType.LIGHT }
        assertEquals(listOf(TimedValue(0, 1f), TimedValue(10_000, 2f)), light.points)
    }

    // --- readings with a missing sensor ---

    @Test
    fun `a sensor with no value in some readings is measured on the others`() {
        val result = analyze(
            sleep,
            reading(0),
            reading(10_000, light = null),
            reading(20_000, light = 5f),
            reading(30_000, light = null),
        )
        val light = result.sensors.first { it.sensor == SensorType.LIGHT }
        assertEquals(2, light.points.size)
        assertEquals(listOf(0L, 20_000L), light.points.map { it.timestamp })
        assertEquals(50, light.missingPercent)
        assertEquals(100, light.inRangePercent)
        // The other sensors have all four readings.
        assertEquals(0, result.sensors.first { it.sensor == SensorType.TEMPERATURE }.missingPercent)
        assertEquals(4, result.sensors.first { it.sensor == SensorType.TEMPERATURE }.points.size)
        assertEquals(4, result.readingCount)
    }

    @Test
    fun `the score of a reading leaves the missing sensor out`() {
        // Perfect apart from light, which is missing: still 100, not dragged down.
        val result = analyze(sleep, reading(0, light = null), reading(10_000, light = null))
        assertEquals(100, result.overall)
        assertEquals(listOf(100f, 100f), result.scorePoints.map { it.value })
    }

    @Test
    fun `a sensor that never had a value is listed and left out of the stats`() {
        val result = analyze(sleep, reading(0, sound = null), reading(10_000, sound = null))
        assertEquals(listOf(SensorType.SOUND), result.noDataSensors)
        assertFalse(result.sensors.any { it.sensor == SensorType.SOUND })
        assertEquals(100, result.overall)
    }

    @Test
    fun `a reading with every sensor that counts empty is skipped in the score`() {
        // Sport ignores sound, so the second reading has nothing to score.
        val result = analyze(
            sport,
            reading(0, temperature = 18f, light = 500f),
            reading(10_000, temperature = null, humidity = null, light = null, sound = 40f),
            reading(20_000, temperature = 18f, light = 500f),
        )
        assertEquals(listOf(0L, 20_000L), result.scorePoints.map { it.timestamp })
        assertEquals(3, result.readingCount)
        assertEquals(33, result.sensors.first { it.sensor == SensorType.TEMPERATURE }.missingPercent)
    }

    @Test
    fun `no analysis when nothing could be scored`() {
        assertNull(SessionAnalyzer.analyze(sport, listOf(reading(0, null, null, null, sound = 40f))))
    }
}
