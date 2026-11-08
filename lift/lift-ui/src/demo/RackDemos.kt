package dev.justinsmith.lift.ui.demo

import dev.justinsmith.lift.ui.demo.KettlebellPoses.BOTTOMS_UP
import dev.justinsmith.lift.ui.demo.KettlebellPoses.HORNS
import dev.justinsmith.lift.ui.demo.KettlebellPoses.RESTING
import dev.justinsmith.lift.ui.demo.KettlebellPoses.castPose
import dev.justinsmith.lift.ui.demo.KettlebellPoses.dip
import dev.justinsmith.lift.ui.demo.KettlebellPoses.drive
import dev.justinsmith.lift.ui.demo.KettlebellPoses.hike
import dev.justinsmith.lift.ui.demo.KettlebellPoses.hinge
import dev.justinsmith.lift.ui.demo.KettlebellPoses.lockout
import dev.justinsmith.lift.ui.demo.KettlebellPoses.pressing
import dev.justinsmith.lift.ui.demo.KettlebellPoses.rack
import dev.justinsmith.lift.ui.demo.KettlebellPoses.squat
import dev.justinsmith.lift.ui.demo.KettlebellPoses.underTheBell
import dev.justinsmith.lift.ui.demo.KettlebellPoses.zip

/** Halfway down a squat, and halfway back up it, the torso a little more upright on the way up. */
private val halfSquat = Pose(shin = 18f, thigh = -55f, torso = 14f, upperArm = 0f, forearm = 0f)
private val risingSquat = Pose(shin = 14f, thigh = -40f, torso = 10f, upperArm = 0f, forearm = 0f)

/**
 * The clean: hike, drive, zip the bell up close with the elbow in, let the handle roll round the wrist so the
 * bell lands on the forearm in the rack, then cast it back into the next hike. Its phases are its cues: Close
 * (0), Roll (1), Rack and cast (2).
 */
val CleanDemo: ExerciseDemo = ExerciseDemo(
    keyframes = listOf(
        Keyframe(at = 0f, pose = hike.freeHand()),
        Keyframe(at = 0.14f, pose = drive.freeHand()),
        Keyframe(at = 0.26f, pose = zip.freeHand()),
        Keyframe(at = 0.38f, pose = rack.freeHand()),
        Keyframe(at = 0.56f, pose = rack.freeHand()),
        Keyframe(at = 0.68f, pose = castPose.freeHand()),
        Keyframe(at = 0.82f, pose = hinge.freeHand()),
        Keyframe(at = 1f, pose = hike.freeHand()),
    ),
    phases = listOf(
        DemoPhase(until = 0.26f, beat = DemoBeat.Drive, cue = 0),
        DemoPhase(until = 0.38f, beat = DemoBeat.Ease, cue = 1),
        DemoPhase(until = 1f, beat = DemoBeat.Set, cue = 2),
    ),
    hold = Hold.NearHand,
    secondsPerRep = 2.4f,
    still = 0.46f,
    bellLag = 0.03f,
    trail = 0.1f,
)

/**
 * The press: brace in the rack, press with the forearm upright, lock out by the ear, pull it back down to the
 * rack. Its phases are its cues: Brace (0), Press (1), Lock out (2).
 */
val PressDemo: ExerciseDemo = pressDemo(bottomsUp = false)

/**
 * The bottoms-up press: the press with the bell upside down over the fist, slower. Its phases are its cues:
 * Crush the handle (0), Forearm upright (1), Slowly (2).
 */
val BottomsUpPressDemo: ExerciseDemo = pressDemo(bottomsUp = true)

/**
 * The push press: a short dip of the knees from the rack, a hard drive through the legs that launches the bell
 * to lockout, then back to the rack. Its phases are its cues: Rack (0), Dip (1), Drive (2).
 */
val PushPressDemo: ExerciseDemo = ExerciseDemo(
    keyframes = listOf(
        Keyframe(at = 0f, pose = rack.stillHand()),
        Keyframe(at = 0.12f, pose = rack.stillHand()),
        Keyframe(at = 0.26f, pose = dip.stillHand()),
        Keyframe(at = 0.36f, pose = pressing.copy(upperArm = 110f).stillHand()),
        Keyframe(at = 0.46f, pose = lockout.stillHand()),
        Keyframe(at = 0.58f, pose = lockout.stillHand()),
        Keyframe(at = 0.8f, pose = pressing.stillHand()),
        Keyframe(at = 1f, pose = rack.stillHand()),
    ),
    phases = listOf(
        DemoPhase(until = 0.12f, beat = DemoBeat.Set, cue = 0),
        DemoPhase(until = 0.26f, beat = DemoBeat.Ease, cue = 1),
        DemoPhase(until = 0.58f, beat = DemoBeat.Drive, cue = 2),
        DemoPhase(until = 1f, beat = DemoBeat.Ease, cue = 0),
    ),
    hold = Hold.NearHand,
    secondsPerRep = 2.4f,
    still = 0.5f,
)

/**
 * The jerk: dip, drive the bell up off the rack, drop under it into a second dip to lock the arm, stand tall
 * under it, then bring it back to the rack. Its phases are its cues: Dip (0), Drive and drop under (1), Stand
 * tall (2).
 */
val JerkDemo: ExerciseDemo = run {
    ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = rack.stillHand()),
            Keyframe(at = 0.1f, pose = rack.stillHand()),
            Keyframe(at = 0.22f, pose = dip.stillHand()),
            Keyframe(at = 0.31f, pose = rack.copy(upperArm = 60f, forearm = 170f).stillHand()),
            Keyframe(at = 0.4f, pose = underTheBell.stillHand()),
            Keyframe(at = 0.48f, pose = underTheBell.stillHand()),
            Keyframe(at = 0.6f, pose = lockout.stillHand()),
            Keyframe(at = 0.7f, pose = lockout.stillHand()),
            Keyframe(at = 0.86f, pose = pressing.stillHand()),
            Keyframe(at = 1f, pose = rack.stillHand()),
        ),
        phases = listOf(
            DemoPhase(until = 0.22f, beat = DemoBeat.Ease, cue = 0),
            DemoPhase(until = 0.48f, beat = DemoBeat.Drive, cue = 1),
            DemoPhase(until = 0.7f, beat = DemoBeat.Set, cue = 2),
            DemoPhase(until = 1f, beat = DemoBeat.Ease, cue = 2),
        ),
        hold = Hold.NearHand,
        secondsPerRep = 2.6f,
        still = 0.44f,
    )
}

/**
 * The front squat: the bell in the rack, the free arm out for balance, sit straight down with the chest up and
 * drive up to stand tall. Its phases are its cues: Rack (0), Sit (1), Stand (2).
 */
val FrontSquatDemo: ExerciseDemo = ExerciseDemo(
    keyframes = listOf(
        Keyframe(at = 0f, pose = rack.balancing()),
        Keyframe(at = 0.1f, pose = rack.balancing()),
        Keyframe(at = 0.3f, pose = rack.standingAs(halfSquat).balancing()),
        Keyframe(at = 0.5f, pose = squat.balancing()),
        Keyframe(at = 0.58f, pose = squat.balancing()),
        Keyframe(at = 0.78f, pose = rack.standingAs(risingSquat).balancing()),
        Keyframe(at = 0.92f, pose = rack.balancing()),
        Keyframe(at = 1f, pose = rack.balancing()),
    ),
    phases = listOf(
        DemoPhase(until = 0.1f, beat = DemoBeat.Set, cue = 0),
        DemoPhase(until = 0.58f, beat = DemoBeat.Ease, cue = 1),
        DemoPhase(until = 0.92f, beat = DemoBeat.Drive, cue = 2),
        DemoPhase(until = 1f, beat = DemoBeat.Set, cue = 0),
    ),
    hold = Hold.NearHand,
    secondsPerRep = 3f,
    still = 0.54f,
)

/**
 * The thruster: the bell by the horns at the chest, a full squat, then up hard with the momentum carried
 * straight into a press overhead, and back to the chest. Its phases are its cues: Hold (0), Squat (1), Drive
 * and press (2).
 */
val ThrusterDemo: ExerciseDemo = run {
    val chest = Pose(shin = 0f, thigh = 0f, torso = 0f, upperArm = 10f, forearm = 155f, bell = HORNS)
    val bottom = Pose(shin = 32f, thigh = -100f, torso = 22f, upperArm = 15f, forearm = 150f, bell = HORNS)
    val overhead = Pose(shin = 0f, thigh = 0f, torso = -2f, upperArm = 168f, forearm = 176f, bell = HORNS)
    ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = chest),
            Keyframe(at = 0.06f, pose = chest),
            Keyframe(at = 0.3f, pose = bottom),
            Keyframe(at = 0.36f, pose = bottom),
            Keyframe(at = 0.52f, pose = chest.standingAs(risingSquat).copy(upperArm = 40f, forearm = 160f)),
            Keyframe(at = 0.64f, pose = overhead),
            Keyframe(at = 0.74f, pose = overhead),
            Keyframe(at = 0.92f, pose = chest),
            Keyframe(at = 1f, pose = chest),
        ),
        phases = listOf(
            DemoPhase(until = 0.06f, beat = DemoBeat.Set, cue = 0),
            DemoPhase(until = 0.36f, beat = DemoBeat.Ease, cue = 1),
            DemoPhase(until = 0.74f, beat = DemoBeat.Drive, cue = 2),
            DemoPhase(until = 1f, beat = DemoBeat.Ease, cue = 0),
        ),
        hold = Hold.BothHands,
        secondsPerRep = 2.8f,
        still = 0.68f,
    )
}

private fun pressDemo(bottomsUp: Boolean): ExerciseDemo {
    val bell = if (bottomsUp) BOTTOMS_UP else RESTING
    val forearm = if (bottomsUp) UPRIGHT else rack.forearm
    val elbow = if (bottomsUp) UPRIGHT_ELBOW else rack.upperArm
    val start = rack.copy(upperArm = elbow, forearm = forearm, bell = bell).stillHand()
    val midway = pressing.copy(bell = bell).stillHand()
    val top = lockout.copy(bell = bell).stillHand()
    return ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = start),
            Keyframe(at = 0.15f, pose = start),
            Keyframe(at = 0.32f, pose = midway),
            Keyframe(at = 0.46f, pose = top),
            Keyframe(at = 0.6f, pose = top),
            Keyframe(at = 0.8f, pose = midway),
            Keyframe(at = 1f, pose = start),
        ),
        phases = listOf(
            DemoPhase(until = 0.15f, beat = DemoBeat.Set, cue = 0),
            DemoPhase(until = 0.46f, beat = DemoBeat.Drive, cue = 1),
            DemoPhase(until = 1f, beat = DemoBeat.Ease, cue = 2),
        ),
        hold = Hold.NearHand,
        secondsPerRep = if (bottomsUp) 4f else 2.8f,
        still = 0.53f,
    )
}

/** A forearm held upright under a bottoms-up bell, the fist straight over the elbow, the elbow out in front. */
private const val UPRIGHT = 178f
private const val UPRIGHT_ELBOW = 32f
