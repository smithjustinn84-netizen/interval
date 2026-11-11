package dev.justinsmith.interval.data

import dev.justinsmith.interval.model.AppSettings
import dev.justinsmith.interval.model.CueSettings
import dev.justinsmith.interval.model.HistoryEntry
import dev.justinsmith.interval.model.LoggedSport
import dev.justinsmith.interval.model.PerceivedEffort
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.PlannedWorkout
import dev.justinsmith.interval.model.SnatchCount
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.model.WorkoutProgress
import dev.justinsmith.interval.platform.HeartRate
import dev.justinsmith.interval.timer.Flow
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

/**
 * The mobility flows, the shipped ones first and the user's after ([flows]), the user's alone in [customFlows]. A
 * flow is saved under a name no other flow has, " 2" added where it would; its [Flow.id] is what it is edited,
 * deleted and put back by.
 */
interface FlowStore {
    val flows: StateFlow<List<Flow>>
    val customFlows: StateFlow<List<Flow>>

    fun saveFlow(flow: Flow)

    fun updateFlow(flow: Flow)

    fun deleteFlow(flow: Flow)

    fun restoreFlow(flow: Flow)
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

/** The finished workouts, newest first, and the days of a sport logged beside them. */
// One store, as the log is one: a workout timed, logged by hand or a sport day, each with its undo.
@Suppress("detekt.TooManyFunctions")
interface HistoryStore {
    val history: StateFlow<List<HistoryEntry>>

    /** The sport days logged, the latest day first. */
    val sportDays: StateFlow<List<LoggedSport>>

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

    /** Sets about how many snatches a set the workout [uid] held and the bell; null takes the count away. */
    fun countSnatches(uid: String, count: SnatchCount?)

    /** As [countSnatches], for the workout stored under [id]: from its page in the Log. */
    fun countSnatchesOf(id: Long, count: SnatchCount?)

    fun deleteWorkout(id: Long)

    fun restoreWorkout(id: Long)

    /** Logs a day of [sport] on [epochDay]. One of a sport a day: a second changes nothing. */
    fun logSport(sport: Sport, epochDay: Long)

    /** Takes back [logSport]: Undo, or the day's own remove. */
    fun unlogSport(sport: Sport, epochDay: Long)

    /**
     * Logs a workout of [config] done away from the app, or without the Timer, as finished at [finishedAtEpochMillis]
     * and run in full ([HistoryEntry.loggedByHand]). Nothing goes to the health store. Returns the uid it is stored
     * under, for [unlogWorkout].
     */
    fun logWorkout(config: IntervalConfig, finishedAtEpochMillis: Long): String

    /**
     * Takes back [logWorkout]: Undo. By uid, since its id is not known until the write lands; the row stays,
     * marked.
     */
    fun unlogWorkout(uid: String)

    fun clearHistory()
}

/** The planned workouts, in the order they were planned. */
interface PlanStore {
    val planned: StateFlow<List<PlannedWorkout>>

    fun planWeekly(workout: PlanWorkout, weekdays: Set<Int>)

    fun planOnce(workout: PlanWorkout, epochDay: Long)

    fun removePlanned(id: Long)

    fun restorePlanned(plan: PlannedWorkout)

    /**
     * Moves [plan]'s day [fromDay] to [toDay]: a weekly plan's by a one-off there that remembers the day it left, a
     * one-off by moving it, the day it first stood on remembered.
     */
    fun movePlanned(plan: PlannedWorkout, fromDay: Long, toDay: Long)

    /** Undoes [movePlanned] with the same arguments, [plan] as it stood before the move. */
    fun unmovePlanned(plan: PlannedWorkout, fromDay: Long, toDay: Long)
}

/** The workout under way or just over, as it last stood, for a restart to pick up. */
interface WorkoutProgressStore {
    val workoutProgress: StateFlow<WorkoutProgress?>

    fun saveWorkoutProgress(progress: WorkoutProgress?)
}
