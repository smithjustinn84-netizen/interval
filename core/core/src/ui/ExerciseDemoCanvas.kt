package dev.justinsmith.interval.ui

import androidx.compose.animation.core.Easing
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
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin

// A lift shown as a diagram: a figure in profile worked from its joints' angles, the floor and a plumb line
// to read it against, and the path the bell takes through the rep. Everything is measured in shares of the
// figure's standing height and scaled to the canvas, so nothing here is a size of its own.

/**
 * How long each part of the figure is, as a share of its standing height: the floor is 0 and the top of the head
 * 1. The lengths are fixed, so a pose can only turn the joints between them, never stretch a limb.
 */
internal object BodyProportions {
    const val ANKLE = 0.04f
    const val SHIN = 0.24f
    const val THIGH = 0.245f
    const val TORSO = 0.29f

    /** From the shoulder to the head's centre, and the head's radius. */
    const val NECK = 0.115f
    const val HEAD = 0.07f
    const val UPPER_ARM = 0.17f
    const val FOREARM = 0.15f

    /** From the wrist to the middle of the grip. */
    const val HAND = 0.045f

    /** The bell: from the grip to the top of its body, the body's radius, and half the handle's width. */
    const val BELL_HANDLE = 0.052f
    const val BELL_RADIUS = 0.068f
    const val BELL_GRIP = 0.044f

    /** The foot along the floor, behind and ahead of the ankle, and how high its middle is. */
    const val HEEL = 0.035f
    const val TOE = 0.12f
    const val SOLE = 0.02f

    /** The room a joint's limbs take round it, when the figure is fitted to its canvas. */
    const val JOINT_REACH = 0.06f
}

/**
 * Where a [BodyPose] puts each joint, in shares of the figure's standing height: x is ahead of the ankle, the way
 * the figure faces, and y is up from the floor. [kettlebellAnchor] is the middle of the grip, where the handle
 * sits in the hand; [kettlebellCenter] the middle of the bell's body, which is what its path follows.
 */
@Immutable
data class BodyJoints(
    val ankle: Offset,
    val knee: Offset,
    val hip: Offset,
    val shoulder: Offset,
    val elbow: Offset,
    val wrist: Offset,
    val headCenter: Offset,
    val kettlebellAnchor: Offset,
    val kettlebellCenter: Offset,
) {
    /** Each point the figure reaches to with the room it takes there, for fitting it to a canvas. */
    internal inline fun forEachReach(reach: (point: Offset, room: Float) -> Unit) {
        reach(knee, BodyProportions.JOINT_REACH)
        reach(hip, BodyProportions.JOINT_REACH)
        reach(shoulder, BodyProportions.JOINT_REACH)
        reach(elbow, BodyProportions.JOINT_REACH)
        reach(kettlebellAnchor, BodyProportions.JOINT_REACH)
        reach(headCenter, BodyProportions.HEAD)
        reach(kettlebellCenter, BodyProportions.BELL_RADIUS)
    }
}

/**
 * A lifter's stance at one moment of a rep, seen from the side and facing right, as the angles of its joints in
 * degrees. Each is the joint's own bend, so standing tall with the arm hanging is every angle at zero:
 *
 * - [torsoAngleDeg]: how far the trunk leans forward from upright.
 * - [hipAngleDeg]: how far the hip is folded, the thigh from the trunk's line. A hinge is a big fold under a
 *   trunk that leans, which sends the hips back.
 * - [kneeAngleDeg]: how far the knee is bent, the shin from the thigh's line.
 * - [shoulderAngleDeg]: how far the arm is raised ahead, from hanging along the trunk to overhead in line with it
 *   (a half turn).
 * - [elbowAngleDeg]: how far the elbow is bent, the forearm folding up toward the shoulder.
 * - [bellAngleDeg]: how far the bell is turned in the hand from the forearm's line: none while it is swung, most of
 *   a half turn back once it rests on the forearm in the rack or locked out.
 *
 * The joints are worked out from these by [joints], along limbs of fixed length. So two poses are blended by their
 * angles ([blend]), never by where their joints are, and a limb keeps its length all the way between them.
 */
@Immutable
data class BodyPose(
    val hipAngleDeg: Float = 0f,
    val kneeAngleDeg: Float = 0f,
    val torsoAngleDeg: Float = 0f,
    val shoulderAngleDeg: Float = 0f,
    val elbowAngleDeg: Float = 0f,
    val bellAngleDeg: Float = 0f,
) {
    /** Where the joints are: up the leg from the ankle, which never leaves its place, then out along the arm. */
    fun joints(): BodyJoints {
        val thighLean = torsoAngleDeg - hipAngleDeg
        val armSwing = shoulderAngleDeg - torsoAngleDeg
        val forearmSwing = armSwing + elbowAngleDeg
        val ankle = Offset(0f, BodyProportions.ANKLE)
        val knee = ankle + rising(thighLean + kneeAngleDeg) * BodyProportions.SHIN
        val hip = knee + rising(thighLean) * BodyProportions.THIGH
        val shoulder = hip + rising(torsoAngleDeg) * BodyProportions.TORSO
        val elbow = shoulder + hanging(armSwing) * BodyProportions.UPPER_ARM
        val wrist = elbow + hanging(forearmSwing) * BodyProportions.FOREARM
        val grip = wrist + hanging(forearmSwing) * BodyProportions.HAND
        val bell = hanging(forearmSwing + bellAngleDeg) * (BodyProportions.BELL_HANDLE + BodyProportions.BELL_RADIUS)
        return BodyJoints(
            ankle = ankle,
            knee = knee,
            hip = hip,
            shoulder = shoulder,
            elbow = elbow,
            wrist = wrist,
            headCenter = shoulder + rising(torsoAngleDeg) * BodyProportions.NECK,
            kettlebellAnchor = grip,
            kettlebellCenter = grip + bell,
        )
    }

    /** This pose [share] of the way to [to], from 0 to 1, every joint turning at once. */
    fun blend(to: BodyPose, share: Float): BodyPose {
        val from = angles()
        val toward = to.angles()
        return of(FloatArray(JOINTS) { from[it] + (toward[it] - from[it]) * share })
    }

    internal fun angles(): FloatArray =
        floatArrayOf(hipAngleDeg, kneeAngleDeg, torsoAngleDeg, shoulderAngleDeg, elbowAngleDeg, bellAngleDeg)

    internal companion object {
        const val JOINTS = 6

        /** The pose whose joints are [angles], in the order [BodyPose.angles] gives them. */
        fun of(angles: FloatArray): BodyPose {
            @Suppress("detekt.MagicNumber") // Each joint's place in the array.
            return BodyPose(
                hipAngleDeg = angles[0],
                kneeAngleDeg = angles[1],
                torsoAngleDeg = angles[2],
                shoulderAngleDeg = angles[3],
                elbowAngleDeg = angles[4],
                bellAngleDeg = angles[5],
            )
        }

        /** The way a part standing on its joint points, leaning [leanDeg] forward from straight up. */
        private fun rising(leanDeg: Float): Offset = Angles.radians(leanDeg).let { Offset(sin(it), cos(it)) }

        /** The way a part hanging from its joint points, swung [swingDeg] forward from straight down. */
        private fun hanging(swingDeg: Float): Offset = Angles.radians(swingDeg).let { Offset(sin(it), -cos(it)) }
    }
}

/** The stances a kettlebell lift rests in or passes through, which a rep is put together from. */
object KettlebellPoses {
    /** The bottom of the hinge: hips back, shins upright, the bell swung back between the legs. */
    val Hinge = BodyPose(hipAngleDeg = 82f, kneeAngleDeg = 25f, torsoAngleDeg = 60f, shoulderAngleDeg = 10f)

    /** The hips through: standing tall already, the arm only now leaving the body. A swing passes through it. */
    val Drive = BodyPose(shoulderAngleDeg = 24f)

    /** The top of a swing: standing tall, the bell floating at the end of a long arm. */
    val Floating = BodyPose(shoulderAngleDeg = 84f, elbowAngleDeg = 6f)

    /**
     * The pull of a clean: the elbow drawn back past the ribs and half bent, so the bell comes up close to the
     * body rather than swinging out round the elbow. A clean passes through it.
     */
    val Pull = BodyPose(shoulderAngleDeg = -25f, elbowAngleDeg = 75f, bellAngleDeg = -50f)

    /** The rack: the elbow tucked, the fist under the chin, the bell resting on the forearm. */
    val Rack = BodyPose(shoulderAngleDeg = 8f, elbowAngleDeg = 152f, bellAngleDeg = -160f)

    /**
     * Halfway up a press: the elbow as high as the shoulder and the forearm tipped back over it, so the bell goes
     * up past the face, over the feet, rather than out ahead of them. A press passes through it.
     */
    val Press = BodyPose(shoulderAngleDeg = 100f, elbowAngleDeg = 110f, bellAngleDeg = -225f)

    /** Locked out overhead: the arm straight up beside the ear, the bell lying on the back of the forearm. */
    val Lockout = BodyPose(shoulderAngleDeg = Angles.HALF_TURN, bellAngleDeg = -205f)
}

/**
 * One stance of a rep: the figure is in [pose] when the rep is [at] this share of the way through, and comes to
 * rest there unless it is [passing] through on its way to the next it rests in. [easing] is the pace of the whole
 * stretch into a stance from the one it last rested in, however many it passes through on the way; a stance
 * passed through has no pace of its own.
 */
@Immutable
data class PoseKey(
    val at: Float,
    val pose: BodyPose,
    val easing: Easing = StandardEasing,
    val passing: Boolean = false,
)

/**
 * A rep as the stances it goes through, in order, from the first at 0 round to the first again at 1, over
 * [seconds] when it runs by itself. [still] is where it stands when it mustn't move: its most telling moment.
 *
 * Between two stances it rests in, each joint's angle runs along a smooth curve through the stances passed on the
 * way, one that never swings past them: a joint turns no further than a stance says.
 */
@Immutable
data class ExerciseRep(val keys: List<PoseKey>, val seconds: Float, val still: Float = 0f) {
    init {
        require(keys.size >= 2 && keys.first().at == 0f && !keys.first().passing) {
            "A rep is two stances or more, the first a rest at 0"
        }
        require(keys.zipWithNext().all { (a, b) -> a.at < b.at } && keys.last().at < 1f) {
            "A rep's stances come in order, the last before 1"
        }
    }

    /** The rep cut at each stance it rests in, the last stretch coming round to the first stance again. */
    private val stretches: List<Stretch> = (keys + keys.first().copy(at = 1f)).let { round ->
        round.indices.filter { !round[it].passing }.zipWithNext { from, to -> Stretch(round.subList(from, to + 1)) }
    }

    /**
     * The stance [progress] of the way through the rep. A rep goes round, so 1 is 0 again and anything past
     * either end wraps.
     */
    fun poseAt(progress: Float): BodyPose {
        val at = progress - floor(progress)
        return stretches.last { it.from <= at }.poseAt(at)
    }

    companion object {
        /**
         * A swing: the hips drive out of the hinge, then the bell floats; it falls, then the hips fold. The way
         * down mirrors the way up, so the bell comes back along one arc.
         */
        val Swing = ExerciseRep(
            keys = listOf(
                PoseKey(at = 0f, pose = KettlebellPoses.Hinge),
                PoseKey(at = 0.17f, pose = KettlebellPoses.Drive, passing = true),
                PoseKey(at = 0.42f, pose = KettlebellPoses.Floating),
                PoseKey(at = 0.765f, pose = KettlebellPoses.Drive, passing = true),
            ),
            seconds = 1.8f,
            still = 0.42f,
        )

        /** A clean and press: from the hinge to the rack, pressed to the lockout, and back the way it came. */
        val CleanAndPress = ExerciseRep(
            keys = listOf(
                PoseKey(at = 0f, pose = KettlebellPoses.Hinge),
                PoseKey(at = 0.16f, pose = KettlebellPoses.Pull, passing = true),
                PoseKey(at = 0.26f, pose = KettlebellPoses.Rack),
                PoseKey(at = 0.39f, pose = KettlebellPoses.Press, passing = true),
                PoseKey(at = 0.52f, pose = KettlebellPoses.Lockout),
                PoseKey(at = 0.64f, pose = KettlebellPoses.Press, passing = true),
                PoseKey(at = 0.76f, pose = KettlebellPoses.Rack),
                PoseKey(at = 0.852f, pose = KettlebellPoses.Pull, passing = true),
            ),
            seconds = 4.2f,
            still = 0.52f,
        )
    }
}

/**
 * A rep from one stance it rests in to the next, through [keys]: the first and last are the rests, any between
 * are passed through. The last one's easing paces the whole of it.
 */
private class Stretch(keys: List<PoseKey>) {
    val from = keys.first().at
    private val to = keys.last().at
    private val easing = keys.last().easing
    private val times = FloatArray(keys.size) { keys[it].at }
    private val angles = keys.map { it.pose.angles() }

    /** How fast each joint is turning as it goes through each stance: by joint, then by stance. */
    private val slopes = List(BodyPose.JOINTS) { joint -> slopes(FloatArray(keys.size) { angles[it][joint] }) }

    fun poseAt(at: Float): BodyPose {
        val paced = from + easing.transform((at - from) / (to - from)).coerceIn(0f, 1f) * (to - from)
        val index = times.indexOfLast { it <= paced }.coerceIn(0, times.size - 2)
        val span = times[index + 1] - times[index]
        val share = (paced - times[index]) / span
        return BodyPose.of(
            FloatArray(BodyPose.JOINTS) { joint ->
                hermite(
                    from = angles[index][joint],
                    leaving = slopes[joint][index] * span,
                    to = angles[index + 1][joint],
                    arriving = slopes[joint][index + 1] * span,
                    share = share,
                )
            },
        )
    }

    /**
     * How fast a joint whose angle is [values] at each stance turns through each: the plain rate between two at
     * either end, and in the middle Steffen's choice, the most that still keeps the curve from swinging past a
     * stance (none at all where the joint turns back, or stays put on one side).
     */
    private fun slopes(values: FloatArray): FloatArray {
        val rates = FloatArray(values.size - 1) { (values[it + 1] - values[it]) / (times[it + 1] - times[it]) }
        return FloatArray(values.size) { index ->
            if (index == 0 || index == values.lastIndex) return@FloatArray rates[index.coerceAtMost(rates.lastIndex)]
            val before = rates[index - 1]
            val after = rates[index]
            val sinceLast = times[index] - times[index - 1]
            val untilNext = times[index + 1] - times[index]
            val mean = (before * untilNext + after * sinceLast) / (sinceLast + untilNext)
            (sign(before) + sign(after)) * minOf(abs(before), abs(after), abs(mean) / 2)
        }
    }

    /** The cubic [from] one value [to] the next that is [leaving] the first and [arriving] at the second so fast. */
    @Suppress("detekt.MagicNumber") // Hermite's four cubics, as they are written everywhere.
    private fun hermite(from: Float, leaving: Float, to: Float, arriving: Float, share: Float): Float {
        val squared = share * share
        val cubed = squared * share
        return (2 * cubed - 3 * squared + 1) * from + (cubed - 2 * squared + share) * leaving +
            (3 * squared - 2 * cubed) * to + (cubed - squared) * arriving
    }
}

/**
 * The colours a demonstration is drawn in: the [body], the dot at each of its joints, the [reference] lines it is
 * read against (the floor and the plumb line), and the [bell] with its path.
 */
@Immutable
data class ExerciseDemoColors(val body: Color, val joint: Color, val reference: Color, val bell: Color) {
    companion object {
        /**
         * [palette]'s: the body in the text's colour, the references in the outline's, quiet enough to sit behind
         * it, and the bell in the accent.
         */
        internal fun of(palette: IntervalPalette) = ExerciseDemoColors(
            body = palette.text,
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
 * [contentDescription] says what it shows, for a screen reader.
 */
@Composable
fun ExerciseDemoCanvas(
    rep: ExerciseRep,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    colors: ExerciseDemoColors = ExerciseDemoColors.theme(),
    contentDescription: String? = null,
) {
    val held by rememberUpdatedState(progress?.coerceIn(0f, 1f))
    var inView by remember { mutableStateOf(true) }
    val loop = rememberLoopProgress(rep, progress, inView)
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
                    val at = held ?: loop.floatValue
                    drawReferences(stage, colors.reference)
                    drawTrajectory(stage, at, colors.bell)
                    drawFigure(stage, rep.poseAt(at).joints(), colors)
                }
            },
    )
}

/**
 * How far through [rep] it has run by itself. It stands where [held] has it while it is held, so it runs on from
 * there, stands still under reduced motion, and takes no frames while it is scrolled out of view ([inView]). The
 * frames come from an infinite animation, which a UI test cancels rather than waits on.
 */
@Composable
private fun rememberLoopProgress(rep: ExerciseRep, held: Float?, inView: Boolean): FloatState {
    val still = prefersReducedMotion()
    val progress = remember(rep) { mutableFloatStateOf(rep.still) }
    LaunchedEffect(rep, held, still, inView) {
        if (held != null) progress.floatValue = held.coerceIn(0f, 1f)
        if (held != null || still || !inView) return@LaunchedEffect
        val from = progress.floatValue
        val start = withInfiniteAnimationFrameMillis { it }
        while (true) {
            val elapsed = withInfiniteAnimationFrameMillis { it } - start
            progress.floatValue = (from + elapsed / (rep.seconds * MILLIS_PER_SECOND)).mod(1f)
        }
    }
    return progress
}

/** A limb's width at each end, as shares of the figure's height: wider where it joins the body. */
private enum class Limb(val from: Float, val to: Float) {
    Thigh(from = 0.098f, to = 0.068f),
    Shin(from = 0.062f, to = 0.044f),
    Torso(from = 0.1f, to = 0.112f),
    UpperArm(from = 0.06f, to = 0.05f),
    Forearm(from = 0.048f, to = 0.036f),
    Hand(from = 0.036f, to = 0.046f),
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
        keyMarks = rep.keys.filterNot { it.passing }.map { place(it.pose.joints().kettlebellCenter) }
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
        DemoPhase("Clean and press: the bottom of the hinge", ExerciseRep.CleanAndPress, at = 0f),
        DemoPhase("Swing: out of the hinge, the hips ahead of the arm", ExerciseRep.Swing, at = 0.12f),
    )
}

@PreviewFormFactors
@Composable
private fun ExerciseDemoRackFloatPreview() {
    DemoPhases(
        DemoPhase("Clean and press: the rack", ExerciseRep.CleanAndPress, at = 0.26f),
        DemoPhase("Swing: the float", ExerciseRep.Swing, at = ExerciseRep.Swing.still),
    )
}

@PreviewFormFactors
@Composable
private fun ExerciseDemoLockoutPreview() {
    DemoPhases(
        DemoPhase("Clean and press: the lockout", ExerciseRep.CleanAndPress, at = ExerciseRep.CleanAndPress.still),
        DemoPhase("Clean and press: halfway up the press", ExerciseRep.CleanAndPress, at = 0.39f),
    )
}
