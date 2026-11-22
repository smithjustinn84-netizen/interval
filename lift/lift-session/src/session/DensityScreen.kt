package dev.justinsmith.lift.session

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.justinsmith.interval.platform.LiveHeartRateState
import dev.justinsmith.interval.ui.HeartBeatBadge
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.RingDial
import dev.justinsmith.interval.ui.RingZone
import dev.justinsmith.interval.ui.RollingText
import dev.justinsmith.interval.ui.rememberRingZone
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.DensityPhase
import dev.justinsmith.lift.domain.DensityState
import dev.justinsmith.lift.domain.EmomClock
import dev.justinsmith.lift.domain.Load
import dev.justinsmith.lift.domain.Side
import dev.justinsmith.lift.session.resources.Res
import dev.justinsmith.lift.session.resources.density_best
import dev.justinsmith.lift.session.resources.density_cap
import dev.justinsmith.lift.session.resources.density_go
import dev.justinsmith.lift.session.resources.density_round_done
import dev.justinsmith.lift.session.resources.density_rounds
import dev.justinsmith.lift.session.resources.density_stop_text
import dev.justinsmith.lift.session.resources.density_stop_title
import dev.justinsmith.lift.session.resources.emom_get_ready
import dev.justinsmith.lift.session.resources.summary_done
import dev.justinsmith.lift.ui.liftName
import dev.justinsmith.lift.ui.setLine
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A density block, in the EMOM's look ([TimedBlockScaffold]): the phase colour on the band, the ring (the cap
 * going by) with the time left in it, the rounds done rolling up under it beside the best to beat, a ghost
 * doing the lift before them ([BlockFigure]), and one big Round done. Count-in amber, work red for the whole cap,
 * done green. A haptic marks each change of phase and each round; the beeps and the calls of the clock come from
 * the session's timers.
 */
@Composable
fun DensityScreen(
    run: DensityRun,
    state: DensityState,
    onFinishRound: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    progress: () -> Float = { state.progress },
    heart: @Composable () -> Unit = {},
    zone: RingZone? = null,
) {
    val colors = IntervalTheme.workout
    val block = TimedBlock(
        color = when (state.phase) {
            DensityPhase.GetReady -> colors.prep
            DensityPhase.Work -> colors.work
            DensityPhase.Done -> colors.done
        },
        phaseWord = stringResource(
            when (state.phase) {
                DensityPhase.GetReady -> Res.string.emom_get_ready
                DensityPhase.Work -> Res.string.density_go
                DensityPhase.Done -> Res.string.summary_done
            },
        ),
        heading = stringResource(Res.string.density_cap, state.minutes),
        // A one-sided lift's side turns each round.
        task = taskWords(
            liftName(run.exercise, run.double),
            setLine(run.load, run.reps, run.sideOf(state.rounds + 1)),
        ),
        action = stringResource(Res.string.density_round_done),
        stopTitle = stringResource(Res.string.density_stop_title),
        stopText = stringResource(Res.string.density_stop_text),
        secondsLeft = state.secondsLeft,
        clockKey = state.phase,
        beat = state.phase to state.rounds,
        dial = densityDial(state),
        working = state.phase == DensityPhase.Work,
        over = state.phase == DensityPhase.Done,
        largeAction = true,
    )
    TimedBlockScaffold(
        block,
        progress,
        onFinishRound,
        onStop,
        modifier,
        heart,
        tally = { Rounds(run, state, it) },
        figure = { BlockFigure(run.exercise, run.double, still = block.over, it) },
        zone = zone,
    )
}

/** The rounds done, rolling up as each is, and the best of the block before to beat, "Best: 14". */
@Composable
private fun Rounds(run: DensityRun, state: DensityState, modifier: Modifier = Modifier) {
    val colors = IntervalTheme.workout
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        RollingText(
            pluralStringResource(Res.plurals.density_rounds, state.rounds, state.rounds),
            rising = true,
            style = IntervalTheme.type.stat,
            color = colors.onGround,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        run.best?.let {
            Text(
                stringResource(Res.string.density_best, it),
                style = IntervalTheme.type.label,
                color = colors.onGroundDim,
            )
        }
    }
}

/** The ring as a dial of what it fills over: the count-in, then the whole cap; none once the block is over. */
private fun densityDial(state: DensityState): RingDial? = when (state.phase) {
    DensityPhase.GetReady -> RingDial((EmomClock.COUNT_IN_MILLIS / EmomClock.SECOND_MILLIS).toInt())
    DensityPhase.Work -> RingDial(state.minutes * (EmomClock.MINUTE_MILLIS / EmomClock.SECOND_MILLIS).toInt())
    DensityPhase.Done -> null
}

@PreviewFormFactors
@Composable
private fun DensityScreenPreview() {
    IntervalTheme(darkTheme = true) {
        DensityScreen(
            DensityRun(
                BuiltInExercise.ArmorBuildingComplex,
                Load.Bell(kg = 24.0, double = true),
                reps = 1,
                minutes = 20,
                firstSide = Side.Left,
                startedAtMillis = 0,
                rounds = 9,
                best = 14,
            ),
            DensityState(DensityPhase.Work, secondsLeft = 545, rounds = 9, minutes = 20, progress = 0.55f),
            onFinishRound = {},
            onStop = {},
        )
    }
}

@PreviewFormFactors
@Composable
private fun DensityScreenHeartPreview() {
    IntervalTheme(darkTheme = true) {
        DensityScreen(
            DensityRun(
                BuiltInExercise.ArmorBuildingComplex,
                Load.Bell(kg = 24.0, double = true),
                reps = 1,
                minutes = 20,
                firstSide = Side.Left,
                startedAtMillis = 0,
                rounds = 9,
                best = 14,
            ),
            DensityState(DensityPhase.Work, secondsLeft = 545, rounds = 9, minutes = 20, progress = 0.55f),
            onFinishRound = {},
            onStop = {},
            heart = { HeartBeatBadge(LiveHeartRateState.Reading(bpm = 164)) },
            zone = rememberRingZone(LiveHeartRateState.Reading(bpm = 164)),
        )
    }
}
