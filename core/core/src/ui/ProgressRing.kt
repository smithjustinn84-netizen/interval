package dev.justinsmith.interval.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs

/**
 * A ring filling clockwise from 12 o'clock to [progress] (0 to 1) in [accent], over a track in the
 * workout's control colour: the interval going by, or an EMOM's minute. It [glide]s to each new value
 * over [glideMillis] (the time between the ticks that drive it) so it turns rather than steps; a
 * change past [Motion.RING_SNAP_SHARE] of the ring is a new interval, which snaps. Its stroke is
 * [Sizes.ring] times [strokeScale], which the workout swells on an interval's last seconds.
 *
 * Each value is a provider, read where it is drawn (and [progress] by the one glide that follows it), so a
 * tick, a swell or a crossfade redraws the ring without recomposing it or its caller. Without [glide] the
 * ring draws [progress] as it stands, for a caller that animates it itself.
 */
@Composable
fun ProgressRing(
    progress: () -> Float,
    accent: () -> Color,
    modifier: Modifier = Modifier,
    glide: Boolean = true,
    glideMillis: Int = Motion.RING_GLIDE_MILLIS,
    strokeScale: () -> Float = { 1f },
) {
    val latest by rememberUpdatedState(progress)
    val shown = remember { Animatable(progress()) }
    if (glide) {
        LaunchedEffect(glideMillis) {
            snapshotFlow { latest() }.collectLatest { target ->
                if (abs(target - shown.value) > Motion.RING_SNAP_SHARE) {
                    shown.snapTo(target)
                } else {
                    shown.animateTo(target, tween(glideMillis, easing = LinearEasing))
                }
            }
        }
    }
    val track = IntervalTheme.workout.control
    Spacer(
        modifier.clearAndSetSemantics {}.drawWithCache {
            val ring = RingStrokes()
            onDrawBehind {
                val width = Sizes.ring.toPx() * strokeScale()
                ring.widen(width)
                val diameter = size.minDimension - width
                val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
                val arc = Size(diameter, diameter)
                drawArc(track, 0f, Angles.FULL_TURN, useCenter = false, topLeft, arc, style = ring.track)
                val sweep = (if (glide) shown.value else latest()).coerceIn(0f, 1f)
                if (sweep > 0f) {
                    drawArc(
                        accent(),
                        startAngle = Angles.TWELVE_O_CLOCK,
                        sweepAngle = Angles.FULL_TURN * sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arc,
                        style = ring.arc,
                    )
                }
            }
        },
    )
}

/** The ring's two strokes, made again only when the width changes (a swell), not on every frame. */
private class RingStrokes {
    var track = Stroke(0f)
        private set
    var arc = Stroke(0f, cap = StrokeCap.Round)
        private set

    fun widen(width: Float) {
        if (width == track.width) return
        track = Stroke(width)
        arc = Stroke(width, cap = StrokeCap.Round)
    }
}
