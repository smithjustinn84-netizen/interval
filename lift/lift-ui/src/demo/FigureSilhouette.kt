package dev.justinsmith.lift.ui.demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Draws an anatomically tapered segment from [a] (radius [rA]) to [b] (radius [rB]): two circles joined by their
 * exact external tangents, traced onto the reused [path] so each frame allocates nothing.
 */
internal fun DrawScope.drawTaperedSegment(
    a: Offset,
    b: Offset,
    rA: Float,
    rB: Float,
    color: Color,
    path: Path,
) {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val len = hypot(dx, dy)
    if (len <= abs(rA - rB) + MIN_SEGMENT) {
        if (rA >= rB) drawCircle(color, rA, a) else drawCircle(color, rB, b)
        return
    }
    val ux = dx / len
    val uy = dy / len
    val nx = -uy
    val ny = ux
    val sinA = ((rA - rB) / len).coerceIn(-MAX_SIN, MAX_SIN)
    val cosA = sqrt(1f - sinA * sinA)
    val d1x = ux * sinA + nx * cosA
    val d1y = uy * sinA + ny * cosA
    val d2x = ux * sinA - nx * cosA
    val d2y = uy * sinA - ny * cosA
    path.rewind()
    path.moveTo(a.x + d1x * rA, a.y + d1y * rA)
    path.lineTo(b.x + d1x * rB, b.y + d1y * rB)
    path.lineTo(b.x + d2x * rB, b.y + d2y * rB)
    path.lineTo(a.x + d2x * rA, a.y + d2y * rA)
    path.close()
    drawPath(path, color)
    drawCircle(color, rA, a)
    drawCircle(color, rB, b)
}

/** The tapered torso (waist to chest), connected neck, and athletic head. */
internal fun DrawScope.drawTorsoAndHead(figure: Figure, color: Color, path: Path) {
    drawTaperedSegment(figure.hip, figure.shoulder, Silhouette.HIP, Silhouette.SHOULDER, color, path)
    drawTaperedSegment(figure.shoulder, figure.head, Silhouette.NECK_BASE, Silhouette.NECK_TOP, color, path)
    drawCircle(color, Silhouette.HEAD, figure.head)
}

/**
 * A tapered leg (thigh, shin, and sculpted foot) at [scale]: full size for the near leg, slightly slimmer for
 * the far leg to read depth.
 */
internal fun DrawScope.drawTaperedLeg(
    hip: Offset,
    knee: Offset,
    ankle: Offset,
    heel: Offset,
    toe: Offset,
    color: Color,
    path: Path,
    scale: Float = 1f,
) {
    val thighTop = Silhouette.THIGH_TOP * scale
    val kneeR = Silhouette.KNEE * scale
    val ankleR = Silhouette.ANKLE * scale
    val heelR = Silhouette.FOOT_HEEL * scale
    val toeR = Silhouette.FOOT_TOE * scale
    drawTaperedSegment(hip, knee, thighTop, kneeR, color, path)
    drawTaperedSegment(knee, ankle, kneeR, ankleR, color, path)
    val midFoot = Offset((heel.x + toe.x) * HALF, (heel.y + toe.y) * HALF)
    drawTaperedSegment(ankle, heel, ankleR, heelR, color, path)
    drawTaperedSegment(ankle, midFoot, ankleR, heelR, color, path)
    drawTaperedSegment(heel, toe, heelR, toeR, color, path)
}

/**
 * A tapered arm (upper arm, forearm, and fist) at [scale]. When [separation] is given, a thin backing silhouette
 * along the forearm and elbow keeps a near arm distinct where it crosses in front of the torso.
 */
internal fun DrawScope.drawTaperedArm(
    shoulder: Offset,
    elbow: Offset,
    hand: Offset,
    color: Color,
    path: Path,
    scale: Float = 1f,
    separation: Color? = null,
) {
    val upperTop = Silhouette.UPPER_ARM_TOP * scale
    val elbowR = Silhouette.ELBOW * scale
    val wristR = Silhouette.WRIST * scale
    val handR = Silhouette.HAND * scale
    if (separation != null) {
        val pad = Silhouette.SEPARATION_PAD
        val midUpper = Offset((shoulder.x + elbow.x) * HALF, (shoulder.y + elbow.y) * HALF)
        drawTaperedSegment(midUpper, elbow, elbowR + pad * HALF, elbowR + pad, separation, path)
        drawTaperedSegment(elbow, hand, elbowR + pad, wristR + pad, separation, path)
        drawCircle(separation, handR + pad, hand)
    }
    drawTaperedSegment(shoulder, elbow, upperTop, elbowR, color, path)
    drawTaperedSegment(elbow, hand, elbowR, wristR, color, path)
    drawCircle(color, handR, hand)
}

/** Tapered body radii in [Rig] units, keeping every joint's outer envelope within [Rig]'s bounds. */
internal object Silhouette {
    const val HIP = 8.5f
    const val SHOULDER = 10.5f
    const val NECK_BASE = 6.2f
    const val NECK_TOP = 5.0f
    const val HEAD = 15.5f

    const val THIGH_TOP = 9.0f
    const val KNEE = 6.6f
    const val ANKLE = 4.6f
    const val FOOT_HEEL = 4.2f
    const val FOOT_TOE = 3.4f

    const val UPPER_ARM_TOP = 6.6f
    const val ELBOW = 5.0f
    const val WRIST = 3.8f
    const val HAND = 6.4f

    /** Far limbs are slightly slimmer for subtle depth perspective. */
    const val FAR_SCALE = 0.92f

    /** Thin contour behind the near forearm so it separates cleanly when crossing the torso. */
    const val SEPARATION_PAD = 2.2f
    const val SEPARATION_ALPHA = 0.65f
}

private const val HALF = 0.5f
private const val MIN_SEGMENT = 0.001f
private const val MAX_SIN = 0.9999f
