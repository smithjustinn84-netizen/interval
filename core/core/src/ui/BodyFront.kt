package dev.justinsmith.interval.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

// Front and top-down (RepFacing.Front, RepFacing.Above) sculpted body drawing on BodyFrame screen coordinates.

/**
 * The sculpted athlete viewed from the front or above, back to front: recessed arms when [BodyFrame.armsBehind] is
 * set, continuous athletic V-taper trunk & head, both legs seamlessly grounded onto the pelvis, and foreground arms
 * with held weights.
 */
internal fun DrawScope.drawFrontBody(
    hold: RepHold,
    frame: BodyFrame,
    pen: BodyPen,
    bellColor: Color,
    colors: ExerciseDemoColors,
    place: (Offset) -> Offset,
) {
    if (frame.armsBehind) drawFrontArms(hold, frame, pen, bellColor, colors.far, far = true, place = place)

    pen.ink(colors.body)
    drawFrontTrunk(
        pen,
        hip = place(frame.hip),
        farHip = place(frame.farHip),
        shoulder = place(frame.shoulder),
        farShoulder = place(frame.farShoulder),
        head = place(frame.headCenter),
        spineBow = frame.spineBow,
    )
    drawContouredLeg(
        pen,
        hip = place(frame.farHip),
        knee = place(frame.farKnee),
        ankle = place(frame.farAnkle),
        heel = place(frame.farHeel),
        toe = place(frame.farToe),
        side = -1f,
    )
    drawContouredLeg(
        pen,
        hip = place(frame.hip),
        knee = place(frame.knee),
        ankle = place(frame.ankle),
        heel = place(frame.heel),
        toe = place(frame.toe),
    )

    if (!frame.armsBehind) drawFrontArms(hold, frame, pen, bellColor, colors.body, far = false, place = place)
}

/**
 * Continuous athletic V-taper trunk seen from the front or above: curved trapezius slopes into rounded deltoids,
 * sculpted lat/ribcage V-taper (expanding with breathing), lean waist (bowing with [spineBow]), and smooth oblique
 * flare into the pelvis, topped by a contoured jawed head and neck.
 */
@Suppress("detekt.MagicNumber") // Front-view athletic V-taper Bézier torso landmarks in standing-height shares.
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
    val p = pen.pad
    val hips = (hip + farHip) * HALF
    val neck = (shoulder + farShoulder) * HALF
    val spine = neck - hips
    val len = safeDistance(spine.x, spine.y)
    val u = spine / len
    val across = shoulder - farShoulder
    val acrossLen = across.getDistance()
    val r = if (acrossLen > MIN_AXIS) across / acrossLen else Offset(-u.y, u.x)

    val bow = spineBow * BodyBuild.BOW * s
    val breath = pen.breath * BodyBuild.BREATH * s
    val chestHalf = BodyBuild.FRONT_CHEST * s + breath + p
    val waistHalf = BodyBuild.FRONT_WAIST * s + p
    val hipHalf = BodyBuild.FRONT_HIPS * s + p
    val delt = BodyBuild.DELTOID * s + p
    val neckHalf = BodyBuild.NECK * s + p

    fun tp(along: Float, sidePx: Float): Offset = hips + u * (len * along) + r * sidePx

    val neckR = neck + r * neckHalf + u * (0.012f * s)
    val neckL = neck - r * neckHalf + u * (0.012f * s)
    val shoulderOuterR = shoulder + r * (delt * 0.92f) - u * (0.008f * s)
    val shoulderOuterL = farShoulder - r * (delt * 0.92f) - u * (0.008f * s)
    val chestR = tp(BodyBuild.CHEST_AT, chestHalf - bow * BodyBuild.CHEST_BOW)
    val chestL = tp(BodyBuild.CHEST_AT, -(chestHalf + bow * BodyBuild.CHEST_BOW))
    val waistR = tp(BodyBuild.WAIST_AT, waistHalf - bow * BodyBuild.WAIST_BOW)
    val waistL = tp(BodyBuild.WAIST_AT, -(waistHalf + bow * BodyBuild.WAIST_BOW))
    val hipOuterR = tp(0.02f, hipHalf)
    val hipOuterL = tp(0.02f, -hipHalf)
    val pelvisBot = hips - u * (0.050f * s + p)

    val path = pen.path
    path.rewind()
    fun Path.cubic(c1: Offset, c2: Offset, to: Offset) = cubicTo(c1.x, c1.y, c2.x, c2.y, to.x, to.y)
    path.moveTo(neckR.x, neckR.y)
    path.cubic(
        neck + r * (neckHalf * 1.45f) + u * (0.014f * s),
        shoulder + r * (delt * 0.65f) + u * (delt * 0.72f),
        shoulderOuterR,
    )
    path.cubic(
        shoulder + r * (delt * 0.98f) - u * (delt * 0.85f),
        tp(0.84f, chestHalf * 1.08f - bow * 0.55f),
        chestR,
    )
    path.cubic(tp(0.60f, chestHalf * 0.88f - bow * 0.82f), tp(0.50f, waistHalf * 1.02f - bow * 0.95f), waistR)
    path.cubic(tp(0.24f, waistHalf * 1.06f - bow * 0.6f), tp(0.14f, hipHalf * 0.98f), hipOuterR)
    path.cubic(tp(-0.08f, hipHalf * 0.84f), pelvisBot + r * (hipHalf * 0.45f), pelvisBot)
    path.cubic(pelvisBot - r * (hipHalf * 0.45f), tp(-0.08f, -hipHalf * 0.84f), hipOuterL)
    path.cubic(tp(0.14f, -hipHalf * 0.98f), tp(0.24f, -(waistHalf * 1.06f + bow * 0.6f)), waistL)
    path.cubic(tp(0.50f, -(waistHalf * 1.02f + bow * 0.95f)), tp(0.60f, -(chestHalf * 0.88f + bow * 0.82f)), chestL)
    path.cubic(
        tp(0.84f, -(chestHalf * 1.08f + bow * 0.55f)),
        farShoulder - r * (delt * 0.98f) - u * (delt * 0.85f),
        shoulderOuterL,
    )
    path.cubic(
        farShoulder - r * (delt * 0.65f) + u * (delt * 0.72f),
        neck - r * (neckHalf * 1.45f) + u * (0.014f * s),
        neckL,
    )
    path.close()
    fillSculptedLimb(pen, path, hips, neck, BodyBuild.FRONT_CHEST * s)
    drawFrontHead(pen, neck, head)
}

@Suppress("detekt.LongParameterList") // Matches BodyFront's draw order helper without intermediate wrappers.
private fun DrawScope.drawFrontArms(
    hold: RepHold,
    frame: BodyFrame,
    pen: BodyPen,
    bellColor: Color,
    armColor: Color,
    far: Boolean,
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

    val drawBothArms = {
        drawContouredArm(
            pen,
            shoulder = place(frame.farShoulder),
            elbow = farElbow,
            wrist = place(frame.farWrist),
            hand = farHand,
            fingers = place(frame.farFingers),
            openPalm = frame.hasFarOpenPalm,
            side = -1f,
        )
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

    if (far) {
        pen.ink(armColor, far = true)
        drawBothArms()
    } else {
        gapped(pen, armColor) { drawBothArms() }
    }

    if (farResting) gapped(pen, bellColor) { drawSculptedKettlebell(pen, farHand, farBell) }
    if (resting) gapped(pen, bellColor) { drawSculptedKettlebell(pen, grip, bell) }
}

private const val HALF = 0.5f
private const val MIN_AXIS = 0.5f
