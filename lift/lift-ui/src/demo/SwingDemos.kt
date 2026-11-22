@file:Suppress("detekt.MagicNumber")

package dev.justinsmith.lift.ui.demo

import dev.justinsmith.interval.ui.BodyPose
import dev.justinsmith.interval.ui.ExerciseKeyframe
import dev.justinsmith.interval.ui.ExerciseRep
import dev.justinsmith.interval.ui.KettlebellPoses
import dev.justinsmith.interval.ui.RepBeat
import dev.justinsmith.interval.ui.RepHold
import dev.justinsmith.interval.ui.RepPhase

// Kettlebell hinge & ballistic variations built natively on core's KettlebellPoses kinematics.

/** Deep deadlift hinge: bell clear of the floor between the feet, back flat, shins near vertical. */
private val DeadliftFloor = BodyPose(
    hipAngleDeg = 114f,
    kneeAngleDeg = 72f,
    torsoAngleDeg = 54f,
    shoulderAngleDeg = 54f,
    elbowAngleDeg = 0f,
    bellAngleDeg = 0f,
)

/** Mid-shin pull passing through on the way up and down. */
private val DeadliftMid = BodyPose(
    hipAngleDeg = 48f,
    kneeAngleDeg = 20f,
    torsoAngleDeg = 30f,
    shoulderAngleDeg = 30f,
)

/** Standing tall at the top of a deadlift, arms hanging straight with the bell in front of the hips. */
private val DeadliftStand = BodyPose()

/**
 * The kettlebell deadlift: from the bell between the feet, push the floor away to stand tall, then hinge back down.
 */
val DeadliftRep: ExerciseRep = ExerciseRep(
    keyframes = listOf(
        ExerciseKeyframe(DeadliftFloor),
        ExerciseKeyframe(DeadliftFloor, weight = 0.12f, phase = RepPhase.HikePass, beat = RepBeat.Set, cueIndex = 0),
        ExerciseKeyframe(
            DeadliftMid,
            weight = 0.20f,
            phase = RepPhase.Drive,
            passing = true,
            beat = RepBeat.Drive,
            cueIndex = 1,
        ),
        ExerciseKeyframe(DeadliftStand, weight = 0.18f, phase = RepPhase.Drive, beat = RepBeat.Drive, cueIndex = 1),
        ExerciseKeyframe(DeadliftStand, weight = 0.12f, phase = RepPhase.Stand, beat = RepBeat.Set, cueIndex = 1),
        ExerciseKeyframe(
            DeadliftMid,
            weight = 0.20f,
            phase = RepPhase.Descend,
            passing = true,
            beat = RepBeat.Ease,
            cueIndex = 2,
        ),
        ExerciseKeyframe(DeadliftFloor, weight = 0.18f, phase = RepPhase.Descend, beat = RepBeat.Ease, cueIndex = 2),
    ),
    seconds = 3.2f,
    stillAt = 1,
    hold = RepHold.BothHands,
)

/**
 * The high pull: a hip-snap swing whose elbow pulls high and back at the top with the bell close to the chest,
 * then falls back into the hike pass.
 */
val HighPullRep: ExerciseRep = ExerciseRep(
    keyframes = listOf(
        ExerciseKeyframe(KettlebellPoses.Hinge.freeArm()),
        ExerciseKeyframe(
            KettlebellPoses.SnatchPull.freeArm(),
            weight = 0.15f,
            phase = RepPhase.HipSnap,
            passing = true,
            beat = RepBeat.Drive,
            cueIndex = 0,
        ),
        ExerciseKeyframe(
            KettlebellPoses.HighPull.freeArm(),
            weight = 0.20f,
            phase = RepPhase.HighPull,
            beat = RepBeat.Drive,
            cueIndex = 1,
        ),
        ExerciseKeyframe(
            KettlebellPoses.HighPull.freeArm(),
            weight = 0.10f,
            phase = RepPhase.HighPull,
            beat = RepBeat.Drive,
            cueIndex = 1,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Drive.freeArm(),
            weight = 0.30f,
            phase = RepPhase.Drop,
            passing = true,
            beat = RepBeat.Ease,
            cueIndex = 2,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Hinge.freeArm(),
            weight = 0.25f,
            phase = RepPhase.HikePass,
            beat = RepBeat.Set,
            cueIndex = 2,
        ),
    ),
    seconds = 2.2f,
    stillAt = 2,
    hold = RepHold.NearHand,
    showsTrail = true,
)

/**
 * The half snatch: snatch to overhead lockout, lower under control into the rack, then drop into the next hike.
 */
val HalfSnatchRep: ExerciseRep = ExerciseRep(
    keyframes = listOf(
        ExerciseKeyframe(KettlebellPoses.Hinge.freeArm()),
        ExerciseKeyframe(
            KettlebellPoses.SnatchPull.freeArm(),
            weight = 0.12f,
            phase = RepPhase.HipSnap,
            passing = true,
            beat = RepBeat.Drive,
            cueIndex = 0,
        ),
        ExerciseKeyframe(
            KettlebellPoses.HighPull.freeArm(),
            weight = 0.08f,
            phase = RepPhase.HighPull,
            passing = true,
            beat = RepBeat.Drive,
            cueIndex = 0,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Turnover.freeArm(),
            weight = 0.08f,
            phase = RepPhase.PunchThrough,
            passing = true,
            beat = RepBeat.Drive,
            cueIndex = 0,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Lockout.freeArm(),
            weight = 0.10f,
            phase = RepPhase.PunchThrough,
            beat = RepBeat.Drive,
            cueIndex = 0,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Lockout.freeArm(),
            weight = 0.10f,
            phase = RepPhase.Lockout,
            beat = RepBeat.Ease,
            cueIndex = 0,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Press.freeArm(),
            weight = 0.10f,
            phase = RepPhase.Lower,
            passing = true,
            beat = RepBeat.Ease,
            cueIndex = 1,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Rack.freeArm(),
            weight = 0.10f,
            phase = RepPhase.Rack,
            beat = RepBeat.Ease,
            cueIndex = 1,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Rack.freeArm(),
            weight = 0.08f,
            phase = RepPhase.Rack,
            beat = RepBeat.Set,
            cueIndex = 1,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Pull.freeArm(),
            weight = 0.10f,
            phase = RepPhase.Drop,
            passing = true,
            beat = RepBeat.Set,
            cueIndex = 2,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Hinge.freeArm(),
            weight = 0.14f,
            phase = RepPhase.HikePass,
            beat = RepBeat.Set,
            cueIndex = 2,
        ),
    ),
    seconds = 3.4f,
    stillAt = 5,
    hold = RepHold.NearHand,
    showsTrail = true,
)

/** Frees the far arm to hang naturally beside the torso for single-bell ballistic lifts. */
internal fun BodyPose.freeArm(): BodyPose = copy(farShoulderAngleDeg = torsoAngleDeg - 6f, farElbowAngleDeg = 8f)
