package dev.justinsmith.lift.domain

/** The best an [exercise] has been done, with one bell or two ([double]), in the terms its sets are counted. */
sealed interface PersonalRecord {
    val exercise: Exercise
    val double: Boolean

    /**
     * Sets counted in reps with a weight, or none: the heaviest (the bell, or what was added to bodyweight;
     * 0 for the mat or bodyweight alone) with the most reps in one set at it, and the most reps in one set at
     * any weight, with that weight.
     */
    data class Weighted(
        override val exercise: Exercise,
        val heaviestKg: Double,
        val repsAtHeaviest: Int,
        val mostReps: Int,
        val mostRepsKg: Double,
        override val double: Boolean = false,
    ) : PersonalRecord

    /** Band sets: the heaviest band with the most reps at it, and the most reps with any band, with that band. */
    data class Banded(
        override val exercise: Exercise,
        val heaviest: Band,
        val repsAtHeaviest: Int,
        val mostReps: Int,
        val mostRepsBand: Band,
    ) : PersonalRecord {
        override val double: Boolean get() = false
    }

    /** Holds: the longest, in seconds. */
    data class Held(override val exercise: Exercise, val longestSeconds: Int, override val double: Boolean = false) :
        PersonalRecord
}

/**
 * Each exercise's [PersonalRecord] over [sets], one bell and two kept apart, in the catalogue's order: a
 * hold by its length, a band set by its band (lightest to heaviest in the user's order of them), any other by its
 * weight and reps. Weight added to bodyweight is the bodyweight exercise's, never a bell's. Sets of an
 * exercise this build doesn't know set none.
 */
fun personalRecords(sets: List<SetLog>): List<PersonalRecord> = sets
    .filter { it.exercise !is UnknownExercise }
    .groupBy { Triple(it.exercise.key, it.double, it.recordKind) }
    .map { (lift, done) ->
        val exercise = done.first().exercise
        when (lift.third) {
            RecordKind.Held -> PersonalRecord.Held(exercise, done.maxOf { it.seconds ?: 0 }, lift.second)
            RecordKind.Banded -> banded(exercise, done)
            RecordKind.Weighted -> weighted(exercise, done, lift.second)
        }
    }
    .sortedWith(compareBy(Exercise.order, PersonalRecord::exercise).thenBy { it.double }.thenBy { it.kindOrder })

/**
 * The records [session] set: those of its exercises (one bell or two) whose record over [before] plus the
 * session beats the record over [before] alone, by a heavier load, or more reps at the heaviest or in one set,
 * or a longer hold.
 */
fun newRecords(before: List<SetLog>, session: LiftSession): List<PersonalRecord> = newRecords(before, session.sets)

/** The records [sets], logged after [before], set between them, as [newRecords] judges a session's. */
fun newRecords(before: List<SetLog>, sets: List<SetLog>): List<PersonalRecord> {
    val old = personalRecords(before).associateBy { it.lift }
    val done = personalRecords(sets).map { it.lift }.toSet()
    return personalRecords(before + sets).filter { record ->
        record.lift in done && old[record.lift].let { was -> was == null || record.beats(was) }
    }
}

/**
 * Of [sets], logged in that order after [before], the ids of those that beat a record standing as they
 * were logged: a heavier load, more reps at the heaviest or in one set, or a longer hold. A lift's first
 * set ever has no record to beat, so it is not one; the summary's [newRecords] lists it all the same.
 */
fun recordSetIds(before: List<SetLog>, sets: List<SetLog>): Set<Long> {
    val prior = before.filter { it.exercise !is UnknownExercise }
        .groupBy { it.lift }
        .mapValuesTo(mutableMapOf()) { it.value.toMutableList() }
    val ids = mutableSetOf<Long>()
    for (set in sets) {
        if (set.exercise is UnknownExercise) continue
        val same = prior.getOrPut(set.lift) { mutableListOf() }
        val was = personalRecords(same).singleOrNull()
        same += set
        if (was != null && personalRecords(same).single().beats(was)) ids += set.id
    }
    return ids
}

private enum class RecordKind { Weighted, Banded, Held }

private val SetLog.lift: Triple<String, Boolean, RecordKind> get() = Triple(exercise.key, double, recordKind)

private val SetLog.recordKind: RecordKind get() = when {
    seconds != null -> RecordKind.Held
    equipment == Equipment.Band && band != null -> RecordKind.Banded
    else -> RecordKind.Weighted
}

private val PersonalRecord.kind: RecordKind get() = when (this) {
    is PersonalRecord.Weighted -> RecordKind.Weighted
    is PersonalRecord.Banded -> RecordKind.Banded
    is PersonalRecord.Held -> RecordKind.Held
}

private val PersonalRecord.kindOrder: Int get() = kind.ordinal

private val PersonalRecord.lift: Triple<String, Boolean, RecordKind> get() = Triple(exercise.key, double, kind)

private fun weighted(exercise: Exercise, done: List<SetLog>, double: Boolean): PersonalRecord.Weighted {
    val heaviest = done.maxOf { it.bellKg }
    val most = done.maxWith(compareBy<SetLog> { it.reps }.thenBy { it.bellKg })
    return PersonalRecord.Weighted(
        exercise = exercise,
        heaviestKg = heaviest,
        repsAtHeaviest = done.filter { it.bellKg == heaviest }.maxOf { it.reps },
        mostReps = most.reps,
        mostRepsKg = most.bellKg,
        double = double,
    )
}

private fun banded(exercise: Exercise, done: List<SetLog>): PersonalRecord.Banded {
    // Every one has a band: that is what made it a band record.
    val sets = done.mapNotNull { set -> set.band?.let { it to set.reps } }
    val heaviest = sets.maxBy { it.first.position }.first
    val most = sets.maxWith(compareBy<Pair<Band, Int>> { it.second }.thenBy { it.first.position })
    return PersonalRecord.Banded(
        exercise = exercise,
        heaviest = heaviest,
        repsAtHeaviest = sets.filter { it.first.id == heaviest.id }.maxOf { it.second },
        mostReps = most.second,
        mostRepsBand = most.first,
    )
}

/** Whether this record is better than [was], a record of the same exercise and kind. */
private fun PersonalRecord.beats(was: PersonalRecord): Boolean = when {
    this is PersonalRecord.Weighted && was is PersonalRecord.Weighted ->
        heaviestKg > was.heaviestKg ||
            (heaviestKg == was.heaviestKg && repsAtHeaviest > was.repsAtHeaviest) ||
            mostReps > was.mostReps

    this is PersonalRecord.Banded && was is PersonalRecord.Banded ->
        heaviest.position > was.heaviest.position ||
            (heaviest.id == was.heaviest.id && repsAtHeaviest > was.repsAtHeaviest) ||
            mostReps > was.mostReps

    this is PersonalRecord.Held && was is PersonalRecord.Held -> longestSeconds > was.longestSeconds

    else -> false
}
