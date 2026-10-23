package dev.justinsmith.interval.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLength
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

/**
 * A filled single-line field for a name or a line of text, [maxLength] characters at most, read out by
 * its [label] and showing its [placeholder] while empty; the accent ring appears only while it has focus.
 */
@Composable
fun NameField(
    state: TextFieldState,
    placeholder: String,
    onDone: () -> Unit,
    label: String,
    maxLength: Int,
    modifier: Modifier = Modifier,
) {
    val palette = IntervalTheme.palette
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val ring by animateColorAsState(
        if (focused) palette.accent else Color.Transparent,
        tween(Motion.STATE_MILLIS, easing = StandardEasing),
        label = "focusRing",
    )
    UnstyledTextField(
        state = state,
        accessibilityLabel = label,
        textStyle = IntervalTheme.type.label,
        textColor = palette.text,
        cursorBrush = SolidColor(palette.accent),
        selectionColors = TextSelectionColors(handleColor = palette.accent, backgroundColor = palette.accentTint),
        lineLimits = TextFieldLineLimits.SingleLine,
        inputTransformation = InputTransformation.maxLength(maxLength),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        onKeyboardAction = { onDone() },
        interactionSource = interaction,
        modifier = modifier
            .fillMaxWidth()
            .widthIn(min = Sizes.fieldMin)
            .clip(Corners.radius12)
            .background(palette.surfaceVariant)
            .border(Sizes.border, ring, Corners.radius12)
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
