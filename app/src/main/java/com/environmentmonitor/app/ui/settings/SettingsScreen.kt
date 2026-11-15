package com.environmentmonitor.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.environmentmonitor.app.data.RecordingInterval
import com.environmentmonitor.app.sensors.MockScenario
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val interval by viewModel.recordingInterval.collectAsStateWithLifecycle()
    val scenario by viewModel.mockScenario.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = { TopAppBar(title = { Text("Settings") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsSection(
                title = "Recording",
                note = "How often a running session stores a reading. A change applies to the next " +
                    "session you start. A session that is already running keeps the interval it " +
                    "started with.",
            ) {
                RecordingInterval.entries.forEach { option ->
                    ChoiceRow(
                        selected = option == interval,
                        title = option.label,
                        subtitle = "About ${readingsPerNight(option)} readings for an 8 hour night",
                        onSelect = { viewModel.selectRecordingInterval(option) },
                    )
                }
            }

            SettingsSection(
                title = "Demo data",
                note = "Until the real sensor device is connected, the readings are made up. " +
                    "Pick the kind of room they pretend to be in. The values glide there over " +
                    "about 20 seconds.",
            ) {
                MockScenario.entries.forEach { option ->
                    ChoiceRow(
                        selected = option == scenario,
                        title = option.label,
                        subtitle = null,
                        onSelect = { viewModel.selectMockScenario(option) },
                    )
                }
            }

            SettingsSection(title = "About", note = null) {
                Text("Environment Monitor ${appVersion()}", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "Sensor source: demo data. Connecting the Raspberry Pi Pico W comes in a later phase.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** "2,880" for every 10 seconds. */
private fun readingsPerNight(interval: RecordingInterval): String =
    String.format(Locale.US, "%,d", 8 * 3600 / interval.seconds)

@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    return runCatching {
        "v" + context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrDefault("")
}

@Composable
private fun SettingsSection(
    title: String,
    note: String?,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (note != null) {
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .padding(vertical = 8.dp, horizontal = 8.dp)
                    .selectableGroup(),
            ) { content() }
        }
    }
}

@Composable
private fun ChoiceRow(
    selected: Boolean,
    title: String,
    subtitle: String?,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
