package com.environmentmonitor.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.environmentmonitor.app.data.Importance
import com.environmentmonitor.app.data.SensorType
import com.environmentmonitor.app.data.Session
import com.environmentmonitor.app.data.SessionRepository
import com.environmentmonitor.app.ui.formatDateTime
import com.environmentmonitor.app.ui.formatDuration
import com.environmentmonitor.app.ui.formatRange
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    viewModel: SessionDetailViewModel,
    onBack: () -> Unit,
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            TopAppBar(
                title = { Text("Session") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            SessionInfoCard(current)
            if (current.isActive) {
                Button(onClick = viewModel::stop, modifier = Modifier.fillMaxWidth()) {
                    Text("Stop session")
                }
            }
            Text(
                text = "Scores and charts appear here in the next steps.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
private fun SessionInfoCard(session: Session) {
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
            InfoRow("Environment", "${session.profileIcon} ${session.profileName}")
            InfoRow("Started", formatDateTime(session.startedAt))
            InfoRow(
                "Ended",
                session.endedAt?.let { formatDateTime(it) } ?: "Still recording",
            )
            InfoRow("Duration", formatDuration(session.durationMillis(now)))
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
                    "${sensor.formatRange(setting.idealMin, setting.idealMax)} · ${setting.importance.label}"
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
