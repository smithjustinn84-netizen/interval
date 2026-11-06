package dev.justinsmith.lift.ui.demo

import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.Exercise

/** The demo drawn for this exercise, or null for one not drawn yet. */
val Exercise.demo: ExerciseDemo?
    get() = builtInDemos[this]

private val builtInDemos: Map<Exercise, ExerciseDemo> by lazy {
    mapOf(
        BuiltInExercise.TwoHandSwing to TwoHandSwingDemo,
        BuiltInExercise.GobletSquat to GobletSquatDemo,
        BuiltInExercise.OneHandSwing to OneHandSwingDemo,
        BuiltInExercise.Deadlift to DeadliftDemo,
        BuiltInExercise.HighPull to HighPullDemo,
        BuiltInExercise.Snatch to SnatchDemo,
        BuiltInExercise.HalfSnatch to HalfSnatchDemo,
    )
}
