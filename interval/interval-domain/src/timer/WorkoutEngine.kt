package dev.justinsmith.interval.timer

/**
 * The parts of a workout in the order they run: get ready, a warm-up, the rounds of work and rest, a
 * cool-down. A part of no length is left out.
 */
enum class Phase { Prep, WarmUp, Work, Rest, CoolDown, Done }

/** A snapshot of a running workout. Immutable: every [WorkoutEngine] call returns a new one. */
data class WorkoutState(
    val config: IntervalConfig,
    val phase: Phase,
    /** 1-based. During prep and the warm-up it is the round about to start, during the cool-down the last. */
    val round: Int,
    val remainingMillis: Long,
    /** The current interval's full length; grows when time is added so progress stays within 0..1. */
    val phaseMillis: Long,
    /** The current interval's [Cue.Halfway] has played: added time can't bring it back. */
    val halfwayCued: Boolean = false,
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
            val work = config.workSeconds * MILLIS_PER_SECOND
            val rest = config.restSeconds * MILLIS_PER_SECOND
            val warmUp = config.warmUpSeconds * MILLIS_PER_SECOND
            val coolDown = config.coolDownSeconds * MILLIS_PER_SECOND
            val rounds = config.rounds * work + (config.rounds - 1) * rest
            val after = when (phase) {
                Phase.Prep -> warmUp + rounds + coolDown
                Phase.WarmUp -> rounds + coolDown
                Phase.Work -> roundsAfter * (work + rest) + coolDown
                Phase.Rest -> roundsAfter * work + (roundsAfter - 1).coerceAtLeast(0) * rest + coolDown
                Phase.CoolDown, Phase.Done -> 0L
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

/**
 * A change of phase ahead of a running workout: what starts, in which round, and how long from now. The
 * platform schedules a notification for each when the app leaves the screen ([WorkoutEngine.upcomingChanges]).
 */
data class UpcomingChange(
    val phase: Phase,
    /** 1-based, like [WorkoutState.round]; for [Phase.Done] the last round. */
    val round: Int,
    val offsetMillis: Long,
)

/**
 * The result of an engine call: the new state and the cues it produced, in order. For a [WorkoutEngine.tick],
 * also the time it moved the workout on by, [activeMillis], which stops counting at the workout's end, and
 * the part of that spent in work intervals, [workMillis], for the calorie estimate.
 */
data class Step(
    val state: WorkoutState,
    val cues: List<Cue> = emptyList(),
    val activeMillis: Long = 0,
    val workMillis: Long = 0,
)

/**
 * The interval timer as pure functions. No clock, no coroutines: callers feed elapsed time into
 * [tick], which makes every transition testable without waiting.
 */
object WorkoutEngine {
    /** Each of an interval's last this-many seconds gets a [Cue.Countdown]. */
    const val COUNTDOWN_SECONDS = 3
    const val HALFWAY_MIN_SECONDS = 30
    const val ADD_TIME_MILLIS = 10_000L

    /** How far into an interval Back restarts it rather than going to the previous one. */
    const val BACK_RESTART_THRESHOLD_MILLIS = 2_000L

    fun start(config: IntervalConfig): Step = started(
        config,
        when {
            config.prepSeconds > 0 -> Phase.Prep
            config.warmUpSeconds > 0 -> Phase.WarmUp
            else -> Phase.Work
        },
        round = 1,
    )

    /**
     * Moves the workout on by [elapsedMillis], however long: the tenth of a second between two ticks, or
     * the minutes the app spent suspended, through as many intervals as that covers, up to the end of
     * the workout. Time that runs past the end is not counted ([Step.activeMillis]).
     *
     * The cues are what the user needs to hear now, not a replay of everything that would have played
     * in between: the current interval's own cues (its countdown, its halfway) and, when the step enters
     * other intervals, the start cue of the last one entered, [Cue.Finished] if that is the end. So a
     * tick that crosses one interval's end cues exactly as before, while a return from forty seconds away
     * hears the one phase it is now in.
     */
    fun tick(state: WorkoutState, elapsedMillis: Long): Step {
        if (state.isDone || elapsedMillis <= 0) return Step(state)
        var current = state
        var left = elapsedMillis
        var activeMillis = 0L
        var workMillis = 0L
        val first = mutableListOf<Cue>()
        // The cues of the latest interval entered, with what has happened in it since; null while still
        // in the interval the step began in.
        var latest: List<Cue>? = null
        while (left > 0 && !current.isDone) {
            val used = minOf(left, current.remainingMillis)
            left -= used
            activeMillis += used
            if (current.phase == Phase.Work) workMillis += used
            val step = runDown(current, used)
            current = step.state
            if (latest == null) first += step.cues else latest = latest + step.cues
            if (current.remainingMillis <= 0) {
                val next = advance(current)
                current = next.state
                latest = next.cues
            }
        }
        return Step(current, first + latest.orEmpty(), activeMillis, workMillis)
    }

    /** [elapsed] of the current interval, no more than is left of it: the clock and its cues. */
    private fun runDown(state: WorkoutState, elapsed: Long): Step {
        val before = ceilSeconds(state.remainingMillis)
        val left = state.remainingMillis - elapsed
        val after = ceilSeconds(left)
        val cues = mutableListOf<Cue>()
        var halfwayCued = state.halfwayCued
        if (after < before) {
            // The countdown plays again after added time, as it should: those are the seconds left.
            if (after in 1..COUNTDOWN_SECONDS) cues += Cue.Countdown(after)
            val length = (state.phaseMillis / MILLIS_PER_SECOND).toInt()
            val hasHalfway = state.phase != Phase.Prep && length > HALFWAY_MIN_SECONDS
            if (hasHalfway && !halfwayCued && after == length / 2) {
                cues += Cue.Halfway
                halfwayCued = true
            }
        }
        return Step(state.copy(remainingMillis = left, halfwayCued = halfwayCued), cues)
    }

    /** Ends the current interval now and starts the next, as if its time had run out. */
    fun advance(state: WorkoutState): Step {
        val config = state.config
        return when (state.phase) {
            Phase.Prep -> started(config, if (config.warmUpSeconds > 0) Phase.WarmUp else Phase.Work, state.round)

            Phase.WarmUp -> started(config, Phase.Work, state.round)

            Phase.Work -> when {
                state.isLastRound && config.coolDownSeconds > 0 -> started(config, Phase.CoolDown, state.round)
                state.isLastRound -> finished(state)
                config.restSeconds == 0 -> started(config, Phase.Work, state.round + 1)
                else -> started(config, Phase.Rest, state.round)
            }

            Phase.Rest -> started(config, Phase.Work, state.round + 1)

            Phase.CoolDown -> finished(state)

            Phase.Done -> Step(state)
        }
    }

    private fun finished(state: WorkoutState): Step =
        Step(state.copy(phase = Phase.Done, remainingMillis = 0, phaseMillis = 0), listOf(Cue.Finished))

    /**
     * Restarts the current interval, or, within [BACK_RESTART_THRESHOLD_MILLIS] of its start, goes to
     * the one before (like a music player's previous button). Silent: no cues.
     */
    fun back(state: WorkoutState): WorkoutState {
        if (state.isDone) return state
        val config = state.config
        val intoInterval = state.phaseMillis - state.remainingMillis
        val before = before(state)
        if (intoInterval > BACK_RESTART_THRESHOLD_MILLIS || before == null) {
            return enter(config, state.phase, state.round)
        }
        return enter(config, before.first, before.second)
    }

    /** The interval that runs before the current one, as its phase and round; null for the first. */
    private fun before(state: WorkoutState): Pair<Phase, Int>? {
        val config = state.config
        val beforeRounds = when {
            config.warmUpSeconds > 0 -> Phase.WarmUp
            config.prepSeconds > 0 -> Phase.Prep
            else -> null
        }
        return when (state.phase) {
            Phase.Prep, Phase.Done -> null

            Phase.WarmUp -> if (config.prepSeconds > 0) Phase.Prep to 1 else null

            Phase.Work -> when {
                state.round == 1 -> beforeRounds?.let { it to 1 }
                config.restSeconds == 0 -> Phase.Work to state.round - 1
                else -> Phase.Rest to state.round - 1
            }

            Phase.Rest, Phase.CoolDown -> Phase.Work to state.round
        }
    }

    /**
     * Every change of phase still to come, nearest first, as the workout stands at [state]: the end of the
     * current interval, then each interval after it, down to [Phase.Done]. Offsets count from now, so the
     * first is the time left in the current interval, and the last is [WorkoutState.sessionRemainingMillis].
     * Pure: what a phone that is about to suspend the app can schedule in its stead. Empty once done.
     */
    fun upcomingChanges(state: WorkoutState): List<UpcomingChange> {
        val changes = mutableListOf<UpcomingChange>()
        var current = state
        var offset = 0L
        while (!current.isDone) {
            offset += current.remainingMillis.coerceAtLeast(0)
            current = advance(current).state
            changes += UpcomingChange(current.phase, current.round, offset)
        }
        return changes
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
            Phase.WarmUp -> config.warmUpSeconds
            Phase.Work -> config.workSeconds
            Phase.Rest -> config.restSeconds
            Phase.CoolDown -> config.coolDownSeconds
            Phase.Done -> 0
        } * MILLIS_PER_SECOND
        return WorkoutState(config, phase, round, remainingMillis = millis, phaseMillis = millis)
    }
}
