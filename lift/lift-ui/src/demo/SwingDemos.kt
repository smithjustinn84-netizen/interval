package dev.justinsmith.lift.ui.demo

import dev.justinsmith.lift.ui.demo.KettlebellPoses.RESTING_TURNED
import dev.justinsmith.lift.ui.demo.KettlebellPoses.castPose
import dev.justinsmith.lift.ui.demo.KettlebellPoses.drive
import dev.justinsmith.lift.ui.demo.KettlebellPoses.hike
import dev.justinsmith.lift.ui.demo.KettlebellPoses.hinge
import dev.justinsmith.lift.ui.demo.KettlebellPoses.lockout
import dev.justinsmith.lift.ui.demo.KettlebellPoses.rack
import dev.justinsmith.lift.ui.demo.KettlebellPoses.snap
import dev.justinsmith.lift.ui.demo.KettlebellPoses.snatchDrop
import dev.justinsmith.lift.ui.demo.KettlebellPoses.snatchPull
import dev.justinsmith.lift.ui.demo.KettlebellPoses.stand
import dev.justinsmith.lift.ui.demo.KettlebellPoses.swingPose

/**
 * The one-hand swing: the two-hand swing's rep with the bell in one hand, the free arm hanging by the side and
 * swaying after it. Its phases are its cues: Hike (0), Snap (1), Stay square (2).
 */
val OneHandSwingDemo: ExerciseDemo = run {
    ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = hike.freeHand()),
            Keyframe(at = 0.14f, pose = drive.freeHand()),
            Keyframe(at = 0.26f, pose = snap.freeHand()),
            Keyframe(at = 0.46f, pose = swingPose(shin = 0f, thigh = 0f, torso = -3f, arm = 88f).freeHand()),
            Keyframe(at = 0.66f, pose = swingPose(shin = 0f, thigh = 0f, torso = -2f, arm = 16f).freeHand()),
            Keyframe(at = 0.80f, pose = hinge.freeHand()),
            Keyframe(at = 1f, pose = hike.freeHand()),
        ),
        phases = listOf(
            DemoPhase(until = 0.26f, beat = DemoBeat.Drive, cue = 1),
            DemoPhase(until = 0.66f, beat = DemoBeat.Ease, cue = 2),
            DemoPhase(until = 1f, beat = DemoBeat.Set, cue = 0),
        ),
        bells = Bells.NearHand,
        secondsPerRep = 1.8f,
        still = 0.46f,
        bellLag = 0.03f,
        trail = 0.16f,
    )
}

/**
 * The deadlift: from the bell on the floor between the feet, push the floor away to stand tall, then lower it
 * hips back first. Its phases are its cues: Set (0), Stand (1), Lower (2).
 */
val DeadliftDemo: ExerciseDemo = run {
    val floor = swingPose(shin = 18f, thigh = -76f, torso = 60f, arm = -6f)
    ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = floor),
            Keyframe(at = 0.12f, pose = floor),
            Keyframe(at = 0.32f, pose = swingPose(shin = 8f, thigh = -30f, torso = 32f, arm = -3f)),
            Keyframe(at = 0.48f, pose = stand),
            Keyframe(at = 0.58f, pose = stand),
            Keyframe(at = 0.78f, pose = swingPose(shin = 6f, thigh = -38f, torso = 40f, arm = -3f)),
            Keyframe(at = 1f, pose = floor),
        ),
        phases = listOf(
            DemoPhase(until = 0.12f, beat = DemoBeat.Set, cue = 0),
            DemoPhase(until = 0.58f, beat = DemoBeat.Drive, cue = 1),
            DemoPhase(until = 1f, beat = DemoBeat.Ease, cue = 2),
        ),
        bells = Bells.BothHands,
        secondsPerRep = 3.2f,
        still = 0.06f,
    )
}

/**
 * The high pull: a swing whose bell is met at the top by the elbow, pulled high and back with the bell close to
 * the chest, then let fall into the next swing. Its phases are its cues: Drive (0), Pull (1), Fall (2).
 */
val HighPullDemo: ExerciseDemo = run {
    val pulled = Pose(shin = 0f, thigh = 0f, torso = -4f, upperArm = -55f, forearm = 100f, bell = 30f)
    ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = hike.freeHand()),
            Keyframe(at = 0.14f, pose = drive.freeHand()),
            Keyframe(at = 0.26f, pose = snap.freeHand()),
            Keyframe(at = 0.44f, pose = pulled.freeHand()),
            Keyframe(at = 0.52f, pose = pulled.freeHand()),
            Keyframe(at = 0.68f, pose = swingPose(shin = 0f, thigh = 0f, torso = -2f, arm = 20f).freeHand()),
            Keyframe(at = 0.82f, pose = hinge.freeHand()),
            Keyframe(at = 1f, pose = hike.freeHand()),
        ),
        phases = listOf(
            DemoPhase(until = 0.26f, beat = DemoBeat.Drive, cue = 0),
            DemoPhase(until = 0.52f, beat = DemoBeat.Drive, cue = 1),
            DemoPhase(until = 1f, beat = DemoBeat.Ease, cue = 2),
        ),
        bells = Bells.NearHand,
        secondsPerRep = 2f,
        still = 0.48f,
        bellLag = 0.03f,
        trail = 0.12f,
    )
}

/**
 * The snatch: hike, drive, pull the elbow back to keep the arc close, punch through so the bell turns over the
 * hand onto the back of the forearm, pause locked out, then turn it back over into the next swing. The bell
 * goes right round the hand each rep. Its phases are its cues: Hike and drive (0), Punch through (1), Pause and
 * drop (2).
 */
val SnatchDemo: ExerciseDemo = ExerciseDemo(
    keyframes = listOf(
        Keyframe(at = 0f, pose = hike.freeHand()),
        Keyframe(at = 0.12f, pose = drive.freeHand()),
        Keyframe(at = 0.24f, pose = snap.copy(upperArm = 40f, forearm = 40f, bell = 40f).freeHand()),
        Keyframe(at = 0.34f, pose = snatchPull.freeHand()),
        Keyframe(at = 0.44f, pose = lockout.turned().freeHand()),
        Keyframe(at = 0.58f, pose = lockout.turned().freeHand()),
        Keyframe(at = 0.70f, pose = snatchDrop.freeHand()),
        Keyframe(at = 0.82f, pose = swingPose(shin = 0f, thigh = 0f, torso = -2f, arm = 20f).turned().freeHand()),
        Keyframe(at = 0.91f, pose = hinge.turned().freeHand()),
        Keyframe(at = 1f, pose = hike.turned().freeHand()),
    ),
    phases = listOf(
        DemoPhase(until = 0.24f, beat = DemoBeat.Drive, cue = 0),
        DemoPhase(until = 0.58f, beat = DemoBeat.Ease, cue = 1),
        DemoPhase(until = 1f, beat = DemoBeat.Set, cue = 2),
    ),
    bells = Bells.NearHand,
    secondsPerRep = 2.4f,
    still = 0.5f,
    bellLag = 0.03f,
    trail = 0.14f,
)

/**
 * The half snatch: a snatch to lockout, then pulled down into the rack rather than dropped, and cast from the
 * rack into the next hike. Its phases are its cues: Drive and punch (0), Pull to the rack (1), Cast (2).
 */
val HalfSnatchDemo: ExerciseDemo = ExerciseDemo(
    keyframes = listOf(
        Keyframe(at = 0f, pose = hike.freeHand()),
        Keyframe(at = 0.1f, pose = drive.freeHand()),
        Keyframe(at = 0.2f, pose = snap.copy(upperArm = 40f, forearm = 40f, bell = 40f).freeHand()),
        Keyframe(at = 0.28f, pose = snatchPull.freeHand()),
        Keyframe(at = 0.36f, pose = lockout.turned().freeHand()),
        Keyframe(at = 0.46f, pose = lockout.turned().freeHand()),
        Keyframe(at = 0.58f, pose = rack.turned().freeHand()),
        Keyframe(at = 0.66f, pose = rack.turned().freeHand()),
        Keyframe(at = 0.78f, pose = castPose.copy(bell = RESTING_TURNED).freeHand()),
        Keyframe(at = 0.89f, pose = hinge.turned().freeHand()),
        Keyframe(at = 1f, pose = hike.turned().freeHand()),
    ),
    phases = listOf(
        DemoPhase(until = 0.46f, beat = DemoBeat.Drive, cue = 0),
        DemoPhase(until = 0.66f, beat = DemoBeat.Ease, cue = 1),
        DemoPhase(until = 1f, beat = DemoBeat.Set, cue = 2),
    ),
    bells = Bells.NearHand,
    secondsPerRep = 3f,
    still = 0.4f,
    bellLag = 0.03f,
)
