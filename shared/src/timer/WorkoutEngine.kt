package dev.justinsmith.interval.timer

enum class Phase { Prep, Work, Rest, Done }

/** A snapshot of a running workout. Immutable: every [WorkoutEngine] call returns a new one. */
data class WorkoutState(
    val config: IntervalConfig,
    val phase: Phase,
    /** 1-based. During prep it is the round about to start. */
    val round: Int,
    val remainingMillis: Long,
    /** The current interval's full length; grows when time is added so progress stays within 0..1. */
    val phaseMillis: Long,
) {
    val isDone: Boolean get() = phase == Phase.Done
    val isLastRound: Boolean get() = round >= config.rounds

    /** Share of the current interval still to go, 1 at its start and 0 at its end. */
    val progress: Float
        get() = if (phaseMillis <= 0) 0f else (remainingMillis.toFloat() / phaseMillis).coerceIn(0f, 1f)

    /** Time left in the whole session, the current interval included. */
    val sessionRemainingMillis: Long
        get() {
            val roundsAfter = (config.rounds - round).toLong()
            val work = config.workSeconds * 1000L
            val rest = config.restSeconds * 1000L
            val after = when (phase) {
                Phase.Prep -> config.rounds * work + (config.rounds - 1) * rest
                Phase.Work -> roundsAfter * (work + rest)
                Phase.Rest -> roundsAfter * work + (roundsAfter - 1).coerceAtLeast(0) * rest
                Phase.Done -> 0L
            }
            return (remainingMillis.coerceAtLeast(0) + after).coerceAtLeast(0)
        }
}

/** Something the user should hear or feel. The UI layer decides which ones are enabled. */
sealed interface Cue {
    /** The last three seconds of an interval: 3, 2, 1. */
    data class Countdown(val secondsLeft: Int) : Cue

    /** The midpoint of an interval longer than [WorkoutEngine.HALFWAY_MIN_SECONDS]. */
    data object Halfway : Cue

    data class PhaseStarted(val phase: Phase, val lastRound: Boolean) : Cue

    data object Finished : Cue
}

/** The result of an engine call: the new state and the cues it produced, in order. */
data class Step(val state: WorkoutState, val cues: List<Cue> = emptyList())

/**
 * The interval timer as pure functions. No clock, no coroutines: callers feed elapsed time into
 * [tick], which makes every transition testable without waiting.
 */
object WorkoutEngine {
    const val HALFWAY_MIN_SECONDS = 30
    const val ADD_TIME_MILLIS = 10_000L

    /** How far into an interval Back restarts it rather than going to the previous one. */
    const val BACK_RESTART_THRESHOLD_MILLIS = 2_000L

    /**
     * Longest step [tick] honours. A bigger gap (the app was suspended) advances the clock by this
     * much only, so a workout never silently skips intervals.
     */
    const val MAX_TICK_MILLIS = 1_000L

    fun start(config: IntervalConfig): Step {
        val phase = if (config.prepSeconds > 0) Phase.Prep else Phase.Work
        val state = enter(config, phase, round = 1)
        return Step(state, listOf(Cue.PhaseStarted(phase, lastRound = phase == Phase.Work && config.rounds == 1)))
    }

    fun tick(state: WorkoutState, elapsedMillis: Long): Step {
        if (state.isDone || elapsedMillis <= 0) return Step(state)
        val elapsed = elapsedMillis.coerceAtMost(MAX_TICK_MILLIS)
        val before = ceilSeconds(state.remainingMillis)
        val left = state.remainingMillis - elapsed
        val after = ceilSeconds(left)
        val cues = mutableListOf<Cue>()
        if (after < before) {
            if (after in 1..3) cues += Cue.Countdown(after)
            val length = (state.phaseMillis / 1000).toInt()
            val half = length / 2
            if (state.phase != Phase.Prep && length > HALFWAY_MIN_SECONDS && after == half) cues += Cue.Halfway
        }
        if (left > 0) return Step(state.copy(remainingMillis = left), cues)
        val next = advance(state)
        return Step(next.state, cues + next.cues)
    }

    /** Ends the current interval now and starts the next, as if its time had run out. */
    fun advance(state: WorkoutState): Step {
        val config = state.config
        return when (state.phase) {
            Phase.Prep -> started(config, Phase.Work, state.round)

            Phase.Work -> when {
                state.isLastRound -> Step(
                    state.copy(phase = Phase.Done, remainingMillis = 0, phaseMillis = 0),
                    listOf(Cue.Finished),
                )

                config.restSeconds == 0 -> started(config, Phase.Work, state.round + 1)

                else -> started(config, Phase.Rest, state.round)
            }

            Phase.Rest -> started(config, Phase.Work, state.round + 1)

            Phase.Done -> Step(state)
        }
    }

    /**
     * Restarts the current interval, or, within [BACK_RESTART_THRESHOLD_MILLIS] of its start, goes to
     * the one before (like a music player's previous button). Silent: no cues.
     */
    fun back(state: WorkoutState): WorkoutState {
        if (state.isDone) return state
        val config = state.config
        val intoInterval = state.phaseMillis - state.remainingMillis
        val atFirstInterval = state.phase == Phase.Prep ||
            (state.phase == Phase.Work && state.round == 1 && config.prepSeconds == 0)
        if (intoInterval > BACK_RESTART_THRESHOLD_MILLIS || atFirstInterval) {
            return enter(config, state.phase, state.round)
        }
        return when (state.phase) {
            Phase.Work -> when {
                state.round == 1 -> enter(config, Phase.Prep, 1)
                config.restSeconds == 0 -> enter(config, Phase.Work, state.round - 1)
                else -> enter(config, Phase.Rest, state.round - 1)
            }

            Phase.Rest -> enter(config, Phase.Work, state.round)

            else -> state
        }
    }

    fun addTime(state: WorkoutState, millis: Long = ADD_TIME_MILLIS): WorkoutState {
        if (state.isDone) return state
        val remaining = state.remainingMillis + millis
        return state.copy(remainingMillis = remaining, phaseMillis = maxOf(state.phaseMillis, remaining))
    }

    private fun started(config: IntervalConfig, phase: Phase, round: Int): Step {
        val state = enter(config, phase, round)
        return Step(state, listOf(Cue.PhaseStarted(phase, lastRound = phase == Phase.Work && state.isLastRound)))
    }

    private fun enter(config: IntervalConfig, phase: Phase, round: Int): WorkoutState {
        val millis = when (phase) {
            Phase.Prep -> config.prepSeconds
            Phase.Work -> config.workSeconds
            Phase.Rest -> config.restSeconds
            Phase.Done -> 0
        } * 1000L
        return WorkoutState(config, phase, round, remainingMillis = millis, phaseMillis = millis)
    }
}
