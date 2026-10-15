package dev.justinsmith.interval.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.data.CueSettings
import dev.justinsmith.interval.data.HistoryEntry
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.timer.ConfigField
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.builtInPresets
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.timer.stepped
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class PresetItem(
    val preset: Preset,
    val description: String,
    val custom: Boolean,
    val selected: Boolean,
)

data class HistoryItem(
    val title: String,
    val finishedAt: String,
    val total: String,
)

data class SetupUiState(
    val config: IntervalConfig,
    val cues: CueSettings,
    val total: String,
    val presets: List<PresetItem>,
    /** False when the current settings already match a preset, so saving would duplicate it. */
    val canSavePreset: Boolean,
    val history: List<HistoryItem>,
)

/** Toggles a single [CueSettings] flag. */
enum class CueToggle { Sound, Voice, Halfway, Vibrate }

class SetupViewModel(
    private val repository: IntervalRepository,
    private val timestamps: TimestampFormatter,
) : ViewModel() {
    val state: StateFlow<SetupUiState> = combine(
        repository.config,
        repository.cueSettings,
        repository.customPresets,
        repository.history,
    ) { config, cues, custom, history -> buildState(config, cues, custom, history) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = buildState(
                repository.config.value,
                repository.cueSettings.value,
                repository.customPresets.value,
                repository.history.value,
            ),
        )

    fun step(field: ConfigField, direction: Int) {
        repository.setConfig(repository.config.value.stepped(field, direction))
    }

    fun applyPreset(preset: Preset) {
        repository.setConfig(preset.config)
    }

    fun savePreset(name: String) {
        repository.saveCurrentAsPreset(name)
    }

    fun deletePreset(preset: Preset) {
        repository.deletePreset(preset)
    }

    fun toggle(toggle: CueToggle) {
        val cues = repository.cueSettings.value
        repository.setCueSettings(
            when (toggle) {
                CueToggle.Sound -> cues.copy(sound = !cues.sound)
                CueToggle.Voice -> cues.copy(voice = !cues.voice)
                CueToggle.Halfway -> cues.copy(halfway = !cues.halfway)
                CueToggle.Vibrate -> cues.copy(vibrate = !cues.vibrate)
            },
        )
    }

    fun clearHistory() {
        repository.clearHistory()
    }

    private fun buildState(
        config: IntervalConfig,
        cues: CueSettings,
        custom: List<Preset>,
        history: List<HistoryEntry>,
    ): SetupUiState {
        val all = builtInPresets.map { it to false } + custom.map { it to true }
        return SetupUiState(
            config = config,
            cues = cues,
            total = formatClock(config.totalSeconds),
            presets = all.map { (preset, isCustom) ->
                PresetItem(preset, preset.config.description, isCustom, selected = preset.config == config)
            },
            canSavePreset = all.none { (preset, _) -> preset.config == config },
            history = history.map { entry ->
                val name = all.firstOrNull { (preset, _) -> preset.config == entry.config }?.first?.name
                HistoryItem(
                    title = listOfNotNull(name, entry.config.description).joinToString(" · "),
                    finishedAt = timestamps.format(entry.finishedAtEpochMillis),
                    total = formatClock(entry.config.totalSeconds),
                )
            },
        )
    }
}
