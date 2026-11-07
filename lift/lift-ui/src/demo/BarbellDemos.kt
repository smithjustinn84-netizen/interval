package dev.justinsmith.lift.ui.demo

import androidx.compose.ui.geometry.Rect
import dev.justinsmith.lift.ui.demo.BarbellPoses.atKnee
import dev.justinsmith.lift.ui.demo.BarbellPoses.backDeep
import dev.justinsmith.lift.ui.demo.BarbellPoses.backRack
import dev.justinsmith.lift.ui.demo.BarbellPoses.bar
import dev.justinsmith.lift.ui.demo.BarbellPoses.deep
import dev.justinsmith.lift.ui.demo.BarbellPoses.dip
import dev.justinsmith.lift.ui.demo.BarbellPoses.extended
import dev.justinsmith.lift.ui.demo.BarbellPoses.floor
import dev.justinsmith.lift.ui.demo.BarbellPoses.frontRack
import dev.justinsmith.lift.ui.demo.BarbellPoses.halfSplit
import dev.justinsmith.lift.ui.demo.BarbellPoses.hang
import dev.justinsmith.lift.ui.demo.BarbellPoses.jumping
import dev.justinsmith.lift.ui.demo.BarbellPoses.overhead
import dev.justinsmith.lift.ui.demo.BarbellPoses.overheadFromBack
import dev.justinsmith.lift.ui.demo.BarbellPoses.power
import dev.justinsmith.lift.ui.demo.BarbellPoses.pressRack
import dev.justinsmith.lift.ui.demo.BarbellPoses.pressing
import dev.justinsmith.lift.ui.demo.BarbellPoses.split
import dev.justinsmith.lift.ui.demo.BarbellPoses.standing

/** The deadlift: from the floor, push the floor away to stand tall, lower it hips back first. */
val BarbellDeadliftDemo: ExerciseDemo = barLift(
    seconds = 3.4f,
    still = 0.05f,
    phases = phases(0.1f to 0, 0.58f to 1, 1f to 2),
    0f to floor,
    0.1f to floor,
    0.3f to atKnee,
    0.48f to standing,
    0.58f to standing,
    0.78f to atKnee,
    1f to floor,
)

/** The Romanian deadlift: from standing, hips back with the bar sliding down the thighs, then drive them forward. */
val BarbellRomanianDeadliftDemo: ExerciseDemo = run {
    val bottom = bar(shin = 6f, thigh = -38f, torso = 78f, arm = -4f)
    barLift(
        seconds = 3.4f,
        still = 0.48f,
        phases = phases(0.1f to 0, 0.52f to 1, 1f to 2),
        0f to standing,
        0.1f to standing,
        0.45f to bottom,
        0.52f to bottom,
        0.9f to standing,
        1f to standing,
    )
}

/** The row: hinged over a flat back, pull the bar to the lower ribs, lower it without standing up. */
val BarbellRowDemo: ExerciseDemo = run {
    val hinged = bar(shin = 12f, thigh = -45f, torso = 70f, arm = -4f)
    val pulled = hinged.copy(upperArm = -90f, forearm = 10f)
    barLift(
        seconds = 2.4f,
        still = 0.42f,
        phases = phases(0.1f to 0, 0.46f to 1, 1f to 2),
        0f to hinged,
        0.1f to hinged,
        0.38f to pulled,
        0.46f to pulled,
        0.9f to hinged,
        1f to hinged,
    )
}

/** The clean pull: a clean's pull from the floor, finishing tall with a shrug, the arms long. */
val BarbellCleanPullDemo: ExerciseDemo = pull()

/** The snatch pull: a snatch's pull from the floor, finishing tall with a shrug, the arms long. */
val BarbellSnatchPullDemo: ExerciseDemo = pull()

/** The power clean: from the floor, extend hard and catch the bar on the shoulders above a quarter squat. */
val BarbellPowerCleanDemo: ExerciseDemo = catchFromFloor(catch = frontRack over power, stood = frontRack)

/** The clean: from the floor, extend, pull under and catch it on the shoulders in a full squat, then stand. */
val BarbellCleanDemo: ExerciseDemo = catchFromFloor(catch = frontRack over deep, stood = frontRack)

/** The power snatch: from the floor, extend and catch it locked overhead above a quarter squat. */
val BarbellPowerSnatchDemo: ExerciseDemo = catchFromFloor(catch = overhead over power, stood = overhead)

/** The snatch: from the floor, extend, pull under and catch it overhead in a full squat, then stand. */
val BarbellSnatchDemo: ExerciseDemo = catchFromFloor(catch = overhead over deep, stood = overhead)

/** The hang clean: from standing, lower to the hang, drive and shrug, catch it on the shoulders and stand. */
val BarbellHangCleanDemo: ExerciseDemo = catchFromHang(catch = frontRack over power, stood = frontRack)

/** The hang snatch: from standing, lower to the hang, jump and pull under, catch it overhead and stand. */
val BarbellHangSnatchDemo: ExerciseDemo = catchFromHang(catch = overhead over deep, stood = overhead)

/** The overhead squat: the bar locked overhead, squat with it over the middle of the foot, push up into it. */
val BarbellOverheadSquatDemo: ExerciseDemo = squat(top = overhead, seconds = 3.6f)

/** The back squat: the bar on the upper back, sit down between the heels, drive up. */
val BarbellBackSquatDemo: ExerciseDemo = squat(top = backRack, bottom = backDeep, seconds = 3.2f)

/** The front squat: the bar on the front of the shoulders, elbows high, sit straight down, drive up. */
val BarbellFrontSquatDemo: ExerciseDemo = squat(top = frontRack, seconds = 3.2f)

/** The snatch balance: from the bar on the back, dip, then drive down under it to a full squat with it locked out. */
val BarbellSnatchBalanceDemo: ExerciseDemo = barLift(
    seconds = 3.2f,
    still = 0.42f,
    phases = phases(0.1f to 0, 0.36f to 1, 1f to 2),
    0f to backRack,
    0.1f to backRack,
    0.2f to (backRack over dip),
    0.36f to (overheadFromBack over deep),
    0.46f to (overheadFromBack over deep),
    0.7f to overheadFromBack,
    0.8f to overheadFromBack,
    1f to backRack,
)

/** The overhead press: from the front of the shoulders, press straight up past the face to lockout. */
val BarbellOverheadPressDemo: ExerciseDemo = barLift(
    seconds = 2.8f,
    still = 0.52f,
    phases = phases(0.12f to 0, 0.46f to 1, 1f to 2),
    0f to pressRack,
    0.12f to pressRack,
    0.3f to pressing,
    0.46f to overhead,
    0.58f to overhead,
    0.8f to pressing,
    1f to pressRack,
)

/** The push press: a dip, a drive with the legs, then pressed the rest of the way to lockout. */
val BarbellPushPressDemo: ExerciseDemo = barLift(
    seconds = 2.6f,
    still = 0.52f,
    phases = phases(0.1f to 0, 0.36f to 1, 1f to 2),
    0f to frontRack,
    0.1f to frontRack,
    0.24f to (frontRack over dip),
    0.34f to pressing,
    0.44f to overhead,
    0.58f to overhead,
    0.8f to pressing,
    1f to frontRack,
)

/** The split jerk: dip and drive, split the feet as you punch under, recover the front foot then the back. */
val BarbellJerkDemo: ExerciseDemo = barLift(
    seconds = 3f,
    still = 0.4f,
    phases = phases(0.18f to 0, 0.44f to 1, 1f to 2),
    0f to frontRack,
    0.08f to frontRack,
    0.18f to (frontRack over dip),
    0.27f to (pressing over jumping),
    0.36f to (overhead over split),
    0.44f to (overhead over split),
    0.56f to (overhead over halfSplit),
    0.66f to overhead,
    0.74f to overhead,
    0.9f to pressing,
    1f to frontRack,
)

/** The clean and jerk: the clean to the shoulders and up out of the squat, then the split jerk, and back down. */
val BarbellCleanAndJerkDemo: ExerciseDemo = barLift(
    seconds = 5.6f,
    still = 0.6f,
    phases = phases(0.38f to 0, 0.66f to 1, 1f to 2),
    0f to floor,
    0.06f to floor,
    0.12f to atKnee,
    0.17f to hang,
    0.21f to extended,
    0.27f to (frontRack over deep),
    0.31f to (frontRack over deep),
    0.4f to frontRack,
    0.45f to (frontRack over dip),
    0.5f to (pressing over jumping),
    0.55f to (overhead over split),
    0.6f to (overhead over split),
    0.66f to (overhead over halfSplit),
    0.72f to overhead,
    0.78f to overhead,
    0.86f to frontRack,
    0.92f to standing,
    1f to floor,
)

/**
 * The bench press: lying on the bench, feet planted, lower the bar to the lower chest with the elbows under it,
 * press up and a little back.
 */
val BarbellBenchPressDemo: ExerciseDemo = run {
    val lying = Pose(shin = -10f, thigh = -90f, torso = -90f, upperArm = 180f, forearm = 180f)
    val chest = lying.copy(upperArm = 60f, forearm = 160f)
    ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = lying),
            Keyframe(at = 0.12f, pose = lying),
            Keyframe(at = 0.45f, pose = chest),
            Keyframe(at = 0.52f, pose = chest),
            Keyframe(at = 0.88f, pose = lying),
            Keyframe(at = 1f, pose = lying),
        ),
        phases = phases(0.12f to 0, 0.52f to 1, 1f to 2),
        hold = Hold.Bar,
        secondsPerRep = 2.8f,
        still = 0.12f,
        view = Rect(left = -60f, top = 60f, right = 300f, bottom = 380f),
        props = listOf(Bench(from = -30f, to = 135f, height = 62f)),
    )
}

/** A pull from the floor to a tall finish and back, the arms long throughout. */
// A keyframe table: its shares of the rep are data, as in the demos above.
@Suppress("detekt.MagicNumber")
private fun pull() = barLift(
    seconds = 2.6f,
    still = 0.42f,
    phases = phases(0.12f to 0, 0.46f to 1, 1f to 2),
    0f to floor,
    0.12f to floor,
    0.26f to atKnee,
    0.34f to hang,
    0.42f to extended,
    0.48f to extended,
    0.68f to hang,
    0.82f to atKnee,
    1f to floor,
)

/** A lift from the floor caught as [catch], stood up to [stood], then taken back down to the floor. */
// A keyframe table: its shares of the rep are data, as in the demos above.
@Suppress("detekt.MagicNumber")
private fun catchFromFloor(catch: Pose, stood: Pose) = barLift(
    seconds = 3.6f,
    still = 0.36f,
    phases = phases(0.08f to 0, 0.28f to 1, 1f to 2),
    0f to floor,
    0.08f to floor,
    0.16f to atKnee,
    0.22f to hang,
    0.28f to extended,
    0.36f to catch,
    0.42f to catch,
    0.56f to stood,
    0.64f to stood,
    0.8f to standing,
    0.9f to atKnee,
    1f to floor,
)

/** A lift from standing lowered to the hang, caught as [catch], stood up to [stood] and lowered again. */
// A keyframe table: its shares of the rep are data, as in the demos above.
@Suppress("detekt.MagicNumber")
private fun catchFromHang(catch: Pose, stood: Pose) = barLift(
    seconds = 3.2f,
    still = 0.42f,
    phases = phases(0.16f to 0, 0.3f to 1, 1f to 2),
    0f to standing,
    0.08f to standing,
    0.16f to hang,
    0.22f to hang,
    0.3f to extended,
    0.38f to catch,
    0.44f to catch,
    0.58f to stood,
    0.66f to stood,
    0.86f to standing,
    1f to standing,
)

/** A squat holding the bar as [top] holds it, down to [bottom] and back up. */
// A keyframe table: its shares of the rep are data, as in the demos above.
@Suppress("detekt.MagicNumber")
private fun squat(top: Pose, seconds: Float, bottom: Pose = deep) = barLift(
    seconds = seconds,
    still = 0.54f,
    phases = phases(0.1f to 0, 0.58f to 1, 1f to 2),
    0f to top,
    0.1f to top,
    0.5f to (top over bottom),
    0.58f to (top over bottom),
    0.92f to top,
    1f to top,
)

/** A barbell lift on the spot through these [poses], each at its share of the rep. */
private fun barLift(
    seconds: Float,
    still: Float,
    phases: List<DemoPhase>,
    vararg poses: Pair<Float, Pose>,
) = ExerciseDemo(
    keyframes = poses.map { (at, pose) -> Keyframe(at = at, pose = pose) },
    phases = phases,
    hold = Hold.Bar,
    secondsPerRep = seconds,
    still = still,
)

/** Phases to each share of the rep, each speaking to its cue: the first sets up, the second drives, the rest ease. */
internal fun phases(vararg until: Pair<Float, Int>): List<DemoPhase> = until.mapIndexed { i, (end, cue) ->
    val beat = when (i) {
        0 -> DemoBeat.Set
        1 -> DemoBeat.Drive
        else -> DemoBeat.Ease
    }
    DemoPhase(until = end, beat = beat, cue = cue)
}
