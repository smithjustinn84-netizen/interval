package dev.justinsmith.interval.workout

import dev.justinsmith.interval.data.HistoryStore
import dev.justinsmith.interval.data.PresetStore
import dev.justinsmith.interval.data.SettingsStore
import dev.justinsmith.interval.platform.HealthStore
import dev.justinsmith.interval.platform.HealthWorkout
import dev.justinsmith.interval.platform.HeartRate
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.timer.WeekStanding
import dev.justinsmith.interval.timer.nameOf
import dev.justinsmith.interval.timer.weekStanding
import dev.justinsmith.lift.data.LiftActivity

/**
 * Keeps a finished workout: its History entry, where the week stands with it, and the workout as it ran in the
 * health store; then what its summary says of it, in History at once and in the health store again as the summary
 * goes.
 */
class WorkoutRecorder(
    private val historyStore: HistoryStore,
    private val presetStore: PresetStore,
    private val settingsStore: SettingsStore,
    private val bells: LiftActivity,
    private val timestamps: TimestampFormatter,
    private val clock: WallClock,
    private val health: HealthStore,
) {
    /**
     * Records [run], over and its course closed, in History ([endedEarly] when it stopped before its last round)
     * and hands it to the health store. Returns it with the entry's uid and the week as it stands with it.
     */
    fun finished(run: WorkoutRun, endedEarly: Boolean): WorkoutRun {
        // Before the write, which lands later, so the summary has it as it arrives.
        val week = weekWith()
        val uid = historyStore.recordWorkout(
            run.config,
            run.activeSeconds,
            endedEarly,
            workSeconds = run.workSeconds,
            startedAtEpochMillis = run.startedAt,
        )
        health.record(healthWorkout(run, endedAt = run.openSince))
        return run.copy(week = week, entryUid = uid)
    }

    /** Keeps what the summary of [run] says, in History at once. Nothing before the workout is recorded. */
    fun reflect(run: WorkoutRun) {
        val uid = run.entryUid ?: return
        historyStore.reflectOnWorkout(uid, run.effort, run.note)
    }

    /**
     * The heart rate over the finished [run], read from the health store and kept in History with it; null when there
     * is none to read yet. Read from the screen: the store answers only an app on show.
     */
    suspend fun heartRate(run: WorkoutRun): HeartRate? {
        val uid = run.entryUid ?: return null
        val heartRate = health.heartRate(run.startedAt, run.endedAt) ?: return null
        historyStore.keepHeartRate(uid, heartRate)
        return heartRate
    }

    /**
     * Writes the finished [run] to the health store again with the summary's words, as a newer version of the
     * record written as it ended. Nothing when there is no finished workout, or nothing was said of it.
     */
    fun rewriteForHealth(run: WorkoutRun) {
        if (!run.reflected) return
        val workout = healthWorkout(run, endedAt = run.spans.lastOrNull()?.endEpochMillis ?: run.startedAt)
        health.record(workout.copy(notes = run.healthNotes, version = clock.nowMillis()))
    }

    /**
     * [run] as it ran, to [endedAt], as the health store takes it, named for the preset its shape now matches or
     * the flow it is.
     */
    private fun healthWorkout(run: WorkoutRun, endedAt: Long): HealthWorkout {
        val presetName = presetStore.presets.value.nameOf(run.config)
        return healthWorkout(run.config.activity, presetName, run.startedAt, endedAt, run.spans, run.calories)
    }

    /** The week as it stands with a workout today in it, every kind of workout counted as History counts them. */
    private fun weekWith(): WeekStanding {
        val today = timestamps.dayNumber(clock.nowMillis())
        val days = historyStore.history.value.map { timestamps.dayNumber(it.finishedAtEpochMillis) } +
            bells.sessions.value.mapNotNull { it.finishedAtEpochMillis }.map(timestamps::dayNumber) +
            historyStore.sportDays.value.map { it.epochDay } +
            today
        return weekStanding(days, today, settingsStore.appSettings.value.weeklyGoal)
    }
}
