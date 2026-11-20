package dev.justinsmith.interval.platform

import co.touchlab.kermit.Logger
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApi.PolarBleSdkFeature
import dev.justinsmith.interval.storage.catchingFailures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** What each of Polar's sensors connected has said it is ready for, by its id. */
internal typealias ReadySensors = MutableStateFlow<Map<String, Set<PolarBleSdkFeature>>>

internal fun Map<String, Set<PolarBleSdkFeature>>.has(id: String, feature: PolarBleSdkFeature) =
    this[id]?.contains(feature) == true

/**
 * The heart rate live from one of Polar's sensors, for a workout or a Lift session: a connection held while
 * anyone holds the stream ([start] to [stop], each by its user), tried again whenever it drops. The sensor takes
 * one connection, so the stream gives way to any other work with it ([take])
 * and comes back after ([giveBack]). [sensorId] is the sensor to stream from, null while none may be (none
 * paired, Bluetooth not allowed, or it is recording a class); [idle] is what the reading stands at with nothing
 * streaming.
 */
internal class PolarHeartStream(
    private val api: () -> PolarBleApi,
    private val ready: ReadySensors,
    private val log: Logger,
    private val sensorId: () -> String?,
    private val idle: () -> LiveHeartRateState,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Guards the three below: the stream is started and stopped from any thread. */
    private val lock = Any()

    @Volatile
    private var job: Job? = null

    /** Who wants the heart rate live, streaming or not: what the stream comes back to after other work. */
    private val users = mutableSetOf<Any>()

    /** Whether other work has the sensor: the stream waits for it. */
    private var taken = false

    private val _reading = MutableStateFlow(idle())
    val reading: StateFlow<LiveHeartRateState> = _reading.asStateFlow()

    fun start(user: Any) = synchronized(lock) {
        users += user
        if (!taken) launch()
    }

    fun stop(user: Any) = stop { users -= user }

    /** Stops it for everyone: the sensor is the app's no longer. */
    fun stopAll() = stop { users.clear() }

    private fun stop(leave: () -> Unit) {
        val stopped = synchronized(lock) {
            leave()
            if (users.isEmpty()) job.also { job = null } else return
        }
        stopped?.cancel()
        settle()
    }

    /** The reading as it stands with nothing streaming, after the sensor paired or its recording changed. */
    fun settle() {
        if (job?.isActive != true) _reading.value = idle()
    }

    /** The SDK says a sensor's connection is gone: a stream running has lost its readings. */
    fun dropped() {
        if (job?.isActive == true) _reading.value = LiveHeartRateState.Disconnected
    }

    /**
     * Takes the sensor from the stream for other work: the stream is stopped and its connection seen gone, so the
     * work's own connection is a fresh one and its letting go drops nothing of the stream's.
     */
    suspend fun take() {
        val running = synchronized(lock) {
            taken = true
            job.also { job = null }
        } ?: return
        val id = sensorId()
        _reading.value = LiveHeartRateState.Connecting
        running.cancelAndJoin()
        if (id == null) return
        withTimeoutOrNull(DISCONNECT_MILLIS) { ready.first { id !in it } }
        ready.update { it - id }
    }

    /** Hands the sensor back: a stream still wanted starts again, if the sensor may still be streamed from. */
    fun giveBack() = synchronized(lock) {
        taken = false
        if (users.isNotEmpty()) launch() else settle()
    }

    fun close() = scope.cancel()

    /** Starts the stream if it may run and isn't; under [lock]. */
    private fun launch() {
        if (job?.isActive == true) return
        val id = sensorId()
        if (id == null) {
            _reading.value = idle()
            return
        }
        _reading.value = LiveHeartRateState.Connecting
        job = scope.launch { stream(id) }
    }

    /**
     * Streams from the sensor [id] until cancelled: a connection that drops, never comes or fails is tried again
     * after a wait that grows to [MAX_RETRY_MILLIS] and starts over once the sensor has read a heart.
     */
    private suspend fun stream(id: String) {
        var wait = RETRY_MILLIS
        try {
            while (true) {
                val read = catchingFailures(
                    onFailure = { e ->
                        log.w(e) { "Live HR streaming failed" }
                        false
                    },
                ) { streamOnce(id) }
                _reading.value = LiveHeartRateState.Disconnected
                wait = if (read) RETRY_MILLIS else (wait * 2).coerceAtMost(MAX_RETRY_MILLIS)
                delay(wait)
            }
        } finally {
            quietly(log, "letting the stream's sensor go") { api().disconnectFromDevice(id) }
        }
    }

    /** One connection's readings, until it ends; whether it read a heart at all. */
    private suspend fun streamOnce(id: String): Boolean {
        api().connectToDevice(id)
        withTimeoutOrNull(CONNECT_MILLIS) { ready.first { it.has(id, PolarBleSdkFeature.FEATURE_HR) } } ?: return false
        var read = false
        api().startHrStreaming(id).collect { data ->
            val sample = data.samples.lastOrNull() ?: return@collect
            // A strap off the chest reads 0, and says so where it can tell: shown as no signal, never as a number.
            val contact = sample.hr in HeartRate.BPM_RANGE && (sample.contactStatus || !sample.contactStatusSupported)
            if (contact) read = true
            _reading.value = LiveHeartRateState.Reading(bpm = if (contact) sample.hr else 0, contact = contact)
        }
        return read
    }

    private companion object {
        const val CONNECT_MILLIS = 30_000L

        /** The wait before a dropped connection is tried again, and what it grows to while none comes. */
        const val RETRY_MILLIS = 5_000L
        const val MAX_RETRY_MILLIS = 30_000L

        /** How long a stopped stream's connection is waited on to be gone before the work goes ahead anyway. */
        const val DISCONNECT_MILLIS = 5_000L
    }
}
