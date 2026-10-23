package dev.justinsmith.bells.domain

/**
 * The built-in barbell programs. None names a load: the first session starts on the empty bar (or what was
 * used last time), and a day done in full offers the next a step heavier.
 */
internal val barbellPrograms: List<Program> = listOf(
    // The snatch and the clean and jerk learnt from the power versions and the pulls, with a squat each day.
    builtIn(
        ProgramId.OlympicFoundations,
        days = listOf(
            listOf(
                Block(BuiltInExercise.BarbellPowerSnatch, rounds = 5, reps = 3),
                Block(BuiltInExercise.BarbellOverheadSquat, rounds = 3, reps = 5),
                Block(BuiltInExercise.BarbellSnatchPull, rounds = 3, reps = 3),
                Block(BuiltInExercise.BarbellBackSquat, rounds = 3, reps = 5),
            ),
            listOf(
                Block(BuiltInExercise.BarbellPowerClean, rounds = 5, reps = 3),
                Block(BuiltInExercise.BarbellPushPress, rounds = 3, reps = 5),
                Block(BuiltInExercise.BarbellCleanPull, rounds = 3, reps = 3),
                Block(BuiltInExercise.BarbellFrontSquat, rounds = 3, reps = 5),
            ),
            listOf(
                Block(BuiltInExercise.BarbellSnatch, rounds = 5, reps = 2),
                Block(BuiltInExercise.BarbellCleanAndJerk, rounds = 5, reps = 1),
                Block(BuiltInExercise.BarbellBackSquat, rounds = 5, reps = 3),
            ),
        ),
        daysPerWeek = 3,
        weeks = 8,
    ),
    // Five sets of five, two days in turn, squatting every session.
    builtIn(
        ProgramId.BarbellFiveByFive,
        days = listOf(
            listOf(
                Block(BuiltInExercise.BarbellBackSquat, rounds = 5, reps = 5),
                Block(BuiltInExercise.BarbellBenchPress, rounds = 5, reps = 5),
                Block(BuiltInExercise.BarbellRow, rounds = 5, reps = 5),
            ),
            listOf(
                Block(BuiltInExercise.BarbellBackSquat, rounds = 5, reps = 5),
                Block(BuiltInExercise.BarbellOverheadPress, rounds = 5, reps = 5),
                Block(BuiltInExercise.BarbellDeadlift, rounds = 1, reps = 5),
            ),
        ),
        daysPerWeek = 3,
        weeks = 12,
    ),
)
