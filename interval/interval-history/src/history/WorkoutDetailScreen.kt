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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.justinsmith.interval.data.FlowStore
import dev.justinsmith.interval.history.resources.Res
import dev.justinsmith.interval.history.resources.common_cancel
import dev.justinsmith.interval.history.resources.common_joined
import dev.justinsmith.interval.history.resources.detail_cap_block
import dev.justinsmith.interval.history.resources.detail_cap_pace
import dev.justinsmith.interval.history.resources.detail_cap_rounds
import dev.justinsmith.interval.history.resources.detail_cap_splits
import dev.justinsmith.interval.history.resources.detail_close_cd
import dev.justinsmith.interval.history.resources.detail_delete_confirm
import dev.justinsmith.interval.history.resources.detail_delete_menu
import dev.justinsmith.interval.history.resources.detail_delete_text
import dev.justinsmith.interval.history.resources.detail_delete_title
import dev.justinsmith.interval.history.resources.detail_how_it_went
import dev.justinsmith.interval.history.resources.detail_plan_again
import dev.justinsmith.interval.history.resources.detail_planned_done
import dev.justinsmith.interval.history.resources.detail_repeat
import dev.justinsmith.interval.history.resources.detail_share_menu
import dev.justinsmith.interval.history.resources.detail_total
import dev.justinsmith.interval.history.resources.detail_work
import dev.justinsmith.interval.history.resources.history_calories
import dev.justinsmith.interval.history.resources.history_ended_early
import dev.justinsmith.interval.history.resources.history_more_options_cd
import dev.justinsmith.interval.history.resources.notice_undo
import dev.justinsmith.interval.history.resources.notice_workout_deleted
import dev.justinsmith.interval.model.CapScore
import dev.justinsmith.interval.model.PerceivedEffort
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.SnatchCount
import dev.justinsmith.interval.plan.AddToPlanSheet
import dev.justinsmith.interval.plan.DayChoice
import dev.justinsmith.interval.platform.HeartRate
import dev.justinsmith.interval.platform.Recovery
import dev.justinsmith.interval.platform.Sharer
import dev.justinsmith.interval.platform.heartRateZone
import dev.justinsmith.interval.timer.MILLIS_PER_SECOND
import dev.justinsmith.interval.timer.SECONDS_PER_MINUTE
import dev.justinsmith.interval.timer.builtInPresets
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.timer.weekdayOf
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.BackAction
import dev.justinsmith.interval.ui.ConfirmDialog
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.HeartRateChart
import dev.justinsmith.interval.ui.HeartReading
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalMenu
import dev.justinsmith.interval.ui.IntervalMenuItem
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.LocalNotices
import dev.justinsmith.interval.ui.LocalRestingHeartRate
import dev.justinsmith.interval.ui.NoticeAction
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.ScrollPage
import dev.justinsmith.interval.ui.SecondaryButton
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.SnatchQuestion
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.Timeline
import dev.justinsmith.interval.ui.ZoneBreakdown
import dev.justinsmith.interval.ui.workoutDescription
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Resolves the page's ViewModel for the workout stored under [id] and binds it to [WorkoutDetailScreen].
 * Repeating puts it on the Timer, then [onStartWorkout] (a flow, by its key, without the Timer); once it is gone, deleted here or cleared, [onBack].
 * [inPane] when it sits beside History's log.
 */
@Composable
fun WorkoutDetailRoute(
    id: Long,
    onBack: () -> Unit,
    onStartWorkout: (flow: String?) -> Unit,
    modifier: Modifier = Modifier,
    inPane: Boolean = false,
) {
    val sharer = koinInject<Sharer>()
    val flows = koinInject<FlowStore>()
    val viewModel = koinViewModel<WorkoutDetailViewModel>(key = "workout-$id") { parametersOf(id) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Each time the page comes into view without a heart rate: a watch may have synced since the summary.
    val unread = state.workout?.heartRate == null
    LifecycleStartEffect(unread) {
        if (unread) viewModel.readHeartRate()
        onStopOrDispose {}
    }
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
            val flow = state.workout?.config?.let { flowKeyOf(it, flows.flows.value) }
            if (flow == null) viewModel.putOnTimer()
            onStartWorkout(flow)
        },
        // The notice outlives this page, which leaves once the workout is gone.
        onDelete = {
            viewModel.delete()
            notices.show(deleted, NoticeAction(undo, viewModel::restore))
        },
        onPlanWeekly = viewModel::planWeekly,
        onPlanOnce = viewModel::planOnce,
        onUseMax = viewModel::useMaxHeartRate,
        modifier = modifier,
        inPane = inPane,
        onShare = sharer::shareText,
        onCountSnatches = viewModel::countSnatches,
    )
}

/**
 * One finished workout: when it was, its shape and timeline, its figures, and the ways to repeat it now,
 * plan it again, share it in words, or (from the overflow, behind a confirmation) delete it. Beside History's
 * log ([inPane]) it
 * closes rather than going back, as Settings does beside the Timer.
 */
@Composable
fun WorkoutDetailScreen(
    state: WorkoutDetailUiState,
    onBack: () -> Unit,
    onRepeat: () -> Unit,
    onDelete: () -> Unit,
    onPlanWeekly: (PlanWorkout, Set<Int>) -> Unit,
    onPlanOnce: (PlanWorkout, Long) -> Unit,
    modifier: Modifier = Modifier,
    inPane: Boolean = false,
    onShare: (String) -> Unit = {},
    onCountSnatches: (SnatchCount?) -> Unit = {},
    onUseMax: (Int) -> Unit = {},
) {
    val workout = state.workout ?: return
    var menu by rememberSaveable { mutableStateOf(false) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    var planning by rememberSaveable { mutableStateOf(false) }
    val title = workout.presetName ?: workoutDescription(workout.config)
    val shareText = workoutShareText(workout)
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
                    IntervalMenuItem(
                        stringResource(Res.string.detail_share_menu),
                        onClick = {
                            menu = false
                            onShare(shareText)
                        },
                    )
                    IntervalMenuItem(
                        stringResource(Res.string.detail_delete_menu),
                        onClick = { confirming = true },
                        destructive = true,
                    )
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
            DetailFigures(workout, onUseMax)
            workout.snatches?.let { ask -> SummaryCard { SnatchQuestion(ask, onCountSnatches) } }
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
            destructive = true,
        )
    }
}

/** When it was, its shape, its total with any badge, its timeline and its figures. */
@Composable
private fun DetailFigures(workout: FinishedWorkout, onUseMax: (Int) -> Unit) {
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
            Stat(stringResource(Res.string.detail_work), workout.work)
            workout.calories?.let { Stat(stringResource(Res.string.history_calories), it.toString()) }
        }
        workout.caps.forEach { cap -> CapFigures(cap, block = cap.block.takeIf { workout.config.isSequence }) }
    }
    workout.heartRate?.let { heartRate ->
        // As a workout's summary reads it: the average on a card of its own, its dot held still, with the heart
        // rate drawn and its time in each zone; under the card the peak, the recovery and the load.
        SummaryCard {
            HeartReading(
                heartRate.averageBpm,
                heartRateZone(heartRate.averageBpm, workout.maxHeartRate, LocalRestingHeartRate.current),
                onWorkoutGround = false,
                beating = false,
            )
            val samples = workout.heartRateSamples
            if (!samples.isNullOrEmpty()) {
                HeartRateChart(samples, maxBpm = workout.maxHeartRate)
                ZoneBreakdown(samples, maxBpm = workout.maxHeartRate)
            }
        }
        HeartInsights(heartRate.peakBpm, workout.recovery, workout.heartLoad)
        workout.peakAboveMax?.let { peak -> MaxNudge(peak, workout.maxHeartRate, onUse = { onUseMax(peak) }) }
    }
    if (workout.effort != null || workout.note != null) {
        SummaryCard {
            Text(
                stringResource(Res.string.detail_how_it_went),
                style = IntervalTheme.type.caption,
                color = palette.textDim,
            )
            workout.effort?.let { Text(effortWord(it), style = IntervalTheme.type.value, color = palette.text) }
            workout.note?.let { Text(it, style = IntervalTheme.type.body, color = palette.text) }
        }
    }
}

/**
 * A time cap's rounds and the average round, with the fastest and slowest under them; in a sequence, after its
 * [block] (0-based).
 */
@Composable
private fun CapFigures(cap: CapScore, block: Int?) {
    val palette = IntervalTheme.palette
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        block?.let {
            Text(
                stringResource(Res.string.detail_cap_block, it + 1),
                style = IntervalTheme.type.caption,
                color = palette.textDim,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
            Stat(
                stringResource(Res.string.detail_cap_rounds, cap.capSeconds / SECONDS_PER_MINUTE),
                cap.rounds.toString(),
            )
            cap.paceMillis?.let {
                Stat(stringResource(Res.string.detail_cap_pace), formatClock((it / MILLIS_PER_SECOND).toInt()))
            }
        }
        val fastest = cap.fastestMillis
        val slowest = cap.slowestMillis
        if (fastest != null && slowest != null && cap.rounds > 1) {
            Text(
                stringResource(
                    Res.string.detail_cap_splits,
                    formatClock((fastest / MILLIS_PER_SECOND).toInt()),
                    formatClock((slowest / MILLIS_PER_SECOND).toInt()),
                ),
                style = IntervalTheme.type.meta,
                color = palette.textDim,
            )
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

/** The preview's today, a Thursday the 15th. */
private const val PREVIEW_TODAY = 20_741L

@PreviewFormFactors
@Composable
private fun WorkoutDetailScreenPreview() {
    val hiit = builtInPresets[1]
    val workout = FinishedWorkout(
        id = 4,
        presetName = hiit.name,
        config = hiit.config,
        day = HistoryDay.On("Wednesday, Oct 14"),
        time = "7:05 AM",
        total = "9:40",
        work = "5:00",
        calories = 84,
        endedEarly = false,
        planned = true,
        effort = PerceivedEffort.Hard,
        note = "Legs heavy from Tuesday, held the pace to the end.",
        heartRate = HeartRate(averageBpm = 148, peakBpm = 174),
    )
    WorkoutDetailPreview(workout)
}

@PreviewFormFactors
@Composable
private fun WorkoutDetailScreenHeartPreview() {
    val hiit = builtInPresets[1]
    val samples = List(PREVIEW_HEART_SAMPLES) { index ->
        val inWork = index % PREVIEW_ROUND_SAMPLES < PREVIEW_WORK_SAMPLES
        PREVIEW_HEART_BASE + index / 2 + if (inWork) PREVIEW_WORK_RISE else 0
    }
    val fading = Recovery(listOf(34, 31, 26, 19), seconds = 60)
    WorkoutDetailPreview(
        FinishedWorkout(
            id = 4,
            presetName = hiit.name,
            config = hiit.config,
            day = HistoryDay.On("Wednesday, Oct 14"),
            time = "7:05 AM",
            total = "9:40",
            work = "5:00",
            calories = 84,
            endedEarly = false,
            planned = true,
            heartRate = HeartRate(averageBpm = 158, peakBpm = 194),
            heartRateSamples = samples,
            recovery = fading,
            heartLoad = 41,
            peakAboveMax = 194,
        ),
    )
}

private const val PREVIEW_HEART_SAMPLES = 116
private const val PREVIEW_ROUND_SAMPLES = 15
private const val PREVIEW_WORK_SAMPLES = 8
private const val PREVIEW_HEART_BASE = 112
private const val PREVIEW_WORK_RISE = 24

@Composable
private fun WorkoutDetailPreview(workout: FinishedWorkout) {
    val state = WorkoutDetailUiState(
        workout,
        days = (0 until 14).map { DayChoice(PREVIEW_TODAY + it, weekdayOf(PREVIEW_TODAY + it), 15 + it) },
        todayWeekday = 4,
    )
    IntervalTheme {
        WorkoutDetailScreen(
            state = state,
            onBack = {},
            onRepeat = {},
            onDelete = {},
            onPlanWeekly = { _, _ -> },
            onPlanOnce = { _, _ -> },
        )
    }
}
