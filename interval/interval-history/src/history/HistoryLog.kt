package dev.justinsmith.interval.history

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.justinsmith.interval.history.resources.Res
import dev.justinsmith.interval.history.resources.history_calories_value
import dev.justinsmith.interval.history.resources.history_ended_early
import dev.justinsmith.interval.history.resources.history_kind_conditioning
import dev.justinsmith.interval.history.resources.history_kind_mobility
import dev.justinsmith.interval.history.resources.history_kind_strength
import dev.justinsmith.interval.history.resources.history_remove_cd
import dev.justinsmith.interval.history.resources.history_sport_logged
import dev.justinsmith.interval.history.resources.notice_plan_removed
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.plan.sportTitle
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.isFlowShaped
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalSizes
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.StandardEasing
import dev.justinsmith.interval.ui.StripeMark
import dev.justinsmith.interval.ui.joined
import dev.justinsmith.interval.ui.pressScale
import dev.justinsmith.interval.ui.workoutDescription
import org.jetbrains.compose.resources.stringResource

/**
 * A day's heading over its card of workouts, each opening its page; the one open beside the log shows it. The
 * sports logged that day follow them, each with the way to take it off.
 */
@Composable
internal fun DaySection(
    section: HistorySection,
    actions: HistoryActions,
    selected: (HistoryItem) -> Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        GroupLabel(dayHeading(section.day))
        ListCard {
            section.items.forEachIndexed { index, item ->
                if (index > 0) RowDivider(Modifier.padding(start = Spacing.space20))
                HistoryRow(item, actions.onOpenWorkout, onOpenLift = actions.onOpenLift, selected = selected(item))
            }
            section.sports.forEachIndexed { index, sport ->
                if (index > 0 || section.items.isNotEmpty()) RowDivider(Modifier.padding(start = Spacing.space20))
                SportRow(sport, section.dayNumber, actions)
            }
        }
    }
}

/**
 * This week's finished workouts in one card, read like the schedule over it: each row keyed by its day in the
 * column, the first row of a day carrying it and the rest of that day blank under it; the sports logged on a day
 * follow its workouts, each with the way to take it off.
 */
@Composable
internal fun WeekDoneCard(
    sections: List<HistorySection>,
    actions: HistoryActions,
    selected: (HistoryItem) -> Boolean,
    modifier: Modifier = Modifier,
) {
    ListCard(modifier) {
        var rows = 0
        sections.forEach { section ->
            val label = dayColumn(section.day, section.dayNumber)
            val today = section.day == HistoryDay.Today
            var shown = 0

            // The day on its first row alone.
            fun day() = DayMark(if (shown++ == 0) label else "", today)
            section.items.forEach { item ->
                if (rows++ > 0) RowDivider(Modifier.padding(start = Spacing.space20))
                HistoryRow(
                    item,
                    actions.onOpenWorkout,
                    onOpenLift = actions.onOpenLift,
                    selected = selected(item),
                    day = day(),
                )
            }
            section.sports.forEach { sport ->
                if (rows++ > 0) RowDivider(Modifier.padding(start = Spacing.space20))
                SportRow(sport, section.dayNumber, actions, day = day())
            }
        }
    }
}

/** A row's day for its column: its words ("Today", "Wed"; none under the same day's row above) and whether today. */
internal class DayMark(val text: String, val today: Boolean)

/**
 * A day of a sport logged: its name, that it was logged, and the × that takes it off again. It has no page to
 * open: there is nothing more to it than the day. With [day], its day stands in the column before it.
 */
@Composable
internal fun SportRow(
    sport: Sport,
    epochDay: Long,
    actions: HistoryActions,
    modifier: Modifier = Modifier,
    day: DayMark? = null,
) {
    val palette = IntervalTheme.palette
    val name = sportTitle(sport)
    val removed = stringResource(Res.string.notice_plan_removed, name)
    Row(
        modifier
            .fillMaxWidth()
            // Read as one row, its remove a button of its own.
            .semantics(mergeDescendants = true) {}
            .heightIn(min = Sizes.listRow)
            .padding(start = Spacing.space16, end = Spacing.space4, top = Spacing.space8, bottom = Spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        day?.let { DayLabel(it.text, it.today) }
        StripeMark(palette.textDim, height = IntervalSizes.historyMarkHeight)
        Column(Modifier.weight(1f)) {
            Text(name, style = IntervalTheme.type.label, color = palette.text)
            Text(
                stringResource(Res.string.history_sport_logged),
                style = IntervalTheme.type.meta,
                color = palette.textDim,
            )
        }
        IconAction(
            IntervalIcons.Close,
            stringResource(Res.string.history_remove_cd, name),
            { actions.onUnlogSport(sport, epochDay, removed) },
            haptic = HapticFeedbackType.SegmentTick,
        )
    }
}

/**
 * What a row's line leads with: its kind, and its shape where its name doesn't say it. A session on Lift is
 * "Strength"; a named workout "Conditioning · 20s on · 10s off · ×8" or "Mobility · 6 moves"; one named by its
 * shape its kind alone, or nothing when the shape says it (a flow's, "Mobility · 6 moves").
 */
@Composable
private fun kindLead(item: HistoryItem): String? = when {
    item.liftUid != null -> stringResource(Res.string.history_kind_strength)
    item.presetName != null -> kindDescription(item.config)
    item.config.isFlowShaped -> null
    item.config.activity == Activity.Mobility -> stringResource(Res.string.history_kind_mobility)
    else -> stringResource(Res.string.history_kind_conditioning)
}

/**
 * A row's stripe: kettlebell sessions take the accent, as Lift draws them; mobility the rest mark, as its rows on
 * Train do; interval workouts, the work mark.
 */
@Composable
private fun markOf(item: HistoryItem): Color = when {
    item.liftUid != null -> IntervalTheme.palette.accent
    item.config.activity == Activity.Mobility -> IntervalTheme.palette.restMark
    else -> IntervalTheme.palette.workMark
}

/**
 * One finished workout, opening its page with a chevron saying so: an interval workout once stored, a
 * kettlebell session by [onOpenLift]. [selected] while its page is open beside the log, in the accent's tint.
 * With [day], its day stands in the column before it. Its line leads with its kind ("Strength · 7:42 AM",
 * "Conditioning · 20s on · 10s off · ×8 · ended early · 7:42 PM"), then how it went, its calories and its time.
 */
@Composable
internal fun HistoryRow(
    item: HistoryItem,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onOpenLift: (String) -> Unit = {},
    selected: Boolean = false,
    day: DayMark? = null,
) {
    val palette = IntervalTheme.palette
    val interaction = remember { MutableInteractionSource() }
    val fill by animateColorAsState(
        if (selected) palette.accentTint else Color.Transparent,
        tween(Motion.STATE_MILLIS, easing = StandardEasing),
        label = "selected",
    )
    val id = item.id
    val lift = item.liftUid
    val click: (() -> Unit)? = when {
        id != null -> ({ onOpen(id) })
        lift != null -> ({ onOpenLift(lift) })
        else -> null
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.listRow)
            .background(fill)
            // Only the open row says so: a phone's log has no selection to read out.
            .then(if (selected) Modifier.semantics { this.selected = true } else Modifier)
            .then(
                if (click == null) {
                    // Read as one row all the same.
                    Modifier.semantics(mergeDescendants = true) {}
                } else {
                    Modifier
                        .clickable(
                            interaction,
                            LocalIndication.current,
                            role = Role.Button,
                            onClick = click,
                        )
                        .pressScale(interaction)
                },
            )
            .padding(start = Spacing.space16, end = Spacing.space12, top = Spacing.space12, bottom = Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        day?.let { DayLabel(it.text, it.today) }
        StripeMark(markOf(item), height = IntervalSizes.historyMarkHeight)
        Column(Modifier.weight(1f)) {
            val shape = workoutDescription(item.config)
            val detail = listOfNotNull(
                kindLead(item),
                stringResource(Res.string.history_ended_early).takeIf { item.endedEarly },
                item.effort?.let { effortWord(it) },
                item.calories?.let { stringResource(Res.string.history_calories_value, it) },
                item.time,
            )
            Text(item.presetName ?: shape, style = IntervalTheme.type.label, color = palette.text)
            Text(
                joined(detail),
                style = IntervalTheme.type.meta,
                color = palette.textDim,
            )
            item.note?.let { note ->
                Text(
                    note,
                    style = IntervalTheme.type.meta,
                    color = palette.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(item.total, style = IntervalTheme.type.value, color = palette.text)
        if (click != null) {
            Icon(IntervalIcons.Chevron, null, tint = palette.textDim, modifier = Modifier.size(Sizes.icon))
        }
    }
}
