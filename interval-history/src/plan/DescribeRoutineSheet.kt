package dev.justinsmith.interval.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import dev.justinsmith.interval.history.HistoryDay
import dev.justinsmith.interval.history.dayHeading
import dev.justinsmith.interval.history.resources.Res
import dev.justinsmith.interval.history.resources.common_joined
import dev.justinsmith.interval.history.resources.history_goal_line
import dev.justinsmith.interval.history.resources.plan_add_confirm
import dev.justinsmith.interval.history.resources.plan_describe_field_label
import dev.justinsmith.interval.history.resources.plan_describe_hint
import dev.justinsmith.interval.history.resources.plan_describe_no_day
import dev.justinsmith.interval.history.resources.plan_describe_placeholder
import dev.justinsmith.interval.history.resources.plan_describe_title
import dev.justinsmith.interval.history.resources.plan_describe_unrecognized
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.dateOfEpochDay
import dev.justinsmith.interval.timer.weekdayOf
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.DescribeHint
import dev.justinsmith.interval.ui.IntervalSheet
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.NameField
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.StripeMark
import dev.justinsmith.interval.ui.WeekdayForm
import dev.justinsmith.interval.ui.joined
import dev.justinsmith.interval.ui.weekdayText
import dev.justinsmith.interval.ui.workoutDescription
import org.jetbrains.compose.resources.stringResource

/**
 * A routine typed in words ("tabata mon wed fri, 20 min bike saturday, 4 a week"), read as it is typed
 * into the workouts it plans, each with its days, and the weekly goal. Add to plan hands it to
 * [onPlan]; a workout the words gave no day says so and is left out. Until the words say anything, it
 * offers examples.
 */
@Composable
internal fun DescribeRoutineSheet(state: PlanUiState, onPlan: (DescribedRoutine) -> Unit, onDismiss: () -> Unit) {
    val palette = IntervalTheme.palette
    val haptics = LocalHapticFeedback.current
    val title = stringResource(Res.string.plan_describe_title)
    val words = rememberTextFieldState()
    val text = words.text.toString()
    val described = remember(text, state.choices, state.today) {
        // The choices are the Timer's workout, which fills in what the words leave out, then the presets.
        val current = state.choices.firstOrNull { it.onTimer }?.preset?.config
            ?: state.choices.firstOrNull()?.preset?.config
            ?: IntervalConfig()
        val presets = state.choices.filterNot { it.onTimer }.map { it.preset }
        describeRoutine(text, current, presets, state.today)
    }
    IntervalSheet(title = title, onDismiss = onDismiss) { hide ->
        val plan = {
            described?.takeIf { it.addsAnything }?.let {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onPlan(it)
                hide()
            }
        }
        Text(title, style = IntervalTheme.type.value, color = palette.text)
        NameField(
            words,
            placeholder = stringResource(Res.string.plan_describe_placeholder),
            label = stringResource(Res.string.plan_describe_field_label),
            maxLength = MAX_ROUTINE_LENGTH,
            onDone = { plan() },
            autoCorrectEnabled = false,
        )
        if (described == null) {
            DescribeHint(
                stringResource(
                    if (text.isBlank()) Res.string.plan_describe_hint else Res.string.plan_describe_unrecognized,
                ),
            )
        } else {
            DescribedRoutinePreview(described, state.today)
            if (described.addsAnything) {
                AccentButton(
                    stringResource(Res.string.plan_add_confirm),
                    onClick = { plan() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** A workout with a day, or a goal: something Add to plan would change. */
private val DescribedRoutine.addsAnything: Boolean get() = goal != null || plans.any { it.scheduled }

/** One row per workout, its days or that it has none, then the goal if the words gave one. */
@Composable
private fun DescribedRoutinePreview(routine: DescribedRoutine, today: Long) {
    val palette = IntervalTheme.palette
    ListCard {
        routine.plans.forEachIndexed { index, plan ->
            if (index > 0) RowDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Sizes.listRow)
                    .padding(horizontal = Spacing.space16, vertical = Spacing.space8),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
            ) {
                StripeMark(
                    if (plan.scheduled) palette.workMark else palette.textDim,
                    height = Sizes.historyMarkHeight,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        plan.workout.name,
                        style = IntervalTheme.type.label,
                        color = if (plan.scheduled) palette.text else palette.textDim,
                    )
                    Text(
                        stringResource(
                            Res.string.common_joined,
                            if (plan.scheduled) {
                                daysText(
                                    plan,
                                    today,
                                )
                            } else {
                                stringResource(Res.string.plan_describe_no_day)
                            },
                            workoutDescription(plan.workout.config),
                        ),
                        style = IntervalTheme.type.meta,
                        color = palette.textDim,
                    )
                }
            }
        }
        routine.goal?.let { goal ->
            if (routine.plans.isNotEmpty()) RowDivider()
            Text(
                stringResource(Res.string.history_goal_line, goal),
                style = IntervalTheme.type.label,
                color = palette.text,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Sizes.listRow)
                    .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
            )
        }
    }
}

/** "Mon · Wed · Fri", then the one-offs: "Tomorrow", "Sat · 24". */
@Composable
private fun daysText(plan: DescribedPlan, today: Long): String {
    val weekly = plan.weekdays.sorted().map { weekdayText(it, WeekdayForm.Short) }
    val once = plan.days.sorted().map { day ->
        val heading = when (day) {
            today -> HistoryDay.Today

            today + 1 -> HistoryDay.Tomorrow

            else -> HistoryDay.On(
                stringResource(
                    Res.string.common_joined,
                    weekdayText(weekdayOf(day), WeekdayForm.Short),
                    dateOfEpochDay(day).day.toString(),
                ),
            )
        }
        dayHeading(heading)
    }
    return joined(weekly + once)
}

/** Room for a week's workouts said in one go. */
private const val MAX_ROUTINE_LENGTH = 160
