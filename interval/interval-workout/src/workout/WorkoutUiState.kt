package dev.justinsmith.interval.workout

import dev.justinsmith.interval.model.PerceivedEffort
import dev.justinsmith.interval.platform.HeartRate
import dev.justinsmith.interval.timer.Course
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Phase
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.WeekStanding
import dev.justinsmith.interval.timer.WorkoutEngine
import dev.justinsmith.interval.timer.WorkoutState
import dev.justinsmith.interval.timer.ceilSeconds
import dev.justinsmith.interval.timer.formatClock

/** What the line under the clock says comes next. */
sealed interface NextUp {
    /** Work of this length, after get ready or the warm-up. */
    data class Work(val clock: String) : NextUp

    /** A warm-up of this length, after get ready. */
    data class WarmUp(val clock: String) : NextUp

    /** A cool-down of this length, after the last work. */
    data class CoolDown(val clock: String) : NextUp

    /** A rest of this length. */
    data class Rest(val clock: String) : NextUp

    /** The next round's work, straight after this interval; with its [clock] on a ladder, where it differs. */
    data class Round(val round: Int, val of: Int, val clock: String? = null) : NextUp

    /** The longer rest of this length after the set. */
    data class SetRest(val clock: String) : NextUp

    /** The next set's first work, straight after this interval; with its [clock] on a ladder, where it differs. */
    data class Set(val set: Int, val of: Int, val clock: String? = null) : NextUp

    /** This work interval is the last one. */
    data object LastRound : NextUp
}

data class WorkoutUiState(
    val phase: Phase,
    val running: Boolean,
    val config: IntervalConfig,
    /**
     * 1-based: the round of its set being worked or rested after, or about to start during get ready and the
     * warm-up; the last during the cool-down.
     */
    val round: Int,
    /** Rounds whose work interval is behind us, in every set. */
    val completedRounds: Int,
    /** The round of the set being worked, or null between work intervals. */
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
    /** Every preset, so the summary's editor can say when a name or shape is taken. */
    val presets: List<Preset> = emptyList(),
    /** Keep the display awake while the timer runs: the setting as it was when the workout started. */
    val keepScreenOn: Boolean = false,
    /** The estimated calories burned so far, for the summary; null when no body weight is set. */
    val calories: Int? = null,
    /** The time the timer has run, in seconds, behind [elapsed]. */
    val elapsedSeconds: Int = 0,
    /** Where the week stands with this workout in it, once it is recorded. */
    val week: WeekStanding? = null,
    /** Ended from off screen (the notification's End) with no round done: nothing to keep, so the screen leaves. */
    val leaving: Boolean = false,
    /** How hard it felt, as said on the summary; null until said. */
    val effort: PerceivedEffort? = null,
    /** The summary's note as typed. */
    val note: String = "",
    /** Its heart rate as a watch recorded it, for the summary; null without one. */
    val heartRate: HeartRate? = null,
    /** 1-based, the set [round] is in. */
    val set: Int = 1,
) {
    /** Offer "Save as preset" on the summary: no preset has this shape yet. */
    val canSavePreset: Boolean get() = presetName == null

    /** Every round of the workout, in all its sets. */
    val rounds: Int get() = config.sets * config.rounds

    /** The rounds of one set. */
    val setRounds: Int get() = config.rounds

    val sets: Int get() = config.sets

    /** Rounds of the current set whose work is behind it. */
    val completedSetRounds: Int get() = (completedRounds - (set - 1) * setRounds).coerceIn(0, setRounds)
    val isDone: Boolean get() = phase == Phase.Done || ended
    val paused: Boolean get() = !running && !isDone

    /** Ending now keeps a summary and a history entry only once a round is finished. */
    val hasProgress: Boolean get() = completedRounds > 0

    /** The interval's last seconds are ticking down, with their beeps: the screen counts along. */
    val countingDown: Boolean get() = running && secondsLeft in 1..WorkoutEngine.COUNTDOWN_SECONDS
}

/**
 * The screen's view of an engine state. [ended] marks a workout stopped early; [presetName] is the
 * preset matching its shape, if any, among [presets]; [elapsedSeconds] is the time the timer has run.
 */
fun WorkoutState.toUiState(
    running: Boolean,
    ended: Boolean = false,
    presetName: String? = null,
    elapsedSeconds: Int = 0,
    presets: List<Preset> = emptyList(),
    keepScreenOn: Boolean = false,
    calories: Int? = null,
    week: WeekStanding? = null,
    leaving: Boolean = false,
    effort: PerceivedEffort? = null,
    note: String = "",
): WorkoutUiState {
    val secondsLeft = ceilSeconds(remainingMillis)
    return WorkoutUiState(
        phase = phase,
        running = running,
        config = config,
        round = round,
        completedRounds = completedRounds,
        activeRound = round.takeIf { phase == Phase.Work },
        clock = formatClock(secondsLeft),
        secondsLeft = secondsLeft,
        progress = if (isDone) 0f else progress,
        next = nextUp(),
        sessionLeft = formatClock(ceilSeconds(sessionRemainingMillis)),
        total = formatClock(config.totalSeconds),
        ended = ended,
        presetName = presetName,
        elapsed = formatClock(elapsedSeconds),
        elapsedSeconds = elapsedSeconds,
        week = week,
        presets = presets,
        keepScreenOn = keepScreenOn,
        calories = calories,
        leaving = leaving,
        effort = effort,
        note = note,
        set = set,
    )
}

/** What follows this interval in the course: its length, the round or the set it starts. */
private fun WorkoutState.nextUp(): NextUp? {
    if (phase == Phase.CoolDown || phase == Phase.Done) return null
    val next = Course.next(this) ?: return NextUp.LastRound
    val clock = formatClock(next.seconds)
    return when (next.phase) {
        Phase.WarmUp -> NextUp.WarmUp(clock)

        Phase.Work -> {
            // On a ladder the coming round works another length than this one: say how long.
            val differs = clock.takeIf { next.seconds != config.workOf(round) }
            when {
                phase == Phase.Prep || phase == Phase.WarmUp -> NextUp.Work(clock)
                next.set != set -> NextUp.Set(next.set, config.sets, differs)
                else -> NextUp.Round(next.round, config.rounds, differs)
            }
        }

        Phase.Rest -> NextUp.Rest(clock)

        Phase.SetRest -> NextUp.SetRest(clock)

        Phase.CoolDown -> NextUp.CoolDown(clock)

        Phase.Prep, Phase.Done -> null
    }
}

/** Rounds whose work interval is behind it, in every set: ending now keeps a workout only once there is one. */
val WorkoutState.completedRounds: Int
    get() = when (phase) {
        Phase.Prep, Phase.WarmUp -> 0
        Phase.Work -> (set - 1) * config.rounds + round - 1
        Phase.Rest, Phase.SetRest -> (set - 1) * config.rounds + round
        Phase.CoolDown, Phase.Done -> config.sets * config.rounds
    }
