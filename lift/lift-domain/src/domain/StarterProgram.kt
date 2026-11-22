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
 * What the user has available when Set up picks a starter program.
 */
data class StarterGear(
    val oneBell: Boolean = false,
    val pairOfBells: Boolean = false,
    val barbell: Boolean = false,
    val bands: Boolean = false,
    val pullUpBar: Boolean = false,
    val bodyweight: Boolean = false,
) {
    val hasAny: Boolean get() = oneBell || pairOfBells || barbell || bands || pullUpBar || bodyweight

    val hasBells: Boolean get() = oneBell || pairOfBells
}

/** The outside sport Set up is building a strength program beside, if any. */
enum class StarterSport { Grappling, Climbing, Running }

/** Whether every block in this program can be done with [gear]. */
fun Program.supportedBy(gear: StarterGear): Boolean {
    if (!gear.hasAny) return false
    return days.flatten().all { block ->
        val exercise = block.exercise
        when (exercise.equipment) {
            Equipment.Kettlebell -> if (exercise.doubled(block.double)) gear.pairOfBells else gear.hasBells
            Equipment.Barbell -> gear.barbell
            Equipment.Band -> gear.bands
            Equipment.Bodyweight, Equipment.Mat -> exercise !in needsBarOrBench || gear.pullUpBar
        }
    }
}

/**
 * Whether one kettlebell is all this program asks for: no lift done with two, none under a bar or against a
 * band, and none that needs a bar to hang from or a bench.
 */
val Program.oneBell: Boolean get() = supportedBy(StarterGear(oneBell = true))

/**
 * Whether a pair of matching kettlebells is all this program asks for: as [oneBell], with lifts done with two
 * allowed.
 */
val Program.pairOfBells: Boolean get() = supportedBy(StarterGear(pairOfBells = true))

/** The two programs made to sit beside BJJ, which Set up builds a grappler's week around. */
val grapplersPrograms: Set<ProgramId> = setOf(ProgramId.BjjKettlebell, ProgramId.BjjOneBell)

/** The family a built-in is in; null for a program the user made. */
val Program.family: ProgramFamily? get() = (key as? ProgramKey.BuiltIn)?.id?.family

private fun Program.usesEquipment(equipment: Equipment): Boolean =
    days.flatten().any { it.exercise.equipment == equipment }

private fun Program.usesBarOrBench(): Boolean =
    days.flatten().any { it.exercise in needsBarOrBench }

/**
 * The program to start on with one bell, or a [pair] of them, and [minutes] a session, resting [restSeconds] between
 * sets: of those whose longest day fits ([Program.dayMinutes]), one of the [prefer]red family first, then one for
 * beginners, then any, and of those the one that fills the time best.
 */
fun starterProgram(
    minutes: Int,
    restSeconds: Int,
    prefer: ProgramFamily? = null,
    pair: Boolean = false,
    programs: List<Program> = programCatalogue(),
): Program? = starterProgram(
    minutes = minutes,
    restSeconds = restSeconds,
    gear = StarterGear(oneBell = !pair, pairOfBells = pair),
    sport = StarterSport.Grappling.takeIf { prefer == ProgramFamily.Grappling },
    prefer = prefer,
    programs = programs,
)

/**
 * The program to start on with [gear] and [minutes] a session, resting [restSeconds] between sets, optionally
 * tailored to [sport]. Null when [gear] has no equipment or [programs] has nothing [gear] can do.
 */
fun starterProgram(
    minutes: Int,
    restSeconds: Int,
    gear: StarterGear,
    sport: StarterSport? = null,
    prefer: ProgramFamily? = null,
    programs: List<Program> = programCatalogue(bar = gear.barbell),
): Program? {
    val owned = programs.filter { it.supportedBy(gear) }
    if (owned.isEmpty()) return null
    val pool = primaryPool(owned, gear)
    val candidates = pool.map { it to it.dayMinutes(restSeconds).last }
    val fitting = candidates.filter { (_, longest) -> longest <= minutes }
    return if (fitting.isEmpty()) {
        shortestCandidate(candidates, gear)
    } else {
        pickFitting(candidates, fitting, minutes, gear, sport, prefer)
    }
}

private fun shortestCandidate(candidates: List<Pair<Program, Int>>, gear: StarterGear): Program? =
    if (!gear.hasBells && gear.barbell) {
        candidates.findBuiltIn(ProgramId.BarbellFiveByFive)
            ?: candidates.minByOrNull { (_, longest) -> longest }?.first
    } else {
        candidates.minByOrNull { (_, longest) -> longest }?.first
    }

private fun pickFitting(
    candidates: List<Pair<Program, Int>>,
    fitting: List<Pair<Program, Int>>,
    minutes: Int,
    gear: StarterGear,
    sport: StarterSport?,
    prefer: ProgramFamily?,
): Program? {
    val special = sportPick(candidates, fitting, minutes, gear, sport)
        ?: fitting.findBuiltIn(ProgramId.BarbellFiveByFive).takeIf { !gear.hasBells && gear.barbell }
    if (special != null) return special
    val preferredFamily = prefer ?: ProgramFamily.Grappling.takeIf { sport == StarterSport.Grappling }
    val families = listOfNotNull(preferredFamily, ProgramFamily.Beginner)
    val group = families.firstNotNullOfOrNull { family ->
        fitting.filter { (program, _) -> program.family == family }.takeIf { it.isNotEmpty() }
    } ?: fitting
    return group.maxByOrNull { (_, longest) -> longest }?.first
}

private fun List<Pair<Program, Int>>.findBuiltIn(id: ProgramId): Program? =
    firstOrNull { (program, _) -> program.key == ProgramKey.BuiltIn(id) }?.first

/** Narrows [owned] to the primary gear tier so e.g. a barbell or band user isn't given a floor-only fallback. */
private fun primaryPool(owned: List<Program>, gear: StarterGear): List<Program> {
    val narrowed = when {
        gear.hasBells -> owned.filter { it.usesEquipment(Equipment.Kettlebell) }
        gear.barbell -> owned.filter { it.family == ProgramFamily.Barbell }
        gear.bands -> owned.filter { it.usesEquipment(Equipment.Band) }
        gear.pullUpBar -> owned.filter { it.usesBarOrBench() }
        else -> owned
    }
    return narrowed.ifEmpty { owned }
}

private fun sportPick(
    candidates: List<Pair<Program, Int>>,
    fitting: List<Pair<Program, Int>>,
    minutes: Int,
    gear: StarterGear,
    sport: StarterSport?,
): Program? {
    if (!gear.hasBells || sport == null) return null
    return when (sport) {
        StarterSport.Grappling -> {
            val beside = if (gear.pairOfBells) ProgramId.BjjKettlebell else ProgramId.BjjOneBell
            fitting.findBuiltIn(beside)
        }

        StarterSport.Climbing -> {
            val bandsPick = fitting.findBuiltIn(ProgramId.BandsAndBells).takeIf { gear.bands }
            bandsPick ?: candidates.findBuiltIn(ProgramId.ShoulderArmor)
                .takeIf { minutes >= CLIMBING_SHOULDER_MIN_MINUTES }
        }

        StarterSport.Running -> {
            val bandsPick = fitting.findBuiltIn(ProgramId.BandsAndBells).takeIf { gear.bands }
            bandsPick ?: fitting
                .filter { (p, _) -> p.daysPerWeek <= RUNNER_MAX_STRENGTH_DAYS && p.family != ProgramFamily.Grappling }
                .maxByOrNull { (_, longest) -> longest }
                ?.first
        }
    }
}

private const val RUNNER_MAX_STRENGTH_DAYS = 2
private const val CLIMBING_SHOULDER_MIN_MINUTES = 30
