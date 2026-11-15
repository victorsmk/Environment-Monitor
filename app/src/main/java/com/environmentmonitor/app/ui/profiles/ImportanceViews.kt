package com.environmentmonitor.app.ui.profiles

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.environmentmonitor.app.data.Importance
import com.environmentmonitor.app.data.ScoreShares
import com.environmentmonitor.app.data.SensorType
import com.environmentmonitor.app.ui.icons.SensorIcon
import com.environmentmonitor.app.ui.theme.LocalUseDarkTheme
import com.environmentmonitor.app.ui.theme.color

/**
 * One bar showing how a profile divides up the score: a coloured block per sensor that counts,
 * as wide as that sensor's share, in the sensor's own colour. Change a sensor's importance and its
 * block grows or shrinks; set it to Ignore and its block disappears.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScoreSplitBar(importance: Map<SensorType, Importance>, modifier: Modifier = Modifier) {
    val shares = ScoreShares.of(importance)
    val active = SensorType.entries.filter { it in shares }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("How the score is made up", style = MaterialTheme.typography.titleMedium)
            if (active.isEmpty()) {
                Text(
                    text = "No sensor counts yet. Set at least one to something other than Ignore.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    active.forEach { sensor ->
                        // key: each segment keeps its own animation when others come and go.
                        key(sensor) {
                            SplitSegment(
                                sensor = sensor,
                                weight = importance.getValue(sensor).weight.toFloat(),
                                share = shares.getValue(sensor),
                            )
                        }
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    active.forEach { sensor ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SensorIcon(sensor, size = 16.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "${sensor.label} · ${importance.getValue(sensor).label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.SplitSegment(sensor: SensorType, weight: Float, share: Int) {
    // The sensor colours are dark in light mode and light in dark mode, so the text flips too.
    val textColor = if (LocalUseDarkTheme.current) Color(0xFF1B1B1B) else Color.White
    // Wider for a more important sensor, and eased when its importance changes.
    val animatedWeight by animateFloatAsState(
        targetValue = weight,
        animationSpec = tween(durationMillis = 300),
        label = "segment width",
    )
    Box(
        modifier = Modifier
            .weight(animatedWeight.coerceAtLeast(0.01f))
            .fillMaxHeight()
            .background(sensor.color(), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$share %",
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}

/** Explains the four importance levels, with an example. Opened by the "?" beside each heading. */
@Composable
fun ImportanceHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What importance means") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Every sensor that is not set to Ignore counts toward the score. Importance " +
                        "decides how much it counts compared with the others.",
                )
                Importance.entries.forEach { importance ->
                    Row {
                        Text(
                            text = importance.label,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(90.dp),
                        )
                        Text(
                            text = when (importance) {
                                Importance.OFF -> "Not used at all."
                                Importance.LOW -> "Counts a little (1 part)."
                                Importance.MEDIUM -> "Counts normally (2 parts)."
                                Importance.HIGH -> "Matters most (3 parts)."
                            },
                        )
                    }
                }
                Text(
                    "For example, when sleeping, noise and light matter more than humidity. Set " +
                        "Sound and Light to Critical and Humidity to Important.",
                )
                Text(
                    "The bar at the top shows each sensor's share of the score. It updates as you " +
                        "change the buttons.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } },
    )
}
