package com.environmentmonitor.app.data

import com.environmentmonitor.app.ui.advice
import com.environmentmonitor.app.ui.missingNote
import com.environmentmonitor.app.ui.problemText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoringEngineTest {

    // Sleep: temperature 16–19 High, humidity 40–60 Medium, light 0–10 High, sound 0–20 High.
    private val sleep = Presets.all[0].settings()
    // Sport: temperature 16–20 High, humidity 40–60 Low, light 200–1000 Low, sound Off.
    private val sport = Presets.all[2].settings()

    private val perfectSleep = Measurement(temperature = 17f, humidity = 50f, light = 5f, sound = 10f)

    private fun evaluate(settings: Map<SensorType, SensorSetting>, m: Measurement) =
        ScoringEngine.evaluate(settings, m)!!

    // --- one sensor ---

    @Test
    fun `inside the range scores 100`() {
        val setting = SensorSetting(16f, 19f, Importance.HIGH)
        assertEquals(100f, ScoringEngine.sensorScore(SensorType.TEMPERATURE, setting, 17.5f), 0.001f)
    }

    @Test
    fun `the edges of the range still score 100`() {
        val setting = SensorSetting(16f, 19f, Importance.HIGH)
        assertEquals(100f, ScoringEngine.sensorScore(SensorType.TEMPERATURE, setting, 16f), 0.001f)
        assertEquals(100f, ScoringEngine.sensorScore(SensorType.TEMPERATURE, setting, 19f), 0.001f)
    }

    @Test
    fun `the score falls in a straight line outside the range`() {
        val setting = SensorSetting(16f, 19f, Importance.HIGH)
        // Tolerance is 5 °C: 2.5 above the range is halfway to zero, on either side.
        assertEquals(50f, ScoringEngine.sensorScore(SensorType.TEMPERATURE, setting, 21.5f), 0.001f)
        assertEquals(50f, ScoringEngine.sensorScore(SensorType.TEMPERATURE, setting, 13.5f), 0.001f)
    }

    @Test
    fun `at or beyond the tolerance a sensor scores 0`() {
        val setting = SensorSetting(16f, 19f, Importance.HIGH)
        assertEquals(0f, ScoringEngine.sensorScore(SensorType.TEMPERATURE, setting, 24f), 0.001f)
        assertEquals(0f, ScoringEngine.sensorScore(SensorType.TEMPERATURE, setting, 40f), 0.001f)
    }

    @Test
    fun `status says which side of the range the value is on`() {
        val setting = SensorSetting(16f, 19f, Importance.HIGH)
        assertEquals(RangeStatus.TOO_LOW, ScoringEngine.status(setting, 15.9f))
        assertEquals(RangeStatus.IN_RANGE, ScoringEngine.status(setting, 16f))
        assertEquals(RangeStatus.IN_RANGE, ScoringEngine.status(setting, 19f))
        assertEquals(RangeStatus.TOO_HIGH, ScoringEngine.status(setting, 19.1f))
    }

    // --- overall score ---

    @Test
    fun `a perfect room scores 100 and is Good`() {
        val result = evaluate(sleep, perfectSleep)
        assertEquals(100, result.overall)
        assertEquals(ScoreLabel.GOOD, result.label)
        assertNull(result.worst)
        assertEquals("Everything is in the ideal range", result.advice())
    }

    @Test
    fun `a sensor with high importance costs more than one with low importance`() {
        val settings = sleep + (SensorType.HUMIDITY to SensorSetting(40f, 60f, Importance.LOW))
        // Same bad humidity (zero score) under Medium and under Low importance.
        val bad = perfectSleep.copy(humidity = 80f)
        val medium = evaluate(sleep, bad).overall
        val low = evaluate(settings, bad).overall
        assertTrue("low=$low medium=$medium", low > medium)
    }

    @Test
    fun `overall is the weighted average of the sensor scores`() {
        // Temperature scores 0 with weight 3 out of a total weight of 11: 100 - 300/11 = 72.7.
        val result = evaluate(sleep, perfectSleep.copy(temperature = 24f))
        assertEquals(73, result.overall)
        assertEquals(ScoreLabel.FAIR, result.label)
    }

    @Test
    fun `a sensor that is Off is ignored completely`() {
        val result = evaluate(sport, Measurement(temperature = 18f, humidity = 50f, light = 500f, sound = 100f))
        assertEquals(100, result.overall)
        assertEquals(listOf(SensorType.TEMPERATURE, SensorType.HUMIDITY, SensorType.LIGHT), result.sensors.map { it.sensor })
    }

    @Test
    fun `with a single active sensor the overall equals that sensor`() {
        val onlyLight = SensorType.entries.associateWith { SensorSetting(0f, 10f, Importance.OFF) } +
            (SensorType.LIGHT to SensorSetting(300f, 500f, Importance.MEDIUM))
        // 75 lux below the range, tolerance 150: score 50.
        val result = evaluate(onlyLight, Measurement(20f, 50f, 225f, 10f))
        assertEquals(50, result.overall)
        assertEquals(ScoreLabel.FAIR, result.label)
    }

    @Test
    fun `when every sensor is Off there is nothing to score`() {
        val allOff = sleep.mapValues { it.value.copy(importance = Importance.OFF) }
        assertNull(ScoringEngine.evaluate(allOff, perfectSleep))
    }

    @Test
    fun `an awful room scores 0 and is Poor`() {
        val result = evaluate(sleep, Measurement(temperature = 40f, humidity = 100f, light = 1000f, sound = 100f))
        assertEquals(0, result.overall)
        assertEquals(ScoreLabel.POOR, result.label)
    }

    // --- labels ---

    @Test
    fun `label thresholds`() {
        assertEquals(ScoreLabel.GOOD, ScoreLabel.of(100))
        assertEquals(ScoreLabel.GOOD, ScoreLabel.of(80))
        assertEquals(ScoreLabel.FAIR, ScoreLabel.of(79))
        assertEquals(ScoreLabel.FAIR, ScoreLabel.of(50))
        assertEquals(ScoreLabel.POOR, ScoreLabel.of(49))
        assertEquals(ScoreLabel.POOR, ScoreLabel.of(0))
    }

    // --- advice ---

    @Test
    fun `the worst sensor is the one that costs the most points`() {
        // Humidity 80: score 0, weight 2 -> loses 18.2 points.
        // Sound 30: score 60, weight 3 -> loses 10.9 points.
        val result = evaluate(sleep, perfectSleep.copy(humidity = 80f, sound = 30f))
        assertEquals(SensorType.HUMIDITY, result.worst!!.sensor)
    }

    @Test
    fun `a small miss on an important sensor can lose to a big miss on a minor one`() {
        val settings = sleep + (SensorType.HUMIDITY to SensorSetting(40f, 60f, Importance.LOW))
        // Light 10 lux over (score 93, weight 3 -> 1.9 lost); humidity 80 (score 0, weight 1 -> 7.7 lost).
        val result = evaluate(settings, perfectSleep.copy(light = 20f, humidity = 80f))
        assertEquals(SensorType.HUMIDITY, result.worst!!.sensor)
    }

    @Test
    fun `advice names the sensor, the direction and the ideal range`() {
        val tooHigh = evaluate(sleep, perfectSleep.copy(temperature = 21f))
        assertEquals("Temperature is too high: 21 °C, ideal 16–19 °C", tooHigh.advice())
        val tooLow = evaluate(sleep, perfectSleep.copy(temperature = 14.5f))
        assertEquals("Temperature is too low: 14.5 °C, ideal 16–19 °C", tooLow.advice())
        assertNotNull(tooLow.worst!!.problemText())
    }

    @Test
    fun `every preset is Good in its own ideal middle`() {
        Presets.all.forEach { preset ->
            val middle = Measurement(
                temperature = mid(preset.temperature),
                humidity = mid(preset.humidity),
                light = mid(preset.light),
                sound = mid(preset.sound),
            )
            assertEquals(preset.name, 100, evaluate(preset.settings(), middle).overall)
        }
    }

    private fun mid(setting: SensorSetting) = (setting.idealMin + setting.idealMax) / 2f

    // --- a sensor with no value ---

    @Test
    fun `a sensor with no value is left out and the others fill the whole score`() {
        // Sleep weights: temperature 3, humidity 2, light 3, sound 3. Light has no value.
        val result = evaluate(sleep, perfectSleep.copy(light = null))
        assertEquals(listOf(SensorType.TEMPERATURE, SensorType.HUMIDITY, SensorType.SOUND), result.sensors.map { it.sensor })
        assertEquals(listOf(SensorType.LIGHT), result.missing)
        assertEquals(100, result.overall)
    }

    @Test
    fun `the weights of the remaining sensors are renormalised`() {
        // Without light the total weight is 8. Sound (weight 3) at 0 costs 3/8 of the score: 37.5 points.
        val result = evaluate(sleep, perfectSleep.copy(light = null, sound = 100f))
        assertEquals(37.5f, result.sensors.first { it.sensor == SensorType.SOUND }.pointsLost, 0.01f)
        assertEquals(63, result.overall) // 62.5 rounds up
    }

    @Test
    fun `a missing sensor is not blamed for lost points`() {
        val result = evaluate(sleep, perfectSleep.copy(temperature = null))
        assertNull(result.worst)
        assertEquals(listOf(SensorType.TEMPERATURE), result.missing)
    }

    @Test
    fun `several sensors can be missing at once`() {
        val result = evaluate(sleep, Measurement(temperature = null, humidity = 50f, light = null, sound = 10f))
        assertEquals(listOf(SensorType.TEMPERATURE, SensorType.LIGHT), result.missing)
        assertEquals(2, result.sensors.size)
        assertEquals(100, result.overall)
    }

    @Test
    fun `no score when every sensor that counts has no value`() {
        // Sport ignores sound, so a lone sound value is not enough to judge.
        val onlySound = Measurement(temperature = null, humidity = null, light = null, sound = 40f)
        assertNull(ScoringEngine.evaluate(sport, onlySound))
    }

    @Test
    fun `no score for an empty measurement`() {
        assertNull(ScoringEngine.evaluate(sleep, Measurement(null, null, null, null)))
    }

    @Test
    fun `an ignored sensor with no value is not reported as missing`() {
        // Sport ignores sound.
        val result = evaluate(sport, Measurement(temperature = 18f, humidity = 50f, light = 500f, sound = null))
        assertTrue(result.missing.isEmpty())
    }

    @Test
    fun `advice says what was measured when something is missing`() {
        val result = evaluate(sleep, perfectSleep.copy(light = null))
        assertEquals("Everything measured is in the ideal range", result.advice())
    }

    @Test
    fun `the note names the sensors the score is without`() {
        assertEquals(
            "Score without light: no data from that sensor",
            evaluate(sleep, perfectSleep.copy(light = null)).missingNote(),
        )
        assertEquals(
            "Score without temperature and light: no data from those sensors",
            evaluate(sleep, perfectSleep.copy(temperature = null, light = null)).missingNote(),
        )
        assertNull(evaluate(sleep, perfectSleep).missingNote())
    }

    @Test
    fun `measurement helpers`() {
        assertTrue(Measurement(null, null, null, null).isEmpty)
        assertFalse(perfectSleep.isEmpty)
        val without = perfectSleep.without(setOf(SensorType.HUMIDITY, SensorType.SOUND))
        assertNull(without.humidity)
        assertNull(without.sound)
        assertEquals(17f, without.temperature)
    }
}
