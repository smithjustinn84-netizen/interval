package dev.justinsmith.lift.ui.demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.hypot

/** What a figure draws with, kept from frame to frame: the path each limb is traced on and the bell's trail. */
internal class FigurePen {
    val path = Path()
    val trail = FloatArray(2 * ExerciseDemo.TRAIL_STEPS)
    var trailCount = 0
}

/**
 * Draws [demo]'s [figure] fitted and centred in the canvas, its bell in [bell] with [pen]'s trail behind it. Back to
 * front: the floor and dynamic shadow, props behind, the far limbs, the tapered trunk and head, then the near arm
 * and leg in whichever order keeps a bell low between the legs behind the near one and a bell held high in front of
 * it. A bell circled behind the head goes behind the trunk. A band tied to a post behind the figure runs behind it;
 * any other is drawn over it.
 */
internal fun DrawScope.drawFigure(
    demo: ExerciseDemo,
    figure: Figure,
    pen: FigurePen,
    bell: Color,
    colors: FigureColors,
) {
    val path = pen.path
    val view = demo.view
    val scale = minOf(size.width / view.width, size.height / view.height)
    val left = (size.width - view.width * scale) / 2
    val top = (size.height - view.height * scale) / 2
    withTransform({
        translate(left, top)
        scale(scale, scale, pivot = Offset.Zero)
        translate(-view.left, -view.top)
    }) {
        drawFloor(figure, view, colors)
        // Indexed loops, here and below: a for-each would make an iterator every frame.
        val props = demo.propsBehind
        for (i in props.indices) drawProp(props[i], colors.farLeg)
        // A band tied behind the figure runs behind it; any other is held in front.
        drawBands(demo.bands, figure, bell, behind = true)
        drawTrail(pen, figure.bell, bell)
        val behind = Offset(-Rig.FAR_LEG_OFFSET, 0f)
        drawTaperedLeg(
            hip = figure.hip,
            knee = figure.farKnee + behind,
            ankle = figure.farAnkle + behind,
            heel = figure.farHeel + behind,
            toe = figure.farToe + behind,
            color = colors.farLeg,
            path = path,
            scale = Silhouette.FAR_SCALE,
        )
        if (demo.hold == Hold.NearHand || demo.hold == Hold.Pair || demo.hold == Hold.Free) {
            drawFarArm(figure, demo.hold == Hold.Pair, bell, colors.farLeg, path)
        }
        val behindHead = figure.bell.x < figure.shoulder.x && figure.bell.y < figure.shoulder.y
        if (behindHead) drawArmAndBell(figure, demo.hold, bell, colors, separation = null, path = path)
        drawTorsoAndHead(figure, colors.body, path)
        val armSeparation = colors.floor.copy(alpha = Silhouette.SEPARATION_ALPHA)
        when {
            behindHead -> drawNearLeg(figure, colors.body, path)

            figure.bell.y > figure.hip.y -> {
                drawArmAndBell(figure, demo.hold, bell, colors, separation = armSeparation, path = path)
                drawNearLeg(figure, colors.body, path)
            }

            else -> {
                drawNearLeg(figure, colors.body, path)
                drawArmAndBell(figure, demo.hold, bell, colors, separation = armSeparation, path = path)
            }
        }
        if (demo.hold == Hold.Bar) drawPlate(figure.hand, bell, colors.body)
        drawBands(demo.bands, figure, bell, behind = false)
        // A bar to hang from goes over the fists that grip it.
        val rails = demo.rails
        for (i in rails.indices) drawProp(rails[i], colors.farLeg)
    }
}

/**
 * The floor line and a dynamic ground shadow that tracks the figure's horizontal footprint and softens when the
 * figure rises off the floor (hanging, jumping, or elevated on a bench).
 */
private fun DrawScope.drawFloor(figure: Figure, view: Rect, colors: FigureColors) {
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
    val floorLeft = minOf(Rig.ANKLE_X - Rig.FLOOR_REACH, centerX - outerW * HALF - FLOOR_MARGIN)
        .coerceAtLeast(view.left + FLOOR_EDGE_INSET)
    val floorRight = maxOf(Rig.ANKLE_X + Rig.FLOOR_REACH_FORWARD, centerX + outerW * HALF + FLOOR_MARGIN)
        .coerceAtMost(view.right - FLOOR_EDGE_INSET)
    drawLine(
        colors.floor,
        Offset(floorLeft, Rig.FLOOR),
        Offset(floorRight, Rig.FLOOR),
        Rig.FLOOR_LINE,
        StrokeCap.Round,
    )
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
private fun DrawScope.drawArmAndBell(
    figure: Figure,
    hold: Hold,
    bell: Color,
    colors: FigureColors,
    separation: Color?,
    path: Path,
) {
    if (hold == Hold.Bar || hold == Hold.Free) {
        drawTaperedArm(figure.shoulder, figure.elbow, figure.hand, colors.body, path, separation = separation)
        return
    }
    val resting = hold != Hold.BothHands && rests(figure.elbow, figure.hand, figure.bell)
    if (!resting) drawBell(figure.hand, figure.bell, bell, path)
    drawTaperedArm(figure.shoulder, figure.elbow, figure.hand, colors.body, path, separation = separation)
    if (resting) drawBell(figure.hand, figure.bell, bell, path)
}

/** The far arm, behind the trunk, with its own bell in a [pair]. */
private fun DrawScope.drawFarArm(figure: Figure, pair: Boolean, bell: Color, far: Color, path: Path) {
    val behind = Offset(-Rig.FAR_ARM_OFFSET, 0f)
    if (pair) drawBell(figure.farHand, figure.farBell, bell, path)
    drawTaperedArm(
        figure.shoulder + behind,
        figure.farElbow,
        figure.farHand,
        far,
        path,
        scale = Silhouette.FAR_SCALE,
    )
}

/**
 * A sculpted kettlebell held at [hand], its body at [body]: a curved U-arch handle meeting the bell shoulders,
 * and a cast body with a subtle specular highlight and lower shading arc for three-dimensional weight.
 */
private fun DrawScope.drawBell(hand: Offset, body: Offset, color: Color, path: Path) {
    val along = (body - hand) / Rig.BELL_REACH
    val across = Offset(-along.y, along.x)
    val gripHalf = across * Rig.HANDLE_HALF
    val shoulderHalf = across * BELL_SHOULDER_HALF
    val sides = along * Rig.HANDLE_SIDES
    val corner = across * BELL_HANDLE_CORNER
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
    drawPath(path, color, style = HandleStroke)
    drawCircle(color, Rig.BELL, body)
    // Lower depth rim and upper-left specular highlight give the iron sphere clear form at any size.
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

private fun DrawScope.drawNearLeg(figure: Figure, color: Color, path: Path) =
    drawTaperedLeg(figure.hip, figure.knee, figure.ankle, figure.heel, figure.toe, color, path)

private val HandleStroke = Stroke(Rig.HANDLE, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val PlateRimStroke = Stroke(Rig.PLATE_RIM)
private val PlateInnerStroke = Stroke(PLATE_INNER_STROKE)
private val BellDetailStroke = Stroke(BELL_SHADE_STROKE, cap = StrokeCap.Round)
private val BellHighlightStroke = Stroke(BELL_HIGHLIGHT_STROKE, cap = StrokeCap.Round)

private const val HALF = 0.5f

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
private const val FLOOR_MARGIN = 18f
private const val FLOOR_EDGE_INSET = 8f

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
