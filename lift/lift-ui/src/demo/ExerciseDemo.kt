package dev.justinsmith.lift.ui.demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.Exercise
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A figure's stance at one moment of a rep, as the angles of its joints in degrees, seen from the side with
 * the figure facing right. [shin], [thigh] and [torso] lean from straight up; [upperArm] and [forearm] hang
 * from straight down. Positive is toward the way the figure faces, so a hinge is a negative [thigh] (the
 * hips back) under a positive [torso]. A straight arm has the same [upperArm] and [forearm]; a forearm
 * folded up past the horizontal is over 90.
 */
data class Pose(val shin: Float, val thigh: Float, val torso: Float, val upperArm: Float, val forearm: Float) {
    internal fun angles(): FloatArray = floatArrayOf(shin, thigh, torso, upperArm, forearm)

    internal companion object {
        const val JOINTS = 5

        /** The pose whose joints are [angles], in the order [Pose.angles] gives them. */
        fun of(angles: FloatArray): Pose {
            val joint = angles.iterator()
            return Pose(joint.next(), joint.next(), joint.next(), joint.next(), joint.next())
        }
    }
}

/** How the bell is held: [Swung] on straight arms, hanging past the grip; [AtChest] by the horns, under the hands. */
enum class BellHold { Swung, AtChest }

/** What a stretch of the rep is, which the bell's colour says: setting up, driving the bell, or easing it back. */
enum class DemoBeat { Set, Drive, Ease }

/** A stretch of the rep up to [until] (a share of it), which the exercise's [cue]th cue speaks to. */
data class DemoPhase(val until: Float, val beat: DemoBeat, val cue: Int)

/** The figure's [pose] [at] this share of the rep. */
data class Keyframe(val at: Float, val pose: Pose)

/**
 * One exercise drawn as a figure doing it: [keyframes] from 0 to 1, the last the same as the first so the rep
 * loops; [phases] to 1, each naming one of the exercise's cues. A rep takes [secondsPerRep]; a still picture
 * of it (reduced motion, or a list) shows it at [still].
 *
 * Each joint follows a smooth curve through its keyframes (a monotone cubic, which the loop closes): it keeps
 * its speed through a pose it only passes, comes to rest where it turns round, stays put between two
 * keyframes that hold one pose, and never swings past the poses it moves between.
 *
 * A swung bell hangs from the hands rather than standing on them: it points where the arms did [bellLag] of
 * a rep ago, so it trails them up and floats on after them. It leaves a trail [trail] of a rep long (none
 * for 0), whose spacing shows its speed.
 */
class ExerciseDemo(
    val keyframes: List<Keyframe>,
    val phases: List<DemoPhase>,
    val hold: BellHold,
    val secondsPerRep: Float,
    val still: Float,
    val bellLag: Float = 0f,
    val trail: Float = 0f,
) {
    init {
        require(keyframes.first().at == 0f && keyframes.last().at == 1f) { "A rep runs from 0 to 1" }
        require(keyframes.first().pose == keyframes.last().pose) { "A rep ends as it starts" }
        require(keyframes.zipWithNext().all { (a, b) -> a.at < b.at }) { "Keyframes out of order" }
        require(phases.last().until >= 1f) { "Phases stop short of the rep's end" }
    }

    private val times = FloatArray(keyframes.size) { keyframes[it].at }
    private val angles = keyframes.map { it.pose.angles() }

    /** Each joint's speed at each keyframe, in degrees a rep. */
    private val speeds = keyframes.indices.map { key ->
        // The rep loops, so its first and last keyframes are one moment: the stretch before it is the rep's last.
        val before = if (key == 0) keyframes.lastIndex - 1 else key - 1
        val after = if (key == keyframes.lastIndex) 0 else key
        FloatArray(Pose.JOINTS) { joint ->
            speedThrough(slope(before, joint), span(before), slope(after, joint), span(after))
        }
    }

    /** The pose [progress] (0 to 1) through the rep. */
    fun poseAt(progress: Float): Pose {
        val t = progress.coerceIn(0f, 1f)
        val to = times.indexOfFirst { it >= t }.coerceAtLeast(1)
        val from = to - 1
        val span = span(from)
        val share = (t - times[from]) / span
        return Pose.of(
            FloatArray(Pose.JOINTS) { joint ->
                hermite(
                    from = angles[from][joint],
                    leaving = speeds[from][joint] * span,
                    to = angles[to][joint],
                    arriving = speeds[to][joint] * span,
                    share = share,
                )
            },
        )
    }

    /** The phase [progress] (0 to 1) is in. */
    fun phaseAt(progress: Float): DemoPhase = phases.first { progress < it.until || it === phases.last() }

    /** The figure at [progress], its bell hanging where the arms were a moment before. */
    fun figureAt(progress: Float): Figure {
        val pose = poseAt(progress)
        val hangs = if (bellLag == 0f) pose.forearm else poseAt(looped(progress - bellLag)).forearm
        return pose.figure(hold, hangs)
    }

    /** Where the bell was over the last [trail] of a rep before [progress], oldest first; empty with no trail. */
    fun trailAt(progress: Float): List<Offset> {
        if (trail == 0f) return emptyList()
        return (TRAIL_STEPS downTo 1).map { step -> figureAt(looped(progress - trail * step / TRAIL_STEPS)).bell }
    }

    private fun span(stretch: Int): Float = times[stretch + 1] - times[stretch]

    private fun slope(stretch: Int, joint: Int): Float =
        (angles[stretch + 1][joint] - angles[stretch][joint]) / span(stretch)

    companion object {
        /** How many places a trail remembers. */
        const val TRAIL_STEPS = 9
    }
}

/** The figure's joints in [Rig] units, for drawing. */
data class Figure(
    val toe: Offset,
    val heel: Offset,
    val ankle: Offset,
    val knee: Offset,
    val hip: Offset,
    val shoulder: Offset,
    val head: Offset,
    val elbow: Offset,
    val hand: Offset,
    val bell: Offset,
)

/**
 * The figure's proportions, in the units of a [view] it is drawn in: the ankle on the floor at [ANKLE_X],
 * [FLOOR], the rest stacked on it.
 */
object Rig {
    const val SHIN = 72f
    const val THIGH = 78f
    const val TORSO = 112f
    const val NECK = 32f

    /** The head tips forward of the torso by this much. */
    const val HEAD_TILT = 8f
    const val UPPER_ARM = 50f
    const val FOREARM = 50f
    const val FOOT = 30f
    const val HEEL = 6f

    /** From the hands on the bell's handle to the middle of its body. */
    const val BELL_REACH = 26f

    /** A bell held at the chest hangs this far forward of straight down, clear of the ribs. */
    const val CHEST_BELL_ANGLE = 12f

    const val ANKLE_X = 205f
    const val FLOOR = 360f

    /** The far leg, a step behind the near one. */
    const val FAR_LEG_OFFSET = 9f

    const val LEG = 14f
    const val FAR_LEG = 13f
    const val TRUNK = 17f
    const val ARM = 11f
    const val HEAD = 17f
    const val HAND = 7.5f

    /** The bell's body, and its handle: half its width, how far its sides run down to the body, its stroke. */
    const val BELL = 17f
    const val HANDLE_HALF = 9f
    const val HANDLE_SIDES = 12f
    const val HANDLE = 5f

    /** A trail's dots, from the oldest to the one just behind the bell. */
    const val TRAIL_DOT_OLDEST = 2f
    const val TRAIL_DOT_NEWEST = 9f

    const val FLOOR_LINE = 1.5f
    const val SHADOW_WIDTH = 140f
    const val SHADOW_HEIGHT = 12f

    /** The shadow's middle, just under the floor line. */
    const val SHADOW_DROP = 4f

    /** The floor reaches this far either side of the ankle. */
    const val FLOOR_REACH = 121f
    const val FLOOR_REACH_FORWARD = 171f

    /** The box every pose of every demo fits in, with room for the bell at the top of a swing. */
    val view = Rect(left = 70f, top = 30f, right = 390f, bottom = 380f)
}

/**
 * Where this pose puts each joint, holding the bell as [hold] says. A swung bell hangs from the hands at
 * [hangs] from straight down, which is along the arms unless the demo says it lags them.
 */
fun Pose.figure(hold: BellHold, hangs: Float = forearm): Figure {
    val ankle = Offset(Rig.ANKLE_X, Rig.FLOOR)
    val knee = ankle.up(Rig.SHIN, shin)
    val hip = knee.up(Rig.THIGH, thigh)
    val shoulder = hip.up(Rig.TORSO, torso)
    val elbow = shoulder.down(Rig.UPPER_ARM, upperArm)
    val hand = elbow.down(Rig.FOREARM, forearm)
    val bell = when (hold) {
        BellHold.Swung -> hand.down(Rig.BELL_REACH, hangs)
        BellHold.AtChest -> hand.down(Rig.BELL_REACH, Rig.CHEST_BELL_ANGLE)
    }
    return Figure(
        toe = ankle + Offset(Rig.FOOT, 0f),
        heel = ankle - Offset(Rig.HEEL, 0f),
        ankle = ankle,
        knee = knee,
        hip = hip,
        shoulder = shoulder,
        head = shoulder.up(Rig.NECK, torso - Rig.HEAD_TILT),
        elbow = elbow,
        hand = hand,
        bell = bell,
    )
}

/** The demo drawn for this exercise, or null for one not drawn yet. */
val Exercise.demo: ExerciseDemo?
    get() = when (this) {
        BuiltInExercise.TwoHandSwing -> TwoHandSwingDemo
        BuiltInExercise.GobletSquat -> GobletSquatDemo
        else -> null
    }

private fun Offset.up(length: Float, degrees: Float): Offset {
    val angle = degrees.radians()
    return Offset(x + length * sin(angle), y - length * cos(angle))
}

private fun Offset.down(length: Float, degrees: Float): Offset {
    val angle = degrees.radians()
    return Offset(x + length * sin(angle), y + length * cos(angle))
}

private fun Float.radians(): Float = (this * PI / HALF_TURN).toFloat()

/** [progress] brought into a rep, so a moment before its start is one near the end of the rep before. */
private fun looped(progress: Float): Float = ((progress % 1f) + 1f) % 1f

/**
 * A joint's speed at a keyframe it reaches at [slopeIn] over [spanIn] and leaves at [slopeOut] over [spanOut]:
 * none where it turns round or rests on either side, else between the two, leaning to the shorter stretch
 * (Fritsch and Carlson's weighted harmonic mean, which is what keeps the curve from overshooting).
 */
private fun speedThrough(slopeIn: Float, spanIn: Float, slopeOut: Float, spanOut: Float): Float {
    if (slopeIn * slopeOut <= 0f) return 0f
    val weightIn = 2 * spanOut + spanIn
    val weightOut = spanOut + 2 * spanIn
    return (weightIn + weightOut) / (weightIn / slopeIn + weightOut / slopeOut)
}

/** The cubic from [from] to [to] that sets off at [leaving] and comes in at [arriving], [share] of the way along. */
private fun hermite(from: Float, leaving: Float, to: Float, arriving: Float, share: Float): Float {
    val squared = share * share
    val cubed = squared * share
    return (2 * cubed - THREE * squared + 1) * from +
        (cubed - 2 * squared + share) * leaving +
        (THREE * squared - 2 * cubed) * to +
        (cubed - squared) * arriving
}

private const val HALF_TURN = 180.0
private const val THREE = 3f
