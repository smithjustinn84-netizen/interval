package dev.justinsmith.lift.domain

/**
 * The muscles as the Lift tab's balance groups them: few enough to name on a phone, each a set of [Muscle]s. In
 * the order the card lists them, the upper body's pushing and pulling, then the trunk, then the legs.
 */
enum class MuscleGroup(val muscles: Set<Muscle>) {
    Chest(setOf(Muscle.Chest)),
    Shoulders(setOf(Muscle.Shoulders)),
    Back(setOf(Muscle.Lats, Muscle.UpperBack, Muscle.Traps, Muscle.RearDelts, Muscle.LowerBack)),
    Arms(setOf(Muscle.Biceps, Muscle.Triceps, Muscle.Forearms)),
    Core(setOf(Muscle.Abs, Muscle.Obliques, Muscle.HipFlexors)),

    /** The glutes, hamstrings and adductors: what a swing, a deadlift or a bridge works. */
    Hips(setOf(Muscle.Glutes, Muscle.Hamstrings, Muscle.Adductors)),

    /** The quads and calves: what a squat, a lunge or a step-up works. */
    Legs(setOf(Muscle.Quads, Muscle.Calves)),
}

/** The groups a [Focus] covers, which a custom exercise counts toward: it names where it works, not which muscle. */
val Focus.groups: Set<MuscleGroup>
    get() = when (this) {
        Focus.UpperBody -> setOf(MuscleGroup.Chest, MuscleGroup.Shoulders, MuscleGroup.Back, MuscleGroup.Arms)
        Focus.LowerBody -> setOf(MuscleGroup.Hips, MuscleGroup.Legs)
        Focus.Core -> setOf(MuscleGroup.Core)
        Focus.FullBody -> MuscleGroup.entries.toSet()
    }

/** One group's sets: those that [worked] it most, and those it only [assisted] in. A set of one side counts half. */
data class MuscleLoad(val group: MuscleGroup, val worked: Double, val assisted: Double) {
    val total: Double get() = worked + assisted
}

/**
 * Sets per muscle group over some sessions, every group in [MuscleGroup]'s order: the Lift tab's balance over the
 * last four weeks.
 */
data class MuscleBalance(val loads: List<MuscleLoad>) {
    /** The groups no set worked or assisted in: the one line of advice the card gives. */
    val untrained: List<MuscleGroup> get() = loads.filter { it.total == 0.0 }.map { it.group }

    /** The most sets any group has, the top of the card's bars. */
    val max: Double get() = loads.maxOf { it.total }

    companion object {
        /**
         * The balance of [sessions], or null with fewer than two to tell a balance from: the caller picks which
         * sessions (the last four weeks' finished ones). A set counts once in each group: as worked where the
         * exercise works a muscle of it most, else as assisted where it works one also. A set of one side counts
         * half, so a left and a right weigh as one set with both hands. A custom exercise names only its
         * [Focus], so its sets count as assisted in each group of it; one this build doesn't know counts nowhere.
         */
        fun of(sessions: List<LiftSession>): MuscleBalance? {
            if (sessions.size < MIN_SESSIONS) return null
            return ofSets(sessions.flatMap { it.sets }.map { it.exercise to it.side })
        }

        /**
         * The balance of [sets], each an exercise and the side it was done on (null for both, or none), counted as
         * [of] counts a logged set: what a program plans as well as what was done.
         */
        fun ofSets(sets: List<Pair<Exercise, Side?>>): MuscleBalance {
            val worked = MuscleGroup.entries.associateWith { 0.0 }.toMutableMap()
            val assisted = MuscleGroup.entries.associateWith { 0.0 }.toMutableMap()
            sets.forEach { (exercise, side) ->
                val weight = if (side == null) 1.0 else HALF
                val (most, also) = groupsOf(exercise)
                most.forEach { worked[it] = worked.getValue(it) + weight }
                (also - most).forEach { assisted[it] = assisted.getValue(it) + weight }
            }
            return MuscleBalance(
                MuscleGroup.entries.map { MuscleLoad(it, worked.getValue(it), assisted.getValue(it)) },
            )
        }

        /** The groups [exercise] works most, and those it works also. */
        private fun groupsOf(exercise: Exercise): Pair<Set<MuscleGroup>, Set<MuscleGroup>> = when (exercise) {
            is BuiltInExercise -> exercise.muscles.let { muscles ->
                groupsWith(muscles.primary) to groupsWith(muscles.also)
            }

            is CustomExercise -> emptySet<MuscleGroup>() to exercise.focus.groups

            is UnknownExercise -> emptySet<MuscleGroup>() to emptySet()
        }

        private fun groupsWith(muscles: List<Muscle>): Set<MuscleGroup> =
            MuscleGroup.entries.filterTo(mutableSetOf()) { group -> muscles.any { it in group.muscles } }

        private const val MIN_SESSIONS = 2
        private const val HALF = 0.5
    }
}

/**
 * What a week of this program plans, by muscle group: its days taken in turn for a week's [Program.daysPerWeek],
 * each of their planned sets counted as [MuscleBalance.of] counts a set done.
 */
fun Program.weekBalance(): MuscleBalance =
    MuscleBalance.ofSets((1..daysPerWeek).flatMap { day(it).plannedSets() }.map { it.exercise to it.side })

/** How many days back the Lift tab's balance looks, today among them. */
const val BALANCE_DAYS = 28
