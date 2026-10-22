package dev.justinsmith.bells.domain

/** How a block's sets run: one after another, every minute on the minute, or in ladders of rising reps. */
enum class BlockKind { Sets, Emom, Ladder }

/**
 * One part of a program day. [Sets]: [rounds] sets of [reps]. [Emom]: [rounds] minutes, [reps] at the top
 * of each. [Ladder]: [rounds] ladders, each climbing [rungs]. A one-sided lift does each set on both sides,
 * unless it is done with two bells ([double]). For a hold ([Measure.Time]), [reps] counts the seconds held,
 * and a hold is only ever sets.
 */
data class Block(
    val exercise: Exercise,
    val rounds: Int,
    val reps: Int = 0,
    val kind: BlockKind = BlockKind.Sets,
    val rungs: List<Int> = emptyList(),
    val double: Boolean = false,
) {
    /** The sets it comes to, in order: one-sided lifts left then right, except an EMOM, which alternates by minute. */
    fun plannedSets(): List<PlannedSet> = when (kind) {
        BlockKind.Sets -> (1..rounds).flatMap { sides(reps) }

        BlockKind.Emom -> (0 until rounds).map { minute ->
            PlannedSet(exercise, reps, if (exercise.sided(double)) Side.entries[minute % 2] else null, double)
        }

        BlockKind.Ladder -> (1..rounds).flatMap { rungs.flatMap { rung -> sides(rung) } }
    }

    /** The reps it comes to in all. */
    val totalReps: Int get() = plannedSets().sumOf { it.reps }

    private fun sides(reps: Int) =
        if (exercise.sided(double)) {
            Side.entries.map {
                PlannedSet(exercise, reps, it, double)
            }
        } else {
            listOf(PlannedSet(exercise, reps, null, double))
        }
}

/** One set a program asks for, with two bells when [double]. A hold's [reps] are the seconds it lasts. */
data class PlannedSet(val exercise: Exercise, val reps: Int, val side: Side?, val double: Boolean = false) {
    /** Whether it is a hold, timed rather than counted. */
    val held: Boolean get() = exercise.measure == Measure.Time
}

/**
 * A program: [days] repeat in turn, [daysPerWeek] of them a week, for [weeks] weeks. A built-in is named from
 * the app's strings; one the user made carries its [name].
 */
data class Program(
    val key: ProgramKey,
    val days: List<List<Block>>,
    val daysPerWeek: Int,
    val weeks: Int,
    val name: String? = null,
) {
    init {
        require(days.isNotEmpty() && days.none { it.isEmpty() }) { "A program needs days, each with a block" }
    }

    val totalDays: Int get() = daysPerWeek * weeks

    /** Where someone stands after [done] sessions of it: the day to do next, or null once it is over. */
    fun dayAfter(done: Int): ProgramDay? = if (done >= totalDays) {
        null
    } else {
        ProgramDay(this, number = done + 1, week = done / daysPerWeek + 1, blocks = days[done % days.size])
    }
}

/** Day [number] (from 1) of [program], in [week] (from 1), and what it asks for. */
data class ProgramDay(val program: Program, val number: Int, val week: Int, val blocks: List<Block>) {
    fun plannedSets(): List<PlannedSet> = blocks.flatMap { it.plannedSets() }
}

/** A program's id as it is stored, with its sessions and as the one followed. */
sealed interface ProgramKey {
    val stored: String

    /** A built-in, stored by its [ProgramId]'s name. */
    data class BuiltIn(val id: ProgramId) : ProgramKey {
        override val stored: String get() = id.name
    }

    /** One the user made, by its [uid]. */
    data class Custom(val uid: String) : ProgramKey {
        override val stored: String get() = CUSTOM_PREFIX + uid
    }

    companion object {
        /** The key [stored] names; null for a built-in this build doesn't know. */
        fun parse(stored: String): ProgramKey? = if (stored.startsWith(CUSTOM_PREFIX)) {
            stored.removePrefix(CUSTOM_PREFIX).takeIf { it.isNotEmpty() }?.let(::Custom)
        } else {
            ProgramId.entries.firstOrNull { it.name == stored }?.let(::BuiltIn)
        }
    }
}

private const val CUSTOM_PREFIX = "custom:"

/** The kind of program a built-in is, which groups it on the Programs page. */
enum class ProgramFamily {
    /** Light, with few lifts to learn: where someone new to the bell starts. */
    Beginner,
    Classic,

    /** After Dan John's kettlebell programming. */
    DanJohn,

    /** Strength and conditioning for grapplers, light enough to sit beside mat time. */
    Grappling,

    /** Bodyweight alone, or with bells, bands and the mat. */
    Mixed,
}

/** The built-in programs, by a stable id that is stored. Screens name them from the app's strings. */
enum class ProgramId(val family: ProgramFamily = ProgramFamily.Classic) {
    DailySwingsGetUps,
    PressLadders,
    CleanPressEmom,
    GobletSquatRow,
    EasyStrength(ProgramFamily.DanJohn),
    ArmorBuildingFormula(ProgramFamily.DanJohn),
    TenThousandSwings(ProgramFamily.DanJohn),
    SwingSquatCountdown(ProgramFamily.DanJohn),
    SimpleAndSinister,
    ProgramMinimum,
    RiteOfPassage,
    IronCardio,
    SnatchTestPrep,
    LongCycleEmom,
    GrapplerStrength(ProgramFamily.Grappling),
    RoundConditioning(ProgramFamily.Grappling),
    GripAndPull(ProgramFamily.Grappling),
    ShoulderArmor(ProgramFamily.Grappling),
    KettlebellFoundations(ProgramFamily.Beginner),
    FirstHundredSwings(ProgramFamily.Beginner),
    BeginnerFullBody(ProgramFamily.Beginner),
    GetUpPractice(ProgramFamily.Beginner),
    BodyweightBasics(ProgramFamily.Mixed),
    BodyweightEmom(ProgramFamily.Mixed),
    BellsAndBodyweight(ProgramFamily.Mixed),
    BandsAndBells(ProgramFamily.Mixed),
    CoreOnTheMat(ProgramFamily.Mixed),
}

private fun builtIn(id: ProgramId, days: List<List<Block>>, daysPerWeek: Int, weeks: Int) =
    Program(ProgramKey.BuiltIn(id), days, daysPerWeek, weeks)

/** 500 swings in five ladders of 10, 15, 25 and 50: a day of 10,000 Swings. */
private val fiveHundredSwings =
    Block(BuiltInExercise.TwoHandSwing, rounds = 5, kind = BlockKind.Ladder, rungs = listOf(10, 15, 25, 50))

/** Twenty minutes of the Armor Building Complex, one every minute. */
private val armorBuildingComplex =
    Block(BuiltInExercise.ArmorBuildingComplex, rounds = 20, reps = 1, kind = BlockKind.Emom, double = true)

/** [ladders] clean and press ladders of 1 up to [top], a side. */
private fun pressLadders(ladders: Int, top: Int) =
    Block(BuiltInExercise.Press, rounds = ladders, kind = BlockKind.Ladder, rungs = (1..top).toList())

/** [reps] two-hand swings on the minute for ten minutes, after deadlifts to groove the hinge. */
private fun swingDay(reps: Int) = listOf(
    Block(BuiltInExercise.Deadlift, rounds = 2, reps = 10),
    Block(BuiltInExercise.TwoHandSwing, rounds = 10, reps = reps, kind = BlockKind.Emom),
)

/** Six minutes of [exercise], [reps] at the top of each: as long as a round on the mat. */
private fun round(exercise: Exercise, reps: Int) = Block(exercise, rounds = 6, reps = reps, kind = BlockKind.Emom)

val builtInPrograms: List<Program> = listOf(
    builtIn(
        ProgramId.DailySwingsGetUps,
        days = listOf(
            listOf(
                Block(BuiltInExercise.TwoHandSwing, rounds = 10, reps = 10, kind = BlockKind.Emom),
                Block(BuiltInExercise.GetUp, rounds = 5, reps = 1),
            ),
        ),
        daysPerWeek = 5,
        weeks = 6,
    ),
    builtIn(
        ProgramId.PressLadders,
        days = listOf(
            listOf(
                Block(BuiltInExercise.Press, rounds = 3, kind = BlockKind.Ladder, rungs = listOf(1, 2, 3)),
                Block(BuiltInExercise.TwoHandSwing, rounds = 5, reps = 10),
            ),
            listOf(
                Block(BuiltInExercise.Press, rounds = 4, kind = BlockKind.Ladder, rungs = listOf(1, 2, 3)),
                Block(BuiltInExercise.Snatch, rounds = 5, reps = 5),
            ),
            listOf(
                Block(BuiltInExercise.Press, rounds = 5, kind = BlockKind.Ladder, rungs = listOf(1, 2, 3)),
                Block(BuiltInExercise.TwoHandSwing, rounds = 5, reps = 15),
            ),
        ),
        daysPerWeek = 3,
        weeks = 8,
    ),
    builtIn(
        ProgramId.CleanPressEmom,
        days = listOf(
            listOf(
                Block(BuiltInExercise.Clean, rounds = 10, reps = 3, kind = BlockKind.Emom),
                Block(BuiltInExercise.Press, rounds = 10, reps = 3, kind = BlockKind.Emom),
            ),
        ),
        daysPerWeek = 3,
        weeks = 4,
    ),
    builtIn(
        ProgramId.GobletSquatRow,
        days = listOf(
            listOf(
                Block(BuiltInExercise.GobletSquat, rounds = 3, reps = 10),
                Block(BuiltInExercise.Row, rounds = 3, reps = 10),
            ),
        ),
        daysPerWeek = 2,
        weeks = 4,
    ),
    // The 40-day workout: the same few lifts, light and easy, five days a week.
    builtIn(
        ProgramId.EasyStrength,
        days = listOf(
            listOf(
                Block(BuiltInExercise.Press, rounds = 2, reps = 5),
                Block(BuiltInExercise.Row, rounds = 2, reps = 5),
                Block(BuiltInExercise.GobletSquat, rounds = 2, reps = 5),
                Block(BuiltInExercise.TwoHandSwing, rounds = 3, reps = 15),
                Block(BuiltInExercise.GetUp, rounds = 1, reps = 1),
            ),
        ),
        daysPerWeek = 5,
        weeks = 8,
    ),
    // The complex on two days of three; press ladders of 2-3-5 and swings between them.
    builtIn(
        ProgramId.ArmorBuildingFormula,
        days = listOf(
            listOf(armorBuildingComplex),
            listOf(
                Block(BuiltInExercise.Press, rounds = 5, kind = BlockKind.Ladder, rungs = listOf(2, 3, 5)),
                Block(BuiltInExercise.TwoHandSwing, rounds = 10, reps = 10),
            ),
            listOf(armorBuildingComplex),
        ),
        daysPerWeek = 3,
        weeks = 6,
    ),
    // 500 swings a session for 20 sessions, with a strength move in small ladders.
    builtIn(
        ProgramId.TenThousandSwings,
        days = listOf(
            listOf(
                fiveHundredSwings,
                Block(BuiltInExercise.Press, rounds = 5, kind = BlockKind.Ladder, rungs = listOf(1, 2, 3)),
            ),
            listOf(
                fiveHundredSwings,
                Block(BuiltInExercise.GobletSquat, rounds = 5, kind = BlockKind.Ladder, rungs = listOf(1, 2, 3)),
            ),
        ),
        daysPerWeek = 5,
        weeks = 4,
    ),
    // After the Humane Burpee: goblet squats counting down from 10, with 15 swings for each step.
    builtIn(
        ProgramId.SwingSquatCountdown,
        days = listOf(
            listOf(
                Block(BuiltInExercise.GobletSquat, rounds = 1, kind = BlockKind.Ladder, rungs = (10 downTo 1).toList()),
                Block(BuiltInExercise.TwoHandSwing, rounds = 10, reps = 15),
            ),
        ),
        daysPerWeek = 3,
        weeks = 4,
    ),
    // 100 one-arm swings and 10 get-ups, every training day.
    builtIn(
        ProgramId.SimpleAndSinister,
        days = listOf(
            listOf(
                Block(BuiltInExercise.OneHandSwing, rounds = 5, reps = 10),
                Block(BuiltInExercise.GetUp, rounds = 5, reps = 1),
            ),
        ),
        daysPerWeek = 5,
        weeks = 12,
    ),
    // Swings one day, get-ups the next.
    builtIn(
        ProgramId.ProgramMinimum,
        days = listOf(
            listOf(Block(BuiltInExercise.TwoHandSwing, rounds = 12, reps = 10, kind = BlockKind.Emom)),
            listOf(Block(BuiltInExercise.GetUp, rounds = 5, reps = 1)),
        ),
        daysPerWeek = 4,
        weeks = 4,
    ),
    // Heavy, light and medium days of ladders, at the stage the book builds to; snatches or swings after.
    builtIn(
        ProgramId.RiteOfPassage,
        days = listOf(
            listOf(pressLadders(ladders = 5, top = 5), Block(BuiltInExercise.Snatch, rounds = 5, reps = 5)),
            listOf(
                pressLadders(ladders = 5, top = 3),
                Block(BuiltInExercise.TwoHandSwing, rounds = 10, reps = 10, kind = BlockKind.Emom),
            ),
            listOf(pressLadders(ladders = 5, top = 4), Block(BuiltInExercise.OneHandSwing, rounds = 5, reps = 10)),
        ),
        daysPerWeek = 3,
        weeks = 12,
    ),
    builtIn(
        ProgramId.IronCardio,
        days = listOf(
            listOf(
                Block(BuiltInExercise.IronCardio, rounds = 20, reps = 3, kind = BlockKind.Emom),
                Block(BuiltInExercise.TwoHandSwing, rounds = 5, reps = 20),
            ),
            listOf(Block(BuiltInExercise.IronCardio, rounds = 30, reps = 2, kind = BlockKind.Emom)),
        ),
        daysPerWeek = 3,
        weeks = 6,
    ),
    // Snatches on the minute, the third day at the test's pace: 100 in 5 minutes.
    builtIn(
        ProgramId.SnatchTestPrep,
        days = listOf(
            listOf(Block(BuiltInExercise.Snatch, rounds = 10, reps = 8, kind = BlockKind.Emom)),
            listOf(Block(BuiltInExercise.Snatch, rounds = 12, reps = 10, kind = BlockKind.Emom)),
            listOf(Block(BuiltInExercise.Snatch, rounds = 5, reps = 20, kind = BlockKind.Emom)),
        ),
        daysPerWeek = 3,
        weeks = 6,
    ),
    builtIn(
        ProgramId.LongCycleEmom,
        days = listOf(
            listOf(Block(BuiltInExercise.LongCycle, rounds = 10, reps = 6, kind = BlockKind.Emom)),
            listOf(
                Block(BuiltInExercise.Jerk, rounds = 10, reps = 8, kind = BlockKind.Emom),
                Block(BuiltInExercise.Snatch, rounds = 5, reps = 10),
            ),
            listOf(Block(BuiltInExercise.LongCycle, rounds = 12, reps = 5, kind = BlockKind.Emom)),
        ),
        daysPerWeek = 3,
        weeks = 6,
    ),
    // Heavy and low in reps, so it leaves something for the mat.
    builtIn(
        ProgramId.GrapplerStrength,
        days = listOf(
            listOf(
                Block(BuiltInExercise.FrontSquat, rounds = 5, reps = 5, double = true),
                Block(BuiltInExercise.Row, rounds = 4, reps = 8),
                Block(BuiltInExercise.GetUp, rounds = 3, reps = 1),
            ),
            listOf(
                Block(BuiltInExercise.Deadlift, rounds = 5, reps = 5, double = true),
                pressLadders(ladders = 3, top = 3),
                Block(BuiltInExercise.Windmill, rounds = 3, reps = 5),
            ),
        ),
        daysPerWeek = 2,
        weeks = 8,
    ),
    // Four six-minute rounds a day, each lift on the minute.
    builtIn(
        ProgramId.RoundConditioning,
        days = listOf(
            listOf(
                round(BuiltInExercise.TwoHandSwing, reps = 15),
                round(BuiltInExercise.Snatch, reps = 6),
                round(BuiltInExercise.HighPull, reps = 8),
                round(BuiltInExercise.GobletSquat, reps = 10),
            ),
            listOf(
                round(BuiltInExercise.OneHandSwing, reps = 10),
                round(BuiltInExercise.Clean, reps = 5),
                round(BuiltInExercise.RackedLunge, reps = 6),
                round(BuiltInExercise.Halo, reps = 8),
            ),
        ),
        daysPerWeek = 2,
        weeks = 6,
    ),
    // For the grips and pulls a guard is made of.
    builtIn(
        ProgramId.GripAndPull,
        days = listOf(
            listOf(
                Block(BuiltInExercise.Row, rounds = 5, reps = 8),
                Block(BuiltInExercise.HighPull, rounds = 5, reps = 8),
                Block(BuiltInExercise.Clean, rounds = 5, reps = 5),
                Block(BuiltInExercise.OneHandSwing, rounds = 5, reps = 15),
            ),
        ),
        daysPerWeek = 2,
        weeks = 6,
    ),
    // Shoulders that hold up under frames and locks.
    builtIn(
        ProgramId.ShoulderArmor,
        days = listOf(
            listOf(
                Block(BuiltInExercise.Halo, rounds = 3, reps = 10),
                Block(BuiltInExercise.GetUp, rounds = 5, reps = 1),
                Block(BuiltInExercise.Windmill, rounds = 3, reps = 5),
                Block(BuiltInExercise.BottomsUpPress, rounds = 3, reps = 5),
            ),
        ),
        daysPerWeek = 3,
        weeks = 6,
    ),
    // The hinge, the squat and the get-up, before anything fast.
    builtIn(
        ProgramId.KettlebellFoundations,
        days = listOf(
            listOf(
                Block(BuiltInExercise.Deadlift, rounds = 3, reps = 10),
                Block(BuiltInExercise.GobletSquat, rounds = 3, reps = 8),
                Block(BuiltInExercise.Halo, rounds = 2, reps = 6),
            ),
            listOf(
                Block(BuiltInExercise.TwoHandSwing, rounds = 5, reps = 10),
                Block(BuiltInExercise.GetUp, rounds = 3, reps = 1),
            ),
        ),
        daysPerWeek = 3,
        weeks = 4,
    ),
    // Swings on the minute, from 50 a day to 100.
    builtIn(
        ProgramId.FirstHundredSwings,
        days = listOf(swingDay(reps = 5), swingDay(reps = 8), swingDay(reps = 10)),
        daysPerWeek = 3,
        weeks = 4,
    ),
    builtIn(
        ProgramId.BeginnerFullBody,
        days = listOf(
            listOf(
                Block(BuiltInExercise.GobletSquat, rounds = 3, reps = 8),
                Block(BuiltInExercise.Press, rounds = 3, reps = 5),
                Block(BuiltInExercise.Row, rounds = 3, reps = 8),
            ),
            listOf(
                Block(BuiltInExercise.Deadlift, rounds = 3, reps = 8),
                Block(BuiltInExercise.Clean, rounds = 3, reps = 5),
                Block(BuiltInExercise.TwoHandSwing, rounds = 5, reps = 10),
            ),
        ),
        daysPerWeek = 2,
        weeks = 6,
    ),
    builtIn(
        ProgramId.GetUpPractice,
        days = listOf(
            listOf(
                Block(BuiltInExercise.GetUp, rounds = 5, reps = 1),
                Block(BuiltInExercise.Halo, rounds = 2, reps = 8),
                Block(BuiltInExercise.GobletSquat, rounds = 2, reps = 5),
            ),
        ),
        daysPerWeek = 3,
        weeks = 4,
    ),
    builtIn(
        ProgramId.BodyweightBasics,
        days = listOf(
            listOf(
                Block(BuiltInExercise.PushUp, rounds = 3, reps = 10),
                Block(BuiltInExercise.AirSquat, rounds = 3, reps = 15),
                Block(BuiltInExercise.InvertedRow, rounds = 3, reps = 8),
                Block(BuiltInExercise.Plank, rounds = 3, reps = 30),
            ),
            listOf(
                Block(BuiltInExercise.ReverseLunge, rounds = 3, reps = 8),
                Block(BuiltInExercise.BenchDip, rounds = 3, reps = 10),
                Block(BuiltInExercise.GluteBridge, rounds = 3, reps = 12),
                Block(BuiltInExercise.SidePlank, rounds = 2, reps = 20),
            ),
        ),
        daysPerWeek = 3,
        weeks = 6,
    ),
    builtIn(
        ProgramId.BodyweightEmom,
        days = listOf(
            listOf(
                Block(BuiltInExercise.Burpee, rounds = 10, reps = 6, kind = BlockKind.Emom),
                Block(BuiltInExercise.AirSquat, rounds = 10, reps = 15, kind = BlockKind.Emom),
            ),
            listOf(
                Block(BuiltInExercise.PushUp, rounds = 10, reps = 8, kind = BlockKind.Emom),
                Block(BuiltInExercise.MountainClimber, rounds = 10, reps = 20, kind = BlockKind.Emom),
            ),
        ),
        daysPerWeek = 3,
        weeks = 4,
    ),
    builtIn(
        ProgramId.BellsAndBodyweight,
        days = listOf(
            listOf(
                Block(BuiltInExercise.TwoHandSwing, rounds = 10, reps = 10, kind = BlockKind.Emom),
                Block(BuiltInExercise.PushUp, rounds = 5, reps = 10),
                Block(BuiltInExercise.GobletSquat, rounds = 3, reps = 10),
            ),
            listOf(
                Block(BuiltInExercise.Clean, rounds = 3, reps = 5),
                Block(BuiltInExercise.PullUp, rounds = 5, reps = 5),
                pressLadders(ladders = 3, top = 3),
                Block(BuiltInExercise.HollowHold, rounds = 3, reps = 20),
            ),
        ),
        daysPerWeek = 3,
        weeks = 6,
    ),
    // Bands for the small pulls and the core, bells for the big lifts.
    builtIn(
        ProgramId.BandsAndBells,
        days = listOf(
            listOf(
                Block(BuiltInExercise.Press, rounds = 3, reps = 5),
                Block(BuiltInExercise.BandPullApart, rounds = 3, reps = 15),
                Block(BuiltInExercise.Row, rounds = 3, reps = 8),
                Block(BuiltInExercise.BandFacePull, rounds = 3, reps = 15),
            ),
            listOf(
                Block(BuiltInExercise.GobletSquat, rounds = 3, reps = 10),
                Block(BuiltInExercise.BandPallofPress, rounds = 3, reps = 10),
                Block(BuiltInExercise.Deadlift, rounds = 3, reps = 10),
                Block(BuiltInExercise.BandGluteBridge, rounds = 3, reps = 15),
            ),
        ),
        daysPerWeek = 2,
        weeks = 6,
    ),
    // Classical mat work for the trunk, then a bell for the same work standing and rising.
    builtIn(
        ProgramId.CoreOnTheMat,
        days = listOf(
            listOf(
                Block(BuiltInExercise.Hundred, rounds = 1, reps = 100),
                Block(BuiltInExercise.RollUp, rounds = 2, reps = 6),
                Block(BuiltInExercise.SingleLegStretch, rounds = 2, reps = 10),
                Block(BuiltInExercise.Swimming, rounds = 2, reps = 30),
                Block(BuiltInExercise.Windmill, rounds = 2, reps = 5),
                Block(BuiltInExercise.GetUp, rounds = 3, reps = 1),
            ),
        ),
        daysPerWeek = 2,
        weeks = 6,
    ),
)

fun program(
    id: ProgramId,
): Program = builtInPrograms.first { it.key == ProgramKey.BuiltIn(id) }

/**
 * Of [planned], the first set not yet done by [logged]: the n-th planned set of a lift (and side) is done by
 * the n-th logged one, whatever its bell or reps, so going off plan never blocks what comes next. A set
 * with two bells has no side, so for a one-sided lift a planned double set is done only by a double one; for
 * a lift with no sides, one bell or two does it.
 */
fun nextPlanned(planned: List<PlannedSet>, logged: List<SetLog>): Int? {
    val used = mutableMapOf<Pair<String, Side?>, Int>()
    logged.forEach { set ->
        val key = set.exercise.key to set.side
        used[key] = (used[key] ?: 0) + 1
    }
    planned.forEachIndexed { i, set ->
        val key = set.exercise.key to set.side
        val left = used[key] ?: 0
        if (left == 0) return i
        used[key] = left - 1
    }
    return null
}

/** The band to suggest for [exercise]: the last one used for it, else [fallback]. */
fun suggestedBand(exercise: Exercise, history: List<SetLog>, fallback: BandLevel): BandLevel =
    history.lastOrNull { it.exercise.key == exercise.key && it.band != null }?.band ?: fallback

/**
 * The bell to suggest for [exercise], with two bells or one ([double]): the last one used for it so, else
 * the last used for it at all, else [fallback].
 */
fun suggestedBell(exercise: Exercise, double: Boolean, history: List<SetLog>, fallback: Double): Double =
    history.lastOrNull { it.exercise.key == exercise.key && it.double == double }?.bellKg
        ?: history.lastOrNull { it.exercise.key == exercise.key }?.bellKg
        ?: fallback
