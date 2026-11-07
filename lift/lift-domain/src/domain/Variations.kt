package dev.justinsmith.lift.domain

// Same but different: before a bell lift goes to a much heavier bell, it moves once to a harder lift at the bell it
// has, the stimulus new and the bell the same (docs/kettlebell-coaching.md, section 4).

/** A lift to move to at the same bell: [exercise], with two bells when [double]. */
data class Variation(val exercise: Exercise, val double: Boolean)

/**
 * The harder lift each of these moves to with one bell: the same pattern, harder at the same bell. One hand takes
 * the whole bell (and does a side each); the snatch throws it overhead; the strict press loses the legs' drive; the
 * front squat racks it on one side.
 */
val harderLifts: Map<BuiltInExercise, BuiltInExercise> = mapOf(
    BuiltInExercise.TwoHandSwing to BuiltInExercise.OneHandSwing,
    BuiltInExercise.HighPull to BuiltInExercise.Snatch,
    BuiltInExercise.HalfSnatch to BuiltInExercise.Snatch,
    BuiltInExercise.PushPress to BuiltInExercise.Press,
    BuiltInExercise.GobletSquat to BuiltInExercise.FrontSquat,
)

/**
 * The harder lift [exercise], done with two bells or one ([double]) at [kg] a bell, moves to at the same bell: with
 * one bell, its harder lift ([harderLifts]); else, a lift that can be done with two bells, the same with two, once
 * [pairs] (the bells owned two of) has [kg]. Null for a lift the user made, any but a counted bell lift, or none.
 */
fun variationOf(exercise: Exercise, double: Boolean, kg: Double, pairs: Set<Double>): Variation? {
    if (exercise !is BuiltInExercise || exercise.equipment != Equipment.Kettlebell) return null
    if (exercise.measure != Measure.Reps || double) return null
    harderLifts[exercise]?.let { return Variation(it, double = false) }
    return Variation(exercise, double = true).takeIf { exercise.doubles == Doubles.Optional && kg in pairs }
}
