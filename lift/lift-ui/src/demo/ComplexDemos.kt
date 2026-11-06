package dev.justinsmith.lift.ui.demo

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

/**
 * The clean and press: a clean to the rack, a press to lockout, back down to the rack and cast into the next
 * hike. Its phases are its cues: Clean (0), Press (1), Lower and cast (2).
 */
val CleanAndPressDemo: ExerciseDemo = ExerciseDemo(
    keyframes = listOf(
        Keyframe(at = 0f, pose = hike),
        Keyframe(at = 0.08f, pose = drive),
        Keyframe(at = 0.15f, pose = zip),
        Keyframe(at = 0.22f, pose = rack),
        Keyframe(at = 0.3f, pose = rack),
        Keyframe(at = 0.4f, pose = pressing),
        Keyframe(at = 0.5f, pose = lockout),
        Keyframe(at = 0.58f, pose = lockout),
        Keyframe(at = 0.68f, pose = pressing),
        Keyframe(at = 0.78f, pose = rack),
        Keyframe(at = 0.86f, pose = castPose),
        Keyframe(at = 0.93f, pose = hinge),
        Keyframe(at = 1f, pose = hike),
    ).map { it.copy(pose = it.pose.stillHand()) },
    phases = listOf(
        DemoPhase(until = 0.3f, beat = DemoBeat.Drive, cue = 0),
        DemoPhase(until = 0.58f, beat = DemoBeat.Drive, cue = 1),
        DemoPhase(until = 1f, beat = DemoBeat.Ease, cue = 2),
    ),
    hold = Hold.NearHand,
    secondsPerRep = 3.6f,
    still = 0.54f,
    bellLag = 0.02f,
)

/**
 * The long cycle: a clean to the rack, a jerk overhead, back to the rack and into the next swing. Its phases
 * are its cues: Clean and jerk (0), Back to the rack and the swing (1).
 */
val LongCycleDemo: ExerciseDemo = ExerciseDemo(
    keyframes = listOf(
        Keyframe(at = 0f, pose = hike),
        Keyframe(at = 0.07f, pose = drive),
        Keyframe(at = 0.13f, pose = zip),
        Keyframe(at = 0.19f, pose = rack),
        Keyframe(at = 0.25f, pose = rack),
        Keyframe(at = 0.32f, pose = dip),
        Keyframe(at = 0.38f, pose = rack.copy(upperArm = 60f, forearm = 170f)),
        Keyframe(at = 0.44f, pose = underTheBell),
        Keyframe(at = 0.48f, pose = underTheBell),
        Keyframe(at = 0.56f, pose = lockout),
        Keyframe(at = 0.6f, pose = lockout),
        Keyframe(at = 0.7f, pose = rack),
        Keyframe(at = 0.74f, pose = rack),
        Keyframe(at = 0.82f, pose = castPose),
        Keyframe(at = 0.91f, pose = hinge),
        Keyframe(at = 1f, pose = hike),
    ).map { it.copy(pose = it.pose.freeHand()) },
    phases = listOf(
        DemoPhase(until = 0.25f, beat = DemoBeat.Drive, cue = 0),
        DemoPhase(until = 0.6f, beat = DemoBeat.Drive, cue = 0),
        DemoPhase(until = 1f, beat = DemoBeat.Ease, cue = 1),
    ),
    hold = Hold.NearHand,
    secondsPerRep = 4f,
    still = 0.58f,
    bellLag = 0.02f,
)

/**
 * Iron Cardio: a clean, a press and a squat with the bell kept in the rack, then cast into the next clean. Its
 * phases are its cues: Clean and press (0), Squat in the rack (1), Breathe at the top (2).
 */
val IronCardioDemo: ExerciseDemo = ExerciseDemo(
    keyframes = listOf(
        Keyframe(at = 0f, pose = hike),
        Keyframe(at = 0.06f, pose = drive),
        Keyframe(at = 0.12f, pose = zip),
        Keyframe(at = 0.18f, pose = rack),
        Keyframe(at = 0.26f, pose = pressing),
        Keyframe(at = 0.33f, pose = lockout),
        Keyframe(at = 0.4f, pose = pressing),
        Keyframe(at = 0.47f, pose = rack),
        Keyframe(at = 0.56f, pose = squat),
        Keyframe(at = 0.6f, pose = squat),
        Keyframe(at = 0.7f, pose = rack),
        Keyframe(at = 0.77f, pose = rack),
        Keyframe(at = 0.85f, pose = castPose),
        Keyframe(at = 0.93f, pose = hinge),
        Keyframe(at = 1f, pose = hike),
    ).map { it.copy(pose = it.pose.stillHand()) },
    phases = listOf(
        DemoPhase(until = 0.47f, beat = DemoBeat.Drive, cue = 0),
        DemoPhase(until = 0.7f, beat = DemoBeat.Ease, cue = 1),
        DemoPhase(until = 1f, beat = DemoBeat.Set, cue = 2),
    ),
    hold = Hold.NearHand,
    secondsPerRep = 4.4f,
    still = 0.33f,
    bellLag = 0.02f,
)

/**
 * The Armor Building Complex, with a bell in each hand: two cleans, a press and three front squats, then cast
 * into the next rep. Its phases are its cues: Close on the cleans (1), The press and squats (0), Rest in the
 * rack (2).
 */
val ArmorBuildingComplexDemo: ExerciseDemo = ExerciseDemo(
    keyframes = listOf(
        Keyframe(at = 0f, pose = hike),
        Keyframe(at = 0.05f, pose = drive),
        Keyframe(at = 0.09f, pose = zip),
        Keyframe(at = 0.13f, pose = rack),
        Keyframe(at = 0.16f, pose = rack),
        Keyframe(at = 0.2f, pose = castPose),
        Keyframe(at = 0.24f, pose = hinge),
        Keyframe(at = 0.28f, pose = hike),
        Keyframe(at = 0.33f, pose = drive),
        Keyframe(at = 0.37f, pose = zip),
        Keyframe(at = 0.41f, pose = rack),
        Keyframe(at = 0.45f, pose = rack),
        Keyframe(at = 0.5f, pose = pressing),
        Keyframe(at = 0.55f, pose = lockout),
        Keyframe(at = 0.58f, pose = lockout),
        Keyframe(at = 0.63f, pose = pressing),
        Keyframe(at = 0.67f, pose = rack),
        Keyframe(at = 0.71f, pose = squat),
        Keyframe(at = 0.75f, pose = rack),
        Keyframe(at = 0.79f, pose = squat),
        Keyframe(at = 0.83f, pose = rack),
        Keyframe(at = 0.87f, pose = squat),
        Keyframe(at = 0.91f, pose = rack),
        Keyframe(at = 0.94f, pose = castPose),
        Keyframe(at = 0.97f, pose = hinge),
        Keyframe(at = 1f, pose = hike),
    ),
    phases = listOf(
        DemoPhase(until = 0.45f, beat = DemoBeat.Drive, cue = 1),
        DemoPhase(until = 0.91f, beat = DemoBeat.Ease, cue = 0),
        DemoPhase(until = 1f, beat = DemoBeat.Set, cue = 2),
    ),
    hold = Hold.Pair,
    secondsPerRep = 8f,
    still = 0.56f,
    bellLag = 0.02f,
)
