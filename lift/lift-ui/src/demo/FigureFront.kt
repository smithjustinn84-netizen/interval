package dev.justinsmith.lift.ui.demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope

// The figure's parts seen from the front (Facing.Front): each side has its own hip and shoulder and is as near as
// the other, so nothing is drawn a step behind or slighter.

/**
 * The trunk and the head seen from the front: the hips across, the waist drawn in, the chest broad, the shoulders
 * across the top of it, a neck and a head. The chest swells and sinks with the pen's breath.
 */
internal fun DrawScope.drawFrontTrunk(pen: FigurePen, figure: Figure) {
    val chest = Build.FRONT_CHEST + pen.breath * Build.BREATH
    val hips = figure.hips
    val neck = figure.neck
    // A bowed spine bends the trunk across the body, the waist most: to the near side, or arched to the far one.
    val spine = neck - hips
    val across = Offset(-spine.y, spine.x) / spine.getDistance().coerceAtLeast(1f)
    val waist = hips + spine * Build.WAIST_AT - across * (figure.spine * Build.BOW * Build.WAIST_BOW)
    val ribs = hips + spine * Build.CHEST_AT - across * (figure.spine * Build.BOW * Build.CHEST_BOW)
    drawSegment(pen, figure.hip, figure.farHip, Build.PELVIS, Build.PELVIS)
    drawSegment(pen, hips, waist, Build.FRONT_HIPS, Build.FRONT_WAIST)
    drawSegment(pen, waist, ribs, Build.FRONT_WAIST, chest)
    drawSegment(pen, ribs, neck, chest, Build.FRONT_COLLAR)
    drawSegment(pen, figure.shoulder, figure.farShoulder, Build.DELTOID, Build.DELTOID)
    drawSegment(pen, neck, figure.head, Build.NECK, Build.NECK_TOP)
    drawHead(pen, neck, figure.head)
}

/**
 * A leg seen from the front, each from its own hip and as near as the other: the shoe points at the eye, so what
 * shows of it is short, and the [far] one's is turned the other way.
 */
internal fun DrawScope.drawFrontLeg(pen: FigurePen, figure: Figure, far: Boolean) = if (far) {
    drawLegFrom(pen, figure.farHip, figure.farKnee, figure.farAnkle, figure.farHeel, figure.farToe, side = -1f)
} else {
    drawLegFrom(pen, figure.hip, figure.knee, figure.ankle, figure.heel, figure.toe)
}

/** An arm seen from the front, each from its own shoulder and as near as the other. */
internal fun DrawScope.drawFrontArm(pen: FigurePen, figure: Figure, far: Boolean) = if (far) {
    drawArmFrom(pen, figure.farShoulder, figure.farElbow, figure.farHand)
} else {
    drawArmFrom(pen, figure.shoulder, figure.elbow, figure.hand)
}
