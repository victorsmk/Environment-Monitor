package com.environmentmonitor.app.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Surface
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.environmentmonitor.app.data.LightLevel
import com.environmentmonitor.app.data.Importance
import com.environmentmonitor.app.data.Profile
import com.environmentmonitor.app.data.RangeStatus
import com.environmentmonitor.app.data.ScoreShares
import com.environmentmonitor.app.data.ScoreLabel
import com.environmentmonitor.app.data.SensorScore
import com.environmentmonitor.app.data.SensorType
import com.environmentmonitor.app.data.Session
import com.environmentmonitor.app.ui.advice
import com.environmentmonitor.app.ui.missingNote
import com.environmentmonitor.app.ui.scoreColor
import com.environmentmonitor.app.ui.icons.ProfileIconImage
import com.environmentmonitor.app.ui.icons.SensorIcon
import com.environmentmonitor.app.ui.theme.color
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
            if (state.offline) ConnectionBanner(recording = state.activeSession != null)
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

/** Shown while the sensors have stopped answering. */
@Composable
private fun ConnectionBanner(recording: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "No signal from the sensors",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (recording) {
                    "The values below are the last ones received. Recording is paused and " +
                        "continues by itself when the signal returns."
                } else {
                    "The values below are the last ones received. The app keeps trying to reconnect."
                },
                style = MaterialTheme.typography.bodySmall,
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
                if (name != null) {
                    ProfileIconImage(icon, size = 20.dp, tint = LocalContentColor.current)
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    text = name ?: "No profile",
                    modifier = Modifier.weight(1f),
                )
                if (!locked) Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                profiles.forEach { profile ->
                    DropdownMenuItem(
                        text = { Text(profile.name) },
                        leadingIcon = { ProfileIconImage(profile.icon, size = 20.dp) },
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
    val color by animateColorAsState(
        targetValue = if (score == null) MaterialTheme.colorScheme.onSurfaceVariant else scoreColor(score.label),
        animationSpec = tween(600),
        label = "score colour",
    )
    // The card takes a soft tint of the verdict colour, so Good / Fair / Poor reads at a glance.
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.12f).compositeOver(MaterialTheme.colorScheme.surface),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (score == null) {
                Text(
                    text = when {
                        state.offline -> "No score while there is no signal"
                        state.noUsableData ->
                            "No score: none of the sensors that matter for this profile have a value"
                        else -> "Waiting for the sensors…"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 64.dp),
                )
                return@Column
            }
            // The ring sweeps up from empty when the screen opens and eases whenever the score moves.
            val shown by animateFloatAsState(
                targetValue = score.overall.toFloat(),
                animationSpec = tween(900),
                label = "score",
            )
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.size(190.dp),
                    color = color.copy(alpha = 0.15f),
                    strokeWidth = 16.dp,
                    strokeCap = StrokeCap.Round,
                    trackColor = Color.Transparent,
                )
                CircularProgressIndicator(
                    progress = { shown / 100f },
                    modifier = Modifier.size(190.dp),
                    color = color,
                    trackColor = Color.Transparent,
                    strokeWidth = 16.dp,
                    strokeCap = StrokeCap.Round,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = shown.roundToInt().toString(),
                        fontSize = 60.sp,
                        fontWeight = FontWeight.Bold,
                        color = color,
                    )
                    Text(
                        text = score.label.text,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = color,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = score.advice(),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            score.missingNote()?.let { note ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
@Composable
private fun SensorGrid(state: DashboardUiState) {
    val settings = state.settings
    val measurement = state.measurement
    val scores = state.score?.sensors?.associateBy { it.sensor }
    val shares = settings?.let { ScoreShares.ofSettings(it) }.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SensorType.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { sensor ->
                    SensorTile(
                        sensor = sensor,
                        value = measurement?.value(sensor),
                        score = scores?.get(sensor),
                        ignored = settings?.get(sensor)?.importance == Importance.OFF,
                        offline = state.offline,
                        range = settings?.get(sensor)?.let { sensor.formatRange(it.idealMin, it.idealMax) },
                        ideal = settings?.get(sensor)?.let { it.idealMin..it.idealMax },
                        importanceText = settings?.get(sensor)
                            ?.takeIf { it.importance != Importance.OFF }
                            ?.let { "${it.importance.label} · ${shares[sensor]} %" },
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
    /** No signal: the value is the last one received, so the tile is dimmed. */
    offline: Boolean,
    range: String?,
    ideal: ClosedFloatingPointRange<Float>?,
    /** For example "Critical · 27 %": the sensor's importance and its share of the score. Null if ignored. */
    importanceText: String?,
    modifier: Modifier = Modifier,
) {
    val color = when {
        score == null -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> scoreColor(ScoreLabel.of(score.score.roundToInt()))
    }
    Card(
        modifier = modifier.alpha(if (offline) 0.5f else 1f),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (ignored) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SensorIcon(sensor, size = 18.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = sensor.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Lux means little to most people, so light also gets a plain word.
                if (sensor == SensorType.LIGHT && value != null) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = LightLevel.of(value).label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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
                    value == null -> "No data from this sensor"
                    offline -> "Last reading"
                    score == null -> " "
                    score.status == RangeStatus.IN_RANGE -> "In range · ${range.orEmpty()}"
                    score.status == RangeStatus.TOO_LOW -> "Too low · ${range.orEmpty()}"
                    else -> "Too high · ${range.orEmpty()}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!ignored && ideal != null && value != null) {
                Spacer(Modifier.height(10.dp))
                RangeGauge(sensor, value, ideal, color)
            }
            // How much this sensor counts in the profile in use, so it is clear why it moves the score.
            if (importanceText != null) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = importanceText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

/**
 * A thin track from "too low" to "too high" with the ideal range shaded and a dot where the room
 * is right now. The ends of the track are the points where this sensor's score reaches zero.
 */
@Composable
private fun RangeGauge(sensor: SensorType, value: Float, ideal: ClosedFloatingPointRange<Float>, dotColor: Color) {
    val low = ideal.start - sensor.tolerance
    val high = ideal.endInclusive + sensor.tolerance
    val position by animateFloatAsState(
        targetValue = ((value - low) / (high - low)).coerceIn(0f, 1f),
        animationSpec = tween(500),
        label = "gauge dot",
    )
    val track = MaterialTheme.colorScheme.outlineVariant
    val band = sensor.color().copy(alpha = 0.45f)
    val ring = MaterialTheme.colorScheme.surfaceContainerHigh
    Canvas(Modifier.fillMaxWidth().height(14.dp)) {
        val h = 6.dp.toPx()
        val top = (size.height - h) / 2
        val corner = CornerRadius(h / 2)
        drawRoundRect(track, Offset(0f, top), Size(size.width, h), corner)
        val bandStart = (ideal.start - low) / (high - low) * size.width
        val bandEnd = (ideal.endInclusive - low) / (high - low) * size.width
        drawRoundRect(band, Offset(bandStart, top), Size(bandEnd - bandStart, h), corner)
        val cx = position * size.width
        drawCircle(ring, 7.dp.toPx(), Offset(cx, size.height / 2))
        drawCircle(dotColor, 5.dp.toPx(), Offset(cx, size.height / 2))
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
