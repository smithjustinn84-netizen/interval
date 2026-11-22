package dev.justinsmith.interval.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope

/** Draws one animation frame of [rep] onto [stage]: floor, shadow, props, telemetry, sculpted body, and foreground. */
internal fun DrawScope.drawCanvasScene(
    rep: ExerciseRep,
    stage: DemoStage,
    joints: BodyFrame,
    at: Float,
    beatColor: Color,
    faintBell: Color,
    colors: ExerciseDemoColors,
    mode: DemoDisplayMode,
) {
    if (mode == DemoDisplayMode.Ghost) {
        drawGhostScene(rep, stage, joints, colors)
        return
    }
    val place = stage::place
    val s = stage.scale
    val detail = mode != DemoDisplayMode.Thumb
    drawFloorAndShadow(
        frame = joints,
        floorY = stage.floor,
        floorBrush = stage.floorBrush,
        floorFrom = stage.floorFrom,
        floorTo = stage.floorTo,
        matRange = stage.matRange,
        matFromAbove = stage.matFromAbove,
        scale = s,
        beatColor = beatColor,
        shadowColor = colors.shadow,
        detail = detail,
        place = place,
    )
    val propsBehind = rep.propsBehind
    for (i in propsBehind.indices) drawRepProp(propsBehind[i], stage.floor, stage.top, s, colors.far, place)
    val rails = rep.rails
    for (i in rails.indices) drawRepRailStand(rails[i], stage.floor, stage.top, s, colors.far, place)
    drawRepBands(rep.bands, joints, s, beatColor, behind = true, place)
    drawDetailTelemetry(rep, stage, at, beatColor, faintBell, colors, mode)
    drawMotionAccents(rep, stage, joints, at, beatColor, mode)

    val pen = stage.pen
    pen.detail = detail
    pen.tone.update(colors.body, colors.far)
    drawContext.canvas.saveLayer(stage.layerBounds, pen.layer)
    drawBody(rep, joints, pen, beatColor, colors, place)
    drawContext.canvas.restore()

    if (rep.hold == RepHold.Bar) drawBarbellPlate(place(joints.kettlebellAnchor), s, beatColor, colors.body)
    drawRepBands(rep.bands, joints, s, beatColor, behind = false, place)
    for (i in rails.indices) drawRepProp(rails[i], stage.floor, stage.top, s, colors.far, place)
}

/** The sculpted body and what it holds, from the side, the front or above, into the body's layer. */
private fun DrawScope.drawBody(
    rep: ExerciseRep,
    joints: BodyFrame,
    pen: BodyPen,
    bellColor: Color,
    colors: ExerciseDemoColors,
    place: (Offset) -> Offset,
) {
    if (joints.front) {
        drawFrontBody(rep.hold, joints, pen, bellColor, colors, place)
    } else {
        drawSideBody(rep, joints, pen, bellColor, colors, place)
    }
}

/**
 * A [DemoDisplayMode.Ghost]'s frame, all in the quiet [ExerciseDemoColors.far]: the floor as a hairline (none
 * from above), what the lift is done on or against, and the sculpted body with what it holds as one line round
 * all of it, every part drawn a stroke fatter then cleared at its own size ([BodyPen.rim], [BodyPen.hollow]).
 */
private fun DrawScope.drawGhostScene(
    rep: ExerciseRep,
    stage: DemoStage,
    joints: BodyFrame,
    colors: ExerciseDemoColors,
) {
    val place = stage::place
    val s = stage.scale
    val ink = colors.far
    val pen = stage.pen
    pen.breath = 0f
    pen.pulse = 0f
    pen.detail = false
    if (stage.matFromAbove == null) {
        drawLine(
            stage.floorBrush,
            Offset(stage.floorFrom, stage.floor),
            Offset(stage.floorTo, stage.floor),
            stage.hairline,
            StrokeCap.Round,
        )
    }
    val propsBehind = rep.propsBehind
    for (i in propsBehind.indices) drawRepProp(propsBehind[i], stage.floor, stage.top, s, ink, place)
    val rails = rep.rails
    for (i in rails.indices) drawRepRailStand(rails[i], stage.floor, stage.top, s, ink, place)
    drawRepBands(rep.bands, joints, s, ink, behind = true, place)

    drawContext.canvas.saveLayer(stage.layerBounds, pen.layer)
    pen.rim(ink, stage.hairline)
    drawBody(rep, joints, pen, ink, colors, place)
    pen.hollow()
    drawBody(rep, joints, pen, ink, colors, place)
    pen.solid()
    drawContext.canvas.restore()

    if (rep.hold == RepHold.Bar) {
        drawCircle(ink, BodyProportions.PLATE_RADIUS * s, place(joints.kettlebellAnchor), style = stage.markStyle)
    }
    drawRepBands(rep.bands, joints, s, ink, behind = false, place)
    for (i in rails.indices) drawRepProp(rails[i], stage.floor, stage.top, s, ink, place)
}

private fun DrawScope.drawDetailTelemetry(
    rep: ExerciseRep,
    stage: DemoStage,
    at: Float,
    beatColor: Color,
    faintBell: Color,
    colors: ExerciseDemoColors,
    mode: DemoDisplayMode,
) {
    if (mode != DemoDisplayMode.Detail) return
    val groundedProfile = rep.facing == RepFacing.Side && rep.root == RepRoot.Ankle
    if (groundedProfile && rep.crop == null && !rep.follows) {
        drawPlumbReferences(stage, colors.reference)
    }
    if (rep.showsTrajectory) {
        drawTrajectory(stage, at, beatColor, faintBell, !rep.showsTrail)
    }
}

private fun DrawScope.drawMotionAccents(
    rep: ExerciseRep,
    stage: DemoStage,
    joints: BodyFrame,
    at: Float,
    beatColor: Color,
    mode: DemoDisplayMode,
) {
    val pen = stage.pen
    pen.breath = if (mode == DemoDisplayMode.Thumb) 0f else rep.breathAt(at)
    pen.pulse = if (mode == DemoDisplayMode.Thumb) 0f else rep.pulseAt(at)
    if (mode == DemoDisplayMode.Thumb) return
    val hand = stage.place(joints.kettlebellAnchor)
    val bell = stage.place(joints.kettlebellCenter)
    drawWeightGlow(rep.hold, hand, bell, pen.pulse, stage.scale, beatColor)
    if (rep.showsTrail) {
        stage.populateMotionTrail(at)
        stage.jointsAt(at)
        drawMotionTrail(pen, bell, beatColor)
    }
}

/** Vertical plumb line up from the planted ankle with standing hip & shoulder datum ticks. */
private fun DrawScope.drawPlumbReferences(stage: DemoStage, color: Color) {
    drawLine(
        color,
        Offset(stage.plumb, stage.top),
        Offset(stage.plumb, stage.floor),
        stage.hairline,
        cap = StrokeCap.Round,
        pathEffect = stage.dashes,
    )
    val tick = BodyProportions.JOINT_REACH * stage.scale
    for (y in stage.datums) {
        drawLine(color, Offset(stage.plumb - tick, y), Offset(stage.plumb + tick, y), stage.hairline)
    }
}

/** Dashed trajectory path, rest-pose rings, and (when not using the tapered ribbon) active solid trail. */
private fun DrawScope.drawTrajectory(
    stage: DemoStage,
    progress: Float,
    color: Color,
    faint: Color,
    drawLineTrail: Boolean,
) {
    drawPath(stage.trajectory, faint, style = stage.pathStyle)
    for (index in stage.keyMarks.indices) {
        drawCircle(faint, stage.halo * DemoStage.KEY_MARK, stage.keyMarks[index], style = stage.markStyle)
    }
    if (drawLineTrail) {
        drawPath(stage.trailTo(progress, DemoStage.TRAIL_SHARE), faint, style = stage.trailStyle)
        drawPath(stage.trailTo(progress, DemoStage.TRAIL_SHARE * HALF), color, style = stage.trailStyle)
    }
}

/**
 * Side-view sculpted athlete drawn back to front: recessed far limbs with cast hip/shoulder occlusion, seamless
 * contoured trunk & near leg, and foreground arm/bell with refined hairline separation where they cross the torso.
 */
private fun DrawScope.drawSideBody(
    rep: ExerciseRep,
    joints: BodyFrame,
    pen: BodyPen,
    bellColor: Color,
    colors: ExerciseDemoColors,
    place: (Offset) -> Offset,
) {
    val hold = rep.hold
    val hip = place(joints.hip)
    val knee = place(joints.knee)
    val shoulder = place(joints.shoulder)
    val head = place(joints.headCenter)
    val bell = place(joints.kettlebellCenter)

    drawSideFarLimbs(hold, joints, pen, hip, shoulder, bellColor, colors.far, place)

    val bellHeld = hold != RepHold.Free && hold != RepHold.Bar
    val behindHead = bellHeld && bell.x < shoulder.x && bell.y < shoulder.y
    val swinging = hold == RepHold.BothHands || rep.bellLag > 0f || rep.showsTrail
    val betweenLegs = bell.y > hip.y && swinging
    if (behindHead) drawNearArmAndBell(hold, joints, pen, bellColor, colors.body, place)

    pen.ink(colors.body)
    drawSideTrunk(pen, hip, shoulder, head, joints.spineBow)

    if (betweenLegs) drawNearArmAndBell(hold, joints, pen, bellColor, colors.body, place)

    pen.ink(colors.body)
    drawContouredLeg(
        pen,
        hip = hip,
        knee = knee,
        ankle = place(joints.ankle),
        heel = place(joints.heel),
        toe = place(joints.toe),
    )
    drawHipFlexionShade(pen, hip, knee, shoulder)

    if (!behindHead && !betweenLegs) drawNearArmAndBell(hold, joints, pen, bellColor, colors.body, place)
}

/** Subtle anatomical hip-crease occlusion shadow when the near thigh folds toward the trunk (squat/hinge/lunge). */
private fun DrawScope.drawHipFlexionShade(pen: BodyPen, hip: Offset, knee: Offset, shoulder: Offset) {
    if (!pen.detail) return
    val spine = shoulder - hip
    val thigh = knee - hip
    val sLen = safeDistance(spine.x, spine.y)
    val tLen = safeDistance(thigh.x, thigh.y)
    // Dot product near -1 when standing straight, rising toward +1 in deep squat or tight hinge.
    val fold = ((spine.x * thigh.x + spine.y * thigh.y) / (sLen * tLen) + HIP_FOLD_START)
        .coerceIn(0f, 1f)
    if (fold <= 0f) return
    val crease = hip + (spine / sLen + thigh / tLen) * (HIP_CREASE_SHIFT * pen.scale)
    drawBodyOcclusion(pen, crease, HIP_CREASE_RADIUS * pen.scale, strength = fold * HIP_CREASE_STRENGTH)
}

private fun DrawScope.drawSideFarLimbs(
    hold: RepHold,
    joints: BodyFrame,
    pen: BodyPen,
    hip: Offset,
    shoulder: Offset,
    bellColor: Color,
    farColor: Color,
    place: (Offset) -> Offset,
) {
    val legOffset = if (joints.showsFarSide) BodyProportions.FAR_LEG_OFFSET else FAR_STANCE_LEG_OFFSET
    val farShift = Offset(-legOffset * pen.scale, 0f)
    val farKnee = place(joints.farKnee) + farShift
    pen.ink(farColor, far = true)
    drawContouredLeg(
        pen,
        hip = hip,
        knee = farKnee,
        ankle = place(joints.farAnkle) + farShift,
        heel = place(joints.farHeel) + farShift,
        toe = place(joints.farToe) + farShift,
        limbScale = BodyBuild.FAR,
    )
    // Soft shadow cast by the pelvis & near thigh onto the receding far thigh (no surgical cutout gap).
    if (joints.showsFarSide) {
        val farThighDir = farKnee - hip
        val farThighLen = safeDistance(farThighDir.x, farThighDir.y)
        val castAt = hip + farThighDir * ((FAR_CAST_SHIFT * pen.scale) / farThighLen).coerceAtMost(MAX_CAST_SHARE)
        drawBodyOcclusion(pen, castAt, FAR_CAST_RADIUS * pen.scale, strength = FAR_CAST_STRENGTH)
    }

    val showFarArm = (hold != RepHold.BothHands && hold != RepHold.Bar) || joints.showsFarSide
    if (!showFarArm) return
    val farArmShift = Offset(-BodyProportions.FAR_ARM_OFFSET * pen.scale, 0f)
    val farShoulder = shoulder + farArmShift
    val farElbow = place(joints.farElbow) + farArmShift
    val farHand = place(joints.farBellAnchor) + farArmShift
    val farBell = place(joints.farBellCenter) + farArmShift
    pen.ink(farColor, far = true)
    drawContouredArm(
        pen,
        shoulder = farShoulder,
        elbow = farElbow,
        wrist = place(joints.farWrist) + farArmShift,
        hand = farHand,
        fingers = place(joints.farFingers) + farArmShift,
        openPalm = joints.hasFarOpenPalm,
        limbScale = BodyBuild.FAR,
    )
    if (joints.showsFarSide) {
        val farArmDir = farElbow - farShoulder
        val farArmLen = safeDistance(farArmDir.x, farArmDir.y)
        val castAt = farShoulder + farArmDir * ((FAR_CAST_SHIFT * pen.scale) / farArmLen).coerceAtMost(MAX_CAST_SHARE)
        drawBodyOcclusion(pen, castAt, FAR_ARM_CAST_RADIUS * pen.scale, strength = FAR_CAST_STRENGTH)
    }
    if (hold == RepHold.Pair) gapped(pen, bellColor) { drawSculptedKettlebell(pen, farHand, farBell) }
}

private fun DrawScope.drawNearArmAndBell(
    hold: RepHold,
    joints: BodyFrame,
    pen: BodyPen,
    bellColor: Color,
    bodyColor: Color,
    place: (Offset) -> Offset,
) {
    val elbow = place(joints.elbow)
    val wrist = place(joints.wrist)
    val hand = place(joints.kettlebellAnchor)
    val bell = place(joints.kettlebellCenter)
    val held = hold == RepHold.BothHands || hold == RepHold.NearHand || hold == RepHold.Pair
    val resting = held && hold != RepHold.BothHands && bellRestsOnForearm(elbow, hand, bell)
    if (held && !resting) gapped(pen, bellColor) { drawSculptedKettlebell(pen, hand, bell) }
    gapped(pen, bodyColor) {
        drawContouredArm(
            pen,
            shoulder = place(joints.shoulder),
            elbow = elbow,
            wrist = wrist,
            hand = hand,
            fingers = place(joints.fingers),
            openPalm = joints.hasOpenPalm,
        )
    }
    if (resting) {
        drawBodyOcclusion(pen, bell, BELL_CAST_RADIUS * pen.scale, strength = BELL_CAST_STRENGTH)
        gapped(pen, bellColor) { drawSculptedKettlebell(pen, hand, bell) }
    }
}

private const val HALF = 0.5f
private const val FAR_STANCE_LEG_OFFSET = 0.011f
private const val FAR_CAST_SHIFT = 0.055f
private const val FAR_CAST_RADIUS = 0.052f
private const val FAR_ARM_CAST_RADIUS = 0.038f
private const val FAR_CAST_STRENGTH = 0.72f
private const val MAX_CAST_SHARE = 0.45f
private const val HIP_FOLD_START = 0.45f
private const val HIP_CREASE_SHIFT = 0.034f
private const val HIP_CREASE_RADIUS = 0.034f
private const val HIP_CREASE_STRENGTH = 0.22f
private const val BELL_CAST_RADIUS = 0.064f
private const val BELL_CAST_STRENGTH = 0.50f
