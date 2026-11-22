package dev.justinsmith.interval.platform

import co.touchlab.kermit.Logger
import dev.justinsmith.interval.storage.catchingFailures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What one connection to a heart-rate sensor brings: a reading, or what its battery says. */
sealed interface HeartRateFrame {
    data class Measured(val reading: LiveHeartRateState.Reading) : HeartRateFrame

    /** The sensor's battery, a percentage. */
    data class Battery(val percent: Int) : HeartRateFrame
}

/** The radio under a [HeartRateStream]: each platform's Bluetooth, speaking the standard Heart Rate service. */
fun interface HeartRateLink {
    /**
     * Connects to the sensor [id] (the platform's own id for it) and brings what it sends, its battery first if it
     * says it, until the connection ends (the flow completes) or never comes or fails (it throws). Its collector
     * cancelled, it lets the sensor go.
     */
    fun connect(id: String): Flow<HeartRateFrame>
}

/** A sensor heard nearby: the platform's own [id] for it, the [name] it advertises and how strong it was heard. */
data class HeardSensor(val id: String, val name: String, val rssi: Int)

/**
 * A [HeartRateLink] that can also look for sensors: Android's GATT and iOS's CoreBluetooth. The platform's id for a
 * sensor is its Bluetooth address on Android and the peripheral's identifier on iOS, the same one each time.
 */
interface HeartRateRadio : HeartRateLink {
    /**
     * Every sensor heard nearby that speaks the Heart Rate service, or that goes by a Polar sensor's name, as it is
     * heard: the same one again each time it advertises, its signal then. Scanning while collected.
     */
    fun scan(): Flow<HeardSensor>
}

/**
 * The heart rate live from the paired sensor over [link], for a workout or a Lift session: a connection held while
 * anyone holds the stream ([start] to [stop], each by its user), tried again whenever it drops, after a wait that
 * grows to [MAX_RETRY_MILLIS] while none reads a heart. A sensor takes one connection, so the stream gives way to
 * any other work with it ([take]) and comes back after ([giveBack]).
 *
 * [sensorId] is the sensor to stream from, null while none may be (none paired, Bluetooth not allowed, or it is
 * recording a class), asked again at each start and at [settle]; [idle] is what the reading stands at with nothing
 * streaming. Its battery, as the sensor says it, goes to [onBattery]. It runs in [scope], which it never ends.
 */
class HeartRateStream(
    private val link: HeartRateLink,
    private val sensorId: () -> String?,
    private val idle: () -> LiveHeartRateState,
    scope: CoroutineScope,
    private val onBattery: (Int) -> Unit = {},
    private val log: Logger = Logger.withTag("HeartRateStream"),
) : LiveHeartRate {
    /** Who wants the heart rate live, streaming or not: what the stream comes back to after other work. */
    private val users = MutableStateFlow(emptySet<Any>())

    /** Whether other work has the sensor: the stream waits for it. */
    private val taken = MutableStateFlow(false)

    /** Moved on at [settle], so the sensor to stream from is asked again. */
    private val asked = MutableStateFlow(0)

    /** Whether a connection may be open: what [take] waits on to be gone. */
    private val streaming = MutableStateFlow(false)

    private val _reading = MutableStateFlow(idle())
    override val reading: StateFlow<LiveHeartRateState> = _reading.asStateFlow()

    private val _readings = MutableSharedFlow<Int>(extraBufferCapacity = READINGS_BUFFER)
    override val readings: Flow<Int> = _readings.asSharedFlow()

    init {
        scope.launch {
            // A holder joining or leaving while another holds it changes nothing here: the connection stays up.
            combine(
                users.map { it.isNotEmpty() },
                taken,
                asked,
                ::Triple,
            ).distinctUntilChanged().collectLatest { (wanted, taken) ->
                // Waiting on other work the stream says it is coming; else nothing wanted or nothing to stream from.
                val id = if (wanted && !taken) sensorId() else null
                if (id == null) {
                    _reading.value = if (wanted && taken) LiveHeartRateState.Connecting else idle()
                    return@collectLatest
                }
                streaming.value = true
                try {
                    stream(id)
                } finally {
                    streaming.value = false
                }
            }
        }
    }

    override fun start(user: Any) {
        users.update { it + user }
        // With nothing streaming, a new holder asks for the sensor again: Bluetooth may have been switched on since.
        if (!streaming.value) settle()
    }

    override fun stop(user: Any) = users.update { it - user }

    /** Stops it for everyone: the sensor is the app's no longer. */
    fun stopAll() {
        users.value = emptySet()
    }

    /** The sensor to stream from, or the reading with none, may have changed: paired, forgotten, or recording. */
    fun settle() = asked.update { it + 1 }

    /** Takes the sensor from the stream for other work, once its connection is let go. */
    suspend fun take() {
        taken.value = true
        streaming.first { !it }
    }

    /** Hands the sensor back: a stream still wanted starts again, if the sensor may still be streamed from. */
    fun giveBack() {
        taken.value = false
    }

    /** Streams from the sensor [id] until cancelled, trying again whenever a connection ends, fails or never comes. */
    private suspend fun stream(id: String) {
        var wait = RETRY_MILLIS
        _reading.value = LiveHeartRateState.Connecting
        while (true) {
            val read = catchingFailures(
                onFailure = { e ->
                    log.w(e) { "Live heart rate failed" }
                    false
                },
            ) { streamOnce(id) }
            _reading.value = LiveHeartRateState.Disconnected
            // Straight back after a connection that read a heart; slower and slower while none comes.
            wait = if (read) RETRY_MILLIS else (wait * 2).coerceAtMost(MAX_RETRY_MILLIS)
            delay(wait)
        }
    }

    /** One connection's readings, until it ends; whether it read a heart at all. */
    private suspend fun streamOnce(id: String): Boolean {
        var read = false
        link.connect(id).collect { frame ->
            when (frame) {
                is HeartRateFrame.Battery -> onBattery(frame.percent)

                is HeartRateFrame.Measured -> {
                    _reading.value = frame.reading
                    if (frame.reading.contact) {
                        read = true
                        _readings.tryEmit(frame.reading.bpm)
                    }
                }
            }
        }
        return read
    }

    companion object {
        /** The wait before a dropped connection is tried again, and what it grows to while none reads a heart. */
        const val RETRY_MILLIS = 5_000L
        const val MAX_RETRY_MILLIS = 30_000L

        /** Readings held for a collector that is behind: a minute of a reading a second. */
        private const val READINGS_BUFFER = 64
    }
}
