package dev.justinsmith.lift.ui.demo

import dev.justinsmith.lift.ui.demo.KettlebellPoses.BOTTOMS_UP
import dev.justinsmith.lift.ui.demo.KettlebellPoses.lockout
import dev.justinsmith.lift.ui.demo.KettlebellPoses.rack

/**
 * The racked lunge: the bell in the rack, step back and lower the back knee toward the floor, drive through
 * the front foot to stand. The near foot is the front one and stays put. Its phases are its cues: Rack (0),
 * Step back and lower (1), Drive up (2).
 */
val RackedLungeDemo: ExerciseDemo = run {
    val standing = rack.stillHand()
    val stepping = standing.standingAs(
        Pose(
            shin = 4f,
            thigh = -22f,
            torso = 0f,
            upperArm = 0f,
            forearm = 0f,
            farShin = 85f,
            farThigh = 28f,
            farFoot = 10f,
        ),
    )
    val bottom = standing.standingAs(
        Pose(
            shin = 10f,
            thigh = -80f,
            torso = 0f,
            upperArm = 0f,
            forearm = 0f,
            farShin = 105f,
            farThigh = 18f,
            farFoot = 75f,
        ),
    )
    ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = standing),
            Keyframe(at = 0.1f, pose = standing),
            Keyframe(at = 0.28f, pose = stepping),
            Keyframe(at = 0.48f, pose = bottom),
            Keyframe(at = 0.56f, pose = bottom),
            Keyframe(at = 0.76f, pose = stepping),
            Keyframe(at = 0.92f, pose = standing),
            Keyframe(at = 1f, pose = standing),
        ),
        phases = listOf(
            DemoPhase(until = 0.1f, beat = DemoBeat.Set, cue = 0),
            DemoPhase(until = 0.56f, beat = DemoBeat.Ease, cue = 1),
            DemoPhase(until = 0.92f, beat = DemoBeat.Drive, cue = 2),
            DemoPhase(until = 1f, beat = DemoBeat.Set, cue = 0),
        ),
        hold = Hold.NearHand,
        secondsPerRep = 3.4f,
        still = 0.52f,
    )
}

/**
 * The row: hinged over a flat back with the free hand braced on a bench, pull the elbow back to the hip and
 * lower to a long arm. Its phases are its cues: Brace (0), Pull (1), Lower (2).
 */
val RowDemo: ExerciseDemo = run {
    val braced = Pose(
        shin = 8f,
        thigh = -40f,
        torso = 65f,
        upperArm = 0f,
        forearm = 0f,
        farUpperArm = -4f,
        farForearm = -4f,
    )
    val pulled = braced.copy(upperArm = -70f, forearm = 10f, bell = 10f)
    ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = braced),
            Keyframe(at = 0.1f, pose = braced),
            Keyframe(at = 0.4f, pose = pulled),
            Keyframe(at = 0.5f, pose = pulled),
            Keyframe(at = 0.9f, pose = braced),
            Keyframe(at = 1f, pose = braced),
        ),
        phases = listOf(
            DemoPhase(until = 0.1f, beat = DemoBeat.Set, cue = 0),
            DemoPhase(until = 0.5f, beat = DemoBeat.Drive, cue = 1),
            DemoPhase(until = 1f, beat = DemoBeat.Ease, cue = 2),
        ),
        hold = Hold.NearHand,
        secondsPerRep = 2.8f,
        still = 0.45f,
        bench = Bench(from = 236f, to = 340f, height = 78f),
    )
}

/**
 * The windmill: the bell locked out overhead, push the hip back and hinge there with the bell arm upright and
 * the free hand sliding down toward the floor, then drive the hip through to stand. Its phases are its cues:
 * Lock out (0), Push the hip back (1), Stand (2).
 */
val WindmillDemo: ExerciseDemo = run {
    val tall = lockout.stillHand()
    val hinged = Pose(shin = 0f, thigh = -30f, torso = 75f, upperArm = 0f, forearm = 0f)
    val down = lockout.standingAs(hinged).copy(farUpperArm = 8f, farForearm = 8f)
    ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = tall),
            Keyframe(at = 0.12f, pose = tall),
            Keyframe(at = 0.5f, pose = down),
            Keyframe(at = 0.6f, pose = down),
            Keyframe(at = 0.9f, pose = tall),
            Keyframe(at = 1f, pose = tall),
        ),
        phases = listOf(
            DemoPhase(until = 0.12f, beat = DemoBeat.Set, cue = 0),
            DemoPhase(until = 0.6f, beat = DemoBeat.Ease, cue = 1),
            DemoPhase(until = 1f, beat = DemoBeat.Drive, cue = 2),
        ),
        hold = Hold.NearHand,
        secondsPerRep = 4f,
        still = 0.55f,
    )
}

/**
 * The halo: the bell upside down by the horns, circled close round the head, in front of the face, past the
 * ear, behind the head and back. Its phases are its cues: Hold (0), Circle close (1), Ribs down (2).
 */
val HaloDemo: ExerciseDemo = run {
    val front = haloPose(upperArm = 40f, forearm = 165f)
    ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = front),
            Keyframe(at = 0.22f, pose = haloPose(upperArm = 100f, forearm = 228f)),
            Keyframe(at = 0.45f, pose = haloPose(upperArm = 140f, forearm = 250f)),
            Keyframe(at = 0.7f, pose = haloPose(upperArm = 92f, forearm = 220f)),
            Keyframe(at = 1f, pose = front),
        ),
        phases = listOf(
            DemoPhase(until = 0.22f, beat = DemoBeat.Set, cue = 0),
            DemoPhase(until = 0.7f, beat = DemoBeat.Drive, cue = 1),
            DemoPhase(until = 1f, beat = DemoBeat.Ease, cue = 2),
        ),
        hold = Hold.BothHands,
        secondsPerRep = 3f,
        still = 0.45f,
    )
}

/** A halo's pose: standing tall, the bell upside down over the hands. */
private fun haloPose(upperArm: Float, forearm: Float) =
    Pose(shin = 0f, thigh = 0f, torso = 0f, upperArm = upperArm, forearm = forearm, bell = BOTTOMS_UP)
