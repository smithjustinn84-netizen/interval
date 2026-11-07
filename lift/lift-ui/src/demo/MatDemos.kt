// Keyframe tables: their shares of the rep and solved angles are data, as in every demo.
@file:Suppress("detekt.MagicNumber")

package dev.justinsmith.lift.ui.demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

// The mat Pilates lifts, nearly all on the floor, so each is grounded by its hips over whatever rests on the mat.
// Lying on the back the head is to the left: a leg's angle of 270 lies along the mat toward the feet, 180 points
// at the ceiling, 240 is a leg held long and low. The far leg takes the same numbers as the near one.

/** Lying flat: the torso tipped just enough that the head and the hips both rest on the mat. */
private const val FLAT = -85f

/** The head and shoulders curled up off the mat. */
private const val CURLED = -65f

/** A leg along the mat, a leg long and low, a leg up at the ceiling. */
private const val DOWN = 270f
private const val LONG = 240f
private const val UP = 180f

/** A knee hugged in: the thigh toward the chest, the shin level. */
private const val HUGGED_THIGH = 150f
private const val HUGGED_SHIN = 270f

/** Arms overhead along the mat, and arms long by the sides toward the feet. */
private const val OVERHEAD = 270f
private const val BY_SIDES = 90f

/** Lying face down, the torso tipped so the hips rest on the mat. */
private const val PRONE = 88f

private val matAnchor = Offset(185f, Rig.FLOOR)

/** A wide, low box for anything done on the mat. */
private val matView = Rect(left = -60f, top = 120f, right = 420f, bottom = 380f)

/** A taller one, for a leg or the hips up toward the ceiling. */
private val tallMatView = Rect(left = -60f, top = 40f, right = 420f, bottom = 380f)

/** The hundred: head and shoulders curled up, the legs up long, the arms pumping by the sides. */
val HundredDemo: ExerciseDemo = run {
    val up = back(torso = CURLED, legs = 225f, arms = 100f)
    val down = up.arms(86f)
    mat(
        listOf(0f to up, 0.25f to down, 0.5f to up, 0.75f to down, 1f to up),
        phases(0.5f to 1, 1f to 2),
        seconds = 1f,
        still = 0.25f,
    )
}

/** The roll-up: from lying long, arms overhead, peeled up through sitting to reach past the toes, and back down. */
val RollUpDemo: ExerciseDemo = run {
    val lying = back(torso = FLAT, legs = DOWN, arms = OVERHEAD).flexed()
    val peeling = lying.copy(torso = -45f).arms(UP)
    val sitting = lying.copy(torso = 10f).arms(100f)
    val folded = lying.copy(torso = 55f).arms(80f)
    mat(
        listOf(
            0f to lying,
            0.12f to lying,
            0.28f to peeling,
            0.42f to sitting,
            0.54f to folded,
            0.62f to folded,
            0.75f to sitting,
            0.88f to peeling,
            1f to lying,
        ),
        phases(0.12f to 0, 0.62f to 1, 1f to 2),
        seconds = 5f,
        still = 0.56f,
    )
}

/**
 * Single-leg circles: on the back, one leg up at the ceiling circling, the other long on the mat. Seen from the
 * side a circle is the leg swinging toward the head and away.
 */
val SingleLegCirclesDemo: ExerciseDemo = run {
    fun circling(leg: Float) = back(torso = FLAT, legs = DOWN, arms = 88f).near(leg)
    mat(
        listOf(
            0f to circling(UP),
            0.25f to circling(200f),
            0.5f to circling(UP),
            0.75f to circling(160f),
            1f to circling(UP),
        ),
        phases(0.5f to 1, 1f to 2),
        seconds = 3f,
        still = 0.25f,
        view = tallMatView,
    )
}

/** Rolling like a ball: balanced behind the sit bones, knees hugged in, rolled back to the shoulder blades and up. */
val RollingLikeABallDemo: ExerciseDemo = run {
    val ball = tuck(torso = -15f, arms = 50f, hands = 65f)
    rolling(ball, listOf(0f to ball, 0.15f to ball), seconds = 2.4f)
}

/** The single-leg stretch: head up, one knee hugged in as the other leg stretches out long, then switch. */
val SingleLegStretchDemo: ExerciseDemo = run {
    val nearIn = back(torso = CURLED, legs = LONG, arms = 95f).hugNear().copy(forearm = 110f, farForearm = 110f)
    val farIn = back(torso = CURLED, legs = LONG, arms = 95f).hugFar().copy(forearm = 110f, farForearm = 110f)
    switching(nearIn, farIn, seconds = 2f)
}

/** The double-leg stretch: both knees hugged in, then the arms and legs stretched out long, the arms circled back. */
val DoubleLegStretchDemo: ExerciseDemo = run {
    val hug = back(torso = CURLED, legs = LONG, arms = 95f).hugNear().hugFar().copy(forearm = 110f, farForearm = 110f)
    val long = back(torso = CURLED, legs = LONG, arms = 245f)
    val round = long.arms(BY_SIDES)
    mat(
        listOf(0f to hug, 0.1f to hug, 0.35f to long, 0.45f to long, 0.7f to round, 1f to hug),
        phases(0.1f to 0, 0.45f to 1, 1f to 2),
        seconds = 3.2f,
        still = 0.4f,
    )
}

/** Scissors: both legs up, one pulled toward the face with two pulses as the other lowers, then switch. */
val ScissorsDemo: ExerciseDemo = run {
    fun scissor(up: Float, down: Float, near: Boolean): Pose {
        val legs = back(torso = CURLED, legs = down, arms = 165f).copy(forearm = 105f, farForearm = 105f)
        return if (near) legs.near(up) else legs.far(up)
    }
    val near = scissor(up = 150f, down = LONG, near = true)
    val nearPulse = scissor(up = 140f, down = 250f, near = true)
    val far = scissor(up = 150f, down = LONG, near = false)
    val farPulse = scissor(up = 140f, down = 250f, near = false)
    mat(
        listOf(
            0f to near,
            0.1f to nearPulse,
            0.2f to near,
            0.5f to far,
            0.6f to farPulse,
            0.7f to far,
            1f to near,
        ),
        phases(0.5f to 1, 1f to 2),
        seconds = 2.4f,
        still = 0.1f,
        view = tallMatView,
    )
}

/** The criss-cross: hands behind the head, an elbow turned to the opposite knee as it comes in, then the other. */
val CrissCrossDemo: ExerciseDemo = run {
    val hands = back(torso = -60f, legs = LONG, arms = 200f).copy(forearm = 300f, farForearm = 300f)
    switching(hands.copy(torso = -55f).hugNear(), hands.copy(torso = -55f).hugFar(), seconds = 2.4f)
}

/** The spine stretch forward: sitting tall, legs long and feet flexed, rounded forward reaching ahead, stacked up. */
val SpineStretchForwardDemo: ExerciseDemo = run {
    val tall = seated.arms(BY_SIDES)
    val round = seated.copy(torso = 45f).arms(100f)
    mat(
        listOf(0f to tall, 0.1f to tall, 0.45f to round, 0.6f to round, 1f to tall),
        phases(0.1f to 0, 0.6f to 1, 1f to 2),
        seconds = 4f,
        still = 0.52f,
        view = tallMatView,
    )
}

/**
 * The saw: sitting tall with the arms out to the sides (pointing at the eye and away, so they look short), then
 * twisted and folded, the front hand reaching past the far foot and the back one behind.
 */
val SawDemo: ExerciseDemo = run {
    val tall = seated.arms(BY_SIDES).copy(reach = 0.15f)
    val saw = seated.copy(torso = 40f, upperArm = 95f, forearm = 95f, farUpperArm = -120f, farForearm = -120f)
    mat(
        listOf(0f to tall, 0.1f to tall, 0.4f to saw, 0.55f to saw, 0.9f to tall, 1f to tall),
        phases(0.1f to 0, 0.55f to 1, 1f to 2),
        seconds = 3.6f,
        still = 0.48f,
        view = tallMatView,
    )
}

/** The swan: face down, hands under the shoulders, the chest lifted by lengthening the spine, lowered slowly. */
val SwanDemo: ExerciseDemo = run {
    // The hands stay where they are on the mat, so the arms were solved for each lift of the chest.
    val down = front(torso = PRONE, legs = 90f).arms(upper = 153.9f, fore = 3.5f)
    val low = front(torso = 72f, legs = 90f).arms(upper = 102.5f, fore = -21.6f)
    val mid = front(torso = 58f, legs = 90f).arms(upper = 77.4f, fore = -7.9f)
    val up = front(torso = 45f, legs = 90f).arms(upper = 44.3f, fore = 27.2f)
    mat(
        listOf(
            0f to down, 0.1f to down, 0.22f to low, 0.32f to mid, 0.42f to up, 0.52f to up, 0.68f to mid, 0.8f to low,
            1f to down,
        ),
        phases(0.1f to 0, 0.52f to 1, 1f to 2),
        seconds = 3.6f,
        still = 0.48f,
    )
}

/** The single-leg kick: on the forearms, the chest lifted, one heel kicked to the glutes twice, then the other. */
val SingleLegKickDemo: ExerciseDemo = run {
    val prone = front(torso = 65f, legs = 90f).arms(upper = 0f, fore = 90f)
    fun near(shin: Float) = prone.copy(shin = shin, foot = shin + 90f)
    fun far(shin: Float) = prone.copy(farShin = shin, farFoot = shin + 90f)
    mat(
        listOf(
            0f to prone,
            0.1f to near(200f),
            0.17f to near(170f),
            0.25f to near(200f),
            0.4f to prone,
            0.5f to prone,
            0.6f to far(200f),
            0.67f to far(170f),
            0.75f to far(200f),
            0.9f to prone,
            1f to prone,
        ),
        phases(0.5f to 1, 1f to 2),
        seconds = 2.4f,
        still = 0.25f,
    )
}

/**
 * The side kick series: lying on one side propped on the forearm, the top leg lifted to hip height and swung.
 * Seen from the front the swing comes toward the eye and away, so it shows as the leg rising and falling, the
 * foot flexed forward and pointed back.
 */
val SideKickSeriesDemo: ExerciseDemo = run {
    val side = Pose(
        shin = DOWN,
        thigh = DOWN,
        torso = -70f,
        upperArm = 0f,
        forearm = -90f,
        foot = 0f,
        farShin = -90f,
        farThigh = -90f,
        farFoot = 0f,
        farUpperArm = 100f,
        farForearm = 100f,
    )
    val forward = side.copy(farShin = -112f, farThigh = -112f, farFoot = -80f)
    val back = side.copy(farShin = -98f, farThigh = -98f, farFoot = 10f)
    mat(
        listOf(0f to side, 0.1f to side, 0.35f to forward, 0.65f to back, 0.9f to side, 1f to side),
        phases(0.1f to 0, 0.65f to 1, 1f to 2),
        seconds = 2.4f,
        still = 0.35f,
    )
}

/** The teaser: from lying long, rolled up to balance in a V, the arms reaching for the feet, and down with control. */
val TeaserDemo: ExerciseDemo = run {
    val lying = back(torso = FLAT, legs = DOWN, arms = OVERHEAD)
    val vee = back(torso = -30f, legs = 210f, arms = 150f)
    mat(
        listOf(0f to lying, 0.1f to lying, 0.4f to vee, 0.55f to vee, 0.9f to lying, 1f to lying),
        phases(0.1f to 0, 0.55f to 1, 1f to 2),
        seconds = 4f,
        still = 0.48f,
        view = tallMatView,
    )
}

/** Swimming: face down, arms and legs long and lifted, fluttering opposite arm and leg. */
val SwimmingDemo: ExerciseDemo = run {
    val lifted = front(torso = 80f, legs = 98f)
    val nearArm = lifted.copy(
        upperArm = 110f,
        forearm = 110f,
        farUpperArm = 94f,
        farForearm = 94f,
        shin = 94f,
        thigh = 94f,
        foot = 184f,
        farShin = 104f,
        farThigh = 104f,
        farFoot = 194f,
    )
    val farArm = lifted.copy(
        upperArm = 94f,
        forearm = 94f,
        farUpperArm = 110f,
        farForearm = 110f,
        shin = 104f,
        thigh = 104f,
        foot = 194f,
        farShin = 94f,
        farThigh = 94f,
        farFoot = 184f,
    )
    mat(
        listOf(0f to nearArm, 0.5f to farArm, 1f to nearArm),
        phases(0.5f to 1, 1f to 2),
        seconds = 1f,
        still = 0f,
    )
}

/** The shoulder bridge: the hips bridged up, one leg lifted to the ceiling and lowered long, the hips level. */
val ShoulderBridgeDemo: ExerciseDemo = run {
    val lying = Pose(shin = -40.8f, thigh = -126.2f, torso = -80f, upperArm = 90f, forearm = 90f)
    val bridged = Pose(shin = -27.9f, thigh = -102f, torso = -100f, upperArm = 90f, forearm = 90f)
    fun leg(angle: Float) = bridged.copy(farShin = angle, farThigh = angle, farFoot = 90f + angle)
    val up = leg(-180f)
    val lowered = leg(-100f)
    // The knee drawn up first, so the leg unfolds to the ceiling rather than swinging up straight from the foot.
    val kneeUp = bridged.copy(farShin = -60f, farThigh = -160f, farFoot = 20f)
    ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = lying),
            Keyframe(at = 0.12f, pose = bridged),
            Keyframe(at = 0.18f, pose = kneeUp),
            Keyframe(at = 0.26f, pose = up),
            Keyframe(at = 0.42f, pose = lowered),
            Keyframe(at = 0.58f, pose = up),
            Keyframe(at = 0.66f, pose = kneeUp),
            Keyframe(at = 0.74f, pose = bridged),
            Keyframe(at = 0.88f, pose = lying),
            Keyframe(at = 1f, pose = lying),
        ),
        phases = phases(0.12f to 0, 0.6f to 1, 1f to 2),
        hold = Hold.Free,
        secondsPerRep = 5f,
        still = 0.26f,
        view = Rect(left = -80f, top = 40f, right = 320f, bottom = 380f),
    )
}

/** The seal: balanced holding the ankles, the feet clapped three times, rolled back and up to clap again. */
val SealDemo: ExerciseDemo = run {
    val ball = tuck(torso = -5f, arms = 38f, hands = 38f)
    val clap = ball.copy(farThigh = 190f)
    rolling(
        ball,
        listOf(0f to ball, 0.06f to clap, 0.12f to ball, 0.18f to clap, 0.24f to ball, 0.3f to clap, 0.36f to ball),
        seconds = 3.2f,
    )
}

/** The leg pull front: in a high plank, one leg lifted without the hips shifting, lowered, then the other. */
val LegPullFrontDemo: ExerciseDemo = run {
    val plank = Pose(
        shin = 73.2f,
        thigh = 73.2f,
        torso = 73.2f,
        upperArm = -5.5f,
        forearm = 10.1f,
        foot = 76.9f,
        farFoot = 76.9f,
    )
    val nearUp = plank.copy(shin = 92f, thigh = 92f, foot = 172f)
    val farUp = plank.copy(farShin = 92f, farThigh = 92f, farFoot = 172f)
    ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = plank),
            Keyframe(at = 0.1f, pose = plank),
            Keyframe(at = 0.25f, pose = nearUp),
            Keyframe(at = 0.32f, pose = nearUp),
            Keyframe(at = 0.5f, pose = plank),
            Keyframe(at = 0.6f, pose = plank),
            Keyframe(at = 0.75f, pose = farUp),
            Keyframe(at = 0.82f, pose = farUp),
            Keyframe(at = 1f, pose = plank),
        ),
        phases = phases(0.1f to 0, 0.32f to 1, 1f to 2),
        hold = Hold.Free,
        secondsPerRep = 3.6f,
        still = 0.28f,
        root = Root.Hand,
        anchor = Offset(300f, Rig.FLOOR - Rig.HAND),
        view = Rect(left = -10f, top = 120f, right = 390f, bottom = 380f),
    )
}

/** On the back at [torso], both legs straight at [legs] with the toes pointed along them, both arms at [arms]. */
private fun back(torso: Float, legs: Float, arms: Float) = Pose(
    shin = legs,
    thigh = legs,
    torso = torso,
    upperArm = arms,
    forearm = arms,
    farShin = legs,
    farThigh = legs,
    foot = pointed(legs),
    farFoot = pointed(legs),
)

/** Face down at [torso], both legs straight back at [legs], the toes pointed. */
private fun front(torso: Float, legs: Float) = Pose(
    shin = legs,
    thigh = legs,
    torso = torso,
    upperArm = 0f,
    forearm = 0f,
    farShin = legs,
    farThigh = legs,
    foot = legs + 90f,
    farFoot = legs + 90f,
)

/** The toes of a straight leg at [leg], pointed a little short of straight on along it. */
private fun pointed(leg: Float) = leg + 80f

/** This pose with both feet flexed, the toes back toward the shins. */
private fun Pose.flexed() = copy(foot = shin, farFoot = farShin)

/** This pose with both arms straight at [angle]. */
private fun Pose.arms(angle: Float) = arms(upper = angle, fore = angle)

/** This pose with both arms at [upper] and [fore]. */
private fun Pose.arms(upper: Float, fore: Float) =
    copy(upperArm = upper, forearm = fore, farUpperArm = upper, farForearm = fore)

/** The near leg straight at [leg], or the far one. */
private fun Pose.near(leg: Float) = copy(shin = leg, thigh = leg, foot = pointed(leg))

private fun Pose.far(leg: Float) = copy(farShin = leg, farThigh = leg, farFoot = pointed(leg))

/** The near knee hugged in, or the far one. */
private fun Pose.hugNear() = copy(shin = HUGGED_SHIN, thigh = HUGGED_THIGH, foot = pointed(HUGGED_SHIN))

private fun Pose.hugFar() = copy(farShin = HUGGED_SHIN, farThigh = HUGGED_THIGH, farFoot = pointed(HUGGED_SHIN))

/**
 * Balanced behind the sit bones at [torso], the knees drawn in to the chest and the feet off the mat, the
 * hands at [arms] and [hands] on the shins.
 */
private fun tuck(torso: Float, arms: Float, hands: Float) = Pose(
    shin = -60f,
    thigh = 200f,
    torso = torso,
    upperArm = arms,
    forearm = hands,
    farShin = -60f,
    farThigh = 200f,
    foot = 20f,
    farFoot = 20f,
)

/**
 * This pose tipped over as one by [degrees], forward for positive: every joint turns with it, the legs and torso
 * from up and the arms from down the other way.
 */
private fun Pose.tipped(degrees: Float) = copy(
    shin = shin + degrees,
    thigh = thigh + degrees,
    torso = torso + degrees,
    upperArm = upperArm - degrees,
    forearm = forearm - degrees,
    farShin = farShin + degrees,
    farThigh = farThigh + degrees,
    farFoot = farFoot + degrees,
    farUpperArm = farUpperArm - degrees,
    farForearm = farForearm - degrees,
    foot = foot + degrees,
)

/** A rep from [ball] through what it does [first] (ending on [ball]), rolled back to the shoulder blades and up. */
private fun rolling(ball: Pose, first: List<Pair<Float, Pose>>, seconds: Float): ExerciseDemo {
    val start = first.last().first
    val back = ball.tipped(-70f)
    val rest = 1f - start
    return mat(
        first + listOf(
            start + rest * 0.4f to back,
            start + rest * 0.55f to back,
            1f to ball,
        ),
        phases(start to 0, start + rest * 0.55f to 1, 1f to 2),
        seconds = seconds,
        still = start + rest * 0.45f,
        view = tallMatView,
    )
}

/** A rep switching from [one] to [other] and back, holding each a moment. */
private fun switching(one: Pose, other: Pose, seconds: Float) = mat(
    listOf(0f to one, 0.1f to one, 0.4f to other, 0.6f to other, 0.9f to one, 1f to one),
    phases(0.1f to 0, 0.6f to 1, 1f to 2),
    seconds = seconds,
    still = 0.5f,
)

/** A demo on the mat from these keyframes, grounded by the hips. */
private fun mat(
    keys: List<Pair<Float, Pose>>,
    phases: List<DemoPhase>,
    seconds: Float,
    still: Float,
    view: Rect = matView,
) = ExerciseDemo(
    keyframes = keys.map { (at, pose) -> Keyframe(at, pose) },
    phases = phases,
    hold = Hold.Free,
    secondsPerRep = seconds,
    still = still,
    root = Root.Hips,
    anchor = matAnchor,
    view = view,
)
