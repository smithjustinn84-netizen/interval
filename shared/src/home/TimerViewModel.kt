package dev.justinsmith.interval.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.data.AppSettings
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.ConfigField
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.timer.matching
import dev.justinsmith.interval.timer.stepped
import dev.justinsmith.interval.timer.withValue
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class TimerUiState(
    val config: IntervalConfig,
    val total: String,
    /** The matching preset's name, or null for a shape no preset has. */
    val presetName: String?,
    /** Every preset, so the editor can say when a name or shape is taken. */
    val presets: List<Preset> = emptyList(),
    /** The estimated calories the workout will burn, or null when no body weight is set. */
    val calories: Int? = null,
) {
    /** A shape no preset has yet, so the title bar offers to save it. */
    val canSavePreset: Boolean get() = presetName == null
}

class TimerViewModel(private val repository: IntervalRepository) : ViewModel() {
    val state: StateFlow<TimerUiState> =
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

    /** What the work intervals are spent doing, for the calorie estimate. */
    fun setActivity(activity: Activity) {
        repository.updateConfig { it.copy(activity = activity) }
    }

    fun step(field: ConfigField, direction: Int) {
        repository.updateConfig { it.stepped(field, direction) }
    }

    fun set(field: ConfigField, value: Int) {
        repository.updateConfig { it.withValue(field, value) }
    }

    /** Saves [preset] as a new one of the user's own, keeping its shape (changed in the editor or not) on the Timer. */
    fun savePreset(preset: Preset) {
        repository.saveAndApplyPreset(preset)
    }

    private fun buildState(config: IntervalConfig, presets: List<Preset>, app: AppSettings): TimerUiState =
        TimerUiState(
            config = config,
            total = formatClock(config.totalSeconds),
            presetName = presets.matching(config)?.name,
            presets = presets,
            calories = app.body.plannedCalories(config),
        )
}
