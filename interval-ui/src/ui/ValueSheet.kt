package dev.justinsmith.interval.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import dev.justinsmith.interval.timer.ConfigField
import dev.justinsmith.interval.timer.format
import dev.justinsmith.interval.ui.resources.Res
import dev.justinsmith.interval.ui.resources.common_done
import dev.justinsmith.interval.ui.resources.value_sheet_hint_hold
import dev.justinsmith.interval.ui.resources.value_sheet_hint_steps
import dev.justinsmith.interval.ui.resources.value_sheet_new_total
import dev.justinsmith.interval.ui.resources.value_sheet_quick_picks
import org.jetbrains.compose.resources.stringResource

/**
 * The edit sheet for one value: big − / + steppers (hold to repeat), one-tap common values, the
 * session total it leads to, and Done. Changes apply as they are made; Done, or any way out of the sheet, closes.
 */
@Composable
fun ValueSheet(
    field: ConfigField,
    value: Int,
    total: String?,
    onStep: (Int) -> Unit,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = IntervalTheme.palette
    // Screen readers announce the sheet by its field as it opens.
    IntervalSheet(title = stringResource(field.labelRes), onDismiss = onDismiss) { hide ->
        SheetHeader(field)
        SheetStepper(field, value, onStep)
        QuickPicks(field, value, onPick)
        if (total != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.space4)) {
                Text(
                    stringResource(Res.string.value_sheet_new_total),
                    style = IntervalTheme.type.body,
                    color = palette.textDim,
                    modifier = Modifier.weight(1f),
                )
                Text(total, style = IntervalTheme.type.section, color = palette.text)
            }
        }
        AccentButton(
            stringResource(Res.string.common_done),
            onClick = hide,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SheetHeader(field: ConfigField, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        StripeMark(field.stripe(), height = Sizes.sheetHeaderStripe)
        Column {
            Text(stringResource(field.labelRes), style = IntervalTheme.type.value, color = palette.text)
            Text(stringResource(field.hintRes), style = IntervalTheme.type.meta, color = palette.textDim)
        }
    }
}

/** The big value between − and +; it rolls up or down with each step. */
@Composable
private fun SheetStepper(field: ConfigField, value: Int, onStep: (Int) -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val rising = rememberRising(value)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FieldStepButton(field, value, more = false, onStep = onStep, Modifier.size(Sizes.sheetStepper))
            Box(
                Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                contentAlignment = Alignment.Center,
            ) {
                RollingText(
                    field.format(value),
                    rising,
                    style = IntervalTheme.type.display.copy(textAlign = TextAlign.Center),
                    color = field.valueColor(),
                )
            }
            FieldStepButton(field, value, more = true, onStep = onStep, Modifier.size(Sizes.sheetStepper))
        }
        val hint = if (field == ConfigField.Rounds) {
            stringResource(Res.string.value_sheet_hint_hold)
        } else {
            stringResource(Res.string.value_sheet_hint_steps, field.step)
        }
        Text(
            hint,
            style = IntervalTheme.type.meta,
            color = palette.textDim,
            modifier = Modifier.padding(top = Spacing.space8),
        )
    }
}

@Composable
private fun QuickPicks(field: ConfigField, value: Int, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        Text(
            stringResource(Res.string.value_sheet_quick_picks),
            style = IntervalTheme.type.label,
            color = IntervalTheme.palette.textDim,
        )
        field.quickPicks.chunked(QUICK_PICKS_PER_ROW).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
                row.forEach { pick ->
                    QuickPick(
                        field,
                        field.format(pick),
                        selected = pick == value,
                        onClick = { onPick(pick) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private const val QUICK_PICKS_PER_ROW = 4

@Composable
private fun QuickPick(
    field: ConfigField,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = IntervalTheme.palette
    val shape = Corners.radius12
    val spec = tween<Color>(Motion.STATE_MILLIS, easing = StandardEasing)
    val fill by animateColorAsState(if (selected) field.tint() else palette.surface, spec, label = "fill")
    val line by animateColorAsState(if (selected) field.stripe() else palette.line, spec, label = "line")
    val ink by animateColorAsState(if (selected) field.valueColor() else palette.text, spec, label = "ink")
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    Box(
        modifier
            .heightIn(min = Sizes.pillHeight)
            // One choice of several, as the theme picker's are: read out as a radio button, selected or not.
            .surfaceButton(interaction, shape, fill, role = Role.RadioButton, onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onClick()
            })
            .border(Sizes.border, line, shape)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = IntervalTheme.type.control, color = ink)
    }
}
