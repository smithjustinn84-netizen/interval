package dev.justinsmith.interval.workout

import dev.justinsmith.interval.timer.Phase
import dev.justinsmith.interval.timer.WorkoutState
import dev.justinsmith.interval.timer.ceilSeconds
import dev.justinsmith.interval.timer.formatClock

data class WorkoutUiState(
    val phase: Phase,
    val running: Boolean,
    val rounds: Int,
    /** Rounds whose work interval is behind us. */
    val completedRounds: Int,
    /** The round being worked, or null between work intervals. */
    val activeRound: Int?,
    val clock: String,
    val progress: Float,
    val label: String,
    val next: String,
    val meta: String,
    val summary: String,
    /** True once the workout was ended before its last round; it then shows its summary like a finished one. */
    val ended: Boolean = false,
    /** The summary's heading line: the preset's name, or "Custom", and the workout's shape. */
    val shape: String = "",
    /** Time spent, for the summary. */
    val elapsed: String = "",
    /** Offer "Save as preset" on the summary: no preset has this shape yet. */
    val canSavePreset: Boolean = false,
) {
    val isDone: Boolean get() = phase == Phase.Done || ended
    val paused: Boolean get() = !running && !isDone

    /** Ending now keeps a summary and a history entry only once a round is finished. */
    val hasProgress: Boolean get() = completedRounds > 0
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
    val finished = isDone || ended
    val paused = !running && !finished
    val rounds = config.rounds
    val completed = when (phase) {
        Phase.Prep -> 0
        Phase.Work -> round - 1
        Phase.Rest -> round
        Phase.Done -> rounds
    }
    return WorkoutUiState(
        phase = phase,
        running = running,
        rounds = rounds,
        completedRounds = completed,
        activeRound = round.takeIf { phase == Phase.Work },
        clock = formatClock(ceilSeconds(remainingMillis)),
        progress = if (isDone) 0f else progress,
        label = if (paused) {
            "Paused"
        } else {
            when (phase) {
                Phase.Prep -> "Get ready"
                Phase.Work -> "Work"
                Phase.Rest -> "Rest"
                Phase.Done -> "Done"
            }
        },
        next = when (phase) {
            Phase.Prep -> "Next: work ${formatClock(config.workSeconds)}"

            Phase.Work -> when {
                isLastRound -> "Last round — finish strong"
                config.restSeconds == 0 -> "Next: round ${round + 1} of $rounds"
                else -> "Next: rest ${formatClock(config.restSeconds)}"
            }

            Phase.Rest -> "Next: round ${round + 1} of $rounds"

            Phase.Done -> ""
        },
        meta = if (finished) {
            ""
        } else {
            "Round $round of $rounds · ${formatClock(
                ceilSeconds(sessionRemainingMillis),
            )} left"
        },
        summary = if (ended) {
            "$completed of $rounds rounds · ${formatClock(elapsedSeconds)}"
        } else {
            "$rounds round${if (rounds == 1) "" else "s"} · ${formatClock(config.totalSeconds)} total"
        },
        ended = ended,
        shape = "${presetName ?: "Custom"} · ${config.description}",
        elapsed = formatClock(elapsedSeconds),
        canSavePreset = presetName == null,
    )
}
