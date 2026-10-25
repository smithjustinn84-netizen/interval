package dev.justinsmith.interval.presets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.model.AppSettings
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.timer.matching
import dev.justinsmith.interval.timer.withPreset
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
    /** The estimated calories it will burn, with the user's get-ready countdown; null without a body weight. */
    val calories: Int? = null,
    /**
     * The Timer's activity, when the estimate is worked out with it because the preset names none of its
     * own; null when the preset has one, or the Timer's is General too.
     */
    val estimatedAs: Activity? = null,
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
    /** The current settings' estimated calories, as [PresetRowItem.calories]. */
    val currentCalories: Int? = null,
) {
    /** Every row the screen shows, the user's own first. */
    val rows: List<PresetRowItem> get() = custom + builtIn
}

class PresetsViewModel(private val repository: IntervalRepository) : ViewModel() {
    val state: StateFlow<PresetsUiState> =
        combine(repository.config, repository.presets, repository.appSettings, ::buildState)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = buildState(
                    repository.config.value,
                    repository.presets.value,
                    repository.appSettings.value,
                ),
            )

    /** Puts [preset]'s shape on the Timer. */
    fun putOnTimer(preset: Preset) {
        repository.applyPreset(preset)
    }

    /**
     * Saves [preset]: a new one of the user's own, or the changes to the one its id names. Changes to
     * the preset on the Timer go onto the Timer too, so the two stay in step rather than leaving the
     * old shape there, unsaved.
     */
    fun save(preset: Preset) {
        if (preset.custom) repository.updatePreset(preset) else repository.savePreset(preset)
    }

    /** Saves [preset] as a new one of the user's own and puts its shape on the Timer, which it came from. */
    fun saveCurrent(preset: Preset) {
        repository.saveAndApplyPreset(preset)
    }

    fun delete(preset: Preset) {
        repository.deletePreset(preset)
    }

    /** Undo on [delete]: the preset comes back under its own id. */
    fun restore(preset: Preset) {
        repository.restorePreset(preset)
    }

    private fun buildState(config: IntervalConfig, presets: List<Preset>, app: AppSettings): PresetsUiState {
        // Every total and estimate is the preset as it would run from the Timer: with the user's own
        // get-ready countdown, and the Timer's activity unless the preset names one.
        fun asRun(preset: Preset) = config.withPreset(preset)
        fun totalOf(preset: Preset) = asRun(preset).totalSeconds
        val onTimer = presets.matching(config)
        val canSaveCurrent = onTimer == null
        val longest = (presets.map { totalOf(it) } + if (canSaveCurrent) config.totalSeconds else 0)
            .max()
            .coerceAtLeast(1)
        val rows = presets.map { preset ->
            val total = totalOf(preset)
            PresetRowItem(
                preset = preset,
                total = formatClock(total),
                selected = preset == onTimer,
                share = total.toFloat() / longest,
                calories = app.body.plannedCalories(asRun(preset)),
                estimatedAs = config.activity.takeIf {
                    preset.config.activity == Activity.General && it != Activity.General
                },
            )
        }
        return PresetsUiState(
            custom = rows.filter { it.preset.custom },
            builtIn = rows.filterNot { it.preset.custom },
            canSaveCurrent = canSaveCurrent,
            current = config,
            currentShare = config.totalSeconds.toFloat() / longest,
            currentCalories = app.body.plannedCalories(config),
        )
    }
}
