package dev.justinsmith.interval.workout

import dev.justinsmith.interval.model.BodySettings
import dev.justinsmith.interval.model.CapRound
import dev.justinsmith.interval.model.CourseSpan
import dev.justinsmith.interval.model.HistoryEntry
import dev.justinsmith.interval.model.PerceivedEffort
import dev.justinsmith.interval.model.SnatchCount
import dev.justinsmith.interval.model.WorkoutProgress
import dev.justinsmith.interval.model.bestRounds
import dev.justinsmith.interval.model.roundsIn
import dev.justinsmith.interval.model.scores
import dev.justinsmith.interval.model.withoutLastIn
import dev.justinsmith.interval.platform.DEFAULT_MAX_BPM
import dev.justinsmith.interval.platform.HR_SAMPLE_SECONDS
import dev.justinsmith.interval.platform.HeartRate
import dev.justinsmith.interval.platform.WorkoutStatus
import dev.justinsmith.interval.platform.heartLoad
import dev.justinsmith.interval.platform.recovery
import dev.justinsmith.interval.timer.Flow
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.MILLIS_PER_SECOND
import dev.justinsmith.interval.timer.Phase
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.Step
import dev.justinsmith.interval.timer.WeekStanding
import dev.justinsmith.interval.timer.WorkoutOutline
import dev.justinsmith.interval.timer.WorkoutState
import dev.justinsmith.interval.timer.ceilSeconds
import dev.justinsmith.interval.timer.matching
import dev.justinsmith.interval.timer.nameOf
import dev.justinsmith.interval.timer.stepAt

/**
 * One workout as it stands in [WorkoutSession]: where the engine is, whether the timer runs, the time it has run,
 * its course on the wall clock, and, once it is recorded, its History entry and what the summary says of it. Each
 * change is a new value, worked out here from the moment it happens ([turned], [stepped]); the session does what
 * the change asks of the world (cues, writes, the notification).
 */
data class WorkoutRun(
    /** The route's: the saved progress is this workout's only when the two match. */
    val id: Long,
    val engine: WorkoutState,
    val running: Boolean,
    /** Stopped before its last round, by the screen's End or the notification's. */
    val ended: Boolean = false,
    /** Ended from off screen with nothing to keep: the screen leaves. */
    val leaving: Boolean = false,
    /** Where the week stands with this workout in it, from the moment it is recorded; for the summary. */
    val week: WeekStanding? = null,
    /** Time the timer has run, pauses excluded: skips shorten it, added time lengthens it. */
    val activeMillis: Long = 0,
    /** Of [activeMillis], the time spent in work intervals, for the calorie estimate. */
    val workMillis: Long = 0,
    /** When the workout began, and its course since on the wall clock: the stretches closed, and the open one. */
    val startedAt: Long,
    val spans: List<CourseSpan> = emptyList(),
    val openSince: Long = startedAt,
    val openPhase: Phase? = engine.phase,
    /** The uid the finished workout was recorded under, and how it felt as the summary says. */
    val entryUid: String? = null,
    val effort: PerceivedEffort? = null,
    val note: String = "",
    /** About how many snatches a set, as counted on the summary; null until counted. */
    val snatches: SnatchCount? = null,
    /** The rounds done in its time caps, each as it was done. */
    val capRounds: List<CapRound> = emptyList(),
    /** The summary's words for the health store's note, as the screen put them. */
    val healthNotes: String? = null,
    /** Its heart rate as a watch recorded it in the health store, or live from the sensor. */
    val heartRate: HeartRate? = null,
    /** 5-second BPM samples recorded live by the paired sensor during this run. */
    val heartRateSamples: List<Int>? = null,
    /**
     * The samples so far while it runs, one every five seconds from [startedAt] (0 where none was read), for the
     * trace under the ring and for a process killed under it to come back to; empty with no sensor.
     */
    val liveSamples: List<Int> = emptyList(),
    /** Read once at start, like the configuration: a setting changed mid-workout applies next time. */
    val keepScreenOn: Boolean = false,
    val body: BodySettings = BodySettings(),
) {
    val config: IntervalConfig get() = engine.config

    /** Finished, ended early or left: the summary shows, or the screen goes, and the controls do nothing. */
    val over: Boolean get() = engine.isDone || ended || leaving

    val activeSeconds: Int get() = activeMillis.roundedSeconds()
    val workSeconds: Int get() = workMillis.roundedSeconds()

    /** The estimated calories burned so far; null without a body weight. */
    val calories: Int? get() = body.calories(workSeconds, activeSeconds - workSeconds, config.activity)

    /** Something was said of the finished workout on its summary. */
    val reflected: Boolean get() = entryUid != null && (effort != null || note.isNotBlank())

    /**
     * Closes the stretch of the course under way at [now] and opens one of [phase], or a pause for null. A tick
     * that crossed several intervals while the app was away closes one stretch for them all.
     */
    fun turned(phase: Phase?, now: Long): WorkoutRun = copy(
        spans = if (now > openSince) spans + CourseSpan(openPhase, openSince, now) else spans,
        openSince = now,
        openPhase = phase,
    )

    /**
     * The engine moved by [step] at [now]: its time counted, a new interval begun on the course, or the
     * workout over and the timer stopped.
     */
    fun stepped(step: Step, now: Long): WorkoutRun {
        val moved = copy(
            engine = step.state,
            activeMillis = activeMillis + step.activeMillis,
            workMillis = workMillis + step.workMillis,
        )
        return when {
            step.state.isDone -> moved.copy(running = false)
            moved.turnedFrom(engine) -> moved.turned(step.state.phase, now)
            else -> moved
        }
    }

    /**
     * A new interval since [before]: a change of course, which the clock running down within one is not. Another
     * round, set or block counts too, since work can run straight into work.
     */
    fun turnedFrom(before: WorkoutState): Boolean = engine.phase != before.phase || engine.round != before.round ||
        engine.set != before.set || engine.block != before.block

    /** Where it stands at [now], saved for a process killed before the next change to come back to. */
    fun toProgress(now: Long): WorkoutProgress = WorkoutProgress(
        id = id,
        state = engine,
        running = running,
        ended = ended,
        activeMillis = activeMillis,
        workMillis = workMillis,
        week = week,
        atEpochMillis = now,
        startedAtEpochMillis = startedAt,
        spans = spans,
        entryUid = entryUid,
        effort = effort,
        note = note,
        snatches = snatches,
        capRounds = capRounds,
        heartRateSamples = liveSamples,
    )

    /**
     * Each work interval so far as a share of [liveSamples]' length, for the trace to shade: the stretches of work
     * closed, and the one under way up to the last sample.
     */
    val liveMarks: List<ClosedFloatingPointRange<Float>>
        get() {
            val length = liveSamples.size * HR_SAMPLE_SECONDS * MILLIS_PER_SECOND
            if (length <= 0) return emptyList()
            fun share(epochMillis: Long) = ((epochMillis - startedAt).toFloat() / length).coerceIn(0f, 1f)
            val closed = spans.filter { it.phase == Phase.Work }
                .map { share(it.startEpochMillis)..share(it.endEpochMillis) }
            val open = if (openPhase == Phase.Work && !over) listOf(share(openSince)..1f) else emptyList()
            return (closed + open).filter { it.endInclusive > it.start }
        }

    /**
     * Each rest of its course as the seconds from [startedAt] it ran over, first to last: from a stretch of work's
     * end to the next one's start, a pause between them part of it. The last work's has none: nothing shows it
     * was rested through.
     */
    val rests: List<IntRange>
        get() = spans.zipWithNext().mapNotNull { (work, next) ->
            if (work.phase != Phase.Work || next.phase == Phase.Work) return@mapNotNull null
            val resumed = spans.firstOrNull { it.phase == Phase.Work && it.startEpochMillis >= next.startEpochMillis }
                ?: return@mapNotNull null
            val from = ((next.startEpochMillis - startedAt) / MILLIS_PER_SECOND).toInt()
            val to = ((resumed.startEpochMillis - startedAt) / MILLIS_PER_SECOND).toInt()
            (from until to).takeIf { !it.isEmpty() }
        }

    /** The rounds done in the time cap it is in; null outside one. */
    val roundsInCap: Int? get() = if (engine.shape.capped) capRounds.roundsIn(engine.block) else null

    /** A round is done now: kept, in the cap it is in, at the time into it. Only in a cap's work, while it runs. */
    fun roundDone(): WorkoutRun {
        if (over || !running || !engine.inCap) return this
        return copy(capRounds = capRounds + CapRound(engine.block, engine.phaseMillis - engine.remainingMillis))
    }

    /** The last round done in the cap it is in, taken back, as a tap by mistake is. Only in a cap's work. */
    fun roundUndone(): WorkoutRun = if (over ||
        !engine.inCap
    ) {
        this
    } else {
        copy(capRounds = capRounds.withoutLastIn(engine.block))
    }

    /**
     * What the notification shows of it: the phase, whether it is paused, the clock, the round and the bar, and the
     * move, when it is one of [flows].
     */
    fun status(flows: List<Flow>): WorkoutStatus {
        val shown = engine.toUiState(running = running, ended = ended)
        val flow = flows.matching(config)
        return WorkoutStatus(
            shown.phase,
            shown.paused,
            shown.clock,
            shown.round,
            shown.setRounds,
            WorkoutOutline.of(engine),
            shown.set,
            shown.sets,
            shown.block,
            engine.config.blocks.takeIf { it.size > 1 }.orEmpty(),
            shown.inBlockRest,
            flow,
            flow?.stepAt(engine),
            roundsInCap.takeIf { engine.inCap },
            config.blocks.indices.filterTo(mutableSetOf()) { config.blocks[it].capped },
        )
    }

    /**
     * The screen's view of it, its shape named after the one of [presets] it matches, or the one of [flows] it is,
     * and its time caps beside the best of the same workout in [history], its own entry left out: [bests], which a
     * caller ticking it keeps while the history is the same (see [capBests]).
     */
    fun toUiState(
        presets: List<Preset>,
        flows: List<Flow>,
        history: List<HistoryEntry> = emptyList(),
        bests: Map<Int, Int> = capBests(history),
    ): WorkoutUiState =
        engine.toUiState(
            running = running,
            ended = ended,
            presetName = presets.nameOf(config, flows),
            flows = flows,
            elapsedSeconds = activeSeconds,
            presets = presets,
            keepScreenOn = keepScreenOn,
            calories = calories,
            week = week,
            leaving = leaving,
            effort = effort,
            note = note,
        ).copy(
            workSeconds = workSeconds,
            heartRate = heartRate,
            heartRateSamples = heartRateSamples,
            maxHeartRate = maxHeartRate,
            liveSamples = liveSamples,
            liveMarks = liveMarks,
            recovery = heartRateSamples?.let { recovery(it, rests) },
            heartLoad = heartRateSamples
                ?.let { heartLoad(it, maxHeartRate, restingBpm = body.restingHeartRate) }
                ?.takeIf { it > 0 },
        ).withCaps(bests)

    /** The max heart rate its zones go by: the one set, or the default. */
    val maxHeartRate: Int get() = body.maxHeartRate ?: DEFAULT_MAX_BPM

    /**
     * The most rounds done in each capped block (0-based) of a workout of this shape in [history], its own entry left
     * out; a block none was counted in has none. Nothing in a workout changes it, so it is worked out once per history.
     */
    fun capBests(history: List<HistoryEntry>): Map<Int, Int> {
        if (!config.hasCap) return emptyMap()
        val before = history.filter { it.startedAtEpochMillis != startedAt }
        return config.blocks.indices
            .filter { config.blocks[it].capped }
            .mapNotNull { block -> bestRounds(config, block, before)?.let { block to it } }
            .toMap()
    }

    private fun WorkoutUiState.withCaps(bests: Map<Int, Int>): WorkoutUiState {
        if (!config.hasCap) return this
        val taps = this@WorkoutRun.capRounds
        val caps = taps.scores(config).map { CapResult(it, bests[it.block]) }
        val shown = caps.firstOrNull { it.score.block == engine.block }?.takeUnless { over }
        val cap = shown?.let { result ->
            val into = engine.phaseMillis - engine.remainingMillis
            val last = taps.lastOrNull { it.block == engine.block }?.atMillis ?: 0L
            CapNow(
                rounds = result.score.rounds,
                roundSeconds = ((into - last) / MILLIS_PER_SECOND).toInt().takeIf { engine.inCap },
                paceSeconds = result.score.paceMillis?.let { (it / MILLIS_PER_SECOND).toInt() },
                best = result.best,
                live = engine.inCap,
            )
        }
        return copy(cap = cap, caps = caps)
    }

    /** When it ended: as its course closed, for the window its heart rate is read over. */
    val endedAt: Long get() = spans.lastOrNull()?.endEpochMillis ?: openSince

    companion object {
        /** The workout [saved] was, as it stood then; a running one has not yet caught up with the time since. */
        fun restored(saved: WorkoutProgress, keepScreenOn: Boolean, body: BodySettings): WorkoutRun {
            val startedAt = saved.startedAtEpochMillis
            return WorkoutRun(
                id = saved.id,
                engine = saved.state,
                running = saved.running,
                ended = saved.ended,
                week = saved.week,
                activeMillis = saved.activeMillis,
                workMillis = saved.workMillis,
                startedAt = startedAt,
                spans = saved.spans,
                openSince = saved.spans.lastOrNull()?.endEpochMillis ?: startedAt,
                openPhase = if (saved.running) saved.state.phase else null,
                entryUid = saved.entryUid,
                effort = saved.effort,
                note = saved.note,
                snatches = saved.snatches,
                capRounds = saved.capRounds,
                liveSamples = saved.heartRateSamples,
                keepScreenOn = keepScreenOn,
                body = body,
            )
        }
    }
}

/**
 * What of a run [WorkoutRun.toUiState] can show, its time as the whole seconds the screen counts in: the interval's
 * clock as it rounds up, the time run and worked as they round, and a cap's round so far in whole seconds. Ticks
 * within one of those seconds have the same key, so the screen's state is worked out once a second, not at every
 * tick; anything else that changes (a pause, a tap, a sample) is a new key at once. The ring's progress, which moves
 * every tick, is read from the run itself.
 */
internal data class ShownKey(val run: WorkoutRun, val capSecond: Long?)

internal val WorkoutRun.shownKey: ShownKey
    get() {
        val capSecond = if (engine.inCap && !over) {
            val into = engine.phaseMillis - engine.remainingMillis
            val last = capRounds.lastOrNull { it.block == engine.block }?.atMillis ?: 0L
            (into - last) / MILLIS_PER_SECOND
        } else {
            null
        }
        val run = copy(
            engine = engine.copy(remainingMillis = ceilSeconds(engine.remainingMillis) * MILLIS_PER_SECOND),
            activeMillis = activeSeconds.toLong(),
            workMillis = workSeconds.toLong(),
        )
        return ShownKey(run, capSecond)
    }

private fun Long.roundedSeconds(): Int = ((this + MILLIS_PER_SECOND / 2) / MILLIS_PER_SECOND).toInt()
