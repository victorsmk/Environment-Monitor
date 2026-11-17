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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.environmentmonitor.app.sensors.SensorSourceKind
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.environmentmonitor.app.data.RecordingInterval
import com.environmentmonitor.app.data.SensorType
import com.environmentmonitor.app.sensors.MockScenario
import com.environmentmonitor.app.ui.theme.ThemeMode
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val interval by viewModel.recordingInterval.collectAsStateWithLifecycle()
    val scenario by viewModel.mockScenario.collectAsStateWithLifecycle()
    val mockOnline by viewModel.mockOnline.collectAsStateWithLifecycle()
    val missingSensors by viewModel.mockMissingSensors.collectAsStateWithLifecycle()
    val sourceKind by viewModel.sourceKind.collectAsStateWithLifecycle()
    val picoAddress by viewModel.picoAddress.collectAsStateWithLifecycle()
    val sessionActive by viewModel.sessionActive.collectAsStateWithLifecycle()
    val picoCheck by viewModel.picoCheck.collectAsStateWithLifecycle()

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
                title = "Sensors",
                note = if (sessionActive) {
                    "Locked while a session is recording. Stop the session to change where the " +
                        "readings come from."
                } else {
                    "Where the readings come from."
                },
            ) {
                SensorSourceKind.entries.forEach { option ->
                    ChoiceRow(
                        selected = option == sourceKind,
                        title = option.label,
                        subtitle = option.description,
                        enabled = !sessionActive,
                        onSelect = { viewModel.selectSourceKind(option) },
                    )
                }
                if (sourceKind == SensorSourceKind.PICO) {
                    PicoControls(
                        address = picoAddress,
                        check = picoCheck,
                        onAddressChange = viewModel::setPicoAddress,
                        onFind = viewModel::findPico,
                        onTest = viewModel::testConnection,
                    )
                }
            }

            SettingsSection(
                title = "Appearance",
                note = "Light or dark colours for the whole app.",
            ) {
                ThemeMode.entries.forEach { option ->
                    ChoiceRow(
                        selected = option == themeMode,
                        title = option.label,
                        subtitle = option.description,
                        onSelect = { viewModel.selectThemeMode(option) },
                    )
                }
            }

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
                note = "Made-up readings, used while Demo data is selected under Sensors. " +
                    "Pick the kind of room they pretend to be in. The values glide there over " +
                    "about 20 seconds. The switches at the bottom pretend the sensor device or one " +
                    "of its sensors has failed, to see what the app does; they switch off again " +
                    "when the app restarts.",
            ) {
                MockScenario.entries.forEach { option ->
                    ChoiceRow(
                        selected = option == scenario,
                        title = option.label,
                        subtitle = null,
                        onSelect = { viewModel.selectMockScenario(option) },
                    )
                }
                SwitchRow(
                    checked = !mockOnline,
                    title = "Simulate lost signal",
                    subtitle = "Pretends the sensor device has stopped answering, to see what the " +
                        "app does. Switches off again when the app restarts.",
                    onChange = { viewModel.setMockOnline(!it) },
                )
                SensorType.entries.forEach { sensor ->
                    SwitchRow(
                        checked = sensor in missingSensors,
                        title = "Simulate failed ${sensor.label.lowercase()} sensor",
                        subtitle = null,
                        onChange = { viewModel.setMockSensorFailed(sensor, it) },
                    )
                }
            }

            SettingsSection(title = "About", note = null) {
                Text("Environment Monitor ${appVersion()}", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "Sensor source: ${sourceKind.label}.",
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
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .selectable(selected = selected, enabled = enabled, onClick = onSelect, role = Role.RadioButton)
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

@Composable
private fun SwitchRow(
    checked: Boolean,
    title: String,
    subtitle: String?,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onChange, role = Role.Switch)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = null, modifier = Modifier.padding(start = 12.dp))
    }
}

/** The Pico's address, the two buttons, and what they found out. Shown while the Pico is selected. */
@Composable
private fun PicoControls(
    address: String,
    check: PicoCheck,
    onAddressChange: (String) -> Unit,
    onFind: () -> Unit,
    onTest: () -> Unit,
) {
    // The text is kept here while typing, and saved on every change; the saved value can also change
    // underneath (a search found a new address), which replaces what is shown.
    var text by remember(address) { mutableStateOf(address) }
    val working = check is PicoCheck.Working
    Column(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                onAddressChange(it.trim())
            },
            label = { Text("Pico address") },
            supportingText = { Text("For example 192.168.4.1 (the Pico's own network), or the address on your Wi-Fi.") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onFind, enabled = !working) { Text("Find Pico") }
            OutlinedButton(onClick = onTest, enabled = !working && text.isNotBlank()) { Text("Test connection") }
        }
        when (check) {
            PicoCheck.Idle -> Unit
            is PicoCheck.Working -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(check.text, style = MaterialTheme.typography.bodyMedium)
            }
            is PicoCheck.Success -> Text(check.text, style = MaterialTheme.typography.bodyMedium)
            is PicoCheck.Failure -> Text(
                check.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
