package dev.justinsmith.interval.workout

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import dev.justinsmith.interval.platform.KeepScreenOn
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.workout_end_confirm
import dev.justinsmith.interval.resources.workout_end_nothing
import dev.justinsmith.interval.resources.workout_end_progress
import dev.justinsmith.interval.resources.workout_end_title
import dev.justinsmith.interval.resources.workout_pause
import dev.justinsmith.interval.resources.workout_resume
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.WorkoutEngine
import dev.justinsmith.interval.ui.ConfirmDialog
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.LaneAnchor
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.PhaseColors
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.WorkoutLanes
import dev.justinsmith.interval.ui.isLandscape
import dev.justinsmith.interval.ui.laneRing
import dev.justinsmith.interval.ui.of
import dev.justinsmith.interval.ui.rememberLaneAnchor
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.currentKoinScope

/** What the running workout's controls do, passed down as one value rather than five same-typed lambdas. */
@Immutable
internal class WorkoutActions(
    val onTogglePause: () -> Unit,
    val onSkip: () -> Unit,
    val onBack: () -> Unit,
    val onAddTime: () -> Unit,
    /** Asks before ending: pauses, then shows the confirmation. */
    val onEnd: () -> Unit,
)

/** Resolves the screen's ViewModel, scoped to its back-stack entry, and binds it to [WorkoutScreen]. */
@Composable
fun WorkoutRoute(onExit: () -> Unit, modifier: Modifier = Modifier) {
    val scope = currentKoinScope()
    val viewModel = viewModel { WorkoutViewModel(scope.get(), scope.get()) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.running && state.keepScreenOn) KeepScreenOn()
    WorkoutScreen(
        state = state,
        onTogglePause = viewModel::togglePause,
        onSkip = viewModel::skip,
        onBack = viewModel::back,
        onAddTime = viewModel::addTime,
        onRestart = viewModel::restart,
        onEndEarly = viewModel::endEarly,
        onSavePreset = viewModel::saveAsPreset,
        onExit = onExit,
        modifier = modifier,
    )
}

/**
 * The full-screen timer, with no tab bar, on a near-black ground. The phase colour is the progress
 * ring, the band along the top edge, the phase word and the primary control, so it reads from across
 * a room; tapping anywhere in the middle pauses or resumes. Wider-than-tall windows move the
 * controls into side rails at thumb reach, which leaves the middle for a much bigger clock.
 *
 * End, or the system back gesture, pauses and asks first. A workout that ends, early or not, turns
 * into its summary.
 *
 * With a keyboard (desktop, a Chromebook, a tablet's keyboard cover): Space or Enter pauses and
 * resumes, the arrow keys go back and skip, and Escape ends, the same as the End button.
 */
@Composable
fun WorkoutScreen(
    state: WorkoutUiState,
    onTogglePause: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    onAddTime: () -> Unit,
    onRestart: () -> Unit,
    onEndEarly: () -> Unit,
    onSavePreset: (Preset) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmingEnd by rememberSaveable { mutableStateOf(false) }
    val requestEnd = {
        if (state.running) onTogglePause()
        confirmingEnd = true
    }
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = !state.isDone,
        onBackCompleted = requestEnd,
    )
    val accent = phaseColor(state, primary = false)
    val primaryFill = phaseColor(state, primary = true)
    val focus = remember { FocusRequester() }
    val anchor = rememberLaneAnchor()
    // Key events only reach Compose through a focused node; take focus back after the dialog closes.
    LaunchedEffect(confirmingEnd) { if (!confirmingEnd) focus.requestFocus() }
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(PhaseColors.ground)
            .focusRequester(focus)
            .focusable()
            .workoutKeys(
                enabled = !state.isDone && !confirmingEnd,
                onTogglePause = onTogglePause,
                onBack = onBack,
                onSkip = onSkip,
                onEnd = requestEnd,
            ),
    ) {
        val landscape = isLandscape
        WorkoutLanes(anchor, accent, Modifier.matchParentSize())
        PhaseBand(accent, landscape)
        // Built afresh each time: requestEnd reads this composition's state.
        val actions = WorkoutActions(onTogglePause, onSkip, onBack, onAddTime, onEnd = requestEnd)
        if (state.isDone) {
            SummaryLayout(state, landscape, onRestart, onSavePreset, onExit)
        } else if (landscape) {
            LandscapeWorkout(state, accent, primaryFill, anchor, actions)
        } else {
            PortraitWorkout(state, accent, primaryFill, anchor, actions)
        }
    }
    if (confirmingEnd && !state.isDone) {
        ConfirmDialog(
            title = stringResource(Res.string.workout_end_title),
            text = if (state.hasProgress) {
                pluralStringResource(
                    Res.plurals.workout_end_progress,
                    state.rounds,
                    state.completedRounds,
                    state.rounds,
                )
            } else {
                stringResource(Res.string.workout_end_nothing)
            },
            confirm = stringResource(Res.string.workout_end_confirm),
            dismiss = stringResource(Res.string.workout_resume),
            onConfirm = {
                confirmingEnd = false
                if (state.hasProgress) onEndEarly() else onExit()
            },
            onDismiss = {
                confirmingEnd = false
                if (state.paused) onTogglePause()
            },
        )
    }
}

/**
 * The phase colour, crossfading as the phase changes: grey while paused, blue once done. For the
 * [primary] control's fill, paused is [PhaseColors.onGround] instead, since ink fails on the grey.
 */
@Composable
private fun phaseColor(state: WorkoutUiState, primary: Boolean): Color {
    val target = when {
        state.isDone -> PhaseColors.done
        state.paused -> if (primary) PhaseColors.onGround else PhaseColors.paused
        else -> PhaseColors.of(state.phase)
    }
    val color by animateColorAsState(
        target,
        tween(Motion.PHASE_CROSSFADE_MILLIS),
        label = if (primary) "primary" else "phase",
    )
    return color
}

/**
 * Space or Enter pauses and resumes, the arrow keys go back and skip, and Escape ends. Handled after
 * the focused control, so a button reached with Tab still answers to Enter and Space itself.
 */
private fun Modifier.workoutKeys(
    enabled: Boolean,
    onTogglePause: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onEnd: () -> Unit,
): Modifier = onKeyEvent { event ->
    val action = when (event.key) {
        Key.Spacebar, Key.Enter, Key.NumPadEnter -> onTogglePause
        Key.DirectionLeft -> onBack
        Key.DirectionRight -> onSkip
        Key.Escape -> onEnd
        else -> null
    }
    if (!enabled || event.type != KeyEventType.KeyDown || action == null) return@onKeyEvent false
    action()
    true
}

@Composable
private fun PortraitWorkout(
    state: WorkoutUiState,
    accent: Color,
    primaryFill: Color,
    anchor: LaneAnchor,
    actions: WorkoutActions,
) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(top = Sizes.phaseBand + Spacing.space8, start = Spacing.space16, end = Spacing.space16)
            .padding(vertical = Spacing.space16),
    ) {
        TopBar(state, actions)
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .tapToPause(state, actions.onTogglePause),
            contentAlignment = Alignment.Center,
        ) {
            val diameter = minOf(maxWidth, maxHeight - Sizes.tapHint, Sizes.ringMax)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RingReadout(state, accent, diameter, Modifier.laneRing(anchor))
                TapHint(state, Modifier.padding(top = Spacing.space12))
            }
        }
        RoundDots(
            state,
            accent,
            Modifier.align(Alignment.CenterHorizontally).padding(top = Spacing.space8, bottom = Spacing.space20),
        )
        BottomControls(
            state = state,
            primaryFill = primaryFill,
            actions = actions,
        )
    }
}

/** Sideways: End and Back on the left rail, Skip, +10s and Pause on the right, the ring between. */
@Composable
private fun LandscapeWorkout(
    state: WorkoutUiState,
    accent: Color,
    primaryFill: Color,
    anchor: LaneAnchor,
    actions: WorkoutActions,
) {
    Row(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(start = Sizes.phaseBand + Spacing.space8, end = Spacing.space20)
            .padding(vertical = Spacing.space16),
        horizontalArrangement = Arrangement.spacedBy(Spacing.space16),
    ) {
        Column(
            Modifier.width(Sizes.rail).fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            EndPill(actions.onEnd, Modifier.fillMaxWidth())
            BackButton(actions.onBack, Modifier.fillMaxWidth().height(Sizes.control))
        }
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .tapToPause(state, actions.onTogglePause),
            contentAlignment = Alignment.Center,
        ) {
            val diameter = minOf(maxHeight, maxWidth / 2, Sizes.ringMax)
            LandscapeReadout(state, accent, diameter, Modifier.fillMaxWidth(), ringModifier = Modifier.laneRing(anchor))
        }
        Column(
            Modifier.width(Sizes.rail).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(Spacing.space8),
        ) {
            SkipPill(actions.onSkip, Modifier.fillMaxWidth())
            Spacer(Modifier.weight(1f))
            AddTimeButton(actions.onAddTime, Modifier.fillMaxWidth().height(Sizes.control))
            PrimaryButton(state, primaryFill, actions.onTogglePause, Modifier.fillMaxWidth().height(Sizes.control))
        }
    }
}

/** The band of phase colour along the top edge, or the start edge sideways, just inside the system bars. */
@Composable
private fun BoxScope.PhaseBand(accent: Color, landscape: Boolean) {
    val inset = Modifier.safeDrawingPadding()
    val band = if (landscape) {
        Modifier.align(Alignment.CenterStart).then(inset).fillMaxHeight().width(Sizes.phaseBand)
    } else {
        Modifier.align(Alignment.TopCenter).then(inset).fillMaxWidth().height(Sizes.phaseBand)
    }
    Box(band.clip(Corners.full).background(accent))
}

/** The readout area pauses or resumes on tap, with no ripple: the background change is the feedback. */
@Composable
private fun Modifier.tapToPause(state: WorkoutUiState, onTogglePause: () -> Unit): Modifier = clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    enabled = !state.isDone,
    onClickLabel = stringResource(if (state.running) Res.string.workout_pause else Res.string.workout_resume),
    onClick = onTogglePause,
)

@Composable
private fun TopBar(state: WorkoutUiState, actions: WorkoutActions) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EndPill(actions.onEnd)
        RoundStatus(state, Modifier.weight(1f).padding(horizontal = Spacing.space8))
        SkipPill(actions.onSkip)
    }
}

@Composable
private fun WorkoutPreview(state: WorkoutUiState) {
    IntervalTheme {
        WorkoutScreen(
            state = state,
            onTogglePause = {},
            onSkip = {},
            onBack = {},
            onAddTime = {},
            onRestart = {},
            onEndEarly = {},
            onSavePreset = {},
            onExit = {},
        )
    }
}

private fun previewWorkState(): WorkoutUiState =
    WorkoutEngine.advance(WorkoutEngine.start(IntervalConfig()).state).state.toUiState(running = true)

@PreviewFormFactors
@Composable
private fun WorkoutScreenPreview() {
    WorkoutPreview(previewWorkState())
}

@PreviewFormFactors
@Composable
private fun WorkoutScreenSummaryPreview() {
    val done = generateSequence(WorkoutEngine.start(IntervalConfig()).state) { WorkoutEngine.advance(it).state }
        .first { it.isDone }
    WorkoutPreview(done.toUiState(running = false, elapsedSeconds = done.config.totalSeconds))
}
