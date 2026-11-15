package com.environmentmonitor.app.ui.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.environmentmonitor.app.data.Importance
import com.environmentmonitor.app.data.Profile
import com.environmentmonitor.app.data.SensorType
import com.environmentmonitor.app.ui.icons.SensorIcon
import com.environmentmonitor.app.ui.icons.ProfileAvatar
import com.environmentmonitor.app.ui.formatRange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    viewModel: ProfilesViewModel,
    onOpenProfile: (Long) -> Unit,
    onNewProfile: () -> Unit,
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    // Kept as an id and saved, so the dialog survives rotating the phone.
    var pendingDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    val pendingDelete = profiles.firstOrNull { it.id == pendingDeleteId }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = { TopAppBar(title = { Text("Profiles") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewProfile) {
                Icon(Icons.Default.Add, contentDescription = "New profile")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(profiles, key = { it.id }) { profile ->
                ProfileCard(
                    profile = profile,
                    onClick = { onOpenProfile(profile.id) },
                    onDuplicate = { viewModel.duplicate(profile, onOpenProfile) },
                    onDelete = { pendingDeleteId = profile.id },
                )
            }
        }
    }

    pendingDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete profile?") },
            text = { Text("“${profile.name}” will be removed. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(profile)
                    pendingDeleteId = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ProfileCard(
    profile: Profile,
    onClick: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 14.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileAvatar(profile.icon)
                Spacer(Modifier.width(14.dp))
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                )
                if (profile.isPreset) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Text(
                            text = "Preset",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Duplicate") },
                            onClick = {
                                menuOpen = false
                                onDuplicate()
                            },
                        )
                        if (!profile.isPreset) {
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                onClick = {
                                    menuOpen = false
                                    onDelete()
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // One line per sensor: what it should be, then how much it matters, side by side.
            Column(
                modifier = Modifier.padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SensorType.entries.forEach { sensor ->
                    val setting = profile.setting(sensor)
                    val ignored = setting.importance == Importance.OFF
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SensorIcon(sensor, size = 18.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = sensor.formatRange(setting.idealMin, setting.idealMax),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (ignored) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.weight(1f),
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (ignored) {
                                Color.Transparent
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            },
                        ) {
                            Text(
                                text = setting.importance.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (ignored) {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}