package dev.justinsmith.interval.workout

import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Phase
import dev.justinsmith.interval.timer.WorkoutEngine
import dev.justinsmith.interval.timer.WorkoutState
import dev.justinsmith.interval.timer.ceilSeconds
import dev.justinsmith.interval.timer.formatClock

/** What the line under the clock says comes next. */
sealed interface NextUp {
    /** Work of this length, after get ready. */
    data class Work(val clock: String) : NextUp

    /** A rest of this length. */
    data class Rest(val clock: String) : NextUp

    /** The next round's work, straight after this interval. */
    data class Round(val round: Int, val of: Int) : NextUp

    /** This work interval is the last one. */
    data object LastRound : NextUp
}

data class WorkoutUiState(
    val phase: Phase,
    val running: Boolean,
    val config: IntervalConfig,
    /** 1-based: the round being worked or rested after, or about to start during get ready. */
    val round: Int,
    /** Rounds whose work interval is behind us. */
    val completedRounds: Int,
    /** The round being worked, or null between work intervals. */
    val activeRound: Int?,
    val clock: String,
    /** Whole seconds left in the interval, as the clock shows them; the last few get a countdown. */
    val secondsLeft: Int,
    val progress: Float,
    /** What follows this interval, or null once the workout is done. */
    val next: NextUp?,
    /** Time left in the whole session. */
    val sessionLeft: String,
    /** The workout's full length, for the summary. */
    val total: String,
    /** True once the workout was ended before its last round; it then shows its summary like a finished one. */
    val ended: Boolean = false,
    /** The preset matching the workout's shape, or null for a shape no preset has. */
    val presetName: String? = null,
    /** Time spent, for the summary. */
    val elapsed: String = "",
    /** Offer "Save as preset" on the summary: no preset has this shape yet. */
    val canSavePreset: Boolean = false,
) {
    val rounds: Int get() = config.rounds
    val isDone: Boolean get() = phase == Phase.Done || ended
    val paused: Boolean get() = !running && !isDone

    /** Ending now keeps a summary and a history entry only once a round is finished. */
    val hasProgress: Boolean get() = completedRounds > 0

    /** The interval's last seconds are ticking down, with their beeps: the screen counts along. */
    val countingDown: Boolean get() = running && secondsLeft in 1..WorkoutEngine.COUNTDOWN_SECONDS
}

/**
 * The screen's view of an engine state. [ended] marks a workout stopped early; [presetName] is the
 * preset matching its shape, if any; [elapsedSeconds] is the time the timer has run.
 */
fun WorkoutState.toUiState(
    running: Boolean,
    ended: Boolean = false,
    presetName: String? = null,
    elapsedSeconds: Int = 0,
): WorkoutUiState {
    val rounds = config.rounds
    val secondsLeft = ceilSeconds(remainingMillis)
    return WorkoutUiState(
        phase = phase,
        running = running,
        config = config,
        round = round,
        completedRounds = when (phase) {
            Phase.Prep -> 0
            Phase.Work -> round - 1
            Phase.Rest -> round
            Phase.Done -> rounds
        },
        activeRound = round.takeIf { phase == Phase.Work },
        clock = formatClock(secondsLeft),
        secondsLeft = secondsLeft,
        progress = if (isDone) 0f else progress,
        next = when (phase) {
            Phase.Prep -> NextUp.Work(formatClock(config.workSeconds))

            Phase.Work -> when {
                isLastRound -> NextUp.LastRound
                config.restSeconds == 0 -> NextUp.Round(round + 1, rounds)
                else -> NextUp.Rest(formatClock(config.restSeconds))
            }

            Phase.Rest -> NextUp.Round(round + 1, rounds)

            Phase.Done -> null
        },
        sessionLeft = formatClock(ceilSeconds(sessionRemainingMillis)),
        total = formatClock(config.totalSeconds),
        ended = ended,
        presetName = presetName,
        elapsed = formatClock(elapsedSeconds),
        canSavePreset = presetName == null,
    )
}
