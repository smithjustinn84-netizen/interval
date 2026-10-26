package dev.justinsmith.interval.plan

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
import androidx.compose.runtime.saveable.rememberSaveable
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
import dev.justinsmith.interval.history.resources.Res
import dev.justinsmith.interval.history.resources.common_done
import dev.justinsmith.interval.history.resources.plan_reminder
import dev.justinsmith.interval.history.resources.plan_reminder_hint
import dev.justinsmith.interval.history.resources.plan_reminder_hint_steps
import dev.justinsmith.interval.history.resources.plan_reminder_less_cd
import dev.justinsmith.interval.history.resources.plan_reminder_more_cd
import dev.justinsmith.interval.history.resources.plan_reminder_quick_picks
import dev.justinsmith.interval.history.resources.plan_reminder_turn_off
import dev.justinsmith.interval.model.AppSettings
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.IntervalSheet
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.RepeatButton
import dev.justinsmith.interval.ui.RollingText
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.StandardEasing
import dev.justinsmith.interval.ui.rememberRising
import dev.justinsmith.interval.ui.surfaceButton
import org.jetbrains.compose.resources.stringResource

/**
 * The reminder's edit sheet: the time between − and + (a quarter of an hour a step, hold to repeat), the
 * usual hours in one tap, and Done. Changes apply as they are made, through [onSet], which turns the reminder
 * on with the first; opened with it off, the sheet shows where it would start, which a step, a pick or Done
 * sets. Turn off is offered only when the sheet opened on a reminder, so the first step never moves the
 * buttons under the user's finger.
 */
@Composable
internal fun ReminderSheet(reminder: ReminderUi, onSet: (Int?) -> Unit, onDismiss: () -> Unit) {
    val palette = IntervalTheme.palette
    val canTurnOff = rememberSaveable { reminder.on }
    IntervalSheet(title = stringResource(Res.string.plan_reminder), onDismiss = onDismiss) { hide ->
        Text(stringResource(Res.string.plan_reminder), style = IntervalTheme.type.value, color = palette.text)
        TimeStepper(reminder, onSet)
        QuickPicks(reminder, onSet)
        Text(
            stringResource(Res.string.plan_reminder_hint),
            style = IntervalTheme.type.meta,
            color = palette.textDim,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        AccentButton(
            stringResource(Res.string.common_done),
            onClick = {
                if (!reminder.on) onSet(reminder.minuteOfDay)
                hide()
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (canTurnOff) {
            QuietTextButton(
                stringResource(Res.string.plan_reminder_turn_off),
                onClick = {
                    onSet(null)
                    hide()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The time between − and +; it rolls up or down with each step. */
@Composable
private fun TimeStepper(reminder: ReminderUi, onSet: (Int?) -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val step = AppSettings.REMINDER_STEP
    val rising = rememberRising(reminder.minuteOfDay)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RepeatButton(
                "−",
                stringResource(Res.string.plan_reminder_less_cd, step),
                enabled = true,
                onStep = { onSet(reminder.minuteOfDay - step) },
                modifier = Modifier.size(Sizes.sheetStepper),
            )
            Box(
                Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                contentAlignment = Alignment.Center,
            ) {
                // The stat size, not the display's: "12:00 PM" is twice the width of a bare number.
                RollingText(
                    reminder.text,
                    rising,
                    style = IntervalTheme.type.stat.copy(textAlign = TextAlign.Center),
                    color = palette.text,
                )
            }
            RepeatButton(
                "+",
                stringResource(Res.string.plan_reminder_more_cd, step),
                enabled = true,
                onStep = { onSet(reminder.minuteOfDay + step) },
                modifier = Modifier.size(Sizes.sheetStepper),
            )
        }
        Text(
            stringResource(Res.string.plan_reminder_hint_steps, step),
            style = IntervalTheme.type.meta,
            color = palette.textDim,
            modifier = Modifier.padding(top = Spacing.space8),
        )
    }
}

/** The usual times, one tap each, in rows; the one the reminder stands at is marked. */
@Composable
private fun QuickPicks(reminder: ReminderUi, onSet: (Int?) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        Text(
            stringResource(Res.string.plan_reminder_quick_picks),
            style = IntervalTheme.type.label,
            color = IntervalTheme.palette.textDim,
        )
        reminder.picks.chunked(PICKS_PER_ROW).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
                row.forEach { pick ->
                    TimePick(
                        pick.text,
                        selected = pick.minuteOfDay == reminder.minuteOfDay,
                        onClick = { onSet(pick.minuteOfDay) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private const val PICKS_PER_ROW = 4

@Composable
private fun TimePick(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val shape = Corners.radius12
    val spec = tween<Color>(Motion.STATE_MILLIS, easing = StandardEasing)
    val fill by animateColorAsState(if (selected) palette.accentTint else palette.surface, spec, label = "fill")
    val line by animateColorAsState(if (selected) palette.accent else palette.line, spec, label = "line")
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    Box(
        modifier
            .heightIn(min = Sizes.pillHeight)
            // One choice of several, as the Timer's quick picks are: read out as a radio button, selected or not.
            .surfaceButton(interaction, shape, fill, role = Role.RadioButton, onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onClick()
            })
            .border(Sizes.border, line, shape)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = IntervalTheme.type.control, color = palette.text)
    }
}
