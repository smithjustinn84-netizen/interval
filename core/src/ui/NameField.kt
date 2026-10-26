package dev.justinsmith.interval.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.composeunstyled.TextFieldScope
import com.composeunstyled.TextInput
import com.composeunstyled.UnstyledTextField
import dev.justinsmith.core.resources.Res
import dev.justinsmith.core.resources.field_clear_cd
import org.jetbrains.compose.resources.stringResource

/**
 * A filled field for a name or short note, [maxLength] characters at most, read out by its [label] and showing
 * its [placeholder] while empty; the accent ring appears while focused, or [IntervalPalette.danger] when [isError].
 * When [onDone] is null, the IME action hides the soft keyboard and clears focus instead of stranding it.
 */
@Composable
fun NameField(
    state: TextFieldState,
    placeholder: String,
    label: String,
    maxLength: Int,
    modifier: Modifier = Modifier,
    onDone: (() -> Unit)? = null,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    imeAction: ImeAction = ImeAction.Done,
    autoCorrectEnabled: Boolean = true,
    isError: Boolean = false,
    multiLine: Boolean = false,
) {
    val palette = IntervalTheme.palette
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val targetBorder = when {
        isError -> palette.danger
        focused -> palette.accent
        else -> Color.Transparent
    }
    val ring by animateColorAsState(
        targetBorder,
        tween(Motion.STATE_MILLIS, easing = StandardEasing),
        label = "focusRing",
    )
    val showClear = state.text.isNotEmpty() && !multiLine
    UnstyledTextField(
        state = state,
        accessibilityLabel = label,
        textStyle = IntervalTheme.type.label,
        textColor = palette.text,
        cursorBrush = SolidColor(if (isError) palette.danger else palette.accent),
        selectionColors = TextSelectionColors(handleColor = palette.accent, backgroundColor = palette.accentTint),
        lineLimits = if (multiLine) {
            TextFieldLineLimits.MultiLine(
                minHeightInLines = MIN_MULTILINE_LINES,
                maxHeightInLines = MAX_MULTILINE_LINES,
            )
        } else {
            TextFieldLineLimits.SingleLine
        },
        inputTransformation = InputTransformation.maxLength(maxLength),
        keyboardOptions = KeyboardOptions(
            capitalization = capitalization,
            autoCorrectEnabled = autoCorrectEnabled,
            imeAction = if (multiLine) ImeAction.Default else imeAction,
        ),
        onKeyboardAction = {
            if (onDone != null) {
                onDone()
            } else {
                keyboard?.hide()
                focusManager.clearFocus()
            }
        },
        interactionSource = interaction,
        modifier = modifier
            .fillMaxWidth()
            .widthIn(min = Sizes.fieldMin)
            .clip(Corners.radius12)
            .background(palette.surfaceVariant)
            .border(Sizes.border, ring, Corners.radius12)
            .padding(start = Spacing.space16, end = if (showClear) Spacing.space4 else Spacing.space16),
    ) {
        FieldInner(placeholder, multiLine = multiLine, showClear = showClear, onClear = state::clearText)
    }
}

private const val MIN_MULTILINE_LINES = 2
private const val MAX_MULTILINE_LINES = 4

@Composable
private fun TextFieldScope.FieldInner(
    placeholder: String,
    multiLine: Boolean,
    showClear: Boolean,
    onClear: () -> Unit,
) {
    val palette = IntervalTheme.palette
    Row(
        Modifier.fillMaxWidth().heightIn(min = Sizes.field),
        verticalAlignment = if (multiLine) Alignment.Top else Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .then(if (multiLine) Modifier.padding(vertical = Spacing.space12) else Modifier),
            contentAlignment = if (multiLine) Alignment.TopStart else Alignment.CenterStart,
        ) {
            TextInput(
                Modifier.fillMaxWidth(),
                placeholder = { Text(placeholder, style = IntervalTheme.type.label, color = palette.textDim) },
            )
        }
        if (showClear) {
            val clearCd = stringResource(Res.string.field_clear_cd)
            val clearInteraction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .size(Sizes.iconButton)
                    .surfaceButton(clearInteraction, Corners.full, Color.Transparent, onClick = onClear)
                    .semantics { contentDescription = clearCd },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    IntervalIcons.Close,
                    contentDescription = null,
                    tint = palette.textDim,
                    modifier = Modifier.size(Sizes.chipIcon),
                )
            }
        }
    }
}
