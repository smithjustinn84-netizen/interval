package dev.justinsmith.interval.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.app_name
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.PhaseColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.math.hypot

/**
 * [content] with the intro over it, played once per launch. Saveable, so turning the phone doesn't
 * play it again.
 */
@Composable
internal fun WithIntro(ready: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var played by rememberSaveable { mutableStateOf(false) }
    Box(modifier.fillMaxSize()) {
        content()
        if (!played) Splash(ready = ready, onFinish = { played = true })
    }
}

/**
 * The intro over the app's first frames. It opens on the splash screen exactly (the mark on ground,
 * at the size Android's splash and iOS's launch screen draw it), so the hand-off is seamless. Then
 * the mark comes alive: the hand sweeps a turn, each phase swelling as it passes, while the name
 * rises in beneath. Once [ready] (saved settings loaded, so the app below is in its saved theme) the
 * mark dips, then bursts outward while the ground opens from the centre onto the app behind a ripple
 * of the three phase colours. [onFinish] then removes it.
 *
 * Always Gym dark. Swallows touches until it has gone, so nothing hidden under it can be pressed.
 */
@Composable
internal fun Splash(ready: Boolean, onFinish: () -> Unit, modifier: Modifier = Modifier) {
    val hand = remember { Animatable(0f) }
    val dip = remember { Animatable(1f) }
    val burst = remember { Animatable(0f) }
    val title = remember { Animatable(0f) }
    val isReady by rememberUpdatedState(ready)
    val finish by rememberUpdatedState(onFinish)
    LaunchedEffect(Unit) {
        // Animations are timed from frames, so a slow frame skips them ahead. A cold start's first
        // frames are slow (iOS debug builds especially), so the mark holds still until they settle.
        settle()
        launch {
            title.animateTo(1f, tween(TITLE_MILLIS, TITLE_DELAY_MILLIS, LinearOutSlowInEasing))
        }
        hand.animateTo(1f, tween(SPIN_MILLIS, easing = SpinEasing))
        snapshotFlow { isReady }.first { it }
        // Likewise the app's first composition, below, which starts once it is ready.
        settle()
        dip.animateTo(DIP_SCALE, tween(DIP_MILLIS, easing = FastOutSlowInEasing))
        burst.animateTo(1f, tween(BURST_MILLIS, easing = FastOutSlowInEasing))
        finish()
    }
    // Its own theme: the app's is inside AppContent, under this, and follows the saved setting.
    IntervalTheme(darkTheme = true) {
        Box(
            modifier
                .fillMaxSize()
                .clearAndSetSemantics {}
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent().changes.forEach { it.consume() }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    // Its own layer, so clearing the hole shows the app rather than punching through it.
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
            ) {
                val opened = burst.value
                drawRect(PhaseColors.ground)
                if (opened > 0f) {
                    val ripple = RippleGap.toPx()
                    val reach = hypot(size.width, size.height) / 2 + ripple * Ripples.size
                    val hole = reach * opened
                    drawCircle(Color.Black, hole, blendMode = BlendMode.Clear)
                    val width = RippleWidth.toPx() * (1 - opened)
                    Ripples.forEachIndexed { i, color ->
                        val radius = hole - ripple * i
                        // Not until it is wider than its stroke, which would otherwise fill it in.
                        if (radius > width) drawCircle(color, radius, alpha = 1 - opened, style = Stroke(width))
                    }
                }
                drawIntervalMark(
                    center,
                    unit = MarkSize.toPx() / Mark.CANVAS * dip.value * (1 + BURST_GROWTH * opened),
                    handTurns = hand.value,
                    alpha = (1 - opened * MARK_FADE_SPEED).coerceAtLeast(0f),
                )
            }
            Text(
                stringResource(Res.string.app_name).uppercase(),
                modifier = Modifier
                    .offset(y = TitleOffset)
                    .graphicsLayer {
                        alpha = title.value * (1 - burst.value * MARK_FADE_SPEED).coerceAtLeast(0f)
                        translationY = (1 - title.value) * TitleRise.toPx()
                    },
                style = IntervalTheme.type.title.copy(letterSpacing = TitleTracking),
                color = PhaseColors.onGround,
            )
        }
    }
}

private suspend fun settle() = repeat(SETTLE_FRAMES) { withFrameNanos {} }

/** The 108-unit icon canvas on screen: Android's splash draws an icon without a background this size. */
private val MarkSize = 288.dp

/** The name sits this far below the centre, under the ring, and rises this far as it fades in. */
private val TitleOffset = 108.dp
private val TitleRise = 16.dp
private val TitleTracking = 8.sp

/** The rings of phase colour riding the opening edge, outermost first, and their spacing and stroke. */
private val Ripples = listOf(PhaseColors.prep, PhaseColors.work, PhaseColors.rest)
private val RippleGap = 18.dp
private val RippleWidth = 14.dp

/** Frames to wait out before each animated stage; see [settle]. */
private const val SETTLE_FRAMES = 3

private const val SPIN_MILLIS = 720

/** Winds up, sweeps, and runs a little past 12 before settling back on it, like a stopwatch hand. */
private val SpinEasing = CubicBezierEasing(0.55f, 0f, 0.25f, 1.12f)
private const val TITLE_DELAY_MILLIS = 150
private const val TITLE_MILLIS = 450

/** The anticipation before the burst: the mark draws in to this, then bursts. */
private const val DIP_MILLIS = 140
private const val DIP_SCALE = 0.86f
private const val BURST_MILLIS = 540

/** At the end of the burst the mark has grown this much more… */
private const val BURST_GROWTH = 1.8f

/** …though it and the name fade this many times faster than the burst, gone 40% of the way through. */
private const val MARK_FADE_SPEED = 2.5f
