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
        BuiltInExercise.Clean to CleanDemo,
        BuiltInExercise.Press to PressDemo,
        BuiltInExercise.BottomsUpPress to BottomsUpPressDemo,
        BuiltInExercise.PushPress to PushPressDemo,
        BuiltInExercise.Jerk to JerkDemo,
        BuiltInExercise.FrontSquat to FrontSquatDemo,
        BuiltInExercise.Thruster to ThrusterDemo,
        BuiltInExercise.CleanAndPress to CleanAndPressDemo,
        BuiltInExercise.LongCycle to LongCycleDemo,
        BuiltInExercise.IronCardio to IronCardioDemo,
        BuiltInExercise.ArmorBuildingComplex to ArmorBuildingComplexDemo,
        BuiltInExercise.RackedLunge to RackedLungeDemo,
        BuiltInExercise.Row to RowDemo,
        BuiltInExercise.Windmill to WindmillDemo,
        BuiltInExercise.Halo to HaloDemo,
        BuiltInExercise.FarmersWalk to FarmersWalkDemo,
        BuiltInExercise.SuitcaseCarry to SuitcaseCarryDemo,
        BuiltInExercise.RackCarry to RackCarryDemo,
        BuiltInExercise.BottomsUpCarry to BottomsUpCarryDemo,
    )
}
