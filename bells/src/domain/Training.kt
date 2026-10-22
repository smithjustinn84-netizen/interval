package dev.justinsmith.bells.domain

/**
 * An exercise Bells logs: one of the [BuiltInExercise]s. [perSide] ones are logged one side at a time,
 * unless done with two bells; [doubles] says whether they can be. Sets and programs store it by [key].
 */
sealed interface Exercise {
    /** How it is stored, with its sets and in programs; never changes once shipped. */
    val key: String

    /** What it is done with, which decides the load a set of it takes. */
    val equipment: Equipment

    /** Whether a set of it counts reps or time held. */
    val measure: Measure

    /** Where it works most. */
    val focus: Focus

    val perSide: Boolean

    val doubles: Doubles

    /** Whether a set of it is done with two bells, when [requested] is what was asked for. */
    fun doubled(requested: Boolean): Boolean = when (doubles) {
        Doubles.Never -> false
        Doubles.Optional -> requested
        Doubles.Always -> true
    }

    /** Whether a set of it, with two bells or not ([double]), is done one side at a time. */
    fun sided(double: Boolean): Boolean = perSide && !double

    companion object {
        /** The exercise [key] names, as stored; an unknown one (from a newer build) reads as null. */
        fun parse(key: String): Exercise? = BuiltInExercise.entries.firstOrNull { it.key == key }

        /** The catalogue's order: the built-ins as they are declared. */
        val order: Comparator<Exercise> = compareBy { exercise ->
            when (exercise) {
                is BuiltInExercise -> exercise.ordinal
            }
        }
    }
}

/** The exercises that ship with the app, stored by name. Screens name them from the app's strings. */
enum class BuiltInExercise(
    override val perSide: Boolean,
    override val focus: Focus,
    override val doubles: Doubles = Doubles.Optional,
    override val equipment: Equipment = Equipment.Kettlebell,
    override val measure: Measure = Measure.Reps,
) : Exercise {
    TwoHandSwing(perSide = false, Focus.FullBody, doubles = Doubles.Never),

    /** With two bells, the double swing. */
    OneHandSwing(perSide = true, Focus.FullBody),
    Clean(perSide = true, Focus.FullBody),
    Press(perSide = true, Focus.UpperBody),
    Snatch(perSide = true, Focus.FullBody),
    GetUp(perSide = true, Focus.FullBody, doubles = Doubles.Never),
    GobletSquat(perSide = false, Focus.LowerBody, doubles = Doubles.Never),
    Row(perSide = true, Focus.UpperBody),
    Jerk(perSide = true, Focus.FullBody),
    LongCycle(perSide = true, Focus.FullBody),
    Halo(perSide = false, Focus.UpperBody, doubles = Doubles.Never),
    FrontSquat(perSide = false, Focus.LowerBody),

    /** Dan John's Armor Building Complex with two bells: 2 cleans, a press and 3 front squats, as one rep. */
    ArmorBuildingComplex(perSide = false, Focus.FullBody, doubles = Doubles.Always),
    ;

    override val key: String get() = name
}

/** What an exercise is done with. */
enum class Equipment { Kettlebell, Bodyweight, Band, Mat }

/** How a set is counted: reps, or the time held. */
enum class Measure { Reps, Time }

/** Where an exercise works most. */
enum class Focus { UpperBody, LowerBody, Core, FullBody }

/** Whether a lift can be done with a bell in each hand: never, by choice, or only so. */
enum class Doubles { Never, Optional, Always }

/** The side a one-sided set was done on. */
enum class Side { Left, Right }

/**
 * One logged set: [reps] of [exercise] with a [bellKg] bell, or a pair of them when [double], on [side] for
 * a one-sided lift.
 */
data class SetLog(
    val id: Long,
    val exercise: Exercise,
    val bellKg: Double,
    val reps: Int,
    val side: Side?,
    val loggedAtEpochMillis: Long,
    val double: Boolean = false,
) {
    /** The weight moved: the bell, or both, times the reps. */
    val tonnageKg: Double get() = bellKg * reps * (if (double) 2 else 1)
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
    val program: ProgramKey? = null,
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
 * The best an [exercise] has been done, with one bell or two ([double]): the heaviest bell, with the most
 * reps in one set at it, and the most reps in one set with any bell, with that bell.
 */
data class PersonalRecord(
    val exercise: Exercise,
    val heaviestKg: Double,
    val repsAtHeaviest: Int,
    val mostReps: Int,
    val mostRepsKg: Double,
    val double: Boolean = false,
)

/** Each exercise's [PersonalRecord] over [sets], one bell and two kept apart, in the catalogue's order. */
fun personalRecords(sets: List<SetLog>): List<PersonalRecord> = sets.groupBy { it.exercise.key to it.double }
    .map { (lift, done) ->
        val exercise = done.first().exercise
        val double = lift.second
        val heaviest = done.maxOf { it.bellKg }
        val most = done.maxWith(compareBy<SetLog> { it.reps }.thenBy { it.bellKg })
        PersonalRecord(
            exercise = exercise,
            heaviestKg = heaviest,
            repsAtHeaviest = done.filter { it.bellKg == heaviest }.maxOf { it.reps },
            mostReps = most.reps,
            mostRepsKg = most.bellKg,
            double = double,
        )
    }
    .sortedWith(compareBy(Exercise.order, PersonalRecord::exercise).thenBy { it.double })

/**
 * The records [session] set: those of its lifts (one bell or two) whose record over [before] plus the
 * session beats the record over [before] alone, by a heavier bell or more reps at the heaviest or in one set.
 */
fun newRecords(before: List<SetLog>, session: LiftSession): List<PersonalRecord> {
    val old = personalRecords(before).associateBy { it.exercise.key to it.double }
    val done = session.sets.map { it.exercise.key to it.double }.toSet()
    return personalRecords(before + session.sets).filter { record ->
        val lift = record.exercise.key to record.double
        lift in done && old[lift].let { was ->
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
