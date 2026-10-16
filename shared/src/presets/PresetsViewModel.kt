package dev.justinsmith.interval.presets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.PresetEntry
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.timer.matching
import dev.justinsmith.interval.timer.sameShape
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class PresetRowItem(
    val preset: Preset,
    val total: String,
    val selected: Boolean,
    /** This preset's length as a share of the longest on the screen, so its timeline can be drawn to scale. */
    val share: Float = 1f,
)

data class PresetsUiState(
    val builtIn: List<PresetRowItem>,
    val custom: List<PresetRowItem>,
    /** False when the current settings already match a preset, so saving would duplicate it. */
    val canSaveCurrent: Boolean,
    /** The current settings: shown to save when they match no preset, and in the save dialog. */
    val current: IntervalConfig,
    /** The current settings' length as a share of the longest on the screen, as [PresetRowItem.share]. */
    val currentShare: Float = 1f,
    /** The number a preset saved without a name gets: "Custom 3". */
    val nextCustomNumber: Int = 1,
)

class PresetsViewModel(private val repository: IntervalRepository) : ViewModel() {
    val state: StateFlow<PresetsUiState> = combine(repository.config, repository.presets, ::buildState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = buildState(repository.config.value, repository.presets.value),
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

    private fun buildState(config: IntervalConfig, entries: List<PresetEntry>): PresetsUiState {
        // Every total counts the user's own get-ready countdown, as a workout would.
        fun totalOf(preset: Preset) = preset.config.copy(prepSeconds = config.prepSeconds).totalSeconds
        val canSaveCurrent = entries.matching(config) == null
        val longest = (entries.map { totalOf(it.preset) } + if (canSaveCurrent) config.totalSeconds else 0)
            .max()
            .coerceAtLeast(1)
        val rows = entries.map { entry ->
            val total = totalOf(entry.preset)
            entry.custom to PresetRowItem(
                preset = entry.preset,
                total = formatClock(total),
                selected = entry.preset.config.sameShape(config),
                share = total.toFloat() / longest,
            )
        }
        return PresetsUiState(
            builtIn = rows.filterNot { it.first }.map { it.second },
            custom = rows.filter { it.first }.map { it.second },
            canSaveCurrent = canSaveCurrent,
            current = config,
            currentShare = config.totalSeconds.toFloat() / longest,
            nextCustomNumber = entries.count { it.custom } + 1,
        )
    }
}
