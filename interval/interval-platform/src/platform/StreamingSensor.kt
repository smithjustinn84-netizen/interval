package dev.justinsmith.interval.platform

import co.touchlab.kermit.Logger
import dev.justinsmith.interval.model.ClassRecording
import dev.justinsmith.interval.storage.catchingFailures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** Whether the platform's Bluetooth may be used for a [StreamingSensor]. */
interface SensorAccess {
    /** Whether the device has Bluetooth LE at all, as far as is known: no sensor is offered without it. */
    val supported: Boolean

    /** Whether the app may use it, asking nothing: what the stream goes by. */
    val allowed: Boolean

    /** Whether it is on and the app may use it, asking the user first if they never have been: a pairing's. */
    suspend fun ready(): Boolean
}

/** Which sensor is the app's, and its battery as it last said, kept between runs of the app. */
interface PairedSensorStore {
    /** The sensor paired, by the platform's id for it; null with none. */
    val sensor: NearbySensor?

    /** Its battery, a percentage; null until it has said. */
    var battery: Int?

    /** Takes [id] as the app's sensor; the last one's battery is forgotten. */
    fun pair(id: String, name: String)

    fun clear()
}

/**
 * A heart-rate sensor that only streams: any strap or armband of Bluetooth's standard Heart Rate service, paired
 * over [radio] and read live by a [HeartRateStream] while a workout or a session holds it. It records no class (a
 * Polar sensor does so only through Polar's SDK, on Android), so it stands as [SensorStatus.Paired] with
 * `recordsClasses` false whatever it is, and Today offers no class on it. iOS's sensor, over CoreBluetooth.
 *
 * Every piece of work with the sensor runs one at a time with the stream out of its way, in [scope], which
 * [close] ends.
 */
class StreamingSensor(
    private val radio: HeartRateRadio,
    private val paired: PairedSensorStore,
    private val access: SensorAccess,
    private val scope: CoroutineScope,
    private val log: Logger = Logger.withTag("StreamingSensor"),
) : ClassSensor,
    LiveHeartRate {
    /** One piece of work with the sensor at a time: a pairing or a forgetting. */
    private val working = Mutex()

    private val _status = MutableStateFlow(stored())
    override val status: StateFlow<SensorStatus> = _status.asStateFlow()

    private val _battery = MutableStateFlow(paired.battery)
    override val battery: StateFlow<Int?> = _battery.asStateFlow()

    /** Live from the paired sensor, unless Bluetooth may not be used. */
    private val stream = HeartRateStream(
        radio,
        sensorId = { paired.sensor?.id?.takeIf { access.supported && access.allowed } },
        idle = { if (stored() is SensorStatus.Paired) LiveHeartRateState.Idle else LiveHeartRateState.Unavailable },
        scope = scope,
        onBattery = ::heardBattery,
        log = log,
    )
    override val reading: StateFlow<LiveHeartRateState> get() = stream.reading
    override val readings: Flow<Int> get() = stream.readings

    private fun stored(): SensorStatus {
        if (!access.supported) return SensorStatus.Unavailable
        val sensor = paired.sensor ?: return SensorStatus.Unpaired
        return SensorStatus.Paired(sensor.name, recordsClasses = false)
    }

    private fun heardBattery(level: Int) {
        paired.battery = level
        _battery.value = paired.battery
    }

    override suspend fun pair(choose: suspend (List<NearbySensor>) -> NearbySensor?): SensorFailure? = attempt {
        val nearby = nearby()
        val picked = when (nearby.size) {
            0 -> return@attempt SensorFailure.NotFound

            1 -> nearby.single()

            // Two in reach: the first heard is whichever advertised first, not the one meant.
            else -> choose(nearby) ?: return@attempt null
        }
        paired.pair(picked.id, picked.name)
        _battery.value = null
        _status.value = stored()
        stream.settle()
        null
    }

    /**
     * Every heart-rate sensor heard nearby, the nearest first: the scan goes on a few seconds past the first one,
     * since another may be in reach beside it. Empty when none was heard in time.
     */
    private suspend fun nearby(): List<NearbySensor> = coroutineScope {
        // Each by its id, its latest signal kept.
        val heard = MutableStateFlow(emptyMap<String, HeardSensor>())
        val scan = launch { radio.scan().collect { sensor -> heard.update { it + (sensor.id to sensor) } } }
        val first = withTimeoutOrNull(SCAN_MILLIS) { heard.first { it.isNotEmpty() } }
        if (first != null) delay(SETTLE_MILLIS)
        scan.cancel()
        heard.value.values.sortedByDescending { it.rssi }.map { NearbySensor(it.id, it.name) }
    }

    override suspend fun forget() = working.withLock {
        paired.clear()
        _battery.value = null
        _status.value = stored()
        stream.settle()
    }

    /** Nothing is recorded on a sensor that only streams; never offered, since it says it records no class. */
    override suspend fun startRecording(): SensorFailure = SensorFailure.Failed

    override suspend fun sync(keep: suspend (ClassRecording) -> Unit): SensorFailure = SensorFailure.NothingRecorded

    override fun start(user: Any) = stream.start(user)

    override fun stop(user: Any) = stream.stop(user)

    /**
     * [work] one at a time, with Bluetooth on and allowed and the live stream out of its way, a failure logged and
     * answered rather than thrown.
     */
    private suspend fun attempt(work: suspend () -> SensorFailure?): SensorFailure? {
        if (!access.supported || !access.allowed || !access.ready()) return SensorFailure.NoAccess
        return working.withLock {
            stream.take()
            try {
                catchingFailures(
                    onFailure = { e ->
                        log.w(e) { "The sensor failed" }
                        SensorFailure.Failed
                    },
                ) { work() }
            } finally {
                stream.giveBack()
            }
        }
    }

    /** Lets everything go as the container closes. */
    fun close() = scope.cancel()

    companion object {
        /** How long a pairing's scan waits to hear a sensor at all. */
        const val SCAN_MILLIS = 20_000L

        /** How long the scan goes on once it has heard a sensor, for another beside it to be heard too. */
        const val SETTLE_MILLIS = 4_000L
    }
}
