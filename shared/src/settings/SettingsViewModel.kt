package dev.justinsmith.interval.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.data.AppSettings
import dev.justinsmith.interval.data.CueSettings
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.data.ThemeMode
import dev.justinsmith.interval.timer.ConfigField
import dev.justinsmith.interval.timer.stepped
import dev.justinsmith.interval.timer.withValue
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SettingsUiState(
    val cues: CueSettings,
    val prepSeconds: Int,
    val app: AppSettings,
)

/** Toggles a single [CueSettings] flag. */
enum class CueToggle { Sound, Voice, Halfway, Vibrate }

class SettingsViewModel(private val repository: IntervalRepository) : ViewModel() {
    val state: StateFlow<SettingsUiState> = combine(
        repository.cueSettings,
        repository.config,
        repository.appSettings,
    ) { cues, config, app -> SettingsUiState(cues, config.prepSeconds, app) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SettingsUiState(
                repository.cueSettings.value,
                repository.config.value.prepSeconds,
                repository.appSettings.value,
            ),
        )

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

    fun stepPrep(direction: Int) {
        repository.setConfig(repository.config.value.stepped(ConfigField.Prep, direction))
    }

    fun setPrep(seconds: Int) {
        repository.setConfig(repository.config.value.withValue(ConfigField.Prep, seconds))
    }

    fun toggleKeepScreenOn() {
        val app = repository.appSettings.value
        repository.setAppSettings(app.copy(keepScreenOn = !app.keepScreenOn))
    }

    fun setTheme(theme: ThemeMode) {
        repository.setAppSettings(repository.appSettings.value.copy(theme = theme))
    }
}
