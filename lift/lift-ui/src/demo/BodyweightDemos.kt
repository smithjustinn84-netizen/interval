// Keyframe tables: their shares of the rep and solved angles are data, as in every demo.
@file:Suppress("detekt.MagicNumber")

package dev.justinsmith.lift.ui.demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

// The bodyweight lifts. Where the figure has two holds on the world (hands and toes on the floor, hands on a
// bar and heels on the floor), the poses were solved so both stay put: the push-ups pivot about the toes, the
// inverted row about the heels, the bench dip with the hands on the bench and the heels on the floor.

private const val INCLINE_BENCH = 90f
private const val DIP_BENCH = 62f
private const val ROW_BAR = 140f
private const val STEP = 80f

/** The renegade row's plank, higher than a push-up's for the bells under the hands, and where its toes stand. */
private const val RENEGADE_LEAN = 62f
private const val RENEGADE_FOOT = 69f
private const val RENEGADE_TOES = 60f

/** A leg lifted off the floor behind, the heel folded up, before its knee drives in under the hips. */
private const val LIFTED = 70f
private const val FOLDED = 140f
private const val LIFTED_FOOT = 50f

/** The hips hiked a little as a knee drives in under them, so it clears the floor. */
private const val HIPS_UP = 92f

/** The hands on the floor, as a push-up's are. */
private val floorHands = Offset(300f, Rig.FLOOR - Rig.HAND)

/** A low, wide box for anything done on the floor face down. */
private val plankView = Rect(left = 10f, top = 120f, right = 390f, bottom = 380f)

/** A low, wide box for anything done on the back, reaching overhead. */
private val supineView = Rect(left = -70f, top = 150f, right = 340f, bottom = 380f)

private val pushUpTop = plank(lean = 73.2f, foot = 76.9f, upperArm = -5.5f, forearm = 10.1f)
private val pushUpMid = plank(lean = 81.7f, foot = 85.4f, upperArm = -59.5f, forearm = 43.2f)
private val pushUpBottom = plank(lean = 87.2f, foot = 90.9f, upperArm = -87.6f, forearm = 45.8f)

/** The push-up: a straight body from the toes, lowered with the elbows back until the chest nears the floor. */
val PushUpDemo: ExerciseDemo = pushUp(top = pushUpTop, mid = pushUpMid, bottom = pushUpBottom, hands = floorHands)

/** The incline push-up: the push-up with the hands on a bench, the body steeper. */
val InclinePushUpDemo: ExerciseDemo = pushUp(
    top = plank(lean = 49.9f, foot = 61.2f, upperArm = -5.5f, forearm = 10.1f),
    mid = plank(lean = 60.4f, foot = 71.7f, upperArm = -71.8f, forearm = 22.4f),
    bottom = plank(lean = 66.7f, foot = 78f, upperArm = -104.9f, forearm = 4.5f),
    hands = Offset(330f, Rig.FLOOR - INCLINE_BENCH - Rig.HAND),
    props = listOf(Bench(from = 300f, to = 385f, height = INCLINE_BENCH)),
    view = Rect(left = 60f, top = 40f, right = 420f, bottom = 380f),
)

/** The pike push-up: the hips high in an upside-down V, the head lowered toward the floor between the hands. */
val PikePushUpDemo: ExerciseDemo = run {
    val legs = 17.1f
    val top = Pose(shin = legs, thigh = legs, torso = 125f, upperArm = -5f, forearm = -5f, foot = 69f, farFoot = 69f)
    val bottom = top.copy(torso = 150f).arms(upper = -21.8f, fore = 65.9f)
    ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = top),
            Keyframe(at = 0.1f, pose = top),
            Keyframe(at = 0.3f, pose = top.copy(torso = 138f).arms(upper = -30.1f, fore = 41.6f)),
            Keyframe(at = 0.5f, pose = bottom),
            Keyframe(at = 0.56f, pose = bottom),
            Keyframe(at = 1f, pose = top),
        ),
        phases = phases(0.1f to 0, 0.56f to 1, 1f to 2),
        hold = Hold.Free,
        secondsPerRep = 2.8f,
        still = 0.52f,
        root = Root.Hand,
        anchor = floorHands,
        view = Rect(left = 60f, top = 100f, right = 380f, bottom = 380f),
    )
}

/** The mountain climber: from the top of a push-up, each knee driven to the chest in turn. */
val MountainClimberDemo: ExerciseDemo = run {
    val plank = pushUpTop
    val near = plank.copy(torso = HIPS_UP, shin = 31.8f, thigh = -92.7f, foot = 20f)
    val far = plank.copy(torso = HIPS_UP, farShin = 31.8f, farThigh = -92.7f, farFoot = 20f)
    val nearUp = plank.copy(torso = HIPS_UP, shin = FOLDED, thigh = LIFTED, foot = LIFTED_FOOT)
    val farUp = plank.copy(torso = HIPS_UP, farShin = FOLDED, farThigh = LIFTED, farFoot = LIFTED_FOOT)
    ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = plank),
            Keyframe(at = 0.08f, pose = nearUp),
            Keyframe(at = 0.25f, pose = near),
            Keyframe(at = 0.4f, pose = nearUp),
            Keyframe(at = 0.5f, pose = plank),
            Keyframe(at = 0.58f, pose = farUp),
            Keyframe(at = 0.75f, pose = far),
            Keyframe(at = 0.9f, pose = farUp),
            Keyframe(at = 1f, pose = plank),
        ),
        phases = phases(0.5f to 1, 1f to 2),
        hold = Hold.Free,
        secondsPerRep = 1f,
        still = 0.25f,
        root = Root.Hand,
        anchor = floorHands,
        view = plankView,
    )
}

/** The bench dip: the hands on a bench behind, the heels on the floor, lowered by bending the elbows back. */
val BenchDipDemo: ExerciseDemo = run {
    val top = Pose(shin = -69.5f, thigh = -69.5f, torso = -8f, upperArm = 0f, forearm = 0f, foot = -80f, farFoot = -80f)
    val bottom = top.copy(shin = -81.6f, thigh = -81.6f, farShin = -81.6f, farThigh = -81.6f)
        .arms(upper = -39.2f, fore = 52.2f)
    ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = top),
            Keyframe(at = 0.1f, pose = top),
            Keyframe(
                at = 0.3f,
                pose = top.copy(shin = -76.1f, thigh = -76.1f, farShin = -76.1f, farThigh = -76.1f)
                    .arms(upper = -29.8f, fore = 36.8f),
            ),
            Keyframe(at = 0.48f, pose = bottom),
            Keyframe(at = 0.54f, pose = bottom),
            Keyframe(at = 1f, pose = top),
        ),
        phases = phases(0.1f to 0, 0.54f to 1, 1f to 2),
        hold = Hold.Free,
        secondsPerRep = 2.6f,
        still = 0.5f,
        root = Root.Hand,
        anchor = Offset(140f, Rig.FLOOR - DIP_BENCH - Rig.HAND),
        view = Rect(left = 40f, top = 100f, right = 380f, bottom = 380f),
        props = listOf(Bench(from = 60f, to = 152f, height = DIP_BENCH)),
    )
}

/** The pull-up: from a dead hang, pull the chin over the bar, lower all the way. */
val PullUpDemo: ExerciseDemo = hang()

/** The chin-up: the pull-up with the palms turned toward you, the same from the side. */
val ChinUpDemo: ExerciseDemo = hang()

/** The inverted row: under a low bar, the body straight from the heels, pull the chest up to it. */
val InvertedRowDemo: ExerciseDemo = run {
    val low = plank(lean = 83.9f, foot = -80f, upperArm = 180f, forearm = 180f)
    val high = plank(lean = 67.6f, foot = -80f, upperArm = 76.3f, forearm = 217.2f)
    val halfway = plank(lean = 76.1f, foot = -80f, upperArm = 125.3f, forearm = 223.8f)
    val bar = Offset(300f, Rig.FLOOR - ROW_BAR)
    ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = low),
            Keyframe(at = 0.1f, pose = low),
            Keyframe(at = 0.26f, pose = halfway),
            Keyframe(at = 0.42f, pose = high),
            Keyframe(at = 0.5f, pose = high),
            Keyframe(at = 0.76f, pose = halfway),
            Keyframe(at = 1f, pose = low),
        ),
        phases = phases(0.1f to 0, 0.5f to 1, 1f to 2),
        hold = Hold.Free,
        secondsPerRep = 2.6f,
        still = 0.46f,
        root = Root.Hand,
        anchor = bar,
        view = Rect(left = 20f, top = 120f, right = 380f, bottom = 380f),
        props = listOf(Rail(bar)),
    )
}

/** The air squat: arms forward for balance, sit down below parallel, stand. */
val AirSquatDemo: ExerciseDemo = run {
    val stand = Pose(shin = 0f, thigh = 0f, torso = 0f, upperArm = 0f, forearm = 8f)
    val deep = Pose(shin = 30f, thigh = -105f, torso = 25f, upperArm = 90f, forearm = 90f)
    ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = stand),
            Keyframe(at = 0.1f, pose = stand),
            Keyframe(at = 0.3f, pose = Pose(shin = 18f, thigh = -55f, torso = 20f, upperArm = 60f, forearm = 60f)),
            Keyframe(at = 0.5f, pose = deep),
            Keyframe(at = 0.56f, pose = deep),
            Keyframe(at = 0.92f, pose = stand),
            Keyframe(at = 1f, pose = stand),
        ),
        phases = phases(0.1f to 0, 0.56f to 1, 1f to 2),
        hold = Hold.Free,
        secondsPerRep = 2.4f,
        still = 0.52f,
    )
}

/**
 * The split squat: a long stance held, the back knee lowered toward the floor and driven back up. The back foot
 * stays where it is, so each step down is solved for it.
 */
val SplitSquatDemo: ExerciseDemo = run {
    fun stance(shin: Float, thigh: Float, farThigh: Float, farShin: Float) = Pose(
        shin = shin,
        thigh = thigh,
        torso = 0f,
        upperArm = 0f,
        forearm = 8f,
        farShin = farShin,
        farThigh = farThigh,
        farFoot = 75f,
    )
    val top = stance(shin = 6f, thigh = -45f, farThigh = 36.9f, farShin = 61.8f)
    val third = stance(shin = 7f, thigh = -53f, farThigh = 26.5f, farShin = 75.4f)
    val twoThirds = stance(shin = 8f, thigh = -62f, farThigh = 20.4f, farShin = 86.5f)
    val bottom = stance(shin = 10f, thigh = -80f, farThigh = 18.3f, farShin = 106.1f)
    ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = top),
            Keyframe(at = 0.1f, pose = top),
            Keyframe(at = 0.24f, pose = third),
            Keyframe(at = 0.36f, pose = twoThirds),
            Keyframe(at = 0.5f, pose = bottom),
            Keyframe(at = 0.56f, pose = bottom),
            Keyframe(at = 0.72f, pose = twoThirds),
            Keyframe(at = 0.86f, pose = third),
            Keyframe(at = 1f, pose = top),
        ),
        phases = phases(0.1f to 0, 0.56f to 1, 1f to 2),
        hold = Hold.Free,
        secondsPerRep = 2.8f,
        still = 0.53f,
    )
}

/** The reverse lunge: step back, lower the back knee toward the floor, drive through the front foot to stand. */
val ReverseLungeDemo: ExerciseDemo = run {
    val arms = Pose(shin = 0f, thigh = 0f, torso = 0f, upperArm = 0f, forearm = 8f)
    ExerciseDemo(
        keyframes = RackedLungeDemo.keyframes.map { it.copy(pose = arms.standingAs(it.pose)) },
        phases = RackedLungeDemo.phases,
        hold = Hold.Free,
        secondsPerRep = 3f,
        still = RackedLungeDemo.still,
    )
}

/** The Bulgarian split squat: the back foot laced on a bench behind, down on the front leg and up. */
val BulgarianSplitSquatDemo: ExerciseDemo = run {
    val top = Pose(
        shin = 4f,
        thigh = -28f,
        torso = 4f,
        upperArm = 0f,
        forearm = 8f,
        farShin = 30.4f,
        farThigh = 80.7f,
        farFoot = 170f,
    )
    val bottom = top.copy(shin = 14f, thigh = -82f, torso = 12f, farShin = 22.1f, farThigh = 131.8f)
    upAndDown(
        top,
        bottom,
        seconds = 3f,
        props = listOf(Bench(from = 20f, to = 92f, height = 62f)),
        view = Rect(left = 0f, top = -70f, right = 390f, bottom = 380f),
    )
}

/** The step-up: one foot on a box, stand up onto it, the other knee rising, then step back down. */
val StepUpDemo: ExerciseDemo = run {
    val low = Pose(
        shin = 36.6f,
        thigh = -88.4f,
        torso = 6f,
        upperArm = 0f,
        forearm = 8f,
        farShin = 28.4f,
        farThigh = -10.6f,
    )
    val top = Pose(
        shin = 0f,
        thigh = 0f,
        torso = 0f,
        upperArm = 0f,
        forearm = 8f,
        farShin = -10f,
        farThigh = -70f,
        farFoot = 10f,
    )
    ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = low),
            Keyframe(at = 0.1f, pose = low),
            Keyframe(at = 0.45f, pose = top),
            Keyframe(at = 0.55f, pose = top),
            Keyframe(at = 1f, pose = low),
        ),
        phases = phases(0.1f to 0, 0.55f to 1, 1f to 2),
        hold = Hold.Free,
        secondsPerRep = 2.8f,
        still = 0.5f,
        anchor = Offset(Rig.ANKLE_X, Rig.FLOOR - STEP),
        props = listOf(Bench(from = 175f, to = 290f, height = STEP)),
    )
}

/** The calf raise: up onto the toes, pause, lower the heels slowly. */
val CalfRaiseDemo: ExerciseDemo = run {
    val flat = Pose(shin = 0f, thigh = 0f, torso = 0f, upperArm = 0f, forearm = 8f)
    val up = flat.copy(foot = 40f, farFoot = 40f)
    ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = flat),
            Keyframe(at = 0.1f, pose = flat),
            Keyframe(at = 0.35f, pose = up),
            Keyframe(at = 0.5f, pose = up),
            Keyframe(at = 1f, pose = flat),
        ),
        phases = phases(0.1f to 0, 0.5f to 1, 1f to 2),
        hold = Hold.Free,
        secondsPerRep = 2.4f,
        still = 0.42f,
        root = Root.Toe,
        anchor = Offset(Rig.ANKLE_X + Rig.FOOT, Rig.FLOOR),
    )
}

/** The wall sit: the back flat on a wall, the thighs level, held, breathing. */
val WallSitDemo: ExerciseDemo = breathing(
    Pose(shin = 0f, thigh = -90f, torso = 0f, upperArm = 0f, forearm = 8f),
    Pose(shin = 0f, thigh = -90f, torso = -1f, upperArm = 2f, forearm = 10f),
    props = listOf(Wall(x = 115f)),
)

/** The glute bridge: on the back, the feet planted, drive the hips up until knees, hips and shoulders line up. */
val GluteBridgeDemo: ExerciseDemo = bridge(single = false)

/** The single-leg glute bridge: the bridge on one foot, the other leg held out straight. */
val SingleLegGluteBridgeDemo: ExerciseDemo = bridge(single = true)

/** The forearm plank: a straight body from the toes to the elbows, held, breathing. */
val PlankDemo: ExerciseDemo = breathing(
    forearmPlank(lean = 84f),
    forearmPlank(lean = 83f),
    root = Root.Hips,
    view = plankView,
)

/** The side plank: up on one forearm, the top arm reaching up, the hips lifted high, held. */
val SidePlankDemo: ExerciseDemo = breathing(
    forearmPlank(lean = 80f).copy(farUpperArm = 180f, farForearm = 180f),
    forearmPlank(lean = 80f).copy(shin = 86f, thigh = 88f, farShin = 86f, farThigh = 88f).copy(
        farUpperArm = 180f,
        farForearm = 180f,
    ),
    root = Root.Hips,
    view = plankView,
)

/** The hollow hold: on the back, the shoulders and the legs lifted, the arms long overhead, held. */
val HollowHoldDemo: ExerciseDemo = breathing(
    supine(torso = -72f, nearLeg = 252f, farLeg = -108f, arms = 250f),
    supine(torso = -76f, nearLeg = 248f, farLeg = -112f, arms = 246f),
    root = Root.Hips,
    view = supineView,
    anchor = Offset(170f, Rig.FLOOR),
)

/** The dead bug: on the back, the arms up and the knees over the hips, each arm reaching back with the other leg. */
val DeadBugDemo: ExerciseDemo = run {
    val rest = supine(torso = -82f, nearLeg = 0f, farLeg = 0f, arms = 180f).tableTop()
    val nearArm = rest.copy(upperArm = 250f, forearm = 250f, farShin = -110f, farThigh = -110f)
    val farArm = rest.copy(farUpperArm = 250f, farForearm = 250f, shin = 250f, thigh = 250f)
    ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = rest),
            Keyframe(at = 0.25f, pose = nearArm),
            Keyframe(at = 0.5f, pose = rest),
            Keyframe(at = 0.75f, pose = farArm),
            Keyframe(at = 1f, pose = rest),
        ),
        phases = phases(0.5f to 1, 1f to 2),
        hold = Hold.Free,
        secondsPerRep = 4f,
        still = 0.25f,
        root = Root.Hips,
        anchor = Offset(170f, Rig.FLOOR),
        view = supineView,
    )
}

/** The kettlebell dead bug: the dead bug with one bell held up over the chest in both hands, only the legs reaching. */
val KettlebellDeadBugDemo: ExerciseDemo = run {
    val rest = supine(torso = -82f, nearLeg = 0f, farLeg = 0f, arms = 180f).tableTop()
    val farLeg = rest.copy(farShin = -110f, farThigh = -110f)
    val nearLeg = rest.copy(shin = 250f, thigh = 250f)
    ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = rest),
            Keyframe(at = 0.25f, pose = farLeg),
            Keyframe(at = 0.5f, pose = rest),
            Keyframe(at = 0.75f, pose = nearLeg),
            Keyframe(at = 1f, pose = rest),
        ),
        phases = phases(0.5f to 1, 1f to 2),
        hold = Hold.BothHands,
        secondsPerRep = 4f,
        still = 0.25f,
        root = Root.Hips,
        anchor = Offset(170f, Rig.FLOOR),
        view = supineView,
    )
}

/**
 * The renegade row: a push-up plank with the hands on two bells, the near one rowed to the hip and set down, the
 * far arm and the toes holding the body square.
 */
val RenegadeRowDemo: ExerciseDemo = run {
    val plank = plank(lean = RENEGADE_LEAN, foot = RENEGADE_FOOT, upperArm = -5.5f, forearm = 10.1f)
    val rowed = plank.copy(upperArm = -70f, forearm = 10f, bell = 10f)
    ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = plank),
            Keyframe(at = 0.1f, pose = plank),
            Keyframe(at = 0.4f, pose = rowed),
            Keyframe(at = 0.5f, pose = rowed),
            Keyframe(at = 0.9f, pose = plank),
            Keyframe(at = 1f, pose = plank),
        ),
        phases = phases(0.1f to 0, 0.5f to 1, 1f to 2),
        hold = Hold.Pair,
        secondsPerRep = 3f,
        still = 0.45f,
        root = Root.Toe,
        anchor = Offset(RENEGADE_TOES, Rig.FLOOR),
        view = plankView,
    )
}

/**
 * The burpee: squat the hands to the floor, jump the feet back to a plank, a push-up, jump them in, stand and
 * reach.
 */
val BurpeeDemo: ExerciseDemo = run {
    val stand = Pose(shin = 0f, thigh = 0f, torso = 0f, upperArm = 0f, forearm = 8f)
    val crouch = Pose(shin = 30f, thigh = -105f, torso = 55f, upperArm = 10f, forearm = 10f)
    val reach = stand.copy(upperArm = 175f, forearm = 178f)
    ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = stand),
            Keyframe(at = 0.12f, pose = crouch),
            Keyframe(at = 0.24f, pose = pushUpTop),
            Keyframe(at = 0.36f, pose = pushUpBottom),
            Keyframe(at = 0.48f, pose = pushUpTop),
            Keyframe(at = 0.6f, pose = crouch),
            Keyframe(at = 0.76f, pose = reach),
            Keyframe(at = 0.84f, pose = reach),
            Keyframe(at = 1f, pose = stand),
        ),
        phases = phases(0.24f to 0, 0.6f to 1, 1f to 2),
        hold = Hold.Free,
        secondsPerRep = 3.2f,
        still = 0.24f,
        root = Root.Hips,
    )
}

/** A straight body from the toes at [lean], up on the toes at [foot], the arms as given. */
private fun plank(lean: Float, foot: Float, upperArm: Float, forearm: Float) = Pose(
    shin = lean,
    thigh = lean,
    torso = lean,
    upperArm = upperArm,
    forearm = forearm,
    foot = foot,
    farFoot = foot,
)

/** This pose with both arms at [upper] and [fore]. */
private fun Pose.arms(upper: Float, fore: Float) =
    copy(upperArm = upper, forearm = fore, farUpperArm = upper, farForearm = fore)

/** On the forearms and toes, the body straight at [lean]. */
private fun forearmPlank(lean: Float) = plank(lean = lean, foot = 95f, upperArm = 0f, forearm = 90f)

/** On the back at [torso], the legs at [nearLeg] and [farLeg] (straight), both arms at [arms]. */
private fun supine(torso: Float, nearLeg: Float, farLeg: Float, arms: Float) = Pose(
    shin = nearLeg,
    thigh = nearLeg,
    torso = torso,
    upperArm = arms,
    forearm = arms,
    farShin = farLeg,
    farThigh = farLeg,
    foot = -90f,
    farFoot = -90f,
)

/** This pose with both knees over the hips and the shins level: a dead bug's table top. */
private fun Pose.tableTop() = copy(shin = 270f, thigh = 180f, farShin = -90f, farThigh = -180f)

private fun pushUp(
    top: Pose,
    mid: Pose,
    bottom: Pose,
    hands: Offset,
    props: List<Prop> = emptyList(),
    view: Rect = plankView,
) = ExerciseDemo(
    keyframes = listOf(
        Keyframe(at = 0f, pose = top),
        Keyframe(at = 0.1f, pose = top),
        Keyframe(at = 0.3f, pose = mid),
        Keyframe(at = 0.46f, pose = bottom),
        Keyframe(at = 0.52f, pose = bottom),
        Keyframe(at = 0.72f, pose = mid),
        Keyframe(at = 1f, pose = top),
    ),
    phases = phases(0.1f to 0, 0.52f to 1, 1f to 2),
    hold = Hold.Free,
    secondsPerRep = 2.4f,
    still = 0.48f,
    root = Root.Hand,
    anchor = hands,
    view = view,
    props = props,
)

/** Hanging from a bar by the hands: a dead hang, pulled up until the chin is over it, lowered. */
private fun hang(): ExerciseDemo {
    val bar = Offset(215f, -40f)
    val hanging = Pose(shin = 35f, thigh = 0f, torso = 0f, upperArm = 180f, forearm = 180f)
    val top = Pose(shin = 40f, thigh = -15f, torso = -10f, upperArm = 20f, forearm = 175f)
    return ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = hanging),
            Keyframe(at = 0.1f, pose = hanging),
            Keyframe(at = 0.28f, pose = Pose(shin = 38f, thigh = -8f, torso = -5f, upperArm = 100f, forearm = 185f)),
            Keyframe(at = 0.42f, pose = top),
            Keyframe(at = 0.5f, pose = top),
            Keyframe(at = 1f, pose = hanging),
        ),
        phases = phases(0.1f to 0, 0.5f to 1, 1f to 2),
        hold = Hold.Free,
        secondsPerRep = 3f,
        still = 0.46f,
        root = Root.Hand,
        anchor = bar,
        view = Rect(left = 40f, top = -100f, right = 360f, bottom = 380f),
        props = listOf(Rail(bar)),
    )
}

/** A rep from [top] down to [bottom] and back up, on the spot. */
private fun upAndDown(
    top: Pose,
    bottom: Pose,
    seconds: Float,
    props: List<Prop> = emptyList(),
    view: Rect = Rig.view,
) = ExerciseDemo(
    keyframes = listOf(
        Keyframe(at = 0f, pose = top),
        Keyframe(at = 0.1f, pose = top),
        Keyframe(at = 0.48f, pose = bottom),
        Keyframe(at = 0.56f, pose = bottom),
        Keyframe(at = 1f, pose = top),
    ),
    phases = phases(0.1f to 0, 0.56f to 1, 1f to 2),
    hold = Hold.Free,
    secondsPerRep = seconds,
    still = 0.52f,
    props = props,
    view = view,
)

/** A position held, breathing between [held] and [breath]. */
private fun breathing(
    held: Pose,
    breath: Pose,
    root: Root = Root.Ankle,
    view: Rect = Rig.view,
    props: List<Prop> = emptyList(),
    anchor: Offset = Offset(Rig.ANKLE_X, Rig.FLOOR),
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
    props = props,
)

/**
 * On the back with the feet planted, the hips driven up off the floor and lowered; one leg held out if [single],
 * with [props] round it.
 */
internal fun bridge(single: Boolean, props: List<Prop> = emptyList()): ExerciseDemo {
    val down = Pose(shin = -40.8f, thigh = -126.2f, torso = -80f, upperArm = 90f, forearm = 90f)
    val up = Pose(shin = -27.9f, thigh = -102f, torso = -100f, upperArm = 90f, forearm = 90f)
    fun leg(pose: Pose) = if (single) pose.copy(farShin = pose.thigh, farThigh = pose.thigh) else pose
    return ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = leg(down)),
            Keyframe(at = 0.1f, pose = leg(down)),
            Keyframe(at = 0.4f, pose = leg(up)),
            Keyframe(at = 0.55f, pose = leg(up)),
            Keyframe(at = 1f, pose = leg(down)),
        ),
        phases = phases(0.1f to 0, 0.55f to 1, 1f to 2),
        hold = Hold.Free,
        secondsPerRep = 3f,
        still = 0.47f,
        view = Rect(left = -80f, top = 180f, right = 300f, bottom = 380f),
        props = props,
    )
}
