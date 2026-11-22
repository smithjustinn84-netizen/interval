package dev.justinsmith.interval.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
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

// Organic silhouette rendering on BodyFrame coordinates (mapped into Y-down canvas pixels by DemoStage).
// Kept allocation-free per frame: one BodyPen holds the scratch Path, offscreen layer Paint, and trail buffer.

/**
 * Reusable drawing state for one [ExerciseDemoCanvas]: scratch path, offscreen body layer paint, trail buffer,
 * current breath/pulse/scale, and ink-vs-cutaway mode.
 *
 * A ghost's body is drawn in two passes over the same parts, which leave one line round all of it: [rim], every
 * part a stroke's width fatter in the one ink, then [hollow], every part at its own size cleared out. While
 * either is on ([outlining]) [ink] keeps the pass's ink whatever colour it is handed and [gapped] cuts no gap,
 * so what draws a body needs no branch for it; [solid] ends it. A kettlebell is the one part filled in, in the
 * second pass where it lies among the others: a ring that small is lost in the body's line.
 */
internal class BodyPen {
    val path = Path()
    val layer = Paint()
    val trail = FloatArray(2 * TRAIL_STEPS)
    var trailCount = 0

    var scale = 1f
    var breath = 0f
    var pulse = 0f

    var color = Color.Transparent
        private set
    var pad = 0f
        private set
    var blend = BlendMode.SrcOver
        private set

    /** While a gap is being cleared round a part, rather than the part drawn. */
    var cutting = false
        private set

    private var outline = Outline.None
    private var outlineWidth = 0f

    /** The one ink of the outline under way. */
    var outlineInk = Color.Transparent
        private set
    val outlining: Boolean get() = outline != Outline.None

    /** In an outline's first pass, which a held weight keeps out of: it is drawn whole in the second. */
    val rimming: Boolean get() = outline == Outline.Rim

    fun ink(color: Color) {
        cutting = false
        when (outline) {
            Outline.None -> {
                this.color = color
                pad = 0f
                blend = BlendMode.SrcOver
            }

            Outline.Rim -> {
                this.color = outlineInk
                pad = outlineWidth
                blend = BlendMode.SrcOver
            }

            Outline.Hollow -> {
                this.color = Color.Black
                pad = 0f
                blend = BlendMode.Clear
            }
        }
    }

    fun cut() {
        cutting = true
        color = Color.Black
        pad = BodyBuild.GAP * scale
        blend = BlendMode.Clear
    }

    /** Starts an outline's first pass: every part from here [width] pixels fatter, in [color]. */
    fun rim(color: Color, width: Float) {
        outline = Outline.Rim
        outlineInk = color
        outlineWidth = width
        ink(color)
    }

    /** An outline's second pass: every part from here cleared at its own size, leaving the rim. */
    fun hollow() {
        outline = Outline.Hollow
        ink(outlineInk)
    }

    /** Ends an outline: parts are drawn filled and gapped again. */
    fun solid() {
        outline = Outline.None
    }

    private enum class Outline { None, Rim, Hollow }

    companion object {
        /** Number of sampled points in a tapered motion ribbon trail. */
        const val TRAIL_STEPS = 9
    }
}

/**
 * Draws a body part in [color] over whatever is behind it in the body layer, first cutting a thin gap round its
 * outline so overlapping limbs read cleanly on any background. In an outline's pass ([BodyPen.outlining]) the part
 * is drawn once, as the pass has it, with no gap: the parts are to run together.
 */
internal inline fun gapped(pen: BodyPen, color: Color, part: () -> Unit) {
    if (!pen.outlining) {
        pen.cut()
        part()
    }
    pen.ink(color)
    part()
}

/**
 * A tapered capsule from [a] (radius [rA] in pixels) to [b] (radius [rB] in pixels): two circles joined by their
 * outer tangents, traced onto [pen]'s reusable path.
 */
internal fun DrawScope.drawSegment(pen: BodyPen, a: Offset, b: Offset, rA: Float, rB: Float) {
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

/** A round part of [radius] pixels at [at]: a fist, or a foreshortened limb too short to have sides. */
internal fun DrawScope.drawJoint(pen: BodyPen, at: Offset, radius: Float) =
    drawCircle(pen.color, radius + pen.pad, at, blendMode = pen.blend)

/**
 * The trunk and head in profile: pelvis, drawn-in waist, breathing chest ahead of the spine, shoulders, neck, and
 * tilted oval head. [spineBow] bows the waist and chest back (> 0, rounded) or forward (< 0, arched).
 */
internal fun DrawScope.drawSideTrunk(pen: BodyPen, hip: Offset, shoulder: Offset, head: Offset, spineBow: Float) {
    val s = pen.scale
    val chest = (BodyBuild.CHEST + pen.breath * BodyBuild.BREATH) * s
    val shoulders = (BodyBuild.SHOULDERS + pen.breath * BodyBuild.BREATH * HALF) * s
    val spine = shoulder - hip
    val spineLen = spine.getDistance()
    // In Y-down screen space, facing right: perpendicular to spine pointing forward is (-spine.y, spine.x).
    val front = if (spineLen > MIN_LENGTH) Offset(-spine.y / spineLen, spine.x / spineLen) else Offset(1f, 0f)
    val bowPx = spineBow * BodyBuild.BOW * s
    val waist = hip + spine * BodyBuild.WAIST_AT - front * (bowPx * BodyBuild.WAIST_BOW)
    val ribs = hip + spine * BodyBuild.CHEST_AT + front * (BodyBuild.CHEST_FORWARD * s - bowPx * BodyBuild.CHEST_BOW)
    drawSegment(pen, hip, waist, BodyBuild.PELVIS * s, BodyBuild.WAIST * s)
    drawSegment(pen, waist, ribs, BodyBuild.WAIST * s, chest)
    drawSegment(pen, ribs, shoulder, chest, shoulders)
    drawSegment(pen, shoulder, head, BodyBuild.NECK * s, BodyBuild.NECK_TOP * s)
    drawOvalHead(pen, shoulder, head)
}

/** An oval head longer than it is wide, aligned with the neck up [from] the shoulders to [head]. */
internal fun DrawScope.drawOvalHead(pen: BodyPen, from: Offset, head: Offset) {
    val s = pen.scale
    val neck = head - from
    val lean = atan2(neck.x, -neck.y) * DEGREES
    val across = BodyBuild.HEAD_ACROSS * s + pen.pad
    val along = BodyBuild.HEAD_ALONG * s + pen.pad
    rotate(lean, head) {
        drawOval(
            pen.color,
            topLeft = Offset(head.x - across, head.y - along),
            size = Size(across * 2f, along * 2f),
            blendMode = pen.blend,
        )
    }
}

/**
 * A contoured leg from [hip] through [knee], [ankle], [heel], and [toe] at [limbScale] of full thickness.
 * The shoe stands on its flat sole to the left of heel→toe, or to the right when [side] is -1.
 */
internal fun DrawScope.drawContouredLeg(
    pen: BodyPen,
    hip: Offset,
    knee: Offset,
    ankle: Offset,
    heel: Offset,
    toe: Offset,
    limbScale: Float = 1f,
    side: Float = 1f,
) {
    val sc = pen.scale * limbScale
    val kneeR = BodyBuild.KNEE * sc
    val ankleR = BodyBuild.ANKLE * sc
    drawFromPartway(pen, hip, knee, BodyBuild.THIGH * sc, kneeR)

    val shin = ankle - knee
    val length = shin.getDistance().coerceAtLeast(MIN_LENGTH)
    val down = shin / length
    val cuff = ankle - down * (BodyBuild.ANKLE_RISE * sc)
    val calf = knee + (cuff - knee) * BodyBuild.CALF_AT + Offset(-down.y, down.x) * (BodyBuild.CALF_BACK * sc)
    drawSegment(pen, knee, calf, kneeR, BodyBuild.CALF * sc)
    drawSegment(pen, calf, cuff, BodyBuild.CALF * sc, ankleR)

    val sole = toe - heel
    val along = sole / sole.getDistance().coerceAtLeast(MIN_LENGTH)
    val up = Offset(along.y, -along.x) * side
    val heelR = BodyBuild.HEEL * sc
    val ballR = BodyBuild.BALL * sc
    val toeR = BodyBuild.TOE * sc
    val heelAt = heel + along * heelR + up * heelR
    val ballAt = heel + sole * BodyBuild.BALL_AT + up * ballR
    val toeAt = toe - along * toeR + up * toeR
    drawSegment(pen, cuff, heelAt, ankleR, heelR)
    drawSegment(pen, cuff, ballAt, ankleR, ballR)
    drawSegment(pen, heelAt, ballAt, heelR, ballR)
    drawSegment(pen, ballAt, toeAt, ballR, toeR)
}

/**
 * A contoured arm from [shoulder] through [elbow], [wrist], and [hand] (or open [fingers] when [openPalm] is true).
 */
internal fun DrawScope.drawContouredArm(
    pen: BodyPen,
    shoulder: Offset,
    elbow: Offset,
    wrist: Offset,
    hand: Offset,
    fingers: Offset,
    openPalm: Boolean,
    limbScale: Float = 1f,
) {
    val sc = pen.scale * limbScale
    val elbowR = BodyBuild.ELBOW * sc
    val forearmR = BodyBuild.FOREARM * sc
    drawFromPartway(pen, shoulder, elbow, BodyBuild.DELTOID * sc, elbowR)
    val belly = elbow + (wrist - elbow) * BodyBuild.FOREARM_AT
    drawSegment(pen, elbow, belly, elbowR, forearmR)
    drawSegment(pen, belly, wrist, forearmR, BodyBuild.WRIST * sc)
    if (!openPalm) {
        drawSegment(pen, wrist, hand, BodyBuild.WRIST * sc, BodyBuild.FIST * sc)
        return
    }
    drawSegment(pen, wrist, hand, BodyBuild.WRIST * sc, BodyBuild.WRIST * sc)
    val knuckles = hand + (fingers - hand) * BodyBuild.KNUCKLES_AT
    drawSegment(pen, hand, knuckles, BodyBuild.WRIST * sc, BodyBuild.KNUCKLES * sc)
    drawSegment(pen, knuckles, fingers, BodyBuild.KNUCKLES * sc, BodyBuild.FINGERS * sc)
}

/**
 * First segment of a limb from [a] to [b]: drawn in full when inking, or cut only from partway along when cutting
 * so no gap rings the shoulder or hip joint the limb grows out of.
 */
private fun DrawScope.drawFromPartway(pen: BodyPen, a: Offset, b: Offset, rA: Float, rB: Float) {
    if (!pen.cutting) {
        drawSegment(pen, a, b, rA, rB)
        return
    }
    val from = a + (b - a) * BodyBuild.GAP_FROM
    val there = rA + (rB - rA) * BodyBuild.GAP_FROM
    drawSegment(pen, from, b, there - pen.pad - BodyBuild.GAP_TUCK * pen.scale, rB)
}

/**
 * Organic silhouette radii and contour stations as shares of standing height (`Rig` Build ratios normalized to 1.0).
 */
@Suppress("detekt.MagicNumber") // Anatomical silhouette radii in standing-height shares.
internal object BodyBuild {
    const val PELVIS = 0.045f
    const val WAIST = 0.040f
    const val WAIST_AT = 0.42f
    const val CHEST = 0.053f
    const val CHEST_AT = 0.74f
    const val CHEST_FORWARD = CHEST - PELVIS
    const val SHOULDERS = 0.039f
    const val NECK = 0.026f
    const val NECK_TOP = 0.0225f

    const val FRONT_HIPS = 0.071f
    const val FRONT_WAIST = 0.058f
    const val FRONT_CHEST = 0.080f
    const val FRONT_COLLAR = 0.045f
    const val HEAD_ACROSS = 0.058f
    const val HEAD_ALONG = 0.068f

    const val BOW = 0.00193f
    const val WAIST_BOW = 1f
    const val CHEST_BOW = 0.7f
    const val BREATH = 0.0035f

    const val THIGH = 0.045f
    const val KNEE = 0.027f
    const val CALF = 0.033f
    const val CALF_AT = 0.3f
    const val CALF_BACK = 0.005f
    const val ANKLE = 0.022f

    const val ANKLE_RISE = 0.024f
    const val HEEL = 0.019f
    const val BALL = 0.017f
    const val BALL_AT = 0.6f
    const val TOE = 0.014f

    const val DELTOID = 0.032f
    const val ELBOW = 0.021f
    const val FOREARM = 0.024f
    const val FOREARM_AT = 0.28f
    const val WRIST = 0.018f
    const val FIST = 0.024f

    const val KNUCKLES = 0.021f
    const val KNUCKLES_AT = 0.45f
    const val FINGERS = 0.011f

    /** Far limbs are slightly slighter in profile for visual depth. */
    const val FAR = 0.92f

    /** Gap cut around foreground parts where they cross background parts. */
    const val GAP = 0.0084f
    const val GAP_FROM = 0.34f
    const val GAP_TUCK = 0.0016f
}

private const val HALF = 0.5f
private const val MIN_LENGTH = 0.001f
private const val MAX_SIN = 0.9999f
private const val DEGREES = (180.0 / PI).toFloat()
