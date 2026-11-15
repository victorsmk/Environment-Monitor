package com.environmentmonitor.app.ui.icons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.environmentmonitor.app.data.ProfileIcon
import com.environmentmonitor.app.data.SensorType
import com.environmentmonitor.app.ui.theme.color

/** A sensor's drawn icon, in the sensor's own colour unless a [tint] is given. */
@Composable
fun SensorIcon(
    sensor: SensorType,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = sensor.color(),
) {
    Icon(
        imageVector = sensor.iconVector,
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size),
    )
}

/** A profile's drawn icon with no background. [key] is what the profile saved. */
@Composable
fun ProfileIconImage(
    key: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Icon(
        imageVector = ProfileIcon.fromKey(key).vector,
        contentDescription = ProfileIcon.fromKey(key).label,
        tint = tint,
        modifier = modifier.size(size),
    )
}

/** A profile's icon on a soft round background, as shown beside its name in lists. */
@Composable
fun ProfileAvatar(
    key: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        ProfileIconImage(
            key = key,
            size = size * 0.52f,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}
