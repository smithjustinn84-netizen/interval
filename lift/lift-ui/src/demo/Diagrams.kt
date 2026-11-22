package dev.justinsmith.lift.ui.demo

import dev.justinsmith.interval.ui.ExerciseRep
import dev.justinsmith.interval.ui.KettlebellReps
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.CustomExercise
import dev.justinsmith.lift.domain.Exercise

/**
 * This exercise as a diagram from the side, with the path its bell takes ([ExerciseRep], drawn by core's
 * `ExerciseDiagram`): the flagship kettlebell lifts have one, and one of the user's own has that of the built-in
 * it names as its [CustomExercise.figure]. Null for a lift none is drawn for yet, which is shown by its figure
 * ([demo]) alone.
 */
val Exercise.diagram: ExerciseRep?
    get() = builtInDiagrams[(this as? CustomExercise)?.figure ?: this]

/**
 * The diagram of the exercise stored as [key], one of [custom] among them: null where the key names nothing this
 * build knows, or a lift with no diagram yet.
 */
fun diagramOf(key: String, custom: List<CustomExercise> = emptyList()): ExerciseRep? =
    Exercise.parse(key, custom)?.diagram

/** A swing with one hand is a swing with two, seen from the side. */
private val builtInDiagrams: Map<Exercise, ExerciseRep> = mapOf(
    BuiltInExercise.TwoHandSwing to KettlebellReps.Swing,
    BuiltInExercise.OneHandSwing to KettlebellReps.Swing,
    BuiltInExercise.Clean to KettlebellReps.Clean,
    BuiltInExercise.Snatch to KettlebellReps.Snatch,
    BuiltInExercise.Press to KettlebellReps.Press,
    BuiltInExercise.GobletSquat to KettlebellReps.GobletSquat,
    BuiltInExercise.GetUp to KettlebellReps.GetUp,
    BuiltInExercise.CleanAndPress to KettlebellReps.CleanAndPress,
)
