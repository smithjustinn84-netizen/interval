package dev.justinsmith.interval.history

import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import dev.justinsmith.interval.history.resources.Res
import dev.justinsmith.interval.history.resources.history_kind_conditioning
import dev.justinsmith.interval.history.resources.history_kind_mobility
import dev.justinsmith.interval.history.resources.history_kind_sport_day
import dev.justinsmith.interval.history.resources.history_kind_strength
import dev.justinsmith.interval.history.resources.history_lift_any
import dev.justinsmith.interval.history.resources.history_lift_session
import dev.justinsmith.interval.history.resources.home_minutes
import dev.justinsmith.interval.history.resources.sport_counts
import dev.justinsmith.interval.plan.sportTitle
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.timer.isFlowShaped
import dev.justinsmith.interval.timer.weekdayOf
import dev.justinsmith.interval.ui.IntervalSizes
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.WeekdayForm
import dev.justinsmith.interval.ui.configDescription
import dev.justinsmith.interval.ui.joined
import dev.justinsmith.interval.ui.weekdayText
import dev.justinsmith.interval.ui.workoutDescription
import dev.justinsmith.lift.domain.program
import dev.justinsmith.lift.ui.programDayLifts
import dev.justinsmith.lift.ui.programDayLine
import dev.justinsmith.lift.ui.programName
import org.jetbrains.compose.resources.stringResource

// The words of Log's schedules: what is up next in a line, and the day column its rows and the week's done rows
// are keyed by.

/**
 * A row's day in the column at its start: "Today" in the accent, else its short weekday. A wider word at a large
 * font runs into the gap beside it rather than wrapping. Blank where the row above is the same day.
 */
@Composable
internal fun DayLabel(text: String, today: Boolean, modifier: Modifier = Modifier) {
    Text(
        text,
        style = IntervalTheme.type.caption,
        color = if (today) IntervalTheme.palette.accent else IntervalTheme.palette.textDim,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Visible,
        modifier = modifier.width(IntervalSizes.dayColumn),
    )
}

/** What the day column says of a day: "Today", else its short weekday, "Sat" (tomorrow too: it is a column). */
@Composable
internal fun dayColumn(day: HistoryDay, epochDay: Long): String =
    if (day == HistoryDay.Today) dayHeading(day) else weekdayText(weekdayOf(epochDay), WeekdayForm.Short)

/** What an item in Up next is called: the plan's name, the program's, a session on Lift, the sport's day. */
@Composable
internal fun upNextName(item: UpNextItem): String = when (item) {
    is UpNextItem.Planned -> item.name

    is UpNextItem.Lift -> item.programDay?.let { programName(it.program) }
        ?: stringResource(Res.string.history_lift_session)

    is UpNextItem.SportDay -> sportTitle(item.sport)

    is UpNextItem.Flow -> item.flow.name
}

/** The kind of what is up next, for its mark: a plan's by its activity, a session on Lift strength, a flow mobility. */
internal fun upNextKind(item: UpNextItem): WorkoutKind = when (item) {
    is UpNextItem.Planned ->
        if (item.config.activity == Activity.Mobility) WorkoutKind.Mobility else WorkoutKind.Conditioning

    is UpNextItem.Lift -> WorkoutKind.Strength

    is UpNextItem.Flow -> WorkoutKind.Mobility

    is UpNextItem.SportDay -> WorkoutKind.Sport
}

/**
 * What is up next, in a line led by its kind: "Conditioning · 30s on · 30s off · ×10", "Strength · Day 1 of 12 ·
 * week 1 · Deadlift · Goblet squat", "Mobility · 6 moves", "Sport day · Counts toward your week".
 */
@Composable
internal fun upNextMeta(item: UpNextItem): String = when (item) {
    is UpNextItem.Planned -> kindDescription(item.config)

    is UpNextItem.Lift -> {
        val day = item.programDay
        if (day == null) {
            joined(
                listOf(stringResource(Res.string.history_kind_strength), stringResource(Res.string.history_lift_any)),
            )
        } else {
            joined(
                listOfNotNull(
                    stringResource(Res.string.history_kind_strength),
                    programDayLine(day),
                    programDayLifts(day).takeIf { it.isNotEmpty() },
                ),
            )
        }
    }

    is UpNextItem.Flow -> workoutDescription(item.flow.config)

    is UpNextItem.SportDay ->
        joined(listOf(stringResource(Res.string.history_kind_sport_day), stringResource(Res.string.sport_counts)))
}

/** How long what is up next takes, as a figure: a workout's clock, a program day's "15 min"; a sport has none. */
@Composable
internal fun upNextFigure(item: UpNextItem): String? = when (item) {
    is UpNextItem.Planned -> formatClock(item.config.totalSeconds)
    is UpNextItem.Lift -> item.minutes?.let { stringResource(Res.string.home_minutes, it) }
    is UpNextItem.Flow -> formatClock(item.totalSeconds)
    is UpNextItem.SportDay -> null
}

/**
 * A workout's shape led by its kind, as every row of Log and Today words one: "Conditioning · 20s on · 10s off ·
 * ×8", "Mobility · 6 moves" (a flow's description says its kind itself), "Mobility · 1m on · ×5" for anything else
 * timed as mobility.
 */
@Composable
internal fun kindDescription(config: IntervalConfig): String = when {
    config.isFlowShaped -> workoutDescription(config)

    config.activity == Activity.Mobility ->
        joined(listOf(stringResource(Res.string.history_kind_mobility), configDescription(config)))

    else -> joined(listOf(stringResource(Res.string.history_kind_conditioning), workoutDescription(config)))
}
