package dev.justinsmith.interval.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import kotlin.math.max
import kotlin.math.min

/**
 * [rep] fitted to a canvas of [size] for [mode]: the scale, floor/plumb placement, optional dashed bell trajectory,
 * mat geometry, dynamic ceiling table (when [ExerciseRep.follows] in [DemoDisplayMode.Compact]), and reusable
 * per-frame buffers. Built once per `(rep, size, mode, colors)` inside `drawWithCache`.
 */
@Suppress("detekt.TooManyFunctions") // Stage geometry & sampling helpers grouped in one cached holder.
internal class DemoStage(
    private val rep: ExerciseRep,
    private val size: Size,
    private val mode: DemoDisplayMode,
    referenceColor: Color,
    density: Density,
) {
    /** Current pixels per standing-height unit, floor Y, plumb X, and top Y (updated per frame if [follows]). */
    var scale: Float = 1f
        private set
    var floor: Float = 0f
        private set
    var plumb: Float = 0f
        private set
    var top: Float = 0f
        private set

    private val baseLeft: Float
    private val baseRight: Float
    private val baseBottom: Float
    private val baseTop: Float
    private val pad: Float = size.minDimension * FRAME_PAD

    /** Optional clip rect in screen coordinates when [ExerciseRep.crop] is active. */
    val clipRect: Rect?

    /** Standing hip and shoulder Y positions for datum ticks on the plumb line. */
    val datums: FloatArray

    /** Offscreen layer bounds covering the whole canvas plus margin for cutaway gaps. */
    val layerBounds = Rect(-LAYER_MARGIN, -LAYER_MARGIN, size.width + LAYER_MARGIN, size.height + LAYER_MARGIN)

    /** Horizontal fading floor brush and endpoints. */
    val floorFrom: Float
    val floorTo: Float
    val floorBrush: Brush

    /** Side-view mat X range (in screen px) or top-down mat Rect (in screen px). */
    val matRange: ClosedFloatingPointRange<Float>?
    val matFromAbove: Rect?

    /** Dashed bell trajectory & keyframe rings (only populated in [DemoDisplayMode.Detail]). */
    val trajectory = Path()
    val keyMarks: List<Offset>

    val hairline = with(density) { Sizes.hairline.toPx() }
    val halo = with(density) { Sizes.demoPath.toPx() }
    val dashes: PathEffect =
        with(density) { PathEffect.dashPathEffect(floatArrayOf(Sizes.demoDash.toPx(), Sizes.demoDashGap.toPx())) }
    val pathStyle = Stroke(with(density) { Sizes.demoPath.toPx() }, cap = StrokeCap.Round, pathEffect = dashes)
    val trailStyle = Stroke(with(density) { Sizes.demoTrail.toPx() }, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val markStyle = Stroke(hairline)

    /** Pre-allocated drawing pen & kinematic buffers (zero per-frame heap allocation). */
    val pen = BodyPen()
    private val angles = FloatArray(BodyPose.JOINTS)
    private val laggedAngles = FloatArray(BodyPose.JOINTS)
    private val frame = BodyFrame()
    private val activeTrail = Path()
    private val measure = PathMeasure()
    private val travelled = FloatArray(SAMPLES + 1)

    /** Whether the bell leaves a ribbon behind it: not on a still thumb, nor behind a ghost. */
    private val trails = rep.showsTrail && mode != DemoDisplayMode.Thumb && mode != DemoDisplayMode.Ghost

    /** Smoothed per-step ceiling heights (in Y-up standing-height units) for [ExerciseRep.follows] in Compact mode. */
    private val followTops: FloatArray?

    init {
        val sampleCount = if (mode == DemoDisplayMode.Thumb) 0 else SAMPLES
        val samples = if (mode == DemoDisplayMode.Thumb && rep.props.isEmpty() && rep.crop == null) {
            listOf(rep.jointsAt(rep.still))
        } else {
            List(sampleCount + 1) { rep.jointsAt(if (sampleCount == 0) rep.still else it.toFloat() / sampleCount) }
        }
        val bounds = computeBounds(samples)
        val crop = rep.crop
        baseLeft = crop?.left ?: bounds.left
        baseRight = crop?.right ?: bounds.right
        baseBottom = crop?.top ?: bounds.top
        baseTop = crop?.bottom ?: bounds.bottom
        fitBounds(baseLeft, baseRight, baseBottom, baseTop)

        clipRect = crop?.let {
            val tl = place(Offset(it.left, it.bottom))
            val br = place(Offset(it.right, it.top))
            Rect(tl.x, tl.y, br.x, br.y)
        }

        datums = BodyPose().joints().let { floatArrayOf(place(it.hip).y, place(it.shoulder).y) }
        floorFrom = (size.width * FLOOR_INSET).coerceAtLeast(0f)
        floorTo = (size.width * (1f - FLOOR_INSET)).coerceAtMost(size.width)
        floorBrush = Brush.horizontalGradient(
            0f to referenceColor.copy(alpha = 0f),
            FLOOR_FADE to referenceColor,
            1f - FLOOR_FADE to referenceColor,
            1f to referenceColor.copy(alpha = 0f),
            startX = floorFrom,
            endX = floorTo,
        )

        matRange = computeSideMat(samples)
        matFromAbove = computeAboveMat(samples)

        if (mode == DemoDisplayMode.Detail && rep.showsTrajectory) {
            keyMarks = rep.keyframes.filterNot { it.passing }
                .map { place(it.pose.joints(rep.root, rep.anchor, rep.facing, rep.hold).kettlebellCenter) }
                .distinct()
            layPath(samples.map { place(it.kettlebellCenter) })
        } else {
            keyMarks = emptyList()
        }

        followTops = if (rep.follows && mode == DemoDisplayMode.Compact && crop == null) {
            computeFollowTops()
        } else {
            null
        }
    }

    /** Updates scale/floor/plumb for [progress] if [ExerciseRep.follows] is active. */
    fun frameFor(progress: Float) {
        val table = followTops ?: return
        val at = looped(progress.coerceAtLeast(0f)) * FOLLOW_STEPS
        val step = at.toInt().coerceIn(0, FOLLOW_STEPS - 1)
        val next = (step + 1) % FOLLOW_STEPS
        val dynamicTop = table[step] + (table[next] - table[step]) * (at - step)
        fitBounds(baseLeft, baseRight, baseBottom, dynamicTop)
    }

    /** Computes [BodyFrame] at [progress] in place (zero allocation). */
    fun jointsAt(progress: Float): BodyFrame {
        rep.anglesAt(progress, angles)
        val lagBell: Float
        val lagFarBell: Float
        if (rep.bellLag > 0f && progress >= 0f) {
            rep.anglesAt(looped(progress - rep.bellLag), laggedAngles)
            lagBell = laggedAngles[BodyPose.SHOULDER] - laggedAngles[BodyPose.TORSO] +
                laggedAngles[BodyPose.ELBOW] + laggedAngles[BodyPose.BELL]
            lagFarBell = laggedAngles[BodyPose.FAR_SHOULDER] - laggedAngles[BodyPose.TORSO] +
                laggedAngles[BodyPose.FAR_ELBOW] + laggedAngles[BodyPose.FAR_BELL]
        } else {
            lagBell = Float.NaN
            lagFarBell = Float.NaN
        }
        frame.place(angles, rep.root, rep.anchor, rep.facing, rep.hold, lagBell, lagFarBell)
        return frame
    }

    /** Maps a Y-up standing-height coordinate into Y-down canvas pixels. */
    fun place(point: Offset): Offset = Offset(plumb + point.x * scale, floor - point.y * scale)

    /** Samples the tapered ribbon trail points into [pen]'s pre-allocated float buffer. */
    fun populateMotionTrail(progress: Float) {
        if (!trails || progress < 0f) {
            pen.trailCount = 0
            return
        }
        val steps = BodyPen.TRAIL_STEPS
        for (i in 0..<steps) {
            val at = looped(progress - TRAIL_SHARE * (steps - i) / steps)
            val bell = place(jointsAt(at).kettlebellCenter)
            pen.trail[2 * i] = bell.x
            pen.trail[2 * i + 1] = bell.y
        }
        pen.trailCount = steps
    }

    /** Active stretch along the dashed trajectory behind [progress] (used in [DemoDisplayMode.Detail]). */
    fun trailTo(progress: Float, share: Float): Path {
        val length = travelled.last()
        if (length <= 0f) {
            activeTrail.rewind()
            return activeTrail
        }
        val spot = looped(progress.coerceAtLeast(0f)) * SAMPLES
        val index = spot.toInt().coerceAtMost(SAMPLES - 1)
        val head = travelled[index] + (travelled[index + 1] - travelled[index]) * (spot - index)
        val tail = head - length * share
        activeTrail.rewind()
        if (tail < 0f) measure.getSegment(length + tail, length, activeTrail, true)
        measure.getSegment(tail.coerceAtLeast(0f), head, activeTrail, true)
        return activeTrail
    }

    private fun fitBounds(left: Float, right: Float, bottom: Float, topBound: Float) {
        val spanX = (right - left).coerceAtLeast(MIN_SPAN)
        val spanY = (topBound - bottom).coerceAtLeast(MIN_SPAN)
        scale = min((size.width - pad * 2f) / spanX, (size.height - pad * 2f) / spanY)
        plumb = size.width * HALF - (left + right) * HALF * scale
        floor = size.height * HALF + (topBound + bottom) * HALF * scale
        top = floor - topBound * scale
        pen.scale = scale
    }

    /** Returns `Rect(left, bottomY, right, topY)` in Y-up standing-height units. */
    private fun computeBounds(samples: List<BodyJoints>): Rect {
        var left = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var low = if (rep.facing == RepFacing.Above) Float.MAX_VALUE else 0f
        var high = if (rep.facing == RepFacing.Above) -Float.MAX_VALUE else MIN_HEIGHT
        if (rep.facing == RepFacing.Side && rep.root == RepRoot.Ankle && mode == DemoDisplayMode.Detail) {
            left = -BodyProportions.HEEL - BodyProportions.SOLE
            right = BodyProportions.TOE + BodyProportions.SOLE
            high = 1f
        }
        for (joints in samples) {
            joints.forEachReach(rep.hold) { point, room ->
                left = min(left, point.x - room)
                right = max(right, point.x + room)
                low = min(low, point.y - room)
                high = max(high, point.y + room)
            }
        }
        val withProps = expandForProps(Rect(left, low, right, high))
        return if (rep.facing != RepFacing.Above) {
            withProps.copy(top = min(withProps.top, -FLOOR_SHADOW_ROOM))
        } else {
            Rect(
                withProps.left - BodyProportions.MAT_MARGIN,
                withProps.top - BodyProportions.MAT_MARGIN,
                withProps.right + BodyProportions.MAT_MARGIN,
                withProps.bottom + BodyProportions.MAT_MARGIN,
            )
        }
    }

    private fun expandForProps(initial: Rect): Rect {
        var left = initial.left
        var right = initial.right
        var high = initial.bottom
        for (prop in rep.props) {
            when (prop) {
                is RepBench -> {
                    left = min(left, prop.from)
                    right = max(right, prop.to)
                    high = max(high, prop.height + BodyProportions.BENCH_TOP)
                }

                is RepWall -> {
                    left = min(left, prop.x - PROP_MARGIN)
                    right = max(right, prop.x + PROP_MARGIN)
                }

                is RepRail -> {
                    left = min(left, prop.at.x - BodyProportions.RAIL)
                    right = max(right, prop.at.x + BodyProportions.RAIL)
                    high = max(high, prop.at.y + BodyProportions.RAIL * 2f)
                }

                is RepBand -> {
                    for (end in listOf(prop.from, prop.to)) {
                        if (end is RepBandEnd.Post) {
                            left = min(left, end.at.x - PROP_MARGIN)
                            right = max(right, end.at.x + PROP_MARGIN)
                            high = max(high, end.at.y + PROP_MARGIN)
                        }
                    }
                }
            }
        }
        return Rect(left, initial.top, right, high)
    }

    private fun computeSideMat(samples: List<BodyJoints>): ClosedFloatingPointRange<Float>? {
        if (rep.hold != RepHold.Free || rep.bands.isNotEmpty() || rep.facing == RepFacing.Above) return null
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        for (joints in samples) {
            joints.forEachReach(rep.hold) { point, _ ->
                minX = min(minX, point.x)
                maxX = max(maxX, point.x)
            }
        }
        val reach = max(BodyProportions.MAT_REACH, (BodyProportions.MAT_LENGTH - (maxX - minX)) * HALF)
        val fromPx = place(Offset(minX - reach, 0f)).x.coerceAtLeast(floorFrom)
        val toPx = place(Offset(maxX + reach, 0f)).x.coerceAtMost(floorTo)
        return fromPx..toPx.coerceAtLeast(fromPx)
    }

    private fun computeAboveMat(samples: List<BodyJoints>): Rect? {
        if (rep.facing != RepFacing.Above) return null
        var left = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var low = Float.MAX_VALUE
        var high = -Float.MAX_VALUE
        for (joints in samples) {
            joints.forEachReach(rep.hold) { point, _ ->
                left = min(left, point.x)
                right = max(right, point.x)
                low = min(low, point.y)
                high = max(high, point.y)
            }
        }
        val tl = place(Offset(left - BodyProportions.MAT_MARGIN, high + BodyProportions.MAT_MARGIN))
        val br = place(Offset(right + BodyProportions.MAT_MARGIN, low - BodyProportions.MAT_MARGIN))
        return Rect(tl.x, tl.y, br.x, br.y)
    }

    private fun computeFollowTops(): FloatArray {
        val raw = FloatArray(FOLLOW_STEPS) { step ->
            val joints = rep.jointsAt(step / FOLLOW_STEPS.toFloat())
            var high = MIN_HEIGHT
            joints.forEachReach(rep.hold) { point, room -> high = max(high, point.y + room) }
            high
        }
        val smooth = FloatArray(FOLLOW_STEPS) { step ->
            var highest = MIN_HEIGHT
            for (d in -FOLLOW_WINDOW..FOLLOW_WINDOW) {
                highest = max(highest, raw[(step + d).mod(FOLLOW_STEPS)])
            }
            (highest + FOLLOW_HEADROOM).coerceAtMost(baseTop)
        }
        val maxDelta = FOLLOW_SPEED * rep.seconds / FOLLOW_STEPS
        repeat(FOLLOW_PASSES) {
            for (step in 0..<FOLLOW_STEPS) {
                smooth[step] = max(smooth[step], smooth[(step - 1).mod(FOLLOW_STEPS)] - maxDelta)
            }
            for (step in FOLLOW_STEPS - 1 downTo 0) {
                smooth[step] = max(smooth[step], smooth[(step + 1).mod(FOLLOW_STEPS)] - maxDelta)
            }
        }
        return smooth
    }

    private fun layPath(points: List<Offset>) {
        val whole = Path()
        whole.moveTo(points[0].x, points[0].y)
        var drawing = false
        for (index in 1..points.lastIndex) {
            val from = points[index - 1]
            val to = points[index]
            whole.lineTo(to.x, to.y)
            travelled[index] = travelled[index - 1] + (to - from).getDistance()
            val retraced = (1..<index).any {
                travelled[index] - travelled[it] > halo * APART &&
                    distanceToSegment(to, points[it - 1], points[it]) < halo
            }
            if (!retraced) {
                if (!drawing) trajectory.moveTo(from.x, from.y)
                trajectory.lineTo(to.x, to.y)
            }
            drawing = !retraced
        }
        measure.setPath(whole, forceClosed = false)
    }

    private fun distanceToSegment(point: Offset, from: Offset, to: Offset): Float {
        val along = to - from
        val length = along.getDistanceSquared()
        if (length == 0f) return (point - from).getDistance()
        val share = (((point - from).x * along.x + (point - from).y * along.y) / length).coerceIn(0f, 1f)
        return (point - (from + along * share)).getDistance()
    }

    companion object {
        const val TRAIL_SHARE = 0.16f
        const val KEY_MARK = 2.5f
    }
}

private const val HALF = 0.5f
private const val SAMPLES = 120
private const val FRAME_PAD = 0.06f
private const val APART = 6f
private const val LAYER_MARGIN = 60f
private const val FLOOR_INSET = 0.02f
private const val FLOOR_FADE = 0.14f
private const val FLOOR_SHADOW_ROOM = 0.05f
private const val PROP_MARGIN = 0.08f
private const val MIN_SPAN = 0.2f
private const val MIN_HEIGHT = 0.35f
private const val FOLLOW_STEPS = 48
private const val FOLLOW_WINDOW = 4
private const val FOLLOW_HEADROOM = 0.12f
private const val FOLLOW_SPEED = 0.35f
private const val FOLLOW_PASSES = 3
