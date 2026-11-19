package dev.justinsmith.interval.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.platform.ClassSensor
import dev.justinsmith.interval.platform.SensorFailure
import dev.justinsmith.interval.platform.SensorStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How the heart-rate sensor stands for Settings' row; [looking] while one is being looked for nearby. */
data class SensorSettingsUiState(
    val status: SensorStatus = SensorStatus.Unavailable,
    val looking: Boolean = false,
)

/**
 * The heart-rate sensor in Settings: pairing the one found nearby, and letting it go. A class is recorded and
 * synced from Today.
 */
class SensorSettingsViewModel(private val sensor: ClassSensor) : ViewModel() {
    private val looking = MutableStateFlow(false)
    private val failed = Channel<SensorFailure>(Channel.BUFFERED)

    /** Each pairing that failed, once, for the screen to say. */
    val failures: Flow<SensorFailure> = failed.receiveAsFlow()

    val state: StateFlow<SensorSettingsUiState> =
        combine(sensor.status, looking, ::SensorSettingsUiState)
            .stateIn(viewModelScope, SharingStarted.Eagerly, SensorSettingsUiState(sensor.status.value))

    /** Looks for a sensor nearby and takes it; a second tap while it looks does nothing. */
    fun pair() {
        if (looking.value) return
        looking.value = true
        viewModelScope.launch {
            try {
                sensor.pair()?.let { failed.send(it) }
            } finally {
                looking.value = false
            }
        }
    }

    fun forget() = sensor.forget()
}
