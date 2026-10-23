package dev.justinsmith.bells.session

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import dev.justinsmith.bells.domain.Band
import dev.justinsmith.bells.domain.BlockChange
import dev.justinsmith.bells.domain.PlanChanges
import dev.justinsmith.bells.domain.ProgramKey
import dev.justinsmith.bells.domain.SaveScope
import dev.justinsmith.bells.domain.copyName
import dev.justinsmith.bells.resources.Res
import dev.justinsmith.bells.resources.change_line
import dev.justinsmith.bells.resources.changes_copy_hint
import dev.justinsmith.bells.resources.changes_copy_hint_last
import dev.justinsmith.bells.resources.changes_copy_name
import dev.justinsmith.bells.resources.changes_every_day
import dev.justinsmith.bells.resources.changes_every_day_hint
import dev.justinsmith.bells.resources.changes_kept
import dev.justinsmith.bells.resources.changes_kept_copy
import dev.justinsmith.bells.resources.changes_kept_copy_last
import dev.justinsmith.bells.resources.changes_next_time
import dev.justinsmith.bells.resources.changes_save
import dev.justinsmith.bells.resources.changes_save_copy
import dev.justinsmith.bells.resources.changes_this_day
import dev.justinsmith.bells.resources.changes_this_day_hint
import dev.justinsmith.bells.resources.changes_title
import dev.justinsmith.bells.resources.changes_title_next
import dev.justinsmith.bells.resources.changes_today_only
import dev.justinsmith.bells.resources.common_joined
import dev.justinsmith.bells.resources.summary_done
import dev.justinsmith.bells.resources.summary_shared
import dev.justinsmith.bells.resources.summary_streak
import dev.justinsmith.bells.resources.summary_week_count
import dev.justinsmith.bells.resources.summary_week_of_goal
import dev.justinsmith.bells.resources.today_goal_met
import dev.justinsmith.bells.ui.blockLine
import dev.justinsmith.bells.ui.liftName
import dev.justinsmith.bells.ui.programName
import dev.justinsmith.interval.timer.WeekStanding
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.Burst
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.SecondaryButton
import dev.justinsmith.interval.ui.SegmentedPicker
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.ToggleRow
import dev.justinsmith.interval.ui.enterRising
import dev.justinsmith.interval.ui.rememberEntrance
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A finished session: Done in the done colour, its time, weight moved and reps, and any records it set; for a
 * program day changed in the session, the changes, to keep in the program with [onKeep].
 */
@Composable
fun SummaryScreen(
    finished: Finished,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onKeep: (SaveScope, String, Set<Int>) -> Unit = { _, _, _ -> },
) {
    val palette = IntervalTheme.palette
    val session = finished.session
    val choices = rememberChangesChoices()
    val entrance = rememberEntrance()
    // The finish lands under the thumb: firmer when a record fell.
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        haptics.performHapticFeedback(
            if (finished.records.isEmpty()) HapticFeedbackType.Confirm else HapticFeedbackType.LongPress,
        )
    }
    BoxWithConstraints(modifier.fillMaxSize().background(palette.background), contentAlignment = Alignment.TopCenter) {
        // The session that brings the week to its goal gets the intro's ripple; a record, a ring of the accent.
        val burst = when {
            finished.week?.justMet == true -> listOf(
                IntervalTheme.workout.prep,
                IntervalTheme.workout.work,
                IntervalTheme.workout.rest,
            )

            finished.records.isNotEmpty() -> listOf(palette.accent)

            else -> null
        }
        if (burst != null) Burst(burst, Modifier.matchParentSize(), delayMillis = Motion.ENTER_MILLIS)
        if (hasRoomBeside) {
            // Sideways, or on a tablet: Done, the title and the way out on the left, the numbers and changes
            // scrolling on the right.
            Row(
                Modifier.fillMaxSize().safeDrawingPadding().padding(Spacing.space20),
                horizontalArrangement = Arrangement.spacedBy(Spacing.space20, Alignment.CenterHorizontally),
            ) {
                Column(
                    Modifier.width(Sizes.setupColumnLandscape).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.space8),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                        Arrival(entrance, HEADING_STEP) { SummaryHeading(session.title) }
                    }
                    Arrival(entrance, FOOTER_STEP) { SummaryFooter(finished, onDone) }
                }
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .widthIn(max = Sizes.contentWide)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.space8),
                ) {
                    Arrival(entrance, STATS_STEP) { SessionStats(session, finished.records, highlight = true) }
                    Arrival(entrance, CHANGES_STEP) { Column { ChangesCard(finished, choices, onKeep) } }
                }
            }
        } else {
            Column(
                Modifier.widthIn(max = Sizes.content).fillMaxSize().safeDrawingPadding().padding(Spacing.space20),
                verticalArrangement = Arrangement.spacedBy(Spacing.space8),
            ) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.space8),
                ) {
                    Arrival(entrance, HEADING_STEP) {
                        SummaryHeading(session.title, Modifier.padding(top = Spacing.space24, bottom = Spacing.space16))
                    }
                    Arrival(entrance, STATS_STEP) { SessionStats(session, finished.records, highlight = true) }
                    Arrival(entrance, CHANGES_STEP) { Column { ChangesCard(finished, choices, onKeep) } }
                }
                Arrival(entrance, FOOTER_STEP) { SummaryFooter(finished, onDone) }
            }
        }
    }
}

/** One part of the summary, rising in [step] stagger steps after the first: the finish's small ceremony. */
@Composable
private fun Arrival(entrance: MutableTransitionState<Boolean>, step: Int, content: @Composable () -> Unit) {
    AnimatedVisibility(entrance, enter = enterRising(Motion.STAGGER_MILLIS * step)) { content() }
}

/** The summary's parts arrive in this order, each [Motion.STAGGER_MILLIS] steps after the heading. */
private const val HEADING_STEP = 0
private const val STATS_STEP = 2
private const val CHANGES_STEP = 4
private const val FOOTER_STEP = 6

/** Done in the done colour over the session's title. */
@Composable
private fun SummaryHeading(title: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text(
            stringResource(Res.string.summary_done),
            style = IntervalTheme.type.display,
            color = IntervalTheme.workout.done,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            title,
            style = IntervalTheme.type.section,
            color = IntervalTheme.palette.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Where the week stands with the session in it, where the session went, and Done. */
@Composable
private fun SummaryFooter(finished: Finished, onDone: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        finished.week?.let { week ->
            Text(
                weekText(week),
                style = IntervalTheme.type.label,
                color = IntervalTheme.palette.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            stringResource(Res.string.summary_shared),
            style = IntervalTheme.type.meta,
            color = IntervalTheme.palette.textDim,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        AccentButton(stringResource(Res.string.summary_done), onDone, Modifier.fillMaxWidth())
    }
}

/** "3 of 4 this week", "Goal met this week · 3-week streak", or with no goal set, the count. */
@Composable
private fun weekText(week: WeekStanding): String {
    val goal = week.goal
    val standing = when {
        goal == null -> pluralStringResource(Res.plurals.summary_week_count, week.count, week.count)
        week.goalMet -> stringResource(Res.string.today_goal_met)
        else -> stringResource(Res.string.summary_week_of_goal, week.count, goal)
    }
    return if (week.streak > 1) {
        stringResource(
            Res.string.common_joined,
            standing,
            pluralStringResource(Res.plurals.summary_streak, week.streak, week.streak),
        )
    } else {
        standing
    }
}

/**
 * What was chosen on the changes card. Held by [SummaryScreen] above its layout, so turning the phone, which
 * moves the card to the other column, keeps the choices.
 */
private class ChangesChoices(
    val dismissed: MutableState<Boolean>,
    // Saved by name: an enum is not saveable on every platform.
    val scopeName: MutableState<String>,
    // The steps up left out, by place, as text: a plain string saves on every platform.
    val declined: MutableState<String>,
)

@Composable
private fun rememberChangesChoices() = ChangesChoices(
    dismissed = rememberSaveable { mutableStateOf(false) },
    scopeName = rememberSaveable { mutableStateOf(SaveScope.ThisDay.name) },
    declined = rememberSaveable { mutableStateOf("") },
)

/**
 * A program day's changes, offered to keep: each block as programmed and as it was done, and Next time, the
 * steps up the day earned, each to take or leave; how far to keep them (this day, or every block of the
 * exercise), then Save, in place for a program the user made or in a copy of a built-in, or Today only. Once
 * kept, where they went.
 */
@Composable
private fun ChangesCard(finished: Finished, choices: ChangesChoices, onKeep: (SaveScope, String, Set<Int>) -> Unit) {
    val changes = finished.changes ?: return
    var dismissed by choices.dismissed
    var scopeName by choices.scopeName
    var declined by choices.declined
    val left = declined.split(',').mapNotNull { it.toIntOrNull() }.toSet()
    val taken = changes.nudges.indices.toSet() - left
    val name = programName(changes.program)
    val copy = copyName(stringResource(Res.string.changes_copy_name, name), finished.taken)
    if (dismissed) return
    GroupLabel(
        stringResource(if (changes.changes.isEmpty()) Res.string.changes_title_next else Res.string.changes_title),
        Modifier.padding(top = Spacing.space16),
    )
    ListCard {
        Column(Modifier.padding(Spacing.space16), verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
            changes.changes.forEach { change -> ChangeLine(change, finished.bands) }
            if (finished.kept != null) {
                KeptLine(changes, name, copy)
                return@Column
            }
            NextTime(changes.nudges, taken, finished.bands, onToggle = { i ->
                declined = (if (i in left) left - i else left + i).joinToString(",")
            })
            if (changes.changes.isNotEmpty() || taken.isNotEmpty()) {
                val scope = SaveScope.valueOf(scopeName)
                SaveChoices(changes, scope, name, onScope = { scopeName = it.name })
                SecondaryButton(
                    if (changes.program.key is ProgramKey.BuiltIn) {
                        stringResource(Res.string.changes_save_copy, copy)
                    } else {
                        stringResource(Res.string.changes_save, name)
                    },
                    { onKeep(scope, copy, taken) },
                    Modifier.fillMaxWidth().padding(top = Spacing.space8),
                )
            }
            QuietTextButton(
                stringResource(Res.string.changes_today_only),
                { dismissed = true },
                Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

/** The steps up the day earned, each with a switch: on, it goes in the program for next time. */
@Composable
private fun NextTime(nudges: List<BlockChange>, taken: Set<Int>, bands: List<Band>, onToggle: (Int) -> Unit) {
    if (nudges.isEmpty()) return
    Text(
        stringResource(Res.string.changes_next_time),
        style = IntervalTheme.type.caption,
        color = IntervalTheme.palette.textDim,
        modifier = Modifier.padding(top = Spacing.space8),
    )
    nudges.forEachIndexed { i, nudge ->
        ToggleRow(
            liftName(nudge.to.exercise, nudge.to.double),
            stringResource(Res.string.change_line, blockLine(nudge.from, bands), blockLine(nudge.to, bands)),
            checked = i in taken,
            onToggle = { onToggle(i) },
        )
    }
}

/** How far to keep the changes, what that means, and for a built-in, that they go in a copy. */
@Composable
private fun SaveChoices(changes: PlanChanges, scope: SaveScope, name: String, onScope: (SaveScope) -> Unit) {
    val palette = IntervalTheme.palette
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        SegmentedPicker(SaveScope.entries, scope, label = { scopeLabel(it) }, onSelect = onScope)
        Text(
            if (scope == SaveScope.ThisDay) {
                stringResource(Res.string.changes_this_day_hint, changes.dayIndex + 1)
            } else {
                stringResource(Res.string.changes_every_day_hint)
            },
            style = IntervalTheme.type.meta,
            color = palette.textDim,
        )
        if (changes.program.key is ProgramKey.BuiltIn) {
            Text(
                if (changes.goesOn) {
                    stringResource(Res.string.changes_copy_hint, name, changes.dayNumber + 1)
                } else {
                    stringResource(Res.string.changes_copy_hint_last, name)
                },
                style = IntervalTheme.type.meta,
                color = palette.textDim,
            )
        }
    }
}

@Composable
private fun scopeLabel(scope: SaveScope): String = stringResource(
    when (scope) {
        SaveScope.ThisDay -> Res.string.changes_this_day
        SaveScope.EveryDay -> Res.string.changes_every_day
    },
)

/** Where the changes were kept: the program itself, or a copy followed from the next day. */
@Composable
private fun KeptLine(changes: PlanChanges, name: String, copy: String) {
    Text(
        when {
            changes.program.key !is ProgramKey.BuiltIn -> stringResource(Res.string.changes_kept, name)
            changes.goesOn -> stringResource(Res.string.changes_kept_copy, copy, changes.dayNumber + 1)
            else -> stringResource(Res.string.changes_kept_copy_last, copy)
        },
        style = IntervalTheme.type.label,
        color = IntervalTheme.palette.accent,
    )
}

/** One block changed: its lift, then as programmed → as done; a swap names both lifts. */
@Composable
private fun ChangeLine(change: BlockChange, bands: List<Band>) {
    val palette = IntervalTheme.palette
    val (from, to) = change.from to change.to
    Column {
        Text(
            if (change.swapped) {
                stringResource(
                    Res.string.change_line,
                    liftName(from.exercise, from.double),
                    liftName(to.exercise, to.double),
                )
            } else {
                liftName(to.exercise, to.double)
            },
            style = IntervalTheme.type.label,
            color = palette.text,
        )
        Text(
            stringResource(Res.string.change_line, blockLine(from, bands), blockLine(to, bands)),
            style = IntervalTheme.type.meta,
            color = palette.textDim,
        )
    }
}
