package com.environmentmonitor.app.ui.profiles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import com.environmentmonitor.app.data.LightLevel
import com.environmentmonitor.app.data.ProfileIcon
import com.environmentmonitor.app.data.SensorSetting
import com.environmentmonitor.app.data.SensorType
import com.environmentmonitor.app.ui.DiscardChangesDialog
import com.environmentmonitor.app.ui.icons.ProfileIconImage
import com.environmentmonitor.app.ui.icons.SensorIcon
import com.environmentmonitor.app.ui.formatRange
import com.environmentmonitor.app.ui.rangeWords
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconPicker(selected: String, enabled: Boolean, onSelect: (String) -> Unit) {
    val current = ProfileIcon.fromKey(selected)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Icon", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(8.dp))
            Text(
                text = current.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Wraps onto a second row, so every icon (and the chosen one) is always in view.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProfileIcon.entries.forEach { icon ->
                val isSelected = icon == current
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            CircleShape,
                        )
                        .border(
                            BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                            ),
                            CircleShape,
                        )
                        .clickable(enabled = enabled) { onSelect(icon.key) },
                    contentAlignment = Alignment.Center,
                ) {
                    ProfileIconImage(
                        key = icon.key,
                        size = 24.dp,
                        tint = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SensorIcon(sensor, size = 22.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(sensor.label, style = MaterialTheme.typography.titleMedium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (active) sensor.formatRange(setting.idealMin, setting.idealMax)
                        else "Ignored",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    // For light, say it in words too: "Bright", "Dim to Bright".
                    val words = if (active) sensor.rangeWords(setting.idealMin, setting.idealMax) else null
                    if (words != null) {
                        Text(
                            text = words,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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

            // Light also has five plain-word levels: tap two to select everything between them.
            if (sensor == SensorType.LIGHT) {
                LightLevelPicker(
                    setting = setting,
                    enabled = enabled && active,
                    onRangeChange = onRangeChange,
                )
            }

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Importance.entries.forEachIndexed { index, importance ->
                    SegmentedButton(
                        selected = importance == setting.importance,
                        onClick = { onImportanceChange(importance) },
                        shape = SegmentedButtonDefaults.itemShape(index, Importance.entries.size),
                        enabled = enabled,
                        // Clear colours: the default selected tint is nearly invisible on the cards.
                        colors = importanceButtonColors(),
                        icon = {},
                    ) {
                        // Smaller text: "Medium" was clipped to "Mediu" on narrower phones.
                        Text(
                            text = importance.label,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** The first tap of a level pair, and the range it produced (so a slider move can cancel it). */
private data class PendingLevel(val level: LightLevel, val minLux: Float, val maxLux: Float)

/**
 * Dark, Dim, Soft, Bright and Very bright as tappable chips. The levels inside the current range
 * are filled. Taps come in pairs: the first picks one level, the second stretches the range to
 * include everything between the two, and the slider above follows (Dark then Dim gives 0–50 lux).
 */
@Composable
private fun LightLevelPicker(
    setting: SensorSetting,
    enabled: Boolean,
    onRangeChange: (Float, Float) -> Unit,
) {
    val selected = LightLevel.covering(setting.idealMin, setting.idealMax)
    val scheme = MaterialTheme.colorScheme

    // The first tap of a pair is remembered here. It only counts while the range is still the one
    // that tap produced, so moving the slider by hand cancels it.
    var pending by remember { mutableStateOf<PendingLevel?>(null) }
    val anchor = pending
        ?.takeIf { it.minLux == setting.idealMin && it.maxLux == setting.idealMax }
        ?.level
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Every chip is as tall as the tallest ("Very bright" wraps onto two lines).
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LightLevel.entries.forEach { level ->
                val isSelected = level in selected
                val container = when {
                    isSelected && enabled -> scheme.primary
                    isSelected -> scheme.primary.copy(alpha = 0.65f)
                    else -> Color.Transparent
                }
                val content = when {
                    isSelected -> scheme.onPrimary
                    enabled -> scheme.onSurface
                    else -> scheme.onSurface.copy(alpha = 0.5f)
                }
                androidx.compose.material3.Surface(
                    onClick = {
                        val tap = LightLevel.afterTap(anchor, level)
                        pending = tap.anchor?.let { PendingLevel(it, tap.minLux, tap.maxLux) }
                        onRangeChange(tap.minLux, tap.maxLux)
                    },
                    enabled = enabled,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                    color = container,
                    contentColor = content,
                    border = BorderStroke(1.dp, if (isSelected) scheme.primary else scheme.outline),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = level.label,
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 2,
                        )
                    }
                }
            }
        }
        if (enabled) {
            Text(
                text = if (anchor == null) {
                    "Tap two levels to select the range between them"
                } else {
                    "Now tap a second level (or the same one again)"
                },
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The chosen importance is filled with the brand colour; the others are plain outlines. The chosen
 * one stays clearly visible on a read-only preset too (a little softer, since it cannot be changed).
 */
@Composable
private fun importanceButtonColors(): androidx.compose.material3.SegmentedButtonColors {
    val scheme = MaterialTheme.colorScheme
    return SegmentedButtonDefaults.colors(
        activeContainerColor = scheme.primary,
        activeContentColor = scheme.onPrimary,
        activeBorderColor = scheme.primary,
        inactiveContainerColor = Color.Transparent,
        inactiveContentColor = scheme.onSurface,
        inactiveBorderColor = scheme.outline,
        disabledActiveContainerColor = scheme.primary.copy(alpha = 0.65f),
        disabledActiveContentColor = scheme.onPrimary,
        disabledActiveBorderColor = scheme.primary.copy(alpha = 0.65f),
        disabledInactiveContainerColor = Color.Transparent,
        disabledInactiveContentColor = scheme.onSurface.copy(alpha = 0.5f),
        disabledInactiveBorderColor = scheme.outlineVariant,
    )
}

private fun snapToStep(value: Float, sensor: SensorType): Float =
    ((value / sensor.step).roundToInt() * sensor.step).coerceIn(sensor.min, sensor.max)
