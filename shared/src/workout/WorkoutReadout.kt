package dev.justinsmith.interval.workout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.next_last_round
import dev.justinsmith.interval.resources.next_rest
import dev.justinsmith.interval.resources.next_round
import dev.justinsmith.interval.resources.next_work
import dev.justinsmith.interval.resources.phase_done
import dev.justinsmith.interval.resources.phase_get_ready
import dev.justinsmith.interval.resources.phase_rest
import dev.justinsmith.interval.resources.phase_work
import dev.justinsmith.interval.resources.workout_paused
import dev.justinsmith.interval.resources.workout_status
import dev.justinsmith.interval.resources.workout_tap_pause
import dev.justinsmith.interval.resources.workout_tap_resume
import dev.justinsmith.interval.timer.Phase
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.MAX_ROUND_MARKS
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.PhaseColors
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import org.jetbrains.compose.resources.stringResource

/** "Round 3 of 8 · 4:03 left". */
@Composable
internal fun RoundStatus(state: WorkoutUiState, modifier: Modifier = Modifier) {
    if (state.isDone) return
    Text(
        stringResource(Res.string.workout_status, state.round, state.rounds, state.sessionLeft),
        style = IntervalTheme.type.label,
        color = PhaseColors.onGround,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

/**
 * A short bar per round: finished ones filled, the round being worked in the phase colour ([accent]),
 * the rest dim. Bars narrow to share a tight row. On the summary no round is being worked any more,
 * so it shows only what was finished.
 */
@Composable
internal fun RoundDots(state: WorkoutUiState, accent: Color, modifier: Modifier = Modifier) {
    val active = state.activeRound.takeUnless { state.isDone }
    Row(
        modifier.clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8, Alignment.CenterHorizontally),
    ) {
        repeat(state.rounds) { index ->
            val round = index + 1
            val color = when {
                round == active -> accent
                round <= state.completedRounds -> PhaseColors.onGround
                else -> PhaseColors.upcoming
            }
            Box(
                Modifier
                    .weight(1f, fill = false)
                    .size(Sizes.roundMarkWidth, Sizes.roundMarkHeight)
                    .background(color, Corners.full),
            )
        }
    }
}

/** How far through the current interval, as a ring in the phase colour over a dark track. */
@Composable
internal fun ProgressRing(progress: Float, accent: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.clearAndSetSemantics {}) {
        val stroke = Sizes.ring.toPx()
        val diameter = size.minDimension - stroke
        val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
        val arc = Size(diameter, diameter)
        drawArc(PhaseColors.control, 0f, FULL_TURN, useCenter = false, topLeft, arc, style = Stroke(stroke))
        if (progress > 0f) {
            drawArc(
                accent,
                startAngle = -QUARTER_TURN,
                sweepAngle = FULL_TURN * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arc,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
}

/** The ring with the phase word, clock and next up inside it, [diameter] across. */
@Composable
internal fun RingReadout(state: WorkoutUiState, accent: Color, diameter: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        ProgressRing(state.progress, accent, Modifier.fillMaxSize())
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PhaseLabel(state, accent)
            Clock(state, ringClockSize(diameter, state.clock.length))
            Text(
                nextUpText(state.next),
                style = IntervalTheme.type.label,
                color = PhaseColors.onGroundDim,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Sideways: the clock alone in the ring, and the words beside it. */
@Composable
internal fun LandscapeReadout(state: WorkoutUiState, accent: Color, diameter: Dp, modifier: Modifier = Modifier) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space32, Alignment.CenterHorizontally),
    ) {
        Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
            ProgressRing(state.progress, accent, Modifier.fillMaxSize())
            Clock(state, ringClockSize(diameter, state.clock.length))
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
            PhaseLabel(state, accent)
            Text(nextUpText(state.next), style = IntervalTheme.type.label, color = PhaseColors.onGroundDim)
            RoundStatus(state)
            if (state.rounds <= MAX_ROUND_MARKS) RoundDots(state, accent, Modifier.padding(top = Spacing.space4))
            TapHint(state)
        }
    }
}

@Composable
private fun PhaseLabel(state: WorkoutUiState, accent: Color) {
    val label = when {
        state.paused -> Res.string.workout_paused

        else -> when (state.phase) {
            Phase.Prep -> Res.string.phase_get_ready
            Phase.Work -> Res.string.phase_work
            Phase.Rest -> Res.string.phase_rest
            Phase.Done -> Res.string.phase_done
        }
    }
    Text(
        stringResource(label),
        style = IntervalTheme.type.stat,
        color = if (state.paused) PhaseColors.onGroundDim else accent,
    )
}

/** "Next: rest 0:15", "Last round — finish strong"; nothing once the workout is done. */
@Composable
private fun nextUpText(next: NextUp?): String = when (next) {
    is NextUp.Work -> stringResource(Res.string.next_work, next.clock)
    is NextUp.Rest -> stringResource(Res.string.next_rest, next.clock)
    is NextUp.Round -> stringResource(Res.string.next_round, next.round, next.of)
    NextUp.LastRound -> stringResource(Res.string.next_last_round)
    null -> ""
}

@Composable
private fun Clock(state: WorkoutUiState, size: TextUnit) {
    // A short pop each time a new interval starts, so the change registers even in peripheral vision.
    val scale = remember { Animatable(1f) }
    LaunchedEffect(state.phase, state.completedRounds) {
        scale.snapTo(Motion.CLOCK_POP_SCALE)
        scale.animateTo(1f, tween(Motion.CLOCK_POP_MILLIS))
    }
    Text(
        state.clock,
        style = IntervalTheme.type.clock.copy(fontSize = size, lineHeight = size),
        color = PhaseColors.onGround,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        },
    )
}

@Composable
internal fun TapHint(state: WorkoutUiState, modifier: Modifier = Modifier) {
    Text(
        stringResource(if (state.paused) Res.string.workout_tap_resume else Res.string.workout_tap_pause),
        style = IntervalTheme.type.meta,
        color = if (state.paused) PhaseColors.onGround else PhaseColors.onGroundDim,
        fontWeight = if (state.paused) FontWeight.SemiBold else FontWeight.Normal,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

/**
 * The clock fills the share of the ring its digits allow, up to the clock style's size: "0:14" in a
 * phone's ring comes out around 160sp, as the design canvas draws it, and "10:00" smaller.
 */
internal fun ringClockSize(diameter: Dp, chars: Int): TextUnit =
    (diameter.value * RING_CLOCK_SHARE / (chars * CLOCK_EMS_PER_CHAR)).coerceAtMost(CLOCK_MAX).sp

/** How much of the ring's width the clock may take, leaving the stroke and some air. */
private const val RING_CLOCK_SHARE = 0.82f

/** Width per clock character in Barlow Condensed ExtraBold. */
private const val CLOCK_EMS_PER_CHAR = 0.46f
private const val CLOCK_MAX = 240f
private const val FULL_TURN = 360f
private const val QUARTER_TURN = 90f
