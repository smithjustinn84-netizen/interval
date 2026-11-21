package dev.justinsmith.interval.platform

import dev.justinsmith.interval.model.ClassRecording
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Where the heart-rate sensor a class is recorded on stands. */
sealed interface SensorStatus {
    /** The platform has no sensor to offer: nothing about one is shown. */
    data object Unavailable : SensorStatus

    data object Unpaired : SensorStatus

    /** One is paired, by the [name] it goes by, and records nothing for the app. */
    data class Paired(val name: String) : SensorStatus

    /** It was set recording at [sinceEpochMillis], and the class is on it still, to be synced. */
    data class Recording(val name: String, val sinceEpochMillis: Long) : SensorStatus
}

/** A sensor heard nearby, by the [name] it goes by; its [id] is the platform's own for it. */
data class NearbySensor(val id: String, val name: String)

/** Under this share of its battery a sensor is said to want charging: a class is an hour and a half. */
const val LOW_SENSOR_BATTERY = 20

/** Why the sensor didn't do what was asked. */
enum class SensorFailure {
    /** The app may not use Bluetooth, or it is switched off. */
    NoAccess,

    /** No sensor answered: out of reach, asleep or not on the arm. */
    NotFound,

    /** It answered and then the work failed. */
    Failed,

    /** It was reached and held no recording to sync. */
    NothingRecorded,
}

/**
 * The heart-rate sensor worn through a class, which records on its own memory while the phone is in the bag:
 * paired once, set recording before the class and synced after. Each call reaches the sensor for as long as it
 * takes and lets it go again, so none of these holds a connection once it returns. A failure leaves things as
 * they stood: a recording that wouldn't sync, or couldn't be kept, is on the sensor still.
 */
interface ClassSensor {
    val status: StateFlow<SensorStatus>

    /**
     * The paired sensor's battery as it last said, a percentage: heard whenever the sensor is reached, kept
     * between, so it may be hours old. Null until it has said, and with no sensor paired.
     */
    val battery: StateFlow<Int?>

    /**
     * Takes the sensor found nearby as the app's own; null once it is. With more than one in reach it is the one
     * [choose] picks of them, the nearest first: the app can't tell which is meant. A null from [choose] takes
     * none, which is no failure either.
     */
    suspend fun pair(choose: suspend (List<NearbySensor>) -> NearbySensor?): SensorFailure?

    /** Lets the paired sensor go. A recording on it stays there, the app's no longer. */
    suspend fun forget()

    /** Sets the paired sensor recording; null once it is. */
    suspend fun startRecording(): SensorFailure?

    /**
     * Stops the recording and brings it over to [keep]; null once it is kept and cleared from the sensor. The
     * sensor is cleared only after [keep] returns, so a recording it couldn't store (it throws, answered as
     * [SensorFailure.Failed]) is there to sync again. With nothing on the sensor, [keep] isn't called, and the
     * sensor stands ready again.
     */
    suspend fun sync(keep: suspend (ClassRecording) -> Unit): SensorFailure?
}

/** The sensor of a platform with none. */
object NoClassSensor : ClassSensor {
    override val status: StateFlow<SensorStatus> = MutableStateFlow(SensorStatus.Unavailable)
    override val battery: StateFlow<Int?> = MutableStateFlow(null)

    override suspend fun pair(choose: suspend (List<NearbySensor>) -> NearbySensor?): SensorFailure =
        SensorFailure.NotFound

    override suspend fun forget() = Unit

    override suspend fun startRecording(): SensorFailure = SensorFailure.NotFound

    override suspend fun sync(keep: suspend (ClassRecording) -> Unit): SensorFailure = SensorFailure.NotFound
}
