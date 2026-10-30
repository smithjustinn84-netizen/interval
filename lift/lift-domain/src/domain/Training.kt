package dev.justinsmith.lift.domain

import kotlin.math.roundToLong

/**
 * An exercise Lift logs: one of the [BuiltInExercise]s, one the user added ([CustomExercise]), or one a set
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

    // Barbell: the Olympic lifts and their pulls and squats, then the strength lifts. Prefixed, so a key never
    // clashes with a bell lift's.
    BarbellSnatch(Equipment.Barbell, perSide = false, Focus.FullBody),

    /** A clean and a jerk, as one rep. */
    BarbellCleanAndJerk(Equipment.Barbell, perSide = false, Focus.FullBody),
    BarbellClean(Equipment.Barbell, perSide = false, Focus.FullBody),

    /** The split jerk, from the rack. */
    BarbellJerk(Equipment.Barbell, perSide = false, Focus.FullBody),
    BarbellPowerSnatch(Equipment.Barbell, perSide = false, Focus.FullBody),
    BarbellPowerClean(Equipment.Barbell, perSide = false, Focus.FullBody),
    BarbellHangSnatch(Equipment.Barbell, perSide = false, Focus.FullBody),
    BarbellHangClean(Equipment.Barbell, perSide = false, Focus.FullBody),
    BarbellSnatchPull(Equipment.Barbell, perSide = false, Focus.FullBody),
    BarbellCleanPull(Equipment.Barbell, perSide = false, Focus.FullBody),
    BarbellOverheadSquat(Equipment.Barbell, perSide = false, Focus.FullBody),
    BarbellSnatchBalance(Equipment.Barbell, perSide = false, Focus.FullBody),
    BarbellPushPress(Equipment.Barbell, perSide = false, Focus.UpperBody),
    BarbellBackSquat(Equipment.Barbell, perSide = false, Focus.LowerBody),
    BarbellFrontSquat(Equipment.Barbell, perSide = false, Focus.LowerBody),
    BarbellDeadlift(Equipment.Barbell, perSide = false, Focus.LowerBody),
    BarbellRomanianDeadlift(Equipment.Barbell, perSide = false, Focus.LowerBody),
    BarbellBenchPress(Equipment.Barbell, perSide = false, Focus.UpperBody),
    BarbellOverheadPress(Equipment.Barbell, perSide = false, Focus.UpperBody),
    BarbellRow(Equipment.Barbell, perSide = false, Focus.UpperBody),

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

/** The built-in barbell lifts, in order. */
val barbellLifts: List<BuiltInExercise> = BuiltInExercise.entries.filter { it.equipment == Equipment.Barbell }

/** What an exercise is done with. */
enum class Equipment { Kettlebell, Barbell, Bodyweight, Band, Mat }

/** Whether a set done with it is weighed: a bell, the total on a bar, or weight added to bodyweight. */
val Equipment.takesKg: Boolean get() = when (this) {
    Equipment.Kettlebell, Equipment.Barbell, Equipment.Bodyweight -> true
    Equipment.Band, Equipment.Mat -> false
}

/** How a set is counted: reps, or the time held. */
enum class Measure { Reps, Time }

/** Where an exercise works most. */
enum class Focus { UpperBody, LowerBody, Core, FullBody }

/** Whether a lift can be done with a bell in each hand: never, by choice, or only so. */
enum class Doubles { Never, Optional, Always }

/** The side a one-sided set was done on. */
enum class Side { Left, Right }

/** The side a one-sided exercise goes on with. */
val Side.other: Side get() = if (this == Side.Left) Side.Right else Side.Left

/** The resistance bands every user starts with, lightest first, as the common colours run; see [Band]. */
enum class BandLevel { ExtraLight, Light, Medium, Heavy, ExtraHeavy }

/**
 * One logged set: [reps] of [exercise] with a [bellKg] bell, or a pair of them when [double], on [side] for
 * a one-sided lift. The set keeps the [equipment] it was done with, so changing an exercise later never
 * changes what its sets were. A held set lasted [seconds]; a band set used [band]. For a barbell [bellKg] is
 * the total on the bar, for bodyweight the weight added, for a band or the mat it is 0.
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
    val band: Band? = null,
) {
    /**
     * The weight moved: the bell, or both, the bar as loaded, or the weight added to bodyweight, times the
     * reps. A band, the mat and a hold move none that can be weighed.
     */
    val tonnageKg: Double get() = when (equipment) {
        Equipment.Kettlebell, Equipment.Barbell, Equipment.Bodyweight -> bellKg * reps * (if (double) 2 else 1)
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
    /** The day's plan as this session changed it; null while it is as programmed. */
    val plan: SessionPlan? = null,
    /** Which day of [program] it was, from 1; null for a free session, or one logged before sessions kept it. */
    val day: Int? = null,
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
 * Every set of these sessions, held newest first as the repository holds them, oldest first: the order a
 * suggestion reads the last one done from.
 */
fun List<LiftSession>.setsOldestFirst(): List<SetLog> = asReversed().flatMap { it.sets }

/** The bell sizes Lift offers, in kilograms: the common competition and cast-iron steps. */
val standardBells: List<Double> =
    listOf(4.0, 6.0, 8.0, 10.0, 12.0, 14.0, 16.0, 20.0, 24.0, 28.0, 32.0, 36.0, 40.0, 44.0, 48.0)

/**
 * A weight as Lift writes it, to the hundredth and no further than it needs: "24", "1.5", or a change plate's
 * "1.25".
 */
fun formatKg(kg: Double): String {
    val hundredths = (kg * HUNDREDTHS).roundToLong()
    val whole = hundredths / HUNDREDTHS
    val part = hundredths % HUNDREDTHS
    return when {
        part == 0L -> whole.toString()
        part % TENTH == 0L -> "$whole.${part / TENTH}"
        else -> "$whole.${part.toString().padStart(2, '0')}"
    }
}

private const val HUNDREDTHS = 100L
private const val TENTH = 10L
