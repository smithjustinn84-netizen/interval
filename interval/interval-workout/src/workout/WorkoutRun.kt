package dev.justinsmith.interval.workout

import dev.justinsmith.interval.model.BodySettings
import dev.justinsmith.interval.model.CourseSpan
import dev.justinsmith.interval.model.PerceivedEffort
import dev.justinsmith.interval.model.WorkoutProgress
import dev.justinsmith.interval.platform.HeartRate
import dev.justinsmith.interval.platform.WorkoutStatus
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.MILLIS_PER_SECOND
import dev.justinsmith.interval.timer.Phase
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.Step
import dev.justinsmith.interval.timer.WeekStanding
import dev.justinsmith.interval.timer.WorkoutOutline
import dev.justinsmith.interval.timer.WorkoutState
import dev.justinsmith.interval.timer.matching

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
    /** The summary's words for the health store's note, as the screen put them. */
    val healthNotes: String? = null,
    /** Its heart rate as a watch recorded it in the health store, once read there: not saved, it is read again. */
    val heartRate: HeartRate? = null,
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

    /** A new interval or round since [before]: a change of course, which the clock running down within one is not. */
    fun turnedFrom(before: WorkoutState): Boolean = engine.phase != before.phase || engine.round != before.round

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
    )

    /** What the notification shows of it: the phase, whether it is paused, the clock, the round and the bar. */
    fun status(): WorkoutStatus {
        val shown = engine.toUiState(running = running, ended = ended)
        return WorkoutStatus(
            shown.phase,
            shown.paused,
            shown.clock,
            shown.round,
            shown.rounds,
            WorkoutOutline.of(engine),
        )
    }

    /** The screen's view of it, its shape named after the one of [presets] it matches. */
    fun toUiState(presets: List<Preset>): WorkoutUiState = engine.toUiState(
        running = running,
        ended = ended,
        presetName = presets.matching(config)?.name,
        elapsedSeconds = activeSeconds,
        presets = presets,
        keepScreenOn = keepScreenOn,
        calories = calories,
        week = week,
        leaving = leaving,
        effort = effort,
        note = note,
    ).copy(heartRate = heartRate)

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
                keepScreenOn = keepScreenOn,
                body = body,
            )
        }
    }
}

private fun Long.roundedSeconds(): Int = ((this + MILLIS_PER_SECOND / 2) / MILLIS_PER_SECOND).toInt()
