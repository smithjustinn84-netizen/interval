package dev.justinsmith.lift.ui.demo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onVisibilityChanged
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
 * is paused or dragged. [contentDescription] says what it shows, for a screen reader. It takes no frames while it is
 * wholly scrolled away ([showingPlayer]); without [tracksView] that is left to a card round it whose timeline and
 * cues follow the player too.
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
    tracksView: Boolean = true,
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
        if (tracksView) described.showingPlayer(player) else described,
        colors,
    )
}

/**
 * [demo] at [progress] (0 to 1) through a rep, read while drawing so a moving figure redraws without
 * recomposing. A still picture passes a constant. A moving one draws every frame, so the drawing makes nothing
 * it can keep: one path for every limb, the strokes and the trail's places held from frame to frame.
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
    Spacer(
        described.drawWithCache {
            val pen = FigurePen()
            onDrawBehind {
                val t = progress()
                pen.trailCount = demo.trailInto(t, pen.trail)
                drawFigure(demo, demo.figureAt(t), pen, colors.of(demo.phaseAt(t).beat), colors)
            }
        },
    )
}

/**
 * Tells [player] whether this is on screen, so it takes frames only while it is: any of it showing counts, and
 * one wholly scrolled away stops. On the figure itself, or on a card whose timeline and cues read the player too.
 */
fun Modifier.showingPlayer(player: DemoPlayer): Modifier =
    onVisibilityChanged(minFractionVisible = 0f) { player.inView = it }
