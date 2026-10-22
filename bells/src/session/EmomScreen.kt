package dev.justinsmith.bells.session

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.min
import dev.justinsmith.bells.domain.EmomPhase
import dev.justinsmith.bells.domain.EmomState
import dev.justinsmith.bells.resources.Res
import dev.justinsmith.bells.resources.emom_get_ready
import dev.justinsmith.bells.resources.emom_minute
import dev.justinsmith.bells.resources.emom_next
import dev.justinsmith.bells.resources.emom_rest
import dev.justinsmith.bells.resources.emom_set_done
import dev.justinsmith.bells.resources.emom_stop
import dev.justinsmith.bells.resources.emom_work
import dev.justinsmith.bells.resources.summary_done
import dev.justinsmith.bells.ui.liftName
import dev.justinsmith.bells.ui.setLine
import dev.justinsmith.interval.platform.KeepScreenOn
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.SecondaryButton
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.surfaceButton
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
    LaunchedEffect(state.phase, state.minute) { haptics.performHapticFeedback(HapticFeedbackType.Confirm) }
    KeepScreenOn()
    Box(modifier.fillMaxSize().background(colors.ground), contentAlignment = Alignment.TopCenter) {
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
                Text(
                    stringResource(Res.string.emom_minute, state.minute, state.minutes),
                    style = IntervalTheme.type.label,
                    color = colors.onGround,
                    modifier = Modifier.weight(1f).padding(horizontal = Spacing.space8),
                )
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val diameter = min(min(maxWidth, maxHeight), Sizes.ringMax)
                Ring(state.minuteProgress, color, Modifier.size(diameter))
                EmomReadout(run, state, color)
            }
            val working = state.phase == EmomPhase.Work
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.space16)
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
    }
}

@Composable
private fun StopPill(onStop: () -> Unit) {
    SecondaryButton(stringResource(Res.string.emom_stop), onStop, Modifier.widthIn(min = Sizes.pillWidth))
}

/** The minute going by, from 12 o'clock clockwise, over the control-coloured track. */
@Composable
private fun Ring(progress: Float, color: Color, modifier: Modifier = Modifier) {
    val track = IntervalTheme.workout.control
    Canvas(modifier) {
        val stroke = Sizes.ring.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(track, 0f, FULL_TURN, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
        drawArc(
            color,
            START_ANGLE,
            FULL_TURN * progress.coerceIn(0f, 1f),
            false,
            Offset(inset, inset),
            arcSize,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

private const val FULL_TURN = 360f
private const val START_ANGLE = -90f

/** Inside the ring: the phase word, the clock, and the set to do (or next, while resting). */
@Composable
private fun EmomReadout(run: EmomRun, state: EmomState, color: Color) {
    val colors = IntervalTheme.workout
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
        Text(
            formatClock(state.secondsLeft),
            style = IntervalTheme.type.display,
            color = colors.onGround,
        )
        val side = run.sideOf(state.minute)
        val task = setLine(run.bellKg, run.reps, side, run.double, run.exercise.equipment, band = run.band)
        Text(
            if (state.phase ==
                EmomPhase.Rest
            ) {
                stringResource(Res.string.emom_next, task)
            } else {
                "${liftName(run.exercise, run.double)} · $task"
            },
            style = IntervalTheme.type.label,
            color = colors.onGroundDim,
        )
    }
}
