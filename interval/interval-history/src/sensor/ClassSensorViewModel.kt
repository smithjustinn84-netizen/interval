package dev.justinsmith.interval.sensor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.data.HistoryStore
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.platform.ClassSensor
import dev.justinsmith.interval.platform.SensorFailure
import dev.justinsmith.interval.platform.SensorStatus
import dev.justinsmith.interval.platform.SensorSync
import dev.justinsmith.interval.platform.TimestampFormatter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What Today's sensor card stands at. */
sealed interface SensorCard {
    /** A sensor is paired, by [name], and a class can be set recording on it. */
    data class Ready(val name: String) : SensorCard

    /** A class has been recording [since], a time of day as the device writes one ("6:02 PM"). */
    data class Recording(val since: String) : SensorCard
}

/** [card] is null with no sensor paired: Today shows nothing of one. [busy] while the sensor is being reached. */
data class ClassSensorUiState(val card: SensorCard? = null, val busy: Boolean = false)

/** What reaching the sensor came to, once, for the screen to say or to follow. */
sealed interface ClassSensorEvent {
    data object Started : ClassSensorEvent

    /** A class is kept and its day logged: its page opens, by the moment it started. */
    data class Synced(val startedAtEpochMillis: Long) : ClassSensorEvent

    data class Failed(val why: SensorFailure) : ClassSensorEvent
}

/**
 * A class recorded on the heart-rate sensor, from Today: set recording before it, synced after. A sync keeps the
 * recording, with the rounds found in it, and logs the sport's day it started on, as "Log it" would.
 */
class ClassSensorViewModel(
    private val sensor: ClassSensor,
    private val historyStore: HistoryStore,
    private val timestamps: TimestampFormatter,
) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val happened = Channel<ClassSensorEvent>(Channel.BUFFERED)

    val events: Flow<ClassSensorEvent> = happened.receiveAsFlow()

    val state: StateFlow<ClassSensorUiState> =
        combine(sensor.status, busy) { status, busy -> ClassSensorUiState(card(status), busy) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, ClassSensorUiState(card(sensor.status.value)))

    private fun card(status: SensorStatus): SensorCard? = when (status) {
        is SensorStatus.Paired -> SensorCard.Ready(status.name)
        is SensorStatus.Recording -> SensorCard.Recording(timestamps.time(status.sinceEpochMillis))
        SensorStatus.Unavailable, SensorStatus.Unpaired -> null
    }

    /** Sets the sensor recording. */
    fun record() = reach {
        sensor.startRecording()?.let(ClassSensorEvent::Failed) ?: ClassSensorEvent.Started
    }

    /** Brings the recording over as a class of [sport], on the day it started. */
    fun sync(sport: Sport) = reach {
        when (val synced = sensor.sync()) {
            is SensorSync.Failed -> ClassSensorEvent.Failed(synced.why)

            is SensorSync.Synced -> {
                val recording = synced.recording
                if (recording.samples.none { it.read }) {
                    ClassSensorEvent.Failed(SensorFailure.NothingRecorded)
                } else {
                    val day = timestamps.dayNumber(recording.startedAtEpochMillis)
                    historyStore.keepClass(sport, day, recording)
                    historyStore.logSport(sport, day)
                    ClassSensorEvent.Synced(recording.startedAtEpochMillis)
                }
            }
        }
    }

    /** One piece of work with the sensor at a time: a second tap while one runs does nothing. */
    private fun reach(work: suspend () -> ClassSensorEvent) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                happened.send(work())
            } finally {
                busy.value = false
            }
        }
    }
}
