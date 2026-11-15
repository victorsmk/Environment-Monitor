package com.environmentmonitor.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.environmentmonitor.app.data.ScoreLabel

/** Teal for Good, amber for Fair, red for Poor. */
@Composable
fun scoreColor(label: ScoreLabel): Color = when (label) {
    ScoreLabel.GOOD -> MaterialTheme.colorScheme.primary
    ScoreLabel.FAIR -> MaterialTheme.colorScheme.tertiary
    ScoreLabel.POOR -> MaterialTheme.colorScheme.error
}
