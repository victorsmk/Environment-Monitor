package com.environmentmonitor.app.sensors

import com.environmentmonitor.app.data.Measurement
import com.environmentmonitor.app.data.Presets
import com.environmentmonitor.app.data.ScoreLabel
import com.environmentmonitor.app.data.ScoringEngine
import com.environmentmonitor.app.data.SensorType
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockModelTest {

    private fun settle(scenario: MockScenario, seconds: Int = 120, from: Measurement? = null): Measurement {
        val model = if (from != null) MockModel(Random(7), from) else MockModel(Random(7))
        var last = model.next(scenario)
        repeat(seconds) { last = model.next(scenario) }
        return last
    }

    @Test
    fun `values settle close to the scenario target`() {
        val target = MockScenario.QUIET_OFFICE.target!!
        val result = settle(MockScenario.QUIET_OFFICE)
        SensorType.entries.forEach { sensor ->
            val allowed = sensor.tolerance * 0.2f
            assertEquals(sensor.label, target.value(sensor), result.value(sensor), allowed)
        }
    }

    @Test
    fun `values glide instead of jumping`() {
        val model = MockModel(Random(3), start = Measurement(17.5f, 50f, 3f, 8f))
        val first = model.next(MockScenario.HOT_AND_BRIGHT)
        // The target is 28 °C, but one second later the room has moved only a little.
        assertTrue("temperature jumped to ${first.temperature}", first.temperature < 19.5f)
    }

    @Test
    fun `values never leave the range of the sensor`() {
        val model = MockModel(Random(11))
        repeat(2_000) { i ->
            val scenario = MockScenario.entries[i / 100 % MockScenario.entries.size]
            val m = model.next(scenario)
            SensorType.entries.forEach { sensor ->
                val v = m.value(sensor)
                assertTrue("$sensor=$v", v in sensor.min..sensor.max)
            }
        }
    }

    @Test
    fun `the same seed gives the same readings`() {
        val a = MockModel(Random(5))
        val b = MockModel(Random(5))
        repeat(20) { assertEquals(a.next(MockScenario.NOISY_ROOM), b.next(MockScenario.NOISY_ROOM)) }
    }

    @Test
    fun `the changing scenario moves through different rooms`() {
        val rooms = MockScenario.entries.filter { it.target != null }.map { it.target }
        val seen = (0 until rooms.size * MockScenario.CHANGE_EVERY_TICKS step MockScenario.CHANGE_EVERY_TICKS)
            .map { MockScenario.CHANGING.targetAt(it) }
        assertEquals(rooms.toSet(), seen.toSet())
    }

    @Test
    fun `the comfortable bedroom is Good for Sleep and the hot room is not`() {
        val sleep = Presets.all[0].settings()
        val good = ScoringEngine.evaluate(sleep, settle(MockScenario.COMFORTABLE_BEDROOM))!!
        val bad = ScoringEngine.evaluate(sleep, settle(MockScenario.HOT_AND_BRIGHT))!!
        assertEquals(ScoreLabel.GOOD, good.label)
        assertEquals(ScoreLabel.POOR, bad.label)
    }

    @Test
    fun `the quiet office is Good for Office and the noisy room is not`() {
        val office = Presets.all[1].settings()
        val good = ScoringEngine.evaluate(office, settle(MockScenario.QUIET_OFFICE))!!
        val noisy = ScoringEngine.evaluate(office, settle(MockScenario.NOISY_ROOM))!!
        assertEquals(ScoreLabel.GOOD, good.label)
        assertTrue("noisy room scored ${noisy.overall}", noisy.label != ScoreLabel.GOOD)
    }
}
