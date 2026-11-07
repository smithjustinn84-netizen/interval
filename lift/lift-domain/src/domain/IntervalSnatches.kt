package dev.justinsmith.lift.domain

/**
 * The snatches of one of the app's interval workouts (15:15 MVO2, or one of the user's own kettlebell presets that
 * switches hands), as the lifter counted them on its summary: about [perSet] a set at [bellKg], one set for each
 * work interval done, each ended at its moment in [setEnds]. [source] names the preset, null when none goes by its
 * shape now. Lift never stores them: they are read as sets ([intervalSets]) wherever the sets logged are counted.
 */
data class IntervalSnatches(val perSet: Int, val bellKg: Double, val setEnds: List<Long>, val source: String?)

/** The sets one interval workout's snatches come to, and the preset they were done on ([IntervalSnatches.source]). */
data class IntervalSetGroup(val source: String?, val sets: List<SetLog>)

/**
 * The sets of snatches the interval workouts' [snatches] come to, a group for each: one set a work interval, of the
 * count said at the bell, a hand at a time from the left, logged as the interval ended. Each has an id of its own
 * below zero, which no stored set has.
 */
fun intervalSets(snatches: List<IntervalSnatches>): List<IntervalSetGroup> {
    var id = 0L
    return snatches.map { workout ->
        val sets = workout.setEnds.mapIndexed { index, end ->
            val side = if (index % 2 == 0) Side.Left else Side.Right
            SetLog.of(--id, BuiltInExercise.Snatch, workout.bellKg, workout.perSet, side, end)
        }
        IntervalSetGroup(workout.source, sets)
    }
}

/** Every set of these groups, for what counts the sets logged whatever they were done in. */
val List<IntervalSetGroup>.sets: List<SetLog> get() = flatMap { it.sets }
