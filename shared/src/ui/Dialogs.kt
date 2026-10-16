package dev.justinsmith.interval.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import com.composeunstyled.TextInput
import com.composeunstyled.UnstyledTextField
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

/** Asks before an action that can't be undone: title, one line of text, a dismiss and a confirm action. */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    dismiss: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    IntervalDialog(
        title = title,
        onDismiss = onDismiss,
        actions = {
            DialogButton(dismiss, onClick = onDismiss, primary = false)
            DialogButton(confirm, onClick = onConfirm, primary = true)
        },
    ) {
        Text(text, style = IntervalTheme.type.body, color = IntervalTheme.palette.textDim)
    }
}

/** Names and saves the current workout as a preset. [summary] is its shape: "25s on · 10s off · ×8 · 4:40 total". */
@Composable
fun SavePresetDialog(summary: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val palette = IntervalTheme.palette
    val name = rememberTextFieldState()
    val save = { onSave(name.text.toString()) }
    IntervalDialog(
        title = "Save as preset",
        onDismiss = onDismiss,
        actions = {
            DialogButton("Cancel", onClick = onDismiss, primary = false)
            DialogButton("Save", onClick = save, primary = true)
        },
    ) {
        Text(
            summary,
            style = IntervalTheme.type.presetDescription,
            color = palette.textDim,
            modifier = Modifier.padding(bottom = Spacing.space6),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text("Name", style = IntervalTheme.type.groupLabel, color = palette.textDim, modifier = Modifier.weight(1f))
            Text(
                "${name.text.length} / ${IntervalRepository.MAX_PRESET_NAME}",
                style = IntervalTheme.type.hint,
                color = palette.textDim,
            )
        }
        NameField(name, onDone = save)
    }
}

/** A filled single-line field; the accent ring appears only while it has focus. */
@Composable
private fun NameField(state: TextFieldState, onDone: () -> Unit) {
    val palette = IntervalTheme.palette
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    UnstyledTextField(
        state = state,
        accessibilityLabel = "Name",
        textStyle = IntervalTheme.type.cardTitle,
        textColor = palette.text,
        cursorBrush = SolidColor(palette.accent),
        selectionColors = TextSelectionColors(handleColor = palette.accent, backgroundColor = palette.accentTint),
        lineLimits = TextFieldLineLimits.SingleLine,
        inputTransformation = InputTransformation.maxLength(IntervalRepository.MAX_PRESET_NAME),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        onKeyboardAction = { onDone() },
        interactionSource = interaction,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(min = Sizes.content / 2)
            .clip(Corners.radius12)
            .background(palette.surfaceVariant)
            .border(Sizes.border, if (focused) palette.accent else Color.Transparent, Corners.radius12)
            .padding(horizontal = Spacing.space14),
    ) {
        // The field's text sits on its vertical centre, however tall the field.
        Box(Modifier.fillMaxWidth().heightIn(min = Sizes.field), contentAlignment = Alignment.CenterStart) {
            TextInput(
                Modifier.fillMaxWidth(),
                placeholder = { Text("Custom", style = IntervalTheme.type.cardTitle, color = palette.textDim) },
            )
        }
    }
}
