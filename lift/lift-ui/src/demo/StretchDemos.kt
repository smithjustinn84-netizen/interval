// Keyframe tables: their shares of the rep and solved angles are data, as in every demo.
@file:Suppress("detekt.MagicNumber")

package dev.justinsmith.lift.ui.demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2

// The stretches of the shipped mobility flows. A held one breathes: its rep is a breath between the position and
// a touch deeper, so a still of it is the position itself. The ones on all fours keep the hands and the knees
// where they are (grounded by the hand, the knees solved to stay put); the kneeling ones stand on the near foot
// with the far knee solved down to the floor; the rest lie or sit grounded by the hips, as the mat lifts are.

/** Kneeling: the shin along the mat behind the knee, the top of the foot down. */
private const val KNEELING_SHIN = 90f
private const val KNEELING_FOOT = 180f

/** Lying face down, the torso tipped so the hips rest on the mat, and lying on the back, the head to the left. */
private const val PRONE = 90f
private const val FLAT = -85f

/** A leg along the mat toward the feet, lying on the back, and its toes pointed. */
private const val DOWN = 270f
private const val DOWN_POINTED = 350f

private val matAnchor = Offset(185f, Rig.FLOOR)

/** A wide, low box for anything done on the mat. */
private val matView = Rect(left = -60f, top = 120f, right = 420f, bottom = 380f)

/** A little taller, for a kneeling figure's head. */
private val kneelingView = Rect(left = -10f, top = 100f, right = 410f, bottom = 380f)

/** The hip flexor lunge: the back knee down, the front foot forward, the hips eased forward under tall ribs. */
val HipFlexorLungeDemo: ExerciseDemo = run {
    val kneeling = Pose(
        shin = 25f,
        thigh = -95f,
        torso = -5f,
        upperArm = 8f,
        forearm = 8f,
        farShin = KNEELING_SHIN,
        farThigh = 48.7f,
        farFoot = KNEELING_FOOT,
    )
    val eased = kneeling.copy(shin = 28f, thigh = -100f, farThigh = 56.5f)
    held(kneeling, eased, root = Root.Ankle, anchor = Offset(300f, Rig.FLOOR), view = kneelingView)
}

/** The deep squat: heels down, the chest up, the elbows inside the knees pressing them out. */
val DeepSquatDemo: ExerciseDemo = held(
    Pose(shin = 36f, thigh = -112f, torso = 30f, upperArm = 45f, forearm = 110f),
    Pose(shin = 36f, thigh = -112f, torso = 26f, upperArm = 48f, forearm = 112f),
    root = Root.Ankle,
    anchor = Offset(Rig.ANKLE_X, Rig.FLOOR),
    view = Rig.view,
)

/**
 * The world's greatest stretch: a long lunge with the hands on the floor inside the front foot, the near elbow
 * dropped toward the instep, then that arm turned and reached to the ceiling. The back leg is on its toes.
 */
val WorldsGreatestStretchDemo: ExerciseDemo = run {
    val hands = Pose(
        shin = 45f,
        thigh = -100f,
        torso = 65f,
        upperArm = -39.5f,
        forearm = -39.5f,
        farShin = 86.5f,
        farThigh = 86.5f,
        farFoot = 70f,
    )
    // The hand stays on the floor as the elbow bends down to it, the way there keeping it just clear; on the way
    // up the forearm folds to the shoulder first, since an arm straight down from here would reach into the floor.
    val bending = hands.copy(upperArm = -30f, forearm = -50f)
    val elbow = hands.copy(upperArm = -20f, forearm = -52.8f)
    val lifting = hands.copy(upperArm = -10f, forearm = -100f)
    val folded = hands.copy(upperArm = 20f, forearm = -210f)
    val reach = hands.copy(upperArm = 180f, forearm = -180f)
    val landing = hands.copy(upperArm = -30f, forearm = -60f)
    ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = hands),
            Keyframe(at = 0.12f, pose = hands),
            Keyframe(at = 0.21f, pose = bending),
            Keyframe(at = 0.3f, pose = elbow),
            Keyframe(at = 0.42f, pose = elbow),
            Keyframe(at = 0.48f, pose = lifting),
            Keyframe(at = 0.54f, pose = folded),
            Keyframe(at = 0.66f, pose = reach),
            Keyframe(at = 0.8f, pose = reach),
            Keyframe(at = 0.93f, pose = landing),
            Keyframe(at = 1f, pose = hands),
        ),
        phases = phases(0.12f to 0, 0.42f to 1, 1f to 2),
        hold = Hold.Free,
        secondsPerRep = 6f,
        still = 0.7f,
        anchor = Offset(270f, Rig.FLOOR),
        view = Rect(left = 0f, top = 120f, right = 430f, bottom = 380f),
    )
}

/** Shoulder circles: standing tall, one arm long, the biggest slow circle forward and then back the other way. */
val ShoulderCirclesDemo: ExerciseDemo = run {
    fun arm(angle: Float) =
        Pose(shin = 0f, thigh = 0f, torso = 0f, upperArm = angle, forearm = angle, farUpperArm = 0f, farForearm = 10f)
    ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = arm(0f)),
            Keyframe(at = 0.08f, pose = arm(0f)),
            Keyframe(at = 0.5f, pose = arm(FULL_TURN)),
            Keyframe(at = 0.92f, pose = arm(0f)),
            Keyframe(at = 1f, pose = arm(0f)),
        ),
        phases = phases(0.08f to 0, 0.5f to 1, 1f to 2),
        hold = Hold.Free,
        secondsPerRep = 8f,
        still = 0.25f,
    )
}

/** The head, the shoulders and the arms out in front, close: the hands' drills. */
private val armsCloseUp = Rect(left = 170f, top = 45f, right = 340f, bottom = 180f)

/**
 * Wrist circles: the elbows at the sides and the forearms level, the loose fists circled at the wrists one way then
 * the other. Seen close and side on, a circle shows as the hands tipping up, level, down and level again.
 */
val WristCirclesDemo: ExerciseDemo = run {
    fun fists(wrist: Float) =
        Pose(shin = 0f, thigh = 0f, torso = 0f, upperArm = 0f, forearm = 90f, wrist = wrist, palm = LOOSE_FIST)
    val top = fists(70f)
    val forward = fists(0f)
    val bottom = fists(-70f)
    val back = fists(0f)
    ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = top),
            Keyframe(at = 0.125f, pose = forward),
            Keyframe(at = 0.25f, pose = bottom),
            Keyframe(at = 0.375f, pose = back),
            Keyframe(at = 0.5f, pose = top),
            Keyframe(at = 0.625f, pose = back),
            Keyframe(at = 0.75f, pose = bottom),
            Keyframe(at = 0.875f, pose = forward),
            Keyframe(at = 1f, pose = top),
        ),
        phases = phases(0.5f to 1, 1f to 2),
        hold = Hold.Free,
        secondsPerRep = 4f,
        still = 0.125f,
        crop = armsCloseUp,
    )
}

/**
 * Palms down, fingers back: on all fours with the hands flat and the fingers pointing back to the knees, rocked back
 * until the forearms pull, and eased. Seen close, at the hands.
 */
val PalmsDownFingersBackDemo: ExerciseDemo =
    rocking(allFours(thigh = -32f, torso = 77.3f, arm = 24.8f))

/**
 * Backs of the hands: on all fours with the backs of the hands down and the fingers to the knees, leaned in lightly
 * over them, and eased. From the side a hand on its back looks as one on its palm does; the lean says which.
 */
val BacksOfTheHandsDemo: ExerciseDemo =
    rocking(allFours(thigh = 14f, torso = 78.4f, arm = -10.8f))

/**
 * Finger pulls: one arm out in front with the hand open and the fingers up, the other hand on them drawing them
 * back toward the wrist, and easing them off. Seen close, at the arms.
 */
val FingerPullsDemo: ExerciseDemo = run {
    val meeting = pulling(wrist = 25f)
    val pulled = pulling(wrist = 90f)
    ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = meeting),
            Keyframe(at = 0.1f, pose = meeting),
            Keyframe(at = 0.2f, pose = pulling(wrist = 58f)),
            Keyframe(at = 0.3f, pose = pulled),
            Keyframe(at = 0.5f, pose = pulled),
            Keyframe(at = 0.6f, pose = pulling(wrist = 58f)),
            Keyframe(at = 0.7f, pose = meeting),
            Keyframe(at = 1f, pose = meeting),
        ),
        phases = phases(0.1f to 0, 0.5f to 1, 1f to 2),
        hold = Hold.Free,
        secondsPerRep = 3f,
        still = 0.4f,
        crop = armsCloseUp,
    )
}

/**
 * The forearm stretch: one arm out in front, palm forward and the fingers pointing down, the other hand on them
 * drawing them back toward the body.
 */
val ForearmStretchDemo: ExerciseDemo = held(
    pulling(wrist = -110f),
    pulling(wrist = -118f),
    root = Root.Ankle,
    anchor = Offset(Rig.ANKLE_X, Rig.FLOOR),
    view = Rig.view,
    crop = armsCloseUp,
)

/** A hand half closed, as a loose fist circling is. */
private const val LOOSE_FIST = 0.85f

/**
 * Standing with the near arm out in front, its hand open at [wrist], and the far hand holding it across its
 * fingers: the far arm reaches there from its own shoulder, the elbow under the line.
 */
private fun pulling(wrist: Float): Pose {
    val upperArm = 60f
    val forearm = 120f
    val held = Pose(shin = 0f, thigh = 0f, torso = 0f, upperArm = upperArm, forearm = forearm, wrist = wrist, palm = 1f)
        .figure()
    val grip = held.hand + (held.fingers - held.hand) * GRIP - (held.shoulder - Offset(Rig.FAR_ARM_OFFSET, 0f))
    val way = atan2(grip.x, grip.y)
    val bend = acos((grip.getDistance() / (Rig.UPPER_ARM + Rig.FOREARM)).coerceAtMost(1f))
    return Pose(
        shin = 0f,
        thigh = 0f,
        torso = 0f,
        upperArm = upperArm,
        forearm = forearm,
        wrist = wrist,
        palm = 1f,
        farUpperArm = (way - bend).degrees(),
        farForearm = (way + bend).degrees(),
        farWrist = 0f,
        farPalm = HOLDING,
    )
}

/** How far along the held hand the other takes it, and how far that one is open, closed round it. */
private const val GRIP = 0.6f
private const val HOLDING = 0.5f

private fun Float.degrees() = this * 180f / PI.toFloat()

/**
 * Cat-cow: on all fours, the back rounded to the ceiling with the head tucked (the cat, the hips tucked under
 * and the elbows soft), then let sink with the head up (the cow). The hands and the knees stay put.
 */
val CatCowDemo: ExerciseDemo = run {
    // The cow lets the belly down and the cat rounds the back up, between hips and shoulders that stay put.
    val cow = allFours(thigh = 6f, torso = 78.6f, arm = -4.7f).copy(spine = -32f)
    val cat = allFours(thigh = -10f, torso = 92f, arm = -33.5f).copy(forearm = 51.3f, farForearm = 51.3f, spine = 36f)
    // Halfway, solved too, so the knees stay put as the elbows bend.
    val between = allFours(thigh = -2f, torso = 85.5f, arm = -29.3f)
        .copy(forearm = 30.4f, farForearm = 30.4f, spine = 7f)
    onAllFours(
        listOf(
            0f to cow,
            0.1f to cow,
            0.25f to between,
            0.4f to cat,
            0.55f to cat,
            0.72f to between,
            0.9f to cow,
            1f to cow,
        ),
        phases(0.1f to 0, 0.55f to 1, 1f to 2),
        seconds = 5f,
        still = 0.47f,
    )
}

/** Child's pose: kneeling, sat back to the heels, the arms long on the mat ahead and the forehead down. */
val ChildsPoseDemo: ExerciseDemo = run {
    fun folded(thigh: Float, torso: Float, arm: Float) = Pose(
        shin = KNEELING_SHIN,
        thigh = thigh,
        torso = torso,
        upperArm = arm,
        forearm = arm,
        farFoot = KNEELING_FOOT,
        foot = KNEELING_FOOT,
    )
    held(
        folded(thigh = -45f, torso = 110.1f, arm = 80.7f),
        folded(thigh = -42f, torso = 111.3f, arm = 80.3f),
        anchor = Offset(150f, Rig.FLOOR),
    )
}

/** The cobra: face down, the hands under the shoulders, the chest pressed up with the hips heavy on the mat. */
val CobraDemo: ExerciseDemo = run {
    // The hands stay where they are on the mat, so the arms were solved for each lift of the chest.
    fun pressed(torso: Float, upperArm: Float, forearm: Float) = Pose(
        shin = PRONE,
        thigh = PRONE,
        torso = torso,
        upperArm = upperArm,
        forearm = forearm,
        farFoot = KNEELING_FOOT,
        foot = KNEELING_FOOT,
    )
    held(
        pressed(torso = 45f, upperArm = 44.3f, forearm = 27.2f),
        pressed(torso = 47.5f, upperArm = 55.2f, forearm = 15.5f),
    )
}

/**
 * Downward dog: the hands and feet down and the hips high, the back long from the hands to the hips, the heels
 * toward the mat, then one knee bent and the other to walk them down. Grounded by the hands.
 */
val DownwardDogDemo: ExerciseDemo = run {
    val dog = Pose(shin = 20f, thigh = 20f, torso = 129.0f, upperArm = -51.0f, forearm = -51.0f)
    // A knee bends with the foot flat, lifting it, then the heel comes up: pitching the toes down as the ankle
    // rose would dip them into the floor, since the two ease at different rates.
    val nearLifted = dog.copy(shin = 52f, thigh = -16.9f)
    val nearBent = nearLifted.copy(foot = 40f)
    val farLifted = dog.copy(farShin = 52f, farThigh = -16.9f)
    val farBent = farLifted.copy(farFoot = 40f)
    ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = dog),
            Keyframe(at = 0.3f, pose = dog),
            Keyframe(at = 0.4f, pose = nearLifted),
            Keyframe(at = 0.45f, pose = nearBent),
            Keyframe(at = 0.55f, pose = nearBent),
            Keyframe(at = 0.6f, pose = nearLifted),
            Keyframe(at = 0.68f, pose = dog),
            Keyframe(at = 0.76f, pose = farLifted),
            Keyframe(at = 0.81f, pose = farBent),
            Keyframe(at = 0.9f, pose = farBent),
            Keyframe(at = 0.95f, pose = farLifted),
            Keyframe(at = 1f, pose = dog),
        ),
        phases = phases(0.12f to 0, 0.3f to 1, 1f to 2),
        hold = Hold.Free,
        secondsPerRep = 6f,
        still = 0.15f,
        root = Root.Hand,
        anchor = Offset(250f, Rig.FLOOR - Rig.HAND),
        view = Rect(left = 60f, top = 120f, right = 440f, bottom = 380f),
    )
}

/** The half split: sat back onto the back knee from a lunge, the front leg straight, toes up, folded over it. */
val HalfSplitDemo: ExerciseDemo = run {
    fun sat(torso: Float, arm: Float) = Pose(
        shin = -60f,
        thigh = -60f,
        torso = torso,
        upperArm = arm,
        forearm = arm,
        farShin = KNEELING_SHIN,
        farThigh = 18.4f,
        farFoot = KNEELING_FOOT,
        foot = -90f,
    )
    held(
        sat(torso = 58f, arm = 0f),
        sat(torso = 64f, arm = -6f),
        root = Root.Ankle,
        anchor = Offset(330f, Rig.FLOOR - Rig.HEEL),
        view = kneelingView,
    )
}

/** The forward fold: standing with the knees soft, hung from the hips, the arms folded and the head let go. */
val ForwardFoldDemo: ExerciseDemo = held(
    Pose(shin = 5f, thigh = -10f, torso = 148f, upperArm = 30f, forearm = -100f),
    Pose(shin = 5f, thigh = -10f, torso = 150f, upperArm = 32f, forearm = -100f),
    root = Root.Ankle,
    anchor = Offset(Rig.ANKLE_X, Rig.FLOOR),
    view = Rig.view,
)

/** Pigeon: the front leg folded on the mat, the back leg long behind, folded forward with the hands on the floor. */
val PigeonDemo: ExerciseDemo = run {
    fun folded(torso: Float, arm: Float) = Pose(
        shin = 100f,
        thigh = 268f,
        torso = torso,
        upperArm = arm,
        forearm = arm,
        farShin = 88f,
        farThigh = 88f,
        farFoot = KNEELING_FOOT,
    )
    held(folded(torso = 62f, arm = 56.8f), folded(torso = 66f, arm = 61.5f), anchor = Offset(150f, Rig.FLOOR))
}

/** On all fours over the knees, the hips at [thigh] and the back at [torso], both arms straight at [arm]. */
private fun allFours(thigh: Float, torso: Float, arm: Float) = Pose(
    shin = KNEELING_SHIN,
    thigh = thigh,
    torso = torso,
    upperArm = arm,
    forearm = arm,
    farFoot = KNEELING_FOOT,
    foot = KNEELING_FOOT,
)

/**
 * From all fours (hands under the shoulders, hips over the knees), moved to [moved] and held a moment, then back,
 * seen close at the hands. The hands lie flat with the fingers back toward the knees: each pose turns the wrists to
 * keep them there as the forearms tip, and since every keyframe is held, they stay there between them too.
 */
private fun rocking(moved: Pose): ExerciseDemo {
    val neutral = allFours(thigh = 0f, torso = 78.7f, arm = 0f).flatHands()
    val there = moved.flatHands()
    return onAllFours(
        listOf(0f to neutral, 0.1f to neutral, 0.4f to there, 0.6f to there, 0.9f to neutral, 1f to neutral),
        phases(0.1f to 0, 0.6f to 1, 1f to 2),
        seconds = 4f,
        still = 0.5f,
        crop = Rect(left = 232f, top = 290f, right = 372f, bottom = 378f),
    )
}

/** This pose with both hands flat on the floor, the fingers back toward the knees, whatever the forearms do. */
private fun Pose.flatHands() = copy(wrist = FLAT_HAND - forearm, farWrist = FLAT_HAND - farForearm, palm = 1f)

/** A hand flat on the floor from a wrist a fist's height up, the fingertips back and down to the mat. */
private const val FLAT_HAND = -76f

/** A demo on all fours from these keyframes, grounded by the hand; each pose keeps the knees where they are. */
private fun onAllFours(
    keys: List<Pair<Float, Pose>>,
    phases: List<DemoPhase>,
    seconds: Float,
    still: Float,
    crop: Rect? = null,
) =
    ExerciseDemo(
        keyframes = keys.map { (at, pose) -> Keyframe(at, pose) },
        phases = phases,
        hold = Hold.Free,
        secondsPerRep = seconds,
        still = still,
        root = Root.Hand,
        anchor = Offset(295f, Rig.FLOOR - Rig.HAND),
        view = matView,
        crop = crop,
    )

/** A demo on the mat from these keyframes, grounded by the hips. */
private fun mat(keys: List<Pair<Float, Pose>>, phases: List<DemoPhase>, seconds: Float, still: Float) = ExerciseDemo(
    keyframes = keys.map { (at, pose) -> Keyframe(at, pose) },
    phases = phases,
    hold = Hold.Free,
    secondsPerRep = seconds,
    still = still,
    root = Root.Hips,
    anchor = matAnchor,
    view = matView,
)

/** A position [held], breathing to [breath] and back: on the mat by the hips unless said otherwise. */
private fun held(
    held: Pose,
    breath: Pose,
    root: Root = Root.Hips,
    anchor: Offset = matAnchor,
    view: Rect = matView,
    crop: Rect? = null,
) = ExerciseDemo(
    keyframes = listOf(
        Keyframe(at = 0f, pose = held),
        Keyframe(at = 0.5f, pose = breath),
        Keyframe(at = 1f, pose = held),
    ),
    phases = phases(0.5f to 1, 1f to 2),
    hold = Hold.Free,
    secondsPerRep = 4f,
    still = 0f,
    root = root,
    anchor = anchor,
    view = view,
    crop = crop,
)
