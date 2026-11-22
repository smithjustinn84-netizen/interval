package dev.justinsmith.interval.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

// Front and top-down (RepFacing.Front, RepFacing.Above) body drawing on BodyFrame screen coordinates.

/**
 * The body viewed from the front or above, back to front: trunk & head, both legs over their hips, and both arms
 * with held weights (drawn behind the trunk when [armsBehind] is set).
 */
internal fun DrawScope.drawFrontBody(
    hold: RepHold,
    frame: BodyFrame,
    pen: BodyPen,
    bellColor: Color,
    bodyColor: Color,
    place: (Offset) -> Offset,
) {
    if (frame.armsBehind) drawFrontArms(hold, frame, pen, bellColor, bodyColor, place)
    gapped(pen, bodyColor) {
        drawFrontTrunk(
            pen,
            hip = place(frame.hip),
            farHip = place(frame.farHip),
            shoulder = place(frame.shoulder),
            farShoulder = place(frame.farShoulder),
            head = place(frame.headCenter),
            spineBow = frame.spineBow,
        )
    }
    gapped(pen, bodyColor) {
        drawContouredLeg(
            pen,
            hip = place(frame.farHip),
            knee = place(frame.farKnee),
            ankle = place(frame.farAnkle),
            heel = place(frame.farHeel),
            toe = place(frame.farToe),
            side = -1f,
        )
    }
    gapped(pen, bodyColor) {
        drawContouredLeg(
            pen,
            hip = place(frame.hip),
            knee = place(frame.knee),
            ankle = place(frame.ankle),
            heel = place(frame.heel),
            toe = place(frame.toe),
        )
    }
    if (!frame.armsBehind) drawFrontArms(hold, frame, pen, bellColor, bodyColor, place)
}

/** Contoured trunk and head seen from the front, with breathing chest and lateral spinal bow. */
private fun DrawScope.drawFrontTrunk(
    pen: BodyPen,
    hip: Offset,
    farHip: Offset,
    shoulder: Offset,
    farShoulder: Offset,
    head: Offset,
    spineBow: Float,
) {
    val s = pen.scale
    val chest = (BodyBuild.FRONT_CHEST + pen.breath * BodyBuild.BREATH) * s
    val hips = (hip + farHip) * HALF
    val neck = (shoulder + farShoulder) * HALF
    val spine = neck - hips
    val len = spine.getDistance().coerceAtLeast(1f)
    val across = Offset(-spine.y / len, spine.x / len)
    val bowPx = spineBow * BodyBuild.BOW * s
    val waist = hips + spine * BodyBuild.WAIST_AT - across * (bowPx * BodyBuild.WAIST_BOW)
    val ribs = hips + spine * BodyBuild.CHEST_AT - across * (bowPx * BodyBuild.CHEST_BOW)
    drawSegment(pen, hip, farHip, BodyBuild.PELVIS * s, BodyBuild.PELVIS * s)
    drawSegment(pen, hips, waist, BodyBuild.FRONT_HIPS * s, BodyBuild.FRONT_WAIST * s)
    drawSegment(pen, waist, ribs, BodyBuild.FRONT_WAIST * s, chest)
    drawSegment(pen, ribs, neck, chest, BodyBuild.FRONT_COLLAR * s)
    drawSegment(pen, shoulder, farShoulder, BodyBuild.DELTOID * s, BodyBuild.DELTOID * s)
    drawSegment(pen, neck, head, BodyBuild.NECK * s, BodyBuild.NECK_TOP * s)
    drawOvalHead(pen, neck, head)
}

private fun DrawScope.drawFrontArms(
    hold: RepHold,
    frame: BodyFrame,
    pen: BodyPen,
    bellColor: Color,
    bodyColor: Color,
    place: (Offset) -> Offset,
) {
    val both = hold == RepHold.BothHands
    val hand = place(frame.kettlebellAnchor)
    val farHand = place(frame.farBellAnchor)
    val bell = place(frame.kettlebellCenter)
    val farBell = place(frame.farBellCenter)
    val elbow = place(frame.elbow)
    val farElbow = place(frame.farElbow)
    val grip = if (both) (hand + farHand) * HALF else hand
    val near = both || hold == RepHold.NearHand || hold == RepHold.Pair
    val resting = near && if (both) bell.y < grip.y else bellRestsOnForearm(elbow, hand, bell)
    val farResting = hold == RepHold.Pair && bellRestsOnForearm(farElbow, farHand, farBell)

    if (hold == RepHold.Pair && !farResting) gapped(pen, bellColor) { drawSculptedKettlebell(pen, farHand, farBell) }
    if (near && !resting) gapped(pen, bellColor) { drawSculptedKettlebell(pen, grip, bell) }
    gapped(pen, bodyColor) {
        drawContouredArm(
            pen,
            shoulder = place(frame.farShoulder),
            elbow = farElbow,
            wrist = place(frame.farWrist),
            hand = farHand,
            fingers = place(frame.farFingers),
            openPalm = frame.hasFarOpenPalm,
        )
    }
    gapped(pen, bodyColor) {
        drawContouredArm(
            pen,
            shoulder = place(frame.shoulder),
            elbow = elbow,
            wrist = place(frame.wrist),
            hand = hand,
            fingers = place(frame.fingers),
            openPalm = frame.hasOpenPalm,
        )
    }
    if (farResting) gapped(pen, bellColor) { drawSculptedKettlebell(pen, farHand, farBell) }
    if (resting) gapped(pen, bellColor) { drawSculptedKettlebell(pen, grip, bell) }
}

private const val HALF = 0.5f
