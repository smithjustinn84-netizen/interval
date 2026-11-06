package dev.justinsmith.interval.mobility

import androidx.compose.runtime.Composable
import dev.justinsmith.interval.timer.Move
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.Equipment
import dev.justinsmith.lift.domain.Measure
import dev.justinsmith.lift.ui.cues
import dev.justinsmith.lift.ui.exerciseName
import org.jetbrains.compose.resources.stringArrayResource

// The library's moves a flow can take: the mat's, and the holds that need nothing but the floor.

/** The timed holds done with nothing in hand, then the mat moves, as the library lists them. */
internal val libraryMoves: List<BuiltInExercise> = BuiltInExercise.entries.filter {
    it.equipment == Equipment.Mat || (it.equipment == Equipment.Bodyweight && it.measure == Measure.Time)
}

/** How long a move from the library starts out. */
private const val LIBRARY_MOVE_SECONDS = 30

/**
 * [exercise] as a move of a flow, in the words of the moment: its name and the first line of how it is done,
 * copied, so the flow keeps them as its own (and can say them aloud). Done a side at a time when the lift is; a
 * hold is held still, a mat move moved through.
 */
@Composable
internal fun libraryMove(exercise: BuiltInExercise): Move = Move(
    name = exerciseName(exercise).take(Move.NAME_MAX_LENGTH),
    seconds = LIBRARY_MOVE_SECONDS,
    cue = stringArrayResource(exercise.cues()).firstOrNull().orEmpty().take(Move.CUE_MAX_LENGTH),
    sided = exercise.perSide,
    held = exercise.measure == Measure.Time,
)
