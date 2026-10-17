package dev.justinsmith.interval.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.input.ImeAction
import com.composeunstyled.TextInput
import com.composeunstyled.UnstyledTextField
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.common_cancel
import dev.justinsmith.interval.resources.common_save
import dev.justinsmith.interval.resources.preset_name_count
import dev.justinsmith.interval.resources.preset_name_label
import dev.justinsmith.interval.resources.preset_save_title
import dev.justinsmith.interval.timer.Preset
import org.jetbrains.compose.resources.stringResource

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

/**
 * A filled single-line field for a preset's name, [Preset.MAX_NAME_LENGTH] at most, with its
 * [placeholder] while empty; the accent ring appears only while it has focus.
 */
@Composable
fun NameField(state: TextFieldState, placeholder: String, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    UnstyledTextField(
        state = state,
        accessibilityLabel = stringResource(Res.string.preset_name_label),
        textStyle = IntervalTheme.type.label,
        textColor = palette.text,
        cursorBrush = SolidColor(palette.accent),
        selectionColors = TextSelectionColors(handleColor = palette.accent, backgroundColor = palette.accentTint),
        lineLimits = TextFieldLineLimits.SingleLine,
        inputTransformation = InputTransformation.maxLength(Preset.MAX_NAME_LENGTH),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        onKeyboardAction = { onDone() },
        interactionSource = interaction,
        modifier = modifier
            .fillMaxWidth()
            .widthIn(min = Sizes.content / 2)
            .clip(Corners.radius12)
            .background(palette.surfaceVariant)
            .border(Sizes.border, if (focused) palette.accent else Color.Transparent, Corners.radius12)
            .padding(horizontal = Spacing.space16),
    ) {
        // The field's text sits on its vertical centre, however tall the field.
        Box(Modifier.fillMaxWidth().heightIn(min = Sizes.field), contentAlignment = Alignment.CenterStart) {
            TextInput(
                Modifier.fillMaxWidth(),
                placeholder = { Text(placeholder, style = IntervalTheme.type.label, color = palette.textDim) },
            )
        }
    }
}
