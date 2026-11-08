package dev.justinsmith.interval.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.justinsmith.interval.history.resources.Res
import dev.justinsmith.interval.history.resources.history_add_workout
import dev.justinsmith.interval.history.resources.history_day_nothing
import dev.justinsmith.interval.history.resources.history_day_planned
import dev.justinsmith.interval.history.resources.history_every
import dev.justinsmith.interval.history.resources.history_log_workout
import dev.justinsmith.interval.history.resources.history_once
import dev.justinsmith.interval.history.resources.history_open_plan
import dev.justinsmith.interval.history.resources.history_remove_cd
import dev.justinsmith.interval.history.resources.history_routine_note
import dev.justinsmith.interval.history.resources.history_state_done
import dev.justinsmith.interval.history.resources.history_state_missed
import dev.justinsmith.interval.history.resources.notice_plan_removed
import dev.justinsmith.interval.history.resources.notice_sport_logged
import dev.justinsmith.interval.history.resources.notice_workout_logged
import dev.justinsmith.interval.history.resources.sport_log_it
import dev.justinsmith.interval.history.resources.sport_state_passed
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.plan.planMark
import dev.justinsmith.interval.plan.planName
import dev.justinsmith.interval.ui.DialogButton
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalSheet
import dev.justinsmith.interval.ui.IntervalSizes
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.StripeMark
import dev.justinsmith.interval.ui.joined
import dev.justinsmith.interval.ui.weekdayText
import dev.justinsmith.interval.ui.workoutDescription
import dev.justinsmith.lift.domain.ProgramDay
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One day, opened from the week strip or the calendar: its plans, each startable (a sport's, on its day or
 * after, logged; an interval workout's too, done away from the app) and a one-off removable, and the workouts done
 * and the sports logged on it. A day still to come can have a workout added on it ([onAddWorkout]); today or a day
 * gone by can have one logged on it after the fact ([onLogWorkout]). A weekly plan is changed in Plan, for every
 * week, as the sheet says. A plan of the program's next day starts [programDay]'s program.
 */
@Composable
internal fun DaySheetContent(
    sheet: DaySheet,
    actions: HistoryActions,
    onAddWorkout: () -> Unit,
    onLogWorkout: () -> Unit,
    programDay: ProgramDay? = null,
) {
    val palette = IntervalTheme.palette
    val title = dayHeading(sheet.day)
    IntervalSheet(title = title, onDismiss = { actions.onOpenDay(null) }) { hide ->
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.space16)) {
            Column {
                Text(
                    title,
                    style = IntervalTheme.type.title,
                    color = palette.text,
                    modifier = Modifier.semantics { heading() },
                )
                if (sheet.plans.isNotEmpty()) {
                    Text(
                        pluralStringResource(Res.plurals.history_day_planned, sheet.plans.size, sheet.plans.size),
                        style = IntervalTheme.type.meta,
                        color = palette.textDim,
                    )
                }
            }
            if (sheet.plans.isEmpty() && sheet.workouts.isEmpty() && sheet.sports.isEmpty()) {
                Text(
                    stringResource(Res.string.history_day_nothing),
                    style = IntervalTheme.type.body,
                    color = palette.textDim,
                )
            }
            if (sheet.plans.isNotEmpty()) {
                ListCard {
                    sheet.plans.forEachIndexed { index, plan ->
                        if (index > 0) RowDivider(Modifier.padding(start = Spacing.space20))
                        PlanRow(plan, sheet, programDay, actions)
                    }
                }
                if (sheet.plans.any { it.weekly }) {
                    Text(
                        stringResource(Res.string.history_routine_note),
                        style = IntervalTheme.type.meta,
                        color = palette.textDim,
                    )
                }
            }
            if (sheet.workouts.isNotEmpty() || sheet.sports.isNotEmpty()) {
                ListCard {
                    sheet.workouts.forEachIndexed { index, item ->
                        if (index > 0) RowDivider(Modifier.padding(start = Spacing.space20))
                        HistoryRow(item, actions.onOpenWorkout, onOpenLift = actions.onOpenLift)
                    }
                    sheet.sports.forEachIndexed { index, sport ->
                        val first = index == 0 && sheet.workouts.isEmpty()
                        if (!first) RowDivider(Modifier.padding(start = Spacing.space20))
                        SportRow(sport, sheet.epochDay, actions)
                    }
                }
            }
            DayActions(sheet, hide, onPlan = actions.onPlan, onAddWorkout = onAddWorkout, onLogWorkout = onLogWorkout)
        }
    }
}

/**
 * What the day takes: a day still to come, or today, is planned on (Open plan, Add workout); today, or a day gone
 * by, has a workout logged on it after the fact, never a day ahead. Each closes the sheet ([hide]) first, so the
 * sheet it opens comes over History rather than over this one.
 */
@Composable
private fun DayActions(
    sheet: DaySheet,
    hide: () -> Unit,
    onPlan: () -> Unit,
    onAddWorkout: () -> Unit,
    onLogWorkout: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        if (!sheet.past) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
                DialogButton(
                    stringResource(Res.string.history_open_plan),
                    {
                        hide()
                        onPlan()
                    },
                    primary = false,
                )
                DialogButton(
                    stringResource(Res.string.history_add_workout),
                    {
                        hide()
                        onAddWorkout()
                    },
                    primary = true,
                )
            }
        }
        if (sheet.canLog) {
            Row {
                DialogButton(
                    stringResource(Res.string.history_log_workout),
                    {
                        hide()
                        onLogWorkout()
                    },
                    primary = sheet.past,
                )
            }
        }
    }
}

/** Whether the day takes a workout logged after the fact: today, or one gone by. */
private val DaySheet.canLog: Boolean get() = past || day == HistoryDay.Today

@Composable
private fun PlanRow(plan: SheetPlan, sheet: DaySheet, programDay: ProgramDay?, actions: HistoryActions) {
    val palette = IntervalTheme.palette
    val workout = plan.workout
    val name = planName(workout)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.listRow)
            .padding(start = Spacing.space16, end = Spacing.space4, top = Spacing.space8, bottom = Spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        StripeMark(planMark(workout), height = IntervalSizes.historyMarkHeight)
        Column(Modifier.weight(1f)) {
            Text(name, style = IntervalTheme.type.label, color = palette.text)
            val repeat = if (plan.weekly) {
                stringResource(Res.string.history_every, weekdayText(sheet.weekday))
            } else {
                stringResource(Res.string.history_once)
            }
            val state = when (plan.state) {
                PlanState.Done -> stringResource(Res.string.history_state_done)
                PlanState.Missed -> stringResource(Res.string.history_state_missed)
                PlanState.Open -> null
                PlanState.Passed -> stringResource(Res.string.sport_state_passed)
            }
            val shape = (workout as? PlanWorkout.Interval)?.let { workoutDescription(it.config) }
            Text(
                joined(listOfNotNull(state, repeat, shape)),
                style = IntervalTheme.type.meta,
                color = palette.textDim,
            )
        }
        // Nothing here starts a sport.
        val start: (() -> Unit)? = when (workout) {
            is PlanWorkout.Interval -> ({ actions.onStart(workout.name, workout.config) })
            PlanWorkout.Lift.ProgramDay -> ({ actions.onStartLift(programDay?.program?.key) })
            PlanWorkout.Lift.AnySession -> ({ actions.onStartLift(null) })
            is PlanWorkout.SportDay -> null
        }
        if (plan.state == PlanState.Open && start != null) StartPill(primary = false, onClick = start)
        // An interval workout done away from the app, or without the Timer, is logged on its day or after.
        val interval = (workout as? PlanWorkout.Interval)?.takeIf { sheet.canLog }
        if (interval != null && plan.state != PlanState.Done) {
            val logged = stringResource(Res.string.notice_workout_logged, name)
            StartPill(
                primary = false,
                onClick = { actions.onLogWorkout(interval.config, sheet.epochDay, logged) },
                label = stringResource(Res.string.sport_log_it),
            )
        }
        // A sport is logged on its day or after, never ahead of it.
        val sport = (workout as? PlanWorkout.SportDay)?.sport?.takeIf { sheet.canLog }
        if (sport != null && plan.state != PlanState.Done) {
            val logged = stringResource(Res.string.notice_sport_logged, name)
            StartPill(
                primary = false,
                onClick = { actions.onLogSport(sport, sheet.epochDay, logged) },
                label = stringResource(Res.string.sport_log_it),
            )
        }
        if (!plan.weekly && plan.state != PlanState.Done) {
            val removed = stringResource(Res.string.notice_plan_removed, name)
            IconAction(
                IntervalIcons.Close,
                stringResource(Res.string.history_remove_cd, name),
                { actions.onRemovePlanned(plan.planId, removed) },
                haptic = HapticFeedbackType.SegmentTick,
            )
        }
    }
}
