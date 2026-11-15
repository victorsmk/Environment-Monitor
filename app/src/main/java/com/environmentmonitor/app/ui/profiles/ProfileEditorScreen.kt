package com.environmentmonitor.app.ui.profiles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.environmentmonitor.app.data.Importance
import com.environmentmonitor.app.data.SensorSetting
import com.environmentmonitor.app.data.SensorType
import com.environmentmonitor.app.ui.DiscardChangesDialog
import com.environmentmonitor.app.ui.ProfileIcons
import com.environmentmonitor.app.ui.formatRange
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorScreen(
    viewModel: ProfileEditorViewModel,
    onBack: () -> Unit,
    onDuplicated: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val readOnly = state.isPreset
    val title = when {
        state.isNew -> "New profile"
        readOnly -> "Preset profile"
        else -> "Edit profile"
    }
    // rememberSaveable: the dialog stays open when the phone is rotated.
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val requestBack = { if (state.hasChanges) confirmDiscard = true else onBack() }
    BackHandler(enabled = state.hasChanges) { confirmDiscard = true }

    if (confirmDiscard) {
        DiscardChangesDialog(
            onDiscard = {
                confirmDiscard = false
                onBack()
            },
            onKeepEditing = { confirmDiscard = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = requestBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!readOnly) {
                        IconButton(onClick = { viewModel.save(onSaved = onBack) }) {
                            Icon(Icons.Default.Check, contentDescription = "Save")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
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
            if (readOnly) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Presets are read-only. Duplicate this one to make your own version.")
                        Button(onClick = { viewModel.duplicate(onDuplicated) }) {
                            Text("Duplicate to edit")
                        }
                    }
                }
            }

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Name") },
                singleLine = true,
                enabled = !readOnly,
                isError = state.nameError != null,
                supportingText = { state.nameError?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )

            IconPicker(
                selected = state.icon,
                enabled = !readOnly,
                onSelect = viewModel::onIconChange,
            )

            SensorType.entries.forEach { sensor ->
                SensorSettingCard(
                    sensor = sensor,
                    setting = state.settings.getValue(sensor),
                    enabled = !readOnly,
                    onRangeChange = { min, max -> viewModel.onRangeChange(sensor, min, max) },
                    onImportanceChange = { viewModel.onImportanceChange(sensor, it) },
                )
            }

            state.settingsError?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun IconPicker(selected: String, enabled: Boolean, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Icon", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProfileIcons.forEach { icon ->
                val isSelected = icon == selected
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .border(
                            BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                            ),
                            CircleShape,
                        )
                        .clickable(enabled = enabled) { onSelect(icon) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(icon, fontSize = 24.sp)
                }
            }
        }
    }
}

@Composable
private fun SensorSettingCard(
    sensor: SensorType,
    setting: SensorSetting,
    enabled: Boolean,
    onRangeChange: (Float, Float) -> Unit,
    onImportanceChange: (Importance) -> Unit,
) {
    val active = setting.importance != Importance.OFF

    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(sensor.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (active) sensor.formatRange(setting.idealMin, setting.idealMax)
                    else "Ignored",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            RangeSlider(
                value = setting.idealMin..setting.idealMax,
                onValueChange = { range ->
                    onRangeChange(
                        snapToStep(range.start, sensor),
                        snapToStep(range.endInclusive, sensor),
                    )
                },
                valueRange = sensor.min..sensor.max,
                enabled = enabled && active,
            )

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Importance.entries.forEachIndexed { index, importance ->
                    SegmentedButton(
                        selected = importance == setting.importance,
                        onClick = { onImportanceChange(importance) },
                        shape = SegmentedButtonDefaults.itemShape(index, Importance.entries.size),
                        enabled = enabled,
                        icon = {},
                    ) {
                        Text(importance.label, maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun snapToStep(value: Float, sensor: SensorType): Float =
    ((value / sensor.step).roundToInt() * sensor.step).coerceIn(sensor.min, sensor.max)
