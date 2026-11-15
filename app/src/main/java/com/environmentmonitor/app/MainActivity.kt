package com.environmentmonitor.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.environmentmonitor.app.ui.AppRoot
import com.environmentmonitor.app.ui.theme.EnvironmentMonitorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EnvironmentMonitorTheme {
                AppRoot()
            }
        }
    }
}
