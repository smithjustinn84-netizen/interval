package dev.justinsmith.interval.presets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.timer.matching
import dev.justinsmith.interval.timer.presetEntries
import dev.justinsmith.interval.timer.sameShape
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class PresetRowItem(
    val preset: Preset,
    val description: String,
    val total: String,
    val selected: Boolean,
)

data class PresetsUiState(
    val builtIn: List<PresetRowItem>,
    val custom: List<PresetRowItem>,
    /** False when the current settings already match a preset, so saving would duplicate it. */
    val canSaveCurrent: Boolean,
    /** The current settings' shape, for the save dialog. */
    val currentSummary: String,
)

class PresetsViewModel(private val repository: IntervalRepository) : ViewModel() {
    val state: StateFlow<PresetsUiState> = combine(repository.config, repository.customPresets, ::buildState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = buildState(repository.config.value, repository.customPresets.value),
        )

    fun apply(preset: Preset) {
        repository.applyPreset(preset)
    }

    fun saveCurrent(name: String) {
        repository.saveCurrentAsPreset(name)
    }

    fun delete(preset: Preset) {
        repository.deletePreset(preset)
    }

    private fun buildState(config: IntervalConfig, custom: List<Preset>): PresetsUiState {
        val entries = presetEntries(custom)
        val rows = entries.map { entry ->
            entry.custom to PresetRowItem(
                preset = entry.preset,
                description = entry.preset.config.description,
                total = formatClock(entry.preset.config.copy(prepSeconds = config.prepSeconds).totalSeconds),
                selected = entry.preset.config.sameShape(config),
            )
        }
        return PresetsUiState(
            builtIn = rows.filterNot { it.first }.map { it.second },
            custom = rows.filter { it.first }.map { it.second },
            canSaveCurrent = entries.matching(config) == null,
            currentSummary = "${config.description} · ${formatClock(config.totalSeconds)} total",
        )
    }
}
