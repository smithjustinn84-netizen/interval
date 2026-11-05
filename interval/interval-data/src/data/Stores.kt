package dev.justinsmith.interval.data

import dev.justinsmith.interval.model.AppSettings
import dev.justinsmith.interval.model.CueSettings
import dev.justinsmith.interval.model.HistoryEntry
import dev.justinsmith.interval.model.PerceivedEffort
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.PlannedWorkout
import dev.justinsmith.interval.model.WorkoutProgress
import dev.justinsmith.interval.platform.HeartRate
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import kotlinx.coroutines.flow.StateFlow

// The roles the interval side's storage plays, one per kind of thing it keeps, so a screen takes only what it reads
// and writes: the Timer can't clear the history, and a test can hand a ViewModel a store of its own. Each is a
// repository under IntervalRepository (SettingsRepository is two, the settings and the workout under way), whose
// docs say how it behaves. What only the app does (ready, import, close) stays on IntervalRepository alone.

/** The current setup on the Timer, the cue toggles and the app's settings, each changed with a transform. */
interface SettingsStore {
    val config: StateFlow<IntervalConfig>
    val cueSettings: StateFlow<CueSettings>
    val appSettings: StateFlow<AppSettings>

    fun updateConfig(transform: (IntervalConfig) -> IntervalConfig)

    fun updateCueSettings(transform: (CueSettings) -> CueSettings)

    fun updateAppSettings(transform: (AppSettings) -> AppSettings)
}

/** The presets, built-ins first, the user's after; applying one puts its shape on the Timer. */
interface PresetStore {
    val presets: StateFlow<List<Preset>>
    val customPresets: StateFlow<List<Preset>>

    fun applyPreset(preset: Preset)

    fun savePreset(preset: Preset)

    fun saveAndApplyPreset(preset: Preset)

    fun updatePreset(preset: Preset)

    fun deletePreset(preset: Preset)

    fun restorePreset(preset: Preset)
}

/** The finished workouts, newest first. */
interface HistoryStore {
    val history: StateFlow<List<HistoryEntry>>

    /** Returns the uid the workout is stored under. */
    fun recordWorkout(
        config: IntervalConfig,
        elapsedSeconds: Int,
        endedEarly: Boolean = false,
        workSeconds: Int? = null,
        startedAtEpochMillis: Long? = null,
    ): String

    fun reflectOnWorkout(uid: String, effort: PerceivedEffort?, note: String)

    /** Keeps the [heartRate] of the workout [uid] unless it has one: the first found stays. */
    fun keepHeartRate(uid: String, heartRate: HeartRate)

    /** As [keepHeartRate], for the workout stored under [id]. */
    fun keepHeartRateOf(id: Long, heartRate: HeartRate)

    fun deleteWorkout(id: Long)

    fun restoreWorkout(id: Long)

    fun clearHistory()
}

/** The planned workouts, in the order they were planned. */
interface PlanStore {
    val planned: StateFlow<List<PlannedWorkout>>

    fun planWeekly(workout: PlanWorkout, weekdays: Set<Int>)

    fun planOnce(workout: PlanWorkout, epochDay: Long)

    fun removePlanned(id: Long)

    fun restorePlanned(plan: PlannedWorkout)
}

/** The workout under way or just over, as it last stood, for a restart to pick up. */
interface WorkoutProgressStore {
    val workoutProgress: StateFlow<WorkoutProgress?>

    fun saveWorkoutProgress(progress: WorkoutProgress?)
}
