package dev.justinsmith.interval.history

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import dev.justinsmith.interval.history.resources.Res
import dev.justinsmith.interval.history.resources.history_day_cd
import dev.justinsmith.interval.history.resources.history_state_done
import dev.justinsmith.interval.history.resources.history_state_done_cd
import dev.justinsmith.interval.history.resources.history_state_missed
import dev.justinsmith.interval.history.resources.history_state_missed_cd
import dev.justinsmith.interval.history.resources.history_state_planned
import dev.justinsmith.interval.history.resources.history_state_planned_cd
import dev.justinsmith.interval.history.resources.history_state_rest_cd
import dev.justinsmith.interval.history.resources.history_today
import dev.justinsmith.interval.platform.prefersReducedMotion
import dev.justinsmith.interval.timer.DAYS_PER_WEEK
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.EnterEasing
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.Opacity
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.WeekdayForm
import dev.justinsmith.interval.ui.surfaceButton
import dev.justinsmith.interval.ui.weekdayText
import org.jetbrains.compose.resources.stringResource

/**
 * Monday to Sunday: a bar for each day worked, as tall as its time against the longest; an outlined block
 * for a day still planned, a filled one for a missed day, and a short bar for a rest day. The bars grow
 * up from the baseline as the tab opens, one after another. Each day is a button that opens it.
 */
@Composable
internal fun WeekStrip(week: List<DayCell>, onDay: (Long) -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val max = week.maxOfOrNull { it.seconds } ?: 0
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
        verticalAlignment = Alignment.Bottom,
    ) {
        week.forEachIndexed { index, day ->
            key(day.epochDay) {
                val interaction = remember { MutableInteractionSource() }
                // Each day is its own button, read by name and state: "Monday, done", "Today, planned".
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
                    Box(Modifier.fillMaxWidth().height(Sizes.weekBar), contentAlignment = Alignment.BottomCenter) {
                        DayBar(day, if (max > 0) day.seconds.toFloat() / max else 0f, index)
                    }
                    Text(
                        weekdayText(day.weekday, WeekdayForm.Initial),
                        style = IntervalTheme.type.caption,
                        color = if (day.today) palette.accent else palette.textDim,
                    )
                }
            }
        }
    }
}

@Composable
private fun DayBar(day: DayCell, share: Float, index: Int) {
    val palette = IntervalTheme.palette
    when (day.state) {
        DayState.Done -> {
            val reducedMotion = prefersReducedMotion()
            val grown = remember { Animatable(if (reducedMotion) share else 0f) }
            LaunchedEffect(share, reducedMotion) {
                if (reducedMotion) {
                    grown.snapTo(share)
                } else {
                    grown.animateTo(share, tween(Motion.ENTER_MILLIS, index * Motion.STAGGER_MILLIS, EnterEasing))
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height((Sizes.weekBar * grown.value).coerceAtLeast(Sizes.weekBarEmpty))
                    .clip(Corners.radius12)
                    .background(palette.accent),
            )
        }

        DayState.Planned -> Box(
            Modifier
                .fillMaxWidth()
                .height(Sizes.weekBarMark)
                .border(Sizes.border, palette.accent, Corners.radius12),
        )

        DayState.Missed -> Box(
            Modifier
                .fillMaxWidth()
                .height(Sizes.weekBarMark)
                .clip(Corners.radius12)
                .background(palette.surfaceVariant)
                .drawBehind {
                    // A short slash across the block: missed.
                    val half = size.minDimension / 4
                    drawLine(
                        palette.textDim,
                        Offset(center.x - half, center.y + half),
                        Offset(center.x + half, center.y - half),
                        strokeWidth = Sizes.border.toPx(),
                        cap = StrokeCap.Round,
                    )
                },
        )

        DayState.Rest -> Box(
            Modifier
                .fillMaxWidth()
                .height(Sizes.weekBarEmpty)
                .clip(Corners.full)
                .background(palette.surfaceVariant),
        )
    }
}

/** The key under the week strip, for sighted users: the strip reads its days' states aloud itself. */
@Composable
internal fun Legend(modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Row(modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(Spacing.space16)) {
        LegendItem(stringResource(Res.string.history_state_done)) {
            Box(Modifier.size(Sizes.legendSwatch).clip(Corners.radius12).background(palette.accent))
        }
        LegendItem(stringResource(Res.string.history_state_planned)) {
            Box(Modifier.size(Sizes.legendSwatch).border(Sizes.border, palette.accent, Corners.radius12))
        }
        LegendItem(stringResource(Res.string.history_state_missed)) {
            Box(Modifier.size(Sizes.legendSwatch).clip(Corners.radius12).background(palette.surfaceVariant))
        }
    }
}

@Composable
private fun LegendItem(label: String, swatch: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        swatch()
        Text(label, style = IntervalTheme.type.caption, color = IntervalTheme.palette.textDim)
    }
}

/** Whole weeks, Monday first: a filled mark for a day done, a ring for one planned, struck through when missed. */
@Composable
internal fun MonthCalendar(cells: List<DayCell>, onDay: (Long) -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.space4)) {
        Row(Modifier.clearAndSetSemantics {}) {
            for (weekday in 1..DAYS_PER_WEEK) {
                Text(
                    weekdayText(weekday, WeekdayForm.Initial),
                    style = IntervalTheme.type.caption,
                    color = palette.textDim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        cells.chunked(DAYS_PER_WEEK).forEach { week ->
            Row {
                week.forEach { cell -> CalendarDay(cell, { onDay(cell.epochDay) }, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CalendarDay(cell: DayCell, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val interaction = remember { MutableInteractionSource() }
    val description = dayDescription(cell.date, cell)
    Column(
        modifier
            .defaultMinSize(minWidth = Sizes.calendarCell, minHeight = Sizes.calendarCell)
            .surfaceButton(interaction, Corners.radius12, Color.Transparent, onClick)
            .clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.space4, Alignment.CenterVertically),
    ) {
        val mark = Modifier.size(Sizes.calendarDay).clip(Corners.full)
        val (shape, ink) = when {
            !cell.inMonth -> mark to palette.textDim.copy(alpha = Opacity.OUTSIDE_MONTH)
            cell.state == DayState.Done -> mark.background(palette.accent) to palette.onAccent
            cell.state == DayState.Planned -> mark.border(Sizes.border, palette.accent, Corners.full) to palette.text
            cell.state == DayState.Missed -> mark.background(palette.surfaceVariant) to palette.textDim
            else -> mark to palette.text
        }
        Box(shape, contentAlignment = Alignment.Center) {
            Text(cell.dayOfMonth.toString(), style = IntervalTheme.type.label, color = ink)
        }
        Box(
            Modifier
                .size(Sizes.todayMarkWidth, Sizes.todayMarkHeight)
                .clip(Corners.full)
                .background(if (cell.today) palette.accent else Color.Transparent),
        )
    }
}

/** A day and its state for screen readers: "Monday, done". */
@Composable
private fun dayDescription(name: String, cell: DayCell): String {
    val state = stringResource(
        when (cell.state) {
            DayState.Done -> Res.string.history_state_done_cd
            DayState.Planned -> Res.string.history_state_planned_cd
            DayState.Missed -> Res.string.history_state_missed_cd
            DayState.Rest -> Res.string.history_state_rest_cd
        },
    )
    return stringResource(Res.string.history_day_cd, name, state)
}
