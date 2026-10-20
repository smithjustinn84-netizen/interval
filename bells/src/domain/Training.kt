package dev.justinsmith.bells.domain

/** The lifts Bells logs. [perSide] ones are logged one side at a time. */
enum class Exercise(val perSide: Boolean) {
    TwoHandSwing(perSide = false),
    OneHandSwing(perSide = true),
    Clean(perSide = true),
    Press(perSide = true),
    Snatch(perSide = true),
    GetUp(perSide = true),
    GobletSquat(perSide = false),
    Row(perSide = true),
    Jerk(perSide = true),
    LongCycle(perSide = true),
    Halo(perSide = false),
    ;

    companion object {
        /** By [name], as stored; an unknown name (from a newer build) reads as null. */
        fun named(name: String): Exercise? = entries.firstOrNull { it.name == name }
    }
}

/** The side a one-sided set was done on. */
enum class Side { Left, Right }

/** One logged set: [reps] of [exercise] with a [bellKg] bell, on [side] for a one-sided lift. */
data class SetLog(
    val id: Long,
    val exercise: Exercise,
    val bellKg: Double,
    val reps: Int,
    val side: Side?,
    val loggedAtEpochMillis: Long,
) {
    /** The weight moved: the bell times the reps. */
    val tonnageKg: Double get() = bellKg * reps
}

/**
 * A session of sets. While [finishedAtEpochMillis] is null it is the one in progress. [title] is set when
 * it finishes, in the words the screen chose.
 */
data class LiftSession(
    val uid: String,
    val startedAtEpochMillis: Long,
    val finishedAtEpochMillis: Long?,
    val title: String,
    val sets: List<SetLog>,
    /** The program it is a day of, or null for a free session. */
    val programId: ProgramId? = null,
) {
    val tonnageKg: Double get() = sets.sumOf { it.tonnageKg }

    val reps: Int get() = sets.sumOf { it.reps }

    /** The lifts done, in the order first done. */
    val exercises: List<Exercise> get() = sets.map { it.exercise }.distinct()

    /** How long it ran, up to [nowMillis] while in progress. */
    fun elapsedSeconds(nowMillis: Long): Int =
        (((finishedAtEpochMillis ?: nowMillis) - startedAtEpochMillis) / MILLIS_PER_SECOND).toInt().coerceAtLeast(0)

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}

/**
 * The best an [exercise] has been done: the heaviest bell, with the most reps in one set at it, and the
 * most reps in one set with any bell, with that bell.
 */
data class PersonalRecord(
    val exercise: Exercise,
    val heaviestKg: Double,
    val repsAtHeaviest: Int,
    val mostReps: Int,
    val mostRepsKg: Double,
)

/** Each exercise's [PersonalRecord] over [sets], in the catalogue's order. */
fun personalRecords(sets: List<SetLog>): List<PersonalRecord> = sets.groupBy { it.exercise }
    .map { (exercise, done) ->
        val heaviest = done.maxOf { it.bellKg }
        val most = done.maxWith(compareBy<SetLog> { it.reps }.thenBy { it.bellKg })
        PersonalRecord(
            exercise = exercise,
            heaviestKg = heaviest,
            repsAtHeaviest = done.filter { it.bellKg == heaviest }.maxOf { it.reps },
            mostReps = most.reps,
            mostRepsKg = most.bellKg,
        )
    }
    .sortedBy { it.exercise.ordinal }

/**
 * The records [session] set: those of its exercises whose record over [before] plus the session beats the
 * record over [before] alone, by a heavier bell or more reps at the heaviest or in one set.
 */
fun newRecords(before: List<SetLog>, session: LiftSession): List<PersonalRecord> {
    val old = personalRecords(before).associateBy { it.exercise }
    return personalRecords(before + session.sets).filter { record ->
        record.exercise in session.exercises && old[record.exercise].let { was ->
            was == null ||
                record.heaviestKg > was.heaviestKg ||
                (record.heaviestKg == was.heaviestKg && record.repsAtHeaviest > was.repsAtHeaviest) ||
                record.mostReps > was.mostReps
        }
    }
}

/** The bell sizes Bells offers, in kilograms: the common competition and cast-iron steps. */
val standardBells: List<Double> =
    listOf(4.0, 6.0, 8.0, 10.0, 12.0, 14.0, 16.0, 20.0, 24.0, 28.0, 32.0, 36.0, 40.0, 44.0, 48.0)

/** A weight as Bells writes it: no decimals for a whole number ("24"), otherwise one ("1.5"). */
fun formatKg(kg: Double): String {
    val whole = kg.toLong()
    return if (kg == whole.toDouble()) whole.toString() else ((kg * TENTHS).toLong() / TENTHS.toDouble()).toString()
}

private const val TENTHS = 10
