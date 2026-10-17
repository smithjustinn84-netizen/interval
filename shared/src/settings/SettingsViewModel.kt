package dev.justinsmith.interval.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.data.AppSettings
import dev.justinsmith.interval.data.CueSettings
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.data.ThemeMode
import dev.justinsmith.interval.platform.Beep
import dev.justinsmith.interval.platform.CuePlayer
import dev.justinsmith.interval.platform.Haptic
import dev.justinsmith.interval.platform.Speech
import dev.justinsmith.interval.timer.ConfigField
import dev.justinsmith.interval.timer.Cue
import dev.justinsmith.interval.timer.stepped
import dev.justinsmith.interval.timer.withValue
import dev.justinsmith.interval.workout.playCue
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

/** The set-once preferences. A cue switched on plays a sample through [player], so its volume can be checked. */
class SettingsViewModel(
    private val repository: IntervalRepository,
    private val player: CuePlayer,
) : ViewModel() {
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
        repository.updateCueSettings { cues ->
            when (toggle) {
                CueToggle.Sound -> cues.copy(sound = !cues.sound)
                CueToggle.Voice -> cues.copy(voice = !cues.voice)
                CueToggle.Halfway -> cues.copy(halfway = !cues.halfway)
                CueToggle.Vibrate -> cues.copy(vibrate = !cues.vibrate)
            }
        }
        preview(toggle, repository.cueSettings.value)
    }

    /** Plays the cue [toggle] has just switched on, as a workout would; switching one off is silent. */
    private fun preview(toggle: CueToggle, cues: CueSettings) {
        when (toggle) {
            CueToggle.Sound -> if (cues.sound) player.beep(Beep.Work)
            CueToggle.Voice -> if (cues.voice) player.speak(Speech.Work(lastRound = false))
            CueToggle.Halfway -> playCue(Cue.Halfway, cues, player)
            CueToggle.Vibrate -> if (cues.vibrate) player.vibrate(Haptic.Work)
        }
    }

    fun stepPrep(direction: Int) {
        repository.updateConfig { it.stepped(ConfigField.Prep, direction) }
    }

    fun setPrep(seconds: Int) {
        repository.updateConfig { it.withValue(ConfigField.Prep, seconds) }
    }

    fun toggleKeepScreenOn() {
        repository.updateAppSettings { it.copy(keepScreenOn = !it.keepScreenOn) }
    }

    fun setTheme(theme: ThemeMode) {
        repository.updateAppSettings { it.copy(theme = theme) }
    }
}
