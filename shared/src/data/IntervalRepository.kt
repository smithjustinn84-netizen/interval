package dev.justinsmith.interval.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import co.touchlab.kermit.Logger
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.PresetEntry
import dev.justinsmith.interval.timer.presetEntries
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which feedback plays during a workout. */
data class CueSettings(
    val sound: Boolean = true,
    val voice: Boolean = true,
    val halfway: Boolean = true,
    val vibrate: Boolean = true,
)

/** How the app picks light or dark. */
enum class ThemeMode { System, Light, Dark }

/** App-wide preferences that are not part of a workout's shape. */
data class AppSettings(
    val theme: ThemeMode = ThemeMode.System,
    val keepScreenOn: Boolean = true,
)

/** A finished workout that ran for [elapsedSeconds], pauses excluded, to the end or [endedEarly]. */
data class HistoryEntry(
    val finishedAtEpochMillis: Long,
    val config: IntervalConfig,
    val elapsedSeconds: Int,
    val endedEarly: Boolean,
)

/**
 * The app's saved state: the current setup, cue toggles, app settings, custom presets and workout history.
 *
 * Settings live in [settings] (DataStore) and collections in Room ([historyDao], [presetDao]). Everything is
 * read once into memory, after which [ready] turns true and every [StateFlow] holds what was stored. Settings
 * then change in memory first, synchronously, so a value set a moment ago is what the next screen reads.
 * Collections follow the database. Writes run in the background on [scope], one at a time in the order they
 * were made.
 */
class IntervalRepository(
    private val settings: DataStore<Preferences>,
    private val historyDao: HistoryDao,
    private val presetDao: PresetDao,
    private val scope: CoroutineScope,
    private val now: () -> Long,
) {
    private val log = Logger.withTag("IntervalRepository")

    private val _ready = MutableStateFlow(false)

    /** True once every value below has been read from disk. Until then they hold defaults. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _config = MutableStateFlow(IntervalConfig())
    val config: StateFlow<IntervalConfig> = _config.asStateFlow()

    private val _cueSettings = MutableStateFlow(CueSettings())
    val cueSettings: StateFlow<CueSettings> = _cueSettings.asStateFlow()

    private val _appSettings = MutableStateFlow(AppSettings())
    val appSettings: StateFlow<AppSettings> = _appSettings.asStateFlow()

    private val _customPresets = MutableStateFlow(emptyList<Preset>())

    /** In the order they were saved. */
    val customPresets: StateFlow<List<Preset>> = _customPresets.asStateFlow()

    private val _presets = MutableStateFlow(presetEntries(emptyList()))

    /** Every preset the lists show: the built-ins, then [customPresets]. Updated with it, never behind. */
    val presets: StateFlow<List<PresetEntry>> = _presets.asStateFlow()

    private val _history = MutableStateFlow(emptyList<HistoryEntry>())

    // Queued in call order and run one at a time, so a save can't overtake an earlier one (a clear
    // landing before the insert it followed, presets stored out of order).
    private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    /** Newest first. */
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    init {
        scope.launch {
            for (write in writes) runLogged(write)
        }
        scope.launch {
            val presetsLoaded = CompletableDeferred<Unit>()
            val historyLoaded = CompletableDeferred<Unit>()
            launch {
                presetDao.observeAll().orEmptyOnError("presets").collect { rows ->
                    val custom = rows.map { it.toDomain() }
                    _customPresets.value = custom
                    _presets.value = presetEntries(custom)
                    presetsLoaded.complete(Unit)
                }
            }
            launch {
                historyDao.observeAll().orEmptyOnError("history").collect { rows ->
                    _history.value = rows.map { it.toDomain() }
                    historyLoaded.complete(Unit)
                }
            }
            // An unreadable settings file falls back to the defaults rather than keeping the app from starting.
            val stored = settings.data
                .catch { e ->
                    log.e(e) { "Couldn't read settings" }
                    emit(emptyPreferences())
                }
                .first()
            _config.value = stored.toConfig()
            _cueSettings.value = stored.toCueSettings()
            _appSettings.value = stored.toAppSettings()
            presetsLoaded.await()
            historyLoaded.await()
            _ready.value = true
        }
    }

    fun updateConfig(transform: (IntervalConfig) -> IntervalConfig) {
        _config.update(transform)
        persist { settings.edit { it.write(_config.value) } }
    }

    fun updateCueSettings(transform: (CueSettings) -> CueSettings) {
        _cueSettings.update(transform)
        persist { settings.edit { it.write(_cueSettings.value) } }
    }

    fun updateAppSettings(transform: (AppSettings) -> AppSettings) {
        _appSettings.update(transform)
        persist { settings.edit { it.write(_appSettings.value) } }
    }

    /** Takes [preset]'s work, rest and rounds; the get-ready countdown stays the user's own setting. */
    fun applyPreset(preset: Preset) {
        updateConfig { preset.config.copy(prepSeconds = it.prepSeconds) }
    }

    /**
     * Saves the current configuration under [name]. The save dialog fills in a blank name in the user's
     * language ("Custom N"); the English fallback here only guards callers that don't.
     */
    fun saveCurrentAsPreset(name: String) {
        val label = name.trim().ifEmpty { "Custom ${_customPresets.value.size + 1}" }.take(Preset.MAX_NAME_LENGTH)
        val preset = Preset(label, _config.value)
        persist { presetDao.insert(preset.toEntity()) }
    }

    /** Removes one of the user's own presets. Built-ins have no id and are left alone. */
    fun deletePreset(preset: Preset) {
        val id = preset.id ?: return
        persist { presetDao.delete(id) }
    }

    /** Records a workout that ran for [elapsedSeconds]; [endedEarly] when it stopped before the last round. */
    fun recordWorkout(config: IntervalConfig, elapsedSeconds: Int, endedEarly: Boolean = false) {
        val entry = HistoryEntry(now(), config, elapsedSeconds, endedEarly)
        persist { historyDao.insert(entry.toEntity()) }
    }

    fun clearHistory() {
        persist { historyDao.clear() }
    }

    private fun <T> Flow<List<T>>.orEmptyOnError(what: String): Flow<List<T>> = catch { e ->
        log.e(e) { "Couldn't read $what" }
        emit(emptyList())
    }

    private fun persist(write: suspend () -> Unit) {
        writes.trySend(write)
    }

    // A failed write is logged, never thrown: losing one save must not crash a workout.
    @Suppress("detekt.TooGenericExceptionCaught")
    private suspend fun runLogged(write: suspend () -> Unit) {
        try {
            write()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.e(e) { "Couldn't save" }
        }
    }
}
