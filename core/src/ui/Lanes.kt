package dev.justinsmith.interval.ui

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.dp
import dev.justinsmith.interval.platform.prefersReducedMotion
import dev.justinsmith.interval.timer.MILLIS_PER_SECOND
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** How many lanes a track has. */
private const val LANE_COUNT = 11

/** The lanes the Setup comets run (innermost is 0), and where along the stretch each starts. */
private val SetupCometLanes = listOf(1, 4, 7)
private val SetupCometStarts = listOf(0.35f, 0.6f, 0.85f)

/** The workout's two comets: the phase-coloured one just outside the ring, a dim one further out. */
private const val LEAD_COMET_LANE = 1
private const val CHASE_COMET_LANE = 4
private const val CHASE_COMET_START_DEGREES = 130f

private const val SETUP_COMET_ALPHA = 0.65f
private const val LEAD_COMET_ALPHA = 0.75f
private const val CHASE_COMET_ALPHA = 0.22f

/** How a comet's tail fades: its alpha, as a share of the head's, at each share of its length. */
private val TailStops = listOf(0f to 0f, 0.45f to 0.12f, 0.75f to 0.38f, 1f to 1f)

/** A comet is drawn with its head here, then the frame is turned into place: far enough round for any tail. */
private const val COMET_REST_DEGREES = 180f
private const val HALF_TURN_DEGREES = Angles.HALF_TURN.toInt()
private const val DEGREE = (PI / Angles.HALF_TURN).toFloat()

/**
 * The stretch of a circle round [center] with [radius] that crosses a [width] × [height] box grown by
 * [margin] on every side: a range of angles in radians, clockwise from three o'clock, or null when
 * none does. Sampled a degree at a time, so either end can be up to a degree short; keep [margin]
 * over a degree of arc. One stretch only: a circle that crosses the box twice gets both as one, and
 * a stretch over the seam at nine o'clock is not found, so keep the centre left of the box.
 */
internal fun visibleArc(
    center: Offset,
    radius: Float,
    width: Float,
    height: Float,
    margin: Float,
): ClosedFloatingPointRange<Float>? {
    var first: Float? = null
    var last = 0f
    for (degrees in -HALF_TURN_DEGREES until HALF_TURN_DEGREES) {
        val angle = degrees * DEGREE
        val x = center.x + radius * cos(angle)
        val y = center.y + radius * sin(angle)
        val inside = x in -margin..width + margin && y in -margin..height + margin
        if (!inside) continue
        if (first == null) first = angle
        last = angle
    }
    return first?.let { it..last }
}

/**
 * Where a comet's head is, [progress] (0–1) of the way along a run that starts with the head at the
 * [arc]'s start and ends with the tail, [tail] radians long, just past its end: the whole comet is
 * off-screen at both ends, so the run can loop unseen.
 */
internal fun cometHead(arc: ClosedFloatingPointRange<Float>, tail: Float, progress: Float): Float =
    arc.start + (arc.endInclusive - arc.start + tail) * progress

/**
 * The Setup tabs' artwork: a running track seen from above. Its centre sits off the column's
 * bottom-left, so [LANE_COUNT] lanes sweep in from the start edge and bend down behind the cards,
 * every other one faintly filled. Three comets, in the work, rest and accent colours, run the lanes'
 * visible stretch with fading tails. Decorative only, drawn in its own layer so a frame costs nothing
 * else a redraw; with reduced motion on, the comets rest mid-stretch.
 */
@Composable
fun SetupLanes(modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val colors = listOf(palette.workMark, palette.restMark, palette.accent)
    val clock = rememberLaneClock()
    Spacer(
        modifier
            .graphicsLayer { clip = true }
            .drawWithCache {
                val center = Offset(-Sizes.laneCentreOut.toPx(), size.height + Sizes.laneCentreBelow.toPx())
                val first = Sizes.laneFirst.toPx()
                val step = Sizes.laneStep.toPx()
                val tail = Sizes.cometTail.toPx()
                val margin = Sizes.cometMargin.toPx()
                val speed = Motion.COMET_SPEED_DP_PER_SECOND.dp.toPx() / MILLIS_PER_SECOND.toFloat()
                val comets = SetupCometLanes.mapIndexedNotNull { index, lane ->
                    val radius = first + lane * step
                    val arc = visibleArc(center, radius, size.width, size.height, margin)
                    if (arc == null) return@mapIndexedNotNull null
                    SetupComet(
                        radius = radius,
                        arc = arc,
                        tail = tail / radius,
                        periodMillis = ((arc.endInclusive - arc.start) * radius + tail) / speed,
                        start = SetupCometStarts[index],
                        brush = cometBrush(
                            center,
                            tail / radius / DEGREE,
                            colors[index].copy(alpha = SETUP_COMET_ALPHA),
                        ),
                    )
                }
                val laneStrokes = LaneStrokes(step, Sizes.laneStroke.toPx())
                val cometStroke = Stroke(Sizes.cometStroke.toPx(), cap = StrokeCap.Round)
                onDrawBehind {
                    drawLanes(center, first, step, palette.laneLine, palette.laneFill, laneStrokes)
                    val now = clock.longValue
                    comets.forEach { comet ->
                        val progress = (now / comet.periodMillis + comet.start) % 1f
                        val head = cometHead(comet.arc, comet.tail, progress)
                        drawComet(center, comet.radius, head / DEGREE, comet.tail / DEGREE, comet.brush, cometStroke)
                    }
                }
            },
    )
}

private data class SetupComet(
    val radius: Float,
    val arc: ClosedFloatingPointRange<Float>,
    val tail: Float,
    val periodMillis: Float,
    val start: Float,
    val brush: Brush,
)

/**
 * Ties the workout's lanes to its ring: the ring's box takes [laneRing], the lanes find it from
 * there, however the readout is laid out.
 */
@Stable
class LaneAnchor {
    var ring by mutableStateOf<LayoutCoordinates?>(null)
    var lanes by mutableStateOf<LayoutCoordinates?>(null)
}

@Composable
fun rememberLaneAnchor(): LaneAnchor = remember { LaneAnchor() }

/** Marks the ring's box as what [WorkoutLanes] centres on. */
fun Modifier.laneRing(anchor: LaneAnchor): Modifier = onPlaced { anchor.ring = it }

/**
 * The workout's artwork: [LANE_COUNT] lanes circling the ring, every other one faintly filled, with a
 * comet in the phase colour ([accent]) lapping the innermost lane but one and a dim one further out
 * going slower. It fills the screen behind everything, centred wherever [anchor]'s ring is, and
 * draws nothing while no ring is placed, as on the summary. With reduced motion on, the comets rest.
 */
@Composable
fun WorkoutLanes(anchor: LaneAnchor, accent: Color, modifier: Modifier = Modifier) {
    val clock = rememberLaneClock()
    val colors = IntervalTheme.workout
    Spacer(
        modifier
            .graphicsLayer { clip = true }
            .onPlaced { anchor.lanes = it }
            .drawWithCache {
                val gap = Sizes.workoutLaneGap.toPx()
                val step = Sizes.workoutLaneStep.toPx()
                val laneStrokes = LaneStrokes(step, Sizes.laneStroke.toPx())
                val lead = Comet(LEAD_COMET_LANE, Sizes.cometTail.toPx(), Sizes.cometStroke.toPx())
                val chase = Comet(CHASE_COMET_LANE, Sizes.chaseTail.toPx(), Sizes.chaseStroke.toPx())
                onDrawBehind {
                    val lanes = anchor.lanes?.takeIf { it.isAttached } ?: return@onDrawBehind
                    val ring = anchor.ring?.takeIf { it.isAttached } ?: return@onDrawBehind
                    val center = lanes.localPositionOf(ring, Offset(ring.size.width / 2f, ring.size.height / 2f))
                    val first = min(ring.size.width, ring.size.height) / 2f + gap
                    drawLanes(center, first, step, colors.laneLine, colors.laneFill, laneStrokes)
                    val now = clock.longValue
                    val leadHead = Angles.TWELVE_O_CLOCK + now / Motion.LEAD_COMET_TURN_MILLIS * Angles.FULL_TURN
                    val chaseHead = CHASE_COMET_START_DEGREES + now / Motion.CHASE_COMET_TURN_MILLIS * Angles.FULL_TURN
                    lead.draw(this, center, first + lead.lane * step, leadHead, accent.copy(alpha = LEAD_COMET_ALPHA))
                    val dim = colors.onGroundDim.copy(alpha = CHASE_COMET_ALPHA)
                    chase.draw(this, center, first + chase.lane * step, chaseHead, dim)
                }
            },
    )
}

/**
 * A workout comet: which lane it runs, in px how long its tail is and how thick. Its gradient is a shader,
 * built once per place and colour rather than on every frame: the ring moves only as the readout lays out, and
 * the colour only with the phase.
 */
private class Comet(val lane: Int, private val tail: Float, stroke: Float) {
    private val stroke = Stroke(stroke, cap = StrokeCap.Round)
    private var brush: Brush? = null
    private var brushCenter: Offset? = null
    private var brushRadius = 0f
    private var brushColor: Color? = null

    fun draw(scope: DrawScope, center: Offset, radius: Float, headDegrees: Float, color: Color) {
        val tailDegrees = tail / radius / DEGREE
        val brush = brush?.takeIf { center == brushCenter && radius == brushRadius && color == brushColor }
            ?: cometBrush(center, tailDegrees, color).also {
                brush = it
                brushCenter = center
                brushRadius = radius
                brushColor = color
            }
        scope.drawComet(center, radius, headDegrees, tailDegrees, brush, stroke)
    }
}

/** The lanes' two stroke styles, built once per layout rather than per frame: the fill between lanes, and the line. */
private class LaneStrokes(step: Float, line: Float) {
    val fill = Stroke(step)
    val line = Stroke(line)
}

/**
 * The time behind the comets, in milliseconds since the artwork appeared, advanced every frame. Read
 * while drawing, never in composition, so a frame redraws the artwork alone. Frames come from an
 * infinite animation, so UI tests (which cancel those) never wait on it. Still at 0 with reduced
 * motion on.
 */
@Composable
private fun rememberLaneClock(): LongState {
    val millis = remember { mutableLongStateOf(0L) }
    val still = prefersReducedMotion()
    LaunchedEffect(still) {
        millis.longValue = 0L
        if (still) return@LaunchedEffect
        val start = withInfiniteAnimationFrameMillis { it }
        while (true) millis.longValue = withInfiniteAnimationFrameMillis { it } - start
    }
    return millis
}

/** [LANE_COUNT] lanes round [center], the innermost [first] from it and [step] apart, every other one filled. */
private fun DrawScope.drawLanes(
    center: Offset,
    first: Float,
    step: Float,
    line: Color,
    fill: Color,
    strokes: LaneStrokes,
) {
    for (lane in 0 until LANE_COUNT - 1 step 2) {
        drawCircle(fill, first + lane * step + step / 2, center, style = strokes.fill)
    }
    repeat(LANE_COUNT) { lane ->
        drawCircle(line, first + lane * step, center, style = strokes.line)
    }
}

/**
 * A comet's gradient: fading in from nothing along a tail [tailDegrees] long to [color] at the head, for a comet
 * drawn at [COMET_REST_DEGREES] round [center] ([drawComet]). A shader, so built once and kept, not per frame.
 */
private fun cometBrush(center: Offset, tailDegrees: Float, color: Color): Brush {
    val tailStart = COMET_REST_DEGREES - tailDegrees
    val stops = TailStops.map { (share, alpha) ->
        (tailStart + tailDegrees * share) / Angles.FULL_TURN to color.copy(alpha = color.alpha * alpha)
    }
    return Brush.sweepGradient(*(stops + (1f to color)).toTypedArray(), center = center)
}

/**
 * A comet on a lane: a round-capped arc [tailDegrees] long ending at [headDegrees] (clockwise from
 * three o'clock), in [brush] from [cometBrush]. A sweep gradient's stops run once round from three
 * o'clock and can't wrap, so the comet is drawn at a fixed angle and the frame turned to put it in place.
 */
private fun DrawScope.drawComet(
    center: Offset,
    radius: Float,
    headDegrees: Float,
    tailDegrees: Float,
    brush: Brush,
    stroke: Stroke,
) {
    val tailStart = COMET_REST_DEGREES - tailDegrees
    rotate(headDegrees - COMET_REST_DEGREES, center) {
        drawArc(
            brush = brush,
            startAngle = tailStart,
            sweepAngle = tailDegrees,
            useCenter = false,
            topLeft = center - Offset(radius, radius),
            size = Size(radius * 2, radius * 2),
            style = stroke,
        )
    }
}
