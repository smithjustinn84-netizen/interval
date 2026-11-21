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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.PI
import kotlin.math.abs

/**
 * The rounds a ring's outer lane is drawn as, a segment each: [count] of them, the first [done] finished, and
 * [active] (counted from 1) the one under way, or null between rounds or once over.
 */
class RingLaps(val count: Int, val done: Int, val active: Int?) {
    override fun equals(other: Any?): Boolean =
        other is RingLaps && other.count == count && other.done == done && other.active == active

    override fun hashCode(): Int = (count * 31 + done) * 31 + (active ?: 0)
}

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
 *
 * Round it runs a lane: with [laps] a segment a round, finished ones in the workout's on-ground colour, the one
 * under way in [accent], the rest dim, so the rounds sit on the dial rather than in a row under it; without, a
 * dashed hairline.
 */
@Composable
fun ProgressRing(
    progress: () -> Float,
    accent: () -> Color,
    modifier: Modifier = Modifier,
    glide: Boolean = true,
    glideMillis: Int = Motion.RING_GLIDE_MILLIS,
    strokeScale: () -> Float = { 1f },
    laps: (() -> RingLaps?)? = null,
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
    val workout = IntervalTheme.workout
    val colors = RingColors(track = workout.control, lane = workout.upcoming, lapDone = workout.onGround)
    Spacer(
        modifier.clearAndSetSemantics {}.drawWithCache {
            val strokes = RingStrokes(
                outerGap = Sizes.ringOuterGap.toPx(),
                dashed = Stroke(
                    width = Sizes.hairline.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(Sizes.ringDash.toPx(), Sizes.ringDashGap.toPx()),
                    ),
                ),
                lap = Stroke(width = Sizes.ringLap.toPx()),
                lapGap = Sizes.ringLapGap.toPx(),
            )
            onDrawBehind {
                strokes.widen(Sizes.ring.toPx() * strokeScale())
                val sweep = (if (glide) shown.value else latest()).coerceIn(0f, 1f)
                drawRing(sweep, accent(), laps?.invoke()?.takeIf { it.count > 0 }, strokes, colors)
            }
        },
    )
}

/** The workout's colours a ring is drawn in: its [track], its outer [lane], and a finished lap. */
private class RingColors(val track: Color, val lane: Color, val lapDone: Color)

/**
 * The ring's strokes: the outer lane's (dashed, or a [lap] each with [lapGap] between) [outerGap] out from the
 * ring, and the ring's two, made again only when the width changes (a swell), not on every frame.
 */
private class RingStrokes(val outerGap: Float, val dashed: Stroke, val lap: Stroke, val lapGap: Float) {
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

/**
 * The ring filled to [sweep] in [accent] over its track, inside its outer lane: [laps] a segment a round, or with
 * none a dashed hairline.
 */
private fun DrawScope.drawRing(sweep: Float, accent: Color, laps: RingLaps?, strokes: RingStrokes, colors: RingColors) {
    val outerWidth = if (laps != null) strokes.lap.width else strokes.dashed.width
    val outerDiameter = size.minDimension - outerWidth
    if (outerDiameter > 0f) {
        if (laps != null) {
            drawLaps(laps, outerDiameter, strokes.lap, strokes.lapGap, accent, colors.lapDone, colors.lane)
        } else {
            drawDashedLane(outerDiameter, strokes.dashed, colors.lane)
        }
    }
    val lane = if (laps != null) outerWidth else 0f
    val diameter = size.minDimension - strokes.track.width - (strokes.outerGap + lane) * 2
    val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
    val arc = Size(diameter, diameter)
    drawArc(colors.track, 0f, Angles.FULL_TURN, useCenter = false, topLeft, arc, style = strokes.track)
    if (sweep > 0f) {
        drawArc(accent, Angles.TWELVE_O_CLOCK, Angles.FULL_TURN * sweep, false, topLeft, arc, style = strokes.arc)
    }
}

/** The outer lane with no laps: a dashed hairline round a circle [diameter] across, centred. */
private fun DrawScope.drawDashedLane(diameter: Float, stroke: Stroke, color: Color) {
    val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
    drawArc(color, 0f, Angles.FULL_TURN, useCenter = false, topLeft, Size(diameter, diameter), style = stroke)
}

/**
 * [lap]'s rounds round a circle [diameter] across, centred: a segment each with [gapPx] between, the finished ones
 * in [done], the one under way in [accent], the rest in [upcoming].
 */
private fun DrawScope.drawLaps(
    lap: RingLaps,
    diameter: Float,
    stroke: Stroke,
    gapPx: Float,
    accent: Color,
    done: Color,
    upcoming: Color,
) {
    val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
    val each = Angles.FULL_TURN / lap.count
    val gap = (gapPx / (PI.toFloat() * diameter) * Angles.FULL_TURN).coerceAtMost(each / 2)
    repeat(lap.count) { index ->
        val round = index + 1
        val color = when {
            round == lap.active -> accent
            round <= lap.done -> done
            else -> upcoming
        }
        val start = Angles.TWELVE_O_CLOCK + each * index + gap / 2
        drawArc(color, start, each - gap, useCenter = false, topLeft, Size(diameter, diameter), style = stroke)
    }
}
