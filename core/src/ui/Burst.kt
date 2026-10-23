package dev.justinsmith.interval.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.justinsmith.interval.platform.prefersReducedMotion
import kotlinx.coroutines.delay
import kotlin.math.hypot

/**
 * A moment marked: rings of [colors], the first outermost, ride an edge that opens from the centre to
 * past the corners over [Motion.BURST_MILLIS], thinning and fading as they go, as the intro's ripple
 * does onto the app. Once, [delayMillis] after it is composed, behind whatever it marks; nothing at
 * all under reduced motion.
 */
@Composable
fun Burst(colors: List<Color>, modifier: Modifier = Modifier, delayMillis: Int = 0) {
    if (prefersReducedMotion()) return
    val opened = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        opened.animateTo(1f, tween(Motion.BURST_MILLIS, easing = FastOutSlowInEasing))
    }
    Canvas(modifier.clearAndSetSemantics {}) {
        val t = opened.value
        if (t <= 0f || t >= 1f) return@Canvas
        val gap = Sizes.burstGap.toPx()
        val reach = hypot(size.width, size.height) / 2 + gap * colors.size
        val edge = reach * t
        val width = Sizes.burstStroke.toPx() * (1 - t)
        colors.forEachIndexed { i, color ->
            val radius = edge - gap * i
            // Not until it is wider than its stroke, which would otherwise fill it in.
            if (radius > width) drawCircle(color, radius, alpha = 1 - t, style = Stroke(width))
        }
    }
}
