// Keyframe tables: their shares of the rep and solved angles are data, as in every demo.
@file:Suppress("detekt.MagicNumber")

package dev.justinsmith.lift.ui.demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.justinsmith.interval.ui.ExerciseRep

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

/** The face pull: from a post at face height, the hands pulled to beside the ears, the elbows high and wide. */
val BandFacePullDemo: ExerciseRep = pull(
    from = standing.copy(torso = -3f, upperArm = 96f, forearm = 96f).bothArms(),
    to = standing.copy(torso = -3f, upperArm = 95f, forearm = 175f, reach = 0.55f).bothArms(),
    band = Band(BandEnd.Hand, BandEnd.post(Offset(372f, 78f))),
)

/** The seated row: sitting tall, the band round the feet, the elbows pulled back past the ribs. */
val BandSeatedRowDemo: ExerciseRep = pull(
    from = seated.copy(upperArm = 75f, forearm = 75f).bothArms(),
    to = seated.copy(upperArm = -35f, forearm = 85f).bothArms(),
    band = Band(BandEnd.Hand, BandEnd.Foot),
    root = Root.Hips,
    anchor = Offset(140f, Rig.FLOOR),
    view = floorView,
)

/** The lat pulldown: from a post high ahead, the elbows pulled down to the hips. */
val BandLatPulldownDemo: ExerciseRep = pull(
    from = standing.copy(torso = -5f, upperArm = 148f, forearm = 148f).bothArms(),
    to = standing.copy(torso = -5f, upperArm = 10f, forearm = 160f).bothArms(),
    band = Band(BandEnd.Hand, BandEnd.post(Offset(305f, -52f))),
)

/** The chest press: from a post behind at chest height, the hands pressed straight ahead. */
val BandChestPressDemo: ExerciseRep = pull(
    from = standing.copy(upperArm = -60f, forearm = 90f).bothArms(),
    to = standing.copy(upperArm = 90f, forearm = 90f).bothArms(),
    band = Band(BandEnd.Hand, BandEnd.post(Offset(95f, 112f))),
)

/** The overhead press: stood on the band, the hands pressed from the shoulders to overhead. */
val BandOverheadPressDemo: ExerciseRep = run {
    val overhead = standing.copy(upperArm = 180f, forearm = 180f).bothArms()
    repOf(
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
val BandBicepsCurlDemo: ExerciseRep = pull(
    from = standing.copy(upperArm = 0f, forearm = 0f).bothArms(),
    to = standing.copy(upperArm = 0f, forearm = 150f).bothArms(),
    band = underFoot.single(),
)

/** The triceps pushdown: from a post high ahead, the elbows pinned, the hands pushed down straight. */
val BandTricepsPushdownDemo: ExerciseRep = pull(
    from = standing.copy(torso = 10f, upperArm = 5f, forearm = 130f).bothArms(),
    to = standing.copy(torso = 10f, upperArm = 5f, forearm = 5f).bothArms(),
    band = Band(BandEnd.Hand, BandEnd.post(Offset(292f, -40f))),
)

/**
 * The Pallof press: side on to a post at chest height (drawn behind), the hands pressed straight out from the
 * chest and held there against the pull.
 */
val BandPallofPressDemo: ExerciseRep = run {
    val stance = Pose(shin = 8f, thigh = -12f, torso = 4f, upperArm = 10f, forearm = 120f)
    val out = stance.copy(upperArm = 90f, forearm = 90f).bothArms()
    repOf(
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
        props = listOf(Band(BandEnd.Hand, BandEnd.post(Offset(95f, 118f)))),
    )
}

/** The woodchop: from a post high on one side (drawn behind), the hands pulled across and down to the hip. */
val BandWoodchopDemo: ExerciseRep = pull(
    from = standing.copy(torso = -5f, upperArm = 210f, forearm = 210f).bothArms(),
    to = Pose(shin = 12f, thigh = -25f, torso = 20f, upperArm = 25f, forearm = 25f),
    band = Band(BandEnd.Hand, BandEnd.post(Offset(95f, -20f))),
)

/** The band squat: stood on the band, the ends at the shoulders, sit down between the heels and drive up. */
val BandSquatDemo: ExerciseRep = run {
    val deep = Pose(shin = 30f, thigh = -105f, torso = 25f, upperArm = 30f, forearm = 175f)
    repOf(
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
val BandGoodMorningDemo: ExerciseRep = pull(
    from = standing.copy(upperArm = 20f, forearm = 170f).bothArms(),
    // The upper body tipped over as one, so the hands stay at the shoulders.
    to = Pose(shin = 5f, thigh = -10f, torso = 75f, upperArm = -55f, forearm = 95f),
    band = Band(BandEnd.Neck, BandEnd.Foot),
)

/** The banded glute bridge: the bridge with a band above the knees, pushed out against. */
val BandGluteBridgeDemo: ExerciseRep = bridge(single = false, props = listOf(Band(BandEnd.Knee, BandEnd.FarKnee)))

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
) = repOf(
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
