package dev.justinsmith.bells.session

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import dev.justinsmith.bells.domain.BuiltInExercise
import dev.justinsmith.bells.domain.EmomClock
import dev.justinsmith.bells.domain.EmomPhase
import dev.justinsmith.bells.domain.EmomState
import dev.justinsmith.bells.domain.Side
import dev.justinsmith.bells.session.resources.Res
import dev.justinsmith.bells.session.resources.emom_get_ready
import dev.justinsmith.bells.session.resources.emom_minute
import dev.justinsmith.bells.session.resources.emom_next
import dev.justinsmith.bells.session.resources.emom_rest
import dev.justinsmith.bells.session.resources.emom_set_done
import dev.justinsmith.bells.session.resources.emom_stop
import dev.justinsmith.bells.session.resources.emom_work
import dev.justinsmith.bells.session.resources.summary_done
import dev.justinsmith.bells.ui.liftName
import dev.justinsmith.bells.ui.setLine
import dev.justinsmith.interval.platform.KeepScreenOn
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.CountdownClock
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.ProgressRing
import dev.justinsmith.interval.ui.SecondaryButton
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.isLandscape
import dev.justinsmith.interval.ui.ringClockSize
import dev.justinsmith.interval.ui.surfaceButton
import dev.justinsmith.interval.ui.windowIsRoomy
import org.jetbrains.compose.resources.stringResource

/**
 * An EMOM, in the workout's look: the phase colour on the band, the ring (the minute going by),
 * the phase word and Set done. Count-in amber, work red until the set is done, then rest green until the
 * next minute. A haptic marks each change; the beeps come from the view model.
 */
@Composable
fun EmomScreen(
    run: EmomRun,
    state: EmomState,
    onFinishSet: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = IntervalTheme.workout
    val phaseColor = when (state.phase) {
        EmomPhase.GetReady -> colors.prep
        EmomPhase.Work -> colors.work
        EmomPhase.Rest -> colors.rest
        EmomPhase.Done -> colors.done
    }
    val color by animateColorAsState(phaseColor, tween(Motion.PHASE_CROSSFADE_MILLIS))
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state.phase, state.minute) {
        haptics.performHapticFeedback(
            if (state.phase == EmomPhase.Done) HapticFeedbackType.LongPress else HapticFeedbackType.Confirm,
        )
    }
    KeepScreenOn()
    BoxWithConstraints(modifier.fillMaxSize().background(colors.ground), contentAlignment = Alignment.TopCenter) {
        // A tablet has the height for the readout inside the ring either way up, and room for a bigger ring.
        val roomy = windowIsRoomy()
        if (isLandscape && !roomy) {
            LandscapeEmom(run, state, color, onFinishSet, onStop)
        } else {
            PortraitEmom(run, state, color, onFinishSet, onStop, roomy)
        }
    }
}

/**
 * Upright, and on a tablet either way up: the band, Stop and the minute across the top, the ring with the
 * readout inside, Set done at the foot. On a tablet ([roomy]) the ring grows past a phone's widest, and the
 * clock in it to the workout's size.
 */
@Composable
private fun PortraitEmom(
    run: EmomRun,
    state: EmomState,
    color: Color,
    onFinishSet: () -> Unit,
    onStop: () -> Unit,
    roomy: Boolean,
) {
    Column(
        Modifier.widthIn(
            max = Sizes.contentWide,
        ).fillMaxSize().safeDrawingPadding().padding(horizontal = Spacing.space16),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(Sizes.phaseBand).clip(Corners.full).background(color))
        Row(
            Modifier.fillMaxWidth().padding(top = Spacing.space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StopPill(onStop)
            MinuteText(state, Modifier.weight(1f).padding(horizontal = Spacing.space8))
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val diameter = minOf(maxWidth, maxHeight, if (roomy) Sizes.ringMaxRoomy else Sizes.ringMax)
            ProgressRing(
                state.minuteProgress,
                color,
                Modifier.size(diameter),
                glideMillis = Motion.EMOM_RING_GLIDE_MILLIS,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PhaseWord(state, color)
                val type = IntervalTheme.type
                ClockText(state, color, diameter, if (roomy) type.clock else type.display)
                TaskText(run, state)
            }
        }
        SetDone(state, color, onFinishSet, Modifier.fillMaxWidth().padding(bottom = Spacing.space16))
    }
}

/**
 * Sideways, as the workout lies: the band down the start edge, the clock alone in the ring, and beside it a
 * column of Stop and the minute, the phase word and the set, and Set done at its foot. A phone on its side is
 * too short for the readout inside the ring.
 */
@Composable
private fun LandscapeEmom(run: EmomRun, state: EmomState, color: Color, onFinishSet: () -> Unit, onStop: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .safeDrawingPadding()
                .fillMaxHeight()
                .width(Sizes.phaseBand)
                .clip(Corners.full)
                .background(color),
        )
        Row(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(start = Sizes.phaseBand + Spacing.space8, end = Spacing.space20)
                .padding(vertical = Spacing.space16),
            horizontalArrangement = Arrangement.spacedBy(Spacing.space16),
        ) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                val diameter = minOf(maxWidth, maxHeight, Sizes.ringMax)
                Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
                    ProgressRing(
                        state.minuteProgress,
                        color,
                        Modifier.fillMaxSize(),
                        glideMillis = Motion.EMOM_RING_GLIDE_MILLIS,
                    )
                    ClockText(state, color, diameter, IntervalTheme.type.clock)
                }
            }
            Column(
                Modifier.width(Sizes.setupColumnLandscape).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(Spacing.space8),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StopPill(onStop)
                    MinuteText(state, Modifier.weight(1f).padding(horizontal = Spacing.space8))
                }
                Spacer(Modifier.weight(1f))
                PhaseWord(state, color)
                TaskText(run, state)
                Spacer(Modifier.weight(1f))
                SetDone(state, color, onFinishSet, Modifier.fillMaxWidth())
            }
        }
    }
}

/** "Minute 3 of 10". */
@Composable
private fun MinuteText(state: EmomState, modifier: Modifier = Modifier) {
    Text(
        stringResource(Res.string.emom_minute, state.minute, state.minutes),
        style = IntervalTheme.type.label,
        color = IntervalTheme.workout.onGround,
        modifier = modifier,
    )
}

/** Set done, in the phase colour and live only while working. */
@Composable
private fun SetDone(state: EmomState, color: Color, onFinishSet: () -> Unit, modifier: Modifier = Modifier) {
    val colors = IntervalTheme.workout
    val working = state.phase == EmomPhase.Work
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .height(Sizes.control)
            .surfaceButton(
                interaction,
                Corners.radius18,
                if (working) color else colors.control,
                onClick = onFinishSet,
                enabled = working,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(Res.string.emom_set_done),
            style = IntervalTheme.type.value,
            color = if (working) colors.onFill(color) else colors.onGroundDim,
        )
    }
}

@Composable
private fun StopPill(onStop: () -> Unit) {
    SecondaryButton(stringResource(Res.string.emom_stop), onStop, Modifier.widthIn(min = Sizes.pillWidth))
}

/** The phase word, in the phase colour. */
@Composable
private fun PhaseWord(state: EmomState, color: Color) {
    Text(
        stringResource(
            when (state.phase) {
                EmomPhase.GetReady -> Res.string.emom_get_ready
                EmomPhase.Work -> Res.string.emom_work
                EmomPhase.Rest -> Res.string.emom_rest
                EmomPhase.Done -> Res.string.summary_done
            },
        ),
        style = IntervalTheme.type.stat,
        color = color,
    )
}

/**
 * The seconds left in the minute (or the count-in), popping on its last three as the beeps go. It fills the
 * share of a ring [diameter] across its digits allow, up to the size of its [style]: on a phone the display
 * style between the phase word and the set, the clock style alone in the ring or in a tablet's.
 */
@Composable
private fun ClockText(state: EmomState, color: Color, diameter: Dp, style: TextStyle) {
    val text = formatClock(state.secondsLeft)
    val size = ringClockSize(diameter, text.length, style.fontSize)
    CountdownClock(
        text,
        intervalKey = state.phase to state.minute,
        countingDown = state.phase != EmomPhase.Done && state.secondsLeft in 1..EmomClock.COUNTDOWN,
        accent = color,
        style = style.copy(fontSize = size, lineHeight = size),
    )
}

/** The set to do, or next while resting. */
@Composable
private fun TaskText(run: EmomRun, state: EmomState) {
    val side = run.sideOf(state.minute)
    val task = setLine(run.bellKg, run.reps, side, run.double, run.exercise.equipment, band = run.band)
    Text(
        if (state.phase == EmomPhase.Rest) {
            stringResource(Res.string.emom_next, task)
        } else {
            "${liftName(run.exercise, run.double)} · $task"
        },
        style = IntervalTheme.type.label,
        color = IntervalTheme.workout.onGroundDim,
    )
}

@PreviewFormFactors
@Composable
private fun EmomScreenPreview() {
    IntervalTheme(darkTheme = true) {
        EmomScreen(
            EmomRun(
                BuiltInExercise.TwoHandSwing,
                bellKg = 24.0,
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
