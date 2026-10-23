package dev.justinsmith.interval.workout

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import dev.justinsmith.interval.platform.prefersReducedMotion
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
import dev.justinsmith.interval.ui.CountdownClock
import dev.justinsmith.interval.ui.EnterEasing
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.MAX_ROUND_MARKS
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.ProgressRing
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.StandardEasing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** "Round 3 of 8 · 4:03 left". */
@Composable
internal fun RoundStatus(state: WorkoutUiState, modifier: Modifier = Modifier) {
    if (state.isDone) return
    Text(
        stringResource(Res.string.workout_status, state.round, state.rounds, state.sessionLeft),
        style = IntervalTheme.type.label,
        color = IntervalTheme.workout.onGround,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

/**
 * A short bar per round: finished ones filled, the round being worked in the phase colour ([accent]),
 * the rest dim, each crossfading as its turn comes. Bars narrow to share a tight row. On the summary
 * no round is being worked any more, so it shows only what was finished. Past [MAX_ROUND_MARKS] rounds
 * they would be too thin to read, so it draws nothing.
 */
@Composable
internal fun RoundDots(state: WorkoutUiState, accent: Color, modifier: Modifier = Modifier) {
    if (state.rounds > MAX_ROUND_MARKS) return
    val active = state.activeRound.takeUnless { state.isDone }
    Row(
        modifier.clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8, Alignment.CenterHorizontally),
    ) {
        repeat(state.rounds) { index ->
            val round = index + 1
            val target = when {
                round == active -> accent
                round <= state.completedRounds -> IntervalTheme.workout.onGround
                else -> IntervalTheme.workout.upcoming
            }
            val color by animateColorAsState(target, tween(Motion.STATE_MILLIS, easing = StandardEasing), label = "dot")
            Box(
                Modifier
                    .weight(1f, fill = false)
                    .size(Sizes.roundMarkWidth, Sizes.roundMarkHeight)
                    .background(color, Corners.full),
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
            Clock(state, accent, ringClockSize(diameter, state.clock.length, IntervalTheme.type.clock.fontSize))
            Text(
                nextUpText(state.next),
                style = IntervalTheme.type.label,
                color = IntervalTheme.workout.onGroundDim,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Sideways: the clock alone in the ring (its box takes [ringModifier]), and the words beside it. */
@Composable
internal fun LandscapeReadout(
    state: WorkoutUiState,
    accent: Color,
    diameter: Dp,
    modifier: Modifier = Modifier,
    ringModifier: Modifier = Modifier,
) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space32, Alignment.CenterHorizontally),
    ) {
        Box(ringModifier.size(diameter), contentAlignment = Alignment.Center) {
            ProgressRing(state.progress, accent, Modifier.fillMaxSize())
            Clock(state, accent, ringClockSize(diameter, state.clock.length, IntervalTheme.type.clock.fontSize))
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
            PhaseLabel(state, accent)
            Text(nextUpText(state.next), style = IntervalTheme.type.label, color = IntervalTheme.workout.onGroundDim)
            RoundStatus(state)
            RoundDots(state, accent, Modifier.padding(top = Spacing.space4))
            TapHint(state)
        }
    }
}

/** The phase word, in the phase colour. A new word rises in as the old one slips away above it. */
@Composable
private fun PhaseLabel(state: WorkoutUiState, accent: Color) {
    val label: StringResource = when {
        state.paused -> Res.string.workout_paused

        else -> when (state.phase) {
            Phase.Prep -> Res.string.phase_get_ready
            Phase.Work -> Res.string.phase_work
            Phase.Rest -> Res.string.phase_rest
            Phase.Done -> Res.string.phase_done
        }
    }
    AnimatedContent(
        targetState = label,
        transitionSpec = {
            val enter = fadeIn(tween(Motion.CONTENT_MILLIS)) +
                slideInVertically(tween(Motion.CONTENT_MILLIS, easing = EnterEasing)) {
                    (it * Motion.PHASE_WORD_RISE_SHARE).toInt()
                }
            val exit = fadeOut(tween(Motion.EXIT_MILLIS)) +
                slideOutVertically(tween(Motion.EXIT_MILLIS, easing = StandardEasing)) {
                    -(it * Motion.PHASE_WORD_RISE_SHARE).toInt()
                }
            enter.togetherWith(exit).using(SizeTransform(clip = false))
        },
        label = "phase",
    ) { res ->
        Text(
            stringResource(res),
            style = IntervalTheme.type.stat,
            color = if (state.paused) IntervalTheme.workout.onGroundDim else accent,
        )
    }
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

/** The digits inside the ring: see [CountdownClock]. A phase or round change starts a new interval. */
@Composable
private fun Clock(state: WorkoutUiState, accent: Color, size: TextUnit) {
    CountdownClock(
        state.clock,
        intervalKey = state.phase to state.completedRounds,
        countingDown = state.countingDown,
        accent = accent,
        style = IntervalTheme.type.clock.copy(fontSize = size, lineHeight = size),
    )
}

/** "Tap anywhere to pause", or "…to resume", which breathes while paused so the way on is noticed. */
@Composable
internal fun TapHint(state: WorkoutUiState, modifier: Modifier = Modifier) {
    val breathing = state.paused && !prefersReducedMotion()
    Text(
        stringResource(if (state.paused) Res.string.workout_tap_resume else Res.string.workout_tap_pause),
        style = IntervalTheme.type.meta,
        color = if (state.paused) IntervalTheme.workout.onGround else IntervalTheme.workout.onGroundDim,
        fontWeight = if (state.paused) FontWeight.SemiBold else FontWeight.Normal,
        textAlign = TextAlign.Center,
        modifier = if (breathing) modifier.breathing() else modifier,
    )
}

/** Fades slowly between full and [Motion.BREATH_MIN_ALPHA] and back, for as long as it is applied. */
@Composable
private fun Modifier.breathing(): Modifier {
    val breath = rememberInfiniteTransition(label = "breath")
    val alpha by breath.animateFloat(
        initialValue = 1f,
        targetValue = Motion.BREATH_MIN_ALPHA,
        animationSpec = infiniteRepeatable(
            tween(Motion.BREATH_MILLIS / 2, easing = StandardEasing),
            RepeatMode.Reverse,
        ),
        label = "alpha",
    )
    return graphicsLayer { this.alpha = alpha }
}

/**
 * The clock fills the share of the ring its digits allow, up to [max], the clock style's size: "0:14"
 * in a phone's ring comes out around 160sp, as the design canvas draws it, and "10:00" smaller.
 */
private fun ringClockSize(diameter: Dp, chars: Int, max: TextUnit): TextUnit =
    (diameter.value * RING_CLOCK_SHARE / (chars * CLOCK_EMS_PER_CHAR)).coerceAtMost(max.value).sp

/** How much of the ring's width the clock may take, leaving the stroke and some air. */
private const val RING_CLOCK_SHARE = 0.82f

/** Width per clock character in Barlow Condensed ExtraBold. */
private const val CLOCK_EMS_PER_CHAR = 0.46f
