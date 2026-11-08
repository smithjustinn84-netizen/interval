package dev.justinsmith.lift.ui.demo

import dev.justinsmith.lift.ui.demo.KettlebellPoses.HORNS
import dev.justinsmith.lift.ui.demo.KettlebellPoses.RESTING
import kotlin.math.roundToInt

/**
 * What a figure does as a record lands on its card: with something in hand it raises it overhead ([RaisedBell],
 * a bar or a pair too), with nothing in hand it takes a short bow ([Bow]). A figure not on its feet (lying,
 * seated, hanging, on its hands) gives none ([ExerciseDemo.celebration]). The gesture is a one-rep demo of its own
 * from wherever the figure was ([ExerciseDemo.celebrating]), played in place of the rep and then dropped, so
 * nothing about it is stored.
 */
enum class Celebration {
    RaisedBell,
    Bow,
    ;

    companion object {
        /** The whole gesture, there, held and back. */
        const val MILLIS = 1600
        const val SECONDS = MILLIS / 1000f
    }
}

/**
 * The celebration this demo's figure gives, by what it holds, or null when it is never on its feet in the rep:
 * hung from a bar or propped on its hands, at no keyframe standing (its hips well above its knee and both knees
 * well off the floor: not lying, seated or kneeling), or lying down at one (the get-up: its view holds it lying,
 * not stood up). A deadlift starts at the floor but stands at its top, so it celebrates: it stands up to do it.
 */
val ExerciseDemo.celebration: Celebration?
    get() {
        if (root == Root.Hand) return null
        val figures = keyframes.map { it.pose.figure(root, anchor) }
        if (figures.none { it.standing } || figures.any { it.lying }) return null
        return if (hold == Hold.Free) Celebration.Bow else Celebration.RaisedBell
    }

/** On its feet: the hips well above the near knee (the far one may be raised in a step), both knees off the floor. */
private val Figure.standing: Boolean
    get() = hip.y <= knee.y - Rig.THIGH / 2 && knee.y < Rig.FLOOR - Rig.SHIN / 2 && farKnee.y < Rig.FLOOR - Rig.SHIN / 2

/** The hips down on the floor. */
private val Figure.lying: Boolean
    get() = hip.y > Rig.FLOOR - Rig.TRUNK

/**
 * This demo's figure giving its [celebration] from where it is [at] (a share of the rep, or setting up below 0):
 * a one-rep demo [Celebration.SECONDS] long that moves to the gesture, holds it, and comes back to the pose it left,
 * its bells starting where the rep had them hang. Null for a figure that gives none.
 *
 * The figure stands up first and brings its far foot in last, flat, once its hips are at their full height (and
 * steps it back out first on the way down): a leg that straightened while the hips were still low would put that
 * foot through the floor.
 */
fun ExerciseDemo.celebrating(at: Float): ExerciseDemo? {
    val celebration = celebration ?: return null
    val pose = poseAt(at)
    // The rep draws a swung bell where it was a moment before; the gesture starts from there, not from the arm.
    val hanging = if (bellLag == 0f || at < 0f) pose else poseAt(looped(at - bellLag))
    val from = pose.copy(bell = hanging.bell, farBell = hanging.farBell)
    val gesture = when (celebration) {
        Celebration.RaisedBell -> from.raising(hold)
        Celebration.Bow -> from.bowing()
    }
    val farFootOut = gesture.copy(farShin = from.farShin, farThigh = from.farThigh)
    return ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = from),
            Keyframe(at = STOOD_AT, pose = farFootOut),
            Keyframe(at = GESTURE_AT, pose = gesture),
            Keyframe(at = 1f - GESTURE_AT, pose = gesture),
            Keyframe(at = 1f - STOOD_AT, pose = farFootOut),
            Keyframe(at = 1f, pose = from),
        ),
        phases = listOf(DemoPhase(until = 1f, beat = DemoBeat.Drive, cue = 0)),
        hold = hold,
        secondsPerRep = Celebration.SECONDS,
        still = GESTURE_AT,
        root = root,
        view = view,
        props = props,
        anchor = anchor,
    )
}

/**
 * Standing tall with what is in hand locked out overhead: the bell resting on the forearm (by the horns with two
 * hands on it), the bar a little behind the head. Each angle takes the whole turn nearest where it is, so a bell
 * that went over the hand doesn't spin back. The far arm goes up with a pair, two hands or a bar, and hangs still
 * beside one bell.
 */
private fun Pose.raising(hold: Hold): Pose {
    val arm = if (hold == Hold.Bar) BAR_OVERHEAD else OVERHEAD
    val resting = if (hold == Hold.BothHands) HORNS else RESTING
    val tall = standingAs(KettlebellPoses.stand.copy(torso = TALL_TORSO))
        .copy(
            upperArm = upperArm.nearestTurn(arm),
            forearm = forearm.nearestTurn(arm),
            bell = bell.nearestTurn(resting),
            foot = 0f,
            reach = 1f,
        )
    return if (hold == Hold.NearHand) {
        tall.stillHand()
    } else {
        tall.copy(
            farUpperArm = farUpperArm.nearestTurn(arm),
            farForearm = farForearm.nearestTurn(arm),
            farBell = farBell.nearestTurn(resting),
        )
    }
}

/** A short bow: the legs straight, the torso tipped forward from the hips, both arms hanging a little ahead. */
private fun Pose.bowing(): Pose = standingAs(BOW).copy(
    upperArm = upperArm.nearestTurn(BOW.upperArm),
    forearm = forearm.nearestTurn(BOW.forearm),
    farUpperArm = farUpperArm.nearestTurn(BOW.upperArm),
    farForearm = farForearm.nearestTurn(BOW.forearm),
    foot = 0f,
    reach = 1f,
)

/** [to] moved by whole turns to the one nearest this angle: a joint gets there the short way round. */
internal fun Float.nearestTurn(to: Float): Float = to + ((this - to) / FULL_TURN).roundToInt() * FULL_TURN

/** Where the figure stands with its far foot still out, then where the gesture is reached; the same from the end. */
private const val STOOD_AT = 0.2f
private const val GESTURE_AT = 0.3f

/** Standing tall under a load, leaning back a touch, as the rack and the lockout do. */
private const val TALL_TORSO = -2f
private const val OVERHEAD = 178f
private const val BAR_OVERHEAD = 182f
private val BOW = Pose(shin = 0f, thigh = 0f, torso = 38f, upperArm = 8f, forearm = 12f)
