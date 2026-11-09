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
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import dev.justinsmith.interval.platform.prefersReducedMotion
import dev.justinsmith.interval.timer.Phase
import dev.justinsmith.interval.ui.Burst
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.CountdownClock
import dev.justinsmith.interval.ui.EnterEasing
import dev.justinsmith.interval.ui.IntervalSizes
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.MAX_ROUND_MARKS
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.ProgressRing
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.StandardEasing
import dev.justinsmith.interval.ui.of
import dev.justinsmith.interval.ui.rememberCountdownPulse
import dev.justinsmith.interval.ui.ringClockSize
import dev.justinsmith.interval.workout.resources.Res
import dev.justinsmith.interval.workout.resources.next_block
import dev.justinsmith.interval.workout.resources.next_block_rest
import dev.justinsmith.interval.workout.resources.next_cool_down
import dev.justinsmith.interval.workout.resources.next_last_round
import dev.justinsmith.interval.workout.resources.next_rest
import dev.justinsmith.interval.workout.resources.next_round
import dev.justinsmith.interval.workout.resources.next_round_clock
import dev.justinsmith.interval.workout.resources.next_set
import dev.justinsmith.interval.workout.resources.next_set_clock
import dev.justinsmith.interval.workout.resources.next_set_rest
import dev.justinsmith.interval.workout.resources.next_warm_up
import dev.justinsmith.interval.workout.resources.next_work
import dev.justinsmith.interval.workout.resources.phase_block_rest
import dev.justinsmith.interval.workout.resources.phase_cool_down
import dev.justinsmith.interval.workout.resources.phase_done
import dev.justinsmith.interval.workout.resources.phase_get_ready
import dev.justinsmith.interval.workout.resources.phase_rest
import dev.justinsmith.interval.workout.resources.phase_set_rest
import dev.justinsmith.interval.workout.resources.phase_warm_up
import dev.justinsmith.interval.workout.resources.phase_work
import dev.justinsmith.interval.workout.resources.workout_paused
import dev.justinsmith.interval.workout.resources.workout_status
import dev.justinsmith.interval.workout.resources.workout_status_block
import dev.justinsmith.interval.workout.resources.workout_status_left
import dev.justinsmith.interval.workout.resources.workout_status_move
import dev.justinsmith.interval.workout.resources.workout_status_set
import dev.justinsmith.interval.workout.resources.workout_tap_pause
import dev.justinsmith.interval.workout.resources.workout_tap_resume
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * "Round 3 of 8 · 4:03 left", with the set first in a workout of sets ("Set 2 of 4 · Round 3 of 8 · 4:03 left");
 * in the warm-up and cool-down, outside the rounds, "12:03 left". A flow counts its moves: "Move 3 of 6 · 5:10 left".
 */
@Composable
internal fun RoundStatus(state: WorkoutUiState, modifier: Modifier = Modifier) {
    if (state.isDone) return
    val bookend = state.phase == Phase.WarmUp || state.phase == Phase.CoolDown
    val step = state.flow?.step
    val rounds = when {
        step != null -> stringResource(Res.string.workout_status_move, step.number, step.of, state.sessionLeft)

        bookend -> stringResource(Res.string.workout_status_left, state.sessionLeft)

        state.sets > 1 -> stringResource(
            Res.string.workout_status_set,
            state.set,
            state.sets,
            state.round,
            state.setRounds,
            state.sessionLeft,
        )

        else -> stringResource(Res.string.workout_status, state.round, state.setRounds, state.sessionLeft)
    }
    Text(
        if (state.blocks > 1 && !bookend && step == null) {
            stringResource(Res.string.workout_status_block, state.block + 1, state.blocks, rounds)
        } else {
            rounds
        },
        style = IntervalTheme.type.label,
        color = IntervalTheme.workout.onGround,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

/**
 * A short bar per round of the set: finished ones filled, the round being worked in the phase colour ([accent]),
 * the rest dim, each crossfading as its turn comes. Bars narrow to share a tight row. On the summary
 * no round is being worked any more, so it shows only what was finished, and nothing for a workout of sets,
 * whose lines say it, nor for a flow. Past [MAX_ROUND_MARKS] rounds they would be too thin to read, so it draws
 * nothing. A flow has a bar a move.
 */
@Composable
internal fun RoundDots(state: WorkoutUiState, accent: Color, modifier: Modifier = Modifier) {
    val marks = roundMarks(state) ?: return
    Row(
        modifier.clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8, Alignment.CenterHorizontally),
    ) {
        repeat(marks.count) { index ->
            val round = index + 1
            val target = when {
                round == marks.active -> accent
                round <= marks.completed -> IntervalTheme.workout.onGround
                else -> IntervalTheme.workout.upcoming
            }
            val color by animateColorAsState(target, tween(Motion.STATE_MILLIS, easing = StandardEasing), label = "dot")
            Box(
                Modifier
                    .weight(1f, fill = false)
                    .size(IntervalSizes.roundMarkWidth, IntervalSizes.roundMarkHeight)
                    .background(color, Corners.full),
            )
        }
    }
}

/** The bars [RoundDots] draws: how many, how many are behind, and the one being worked. */
private class RoundMarks(val count: Int, val completed: Int, val active: Int?)

/** A flow's moves, the one held and those behind it; else the rounds of the set; null where none are drawn. */
private fun roundMarks(state: WorkoutUiState): RoundMarks? {
    val flow = state.flow
    return when {
        flow != null -> RoundMarks(flow.of, flow.done, flow.step?.number?.takeIf { state.phase == Phase.Work })
            .takeUnless { state.isDone }

        state.setRounds > MAX_ROUND_MARKS || (state.isDone && state.sets > 1) -> null

        else -> RoundMarks(state.setRounds, state.completedSetRounds, state.activeRound.takeUnless { state.isDone })
    }
}

/** The ring with the phase word, clock and next up inside it, [diameter] across. */
@Composable
internal fun RingReadout(state: WorkoutUiState, accent: Color, diameter: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        PhaseBurst(state, Modifier.matchParentSize())
        ProgressRing(state.progress, accent, Modifier.fillMaxSize(), strokeScale = ringSwell(state))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PhaseLabel(state, accent)
            Clock(state, accent, ringClockSize(diameter, state.clock.length, IntervalTheme.type.clock.fontSize))
            val nextText = nextText(state)
            if (nextText.isNotEmpty()) {
                Text(
                    nextText,
                    style = IntervalTheme.type.label,
                    color = IntervalTheme.workout.onGroundDim,
                    textAlign = TextAlign.Center,
                )
            }
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
    onOpenMove: () -> Unit = {},
) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space32, Alignment.CenterHorizontally),
    ) {
        Box(ringModifier.size(diameter), contentAlignment = Alignment.Center) {
            PhaseBurst(state, Modifier.matchParentSize())
            ProgressRing(state.progress, accent, Modifier.fillMaxSize(), strokeScale = ringSwell(state))
            Clock(state, accent, ringClockSize(diameter, state.clock.length, IntervalTheme.type.clock.fontSize))
        }
        Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
            state.flow?.let { flow ->
                flow.step?.let { FlowHeading(it, accent, centered = false) }
                if (flow.figures) FlowFigure(flow, state.paused, onOpenMove)
            }
            PhaseLabel(state, accent)
            val nextText = nextText(state)
            if (nextText.isNotEmpty()) {
                Text(nextText, style = IntervalTheme.type.label, color = IntervalTheme.workout.onGroundDim)
            }
            RoundStatus(state)
            RoundDots(state, accent, Modifier.padding(top = Spacing.space4))
            TapHint(state)
        }
    }
}

/** The ring thickens with the clock's pop on each of an interval's last seconds: [rememberCountdownPulse]. */
@Composable
private fun ringSwell(state: WorkoutUiState): Float =
    1f + rememberCountdownPulse(state.clock, state.countingDown) * (Motion.COUNTDOWN_RING_SCALE - 1f)

/**
 * A change of interval sends one ring of the new phase's colour out from the clock as the ground crossfades
 * to it: the colour the ground is going to, not the one it is passing through. Not the interval the screen
 * opened on (the workout rising into place marks a start, and a workout restored is already under way), nor
 * Done, which the summary marks itself. [Burst] draws nothing under reduced motion.
 */
@Composable
private fun PhaseBurst(state: WorkoutUiState, modifier: Modifier = Modifier) {
    val interval = state.phase to state.completedRounds
    val opened = remember { interval }
    if (interval != opened && !state.isDone) {
        key(interval) { Burst(listOf(IntervalTheme.workout.of(state.phase)), modifier) }
    }
}

/** The phase word, in the phase colour. A new word rises in as the old one slips away above it. */
@Composable
private fun PhaseLabel(state: WorkoutUiState, accent: Color) {
    val reducedMotion = prefersReducedMotion()
    val label: StringResource = when {
        state.paused -> Res.string.workout_paused

        state.flow != null -> flowPhaseWord(state.phase, state.flow.step)

        else -> when (state.phase) {
            Phase.Prep -> Res.string.phase_get_ready
            Phase.WarmUp -> Res.string.phase_warm_up
            Phase.Work -> Res.string.phase_work
            Phase.Rest -> Res.string.phase_rest
            Phase.SetRest -> if (state.inBlockRest) Res.string.phase_block_rest else Res.string.phase_set_rest
            Phase.CoolDown -> Res.string.phase_cool_down
            Phase.Done -> Res.string.phase_done
        }
    }
    AnimatedContent(
        targetState = label,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        transitionSpec = {
            if (reducedMotion) {
                fadeIn(tween(Motion.CONTENT_MILLIS))
                    .togetherWith(fadeOut(tween(Motion.EXIT_MILLIS)))
                    .using(SizeTransform(clip = false))
            } else {
                val enter = fadeIn(tween(Motion.CONTENT_MILLIS)) +
                    slideInVertically(tween(Motion.CONTENT_MILLIS, easing = EnterEasing)) {
                        (it * Motion.PHASE_WORD_RISE_SHARE).toInt()
                    }
                val exit = fadeOut(tween(Motion.EXIT_MILLIS)) +
                    slideOutVertically(tween(Motion.EXIT_MILLIS, easing = StandardEasing)) {
                        -(it * Motion.PHASE_WORD_RISE_SHARE).toInt()
                    }
                enter.togetherWith(exit).using(SizeTransform(clip = false))
            }
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

/** The line under the clock: what follows, in a flow's words for a flow. */
@Composable
private fun nextText(state: WorkoutUiState): String =
    state.flow?.let { flowNextText(state.phase, it) } ?: nextUpText(state.next)

/** "Next: rest 0:15", "Last round — finish strong"; nothing once the workout is done. */
@Composable
private fun nextUpText(next: NextUp?): String = when (next) {
    is NextUp.Work -> stringResource(Res.string.next_work, next.clock)

    is NextUp.WarmUp -> stringResource(Res.string.next_warm_up, next.clock)

    is NextUp.CoolDown -> stringResource(Res.string.next_cool_down, next.clock)

    is NextUp.Rest -> stringResource(Res.string.next_rest, next.clock)

    is NextUp.Round -> next.clock?.let { stringResource(Res.string.next_round_clock, next.round, next.of, it) }
        ?: stringResource(Res.string.next_round, next.round, next.of)

    is NextUp.SetRest -> stringResource(Res.string.next_set_rest, next.clock)

    is NextUp.Set -> next.clock?.let { stringResource(Res.string.next_set_clock, next.set, next.of, it) }
        ?: stringResource(Res.string.next_set, next.set, next.of)

    is NextUp.Block -> stringResource(Res.string.next_block, next.block, next.of, next.clock)

    is NextUp.BlockRest -> stringResource(Res.string.next_block_rest, next.block, next.clock)

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
