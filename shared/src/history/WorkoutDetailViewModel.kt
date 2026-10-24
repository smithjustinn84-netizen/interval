package dev.justinsmith.interval.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.model.AppSettings
import dev.justinsmith.interval.model.HistoryEntry
import dev.justinsmith.interval.model.PlannedWorkout
import dev.justinsmith.interval.plan.DayChoice
import dev.justinsmith.interval.plan.PlanChoice
import dev.justinsmith.interval.plan.dayChoices
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.defaultPresetName
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.timer.matching
import dev.justinsmith.interval.timer.weekdayOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

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
    private val repository: IntervalRepository,
    private val timestamps: TimestampFormatter,
    private val clock: WallClock,
) : ViewModel() {
    val state: StateFlow<WorkoutDetailUiState> =
        combine(repository.history, repository.planned, repository.presets, repository.appSettings) {
                history,
                planned,
                presets,
                app,
            ->
            buildState(history, planned, presets, app)
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            buildState(
                repository.history.value,
                repository.planned.value,
                repository.presets.value,
                repository.appSettings.value,
            ),
        )

    /** Puts this workout's shape on the Timer, for the repeat about to start. */
    fun putOnTimer() {
        val workout = state.value.workout ?: return
        repository.applyPreset(Preset(workout.name, workout.config))
    }

    /** Undo on [delete]. */
    fun restore() {
        repository.restoreWorkout(id)
    }

    fun delete() {
        repository.deleteWorkout(id)
    }

    fun planWeekly(preset: Preset, weekdays: Set<Int>) {
        repository.planWeekly(preset, weekdays)
    }

    fun planOnce(preset: Preset, epochDay: Long) {
        repository.planOnce(preset, epochDay)
    }

    private fun buildState(
        history: List<HistoryEntry>,
        planned: List<PlannedWorkout>,
        presets: List<Preset>,
        app: AppSettings,
    ): WorkoutDetailUiState {
        val today = timestamps.dayNumber(clock.nowMillis())
        val workout = history.firstOrNull { it.id == id }?.let { entry ->
            val day = timestamps.dayNumber(entry.finishedAtEpochMillis)
            val log = TrainingLog(history, planned, today, dayOf = timestamps::dayNumber)
            FinishedWorkout(
                id = id,
                presetName = presets.matching(entry.config)?.name,
                config = entry.config,
                day = timestamps.heading(day, today),
                time = timestamps.time(entry.finishedAtEpochMillis),
                total = formatClock(entry.elapsedSeconds),
                work = formatClock(entry.workDoneSeconds),
                calories = entry.calories(app.body),
                endedEarly = entry.endedEarly,
                planned = log.day(day).plans.any { it.workoutId == id },
            )
        }
        return WorkoutDetailUiState(workout, dayChoices(today), weekdayOf(today))
    }
}
