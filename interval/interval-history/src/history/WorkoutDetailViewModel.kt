package dev.justinsmith.interval.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.data.FlowStore
import dev.justinsmith.interval.data.HistoryStore
import dev.justinsmith.interval.data.PlanStore
import dev.justinsmith.interval.data.PresetStore
import dev.justinsmith.interval.data.SettingsStore
import dev.justinsmith.interval.model.AppSettings
import dev.justinsmith.interval.model.HistoryEntry
import dev.justinsmith.interval.model.PerceivedEffort
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.PlannedWorkout
import dev.justinsmith.interval.plan.DayChoice
import dev.justinsmith.interval.plan.PlanChoice
import dev.justinsmith.interval.plan.dayChoices
import dev.justinsmith.interval.platform.DAY_CHECK_MILLIS
import dev.justinsmith.interval.platform.HealthStore
import dev.justinsmith.interval.platform.HeartRate
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.platform.days
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.defaultPresetName
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.timer.weekdayOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One finished workout, as its page shows it. */
data class FinishedWorkout(
    val id: Long,
    /** Its preset's name, or null for a shape no preset has. */
    val presetName: String?,
    val config: IntervalConfig,
    /** The day it was done, and the time it finished: "7:05 AM". */
    val day: HistoryDay,
    val time: String,
    val total: String,
    /** The time spent working, as a clock. */
    val work: String,
    val calories: Int?,
    val endedEarly: Boolean,
    /** Whether it did one of that day's plans. */
    val planned: Boolean,
    /** How hard it felt and a note on it, as said on its summary; null for none. */
    val effort: PerceivedEffort? = null,
    val note: String? = null,
    /** Its heart rate as a watch recorded it, average and peak in beats a minute; null until read. */
    val heartRate: HeartRate? = null,
) {
    /** What it is planned or repeated as: its preset's name, or its shape's. */
    val name: String get() = presetName ?: config.defaultPresetName()
}

data class WorkoutDetailUiState(
    /** Null once the workout is gone: deleted here or cleared elsewhere. */
    val workout: FinishedWorkout?,
    /** The days Plan again can put it on, and today's weekday. */
    val days: List<DayChoice>,
    val todayWeekday: Int,
) {
    /** Plan again offers just this workout. */
    val choices: List<PlanChoice> get() = listOfNotNull(workout?.let { PlanChoice(Preset(it.name, it.config)) })
}

/** The workout stored under [id]: its figures, and the ways to repeat, plan again or delete it. */
class WorkoutDetailViewModel(
    private val id: Long,
    private val historyStore: HistoryStore,
    private val presetStore: PresetStore,
    private val flowStore: FlowStore,
    private val planStore: PlanStore,
    private val settingsStore: SettingsStore,
    private val timestamps: TimestampFormatter,
    private val clock: WallClock,
    private val health: HealthStore,
    dayCheckMillis: Long = DAY_CHECK_MILLIS,
) : ViewModel() {
    // Read again each midnight, so "Today" turns to "Yesterday" with the page open.
    val state: StateFlow<WorkoutDetailUiState> =
        combine(
            historyStore.history,
            planStore.planned,
            combine(presetStore.presets, flowStore.flows, ::WorkoutNames),
            settingsStore.appSettings,
            clock.days(timestamps, dayCheckMillis),
        ) {
                history,
                planned,
                names,
                app,
                _,
            ->
            buildState(history, planned, names, app)
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            buildState(
                historyStore.history.value,
                planStore.planned.value,
                WorkoutNames(presetStore.presets.value, flowStore.flows.value),
                settingsStore.appSettings.value,
            ),
        )

    /** Puts this workout's shape on the Timer, for the repeat about to start. */
    fun putOnTimer() {
        val workout = state.value.workout ?: return
        presetStore.applyPreset(Preset(workout.name, workout.config))
    }

    /**
     * Reads the workout's heart rate from the health store as the page comes into view, unless it has one, and keeps
     * it in History once found: a watch may have synced since the summary. Only the page on show may read.
     */
    fun readHeartRate() {
        val entry = historyStore.history.value.firstOrNull { it.id == id }?.takeIf { it.averageBpm == null } ?: return
        viewModelScope.launch {
            val heartRate = health.heartRate(entry.startedAt, entry.finishedAtEpochMillis) ?: return@launch
            historyStore.keepHeartRateOf(id, heartRate)
        }
    }

    /** Undo on [delete]. */
    fun restore() {
        historyStore.restoreWorkout(id)
    }

    fun delete() {
        historyStore.deleteWorkout(id)
    }

    fun planWeekly(workout: PlanWorkout, weekdays: Set<Int>) {
        planStore.planWeekly(workout, weekdays)
    }

    fun planOnce(workout: PlanWorkout, epochDay: Long) {
        planStore.planOnce(workout, epochDay)
    }

    private fun buildState(
        history: List<HistoryEntry>,
        planned: List<PlannedWorkout>,
        names: WorkoutNames,
        app: AppSettings,
    ): WorkoutDetailUiState {
        val today = timestamps.dayNumber(clock.nowMillis())
        val workout = history.firstOrNull { it.id == id }?.let { entry ->
            val day = timestamps.dayNumber(entry.finishedAtEpochMillis)
            val log = TrainingLog(history, planned, today, dayOf = timestamps::dayNumber)
            FinishedWorkout(
                id = id,
                presetName = names.of(entry.config),
                config = entry.config,
                day = timestamps.heading(day, today),
                time = timestamps.time(entry.finishedAtEpochMillis),
                total = formatClock(entry.elapsedSeconds),
                work = formatClock(entry.workDoneSeconds),
                calories = entry.calories(app.body),
                endedEarly = entry.endedEarly,
                planned = log.day(day).plans.any { it.workoutId == id },
                effort = entry.effort,
                note = entry.note,
                heartRate = entry.averageBpm?.let { average -> entry.peakBpm?.let { HeartRate(average, it) } },
            )
        }
        return WorkoutDetailUiState(workout, dayChoices(today), weekdayOf(today))
    }
}
