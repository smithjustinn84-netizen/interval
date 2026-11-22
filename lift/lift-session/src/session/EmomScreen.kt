package dev.justinsmith.lift.session

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.justinsmith.interval.platform.LiveHeartRateState
import dev.justinsmith.interval.ui.HeartBeatBadge
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.RingDial
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.EmomClock
import dev.justinsmith.lift.domain.EmomPhase
import dev.justinsmith.lift.domain.EmomState
import dev.justinsmith.lift.domain.Load
import dev.justinsmith.lift.domain.Side
import dev.justinsmith.lift.session.resources.Res
import dev.justinsmith.lift.session.resources.emom_get_ready
import dev.justinsmith.lift.session.resources.emom_minute
import dev.justinsmith.lift.session.resources.emom_rest
import dev.justinsmith.lift.session.resources.emom_set_done
import dev.justinsmith.lift.session.resources.emom_stop_text
import dev.justinsmith.lift.session.resources.emom_stop_title
import dev.justinsmith.lift.session.resources.emom_work
import dev.justinsmith.lift.session.resources.summary_done
import dev.justinsmith.lift.ui.liftName
import dev.justinsmith.lift.ui.setLine
import org.jetbrains.compose.resources.stringResource

/**
 * An EMOM, in the workout's look ([TimedBlockScaffold]): the phase colour on the band, the ring (the minute going
 * by), the phase word and Set done. Count-in amber, work red until the set is done, then rest green until the
 * next minute, its set shown as next. Under the ring a ghost does the lift ([BlockFigure]), the one every minute
 * is. A haptic marks each change; the beeps come from the view model. [heart] is the live heart rate, at the end
 * of the top row while a sensor is in use.
 */
@Composable
fun EmomScreen(
    run: EmomRun,
    state: EmomState,
    onFinishSet: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    progress: () -> Float = { state.minuteProgress },
    heart: @Composable () -> Unit = {},
) {
    val colors = IntervalTheme.workout
    val block = TimedBlock(
        color = when (state.phase) {
            EmomPhase.GetReady -> colors.prep
            EmomPhase.Work -> colors.work
            EmomPhase.Rest -> colors.rest
            EmomPhase.Done -> colors.done
        },
        phaseWord = stringResource(
            when (state.phase) {
                EmomPhase.GetReady -> Res.string.emom_get_ready
                EmomPhase.Work -> Res.string.emom_work
                EmomPhase.Rest -> Res.string.emom_rest
                EmomPhase.Done -> Res.string.summary_done
            },
        ),
        heading = stringResource(Res.string.emom_minute, state.minute, state.minutes),
        task = taskWords(
            liftName(run.exercise, run.double),
            setLine(run.load, run.reps, run.sideOf(state.minute)),
            next = state.phase == EmomPhase.Rest,
        ),
        action = stringResource(Res.string.emom_set_done),
        stopTitle = stringResource(Res.string.emom_stop_title),
        stopText = stringResource(Res.string.emom_stop_text),
        secondsLeft = state.secondsLeft,
        clockKey = state.phase to state.minute,
        beat = state.phase to state.minute,
        dial = emomDial(state),
        working = state.phase == EmomPhase.Work,
        over = state.phase == EmomPhase.Done,
    )
    TimedBlockScaffold(
        block,
        progress,
        onFinishSet,
        onStop,
        modifier,
        heart,
        figure = { BlockFigure(run.exercise, run.double, still = block.over, it) },
    )
}

/** The ring as a dial of what it fills over: the count-in, then each minute; none once the EMOM is over. */
private fun emomDial(state: EmomState): RingDial? = when (state.phase) {
    EmomPhase.GetReady -> RingDial((EmomClock.COUNT_IN_MILLIS / EmomClock.SECOND_MILLIS).toInt())
    EmomPhase.Work, EmomPhase.Rest -> RingDial((EmomClock.MINUTE_MILLIS / EmomClock.SECOND_MILLIS).toInt())
    EmomPhase.Done -> null
}

@PreviewFormFactors
@Composable
private fun EmomScreenPreview() {
    IntervalTheme(darkTheme = true) {
        EmomScreen(
            EmomRun(
                BuiltInExercise.TwoHandSwing,
                Load.Bell(kg = 24.0),
                reps = 10,
                minutes = 10,
                firstSide = Side.Left,
                startedAtMillis = 0,
            ),
            EmomState(EmomPhase.Work, minute = 3, minutes = 10, secondsLeft = 45, minuteProgress = 0.25f),
            onFinishSet = {},
            onStop = {},
        )
    }
}

@PreviewFormFactors
@Composable
private fun EmomScreenHeartPreview() {
    IntervalTheme(darkTheme = true) {
        EmomScreen(
            EmomRun(
                BuiltInExercise.TwoHandSwing,
                Load.Bell(kg = 24.0),
                reps = 10,
                minutes = 10,
                firstSide = Side.Left,
                startedAtMillis = 0,
            ),
            EmomState(EmomPhase.Work, minute = 3, minutes = 10, secondsLeft = 45, minuteProgress = 0.25f),
            onFinishSet = {},
            onStop = {},
            heart = { HeartBeatBadge(LiveHeartRateState.Reading(bpm = 156)) },
        )
    }
}
