package dev.justinsmith.interval.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.timer.ConfigField
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.timer.matching
import dev.justinsmith.interval.timer.presetEntries
import dev.justinsmith.interval.timer.sameShape
import dev.justinsmith.interval.timer.stepped
import dev.justinsmith.interval.timer.withValue
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class PresetChip(val preset: Preset, val description: String, val selected: Boolean)

data class TimerUiState(
    val config: IntervalConfig,
    val total: String,
    /** The matching preset's name, or null for a shape no preset has. */
    val presetName: String?,
    /** "Starts with 0:10 to get ready", or null when there is no countdown. */
    val prepLine: String?,
    val presets: List<PresetChip>,
) {
    /** A shape no preset has yet can be saved as one. */
    val canSavePreset: Boolean get() = presetName == null

    /** "25s on · 10s off · ×8 · 4:40 total", for the save dialog. */
    val summary: String get() = "${config.description} · $total total"
}

class TimerViewModel(private val repository: IntervalRepository) : ViewModel() {
    val state: StateFlow<TimerUiState> = combine(repository.config, repository.customPresets, ::buildState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = buildState(repository.config.value, repository.customPresets.value),
        )

    fun step(field: ConfigField, direction: Int) {
        repository.updateConfig { it.stepped(field, direction) }
    }

    fun set(field: ConfigField, value: Int) {
        repository.updateConfig { it.withValue(field, value) }
    }

    fun applyPreset(preset: Preset) {
        repository.applyPreset(preset)
    }

    fun savePreset(name: String) {
        repository.saveCurrentAsPreset(name)
    }

    private fun buildState(config: IntervalConfig, custom: List<Preset>): TimerUiState {
        val entries = presetEntries(custom)
        val prep = config.prepSeconds
        return TimerUiState(
            config = config,
            total = formatClock(config.totalSeconds),
            presetName = entries.matching(config)?.name,
            prepLine = if (prep > 0) "${formatClock(prep)} to get ready" else null,
            presets = entries.map { (preset, _) ->
                PresetChip(preset, preset.config.description, selected = preset.config.sameShape(config))
            },
        )
    }
}
