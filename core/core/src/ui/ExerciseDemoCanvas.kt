package dev.justinsmith.interval.ui

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import dev.justinsmith.interval.platform.prefersReducedMotion
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

// A lift shown as a diagram: a figure in profile worked from its joints' angles (BodyPose.kt), the floor and a
// plumb line to read it against, and the path the bell takes through the rep (ExerciseRep.kt). Everything is
// measured in shares of the figure's standing height and scaled to the canvas, so nothing here is a size of its own.

/**
 * The colours a demonstration is drawn in: the [body], its [far] leg and arm where they show behind it, the dot at
 * each of its joints, the [reference] lines it is read against (the floor and the plumb line), and the [bell] with
 * its path.
 */
@Immutable
data class ExerciseDemoColors(
    val body: Color,
    val far: Color,
    val joint: Color,
    val reference: Color,
    val bell: Color,
) {
    companion object {
        /**
         * [palette]'s: the body in the text's colour and its far side in the dimmer text's, the references in the
         * outline's, quiet enough to sit behind it, and the bell in the accent.
         */
        internal fun of(palette: IntervalPalette) = ExerciseDemoColors(
            body = palette.text,
            far = palette.textDim,
            joint = palette.background,
            reference = palette.line,
            bell = palette.accent,
        )

        /** The theme's. */
        @Composable
        @ReadOnlyComposable
        fun theme(): ExerciseDemoColors = of(IntervalTheme.palette)
    }
}

/**
 * [rep] shown as a diagram: a figure in profile with a kettlebell, the floor it stands on, a plumb line up from
 * its ankle with a tick at the height of its hip and shoulder standing (so how deep a hinge goes and whether a
 * lockout is stacked can be read off), and the path the bell takes, dashed, with the stretch it has just come
 * along drawn solid behind it.
 *
 * It runs round by itself, a rep each [ExerciseRep.seconds], unless [progress] holds it: 0 to 1 through the rep,
 * for a slider or a list of the rep's phases to scrub it. Let go ([progress] null again) it runs on from there.
 * With the system's reduced motion it stands at [ExerciseRep.still] and only moves where it is held.
 *
 * It fills the room [modifier] gives it, the whole rep fitted inside, so the figure never moves about its frame.
 * [contentDescription] says what it shows, for a screen reader. [onPhase] is told the part of the rep it is in
 * ([ExerciseRep.phaseAt]) as that changes, for a label that follows it: [ExerciseDiagram] is the two together.
 * [position] is where it keeps how far through the rep it has run, for a caller that draws that too (a marker on
 * a [PhaseTrack]).
 */
@Composable
fun ExerciseDemoCanvas(
    rep: ExerciseRep,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    colors: ExerciseDemoColors = ExerciseDemoColors.theme(),
    contentDescription: String? = null,
    onPhase: ((RepPhase?) -> Unit)? = null,
    position: RepPosition = rememberRepPosition(rep),
) {
    val held by rememberUpdatedState(progress?.coerceIn(0f, 1f))
    var inView by remember { mutableStateOf(true) }
    RunLoop(rep, position, progress, inView)
    if (onPhase != null) {
        val report by rememberUpdatedState(onPhase)
        LaunchedEffect(rep, position) { snapshotFlow { rep.phaseAt(held ?: position.share) }.collect { report(it) } }
    }
    val described = if (contentDescription == null) {
        Modifier
    } else {
        Modifier.semantics { this.contentDescription = contentDescription }
    }
    Spacer(
        modifier
            .then(described)
            .onVisibilityChanged(minFractionVisible = 0f) { inView = it }
            // A layer of its own, so the gap round the arm and the bell can be cut through what is under them.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithCache {
                if (size.minDimension <= 0f) return@drawWithCache onDrawBehind {}
                val stage = DemoStage(rep, size, this)
                onDrawBehind {
                    // Read here, so a frame redraws the figure and recomposes nothing.
                    val at = held ?: position.share
                    drawReferences(stage, colors.reference)
                    drawTrajectory(stage, at, colors.bell)
                    val pose = rep.poseAt(at)
                    val joints = pose.joints()
                    if (pose.showsFarSide) drawFarSide(stage, joints, colors)
                    drawFigure(stage, joints, colors)
                }
            },
    )
}

/**
 * How far through its rep a diagram has run by itself: [share], 0 to 1. It changes every frame while the rep
 * runs, so read it while drawing, where a frame redraws and recomposes nothing.
 */
@Stable
class RepPosition internal constructor(start: Float) {
    var share: Float by mutableFloatStateOf(start)
        internal set
}

/** A [RepPosition] for [rep], starting where the rep stands still. */
@Composable
fun rememberRepPosition(rep: ExerciseRep): RepPosition = remember(rep) { RepPosition(rep.still) }

/**
 * Runs [position] through [rep] by itself. It stands where [held] has it while it is held, so it runs on from
 * there, stands still under reduced motion, and takes no frames while it is scrolled out of view ([inView]). The
 * frames come from an infinite animation, which a UI test cancels rather than waits on.
 */
@Composable
private fun RunLoop(rep: ExerciseRep, position: RepPosition, held: Float?, inView: Boolean) {
    val still = prefersReducedMotion()
    LaunchedEffect(rep, position, held, still, inView) {
        if (held != null) position.share = held.coerceIn(0f, 1f)
        if (held != null || still || !inView) return@LaunchedEffect
        val from = position.share
        val start = withInfiniteAnimationFrameMillis { it }
        while (true) {
            val elapsed = withInfiniteAnimationFrameMillis { it } - start
            position.share = (from + elapsed / (rep.seconds * MILLIS_PER_SECOND)).mod(1f)
        }
    }
}

/** A limb's width at each end, as shares of the figure's height: wider where it joins the body. */
private enum class Limb(val from: Float, val to: Float) {
    Thigh(from = 0.098f, to = 0.068f),
    Shin(from = 0.062f, to = 0.044f),
    Torso(from = 0.1f, to = 0.112f),
    UpperArm(from = 0.06f, to = 0.05f),
    Forearm(from = 0.048f, to = 0.036f),
    Hand(from = 0.036f, to = 0.046f),

    /** A forearm with no hand drawn beyond it: it ends in the fist. */
    Fist(from = 0.048f, to = 0.046f),
}

/**
 * [rep] fitted to a canvas of [size]: the scale and the place of the floor and the plumb line, the bell's path
 * through the rep, and the strokes everything is drawn with. Worked out once a size, not once a frame.
 */
private class DemoStage(rep: ExerciseRep, size: Size, density: Density) {
    /** Pixels to the figure's standing height. */
    val scale: Float

    /** The floor's y, the plumb line's x, and the y the tallest moment of the rep reaches. */
    val floor: Float
    val plumb: Float
    val top: Float

    /** The y of the standing figure's hip and shoulder: the ticks on the plumb line. */
    val datums: List<Float>

    /** The bell's path where it is drawn dashed: each stretch once, though the bell comes back along it. */
    val trajectory = Path()

    /** Where the bell is at each stance the rep rests in. */
    val keyMarks: List<Offset>

    val hairline = with(density) { Sizes.hairline.toPx() }
    val halo = with(density) { Sizes.demoPath.toPx() }
    val dashes: PathEffect =
        with(density) { PathEffect.dashPathEffect(floatArrayOf(Sizes.demoDash.toPx(), Sizes.demoDashGap.toPx())) }
    val pathStyle = Stroke(with(density) { Sizes.demoPath.toPx() }, cap = StrokeCap.Round, pathEffect = dashes)
    val trailStyle = Stroke(with(density) { Sizes.demoTrail.toPx() }, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val markStyle = Stroke(hairline)
    val handleStyle: Stroke
    val handleHalo: Stroke

    /** Paths a frame draws through and leaves for the next, so none is made while it runs. */
    val scratch = Path()
    val bellBody = Path()
    private val trail = Path()

    /** The whole path, as the bell goes along it, and how far along it the bell is at each sample. */
    private val measure = PathMeasure()
    private val travelled = FloatArray(SAMPLES + 1)

    init {
        val samples = List(SAMPLES + 1) { rep.poseAt(it.toFloat() / SAMPLES).joints() }
        var left = -BodyProportions.HEEL - BodyProportions.SOLE
        var right = BodyProportions.TOE + BodyProportions.SOLE
        var height = 1f
        for (joints in samples) {
            joints.forEachReach { point, room ->
                left = min(left, point.x - room)
                right = max(right, point.x + room)
                height = max(height, point.y + room)
            }
        }
        val pad = size.minDimension * FRAME_PAD
        scale = min((size.width - pad * 2) / (right - left), (size.height - pad * 2) / height)
        plumb = size.width / 2 - (left + right) / 2 * scale
        floor = size.height / 2 + height * scale / 2
        top = floor - height * scale
        datums = BodyPose().joints().let { listOf(place(it.hip).y, place(it.shoulder).y) }
        keyMarks = rep.keyframes.filterNot { it.passing }.map { place(it.pose.joints().kettlebellCenter) }.distinct()
        val handle = BodyProportions.BELL_HANDLE * HANDLE_STROKE * scale
        val corner = PathEffect.cornerPathEffect(handle)
        handleStyle = Stroke(handle, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = corner)
        handleHalo = Stroke(handle + halo * 2, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = corner)
        layPath(samples.map { place(it.kettlebellCenter) })
    }

    /** A point of the figure's own measure on the canvas. */
    fun place(point: Offset): Offset = Offset(plumb + point.x * scale, floor - point.y * scale)

    /**
     * The stretch of the path the bell has just come along at [progress], [share] of the whole path long. It goes
     * round with the rep, so at the start of one it is the end of the one before.
     */
    fun trailTo(progress: Float, share: Float): Path {
        val length = travelled.last()
        val spot = progress.coerceIn(0f, 1f) * SAMPLES
        val index = spot.toInt().coerceAtMost(SAMPLES - 1)
        val head = travelled[index] + (travelled[index + 1] - travelled[index]) * (spot - index)
        val tail = head - length * share
        trail.rewind()
        if (tail < 0f) measure.getSegment(length + tail, length, trail, true)
        measure.getSegment(tail.coerceAtLeast(0f), head, trail, true)
        return trail
    }

    /**
     * Lays the bell's path through [points], twice over: whole, for measuring along, and for drawing dashed with
     * every stretch the bell has already been along left out. Most reps come back the way they went, and two
     * runs of dashes out of step on one line would fill each other's gaps.
     */
    private fun layPath(points: List<Offset>) {
        val whole = Path()
        whole.moveTo(points[0].x, points[0].y)
        var drawing = false
        for (index in 1..points.lastIndex) {
            val from = points[index - 1]
            val to = points[index]
            whole.lineTo(to.x, to.y)
            travelled[index] = travelled[index - 1] + (to - from).getDistance()
            // Been along before: beside an earlier stretch that is not simply the one it has just left.
            val retraced = (1 until index).any {
                travelled[index] - travelled[it] > halo * APART && distance(to, points[it - 1], points[it]) < halo
            }
            if (!retraced) {
                if (!drawing) trajectory.moveTo(from.x, from.y)
                trajectory.lineTo(to.x, to.y)
            }
            drawing = !retraced
        }
        measure.setPath(whole, forceClosed = false)
    }

    /** How far [point] is from the line between [from] and [to]. */
    private fun distance(point: Offset, from: Offset, to: Offset): Float {
        val along = to - from
        val length = along.getDistanceSquared()
        if (length == 0f) return (point - from).getDistance()
        val share = (((point - from).x * along.x + (point - from).y * along.y) / length).coerceIn(0f, 1f)
        return (point - (from + along * share)).getDistance()
    }
}

/** The floor, right across, and the plumb line up from the ankle with a tick at the standing hip and shoulder. */
private fun DrawScope.drawReferences(stage: DemoStage, color: Color) {
    drawLine(color, Offset(0f, stage.floor), Offset(size.width, stage.floor), stage.hairline)
    drawLine(
        color,
        Offset(stage.plumb, stage.top),
        Offset(stage.plumb, stage.floor),
        stage.hairline,
        cap = StrokeCap.Round,
        pathEffect = stage.dashes,
    )
    val tick = BodyProportions.JOINT_REACH * stage.scale
    stage.datums.forEach { y ->
        drawLine(color, Offset(stage.plumb - tick, y), Offset(stage.plumb + tick, y), stage.hairline)
    }
}

/**
 * The bell's path through the whole rep, dashed and faint, a ring where each stance it rests in has it, and over
 * that the stretch it has just come along at [progress], fading away behind it.
 */
private fun DrawScope.drawTrajectory(stage: DemoStage, progress: Float, color: Color) {
    val faint = color.copy(alpha = PATH_ALPHA)
    drawPath(stage.trajectory, faint, style = stage.pathStyle)
    stage.keyMarks.forEach { drawCircle(faint, stage.halo * KEY_MARK, it, style = stage.markStyle) }
    drawPath(stage.trailTo(progress, TRAIL_SHARE), faint, style = stage.trailStyle)
    drawPath(stage.trailTo(progress, TRAIL_SHARE / 2), color, style = stage.trailStyle)
}

/**
 * The far leg and arm, behind the body: each limb and a dot a joint in the far side's colour, then a gap cut out of
 * them round where the near leg, the trunk and the head are about to be drawn, so the body stands off them.
 */
private fun DrawScope.drawFarSide(stage: DemoStage, joints: BodyJoints, colors: ExerciseDemoColors) {
    val hip = stage.place(joints.hip)
    val shoulder = stage.place(joints.shoulder)
    val knee = stage.place(joints.farKnee)
    val ankle = stage.place(joints.farAnkle)
    val elbow = stage.place(joints.farElbow)
    val wrist = stage.place(joints.farWrist)
    drawLine(
        colors.far,
        stage.place(joints.farHeel),
        stage.place(joints.farToe),
        strokeWidth = BodyProportions.SOLE * 2 * stage.scale,
        cap = StrokeCap.Round,
    )
    drawLimb(Limb.Shin, knee, ankle, stage, colors.far)
    drawLimb(Limb.Thigh, hip, knee, stage, colors.far)
    drawLimb(Limb.UpperArm, shoulder, elbow, stage, colors.far)
    drawLimb(Limb.Fist, elbow, wrist, stage, colors.far)
    val dot = BodyProportions.JOINT_REACH * JOINT_DOT * stage.scale
    drawCircle(colors.joint, dot, knee)
    drawCircle(colors.joint, dot, ankle)
    drawCircle(colors.joint, dot, elbow)
    drawCircle(colors.joint, dot, wrist)
    drawLimb(Limb.Shin, stage.place(joints.knee), stage.place(joints.ankle), stage, colors.far, cutOut = true)
    drawLimb(Limb.Thigh, hip, stage.place(joints.knee), stage, colors.far, cutOut = true)
    drawLimb(Limb.Torso, hip, shoulder, stage, colors.far, cutOut = true)
    drawCircle(
        colors.far,
        BodyProportions.HEAD * stage.scale + stage.halo,
        stage.place(joints.headCenter),
        blendMode = BlendMode.Clear,
    )
}

/**
 * The figure with its [joints] where they are: the leg, the trunk and the head, then the arm as a piece of its own
 * with a gap cut round it, the bell likewise, the hand closed over the bell's handle, and a dot at each joint.
 */
private fun DrawScope.drawFigure(stage: DemoStage, joints: BodyJoints, colors: ExerciseDemoColors) {
    val ankle = stage.place(joints.ankle)
    val knee = stage.place(joints.knee)
    val hip = stage.place(joints.hip)
    val shoulder = stage.place(joints.shoulder)
    val elbow = stage.place(joints.elbow)
    val wrist = stage.place(joints.wrist)
    drawLine(
        colors.body,
        stage.place(Offset(-BodyProportions.HEEL, BodyProportions.SOLE)),
        stage.place(Offset(BodyProportions.TOE, BodyProportions.SOLE)),
        strokeWidth = BodyProportions.SOLE * 2 * stage.scale,
        cap = StrokeCap.Round,
    )
    drawLimb(Limb.Shin, knee, ankle, stage, colors.body)
    drawLimb(Limb.Thigh, hip, knee, stage, colors.body)
    drawLimb(Limb.Torso, hip, shoulder, stage, colors.body)
    drawCircle(colors.body, BodyProportions.HEAD * stage.scale, stage.place(joints.headCenter))
    drawLimb(Limb.UpperArm, shoulder, elbow, stage, colors.body, cutOut = true)
    drawLimb(Limb.Forearm, elbow, wrist, stage, colors.body, cutOut = true)
    drawLimb(Limb.UpperArm, shoulder, elbow, stage, colors.body)
    drawLimb(Limb.Forearm, elbow, wrist, stage, colors.body)
    drawKettlebell(stage, joints, colors.bell)
    drawLimb(Limb.Hand, wrist, stage.place(joints.kettlebellAnchor), stage, colors.body)
    val dot = BodyProportions.JOINT_REACH * JOINT_DOT * stage.scale
    drawCircle(colors.joint, dot, ankle)
    drawCircle(colors.joint, dot, knee)
    drawCircle(colors.joint, dot, hip)
    drawCircle(colors.joint, dot, shoulder)
    drawCircle(colors.joint, dot, elbow)
    drawCircle(colors.joint, dot, wrist)
}

/**
 * One limb [from] one joint [to] the next: straight sides between a round end at each, wider at the first. With
 * [cutOut] it draws nothing but clears a little more than its own shape from whatever is under it, the gap that
 * sets a limb drawn after apart from the body behind.
 */
private fun DrawScope.drawLimb(
    limb: Limb,
    from: Offset,
    to: Offset,
    stage: DemoStage,
    color: Color,
    cutOut: Boolean = false,
) {
    val grown = if (cutOut) stage.halo else 0f
    val blend = if (cutOut) BlendMode.Clear else BlendMode.SrcOver
    val start = limb.from * stage.scale / 2 + grown
    val end = limb.to * stage.scale / 2 + grown
    val along = to - from
    val length = along.getDistance()
    if (length > 0f) {
        val across = Offset(-along.y, along.x) / length
        val sides = stage.scratch
        sides.rewind()
        sides.moveTo(from.x + across.x * start, from.y + across.y * start)
        sides.lineTo(to.x + across.x * end, to.y + across.y * end)
        sides.lineTo(to.x - across.x * end, to.y - across.y * end)
        sides.lineTo(from.x - across.x * start, from.y - across.y * start)
        sides.close()
        drawPath(sides, color, blendMode = blend)
    }
    drawCircle(color, start, from, blendMode = blend)
    drawCircle(color, end, to, blendMode = blend)
}

/**
 * The bell as two plain shapes: the handle, a loop from the grip down to the body's shoulders, and the body, a
 * disc with a flat base, hanging from the hand the way its pose turns it. A gap is cut round it first, so it
 * stands off the arm and the chest it rests on, and a hole at its centre marks the point its path follows.
 */
private fun DrawScope.drawKettlebell(stage: DemoStage, joints: BodyJoints, color: Color) {
    val grip = stage.place(joints.kettlebellAnchor)
    val centre = stage.place(joints.kettlebellCenter)
    val hang = (centre - grip) / (centre - grip).getDistance()
    val half = Offset(-hang.y, hang.x) * (BodyProportions.BELL_GRIP * stage.scale)
    val radius = BodyProportions.BELL_RADIUS * stage.scale
    val seat = centre - hang * (radius * HORN_SEAT)
    val handle = stage.scratch
    handle.rewind()
    handle.moveTo(seat.x - half.x, seat.y - half.y)
    handle.lineTo(grip.x - half.x, grip.y - half.y)
    handle.lineTo(grip.x + half.x, grip.y + half.y)
    handle.lineTo(seat.x + half.x, seat.y + half.y)
    val base = Angles.degrees(atan2(hang.y, hang.x))
    val body = stage.bellBody
    body.rewind()
    body.arcTo(Rect(centre, radius), base + BASE_HALF_DEGREES, Angles.FULL_TURN - BASE_HALF_DEGREES * 2, true)
    body.close()
    drawPath(handle, color, style = stage.handleHalo, blendMode = BlendMode.Clear)
    drawCircle(color, radius + stage.halo, centre, blendMode = BlendMode.Clear)
    drawPath(handle, color, style = stage.handleStyle)
    drawPath(body, color)
    drawCircle(color, radius * BELL_MARK, centre, blendMode = BlendMode.Clear)
}

private const val MILLIS_PER_SECOND = 1000f

/** How many moments of a rep the bell's path is drawn through. */
private const val SAMPLES = 120

/** The room left round the fitted rep, as a share of the canvas's shorter side. */
private const val FRAME_PAD = 0.06f

/** The dashed path and the far end of the trail: the bell's colour, faint. */
private const val PATH_ALPHA = 0.4f

/** A stretch of path this many gaps' widths on along it is no longer the one the bell has just left. */
private const val APART = 6f

/** The trail is this share of the whole path long. */
private const val TRAIL_SHARE = 0.16f

/** A stance's ring on the path, in gaps' widths; a joint's dot, as a share of the room round a joint. */
private const val KEY_MARK = 2.5f
private const val JOINT_DOT = 0.17f

/** The handle's stroke, as a share of its height, and how far down the body's shoulders its ends are seated. */
private const val HANDLE_STROKE = 0.42f
private const val HORN_SEAT = 0.7f

/** Half the angle the bell's flat base takes out of its disc, and the hole at its centre as a share of it. */
private const val BASE_HALF_DEGREES = 32f
private const val BELL_MARK = 0.16f

/** A moment of a rep for a preview: what to call it, and how far through the rep it is. */
private class DemoPhase(val caption: String, val rep: ExerciseRep, val at: Float)

/** A rep held at each of [phases]. */
@Composable
private fun DemoPhases(vararg phases: DemoPhase) {
    IntervalTheme {
        val palette = IntervalTheme.palette
        Box(Modifier.fillMaxSize().background(palette.background), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = Sizes.content).padding(Spacing.space16),
                verticalArrangement = Arrangement.spacedBy(Spacing.space12),
            ) {
                phases.forEach { phase ->
                    Column(
                        Modifier.fillMaxWidth().clip(Corners.radius18).background(palette.surface)
                            .padding(Spacing.space12),
                    ) {
                        Text(phase.caption, style = IntervalTheme.type.caption, color = palette.textDim)
                        ExerciseDemoCanvas(
                            phase.rep,
                            Modifier.fillMaxWidth().height(Sizes.figurePage),
                            progress = phase.at,
                        )
                    }
                }
            }
        }
    }
}

@PreviewFormFactors
@Composable
private fun ExerciseDemoBottomHingePreview() {
    DemoPhases(
        DemoPhase("Swing: the hike pass, hips back and shins upright", KettlebellReps.Swing, at = 0f),
        DemoPhase("Goblet squat: the bottom, hips down between the heels", KettlebellReps.GobletSquat, at = 0.4f),
    )
}

@PreviewFormFactors
@Composable
private fun ExerciseDemoRackFloatPreview() {
    DemoPhases(
        DemoPhase("Clean: the rack", KettlebellReps.Clean, at = KettlebellReps.Clean.still),
        DemoPhase("Swing: the float", KettlebellReps.Swing, at = KettlebellReps.Swing.still),
    )
}

@PreviewFormFactors
@Composable
private fun ExerciseDemoLockoutPreview() {
    DemoPhases(
        DemoPhase("Snatch: the lockout", KettlebellReps.Snatch, at = KettlebellReps.Snatch.still),
        DemoPhase("Half-kneeling press: on the way up", KettlebellReps.HalfKneelingPress, at = 0.12f),
    )
}
