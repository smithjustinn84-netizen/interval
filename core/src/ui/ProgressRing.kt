package dev.justinsmith.interval.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlin.math.abs

/**
 * A ring filling clockwise from 12 o'clock to [progress] (0 to 1) in [accent], over a track in the
 * workout's control colour: the interval going by, or an EMOM's minute. It [glide]s to each new value
 * over [glideMillis] (the time between the ticks that drive it) so it turns rather than steps; a
 * change past [Motion.RING_SNAP_SHARE] of the ring is a new interval, which snaps.
 */
@Composable
fun ProgressRing(
    progress: Float,
    accent: Color,
    modifier: Modifier = Modifier,
    glide: Boolean = true,
    glideMillis: Int = Motion.RING_GLIDE_MILLIS,
) {
    val shown = remember { Animatable(progress) }
    LaunchedEffect(progress, glide) {
        if (!glide || abs(progress - shown.value) > Motion.RING_SNAP_SHARE) {
            shown.snapTo(progress)
        } else {
            shown.animateTo(progress, tween(glideMillis, easing = LinearEasing))
        }
    }
    val track = IntervalTheme.workout.control
    Canvas(modifier.clearAndSetSemantics {}) {
        val stroke = Sizes.ring.toPx()
        val diameter = size.minDimension - stroke
        val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
        val arc = Size(diameter, diameter)
        drawArc(track, 0f, Angles.FULL_TURN, useCenter = false, topLeft, arc, style = Stroke(stroke))
        val sweep = shown.value.coerceIn(0f, 1f)
        if (sweep > 0f) {
            drawArc(
                accent,
                startAngle = Angles.TWELVE_O_CLOCK,
                sweepAngle = Angles.FULL_TURN * sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arc,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
}
