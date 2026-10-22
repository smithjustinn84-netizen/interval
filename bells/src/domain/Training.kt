package dev.justinsmith.bells.domain

/**
 * An exercise Bells logs: one of the [BuiltInExercise]s, one the user added ([CustomExercise]), or one a set
 * names that this build can't ([UnknownExercise]). [perSide] ones are logged one side at a time, unless done
 * with two bells; [doubles] says whether they can be. Sets and programs store it by [key].
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
        /**
         * The exercise [key] names, as stored: a built-in, or one of [custom]; null for one this build doesn't
         * know (from a newer build) or that is gone.
         */
        fun parse(key: String, custom: List<CustomExercise> = emptyList()): Exercise? =
            if (key.startsWith(CUSTOM_EXERCISE_PREFIX)) {
                custom.firstOrNull { it.key == key }
            } else {
                BuiltInExercise.entries.firstOrNull { it.key == key }
            }

        /** The catalogue's order: the built-ins as they are declared, then the user's by name, then unknowns. */
        val order: Comparator<Exercise> = compareBy<Exercise> { exercise ->
            when (exercise) {
                is BuiltInExercise -> exercise.ordinal
                is CustomExercise -> BuiltInExercise.entries.size
                is UnknownExercise -> BuiltInExercise.entries.size + 1
            }
        }.thenBy { (it as? CustomExercise)?.name?.lowercase() }.thenBy { it.key }
    }
}

private const val CUSTOM_EXERCISE_PREFIX = "custom:"

/**
 * An exercise the user added, by its [uid], named as they wrote it, with [cues] to remember mid-set. An
 * [archived] one is out of the picker but keeps its sets. A kettlebell one can be done with two bells.
 */
data class CustomExercise(
    val uid: String,
    val name: String,
    override val equipment: Equipment,
    override val measure: Measure,
    override val perSide: Boolean,
    override val focus: Focus,
    val cues: String = "",
    val archived: Boolean = false,
) : Exercise {
    override val key: String get() = CUSTOM_EXERCISE_PREFIX + uid

    override val doubles: Doubles get() = if (equipment == Equipment.Kettlebell) Doubles.Optional else Doubles.Never
}

/**
 * What a set names that this build can't: an exercise from a newer build, or one of the user's no longer
 * there. It keeps the set, with the [equipment] and [measure] the set was logged with, and whatever bells.
 */
data class UnknownExercise(
    override val key: String,
    override val equipment: Equipment,
    override val measure: Measure,
) : Exercise {
    override val focus: Focus get() = Focus.FullBody
    override val perSide: Boolean get() = false
    override val doubles: Doubles get() = Doubles.Optional
}

/** What makes a [CustomExercise], as the user lays it out. */
data class ExerciseDraft(
    val name: String = "",
    val equipment: Equipment = Equipment.Bodyweight,
    val measure: Measure = Measure.Reps,
    val perSide: Boolean = false,
    val focus: Focus = Focus.FullBody,
    val cues: String = "",
)

/**
 * The exercises that ship with the app, stored by name: a name, once shipped, never changes. Screens name
 * them from the app's strings. Only a kettlebell lift can be done with two bells.
 */
enum class BuiltInExercise(
    override val equipment: Equipment,
    override val perSide: Boolean,
    override val focus: Focus,
    override val measure: Measure = Measure.Reps,
    override val doubles: Doubles = if (equipment == Equipment.Kettlebell) Doubles.Optional else Doubles.Never,
) : Exercise {
    TwoHandSwing(Equipment.Kettlebell, perSide = false, Focus.FullBody, doubles = Doubles.Never),

    /** With two bells, the double swing. */
    OneHandSwing(Equipment.Kettlebell, perSide = true, Focus.FullBody),
    Clean(Equipment.Kettlebell, perSide = true, Focus.FullBody),
    Press(Equipment.Kettlebell, perSide = true, Focus.UpperBody),
    Snatch(Equipment.Kettlebell, perSide = true, Focus.FullBody),
    GetUp(Equipment.Kettlebell, perSide = true, Focus.FullBody, doubles = Doubles.Never),
    GobletSquat(Equipment.Kettlebell, perSide = false, Focus.LowerBody, doubles = Doubles.Never),
    Row(Equipment.Kettlebell, perSide = true, Focus.UpperBody),
    Jerk(Equipment.Kettlebell, perSide = true, Focus.FullBody),
    LongCycle(Equipment.Kettlebell, perSide = true, Focus.FullBody),
    Halo(Equipment.Kettlebell, perSide = false, Focus.UpperBody, doubles = Doubles.Never),
    FrontSquat(Equipment.Kettlebell, perSide = false, Focus.LowerBody),

    /** Dan John's Armor Building Complex with two bells: 2 cleans, a press and 3 front squats, as one rep. */
    ArmorBuildingComplex(Equipment.Kettlebell, perSide = false, Focus.FullBody, doubles = Doubles.Always),
    Deadlift(Equipment.Kettlebell, perSide = false, Focus.LowerBody),
    HighPull(Equipment.Kettlebell, perSide = true, Focus.FullBody),

    /** A reverse lunge with the bell in the rack, a side at a time; with two bells, both racked. */
    RackedLunge(Equipment.Kettlebell, perSide = true, Focus.LowerBody),
    Windmill(Equipment.Kettlebell, perSide = true, Focus.Core, doubles = Doubles.Never),
    BottomsUpPress(Equipment.Kettlebell, perSide = true, Focus.UpperBody, doubles = Doubles.Never),

    /** Iron Cardio: a clean, a press and a squat, as one rep; with two bells, double Iron Cardio. */
    IronCardio(Equipment.Kettlebell, perSide = true, Focus.FullBody),

    PushUp(Equipment.Bodyweight, perSide = false, Focus.UpperBody),
    InclinePushUp(Equipment.Bodyweight, perSide = false, Focus.UpperBody),
    PikePushUp(Equipment.Bodyweight, perSide = false, Focus.UpperBody),
    BenchDip(Equipment.Bodyweight, perSide = false, Focus.UpperBody),
    PullUp(Equipment.Bodyweight, perSide = false, Focus.UpperBody),
    ChinUp(Equipment.Bodyweight, perSide = false, Focus.UpperBody),
    InvertedRow(Equipment.Bodyweight, perSide = false, Focus.UpperBody),
    AirSquat(Equipment.Bodyweight, perSide = false, Focus.LowerBody),
    SplitSquat(Equipment.Bodyweight, perSide = true, Focus.LowerBody),
    ReverseLunge(Equipment.Bodyweight, perSide = true, Focus.LowerBody),
    BulgarianSplitSquat(Equipment.Bodyweight, perSide = true, Focus.LowerBody),
    StepUp(Equipment.Bodyweight, perSide = true, Focus.LowerBody),
    GluteBridge(Equipment.Bodyweight, perSide = false, Focus.LowerBody),
    SingleLegGluteBridge(Equipment.Bodyweight, perSide = true, Focus.LowerBody),
    WallSit(Equipment.Bodyweight, perSide = false, Focus.LowerBody, Measure.Time),
    CalfRaise(Equipment.Bodyweight, perSide = false, Focus.LowerBody),
    Plank(Equipment.Bodyweight, perSide = false, Focus.Core, Measure.Time),
    SidePlank(Equipment.Bodyweight, perSide = true, Focus.Core, Measure.Time),
    HollowHold(Equipment.Bodyweight, perSide = false, Focus.Core, Measure.Time),
    DeadBug(Equipment.Bodyweight, perSide = true, Focus.Core),
    Burpee(Equipment.Bodyweight, perSide = false, Focus.FullBody),
    MountainClimber(Equipment.Bodyweight, perSide = false, Focus.FullBody),

    // Bands: pulls assume a door anchor or a post. Prefixed, so a key never clashes with a bell lift's.
    BandPullApart(Equipment.Band, perSide = false, Focus.UpperBody),
    BandFacePull(Equipment.Band, perSide = false, Focus.UpperBody),
    BandSeatedRow(Equipment.Band, perSide = false, Focus.UpperBody),
    BandLatPulldown(Equipment.Band, perSide = false, Focus.UpperBody),
    BandChestPress(Equipment.Band, perSide = false, Focus.UpperBody),
    BandOverheadPress(Equipment.Band, perSide = false, Focus.UpperBody),
    BandBicepsCurl(Equipment.Band, perSide = false, Focus.UpperBody),
    BandTricepsPushdown(Equipment.Band, perSide = false, Focus.UpperBody),
    BandPallofPress(Equipment.Band, perSide = true, Focus.Core),
    BandWoodchop(Equipment.Band, perSide = true, Focus.Core),
    BandSquat(Equipment.Band, perSide = false, Focus.LowerBody),
    BandGoodMorning(Equipment.Band, perSide = false, Focus.LowerBody),
    BandGluteBridge(Equipment.Band, perSide = false, Focus.LowerBody),
    BandLateralWalk(Equipment.Band, perSide = true, Focus.LowerBody),
    BandClamshell(Equipment.Band, perSide = true, Focus.LowerBody),

    // Mat Pilates, in the classical order.
    Hundred(Equipment.Mat, perSide = false, Focus.Core),
    RollUp(Equipment.Mat, perSide = false, Focus.Core),
    SingleLegCircles(Equipment.Mat, perSide = true, Focus.Core),
    RollingLikeABall(Equipment.Mat, perSide = false, Focus.Core),
    SingleLegStretch(Equipment.Mat, perSide = false, Focus.Core),
    DoubleLegStretch(Equipment.Mat, perSide = false, Focus.Core),
    Scissors(Equipment.Mat, perSide = false, Focus.Core),
    CrissCross(Equipment.Mat, perSide = false, Focus.Core),
    SpineStretchForward(Equipment.Mat, perSide = false, Focus.FullBody),
    Saw(Equipment.Mat, perSide = true, Focus.FullBody),
    Swan(Equipment.Mat, perSide = false, Focus.UpperBody),
    SingleLegKick(Equipment.Mat, perSide = true, Focus.LowerBody),
    SideKickSeries(Equipment.Mat, perSide = true, Focus.LowerBody),
    Teaser(Equipment.Mat, perSide = false, Focus.Core),
    Swimming(Equipment.Mat, perSide = false, Focus.FullBody, Measure.Time),
    ShoulderBridge(Equipment.Mat, perSide = true, Focus.LowerBody),
    Seal(Equipment.Mat, perSide = false, Focus.Core),
    LegPullFront(Equipment.Mat, perSide = false, Focus.FullBody),
    ;

    override val key: String get() = name
}

/** The built-in kettlebell lifts, in order: all the Log sheet and the program editor offer for now. */
val kettlebellLifts: List<BuiltInExercise> = BuiltInExercise.entries.filter { it.equipment == Equipment.Kettlebell }

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

/** The resistance bands every user starts with, lightest first, as the common colours run. */
enum class BandLevel { ExtraLight, Light, Medium, Heavy, ExtraHeavy }

/**
 * One logged set: [reps] of [exercise] with a [bellKg] bell, or a pair of them when [double], on [side] for
 * a one-sided lift. The set keeps the [equipment] it was done with, so changing an exercise later never
 * changes what its sets were. A held set lasted [seconds]; a band set used [band]. For bodyweight [bellKg]
 * is the weight added, for a band or the mat it is 0.
 */
data class SetLog(
    val id: Long,
    val exercise: Exercise,
    val bellKg: Double,
    val reps: Int,
    val side: Side?,
    val loggedAtEpochMillis: Long,
    val double: Boolean = false,
    val equipment: Equipment = exercise.equipment,
    val seconds: Int? = null,
    val band: BandLevel? = null,
) {
    /**
     * The weight moved: the bell, or both, or the weight added to bodyweight, times the reps. A band, the mat
     * and a hold move none that can be weighed.
     */
    val tonnageKg: Double get() = when (equipment) {
        Equipment.Kettlebell, Equipment.Bodyweight -> bellKg * reps * (if (double) 2 else 1)
        Equipment.Band, Equipment.Mat -> 0.0
    }
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

/**
 * Each kettlebell lift's [PersonalRecord] over [sets], one bell and two kept apart, in the catalogue's order.
 * Sets of other equipment set none yet.
 */
fun personalRecords(sets: List<SetLog>): List<PersonalRecord> = sets
    .filter { it.equipment == Equipment.Kettlebell && it.exercise.equipment == Equipment.Kettlebell }
    .groupBy { it.exercise.key to it.double }
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
