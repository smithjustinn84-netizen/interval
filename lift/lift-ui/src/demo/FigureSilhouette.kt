package dev.justinsmith.lift.ui.demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * What a figure draws with, kept from frame to frame so a moving one makes nothing: the path each part is traced
 * on, the bell's trail, the layer its body is drawn in and the floor's fading line, for a figure seen in [view]
 * standing on a floor of [floor].
 *
 * It is either inking a part in a colour ([ink]) or cutting the gap round one ([cut]): every part is drawn
 * through it, so the same tracing does both, the cut [pad] wider than the part.
 */
internal class FigurePen(view: Rect, floor: Color) {
    val path = Path()
    val trail = FloatArray(2 * ExerciseDemo.TRAIL_STEPS)
    var trailCount = 0

    /** The breath the chest is at, -1 (out) to 1 (in), and how fresh the drive is, 1 down to 0. */
    var breath = 0f
    var pulse = 0f

    /** The body's own layer, past the view by a limb's reach so nothing near its edge is clipped. */
    val layer = Paint()
    val layerBounds = view.inflate(LAYER_MARGIN)

    /** The floor from one side of the view to the other, fading out at both ends. */
    val floorFrom = view.left + FLOOR_INSET
    val floorTo = view.right - FLOOR_INSET
    val floor: Brush = Brush.horizontalGradient(
        0f to floor.copy(alpha = 0f),
        FLOOR_FADE to floor,
        1f - FLOOR_FADE to floor,
        1f to floor.copy(alpha = 0f),
        startX = floorFrom,
        endX = floorTo,
    )

    var color = Color.Transparent
        private set
    var pad = 0f
        private set
    var blend = BlendMode.SrcOver
        private set
    val cutting: Boolean get() = pad > 0f

    fun ink(color: Color) {
        this.color = color
        pad = 0f
        blend = BlendMode.SrcOver
    }

    fun cut() {
        color = Color.Black
        pad = Build.GAP
        blend = BlendMode.Clear
    }
}

/**
 * Draws a part in [color] over whatever is under it, with a gap cut round it first: the part reads as nearer than
 * what it crosses, on any ground, since the gap shows what is behind the whole figure.
 */
internal inline fun gapped(pen: FigurePen, color: Color, part: () -> Unit) {
    pen.cut()
    part()
    pen.ink(color)
    part()
}

/**
 * A length of limb from [a], [rA] thick, to [b], [rB] thick: two circles joined by their outer tangents, traced
 * on the pen's path.
 */
internal fun DrawScope.drawSegment(pen: FigurePen, a: Offset, b: Offset, rA: Float, rB: Float) {
    val ra = rA + pen.pad
    val rb = rB + pen.pad
    val dx = b.x - a.x
    val dy = b.y - a.y
    val len = hypot(dx, dy)
    if (len <= abs(ra - rb) + MIN_LENGTH) {
        if (ra >= rb) drawJoint(pen, a, rA) else drawJoint(pen, b, rB)
        return
    }
    val ux = dx / len
    val uy = dy / len
    val sinA = ((ra - rb) / len).coerceIn(-MAX_SIN, MAX_SIN)
    val cosA = sqrt(1f - sinA * sinA)
    val d1x = ux * sinA - uy * cosA
    val d1y = uy * sinA + ux * cosA
    val d2x = ux * sinA + uy * cosA
    val d2y = uy * sinA - ux * cosA
    val path = pen.path
    path.rewind()
    path.moveTo(a.x + d1x * ra, a.y + d1y * ra)
    path.lineTo(b.x + d1x * rb, b.y + d1y * rb)
    path.lineTo(b.x + d2x * rb, b.y + d2y * rb)
    path.lineTo(a.x + d2x * ra, a.y + d2y * ra)
    path.close()
    drawPath(path, pen.color, blendMode = pen.blend)
    drawCircle(pen.color, ra, a, blendMode = pen.blend)
    drawCircle(pen.color, rb, b, blendMode = pen.blend)
}

/** A round part [radius] across [at] a joint: a fist, or a length of limb too short to have sides. */
internal fun DrawScope.drawJoint(pen: FigurePen, at: Offset, radius: Float) =
    drawCircle(pen.color, radius + pen.pad, at, blendMode = pen.blend)

/**
 * The trunk and the head: the hips, the waist drawn in, the chest deeper and ahead of the spine, the shoulders,
 * a neck and a head longer than it is wide. The chest swells and sinks with the pen's breath.
 */
internal fun DrawScope.drawTrunk(pen: FigurePen, figure: Figure) {
    val chest = Build.CHEST + pen.breath * Build.BREATH
    val shoulders = Build.SHOULDERS + pen.breath * Build.BREATH * HALF
    val waist = figure.waist()
    val ribs = figure.chest()
    drawSegment(pen, figure.hip, waist, Build.PELVIS, Build.WAIST)
    drawSegment(pen, waist, ribs, Build.WAIST, chest)
    drawSegment(pen, ribs, figure.shoulder, chest, shoulders)
    drawSegment(pen, figure.shoulder, figure.head, Build.NECK, Build.NECK_TOP)
    drawHead(pen, figure.shoulder, figure.head)
}

/** A head longer than it is wide, at [head], in line with the neck up [from] the shoulders. */
internal fun DrawScope.drawHead(pen: FigurePen, from: Offset, head: Offset) {
    val neck = head - from
    val lean = atan2(neck.x, -neck.y) * DEGREES
    val across = Build.HEAD_ACROSS + pen.pad
    val along = Build.HEAD_ALONG + pen.pad
    rotate(lean, head) {
        drawOval(
            pen.color,
            topLeft = Offset(head.x - across, head.y - along),
            size = Size(across * 2, along * 2),
            blendMode = pen.blend,
        )
    }
}

/**
 * A leg, the near one or the [far] one a step behind it and a little slighter: the thigh, the shin with the calf
 * behind it, and a shoe standing on the sole from heel to toe. A gap is cut from partway down the thigh, so the
 * leg stays one with the hips it comes from.
 */
internal fun DrawScope.drawLeg(pen: FigurePen, figure: Figure, far: Boolean = false) {
    val behind = if (far) Offset(-Rig.FAR_LEG_OFFSET, 0f) else Offset.Zero
    val scale = if (far) Build.FAR else 1f
    val knee = (if (far) figure.farKnee else figure.knee) + behind
    val ankle = (if (far) figure.farAnkle else figure.ankle) + behind
    val heel = (if (far) figure.farHeel else figure.heel) + behind
    val toe = (if (far) figure.farToe else figure.toe) + behind
    drawLegFrom(pen, figure.hip, knee, ankle, heel, toe, scale)
}

/**
 * A leg from [hip] by its joints, [scale] of its full thickness. The shoe stands on its sole to the left of the
 * way from heel to toe, or with [side] -1 to the right, for a foot turned the other way.
 */
internal fun DrawScope.drawLegFrom(
    pen: FigurePen,
    hip: Offset,
    knee: Offset,
    ankle: Offset,
    heel: Offset,
    toe: Offset,
    scale: Float = 1f,
    side: Float = 1f,
) {
    val kneeR = Build.KNEE * scale
    val ankleR = Build.ANKLE * scale
    drawFromPartway(pen, hip, knee, Build.THIGH * scale, kneeR)

    val shin = ankle - knee
    val length = shin.getDistance().coerceAtLeast(MIN_LENGTH)
    val down = shin / length
    // The shin stops short of the sole, where the shoe takes over; the calf bulges to the back of it.
    val cuff = ankle - down * (Build.ANKLE_RISE * scale)
    val calf = knee + (cuff - knee) * Build.CALF_AT + Offset(-down.y, down.x) * (Build.CALF_BACK * scale)
    drawSegment(pen, knee, calf, kneeR, Build.CALF * scale)
    drawSegment(pen, calf, cuff, Build.CALF * scale, ankleR)

    val sole = toe - heel
    val along = sole / sole.getDistance().coerceAtLeast(MIN_LENGTH)
    val up = Offset(along.y, -along.x) * side
    val heelR = Build.HEEL * scale
    val ballR = Build.BALL * scale
    val toeR = Build.TOE * scale
    // Each of the shoe's rounds sits its own radius over the sole, so the sole is flat from heel to toe.
    val heelAt = heel + along * heelR + up * heelR
    val ballAt = heel + sole * Build.BALL_AT + up * ballR
    val toeAt = toe - along * toeR + up * toeR
    drawSegment(pen, cuff, heelAt, ankleR, heelR)
    drawSegment(pen, cuff, ballAt, ankleR, ballR)
    drawSegment(pen, heelAt, ballAt, heelR, ballR)
    drawSegment(pen, ballAt, toeAt, ballR, toeR)
}

/**
 * An arm, the near one or the [far] one behind the trunk: the upper arm from the shoulder's cap, the forearm
 * thickest just past the elbow, and a fist. A gap is cut from partway down the upper arm, as a leg's is.
 */
internal fun DrawScope.drawArm(pen: FigurePen, figure: Figure, far: Boolean = false) {
    val scale = if (far) Build.FAR else 1f
    val shoulder = if (far) figure.shoulder - Offset(Rig.FAR_ARM_OFFSET, 0f) else figure.shoulder
    val elbow = if (far) figure.farElbow else figure.elbow
    val hand = if (far) figure.farHand else figure.hand
    drawArmFrom(pen, shoulder, elbow, hand, scale)
}

internal fun DrawScope.drawArmFrom(pen: FigurePen, shoulder: Offset, elbow: Offset, hand: Offset, scale: Float = 1f) {
    val elbowR = Build.ELBOW * scale
    val forearmR = Build.FOREARM * scale
    drawFromPartway(pen, shoulder, elbow, Build.DELTOID * scale, elbowR)
    val belly = elbow + (hand - elbow) * Build.FOREARM_AT
    drawSegment(pen, elbow, belly, elbowR, forearmR)
    drawSegment(pen, belly, hand, forearmR, Build.WRIST * scale)
    drawJoint(pen, hand, Build.FIST * scale)
}

/**
 * A limb's first length from [a] to [b]. Inked, all of it; cut, only from partway along, where the gap opens
 * from nothing, so no gap rings the joint the limb grows from.
 */
private fun DrawScope.drawFromPartway(pen: FigurePen, a: Offset, b: Offset, rA: Float, rB: Float) {
    if (!pen.cutting) {
        drawSegment(pen, a, b, rA, rB)
        return
    }
    val from = a + (b - a) * Build.GAP_FROM
    val there = rA + (rB - rA) * Build.GAP_FROM
    drawSegment(pen, from, b, there - pen.pad - Build.GAP_TUCK, rB)
}

/** The way the chest faces: across the spine, to the right of a figure standing. */
internal fun Figure.front(): Offset {
    val spine = shoulder - hip
    val length = spine.getDistance()
    return if (length > MIN_LENGTH) Offset(-spine.y / length, spine.x / length) else Offset(1f, 0f)
}

/**
 * The middle of the waist, and of the chest: ahead of the spine by as much as it is deeper than the hips, so the
 * back is one line from the hips up and a figure on its back lies on all of it.
 */
internal fun Figure.waist(): Offset = hip + (shoulder - hip) * Build.WAIST_AT

internal fun Figure.chest(): Offset = hip + (shoulder - hip) * Build.CHEST_AT + front() * Build.CHEST_FORWARD

/**
 * The body's build, in [Rig] units: how thick each part is (its radius) and where along its bone it is thickest.
 * The pelvis and a fist are as thick as [Rig] has them resting; a knee and an elbow a little thicker.
 */
internal object Build {
    const val PELVIS = Rig.TRUNK / 2
    const val WAIST = 12.5f
    const val WAIST_AT = 0.42f
    const val CHEST = 16.5f
    const val CHEST_AT = 0.74f
    const val CHEST_FORWARD = CHEST - PELVIS
    const val SHOULDERS = 12f
    const val NECK = 8f
    const val NECK_TOP = 7f

    /** Seen from the front: across the hips, the waist, the chest and the collar, each half its width. */
    const val FRONT_HIPS = 22f
    const val FRONT_WAIST = 18f
    const val FRONT_CHEST = 25f
    const val FRONT_COLLAR = 14f
    const val HEAD_ACROSS = 17.5f
    const val HEAD_ALONG = 19.5f

    /** How far the chest swells with a breath in. */
    const val BREATH = 1.1f

    const val THIGH = 14f
    const val KNEE = 8.5f
    const val CALF = 10.2f
    const val CALF_AT = 0.3f
    const val CALF_BACK = 1.5f
    const val ANKLE = 6.8f

    /** How far up the shin from the sole its cuff is, and the shoe's heel, ball and toe. */
    const val ANKLE_RISE = 8f
    const val HEEL = 6f
    const val BALL = 5.4f
    const val BALL_AT = 0.6f
    const val TOE = 4.4f

    const val DELTOID = 10f
    const val ELBOW = 6.6f
    const val FOREARM = 7.4f
    const val FOREARM_AT = 0.28f
    const val WRIST = 5.6f
    const val FIST = Rig.HAND

    /** The far limbs are a little slighter, for depth. */
    const val FAR = 0.92f

    /**
     * The gap cut round a near part where it crosses another, how far along a limb's first length it opens, and
     * how far inside the limb it starts, to open from nothing.
     */
    const val GAP = 2.6f
    const val GAP_FROM = 0.34f
    const val GAP_TUCK = 0.5f
}

private const val HALF = 0.5f
private const val MIN_LENGTH = 0.001f
private const val MAX_SIN = 0.9999f
private const val DEGREES = (180.0 / PI).toFloat()
private const val LAYER_MARGIN = 60f
private const val FLOOR_INSET = 6f
private const val FLOOR_FADE = 0.14f
