package dev.justinsmith.interval.workout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
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
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.MAX_ROUND_MARKS
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.PhaseColors
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import org.jetbrains.compose.resources.stringResource

/** The round dots over the status line ("Round 3 of 8 · 4:03 left"). */
@Composable
internal fun RoundStatus(state: WorkoutUiState, modifier: Modifier = Modifier) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.space6),
    ) {
        // Dots stop being readable past a dozen rounds; the text below says the same thing.
        if (state.rounds <= MAX_ROUND_MARKS) RoundDots(state)
        if (!state.isDone) {
            Text(
                stringResource(Res.string.workout_status, state.round, state.rounds, state.sessionLeft),
                style = IntervalTheme.type.cardTitle,
                color = PhaseColors.onPhase85,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * A dot per round: finished ones filled, the rest as halos, and the round being worked ringed. On the
 * summary no round is being worked any more, so it shows only what was finished.
 */
@Composable
internal fun RoundDots(state: WorkoutUiState, modifier: Modifier = Modifier) {
    val active = state.activeRound.takeUnless { state.isDone }
    Row(
        modifier.clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        repeat(state.rounds) { index ->
            val round = index + 1
            val halo = PhaseColors.dotHalo
            val dot = Modifier.size(Sizes.dot)
            when {
                round == active -> Box(
                    dot
                        .drawBehind { drawCircle(halo, radius = size.minDimension / 2 + Sizes.dotHalo.toPx()) }
                        .background(PhaseColors.onPhase, CircleShape),
                )

                round <= state.completedRounds -> Box(dot.background(PhaseColors.onPhase, CircleShape))

                else -> Box(dot.background(halo, CircleShape))
            }
        }
    }
}

@Composable
internal fun PortraitReadout(state: WorkoutUiState, clockSize: TextUnit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PhaseLabel(state)
        Clock(state, clockSize)
        Text(
            nextUpText(state.next),
            style = IntervalTheme.type.nextUp,
            color = PhaseColors.onPhase85,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.space12),
        )
        TapHint(state, Modifier.padding(top = Spacing.space26))
    }
}

/**
 * The landscape readout: label, clock, next up and hint, stacked and centred. The clock takes the
 * height the other lines leave, up to [clockMax], and never more than fits across.
 */
@Composable
internal fun LandscapeReadout(state: WorkoutUiState, clockMax: TextUnit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PhaseLabel(state)
        // Weighted children are measured last, so this box gets exactly the height left over.
        BoxWithConstraints(Modifier.weight(1f, fill = false), contentAlignment = Alignment.Center) {
            val density = LocalDensity.current
            val byHeight = with(density) { (maxHeight / LANDSCAPE_CLOCK_LINE_HEIGHT).toSp() }
            val byWidth = with(density) { (maxWidth / (state.clock.length * CLOCK_EMS_PER_CHAR)).toSp() }
            val size = minOf(clockMax.value, byHeight.value, byWidth.value).sp
            Clock(state, size, lineHeight = size * LANDSCAPE_CLOCK_LINE_HEIGHT)
        }
        Text(
            nextUpText(state.next),
            style = IntervalTheme.type.nextUp,
            color = PhaseColors.onPhase85,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.space8),
        )
        TapHint(state, Modifier.padding(top = Spacing.space6))
    }
}

@Composable
private fun PhaseLabel(state: WorkoutUiState) {
    val label = when {
        state.paused -> Res.string.workout_paused

        else -> when (state.phase) {
            Phase.Prep -> Res.string.phase_get_ready
            Phase.Work -> Res.string.phase_work
            Phase.Rest -> Res.string.phase_rest
            Phase.Done -> Res.string.phase_done
        }
    }
    Text(stringResource(label), style = IntervalTheme.type.phaseLabel, color = PhaseColors.onPhase90)
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
private fun Clock(state: WorkoutUiState, size: TextUnit, lineHeight: TextUnit = size) {
    // A short pop each time a new interval starts, so the change registers even in peripheral vision.
    val scale = remember { Animatable(1f) }
    LaunchedEffect(state.phase, state.completedRounds) {
        scale.snapTo(Motion.CLOCK_POP_SCALE)
        scale.animateTo(1f, tween(Motion.CLOCK_POP_MILLIS))
    }
    Text(
        state.clock,
        style = IntervalTheme.type.clock.copy(fontSize = size, lineHeight = lineHeight),
        color = PhaseColors.onPhase,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        },
    )
}

@Composable
private fun TapHint(state: WorkoutUiState, modifier: Modifier = Modifier) {
    Text(
        stringResource(if (state.paused) Res.string.workout_tap_resume else Res.string.workout_tap_pause),
        style = IntervalTheme.type.meta,
        color = if (state.paused) PhaseColors.onPhase else PhaseColors.onPhase65,
        fontWeight = if (state.paused) FontWeight.SemiBold else FontWeight.Normal,
        modifier = modifier,
    )
}

/**
 * The portrait clock fills the width its digits allow, between a floor and the clock style's size:
 * "0:14" on a phone comes out around 190sp, as the design canvas draws it, and "10:00" smaller.
 */
internal fun portraitClockSize(width: Dp, chars: Int): TextUnit =
    (width.value / (chars * PORTRAIT_CLOCK_EMS_PER_CHAR)).coerceIn(PORTRAIT_CLOCK_MIN, PORTRAIT_CLOCK_MAX).sp

/**
 * The landscape clock's cap for a window [height] tall. A phone held sideways keeps the canvas's cap;
 * tablets and desktop windows have the room for the clock style's full size.
 */
internal fun landscapeClockMax(height: Dp): TextUnit =
    if (height < Sizes.landscapeMaxHeight) LANDSCAPE_CLOCK_MAX else LANDSCAPE_CLOCK_MAX_LARGE

/** Width per clock character in portrait, where the tight tracking lets the digits sit closer. */
private const val PORTRAIT_CLOCK_EMS_PER_CHAR = 0.46f
private const val PORTRAIT_CLOCK_MIN = 96f
private const val PORTRAIT_CLOCK_MAX = 240f

/** The landscape clock's size cap on a phone held sideways, as drawn on the design canvas. */
private val LANDSCAPE_CLOCK_MAX = 200.sp

/** The cap on wider-than-tall tablets and desktop windows: the clock style's portrait maximum. */
private val LANDSCAPE_CLOCK_MAX_LARGE = 240.sp

/** Landscape sets the clock tighter than its size (170 on 200), so it fits between label and next up. */
private const val LANDSCAPE_CLOCK_LINE_HEIGHT = 0.85f

/** A generous width per clock character in Barlow Condensed ExtraBold, so "10:00" never wraps. */
private const val CLOCK_EMS_PER_CHAR = 0.52f
