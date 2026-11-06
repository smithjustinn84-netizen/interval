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
        drawFigure(demo.figureAt(t), demo.hold, colors.of(demo.phaseAt(t).beat), colors)
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
 * Draws [figure] fitted and centred in the canvas, its bell in [bell]: back to front, so a swung bell passes
 * between the legs.
 */
private fun DrawScope.drawFigure(figure: Figure, hold: BellHold, bell: Color, colors: FigureColors) {
    val view = Rig.view
    val scale = minOf(size.width / view.width, size.height / view.height)
    val left = (size.width - view.width * scale) / 2
    val top = (size.height - view.height * scale) / 2
    withTransform({
        translate(left, top)
        scale(scale, scale, pivot = Offset.Zero)
        translate(-view.left, -view.top)
    }) {
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
        val behind = Offset(-Rig.FAR_LEG_OFFSET, 0f)
        val farLeg = listOf(figure.toe + behind, figure.ankle + behind, figure.knee + behind, figure.hip)
        drawLimb(farLeg, colors.farLeg, Rig.FAR_LEG)
        drawLine(colors.body, figure.hip, figure.shoulder, Rig.TRUNK, StrokeCap.Round)
        drawCircle(colors.body, Rig.HEAD, figure.head)
        when (hold) {
            BellHold.Swung -> {
                drawLimb(listOf(figure.shoulder, figure.elbow, figure.hand), colors.body, Rig.ARM)
                drawCircle(bell, Rig.GRIP, figure.hand, style = Stroke(Rig.GRIP_STROKE))
                drawCircle(bell, Rig.BELL, figure.bell)
                drawNearLeg(figure, colors.body)
            }

            BellHold.AtChest -> {
                drawNearLeg(figure, colors.body)
                drawCircle(bell, Rig.CHEST_BELL, figure.bell)
                drawLimb(listOf(figure.shoulder, figure.elbow, figure.hand), colors.body, Rig.ARM)
            }
        }
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

private const val MILLIS_PER_SECOND = 1000f
