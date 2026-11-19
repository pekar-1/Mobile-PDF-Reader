package com.example.pdf_reader.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.pdf_reader.R

@Composable
fun AddTagDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val start = remember { TagColors.toHsv(TagColors.palette.first()) }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var saturation by remember { mutableFloatStateOf(start[1]) }
    var brightness by remember { mutableFloatStateOf(start[2]) }
    val current = TagColors.fromHsv(hue, saturation, brightness)

    fun pickPreset(color: Color) {
        val hsv = TagColors.toHsv(color)
        hue = hsv[0]
        saturation = hsv[1]
        brightness = hsv[2]
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tag_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.tag_dialog_placeholder)) },
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(R.string.tag_dialog_color),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TagColors.palette.forEach { color ->
                        val selected = color.toArgb() == current.toArgb()
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        Color.Black.copy(alpha = 0.12f)
                                    },
                                    shape = CircleShape,
                                )
                                .clickable { pickPreset(color) },
                        )
                    }
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(current)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Box(contentAlignment = Alignment.Center) {
                    HueBar(Modifier.padding(horizontal = 10.dp))
                    Slider(
                        value = hue,
                        onValueChange = { hue = it },
                        valueRange = 0f..360f,
                        modifier = Modifier.height(30.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = current,
                            activeTrackColor = Color.Transparent,
                            inactiveTrackColor = Color.Transparent,
                        ),
                    )
                }
                Text(
                    stringResource(R.string.tag_dialog_saturation),
                    style = MaterialTheme.typography.labelSmall,
                )
                Slider(
                    value = saturation,
                    onValueChange = { saturation = it },
                    valueRange = 0f..1f,
                    modifier = Modifier.height(30.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = current,
                        activeTrackColor = current,
                    ),
                )
                Text(
                    stringResource(R.string.tag_dialog_brightness),
                    style = MaterialTheme.typography.labelSmall,
                )
                Slider(
                    value = brightness,
                    onValueChange = { brightness = it },
                    valueRange = 0.15f..1f,
                    modifier = Modifier.height(30.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = current,
                        activeTrackColor = current,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, current.toArgb()) }) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun HueBar(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.Red,
                        Color.Yellow,
                        Color.Green,
                        Color.Cyan,
                        Color.Blue,
                        Color.Magenta,
                        Color.Red,
                    ),
                ),
            ),
    )
}
