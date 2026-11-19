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

/** What a sync came to. */
sealed interface SensorSync {
    data class Synced(val recording: ClassRecording) : SensorSync

    data class Failed(val why: SensorFailure) : SensorSync
}

/**
 * The heart-rate sensor worn through a class, which records on its own memory while the phone is in the bag:
 * paired once, set recording before the class and synced after. Each call reaches the sensor for as long as it
 * takes and lets it go again, so nothing here holds a connection. A failure leaves things as they stood: a
 * recording that wouldn't sync is on the sensor still.
 */
interface ClassSensor {
    val status: StateFlow<SensorStatus>

    /** Takes the sensor found nearby as the app's own; null once it is. */
    suspend fun pair(): SensorFailure?

    /** Lets the paired sensor go. A recording on it stays there, the app's no longer. */
    fun forget()

    /** Sets the paired sensor recording; null once it is. */
    suspend fun startRecording(): SensorFailure?

    /** Stops the recording, brings it over and clears it from the sensor. */
    suspend fun sync(): SensorSync
}

/** The sensor of a platform with none. */
object NoClassSensor : ClassSensor {
    override val status: StateFlow<SensorStatus> = MutableStateFlow(SensorStatus.Unavailable)

    override suspend fun pair(): SensorFailure = SensorFailure.NotFound

    override fun forget() = Unit

    override suspend fun startRecording(): SensorFailure = SensorFailure.NotFound

    override suspend fun sync(): SensorSync = SensorSync.Failed(SensorFailure.NotFound)
}
