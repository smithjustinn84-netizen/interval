package dev.justinsmith.interval.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import dev.justinsmith.interval.timer.Move
import dev.justinsmith.interval.ui.resources.Res
import dev.justinsmith.interval.ui.resources.move_figure_cd
import dev.justinsmith.interval.ui.resources.move_show_how
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.ui.demo.ExerciseDemo
import dev.justinsmith.lift.ui.demo.ExerciseFigure
import dev.justinsmith.lift.ui.demo.FigureColors
import dev.justinsmith.lift.ui.demo.demo
import dev.justinsmith.lift.ui.demo.rememberDemoPlayer
import org.jetbrains.compose.resources.stringResource

// A flow's move shown by Lift's figure, where the move is one of Lift's exercises.

/** The Lift built-in [Move.exercise] names, when it is one this build knows; null for a move with none. */
val Move.builtIn: BuiltInExercise? get() = exercise?.let { Exercise.parse(it) as? BuiltInExercise }

/** The figure that shows how the move goes; null for a move with none (a stretch, a move typed in). */
val Move.demo: ExerciseDemo? get() = builtIn?.demo

/**
 * A still of [demo] at the end of a list row, where the figure holds its shape best: decoration beside the row's
 * words, which name the move, so a screen reader hears nothing of it.
 */
@Composable
fun MoveThumb(demo: ExerciseDemo, modifier: Modifier = Modifier) {
    ExerciseFigure(
        demo,
        progress = { demo.still },
        contentDescription = null,
        modifier = modifier
            .size(Sizes.figureThumbWidth, Sizes.figureThumbHeight)
            .clip(Corners.radius12)
            .background(IntervalTheme.palette.surface)
            .padding(Spacing.space4),
    )
}

/**
 * [move]'s figure doing it over and over, a band across the screen: as Lift's session card shows the next lift.
 * It holds still while the workout is [paused] and runs on with it; a tap opens the move's page ([onOpen]), where
 * the figure can be paused and read cue by cue. Drawn in [colors] on [fill]: the workout screen's, over the ring.
 */
@Composable
fun MoveFigureBand(
    move: Move,
    demo: ExerciseDemo,
    paused: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    fill: Color = IntervalTheme.palette.surfaceVariant,
    colors: FigureColors = FigureColors.theme(),
) {
    val player = rememberDemoPlayer(demo)
    LaunchedEffect(player, paused) {
        if (paused) player.pause() else player.play()
    }
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .fillMaxWidth()
            .height(Sizes.figureBand)
            .surfaceButton(
                interaction,
                shape = Corners.radius18,
                fill = fill,
                onClick = onOpen,
                onClickLabel = stringResource(Res.string.move_show_how),
            ),
    ) {
        ExerciseFigure(
            demo,
            player,
            contentDescription = stringResource(Res.string.move_figure_cd, move.name),
            modifier = Modifier.fillMaxSize().padding(Spacing.space8),
            colors = colors,
        )
    }
}
