package dev.justinsmith.interval.timer

import dev.justinsmith.interval.model.Sport

/**
 * One move of a [Flow]: [name]d, done for [seconds], on each side in turn when [sided], and explained in one line
 * by its [cue]. [held] is a position kept still; one that isn't is moved through for the time. Its words are data,
 * like a preset's name, so the app can say them aloud.
 */
data class Move(
    val name: String,
    val seconds: Int,
    val cue: String,
    val sided: Boolean = false,
    val held: Boolean = true,
) {
    /** The time it takes in all: both sides and the change between them, of one with sides. */
    val totalSeconds: Int get() = if (sided) 2 * seconds + Flow.SIDE_CHANGE_SECONDS else seconds
}

/** When a flow is for: before training, or after it and on a day off. */
enum class FlowUse { Before, After }

/** The side of a [Move] with sides: the left first. */
enum class FlowSide { Left, Right }

/**
 * A mobility session followed along: [moves] one after another, each for its time, with a few seconds to change
 * sides and to get into the next. It runs on the workout screen as the sequence [config] is, a block a move and a
 * round a side, and a finished one is known again by that shape ([matching]). [key] names it on a route; [name]
 * and [note] ("no bell") are its words, data like a built-in preset's.
 */
data class Flow(
    val key: String,
    val name: String,
    val use: FlowUse,
    val moves: List<Move>,
    val note: String? = null,
) {
    init {
        require(moves.size in 1..IntervalConfig.MAX_BLOCKS) { "${moves.size} moves" }
    }

    /** The workout it runs as, with the default get-ready countdown: see [config]. */
    val config: IntervalConfig = IntervalConfig(
        activity = Activity.Mobility,
        blockRestSeconds = MOVE_CHANGE_SECONDS,
    ).withBlocks(
        moves.map {
            Block(
                workSeconds = it.seconds,
                restSeconds = if (it.sided) SIDE_CHANGE_SECONDS else 0,
                rounds = if (it.sided) 2 else 1,
            )
        },
    )

    /** The workout it runs as after a get-ready countdown of [prepSeconds], the user's own setting. */
    fun config(prepSeconds: Int): IntervalConfig = config.copy(prepSeconds = prepSeconds)

    /** The move of [block] (0-based) and the side [round] (1-based) of it is done on. */
    fun step(block: Int, round: Int): FlowStep {
        val index = block.coerceIn(moves.indices)
        val move = moves[index]
        val side = if (move.sided) FlowSide.entries[(round - 1).coerceIn(0, 1)] else null
        return FlowStep(move, index + 1, moves.size, side)
    }

    companion object {
        /** The seconds to change sides in, and to get into the next move. */
        const val SIDE_CHANGE_SECONDS = 5
        const val MOVE_CHANGE_SECONDS = 10
    }
}

/** Where a flow stands: its [move], the [number]th [of] them, on [side] of one with sides. */
data class FlowStep(val move: Move, val number: Int, val of: Int, val side: FlowSide?)

/** The flow [config] is, by its shape: one run or saved as a workout is known again this way. */
fun List<Flow>.matching(config: IntervalConfig): Flow? =
    if (config.activity == Activity.Mobility) firstOrNull { it.config.sameShape(config) } else null

/** The flow a route's [key] names, or null for one this build has none of. */
fun List<Flow>.keyed(key: String?): Flow? = firstOrNull { it.key == key }

/**
 * The move an interval of [phase] is for, by the [block] and [round] the course gives it: the one worked, or,
 * in a change-over (the get-ready, the change of sides, the rest after a move, which the course counts with the
 * move behind it), the one about to start. Null once none is left.
 */
fun Flow.stepFor(phase: Phase, block: Int, round: Int): FlowStep? = when (phase) {
    Phase.Work -> step(block, round)
    Phase.Rest -> step(block, round + 1)
    Phase.SetRest -> moves.getOrNull(block + 1)?.let { step(block + 1, 1) }
    Phase.Prep, Phase.WarmUp -> step(0, 1)
    Phase.CoolDown, Phase.Done -> null
}

/** The move [state] is in, or the one it is about to start: see [stepFor]. */
fun Flow.stepAt(state: WorkoutState): FlowStep? = stepFor(state.phase, state.block, state.round)

/** The step after [step]: its other side, the next move, or null after the last. */
fun Flow.stepAfter(step: FlowStep): FlowStep? = when {
    step.side == FlowSide.Left -> step.copy(side = FlowSide.Right)
    step.number < moves.size -> step(step.number, 1)
    else -> null
}

/** The name [config] goes by: the preset of these with its shape, else the flow it is, else none. */
fun List<Preset>.nameOf(config: IntervalConfig): String? =
    matching(config)?.name ?: builtInFlows.matching(config)?.name

/** The flow to do before a day of [sport]: the wrists before the wall, the hips and shoulders before the rest. */
fun flowBefore(sport: Sport): Flow =
    builtInFlows.first { it.key == if (sport == Sport.Climbing) WRISTS_KEY else HIPS_KEY }

internal const val HIPS_KEY = "hips-shoulders"
internal const val WRISTS_KEY = "wrists-fingers"
