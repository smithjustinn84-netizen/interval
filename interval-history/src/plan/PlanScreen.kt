@file:OptIn(ExperimentalLayoutApi::class)

package dev.justinsmith.interval.plan

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.justinsmith.interval.history.dayHeading
import dev.justinsmith.interval.history.resources.Res
import dev.justinsmith.interval.history.resources.common_joined
import dev.justinsmith.interval.history.resources.history_streak
import dev.justinsmith.interval.history.resources.notice_plan_removed
import dev.justinsmith.interval.history.resources.notice_plan_removed_on
import dev.justinsmith.interval.history.resources.notice_undo
import dev.justinsmith.interval.history.resources.plan_add_on_date
import dev.justinsmith.interval.history.resources.plan_add_weekday_cd
import dev.justinsmith.interval.history.resources.plan_describe_cd
import dev.justinsmith.interval.history.resources.plan_every_week
import dev.justinsmith.interval.history.resources.plan_goal
import dev.justinsmith.interval.history.resources.plan_goal_less_cd
import dev.justinsmith.interval.history.resources.plan_goal_more_cd
import dev.justinsmith.interval.history.resources.plan_goal_off
import dev.justinsmith.interval.history.resources.plan_goal_text
import dev.justinsmith.interval.history.resources.plan_one_offs
import dev.justinsmith.interval.history.resources.plan_one_offs_empty
import dev.justinsmith.interval.history.resources.plan_remove_cd
import dev.justinsmith.interval.history.resources.plan_remove_on_cd
import dev.justinsmith.interval.history.resources.plan_rest
import dev.justinsmith.interval.history.resources.plan_routine
import dev.justinsmith.interval.history.resources.plan_title
import dev.justinsmith.interval.model.AppSettings
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.ui.BackAction
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.LocalNotices
import dev.justinsmith.interval.ui.NoticeAction
import dev.justinsmith.interval.ui.RepeatButton
import dev.justinsmith.interval.ui.RollingText
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.StripeMark
import dev.justinsmith.interval.ui.TabGrid
import dev.justinsmith.interval.ui.WeekdayForm
import dev.justinsmith.interval.ui.rememberRising
import dev.justinsmith.interval.ui.surfaceButton
import dev.justinsmith.interval.ui.weekdayText
import dev.justinsmith.interval.ui.workoutDescription
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.currentKoinScope

/** Resolves the plan's ViewModel and binds it to [PlanScreen]. */
@Composable
fun PlanRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val scope = currentKoinScope()
    val viewModel = viewModel { PlanViewModel(scope.get(), scope.get(), scope.get(), scope.get()) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notices = LocalNotices.current
    val undo = stringResource(Res.string.notice_undo)
    PlanScreen(
        state = state,
        onBack = onBack,
        onStepGoal = viewModel::stepGoal,
        onPlanWeekly = viewModel::planWeekly,
        onPlanOnce = viewModel::planOnce,
        onPlanRoutine = viewModel::planDescribed,
        onRemove = { planId, removedText ->
            viewModel.removePlanned(planId)?.let { plan ->
                notices.show(removedText, NoticeAction(undo) { viewModel.restorePlanned(plan) })
            }
        },
        modifier = modifier,
    )
}

// What the add sheet opens on: closed, a weekday's + (1 to 7), or a date.
private const val ADD_CLOSED = 0
private const val ADD_ON_DATE = -1

/**
 * The plan: the weekly goal's stepper, the routine as a row per weekday with its workouts and a + to add
 * one, and the one-offs still to come. Each planned workout comes off with its own remove button, which
 * hands [onRemove] its id and the line saying what went ("Removed Tabata from Monday").
 */
@Composable
fun PlanScreen(
    state: PlanUiState,
    onBack: () -> Unit,
    onStepGoal: (Int) -> Unit,
    onPlanWeekly: (Preset, Set<Int>) -> Unit,
    onPlanOnce: (Preset, Long) -> Unit,
    onPlanRoutine: (DescribedRoutine) -> Unit,
    onRemove: (planId: Long, removedText: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var adding by rememberSaveable { mutableIntStateOf(ADD_CLOSED) }
    var describing by rememberSaveable { mutableStateOf(false) }
    // One column on a phone; wider, the routine beside the goal and the one-offs.
    TabGrid(
        stringResource(Res.string.plan_title),
        modifier,
        navigation = { BackAction(onBack) },
        actions = {
            IconAction(IntervalIcons.Describe, stringResource(Res.string.plan_describe_cd), { describing = true })
        },
    ) {
        item(key = "goal") { GoalCard(state, onStepGoal, Modifier.padding(horizontal = Spacing.space16)) }
        item(key = "routine") {
            Column(Modifier.padding(horizontal = Spacing.space16)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    GroupLabel(stringResource(Res.string.plan_routine), Modifier.weight(1f))
                    Text(
                        stringResource(Res.string.plan_every_week),
                        style = IntervalTheme.type.caption,
                        color = IntervalTheme.palette.textDim,
                        modifier = Modifier.padding(bottom = Spacing.space8, end = Spacing.space4),
                    )
                }
                ListCard {
                    state.routine.forEachIndexed { index, day ->
                        if (index > 0) RowDivider()
                        RoutineRow(day, onAdd = { adding = day.weekday }, onRemove = onRemove)
                    }
                }
            }
        }
        item(key = "oneOffs") {
            Column(Modifier.padding(horizontal = Spacing.space16)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    GroupLabel(stringResource(Res.string.plan_one_offs), Modifier.weight(1f))
                    val interaction = remember { MutableInteractionSource() }
                    Box(
                        Modifier
                            .heightIn(min = Sizes.pillHeight)
                            .surfaceButton(interaction, Corners.radius12, Color.Transparent, { adding = ADD_ON_DATE })
                            .padding(horizontal = Spacing.space12),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(Res.string.plan_add_on_date),
                            style = IntervalTheme.type.label,
                            color = IntervalTheme.palette.accent,
                        )
                    }
                }
                ListCard {
                    if (state.oneOffs.isEmpty()) {
                        Text(
                            stringResource(Res.string.plan_one_offs_empty),
                            style = IntervalTheme.type.meta,
                            color = IntervalTheme.palette.textDim,
                            modifier = Modifier.padding(Spacing.space16),
                        )
                    }
                    state.oneOffs.forEachIndexed { index, oneOff ->
                        if (index > 0) RowDivider()
                        OneOffRow(oneOff, onRemove)
                    }
                }
            }
        }
    }
    if (adding != ADD_CLOSED) {
        AddToPlanSheet(
            choices = state.choices,
            days = state.days,
            onPlanWeekly = onPlanWeekly,
            onPlanOnce = onPlanOnce,
            onDismiss = { adding = ADD_CLOSED },
            repeat = if (adding == ADD_ON_DATE) PlanRepeat.Once else PlanRepeat.Weekly,
            weekday = if (adding == ADD_ON_DATE) state.todayWeekday else adding,
        )
    }
    if (describing) DescribeRoutineSheet(state, onPlanRoutine, onDismiss = { describing = false })
}

/** The weekly goal between − and +, "Off" below one; the streak under it while there is one. */
@Composable
private fun GoalCard(state: PlanUiState, onStepGoal: (Int) -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val goal = state.goal
    ListCard(modifier) {
        Row(
            Modifier.padding(
                start = Spacing.space20,
                end = Spacing.space12,
                top = Spacing.space12,
                bottom = Spacing.space12,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.plan_goal), style = IntervalTheme.type.label, color = palette.text)
                val detail = if (goal != null && state.streak > 0) {
                    stringResource(
                        Res.string.common_joined,
                        stringResource(Res.string.plan_goal_text),
                        pluralStringResource(Res.plurals.history_streak, state.streak, state.streak),
                    )
                } else {
                    stringResource(Res.string.plan_goal_text)
                }
                Text(detail, style = IntervalTheme.type.meta, color = palette.textDim)
            }
            RepeatButton(
                "−",
                stringResource(Res.string.plan_goal_less_cd),
                enabled = goal != null,
                onStep = { onStepGoal(-1) },
                modifier = Modifier.size(Sizes.segment),
            )
            Box(
                Modifier.width(Sizes.pillWidth).semantics { liveRegion = LiveRegionMode.Polite },
                contentAlignment = Alignment.Center,
            ) {
                RollingText(
                    goal?.toString() ?: stringResource(Res.string.plan_goal_off),
                    rising = rememberRising(goal ?: 0),
                    style = IntervalTheme.type.value,
                    color = if (goal == null) palette.textDim else palette.text,
                )
            }
            RepeatButton(
                "+",
                stringResource(Res.string.plan_goal_more_cd),
                enabled = goal == null || goal < AppSettings.WEEKLY_GOAL_RANGE.last,
                onStep = { onStepGoal(1) },
                modifier = Modifier.size(Sizes.segment),
            )
        }
    }
}

/** A weekday: its short name, a chip per workout planned on it every week (tap to remove), and +. */
@Composable
private fun RoutineRow(day: RoutineDay, onAdd: () -> Unit, onRemove: (Long, String) -> Unit) {
    val palette = IntervalTheme.palette
    val dayName = weekdayText(day.weekday)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.listRow)
            .padding(start = Spacing.space16, end = Spacing.space4, top = Spacing.space8, bottom = Spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        Text(
            weekdayText(day.weekday, WeekdayForm.Short),
            style = IntervalTheme.type.label,
            color = palette.textDim,
            modifier = Modifier.width(Sizes.pillHeight).semantics { contentDescription = dayName },
        )
        FlowRow(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
            verticalArrangement = Arrangement.spacedBy(Spacing.space8),
        ) {
            if (day.plans.isEmpty()) {
                Text(stringResource(Res.string.plan_rest), style = IntervalTheme.type.meta, color = palette.textDim)
            }
            day.plans.forEach { plan ->
                val removed = stringResource(Res.string.notice_plan_removed_on, plan.name, dayName)
                PlanChip(plan.name, stringResource(Res.string.plan_remove_on_cd, plan.name, dayName)) {
                    onRemove(plan.planId, removed)
                }
            }
        }
        IconAction(IntervalIcons.Add, stringResource(Res.string.plan_add_weekday_cd, dayName), onAdd)
    }
}

/** A planned workout as a pill with ×: the pill is the remove button, read by [description]. */
@Composable
private fun PlanChip(name: String, description: String, onRemove: () -> Unit) {
    val palette = IntervalTheme.palette
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .heightIn(min = Sizes.segment)
            .surfaceButton(
                interaction,
                Corners.full,
                palette.surfaceVariant,
                onRemove,
                haptic = HapticFeedbackType.SegmentTick,
            )
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(start = Spacing.space12, end = Spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        StripeMark(palette.workMark, height = Sizes.chipIcon)
        Text(name, style = IntervalTheme.type.label, color = palette.text)
        Icon(IntervalIcons.Close, null, tint = palette.textDim, modifier = Modifier.size(Sizes.chipIcon))
    }
}

@Composable
private fun OneOffRow(oneOff: OneOff, onRemove: (Long, String) -> Unit) {
    val palette = IntervalTheme.palette
    val removed = stringResource(Res.string.notice_plan_removed, oneOff.entry.name)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.listRow)
            .padding(start = Spacing.space16, end = Spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        StripeMark(palette.workMark, height = Sizes.historyMarkHeight)
        Column(Modifier.weight(1f)) {
            Text(oneOff.entry.name, style = IntervalTheme.type.label, color = palette.text)
            Text(
                stringResource(
                    Res.string.common_joined,
                    dayHeading(oneOff.day),
                    workoutDescription(oneOff.entry.config),
                ),
                style = IntervalTheme.type.meta,
                color = palette.textDim,
            )
        }
        IconAction(
            IntervalIcons.Close,
            stringResource(Res.string.plan_remove_cd, oneOff.entry.name, dayHeading(oneOff.day)),
            { onRemove(oneOff.entry.planId, removed) },
            haptic = HapticFeedbackType.SegmentTick,
        )
    }
}
