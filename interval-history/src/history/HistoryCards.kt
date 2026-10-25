package dev.justinsmith.interval.history

import androidx.compose.foundation.LocalIndication
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import dev.justinsmith.bells.domain.program
import dev.justinsmith.bells.ui.programDayLifts
import dev.justinsmith.bells.ui.programDayLine
import dev.justinsmith.bells.ui.programName
import dev.justinsmith.interval.history.resources.Res
import dev.justinsmith.interval.history.resources.common_joined
import dev.justinsmith.interval.history.resources.history_edit_plan
import dev.justinsmith.interval.history.resources.history_most_done
import dev.justinsmith.interval.history.resources.history_resume
import dev.justinsmith.interval.history.resources.history_start
import dev.justinsmith.interval.history.resources.history_then
import dev.justinsmith.interval.history.resources.history_times
import dev.justinsmith.interval.history.resources.history_up_next
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.timer.weekdayOf
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.StripeMark
import dev.justinsmith.interval.ui.WeekdayForm
import dev.justinsmith.interval.ui.joined
import dev.justinsmith.interval.ui.pressScale
import dev.justinsmith.interval.ui.surfaceButton
import dev.justinsmith.interval.ui.weekdayText
import dev.justinsmith.interval.ui.workoutDescription
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
                val interaction = remember { MutableInteractionSource() }
                Text(
                    stringResource(Res.string.history_edit_plan),
                    style = IntervalTheme.type.label,
                    color = palette.accent,
                    modifier = Modifier
                        .heightIn(min = Sizes.segment)
                        .clickable(interaction, LocalIndication.current, role = Role.Button, onClick = actions.onPlan)
                        .pressScale(interaction)
                        .padding(vertical = Spacing.space12),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
            ) {
                when (next) {
                    is UpNextItem.Planned -> {
                        StripeMark(palette.workMark, height = Sizes.historyMarkHeight)
                        UpNextText(
                            next.name,
                            stringResource(
                                Res.string.common_joined,
                                workoutDescription(next.config),
                                formatClock(next.config.totalSeconds),
                            ),
                            Modifier.weight(1f),
                        )
                        StartPill(primary = true, onClick = { actions.onStart(next.name, next.config) })
                    }

                    is UpNextItem.Lift -> {
                        // The accent, as Lift and History's rows mark kettlebell sessions.
                        StripeMark(palette.accent, height = Sizes.historyMarkHeight)
                        UpNextText(
                            programName(next.programDay.program),
                            joined(listOf(programDayLine(next.programDay), programDayLifts(next.programDay))),
                            Modifier.weight(1f),
                        )
                        StartPill(
                            primary = true,
                            onClick = { actions.onStartLift(next.programDay.program.key.takeUnless { next.resume }) },
                            label = stringResource(
                                if (next.resume) Res.string.history_resume else Res.string.history_start,
                            ),
                        )
                    }
                }
            }
            val later = upNext.drop(1).take(2)
            if (later.isNotEmpty()) {
                val parts = later.map {
                    stringResource(Res.string.common_joined, shortDay(it.day, it.epochDay), upNextName(it))
                }
                Text(
                    stringResource(
                        Res.string.history_then,
                        joined(parts),
                    ),
                    style = IntervalTheme.type.meta,
                    color = palette.textDim,
                )
            }
        }
    }
}

/** Up next's headline: its name over a line of what it is. */
@Composable
private fun UpNextText(name: String, detail: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(name, style = IntervalTheme.type.label, color = IntervalTheme.palette.text)
        Text(detail, style = IntervalTheme.type.meta, color = IntervalTheme.palette.textDim)
    }
}

/** What a later item in Up next is called: the plan's name, or the program's. */
@Composable
private fun upNextName(item: UpNextItem): String = when (item) {
    is UpNextItem.Planned -> item.name
    is UpNextItem.Lift -> programName(item.programDay.program)
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
                    StripeMark(palette.workMark, height = Sizes.historyMarkHeight)
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

/** A day in a line of them: "Today", "Tomorrow", else its weekday, "Fri". */
@Composable
private fun shortDay(day: HistoryDay, epochDay: Long): String = when (day) {
    HistoryDay.Today, HistoryDay.Tomorrow, HistoryDay.Yesterday -> dayHeading(day)
    is HistoryDay.On -> weekdayText(weekdayOf(epochDay), WeekdayForm.Short)
}
