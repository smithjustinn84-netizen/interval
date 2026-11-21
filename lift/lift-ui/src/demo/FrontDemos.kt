// Keyframe tables: their shares of the rep and solved angles are data, as in every demo.
@file:Suppress("detekt.MagicNumber")

package dev.justinsmith.lift.ui.demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.justinsmith.lift.ui.demo.KettlebellPoses.BOTTOMS_UP
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

// The lifts that go sideways, seen from the front (Facing.Front): side on they show nothing of themselves. The
// near side is drawn on the right, and each leans across the picture, so legs set apart take opposite signs. After
// them, the ones that go across the floor, seen from above (Facing.Above), lying with the head to the left.

/** Where a figure seen from the front stands, its feet either side of the view's middle. */
private val frontAnchor = Offset(250f, Rig.FLOOR)

/** Standing square to the eye, each foot [stance] degrees out from under its hip, the arms hanging. */
private fun facing(stance: Float) = Pose(
    shin = -stance,
    thigh = -stance,
    torso = 0f,
    upperArm = 0f,
    forearm = 0f,
    farShin = stance,
    farThigh = stance,
)

/**
 * The windmill: the bell locked out overhead, the feet apart, bend to the side away from it with the bell arm
 * upright and the free hand sliding down the inside of the leg, then stand. Its phases are its cues: Lock out
 * (0), Push the hip back (1), Stand (2).
 */
val WindmillDemo: ExerciseDemo = run {
    // The bell rests on the back of the forearm, a little to the outside of it.
    val tall = facing(stance = 14f).copy(
        upperArm = 180f,
        forearm = 180f,
        bell = 16f,
        farUpperArm = -4f,
        farForearm = -4f,
    )
    // The bell side long, the waist bowed out toward it.
    val down = tall.copy(torso = -60f, farUpperArm = 42f, farForearm = 42f, spine = -14f)
    ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = tall),
            Keyframe(at = 0.12f, pose = tall),
            Keyframe(at = 0.5f, pose = down),
            Keyframe(at = 0.6f, pose = down),
            Keyframe(at = 0.9f, pose = tall),
            Keyframe(at = 1f, pose = tall),
        ),
        phases = listOf(
            DemoPhase(until = 0.12f, beat = DemoBeat.Set, cue = 0),
            DemoPhase(until = 0.6f, beat = DemoBeat.Ease, cue = 1),
            DemoPhase(until = 1f, beat = DemoBeat.Drive, cue = 2),
        ),
        hold = Hold.NearHand,
        secondsPerRep = 4f,
        still = 0.55f,
        anchor = frontAnchor,
        facing = Facing.Front,
    )
}

/**
 * The halo: the bell upside down by the horns in front of the chin, circled close round the head, past one ear,
 * behind the neck where it hangs, past the other ear and back. Its phases are its cues: Hold (0), Circle close
 * (1), Ribs down (2).
 */
val HaloDemo: ExerciseDemo = run {
    val steps = 16
    var last: Pose? = null
    val round = List(steps + 1) { step ->
        val pose = haloPose(turn = step * 360f / steps, after = last)
        last = pose
        Keyframe(at = step / steps.toFloat(), pose = pose)
    }
    ExerciseDemo(
        keyframes = round,
        phases = listOf(
            DemoPhase(until = 0.125f, beat = DemoBeat.Set, cue = 0),
            DemoPhase(until = 0.75f, beat = DemoBeat.Drive, cue = 1),
            DemoPhase(until = 1f, beat = DemoBeat.Ease, cue = 2),
        ),
        hold = Hold.BothHands,
        secondsPerRep = 3f,
        still = 0.25f,
        anchor = frontAnchor,
        facing = Facing.Front,
    )
}

/**
 * A halo's pose with the bell [turn] degrees round the head from in front of the chin, by the near ear first: the
 * hands either side of where its handle is, each elbow out to its own side, the bell tipping over as it rounds
 * the ear to hang behind the neck. Each arm's angles are the turn nearest those [after] the pose before, so an
 * elbow that goes right round doesn't spin back.
 */
private fun haloPose(turn: Float, after: Pose?): Pose {
    val angle = turn * PI.toFloat() / 180f
    val behind = (1f - cos(angle)) / 2f
    val side = sin(angle)
    val height = HALO_FRONT + (HALO_BEHIND - HALO_FRONT) * behind - HALO_RISE * side * side
    val handle = Offset(HALO_ACROSS * side, height)
    // Behind the head the elbows point up and out, so more of the arms shows than in front, bent toward the eye.
    val reach = HALO_REACH + (HALO_REACH_BEHIND - HALO_REACH) * behind
    val near = armTo(handle + Offset(HALO_GRIP, 0f) - Offset(Rig.SHOULDER_HALF, 0f), reach, outward = 1f)
    val far = armTo(handle - Offset(HALO_GRIP, 0f) + Offset(Rig.SHOULDER_HALF, 0f), reach, outward = -1f)
    return facing(stance = 5f).copy(
        upperArm = (after?.upperArm ?: 0f).nearestTurn(near.first),
        forearm = (after?.forearm ?: 180f).nearestTurn(near.second),
        farUpperArm = (after?.farUpperArm ?: 0f).nearestTurn(far.first),
        farForearm = (after?.farForearm ?: -180f).nearestTurn(far.second),
        bell = BOTTOMS_UP - turn,
        reach = reach,
        behind = behind,
    )
}

/**
 * The upper arm's and the forearm's angles that put a hand [at] this place from its shoulder, [reach] of the arm
 * showing, the elbow to the right of the way there ([outward] 1) or to the left (-1).
 */
private fun armTo(at: Offset, reach: Float, outward: Float): Pair<Float, Float> {
    val bone = Rig.UPPER_ARM * reach
    val way = atan2(at.x, at.y)
    val bend = acos((hypot(at.x, at.y) / (2 * bone)).coerceAtMost(1f))
    val either = listOf(way + bend to way - bend, way - bend to way + bend)
    val (upper, fore) = either.maxBy { (upper, _) -> sin(upper) * outward }
    return upper * 180f / PI.toFloat() to fore * 180f / PI.toFloat()
}

/** The handle's path round the head, from the middle of the shoulders: how far to each side, and how low. */
private const val HALO_ACROSS = 40f
private const val HALO_FRONT = 34f
private const val HALO_BEHIND = -14f
private const val HALO_RISE = 30f

/** Each hand from the middle of the handle, and how much of the arms shows, bent toward the eye as they are. */
private const val HALO_GRIP = 6f
private const val HALO_REACH = 0.7f
private const val HALO_REACH_BEHIND = 0.95f

/**
 * The band pull-apart: the arms straight ahead at shoulder height, toward the eye, then spread wide to the
 * sides with the band stretched across the chest.
 */
val BandPullApartDemo: ExerciseDemo = run {
    val ahead = facing(stance = 5f).copy(
        upperArm = 90f,
        forearm = 90f,
        farUpperArm = -90f,
        farForearm = -90f,
        reach = 0.12f,
    )
    val apart = ahead.copy(reach = 1f)
    ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = ahead),
            Keyframe(at = 0.1f, pose = ahead),
            Keyframe(at = 0.4f, pose = apart),
            Keyframe(at = 0.5f, pose = apart),
            Keyframe(at = 1f, pose = ahead),
        ),
        phases = phases(0.1f to 0, 0.4f to 1, 1f to 2),
        hold = Hold.Free,
        secondsPerRep = 2.6f,
        still = 0.45f,
        anchor = frontAnchor,
        props = listOf(Band(BandEnd.Hand, BandEnd.FarHand)),
        facing = Facing.Front,
    )
}

/**
 * The lateral walk: in a half squat with a band round the knees, the near foot stepped out wide against the band
 * and the far one brought in after it. It walks on the spot, the hips staying put, as the carries do.
 */
val BandLateralWalkDemo: ExerciseDemo = run {
    val squat = Pose(
        shin = -4f,
        thigh = -38f,
        torso = 0f,
        upperArm = 10f,
        forearm = -140f,
        farShin = 4f,
        farThigh = 38f,
        farUpperArm = -10f,
        farForearm = 140f,
    )
    val steppingOut = squat.copy(shin = 8f, thigh = -58f)
    val wide = squat.copy(shin = -10f, thigh = -48f, farShin = 10f, farThigh = 48f)
    val steppingIn = squat.copy(shin = -2f, thigh = -28f, farShin = -6f, farThigh = 50f)
    ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = squat),
            Keyframe(at = 0.18f, pose = steppingOut),
            Keyframe(at = 0.36f, pose = wide),
            Keyframe(at = 0.5f, pose = wide),
            Keyframe(at = 0.7f, pose = steppingIn),
            Keyframe(at = 0.88f, pose = squat),
            Keyframe(at = 1f, pose = squat),
        ),
        phases = phases(0.5f to 1, 1f to 2),
        hold = Hold.Free,
        secondsPerRep = 1.6f,
        still = 0.4f,
        root = Root.Hips,
        anchor = Offset(240f, Rig.FLOOR),
        props = listOf(Band(BandEnd.Knee, BandEnd.FarKnee)),
        facing = Facing.Front,
    )
}

/** Lying along the floor with the head to the left, the near side uppermost in the picture. */
private const val HEAD_LEFT = -90f

/**
 * The clamshell: lying on one side facing the eye with a band round the knees, the head on the lower arm, the
 * knees bent toward the eye (so the legs look short) and the feet together; the top knee opens upward against
 * the band and closes.
 */
val BandClamshellDemo: ExerciseDemo = run {
    val closed = Pose(
        shin = 0f,
        thigh = 0f,
        torso = 0f,
        upperArm = 6f,
        forearm = -24f,
        farUpperArm = -178f,
        farForearm = -178f,
        reach = 0.75f,
        turn = HEAD_LEFT,
        legs = 0.7f,
    )
    // The knee swings up round the line from the hip to the feet, so more of the leg shows as it opens.
    val open = closed.copy(shin = 35f, thigh = -30.6f, legs = 0.84f, farLegs = 0.7f)
    ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = closed),
            Keyframe(at = 0.1f, pose = closed),
            Keyframe(at = 0.42f, pose = open),
            Keyframe(at = 0.55f, pose = open),
            Keyframe(at = 1f, pose = closed),
        ),
        phases = phases(0.1f to 0, 0.55f to 1, 1f to 2),
        hold = Hold.Free,
        secondsPerRep = 2.4f,
        still = 0.48f,
        root = Root.Hips,
        anchor = Offset(205f, Rig.FLOOR),
        view = Rect(left = 10f, top = 210f, right = 350f, bottom = 380f),
        props = listOf(Band(BandEnd.Knee, BandEnd.FarKnee)),
        facing = Facing.Front,
    )
}

/** A low box for a figure sitting square to the eye. */
private val seatedView = Rect(left = 60f, top = 150f, right = 340f, bottom = 380f)
private val seatedAnchor = Offset(212f, Rig.FLOOR)

/**
 * The butterfly: sitting tall with the soles of the feet together and the hands on them, each knee fallen open to
 * its side, then folded forward over the feet (toward the eye, so the back looks short) as the knees sink.
 */
val ButterflyDemo: ExerciseDemo = run {
    val tall = Pose(
        shin = 80f,
        thigh = -101f,
        torso = 0f,
        upperArm = -8f,
        forearm = -10f,
        farShin = -80f,
        farThigh = 101f,
        farUpperArm = 8f,
        farForearm = 10f,
        legs = 0.9f,
    )
    val folded = tall.copy(
        shin = 84f,
        thigh = -90f,
        farShin = -84f,
        farThigh = 90f,
        upperArm = 50f,
        forearm = -55f,
        farUpperArm = -50f,
        farForearm = 55f,
        trunk = 0.72f,
    )
    ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = tall),
            Keyframe(at = 0.1f, pose = tall),
            Keyframe(at = 0.4f, pose = folded),
            Keyframe(at = 0.6f, pose = folded),
            Keyframe(at = 0.9f, pose = tall),
            Keyframe(at = 1f, pose = tall),
        ),
        phases = phases(0.1f to 0, 0.6f to 1, 1f to 2),
        hold = Hold.Free,
        secondsPerRep = 5f,
        still = 0.5f,
        root = Root.Hips,
        anchor = seatedAnchor,
        view = seatedView,
        facing = Facing.Front,
    )
}

/**
 * The 90/90 hip switch: sitting with the feet planted wide and the knees up, both knees dropped to one side
 * until the legs lie on the floor, lifted over the top and dropped to the other. Each thigh goes over the top,
 * never down through the floor, so its angle runs on past a half turn.
 */
val HipSwitchDemo: ExerciseDemo = run {
    val up = Pose(
        shin = 0f,
        thigh = -135f,
        torso = 0f,
        upperArm = 22f,
        forearm = 6f,
        farShin = 0f,
        farThigh = 135f,
        farUpperArm = -22f,
        farForearm = -6f,
        legs = 0.85f,
    )
    val right = up.copy(shin = 85f, thigh = -95f, torso = 5f, farShin = 75f, farThigh = 280f, legs = 0.7f)
    val left = up.copy(shin = -75f, thigh = -280f, torso = -5f, farShin = -85f, farThigh = 95f, legs = 0.7f)
    ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = right),
            Keyframe(at = 0.12f, pose = right),
            Keyframe(at = 0.3f, pose = up),
            Keyframe(at = 0.45f, pose = left),
            Keyframe(at = 0.62f, pose = left),
            Keyframe(at = 0.8f, pose = up),
            Keyframe(at = 1f, pose = right),
        ),
        phases = phases(0.12f to 0, 0.62f to 1, 1f to 2),
        hold = Hold.Free,
        secondsPerRep = 6f,
        still = 0.05f,
        root = Root.Hips,
        anchor = seatedAnchor,
        view = seatedView,
        facing = Facing.Front,
    )
}

/** The mat's room seen from above, and where the hips lie on it. */
private val aboveView = Rect(left = 0f, top = 100f, right = 400f, bottom = 400f)
private val aboveAnchor = Offset(215f, 250f)

/** A rep on the mat seen from above, from these keyframes. */
private fun fromAbove(
    keys: List<Pair<Float, Pose>>,
    phases: List<DemoPhase>,
    seconds: Float,
    still: Float,
    root: Root = Root.Hips,
    anchor: Offset = aboveAnchor,
) = ExerciseDemo(
    keyframes = keys.map { (at, pose) -> Keyframe(at, pose) },
    phases = phases,
    hold = Hold.Free,
    secondsPerRep = seconds,
    still = still,
    root = root,
    anchor = anchor,
    view = aboveView,
    facing = Facing.Above,
)

/** A position [held] on the mat seen from above, breathing to [breath] and back. */
private fun heldFromAbove(held: Pose, breath: Pose) =
    fromAbove(listOf(0f to held, 0.5f to breath, 1f to held), phases(0.5f to 1, 1f to 2), seconds = 4f, still = 0f)

/**
 * The lying twist: on the back, one knee drawn up and across the body to the floor on the other side, the other
 * leg long, the arms out wide and both shoulders down.
 */
val LyingTwistDemo: ExerciseDemo = run {
    fun twisted(thigh: Float, shin: Float) = Pose(
        shin = shin,
        thigh = thigh,
        torso = 0f,
        upperArm = 90f,
        forearm = 90f,
        farShin = 0f,
        farThigh = 0f,
        farUpperArm = -90f,
        farForearm = -90f,
        turn = HEAD_LEFT,
        legs = 0.85f,
        farLegs = 1f,
    )
    heldFromAbove(twisted(thigh = 88f, shin = -17f), twisted(thigh = 94f, shin = -13f))
}

/**
 * Thread the needle: on all fours, one arm slid under the chest and out the other side, that shoulder down, the
 * other arm long on the floor ahead. The thighs stand upright, so only the shins show of the legs.
 */
val ThreadTheNeedleDemo: ExerciseDemo = run {
    fun threaded(torso: Float, arm: Float) = Pose(
        shin = 0f,
        thigh = 0f,
        torso = torso,
        upperArm = arm,
        forearm = arm + 4f,
        farUpperArm = -176f,
        farForearm = -176f,
        reach = 0.85f,
        behind = 1f,
        turn = HEAD_LEFT,
        legs = 0.5f,
    )
    heldFromAbove(threaded(torso = 6f, arm = -92f), threaded(torso = 8f, arm = -96f))
}

/**
 * The frog: face down on the forearms with the knees wide, each thigh out to its side and the shin back from it,
 * the hips eased back toward the heels and forward again with each breath.
 */
val FrogStretchDemo: ExerciseDemo = run {
    fun frog(back: Float, reach: Float) = Pose(
        shin = 0f,
        thigh = -90f - back,
        torso = 0f,
        upperArm = 170f,
        forearm = 178f,
        farShin = 0f,
        farThigh = 90f + back,
        farUpperArm = -170f,
        farForearm = -178f,
        reach = reach,
        turn = HEAD_LEFT,
        legs = 0.8f,
    )
    heldFromAbove(frog(back = 8f, reach = 0.55f), frog(back = 16f, reach = 0.6f))
}

/**
 * The hip escape: from the back with the knees bent and the feet planted, turned onto the shoulder with the hips
 * pushed away from the feet and out to the side (the legs long, the hands framing), then the knees brought back in.
 * The near foot stays where it is planted.
 */
val HipEscapeDemo: ExerciseDemo = run {
    val bent = Pose(
        shin = 0f,
        thigh = 0f,
        torso = 0f,
        upperArm = 30f,
        forearm = 150f,
        farUpperArm = -30f,
        farForearm = -150f,
        reach = 0.6f,
        turn = HEAD_LEFT,
        legs = 0.6f,
    )
    val escaped = bent.copy(
        shin = 32f,
        thigh = 32f,
        torso = -38f,
        upperArm = 40f,
        forearm = 80f,
        farShin = 32f,
        farThigh = 32f,
        farUpperArm = 10f,
        farForearm = 70f,
        reach = 0.8f,
        legs = 0.95f,
        farLegs = 0.95f,
    )
    fromAbove(
        listOf(0f to bent, 0.1f to bent, 0.4f to escaped, 0.6f to escaped, 0.9f to bent, 1f to bent),
        phases(0.1f to 0, 0.6f to 1, 1f to 2),
        seconds = 2.4f,
        still = 0.5f,
        root = Root.Ankle,
        anchor = Offset(330f, 262f),
    )
}

/**
 * Single-leg circles: on the back, one leg long on the mat, the other up at the ceiling (toward the eye, so it
 * looks short) and circling: across the body, down toward the feet, out to the side and back up.
 */
val SingleLegCirclesDemo: ExerciseDemo = run {
    fun circling(leg: Float) = Pose(
        shin = leg,
        thigh = leg,
        torso = 0f,
        upperArm = 4f,
        forearm = 4f,
        farShin = 0f,
        farThigh = 0f,
        farUpperArm = -4f,
        farForearm = -4f,
        turn = HEAD_LEFT,
        legs = 0.5f,
        farLegs = 1f,
    )
    fromAbove(
        listOf(
            0f to circling(180f),
            0.25f to circling(90f),
            0.5f to circling(0f),
            0.75f to circling(-90f),
            1f to circling(-180f),
        ),
        phases(0.5f to 1, 1f to 2),
        seconds = 3f,
        still = 0.25f,
    )
}
