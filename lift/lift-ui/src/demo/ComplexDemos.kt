@file:Suppress("detekt.MagicNumber")

package dev.justinsmith.lift.ui.demo

import dev.justinsmith.interval.ui.ExerciseKeyframe
import dev.justinsmith.interval.ui.ExerciseRep
import dev.justinsmith.interval.ui.KettlebellPoses
import dev.justinsmith.interval.ui.RepBeat
import dev.justinsmith.interval.ui.RepHold
import dev.justinsmith.interval.ui.RepPhase

// Kettlebell complexes built natively on core's KettlebellPoses kinematics.

/**
 * Long cycle: clean to the rack, jerk overhead, lower to the rack, and drop into the next clean.
 */
val LongCycleRep: ExerciseRep = ExerciseRep(
    keyframes = listOf(
        ExerciseKeyframe(KettlebellPoses.Hinge.freeArm()),
        ExerciseKeyframe(
            KettlebellPoses.Pull.freeArm(),
            weight = 0.10f,
            phase = RepPhase.Pull,
            passing = true,
            beat = RepBeat.Drive,
            cueIndex = 0,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Rack.freeArm(),
            weight = 0.09f,
            phase = RepPhase.Rack,
            beat = RepBeat.Drive,
            cueIndex = 0,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Rack.freeArm(),
            weight = 0.06f,
            phase = RepPhase.Rack,
            beat = RepBeat.Drive,
            cueIndex = 0,
        ),
        ExerciseKeyframe(
            RackDip.freeArm(),
            weight = 0.08f,
            phase = RepPhase.Descend,
            beat = RepBeat.Drive,
            cueIndex = 0,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Press.freeArm(),
            weight = 0.06f,
            phase = RepPhase.Drive,
            passing = true,
            beat = RepBeat.Drive,
            cueIndex = 0,
        ),
        ExerciseKeyframe(
            JerkCatch.freeArm(),
            weight = 0.08f,
            phase = RepPhase.Drive,
            beat = RepBeat.Drive,
            cueIndex = 0,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Lockout.freeArm(),
            weight = 0.10f,
            phase = RepPhase.Lockout,
            beat = RepBeat.Drive,
            cueIndex = 0,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Lockout.freeArm(),
            weight = 0.06f,
            phase = RepPhase.Lockout,
            beat = RepBeat.Drive,
            cueIndex = 0,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Rack.freeArm(),
            weight = 0.12f,
            phase = RepPhase.Lower,
            beat = RepBeat.Ease,
            cueIndex = 1,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Pull.freeArm(),
            weight = 0.10f,
            phase = RepPhase.Drop,
            passing = true,
            beat = RepBeat.Ease,
            cueIndex = 1,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Hinge.freeArm(),
            weight = 0.15f,
            phase = RepPhase.HikePass,
            beat = RepBeat.Ease,
            cueIndex = 1,
        ),
    ),
    seconds = 4.2f,
    stillAt = 8,
    hold = RepHold.NearHand,
    showsTrail = true,
)

/**
 * Iron Cardio: clean, press, and front squat in one continuous flow, then drop into the next clean.
 */
val IronCardioRep: ExerciseRep = ExerciseRep(
    keyframes = listOf(
        ExerciseKeyframe(KettlebellPoses.Hinge.freeArm()),
        ExerciseKeyframe(
            KettlebellPoses.Pull.freeArm(),
            weight = 0.09f,
            phase = RepPhase.Pull,
            passing = true,
            beat = RepBeat.Drive,
            cueIndex = 0,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Rack.freeArm(),
            weight = 0.08f,
            phase = RepPhase.Rack,
            beat = RepBeat.Drive,
            cueIndex = 0,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Press.freeArm(),
            weight = 0.09f,
            phase = RepPhase.Press,
            passing = true,
            beat = RepBeat.Drive,
            cueIndex = 0,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Lockout.freeArm(),
            weight = 0.09f,
            phase = RepPhase.Lockout,
            beat = RepBeat.Drive,
            cueIndex = 0,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Press.freeArm(),
            weight = 0.08f,
            phase = RepPhase.Lower,
            passing = true,
            beat = RepBeat.Drive,
            cueIndex = 0,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Rack.freeArm(),
            weight = 0.08f,
            phase = RepPhase.Rack,
            beat = RepBeat.Drive,
            cueIndex = 0,
        ),
        ExerciseKeyframe(
            FrontSquatBottom.freeArm(),
            weight = 0.12f,
            phase = RepPhase.Descend,
            beat = RepBeat.Ease,
            cueIndex = 1,
        ),
        ExerciseKeyframe(
            FrontSquatBottom.freeArm(),
            weight = 0.05f,
            phase = RepPhase.Bottom,
            beat = RepBeat.Ease,
            cueIndex = 1,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Rack.freeArm(),
            weight = 0.10f,
            phase = RepPhase.Drive,
            beat = RepBeat.Ease,
            cueIndex = 1,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Rack.freeArm(),
            weight = 0.06f,
            phase = RepPhase.Rack,
            beat = RepBeat.Set,
            cueIndex = 2,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Pull.freeArm(),
            weight = 0.07f,
            phase = RepPhase.Drop,
            passing = true,
            beat = RepBeat.Set,
            cueIndex = 2,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Hinge.freeArm(),
            weight = 0.09f,
            phase = RepPhase.HikePass,
            beat = RepBeat.Set,
            cueIndex = 2,
        ),
    ),
    seconds = 4.8f,
    stillAt = 4,
    hold = RepHold.NearHand,
    showsTrail = true,
)

/**
 * Armor Building Complex (double bells): two cleans, one press, and three front squats.
 */
val ArmorBuildingComplexRep: ExerciseRep = ExerciseRep(
    keyframes = listOf(
        ExerciseKeyframe(KettlebellPoses.Hinge),
        ExerciseKeyframe(
            KettlebellPoses.Pull,
            weight = 0.06f,
            phase = RepPhase.Pull,
            passing = true,
            beat = RepBeat.Drive,
            cueIndex = 1,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Rack,
            weight = 0.06f,
            phase = RepPhase.Rack,
            beat = RepBeat.Drive,
            cueIndex = 1,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Pull,
            weight = 0.05f,
            phase = RepPhase.Drop,
            passing = true,
            beat = RepBeat.Drive,
            cueIndex = 1,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Hinge,
            weight = 0.06f,
            phase = RepPhase.HikePass,
            beat = RepBeat.Drive,
            cueIndex = 1,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Pull,
            weight = 0.06f,
            phase = RepPhase.Pull,
            passing = true,
            beat = RepBeat.Drive,
            cueIndex = 1,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Rack,
            weight = 0.06f,
            phase = RepPhase.Rack,
            beat = RepBeat.Drive,
            cueIndex = 1,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Press,
            weight = 0.06f,
            phase = RepPhase.Press,
            passing = true,
            beat = RepBeat.Ease,
            cueIndex = 0,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Lockout,
            weight = 0.06f,
            phase = RepPhase.Lockout,
            beat = RepBeat.Ease,
            cueIndex = 0,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Lockout,
            weight = 0.04f,
            phase = RepPhase.Lockout,
            beat = RepBeat.Ease,
            cueIndex = 0,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Press,
            weight = 0.05f,
            phase = RepPhase.Lower,
            passing = true,
            beat = RepBeat.Ease,
            cueIndex = 0,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Rack,
            weight = 0.05f,
            phase = RepPhase.Rack,
            beat = RepBeat.Ease,
            cueIndex = 0,
        ),
        ExerciseKeyframe(FrontSquatBottom, weight = 0.05f, phase = RepPhase.Descend, beat = RepBeat.Ease, cueIndex = 0),
        ExerciseKeyframe(
            KettlebellPoses.Rack,
            weight = 0.05f,
            phase = RepPhase.Drive,
            beat = RepBeat.Ease,
            cueIndex = 0,
        ),
        ExerciseKeyframe(FrontSquatBottom, weight = 0.05f, phase = RepPhase.Descend, beat = RepBeat.Ease, cueIndex = 0),
        ExerciseKeyframe(
            KettlebellPoses.Rack,
            weight = 0.05f,
            phase = RepPhase.Drive,
            beat = RepBeat.Ease,
            cueIndex = 0,
        ),
        ExerciseKeyframe(FrontSquatBottom, weight = 0.05f, phase = RepPhase.Descend, beat = RepBeat.Ease, cueIndex = 0),
        ExerciseKeyframe(
            KettlebellPoses.Rack,
            weight = 0.05f,
            phase = RepPhase.Drive,
            beat = RepBeat.Set,
            cueIndex = 2,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Pull,
            weight = 0.04f,
            phase = RepPhase.Drop,
            passing = true,
            beat = RepBeat.Set,
            cueIndex = 2,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Hinge,
            weight = 0.05f,
            phase = RepPhase.HikePass,
            beat = RepBeat.Set,
            cueIndex = 2,
        ),
    ),
    seconds = 8.4f,
    stillAt = 9,
    hold = RepHold.Pair,
    showsTrail = true,
)
