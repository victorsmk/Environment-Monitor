package com.environmentmonitor.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.environmentmonitor.app.ui.AppRoot
import com.environmentmonitor.app.ui.theme.EnvironmentMonitorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings = (application as EnvironmentMonitorApplication).container.settings
            val mode by settings.themeMode.collectAsStateWithLifecycle()
            val dark = mode.isDark(systemIsDark = isSystemInDarkTheme())

            // The status and navigation bar icons must be dark on a light app and light on a dark
            // app, even when that differs from the phone's own setting.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(
                        lightScrim = Color.argb(0xE6, 0xFF, 0xFF, 0xFF),
                        darkScrim = Color.argb(0x80, 0x1B, 0x1B, 0x1B),
                    ) { dark },
                )
                onDispose { }
            }

            EnvironmentMonitorTheme(darkTheme = dark) {
                AppRoot()
            }
        }
    }
}
