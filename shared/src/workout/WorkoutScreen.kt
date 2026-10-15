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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.justinsmith.interval.platform.KeepScreenOn
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.WorkoutEngine
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.PhaseColors
import kotlinx.coroutines.delay
import org.koin.compose.currentKoinScope

private val ink = Color(0xFF141A22)
private val glass = Color.White.copy(alpha = 0.18f)

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
 * tapping anywhere in the middle pauses or resumes. Phones held sideways get a side-by-side layout.
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
    val background by animateColorAsState(target, tween(durationMillis = 350), label = "phase")
    BoxWithConstraints(modifier.fillMaxSize().background(background)) {
        val landscape = maxWidth > maxHeight && maxHeight < 480.dp
        val clockSize = if (landscape) {
            (maxHeight.value * 0.36f).coerceIn(72f, 180f).sp
        } else {
            (maxWidth.value * 0.35f).coerceIn(96f, 240f).sp
        }
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = if (landscape) 16.dp else 20.dp, vertical = if (landscape) 10.dp else 16.dp),
        ) {
            TopBar(state = state, onEnd = onEnd, onSkip = onSkip)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !state.isDone,
                        onClickLabel = if (state.running) "Pause" else "Resume",
                        onClick = onTogglePause,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    state.isDone -> Summary(state)
                    landscape -> LandscapeReadout(state, clockSize)
                    else -> PortraitReadout(state, clockSize)
                }
            }
            ProgressBar(state.progress, Modifier.padding(top = 8.dp, bottom = if (landscape) 10.dp else 16.dp))
            BottomControls(
                state = state,
                height = if (landscape) 54.dp else 68.dp,
                onTogglePause = onTogglePause,
                onBack = onBack,
                onAddTime = onAddTime,
                onRestart = onRestart,
            )
        }
    }
}

@Composable
private fun TopBar(state: WorkoutUiState, onEnd: () -> Unit, onSkip: () -> Unit) {
    // Ending takes two taps, so a sweaty palm can't throw away a session.
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(2_500)
            armed = false
        }
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PillButton(
            text = if (armed) "End?" else "End",
            onClick = {
                if (armed || state.isDone) {
                    onEnd()
                } else {
                    armed = true
                }
            },
            background = if (armed) Color.White else glass,
            content = if (armed) ink else Color.White,
            description = if (armed) "Tap again to end the workout" else "End workout",
        )
        Column(
            Modifier.weight(1f).padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Dots stop being readable past a dozen rounds; the text below says the same thing.
            if (state.rounds <= MAX_DOTS) RoundDots(state)
            if (state.meta.isNotEmpty()) {
                Text(
                    state.meta,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (state.isDone) {
            Spacer(Modifier.width(72.dp))
        } else {
            PillButton(text = "Skip", onClick = onSkip, description = "Skip to next interval")
        }
    }
}

@Composable
private fun RoundDots(state: WorkoutUiState) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clearAndSetSemantics {},
    ) {
        repeat(state.rounds) { index ->
            val round = index + 1
            val halo = Color.White.copy(alpha = 0.35f)
            val dot = Modifier.size(10.dp)
            when {
                round == state.activeRound -> Box(
                    dot
                        .drawBehind { drawCircle(halo, radius = size.minDimension / 2 + 3.dp.toPx()) }
                        .background(Color.White, CircleShape),
                )

                round <= state.completedRounds -> Box(dot.background(Color.White, CircleShape))

                else -> Box(dot.background(halo, CircleShape))
            }
        }
    }
}

@Composable
private fun PortraitReadout(state: WorkoutUiState, clockSize: TextUnit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PhaseLabel(state, 34.sp)
        Clock(state, clockSize)
        Text(
            state.next,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
        TapHint(state, Modifier.padding(top = 26.dp))
    }
}

@Composable
private fun LandscapeReadout(state: WorkoutUiState, clockSize: TextUnit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Clock(state, clockSize)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PhaseLabel(state, 40.sp)
            Text(state.next, color = Color.White.copy(alpha = 0.85f), fontSize = 20.sp)
            TapHint(state)
        }
    }
}

@Composable
private fun PhaseLabel(state: WorkoutUiState, size: TextUnit) {
    Text(
        state.label,
        color = Color.White.copy(alpha = 0.9f),
        fontFamily = IntervalTheme.condensed,
        fontWeight = FontWeight.SemiBold,
        fontSize = size,
    )
}

@Composable
private fun Clock(state: WorkoutUiState, size: TextUnit) {
    // A short pop each time a new interval starts, so the change registers even in peripheral vision.
    val scale = remember { Animatable(1f) }
    LaunchedEffect(state.phase, state.completedRounds) {
        scale.snapTo(1.06f)
        scale.animateTo(1f, tween(durationMillis = 300))
    }
    Text(
        state.clock,
        color = Color.White,
        fontFamily = IntervalTheme.condensed,
        fontWeight = FontWeight.ExtraBold,
        fontSize = size,
        lineHeight = size,
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
        color = Color.White.copy(alpha = if (state.paused) 1f else 0.65f),
        fontWeight = if (state.paused) FontWeight.SemiBold else FontWeight.Normal,
        fontSize = 14.sp,
        modifier = modifier,
    )
}

@Composable
private fun Summary(state: WorkoutUiState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Done",
            color = Color.White,
            fontFamily = IntervalTheme.condensed,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 96.sp,
        )
        Text(state.summary, color = Color.White.copy(alpha = 0.9f), fontSize = 18.sp)
    }
}

@Composable
private fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.25f)),
    ) {
        if (progress > 0f) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun BottomControls(
    state: WorkoutUiState,
    height: Dp,
    onTogglePause: () -> Unit,
    onBack: () -> Unit,
    onAddTime: () -> Unit,
    onRestart: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.isDone) {
            BigButton("Restart", onRestart, Modifier.weight(1f).height(height))
        } else {
            BigButton(
                "Back",
                onBack,
                Modifier.width(84.dp).height(height),
                description = "Back to previous interval",
            )
            BigButton(
                if (state.running) "Pause" else "Resume",
                onTogglePause,
                Modifier.weight(1f).height(height),
                background = Color.White,
                content = ink,
                fontSize = 22.sp,
            )
            BigButton(
                "+10s",
                onAddTime,
                Modifier.width(84.dp).height(height),
                description = "Add ten seconds",
            )
        }
    }
}

@Composable
private fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = glass,
    content: Color = Color.White,
    fontSize: TextUnit = 19.sp,
    description: String? = null,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = content,
            fontFamily = IntervalTheme.condensed,
            fontWeight = FontWeight.ExtraBold,
            fontSize = fontSize,
        )
    }
}

@Composable
private fun PillButton(
    text: String,
    onClick: () -> Unit,
    description: String,
    background: Color = glass,
    content: Color = Color.White,
) {
    Box(
        Modifier
            .sizeIn(minWidth = 72.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = content,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

private const val MAX_DOTS = 12

@Preview
@Composable
private fun WorkoutScreenPreview() {
    val state = WorkoutEngine.start(IntervalConfig()).state
    IntervalTheme {
        WorkoutScreen(
            state = WorkoutEngine.advance(state).state.toUiState(running = true),
            onTogglePause = {},
            onSkip = {},
            onBack = {},
            onAddTime = {},
            onRestart = {},
            onEnd = {},
        )
    }
}
