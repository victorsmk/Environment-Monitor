package com.environmentmonitor.app.data

import com.environmentmonitor.app.data.Importance.HIGH
import com.environmentmonitor.app.data.Importance.LOW
import com.environmentmonitor.app.data.Importance.MEDIUM
import com.environmentmonitor.app.data.Importance.OFF

/**
 * Built-in profiles seeded on first launch. The values are starting points based on
 * common comfort guidelines; users can duplicate a preset and tune the copy.
 */
object Presets {
    val all: List<Profile> = listOf(
        Profile(
            name = "Sleep",
            icon = ProfileIcon.MOON.key,
            isPreset = true,
            temperature = SensorSetting(16f, 19f, HIGH),
            humidity = SensorSetting(40f, 60f, MEDIUM),
            light = SensorSetting(0f, 10f, HIGH),
            sound = SensorSetting(0f, 20f, HIGH),
        ),
        Profile(
            name = "Office",
            icon = ProfileIcon.LAPTOP.key,
            isPreset = true,
            temperature = SensorSetting(20f, 24f, HIGH),
            humidity = SensorSetting(40f, 60f, MEDIUM),
            light = SensorSetting(300f, 500f, HIGH),
            sound = SensorSetting(0f, 45f, MEDIUM),
        ),
        Profile(
            name = "Sport",
            icon = ProfileIcon.DUMBBELL.key,
            isPreset = true,
            temperature = SensorSetting(16f, 20f, HIGH),
            humidity = SensorSetting(40f, 60f, LOW),
            light = SensorSetting(200f, 1000f, LOW),
            sound = SensorSetting(0f, 100f, OFF),
        ),
    )
}
