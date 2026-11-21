package dev.justinsmith.interval.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.platform.ClassSensor
import dev.justinsmith.interval.platform.NearbySensor
import dev.justinsmith.interval.platform.SensorFailure
import dev.justinsmith.interval.platform.SensorStatus
import kotlinx.coroutines.CompletableDeferred
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
    /** The paired sensor's battery as it last said, a percentage; null until it has. */
    val battery: Int? = null,
    /** The sensors found nearby when there was more than one, the nearest first, for one to be picked of. */
    val choices: List<NearbySensor> = emptyList(),
)

/**
 * The heart-rate sensor in Settings: pairing the one found nearby, the one picked when there are several, and
 * letting it go. A class is recorded and synced from Today.
 */
class SensorSettingsViewModel(private val sensor: ClassSensor) : ViewModel() {
    private val looking = MutableStateFlow(false)
    private val failed = Channel<SensorFailure>(Channel.BUFFERED)
    private val choices = MutableStateFlow(emptyList<NearbySensor>())

    /** The answer the pairing waits on while [choices] are shown. */
    private var picking: CompletableDeferred<NearbySensor?>? = null

    /** Each pairing that failed, once, for the screen to say. */
    val failures: Flow<SensorFailure> = failed.receiveAsFlow()

    val state: StateFlow<SensorSettingsUiState> =
        combine(sensor.status, looking, sensor.battery, choices, ::SensorSettingsUiState).stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            SensorSettingsUiState(sensor.status.value, battery = sensor.battery.value),
        )

    /**
     * Looks for a sensor nearby and takes it, or with several asks which ([choose] answers); a second tap while
     * it looks does nothing.
     */
    fun pair() {
        if (looking.value) return
        looking.value = true
        viewModelScope.launch {
            try {
                sensor.pair(::ask)?.let { failed.send(it) }
            } finally {
                looking.value = false
            }
        }
    }

    /** Takes [picked] of the sensors offered, or none of them with null. */
    fun choose(picked: NearbySensor?) {
        picking?.complete(picked)
    }

    private suspend fun ask(found: List<NearbySensor>): NearbySensor? {
        val answer = CompletableDeferred<NearbySensor?>()
        picking = answer
        choices.value = found
        try {
            return answer.await()
        } finally {
            choices.value = emptyList()
            picking = null
        }
    }

    /** Lets the paired sensor go, once whatever is being done with it is over. */
    fun forget() {
        viewModelScope.launch { sensor.forget() }
    }
}
