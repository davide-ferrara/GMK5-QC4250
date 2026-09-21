package com.golfv.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.golfv.launcher.R

private val doorImages = intArrayOf(
    R.drawable.golf_top_00, R.drawable.golf_top_01, R.drawable.golf_top_02, R.drawable.golf_top_03,
    R.drawable.golf_top_04, R.drawable.golf_top_05, R.drawable.golf_top_06, R.drawable.golf_top_07,
    R.drawable.golf_top_08, R.drawable.golf_top_09, R.drawable.golf_top_10, R.drawable.golf_top_11,
    R.drawable.golf_top_12, R.drawable.golf_top_13, R.drawable.golf_top_14, R.drawable.golf_top_15,
)

@Composable
internal fun DoorRender(mask: Int, lightsOn: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier.testTag("doorRender$mask")) {
        Image(
            painter = painterResource(doorImages[mask]),
            contentDescription = stringResource(R.string.doors_top_view),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        CarLightsOverlay(lightsOn, topView = true)
    }
}

@Composable
internal fun DoorPreviewControls(preview: Int?, canMask: Int, onChange: (Int?) -> Unit) {
    val labels = listOf(
        R.string.door_front_driver, R.string.door_front_passenger,
        R.string.door_rear_driver, R.string.door_rear_passenger,
    )
    Column {
        Text(stringResource(R.string.doors_preview_label), color = MaterialTheme.colorScheme.onSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(
                selected = preview == null,
                onClick = { onChange(null) },
                label = { Text(stringResource(R.string.lights_preview_auto)) },
                modifier = Modifier.testTag("doorsPreviewAuto"),
            )
            FilterChip(
                selected = preview == 0,
                onClick = { onChange(0) },
                label = { Text(stringResource(R.string.doors_preview_closed)) },
            )
            FilterChip(
                selected = preview == 15,
                onClick = { onChange(15) },
                label = { Text(stringResource(R.string.doors_preview_open)) },
            )
        }
        // Two rows stay readable on the 1024 x 600 head unit.
        repeat(2) { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(2) { column ->
                    val index = row * 2 + column
                    val bit = 1 shl index
                    FilterChip(
                        selected = (preview ?: canMask) and bit != 0,
                        onClick = { onChange((preview ?: canMask) xor bit) },
                        label = { Text(stringResource(labels[index])) },
                        modifier = Modifier.weight(1f).testTag("doorsPreview$bit"),
                    )
                }
            }
        }
        Text(stringResource(R.string.doors_preview_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
