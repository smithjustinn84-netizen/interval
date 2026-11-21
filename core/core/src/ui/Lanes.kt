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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.dp
import dev.justinsmith.interval.platform.prefersReducedMotion
import dev.justinsmith.interval.timer.MILLIS_PER_SECOND
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Track lines on the Setup bend (4 lane corridors) and the quiet corner echo at the foot. */
private const val SETUP_LANE_COUNT = 5
private const val SETUP_DASHED_LANE = 2
private val EchoLaneAlphas = listOf(0.55f, 0.34f, 0.16f)

/** Where the Setup track bend crests across the header and how wide its radius is relative to the viewport. */
private const val SETUP_APEX_X_RATIO = 0.30f
private const val SETUP_RADIUS_WIDTH_RATIO = 1.45f

/** Staggered-start chalk ticks along the right shoulder of the Setup bend (clockwise from 3 o'clock). */
private const val STAGGER_BASE_DEGREES = -78f
private const val STAGGER_STEP_DEGREES = 3.5f
private const val STAGGER_TICK_ALPHA = 0.72f

/** Ambient infield lighting: soft upper-right wash and grounded base vignette. */
private const val GLOW_X_RATIO = 0.78f
private const val GLOW_Y_RATIO = 0.06f
private const val GLOW_RADIUS_RATIO = 0.78f
private const val DARK_LUMINANCE = 0.5f
private const val DARK_GLOW_WARMTH = 0.08f
private const val DARK_GLOW_ALPHA = 0.52f
private const val DARK_VIGNETTE_ALPHA = 0.22f
private const val LIGHT_GLOW_ALPHA = 0.65f
private const val LIGHT_VIGNETTE_ALPHA = 0.38f
private const val VIGNETTE_START_RATIO = 0.62f

/** Two quiet pace glints gliding along the Setup bend's lanes. */
private val SetupCometLanes = listOf(1, 3)
private val SetupCometStarts = listOf(0.62f, 0.88f)
private const val SETUP_COMET_ALPHA = 0.32f

/** Graduated concentric rings around the live workout dial, fading gently toward the edges. */
private val WorkoutRingAlphas = listOf(1.0f, 0.84f, 0.68f, 0.50f, 0.34f, 0.20f, 0.10f)
private const val WORKOUT_DASHED_LANE = 2
private const val WORKOUT_INNER_FILL_ALPHA = 0.85f
private const val WORKOUT_OUTER_FILL_ALPHA = 0.42f
private const val WORKOUT_HALO_DARK_ALPHA = 0.11f
private const val WORKOUT_HALO_LIGHT_ALPHA = 0.07f
private const val WORKOUT_HALO_LANES = 3.6f
private val CardinalAngles = listOf(0f, 90f, 180f, 270f)
private const val CARDINAL_TICK_ALPHA = 0.85f

/** The workout's two comets: the phase-coloured lead on lane 1, and a dim chase further out. */
private const val LEAD_COMET_LANE = 1
private const val CHASE_COMET_LANE = 3
private const val CHASE_COMET_START_DEGREES = 135f
private const val LEAD_COMET_ALPHA = 0.72f
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
 * a stretch over the seam at nine o'clock is not found, so keep the centre left of or below the box.
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
 * The Setup tabs' Track & Chalk artwork: soft infield ambient lighting, a graceful 5-line stadium track
 * bend sweeping across the header and week strip with a dashed center lane and staggered-start chalk ticks,
 * a quiet echoing corner arc at the foot, and two subtle pace glints running the upper curve. Decorative only,
 * drawn in its own cached layer so a frame costs nothing else a redraw; with reduced motion on, the glints rest
 * mid-stretch, and while the app is idle ([LocalIdle]) they stop where they are.
 */
@Composable
fun SetupLanes(modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val colors = listOf(palette.accent, palette.restMark)
    val idle = LocalIdle.current
    val clock = rememberLaneClock(paused = idle())
    Spacer(
        modifier
            .graphicsLayer { clip = true }
            .drawWithCache {
                val step = Sizes.laneStep.toPx()
                val baseRadius = max(size.width * SETUP_RADIUS_WIDTH_RATIO, Sizes.laneFirst.toPx())
                val outerRadius = baseRadius + (SETUP_LANE_COUNT - 1) * step
                val center = Offset(size.width * SETUP_APEX_X_RATIO, Sizes.laneApex.toPx() + outerRadius)
                val echoCenter = Offset(
                    size.width + Sizes.laneEchoOut.toPx(),
                    size.height + Sizes.laneEchoBelow.toPx(),
                )
                val echoFirst = Sizes.laneEchoFirst.toPx()
                val strokes = LaneStrokes(
                    step = step,
                    line = Sizes.laneStroke.toPx(),
                    dash = Sizes.laneDash.toPx(),
                    dashGap = Sizes.laneDashGap.toPx(),
                )
                val tick = Sizes.laneTick.toPx()
                val atmosphere = setupAtmosphere(size, palette)
                val comets = setupComets(
                    size = size,
                    center = center,
                    baseRadius = baseRadius,
                    step = step,
                    tail = Sizes.cometTail.toPx(),
                    margin = Sizes.cometMargin.toPx(),
                    speed = Motion.COMET_SPEED_DP_PER_SECOND.dp.toPx() / MILLIS_PER_SECOND.toFloat(),
                    colors = colors,
                )
                val cometStroke = Stroke(Sizes.setupCometStroke.toPx(), cap = StrokeCap.Round)
                onDrawBehind {
                    drawRect(atmosphere.glow)
                    drawRect(atmosphere.vignette)
                    drawSetupBend(center, baseRadius, step, tick, palette.laneLine, palette.laneFill, strokes)
                    drawCornerEcho(echoCenter, echoFirst, step, palette.laneLine, strokes.line)
                    val now = clock.longValue
                    for (i in comets.indices) {
                        val comet = comets[i]
                        val progress = (now / comet.periodMillis + comet.start) % 1f
                        val head = cometHead(comet.arc, comet.tail, progress)
                        drawComet(center, comet.radius, head / DEGREE, comet.tail / DEGREE, comet.brush, cometStroke)
                    }
                }
            },
    )
}

private class SetupAtmosphere(val glow: Brush, val vignette: Brush)

private fun setupAtmosphere(size: Size, palette: IntervalPalette): SetupAtmosphere {
    val dark = palette.background.luminance() < DARK_LUMINANCE
    val glowColor = if (dark) {
        lerp(palette.surface, palette.accent, DARK_GLOW_WARMTH).copy(alpha = DARK_GLOW_ALPHA)
    } else {
        Color.White.copy(alpha = LIGHT_GLOW_ALPHA)
    }
    val glow = Brush.radialGradient(
        colors = listOf(glowColor, Color.Transparent),
        center = Offset(size.width * GLOW_X_RATIO, size.height * GLOW_Y_RATIO),
        radius = max(size.width, size.height) * GLOW_RADIUS_RATIO,
    )
    val baseColor = if (dark) {
        Color.Black.copy(alpha = DARK_VIGNETTE_ALPHA)
    } else {
        palette.surfaceVariant.copy(alpha = LIGHT_VIGNETTE_ALPHA)
    }
    val vignette = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to Color.Transparent,
            VIGNETTE_START_RATIO to Color.Transparent,
            1f to baseColor,
        ),
        startY = 0f,
        endY = size.height,
    )
    return SetupAtmosphere(glow, vignette)
}

private fun setupComets(
    size: Size,
    center: Offset,
    baseRadius: Float,
    step: Float,
    tail: Float,
    margin: Float,
    speed: Float,
    colors: List<Color>,
): List<SetupComet> = SetupCometLanes.mapIndexedNotNull { index, lane ->
    val radius = baseRadius + lane * step
    val arc = visibleArc(center, radius, size.width, size.height, margin) ?: return@mapIndexedNotNull null
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
 * The workout's artwork: a soft phase-tinted ambient halo behind the ring, graduated concentric track lanes
 * fading toward the edges with a dashed cadence lane and cardinal compass ticks, plus a phase-coloured lead
 * comet and a quiet chase comet further out. Centred wherever [anchor]'s ring is, and draws nothing while no
 * ring is placed (as on the summary). With reduced motion on, the comets rest.
 */
@Composable
fun WorkoutLanes(anchor: LaneAnchor, accent: () -> Color, modifier: Modifier = Modifier) {
    val clock = rememberLaneClock()
    val colors = IntervalTheme.workout
    val haloAlpha = if (colors.ground.luminance() < DARK_LUMINANCE) {
        WORKOUT_HALO_DARK_ALPHA
    } else {
        WORKOUT_HALO_LIGHT_ALPHA
    }
    Spacer(
        modifier
            .graphicsLayer { clip = true }
            .onPlaced { anchor.lanes = it }
            .drawWithCache {
                val gap = Sizes.workoutLaneGap.toPx()
                val step = Sizes.workoutLaneStep.toPx()
                val tick = Sizes.laneTick.toPx()
                val strokes = LaneStrokes(
                    step = step,
                    line = Sizes.laneStroke.toPx(),
                    dash = Sizes.laneDash.toPx(),
                    dashGap = Sizes.laneDashGap.toPx(),
                )
                val halo = Halo()
                val lead = Comet(LEAD_COMET_LANE, Sizes.cometTail.toPx(), Sizes.cometStroke.toPx())
                val chase = Comet(CHASE_COMET_LANE, Sizes.chaseTail.toPx(), Sizes.chaseStroke.toPx())
                onDrawBehind {
                    val lanes = anchor.lanes?.takeIf { it.isAttached } ?: return@onDrawBehind
                    val ring = anchor.ring?.takeIf { it.isAttached } ?: return@onDrawBehind
                    val center = lanes.localPositionOf(ring, Offset(ring.size.width / 2f, ring.size.height / 2f))
                    val first = min(ring.size.width, ring.size.height) / 2f + gap
                    val phase = accent()
                    halo.draw(this, center, first + step * WORKOUT_HALO_LANES, phase.copy(alpha = haloAlpha))
                    drawWorkoutRings(center, first, step, tick, colors.laneLine, colors.laneFill, strokes)
                    val now = clock.longValue
                    val leadHead = Angles.TWELVE_O_CLOCK + now / Motion.LEAD_COMET_TURN_MILLIS * Angles.FULL_TURN
                    val chaseHead = CHASE_COMET_START_DEGREES + now / Motion.CHASE_COMET_TURN_MILLIS * Angles.FULL_TURN
                    lead.draw(this, center, first + lead.lane * step, leadHead, phase.copy(alpha = LEAD_COMET_ALPHA))
                    val dim = colors.onGroundDim.copy(alpha = CHASE_COMET_ALPHA)
                    chase.draw(this, center, first + chase.lane * step, chaseHead, dim)
                }
            },
    )
}

/** Caches the workout's radial phase halo so it rebuilds only when layout or phase colour changes. */
private class Halo {
    private var brush: Brush? = null
    private var brushCenter: Offset? = null
    private var brushRadius = 0f
    private var brushColor: Color? = null

    fun draw(scope: DrawScope, center: Offset, radius: Float, color: Color) {
        val shader = brush?.takeIf { center == brushCenter && radius == brushRadius && color == brushColor }
            ?: Brush.radialGradient(
                colors = listOf(color, Color.Transparent),
                center = center,
                radius = radius,
            ).also {
                brush = it
                brushCenter = center
                brushRadius = radius
                brushColor = color
            }
        scope.drawCircle(shader, radius, center)
    }
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

/** Cached lane strokes: corridor fill, solid chalk line, and dashed cadence line. */
private class LaneStrokes(step: Float, line: Float, dash: Float, dashGap: Float) {
    val fill = Stroke(step)
    val line = Stroke(line)
    val dashed = Stroke(line, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dashGap)))
}

/**
 * The time behind the comets, in milliseconds the artwork has run, advanced every frame. Read while
 * drawing, never in composition, so a frame redraws the artwork alone. Frames come from an infinite
 * animation, so UI tests (which cancel those) never wait on it. Still at 0 with reduced motion on;
 * held while [paused], asking for no frames, and run on from there after.
 */
@Composable
private fun rememberLaneClock(paused: Boolean = false): LongState {
    val millis = remember { mutableLongStateOf(0L) }
    val still = prefersReducedMotion()
    LaunchedEffect(still, paused) {
        if (still) millis.longValue = 0L
        if (still || paused) return@LaunchedEffect
        val from = millis.longValue
        val start = withInfiniteAnimationFrameMillis { it }
        while (true) millis.longValue = from + withInfiniteAnimationFrameMillis { it } - start
    }
    return millis
}

/** The Setup header's sweeping 5-line track bend: alternating corridor fills, chalk lines, and stagger ticks. */
private fun DrawScope.drawSetupBend(
    center: Offset,
    first: Float,
    step: Float,
    tick: Float,
    line: Color,
    fill: Color,
    strokes: LaneStrokes,
) {
    for (lane in 0 until SETUP_LANE_COUNT - 1 step 2) {
        drawCircle(fill, first + lane * step + step / 2f, center, style = strokes.fill)
    }
    repeat(SETUP_LANE_COUNT) { lane ->
        val style = if (lane == SETUP_DASHED_LANE) strokes.dashed else strokes.line
        drawCircle(line, first + lane * step, center, style = style)
    }
    val tickColor = line.copy(alpha = line.alpha * STAGGER_TICK_ALPHA)
    repeat(SETUP_LANE_COUNT - 1) { lane ->
        val midRadius = first + lane * step + step / 2f
        val angle = (STAGGER_BASE_DEGREES + lane * STAGGER_STEP_DEGREES) * DEGREE
        drawRadialTick(center, midRadius, tick, angle, tickColor, strokes.line.width)
    }
}

/** Three quiet ghosted arcs in the lower-right corner to balance tall screens without cluttering cards. */
private fun DrawScope.drawCornerEcho(center: Offset, first: Float, step: Float, line: Color, stroke: Stroke) {
    EchoLaneAlphas.forEachIndexed { lane, alpha ->
        drawCircle(line.copy(alpha = line.alpha * alpha), first + lane * step, center, style = stroke)
    }
}

/** Concentric workout track rings attenuating outward, with inner apron washes and 12/3/6/9 compass ticks. */
private fun DrawScope.drawWorkoutRings(
    center: Offset,
    first: Float,
    step: Float,
    tick: Float,
    line: Color,
    fill: Color,
    strokes: LaneStrokes,
) {
    drawCircle(
        fill.copy(alpha = fill.alpha * WORKOUT_INNER_FILL_ALPHA),
        first + step / 2f,
        center,
        style = strokes.fill,
    )
    drawCircle(
        fill.copy(alpha = fill.alpha * WORKOUT_OUTER_FILL_ALPHA),
        first + 2 * step + step / 2f,
        center,
        style = strokes.fill,
    )
    WorkoutRingAlphas.forEachIndexed { lane, alpha ->
        val style = if (lane == WORKOUT_DASHED_LANE) strokes.dashed else strokes.line
        drawCircle(line.copy(alpha = line.alpha * alpha), first + lane * step, center, style = style)
    }
    val tickColor = line.copy(alpha = line.alpha * CARDINAL_TICK_ALPHA)
    CardinalAngles.forEach { degrees ->
        drawRadialTick(center, first, tick, degrees * DEGREE, tickColor, strokes.line.width)
    }
}

/** A crisp radial chalk mark [length] px long centred on [radius] at [angle] radians from [center]. */
private fun DrawScope.drawRadialTick(
    center: Offset,
    radius: Float,
    length: Float,
    angle: Float,
    color: Color,
    strokeWidth: Float,
) {
    val half = length / 2f
    val cosA = cos(angle)
    val sinA = sin(angle)
    val start = Offset(center.x + (radius - half) * cosA, center.y + (radius - half) * sinA)
    val end = Offset(center.x + (radius + half) * cosA, center.y + (radius + half) * sinA)
    drawLine(color, start, end, strokeWidth = strokeWidth, cap = StrokeCap.Round)
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
