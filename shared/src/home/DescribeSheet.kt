package dev.justinsmith.interval.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import dev.justinsmith.interval.presets.NameField
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.activity_title
import dev.justinsmith.interval.resources.describe_field_label
import dev.justinsmith.interval.resources.describe_hint
import dev.justinsmith.interval.resources.describe_kept_hint
import dev.justinsmith.interval.resources.describe_placeholder
import dev.justinsmith.interval.resources.describe_save_preset
import dev.justinsmith.interval.resources.describe_title
import dev.justinsmith.interval.resources.describe_use
import dev.justinsmith.interval.resources.preset_editor_total
import dev.justinsmith.interval.timer.ConfigField
import dev.justinsmith.interval.timer.DescribedWorkout
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.describeWorkout
import dev.justinsmith.interval.timer.format
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.timer.valueOf
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.IntervalSheet
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.SecondaryButton
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.StandardEasing
import dev.justinsmith.interval.ui.labelRes
import dev.justinsmith.interval.ui.valueColor
import org.jetbrains.compose.resources.stringResource

/**
 * A workout typed in words ("40/20 x8 on the rower"), read as it is typed into what the Timer would
 * hold: work, rest, rounds and the activity, the values the words gave in full ink and the ones they
 * left as the Timer has them dimmed, and the total with the Timer's get-ready countdown. Use puts it
 * on the Timer; Save as preset hands it to [onSaveAsPreset] once the sheet has gone, for the preset
 * editor. Until the words say anything, it offers examples.
 */
@Composable
internal fun DescribeSheet(
    state: TimerUiState,
    onUse: (IntervalConfig) -> Unit,
    onSaveAsPreset: (IntervalConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = IntervalTheme.palette
    val haptics = LocalHapticFeedback.current
    val title = stringResource(Res.string.describe_title)
    val words = rememberTextFieldState()
    val text = words.text.toString()
    val described = remember(text, state.config, state.presets) { describeWorkout(text, state.config, state.presets) }
    // Saved so the editor still opens if the sheet closes across a configuration change.
    var saving by rememberSaveable { mutableStateOf(false) }
    IntervalSheet(
        title = title,
        onDismiss = {
            if (saving) described?.let { onSaveAsPreset(it.config) }
            onDismiss()
        },
    ) { hide ->
        val use = {
            described?.let {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onUse(it.config)
                hide()
            }
        }
        Text(title, style = IntervalTheme.type.value, color = palette.text)
        NameField(
            words,
            placeholder = stringResource(Res.string.describe_placeholder),
            onDone = { use() },
            label = stringResource(Res.string.describe_field_label),
            maxLength = MAX_DESCRIPTION_LENGTH,
        )
        if (described == null) {
            DescribeHint(stringResource(Res.string.describe_hint))
        } else {
            DescribedPreview(described)
            AccentButton(
                stringResource(Res.string.describe_use),
                onClick = { use() },
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryButton(
                stringResource(Res.string.describe_save_preset),
                onClick = {
                    saving = true
                    hide()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** What the words come to: one row per value, then the total, and a note when some values are kept. */
@Composable
private fun DescribedPreview(described: DescribedWorkout) {
    val config = described.config
    ListCard {
        ConfigField.shape.forEachIndexed { index, field ->
            if (index > 0) RowDivider()
            PreviewRow(
                stringResource(field.labelRes),
                field.format(config.valueOf(field)),
                understood = field in described.understood,
                color = field.valueColor(),
            )
        }
        RowDivider()
        PreviewRow(
            stringResource(Res.string.activity_title),
            stringResource(config.activity.labelRes),
            understood = described.activityFound,
            color = IntervalTheme.palette.accent,
        )
        RowDivider()
        PreviewRow(
            stringResource(Res.string.preset_editor_total),
            formatClock(config.totalSeconds),
            understood = true,
            color = IntervalTheme.palette.text,
        )
    }
    val keptAny = described.understood.size < ConfigField.shape.size || !described.activityFound
    if (keptAny) DescribeHint(stringResource(Res.string.describe_kept_hint))
}

/** A [label] and its [value], in full ink and [color] when the words gave it and dimmed when kept. */
@Composable
private fun PreviewRow(label: String, value: String, understood: Boolean, color: Color) {
    val palette = IntervalTheme.palette
    val labelInk by animateColorAsState(
        if (understood) palette.text else palette.textDim,
        tween(Motion.STATE_MILLIS, easing = StandardEasing),
        label = "label",
    )
    val valueInk by animateColorAsState(
        if (understood) color else palette.textDim,
        tween(Motion.STATE_MILLIS, easing = StandardEasing),
        label = "value",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.listRow)
            .padding(horizontal = Spacing.space16, vertical = Spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        Text(label, style = IntervalTheme.type.label, color = labelInk, modifier = Modifier.weight(1f))
        Text(value, style = IntervalTheme.type.value, color = valueInk)
    }
}

/** A dim line under a sheet's field: examples to try, or a note on what the words left out. */
@Composable
internal fun DescribeHint(text: String) {
    Text(
        text,
        style = IntervalTheme.type.meta,
        color = IntervalTheme.palette.textDim,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.space4),
    )
}

/** Long enough for any way of saying a workout, short enough to stay one line's worth of thought. */
private const val MAX_DESCRIPTION_LENGTH = 80
