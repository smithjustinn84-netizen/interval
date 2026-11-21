package dev.justinsmith.lift.ui.demo

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.hypot

/**
 * Draws [demo]'s [figure] fitted and centred in the canvas, its bell in [bell] with [pen]'s trail behind it. Back to
 * front: the floor, its shadow and a mat, the props behind, the bell's glow and trail, then the body in a layer of
 * its own, and over it a bar's plate, a band held in front and a bar to hang from.
 */
internal fun DrawScope.drawFigure(
    demo: ExerciseDemo,
    figure: Figure,
    pen: FigurePen,
    bell: Color,
    colors: FigureColors,
    progress: Float = 0f,
) {
    // A close-up fits its crop, and is clipped to it, so nothing past it is drawn over what is round the figure. One
    // that follows its figure draws from just over it down, which moves; worked out in floats, as it is every frame.
    val view = demo.crop ?: demo.view
    val ceiling = if (demo.crop == null) demo.ceilingAt(progress) else view.top
    val width = view.width
    val height = view.bottom - ceiling
    val scale = minOf(size.width / width, size.height / height)
    val left = (size.width - width * scale) / 2
    val top = (size.height - height * scale) / 2
    withTransform({
        translate(left, top)
        scale(scale, scale, pivot = Offset.Zero)
        translate(-view.left, -ceiling)
        if (demo.crop != null) clipRect(view.left, view.top, view.right, view.bottom)
    }) {
        drawFloor(demo, figure, pen, bell, colors)
        // Indexed loops, here and below: a for-each would make an iterator every frame.
        val props = demo.propsBehind
        for (i in props.indices) drawProp(props[i], colors.farLeg)
        val stands = demo.rails
        for (i in stands.indices) drawRailStand(stands[i], colors.farLeg, view.top)
        // A band tied behind the figure runs behind it; any other is held in front.
        drawBands(demo.bands, figure, bell, behind = true)
        drawGlow(demo.hold, figure, pen.pulse, bell)
        drawTrail(pen, figure.bell, bell)
        // The body has a layer to itself, so a gap cut round a near part cuts only the body behind it, never the
        // floor or a prop, and shows whatever ground the figure is drawn on.
        drawContext.canvas.saveLayer(pen.layerBounds, pen.layer)
        drawBody(demo, figure, pen, bell, colors)
        drawContext.canvas.restore()
        if (demo.hold == Hold.Bar) drawPlate(figure.hand, bell, colors.body)
        drawBands(demo.bands, figure, bell, behind = false)
        // A bar to hang from goes over the fists that grip it.
        val rails = demo.rails
        for (i in rails.indices) drawProp(rails[i], colors.farLeg)
    }
}

/**
 * The body, back to front: the far leg and arm, the trunk and head, then the near arm and leg. The arm goes over
 * the leg, as an arm at the side is nearer the eye, unless its bell is low between the legs: one in both hands,
 * or one that swings, which passes behind the near leg. A bell circled behind the head goes behind the trunk.
 * Each part after the far leg has a gap cut round it where it crosses what is behind, so a limb over the trunk
 * or over its twin reads apart from it.
 */
private fun DrawScope.drawBody(demo: ExerciseDemo, figure: Figure, pen: FigurePen, bell: Color, colors: FigureColors) {
    val hold = demo.hold
    if (figure.front) {
        drawFrontBody(hold, figure, pen, bell, colors.body)
        return
    }
    pen.ink(colors.farLeg)
    drawLeg(pen, figure, far = true)
    if (hold == Hold.NearHand || hold == Hold.Pair || hold == Hold.Free) {
        if (hold == Hold.Pair) gapped(pen, bell) { drawBell(pen, figure.farHand, figure.farBell) }
        gapped(pen, colors.farLeg) { drawArm(pen, figure, far = true) }
    }
    val behindHead = figure.bell.x < figure.shoulder.x && figure.bell.y < figure.shoulder.y
    val betweenLegs = figure.bell.y > figure.hip.y && (hold == Hold.BothHands || demo.bellLag > 0f)
    if (behindHead) drawNearArm(hold, figure, pen, bell, colors.body)
    gapped(pen, colors.body) { drawTrunk(pen, figure) }
    if (betweenLegs) drawNearArm(hold, figure, pen, bell, colors.body)
    gapped(pen, colors.body) { drawLeg(pen, figure) }
    if (!behindHead && !betweenLegs) drawNearArm(hold, figure, pen, bell, colors.body)
}

/**
 * The body seen from the front, back to front: the trunk and head, the legs over the hips they come from, then
 * the arms and what they hold, unless they are round behind the body, which go first. Both sides are as near as
 * each other, so both are the body's colour, told apart by the gap round each.
 */
private fun DrawScope.drawFrontBody(hold: Hold, figure: Figure, pen: FigurePen, bell: Color, body: Color) {
    if (figure.behind) drawFrontArms(hold, figure, pen, bell, body)
    gapped(pen, body) { drawFrontTrunk(pen, figure) }
    gapped(pen, body) { drawFrontLeg(pen, figure, far = true) }
    gapped(pen, body) { drawFrontLeg(pen, figure, far = false) }
    if (!figure.behind) drawFrontArms(hold, figure, pen, bell, body)
}

/**
 * Both arms seen from the front, and their bells: one in the near hand, one in each, or one between both hands,
 * its handle where they meet. A bell that hangs goes under the fists and one resting on a forearm over it, as
 * from the side; one stood over both hands by its horns goes over them, or the forearms under it would hide it.
 */
private fun DrawScope.drawFrontArms(hold: Hold, figure: Figure, pen: FigurePen, bell: Color, body: Color) {
    val both = hold == Hold.BothHands
    val grip = if (both) (figure.hand + figure.farHand) / 2f else figure.hand
    val near = both || hold == Hold.NearHand || hold == Hold.Pair
    val resting = near && if (both) figure.bell.y < grip.y else rests(figure.elbow, figure.hand, figure.bell)
    val farResting = hold == Hold.Pair && rests(figure.farElbow, figure.farHand, figure.farBell)
    if (hold == Hold.Pair && !farResting) gapped(pen, bell) { drawBell(pen, figure.farHand, figure.farBell) }
    if (near && !resting) gapped(pen, bell) { drawBell(pen, grip, figure.bell) }
    gapped(pen, body) { drawFrontArm(pen, figure, far = true) }
    gapped(pen, body) { drawFrontArm(pen, figure, far = false) }
    if (farResting) gapped(pen, bell) { drawBell(pen, figure.farHand, figure.farBell) }
    if (resting) gapped(pen, bell) { drawBell(pen, grip, figure.bell) }
}

/**
 * The floor from one side of the view to the other, fading at its ends; a ground shadow that tracks the figure's
 * footprint and softens as it rises off the floor (hanging, jumping, up on a bench); and, under a figure with
 * nothing in its hands, a mat in the [beat]'s colour, which a bell, a bar or a band would otherwise show.
 */
private fun DrawScope.drawFloor(demo: ExerciseDemo, figure: Figure, pen: FigurePen, beat: Color, colors: FigureColors) {
    val under = demo.matFromAbove
    if (under != null) {
        // Seen from above there is no floor's edge, only the mat the figure lies on.
        val corner = CornerRadius(Rig.MAT_CORNER)
        drawRoundRect(beat.copy(alpha = MAT_FILL_ALPHA), under.topLeft, under.size, corner)
        drawRoundRect(beat.copy(alpha = MAT_EDGE_ALPHA), under.topLeft, under.size, corner, style = MatEdgeStroke)
        return
    }
    drawLine(
        pen.floor,
        Offset(pen.floorFrom, Rig.FLOOR),
        Offset(pen.floorTo, Rig.FLOOR),
        Rig.FLOOR_LINE,
        StrokeCap.Round,
    )
    val minX = minOf(
        minOf(figure.toe.x, figure.heel.x, figure.farToe.x, figure.farHeel.x),
        minOf(figure.knee.x, figure.farKnee.x, figure.hip.x, figure.shoulder.x),
        minOf(figure.head.x, figure.hand.x, figure.farHand.x),
    )
    val maxX = maxOf(
        maxOf(figure.toe.x, figure.heel.x, figure.farToe.x, figure.farHeel.x),
        maxOf(figure.knee.x, figure.farKnee.x, figure.hip.x, figure.shoulder.x),
        maxOf(figure.head.x, figure.hand.x, figure.farHand.x),
    )
    val lowestY = maxOf(
        maxOf(figure.toe.y, figure.heel.y, figure.farToe.y, figure.farHeel.y),
        maxOf(figure.knee.y, figure.farKnee.y, figure.hip.y, figure.shoulder.y),
        maxOf(figure.head.y, figure.hand.y, figure.farHand.y),
    )
    val clearance = (Rig.FLOOR - lowestY).coerceAtLeast(0f)
    val elevation = (1f - clearance / SHADOW_FADE_HEIGHT).coerceIn(SHADOW_MIN_ALPHA_FACTOR, 1f)
    val centerX = (minX + maxX) * HALF
    val span = ((maxX - minX) * SHADOW_SPAN_RATIO + SHADOW_PADDING).coerceIn(SHADOW_MIN_WIDTH, SHADOW_MAX_WIDTH)
    val outerW = span * (1f + clearance / SHADOW_SPREAD_HEIGHT)
    val outerH = Rig.SHADOW_HEIGHT
    val innerW = span * SHADOW_CORE_RATIO
    val innerH = Rig.SHADOW_HEIGHT * SHADOW_CORE_HEIGHT_RATIO
    val shadowY = Rig.FLOOR + Rig.SHADOW_DROP
    val shadowAlpha = colors.shadow.alpha * elevation
    drawOval(
        colors.shadow.copy(alpha = shadowAlpha * SHADOW_OUTER_ALPHA),
        topLeft = Offset(centerX - outerW * HALF, shadowY - outerH * HALF),
        size = Size(outerW, outerH),
    )
    drawOval(
        colors.shadow.copy(alpha = shadowAlpha * SHADOW_INNER_ALPHA),
        topLeft = Offset(centerX - innerW * HALF, shadowY - innerH * HALF),
        size = Size(innerW, innerH),
    )
    val mat = demo.mat ?: return
    val matY = Rig.FLOOR + Rig.MAT * HALF
    drawLine(beat, Offset(mat.start, matY), Offset(mat.endInclusive, matY), Rig.MAT, StrokeCap.Round)
}

/**
 * A soft light round the weight in the beat's colour, behind everything: faint while the bell is carried, and
 * flaring wider and brighter with the [pulse] as a drive starts, so the moment to put the effort in is seen.
 */
private fun DrawScope.drawGlow(hold: Hold, figure: Figure, pulse: Float, color: Color) {
    if (hold == Hold.Free) return
    val bar = hold == Hold.Bar
    val at = if (bar) figure.hand else figure.bell
    val radius = (if (bar) Rig.PLATE * GLOW_PLATE_REACH else Rig.BELL * GLOW_BELL_REACH) * (1f + pulse * GLOW_FLARE)
    val step = (GLOW_ALPHA + pulse * GLOW_PULSE_ALPHA) / GLOW_STEPS
    // Rings one inside the next, each as faint as the last: the light is strongest where they all overlap.
    for (i in GLOW_STEPS downTo 1) drawCircle(color.copy(alpha = step), radius * i / GLOW_STEPS, at)
}

/**
 * Where the bell has just been, drawn as a seamless tapered motion ribbon ending at [tip] with a brighter inner
 * core streak: adjacent quads share exact perpendicular vertices so there is no alpha-overlap beading.
 */
private fun DrawScope.drawTrail(pen: FigurePen, tip: Offset, color: Color) {
    val count = pen.trailCount
    if (count == 0) return
    drawTrailRibbon(pen, tip, color, TRAIL_TAIL_RADIUS, TRAIL_HEAD_RADIUS, TRAIL_OUTER_ALPHA)
    drawTrailRibbon(
        pen,
        tip,
        color,
        TRAIL_TAIL_RADIUS * TRAIL_CORE_RATIO,
        TRAIL_HEAD_RADIUS * TRAIL_CORE_RATIO,
        TRAIL_CORE_ALPHA,
    )
}

/** Traces one tapered ribbon along [pen]'s trail points to [tip] as non-overlapping quads sharing vertex normals. */
private fun DrawScope.drawTrailRibbon(
    pen: FigurePen,
    tip: Offset,
    color: Color,
    tailRadius: Float,
    headRadius: Float,
    maxAlpha: Float,
) {
    val count = pen.trailCount
    val path = pen.path
    var prevPoint = Offset(pen.trail[0], pen.trail[1])
    var prevNormal = trailNormal(pen, tip, 0)
    var prevRadius = tailRadius
    for (i in 1..count) {
        val point = if (i < count) Offset(pen.trail[2 * i], pen.trail[2 * i + 1]) else tip
        val normal = trailNormal(pen, tip, i)
        val share = i.toFloat() / count
        val radius = tailRadius + (headRadius - tailRadius) * share
        path.rewind()
        path.moveTo(prevPoint.x + prevNormal.x * prevRadius, prevPoint.y + prevNormal.y * prevRadius)
        path.lineTo(point.x + normal.x * radius, point.y + normal.y * radius)
        path.lineTo(point.x - normal.x * radius, point.y - normal.y * radius)
        path.lineTo(prevPoint.x - prevNormal.x * prevRadius, prevPoint.y - prevNormal.y * prevRadius)
        path.close()
        drawPath(path, color.copy(alpha = maxAlpha * share * share))
        prevPoint = point
        prevNormal = normal
        prevRadius = radius
    }
}

/** Unit normal perpendicular to the trail's tangent at point [index] (where [pen.trailCount] is [tip]). */
private fun trailNormal(pen: FigurePen, tip: Offset, index: Int): Offset {
    val count = pen.trailCount
    val prev = when {
        index <= 0 -> Offset(pen.trail[0], pen.trail[1])
        index - 1 < count -> Offset(pen.trail[2 * (index - 1)], pen.trail[2 * (index - 1) + 1])
        else -> tip
    }
    val next = when {
        index >= count -> tip
        index + 1 < count -> Offset(pen.trail[2 * (index + 1)], pen.trail[2 * (index + 1) + 1])
        else -> tip
    }
    val dx = next.x - prev.x
    val dy = next.y - prev.y
    val len = hypot(dx, dy)
    return if (len > MIN_TRAIL_STEP) Offset(-dy / len, dx / len) else Offset.Zero
}

/**
 * The near arm and its bell. A bell that hangs past the grip goes under the arm, the fist over its handle; one
 * resting on the back of the forearm (the rack, a lockout) goes over it, on the side nearer the eye, unless
 * both hands hold it by the horns.
 */
private fun DrawScope.drawNearArm(hold: Hold, figure: Figure, pen: FigurePen, bell: Color, body: Color) {
    val held = hold == Hold.BothHands || hold == Hold.NearHand || hold == Hold.Pair
    val resting = held && hold != Hold.BothHands && rests(figure.elbow, figure.hand, figure.bell)
    if (held && !resting) gapped(pen, bell) { drawBell(pen, figure.hand, figure.bell) }
    gapped(pen, body) { drawArm(pen, figure) }
    if (resting) gapped(pen, bell) { drawBell(pen, figure.hand, figure.bell) }
}

/**
 * A kettlebell held at [hand], its body at [body]: a handle arched from the body's shoulders to the grip, and a
 * cast body with a highlight above and a shade below, to give it weight. Cutting, only its outline.
 */
private fun DrawScope.drawBell(pen: FigurePen, hand: Offset, body: Offset) {
    val along = (body - hand) / Rig.BELL_REACH
    val across = Offset(-along.y, along.x)
    val gripHalf = across * Rig.HANDLE_HALF
    val shoulderHalf = across * BELL_SHOULDER_HALF
    val sides = along * Rig.HANDLE_SIDES
    val corner = across * BELL_HANDLE_CORNER
    val path = pen.path
    path.rewind()
    val rightBase = hand + shoulderHalf + sides
    val rightCorner = hand + gripHalf
    val rightGrip = hand + corner
    val leftGrip = hand - corner
    val leftCorner = hand - gripHalf
    val leftBase = hand - shoulderHalf + sides
    path.moveTo(rightBase.x, rightBase.y)
    path.quadraticTo(rightCorner.x, rightCorner.y, rightGrip.x, rightGrip.y)
    path.lineTo(leftGrip.x, leftGrip.y)
    path.quadraticTo(leftCorner.x, leftCorner.y, leftBase.x, leftBase.y)
    drawPath(path, pen.color, style = if (pen.cutting) HandleGapStroke else HandleStroke, blendMode = pen.blend)
    drawCircle(pen.color, Rig.BELL + pen.pad, body, blendMode = pen.blend)
    if (pen.cutting) return
    val arcBox = Size(BELL_DETAIL_DIAMETER, BELL_DETAIL_DIAMETER)
    val arcTopLeft = Offset(body.x - BELL_DETAIL_RADIUS, body.y - BELL_DETAIL_RADIUS)
    drawArc(
        color = Color.Black.copy(alpha = BELL_SHADE_ALPHA),
        startAngle = BELL_SHADE_START,
        sweepAngle = BELL_SHADE_SWEEP,
        useCenter = false,
        topLeft = arcTopLeft,
        size = arcBox,
        style = BellDetailStroke,
    )
    drawArc(
        color = Color.White.copy(alpha = BELL_HIGHLIGHT_ALPHA),
        startAngle = BELL_HIGHLIGHT_START,
        sweepAngle = BELL_HIGHLIGHT_SWEEP,
        useCenter = false,
        topLeft = arcTopLeft,
        size = arcBox,
        style = BellHighlightStroke,
    )
}

/**
 * A barbell seen end on, nearest of everything: a translucent bumper disc, bold outer rim, inner machined
 * flange, and central sleeve hub around [sleeve].
 */
private fun DrawScope.drawPlate(sleeve: Offset, color: Color, axle: Color) {
    drawCircle(color.copy(alpha = PLATE_FILL_ALPHA), Rig.PLATE - Rig.PLATE_RIM * HALF, sleeve)
    drawCircle(color, Rig.PLATE - Rig.PLATE_RIM * HALF, sleeve, style = PlateRimStroke)
    drawCircle(color.copy(alpha = PLATE_INNER_ALPHA), PLATE_INNER_RADIUS, sleeve, style = PlateInnerStroke)
    drawCircle(color, Rig.SLEEVE, sleeve)
    drawCircle(axle.copy(alpha = PLATE_AXLE_ALPHA), PLATE_AXLE_RADIUS, sleeve)
}

private val HandleStroke = Stroke(Rig.HANDLE, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val HandleGapStroke = Stroke(Rig.HANDLE + 2 * Build.GAP, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val MatEdgeStroke = Stroke(Rig.MAT_EDGE)
private val PlateRimStroke = Stroke(Rig.PLATE_RIM)
private val PlateInnerStroke = Stroke(PLATE_INNER_STROKE)
private val BellDetailStroke = Stroke(BELL_SHADE_STROKE, cap = StrokeCap.Round)
private val BellHighlightStroke = Stroke(BELL_HIGHLIGHT_STROKE, cap = StrokeCap.Round)

private const val HALF = 0.5f

/** A mat seen from above: its fill and its edge, in the beat's colour. */
private const val MAT_FILL_ALPHA = 0.16f
private const val MAT_EDGE_ALPHA = 0.7f

/** Dynamic ground shadow tuning. */
private const val SHADOW_SPAN_RATIO = 0.9f
private const val SHADOW_PADDING = 36f
private const val SHADOW_MIN_WIDTH = 92f
private const val SHADOW_MAX_WIDTH = 280f
private const val SHADOW_FADE_HEIGHT = 180f
private const val SHADOW_SPREAD_HEIGHT = 360f
private const val SHADOW_MIN_ALPHA_FACTOR = 0.25f
private const val SHADOW_CORE_RATIO = 0.62f
private const val SHADOW_CORE_HEIGHT_RATIO = 0.6f
private const val SHADOW_OUTER_ALPHA = 0.38f
private const val SHADOW_INNER_ALPHA = 0.68f

/** The light round the weight: how far past it it reaches, how strong it is carried and at a drive's start. */
private const val GLOW_STEPS = 16
private const val GLOW_BELL_REACH = 2.3f
private const val GLOW_PLATE_REACH = 1.35f
private const val GLOW_FLARE = 0.35f
private const val GLOW_ALPHA = 0.16f
private const val GLOW_PULSE_ALPHA = 0.2f

/** Tapered motion trail ribbon tuning. */
private const val MIN_TRAIL_STEP = 0.001f
private const val TRAIL_TAIL_RADIUS = 1.2f
private const val TRAIL_HEAD_RADIUS = 9.5f
private const val TRAIL_CORE_RATIO = 0.42f
private const val TRAIL_OUTER_ALPHA = 0.34f
private const val TRAIL_CORE_ALPHA = 0.55f

/** Sculpted kettlebell geometry and shading. */
private const val BELL_SHOULDER_HALF = 11.5f
private const val BELL_HANDLE_CORNER = 4.5f
private const val BELL_DETAIL_RADIUS = 12.2f
private const val BELL_DETAIL_DIAMETER = BELL_DETAIL_RADIUS * 2f
private const val BELL_SHADE_ALPHA = 0.18f
private const val BELL_SHADE_STROKE = 3.2f
private const val BELL_SHADE_START = 20f
private const val BELL_SHADE_SWEEP = 140f
private const val BELL_HIGHLIGHT_ALPHA = 0.34f
private const val BELL_HIGHLIGHT_STROKE = 2.6f
private const val BELL_HIGHLIGHT_START = 205f
private const val BELL_HIGHLIGHT_SWEEP = 75f

/** Barbell bumper plate detail. */
private const val PLATE_FILL_ALPHA = 0.14f
private const val PLATE_INNER_RADIUS = 18f
private const val PLATE_INNER_STROKE = 2.2f
private const val PLATE_INNER_ALPHA = 0.42f
private const val PLATE_AXLE_RADIUS = 2.6f
private const val PLATE_AXLE_ALPHA = 0.7f
