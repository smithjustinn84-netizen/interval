package dev.justinsmith.bells.domain

/**
 * What the exercise library narrows to: any of the choices within a facet, and every facet together. An
 * empty facet lets everything through.
 */
data class ExerciseFilters(
    val equipment: Set<Equipment> = emptySet(),
    val focus: Set<Focus> = emptySet(),
    val measure: Set<Measure> = emptySet(),
    /** Only the exercises the user added. */
    val yoursOnly: Boolean = false,
) {
    /** How many choices are set besides the equipment, which the library keeps in a row of its own. */
    val moreCount: Int get() = focus.size + measure.size + (if (yoursOnly) 1 else 0)

    val isEmpty: Boolean get() = this == ExerciseFilters()

    fun matches(exercise: Exercise): Boolean =
        (equipment.isEmpty() || exercise.equipment in equipment) &&
            (focus.isEmpty() || exercise.focus in focus) &&
            (measure.isEmpty() || exercise.measure in measure) &&
            (!yoursOnly || exercise is CustomExercise)
}

/** What the library and the Log sheet offer: every built-in, then those of [made] not archived. */
fun offeredExercises(made: List<CustomExercise>): List<Exercise> =
    BuiltInExercise.entries + made.filterNot { it.archived }

/** This set with [item] taken out if it is in, or put in if it isn't. */
fun <T> Set<T>.toggled(item: T): Set<T> = if (item in this) this - item else this + item

/**
 * Whether [name] answers [query]: the query's letters and digits, in order, somewhere in the name's, so case,
 * spaces and hyphens never matter ("pushup" finds "Push-up", "split sq" "Bulgarian split squat"). An empty
 * query answers everything.
 */
fun nameMatches(name: String, query: String): Boolean = compact(name).contains(compact(query))

private fun compact(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }

/** Up to [limit] of [offered], the last done first, by [sets] in the order they were logged. */
fun recentExercises(sets: List<SetLog>, offered: List<Exercise>, limit: Int = RECENT_EXERCISES): List<Exercise> {
    val byKey = offered.associateBy { it.key }
    return sets.asReversed().asSequence().mapNotNull { byKey[it.exercise.key] }.distinct().take(limit).toList()
}

/** How many exercises the library lists as recent. */
const val RECENT_EXERCISES = 4

/** How the user's names are compared: trimmed, case ignored. */
fun exerciseNameKey(name: String): String = name.trim().lowercase()

/** The names taken among [made], the exercise [uid] being changed aside: no two of the user's share one. */
fun takenNames(made: List<CustomExercise>, uid: String?): Set<String> =
    made.filter { it.uid != uid }.map { exerciseNameKey(it.name) }.toSet()

/** Whether this can be saved as [name]: a name, and not one [taken] already. */
fun ExerciseDraft.canSave(name: String, taken: Set<String>): Boolean =
    name.isNotBlank() && exerciseNameKey(name) !in taken

/** The longest name and cues an exercise of the user's takes. */
const val MAX_EXERCISE_NAME = 40
const val MAX_EXERCISE_CUES = 120
