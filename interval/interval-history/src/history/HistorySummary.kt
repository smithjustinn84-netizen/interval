package dev.justinsmith.interval.history

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import dev.justinsmith.interval.history.resources.Res
import dev.justinsmith.interval.history.resources.history_best_streak
import dev.justinsmith.interval.history.resources.history_calories
import dev.justinsmith.interval.history.resources.history_goal_cd
import dev.justinsmith.interval.history.resources.history_goal_line
import dev.justinsmith.interval.history.resources.history_goal_met
import dev.justinsmith.interval.history.resources.history_goal_ring
import dev.justinsmith.interval.history.resources.history_goal_set
import dev.justinsmith.interval.history.resources.history_goal_to_go
import dev.justinsmith.interval.history.resources.history_hours_minutes
import dev.justinsmith.interval.history.resources.history_kind_cd
import dev.justinsmith.interval.history.resources.history_kind_conditioning
import dev.justinsmith.interval.history.resources.history_kind_mobility
import dev.justinsmith.interval.history.resources.history_kind_sport_days
import dev.justinsmith.interval.history.resources.history_kind_strength
import dev.justinsmith.interval.history.resources.history_kind_tally
import dev.justinsmith.interval.history.resources.history_last_12_weeks
import dev.justinsmith.interval.history.resources.history_month_next_cd
import dev.justinsmith.interval.history.resources.history_month_previous_cd
import dev.justinsmith.interval.history.resources.history_planned_done
import dev.justinsmith.interval.history.resources.history_planned_done_value
import dev.justinsmith.interval.history.resources.history_range_all
import dev.justinsmith.interval.history.resources.history_range_month
import dev.justinsmith.interval.history.resources.history_range_week
import dev.justinsmith.interval.history.resources.history_since
import dev.justinsmith.interval.history.resources.history_streak
import dev.justinsmith.interval.history.resources.history_this_week
import dev.justinsmith.interval.history.resources.history_time
import dev.justinsmith.interval.history.resources.history_week_count
import dev.justinsmith.interval.history.resources.history_week_intervals
import dev.justinsmith.interval.history.resources.history_week_lifts
import dev.justinsmith.interval.history.resources.history_week_mobility
import dev.justinsmith.interval.history.resources.history_week_sports
import dev.justinsmith.interval.history.resources.history_weeks
import dev.justinsmith.interval.history.resources.history_weeks_cd
import dev.justinsmith.interval.history.resources.history_workouts
import dev.justinsmith.interval.timer.SECONDS_PER_HOUR
import dev.justinsmith.interval.timer.SECONDS_PER_MINUTE
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.Angles
import dev.justinsmith.interval.ui.Bar
import dev.justinsmith.interval.ui.Bars
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.EnterEasing
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalSizes
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.ProgressBar
import dev.justinsmith.interval.ui.RollingText
import dev.justinsmith.interval.ui.SegmentedPicker
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.StreakBadge
import dev.justinsmith.interval.ui.joined
import dev.justinsmith.interval.ui.popOnce
import dev.justinsmith.interval.ui.rememberRising
import dev.justinsmith.interval.ui.surfaceButton
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Week, Month or All time. */
@Composable
internal fun RangePicker(range: TrainingRange, onRange: (TrainingRange) -> Unit, modifier: Modifier = Modifier) {
    SegmentedPicker(
        options = TrainingRange.entries,
        selected = range,
        label = {
            stringResource(
                when (it) {
                    TrainingRange.Week -> Res.string.history_range_week
                    TrainingRange.Month -> Res.string.history_range_month
                    TrainingRange.AllTime -> Res.string.history_range_all
                },
            )
        },
        onSelect = onRange,
        modifier = modifier,
    )
}

/** The summary card's frame: a surface card with its sections spaced out. */
@Composable
internal fun SummaryCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(Corners.radius18)
            .background(IntervalTheme.palette.surface)
            .padding(horizontal = Spacing.space20, vertical = Spacing.space16),
        verticalArrangement = Arrangement.spacedBy(Spacing.space16),
        content = content,
    )
}

/**
 * This week: the goal ring and streak, a bar a kind of what was done against what was planned, the time and
 * calories, and a block per day, each opening its day.
 */
@Composable
internal fun WeekSummary(
    state: HistoryUiState,
    onDay: (Long) -> Unit,
    onSetGoal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SummaryCard(modifier) {
        GoalRow(state.weekGoal, onSetGoal, split = state.byKind.isEmpty())
        if (state.byKind.isNotEmpty()) KindBars(state.byKind)
        // The figures once any time is logged: a week not begun, or of a class alone, has no 0:00 to show at
        // this size.
        if (state.summary.totalSeconds > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
                Stat(
                    stringResource(Res.string.history_time),
                    state.summary.time,
                    state.summary.totalSeconds,
                    accent = true,
                )
                state.summary.calories?.let { Stat(stringResource(Res.string.history_calories), it.toString(), it) }
            }
        }
        WeekStrip(state.week, onDay)
    }
}

/** A row a kind: its name, a bar of what was done of it against the plan, and the two as "1 of 2". */
@Composable
private fun KindBars(tallies: List<KindTally>, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        tallies.forEach { tally ->
            val name = stringResource(
                when (tally.kind) {
                    WorkoutKind.Strength -> Res.string.history_kind_strength
                    WorkoutKind.Conditioning -> Res.string.history_kind_conditioning
                    WorkoutKind.Mobility -> Res.string.history_kind_mobility
                    WorkoutKind.Sport -> Res.string.history_kind_sport_days
                },
            )
            val description = stringResource(Res.string.history_kind_cd, name, tally.done, tally.of)
            Row(
                Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
            ) {
                Text(
                    name,
                    style = IntervalTheme.type.meta,
                    color = palette.text,
                    // At its width at least, so the bars line up; wider with a large font, rather than wrapping.
                    modifier = Modifier.widthIn(min = IntervalSizes.kindLabel),
                )
                ProgressBar(tally.done.toFloat() / tally.of, kindColor(tally.kind), Modifier.weight(1f))
                Text(
                    stringResource(Res.string.history_kind_tally, tally.done, tally.of),
                    style = IntervalTheme.type.meta,
                    color = palette.textDim,
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(min = IntervalSizes.kindTally),
                )
            }
        }
    }
}

/** The mark of a kind, as the log's rows stripe it: the accent for Lift, work, rest for mobility, a sport dim. */
@Composable
internal fun kindColor(kind: WorkoutKind): Color {
    val palette = IntervalTheme.palette
    return when (kind) {
        WorkoutKind.Strength -> palette.accent
        WorkoutKind.Conditioning -> palette.workMark
        WorkoutKind.Mobility -> palette.restMark
        WorkoutKind.Sport -> palette.textDim
    }
}

/**
 * This week against the goal: the ring, what is left of it and the streak, or the count and the way to a goal.
 * With [split] it says the week by kind in a line, where nothing beside it shows them.
 */
@Composable
internal fun GoalRow(week: WeekGoal, onSetGoal: () -> Unit, split: Boolean = true) {
    val palette = IntervalTheme.palette
    val goal = week.goal
    if (goal == null) {
        // The way to a goal goes under the count where the two don't fit side by side (a narrow column),
        // rather than squeezing it.
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(Spacing.space8),
        ) {
            Column(Modifier.padding(end = Spacing.space16)) {
                Text(
                    stringResource(Res.string.history_this_week),
                    style = IntervalTheme.type.caption,
                    color = palette.textDim,
                )
                Text(
                    pluralStringResource(Res.plurals.history_week_count, week.count, week.count),
                    style = IntervalTheme.type.title,
                    color = palette.text,
                )
                if (split) WeekSplit(week)
            }
            ChipButton(
                stringResource(Res.string.history_goal_set),
                onSetGoal,
                Modifier.align(Alignment.CenterVertically),
            )
        }
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.space16)) {
        GoalRing(week.count, goal)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.space4)) {
            Text(
                stringResource(Res.string.history_this_week),
                style = IntervalTheme.type.caption,
                color = palette.textDim,
            )
            // The ring beside it carries the numbers: this says only what is left of them.
            val left = goal - week.count
            Text(
                if (left <= 0) {
                    stringResource(Res.string.history_goal_met)
                } else {
                    pluralStringResource(Res.plurals.history_goal_to_go, left, left)
                },
                style = IntervalTheme.type.title,
                color = palette.text,
            )
            if (split) WeekSplit(week)
            week.streak?.current?.takeIf { it > 0 }?.let { StreakChip(it) }
        }
    }
}

/** The streak as a small chip: a flame and "3-week streak". */
@Composable
internal fun StreakChip(weeks: Int, modifier: Modifier = Modifier) {
    StreakBadge(weeks, label = { pluralStringResource(Res.plurals.history_streak, it, it) }, modifier)
}

/**
 * This week's workouts by kind, "1 lift · 2 intervals · 1 mobility · 1 sport day", once a session on Lift, a
 * sport day or mobility is among them; a week of intervals alone says nothing more than its count.
 */
@Composable
private fun WeekSplit(week: WeekGoal) {
    val intervals = week.count - week.lifts - week.sports - week.mobility
    if (intervals == week.count) return
    val parts = listOfNotNull(
        week.lifts.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.history_week_lifts, it, it) },
        intervals.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.history_week_intervals, it, it) },
        week.mobility.takeIf { it > 0 }?.let { stringResource(Res.string.history_week_mobility, it) },
        week.sports.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.history_week_sports, it, it) },
    )
    Text(
        joined(parts),
        style = IntervalTheme.type.meta,
        color = IntervalTheme.palette.textDim,
    )
}

/** This week's workouts as a share of [goal], drawn closing as it fills; the figure inside reads "3/4". */
@Composable
private fun GoalRing(count: Int, goal: Int, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val share = remember { Animatable(0f) }
    val met = count >= goal
    // Closed on the goal: set as the sweep lands, so the ring ignites and pops as it comes round, not before.
    var closed by remember { mutableStateOf(met) }
    LaunchedEffect(count, goal) {
        share.animateTo((count.toFloat() / goal).coerceAtMost(1f), tween(Motion.ENTER_MILLIS, easing = EnterEasing))
        closed = met
    }
    val description = stringResource(Res.string.history_goal_cd, count, goal)
    Box(
        modifier
            .size(IntervalSizes.goalRing)
            .popOnce(closed, Motion.RING_CLOSE_POP_SCALE)
            .clearAndSetSemantics { contentDescription = description }
            .drawWithCache {
                val stroke = IntervalSizes.goalRingStroke.toPx()
                val topLeft = Offset(stroke / 2, stroke / 2)
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val trackStroke = Stroke(stroke)
                val arcStroke = Stroke(stroke, cap = StrokeCap.Round)
                // Met, the ring wears the three phase colours round: the intro's ripple, and the summary's.
                val metBrush = Brush.sweepGradient(
                    listOf(palette.prepMark, palette.workMark, palette.restMark, palette.prepMark),
                )
                onDrawBehind {
                    drawArc(palette.surfaceVariant, 0f, Angles.FULL_TURN, false, topLeft, arcSize, style = trackStroke)
                    if (share.value > 0f && closed) {
                        rotate(Angles.TWELVE_O_CLOCK) {
                            drawArc(
                                metBrush,
                                0f,
                                Angles.FULL_TURN * share.value,
                                false,
                                topLeft,
                                arcSize,
                                style = arcStroke,
                            )
                        }
                    } else if (share.value > 0f) {
                        drawArc(
                            palette.accent,
                            Angles.TWELVE_O_CLOCK,
                            Angles.FULL_TURN * share.value,
                            false,
                            topLeft,
                            arcSize,
                            style = arcStroke,
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        RollingText(
            stringResource(Res.string.history_goal_ring, count, goal),
            rising = rememberRising(count),
            style = IntervalTheme.type.value,
            color = palette.text,
        )
    }
}

/**
 * A small figure under its caption, rolling to its new value as [rank] (the number behind it) rises or
 * falls; a figure that never changes on its page passes none.
 */
@Composable
internal fun RowScope.Stat(caption: String, value: String, rank: Int = 0, accent: Boolean = false) {
    val palette = IntervalTheme.palette
    Column(Modifier.weight(1f)) {
        Text(caption, style = IntervalTheme.type.caption, color = palette.textDim)
        RollingText(
            value,
            rising = rememberRising(rank),
            style = IntervalTheme.type.stat,
            color = if (accent) palette.accent else palette.text,
        )
    }
}

/** A month: its title between the ways to the months either side, its figures, and its calendar. */
@Composable
internal fun MonthSummary(
    state: HistoryUiState,
    onShiftMonth: (Int) -> Unit,
    onDay: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = IntervalTheme.palette
    SummaryCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconAction(IntervalIcons.ChevronBack, stringResource(Res.string.history_month_previous_cd), {
                onShiftMonth(-1)
            })
            Text(
                state.month.title,
                style = IntervalTheme.type.section,
                color = palette.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            IconAction(IntervalIcons.Chevron, stringResource(Res.string.history_month_next_cd), { onShiftMonth(1) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
            Stat(stringResource(Res.string.history_workouts), state.summary.count.toString(), state.summary.count)
            Stat(stringResource(Res.string.history_time), state.summary.time, state.summary.totalSeconds, accent = true)
            if (state.summary.plannedDue > 0) {
                Stat(
                    stringResource(Res.string.history_planned_done),
                    stringResource(
                        Res.string.history_planned_done_value,
                        state.summary.plannedDone,
                        state.summary.plannedDue,
                    ),
                    state.summary.plannedDone,
                )
            }
        }
        MonthCalendar(state.month.cells, onDay)
        Legend()
    }
}

/** Everything so far: the totals, the best streak, and workouts a week over the last twelve. */
@Composable
internal fun AllTimeSummary(state: HistoryUiState, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    SummaryCard(modifier) {
        state.summary.since?.let {
            Text(
                stringResource(Res.string.history_since, it),
                style = IntervalTheme.type.caption,
                color = palette.textDim,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
            Stat(stringResource(Res.string.history_workouts), state.summary.count.toString(), state.summary.count)
            Stat(
                stringResource(Res.string.history_time),
                longTotal(state.summary.totalSeconds),
                state.summary.totalSeconds,
                accent = true,
            )
        }
        val calories = state.summary.calories
        // A best streak once there is one: "0 weeks" says nothing.
        val best = state.streak?.best?.takeIf { it > 0 }
        if (calories != null || best != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
                calories?.let { Stat(stringResource(Res.string.history_calories), it.toString(), it) }
                best?.let {
                    Stat(
                        stringResource(Res.string.history_best_streak),
                        pluralStringResource(Res.plurals.history_weeks, it, it),
                        it,
                    )
                }
            }
        }
        WeekBars(state)
    }
}

/** A clock under an hour ("48:20"), hours and minutes from one ("9h 48m"). */
@Composable
internal fun longTotal(seconds: Int): String =
    if (seconds < SECONDS_PER_HOUR) {
        formatClock(seconds)
    } else {
        stringResource(
            Res.string.history_hours_minutes,
            seconds / SECONDS_PER_HOUR,
            seconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE,
        )
    }

/** A bar a week, this week outlined as it is still going; a dashed line marks the goal when there is one. */
@Composable
private fun WeekBars(state: HistoryUiState, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val goal = state.goal
    val description = stringResource(Res.string.history_weeks_cd, state.weeks.joinToString { it.count.toString() })
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                stringResource(Res.string.history_last_12_weeks),
                style = IntervalTheme.type.caption,
                color = palette.textDim,
                modifier = Modifier.weight(1f),
            )
            goal?.let {
                Text(
                    stringResource(Res.string.history_goal_line, it),
                    style = IntervalTheme.type.caption,
                    color = palette.textDim,
                )
            }
        }
        Bars(
            bars = state.weeks.map { week ->
                Bar(week.count.toFloat(), filled = goal == null || week.count >= goal, outlined = week.current)
            },
            max = state.weeksMax.toFloat(),
            description = description,
            line = goal?.toFloat(),
        )
        Row(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(Res.string.history_this_week),
                style = IntervalTheme.type.caption,
                color = palette.accent,
            )
        }
    }
}

/** A pill-shaped quiet action, such as "Set a weekly goal". */
@Composable
internal fun ChipButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .heightIn(min = Sizes.segment)
            .surfaceButton(interaction, Corners.full, IntervalTheme.palette.surfaceVariant, onClick)
            .padding(horizontal = Spacing.space16),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = IntervalTheme.type.label, color = IntervalTheme.palette.text)
    }
}
