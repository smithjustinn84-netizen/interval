package dev.justinsmith.lift.ui.demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform

/** What a figure draws with, kept from frame to frame: the path each limb is traced on and the bell's trail. */
internal class FigurePen {
    val path = Path()
    val trail = FloatArray(2 * ExerciseDemo.TRAIL_STEPS)
    var trailCount = 0
}

/**
 * Draws [demo]'s [figure] fitted and centred in the canvas, its bell in [bell] with [pen]'s trail behind it. Back to
 * front: the bench, the far limbs, the trunk, then the near arm and leg in whichever order keeps a bell low
 * between the legs behind the near one and a bell held high in front of it. A bell circled behind the head
 * goes behind the trunk. A band tied to a post behind the figure runs behind it; any other is drawn over it.
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
        drawFloor(colors)
        // Indexed loops, here and below: a for-each would make an iterator every frame.
        val props = demo.propsBehind
        for (i in props.indices) drawProp(props[i], colors.farLeg)
        // A band tied behind the figure runs behind it; any other is held in front.
        drawBands(demo.bands, figure, bell, behind = true)
        drawTrail(pen, bell)
        val behind = Offset(-Rig.FAR_LEG_OFFSET, 0f)
        path.limb(
            figure.farToe + behind,
            figure.farHeel + behind,
            figure.farAnkle + behind,
            figure.farKnee + behind,
            figure.hip,
        )
        drawPath(path, colors.farLeg, style = FarLegStroke)
        if (demo.hold == Hold.NearHand || demo.hold == Hold.Pair || demo.hold == Hold.Free) {
            drawFarArm(figure, demo.hold == Hold.Pair, bell, colors.farLeg, path)
        }
        val behindHead = figure.bell.x < figure.shoulder.x && figure.bell.y < figure.shoulder.y
        if (behindHead) drawArmAndBell(figure, demo.hold, bell, colors.body, path)
        drawLine(colors.body, figure.hip, figure.shoulder, Rig.TRUNK, StrokeCap.Round)
        drawCircle(colors.body, Rig.HEAD, figure.head)
        when {
            behindHead -> drawNearLeg(figure, colors.body, path)

            figure.bell.y > figure.hip.y -> {
                drawArmAndBell(figure, demo.hold, bell, colors.body, path)
                drawNearLeg(figure, colors.body, path)
            }

            else -> {
                drawNearLeg(figure, colors.body, path)
                drawArmAndBell(figure, demo.hold, bell, colors.body, path)
            }
        }
        if (demo.hold == Hold.Bar) drawPlate(figure.hand, bell)
        drawBands(demo.bands, figure, bell, behind = false)
        // A bar to hang from goes over the fists that grip it.
        val rails = demo.rails
        for (i in rails.indices) drawProp(rails[i], colors.farLeg)
    }
}

private fun DrawScope.drawFloor(colors: FigureColors) {
    drawOval(
        colors.shadow,
        topLeft = Offset(Rig.ANKLE_X - Rig.SHADOW_WIDTH / 2, Rig.FLOOR + Rig.SHADOW_DROP - Rig.SHADOW_HEIGHT / 2),
        size = Size(Rig.SHADOW_WIDTH, Rig.SHADOW_HEIGHT),
    )
    drawLine(
        colors.floor,
        Offset(Rig.ANKLE_X - Rig.FLOOR_REACH, Rig.FLOOR),
        Offset(Rig.ANKLE_X + Rig.FLOOR_REACH_FORWARD, Rig.FLOOR),
        Rig.FLOOR_LINE,
    )
}

/** Where the bell has just been, as dots that grow and darken toward it: the wider apart, the faster it went. */
private fun DrawScope.drawTrail(pen: FigurePen, color: Color) {
    val count = pen.trailCount
    for (i in 0 until count) {
        val share = (i + 1f) / count
        val radius = Rig.TRAIL_DOT_OLDEST + (Rig.TRAIL_DOT_NEWEST - Rig.TRAIL_DOT_OLDEST) * share
        drawCircle(color.copy(alpha = TRAIL_ALPHA * share), radius, Offset(pen.trail[2 * i], pen.trail[2 * i + 1]))
    }
}

/**
 * The near arm and its bell. A bell that hangs past the grip goes under the arm, the fist over its handle; one
 * resting on the back of the forearm (the rack, a lockout) goes over it, on the side nearer the eye, unless
 * both hands hold it by the horns.
 */
private fun DrawScope.drawArmAndBell(figure: Figure, hold: Hold, bell: Color, body: Color, path: Path) {
    if (hold == Hold.Bar || hold == Hold.Free) {
        drawArm(figure.shoulder, figure.elbow, figure.hand, body, path)
        return
    }
    val resting = hold != Hold.BothHands && rests(figure.elbow, figure.hand, figure.bell)
    if (!resting) drawBell(figure.hand, figure.bell, bell, path)
    drawArm(figure.shoulder, figure.elbow, figure.hand, body, path)
    if (resting) drawBell(figure.hand, figure.bell, bell, path)
}

/** An arm from [shoulder] through [elbow] to the fist at [hand]. */
private fun DrawScope.drawArm(shoulder: Offset, elbow: Offset, hand: Offset, color: Color, path: Path) {
    path.limb(shoulder, elbow, hand)
    drawPath(path, color, style = ArmStroke)
    drawCircle(color, Rig.HAND, hand)
}

/** The far arm, behind the trunk, with its own bell in a [pair]. */
private fun DrawScope.drawFarArm(figure: Figure, pair: Boolean, bell: Color, far: Color, path: Path) {
    val behind = Offset(-Rig.FAR_ARM_OFFSET, 0f)
    if (pair) drawBell(figure.farHand, figure.farBell, bell, path)
    drawArm(figure.shoulder + behind, figure.farElbow, figure.farHand, far, path)
}

/**
 * A kettlebell held at [hand], its body at [body]: the body under the handle's bar, which sits across the grip
 * with a side running down to the body from each end.
 */
private fun DrawScope.drawBell(hand: Offset, body: Offset, color: Color, path: Path) {
    val along = (body - hand) / Rig.BELL_REACH
    val across = Offset(-along.y, along.x) * Rig.HANDLE_HALF
    val sides = along * Rig.HANDLE_SIDES
    path.limb(hand + across + sides, hand + across, hand - across, hand - across + sides)
    drawPath(path, color, style = HandleStroke)
    drawCircle(color, Rig.BELL, body)
}

/** A barbell seen end on, nearest of everything: the plate's rim round the sleeve at [sleeve]. */
private fun DrawScope.drawPlate(sleeve: Offset, color: Color) {
    drawCircle(color, Rig.PLATE - Rig.PLATE_RIM / 2, sleeve, style = PlateRimStroke)
    drawCircle(color, Rig.SLEEVE, sleeve)
}

private fun DrawScope.drawNearLeg(figure: Figure, color: Color, path: Path) {
    path.limb(figure.toe, figure.heel, figure.ankle, figure.knee, figure.hip)
    drawPath(path, color, style = LegStroke)
}

/** Traces a limb through its joints in order on this path, cleared first but keeping what it has allocated. */
private fun Path.limb(a: Offset, b: Offset, c: Offset) {
    rewind()
    moveTo(a.x, a.y)
    lineTo(b.x, b.y)
    lineTo(c.x, c.y)
}

private fun Path.limb(a: Offset, b: Offset, c: Offset, d: Offset) {
    limb(a, b, c)
    lineTo(d.x, d.y)
}

private fun Path.limb(a: Offset, b: Offset, c: Offset, d: Offset, e: Offset) {
    limb(a, b, c, d)
    lineTo(e.x, e.y)
}

/** A limb's stroke, in [Rig] units: the drawing is scaled to the canvas, so these hold for every size. */
private fun limbStroke(width: Float) = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round)

private val LegStroke = limbStroke(Rig.LEG)
private val FarLegStroke = limbStroke(Rig.FAR_LEG)
private val ArmStroke = limbStroke(Rig.ARM)
private val HandleStroke = limbStroke(Rig.HANDLE)
private val PlateRimStroke = Stroke(Rig.PLATE_RIM)

/** A trail's newest dot is this strong; the older ones fade from it. */
private const val TRAIL_ALPHA = 0.6f
