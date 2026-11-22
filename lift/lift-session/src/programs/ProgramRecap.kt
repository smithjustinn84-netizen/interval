package dev.justinsmith.lift.programs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.SecondaryButton
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.lift.domain.EaseReason
import dev.justinsmith.lift.domain.Followed
import dev.justinsmith.lift.domain.Gear
import dev.justinsmith.lift.domain.LiftChange
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.NextCycle
import dev.justinsmith.lift.domain.Program
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.domain.ProgramRecap
import dev.justinsmith.lift.domain.SetLog
import dev.justinsmith.lift.domain.program
import dev.justinsmith.lift.domain.programProgress
import dev.justinsmith.lift.domain.programRecap
import dev.justinsmith.lift.session.resources.Res
import dev.justinsmith.lift.session.resources.program_follow_again
import dev.justinsmith.lift.session.resources.program_follow_again_hint
import dev.justinsmith.lift.session.resources.program_pick_another
import dev.justinsmith.lift.session.resources.recap_ease_off
import dev.justinsmith.lift.session.resources.recap_follow
import dev.justinsmith.lift.session.resources.recap_from_last
import dev.justinsmith.lift.session.resources.recap_from_plan
import dev.justinsmith.lift.session.resources.recap_go_again
import dev.justinsmith.lift.session.resources.recap_go_again_heavier
import dev.justinsmith.lift.session.resources.recap_hard_going
import dev.justinsmith.lift.session.resources.recap_lift_change
import dev.justinsmith.lift.session.resources.recap_lifts
import dev.justinsmith.lift.session.resources.recap_missed_work
import dev.justinsmith.lift.session.resources.recap_move_on
import dev.justinsmith.lift.session.resources.recap_move_on_why
import dev.justinsmith.lift.session.resources.recap_next_cycle
import dev.justinsmith.lift.session.resources.recap_sets_done
import dev.justinsmith.lift.ui.liftName
import dev.justinsmith.lift.ui.programName
import dev.justinsmith.lift.ui.setLine
import org.jetbrains.compose.resources.stringResource

/**
 * A finished program's next cycle, as a coach would call it: go again (from the loads last lifted, when the program
 * names none), a few easy days first (too much undone, or sessions that felt very hard), or on to the next program
 * after a beginner's one. Its button does what it says: Follow again, or follow the next program; Pick another
 * program is always under it.
 */
@Composable
internal fun NextCycleCard(recap: ProgramRecap, actions: ProgramProgressActions, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val next = recap.next
    Column(modifier) {
        GroupLabel(stringResource(Res.string.recap_next_cycle))
        ListCard {
            RecapHeroBand(recap)
            Column(Modifier.padding(Spacing.space16), verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
                Text(
                    stringResource(
                        when (next) {
                            is NextCycle.GoAgain ->
                                if (next.fromLast) Res.string.recap_go_again_heavier else Res.string.recap_go_again

                            is NextCycle.EaseOff -> Res.string.recap_ease_off

                            is NextCycle.MoveOn -> Res.string.recap_move_on
                        },
                    ),
                    style = IntervalTheme.type.section,
                    color = palette.accent,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(Res.string.recap_sets_done, recap.setsDone, recap.setsPlanned),
                    style = IntervalTheme.type.body,
                    color = palette.text,
                )
                Text(nextCycleWhy(next), style = IntervalTheme.type.meta, color = palette.textDim)
                NextCycleButtons(next, actions)
            }
        }
    }
}

@Composable
private fun nextCycleWhy(next: NextCycle): String = when (next) {
    is NextCycle.GoAgain ->
        stringResource(if (next.fromLast) Res.string.recap_from_last else Res.string.recap_from_plan)

    is NextCycle.EaseOff -> stringResource(
        when (next.reason) {
            EaseReason.MissedWork -> Res.string.recap_missed_work
            EaseReason.HardGoing -> Res.string.recap_hard_going
        },
    )

    is NextCycle.MoveOn -> stringResource(Res.string.recap_move_on_why, programName(next.program))
}

/** The call's own button first, then the others: a move on keeps Follow again under it, quietly. */
@Composable
private fun NextCycleButtons(next: NextCycle, actions: ProgramProgressActions) {
    Column(
        Modifier.fillMaxWidth().padding(top = Spacing.space8),
        verticalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        if (next is NextCycle.MoveOn) {
            AccentButton(
                stringResource(Res.string.recap_follow, programName(next.program)),
                { actions.onFollow(next.program) },
                Modifier.fillMaxWidth(),
            )
            SecondaryButton(
                stringResource(Res.string.program_pick_another),
                actions.onPickAnother,
                Modifier.fillMaxWidth(),
            )
            QuietTextButton(
                stringResource(Res.string.program_follow_again),
                actions.onFollowAgain,
                Modifier.fillMaxWidth(),
            )
        } else {
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
            )
        }
    }
}

/** Each lift done more than once in the run, its best set at the start beside its best at the end. */
@Composable
internal fun LiftChanges(lifts: List<LiftChange>, modifier: Modifier = Modifier) {
    Column(modifier) {
        GroupLabel(stringResource(Res.string.recap_lifts))
        ListCard {
            lifts.forEachIndexed { i, lift ->
                if (i > 0) RowDivider()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.space16, vertical = Spacing.space12),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
                ) {
                    RecapLiftTile(lift.exercise, lift.double)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.space4)) {
                        Text(
                            liftName(lift.exercise, lift.double),
                            style = IntervalTheme.type.label,
                            color = IntervalTheme.palette.text,
                        )
                        Text(
                            stringResource(Res.string.recap_lift_change, setLine(lift.first), setLine(lift.last)),
                            style = IntervalTheme.type.meta,
                            color = IntervalTheme.palette.textDim,
                        )
                        LiftChangeTrack(lift, Modifier.padding(top = Spacing.space4))
                    }
                }
            }
        }
    }
}

/** A beginner's program done in full, its bell a size heavier each week: the recap moves on. */
@PreviewFormFactors
@Composable
private fun ProgramRecapPreview() {
    val program = program(ProgramId.FirstHundredSwings)
    val sessions = (1..program.totalDays).map { previewDay(program, it) }.asReversed()
    val progress = programProgress(Followed(program, 0), sessions)
    IntervalTheme(darkTheme = true) {
        ProgramProgressScreen(
            ProgramProgressUiState(
                progress,
                recap = programRecap(progress, Gear(bells = PREVIEW_BELLS), PREVIEW_REST_SECONDS),
            ),
            ProgramProgressActions(),
        )
    }
}

private fun previewDay(program: Program, day: Int): LiftSession {
    val at = day * PREVIEW_DAY_MILLIS
    val kg = PREVIEW_FIRST_KG + (day - 1) / program.daysPerWeek * PREVIEW_STEP_KG
    val sets = program.day(day).plannedSets().mapIndexed { i, set ->
        SetLog.of(i.toLong(), set.exercise, kg, set.reps, set.side, at + i)
    }
    return LiftSession("d$day", at, at + 1, "", sets, program.key, day = day)
}

private const val PREVIEW_DAY_MILLIS = 86_400_000L
private const val PREVIEW_FIRST_KG = 16.0
private const val PREVIEW_STEP_KG = 4.0
private const val PREVIEW_REST_SECONDS = 60
private val PREVIEW_BELLS = listOf(
    PREVIEW_FIRST_KG,
    PREVIEW_FIRST_KG + PREVIEW_STEP_KG,
    PREVIEW_FIRST_KG + 2 * PREVIEW_STEP_KG,
)
