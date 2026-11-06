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
    /** The way from this pose to [to], [share] of it along. */
    fun towards(to: Pose, share: Float): Pose = Pose(
        shin = shin + (to.shin - shin) * share,
        thigh = thigh + (to.thigh - thigh) * share,
        torso = torso + (to.torso - torso) * share,
        upperArm = upperArm + (to.upperArm - upperArm) * share,
        forearm = forearm + (to.forearm - forearm) * share,
    )
}

/** Where the bell sits on the hands: [Swung] on straight arms, out past the grip; [AtChest] by the horns, below. */
enum class BellHold { Swung, AtChest }

/** What a stretch of the rep is, which the bell's colour says: setting up, driving the bell, or easing it back. */
enum class DemoBeat { Set, Drive, Ease }

/** A stretch of the rep up to [until] (a share of it), which the exercise's [cue]th cue speaks to. */
data class DemoPhase(val until: Float, val beat: DemoBeat, val cue: Int)

/** The figure's [pose] [at] this share of the rep. */
data class Keyframe(val at: Float, val pose: Pose)

/**
 * One exercise drawn as a figure doing it: [keyframes] from 0 to 1, eased between, the last the same as the
 * first so the rep loops; [phases] to 1, each naming one of the exercise's cues. A rep takes [secondsPerRep];
 * a still picture of it (reduced motion, or a list) shows it at [still].
 */
class ExerciseDemo(
    val keyframes: List<Keyframe>,
    val phases: List<DemoPhase>,
    val hold: BellHold,
    val secondsPerRep: Float,
    val still: Float,
) {
    init {
        require(keyframes.first().at == 0f && keyframes.last().at == 1f) { "A rep runs from 0 to 1" }
        require(keyframes.zipWithNext().all { (a, b) -> a.at <= b.at }) { "Keyframes out of order" }
        require(phases.last().until >= 1f) { "Phases stop short of the rep's end" }
    }

    /** The pose [progress] (0 to 1) through the rep, eased in and out between keyframes. */
    fun poseAt(progress: Float): Pose {
        val t = progress.coerceIn(0f, 1f)
        val next = keyframes.indexOfFirst { it.at >= t }.coerceAtLeast(1)
        val from = keyframes[next - 1]
        val to = keyframes[next]
        val span = to.at - from.at
        val share = if (span == 0f) 1f else ((t - from.at) / span).coerceIn(0f, 1f)
        return from.pose.towards(to.pose, smoothStep(share))
    }

    /** The phase [progress] (0 to 1) is in. */
    fun phaseAt(progress: Float): DemoPhase = phases.first { progress < it.until || it === phases.last() }

    /** The figure at [progress]. */
    fun figureAt(progress: Float): Figure = poseAt(progress).figure(hold)
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

    /** A swung bell's middle, out past the grip along the forearm. */
    const val SWUNG_BELL = 24f

    /** A bell held at the chest: its middle forward of and below the hands. */
    const val CHEST_BELL_FORWARD = 6f
    const val CHEST_BELL_DOWN = 16f

    const val ANKLE_X = 205f
    const val FLOOR = 360f

    /** The far leg, a step behind the near one. */
    const val FAR_LEG_OFFSET = 9f

    const val LEG = 14f
    const val FAR_LEG = 13f
    const val TRUNK = 17f
    const val ARM = 11f
    const val HEAD = 17f
    const val BELL = 17f
    const val CHEST_BELL = 19f
    const val GRIP = 9f
    const val GRIP_STROKE = 4f
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

/** Where this pose puts each joint, holding the bell as [hold] says. */
fun Pose.figure(hold: BellHold): Figure {
    val ankle = Offset(Rig.ANKLE_X, Rig.FLOOR)
    val knee = ankle.up(Rig.SHIN, shin)
    val hip = knee.up(Rig.THIGH, thigh)
    val shoulder = hip.up(Rig.TORSO, torso)
    val elbow = shoulder.down(Rig.UPPER_ARM, upperArm)
    val hand = elbow.down(Rig.FOREARM, forearm)
    val bell = when (hold) {
        BellHold.Swung -> hand.down(Rig.SWUNG_BELL, forearm)
        BellHold.AtChest -> hand + Offset(Rig.CHEST_BELL_FORWARD, Rig.CHEST_BELL_DOWN)
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

private fun smoothStep(share: Float): Float = share * share * (THREE - 2 * share)

private const val HALF_TURN = 180.0
private const val THREE = 3f
