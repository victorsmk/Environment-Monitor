package com.environmentmonitor.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreSharesTest {

    private fun shares(vararg pairs: Pair<SensorType, Importance>) = ScoreShares.of(pairs.toMap())

    @Test
    fun `the sleep preset gives three sensors 27 percent and humidity 18`() {
        val result = ScoreShares.ofSettings(Presets.all[0].settings())
        assertEquals(27, result[SensorType.TEMPERATURE])
        assertEquals(18, result[SensorType.HUMIDITY])
        assertEquals(27, result[SensorType.LIGHT])
        assertEquals(27, result[SensorType.SOUND])
    }

    @Test
    fun `the office preset gives 30 and 20 percent`() {
        val result = ScoreShares.ofSettings(Presets.all[1].settings())
        assertEquals(30, result[SensorType.TEMPERATURE])
        assertEquals(20, result[SensorType.HUMIDITY])
        assertEquals(30, result[SensorType.LIGHT])
        assertEquals(20, result[SensorType.SOUND])
    }

    @Test
    fun `a sensor that is ignored has no share and the others take its place`() {
        val sport = ScoreShares.ofSettings(Presets.all[2].settings())
        assertFalse(SensorType.SOUND in sport)
        // Temperature Critical (3), humidity Minor (1), light Minor (1): 3, 1 and 1 out of 5.
        assertEquals(60, sport[SensorType.TEMPERATURE])
        assertEquals(20, sport[SensorType.HUMIDITY])
        assertEquals(20, sport[SensorType.LIGHT])
    }

    @Test
    fun `one active sensor takes the whole score`() {
        val result = shares(
            SensorType.LIGHT to Importance.LOW,
            SensorType.SOUND to Importance.OFF,
        )
        assertEquals(mapOf(SensorType.LIGHT to 100), result)
    }

    @Test
    fun `equally important sensors always show equal shares`() {
        val result = shares(
            SensorType.TEMPERATURE to Importance.MEDIUM,
            SensorType.HUMIDITY to Importance.MEDIUM,
            SensorType.LIGHT to Importance.MEDIUM,
        )
        assertEquals(setOf(33), result.values.toSet())
    }

    @Test
    fun `a more important sensor always has a bigger share`() {
        val result = shares(
            SensorType.TEMPERATURE to Importance.LOW,
            SensorType.HUMIDITY to Importance.MEDIUM,
            SensorType.LIGHT to Importance.HIGH,
        )
        assertTrue(result.getValue(SensorType.TEMPERATURE) < result.getValue(SensorType.HUMIDITY))
        assertTrue(result.getValue(SensorType.HUMIDITY) < result.getValue(SensorType.LIGHT))
    }

    @Test
    fun `no active sensor means no shares`() {
        assertEquals(emptyMap<SensorType, Int>(), shares(SensorType.LIGHT to Importance.OFF))
        assertEquals(emptyMap<SensorType, Int>(), ScoreShares.of(emptyMap()))
    }

    @Test
    fun `shares are close to a hundred in total`() {
        SensorType.entries.indices.forEach { a ->
            Importance.entries.forEach { importance ->
                val map = SensorType.entries.associateWith { if (it.ordinal == a) importance else Importance.MEDIUM }
                val total = ScoreShares.of(map).values.sum()
                assertTrue("total=$total", total in 98..102)
            }
        }
    }
}

class ImportanceNamesTest {

    @Test
    fun `the levels have names that say what they do`() {
        assertEquals(
            listOf("Ignore", "Minor", "Important", "Critical"),
            Importance.entries.map { it.label },
        )
    }

    @Test
    fun `renaming the labels did not change what is saved`() {
        // The database stores these enum names, so they must stay exactly as they were.
        assertEquals(listOf("OFF", "LOW", "MEDIUM", "HIGH"), Importance.entries.map { it.name })
    }

    @Test
    fun `the weights are unchanged`() {
        assertEquals(listOf(0, 1, 2, 3), Importance.entries.map { it.weight })
    }

    @Test
    fun `every level has its own explanation`() {
        val meanings = Importance.entries.map { it.meaning }
        assertTrue(meanings.all { it.isNotBlank() })
        assertEquals(meanings.size, meanings.toSet().size)
        assertTrue(Importance.OFF.meaning.contains("won't affect"))
    }
}
