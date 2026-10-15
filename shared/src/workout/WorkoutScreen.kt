package dev.justinsmith.interval.workout

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.justinsmith.interval.platform.KeepScreenOn
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.WorkoutEngine
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.PhaseColors
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import kotlinx.coroutines.delay
import org.koin.compose.currentKoinScope

/** Resolves the screen's ViewModel, scoped to its back-stack entry, and binds it to [WorkoutScreen]. */
@Composable
fun WorkoutRoute(onExit: () -> Unit) {
    val scope = currentKoinScope()
    val viewModel = viewModel { WorkoutViewModel(scope.get(), scope.get()) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.running) KeepScreenOn()
    WorkoutScreen(
        state = state,
        onTogglePause = viewModel::togglePause,
        onSkip = viewModel::skip,
        onBack = viewModel::back,
        onAddTime = viewModel::addTime,
        onRestart = viewModel::restart,
        onEnd = onExit,
    )
}

/**
 * The full-screen timer. The whole background is the phase color so it reads from across a room;
 * tapping anywhere in the middle pauses or resumes. Wider-than-tall windows move the controls into
 * side rails at thumb reach, which leaves the middle for a much bigger clock.
 */
@Composable
fun WorkoutScreen(
    state: WorkoutUiState,
    onTogglePause: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    onAddTime: () -> Unit,
    onRestart: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val target = if (state.paused) PhaseColors.paused else PhaseColors.of(state.phase)
    val background by animateColorAsState(target, tween(Motion.PHASE_CROSSFADE_MILLIS), label = "phase")
    BoxWithConstraints(modifier.fillMaxSize().background(background)) {
        if (maxWidth > maxHeight) {
            // A phone held sideways keeps the canvas's cap; tablets and desktop windows have the room
            // for the clock style's full size.
            val clockMax = if (maxHeight < Sizes.landscapeMaxHeight) LANDSCAPE_CLOCK_MAX else LANDSCAPE_CLOCK_MAX_LARGE
            LandscapeWorkout(state, clockMax, onTogglePause, onSkip, onBack, onAddTime, onRestart, onEnd)
        } else {
            PortraitWorkout(
                state = state,
                clockSize = (maxWidth.value * PORTRAIT_CLOCK_WIDTH_SHARE)
                    .coerceIn(PORTRAIT_CLOCK_MIN, PORTRAIT_CLOCK_MAX).sp,
                onTogglePause = onTogglePause,
                onSkip = onSkip,
                onBack = onBack,
                onAddTime = onAddTime,
                onRestart = onRestart,
                onEnd = onEnd,
            )
        }
    }
}

@Composable
private fun PortraitWorkout(
    state: WorkoutUiState,
    clockSize: TextUnit,
    onTogglePause: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    onAddTime: () -> Unit,
    onRestart: () -> Unit,
    onEnd: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = Spacing.space20, vertical = Spacing.space16),
    ) {
        TopBar(state = state, onEnd = onEnd, onSkip = onSkip)
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .tapToPause(state, onTogglePause),
            contentAlignment = Alignment.Center,
        ) {
            if (state.isDone) Summary(state) else PortraitReadout(state, clockSize)
        }
        ProgressBar(state.progress, Modifier.padding(top = Spacing.space8, bottom = Spacing.space16))
        BottomControls(
            state = state,
            onTogglePause = onTogglePause,
            onBack = onBack,
            onAddTime = onAddTime,
            onRestart = onRestart,
        )
    }
}

/** Sideways: End and Back on the left rail, Skip, +10s and Pause on the right, the readout between. */
@Composable
private fun LandscapeWorkout(
    state: WorkoutUiState,
    clockMax: TextUnit,
    onTogglePause: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    onAddTime: () -> Unit,
    onRestart: () -> Unit,
    onEnd: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = Spacing.space20, vertical = Spacing.space16),
        horizontalArrangement = Arrangement.spacedBy(Spacing.space16),
    ) {
        Column(
            Modifier.width(Sizes.rail).fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            EndPill(state, onEnd, Modifier.fillMaxWidth())
            if (!state.isDone) {
                BigButton(
                    "Back",
                    onBack,
                    Modifier.fillMaxWidth().height(Sizes.control),
                    description = "Back to previous interval",
                )
            }
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .tapToPause(state, onTogglePause),
        ) {
            RoundStatus(state, Modifier.fillMaxWidth().padding(top = Spacing.space4))
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (state.isDone) Summary(state) else LandscapeReadout(state, clockMax)
            }
            ProgressBar(state.progress)
        }
        Column(
            Modifier.width(Sizes.rail).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(Spacing.space10),
        ) {
            if (!state.isDone) {
                PillButton(
                    text = "Skip",
                    onClick = onSkip,
                    description = "Skip to next interval",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.weight(1f))
            if (state.isDone) {
                BigButton("Restart", onRestart, Modifier.fillMaxWidth().height(Sizes.control))
            } else {
                BigButton(
                    "+10s",
                    onAddTime,
                    Modifier.fillMaxWidth().height(Sizes.control),
                    description = "Add ten seconds",
                )
                PrimaryButton(state, onTogglePause, Modifier.fillMaxWidth().height(Sizes.control))
            }
        }
    }
}

/** The readout area pauses or resumes on tap, with no ripple: the background change is the feedback. */
@Composable
private fun Modifier.tapToPause(state: WorkoutUiState, onTogglePause: () -> Unit): Modifier = clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    enabled = !state.isDone,
    onClickLabel = if (state.running) "Pause" else "Resume",
    onClick = onTogglePause,
)

@Composable
private fun TopBar(state: WorkoutUiState, onEnd: () -> Unit, onSkip: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EndPill(state, onEnd)
        RoundStatus(state, Modifier.weight(1f).padding(horizontal = Spacing.space8))
        if (state.isDone) {
            Spacer(Modifier.width(Sizes.pillWidth))
        } else {
            PillButton(text = "Skip", onClick = onSkip, description = "Skip to next interval")
        }
    }
}

@Composable
private fun EndPill(state: WorkoutUiState, onEnd: () -> Unit, modifier: Modifier = Modifier) {
    // Ending takes two taps, so a sweaty palm can't throw away a session.
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(Motion.END_CONFIRM_MILLIS)
            armed = false
        }
    }
    PillButton(
        text = if (armed) "End?" else "End",
        onClick = {
            if (armed || state.isDone) {
                onEnd()
            } else {
                armed = true
            }
        },
        background = if (armed) PhaseColors.onPhase else PhaseColors.glass,
        content = if (armed) PhaseColors.ink else PhaseColors.onPhase,
        description = if (armed) "Tap again to end the workout" else "End workout",
        modifier = modifier,
    )
}

/** The round dots over the status line ("Round 3 of 8 · 4:03 left"). */
@Composable
private fun RoundStatus(state: WorkoutUiState, modifier: Modifier = Modifier) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.space6),
    ) {
        // Dots stop being readable past a dozen rounds; the text below says the same thing.
        if (state.rounds <= MAX_DOTS) RoundDots(state)
        if (state.meta.isNotEmpty()) {
            Text(
                state.meta,
                style = IntervalTheme.type.meta,
                color = PhaseColors.onPhase85,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun RoundDots(state: WorkoutUiState) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.space6),
        modifier = Modifier.clearAndSetSemantics {},
    ) {
        repeat(state.rounds) { index ->
            val round = index + 1
            val halo = PhaseColors.dotHalo
            val dot = Modifier.size(Sizes.dot)
            when {
                round == state.activeRound -> Box(
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
private fun PortraitReadout(state: WorkoutUiState, clockSize: TextUnit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PhaseLabel(state, IntervalTheme.type.phaseLabel)
        Clock(state, clockSize)
        Text(
            state.next,
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
private fun LandscapeReadout(state: WorkoutUiState, clockMax: TextUnit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PhaseLabel(state, IntervalTheme.type.phaseLabel)
        // Weighted children are measured last, so this box gets exactly the height left over.
        BoxWithConstraints(Modifier.weight(1f, fill = false), contentAlignment = Alignment.Center) {
            val density = LocalDensity.current
            val byHeight = with(density) { (maxHeight / LANDSCAPE_CLOCK_LINE_HEIGHT).toSp() }
            val byWidth = with(density) { (maxWidth / (state.clock.length * CLOCK_EMS_PER_CHAR)).toSp() }
            val size = minOf(clockMax.value, byHeight.value, byWidth.value).sp
            Clock(state, size, lineHeight = size * LANDSCAPE_CLOCK_LINE_HEIGHT)
        }
        Text(
            state.next,
            style = IntervalTheme.type.nextUp,
            color = PhaseColors.onPhase85,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.space8),
        )
        TapHint(state, Modifier.padding(top = Spacing.space6))
    }
}

@Composable
private fun PhaseLabel(state: WorkoutUiState, style: TextStyle) {
    Text(state.label, style = style, color = PhaseColors.onPhase90)
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
        if (state.paused) "Tap anywhere to resume" else "Tap anywhere to pause",
        style = IntervalTheme.type.meta,
        color = if (state.paused) PhaseColors.onPhase else PhaseColors.onPhase65,
        fontWeight = if (state.paused) FontWeight.SemiBold else FontWeight.Normal,
        modifier = modifier,
    )
}

@Composable
private fun Summary(state: WorkoutUiState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Done", style = IntervalTheme.type.done, color = PhaseColors.onPhase)
        Text(state.summary, style = IntervalTheme.type.summary, color = PhaseColors.onPhase90)
    }
}

@Composable
private fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(Sizes.progress)
            .clip(Corners.full)
            .background(PhaseColors.progressTrack),
    ) {
        if (progress > 0f) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .clip(Corners.full)
                    .background(PhaseColors.onPhase),
            )
        }
    }
}

@Composable
private fun BottomControls(
    state: WorkoutUiState,
    onTogglePause: () -> Unit,
    onBack: () -> Unit,
    onAddTime: () -> Unit,
    onRestart: () -> Unit,
) {
    val height = Sizes.control
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.space10)) {
        if (state.isDone) {
            BigButton("Restart", onRestart, Modifier.weight(1f).height(height))
        } else {
            BigButton(
                "Back",
                onBack,
                Modifier.width(Sizes.controlSide).height(height),
                description = "Back to previous interval",
            )
            PrimaryButton(state, onTogglePause, Modifier.weight(1f).height(height))
            BigButton(
                "+10s",
                onAddTime,
                Modifier.width(Sizes.controlSide).height(height),
                description = "Add ten seconds",
            )
        }
    }
}

/** The white Pause / Resume button. */
@Composable
private fun PrimaryButton(state: WorkoutUiState, onTogglePause: () -> Unit, modifier: Modifier = Modifier) {
    BigButton(
        if (state.running) "Pause" else "Resume",
        onTogglePause,
        modifier,
        background = PhaseColors.onPhase,
        content = PhaseColors.ink,
        style = IntervalTheme.type.primaryControl,
    )
}

@Composable
private fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = PhaseColors.glass,
    content: Color = PhaseColors.onPhase,
    style: TextStyle = IntervalTheme.type.control,
    description: String? = null,
) {
    Box(
        modifier
            .clip(Corners.radius16)
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = style, color = content)
    }
}

@Composable
private fun PillButton(
    text: String,
    onClick: () -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    background: Color = PhaseColors.glass,
    content: Color = PhaseColors.onPhase,
) {
    Box(
        modifier
            .sizeIn(minWidth = Sizes.pillWidth, minHeight = Sizes.pillHeight)
            .clip(Corners.radius12)
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = Spacing.space18),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = IntervalTheme.type.cardTitle,
            color = content,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

private const val MAX_DOTS = 12

/** The portrait clock scales with the screen width, between a floor and the clock style's size. */
private const val PORTRAIT_CLOCK_WIDTH_SHARE = 0.35f
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
            onEnd = {},
        )
    }
}

private fun previewWorkState(): WorkoutUiState =
    WorkoutEngine.advance(WorkoutEngine.start(IntervalConfig()).state).state.toUiState(running = true)

@Preview
@Composable
private fun WorkoutScreenPreview() {
    WorkoutPreview(previewWorkState())
}

@Preview(widthDp = 844, heightDp = 390)
@Composable
private fun WorkoutScreenLandscapePreview() {
    WorkoutPreview(previewWorkState())
}

@Preview(widthDp = 844, heightDp = 390)
@Composable
private fun WorkoutScreenDoneLandscapePreview() {
    val done = generateSequence(WorkoutEngine.start(IntervalConfig()).state) { WorkoutEngine.advance(it).state }
        .first { it.isDone }
    WorkoutPreview(done.toUiState(running = false))
}
