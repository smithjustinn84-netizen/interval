package dev.justinsmith.lift.domain

/**
 * How much a single [Muscle] was trained: [worked] sets where it was a primary target, and [assisted] sets where it
 * was secondary. A set of one side counts half.
 */
data class MuscleEmphasis(val worked: Double = 0.0, val assisted: Double = 0.0) {
    val total: Double get() = worked + assisted
}

/**
 * Per-muscle training emphasis for drawing the anatomical body map: every trained [Muscle] mapped to its
 * [MuscleEmphasis], with normalized intensity helpers for shading.
 */
data class MuscleMap(val muscles: Map<Muscle, MuscleEmphasis>) {
    /** The highest worked set count on any single muscle, used to scale primary highlight intensity. */
    val maxWorked: Double get() = muscles.values.maxOfOrNull { it.worked } ?: 0.0

    /** The highest assisted set count on any muscle that was only assisted. */
    val maxAssisted: Double
        get() = muscles.values
            .filter { it.worked == 0.0 }
            .maxOfOrNull { it.assisted } ?: 0.0

    /** The highest total load on any single muscle. */
    val max: Double get() = muscles.values.maxOfOrNull { it.total } ?: 0.0

    /** True when no muscle has any worked or assisted sets. */
    val isEmpty: Boolean get() = muscles.values.all { it.total == 0.0 }

    /** Muscles worked as primary targets, ordered by worked sets descending (preserving order on ties). */
    val primary: List<Muscle>
        get() = muscles.entries
            .filter { it.value.worked > 0.0 }
            .sortedByDescending { it.value.worked }
            .map { it.key }

    /** Muscles worked only as assisted (no primary sets), ordered by assisted sets descending. */
    val assistedOnly: List<Muscle>
        get() = muscles.entries
            .filter { it.value.worked == 0.0 && it.value.assisted > 0.0 }
            .sortedByDescending { it.value.assisted }
            .map { it.key }

    /** Normalized primary intensity in `0f..1f` for [muscle], or `0f` if it had no primary sets. */
    fun primaryIntensity(muscle: Muscle): Float {
        val w = muscles[muscle]?.worked ?: 0.0
        val top = maxWorked
        if (w <= 0.0 || top <= 0.0) return 0f
        return (w / top).toFloat().coerceIn(0f, 1f)
    }

    /** Normalized assisted intensity in `0f..1f` for [muscle] when only assisted, or `0f` otherwise. */
    fun assistedIntensity(muscle: Muscle): Float {
        val entry = muscles[muscle] ?: return 0f
        if (entry.worked > 0.0 || entry.assisted <= 0.0) return 0f
        val top = maxAssisted
        if (top <= 0.0) return 0f
        return (entry.assisted / top).toFloat().coerceIn(0f, 1f)
    }

    companion object {
        val Empty = MuscleMap(emptyMap())

        /** A single built-in exercise's muscles: primary at full worked (1.0), also at full assisted (1.0). */
        fun of(muscles: Muscles): MuscleMap = MuscleMap(
            buildMap {
                muscles.primary.forEach { put(it, MuscleEmphasis(worked = 1.0)) }
                muscles.also.forEach { if (it !in this) put(it, MuscleEmphasis(assisted = 1.0)) }
            },
        )

        /** A custom exercise's focus: every muscle in its focus's groups as assisted (1.0). */
        fun of(focus: Focus): MuscleMap = MuscleMap(
            focus.groups.flatMap { it.muscles }.associateWith { MuscleEmphasis(assisted = 1.0) },
        )

        /** Any exercise: built-in by its exact muscles, custom by its focus, unknown as empty. */
        fun of(exercise: Exercise): MuscleMap = when (exercise) {
            is BuiltInExercise -> of(exercise.muscles)
            is CustomExercise -> of(exercise.focus)
            is UnknownExercise -> Empty
        }

        /**
         * Per-muscle emphasis across [sets], each an exercise and the side it was done on (null for both or none).
         * A unilateral set counts half, matching [MuscleBalance.ofSets].
         */
        fun ofSets(sets: List<Pair<Exercise, Side?>>): MuscleMap {
            val worked = mutableMapOf<Muscle, Double>()
            val assisted = mutableMapOf<Muscle, Double>()
            sets.forEach { (exercise, side) ->
                val weight = if (side == null) 1.0 else HALF
                when (exercise) {
                    is BuiltInExercise -> {
                        exercise.muscles.primary.forEach { worked[it] = (worked[it] ?: 0.0) + weight }
                        exercise.muscles.also.forEach { assisted[it] = (assisted[it] ?: 0.0) + weight }
                    }

                    is CustomExercise -> {
                        exercise.focus.groups.flatMap { it.muscles }.forEach {
                            assisted[it] = (assisted[it] ?: 0.0) + weight
                        }
                    }

                    is UnknownExercise -> Unit
                }
            }
            val all = worked.keys + assisted.keys
            return MuscleMap(
                all.associateWith { m -> MuscleEmphasis(worked = worked[m] ?: 0.0, assisted = assisted[m] ?: 0.0) },
            )
        }

        /** Per-muscle emphasis for a single [session]. */
        fun of(session: LiftSession): MuscleMap = ofSets(session.sets.map { it.exercise to it.side })

        /** Per-muscle emphasis across [sessions]. */
        fun of(sessions: List<LiftSession>): MuscleMap =
            ofSets(sessions.flatMap { it.sets }.map { it.exercise to it.side })

        private const val HALF = 0.5
    }
}
