@file:Suppress("detekt.MagicNumber", "detekt.LongParameterList")

package dev.justinsmith.lift.ui.demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.justinsmith.interval.ui.Angles
import dev.justinsmith.interval.ui.BodyPose
import dev.justinsmith.interval.ui.ExerciseKeyframe
import dev.justinsmith.interval.ui.ExerciseRep
import dev.justinsmith.interval.ui.RepBand
import dev.justinsmith.interval.ui.RepBandEnd
import dev.justinsmith.interval.ui.RepBeat
import dev.justinsmith.interval.ui.RepBench
import dev.justinsmith.interval.ui.RepFacing
import dev.justinsmith.interval.ui.RepHold
import dev.justinsmith.interval.ui.RepProp
import dev.justinsmith.interval.ui.RepRail
import dev.justinsmith.interval.ui.RepRoot
import dev.justinsmith.interval.ui.RepSpan
import dev.justinsmith.interval.ui.RepWall
import kotlin.math.abs

// Lightweight data-table builder that converts side/front/above pose tables and Rig-unit prop coordinates
// directly into core's ExerciseRep and BodyPose. No separate runtime model or renderer exists.

internal typealias DemoBeat = RepBeat
internal typealias Hold = RepHold
internal typealias Root = RepRoot
internal typealias Facing = RepFacing
internal typealias Prop = RepProp
internal typealias Band = RepBand

/** Input-table pose using absolute lean/hang angles, converted 1-to-1 into relative [BodyPose] joint bends. */
internal data class Pose(
    val shin: Float,
    val thigh: Float,
    val torso: Float,
    val upperArm: Float,
    val forearm: Float,
    val bell: Float = forearm,
    val farShin: Float = shin,
    val farThigh: Float = thigh,
    val farFoot: Float = 0f,
    val farUpperArm: Float = upperArm,
    val farForearm: Float = forearm,
    val farBell: Float = bell,
    val foot: Float = 0f,
    val reach: Float = 1f,
    val behind: Float = 0f,
    val turn: Float = 0f,
    val legs: Float = 1f,
    val farLegs: Float = legs,
    val trunk: Float = 1f,
    val spine: Float = 0f,
    val wrist: Float = 0f,
    val farWrist: Float = wrist,
    val palm: Float = 0f,
    val farPalm: Float = palm,
) {
    /** Converts these absolute lean/hang angles into [BodyPose]'s relative joint bend angles. */
    fun toBodyPose(): BodyPose = BodyPose(
        hipAngleDeg = torso - thigh,
        kneeAngleDeg = shin - thigh,
        torsoAngleDeg = torso,
        shoulderAngleDeg = upperArm + torso,
        elbowAngleDeg = forearm - upperArm,
        bellAngleDeg = bell - forearm,
        farHipAngleDeg = torso - farThigh,
        farKneeAngleDeg = farShin - farThigh,
        farShoulderAngleDeg = farUpperArm + torso,
        farElbowAngleDeg = farForearm - farUpperArm,
        farBellAngleDeg = farBell - farForearm,
        footPitchDeg = foot,
        farFootPitchDeg = farFoot,
        spineBowDeg = spine,
        wristDeg = wrist,
        farWristDeg = farWrist,
        palmOpen = palm,
        farPalmOpen = farPalm,
        armReach = reach,
        legsScale = legs,
        farLegsScale = farLegs,
        trunkScale = trunk,
        armsBehind = behind,
        bodyTurnDeg = turn,
    )

    internal fun angles(): FloatArray = floatArrayOf(
        shin, thigh, torso, upperArm, forearm, bell, farShin, farThigh, farFoot,
        farUpperArm, farForearm, farBell, foot, reach, behind, turn, legs, farLegs,
        trunk, spine, wrist, farWrist, palm, farPalm,
    )

    internal companion object {
        const val JOINTS = 24

        fun of(a: FloatArray) = Pose(
            shin = a[0], thigh = a[1], torso = a[2], upperArm = a[3], forearm = a[4], bell = a[5],
            farShin = a[6], farThigh = a[7], farFoot = a[8], farUpperArm = a[9], farForearm = a[10],
            farBell = a[11], foot = a[12], reach = a[13], behind = a[14], turn = a[15], legs = a[16],
            farLegs = a[17], trunk = a[18], spine = a[19], wrist = a[20], farWrist = a[21],
            palm = a[22], farPalm = a[23],
        )
    }
}

internal data class Keyframe(val at: Float, val pose: Pose)

internal data class DemoPhase(val until: Float, val beat: RepBeat, val cue: Int)

/** Reference unit scale used by the exercise pose tables (standing height = 311 units, floor = 360). */
internal object Rig {
    const val ANKLE_X = 205f
    const val FLOOR = 360f
    const val FOOT = 30f
    const val HEEL = 6f
    const val HAND = 7.5f
    const val UPPER_ARM = 50f
    const val SHOULDER_HALF = 27f
    const val HEIGHT = 311f
    val view = Rect(left = 20f, top = -70f, right = 390f, bottom = 380f)
}

internal fun rigX(x: Float): Float = (x - Rig.ANKLE_X) / Rig.HEIGHT

internal fun rigY(y: Float): Float = (Rig.FLOOR - y) / Rig.HEIGHT

internal fun rigOffset(offset: Offset): Offset = Offset(rigX(offset.x), rigY(offset.y))

internal fun rigCrop(rect: Rect): Rect = Rect(
    left = rigX(rect.left),
    top = rigY(rect.bottom),
    right = rigX(rect.right),
    bottom = rigY(rect.top),
)

internal fun bench(from: Float, to: Float, height: Float): RepBench =
    RepBench(from = rigX(from), to = rigX(to), height = height / Rig.HEIGHT)

internal fun wall(x: Float): RepWall = RepWall(x = rigX(x))

internal fun rail(at: Offset): RepRail = RepRail(at = rigOffset(at))

internal object BandEnd {
    val Hand: RepBandEnd = RepBandEnd.Hand
    val FarHand: RepBandEnd = RepBandEnd.FarHand
    val Foot: RepBandEnd = RepBandEnd.Foot
    val Knee: RepBandEnd = RepBandEnd.Knee
    val FarKnee: RepBandEnd = RepBandEnd.FarKnee
    val Neck: RepBandEnd = RepBandEnd.Neck
    fun post(at: Offset): RepBandEnd = RepBandEnd.Post(rigOffset(at))
}

internal fun Pose.standingAs(legs: Pose) = copy(
    shin = legs.shin,
    thigh = legs.thigh,
    torso = legs.torso,
    farShin = legs.farShin,
    farThigh = legs.farThigh,
    farFoot = legs.farFoot,
    foot = legs.foot,
)

internal fun Pose.stillHand() = copy(farUpperArm = 0f, farForearm = 6f, farBell = 6f)

/** Builds a core [ExerciseRep] directly from a keyframe/phase data table. */
internal fun repOf(
    keyframes: List<Keyframe>,
    phases: List<DemoPhase>,
    hold: RepHold,
    secondsPerRep: Float,
    still: Float,
    bellLag: Float = 0f,
    trail: Float = 0f,
    root: RepRoot = RepRoot.Ankle,
    @Suppress("UNUSED_PARAMETER") view: Rect = Rig.view,
    props: List<RepProp> = emptyList(),
    anchor: Offset = Offset(Rig.ANKLE_X, Rig.FLOOR),
    facing: RepFacing = RepFacing.Side,
    crop: Rect? = null,
    follows: Boolean = false,
): ExerciseRep {
    val normAnchor = rigOffset(anchor)
    val normCrop = crop?.let(::rigCrop)
    val repKeyframes = keyframes.mapIndexed { index, kf ->
        val weight = if (index == 0) 0f else kf.at - keyframes[index - 1].at
        ExerciseKeyframe(pose = kf.pose.toBodyPose(), weight = weight, passing = true)
    }
    var stillIndex = 0
    var bestDist = Float.MAX_VALUE
    keyframes.forEachIndexed { index, kf ->
        val d = abs(kf.at - still)
        if (d < bestDist) {
            bestDist = d
            stillIndex = index
        }
    }
    val repSpans = phases.mapIndexed { index, phase ->
        RepSpan(
            from = if (index == 0) 0f else phases[index - 1].until,
            until = phase.until,
            beat = phase.beat,
            cueIndex = phase.cue,
        )
    }
    val autoFollow = follows || shouldFollow(repKeyframes, root, normAnchor, facing, hold, props, normCrop)
    return ExerciseRep(
        keyframes = repKeyframes,
        seconds = secondsPerRep,
        stillAt = stillIndex,
        hold = hold,
        facing = facing,
        root = root,
        anchor = normAnchor,
        props = props,
        bellLag = bellLag,
        showsTrajectory = hold != RepHold.Free && facing == RepFacing.Side,
        showsTrail = trail > 0f,
        crop = normCrop,
        follows = autoFollow,
        continuousLoop = true,
        explicitSpans = repSpans,
    )
}

private fun shouldFollow(
    keyframes: List<ExerciseKeyframe>,
    root: RepRoot,
    anchor: Offset,
    facing: RepFacing,
    hold: RepHold,
    props: List<RepProp>,
    crop: Rect?,
): Boolean {
    if (props.isNotEmpty() || crop != null || facing != RepFacing.Side) return false
    var minTop = Float.MAX_VALUE
    var maxTop = -Float.MAX_VALUE
    var lowShoulders = false
    for (kf in keyframes) {
        val j = kf.pose.joints(root, anchor, facing, hold)
        val topY = maxOf(
            maxOf(j.headCenter.y, j.shoulder.y, j.hip.y, j.knee.y),
            maxOf(j.kettlebellCenter.y, j.kettlebellAnchor.y, j.farBellCenter.y, j.ankle.y, j.farAnkle.y),
        )
        if (topY < minTop) minTop = topY
        if (topY > maxTop) maxTop = topY
        if (j.neck.y < FOLLOW_LOW_SHOULDER_Y) lowShoulders = true
    }
    return maxTop - minTop > FOLLOW_RANGE_Y && lowShoulders
}

private const val FOLLOW_RANGE_Y = 0.48f
private const val FOLLOW_LOW_SHOULDER_Y = 0.42f

internal const val FULL_TURN = Angles.FULL_TURN
internal const val BOTTOMS_UP_ANGLE = Angles.HALF_TURN
internal const val RESTING_ANGLE = -25f
internal const val HORNS_ANGLE = 12f
internal val rackPose = Pose(shin = 0f, thigh = 0f, torso = -2f, upperArm = 18f, forearm = 158f, bell = RESTING_ANGLE)
