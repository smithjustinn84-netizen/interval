package dev.justinsmith.interval.sensor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.data.HistoryStore
import dev.justinsmith.interval.data.PlanStore
import dev.justinsmith.interval.history.TrainingLog
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.platform.ClassSensor
import dev.justinsmith.interval.platform.LOW_SENSOR_BATTERY
import dev.justinsmith.interval.platform.SensorFailure
import dev.justinsmith.interval.platform.SensorStatus
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.platform.WallClock
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
    /**
     * A sensor is paired, by [name], and a class can be set recording on it. [lowBattery] is its battery as it
     * last said when that is too little to trust with a class, a percentage; null otherwise.
     */
    data class Ready(val name: String, val lowBattery: Int? = null) : SensorCard

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
 * recording and logs the sport's day it started on, as "Log it" would, as the sport planned that day when it is
 * synced a day late; the sensor is cleared of it only once it is
 * stored.
 */
class ClassSensorViewModel(
    private val sensor: ClassSensor,
    private val historyStore: HistoryStore,
    private val timestamps: TimestampFormatter,
    private val planStore: PlanStore,
    private val clock: WallClock,
) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val happened = Channel<ClassSensorEvent>(Channel.BUFFERED)

    val events: Flow<ClassSensorEvent> = happened.receiveAsFlow()

    val state: StateFlow<ClassSensorUiState> =
        combine(sensor.status, busy, sensor.battery) { status, busy, battery ->
            ClassSensorUiState(card(status, battery), busy)
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            ClassSensorUiState(card(sensor.status.value, sensor.battery.value)),
        )

    private fun card(status: SensorStatus, battery: Int?): SensorCard? = when (status) {
        is SensorStatus.Paired -> SensorCard.Ready(status.name, battery?.takeIf { it < LOW_SENSOR_BATTERY })
        is SensorStatus.Recording -> SensorCard.Recording(timestamps.time(status.sinceEpochMillis))
        SensorStatus.Unavailable, SensorStatus.Unpaired -> null
    }

    /** Sets the sensor recording. */
    fun record() = reach {
        sensor.startRecording()?.let(ClassSensorEvent::Failed) ?: ClassSensorEvent.Started
    }

    /**
     * Brings the recording over as a class of [sport], on the day it started. One with no heart rate read is
     * nothing to keep, and is let go from the sensor all the same.
     */
    fun sync(sport: Sport) = reach {
        var kept: Long? = null
        val failure = sensor.sync { recording ->
            if (recording.samples.any { it.read }) {
                val day = timestamps.dayNumber(recording.startedAtEpochMillis)
                val its = sportOn(day) ?: sport
                historyStore.keepClass(its, day, recording)
                historyStore.logSport(its, day)
                kept = recording.startedAtEpochMillis
            }
        }
        when {
            failure != null -> ClassSensorEvent.Failed(failure)
            else -> kept?.let(ClassSensorEvent::Synced) ?: ClassSensorEvent.Failed(SensorFailure.NothingRecorded)
        }
    }

    /**
     * The sport planned on [day] when that is a day gone by: a class synced a day late is that day's, not the
     * sport of the day it is synced on. Null for today, which Today has read already, and for a day with none
     * planned.
     */
    private fun sportOn(day: Long): Sport? {
        if (day == timestamps.dayNumber(clock.nowMillis())) return null
        return TrainingLog(emptyList(), planStore.planned.value, today = day, dayOf = timestamps::dayNumber)
            .plansOn(day)
            .firstNotNullOfOrNull { (it.workout as? PlanWorkout.SportDay)?.sport }
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
