package dev.justinsmith.interval.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import co.touchlab.kermit.Logger
import dev.justinsmith.interval.model.AppSettings
import dev.justinsmith.interval.model.CueSettings
import dev.justinsmith.interval.model.WorkoutProgress
import dev.justinsmith.interval.storage.SerialWriter
import dev.justinsmith.interval.timer.IntervalConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the settings DataStore keeps: the current setup on the Timer, the cue toggles, the app's settings and the
 * workout under way, the [SettingsStore] and [WorkoutProgressStore]. Read once into memory, after which [ready]
 * turns true. A value then changes in memory first, synchronously, so a value set a moment ago is what the next
 * screen reads, and is written in the background through [writer], the queue every interval repository shares.
 */
class SettingsRepository(
    private val preferences: DataStore<Preferences>,
    private val writer: SerialWriter,
    scope: CoroutineScope,
) : SettingsStore, WorkoutProgressStore {
    private val log = Logger.withTag("SettingsRepository")

    private val _ready = MutableStateFlow(false)

    /** True once every value below has been read from disk. Until then they hold defaults. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _config = MutableStateFlow(IntervalConfig())
    override val config: StateFlow<IntervalConfig> = _config.asStateFlow()

    private val _cueSettings = MutableStateFlow(CueSettings())
    override val cueSettings: StateFlow<CueSettings> = _cueSettings.asStateFlow()

    private val _appSettings = MutableStateFlow(AppSettings())
    override val appSettings: StateFlow<AppSettings> = _appSettings.asStateFlow()

    private val _workoutProgress = MutableStateFlow<WorkoutProgress?>(null)

    /** The workout under way or just over, as it last stood, or null when none is: see [WorkoutProgress]. */
    override val workoutProgress: StateFlow<WorkoutProgress?> = _workoutProgress.asStateFlow()

    init {
        scope.launch {
            // An unreadable settings file falls back to the defaults rather than keeping the app from starting.
            val stored = preferences.data
                .catch { e ->
                    log.e(e) { "Couldn't read settings" }
                    emit(emptyPreferences())
                }
                .first()
            _config.value = stored.toConfig()
            _cueSettings.value = stored.toCueSettings()
            _appSettings.value = stored.toAppSettings()
            _workoutProgress.value = stored.toWorkoutProgress()
            _ready.value = true
        }
    }

    override fun updateConfig(transform: (IntervalConfig) -> IntervalConfig) {
        update(_config, transform) { prefs, config -> prefs.write(config) }
    }

    override fun updateCueSettings(transform: (CueSettings) -> CueSettings) {
        update(_cueSettings, transform) { prefs, cues -> prefs.write(cues) }
    }

    override fun updateAppSettings(transform: (AppSettings) -> AppSettings) {
        update(_appSettings, transform) { prefs, app -> prefs.write(app) }
    }

    /** Keeps [progress] as the workout under way, for a restart to pick up; null once there is none. */
    override fun saveWorkoutProgress(progress: WorkoutProgress?) {
        update(_workoutProgress, { progress }) { prefs, saved -> prefs.write(saved) }
    }

    // In memory at once, so the next read sees it; on disk in the background, the value as it is then.
    private fun <T> update(value: MutableStateFlow<T>, transform: (T) -> T, write: (MutablePreferences, T) -> Unit) {
        value.update(transform)
        writer.persist { preferences.edit { write(it, value.value) } }
    }
}
