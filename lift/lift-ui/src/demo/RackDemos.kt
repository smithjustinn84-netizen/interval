@file:Suppress("detekt.MagicNumber")

package dev.justinsmith.lift.ui.demo

import dev.justinsmith.interval.ui.BodyPose
import dev.justinsmith.interval.ui.ExerciseKeyframe
import dev.justinsmith.interval.ui.ExerciseRep
import dev.justinsmith.interval.ui.KettlebellPoses
import dev.justinsmith.interval.ui.RepBeat
import dev.justinsmith.interval.ui.RepHold
import dev.justinsmith.interval.ui.RepPhase

// Kettlebell rack, press, jerk, front squat, and thruster lifts built natively on core's KettlebellPoses.

/** Short vertical knee dip from the rack for push press and jerk. */
internal val RackDip = KettlebellPoses.Rack.copy(hipAngleDeg = 22f, kneeAngleDeg = 42f, torsoAngleDeg = 4f)

/** Second dip catch under a locked-out bell during a jerk. */
internal val JerkCatch = KettlebellPoses.Lockout.copy(
    hipAngleDeg = 32f,
    kneeAngleDeg = 58f,
    torsoAngleDeg = 6f,
    shoulderAngleDeg = 186f,
)

/** Deep front squat bottom with bell in the rack and torso upright. */
internal val FrontSquatBottom = KettlebellPoses.GobletBottom.copy(
    shoulderAngleDeg = 32f,
    elbowAngleDeg = 148f,
    bellAngleDeg = -170f,
)

/** Rack stance with far arm extended slightly ahead for front-squat balance. */
private fun BodyPose.balancingFarArm(): BodyPose =
    copy(farShoulderAngleDeg = torsoAngleDeg + 52f, farElbowAngleDeg = 16f)

/**
 * Bottoms-up press: the press with the bell balanced upside down above the fist over a vertical forearm.
 */
val BottomsUpPressRep: ExerciseRep = run {
    val start = KettlebellPoses.Rack.copy(shoulderAngleDeg = 24f, elbowAngleDeg = 154f, bellAngleDeg = 0f).freeArm()
    val mid = KettlebellPoses.Press.copy(bellAngleDeg = 0f).freeArm()
    val top = KettlebellPoses.Lockout.copy(bellAngleDeg = 0f).freeArm()
    ExerciseRep(
        keyframes = listOf(
            ExerciseKeyframe(start),
            ExerciseKeyframe(start, weight = 0.14f, phase = RepPhase.Rack, beat = RepBeat.Set, cueIndex = 0),
            ExerciseKeyframe(
                mid,
                weight = 0.18f,
                phase = RepPhase.Press,
                passing = true,
                beat = RepBeat.Drive,
                cueIndex = 1,
            ),
            ExerciseKeyframe(top, weight = 0.16f, phase = RepPhase.Press, beat = RepBeat.Drive, cueIndex = 1),
            ExerciseKeyframe(top, weight = 0.12f, phase = RepPhase.Lockout, beat = RepBeat.Ease, cueIndex = 2),
            ExerciseKeyframe(
                mid,
                weight = 0.20f,
                phase = RepPhase.Lower,
                passing = true,
                beat = RepBeat.Ease,
                cueIndex = 2,
            ),
            ExerciseKeyframe(start, weight = 0.20f, phase = RepPhase.Lower, beat = RepBeat.Ease, cueIndex = 2),
        ),
        seconds = 4.2f,
        stillAt = 4,
        hold = RepHold.NearHand,
    )
}

/**
 * Push press: a crisp knee dip from the rack followed by leg drive launching the bell to overhead lockout.
 */
val PushPressRep: ExerciseRep = ExerciseRep(
    keyframes = listOf(
        ExerciseKeyframe(KettlebellPoses.Rack.freeArm()),
        ExerciseKeyframe(
            KettlebellPoses.Rack.freeArm(),
            weight = 0.10f,
            phase = RepPhase.Rack,
            beat = RepBeat.Set,
            cueIndex = 0,
        ),
        ExerciseKeyframe(
            RackDip.freeArm(),
            weight = 0.14f,
            phase = RepPhase.Descend,
            beat = RepBeat.Ease,
            cueIndex = 1,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Press.freeArm(),
            weight = 0.12f,
            phase = RepPhase.Drive,
            passing = true,
            beat = RepBeat.Drive,
            cueIndex = 2,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Lockout.freeArm(),
            weight = 0.14f,
            phase = RepPhase.Drive,
            beat = RepBeat.Drive,
            cueIndex = 2,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Lockout.freeArm(),
            weight = 0.14f,
            phase = RepPhase.Lockout,
            beat = RepBeat.Drive,
            cueIndex = 2,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Press.freeArm(),
            weight = 0.18f,
            phase = RepPhase.Lower,
            passing = true,
            beat = RepBeat.Ease,
            cueIndex = 0,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Rack.freeArm(),
            weight = 0.18f,
            phase = RepPhase.Lower,
            beat = RepBeat.Ease,
            cueIndex = 0,
        ),
    ),
    seconds = 2.6f,
    stillAt = 5,
    hold = RepHold.NearHand,
)

/**
 * Jerk: dip, drive off the rack, drop under into a second dip at lockout, stand tall, and lower back to the rack.
 */
val JerkRep: ExerciseRep = ExerciseRep(
    keyframes = listOf(
        ExerciseKeyframe(KettlebellPoses.Rack.freeArm()),
        ExerciseKeyframe(
            KettlebellPoses.Rack.freeArm(),
            weight = 0.08f,
            phase = RepPhase.Rack,
            beat = RepBeat.Set,
            cueIndex = 0,
        ),
        ExerciseKeyframe(
            RackDip.freeArm(),
            weight = 0.14f,
            phase = RepPhase.Descend,
            beat = RepBeat.Ease,
            cueIndex = 0,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Press.freeArm(),
            weight = 0.09f,
            phase = RepPhase.Drive,
            passing = true,
            beat = RepBeat.Drive,
            cueIndex = 1,
        ),
        ExerciseKeyframe(
            JerkCatch.freeArm(),
            weight = 0.11f,
            phase = RepPhase.Drive,
            beat = RepBeat.Drive,
            cueIndex = 1,
        ),
        ExerciseKeyframe(
            JerkCatch.freeArm(),
            weight = 0.08f,
            phase = RepPhase.Lockout,
            beat = RepBeat.Drive,
            cueIndex = 1,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Lockout.freeArm(),
            weight = 0.14f,
            phase = RepPhase.Stand,
            beat = RepBeat.Set,
            cueIndex = 2,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Lockout.freeArm(),
            weight = 0.10f,
            phase = RepPhase.Stand,
            beat = RepBeat.Set,
            cueIndex = 2,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Press.freeArm(),
            weight = 0.13f,
            phase = RepPhase.Lower,
            passing = true,
            beat = RepBeat.Ease,
            cueIndex = 2,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Rack.freeArm(),
            weight = 0.13f,
            phase = RepPhase.Lower,
            beat = RepBeat.Ease,
            cueIndex = 2,
        ),
    ),
    seconds = 2.8f,
    stillAt = 5,
    hold = RepHold.NearHand,
)

/**
 * Front squat: bell tucked in the rack, free arm extended for balance, sit straight down and drive tall.
 */
val FrontSquatRep: ExerciseRep = ExerciseRep(
    keyframes = listOf(
        ExerciseKeyframe(KettlebellPoses.Rack.balancingFarArm()),
        ExerciseKeyframe(
            KettlebellPoses.Rack.balancingFarArm(),
            weight = 0.10f,
            phase = RepPhase.Rack,
            beat = RepBeat.Set,
            cueIndex = 0,
        ),
        ExerciseKeyframe(
            FrontSquatBottom.balancingFarArm(),
            weight = 0.36f,
            phase = RepPhase.Descend,
            beat = RepBeat.Ease,
            cueIndex = 1,
        ),
        ExerciseKeyframe(
            FrontSquatBottom.balancingFarArm(),
            weight = 0.10f,
            phase = RepPhase.Bottom,
            beat = RepBeat.Ease,
            cueIndex = 1,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Rack.balancingFarArm(),
            weight = 0.32f,
            phase = RepPhase.Drive,
            beat = RepBeat.Drive,
            cueIndex = 2,
        ),
        ExerciseKeyframe(
            KettlebellPoses.Rack.balancingFarArm(),
            weight = 0.12f,
            phase = RepPhase.Stand,
            beat = RepBeat.Set,
            cueIndex = 0,
        ),
    ),
    seconds = 3.2f,
    stillAt = 3,
    hold = RepHold.NearHand,
)

/**
 * Thruster: goblet squat driven directly into an overhead press, then lowered back to the chest.
 */
val ThrusterRep: ExerciseRep = run {
    val overhead = KettlebellPoses.Lockout.copy(bellAngleDeg = -170f)
    ExerciseRep(
        keyframes = listOf(
            ExerciseKeyframe(KettlebellPoses.Goblet),
            ExerciseKeyframe(
                KettlebellPoses.Goblet,
                weight = 0.08f,
                phase = RepPhase.Rack,
                beat = RepBeat.Set,
                cueIndex = 0,
            ),
            ExerciseKeyframe(
                KettlebellPoses.GobletBottom,
                weight = 0.26f,
                phase = RepPhase.Descend,
                beat = RepBeat.Ease,
                cueIndex = 1,
            ),
            ExerciseKeyframe(
                KettlebellPoses.GobletBottom,
                weight = 0.08f,
                phase = RepPhase.Bottom,
                beat = RepBeat.Ease,
                cueIndex = 1,
            ),
            ExerciseKeyframe(
                KettlebellPoses.Press.copy(bellAngleDeg = -170f),
                weight = 0.14f,
                phase = RepPhase.Drive,
                passing = true,
                beat = RepBeat.Drive,
                cueIndex = 2,
            ),
            ExerciseKeyframe(overhead, weight = 0.14f, phase = RepPhase.Press, beat = RepBeat.Drive, cueIndex = 2),
            ExerciseKeyframe(overhead, weight = 0.10f, phase = RepPhase.Lockout, beat = RepBeat.Drive, cueIndex = 2),
            ExerciseKeyframe(
                KettlebellPoses.Goblet,
                weight = 0.20f,
                phase = RepPhase.Lower,
                beat = RepBeat.Ease,
                cueIndex = 0,
            ),
        ),
        seconds = 3.0f,
        stillAt = 6,
        hold = RepHold.BothHands,
    )
}
