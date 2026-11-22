package dev.justinsmith.interval.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.today.resources.Res
import dev.justinsmith.interval.today.resources.home_coach_conditioning
import dev.justinsmith.interval.today.resources.home_coach_done
import dev.justinsmith.interval.today.resources.home_coach_done_goal
import dev.justinsmith.interval.today.resources.home_coach_grip
import dev.justinsmith.interval.today.resources.home_coach_hard
import dev.justinsmith.interval.today.resources.home_coach_heart
import dev.justinsmith.interval.today.resources.home_coach_mat
import dev.justinsmith.interval.today.resources.home_coach_meets_goal
import dev.justinsmith.interval.today.resources.home_coach_more
import dev.justinsmith.interval.today.resources.home_coach_rest
import dev.justinsmith.interval.today.resources.home_coach_sport
import dev.justinsmith.interval.today.resources.home_coach_sport_flow
import dev.justinsmith.interval.today.resources.home_coach_steps
import dev.justinsmith.interval.today.resources.home_coach_the_mat
import dev.justinsmith.interval.today.resources.home_coach_the_run
import dev.justinsmith.interval.today.resources.home_coach_the_wall
import dev.justinsmith.interval.today.resources.home_coach_toward_goal
import dev.justinsmith.interval.today.resources.home_day_caption
import dev.justinsmith.interval.today.resources.home_day_class
import dev.justinsmith.interval.today.resources.home_day_climbing
import dev.justinsmith.interval.today.resources.home_day_conditioning
import dev.justinsmith.interval.today.resources.home_day_done
import dev.justinsmith.interval.today.resources.home_day_mobility
import dev.justinsmith.interval.today.resources.home_day_rest
import dev.justinsmith.interval.today.resources.home_day_run
import dev.justinsmith.interval.today.resources.home_day_sport
import dev.justinsmith.interval.today.resources.home_day_strength
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalSizes
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.Opacity
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.weekdayText
import dev.justinsmith.lift.domain.ReadinessReason
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The line over the title, as a coach would open the day: the day and what kind it is ("Thursday · class day").
 * On a plain training day it is all Today says of it.
 */
@Composable
internal fun DayCaption(weekday: Int?, kind: DayKind, modifier: Modifier = Modifier) {
    val kindText = dayKindText(kind)
    Text(
        weekday?.let { stringResource(Res.string.home_day_caption, weekdayText(it), kindText) } ?: kindText,
        style = IntervalTheme.type.caption,
        color = IntervalTheme.palette.textDim,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * The one thing worth saying of the day, as a tinted readiness banner under the week strip: that it is a day to
 * take easier and why, that it is the mat's and to loosen up first, that it is done, that rest is part of the week,
 * or on a plain training day where today takes the week, or how much it holds.
 */
@Composable
internal fun CoachLine(note: CoachNote, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    // Warm for a day to take care with, the rest mark for rest and a day done, quiet for a plain training day's word.
    val (fill, badge, border) = when (note) {
        is CoachNote.Taxed, is CoachNote.ForSport ->
            Triple(palette.accentTint, palette.accent, palette.accent.copy(alpha = Opacity.DISABLED))

        is CoachNote.TowardGoal, is CoachNote.Steps -> Triple(palette.surfaceVariant, palette.accent, palette.line)

        is CoachNote.DayDone, CoachNote.RestDay ->
            Triple(palette.tint(palette.restMark), palette.restMark, palette.restMark.copy(alpha = Opacity.DISABLED))
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(Corners.radius18)
            .background(fill)
            .border(Sizes.hairline, border, Corners.radius18)
            .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        CoachBadge(note, badge)
        Text(
            coachText(note),
            style = IntervalTheme.type.label,
            color = palette.text,
            modifier = Modifier.weight(1f),
        )
    }
}

/** A circular tinted status medallion pairing [note]'s glyph with its banner colour. */
@Composable
private fun CoachBadge(note: CoachNote, tint: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(IntervalSizes.coachBadge)
            .clip(CircleShape)
            .background(tint.copy(alpha = BADGE_FILL_ALPHA))
            .border(Sizes.hairline, tint.copy(alpha = BADGE_BORDER_ALPHA), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(coachIcon(note), contentDescription = null, tint = tint, modifier = Modifier.size(Sizes.chipIcon))
    }
}

private fun coachIcon(note: CoachNote): ImageVector = when (note) {
    is CoachNote.Taxed -> IntervalIcons.Ease
    is CoachNote.ForSport -> IntervalIcons.Sport
    is CoachNote.DayDone -> IntervalIcons.Check
    CoachNote.RestDay -> IntervalIcons.Rest
    is CoachNote.TowardGoal -> IntervalIcons.Streak
    is CoachNote.Steps -> IntervalIcons.Today
}

private const val BADGE_FILL_ALPHA = 0.16f
private const val BADGE_BORDER_ALPHA = 0.36f

/** A day's tone as the theme draws it. */
@Composable
internal fun toneColor(tone: DayTone): Color {
    val palette = IntervalTheme.palette
    return when (tone) {
        DayTone.Accent -> palette.accent
        DayTone.Work -> palette.workMark
        DayTone.Rest -> palette.restMark
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
            ReadinessReason.HardConditioning -> Res.string.home_coach_conditioning
            ReadinessReason.HeartLoad -> Res.string.home_coach_heart
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

    is CoachNote.TowardGoal -> if (note.days >= note.goal) {
        stringResource(Res.string.home_coach_meets_goal)
    } else {
        stringResource(Res.string.home_coach_toward_goal, note.days, note.goal)
    }

    is CoachNote.Steps -> pluralStringResource(
        if (note.started) Res.plurals.home_coach_more else Res.plurals.home_coach_steps,
        note.count,
        note.count,
    )
}
