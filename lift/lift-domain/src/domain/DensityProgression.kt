package dev.justinsmith.lift.domain

// A density block steps by its rounds, counted across the sessions of it, and never by its load: the bell waits.

/**
 * What a density [block] asks for next time, [done] the first round logged for it and [rounds] the rounds it came to
 * in each session of it, oldest first, today's last: a rep more a round, at the same load, once each of the last
 * two beat the best before them by a round or more. Null with fewer than three sessions of it, its first round not
 * done at its plan, or the last two short of that.
 */
fun densityStep(block: Block, done: List<SetLog?>, rounds: List<Int>): Block? {
    if (block.kind != BlockKind.Density || doneInFull(block, done) == null || rounds.size < DENSITY_SESSIONS) {
        return null
    }
    val best = rounds.dropLast(2).max()
    return block.copy(reps = block.reps + 1).takeIf { rounds.takeLast(2).all { it > best } }
}

/**
 * The rounds each session of [program] in [sessions] came to in density blocks like [block] (its lift, its reps,
 * one bell or two, and its cap), oldest first: a session that did none has no say.
 */
fun densityRounds(block: Block, program: Program, sessions: List<LiftSession>): List<Int> =
    sessions.filter { it.program == program.key && it.day != null }.sortedBy { it.startedAtEpochMillis }
        .mapNotNull { session ->
            val day = program.day(session.day ?: return@mapNotNull null).copy(plan = session.plan)
            blocksDone(day, session.sets).filter { it.block.likeDensity(block) }.maxOfOrNull { it.rounds }
                ?.takeIf { it > 0 }
        }

/** The most rounds of a density block like [block] in any session of [program] in [sessions]; null with none. */
fun densityBest(block: Block, program: Program, sessions: List<LiftSession>): Int? =
    densityRounds(block, program, sessions).maxOrNull()

/** A density block done in a session: its [block], the [rounds] it came to and the [best] before it (null for none). */
data class DensityDone(val block: Block, val rounds: Int, val best: Int?) {
    /** Whether it beat a best standing before it: the first of a block sets none. */
    val newBest: Boolean get() = best != null && rounds > best
}

/** Each density block of [day] that [logged] did a round of, against the best of it in the [before] sessions. */
fun densityDone(day: ProgramDay, logged: List<SetLog>, before: List<LiftSession>): List<DensityDone> =
    blocksDone(day, logged).filter { it.block.kind == BlockKind.Density && it.rounds > 0 }
        .map { DensityDone(it.block, it.rounds, densityBest(it.block, day.program, before)) }

private fun Block.likeDensity(other: Block): Boolean = kind == BlockKind.Density &&
    other.kind == BlockKind.Density &&
    exercise.key == other.exercise.key &&
    reps == other.reps &&
    double == other.double &&
    rounds == other.rounds

/** A density block steps up only from the third session of it: the best of the first, then two that beat it. */
private const val DENSITY_SESSIONS = 3
