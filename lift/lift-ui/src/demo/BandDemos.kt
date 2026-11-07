// Keyframe tables: their shares of the rep and solved angles are data, as in every demo.
@file:Suppress("detekt.MagicNumber")

package dev.justinsmith.lift.ui.demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

// The band lifts. The band is drawn from the hands to whatever it is tied to or stood on, so it stretches as the
// lift does. A post off to one side (the Pallof press's, the woodchop's) is drawn behind the figure.

private val standing = Pose(shin = 0f, thigh = 0f, torso = 0f, upperArm = 0f, forearm = 8f)

/** The hands at the shoulders, holding a band stood on. */
private val atShoulders = standing.copy(upperArm = 15f, forearm = 165f).bothArms()

/** Sitting tall on the floor, the legs straight ahead and the toes up. */
internal val seated =
    Pose(shin = 270f, thigh = 270f, torso = 0f, upperArm = 0f, forearm = 0f, foot = 270f, farFoot = 270f)

private val underFoot = listOf(Band(BandEnd.Hand, BandEnd.Foot))

/** A low, wide box for a lift done sitting or lying on the floor. */
private val floorView = Rect(left = 20f, top = 150f, right = 390f, bottom = 380f)

/** The band pull-apart: arms straight ahead at shoulder height, the band pulled apart to the chest. */
val BandPullApartDemo: ExerciseDemo = pull(
    from = standing.copy(upperArm = 90f, forearm = 90f).bothArms(),
    to = standing.copy(upperArm = 90f, forearm = 90f, reach = 0.15f).bothArms(),
    band = Band(BandEnd.Hand, BandEnd.FarHand),
)

/** The face pull: from a post at face height, the hands pulled to beside the ears, the elbows high and wide. */
val BandFacePullDemo: ExerciseDemo = pull(
    from = standing.copy(torso = -3f, upperArm = 96f, forearm = 96f).bothArms(),
    to = standing.copy(torso = -3f, upperArm = 95f, forearm = 175f, reach = 0.55f).bothArms(),
    band = Band(BandEnd.Hand, BandEnd.Post(Offset(372f, 78f))),
)

/** The seated row: sitting tall, the band round the feet, the elbows pulled back past the ribs. */
val BandSeatedRowDemo: ExerciseDemo = pull(
    from = seated.copy(upperArm = 75f, forearm = 75f).bothArms(),
    to = seated.copy(upperArm = -35f, forearm = 85f).bothArms(),
    band = Band(BandEnd.Hand, BandEnd.Foot),
    root = Root.Hips,
    anchor = Offset(140f, Rig.FLOOR),
    view = floorView,
)

/** The lat pulldown: from a post high ahead, the elbows pulled down to the hips. */
val BandLatPulldownDemo: ExerciseDemo = pull(
    from = standing.copy(torso = -5f, upperArm = 148f, forearm = 148f).bothArms(),
    to = standing.copy(torso = -5f, upperArm = 10f, forearm = 160f).bothArms(),
    band = Band(BandEnd.Hand, BandEnd.Post(Offset(305f, -52f))),
)

/** The chest press: from a post behind at chest height, the hands pressed straight ahead. */
val BandChestPressDemo: ExerciseDemo = pull(
    from = standing.copy(upperArm = -60f, forearm = 90f).bothArms(),
    to = standing.copy(upperArm = 90f, forearm = 90f).bothArms(),
    band = Band(BandEnd.Hand, BandEnd.Post(Offset(95f, 112f))),
)

/** The overhead press: stood on the band, the hands pressed from the shoulders to overhead. */
val BandOverheadPressDemo: ExerciseDemo = run {
    val overhead = standing.copy(upperArm = 180f, forearm = 180f).bothArms()
    ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = atShoulders),
            Keyframe(at = 0.1f, pose = atShoulders),
            Keyframe(at = 0.28f, pose = standing.copy(upperArm = 120f, forearm = 175f).bothArms()),
            Keyframe(at = 0.42f, pose = overhead),
            Keyframe(at = 0.5f, pose = overhead),
            Keyframe(at = 1f, pose = atShoulders),
        ),
        phases = phases(0.1f to 0, 0.5f to 1, 1f to 2),
        hold = Hold.Free,
        secondsPerRep = 2.6f,
        still = 0.46f,
        props = underFoot,
    )
}

/** The biceps curl: stood on the band, the elbows by the sides, the hands curled to the shoulders. */
val BandBicepsCurlDemo: ExerciseDemo = pull(
    from = standing.copy(upperArm = 0f, forearm = 0f).bothArms(),
    to = standing.copy(upperArm = 0f, forearm = 150f).bothArms(),
    band = underFoot.single(),
)

/** The triceps pushdown: from a post high ahead, the elbows pinned, the hands pushed down straight. */
val BandTricepsPushdownDemo: ExerciseDemo = pull(
    from = standing.copy(torso = 10f, upperArm = 5f, forearm = 130f).bothArms(),
    to = standing.copy(torso = 10f, upperArm = 5f, forearm = 5f).bothArms(),
    band = Band(BandEnd.Hand, BandEnd.Post(Offset(292f, -40f))),
)

/**
 * The Pallof press: side on to a post at chest height (drawn behind), the hands pressed straight out from the
 * chest and held there against the pull.
 */
val BandPallofPressDemo: ExerciseDemo = run {
    val stance = Pose(shin = 8f, thigh = -12f, torso = 4f, upperArm = 10f, forearm = 120f)
    val out = stance.copy(upperArm = 90f, forearm = 90f).bothArms()
    ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = stance),
            Keyframe(at = 0.1f, pose = stance),
            Keyframe(at = 0.35f, pose = out),
            Keyframe(at = 0.65f, pose = out),
            Keyframe(at = 1f, pose = stance),
        ),
        phases = phases(0.1f to 0, 0.35f to 1, 1f to 2),
        hold = Hold.Free,
        secondsPerRep = 3.2f,
        still = 0.5f,
        props = listOf(Band(BandEnd.Hand, BandEnd.Post(Offset(95f, 118f)))),
    )
}

/** The woodchop: from a post high on one side (drawn behind), the hands pulled across and down to the hip. */
val BandWoodchopDemo: ExerciseDemo = pull(
    from = standing.copy(torso = -5f, upperArm = 210f, forearm = 210f).bothArms(),
    to = Pose(shin = 12f, thigh = -25f, torso = 20f, upperArm = 25f, forearm = 25f),
    band = Band(BandEnd.Hand, BandEnd.Post(Offset(95f, -20f))),
)

/** The band squat: stood on the band, the ends at the shoulders, sit down between the heels and drive up. */
val BandSquatDemo: ExerciseDemo = run {
    val deep = Pose(shin = 30f, thigh = -105f, torso = 25f, upperArm = 30f, forearm = 175f)
    ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = atShoulders),
            Keyframe(at = 0.1f, pose = atShoulders),
            Keyframe(at = 0.3f, pose = Pose(shin = 18f, thigh = -55f, torso = 20f, upperArm = 25f, forearm = 172f)),
            Keyframe(at = 0.5f, pose = deep),
            Keyframe(at = 0.56f, pose = deep),
            Keyframe(at = 0.92f, pose = atShoulders),
            Keyframe(at = 1f, pose = atShoulders),
        ),
        phases = phases(0.1f to 0, 0.56f to 1, 1f to 2),
        hold = Hold.Free,
        secondsPerRep = 2.6f,
        still = 0.52f,
        props = underFoot,
    )
}

/** The good morning: stood on the band, its loop behind the neck, a hinge with a flat back and up. */
val BandGoodMorningDemo: ExerciseDemo = pull(
    from = standing.copy(upperArm = 20f, forearm = 170f).bothArms(),
    // The upper body tipped over as one, so the hands stay at the shoulders.
    to = Pose(shin = 5f, thigh = -10f, torso = 75f, upperArm = -55f, forearm = 95f),
    band = Band(BandEnd.Neck, BandEnd.Foot),
)

/** The banded glute bridge: the bridge with a band above the knees, pushed out against. */
val BandGluteBridgeDemo: ExerciseDemo = bridge(single = false, props = listOf(Band(BandEnd.Knee, BandEnd.FarKnee)))

/**
 * The lateral walk: in a half squat with a band round the knees, stepping sideways. The steps go across the
 * view, so they show as each foot lifting and setting down in turn.
 */
val BandLateralWalkDemo: ExerciseDemo = run {
    val squat = Pose(shin = 25f, thigh = -60f, torso = 30f, upperArm = 40f, forearm = 80f)
    val nearUp = squat.copy(thigh = -75f, farThigh = -60f)
    val farUp = squat.copy(farThigh = -75f)
    ExerciseDemo(
        keyframes = listOf(
            Keyframe(at = 0f, pose = squat),
            Keyframe(at = 0.15f, pose = nearUp),
            Keyframe(at = 0.3f, pose = squat),
            Keyframe(at = 0.5f, pose = squat),
            Keyframe(at = 0.65f, pose = farUp),
            Keyframe(at = 0.8f, pose = squat),
            Keyframe(at = 1f, pose = squat),
        ),
        phases = phases(0.5f to 1, 1f to 2),
        hold = Hold.Free,
        secondsPerRep = 1.6f,
        still = 0.15f,
        root = Root.Hips,
        props = listOf(Band(BandEnd.Knee, BandEnd.FarKnee)),
    )
}

/**
 * The clamshell: lying on one side with a band round the knees, the top knee opened and closed, the feet
 * together. Seen from the front, so the bent knees point at the eye and the top one opens upward.
 */
val BandClamshellDemo: ExerciseDemo = run {
    val closed = Pose(
        shin = -90f,
        thigh = -90f,
        torso = -80f,
        upperArm = -80f,
        forearm = -90f,
        farShin = -83f,
        farThigh = -97f,
        farUpperArm = 85f,
        farForearm = 85f,
    )
    val open = closed.copy(farShin = -71f, farThigh = -112f)
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
        anchor = Offset(185f, Rig.FLOOR),
        view = Rect(left = -40f, top = 180f, right = 390f, bottom = 380f),
        props = listOf(Band(BandEnd.Knee, BandEnd.FarKnee)),
    )
}

/** This pose with the far arm doing as the near one does. */
private fun Pose.bothArms() = copy(farUpperArm = upperArm, farForearm = forearm)

/** A rep pulled [from] one pose [to] another, held there a moment and let back slowly, with [band] stretched. */
private fun pull(
    from: Pose,
    to: Pose,
    band: Band,
    root: Root = Root.Ankle,
    anchor: Offset = Offset(Rig.ANKLE_X, Rig.FLOOR),
    view: Rect = Rig.view,
) = ExerciseDemo(
    keyframes = listOf(
        Keyframe(at = 0f, pose = from),
        Keyframe(at = 0.1f, pose = from),
        Keyframe(at = 0.4f, pose = to),
        Keyframe(at = 0.5f, pose = to),
        Keyframe(at = 1f, pose = from),
    ),
    phases = phases(0.1f to 0, 0.4f to 1, 1f to 2),
    hold = Hold.Free,
    secondsPerRep = 2.6f,
    still = 0.45f,
    root = root,
    anchor = anchor,
    view = view,
    props = listOf(band),
)
