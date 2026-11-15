package com.environmentmonitor.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.environmentmonitor.app.data.Profile
import com.environmentmonitor.app.data.RangeStatus
import com.environmentmonitor.app.data.ScoreLabel
import com.environmentmonitor.app.data.SensorScore
import com.environmentmonitor.app.data.SensorType
import com.environmentmonitor.app.data.Session
import com.environmentmonitor.app.ui.advice
import com.environmentmonitor.app.ui.scoreColor
import com.environmentmonitor.app.ui.formatDateTime
import com.environmentmonitor.app.ui.formatDuration
import com.environmentmonitor.app.ui.formatRange
import com.environmentmonitor.app.ui.formatValue
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = { TopAppBar(title = { Text("Dashboard") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            EnvironmentPicker(
                profiles = state.profiles,
                name = state.environmentName,
                icon = state.environmentIcon,
                locked = state.activeSession != null,
                onSelect = viewModel::selectProfile,
            )
            ScoreCard(state)
            SensorGrid(state)
            SessionControl(
                session = state.activeSession,
                canStart = state.selectedProfile != null,
                onStart = viewModel::startSession,
                onStop = viewModel::stopSession,
            )
        }
    }
}

@Composable
private fun EnvironmentPicker(
    profiles: List<Profile>,
    name: String?,
    icon: String?,
    locked: Boolean,
    onSelect: (Profile) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(
            text = "Judging this room as",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box {
            OutlinedButton(
                onClick = { open = true },
                enabled = !locked && profiles.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (name != null) "$icon  $name" else "No profile",
                    modifier = Modifier.weight(1f),
                )
                if (!locked) Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                profiles.forEach { profile ->
                    DropdownMenuItem(
                        text = { Text("${profile.icon}  ${profile.name}") },
                        onClick = {
                            open = false
                            onSelect(profile)
                        },
                    )
                }
            }
        }
        if (locked) {
            Text(
                text = "Locked while a session is recording",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ScoreCard(state: DashboardUiState) {
    val score = state.score
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (score == null) {
                Text(
                    text = "Waiting for the sensors…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 64.dp),
                )
                return@Column
            }
            val color = scoreColor(score.label)
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { score.overall / 100f },
                    modifier = Modifier.size(180.dp),
                    color = color,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeWidth = 14.dp,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = score.overall.toString(),
                        fontSize = 56.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = color,
                    )
                    Text(
                        text = score.label.text,
                        style = MaterialTheme.typography.titleMedium,
                        color = color,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = score.advice(),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SensorGrid(state: DashboardUiState) {
    val settings = state.settings
    val measurement = state.measurement
    val scores = state.score?.sensors?.associateBy { it.sensor }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SensorType.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { sensor ->
                    SensorTile(
                        sensor = sensor,
                        value = measurement?.value(sensor),
                        score = scores?.get(sensor),
                        ignored = settings != null && scores != null && sensor !in scores,
                        range = settings?.get(sensor)?.let { sensor.formatRange(it.idealMin, it.idealMax) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SensorTile(
    sensor: SensorType,
    value: Float?,
    score: SensorScore?,
    ignored: Boolean,
    range: String?,
    modifier: Modifier = Modifier,
) {
    val color = when {
        score == null -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> scoreColor(ScoreLabel.of(score.score.roundToInt()))
    }
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (ignored) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = sensor.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = if (value != null) formatValue(value.roundTo(sensor)) else "–",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (ignored) MaterialTheme.colorScheme.onSurfaceVariant else color,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = sensor.unit,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Text(
                text = when {
                    ignored -> "Ignored by this profile"
                    score == null -> " "
                    score.status == RangeStatus.IN_RANGE -> "In range · ${range.orEmpty()}"
                    score.status == RangeStatus.TOO_LOW -> "Too low · ${range.orEmpty()}"
                    else -> "Too high · ${range.orEmpty()}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Temperature to one decimal, everything else to whole numbers: no flickering digits. */
private fun Float.roundTo(sensor: SensorType): Float =
    if (sensor == SensorType.TEMPERATURE) (this * 10).roundToInt() / 10f else roundToInt().toFloat()

@Composable
private fun SessionControl(
    session: Session?,
    canStart: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    if (session == null) {
        Button(
            onClick = onStart,
            enabled = canStart,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Start session") }
        return
    }

    // The elapsed time is recomputed every few seconds while a session runs.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session.id) {
        while (true) {
            now = System.currentTimeMillis()
            delay(5_000)
        }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Recording: ${session.name}", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Since ${formatDateTime(session.startedAt)} · " +
                    formatDuration(session.durationMillis(now)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Stop session") }
        }
    }
}
