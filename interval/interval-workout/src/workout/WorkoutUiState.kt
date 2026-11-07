package dev.justinsmith.interval.workout

import dev.justinsmith.interval.model.PerceivedEffort
import dev.justinsmith.interval.platform.HeartRate
import dev.justinsmith.interval.timer.Course
import dev.justinsmith.interval.timer.Flow
import dev.justinsmith.interval.timer.FlowStep
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Phase
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.WeekStanding
import dev.justinsmith.interval.timer.WorkoutEngine
import dev.justinsmith.interval.timer.WorkoutState
import dev.justinsmith.interval.timer.ceilSeconds
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.timer.matching
import dev.justinsmith.interval.timer.stepAfter
import dev.justinsmith.interval.timer.stepAt
import dev.justinsmith.interval.ui.SnatchAsk

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

    /** The next block's first work (1-based [block] of [of]), straight after this interval, with its length. */
    data class Block(val block: Int, val of: Int, val clock: String) : NextUp

    /** The rest of this length between this block and the next, [block] (1-based). */
    data class BlockRest(val block: Int, val clock: String) : NextUp

    /** This work interval is the last one. */
    data object LastRound : NextUp
}

/**
 * A flow as it stands on the workout screen: its [name], how many [of] its moves there are and how many are
 * [done], both sides of one with sides; the [step] held or, in a change-over, about to start (null once it is
 * over); and, while one is held, the step [next] after it, null in the last.
 */
data class FlowNow(val name: String, val of: Int, val done: Int, val step: FlowStep?, val next: FlowStep?)

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
    /** "About how many snatches a set?" on the summary, for a workout of kettlebell snatches; null for any other. */
    val snatches: SnatchAsk? = null,
    /** 1-based, the set [round] is in. */
    val set: Int = 1,
    /** 0-based, the block of the config that [set] is in. */
    val block: Int = 0,
    /** In the rest between one block and the next. */
    val inBlockRest: Boolean = false,
    /** The flow the workout is, by its shape, with where it stands; null for any other workout. */
    val flow: FlowNow? = null,
) {
    /** What the summary counts, done and in all: a flow's moves, any other workout's rounds. */
    val countDone: Int get() = flow?.done ?: completedRounds
    val countOf: Int get() = flow?.of ?: rounds

    /** Offer "Save as preset" on the summary: no preset has this shape yet. */
    val canSavePreset: Boolean get() = presetName == null

    /** Every round of the workout, in all its sets and blocks. */
    val rounds: Int get() = Course.of(config).count { it.phase == Phase.Work }

    /** The rounds of one set of the current block. */
    val setRounds: Int get() = config.blocks[block].rounds

    /** The sets of the current block. */
    val sets: Int get() = config.blocks[block].sets

    /** The blocks of the workout, 1 for a workout of one. */
    val blocks: Int get() = config.blocks.size

    /** Rounds of the current set whose work is behind it. */
    val completedSetRounds: Int
        get() {
            val before = Course.of(config).count {
                it.phase == Phase.Work && (it.block < block || (it.block == block && it.set < set))
            }
            return (completedRounds - before).coerceIn(0, setRounds)
        }
    val isDone: Boolean get() = phase == Phase.Done || ended
    val paused: Boolean get() = !running && !isDone

    /** Ending now keeps a summary and a history entry only once a round is finished. */
    val hasProgress: Boolean get() = completedRounds > 0

    /** The interval's last seconds are ticking down, with their beeps: the screen counts along. */
    val countingDown: Boolean get() = running && secondsLeft in 1..WorkoutEngine.COUNTDOWN_SECONDS
}

/**
 * The screen's view of an engine state. [ended] marks a workout stopped early; [presetName] is the
 * preset matching its shape, if any, among [presets]; [elapsedSeconds] is the time the timer has run. The flow it is,
 * if it is one of [flows], says the move.
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
    flows: List<Flow> = emptyList(),
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
        block = block,
        inBlockRest = inBlockRest,
        flow = flows.matching(config)?.let { flowNow(it) },
    )
}

/** Where this state stands in [flow]: the move held or coming, and the moves behind it. */
private fun WorkoutState.flowNow(flow: Flow): FlowNow {
    val step = flow.stepAt(this)
    return FlowNow(
        name = flow.name,
        of = flow.moves.size,
        // The step held, or coming, has every move before it done; with none left, all are.
        done = step?.let { it.number - 1 } ?: flow.moves.size,
        step = step,
        next = step?.takeIf { phase == Phase.Work }?.let(flow::stepAfter),
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
            val differs = clock.takeIf { next.seconds != shape.workOf(round) }
            val coming = config.blocks[next.block]
            when {
                phase == Phase.Prep || phase == Phase.WarmUp -> NextUp.Work(clock)
                next.block != block -> NextUp.Block(next.block + 1, config.blocks.size, clock)
                next.set != set -> NextUp.Set(next.set, coming.sets, differs)
                else -> NextUp.Round(next.round, coming.rounds, differs)
            }
        }

        Phase.Rest -> NextUp.Rest(clock)

        Phase.SetRest -> if (config.isBlockRest(
                next,
            )
        ) {
            NextUp.BlockRest(next.block + 2, clock)
        } else {
            NextUp.SetRest(clock)
        }

        Phase.CoolDown -> NextUp.CoolDown(clock)

        Phase.Prep, Phase.Done -> null
    }
}

/** Rounds whose work interval is behind it, in every set: ending now keeps a workout only once there is one. */
val WorkoutState.completedRounds: Int
    get() = when (phase) {
        Phase.Prep, Phase.WarmUp -> 0
        Phase.Work, Phase.Rest, Phase.SetRest -> Course.workBehind(this)
        Phase.CoolDown, Phase.Done -> Course.of(config).count { it.phase == Phase.Work }
    }
