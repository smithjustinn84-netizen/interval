package dev.justinsmith.lift.ui.demo

import dev.justinsmith.interval.ui.ExerciseRep

internal val rackedLungeKeyframes: List<Keyframe> = run {
    val standing = rackPose.stillHand()
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
    listOf(
        Keyframe(at = 0f, pose = standing),
        Keyframe(at = 0.1f, pose = standing),
        Keyframe(at = 0.28f, pose = stepping),
        Keyframe(at = 0.48f, pose = bottom),
        Keyframe(at = 0.56f, pose = bottom),
        Keyframe(at = 0.76f, pose = stepping),
        Keyframe(at = 0.92f, pose = standing),
        Keyframe(at = 1f, pose = standing),
    )
}

internal val rackedLungePhases: List<DemoPhase> = listOf(
    DemoPhase(until = 0.1f, beat = DemoBeat.Set, cue = 0),
    DemoPhase(until = 0.56f, beat = DemoBeat.Ease, cue = 1),
    DemoPhase(until = 0.92f, beat = DemoBeat.Drive, cue = 2),
    DemoPhase(until = 1f, beat = DemoBeat.Set, cue = 0),
)

internal const val RACKED_LUNGE_SECONDS = 3.4f
internal const val RACKED_LUNGE_STILL = 0.52f

/** The racked lunge: bell in the rack, step back and lower the back knee toward the floor, drive up. */
val RackedLungeDemo: ExerciseRep = repOf(
    keyframes = rackedLungeKeyframes,
    phases = rackedLungePhases,
    hold = Hold.NearHand,
    secondsPerRep = RACKED_LUNGE_SECONDS,
    still = RACKED_LUNGE_STILL,
)

/** The walking lunge: the racked lunge's stride with the bell hanging at the side, hips over the spot. */
val WalkingLungeDemo: ExerciseRep = run {
    val atSide = Pose(shin = 0f, thigh = 0f, torso = 0f, upperArm = 0f, forearm = 0f).stillHand()
    repOf(
        keyframes = rackedLungeKeyframes.map { it.copy(pose = atSide.standingAs(it.pose)) },
        phases = rackedLungePhases,
        hold = Hold.NearHand,
        secondsPerRep = RACKED_LUNGE_SECONDS,
        still = RACKED_LUNGE_STILL,
        root = Root.Hips,
    )
}

/** The row: hinged over a flat back with the free hand braced on a bench, pull the elbow back to the hip. */
val RowDemo: ExerciseRep = run {
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
    repOf(
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
        props = listOf(bench(from = 236f, to = 340f, height = 78f)),
    )
}
