package dev.justinsmith.interval.plan

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import dev.justinsmith.interval.history.WeekdayInitials
import dev.justinsmith.interval.history.WeekdayNames
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.common_joined
import dev.justinsmith.interval.resources.plan_add_confirm
import dev.justinsmith.interval.resources.plan_add_title
import dev.justinsmith.interval.resources.plan_every_week
import dev.justinsmith.interval.resources.plan_on_timer
import dev.justinsmith.interval.resources.plan_once
import dev.justinsmith.interval.resources.plan_when
import dev.justinsmith.interval.resources.plan_workout
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalSheet
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.SegmentedPicker
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.pressScale
import dev.justinsmith.interval.ui.workoutDescription
import org.jetbrains.compose.resources.stringResource

/** Whether a new plan repeats every week or falls once. */
enum class PlanRepeat { Weekly, Once }

/**
 * Plans a workout: one of [choices], then every week on the weekdays picked or once on one of [days].
 * It opens on [repeat] with [weekday] or [epochDay] picked, so the way in (a weekday's +, a date) is
 * already chosen.
 */
@Composable
fun AddToPlanSheet(
    choices: List<PlanChoice>,
    days: List<DayChoice>,
    onPlanWeekly: (Preset, Set<Int>) -> Unit,
    onPlanOnce: (Preset, Long) -> Unit,
    onDismiss: () -> Unit,
    repeat: PlanRepeat = PlanRepeat.Weekly,
    weekday: Int = 1,
    epochDay: Long = days.firstOrNull()?.epochDay ?: 0,
) {
    val palette = IntervalTheme.palette
    // The first choice is the Timer's; a preset is the likelier pick when there is one.
    var picked by rememberSaveable { mutableIntStateOf(if (choices.size > 1) 1 else 0) }
    var mode by rememberSaveable { mutableStateOf(repeat) }
    // The weekdays picked, as bits: Monday is bit 0.
    var weekdays by rememberSaveable { mutableIntStateOf(1 shl (weekday - 1)) }
    var day by rememberSaveable { mutableLongStateOf(epochDay) }
    val title = stringResource(Res.string.plan_add_title)
    IntervalSheet(title = title, onDismiss = onDismiss) { hide ->
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.space16)) {
            Text(title, style = IntervalTheme.type.title, color = palette.text)
            if (choices.size > 1) {
                Caption(stringResource(Res.string.plan_workout))
                ListCard(Modifier.selectableGroup()) {
                    choices.forEachIndexed { index, choice ->
                        if (index > 0) RowDivider()
                        ChoiceRow(choice, selected = index == picked, onPick = { picked = index })
                    }
                }
            }
            Caption(stringResource(Res.string.plan_when))
            SegmentedPicker(
                options = PlanRepeat.entries,
                selected = mode,
                label = {
                    stringResource(
                        if (it ==
                            PlanRepeat.Weekly
                        ) {
                            Res.string.plan_every_week
                        } else {
                            Res.string.plan_once
                        },
                    )
                },
                onSelect = { mode = it },
            )
            when (mode) {
                PlanRepeat.Weekly -> WeekdayPicker(weekdays, onChange = { weekdays = it })
                PlanRepeat.Once -> DayPicker(days, day, onPick = { day = it })
            }
            AccentButton(
                stringResource(Res.string.plan_add_confirm),
                onClick = {
                    val preset = choices[picked.coerceIn(choices.indices)].preset
                    when (mode) {
                        PlanRepeat.Weekly -> onPlanWeekly(
                            preset,
                            (1..WeekdayInitials.size).filter {
                                weekdays and
                                    (1 shl (it - 1)) !=
                                    0
                            }.toSet(),
                        )

                        PlanRepeat.Once -> onPlanOnce(preset, day)
                    }
                    hide()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(text, style = IntervalTheme.type.caption, color = IntervalTheme.palette.textDim)
}

@Composable
private fun ChoiceRow(choice: PlanChoice, selected: Boolean, onPick: () -> Unit) {
    val palette = IntervalTheme.palette
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.listRow)
            .selectable(selected, interaction, LocalIndication.current, role = Role.RadioButton, onClick = onPick)
            .pressScale(interaction)
            .padding(horizontal = Spacing.space16, vertical = Spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        Box(Modifier.size(Sizes.icon), contentAlignment = Alignment.Center) {
            if (selected) Icon(IntervalIcons.Check, null, tint = palette.accent, modifier = Modifier.size(Sizes.icon))
        }
        Column(Modifier.weight(1f)) {
            Text(
                if (choice.onTimer) stringResource(Res.string.plan_on_timer) else choice.preset.name,
                style = IntervalTheme.type.label,
                color = palette.text,
            )
            val shape = workoutDescription(choice.preset.config)
            Text(
                if (choice.onTimer) stringResource(Res.string.common_joined, choice.preset.name, shape) else shape,
                style = IntervalTheme.type.meta,
                color = palette.textDim,
            )
        }
    }
}

/**
 * Monday to Sunday as toggles over [weekdays], a bit per day (Monday is bit 0). At least one day stays
 * picked, so Add always has somewhere to put the workout.
 */
@Composable
private fun WeekdayPicker(weekdays: Int, onChange: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space4)) {
        for (weekday in 1..WeekdayInitials.size) {
            val bit = 1 shl (weekday - 1)
            DayToggle(
                label = stringResource(WeekdayInitials[weekday - 1]),
                description = stringResource(WeekdayNames[weekday - 1]),
                on = weekdays and bit != 0,
                onToggle = { onChange((weekdays xor bit).takeIf { it != 0 } ?: weekdays) },
                role = Role.Checkbox,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** [days] a week to a row, the [picked] one accent; a short last row keeps the columns. */
@Composable
private fun DayPicker(days: List<DayChoice>, picked: Long, onPick: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.space4)) {
        days.chunked(WeekdayInitials.size).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space4)) {
                week.forEach { choice ->
                    val initial = stringResource(WeekdayInitials[choice.weekday - 1])
                    DayToggle(
                        label = "$initial\n${choice.dayOfMonth}",
                        description = stringResource(
                            Res.string.common_joined,
                            stringResource(WeekdayNames[choice.weekday - 1]),
                            choice.dayOfMonth.toString(),
                        ),
                        on = choice.epochDay == picked,
                        onToggle = { onPick(choice.epochDay) },
                        role = Role.RadioButton,
                        modifier = Modifier.weight(1f),
                    )
                }
                // A short week keeps its days in their columns rather than stretching them.
                repeat(WeekdayInitials.size - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A day to pick: accent when [on], following its state's colour. */
@Composable
private fun DayToggle(
    label: String,
    description: String,
    on: Boolean,
    onToggle: () -> Unit,
    role: Role,
    modifier: Modifier = Modifier,
) {
    val palette = IntervalTheme.palette
    val haptics = LocalHapticFeedback.current
    val fill by animateColorAsState(if (on) palette.accent else palette.surfaceVariant, tween(Motion.STATE_MILLIS))
    val ink by animateColorAsState(if (on) palette.onAccent else palette.text, tween(Motion.STATE_MILLIS))
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .heightIn(min = Sizes.segment)
            .pressScale(interaction)
            .clip(Corners.radius12)
            .background(fill)
            .toggleable(on, interaction, LocalIndication.current, role = role) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onToggle()
            }
            .semantics { contentDescription = description }
            .padding(vertical = Spacing.space8),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = IntervalTheme.type.label, color = ink, textAlign = TextAlign.Center)
    }
}
