package dev.justinsmith.interval.presets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.PresetEntry
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.timer.matching
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class PresetRowItem(
    val preset: Preset,
    /** The whole workout's length with the user's own get-ready countdown: "4:00". */
    val total: String,
    /** This is the preset the Timer's settings count as ([matching]): at most one row is. */
    val selected: Boolean,
    /** This preset's length as a share of the longest on the screen, so its timeline can be drawn to scale. */
    val share: Float = 1f,
)

data class PresetsUiState(
    /** The user's own presets, in the order they were saved. */
    val custom: List<PresetRowItem>,
    val builtIn: List<PresetRowItem>,
    /** False when the current settings already match a preset, so saving would duplicate it. */
    val canSaveCurrent: Boolean,
    /** The settings on the Timer: shown to save when they match no preset, and the shape a new preset starts from. */
    val current: IntervalConfig,
    /** The current settings' length as a share of the longest on the screen, as [PresetRowItem.share]. */
    val currentShare: Float = 1f,
) {
    /** Every row the screen shows, the user's own first. */
    val rows: List<PresetRowItem> get() = custom + builtIn
}

class PresetsViewModel(private val repository: IntervalRepository) : ViewModel() {
    val state: StateFlow<PresetsUiState> = combine(repository.config, repository.presets, ::buildState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = buildState(repository.config.value, repository.presets.value),
        )

    /** Puts [preset]'s shape on the Timer. */
    fun apply(preset: Preset) {
        repository.applyPreset(preset)
    }

    /**
     * Saves [preset]: a new one of the user's own, or the changes to the one its id names. Changes to
     * the preset on the Timer go onto the Timer too, so the two stay in step rather than leaving the
     * old shape there, unsaved.
     */
    fun save(preset: Preset) {
        if (!preset.custom) {
            repository.savePreset(preset)
            return
        }
        val wasOnTimer = repository.presets.value.matching(repository.config.value)?.id == preset.id
        repository.updatePreset(preset)
        if (wasOnTimer) repository.applyPreset(preset)
    }

    /** Saves [preset] as a new one of the user's own and puts its shape on the Timer, which it came from. */
    fun saveCurrent(preset: Preset) {
        repository.saveAndApplyPreset(preset)
    }

    fun delete(preset: Preset) {
        repository.deletePreset(preset)
    }

    private fun buildState(config: IntervalConfig, entries: List<PresetEntry>): PresetsUiState {
        // Every total counts the user's own get-ready countdown, as a workout would.
        fun totalOf(preset: Preset) = preset.config.copy(prepSeconds = config.prepSeconds).totalSeconds
        val onTimer = entries.matching(config)
        val canSaveCurrent = onTimer == null
        val longest = (entries.map { totalOf(it.preset) } + if (canSaveCurrent) config.totalSeconds else 0)
            .max()
            .coerceAtLeast(1)
        val rows = entries.map { entry ->
            val total = totalOf(entry.preset)
            entry.custom to PresetRowItem(
                preset = entry.preset,
                total = formatClock(total),
                selected = entry.preset == onTimer,
                share = total.toFloat() / longest,
            )
        }
        return PresetsUiState(
            custom = rows.filter { it.first }.map { it.second },
            builtIn = rows.filterNot { it.first }.map { it.second },
            canSaveCurrent = canSaveCurrent,
            current = config,
            currentShare = config.totalSeconds.toFloat() / longest,
        )
    }
}
