package dev.justinsmith.interval.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import dev.justinsmith.interval.platform.prefersReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot

/**
 * A moment marked: rings of [colors], the first outermost, ride an edge that opens from the centre to
 * past the corners over [durationMillis] ([Motion.BURST_MILLIS] unless the moment has a time of its own, as
 * Start's launch keeps the workout's rise), thinning and fading as they go, as the intro's ripple does onto
 * the app. Once, [delayMillis] after it is composed, behind whatever it marks; nothing at all under reduced
 * motion. The rings start [stroke] wide: a screen's at [Sizes.burstStroke], a row's at [Sizes.rowBurstStroke].
 *
 * With [shock] the moment is struck first: a [Shockwave] of the first colour sets out with the rings and is spent
 * before them, a quarter of their stroke wide, so the hard front leads and the rings follow it out. A record's
 * has it; a change of phase, which comes every few seconds, does not.
 */
@Composable
fun Burst(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    delayMillis: Int = 0,
    stroke: Dp = Sizes.burstStroke,
    durationMillis: Int = Motion.BURST_MILLIS,
    shock: Boolean = false,
) {
    if (prefersReducedMotion()) return
    val opened = remember { Animatable(0f) }
    val struck = remember { Animatable(0f) }
    val shockwave = remember(shock) { if (shock) shaderProgram(SHOCKWAVE_SKSL) else null }
    val hot = onDarkGround()
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        if (shock) launch { struck.animateTo(1f, tween(Motion.SHOCKWAVE_MILLIS, easing = LinearEasing)) }
        opened.animateTo(1f, tween(durationMillis, easing = FastOutSlowInEasing))
    }
    Canvas(modifier.clearAndSetSemantics {}) {
        if (shock && colors.isNotEmpty()) {
            val far = hypot(size.width, size.height) / 2
            val front = stroke.toPx() / SHOCK_THINNER
            drawShockwave(shockwave, struck.value, colors.first(), center, far, front, hot)
        }
        val t = opened.value
        if (t <= 0f || t >= 1f) return@Canvas
        val gap = Sizes.burstGap.toPx()
        val reach = hypot(size.width, size.height) / 2 + gap * colors.size
        val edge = reach * t
        val width = stroke.toPx() * (1 - t)
        val ringStroke = Stroke(width)
        for (i in colors.indices) {
            val radius = edge - gap * i
            // Not until it is wider than its stroke, which would otherwise fill it in.
            if (radius > width) drawCircle(colors[i], radius, alpha = 1 - t, style = ringStroke)
        }
    }
}

/** A burst's shockwave sets out this many times thinner than its rings. */
private const val SHOCK_THINNER = 4f
