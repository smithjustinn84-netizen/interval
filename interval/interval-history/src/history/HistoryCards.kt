package dev.justinsmith.interval.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.justinsmith.interval.history.resources.Res
import dev.justinsmith.interval.history.resources.common_joined
import dev.justinsmith.interval.history.resources.history_coming_up
import dev.justinsmith.interval.history.resources.history_edit_plan
import dev.justinsmith.interval.history.resources.history_most_done
import dev.justinsmith.interval.history.resources.history_resume
import dev.justinsmith.interval.history.resources.history_start
import dev.justinsmith.interval.history.resources.history_then
import dev.justinsmith.interval.history.resources.history_times
import dev.justinsmith.interval.history.resources.history_up_next
import dev.justinsmith.interval.history.resources.notice_sport_logged
import dev.justinsmith.interval.history.resources.preview_open_cd
import dev.justinsmith.interval.history.resources.sport_log_it
import dev.justinsmith.interval.plan.sportTitle
import dev.justinsmith.interval.timer.weekdayOf
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalSizes
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.StripeMark
import dev.justinsmith.interval.ui.WeekdayForm
import dev.justinsmith.interval.ui.joined
import dev.justinsmith.interval.ui.surfaceButton
import dev.justinsmith.interval.ui.weekdayText
import dev.justinsmith.interval.ui.workoutDescription
import dev.justinsmith.lift.domain.program
import org.jetbrains.compose.resources.stringResource

/** The next plan with a way to start it, the two after it in a line, and the way to the plan. */
@Composable
internal fun UpNextCard(upNext: List<UpNextItem>, actions: HistoryActions, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val next = upNext.first()
    ListCard(modifier) {
        Column(
            Modifier.padding(
                start = Spacing.space20,
                end = Spacing.space16,
                top = Spacing.space12,
                bottom = Spacing.space12,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.space8),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GroupLabelText(stringResource(Res.string.history_up_next, dayHeading(next.day)), Modifier.weight(1f))
                EditPlan(actions.onPlan)
            }
            UpNextLead(next, actions)
            UpNextLater(upNext.drop(1))
        }
    }
}

/**
 * Every plan of the next seven days in a card, a schedule: a row each, keyed by its day in a column. Over the card,
 * the way to the plan. A row opens a look at it ([onLook], by its place).
 */
@Composable
internal fun ComingUp(
    upNext: List<UpNextItem>,
    actions: HistoryActions,
    onLook: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GroupLabel(stringResource(Res.string.history_coming_up), Modifier.weight(1f))
            EditPlan(actions.onPlan)
        }
        ListCard {
            upNext.forEachIndexed { index, item ->
                if (index > 0) RowDivider(Modifier.padding(start = Spacing.space20))
                ComingUpRow(item, actions, onLook = { onLook(index) })
            }
        }
    }
}

/**
 * One plan coming up: its day in the column ("Today", "Sat"), its kind's mark, its name over one line of what it
 * is, and on today's rows the way to do it (Start, Resume, Log it). A later row shows its length instead and
 * opens a look at it before Start, where Start is; a sport, which nothing starts, has no look to open.
 */
@Composable
private fun ComingUpRow(item: UpNextItem, actions: HistoryActions, onLook: () -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val today = item.day == HistoryDay.Today
    val figure = upNextFigure(item)
    val looks = item.preview() != null
    val described = stringResource(Res.string.preview_open_cd, upNextName(item))
    Row(
        modifier
            .fillMaxWidth()
            .then(
                if (looks) {
                    Modifier.clickable(onClickLabel = described, onClick = onLook)
                } else {
                    // Read as one row all the same.
                    Modifier.semantics(mergeDescendants = true) {}
                },
            )
            .heightIn(min = Sizes.listRow)
            .padding(start = Spacing.space16, end = Spacing.space12, top = Spacing.space12, bottom = Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        DayLabel(dayColumn(item.day, item.epochDay), today)
        StripeMark(kindColor(upNextKind(item)), height = IntervalSizes.historyMarkHeight)
        // Today's row has the pill where the figure would stand, so its length goes into the line, which may
        // take a second beside the pill's height; a later row's keeps to one.
        val meta = if (today && figure != null) joined(listOf(upNextMeta(item), figure)) else upNextMeta(item)
        UpNextText(upNextName(item), meta, Modifier.weight(1f), maxLines = if (today) 2 else 1)
        if (today) {
            UpNextAction(item, actions)
        } else {
            figure?.let { Text(it, style = IntervalTheme.type.value, color = palette.text) }
            if (looks) {
                Icon(
                    IntervalIcons.Chevron,
                    null,
                    tint = palette.textDim,
                    modifier = Modifier.size(Sizes.chipIcon),
                )
            }
        }
    }
}

/** The quiet action to the plan, beside Up next's heading and Coming up's: accent words, no fill of its own. */
@Composable
private fun EditPlan(onPlan: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .heightIn(min = Sizes.pillHeight)
            .surfaceButton(interaction, Corners.radius12, Color.Transparent, onPlan)
            .padding(horizontal = Spacing.space12),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(Res.string.history_edit_plan),
            style = IntervalTheme.type.label,
            color = IntervalTheme.palette.accent,
        )
    }
}

/**
 * The first [count] of [later], what comes after the lead, in a line: "Then tomorrow · Tabata · Sat · Lift
 * session".
 */
@Composable
internal fun UpNextLater(later: List<UpNextItem>, modifier: Modifier = Modifier, count: Int = 2) {
    if (later.isEmpty()) return
    val parts = later.take(count).map {
        stringResource(Res.string.common_joined, shortDay(it.day, it.epochDay, midSentence = true), upNextName(it))
    }
    Text(
        stringResource(Res.string.history_then, joined(parts)),
        style = IntervalTheme.type.meta,
        color = IntervalTheme.palette.textDim,
        modifier = modifier,
    )
}

/** Up next's lead: its kind's mark, its name over what it is and its length, and the way to do it. */
@Composable
private fun UpNextLead(next: UpNextItem, actions: HistoryActions, modifier: Modifier = Modifier) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        StripeMark(kindColor(upNextKind(next)), height = IntervalSizes.historyMarkHeight)
        UpNextText(upNextName(next), joined(listOfNotNull(upNextMeta(next), upNextFigure(next))), Modifier.weight(1f))
        UpNextAction(next, actions)
    }
}

/**
 * The way to do what is up next: Start on a plan or a flow, Start or Resume on a session on Lift, Log it on a
 * sport's own day. Nothing starts a sport: before its day it is only named.
 */
@Composable
private fun UpNextAction(item: UpNextItem, actions: HistoryActions) {
    when (item) {
        is UpNextItem.Planned -> StartPill(primary = true, onClick = { actions.onStart(item.name, item.config) })

        is UpNextItem.Lift -> StartPill(
            primary = true,
            // Lift starts nothing while a session is in progress, so with one under way this resumes it.
            onClick = { actions.onStartLift(item.programDay?.program?.key.takeUnless { item.resume }) },
            label = stringResource(if (item.resume) Res.string.history_resume else Res.string.history_start),
        )

        // Left out of History's list (HistoryViewModel), which is of what is planned: drawn all the same.
        is UpNextItem.Flow -> StartPill(primary = true, onClick = { actions.onStart(item.flow.name, item.flow.config) })

        is UpNextItem.SportDay -> if (item.day == HistoryDay.Today) {
            val logged = stringResource(Res.string.notice_sport_logged, sportTitle(item.sport))
            StartPill(
                primary = false,
                onClick = { actions.onLogSport(item.sport, item.epochDay, logged) },
                label = stringResource(Res.string.sport_log_it),
            )
        }
    }
}

/** Up next's headline: its name over a line of what it is, cut to [maxLines] where the row has no room for more. */
@Composable
private fun UpNextText(name: String, detail: String, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    Column(modifier) {
        Text(name, style = IntervalTheme.type.label, color = IntervalTheme.palette.text)
        Text(
            detail,
            style = IntervalTheme.type.meta,
            color = IntervalTheme.palette.textDim,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A group heading's look, inside a card. */
@Composable
private fun GroupLabelText(text: String, modifier: Modifier = Modifier) {
    Text(text, style = IntervalTheme.type.caption, color = IntervalTheme.palette.textDim, modifier = modifier)
}

/** Start, or another [label], as a small pill: accent when [primary], on a quiet fill otherwise. */
@Composable
internal fun StartPill(
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(Res.string.history_start),
) {
    val palette = IntervalTheme.palette
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .heightIn(min = Sizes.segment)
            .surfaceButton(interaction, Corners.full, if (primary) palette.accent else palette.surfaceVariant, onClick)
            .padding(horizontal = Spacing.space16),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        val ink = if (primary) palette.onAccent else palette.text
        if (primary) Icon(IntervalIcons.Play, null, tint = ink, modifier = Modifier.size(Sizes.chipIcon))
        Text(label, style = IntervalTheme.type.label, color = ink)
    }
}

/** All time's most done shapes, by preset name where one matches, with how often. */
@Composable
internal fun MostDone(rows: List<ShapeRow>, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Column(modifier.padding(top = Spacing.space8)) {
        GroupLabel(stringResource(Res.string.history_most_done))
        ListCard {
            rows.forEachIndexed { index, row ->
                if (index > 0) RowDivider(Modifier.padding(start = Spacing.space20))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Sizes.listRow)
                        .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
                ) {
                    StripeMark(palette.workMark, height = IntervalSizes.historyMarkHeight)
                    Column(Modifier.weight(1f)) {
                        val shape = workoutDescription(row.config)
                        Text(row.name ?: shape, style = IntervalTheme.type.label, color = palette.text)
                        val total = longTotal(row.totalSeconds)
                        Text(
                            if (row.name != null) stringResource(Res.string.common_joined, shape, total) else total,
                            style = IntervalTheme.type.meta,
                            color = palette.textDim,
                        )
                    }
                    Text(
                        stringResource(Res.string.history_times, row.count),
                        style = IntervalTheme.type.value,
                        color = palette.text,
                    )
                }
            }
        }
    }
}

/** A day in a line of them: "Today", "Tomorrow", else its weekday, "Fri"; [midSentence], after "Then", "tomorrow". */
@Composable
internal fun shortDay(day: HistoryDay, epochDay: Long, midSentence: Boolean = false): String = when (day) {
    HistoryDay.Today, HistoryDay.Tomorrow, HistoryDay.Yesterday ->
        dayHeading(day).let { if (midSentence) it.replaceFirstChar { first -> first.lowercase() } else it }

    is HistoryDay.On -> weekdayText(weekdayOf(epochDay), WeekdayForm.Short)
}
