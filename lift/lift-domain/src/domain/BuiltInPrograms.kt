package dev.justinsmith.lift.domain

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

    /** The barbell: the Olympic lifts, and the strength lifts under them. */
    Barbell,
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
    OneStopShop(ProgramFamily.DanJohn),
    StrongForLife(ProgramFamily.DanJohn),
    SimpleAndSinister,
    ProgramMinimum,
    RiteOfPassage,
    IronCardio,
    SnatchTestPrep,
    LongCycleEmom,
    FifteenMinuteKettlebell,
    GrapplerStrength(ProgramFamily.Grappling),
    RoundConditioning(ProgramFamily.Grappling),
    GripAndPull(ProgramFamily.Grappling),
    ShoulderArmor(ProgramFamily.Grappling),
    BjjKettlebell(ProgramFamily.Grappling),
    BjjOneBell(ProgramFamily.Grappling),
    KettlebellFoundations(ProgramFamily.Beginner),
    FirstHundredSwings(ProgramFamily.Beginner),
    BeginnerFullBody(ProgramFamily.Beginner),
    GetUpPractice(ProgramFamily.Beginner),
    BodyweightBasics(ProgramFamily.Mixed),
    BodyweightEmom(ProgramFamily.Mixed),
    BellsAndBodyweight(ProgramFamily.Mixed),
    BandsAndBells(ProgramFamily.Mixed),
    CoreOnTheMat(ProgramFamily.Mixed),
    OlympicFoundations(ProgramFamily.Barbell),
    BarbellFiveByFive(ProgramFamily.Barbell),
}

internal fun builtIn(id: ProgramId, days: List<List<Block>>, daysPerWeek: Int, weeks: Int) =
    Program(ProgramKey.BuiltIn(id), days, daysPerWeek, weeks)

/** 500 swings in five ladders of 10, 15, 25 and 50: a day of 10,000 Swings. */
private val fiveHundredSwings =
    Block(BuiltInExercise.TwoHandSwing, rounds = 5, kind = BlockKind.Ladder, rungs = listOf(10, 15, 25, 50))

/** [minutes] of the Armor Building Complex, one every minute: twenty for ABF. */
private fun armorBuildingComplex(minutes: Int = 20) =
    Block(BuiltInExercise.ArmorBuildingComplex, rounds = minutes, reps = 1, kind = BlockKind.Emom, double = true)

/** [ladders] press ladders of 1 up to [top], a side. */
private fun pressLadders(ladders: Int, top: Int) =
    Block(BuiltInExercise.Press, rounds = ladders, kind = BlockKind.Ladder, rungs = (1..top).toList())

/** [reps] two-hand swings on the minute for ten minutes, after deadlifts to groove the hinge. */
private fun swingDay(reps: Int) = listOf(
    Block(BuiltInExercise.Deadlift, rounds = 2, reps = 10),
    Block(BuiltInExercise.TwoHandSwing, rounds = 10, reps = reps, kind = BlockKind.Emom),
)

/**
 * This block building from its reps up to [top], and only then a heavier bell or band: a house program's way to
 * step, so a day done in full asks a rep more rather than a bell a size up, and an EMOM keeps its minutes.
 */
private fun Block.upTo(top: Int) = withRange(RepRange(reps, top))

/** Six minutes of [exercise], [reps] at the top of each building to [top]: as long as a round on the mat. */
private fun round(exercise: Exercise, reps: Int, top: Int) =
    Block(exercise, rounds = 6, reps = reps, kind = BlockKind.Emom).upTo(top)

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
                pressLadders(ladders = 3, top = 3),
                Block(BuiltInExercise.TwoHandSwing, rounds = 5, reps = 10).upTo(15),
            ),
            listOf(
                pressLadders(ladders = 4, top = 3),
                Block(BuiltInExercise.Snatch, rounds = 5, reps = 5).upTo(8),
            ),
            listOf(
                pressLadders(ladders = 5, top = 3),
                Block(BuiltInExercise.TwoHandSwing, rounds = 5, reps = 15).upTo(20),
            ),
        ),
        daysPerWeek = 3,
        weeks = 8,
    ),
    builtIn(
        ProgramId.CleanPressEmom,
        days = listOf(
            listOf(
                Block(BuiltInExercise.Clean, rounds = 10, reps = 3, kind = BlockKind.Emom).upTo(5),
                Block(BuiltInExercise.Press, rounds = 10, reps = 3, kind = BlockKind.Emom).upTo(5),
            ),
        ),
        daysPerWeek = 3,
        weeks = 4,
    ),
    // Each built to 15 at a bell before the next: too big a jump to take at 10.
    builtIn(
        ProgramId.GobletSquatRow,
        days = listOf(
            listOf(
                Block(BuiltInExercise.GobletSquat, rounds = 3, reps = 10).upTo(15),
                Block(BuiltInExercise.Row, rounds = 3, reps = 10).upTo(15),
            ),
        ),
        daysPerWeek = 2,
        weeks = 4,
    ),
    // The 40-day workout: the basic human moves, carries among them, light and easy five days a week.
    builtIn(
        ProgramId.EasyStrength,
        days = listOf(
            listOf(
                Block(BuiltInExercise.Press, rounds = 2, reps = 5),
                Block(BuiltInExercise.Row, rounds = 2, reps = 5),
                Block(BuiltInExercise.GobletSquat, rounds = 2, reps = 5),
                Block(BuiltInExercise.TwoHandSwing, rounds = 3, reps = 15),
                Block(BuiltInExercise.GetUp, rounds = 1, reps = 1),
                Block(BuiltInExercise.SuitcaseCarry, rounds = 1, reps = 30),
            ),
        ),
        daysPerWeek = 5,
        weeks = 8,
    ),
    // Alternating the Armor Building Complex and double press ladders of 2-3-5-10 across eight weeks.
    builtIn(
        ProgramId.ArmorBuildingFormula,
        days = listOf(
            listOf(armorBuildingComplex()),
            listOf(
                Block(
                    BuiltInExercise.Press,
                    rounds = 5,
                    kind = BlockKind.Ladder,
                    rungs = listOf(2, 3, 5, 10),
                    double = true,
                ),
            ),
        ),
        daysPerWeek = 3,
        weeks = 8,
    ),
    // 500 swings a session for 20 sessions, rotating the four classic strength pairings in ladders.
    builtIn(
        ProgramId.TenThousandSwings,
        days = listOf(
            listOf(
                fiveHundredSwings,
                pressLadders(ladders = 5, top = 3),
            ),
            listOf(
                fiveHundredSwings,
                Block(BuiltInExercise.GobletSquat, rounds = 5, kind = BlockKind.Ladder, rungs = listOf(1, 2, 3)),
            ),
            listOf(
                fiveHundredSwings,
                Block(BuiltInExercise.ChinUp, rounds = 5, kind = BlockKind.Ladder, rungs = listOf(1, 2, 3)),
            ),
            listOf(
                fiveHundredSwings,
                Block(BuiltInExercise.BenchDip, rounds = 5, kind = BlockKind.Ladder, rungs = listOf(2, 3, 5)),
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
    // Every basic human move in one session, with sets of 25 swings: push, pull, hinge, squat and carry.
    builtIn(
        ProgramId.OneStopShop,
        days = listOf(
            listOf(
                Block(BuiltInExercise.TwoHandSwing, rounds = 5, reps = 25),
                Block(BuiltInExercise.Press, rounds = 2, reps = 5).upTo(8),
                Block(BuiltInExercise.Row, rounds = 2, reps = 8).upTo(12),
                Block(BuiltInExercise.Deadlift, rounds = 2, reps = 8).upTo(12),
                Block(BuiltInExercise.GobletSquat, rounds = 2, reps = 8).upTo(12),
                Block(BuiltInExercise.SuitcaseCarry, rounds = 2, reps = 30),
            ),
        ),
        daysPerWeek = 3,
        weeks = 6,
    ),
    // The least that keeps someone strong for life, three short days a week: every basic human move across the two
    // days, a carry to end each, and no set taken near its limit. One bell and the floor.
    builtIn(
        ProgramId.StrongForLife,
        days = listOf(
            listOf(
                Block(BuiltInExercise.GetUp, rounds = 3, reps = 1),
                Block(BuiltInExercise.GobletSquat, rounds = 3, reps = 5).upTo(8),
                Block(BuiltInExercise.SuitcaseCarry, rounds = 3, reps = 30),
            ),
            listOf(
                Block(BuiltInExercise.TwoHandSwing, rounds = 5, reps = 10).upTo(15),
                Block(BuiltInExercise.PushUp, rounds = 3, reps = 5).upTo(10),
                Block(BuiltInExercise.Row, rounds = 3, reps = 5).upTo(8),
                Block(BuiltInExercise.RackCarry, rounds = 2, reps = 30),
            ),
        ),
        daysPerWeek = 3,
        weeks = 8,
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
    // Two fifteen-minute days in turn: a strength circuit of three lifts, and swings and thrusters on the minute.
    builtIn(
        ProgramId.FifteenMinuteKettlebell,
        days = listOf(
            listOf(
                Block(BuiltInExercise.FrontSquat, rounds = 3, reps = 8, double = true).upTo(12),
                Block(BuiltInExercise.CleanAndPress, rounds = 3, reps = 6, double = true).upTo(12),
                Block(BuiltInExercise.Row, rounds = 3, reps = 10, double = true).upTo(15),
            ),
            listOf(
                Block(BuiltInExercise.TwoHandSwing, rounds = 8, reps = 15, kind = BlockKind.Emom).upTo(20),
                Block(BuiltInExercise.Thruster, rounds = 7, reps = 5, kind = BlockKind.Emom, double = true).upTo(6),
            ),
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
                Block(BuiltInExercise.Row, rounds = 4, reps = 8).upTo(12),
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
                round(BuiltInExercise.TwoHandSwing, reps = 15, top = 20),
                round(BuiltInExercise.Snatch, reps = 6, top = 10),
                round(BuiltInExercise.HighPull, reps = 8, top = 12),
                round(BuiltInExercise.GobletSquat, reps = 10, top = 15),
            ),
            listOf(
                round(BuiltInExercise.OneHandSwing, reps = 10, top = 15),
                round(BuiltInExercise.Clean, reps = 5, top = 8),
                round(BuiltInExercise.RackedLunge, reps = 6, top = 10),
                round(BuiltInExercise.Halo, reps = 8, top = 12),
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
                Block(BuiltInExercise.Row, rounds = 5, reps = 8).upTo(12),
                Block(BuiltInExercise.HighPull, rounds = 5, reps = 8).upTo(12),
                Block(BuiltInExercise.Clean, rounds = 5, reps = 5).upTo(8),
                Block(BuiltInExercise.OneHandSwing, rounds = 5, reps = 15).upTo(20),
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
                Block(BuiltInExercise.Halo, rounds = 3, reps = 10).upTo(15),
                Block(BuiltInExercise.GetUp, rounds = 5, reps = 1),
                Block(BuiltInExercise.Windmill, rounds = 3, reps = 5).upTo(8),
                Block(BuiltInExercise.BottomsUpPress, rounds = 3, reps = 5).upTo(8),
            ),
        ),
        daysPerWeek = 3,
        weeks = 6,
    ),
    // Twenty minutes beside BJJ, leaving something in the tank: the Armor Building Complex with a pair on the
    // minute one day; get-ups, a bottoms-up carry, windmills and dead bugs the next, slow, for the trunk and the
    // shoulder. The third session, 15:15 snatches, is the interval side's preset, which Set up plans beside it.
    builtIn(
        ProgramId.BjjKettlebell,
        days = listOf(
            listOf(armorBuildingComplex(minutes = 15)),
            listOf(
                Block(BuiltInExercise.GetUp, rounds = 2, reps = 2),
                Block(BuiltInExercise.BottomsUpCarry, rounds = 2, reps = 30),
                Block(BuiltInExercise.Windmill, rounds = 2, reps = 5).upTo(8),
                Block(BuiltInExercise.DeadBug, rounds = 2, reps = 8),
            ),
        ),
        daysPerWeek = 2,
        weeks = 6,
    ),
    // Beside BJJ with one bell, twenty minutes and something left in the tank: the squat, the press and the row
    // slow and few one day, with a carry at the side; swings on the minute, get-ups and a carry in the rack the
    // next. The mat loads the grip already, so nothing here hangs on it for long.
    builtIn(
        ProgramId.BjjOneBell,
        days = listOf(
            listOf(
                Block(BuiltInExercise.GobletSquat, rounds = 3, reps = 5).upTo(8),
                Block(BuiltInExercise.Press, rounds = 3, reps = 3).upTo(5),
                Block(BuiltInExercise.Row, rounds = 3, reps = 5).upTo(8),
                Block(BuiltInExercise.SuitcaseCarry, rounds = 2, reps = 30),
            ),
            listOf(
                Block(BuiltInExercise.TwoHandSwing, rounds = 8, reps = 10, kind = BlockKind.Emom),
                Block(BuiltInExercise.GetUp, rounds = 3, reps = 1),
                Block(BuiltInExercise.RackCarry, rounds = 2, reps = 30),
            ),
        ),
        daysPerWeek = 2,
        weeks = 6,
    ),
    // The hinge, the squat and the get-up, before anything fast.
    builtIn(
        ProgramId.KettlebellFoundations,
        days = listOf(
            listOf(
                Block(BuiltInExercise.Deadlift, rounds = 3, reps = 10).upTo(15),
                Block(BuiltInExercise.GobletSquat, rounds = 3, reps = 8).upTo(12),
                Block(BuiltInExercise.Halo, rounds = 2, reps = 6).upTo(10),
            ),
            listOf(
                Block(BuiltInExercise.TwoHandSwing, rounds = 5, reps = 10).upTo(15),
                Block(BuiltInExercise.PushUp, rounds = 2, reps = 8),
                Block(BuiltInExercise.GetUp, rounds = 3, reps = 1),
            ),
        ),
        daysPerWeek = 3,
        weeks = 4,
    ),
    // Swings on the minute: a week of 50, 80 and 100 a day, the same each week.
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
                Block(BuiltInExercise.GobletSquat, rounds = 3, reps = 8).upTo(12),
                Block(BuiltInExercise.Press, rounds = 3, reps = 5).upTo(8),
                Block(BuiltInExercise.PushUp, rounds = 3, reps = 8),
                Block(BuiltInExercise.Row, rounds = 3, reps = 8).upTo(12),
            ),
            listOf(
                Block(BuiltInExercise.Deadlift, rounds = 3, reps = 8).upTo(12),
                Block(BuiltInExercise.Clean, rounds = 3, reps = 5).upTo(8),
                Block(BuiltInExercise.TwoHandSwing, rounds = 5, reps = 10).upTo(15),
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
                Block(BuiltInExercise.InvertedRow, rounds = 10, reps = 5, kind = BlockKind.Emom),
            ),
        ),
        daysPerWeek = 3,
        weeks = 4,
    ),
    builtIn(
        ProgramId.BellsAndBodyweight,
        days = listOf(
            listOf(
                Block(BuiltInExercise.TwoHandSwing, rounds = 10, reps = 10, kind = BlockKind.Emom).upTo(15),
                Block(BuiltInExercise.PushUp, rounds = 5, reps = 10),
                Block(BuiltInExercise.GobletSquat, rounds = 3, reps = 10).upTo(15),
            ),
            listOf(
                Block(BuiltInExercise.Clean, rounds = 3, reps = 5).upTo(8),
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
                Block(BuiltInExercise.Press, rounds = 3, reps = 5).upTo(8),
                Block(BuiltInExercise.BandChestPress, rounds = 3, reps = 15).upTo(20),
                Block(BuiltInExercise.BandPullApart, rounds = 3, reps = 15).upTo(20),
                Block(BuiltInExercise.Row, rounds = 3, reps = 8).upTo(12),
                Block(BuiltInExercise.BandFacePull, rounds = 3, reps = 15).upTo(20),
            ),
            listOf(
                Block(BuiltInExercise.GobletSquat, rounds = 3, reps = 10).upTo(15),
                Block(BuiltInExercise.BandPallofPress, rounds = 3, reps = 10).upTo(15),
                Block(BuiltInExercise.Deadlift, rounds = 3, reps = 10).upTo(15),
                Block(BuiltInExercise.BandGluteBridge, rounds = 3, reps = 15).upTo(20),
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
) + barbellPrograms

/**
 * The built-ins no longer offered: each said better by another, or too hard on a shoulder, a grip or the knees to
 * be anyone's default. They stay among [builtInPrograms], since a program is stored by its id: whoever follows one
 * keeps it, and the sessions done of it keep their days.
 */
val retiredPrograms: Set<ProgramId> = setOf(
    ProgramId.DailySwingsGetUps,
    ProgramId.CleanPressEmom,
    ProgramId.LongCycleEmom,
    ProgramId.GripAndPull,
    ProgramId.BodyweightEmom,
)

/** Whether this is a built-in no longer offered ([retiredPrograms]). */
val Program.retired: Boolean get() = (key as? ProgramKey.BuiltIn)?.id in retiredPrograms

/** The built-ins on offer: all but the [retiredPrograms], and of those the one [following], which is kept. */
fun programCatalogue(following: ProgramKey? = null): List<Program> =
    builtInPrograms.filter { !it.retired || it.key == following }

/**
 * The programs to put before someone who trains on the mat or the wall, the first that suit what they own first:
 * the two made to sit beside BJJ, then the shoulders, then strength.
 */
val besideTheMat: List<ProgramId> = listOf(
    ProgramId.BjjKettlebell,
    ProgramId.BjjOneBell,
    ProgramId.ShoulderArmor,
    ProgramId.GrapplerStrength,
)

fun program(
    id: ProgramId,
): Program = builtInPrograms.first { it.key == ProgramKey.BuiltIn(id) }
