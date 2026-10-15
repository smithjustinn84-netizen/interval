package dev.justinsmith.interval.data

import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Minimal persistent string storage. Each platform backs it with its native preferences store. */
interface KeyValueStore {
    fun getString(key: String): String?

    fun putString(key: String, value: String)
}

/** Which feedback plays during a workout. */
@Serializable
data class CueSettings(
    val sound: Boolean = true,
    val voice: Boolean = true,
    val halfway: Boolean = true,
    val vibrate: Boolean = true,
)

/** How the app picks light or dark. */
enum class ThemeMode { System, Light, Dark }

/** App-wide preferences that are not part of a workout's shape. */
@Serializable
data class AppSettings(
    val theme: ThemeMode = ThemeMode.System,
    val keepScreenOn: Boolean = true,
)

/** A finished workout. [elapsedSeconds] is set when it was ended early; null means it ran to the end. */
@Serializable
data class HistoryEntry(
    val finishedAtEpochMillis: Long,
    val config: IntervalConfig,
    val elapsedSeconds: Int? = null,
) {
    val endedEarly: Boolean get() = elapsedSeconds != null
    val durationSeconds: Int get() = elapsedSeconds ?: config.totalSeconds
}

/**
 * The app's saved state: the current setup, cue toggles, app settings, custom presets and workout history.
 * Values are held in memory as [StateFlow]s and written through to [store] on every change.
 */
class IntervalRepository(
    private val store: KeyValueStore,
    private val now: () -> Long,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _config = MutableStateFlow(load(KEY_CONFIG, IntervalConfig.serializer()) ?: IntervalConfig())
    val config: StateFlow<IntervalConfig> = _config.asStateFlow()

    private val _cueSettings = MutableStateFlow(load(KEY_CUES, CueSettings.serializer()) ?: CueSettings())
    val cueSettings: StateFlow<CueSettings> = _cueSettings.asStateFlow()

    private val _appSettings = MutableStateFlow(load(KEY_APP, AppSettings.serializer()) ?: AppSettings())
    val appSettings: StateFlow<AppSettings> = _appSettings.asStateFlow()

    private val _customPresets = MutableStateFlow(load(KEY_PRESETS, presetList) ?: emptyList())
    val customPresets: StateFlow<List<Preset>> = _customPresets.asStateFlow()

    private val _history = MutableStateFlow(load(KEY_HISTORY, historyList) ?: emptyList())

    /** Newest first, at most [MAX_HISTORY] entries. */
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    fun setConfig(config: IntervalConfig) {
        _config.value = config
        save(KEY_CONFIG, IntervalConfig.serializer(), config)
    }

    fun setCueSettings(settings: CueSettings) {
        _cueSettings.value = settings
        save(KEY_CUES, CueSettings.serializer(), settings)
    }

    fun setAppSettings(settings: AppSettings) {
        _appSettings.value = settings
        save(KEY_APP, AppSettings.serializer(), settings)
    }

    /** Takes [preset]'s work, rest and rounds; the get-ready countdown stays the user's own setting. */
    fun applyPreset(preset: Preset) {
        setConfig(preset.config.copy(prepSeconds = _config.value.prepSeconds))
    }

    /** Saves the current configuration under [name]; a blank name becomes "Custom N". */
    fun saveCurrentAsPreset(name: String) {
        _customPresets.update { presets ->
            val label = name.trim().ifEmpty { "Custom ${presets.size + 1}" }.take(MAX_PRESET_NAME)
            presets + Preset(label, _config.value)
        }
        save(KEY_PRESETS, presetList, _customPresets.value)
    }

    fun deletePreset(preset: Preset) {
        _customPresets.update { presets -> presets - preset }
        save(KEY_PRESETS, presetList, _customPresets.value)
    }

    /** Records a workout; pass [elapsedSeconds] when it was ended before the last round. */
    fun recordWorkout(config: IntervalConfig, elapsedSeconds: Int? = null) {
        val entry = HistoryEntry(now(), config, elapsedSeconds)
        _history.update { entries -> (listOf(entry) + entries).take(MAX_HISTORY) }
        save(KEY_HISTORY, historyList, _history.value)
    }

    fun clearHistory() {
        _history.value = emptyList()
        save(KEY_HISTORY, historyList, emptyList())
    }

    // A value that fails to decode (an older format, a corrupt write) falls back to the default
    // rather than crashing the app on launch.
    private fun <T> load(key: String, serializer: KSerializer<T>): T? =
        store.getString(key)?.let { raw -> runCatching { json.decodeFromString(serializer, raw) }.getOrNull() }

    private fun <T> save(key: String, serializer: KSerializer<T>, value: T) {
        store.putString(key, json.encodeToString(serializer, value))
    }

    companion object {
        const val MAX_HISTORY = 20
        const val MAX_PRESET_NAME = 24

        private const val KEY_CONFIG = "config"
        private const val KEY_CUES = "cues"
        private const val KEY_APP = "app"
        private const val KEY_PRESETS = "presets"
        private const val KEY_HISTORY = "history"

        private val presetList = ListSerializer(Preset.serializer())
        private val historyList = ListSerializer(HistoryEntry.serializer())
    }
}
