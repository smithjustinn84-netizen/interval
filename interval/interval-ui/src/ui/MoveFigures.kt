package dev.justinsmith.interval.ui

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.justinsmith.interval.timer.Move
import dev.justinsmith.interval.ui.resources.Res
import dev.justinsmith.interval.ui.resources.move_figure_cd
import dev.justinsmith.interval.ui.resources.move_show_how
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.ui.demo.ExerciseBand
import dev.justinsmith.lift.ui.demo.ExerciseThumb
import dev.justinsmith.lift.ui.demo.rep
import org.jetbrains.compose.resources.stringResource

// A flow's move shown by Lift's unified kinematic rep, where the move is one of Lift's exercises.

/** The Lift built-in [Move.exercise] names, when it is one this build knows; null for a move with none. */
val Move.builtIn: BuiltInExercise? get() = exercise?.let { Exercise.parse(it) as? BuiltInExercise }

/** The unified kinematic rep showing how the move goes; null for a move with none. */
val Move.rep: ExerciseRep? get() = builtIn?.rep

/** A still of [rep] at the end of a list row, as each figure's row in an exercise's editor shows. */
@Composable
fun MoveThumb(rep: ExerciseRep, modifier: Modifier = Modifier) = ExerciseThumb(rep, modifier)

/**
 * [move]'s figure doing it over and over, a band across the screen: shares the same [ExerciseBand] as Lift's
 * session card. Holds still while the workout is [paused] and runs on with it; a tap opens the move's page
 * ([onOpen]). Drawn in [colors] on [fill]: the workout screen's, over the ring.
 */
@Composable
fun MoveFigureBand(
    move: Move,
    rep: ExerciseRep,
    paused: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    fill: Color = IntervalTheme.palette.surfaceVariant,
    border: Color = IntervalTheme.palette.line,
    colors: ExerciseDemoColors = ExerciseDemoColors.theme(),
) {
    ExerciseBand(
        rep = rep,
        contentDescription = stringResource(Res.string.move_figure_cd, move.name),
        onClick = onOpen,
        onClickLabel = stringResource(Res.string.move_show_how),
        modifier = modifier.border(Sizes.hairline, border, Corners.radius18),
        paused = paused,
        shape = Corners.radius18,
        fill = fill,
        colors = colors,
    )
}
