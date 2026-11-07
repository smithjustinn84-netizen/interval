package dev.justinsmith.lift.session

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import dev.justinsmith.interval.timer.WeekStanding
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.Arrival
import dev.justinsmith.interval.ui.Burst
import dev.justinsmith.interval.ui.HowWasIt
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.isLandscape
import dev.justinsmith.interval.ui.rememberEntrance
import dev.justinsmith.lift.domain.PerceivedEffort
import dev.justinsmith.lift.domain.SaveScope
import dev.justinsmith.lift.domain.formatKg
import dev.justinsmith.lift.domain.formatPace
import dev.justinsmith.lift.session.resources.Res
import dev.justinsmith.lift.session.resources.common_joined
import dev.justinsmith.lift.session.resources.summary_abc_best
import dev.justinsmith.lift.session.resources.summary_done
import dev.justinsmith.lift.session.resources.summary_hand_care
import dev.justinsmith.lift.session.resources.summary_heavy_grip
import dev.justinsmith.lift.session.resources.summary_how_was_it
import dev.justinsmith.lift.session.resources.summary_milestone
import dev.justinsmith.lift.session.resources.summary_note_placeholder
import dev.justinsmith.lift.session.resources.summary_pace
import dev.justinsmith.lift.session.resources.summary_program_finished
import dev.justinsmith.lift.session.resources.summary_share
import dev.justinsmith.lift.session.resources.summary_shared
import dev.justinsmith.lift.session.resources.summary_snatch_test
import dev.justinsmith.lift.session.resources.summary_streak
import dev.justinsmith.lift.session.resources.summary_week_count
import dev.justinsmith.lift.session.resources.summary_week_of_goal
import dev.justinsmith.lift.session.resources.today_goal_met
import dev.justinsmith.lift.ui.exerciseName
import dev.justinsmith.lift.ui.liftName
import dev.justinsmith.lift.ui.programName
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
    onShare: (String) -> Unit = {},
    reflection: SummaryReflection = SummaryReflection(),
) {
    val palette = IntervalTheme.palette
    val session = finished.session
    val healthNotes = healthNotes(finished)
    LaunchedEffect(healthNotes) { reflection.onDescribeForHealth(healthNotes) }
    val shareText = sessionShareText(session, finished.records)
    val choices = rememberChangesChoices()
    val entrance = rememberEntrance()
    // Done, or Back, with the changes neither saved nor set aside asks once before the choice is lost.
    var asking by rememberSaveable { mutableStateOf(false) }
    val undecided = choices.undecided(finished)
    val leave = { if (undecided) asking = true else onDone() }
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = undecided,
        onBackCompleted = leave,
    )
    if (asking) {
        UndecidedChangesDialog(finished, choices, onKeep, onLeave = {
            asking = false
            onDone()
        })
    }
    FinishHaptic(finished)
    BoxWithConstraints(modifier.fillMaxSize().background(palette.background), contentAlignment = Alignment.TopCenter) {
        burstColors(finished)?.let { Burst(it, Modifier.matchParentSize(), delayMillis = Motion.ENTER_MILLIS) }
        if (isLandscape) {
            val sideWidth = minOf(Sizes.setupColumnLandscape, maxWidth * LANDSCAPE_PRIMARY_SHARE)
            // Sideways: Done, the title and the way out on the left, the numbers and changes scrolling on the right.
            Row(
                Modifier.fillMaxSize().safeDrawingPadding().padding(Spacing.space20),
                horizontalArrangement = Arrangement.spacedBy(Spacing.space20, Alignment.CenterHorizontally),
            ) {
                Column(
                    Modifier.width(sideWidth).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.space8),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                        Arrival(entrance, HEADING_STEP) { SummaryHeading(session.title) }
                    }
                    Arrival(entrance, FOOTER_STEP) { SummaryFooter(finished, leave, onShare = { onShare(shareText) }) }
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
                    Arrival(entrance, CHANGES_STEP) { AfterStats(finished, choices, onKeep) }
                    Arrival(entrance, HOW_STEP) { SummaryHowWasIt(finished, reflection) }
                }
            }
        } else {
            // Upright: one column, as wide as a session's on a tablet.
            Column(
                Modifier
                    .widthIn(max = if (isWideUpright) Sizes.contentWide else Sizes.content)
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(Spacing.space20),
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
                    Arrival(entrance, CHANGES_STEP) { AfterStats(finished, choices, onKeep) }
                    Arrival(entrance, HOW_STEP) { SummaryHowWasIt(finished, reflection) }
                }
                Arrival(entrance, FOOTER_STEP) { SummaryFooter(finished, leave, onShare = { onShare(shareText) }) }
            }
        }
    }
}

/** The finish lands under the thumb: firmer when a record fell. */
@Composable
private fun FinishHaptic(finished: Finished) {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        haptics.performHapticFeedback(
            if (finished.records.isEmpty()) HapticFeedbackType.Confirm else HapticFeedbackType.LongPress,
        )
    }
}

/** What follows the numbers: the grip's notes, then what to keep for next time. */
@Composable
private fun AfterStats(finished: Finished, choices: ChangesChoices, onKeep: (SaveScope, String, Set<Int>) -> Unit) {
    Column {
        GripNotes(finished)
        NextTimeCards(finished, choices, onKeep)
    }
}

/** What the session means for next time: the changes to keep, and the blocks at the top of their range. */
@Composable
private fun NextTimeCards(finished: Finished, choices: ChangesChoices, onKeep: (SaveScope, String, Set<Int>) -> Unit) {
    ChangesCard(finished, choices, onKeep)
    ToppedOutCard(finished)
}

/** The session that brings the week to its goal gets the intro's ripple; a record, a ring of the accent. */
@Composable
private fun burstColors(finished: Finished): List<Color>? = when {
    finished.week?.justMet == true -> listOf(
        IntervalTheme.workout.prep,
        IntervalTheme.workout.work,
        IntervalTheme.workout.rest,
    )

    finished.records.isNotEmpty() -> listOf(IntervalTheme.palette.accent)

    else -> null
}

/** The summary's parts arrive in this order, each [Motion.STAGGER_MILLIS] steps after the heading. */
private const val HEADING_STEP = 0
private const val STATS_STEP = 2
private const val CHANGES_STEP = 4
private const val HOW_STEP = 5
private const val FOOTER_STEP = 6

/** What the summary's "How was it?" does: keeps the effort and the note, and hands on their words. */
class SummaryReflection(
    val onEffortChange: (PerceivedEffort) -> Unit = {},
    val onNoteChange: (String) -> Unit = {},
    /** The effort and note in words for the health store's note, null with neither said. */
    val onDescribeForHealth: (String?) -> Unit = {},
)

@Composable
private fun SummaryHowWasIt(finished: Finished, reflection: SummaryReflection) {
    HowWasIt(
        title = stringResource(Res.string.summary_how_was_it),
        efforts = PerceivedEffort.entries,
        effort = finished.session.effort,
        effortLabel = { effortWord(it) },
        onEffortChange = reflection.onEffortChange,
        note = finished.note,
        onNoteChange = reflection.onNoteChange,
        notePlaceholder = stringResource(Res.string.summary_note_placeholder),
        noteMaxLength = PerceivedEffort.NOTE_MAX_LENGTH,
        modifier = Modifier.padding(top = Spacing.space16),
    )
}

/**
 * The lifts done faster than ever, a heavy day on the grip, and how to look after the hands after many snatches or
 * one-hand swings.
 */
@Composable
private fun GripNotes(finished: Finished) {
    val paces = finished.paces.map { pace ->
        stringResource(
            Res.string.summary_pace,
            liftName(pace.exercise, pace.double),
            formatPace(pace.perMinute),
            formatKg(pace.kg),
        )
    }
    val bests = listOfNotNull(
        finished.snatchTest?.let { stringResource(Res.string.summary_snatch_test, it) },
        finished.armorBuilding?.let { stringResource(Res.string.summary_abc_best, it) },
    ) + finished.milestones.map { (exercise, count) ->
        stringResource(Res.string.summary_milestone, count, exerciseName(exercise))
    }
    val lines = bests + paces + listOfNotNull(
        Res.string.summary_heavy_grip.takeIf { finished.heavyGrip },
        Res.string.summary_hand_care.takeIf { finished.handCare },
    ).map { stringResource(it) }
    lines.forEach { line ->
        Text(
            line,
            style = IntervalTheme.type.meta,
            color = IntervalTheme.palette.textDim,
            modifier = Modifier.padding(vertical = Spacing.space4),
        )
    }
}

/** The effort and the note as one line, "Hard · Felt strong", either alone, or null with neither. */
@Composable
private fun healthNotes(finished: Finished): String? {
    val word = finished.session.effort?.let { effortWord(it) }
    val note = finished.session.note
    return when {
        word != null && note != null -> stringResource(Res.string.common_joined, word, note)
        else -> word ?: note
    }
}

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
            modifier = Modifier.fillMaxWidth().semantics { heading() },
        )
    }
}

/** Where the week stands with the session in it, the program it finished if it did, where it went, Share and Done. */
@Composable
private fun SummaryFooter(finished: Finished, onDone: () -> Unit, onShare: () -> Unit) {
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
        finished.lastDayOf?.let { program ->
            Text(
                stringResource(Res.string.summary_program_finished, programName(program)),
                style = IntervalTheme.type.label,
                color = IntervalTheme.palette.accent,
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
        QuietTextButton(stringResource(Res.string.summary_share), onShare, Modifier.align(Alignment.CenterHorizontally))
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
