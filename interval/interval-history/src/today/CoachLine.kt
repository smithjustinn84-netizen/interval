package dev.justinsmith.interval.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.justinsmith.interval.history.resources.Res
import dev.justinsmith.interval.history.resources.home_coach_done
import dev.justinsmith.interval.history.resources.home_coach_done_goal
import dev.justinsmith.interval.history.resources.home_coach_grip
import dev.justinsmith.interval.history.resources.home_coach_hard
import dev.justinsmith.interval.history.resources.home_coach_mat
import dev.justinsmith.interval.history.resources.home_coach_rest
import dev.justinsmith.interval.history.resources.home_coach_sport
import dev.justinsmith.interval.history.resources.home_coach_sport_flow
import dev.justinsmith.interval.history.resources.home_coach_the_mat
import dev.justinsmith.interval.history.resources.home_coach_the_run
import dev.justinsmith.interval.history.resources.home_coach_the_wall
import dev.justinsmith.interval.history.resources.home_day_caption
import dev.justinsmith.interval.history.resources.home_day_class
import dev.justinsmith.interval.history.resources.home_day_climbing
import dev.justinsmith.interval.history.resources.home_day_conditioning
import dev.justinsmith.interval.history.resources.home_day_done
import dev.justinsmith.interval.history.resources.home_day_mobility
import dev.justinsmith.interval.history.resources.home_day_rest
import dev.justinsmith.interval.history.resources.home_day_run
import dev.justinsmith.interval.history.resources.home_day_sport
import dev.justinsmith.interval.history.resources.home_day_strength
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.weekdayText
import dev.justinsmith.lift.domain.ReadinessReason
import org.jetbrains.compose.resources.stringResource

/**
 * The line over the day, as a coach would open it: the day and what kind it is ("Thursday · class day"), and
 * under it the one thing worth saying of it, where there is one ([note]): that it is a day to take easier and
 * why, that it is the mat's and to loosen up first, that it is done, that rest is part of the week. On a plain
 * training day the kind is all it says.
 */
@Composable
internal fun CoachLine(weekday: Int?, kind: DayKind, note: CoachNote?, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.space4)) {
        val kindText = dayKindText(kind)
        Text(
            weekday?.let { stringResource(Res.string.home_day_caption, weekdayText(it), kindText) } ?: kindText,
            style = IntervalTheme.type.caption,
            color = palette.textDim,
        )
        note?.let { Text(coachText(it), style = IntervalTheme.type.section, color = palette.text) }
    }
}

@Composable
private fun dayKindText(kind: DayKind): String = when (kind) {
    is DayKind.SportDay -> when (val sport = kind.sport) {
        Sport.Bjj -> stringResource(Res.string.home_day_class)
        Sport.Climbing -> stringResource(Res.string.home_day_climbing)
        Sport.Running -> stringResource(Res.string.home_day_run)
        is Sport.Custom -> stringResource(Res.string.home_day_sport, sport.name)
    }

    DayKind.Strength -> stringResource(Res.string.home_day_strength)

    DayKind.Conditioning -> stringResource(Res.string.home_day_conditioning)

    DayKind.Mobility -> stringResource(Res.string.home_day_mobility)

    DayKind.Rest -> stringResource(Res.string.home_day_rest)

    DayKind.Done -> stringResource(Res.string.home_day_done)
}

@Composable
private fun coachText(note: CoachNote): String = when (note) {
    is CoachNote.Taxed -> stringResource(
        when (note.reason) {
            ReadinessReason.HardSession -> Res.string.home_coach_hard
            ReadinessReason.Mat -> Res.string.home_coach_mat
            ReadinessReason.Grip -> Res.string.home_coach_grip
        },
    )

    is CoachNote.ForSport -> {
        // Where the sport is done: the mat, the wall, the run; one of the user's own by its name.
        val place = when (val sport = note.sport) {
            Sport.Bjj -> stringResource(Res.string.home_coach_the_mat)
            Sport.Climbing -> stringResource(Res.string.home_coach_the_wall)
            Sport.Running -> stringResource(Res.string.home_coach_the_run)
            is Sport.Custom -> sport.name
        }
        stringResource(
            if (note.flowFirst) Res.string.home_coach_sport_flow else Res.string.home_coach_sport,
            place,
        )
    }

    is CoachNote.DayDone ->
        stringResource(if (note.goalMet) Res.string.home_coach_done_goal else Res.string.home_coach_done)

    CoachNote.RestDay -> stringResource(Res.string.home_coach_rest)
}
