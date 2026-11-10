package dev.justinsmith.lift.domain

// The program to start someone on who has said only what they own and how long they have: Set up's choice.

/**
 * The bodyweight lifts that need something to hang from, lean on or step onto. A program with one isn't for
 * someone who has said they own a bell and nothing else.
 */
private val needsBarOrBench = setOf<Exercise>(
    BuiltInExercise.PullUp,
    BuiltInExercise.ChinUp,
    BuiltInExercise.InvertedRow,
    BuiltInExercise.BenchDip,
    BuiltInExercise.InclinePushUp,
    BuiltInExercise.BulgarianSplitSquat,
    BuiltInExercise.StepUp,
)

/**
 * Whether one kettlebell is all this program asks for: no lift done with two, none under a bar or against a
 * band, and none that needs a bar to hang from or a bench.
 */
val Program.oneBell: Boolean
    get() = days.flatten().all { block ->
        val exercise = block.exercise
        !exercise.doubled(block.double) &&
            exercise.equipment in oneBellEquipment &&
            exercise !in needsBarOrBench
    }

/**
 * Whether a pair of matching kettlebells is all this program asks for: as [oneBell], with lifts done with two
 * allowed.
 */
val Program.pairOfBells: Boolean
    get() = days.flatten().all { block ->
        block.exercise.equipment in oneBellEquipment && block.exercise !in needsBarOrBench
    }

private val oneBellEquipment = setOf(Equipment.Kettlebell, Equipment.Bodyweight, Equipment.Mat)

/** The two programs made to sit beside BJJ, which Set up builds a grappler's week around. */
val grapplersPrograms: Set<ProgramId> = setOf(ProgramId.BjjKettlebell, ProgramId.BjjOneBell)

/** The family a built-in is in; null for a program the user made. */
val Program.family: ProgramFamily? get() = (key as? ProgramKey.BuiltIn)?.id?.family

/**
 * The program to start on with one bell, or a [pair] of them, and [minutes] a session, resting [restSeconds] between
 * sets: of those whose longest day fits ([Program.dayMinutes]), one of the [prefer]red family first, then one for
 * beginners, then any, and of those the one that fills the time best. A grappler is started on the program made to
 * sit beside the mat when it fits, the week built around it: [ProgramId.BjjKettlebell] with a pair, else
 * [ProgramId.BjjOneBell]. With none that short, the shortest there is. Null only when [programs] has nothing for
 * what is owned.
 */
fun starterProgram(
    minutes: Int,
    restSeconds: Int,
    prefer: ProgramFamily? = null,
    pair: Boolean = false,
    programs: List<Program> = programCatalogue(),
): Program? {
    val owned = programs.filter { if (pair) it.pairOfBells else it.oneBell }
    val candidates = owned.map { it to it.dayMinutes(restSeconds).last }
    val fitting = candidates.filter { (_, longest) -> longest <= minutes }
    if (fitting.isEmpty()) return candidates.minByOrNull { (_, longest) -> longest }?.first
    if (prefer == ProgramFamily.Grappling) {
        val beside = if (pair) ProgramId.BjjKettlebell else ProgramId.BjjOneBell
        fitting.firstOrNull { (program, _) -> program.key == ProgramKey.BuiltIn(beside) }?.let { return it.first }
    }
    val families = listOfNotNull(prefer, ProgramFamily.Beginner)
    val group = families.firstNotNullOfOrNull { family ->
        fitting.filter { (program, _) -> program.family == family }.takeIf { it.isNotEmpty() }
    } ?: fitting
    return group.maxByOrNull { (_, longest) -> longest }?.first
}
