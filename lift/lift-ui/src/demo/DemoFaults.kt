package dev.justinsmith.lift.ui.demo

import dev.justinsmith.lift.domain.Fault

/**
 * This demo done wrong, as its [fault] makes it look, or null for a fault a figure can't show: squeezing the
 * handle shows no more than a fist does, nor a wrist bent round the handle of a bell hanging from one, and a hand
 * punched through a moment late is too quick to see. So of the faults a hinge is prone to only squatting it is drawn
 * ([Fault.SquattedHinge]): each pose the hips hinge in has the knees pushed forward, the hips lower and the chest
 * more upright, in step with how far it hinges, and the poses it stands tall in are as they were. Every other way
 * the demo is the same, so one player runs either and the rep keeps its place as the fault is shown or hidden.
 */
fun ExerciseDemo.doneWrong(fault: Fault): ExerciseDemo? = when (fault) {
    Fault.SquattedHinge -> withPoses { pose ->
        // As far squatted as keeps what it holds off the floor: a deadlift's bell sits on it, and hips taken lower
        // with the arms as they are would put it through.
        SQUAT_SHARES.map { pose.squatted(it) }.first { squatted ->
            val figure = squatted.figure(root, anchor)
            figure.bell.y + Rig.BELL <= Rig.FLOOR && figure.farBell.y + Rig.BELL <= Rig.FLOOR
        }
    }

    Fault.LateHand, Fault.DeathGrip, Fault.BentWrist -> null
}

/** This demo with each keyframe's pose made [into] another, everything else kept. */
private inline fun ExerciseDemo.withPoses(into: (Pose) -> Pose) = ExerciseDemo(
    keyframes = keyframes.map { Keyframe(it.at, into(it.pose)) },
    phases = phases,
    hold = hold,
    secondsPerRep = secondsPerRep,
    still = still,
    bellLag = bellLag,
    trail = trail,
    root = root,
    view = view,
    props = props,
    anchor = anchor,
    setUp = setUp,
    facing = facing,
    crop = crop,
    follows = follows,
)

/**
 * This pose squatted rather than hinged, [share] of the way: by how far the chest leans over the hips, the shins
 * tipped forward, the thighs nearer level and the chest raised. Both legs alike, so the far one goes with the near.
 */
private fun Pose.squatted(share: Float): Pose {
    val hinge = ((torso - HINGE_FROM) / (HINGE_FULL - HINGE_FROM)).coerceIn(0f, 1f) * share
    if (hinge == 0f) return this
    return copy(
        shin = shin + KNEES_FORWARD * hinge,
        farShin = farShin + KNEES_FORWARD * hinge,
        thigh = thigh - HIPS_DOWN * hinge,
        farThigh = farThigh - HIPS_DOWN * hinge,
        torso = torso - CHEST_UP * hinge,
    )
}

/** The lean of the chest a hinge starts at and is full at, and how far a full one squatted moves each part. */
private const val HINGE_FROM = 15f
private const val HINGE_FULL = 60f
private const val KNEES_FORWARD = 22f
private const val HIPS_DOWN = 18f
private const val CHEST_UP = 22f

/** How far squatted a pose is tried, the most first; none at all always keeps the bell where it was. */
private val SQUAT_SHARES = listOf(1f, 0.75f, 0.5f, 0.25f, 0f)
