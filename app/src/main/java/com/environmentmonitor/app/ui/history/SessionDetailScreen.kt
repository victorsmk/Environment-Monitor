package com.environmentmonitor.app.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.environmentmonitor.app.data.Importance
import com.environmentmonitor.app.data.RecordingInterval
import com.environmentmonitor.app.data.SensorStats
import com.environmentmonitor.app.data.SensorType
import com.environmentmonitor.app.data.Session
import com.environmentmonitor.app.data.SessionAnalysis
import com.environmentmonitor.app.ui.DiscardChangesDialog
import com.environmentmonitor.app.ui.charts.ScoreChart
import com.environmentmonitor.app.ui.charts.SensorChart
import com.environmentmonitor.app.ui.formatDateTime
import com.environmentmonitor.app.ui.formatDuration
import com.environmentmonitor.app.ui.formatRange
import com.environmentmonitor.app.ui.formatValue
import com.environmentmonitor.app.ui.headline
import com.environmentmonitor.app.ui.rangeWords
import com.environmentmonitor.app.ui.scoreColor
import com.environmentmonitor.app.ui.icons.ProfileIconImage
import com.environmentmonitor.app.ui.icons.SensorIcon
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    viewModel: SessionDetailViewModel,
    onBack: () -> Unit,
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val readingCount by viewModel.readingCount.collectAsStateWithLifecycle()
    val analysis by viewModel.analysis.collectAsStateWithLifecycle()
    // rememberSaveable: the dialogs stay open when the phone is rotated.
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val requestBack = { if (viewModel.hasChanges) confirmDiscard = true else onBack() }
    BackHandler(enabled = viewModel.hasChanges) { confirmDiscard = true }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            TopAppBar(
                title = { Text("Session") },
                navigationIcon = {
                    IconButton(onClick = requestBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (session != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete session")
                        }
                    }
                    IconButton(
                        enabled = viewModel.hasChanges,
                        onClick = { viewModel.save() },
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                },
            )
        },
    ) { padding ->
        val current = session
        if (current == null) {
            if (viewModel.loaded) {
                Text(
                    "This session no longer exists.",
                    modifier = Modifier.padding(padding).padding(16.dp),
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (current.isActive) {
                Button(
                    onClick = viewModel::stop,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Stop session") }
            }

            ResultsSection(current, analysis)

            SectionTitle("Details")
            OutlinedTextField(
                value = viewModel.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Name") },
                singleLine = true,
                isError = viewModel.nameError != null,
                supportingText = viewModel.nameError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = viewModel.notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text("Notes") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            SessionInfoCard(current, readingCount)
        }
    }

    if (confirmDiscard) {
        DiscardChangesDialog(
            onDiscard = {
                confirmDiscard = false
                onBack()
            },
            onKeepEditing = { confirmDiscard = false },
        )
    }

    if (confirmDelete) {
        session?.let { current ->
            DeleteSessionDialog(
                session = current,
                onConfirm = {
                    confirmDelete = false
                    viewModel.delete(onDeleted = onBack)
                },
                onDismiss = { confirmDelete = false },
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge)
}

/** The score, how each sensor did, and the charts. */
@Composable
private fun ResultsSection(session: Session, analysis: SessionAnalysis?) {
    SectionTitle("Results")

    if (analysis == null) {
        Text(
            text = if (session.isActive) {
                "Waiting for the first reading…"
            } else {
                "This session has no readings."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    SummaryCard(analysis)

    val first = analysis.scorePoints.first().timestamp
    val last = analysis.scorePoints.last().timestamp
    if (analysis.readingCount < 2) {
        Text(
            text = "Charts appear once there are two readings " +
                "(one is stored ${RecordingInterval.describe(session.recordingIntervalSeconds)}).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    Text(
        text = "Tap or drag on a chart to read the exact value at any moment.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ChartCard(title = "Score over time") {
        ScoreChart(analysis.scorePoints, timeStart = first, timeEnd = last)
    }
    analysis.sensors.forEach { stats ->
        ChartCard(title = stats.sensor.label, subtitle = stats.rangeSummary(), sensor = stats.sensor) {
            SensorChart(
                sensor = stats.sensor,
                setting = stats.setting,
                points = stats.points,
                timeStart = first,
                timeEnd = last,
            )
        }
    }
}

@Composable
private fun SummaryCard(analysis: SessionAnalysis) {
    val color = scoreColor(analysis.label)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                Text(
                    text = analysis.overall.toString(),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                )
                Column(modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)) {
                    Text(analysis.label.text, style = MaterialTheme.typography.titleMedium, color = color)
                    Text(
                        "average score",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(analysis.headline(), style = MaterialTheme.typography.bodyLarge)
            analysis.sensors.forEach { stats ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        SensorIcon(stats.sensor, size = 18.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stats.sensor.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${stats.inRangePercent} % in range")
                }
            }
        }
    }
}

@Composable
private fun ChartCard(
    title: String,
    subtitle: String? = null,
    sensor: SensorType? = null,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                if (sensor != null) {
                    SensorIcon(sensor, size = 22.dp)
                    Spacer(Modifier.width(10.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

/** "Lowest 16.2, average 17.4, highest 18.9 °C" */
private fun SensorStats.rangeSummary(): String =
    "Lowest ${formatValue(roundFor(min))}, average ${formatValue(roundFor(average))}, " +
        "highest ${formatValue(roundFor(max))} ${sensor.unit}"

private fun SensorStats.roundFor(value: Float): Float =
    if (sensor == SensorType.TEMPERATURE) (value * 10).let { kotlin.math.round(it) / 10f } else kotlin.math.round(value)

@Composable
private fun SessionInfoCard(session: Session, readingCount: Int) {
    // A running session shows a duration that keeps counting.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    if (session.isActive) {
        LaunchedEffect(session.id) {
            while (true) {
                delay(30_000)
                now = System.currentTimeMillis()
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text("Environment", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    ProfileIconImage(session.profileIcon, size = 20.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(session.profileName)
                }
            }
            InfoRow("Started", formatDateTime(session.startedAt))
            InfoRow(
                "Ended",
                session.endedAt?.let { formatDateTime(it) } ?: "Still recording",
            )
            InfoRow("Duration", formatDuration(session.durationMillis(now)))
            InfoRow(
                "Recording",
                RecordingInterval.describe(session.recordingIntervalSeconds)
                    .replaceFirstChar { it.uppercase() },
            )
            InfoRow("Readings recorded", readingCount.toString())
            Text(
                text = "Ideal ranges used",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
            SensorType.entries.forEach { sensor ->
                val setting = session.setting(sensor)
                val value = if (setting.importance == Importance.OFF) {
                    "ignored"
                } else {
                    val words = sensor.rangeWords(setting.idealMin, setting.idealMax)
                        ?.let { " ($it)" }.orEmpty()
                    "${sensor.formatRange(setting.idealMin, setting.idealMax)}$words · ${setting.importance.label}"
                }
                InfoRow(sensor.label, value)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}
