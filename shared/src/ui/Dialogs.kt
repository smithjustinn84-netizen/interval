package dev.justinsmith.interval.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import dev.justinsmith.interval.data.IntervalRepository

/** A full-width accent button for a sheet or dialog's confirming action. */
@Composable
fun AccentButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val palette = IntervalTheme.palette
    Box(
        modifier
            .heightIn(min = Sizes.save)
            .clip(Corners.radius14)
            .background(if (enabled) palette.accent else palette.accent.copy(alpha = Opacity.DISABLED))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.space24),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = IntervalTheme.type.primaryControl, color = palette.onAccent)
    }
}

/** A Material alert dialog in the app's colours: title, one line of text, a dismiss and a confirm action. */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    dismiss: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = IntervalTheme.palette
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = Corners.radius18,
        containerColor = palette.surface,
        title = { Text(title, style = IntervalTheme.type.startLabel, color = palette.text) },
        text = { Text(text, style = IntervalTheme.type.body, color = palette.textDim) },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismiss, style = IntervalTheme.type.buttonLabel, color = palette.accent)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirm, style = IntervalTheme.type.buttonLabel, color = palette.accent)
            }
        },
    )
}

/** Names and saves the current workout as a preset. [summary] is its shape: "25s on · 10s off · ×8 · 4:40 total". */
@Composable
fun SavePresetDialog(summary: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val palette = IntervalTheme.palette
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = Corners.radius18,
        containerColor = palette.surface,
        title = {
            Column {
                Text("Save as preset", style = IntervalTheme.type.startLabel, color = palette.text)
                Text(summary, style = IntervalTheme.type.presetDescription, color = palette.textDim)
            }
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(IntervalRepository.MAX_PRESET_NAME) },
                label = { Text("Name") },
                placeholder = { Text("Custom") },
                supportingText = {
                    Text(
                        "${name.length} / ${IntervalRepository.MAX_PRESET_NAME}",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                    )
                },
                singleLine = true,
                shape = Corners.radius14,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave(name) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = palette.surface,
                    unfocusedContainerColor = palette.surface,
                    focusedBorderColor = palette.accent,
                    unfocusedBorderColor = palette.textDim,
                    focusedLabelColor = palette.accent,
                ),
                modifier = Modifier.widthIn(min = Sizes.content / 2),
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = IntervalTheme.type.buttonLabel, color = palette.textDim)
            }
        },
        confirmButton = { AccentButton("Save", onClick = { onSave(name) }) },
    )
}
