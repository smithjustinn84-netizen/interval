package dev.justinsmith.lift.domain

import kotlin.math.ceil
import kotlin.math.roundToInt

// An easier day, offered when the lifter is taxed: leave something in the tank, and come back stronger.

/** A taxed day's EMOM or density block of ballistics keeps this share of its minutes: 20 become 15. */
private const val EASIER_EMOM_SHARE = 0.75

/** A taxed day's ballistic sets lose a round when they have more than this many. */
private const val EASIER_MIN_ROUNDS = 2

/** How a taxed day rests, by tempo, against the rest set on Lift: a grind longer, the rest shorter. */
private const val GRIND_REST = 1.3
private const val BALLISTIC_REST = 0.7
private const val HOLD_REST = 0.5

/** A day this much heavier on the grip than another of its program offers that one instead. */
private const val LIGHTER_DAY_RATIO = 1.4

/**
 * The rest after a set of [block] on a taxed day, from the [base] set on Lift: a grind's longer, a ballistic lift's
 * and a hold's shorter, to the nearest [RestClock.STEP_SECONDS] within the rest timer's range.
 */
fun restFor(block: Block, base: Int): Int {
    val share = when (block.exercise.movement.tempo) {
        Tempo.Grind -> GRIND_REST
        Tempo.Ballistic -> BALLISTIC_REST
        Tempo.Hold -> HOLD_REST
    }
    val steps = (base * share / RestClock.STEP_SECONDS).roundToInt()
    return (steps * RestClock.STEP_SECONDS).coerceIn(RestClock.MIN_SECONDS, RestClock.MAX_SECONDS)
}

/**
 * [block] made easier for a taxed day: a grind a bell lighter than [at], the load it would be done at (of
 * [pairs], the bells owned two of, with two bells once any are known; else of [bells]), or the bar a step lighter,
 * no lighter than the user's [bar]; a ballistic EMOM or density block a quarter of its minutes shorter (a cap no
 * shorter than [DensityClock.MIN_MINUTES]), ballistic sets a round fewer; every block but those on the clock resting
 * by its tempo ([restFor]) against the [baseRest] set on Lift.
 */
@Suppress("detekt.LongParameterList") // What an easier block goes by.
fun Block.easier(at: Load?, bells: List<Double>, pairs: Set<Double>, bar: Double?, baseRest: Int): Block {
    val tempo = exercise.movement.tempo
    val lighter = if (tempo == Tempo.Grind) at?.lighter(double, bells, pairs, bar) else null
    val rounds = when {
        tempo != Tempo.Ballistic -> rounds
        kind == BlockKind.Emom -> ceil(rounds * EASIER_EMOM_SHARE).toInt().coerceAtLeast(1)
        kind == BlockKind.Density -> ceil(rounds * EASIER_EMOM_SHARE).toInt().coerceAtLeast(DensityClock.MIN_MINUTES)
        kind == BlockKind.Sets && rounds > EASIER_MIN_ROUNDS -> rounds - 1
        else -> rounds
    }
    val onTheClock = kind == BlockKind.Emom || kind == BlockKind.Density
    return copy(
        load = lighter ?: load,
        rounds = rounds.coerceAtMost(this.rounds),
        rest = if (onTheClock) rest else restFor(this, baseRest),
    )
}

private fun Load.lighter(double: Boolean, bells: List<Double>, pairs: Set<Double>, bar: Double?): Load? =
    when (this) {
        is Load.Bell -> {
            val owned = if (double && pairs.isNotEmpty()) bells.filter { it in pairs } else bells
            owned.filter { it < kg }.maxOrNull()?.let { copy(kg = it) }
        }

        is Load.Bar -> stepped(-1, bells, bar, emptyList())

        is Load.Bodyweight, is Load.Banded, Load.Mat -> null
    }

/**
 * This plan made easier for a taxed day, each block by [Block.easier], [loadOf] saying the load each would be done
 * at (its own, else the last used for its lift), resting against [baseRest]; null when nothing in it would change.
 */
@Suppress("detekt.LongParameterList") // What an easier day goes by.
fun SessionPlan.easier(
    loadOf: (Block) -> Load?,
    bells: List<Double>,
    pairs: Set<Double>,
    bar: Double?,
    baseRest: Int,
): SessionPlan? {
    val easier = copy(
        blocks = blocks.map { entry ->
            if (entry.skipped) {
                entry
            } else {
                entry.copy(block = entry.block.easier(loadOf(entry.block), bells, pairs, bar, baseRest))
            }
        },
    )
    return easier.takeIf { it != this }
}

/**
 * What a day of a program asks of the grip, as planned: every set's, its reps or a hold's seconds. A density block's
 * count is open, so it is planned as its EMOM would be, a round a minute.
 */
val List<Block>.plannedGripLoad: Double
    get() = sumOf { block ->
        val planned = if (block.kind == BlockKind.Density) block.copy(kind = BlockKind.Emom) else block
        planned.plannedSets().sumOf { set ->
            val held = set.exercise.measure == Measure.Time
            val load = Load.of(set.exercise.equipment, 0.0, set.double)
            SetLog(0, set.exercise, load, if (held) 0 else set.reps, set.side, 0, seconds = set.reps.takeIf { held })
                .gripLoad
        }
    }

/**
 * Of this program's days, the one (1-based) to offer in place of day [day] (1-based) on a taxed day: the one
 * lightest on the grip, when [day] asks [LIGHTER_DAY_RATIO] times its load or more; null with no such day.
 */
fun Program.lighterDayThan(day: Int): Int? {
    if (days.size < 2) return null
    val loads = days.map { it.plannedGripLoad }
    val index = (day - 1).mod(days.size)
    val lightest = loads.indices.minBy { loads[it] }
    return (lightest + 1).takeIf { lightest != index && loads[index] >= loads[lightest] * LIGHTER_DAY_RATIO }
}
