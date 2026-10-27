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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import dev.justinsmith.bells.domain.SaveScope
import dev.justinsmith.bells.session.resources.Res
import dev.justinsmith.bells.session.resources.common_joined
import dev.justinsmith.bells.session.resources.summary_done
import dev.justinsmith.bells.session.resources.summary_program_finished
import dev.justinsmith.bells.session.resources.summary_share
import dev.justinsmith.bells.session.resources.summary_shared
import dev.justinsmith.bells.session.resources.summary_streak
import dev.justinsmith.bells.session.resources.summary_week_count
import dev.justinsmith.bells.session.resources.summary_week_of_goal
import dev.justinsmith.bells.session.resources.today_goal_met
import dev.justinsmith.bells.ui.programName
import dev.justinsmith.interval.timer.WeekStanding
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.Burst
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.isLandscape
import dev.justinsmith.interval.ui.rememberEntrance
import dev.justinsmith.interval.ui.risingEnter
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
) {
    val palette = IntervalTheme.palette
    val session = finished.session
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
    // The finish lands under the thumb: firmer when a record fell.
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        haptics.performHapticFeedback(
            if (finished.records.isEmpty()) HapticFeedbackType.Confirm else HapticFeedbackType.LongPress,
        )
    }
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
                    Arrival(entrance, CHANGES_STEP) { Column { NextTimeCards(finished, choices, onKeep) } }
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
                    Arrival(entrance, CHANGES_STEP) { Column { NextTimeCards(finished, choices, onKeep) } }
                }
                Arrival(entrance, FOOTER_STEP) { SummaryFooter(finished, leave, onShare = { onShare(shareText) }) }
            }
        }
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

/** One part of the summary, rising in [step] stagger steps after the first: the finish's small ceremony. */
@Composable
private fun Arrival(entrance: MutableTransitionState<Boolean>, step: Int, content: @Composable () -> Unit) {
    AnimatedVisibility(entrance, enter = risingEnter(Motion.STAGGER_MILLIS * step)) { content() }
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
