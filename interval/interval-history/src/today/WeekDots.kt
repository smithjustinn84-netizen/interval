package dev.justinsmith.interval.today

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import dev.justinsmith.interval.history.ChipButton
import dev.justinsmith.interval.history.DayCell
import dev.justinsmith.interval.history.DayState
import dev.justinsmith.interval.history.StreakChip
import dev.justinsmith.interval.history.WeekGoal
import dev.justinsmith.interval.history.dayDescription
import dev.justinsmith.interval.history.resources.Res
import dev.justinsmith.interval.history.resources.history_goal_set
import dev.justinsmith.interval.history.resources.history_this_week
import dev.justinsmith.interval.history.resources.history_today
import dev.justinsmith.interval.history.resources.history_week_count
import dev.justinsmith.interval.history.resources.home_week_done
import dev.justinsmith.interval.history.resources.home_week_goal_met
import dev.justinsmith.interval.plan.sportShort
import dev.justinsmith.interval.platform.prefersReducedMotion
import dev.justinsmith.interval.ui.Angles
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.EnterEasing
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.WeekdayForm
import dev.justinsmith.interval.ui.pressSpring
import dev.justinsmith.interval.ui.surfaceButton
import dev.justinsmith.interval.ui.weekdayText
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.hypot

/**
 * This week in a line: "This week" over how it stands against the goal ("2 of 4 done", "Goal met"), with the
 * streak beside it as a chip; with no goal yet, the count ("3 workouts") and the way to set one. The ring and the
 * week by kind are Log's: Today says it once and gets on to what is up next.
 */
@Composable
internal fun WeekHeader(week: WeekGoal, onSetGoal: () -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val goal = week.goal
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.space4)) {
            Text(
                stringResource(Res.string.history_this_week),
                style = IntervalTheme.type.caption,
                color = palette.textDim,
            )
            Text(
                when {
                    goal == null -> pluralStringResource(Res.plurals.history_week_count, week.count, week.count)
                    week.count >= goal -> stringResource(Res.string.home_week_goal_met)
                    else -> stringResource(Res.string.home_week_done, week.count, goal)
                },
                style = IntervalTheme.type.label,
                color = palette.text,
            )
        }
        if (goal == null) {
            ChipButton(stringResource(Res.string.history_goal_set), onSetGoal)
        } else {
            week.streak?.current?.takeIf { it > 0 }?.let { StreakChip(it) }
        }
    }
}

/**
 * Monday to Sunday as a block a day, as Log's week draws them: filled and checked in its kind's colour once done
 * (the accent for a session on Lift, the work mark for an interval workout, the rest mark for mobility, dim for a
 * sport day alone), outlined in the accent while planned, a dim fill with a slash once missed, and the surface for
 * a rest day; today's is ringed in the text colour. Under each is its short name ("Mon"), and its sport's short
 * name ("BJJ", "Climb") where the
 * week has any, kept level across the days. Each day is a button that opens it, read as the strip on Log is:
 * "Today, done, BJJ logged". A day done since Today last drew the week (a workout finished and come back
 * from, a class logged) draws its mark on, the fill sweeping round and the check landing; the days done as
 * Today opens are simply done. What was drawn is kept with the entry, so coming back from the workout counts.
 */
@Composable
internal fun WeekDots(week: List<DayCell>, onDay: (Long) -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val sports = week.any { it.sport != null }
    val doneNow = week.filter { it.state == DayState.Done }.map { it.epochDay }
    var doneDrawn by rememberSaveable(stateSaver = doneDaysSaver) { mutableStateOf(doneNow.toSet()) }
    LaunchedEffect(doneNow) { doneDrawn = doneNow.toSet() }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        week.forEachIndexed { index, day ->
            key(day.epochDay) {
                val interaction = remember { MutableInteractionSource() }
                val name = if (day.today) stringResource(Res.string.history_today) else weekdayText(day.weekday)
                val description = dayDescription(name, day)
                Column(
                    Modifier
                        .weight(1f)
                        .surfaceButton(interaction, Corners.radius12, Color.Transparent, { onDay(day.epochDay) })
                        .clearAndSetSemantics { contentDescription = description },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.space8),
                ) {
                    DayDot(
                        day,
                        drawOn = day.state == DayState.Done && day.epochDay !in doneDrawn,
                        delayMillis = Motion.STAGGER_MILLIS * index,
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            weekdayText(day.weekday, WeekdayForm.Short),
                            style = IntervalTheme.type.caption,
                            color = if (day.today) palette.accent else palette.textDim,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Visible,
                        )
                        if (sports) {
                            Text(
                                day.sport?.let { sportShort(it) }.orEmpty(),
                                style = IntervalTheme.type.caption,
                                color = if (day.sportLogged) palette.text else palette.textDim,
                                maxLines = 1,
                                softWrap = false,
                                // A name wider than its day's column runs into the gap beside it, not off its end.
                                overflow = TextOverflow.Visible,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The days drawn done, kept across the entry's recreation: a `Set<Long>` saved as a list. */
private val doneDaysSaver = listSaver<Set<Long>, Long>(save = { it.toList() }, restore = { it.toSet() })

/**
 * One day's block, by its state, as wide as its day's column; today's wears a ring whatever its state. A done
 * day told to [drawOn] sweeps its fill round from twelve over [Motion.ENTER_MILLIS], after [delayMillis], and
 * lands its check on a spring; at once under reduced motion.
 */
@Composable
private fun DayDot(day: DayCell, drawOn: Boolean, delayMillis: Int, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val reducedMotion = prefersReducedMotion()
    // Latched as the day turns done: the strip marks it drawn right after, which must not cut the sweep short.
    val play = remember(day.state == DayState.Done) { drawOn && !reducedMotion }
    val sweep = remember { Animatable(1f) }
    val check = remember { Animatable(1f) }
    LaunchedEffect(play) {
        if (play) {
            sweep.snapTo(0f)
            check.snapTo(0f)
            delay(delayMillis.toLong())
            sweep.animateTo(1f, tween(Motion.ENTER_MILLIS, easing = EnterEasing))
            check.animateTo(1f, pressSpring())
        } else {
            sweep.snapTo(1f)
            check.snapTo(1f)
        }
    }
    val shape = Corners.radius12
    val ring = if (day.today) Modifier.border(Sizes.border, palette.text, shape) else Modifier
    val fill = doneColor(day)
    Box(
        modifier
            .fillMaxWidth()
            .height(Sizes.weekBarMark)
            .clip(shape)
            .then(
                when (day.state) {
                    DayState.Done -> Modifier.drawBehind {
                        // A circle round the block's corners, so the sweep fills it to its edges as it closes.
                        val radius = hypot(size.width, size.height) / 2
                        drawArc(
                            fill,
                            Angles.TWELVE_O_CLOCK,
                            Angles.FULL_TURN * sweep.value,
                            useCenter = true,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2, radius * 2),
                        )
                    }

                    DayState.Planned -> Modifier.border(Sizes.border, palette.accent, shape)

                    DayState.Missed -> Modifier.background(palette.surfaceVariant).missedSlash(palette.textDim)

                    DayState.Rest -> Modifier.background(palette.surfaceVariant)
                },
            )
            .then(ring),
        contentAlignment = Alignment.Center,
    ) {
        if (day.state == DayState.Done) {
            Icon(
                IntervalIcons.Check,
                null,
                tint = palette.onAccent,
                modifier = Modifier.size(Sizes.chipIcon).graphicsLayer {
                    scaleX = check.value
                    scaleY = check.value
                },
            )
        }
    }
}

/**
 * A done day's colour: the kind most of its time went on; with nothing timed, a sport logged is dim and anything
 * else a Lift session that took no time, in the accent, as the strip on Log draws it.
 */
@Composable
private fun doneColor(day: DayCell): Color {
    val palette = IntervalTheme.palette
    val intervalSeconds = day.seconds - day.liftSeconds - day.mobilitySeconds
    return when {
        day.seconds == 0 -> if (day.sportLogged) palette.textDim else palette.accent
        day.liftSeconds >= day.mobilitySeconds && day.liftSeconds >= intervalSeconds -> palette.accent
        day.mobilitySeconds >= intervalSeconds -> palette.restMark
        else -> palette.workMark
    }
}

/** A slash from corner to corner, as the strip marks a missed day. */
private fun Modifier.missedSlash(color: Color): Modifier = drawBehind {
    val inset = size.minDimension / 4
    drawLine(
        color,
        Offset(inset, size.height - inset),
        Offset(size.width - inset, inset),
        strokeWidth = Sizes.border.toPx(),
        cap = StrokeCap.Round,
    )
}
