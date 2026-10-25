package dev.justinsmith.interval.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.justinsmith.interval.history.resources.Res
import dev.justinsmith.interval.history.resources.common_cancel
import dev.justinsmith.interval.history.resources.common_joined
import dev.justinsmith.interval.history.resources.detail_close_cd
import dev.justinsmith.interval.history.resources.detail_delete_confirm
import dev.justinsmith.interval.history.resources.detail_delete_menu
import dev.justinsmith.interval.history.resources.detail_delete_text
import dev.justinsmith.interval.history.resources.detail_delete_title
import dev.justinsmith.interval.history.resources.detail_plan_again
import dev.justinsmith.interval.history.resources.detail_planned_done
import dev.justinsmith.interval.history.resources.detail_repeat
import dev.justinsmith.interval.history.resources.detail_total
import dev.justinsmith.interval.history.resources.detail_work
import dev.justinsmith.interval.history.resources.history_calories
import dev.justinsmith.interval.history.resources.history_ended_early
import dev.justinsmith.interval.history.resources.history_more_options_cd
import dev.justinsmith.interval.history.resources.notice_undo
import dev.justinsmith.interval.history.resources.notice_workout_deleted
import dev.justinsmith.interval.plan.AddToPlanSheet
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.BackAction
import dev.justinsmith.interval.ui.ConfirmDialog
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalMenu
import dev.justinsmith.interval.ui.IntervalMenuItem
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.LocalNotices
import dev.justinsmith.interval.ui.NoticeAction
import dev.justinsmith.interval.ui.ScrollPage
import dev.justinsmith.interval.ui.SecondaryButton
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.Timeline
import dev.justinsmith.interval.ui.workoutDescription
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.currentKoinScope

/**
 * Resolves the page's ViewModel for the workout stored under [id] and binds it to [WorkoutDetailScreen].
 * Repeating puts it on the Timer, then [onStartWorkout]; once it is gone, deleted here or cleared, [onBack].
 * [inPane] when it sits beside History's log.
 */
@Composable
fun WorkoutDetailRoute(
    id: Long,
    onBack: () -> Unit,
    onStartWorkout: () -> Unit,
    modifier: Modifier = Modifier,
    inPane: Boolean = false,
) {
    val scope = currentKoinScope()
    val viewModel = viewModel(key = "workout-$id") { WorkoutDetailViewModel(id, scope.get(), scope.get(), scope.get()) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gone = state.workout == null
    val leave by rememberUpdatedState(onBack)
    LaunchedEffect(gone) { if (gone) leave() }
    val notices = LocalNotices.current
    val undo = stringResource(Res.string.notice_undo)
    val deleted = stringResource(Res.string.notice_workout_deleted)
    WorkoutDetailScreen(
        state = state,
        onBack = onBack,
        onRepeat = {
            viewModel.putOnTimer()
            onStartWorkout()
        },
        // The notice outlives this page, which leaves once the workout is gone.
        onDelete = {
            viewModel.delete()
            notices.show(deleted, NoticeAction(undo, viewModel::restore))
        },
        onPlanWeekly = viewModel::planWeekly,
        onPlanOnce = viewModel::planOnce,
        modifier = modifier,
        inPane = inPane,
    )
}

/**
 * One finished workout: when it was, its shape and timeline, its figures, and the ways to repeat it now,
 * plan it again, or (from the overflow, behind a confirmation) delete it. Beside History's log ([inPane]) it
 * closes rather than going back, as Settings does beside the Timer.
 */
@Composable
fun WorkoutDetailScreen(
    state: WorkoutDetailUiState,
    onBack: () -> Unit,
    onRepeat: () -> Unit,
    onDelete: () -> Unit,
    onPlanWeekly: (Preset, Set<Int>) -> Unit,
    onPlanOnce: (Preset, Long) -> Unit,
    modifier: Modifier = Modifier,
    inPane: Boolean = false,
) {
    val workout = state.workout ?: return
    var menu by remember { mutableStateOf(false) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    var planning by rememberSaveable { mutableStateOf(false) }
    val title = workout.presetName ?: workoutDescription(workout.config)
    ScrollPage(
        title,
        modifier,
        navigation = { if (!inPane) BackAction(onBack) },
        actions = {
            if (inPane) IconAction(IntervalIcons.Close, stringResource(Res.string.detail_close_cd), onBack)
            IntervalMenu(
                expanded = menu,
                onExpandedChange = { menu = it },
                items = {
                    IntervalMenuItem(stringResource(Res.string.detail_delete_menu), onClick = { confirming = true })
                },
            ) {
                IconAction(
                    IntervalIcons.More,
                    stringResource(Res.string.history_more_options_cd),
                    onClick = { menu = true },
                )
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.space16)) {
            DetailFigures(workout)
            AccentButton(stringResource(Res.string.detail_repeat), onRepeat, Modifier.fillMaxWidth())
            SecondaryButton(stringResource(Res.string.detail_plan_again), { planning = true }, Modifier.fillMaxWidth())
        }
    }
    if (planning) {
        AddToPlanSheet(
            choices = state.choices,
            days = state.days,
            onPlanWeekly = onPlanWeekly,
            onPlanOnce = onPlanOnce,
            onDismiss = { planning = false },
            weekday = state.todayWeekday,
        )
    }
    if (confirming) {
        ConfirmDialog(
            title = stringResource(Res.string.detail_delete_title),
            text = stringResource(Res.string.detail_delete_text, title, dayHeading(workout.day)),
            confirm = stringResource(Res.string.detail_delete_confirm),
            dismiss = stringResource(Res.string.common_cancel),
            onConfirm = {
                confirming = false
                onDelete()
            },
            onDismiss = { confirming = false },
        )
    }
}

/** When it was, its shape, its total with any badge, its timeline and its figures. */
@Composable
private fun DetailFigures(workout: FinishedWorkout) {
    val palette = IntervalTheme.palette
    Column(Modifier.padding(horizontal = Spacing.space4)) {
        Text(
            stringResource(Res.string.common_joined, dayHeading(workout.day), workout.time),
            style = IntervalTheme.type.label,
            color = palette.textDim,
        )
        if (workout.presetName != null) {
            Text(workoutDescription(workout.config), style = IntervalTheme.type.body, color = palette.textDim)
        }
    }
    SummaryCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.detail_total),
                    style = IntervalTheme.type.caption,
                    color = palette.textDim,
                )
                Text(workout.total, style = IntervalTheme.type.display, color = palette.text)
            }
            val badge = when {
                workout.endedEarly -> stringResource(Res.string.history_ended_early)
                workout.planned -> stringResource(Res.string.detail_planned_done)
                else -> null
            }
            badge?.let { Badge(it, check = !workout.endedEarly) }
        }
        Timeline(workout.config, Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
            Stat(stringResource(Res.string.detail_work), workout.work, accent = true)
            workout.calories?.let { Stat(stringResource(Res.string.history_calories), it.toString()) }
        }
    }
}

/** A small pill of state beside the total: "Planned · done", or "ended early". */
@Composable
private fun Badge(text: String, check: Boolean) {
    val palette = IntervalTheme.palette
    Row(
        Modifier
            .padding(bottom = Spacing.space12)
            .clip(Corners.full)
            .background(palette.surfaceVariant)
            .padding(horizontal = Spacing.space12, vertical = Spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        if (check) Icon(IntervalIcons.Check, null, tint = palette.accent, modifier = Modifier.size(Sizes.chipIcon))
        Text(text, style = IntervalTheme.type.caption, color = palette.text)
    }
}
