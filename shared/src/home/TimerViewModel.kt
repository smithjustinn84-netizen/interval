package dev.justinsmith.interval.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.timer.ConfigField
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.PresetEntry
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
)

class TimerViewModel(private val repository: IntervalRepository) : ViewModel() {
    val state: StateFlow<TimerUiState> = combine(repository.config, repository.presets, ::buildState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = buildState(repository.config.value, repository.presets.value),
        )

    fun step(field: ConfigField, direction: Int) {
        repository.updateConfig { it.stepped(field, direction) }
    }

    fun set(field: ConfigField, value: Int) {
        repository.updateConfig { it.withValue(field, value) }
    }

    private fun buildState(config: IntervalConfig, entries: List<PresetEntry>): TimerUiState = TimerUiState(
        config = config,
        total = formatClock(config.totalSeconds),
        presetName = entries.matching(config)?.name,
    )
}
