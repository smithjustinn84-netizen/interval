package dev.justinsmith.lift.ui.demo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.semantics.stateDescription
import dev.justinsmith.interval.platform.prefersReducedMotion
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.lift.ui.resources.Res
import dev.justinsmith.lift.ui.resources.figure_bowing
import dev.justinsmith.lift.ui.resources.figure_raising_bell
import kotlinx.coroutines.flow.filterNotNull
import org.jetbrains.compose.resources.stringResource

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

        /**
         * The workout screen's: Gym dark, the same in both themes, the body in the clock's colour and the beats in
         * the phases' own, so a flow's move over the ring reads as the ring does.
         */
        @Composable
        fun workout(): FigureColors {
            val colors = IntervalTheme.workout
            return FigureColors(
                body = colors.onGround,
                farLeg = colors.onGroundDim,
                floor = colors.control,
                shadow = colors.control,
                set = colors.prep,
                drive = colors.work,
                ease = colors.rest,
            )
        }
    }
}

/**
 * [demo] where [player] has it: running on a rep each [ExerciseDemo.secondsPerRep] while it plays, held where it
 * is paused or dragged. [contentDescription] says what it shows, for a screen reader.
 *
 * Each new [celebrating] (anything but null: a record set's id) has the figure mark it once, from wherever it is:
 * it raises what it holds or takes a bow ([ExerciseDemo.celebrating]) over [Celebration.MILLIS], the player held
 * meanwhile, then goes on with its rep where it left it. One standing as the figure appears counts too, so a card
 * that comes in on the record's moment joins in. Nothing under reduced motion, or for a figure not on its feet.
 */
@Composable
fun ExerciseFigure(
    demo: ExerciseDemo,
    player: DemoPlayer,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    colors: FigureColors = FigureColors.theme(),
    celebrating: Any? = null,
) {
    val key by rememberUpdatedState(celebrating)
    val still = prefersReducedMotion()
    var interlude by remember { mutableStateOf<ExerciseDemo?>(null) }
    val run = remember { Animatable(0f) }
    LaunchedEffect(player, still) {
        if (still) return@LaunchedEffect
        snapshotFlow { key }.filterNotNull().collect {
            val gesture = demo.celebrating(player.progress) ?: return@collect
            val wasPlaying = player.playing
            player.pause()
            interlude = gesture
            try {
                run.snapTo(0f)
                run.animateTo(1f, tween(Celebration.MILLIS, easing = LinearEasing))
            } finally {
                interlude = null
                if (wasPlaying) player.play()
            }
        }
    }
    val celebration = interlude?.celebration
    val described = if (celebration == null) {
        modifier
    } else {
        val words = stringResource(
            when (celebration) {
                Celebration.RaisedBell -> Res.string.figure_raising_bell
                Celebration.Bow -> Res.string.figure_bowing
            },
        )
        modifier.semantics { stateDescription = words }
    }
    ExerciseFigure(
        interlude ?: demo,
        { if (interlude == null) player.progress else run.value },
        contentDescription,
        described,
        colors,
    )
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
 * Draws [demo]'s [figure] fitted and centred in the canvas, its bell in [bell] with [trail] behind it. Back to
 * front: the bench, the far limbs, the trunk, then the near arm and leg in whichever order keeps a bell low
 * between the legs behind the near one and a bell held high in front of it. A bell circled behind the head
 * goes behind the trunk. A band tied to a post behind the figure runs behind it; any other is drawn over it.
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
        val bands = demo.props.filterIsInstance<Band>()
        // A band tied behind the figure runs behind it; any other is held in front.
        val (behindBands, frontBands) = bands.partition { band ->
            listOf(band.from, band.to).any { it is BandEnd.Post && it.at.x < figure.hip.x }
        }
        behindBands.forEach { drawBand(it, figure, bell) }
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
        frontBands.forEach { drawBand(it, figure, bell) }
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

    is Band -> listOf(prop.from, prop.to).filterIsInstance<BandEnd.Post>().forEach { post ->
        drawLine(color, Offset(post.at.x, Rig.FLOOR), post.at - Offset(0f, Rig.POST * 2), Rig.POST, StrokeCap.Round)
    }
}

/** A band stretched between its two ends, where [figure] has them now. */
private fun DrawScope.drawBand(band: Band, figure: Figure, color: Color) =
    drawLine(color, figure.place(band.from), figure.place(band.to), Rig.BAND, StrokeCap.Round)

private fun DrawScope.drawBench(bench: Bench, color: Color) {
    val top = Rig.FLOOR - bench.height
    drawLine(color, Offset(bench.from, top), Offset(bench.to, top), Rig.BENCH_TOP, StrokeCap.Round)
    listOf(bench.from, bench.to).forEach { x ->
        drawLine(color, Offset(x, top), Offset(x, Rig.FLOOR), Rig.BENCH_LEG)
    }
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

/** A trail's newest dot is this strong; the older ones fade from it. */
private const val TRAIL_ALPHA = 0.6f
