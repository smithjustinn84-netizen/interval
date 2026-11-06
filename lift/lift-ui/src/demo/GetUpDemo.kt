package dev.justinsmith.lift.ui.demo

import androidx.compose.ui.geometry.Rect
import dev.justinsmith.lift.ui.demo.KettlebellPoses.lockout

/** A get-up's pose: the legs and torso of [body], the bell's arm straight up over the shoulder. */
private fun getUp(
    body: Pose,
) = lockout.standingAs(body).copy(farUpperArm = body.farUpperArm, farForearm = body.farForearm)

/** On the back, the bell side's knee bent with the foot flat, the other leg long on the floor, the free arm out. */
private val lying = getUp(
    Pose(
        shin = -40f,
        thigh = -126f,
        torso = -82f,
        upperArm = 90f,
        forearm = 90f,
        farShin = -92f,
        farThigh = -92f,
        farFoot = -90f,
    ),
)

/** Rolled up onto the free elbow. */
private val onElbow = getUp(
    lying.copy(torso = -55f, farUpperArm = 10f, farForearm = 70f),
)

/** Up onto the free hand, sitting. */
private val onHand = getUp(lying.copy(torso = -25f, farUpperArm = -5f, farForearm = -5f))

/** The hips bridged high off the floor between the planted foot and hand, the long leg straight. */
private val bridged = getUp(
    Pose(
        shin = 15f,
        thigh = -75f,
        torso = -80f,
        upperArm = 0f,
        forearm = 0f,
        farShin = -60f,
        farThigh = -60f,
        farFoot = -60f,
    ),
)

/** Sweeping the long leg back under: first the knee drawn up, then the foot folded behind it. */
private val legLifted = getUp(bridged.copy(farShin = 20f, farThigh = -100f, farFoot = 0f))
private val legFolded = getUp(
    bridged.copy(shin = 12f, thigh = -78f, farShin = 95f, farThigh = -60f, farFoot = 20f),
)

/** Kneeling on the swept leg's knee, still leaning on the hand. */
private val kneeling = getUp(
    bridged.copy(shin = 10f, thigh = -80f, farShin = 105f, farThigh = 18f, farFoot = 60f),
)

/** The torso swept upright off the hand into a half-kneeling lunge, the free arm hanging. */
private val halfKneeling = getUp(kneeling.copy(torso = 0f, farUpperArm = 0f, farForearm = FREE_BEND))

/** Rising out of the lunge, the back foot stepping through. */
private val stepping = getUp(
    halfKneeling.copy(shin = 4f, thigh = -22f, farShin = 85f, farThigh = 28f, farFoot = 10f),
)

/** Standing tall with the bell locked out. */
private val tall = getUp(
    Pose(shin = 0f, thigh = 0f, torso = -2f, upperArm = 0f, forearm = 0f, farUpperArm = 0f, farForearm = FREE_BEND),
)

/**
 * The get-up: from lying on the back with the bell pressed straight up, roll to the elbow, then the hand,
 * bridge the hips, sweep the straight leg back under to kneel, sweep the torso upright into a lunge and stand;
 * then every step back down to the floor. The bell's arm stays straight up throughout and the near foot (the
 * bell side's) stays planted. Its phases are its cues: Eyes on the bell (0), Elbow, hand, bridge, sweep (1),
 * Lunge, stand and back down (2).
 */
val GetUpDemo: ExerciseDemo = run {
    val up = listOf(lying, onElbow, onHand, bridged, legLifted, legFolded, kneeling, halfKneeling, stepping, tall)
    val upTimes = listOf(0f, 0.05f, 0.1f, 0.15f, 0.19f, 0.23f, 0.27f, 0.33f, 0.39f, 0.46f)
    val climb = up.zip(upTimes) { pose, at -> Keyframe(at = at, pose = pose) }
    val descent = up.zip(upTimes) { pose, at -> Keyframe(at = 1f - at, pose = pose) }.reversed()
    ExerciseDemo(
        keyframes = climb + descent,
        phases = listOf(
            DemoPhase(until = 0.05f, beat = DemoBeat.Set, cue = 0),
            DemoPhase(until = 0.27f, beat = DemoBeat.Drive, cue = 1),
            DemoPhase(until = 0.54f, beat = DemoBeat.Drive, cue = 2),
            DemoPhase(until = 1f, beat = DemoBeat.Ease, cue = 2),
        ),
        hold = Hold.NearHand,
        secondsPerRep = 14f,
        still = 0.1f,
        view = Rect(left = -80f, top = -40f, right = 330f, bottom = 380f),
    )
}

private const val FREE_BEND = 10f
