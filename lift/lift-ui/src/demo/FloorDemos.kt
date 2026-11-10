// Keyframe tables: their shares of the rep and solved angles are data, as in every demo.
@file:Suppress("detekt.MagicNumber")

package dev.justinsmith.lift.ui.demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

// The floor drills: Mark Wildman's foundations of the core, what a grappler drills before rolling, and the two hip
// openers after it. Most rest on the mat by the hips, as the mat lifts do (lying on the back the head is to the
// left); the ones that come up off the mat stand on the near foot, and the sit-out leans on the near hand.

/** Lying on the back, the knees bent and the feet flat: the shins and thighs the shoulder bridge lies with. */
private const val BENT_SHIN = -40.8f
private const val BENT_THIGH = -126.2f

/** Lying flat, the head and the hips both on the mat. */
private const val FLAT = -82f

/** Arms overhead along the mat. */
private const val OVERHEAD = 265f

private val matAnchor = Offset(185f, Rig.FLOOR)

/** A wide, low box for anything done on the mat, and a taller one for legs and arms up toward the ceiling. */
private val matView = Rect(left = -60f, top = 120f, right = 420f, bottom = 380f)
private val tallMatView = Rect(left = -60f, top = 40f, right = 420f, bottom = 380f)

/** Sitting with the knees bent and the feet flat, the chest at [torso] and the hands at the knees. */
private fun sitting(torso: Float) = Pose(shin = -35f, thigh = 229.7f, torso = torso, upperArm = 60f, forearm = 80f)

/** Balanced behind the sit bones, the knees drawn in to the chest and the feet off the mat, the hands on the shins. */
private val tucked = Pose(
    shin = -60f,
    thigh = 200f,
    torso = -15f,
    upperArm = 50f,
    forearm = 65f,
    foot = 20f,
    farFoot = 20f,
)

/**
 * The spinal rock: sitting with the chest up, rounded into a C and rocked back to the shoulder blades with the head
 * kept off the mat, then forward and all the way up to sitting tall.
 */
val SpinalRockDemo: ExerciseDemo = run {
    val tall = sitting(torso = -10f)
    val rounded = sitting(torso = 15f).copy(upperArm = 40f, forearm = 60f)
    val back = tucked.copy(torso = 5f).tipped(-75f)
    onTheMat(
        listOf(
            0f to tall,
            0.1f to tall,
            0.28f to rounded,
            0.48f to back,
            0.56f to back,
            0.78f to rounded,
            0.92f to tall,
            1f to tall,
        ),
        phases(0.1f to 0, 0.56f to 1, 1f to 2),
        seconds = 3.2f,
        still = 0.5f,
        view = tallMatView,
    )
}

/**
 * The same-side shoulder roll: from a crouch, the head tucked and rolled forward over the shoulder, the lead hand
 * reaching on ahead, round and up to the crouch again, then all the way up to standing and back down. Grounded by
 * whatever of it is on the mat, so it tumbles over on the spot.
 */
val ShoulderRollDemo: ExerciseDemo = run {
    // The chin tucked and the lead arm reaching on round ahead, so the arm and the back meet the mat, never the head.
    val ball = tucked.copy(torso = 15f, upperArm = 165f, forearm = 195f, farUpperArm = 50f, farForearm = 65f)
    val stood = Pose(shin = 0f, thigh = 0f, torso = 0f, upperArm = 0f, forearm = 8f).tipped(FULL_TURN)
    onTheMat(
        listOf(
            0f to ball.tipped(60f),
            0.1f to ball.tipped(60f),
            0.24f to ball.tipped(150f),
            0.38f to ball.tipped(240f),
            0.52f to ball.tipped(330f),
            0.62f to ball.tipped(420f),
            0.78f to stood,
            0.86f to stood,
            1f to ball.tipped(420f),
        ),
        phases(0.1f to 0, 0.62f to 1, 1f to 2),
        seconds = 3.6f,
        still = 0.38f,
        view = Rect(left = -60f, top = -70f, right = 420f, bottom = 380f),
    )
}

/**
 * The sit-up: from lying flat with the arms overhead and the ribs open, curled up and all the way up to sitting,
 * the spine extended tall at the top, and back down.
 */
val SitUpDemo: ExerciseDemo = run {
    val lying = bentKnees(torso = FLAT).arms(OVERHEAD)
    val curling = bentKnees(torso = -45f).arms(150f)
    val up = bentKnees(torso = 20f).arms(100f)
    val tall = bentKnees(torso = -5f).arms(upper = 40f, fore = 60f)
    onTheMat(
        listOf(
            0f to lying,
            0.1f to lying,
            0.3f to curling,
            0.44f to up,
            0.54f to tall,
            0.62f to tall,
            0.82f to curling,
            1f to lying,
        ),
        phases(0.1f to 0, 0.62f to 1, 1f to 2),
        seconds = 4f,
        still = 0.5f,
        view = tallMatView,
    )
}

/**
 * The V-up across: from lying long, sat up into a V with one leg lifted and the opposite hand reaching to its foot,
 * lowered, then the other leg and the other hand.
 */
val CrossVUpDemo: ExerciseDemo = run {
    val lying = long(torso = -85f, arms = OVERHEAD)
    val nearLeg = long(torso = -35f, arms = 40f).copy(
        shin = 205f,
        thigh = 205f,
        foot = 285f,
        farUpperArm = 160f,
        farForearm = 165f,
    )
    val farLeg = long(torso = -35f, arms = 160f).copy(
        forearm = 165f,
        farShin = 205f,
        farThigh = 205f,
        farFoot = 285f,
        farUpperArm = 40f,
        farForearm = 40f,
    )
    onTheMat(
        listOf(
            0f to lying,
            0.08f to lying,
            0.24f to nearLeg,
            0.32f to nearLeg,
            0.5f to lying,
            0.58f to lying,
            0.74f to farLeg,
            0.82f to farLeg,
            1f to lying,
        ),
        phases(0.08f to 0, 0.24f to 1, 0.5f to 2, 0.74f to 1, 1f to 2),
        seconds = 4f,
        still = 0.28f,
        view = tallMatView,
    )
}

/**
 * The hip escape: from the back with the knees bent, turned onto the shoulder with the hips pushed away from the
 * feet (the legs long, the hands framing), then the knees brought back in.
 */
val HipEscapeDemo: ExerciseDemo = run {
    val bent = bentKnees(torso = -85f).arms(upper = 120f, fore = 150f)
    val escaped = Pose(
        shin = -78f,
        thigh = -104f,
        torso = -76f,
        upperArm = 112f,
        forearm = 112f,
        farShin = BENT_SHIN,
        farThigh = BENT_THIGH,
    )
    onTheMat(
        listOf(0f to bent, 0.1f to bent, 0.4f to escaped, 0.6f to escaped, 0.9f to bent, 1f to bent),
        phases(0.1f to 0, 0.6f to 1, 1f to 2),
        seconds = 2.4f,
        still = 0.5f,
    )
}

/**
 * The bridge and roll (the upa): from the back with the heels close to the hips, bridged high off the feet, then
 * turned over the shoulder, the near arm reaching across, and back down. It stands on the near foot, as the
 * shoulder bridge does.
 */
val BridgeAndRollDemo: ExerciseDemo = run {
    val lying = Pose(shin = BENT_SHIN, thigh = BENT_THIGH, torso = -80f, upperArm = 90f, forearm = 90f)
    val bridged = Pose(shin = -27.9f, thigh = -102f, torso = -100f, upperArm = 90f, forearm = 90f)
    val turned = bridged.copy(upperArm = 200f, forearm = 210f, farThigh = -115f, farShin = -35f)
    ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = lying),
            Keyframe(at = 0.12f, pose = lying),
            Keyframe(at = 0.3f, pose = bridged),
            Keyframe(at = 0.45f, pose = turned),
            Keyframe(at = 0.6f, pose = turned),
            Keyframe(at = 0.75f, pose = bridged),
            Keyframe(at = 0.9f, pose = lying),
            Keyframe(at = 1f, pose = lying),
        ),
        phases = phases(0.12f to 0, 0.3f to 1, 1f to 2),
        hold = Hold.Free,
        secondsPerRep = 3.2f,
        still = 0.3f,
        view = Rect(left = -80f, top = 120f, right = 320f, bottom = 380f),
    )
}

/**
 * The technical stand-up: sitting up on the far hand with the near arm guarding the face, the hips lifted off the
 * near foot and hand, the far leg swung back under to kneel, then up into a stance; and every step back down. The
 * near foot stays planted, as the get-up's does, through the same solved steps.
 */
val TechnicalStandUpDemo: ExerciseDemo = run {
    val guard = Pose(shin = 0f, thigh = 0f, torso = 0f, upperArm = 50f, forearm = 140f)
    fun guarding(pose: Pose) = pose.copy(upperArm = guard.upperArm, forearm = guard.forearm)
    val posted = guarding(
        Pose(
            shin = -40f,
            thigh = -126f,
            torso = -25f,
            upperArm = 0f,
            forearm = 0f,
            farShin = -92f,
            farThigh = -92f,
            farFoot = -90f,
            farUpperArm = -5f,
            farForearm = -5f,
        ),
    )
    val lifted = guarding(
        Pose(
            shin = 15f,
            thigh = -75f,
            torso = -80f,
            upperArm = 0f,
            forearm = 0f,
            farShin = -60f,
            farThigh = -60f,
            farFoot = -60f,
            farUpperArm = -5f,
            farForearm = -5f,
        ),
    )
    val legUp = lifted.copy(farShin = 20f, farThigh = -100f, farFoot = 0f)
    val legUnder = lifted.copy(shin = 12f, thigh = -78f, farShin = 95f, farThigh = -60f, farFoot = 20f)
    val kneeling = lifted.copy(shin = 10f, thigh = -80f, farShin = 105f, farThigh = 18f, farFoot = 60f)
    val halfKneeling = kneeling.copy(torso = 0f, farUpperArm = guard.upperArm, farForearm = guard.forearm)
    val rising = halfKneeling.copy(shin = 4f, thigh = -22f, farShin = 85f, farThigh = 28f, farFoot = 10f)
    // A staggered stance, the far foot back on its toes where the step through left it.
    val stance = Pose(
        shin = 12f,
        thigh = -25f,
        torso = 12f,
        upperArm = 30f,
        forearm = 140f,
        farShin = 40f,
        farThigh = 18f,
        farFoot = 20f,
    )
    val up = listOf(posted, lifted, legUp, legUnder, kneeling, halfKneeling, rising, stance)
    val upTimes = listOf(0f, 0.08f, 0.14f, 0.2f, 0.26f, 0.34f, 0.41f, 0.48f)
    val climb = up.zip(upTimes) { pose, at -> Keyframe(at = at, pose = pose) }
    val descent = up.zip(upTimes) { pose, at -> Keyframe(at = 1f - at, pose = pose) }.reversed()
    ExerciseDemo(
        // Up by 0.48, a moment in the stance, then back down the same steps.
        keyframes = climb + descent,
        phases = listOf(
            DemoPhase(until = 0.04f, beat = DemoBeat.Set, cue = 0),
            DemoPhase(until = 0.26f, beat = DemoBeat.Drive, cue = 1),
            DemoPhase(until = 0.52f, beat = DemoBeat.Drive, cue = 2),
            DemoPhase(until = 1f, beat = DemoBeat.Ease, cue = 2),
        ),
        hold = Hold.Free,
        secondsPerRep = 7f,
        still = 0.48f,
        view = Rect(left = -80f, top = -40f, right = 330f, bottom = 380f),
    )
}

/**
 * The sit-out (a sit-through): from the hands and knees, the far leg drawn up and threaded through under the body
 * to lie long in front, the hips dropped over the near knee and the far arm up, then back. It leans on the near
 * hand, which stays where it is.
 */
val SitOutDemo: ExerciseDemo = run {
    val kneeling = Pose(
        shin = 90f,
        thigh = 0f,
        torso = 78.7f,
        upperArm = 0f,
        forearm = 0f,
        foot = 180f,
        farFoot = 180f,
    )
    val through = Pose(
        shin = 90f,
        thigh = 47f,
        torso = 65f,
        upperArm = 0f,
        forearm = 0f,
        foot = 180f,
        farShin = -80f,
        farThigh = -80f,
        farFoot = -80f,
        farUpperArm = 165f,
        farForearm = 175f,
    )
    // On the way the far knee comes up and over before the leg unfolds in front, so the foot never drags through.
    val drawn = kneeling.between(through, 0.3f).copy(farThigh = -100f, farShin = 100f, farFoot = 190f)
    val threading = kneeling.between(through, 0.7f).copy(farThigh = -140f, farShin = 0f, farFoot = 0f)
    ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = kneeling),
            Keyframe(at = 0.1f, pose = kneeling),
            Keyframe(at = 0.2f, pose = drawn),
            Keyframe(at = 0.3f, pose = threading),
            Keyframe(at = 0.42f, pose = through),
            Keyframe(at = 0.58f, pose = through),
            Keyframe(at = 0.7f, pose = threading),
            Keyframe(at = 0.8f, pose = drawn),
            Keyframe(at = 0.9f, pose = kneeling),
            Keyframe(at = 1f, pose = kneeling),
        ),
        phases = phases(0.1f to 0, 0.58f to 1, 1f to 2),
        hold = Hold.Free,
        secondsPerRep = 3.6f,
        still = 0.5f,
        root = Root.Hand,
        anchor = Offset(295f, Rig.FLOOR - Rig.HAND),
        view = Rect(left = -10f, top = 100f, right = 410f, bottom = 380f),
    )
}

/**
 * The butterfly: sitting tall with the soles of the feet together, each knee fallen open to the mat (seen from the
 * side, the leg folded flat with the foot in at the hips), then folded forward over the feet with a long back.
 */
val ButterflyDemo: ExerciseDemo = run {
    val tall = Pose(
        shin = 70.8f,
        thigh = -105.4f,
        torso = -3f,
        upperArm = 25f,
        forearm = 60f,
        foot = 10f,
        farFoot = 10f,
    )
    val folded = tall.copy(torso = 28f, upperArm = 60f, forearm = 70f)
    onTheMat(
        listOf(0f to tall, 0.1f to tall, 0.4f to folded, 0.6f to folded, 0.9f to tall, 1f to tall),
        phases(0.1f to 0, 0.6f to 1, 1f to 2),
        seconds = 5f,
        still = 0.5f,
    )
}

/**
 * The frog: on the forearms with the knees wide, the shins out to the sides in line with them (seen from the side,
 * along the mat), the hips eased back toward the heels and forward again with each breath.
 */
val FrogStretchDemo: ExerciseDemo = run {
    fun frog(thigh: Float, torso: Float) = Pose(
        shin = 90f,
        thigh = thigh,
        torso = torso,
        upperArm = 0f,
        forearm = 95f,
        foot = 180f,
        farFoot = 180f,
    )
    held(frog(thigh = -10f, torso = 104.7f), frog(thigh = -16f, torso = 102f))
}

/** Lying on the back at [torso] with the knees bent and the feet flat on the mat. */
private fun bentKnees(torso: Float) =
    Pose(shin = BENT_SHIN, thigh = BENT_THIGH, torso = torso, upperArm = 0f, forearm = 0f)

/** On the back at [torso], both legs long on the mat with the toes pointed, both arms straight at [arms]. */
private fun long(torso: Float, arms: Float) = Pose(
    shin = 270f,
    thigh = 270f,
    torso = torso,
    upperArm = arms,
    forearm = arms,
    foot = 350f,
    farFoot = 350f,
)

/** This pose with both arms straight at [angle], or at [upper] and [fore]. */
private fun Pose.arms(angle: Float) = arms(upper = angle, fore = angle)

private fun Pose.arms(upper: Float, fore: Float) =
    copy(upperArm = upper, forearm = fore, farUpperArm = upper, farForearm = fore)

/** This pose [share] of the way to [other], every joint alike. */
private fun Pose.between(other: Pose, share: Float): Pose =
    Pose.of(FloatArray(Pose.JOINTS) { angles()[it] + (other.angles()[it] - angles()[it]) * share })

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

/** A demo on the mat from these keyframes, grounded by the hips. */
private fun onTheMat(
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

/** A position [held] on the mat, breathing to [breath] and back. */
private fun held(held: Pose, breath: Pose) = ExerciseDemo(
    keyframes = listOf(
        Keyframe(at = 0f, pose = held),
        Keyframe(at = 0.5f, pose = breath),
        Keyframe(at = 1f, pose = held),
    ),
    phases = phases(0.5f to 1, 1f to 2),
    hold = Hold.Free,
    secondsPerRep = 4f,
    still = 0f,
    root = Root.Hips,
    anchor = matAnchor,
    view = matView,
)
