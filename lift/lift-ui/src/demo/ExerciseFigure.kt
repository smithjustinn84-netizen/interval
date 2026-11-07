package dev.justinsmith.lift.ui.demo

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.justinsmith.interval.platform.prefersReducedMotion
import dev.justinsmith.interval.ui.IntervalTheme

/** The colours a figure is drawn in: the body, the far leg, the floor and its shadow, and each beat's bell. */
data class FigureColors(
    val body: Color,
    val farLeg: Color,
    val floor: Color,
    val shadow: Color,
    val set: Color,
    val drive: Color,
    val ease: Color,
) {
    fun of(beat: DemoBeat): Color = when (beat) {
        DemoBeat.Set -> set
        DemoBeat.Drive -> drive
        DemoBeat.Ease -> ease
    }

    companion object {
        /** The theme's: the bell in Get ready's, Work's and Rest's marks, so a demo speaks the workout's colours. */
        @Composable
        fun theme(): FigureColors {
            val palette = IntervalTheme.palette
            return FigureColors(
                body = palette.text,
                farLeg = palette.textDim,
                floor = palette.line,
                shadow = palette.line,
                set = palette.prepMark,
                drive = palette.workMark,
                ease = palette.restMark,
            )
        }
    }
}

/**
 * [demo] doing its exercise over and over, a rep each [ExerciseDemo.secondsPerRep], while [playing]. Paused, it
 * holds where it is; with reduced motion on it stands still at [ExerciseDemo.still]. [contentDescription] says
 * what it shows, for a screen reader.
 */
@Composable
fun ExerciseFigure(
    demo: ExerciseDemo,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
    colors: FigureColors = FigureColors.theme(),
) {
    val progress = rememberRepClock(demo, playing)
    ExerciseFigure(demo, { progress.floatValue }, contentDescription, modifier, colors)
}

/**
 * [demo] at [progress] (0 to 1) through a rep, read while drawing so a moving figure redraws without
 * recomposing. A still picture passes a constant.
 */
@Composable
fun ExerciseFigure(
    demo: ExerciseDemo,
    progress: () -> Float,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    colors: FigureColors = FigureColors.theme(),
) {
    val described = if (contentDescription == null) {
        modifier
    } else {
        modifier.semantics { this.contentDescription = contentDescription }
    }
    Canvas(described) {
        val t = progress()
        drawFigure(demo, demo.figureAt(t), demo.trailAt(t), colors.of(demo.phaseAt(t).beat), colors)
    }
}

/**
 * How far through a rep [demo] is, advanced every frame while [playing]. The frames come from an infinite
 * animation, so UI tests (which cancel those) never wait on it. Held while paused and run on from there;
 * at [ExerciseDemo.still] with reduced motion on.
 */
@Composable
private fun rememberRepClock(demo: ExerciseDemo, playing: Boolean): FloatState {
    val progress = remember(demo) { mutableFloatStateOf(demo.still) }
    val still = prefersReducedMotion()
    LaunchedEffect(demo, still, playing) {
        if (still) progress.floatValue = demo.still
        if (still || !playing) return@LaunchedEffect
        val repMillis = demo.secondsPerRep * MILLIS_PER_SECOND
        val from = progress.floatValue
        val start = withInfiniteAnimationFrameMillis { it }
        while (true) {
            val elapsed = withInfiniteAnimationFrameMillis { it } - start
            progress.floatValue = (from + elapsed / repMillis) % 1f
        }
    }
    return progress
}

/**
 * Draws [demo]'s [figure] fitted and centred in the canvas, its bell in [bell] with [trail] behind it. Back to
 * front: the bench, the far limbs, the trunk, then the near arm and leg in whichever order keeps a bell low
 * between the legs behind the near one and a bell held high in front of it. A bell circled behind the head
 * goes behind the trunk.
 */
private fun DrawScope.drawFigure(
    demo: ExerciseDemo,
    figure: Figure,
    trail: List<Offset>,
    bell: Color,
    colors: FigureColors,
) {
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
        demo.props.filter { it !is Rail }.forEach { drawProp(it, colors.farLeg) }
        drawTrail(trail, bell)
        val behind = Offset(-Rig.FAR_LEG_OFFSET, 0f)
        val farLeg = listOf(figure.farToe + behind, figure.farHeel + behind, figure.farAnkle + behind)
        drawLimb(farLeg + listOf(figure.farKnee + behind, figure.hip), colors.farLeg, Rig.FAR_LEG)
        if (demo.hold == Hold.NearHand || demo.hold == Hold.Pair || demo.hold == Hold.Free) {
            drawFarArm(figure, demo.hold == Hold.Pair, bell, colors.farLeg)
        }
        val behindHead = figure.bell.x < figure.shoulder.x && figure.bell.y < figure.shoulder.y
        if (behindHead) drawArmAndBell(figure, demo.hold, bell, colors.body)
        drawLine(colors.body, figure.hip, figure.shoulder, Rig.TRUNK, StrokeCap.Round)
        drawCircle(colors.body, Rig.HEAD, figure.head)
        when {
            behindHead -> drawNearLeg(figure, colors.body)

            figure.bell.y > figure.hip.y -> {
                drawArmAndBell(figure, demo.hold, bell, colors.body)
                drawNearLeg(figure, colors.body)
            }

            else -> {
                drawNearLeg(figure, colors.body)
                drawArmAndBell(figure, demo.hold, bell, colors.body)
            }
        }
        if (demo.hold == Hold.Bar) drawPlate(figure.hand, bell)
        // A bar to hang from goes over the fists that grip it.
        demo.props.filterIsInstance<Rail>().forEach { drawProp(it, colors.farLeg) }
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
private fun DrawScope.drawTrail(trail: List<Offset>, color: Color) {
    trail.forEachIndexed { i, place ->
        val share = (i + 1f) / trail.size
        val radius = Rig.TRAIL_DOT_OLDEST + (Rig.TRAIL_DOT_NEWEST - Rig.TRAIL_DOT_OLDEST) * share
        drawCircle(color.copy(alpha = TRAIL_ALPHA * share), radius, place)
    }
}

/**
 * The near arm and its bell. A bell that hangs past the grip goes under the arm, the fist over its handle; one
 * resting on the back of the forearm (the rack, a lockout) goes over it, on the side nearer the eye, unless
 * both hands hold it by the horns.
 */
private fun DrawScope.drawArmAndBell(figure: Figure, hold: Hold, bell: Color, body: Color) {
    if (hold == Hold.Bar || hold == Hold.Free) {
        drawLimb(listOf(figure.shoulder, figure.elbow, figure.hand), body, Rig.ARM)
        drawCircle(body, Rig.HAND, figure.hand)
        return
    }
    val resting = hold != Hold.BothHands && rests(figure.elbow, figure.hand, figure.bell)
    if (!resting) drawBell(figure.hand, figure.bell, bell)
    drawLimb(listOf(figure.shoulder, figure.elbow, figure.hand), body, Rig.ARM)
    drawCircle(body, Rig.HAND, figure.hand)
    if (resting) drawBell(figure.hand, figure.bell, bell)
}

/** The far arm, behind the trunk, with its own bell in a [pair]. */
private fun DrawScope.drawFarArm(figure: Figure, pair: Boolean, bell: Color, far: Color) {
    val behind = Offset(-Rig.FAR_ARM_OFFSET, 0f)
    if (pair) drawBell(figure.farHand, figure.farBell, bell)
    drawLimb(listOf(figure.shoulder + behind, figure.farElbow, figure.farHand), far, Rig.ARM)
    drawCircle(far, Rig.HAND, figure.farHand)
}

/**
 * A kettlebell held at [hand], its body at [body]: the body under the handle's bar, which sits across the grip
 * with a side running down to the body from each end.
 */
private fun DrawScope.drawBell(hand: Offset, body: Offset, color: Color) {
    val along = (body - hand) / Rig.BELL_REACH
    val across = Offset(-along.y, along.x) * Rig.HANDLE_HALF
    val sides = along * Rig.HANDLE_SIDES
    drawLimb(listOf(hand + across + sides, hand + across, hand - across, hand - across + sides), color, Rig.HANDLE)
    drawCircle(color, Rig.BELL, body)
}

/** A barbell seen end on, nearest of everything: the plate's rim round the sleeve at [sleeve]. */
private fun DrawScope.drawPlate(sleeve: Offset, color: Color) {
    drawCircle(color, Rig.PLATE - Rig.PLATE_RIM / 2, sleeve, style = Stroke(Rig.PLATE_RIM))
    drawCircle(color, Rig.SLEEVE, sleeve)
}

/** A prop behind the figure: a bench's top and legs, a wall, a bar seen end on. */
private fun DrawScope.drawProp(prop: Prop, color: Color) = when (prop) {
    is Bench -> drawBench(prop, color)
    is Wall -> drawLine(color, Offset(prop.x, Rig.FLOOR), Offset(prop.x, Rig.view.top), Rig.WALL)
    is Rail -> drawCircle(color, Rig.RAIL, prop.at)
}

private fun DrawScope.drawBench(bench: Bench, color: Color) {
    val top = Rig.FLOOR - bench.height
    drawLine(color, Offset(bench.from, top), Offset(bench.to, top), Rig.BENCH_TOP, StrokeCap.Round)
    listOf(bench.from, bench.to).forEach { x ->
        drawLine(color, Offset(x, top), Offset(x, Rig.FLOOR), Rig.BENCH_LEG)
    }
}

/** Whether a bell at [bell] lies back along the forearm from [elbow] to [hand], rather than past the grip. */
private fun rests(elbow: Offset, hand: Offset, bell: Offset): Boolean {
    val forearm = hand - elbow
    val reach = bell - hand
    return forearm.x * reach.x + forearm.y * reach.y < 0f
}

private fun DrawScope.drawNearLeg(figure: Figure, color: Color) =
    drawLimb(listOf(figure.toe, figure.heel, figure.ankle, figure.knee, figure.hip), color, Rig.LEG)

private fun DrawScope.drawLimb(joints: List<Offset>, color: Color, width: Float) {
    val path = Path().apply {
        moveTo(joints.first().x, joints.first().y)
        joints.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private const val MILLIS_PER_SECOND = 1000f

/** A trail's newest dot is this strong; the older ones fade from it. */
private const val TRAIL_ALPHA = 0.6f
