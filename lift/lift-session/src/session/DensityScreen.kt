package dev.justinsmith.lift.session

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import dev.justinsmith.interval.platform.KeepScreenOn
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.ConfirmDialog
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.CountdownClock
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.ProgressRing
import dev.justinsmith.interval.ui.RollingText
import dev.justinsmith.interval.ui.SecondaryButton
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.isLandscape
import dev.justinsmith.interval.ui.ringClockSize
import dev.justinsmith.interval.ui.surfaceButton
import dev.justinsmith.interval.ui.windowIsRoomy
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.DensityPhase
import dev.justinsmith.lift.domain.DensityState
import dev.justinsmith.lift.domain.EmomClock
import dev.justinsmith.lift.domain.Load
import dev.justinsmith.lift.domain.Side
import dev.justinsmith.lift.session.resources.Res
import dev.justinsmith.lift.session.resources.common_cancel
import dev.justinsmith.lift.session.resources.common_joined
import dev.justinsmith.lift.session.resources.density_best
import dev.justinsmith.lift.session.resources.density_cap
import dev.justinsmith.lift.session.resources.density_go
import dev.justinsmith.lift.session.resources.density_round_done
import dev.justinsmith.lift.session.resources.density_rounds
import dev.justinsmith.lift.session.resources.density_stop_text
import dev.justinsmith.lift.session.resources.density_stop_title
import dev.justinsmith.lift.session.resources.emom_get_ready
import dev.justinsmith.lift.session.resources.emom_stop
import dev.justinsmith.lift.session.resources.summary_done
import dev.justinsmith.lift.ui.LiftSizes
import dev.justinsmith.lift.ui.liftName
import dev.justinsmith.lift.ui.setLine
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A density block, in the EMOM's look: the phase colour on the band, the ring (the cap going by) with the time
 * left in it, the rounds done rolling up under it beside the best to beat, and one big Round done. Count-in amber,
 * work red for the whole cap, done green. A haptic marks each change of phase and each round; the beeps and the
 * calls of the clock come from the session's timers.
 */
@Composable
fun DensityScreen(
    run: DensityRun,
    state: DensityState,
    onFinishRound: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = IntervalTheme.workout
    val phaseColor = when (state.phase) {
        DensityPhase.GetReady -> colors.prep
        DensityPhase.Work -> colors.work
        DensityPhase.Done -> colors.done
    }
    val color by animateColorAsState(phaseColor, tween(Motion.PHASE_CROSSFADE_MILLIS))
    var confirmingStop by rememberSaveable { mutableStateOf(false) }
    val requestStop = { if (state.phase != DensityPhase.Done) confirmingStop = true }
    val focus = remember { FocusRequester() }
    var rootFocused by remember { mutableStateOf(false) }
    LaunchedEffect(confirmingStop) { if (!confirmingStop) focus.requestFocus() }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state.phase, state.rounds) {
        haptics.performHapticFeedback(
            if (state.phase == DensityPhase.Done) HapticFeedbackType.LongPress else HapticFeedbackType.Confirm,
        )
    }
    KeepScreenOn()
    val working = state.phase == DensityPhase.Work
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(colors.ground)
            .focusRequester(focus)
            .onFocusChanged { rootFocused = it.isFocused }
            .focusable()
            .densityKeys(
                enabled = !confirmingStop && state.phase != DensityPhase.Done,
                canFinishRound = rootFocused && working,
                onFinishRound = onFinishRound,
                onStop = requestStop,
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        val roomy = windowIsRoomy()
        if (isLandscape && !roomy) {
            val sideWidth = minOf(Sizes.setupColumnLandscape, maxWidth * LANDSCAPE_PRIMARY_SHARE)
            LandscapeDensity(run, state, color, sideWidth, onFinishRound, requestStop)
        } else {
            PortraitDensity(run, state, color, onFinishRound, requestStop, roomy)
        }
    }
    if (confirmingStop && state.phase != DensityPhase.Done) {
        ConfirmDialog(
            title = stringResource(Res.string.density_stop_title),
            text = stringResource(Res.string.density_stop_text),
            confirm = stringResource(Res.string.emom_stop),
            dismiss = stringResource(Res.string.common_cancel),
            onConfirm = {
                confirmingStop = false
                onStop()
            },
            onDismiss = { confirmingStop = false },
            destructive = true,
        )
    }
}

private fun Modifier.densityKeys(
    enabled: Boolean,
    canFinishRound: Boolean,
    onFinishRound: () -> Unit,
    onStop: () -> Unit,
): Modifier = onKeyEvent { event ->
    if (!enabled || event.type != KeyEventType.KeyDown) return@onKeyEvent false
    when (event.key) {
        Key.Spacebar, Key.Enter, Key.NumPadEnter -> if (canFinishRound) {
            onFinishRound()
            true
        } else {
            false
        }

        Key.Escape -> {
            onStop()
            true
        }

        else -> false
    }
}

/** Upright, and on a tablet either way up: the band, Stop and the cap, the ring, the rounds, Round done. */
@Composable
private fun PortraitDensity(
    run: DensityRun,
    state: DensityState,
    color: Color,
    onFinishRound: () -> Unit,
    onStop: () -> Unit,
    roomy: Boolean,
) {
    Column(
        Modifier.widthIn(max = Sizes.contentWide).fillMaxSize().safeDrawingPadding()
            .padding(horizontal = Spacing.space16),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(Sizes.phaseBand).clip(Corners.full).background(color))
        Row(
            Modifier.fillMaxWidth().padding(top = Spacing.space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StopPill(onStop)
            CapText(state, Modifier.weight(1f).padding(horizontal = Spacing.space8))
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val diameter = minOf(maxWidth, maxHeight, if (roomy) LiftSizes.ringMaxRoomy else Sizes.ringMax)
            ProgressRing(state.progress, color, Modifier.size(diameter), glideMillis = Motion.EMOM_RING_GLIDE_MILLIS)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PhaseWord(state, color)
                val type = IntervalTheme.type
                ClockText(state, color, diameter, if (roomy) type.clock else type.display)
                TaskText(run, state)
            }
        }
        Rounds(run, state, Modifier.padding(bottom = Spacing.space16))
        RoundDone(state, color, onFinishRound, Modifier.fillMaxWidth().padding(bottom = Spacing.space16))
    }
}

/** Sideways, as the EMOM lies: the band down the start edge, the clock alone in the ring, the rest beside it. */
@Composable
private fun LandscapeDensity(
    run: DensityRun,
    state: DensityState,
    color: Color,
    sideWidth: Dp,
    onFinishRound: () -> Unit,
    onStop: () -> Unit,
) {
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
                        state.progress,
                        color,
                        Modifier.fillMaxSize(),
                        glideMillis = Motion.EMOM_RING_GLIDE_MILLIS,
                    )
                    ClockText(state, color, diameter, IntervalTheme.type.clock)
                }
            }
            Column(
                Modifier.width(sideWidth).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(Spacing.space8),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StopPill(onStop)
                    CapText(state, Modifier.weight(1f).padding(horizontal = Spacing.space8))
                }
                Spacer(Modifier.weight(1f))
                PhaseWord(state, color)
                TaskText(run, state)
                Rounds(run, state)
                Spacer(Modifier.weight(1f))
                RoundDone(state, color, onFinishRound, Modifier.fillMaxWidth())
            }
        }
    }
}

/** "20 min cap". */
@Composable
private fun CapText(state: DensityState, modifier: Modifier = Modifier) {
    Text(
        stringResource(Res.string.density_cap, state.minutes),
        style = IntervalTheme.type.label,
        color = IntervalTheme.workout.onGround,
        modifier = modifier,
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

/** Round done, in the phase colour and live only while the cap runs. */
@Composable
private fun RoundDone(state: DensityState, color: Color, onFinishRound: () -> Unit, modifier: Modifier = Modifier) {
    val colors = IntervalTheme.workout
    val working = state.phase == DensityPhase.Work
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .heightIn(min = LiftSizes.roundDone)
            .surfaceButton(
                interaction,
                Corners.radius18,
                if (working) color else colors.control,
                onClick = onFinishRound,
                enabled = working,
            )
            .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(Res.string.density_round_done),
            style = IntervalTheme.type.stat,
            color = if (working) colors.onFill(color) else colors.onGroundDim,
        )
    }
}

@Composable
private fun StopPill(onStop: () -> Unit) {
    SecondaryButton(stringResource(Res.string.emom_stop), onStop, Modifier.widthIn(min = Sizes.pillWidth))
}

/** Get ready, Go, or Done, in the phase colour. */
@Composable
private fun PhaseWord(state: DensityState, color: Color) {
    Text(
        stringResource(
            when (state.phase) {
                DensityPhase.GetReady -> Res.string.emom_get_ready
                DensityPhase.Work -> Res.string.density_go
                DensityPhase.Done -> Res.string.summary_done
            },
        ),
        style = IntervalTheme.type.stat,
        color = color,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** The time left of the cap (or the count-in), popping on its last three seconds as the beeps go. */
@Composable
private fun ClockText(state: DensityState, color: Color, diameter: Dp, style: TextStyle) {
    val text = formatClock(state.secondsLeft)
    val size = ringClockSize(diameter, text.length, style.fontSize)
    CountdownClock(
        text,
        intervalKey = state.phase,
        countingDown = state.phase != DensityPhase.Done && state.secondsLeft in 1..EmomClock.COUNTDOWN,
        accent = color,
        style = style.copy(fontSize = size, lineHeight = size),
    )
}

/** The round to do: the lift, its load and reps, and a one-sided lift's side, which turns each round. */
@Composable
private fun TaskText(run: DensityRun, state: DensityState) {
    val task = setLine(run.load, run.reps, run.sideOf(state.rounds + 1))
    Text(
        stringResource(Res.string.common_joined, liftName(run.exercise, run.double), task),
        style = IntervalTheme.type.label,
        color = IntervalTheme.workout.onGroundDim,
    )
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
