package dev.justinsmith.interval.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.abs

// Sculpted asymmetric cubic-Bézier limbs, athletic trainer shoes, and hands (clenched fist / open palm).
// All paths reuse BodyPen.path / BodyPen.rimPath with zero per-frame collection allocations.

/**
 * Contoured leg from [hip] through [knee], [ankle], [heel], and [toe] at [limbScale] of full thickness:
 * sculpted quadriceps & hamstring thigh, rounded patella knee, crisp anterior tibia & high gastrocnemius calf
 * tapering into an Achilles pinch, and a sleek single-path athletic shoe.
 */
@Suppress("detekt.MagicNumber") // Anatomical thigh, shin, and calf contour stations in standing-height shares.
internal fun DrawScope.drawContouredLeg(
    pen: BodyPen,
    hip: Offset,
    knee: Offset,
    ankle: Offset,
    heel: Offset,
    toe: Offset,
    limbScale: Float = 1f,
    side: Float = 1f,
    cutFromPartway: Boolean = false,
) {
    val sc = pen.scale * limbScale

    // Thigh: hip -> knee (quadriceps sweep on front, glute/hamstring curve on back, patella cap at knee).
    drawAnatomicalTube(
        pen = pen,
        a = hip,
        b = knee,
        side = side,
        rFrontA = BodyBuild.THIGH_HIP_FRONT * sc,
        rFrontMid = BodyBuild.THIGH_FRONT * sc,
        rFrontB = BodyBuild.KNEE_FRONT * sc,
        rBackA = BodyBuild.THIGH_HIP_BACK * sc,
        rBackMid = BodyBuild.THIGH_BACK * sc,
        rBackB = BodyBuild.KNEE_BACK * sc,
        frontMidAt = 0.38f,
        backMidAt = 0.40f,
        cutFromPartway = cutFromPartway,
    )

    // Lower leg: knee -> ankle cuff (taut anterior tibia shin, high gastrocnemius calf -> slender Achilles).
    val shin = ankle - knee
    val shinLen = safeDistance(shin.x, shin.y)
    val shinDown = shin / shinLen
    val cuff = ankle - shinDown * (BodyBuild.ANKLE_RISE * sc)
    drawAnatomicalTube(
        pen = pen,
        a = knee,
        b = cuff,
        side = side,
        rFrontA = BodyBuild.KNEE_FRONT * sc,
        rFrontMid = BodyBuild.SHIN_MID * sc,
        rFrontB = BodyBuild.ANKLE_FRONT * sc,
        rBackA = BodyBuild.KNEE_BACK * sc,
        rBackMid = BodyBuild.CALF_BACK * sc,
        rBackB = BodyBuild.ACHILLES_BACK * sc,
        frontMidAt = 0.45f,
        backMidAt = BodyBuild.CALF_AT,
        backPinchAt = 0.76f,
        backPinch = BodyBuild.ACHILLES_BACK * 1.12f * sc,
        cutFromPartway = false,
    )

    // Sleek athletic shoe planted cleanly on the floor.
    drawAthleticShoe(pen, cuff, heel, toe, sc, side)
}

/**
 * Contoured arm from [shoulder] through [elbow], [wrist], and [hand] (or open [fingers] when [openPalm] is true):
 * rounded deltoid cap, sculpted bicep/tricep upper arm, brachioradialis-tapered forearm to crisp slender wrist,
 * and a contoured gripping fist or graceful open palm.
 */
@Suppress("detekt.MagicNumber") // Anatomical arm, forearm, and hand contour stations in standing-height shares.
internal fun DrawScope.drawContouredArm(
    pen: BodyPen,
    shoulder: Offset,
    elbow: Offset,
    wrist: Offset,
    hand: Offset,
    fingers: Offset,
    openPalm: Boolean,
    limbScale: Float = 1f,
    side: Float = 1f,
) {
    val sc = pen.scale * limbScale

    // Upper arm: shoulder deltoid cap -> bicep/tricep -> elbow.
    drawAnatomicalTube(
        pen = pen,
        a = shoulder,
        b = elbow,
        side = side,
        rFrontA = BodyBuild.DELTOID * sc,
        rFrontMid = BodyBuild.BICEP * sc,
        rFrontB = BodyBuild.ELBOW * sc,
        rBackA = BodyBuild.DELTOID * 0.96f * sc,
        rBackMid = BodyBuild.TRICEP * sc,
        rBackB = BodyBuild.ELBOW * sc,
        frontMidAt = 0.48f,
        backMidAt = 0.36f,
        cutFromPartway = true,
    )

    // Forearm: elbow -> fuller brachioradialis belly -> sleek wrist.
    drawAnatomicalTube(
        pen = pen,
        a = elbow,
        b = wrist,
        side = side,
        rFrontA = BodyBuild.ELBOW * sc,
        rFrontMid = BodyBuild.FOREARM_FRONT * sc,
        rFrontB = BodyBuild.WRIST * sc,
        rBackA = BodyBuild.ELBOW * 0.96f * sc,
        rBackMid = BodyBuild.FOREARM_BACK * sc,
        rBackB = BodyBuild.WRIST * sc,
        frontMidAt = BodyBuild.FOREARM_AT,
        backMidAt = 0.28f,
        backPinchAt = 0.74f,
        backPinch = BodyBuild.WRIST * 1.10f * sc,
        cutFromPartway = false,
    )

    if (!openPalm) {
        drawGrippingFist(pen, wrist, hand, sc, side)
    } else {
        drawOpenHand(pen, wrist, hand, fingers, sc, side)
    }
}

/**
 * Draws one continuous asymmetric cubic-Bézier limb segment from [a] to [b], with independent anterior ([rFrontA],
 * [rFrontMid], [rFrontB]) and posterior ([rBackA], [rBackMid], [rBackB]) contours, smooth hemispherical joint caps,
 * optional posterior Achilles/wrist pinch, and studio cylindrical shading.
 */
@Suppress("detekt.MagicNumber", "detekt.LongParameterList") // Parametric anatomical segment contour builder.
internal fun DrawScope.drawAnatomicalTube(
    pen: BodyPen,
    a: Offset,
    b: Offset,
    side: Float,
    rFrontA: Float,
    rFrontMid: Float,
    rFrontB: Float,
    rBackA: Float,
    rBackMid: Float,
    rBackB: Float,
    frontMidAt: Float,
    backMidAt: Float,
    backPinchAt: Float = 0f,
    backPinch: Float = 0f,
    cutFromPartway: Boolean = false,
) {
    val p = pen.pad
    val startAt = if (pen.cutting && cutFromPartway) a + (b - a) * BodyBuild.GAP_FROM else a
    val dx = b.x - startAt.x
    val dy = b.y - startAt.y
    val len = safeDistance(dx, dy)
    val maxR = maxOf(rFrontA, rBackA, rFrontB, rBackB)

    // Foreshortened bone collapsed near a single point (e.g. front-view thigh pointed at the eye).
    if (len <= abs(rFrontA - rFrontB) + MIN_SEGMENT) {
        val radius = maxR + p
        drawCircle(pen.color, radius, b, blendMode = pen.blend)
        return
    }

    val u = Offset(dx / len, dy / len)
    // Anterior perpendicular in Y-down screen space: standing downward bone (0, 1) -> front (+1, 0).
    val f = Offset(u.y, -u.x) * side

    val startFactor = if (pen.cutting && cutFromPartway) BodyBuild.GAP_FROM else 0f
    val tuck = if (pen.cutting && cutFromPartway) BodyBuild.GAP_TUCK * pen.scale else 0f
    val rfA = (rFrontA + (rFrontB - rFrontA) * startFactor) + p - tuck
    val rbA = (rBackA + (rBackB - rBackA) * startFactor) + p - tuck
    val rfM = rFrontMid + p
    val rbM = rBackMid + p
    val rfB = rFrontB + p
    val rbB = rBackB + p
    val capA = (rfA + rbA) * HALF * CAP_DEPTH
    val capB = (rfB + rbB) * HALF * CAP_DEPTH

    fun pt(along: Float, fwdPx: Float): Offset = startAt + u * (len * along) + f * fwdPx
    fun Path.cubic(c1: Offset, c2: Offset, to: Offset) = cubicTo(c1.x, c1.y, c2.x, c2.y, to.x, to.y)

    val aFront = pt(0f, rfA)
    val bFront = pt(1f, rfB)
    val bTip = b + u * capB + f * ((rfB - rbB) * HALF)
    val bBack = pt(1f, -rbB)
    val aBack = pt(0f, -rbA)
    val aTip = startAt - u * capA + f * ((rfA - rbA) * HALF)

    val path = pen.path
    path.rewind()
    path.moveTo(aFront.x, aFront.y)
    val fMid = pt(frontMidAt, rfM)
    path.cubic(pt(frontMidAt * 0.45f, rfA * 0.45f + rfM * 0.55f), pt(frontMidAt * 0.85f, rfM), fMid)
    path.cubic(pt(frontMidAt + (1f - frontMidAt) * 0.35f, rfM * 0.85f + rfB * 0.15f), pt(0.84f, rfB * 1.04f), bFront)
    path.cubic(bFront + u * (capB * 0.88f), bTip + f * (rfB * 0.55f), bTip)
    path.cubic(bTip - f * (rbB * 0.55f), bBack + u * (capB * 0.88f), bBack)

    val bMid = pt(backMidAt, -rbM)
    if (backPinchAt > backMidAt && backPinch > 0f) {
        val pinchR = backPinch + p
        val pinchPt = pt(backPinchAt, -pinchR)
        path.cubic(pt(0.90f, -(rbB * 0.6f + pinchR * 0.4f)), pt(backPinchAt + 0.08f, -pinchR), pinchPt)
        path.cubic(
            pt(backPinchAt - (backPinchAt - backMidAt) * 0.35f, -(pinchR * 0.55f + rbM * 0.45f)),
            pt(backMidAt + (backPinchAt - backMidAt) * 0.32f, -rbM * 1.02f),
            bMid,
        )
    } else {
        path.cubic(
            pt(0.84f, -rbB * 1.04f),
            pt(backMidAt + (1f - backMidAt) * 0.38f, -(rbM * 0.86f + rbB * 0.14f)),
            bMid,
        )
    }
    path.cubic(pt(backMidAt * 0.72f, -rbM), pt(backMidAt * 0.32f, -(rbA * 0.60f + rbM * 0.40f)), aBack)
    path.cubic(aBack - u * (capA * 0.88f), aTip - f * (rbA * 0.55f), aTip)
    path.cubic(aTip + f * (rfA * 0.55f), aFront - u * (capA * 0.88f), aFront)
    path.close()
    fillSculptedLimb(pen, path, startAt, b, maxOf(rFrontMid, rBackMid, maxR))
}

/**
 * Sleek single-path athletic trainer shoe connecting [cuff] cleanly onto the [heel]..[toe] sole plane, with
 * rounded heel counter, flat grounded outsole, contoured instep, tapered toe box, and a subtle midsole highlight.
 */
@Suppress("detekt.MagicNumber") // Trainer shoe Bézier silhouette proportions in standing-height shares.
private fun DrawScope.drawAthleticShoe(
    pen: BodyPen,
    cuff: Offset,
    heel: Offset,
    toe: Offset,
    sc: Float,
    side: Float,
) {
    val p = pen.pad
    val sole = toe - heel
    val soleLen = safeDistance(sole.x, sole.y)
    val along = sole / soleLen
    val up = Offset(along.y, -along.x) * side

    val soleDrop = BodyBuild.SHOE_DROP * sc + p
    val heelBack = heel - along * (BodyBuild.SHOE_HEEL_EXT * sc + p) - up * soleDrop
    val toeBase = toe + along * (BodyBuild.SHOE_TOE_EXT * sc + p) - up * soleDrop
    val collarBack = cuff - along * (BodyBuild.ACHILLES_BACK * sc + p)
    val collarFront = cuff + along * (BodyBuild.ANKLE_FRONT * sc + p)
    val heelTop = heel - along * (BodyBuild.SHOE_HEEL_EXT * 1.1f * sc + p) + up * (0.012f * sc)
    val toeTip = toeBase + up * (BodyBuild.SHOE_TOE_HEIGHT * sc + p * 0.5f)
    val vamp = heel + sole * 0.50f + up * (BodyBuild.SHOE_INSTEP * sc + p)

    val path = pen.path
    path.rewind()
    path.moveTo(collarBack.x, collarBack.y)
    // Heel counter curving cleanly down to flat outsole
    path.cubicTo(
        (collarBack - up * (0.010f * sc) - along * (0.005f * sc)).x,
        (collarBack - up * (0.010f * sc) - along * (0.005f * sc)).y,
        (heelTop + up * (0.006f * sc)).x,
        (heelTop + up * (0.006f * sc)).y,
        heelTop.x,
        heelTop.y,
    )
    path.cubicTo(
        (heelTop - up * (0.012f * sc)).x,
        (heelTop - up * (0.012f * sc)).y,
        (heelBack + up * (0.004f * sc)).x,
        (heelBack + up * (0.004f * sc)).y,
        (heelBack + along * (0.012f * sc)).x,
        (heelBack + along * (0.012f * sc)).y,
    )
    // Flat grounded outsole -> subtle athletic toe spring -> rounded toe box
    val ball = toeBase - along * (0.022f * sc)
    path.lineTo(ball.x, ball.y)
    path.cubicTo(
        (toeBase - along * (0.004f * sc)).x,
        (toeBase - along * (0.004f * sc)).y,
        (toeBase + along * (0.004f * sc) + up * (0.006f * sc)).x,
        (toeBase + along * (0.004f * sc) + up * (0.006f * sc)).y,
        toeTip.x,
        toeTip.y,
    )
    // Tapered toe box -> sloped vamp / laces -> front ankle collar
    path.cubicTo(
        (toeTip - along * (0.016f * sc) + up * (0.014f * sc)).x,
        (toeTip - along * (0.016f * sc) + up * (0.014f * sc)).y,
        (vamp + along * (0.022f * sc) - up * (0.004f * sc)).x,
        (vamp + along * (0.022f * sc) - up * (0.004f * sc)).y,
        vamp.x,
        vamp.y,
    )
    path.cubicTo(
        (vamp - along * (0.016f * sc) + up * (0.006f * sc)).x,
        (vamp - along * (0.016f * sc) + up * (0.006f * sc)).y,
        (collarFront - up * (0.010f * sc)).x,
        (collarFront - up * (0.010f * sc)).y,
        collarFront.x,
        collarFront.y,
    )
    path.close()
    fillSculptedForm(pen, path, (heel + toe) * HALF + up * (0.014f * sc), 0.036f * sc)

    // Subtle crisp midsole line in Detail/Compact modes so grounded foot orientation reads immediately.
    if (!pen.cutting && pen.detail && !pen.isFar) {
        val lift = up * (0.0055f * sc)
        drawLine(
            color = pen.tone.shoeSole.copy(alpha = 0.42f),
            start = heelBack + along * (0.012f * sc) + lift,
            end = toeBase - along * (0.012f * sc) + lift,
            strokeWidth = 0.0045f * sc,
            cap = StrokeCap.Round,
        )
    }
}

/** Sculpted gripping fist contour extending cleanly from [wrist] around [hand]. */
@Suppress("detekt.MagicNumber") // Gripping fist Bézier contour in standing-height shares.
private fun DrawScope.drawGrippingFist(pen: BodyPen, wrist: Offset, hand: Offset, sc: Float, side: Float) {
    val p = pen.pad
    val axis = hand - wrist
    val len = safeDistance(axis.x, axis.y)
    val u = axis / len
    val f = Offset(u.y, -u.x) * side
    val rw = BodyBuild.WRIST * sc + p
    val rf = BodyBuild.FIST * sc + p
    val tip = hand + u * (rf * 0.92f)

    val path = pen.path
    path.rewind()
    val wFront = wrist + f * rw
    val wBack = wrist - f * rw
    val kFront = hand + f * (rf * 1.06f)
    val kBack = hand - f * (rf * 0.92f)
    path.moveTo(wFront.x, wFront.y)
    path.cubicTo(
        (wrist + u * (len * 0.45f) + f * (rf * 0.95f)).x,
        (wrist + u * (len * 0.45f) + f * (rf * 0.95f)).y,
        (hand - u * (len * 0.15f) + f * (rf * 1.12f)).x,
        (hand - u * (len * 0.15f) + f * (rf * 1.12f)).y,
        kFront.x,
        kFront.y,
    )
    path.cubicTo(
        (hand + u * (rf * 0.65f) + f * (rf * 0.95f)).x,
        (hand + u * (rf * 0.65f) + f * (rf * 0.95f)).y,
        (tip + f * (rf * 0.48f)).x,
        (tip + f * (rf * 0.48f)).y,
        tip.x,
        tip.y,
    )
    path.cubicTo(
        (tip - f * (rf * 0.45f)).x,
        (tip - f * (rf * 0.45f)).y,
        (hand + u * (rf * 0.55f) - f * (rf * 0.85f)).x,
        (hand + u * (rf * 0.55f) - f * (rf * 0.85f)).y,
        kBack.x,
        kBack.y,
    )
    path.cubicTo(
        (hand - u * (len * 0.25f) - f * (rf * 0.95f)).x,
        (hand - u * (len * 0.25f) - f * (rf * 0.95f)).y,
        (wrist + u * (len * 0.35f) - f * (rw * 1.10f)).x,
        (wrist + u * (len * 0.35f) - f * (rw * 1.10f)).y,
        wBack.x,
        wBack.y,
    )
    path.close()
    fillSculptedForm(pen, path, hand, BodyBuild.FIST * sc)
}

/** Graceful open palm and tapered fingers extending from [wrist] through [hand] to [fingers]. */
@Suppress("detekt.MagicNumber") // Open palm & finger taper proportions in standing-height shares.
private fun DrawScope.drawOpenHand(
    pen: BodyPen,
    wrist: Offset,
    hand: Offset,
    fingers: Offset,
    sc: Float,
    side: Float,
) {
    drawAnatomicalTube(
        pen = pen,
        a = wrist,
        b = hand,
        side = side,
        rFrontA = BodyBuild.WRIST * sc,
        rFrontMid = BodyBuild.WRIST * 1.08f * sc,
        rFrontB = BodyBuild.KNUCKLES * sc,
        rBackA = BodyBuild.WRIST * sc,
        rBackMid = BodyBuild.WRIST * 1.05f * sc,
        rBackB = BodyBuild.KNUCKLES * 0.92f * sc,
        frontMidAt = 0.5f,
        backMidAt = 0.5f,
    )
    drawAnatomicalTube(
        pen = pen,
        a = hand,
        b = fingers,
        side = side,
        rFrontA = BodyBuild.KNUCKLES * sc,
        rFrontMid = BodyBuild.KNUCKLES * 0.85f * sc,
        rFrontB = BodyBuild.FINGERS * sc,
        rBackA = BodyBuild.KNUCKLES * 0.90f * sc,
        rBackMid = BodyBuild.KNUCKLES * 0.78f * sc,
        rBackB = BodyBuild.FINGERS * sc,
        frontMidAt = BodyBuild.KNUCKLES_AT,
        backMidAt = BodyBuild.KNUCKLES_AT,
    )
}

private const val HALF = 0.5f
private const val MIN_SEGMENT = 0.5f
private const val CAP_DEPTH = 0.85f
