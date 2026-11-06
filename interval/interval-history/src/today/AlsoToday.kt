package dev.justinsmith.interval.today

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
import androidx.compose.ui.graphics.Color
import dev.justinsmith.interval.history.StartPill
import dev.justinsmith.interval.history.UpNextItem
import dev.justinsmith.interval.history.resources.Res
import dev.justinsmith.interval.history.resources.common_joined
import dev.justinsmith.interval.history.resources.history_lift_any
import dev.justinsmith.interval.history.resources.history_lift_session
import dev.justinsmith.interval.history.resources.home_also_today
import dev.justinsmith.interval.history.resources.home_do_it_now
import dev.justinsmith.interval.history.resources.notice_sport_logged
import dev.justinsmith.interval.history.resources.sport_counts
import dev.justinsmith.interval.history.resources.sport_log_it
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.plan.sportTitle
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IntervalSizes
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.StripeMark
import dev.justinsmith.interval.ui.joined
import dev.justinsmith.interval.ui.workoutDescription
import dev.justinsmith.lift.ui.estimateLine
import dev.justinsmith.lift.ui.programDayLine
import dev.justinsmith.lift.ui.programName
import org.jetbrains.compose.resources.stringResource

/**
 * What today holds beside what leads it, on a day with a sport: the class to log while a workout leads, or the
 * program's day stood back while the class does, each a row with its own quiet button.
 */
@Composable
internal fun AlsoToday(also: List<UpNextItem>, actions: HomeActions, modifier: Modifier = Modifier) {
    Column(modifier) {
        GroupLabel(stringResource(Res.string.home_also_today))
        ListCard {
            also.forEachIndexed { index, item ->
                if (index > 0) RowDivider(Modifier.padding(start = Spacing.space4))
                when (item) {
                    is UpNextItem.SportDay -> {
                        val log = logSport(item.sport, actions)
                        AlsoRow(
                            IntervalTheme.palette.textDim,
                            sportTitle(item.sport),
                            stringResource(Res.string.sport_counts),
                        ) { StartPill(primary = false, onClick = log, label = stringResource(Res.string.sport_log_it)) }
                    }

                    is UpNextItem.Lift -> LiftRow(item, actions)

                    is UpNextItem.Planned -> AlsoRow(
                        IntervalTheme.palette.workMark,
                        item.name,
                        stringResource(
                            Res.string.common_joined,
                            workoutDescription(item.config),
                            formatClock(item.config.totalSeconds),
                        ),
                    ) { StartPill(primary = false, onClick = { actions.onStart(item.name, item.config) }) }
                }
            }
        }
    }
}

/** The lead's own Log it, under a class that leads the day. */
@Composable
internal fun LogSportButton(sport: Sport, actions: HomeActions, modifier: Modifier = Modifier) {
    AccentButton(stringResource(Res.string.sport_log_it), logSport(sport, actions), modifier)
}

/** Logging [sport] today, with the words the notice says it in: "BJJ class logged". */
@Composable
private fun logSport(sport: Sport, actions: HomeActions): () -> Unit {
    val logged = stringResource(Res.string.notice_sport_logged, sportTitle(sport))
    return { actions.onLogSport(sport, logged) }
}

/** The program's day, or a session on Lift, stood back: about how long it takes and which day, and "Do it now". */
@Composable
private fun LiftRow(item: UpNextItem.Lift, actions: HomeActions) {
    val day = item.programDay
    AlsoRow(
        IntervalTheme.palette.accent,
        day?.let { programName(it.program) } ?: stringResource(Res.string.history_lift_session),
        day?.let { joined(listOfNotNull(item.minutes?.let { minutes -> estimateLine(minutes) }, programDayLine(it))) }
            ?: stringResource(Res.string.history_lift_any),
    ) {
        StartPill(
            primary = false,
            onClick = { actions.onStartLift(day?.program?.key.takeUnless { item.resume }) },
            label = stringResource(Res.string.home_do_it_now),
        )
    }
}

/** One thing beside the lead: its kind's [mark], its [name] over a line of [detail], and its button. */
@Composable
private fun AlsoRow(mark: Color, name: String, detail: String, button: @Composable () -> Unit) {
    val palette = IntervalTheme.palette
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.listRow)
            .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        StripeMark(mark, height = IntervalSizes.historyMarkHeight)
        Column(Modifier.weight(1f)) {
            Text(name, style = IntervalTheme.type.label, color = palette.text)
            Text(detail, style = IntervalTheme.type.meta, color = palette.textDim)
        }
        button()
    }
}
