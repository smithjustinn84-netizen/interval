package dev.justinsmith.interval.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope

/** Draws one animation frame of [rep] onto [stage]: floor, shadow, props, telemetry, contoured body, and foreground. */
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
    drawContext.canvas.saveLayer(stage.layerBounds, pen.layer)
    drawBody(rep, joints, pen, beatColor, colors, place)
    drawContext.canvas.restore()

    if (rep.hold == RepHold.Bar) drawBarbellPlate(place(joints.kettlebellAnchor), s, beatColor, colors.body)
    drawRepBands(rep.bands, joints, s, beatColor, behind = false, place)
    for (i in rails.indices) drawRepProp(rails[i], stage.floor, stage.top, s, colors.far, place)
}

/** The body and what it holds, from the side, the front or above, into the body's layer. */
private fun DrawScope.drawBody(
    rep: ExerciseRep,
    joints: BodyFrame,
    pen: BodyPen,
    bellColor: Color,
    colors: ExerciseDemoColors,
    place: (Offset) -> Offset,
) {
    if (joints.front) {
        drawFrontBody(rep.hold, joints, pen, bellColor, colors.body, place)
    } else {
        drawSideBody(rep, joints, pen, bellColor, colors, place)
    }
}

/**
 * A [DemoDisplayMode.Ghost]'s frame, all in the quiet [ExerciseDemoColors.far]: the floor as a hairline (none
 * from above), what the lift is done on or against, and the body with what it holds as one line round all of it,
 * every part drawn a stroke fatter then cleared at its own size ([BodyPen.rim], [BodyPen.hollow]).
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

    // A bar's plate, end on, is a ring the body shows through, as its disc lets it when filled in.
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

/** Side-view contoured body drawn back to front with clean cutaway halos (no joint dots). */
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
    val shoulder = place(joints.shoulder)
    val head = place(joints.headCenter)
    val bell = place(joints.kettlebellCenter)

    drawSideFarLimbs(hold, joints, pen, hip, shoulder, bellColor, colors.far, place)

    val bellHeld = hold != RepHold.Free && hold != RepHold.Bar
    val behindHead = bellHeld && bell.x < shoulder.x && bell.y < shoulder.y
    val swinging = hold == RepHold.BothHands || rep.bellLag > 0f || rep.showsTrail
    val betweenLegs = bell.y > hip.y && swinging
    if (behindHead) drawNearArmAndBell(hold, joints, pen, bellColor, colors.body, place)
    gapped(pen, colors.body) { drawSideTrunk(pen, hip, shoulder, head, joints.spineBow) }
    if (betweenLegs) drawNearArmAndBell(hold, joints, pen, bellColor, colors.body, place)
    gapped(pen, colors.body) {
        drawContouredLeg(
            pen,
            hip = hip,
            knee = place(joints.knee),
            ankle = place(joints.ankle),
            heel = place(joints.heel),
            toe = place(joints.toe),
        )
    }
    if (!behindHead && !betweenLegs) drawNearArmAndBell(hold, joints, pen, bellColor, colors.body, place)
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
    val farShift = Offset(-BodyProportions.FAR_LEG_OFFSET * pen.scale, 0f)
    pen.ink(farColor)
    drawContouredLeg(
        pen,
        hip = hip,
        knee = place(joints.farKnee) + farShift,
        ankle = place(joints.farAnkle) + farShift,
        heel = place(joints.farHeel) + farShift,
        toe = place(joints.farToe) + farShift,
        limbScale = BodyBuild.FAR,
    )
    val showFarArm = (hold != RepHold.BothHands && hold != RepHold.Bar) || joints.showsFarSide
    if (!showFarArm) return
    val farArmShift = Offset(-BodyProportions.FAR_ARM_OFFSET * pen.scale, 0f)
    val farHand = place(joints.farBellAnchor) + farArmShift
    val farBell = place(joints.farBellCenter) + farArmShift
    pen.ink(farColor)
    drawContouredArm(
        pen,
        shoulder = shoulder + farArmShift,
        elbow = place(joints.farElbow) + farArmShift,
        wrist = place(joints.farWrist) + farArmShift,
        hand = farHand,
        fingers = place(joints.farFingers) + farArmShift,
        openPalm = joints.hasFarOpenPalm,
        limbScale = BodyBuild.FAR,
    )
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
    if (resting) gapped(pen, bellColor) { drawSculptedKettlebell(pen, hand, bell) }
}

private const val HALF = 0.5f
