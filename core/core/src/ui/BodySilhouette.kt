package dev.justinsmith.interval.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.hypot

// Sculpted anatomical silhouette rendering on BodyFrame coordinates (mapped into Y-down canvas pixels by DemoStage).
// Reuses BodyPen's scratch paths, offscreen layer Paint, studio tone holder, and trail buffer per canvas.

/**
 * Reusable drawing state for one [ExerciseDemoCanvas]: scratch paths, offscreen body layer paint, trail buffer,
 * derived studio lighting [tone], current breath/pulse/scale, detail toggle, and ink-vs-cutaway/ghost modes.
 *
 * A ghost's body is drawn in two passes over the same parts, leaving one hairline outline round all of it: [rim],
 * every part a stroke fatter in the one ink, then [hollow], every part at its own size cleared out. While either is
 * on ([outlining]) [ink] keeps the pass's ink and [gapped] cuts no gap; [solid] ends it.
 */
internal class BodyPen {
    val path = Path()
    val rimPath = Path()
    val layer = Paint()
    val tone = BodyTone()
    val trail = FloatArray(2 * TRAIL_STEPS)
    var trailCount = 0

    var scale = 1f
    var breath = 0f
    var pulse = 0f
    var detail = true

    var color = Color.Transparent
        private set
    var pad = 0f
        private set
    var blend = BlendMode.SrcOver
        private set
    var isFar = false
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

    /** Whether shading/rims/occlusions are bypassed (cutaway pass, ghost outline pass, or Thumb mode). */
    val flat: Boolean get() = cutting || outlining || !detail

    fun ink(color: Color, far: Boolean = false) {
        cutting = false
        isFar = far
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

    fun cut(gapScale: Float = 1f) {
        cutting = true
        color = Color.Black
        pad = BodyBuild.GAP * gapScale * scale
        blend = BlendMode.Clear
        isFar = false
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
 * Draws a foreground part in [color] over whatever is behind it in the body layer, first clearing a refined
 * hairline gap round its outline so overlapping near parts read cleanly on any background. In an outline's pass
 * ([BodyPen.outlining]) the part is drawn once with no gap so parts run together.
 */
internal inline fun gapped(pen: BodyPen, color: Color, gapScale: Float = 1f, part: () -> Unit) {
    if (!pen.outlining) {
        pen.cut(gapScale)
        part()
    }
    pen.ink(color)
    part()
}

/**
 * Sculpted trunk and head in profile: continuous asymmetric cubic-Bézier torso (athletic gluteal curve, concave
 * lumbar curve modulated by [spineBow], thoracic/scapular upper back, pectoral/ribcage expanding with breathing,
 * and lean abdomen) seamlessly joined to a profiled neck, jawline, and cranium.
 */
internal fun DrawScope.drawSideTrunk(pen: BodyPen, hip: Offset, shoulder: Offset, head: Offset, spineBow: Float) {
    val s = pen.scale
    val spine = shoulder - hip
    val len = spine.getDistance().coerceAtLeast(MIN_LENGTH)
    val u = spine / len
    val f = Offset(-u.y, u.x)
    val bow = spineBow * BodyBuild.BOW * s
    val breath = pen.breath * BodyBuild.BREATH * s

    traceSideTorso(pen, hip, u, f, len, bow, breath)
    fillSculptedLimb(pen, pen.path, hip, shoulder, BodyBuild.PECTORAL * s)
    drawSideTrunkRim(pen, hip, u, f, len, bow, breath)
    drawProfileHead(pen, shoulder, head)
}

@Suppress("detekt.MagicNumber", "detekt.LongParameterList") // Profile torso posterior & anterior Bézier stations.
private fun traceSideTorso(
    pen: BodyPen,
    hip: Offset,
    u: Offset,
    f: Offset,
    len: Float,
    bow: Float,
    breath: Float,
) {
    val s = pen.scale
    val p = pen.pad
    fun pt(along: Float, fwdPx: Float): Offset = hip + u * (len * along) + f * fwdPx
    fun Path.cubic(c1: Offset, c2: Offset, to: Offset) = cubicTo(c1.x, c1.y, c2.x, c2.y, to.x, to.y)

    val pelvisBot = pt(-0.14f, -0.006f * s)
    val gluteBack = pt(0.03f, -(BodyBuild.GLUTE * s + p))
    val lumbar = pt(BodyBuild.WAIST_AT, -(BodyBuild.LUMBAR * s + bow * BodyBuild.WAIST_BOW + p))
    val upperBack = pt(BodyBuild.CHEST_AT, -(BodyBuild.SCAPULA * s + bow * BodyBuild.CHEST_BOW + p))
    val rearShoulder = pt(0.97f, -(BodyBuild.REAR_SHOULDER * s + p))
    val shoulderTop = pt(1.06f, 0.004f * s)
    val frontShoulder = pt(0.95f, BodyBuild.FRONT_SHOULDER * s + p)
    val chest = pt(BodyBuild.CHEST_AT, BodyBuild.PECTORAL * s + breath - bow * BodyBuild.CHEST_BOW + p)
    val abdomen = pt(BodyBuild.WAIST_AT, BodyBuild.ABDOMEN * s - bow * BodyBuild.WAIST_BOW + p)
    val frontPelvis = pt(0.06f, BodyBuild.FRONT_PELVIS * s + p)

    val path = pen.path
    path.rewind()
    path.moveTo(pelvisBot.x, pelvisBot.y)
    path.cubic(pt(-0.12f, -(0.044f * s + p)), pt(-0.04f, -(BodyBuild.GLUTE * s + p)), gluteBack)
    path.cubic(
        pt(0.16f, -(BodyBuild.GLUTE * s * 0.90f + p)),
        pt(0.26f, -(BodyBuild.LUMBAR * s + bow * 0.9f + p)),
        lumbar,
    )
    path.cubic(
        pt(0.52f, -(BodyBuild.LUMBAR * s + bow * 0.85f + p)),
        pt(0.62f, -(BodyBuild.SCAPULA * s + bow * 0.72f + p)),
        upperBack,
    )
    path.cubic(
        pt(0.86f, -(BodyBuild.SCAPULA * s * 0.96f + bow * 0.4f + p)),
        pt(0.93f, -(BodyBuild.REAR_SHOULDER * s * 1.10f + p)),
        rearShoulder,
    )
    path.cubic(pt(1.04f, -(0.028f * s + p)), pt(1.07f, -0.008f * s), shoulderTop)
    path.cubic(pt(1.05f, 0.028f * s + p), pt(1.00f, BodyBuild.FRONT_SHOULDER * s + p), frontShoulder)
    path.cubic(
        pt(0.88f, BodyBuild.PECTORAL * s * 0.94f + breath - bow * 0.4f + p),
        pt(0.82f, BodyBuild.PECTORAL * s * 1.03f + breath - bow * 0.6f + p),
        chest,
    )
    path.cubic(
        pt(0.60f, BodyBuild.PECTORAL * s * 0.88f + breath * 0.6f - bow * 0.78f + p),
        pt(0.52f, BodyBuild.ABDOMEN * s * 1.02f - bow * 0.95f + p),
        abdomen,
    )
    path.cubic(
        pt(0.26f, BodyBuild.ABDOMEN * s * 1.03f - bow * 0.7f + p),
        pt(0.16f, BodyBuild.FRONT_PELVIS * s + p),
        frontPelvis,
    )
    path.cubic(pt(-0.04f, BodyBuild.FRONT_PELVIS * s * 0.92f + p), pt(-0.14f, 0.026f * s + p), pelvisBot)
    path.close()
}

@Suppress("detekt.MagicNumber", "detekt.LongParameterList") // Key-lit anterior chest & abdomen specular rim.
private fun DrawScope.drawSideTrunkRim(
    pen: BodyPen,
    hip: Offset,
    u: Offset,
    f: Offset,
    len: Float,
    bow: Float,
    breath: Float,
) {
    if (pen.flat || pen.isFar) return
    val s = pen.scale
    val inset = BodyBuild.RIM_INSET * s
    fun pt(along: Float, fwdPx: Float): Offset = hip + u * (len * along) + f * fwdPx
    val rim = pen.rimPath
    rim.rewind()
    val rTop = pt(0.92f, BodyBuild.FRONT_SHOULDER * s - inset)
    val rChest = pt(BodyBuild.CHEST_AT, BodyBuild.PECTORAL * s + breath - bow * BodyBuild.CHEST_BOW - inset)
    val rAbs = pt(BodyBuild.WAIST_AT, BodyBuild.ABDOMEN * s - bow * BodyBuild.WAIST_BOW - inset)
    val c1 = pt(0.84f, BodyBuild.PECTORAL * s + breath - bow * 0.55f - inset)
    val c2 = pt(0.78f, BodyBuild.PECTORAL * s + breath - bow * 0.65f - inset)
    val c3 = pt(0.58f, BodyBuild.PECTORAL * s * 0.85f + breath * 0.5f - bow * 0.8f - inset)
    val c4 = pt(0.50f, BodyBuild.ABDOMEN * s - bow * 0.95f - inset)
    rim.moveTo(rTop.x, rTop.y)
    rim.cubicTo(c1.x, c1.y, c2.x, c2.y, rChest.x, rChest.y)
    rim.cubicTo(c3.x, c3.y, c4.x, c4.y, rAbs.x, rAbs.y)
    drawStudioRim(pen, rim, BodyBuild.RIM_STROKE * s)
}

/**
 * Sculpted human head and neck in profile: trapezius-to-nape curve, rounded cranium/occiput, subtle editorial
 * brow/nasal/chin profile indicating facing direction, defined jawline, throat, and soft under-jaw depth shading.
 */
@Suppress("detekt.MagicNumber") // Profile cranium, jaw, and neck Bézier landmarks in standing-height shares.
internal fun DrawScope.drawProfileHead(pen: BodyPen, shoulder: Offset, head: Offset) {
    val s = pen.scale
    val neck = head - shoulder
    val len = neck.getDistance().coerceAtLeast(MIN_LENGTH)
    val u = neck / len
    val f = Offset(-u.y, u.x)

    traceProfileHead(pen, shoulder, head, u, f, len)
    fillSculptedForm(pen, pen.path, head, BodyBuild.HEAD_ALONG * s)

    if (!pen.flat && !pen.isFar) {
        fun hp(alongS: Float, fwdS: Float): Offset = head + u * (alongS * s) + f * (fwdS * s)
        drawBodyOcclusion(pen, hp(-0.062f, 0.008f), 0.020f * s, strength = 0.52f)
        val inset = BodyBuild.RIM_INSET * s
        val rim = pen.rimPath
        rim.rewind()
        val rCrown = hp(0.066f, 0.002f) - u * inset
        val c1 = hp(0.064f, 0.034f) - f * inset
        val c2 = hp(0.052f, 0.047f) - f * inset
        val rForehead = hp(0.038f, 0.046f) - f * inset
        val rBrow = hp(0.008f, 0.050f) - f * inset
        rim.moveTo(rCrown.x, rCrown.y)
        rim.cubicTo(c1.x, c1.y, c2.x, c2.y, rForehead.x, rForehead.y)
        rim.lineTo(rBrow.x, rBrow.y)
        drawStudioRim(pen, rim, BodyBuild.RIM_STROKE * s)
    }
}

@Suppress("detekt.MagicNumber", "detekt.LongParameterList") // Profile skull, face, jaw & neck contour stations.
private fun traceProfileHead(
    pen: BodyPen,
    shoulder: Offset,
    head: Offset,
    u: Offset,
    f: Offset,
    len: Float,
) {
    val s = pen.scale
    val p = pen.pad
    fun hp(alongS: Float, fwdS: Float): Offset = head + u * (alongS * s) + f * (fwdS * s)
    fun Path.cubic(c1: Offset, c2: Offset, to: Offset) = cubicTo(c1.x, c1.y, c2.x, c2.y, to.x, to.y)

    val trapBack = shoulder - f * (0.038f * s + p) - u * (0.016f * s)
    val nape = hp(-0.056f, -0.025f) - f * p
    val occiput = hp(-0.008f, -0.055f) - f * p
    val crown = hp(0.069f, -0.004f) + u * p
    val forehead = hp(0.038f, 0.048f) + f * p
    val brow = hp(0.006f, 0.053f) + f * p
    val noseTip = hp(-0.014f, 0.0555f) + f * p
    val chin = hp(-0.045f, 0.046f) + f * p
    val throat = hp(-0.063f, 0.021f) + f * p
    val clavicle = shoulder + f * (0.035f * s + p) - u * (0.014f * s)

    val path = pen.path
    path.rewind()
    path.moveTo(trapBack.x, trapBack.y)
    path.cubic(shoulder - f * (0.031f * s + p) + u * (len * 0.24f), hp(-0.072f, -0.025f) - f * p, nape)
    path.cubic(hp(-0.042f, -0.029f) - f * p, hp(-0.032f, -0.054f) - f * p, occiput)
    path.cubic(hp(0.032f, -0.058f) - f * p, hp(0.069f, -0.042f) + u * p, crown)
    path.cubic(hp(0.069f, 0.034f) + u * p, hp(0.054f, 0.049f) + f * p, forehead)
    path.cubic(hp(0.026f, 0.051f) + f * p, hp(0.014f, 0.053f) + f * p, brow)
    path.cubic(hp(-0.002f, 0.055f) + f * p, hp(-0.009f, 0.0565f) + f * p, noseTip)
    path.cubic(hp(-0.023f, 0.050f) + f * p, hp(-0.036f, 0.050f) + f * p, chin)
    path.cubic(hp(-0.054f, 0.039f) + f * p, hp(-0.058f, 0.028f) + f * p, throat)
    path.cubic(hp(-0.078f, 0.022f) + f * p, shoulder + f * (0.027f * s + p) + u * (len * 0.22f), clavicle)
    path.close()
}

/**
 * Sculpted front/top-down head and neck: rounded cranium tapering smoothly along cheekbones to a clean jaw/chin,
 * anchored onto a contoured neck.
 */
@Suppress("detekt.MagicNumber") // Front-view neck proportions in standing-height shares.
internal fun DrawScope.drawFrontHead(pen: BodyPen, neckBase: Offset, head: Offset) {
    val s = pen.scale
    val p = pen.pad
    val axis = head - neckBase
    val len = axis.getDistance().coerceAtLeast(MIN_LENGTH)
    val u = axis / len
    val r = Offset(-u.y, u.x)

    val path = pen.path
    path.rewind()
    val neckR = BodyBuild.NECK * s + p
    val neckTopR = BodyBuild.NECK_TOP * s + p
    val baseL = neckBase - r * neckR - u * (0.012f * s)
    val baseR = neckBase + r * neckR - u * (0.012f * s)
    val topL = head - u * (0.042f * s) - r * neckTopR
    val topR = head - u * (0.042f * s) + r * neckTopR
    path.moveTo(baseL.x, baseL.y)
    path.lineTo(topL.x, topL.y)
    path.lineTo(topR.x, topR.y)
    path.lineTo(baseR.x, baseR.y)
    path.close()
    fillSculptedLimb(pen, path, neckBase, head, BodyBuild.NECK * s)

    if (!pen.flat && !pen.isFar) {
        drawBodyOcclusion(pen, head - u * (0.056f * s), 0.024f * s, strength = 0.48f)
    }
    traceFrontCranium(pen, head, u, r)
    fillSculptedForm(pen, pen.path, head, BodyBuild.HEAD_ALONG * s + p)
}

@Suppress("detekt.MagicNumber") // Front-view cranium & jawline Bézier stations.
private fun traceFrontCranium(pen: BodyPen, head: Offset, u: Offset, r: Offset) {
    val s = pen.scale
    val p = pen.pad
    val w = BodyBuild.HEAD_ACROSS * s + p
    val h = BodyBuild.HEAD_ALONG * s + p
    fun fp(alongS: Float, sidePx: Float): Offset = head + u * (alongS * s) + r * sidePx
    fun Path.cubic(c1: Offset, c2: Offset, to: Offset) = cubicTo(c1.x, c1.y, c2.x, c2.y, to.x, to.y)

    val crown = head + u * h
    val templeR = fp(0.022f, w)
    val templeL = fp(0.022f, -w)
    val jawR = fp(-0.028f, w * 0.78f)
    val jawL = fp(-0.028f, -w * 0.78f)
    val chin = head - u * (h * 0.94f)

    val path = pen.path
    path.rewind()
    path.moveTo(crown.x, crown.y)
    path.cubic(head + u * h + r * (w * 0.62f), fp(0.050f, w), templeR)
    path.cubic(fp(-0.002f, w * 0.98f), fp(-0.016f, w * 0.90f), jawR)
    path.cubic(fp(-0.048f, w * 0.56f), chin + r * (w * 0.30f), chin)
    path.cubic(chin - r * (w * 0.30f), fp(-0.048f, -w * 0.56f), jawL)
    path.cubic(fp(-0.016f, -w * 0.90f), fp(-0.002f, -w * 0.98f), templeL)
    path.cubic(fp(0.050f, -w), head + u * h - r * (w * 0.62f), crown)
    path.close()
}

/**
 * Anatomical silhouette dimensions and contour stations as shares of standing height (normalized to 1.0).
 */
@Suppress("detekt.MagicNumber") // Anatomical silhouette radii in standing-height shares.
internal object BodyBuild {
    const val PELVIS = 0.045f
    const val GLUTE = 0.056f
    const val FRONT_PELVIS = 0.043f
    const val LUMBAR = 0.039f
    const val ABDOMEN = 0.041f
    const val WAIST_AT = 0.39f
    const val SCAPULA = 0.054f
    const val PECTORAL = 0.062f
    const val CHEST_AT = 0.74f
    const val REAR_SHOULDER = 0.042f
    const val FRONT_SHOULDER = 0.043f
    const val NECK = 0.028f
    const val NECK_TOP = 0.0235f

    const val FRONT_HIPS = 0.070f
    const val FRONT_WAIST = 0.057f
    const val FRONT_CHEST = 0.084f
    const val HEAD_ACROSS = 0.055f
    const val HEAD_ALONG = 0.068f

    const val BOW = 0.00193f
    const val WAIST_BOW = 1f
    const val CHEST_BOW = 0.68f
    const val BREATH = 0.0038f

    const val THIGH_FRONT = 0.048f
    const val THIGH_BACK = 0.049f
    const val THIGH_HIP_BACK = 0.053f
    const val THIGH_HIP_FRONT = 0.043f
    const val KNEE_FRONT = 0.029f
    const val KNEE_BACK = 0.025f
    const val SHIN_MID = 0.022f
    const val CALF_BACK = 0.038f
    const val CALF_AT = 0.28f
    const val ACHILLES_BACK = 0.016f
    const val ANKLE_FRONT = 0.019f

    const val ANKLE_RISE = 0.024f
    const val SHOE_DROP = 0.014f
    const val SHOE_INSTEP = 0.031f
    const val SHOE_HEEL_EXT = 0.013f
    const val SHOE_TOE_EXT = 0.009f
    const val SHOE_TOE_HEIGHT = 0.015f

    const val DELTOID = 0.034f
    const val BICEP = 0.029f
    const val TRICEP = 0.026f
    const val ELBOW = 0.021f
    const val FOREARM_FRONT = 0.026f
    const val FOREARM_BACK = 0.023f
    const val FOREARM_AT = 0.26f
    const val WRIST = 0.0155f
    const val FIST = 0.0235f

    const val KNUCKLES = 0.020f
    const val KNUCKLES_AT = 0.46f
    const val FINGERS = 0.0095f

    /** Far limbs are slightly slighter in profile for visual depth. */
    const val FAR = 0.92f

    /** Refined hairline cutaway gap around overlapping foreground parts (near arm, kettlebell). */
    const val GAP = 0.0038f
    const val GAP_FROM = 0.36f
    const val GAP_TUCK = 0.0012f

    /** Studio specular rim highlight inset and width in standing-height shares. */
    const val RIM_INSET = 0.0045f
    const val RIM_STROKE = 0.0045f
}

private const val MIN_LENGTH = 0.001f

/** Utility norm distance that avoids zero-division on collapsed foreshortened bones. */
internal fun safeDistance(dx: Float, dy: Float): Float = hypot(dx, dy).coerceAtLeast(MIN_LENGTH)
