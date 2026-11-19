package com.environmentmonitor.app.ui.settings

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private fun readStatus(context: Context) = BackgroundRecordingStatus(
    notificationsAllowed = context.getSystemService(NotificationManager::class.java).areNotificationsEnabled(),
    batteryUnrestricted = context.getSystemService(PowerManager::class.java)
        .isIgnoringBatteryOptimizations(context.packageName),
)

/** The content of the "Background recording" card: the two things that decide if a night survives. */
@Composable
fun BackgroundRecordingControls() {
    val context = LocalContext.current
    var status by remember { mutableStateOf(readStatus(context)) }

    // The system screens below change these, so read them again whenever the app comes back.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) status = readStatus(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier.padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(status.notificationsText, style = MaterialTheme.typography.bodyMedium)
        Text(status.batteryText, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            }) { Text("Notifications") }
            OutlinedButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }) { Text("Battery") }
        }
    }
}
