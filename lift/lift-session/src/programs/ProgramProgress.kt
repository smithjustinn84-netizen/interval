package dev.justinsmith.lift.programs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.BackAction
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.LocalNotices
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.Opacity
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.SecondaryButton
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.StandardEasing
import dev.justinsmith.interval.ui.TabGrid
import dev.justinsmith.interval.ui.enterRising
import dev.justinsmith.interval.ui.pressScale
import dev.justinsmith.interval.ui.rememberEntrance
import dev.justinsmith.lift.domain.DayStanding
import dev.justinsmith.lift.domain.Followed
import dev.justinsmith.lift.domain.ProgramProgress
import dev.justinsmith.lift.domain.builtInPrograms
import dev.justinsmith.lift.domain.formatKg
import dev.justinsmith.lift.domain.programProgress
import dev.justinsmith.lift.session.resources.Res
import dev.justinsmith.lift.session.resources.common_joined
import dev.justinsmith.lift.session.resources.kg_value
import dev.justinsmith.lift.session.resources.notice_program_followed
import dev.justinsmith.lift.session.resources.program_day_before_copy
import dev.justinsmith.lift.session.resources.program_day_cd
import dev.justinsmith.lift.session.resources.program_day_done
import dev.justinsmith.lift.session.resources.program_day_done_before
import dev.justinsmith.lift.session.resources.program_day_missed
import dev.justinsmith.lift.session.resources.program_day_next
import dev.justinsmith.lift.session.resources.program_day_to_come
import dev.justinsmith.lift.session.resources.program_days_before_copy
import dev.justinsmith.lift.session.resources.program_days_done
import dev.justinsmith.lift.session.resources.program_days_done_label
import dev.justinsmith.lift.session.resources.program_days_left_label
import dev.justinsmith.lift.session.resources.program_finished
import dev.justinsmith.lift.session.resources.program_follow_again
import dev.justinsmith.lift.session.resources.program_follow_again_hint
import dev.justinsmith.lift.session.resources.program_none_done
import dev.justinsmith.lift.session.resources.program_open_session_cd
import dev.justinsmith.lift.session.resources.program_pick_another
import dev.justinsmith.lift.session.resources.program_preview_day
import dev.justinsmith.lift.session.resources.program_records_set
import dev.justinsmith.lift.session.resources.program_sets_done
import dev.justinsmith.lift.session.resources.program_week
import dev.justinsmith.lift.session.resources.session_moved
import dev.justinsmith.lift.session.resources.summary_reps
import dev.justinsmith.lift.ui.RecordRow
import dev.justinsmith.lift.ui.StatTile
import dev.justinsmith.lift.ui.programDayLine
import dev.justinsmith.lift.ui.programName
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.currentKoinScope

/** What the Program page does: open a day's session, follow the program again or pick another, and go back. */
data class ProgramProgressActions(
    val onOpenSession: (uid: String) -> Unit = {},
    val onFollowAgain: () -> Unit = {},
    val onPickAnother: () -> Unit = {},
    val onBack: () -> Unit = {},
)

@Composable
fun ProgramProgressRoute(onOpenSession: (uid: String) -> Unit, onPickAnother: () -> Unit, onBack: () -> Unit) {
    val scope = currentKoinScope()
    val viewModel = viewModel { ProgramProgressViewModel(scope.get(), scope.get()) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notices = LocalNotices.current
    val followedText = stringResource(
        Res.string.notice_program_followed,
        state.progress?.let { programName(it.program) }.orEmpty(),
    )
    ProgramProgressScreen(
        state,
        ProgramProgressActions(
            onOpenSession = onOpenSession,
            onFollowAgain = {
                notices.show(followedText)
                viewModel.followAgain()
            },
            onPickAnother = onPickAnother,
            onBack = onBack,
        ),
    )
}

/**
 * The program followed: where it stands, a map of its days by week and each day done (a tap opens its
 * session); once every day is done, what it came to, with Follow again and a way to another program ahead of
 * the days. On a wider window the days done sit beside the map. Once nothing is followed the page goes back.
 */
@Composable
fun ProgramProgressScreen(
    state: ProgramProgressUiState,
    actions: ProgramProgressActions,
    modifier: Modifier = Modifier,
) {
    val progress = state.progress
    val back by rememberUpdatedState(actions.onBack)
    LaunchedEffect(progress == null) { if (progress == null) back() }
    if (progress == null) return
    val entrance = rememberEntrance()
    val card = Modifier.padding(horizontal = Spacing.space16, vertical = Spacing.space4)
    TabGrid(programName(progress.program), modifier, navigation = { BackAction(actions.onBack) }) {
        item(key = "standing") { Arrival(entrance, STANDING_STEP) { Standing(progress, card) } }
        item(key = "map") { Arrival(entrance, MAP_STEP) { ListCard(card) { DayMap(progress) } } }
        if (progress.finished && progress.records.isNotEmpty()) {
            item(key = "records") {
                Arrival(entrance, DONE_STEP) {
                    Column(card) {
                        GroupLabel(stringResource(Res.string.program_records_set))
                        ListCard {
                            progress.records.forEachIndexed { i, record ->
                                if (i > 0) RowDivider()
                                RecordRow(record)
                            }
                        }
                    }
                }
            }
        }
        if (progress.finished) {
            item(key = "again", span = StaggeredGridItemSpan.FullLine) {
                Arrival(entrance, AGAIN_STEP) { Again(actions, card.padding(vertical = Spacing.space12)) }
            }
        }
        item(key = "done") { Arrival(entrance, DONE_STEP) { DaysDone(state, actions.onOpenSession, card) } }
    }
}

/** One part of the page, rising in [step] stagger steps after the first. */
@Composable
private fun Arrival(entrance: MutableTransitionState<Boolean>, step: Int, content: @Composable () -> Unit) {
    AnimatedVisibility(entrance, enter = enterRising(Motion.STAGGER_MILLIS * step)) { content() }
}

private const val STANDING_STEP = 0
private const val MAP_STEP = 1
private const val DONE_STEP = 2
private const val AGAIN_STEP = 3

/** The day to do next and the days done and left; once finished, the days, reps and weight the program came to. */
@Composable
private fun Standing(progress: ProgramProgress, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val next = progress.next
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        Text(
            if (next == null) stringResource(Res.string.program_finished) else programDayLine(next),
            style = IntervalTheme.type.caption,
            color = palette.textDim,
        )
        if (next == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
                StatTile(
                    stringResource(Res.string.program_days_done_label),
                    progress.lastDay.toString(),
                    Modifier.weight(1f),
                )
                StatTile(stringResource(Res.string.summary_reps), progress.reps.toString(), Modifier.weight(1f))
            }
            StatTile(
                stringResource(Res.string.session_moved),
                stringResource(Res.string.kg_value, formatKg(progress.tonnageKg)),
                Modifier.fillMaxWidth(),
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
                StatTile(
                    stringResource(Res.string.program_days_done_label),
                    progress.lastDay.toString(),
                    Modifier.weight(1f),
                )
                StatTile(
                    stringResource(Res.string.program_days_left_label),
                    progress.daysLeft.toString(),
                    Modifier.weight(1f),
                )
            }
        }
    }
}

/** Every day of the program, a row a week: done days filled, the next ringed, the rest to come. */
@Composable
private fun DayMap(progress: ProgramProgress) {
    val program = progress.program
    Column(Modifier.padding(Spacing.space16), verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        for (week in 1..program.weeks) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.program_week, week),
                    style = IntervalTheme.type.caption,
                    color = IntervalTheme.palette.textDim,
                    modifier = Modifier.weight(1f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
                    val first = (week - 1) * program.daysPerWeek + 1
                    for (day in first until first + program.daysPerWeek) DayMark(day, progress.standing(day))
                }
            }
        }
    }
}

/** A day on the map, read out as "Day 3: next". It fills in as the day is done, rather than switching. */
@Composable
private fun DayMark(number: Int, standing: DayStanding) {
    val palette = IntervalTheme.palette
    val fill = when (standing) {
        DayStanding.Done -> palette.accent
        DayStanding.DoneBefore -> palette.accent.copy(alpha = Opacity.DISABLED)
        DayStanding.Missed, DayStanding.Next, DayStanding.ToCome -> palette.surfaceVariant
    }
    val ring = when (standing) {
        DayStanding.Next -> palette.text
        DayStanding.Missed -> palette.textDim
        DayStanding.Done, DayStanding.DoneBefore, DayStanding.ToCome -> null
    }
    val word = stringResource(
        when (standing) {
            DayStanding.DoneBefore -> Res.string.program_day_done_before
            DayStanding.Done -> Res.string.program_day_done
            DayStanding.Missed -> Res.string.program_day_missed
            DayStanding.Next -> Res.string.program_day_next
            DayStanding.ToCome -> Res.string.program_day_to_come
        },
    )
    val description = stringResource(Res.string.program_day_cd, number, word)
    val dot by animateColorAsState(fill, tween(Motion.STATE_MILLIS, easing = StandardEasing), label = "day")
    Box(
        Modifier
            .size(Sizes.programDayMark)
            .clip(Corners.full)
            .background(dot)
            .then(if (ring != null) Modifier.border(Sizes.border, ring, Corners.full) else Modifier)
            .clearAndSetSemantics { contentDescription = description },
    )
}

/** Each day done, oldest first, with its date and the sets done of those planned; a tap opens its session. */
@Composable
private fun DaysDone(
    state: ProgramProgressUiState,
    onOpenSession: (uid: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = IntervalTheme.palette
    val before = state.rows.count { it.day.session == null }
    val logged = state.rows.filter { it.day.session != null }
    Column(modifier) {
        GroupLabel(stringResource(Res.string.program_days_done))
        ListCard {
            if (state.rows.isEmpty()) {
                Text(
                    stringResource(Res.string.program_none_done),
                    style = IntervalTheme.type.body,
                    color = palette.textDim,
                    modifier = Modifier.padding(Spacing.space16),
                )
            }
            if (before > 0) {
                Column(Modifier.padding(Spacing.space16)) {
                    Text(
                        if (before == 1) {
                            stringResource(Res.string.program_preview_day, 1)
                        } else {
                            stringResource(Res.string.program_days_before_copy, before)
                        },
                        style = IntervalTheme.type.label,
                        color = palette.text,
                    )
                    Text(
                        stringResource(Res.string.program_day_before_copy),
                        style = IntervalTheme.type.meta,
                        color = palette.textDim,
                    )
                }
            }
            logged.forEachIndexed { i, row ->
                if (i > 0 || before > 0) RowDivider()
                DoneRow(row, onOpenSession)
            }
        }
    }
}

/** "Day 7" over its date and "9 of 9 sets", opened by a tap. */
@Composable
private fun DoneRow(row: DoneRow, onOpenSession: (uid: String) -> Unit) {
    val palette = IntervalTheme.palette
    val uid = row.day.session?.uid ?: return
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interaction,
                LocalIndication.current,
                onClickLabel = stringResource(Res.string.program_open_session_cd),
                role = Role.Button,
            ) { onOpenSession(uid) }
            .pressScale(interaction)
            .padding(start = Spacing.space16, top = Spacing.space16, bottom = Spacing.space16, end = Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.program_preview_day, row.day.number),
                style = IntervalTheme.type.label,
                color = palette.text,
            )
            Text(
                stringResource(
                    Res.string.common_joined,
                    row.date,
                    stringResource(Res.string.program_sets_done, row.day.setsDone, row.day.setsPlanned),
                ),
                style = IntervalTheme.type.meta,
                color = palette.textDim,
            )
        }
        Icon(
            IntervalIcons.Chevron,
            contentDescription = null,
            tint = palette.textDim,
            modifier = Modifier.size(Sizes.icon),
        )
    }
}

/** Once every day is done: Follow again, Pick another program, and what Follow again does. */
@Composable
private fun Again(actions: ProgramProgressActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier.widthIn(max = Sizes.content).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.space8),
        ) {
            AccentButton(
                stringResource(Res.string.program_follow_again),
                actions.onFollowAgain,
                Modifier.fillMaxWidth(),
            )
            SecondaryButton(
                stringResource(Res.string.program_pick_another),
                actions.onPickAnother,
                Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(Res.string.program_follow_again_hint),
                style = IntervalTheme.type.meta,
                color = IntervalTheme.palette.textDim,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@PreviewFormFactors
@Composable
private fun ProgramProgressPreview() {
    IntervalTheme(darkTheme = true) {
        ProgramProgressScreen(
            ProgramProgressUiState(programProgress(Followed(builtInPrograms.first(), 0), emptyList())),
            ProgramProgressActions(),
        )
    }
}
