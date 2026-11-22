package dev.justinsmith.interval.platform

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import co.touchlab.kermit.Logger
import com.polar.androidcommunications.api.ble.model.DisInfo
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApi.PolarBleSdkFeature
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.PolarBleDisconnectInfo
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarHealthThermometerData
import dev.justinsmith.interval.model.ClassRecording
import dev.justinsmith.interval.storage.catchingFailures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The heart-rate sensor paired, one at a time: any strap or armband that speaks Bluetooth's standard Heart Rate
 * service streams the heart rate live through Android's own Bluetooth ([AndroidHeartRateLink], a [HeartRateStream]),
 * and a Polar Verity Sense or H10 also records a class on its own memory through Polar's SDK, so the phone needs it
 * in reach only to start and to sync. The armband records the heart rate and its accelerometer (the SDK's offline
 * recording); the chest strap its one exercise, the heart rate a second alone (the SDK's H10 exercise recording), so
 * its class's rounds go by the heart rate. Which sensor is the app's, and since when it records, is kept in
 * preferences of its own, since the app may well be closed for the class. The sensor takes one connection, so the
 * live stream gives way to a recording's start or its sync and comes back after. A sensor is kept by its Bluetooth
 * address, which both the stream and the SDK connect by; one paired by an older build under Polar's own id is found
 * again by its name the first time it is streamed from. Android 12 brought Bluetooth permissions that ask nothing of
 * location; below it this is unavailable rather than ask for that.
 *
 * Run against a Verity Sense on 2026-11-18 (SDK 8.6.0): pairing, a recording of the heart rate, a reading a
 * second, and the accelerometer, and its sync. A stop before the start answers "already in state", passed over.
 * An H10 on 2026-11-19: pairing, and the heart rate live through a conditioning workout, its zones after. Its
 * class recording has not yet been run on a real strap. The live heart rate over Android's own Bluetooth, and
 * pairing by its scan, have met no sensor yet.
 */
@Suppress("detekt.TooManyFunctions") // The port's calls, and the Bluetooth each needs beneath it.
internal class PolarClassSensor(private val context: Context, private val clock: WallClock) :
    ClassSensor,
    LiveHeartRate {
    private val log = Logger.withTag("PolarClassSensor")
    private val paired = PairedSensor(context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE))

    /** What each sensor connected has said it is ready for, by Polar's own id for it. */
    private val ready = MutableStateFlow(emptyMap<String, Set<PolarBleSdkFeature>>())

    /** Polar's own id of each sensor the SDK has connected to, by its Bluetooth address, which the app keeps. */
    private val polarIds = MutableStateFlow(emptyMap<String, String>())

    /** One piece of work with the sensor at a time: a pairing, a recording's start, a sync, a forgetting. */
    private val working = Mutex()

    private val sdk = lazy {
        PolarBleApiDefaultImpl.defaultImplementation(
            context,
            setOf(
                PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING,
                PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING,
                PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP,
                PolarBleSdkFeature.FEATURE_BATTERY_INFO,
            ),
        ).also { api ->
            api.setApiCallback(object : PolarBleApiCallback() {
                override fun bleSdkFeatureReady(identifier: String, feature: PolarBleSdkFeature) {
                    ready.update { it + (identifier to (it[identifier].orEmpty() + feature)) }
                }

                override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
                    polarIds.update { it + (polarDeviceInfo.address to polarDeviceInfo.deviceId) }
                }

                override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo, info: PolarBleDisconnectInfo) {
                    ready.update { it - polarDeviceInfo.deviceId }
                }

                // Said by the sensor each time it connects, and as it changes while it stays connected.
                override fun batteryLevelReceived(identifier: String, level: Int) {
                    val id = paired.sensor?.id
                    if (identifier == id || identifier == polarIds.value[id]) heardBattery(level)
                }

                override fun disInformationReceived(identifier: String, disInfo: DisInfo) = Unit

                override fun htsNotificationReceived(identifier: String, data: PolarHealthThermometerData) = Unit
            })
        }
    }
    private val api: PolarBleApi by sdk

    private val verity by lazy { VerityRecorder(api, log) }
    private val h10 by lazy { H10Recorder(api, log) }

    private val _status = MutableStateFlow(stored())
    override val status: StateFlow<SensorStatus> = _status.asStateFlow()

    private val _battery = MutableStateFlow(paired.battery)
    override val battery: StateFlow<Int?> = _battery.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val link = AndroidHeartRateLink(context)

    /** Live from the paired sensor, unless Bluetooth isn't allowed or the sensor is recording a class. */
    private val stream = HeartRateStream(
        link,
        sensorId = { streamable() },
        idle = { if (stored() is SensorStatus.Paired) LiveHeartRateState.Idle else LiveHeartRateState.Unavailable },
        scope = scope,
        onBattery = ::heardBattery,
        log = log,
    )
    override val reading: StateFlow<LiveHeartRateState> get() = stream.reading
    override val readings: Flow<Int> get() = stream.readings

    /** Whether an older pairing's address is being looked for: once at a time. */
    private val finding = AtomicBoolean(false)

    /**
     * The address to stream from, null while none may be: no sensor paired, it records a class, Bluetooth isn't
     * allowed, or it was paired under Polar's own id and its address is still being found.
     */
    private fun streamable(): String? {
        val sensor = paired.sensor?.takeIf { paired.since == null && supported && mayUseBluetooth() } ?: return null
        if (BluetoothAdapter.checkBluetoothAddress(sensor.id)) return sensor.id
        findAddress(sensor)
        return null
    }

    /** Finds a sensor paired under Polar's id by the name it advertises, and keeps it by its address from then on. */
    private fun findAddress(sensor: PairedSensor.Sensor) {
        if (!finding.compareAndSet(false, true)) return
        scope.launch {
            try {
                val heard = withTimeoutOrNull(SCAN_MILLIS) { link.scan().first { it.name == sensor.name } }
                if (heard != null && paired.sensor?.id == sensor.id) {
                    paired.pair(heard.id, sensor.name, keepBattery = true)
                    stream.settle()
                }
            } finally {
                finding.set(false)
            }
        }
    }

    private fun heardBattery(level: Int) {
        paired.battery = level
        _battery.value = paired.battery
    }

    private val supported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

    private fun stored(): SensorStatus {
        if (!supported) return SensorStatus.Unavailable
        val sensor = paired.sensor ?: return SensorStatus.Unpaired
        return paired.since?.let { SensorStatus.Recording(sensor.name, it) }
            ?: SensorStatus.Paired(sensor.name, recordsClasses = recordsClasses(sensor))
    }

    private fun mayUseBluetooth(): Boolean {
        val granted = PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        return granted && context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
    }

    /** Whether the sensor records a class, by its name alone: asking builds nothing of Polar's SDK. */
    private fun recordsClasses(sensor: PairedSensor.Sensor): Boolean =
        POLAR in sensor.name && (H10_NAME in sensor.name || VERITY_NAME in sensor.name)

    /** Which of Polar's recordings the sensor makes, by its name; null for any other, which only streams. */
    private fun recorderOf(sensor: PairedSensor.Sensor): PolarRecorder? = when {
        !recordsClasses(sensor) -> null
        H10_NAME in sensor.name -> h10
        else -> verity
    }

    override suspend fun pair(choose: suspend (List<NearbySensor>) -> NearbySensor?): SensorFailure? = attempt {
        val nearby = nearby()
        val picked = when (nearby.size) {
            0 -> return@attempt SensorFailure.NotFound

            1 -> nearby.single()

            // An armband and a strap both in reach: the first to be heard is whichever advertised first, not
            // the one meant.
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
     * since another may be in reach beside it. Empty when none was heard in time. Android's own scan hears only
     * what advertises now, so a strap left at home is never "found".
     */
    private suspend fun nearby(): List<NearbySensor> = coroutineScope {
        // Each by its address, its latest signal kept.
        val heard = MutableStateFlow(emptyMap<String, HeardSensor>())
        val scan = launch { link.scan().collect { sensor -> heard.update { it + (sensor.id to sensor) } } }
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

    override suspend fun startRecording(): SensorFailure? = attempt {
        val sensor = paired.sensor ?: return@attempt SensorFailure.NotFound
        val recorder = recorderOf(sensor) ?: return@attempt SensorFailure.Failed
        connected(sensor.id, recorder.feature) { polarId ->
            recorder.start(polarId, clock.nowMillis())
            paired.since = clock.nowMillis()
            _status.value = stored()
            stream.settle()
            null
        }
    }

    override suspend fun sync(keep: suspend (ClassRecording) -> Unit): SensorFailure? = attempt {
        val sensor = paired.sensor ?: return@attempt SensorFailure.NotFound
        val recorder = recorderOf(sensor) ?: return@attempt SensorFailure.NothingRecorded
        connected(sensor.id, recorder.feature) { polarId ->
            val fetched = recorder.fetch(polarId, paired.since, clock::nowMillis)
            // Kept before it is cleared: a class the app couldn't store throws from here, and is on the sensor still.
            fetched?.let { keep(it.recording) }
            fetched?.clear?.invoke()
            // With nothing on it there is nothing to wait for either: it stands ready again.
            paired.since = null
            _status.value = stored()
            stream.settle()
            if (fetched == null) SensorFailure.NothingRecorded else null
        }
    }

    override fun start(user: Any) = stream.start(user)

    override fun stop(user: Any) = stream.stop(user)

    /**
     * [work] with the sensor at [id] (its address, or Polar's id where an older build kept that) connected and
     * ready for [feature], handed Polar's id for it, which the SDK's work goes by; or that it never came. Let go
     * after, either way.
     */
    private suspend fun connected(
        id: String,
        feature: PolarBleSdkFeature,
        work: suspend (polarId: String) -> SensorFailure?,
    ): SensorFailure? {
        api.connectToDevice(id)
        try {
            val polarId = withTimeoutOrNull(CONNECT_MILLIS) {
                // Under Polar's id for it, or the address it was reached by, whichever the SDK says it under.
                combine(ready, polarIds) { ready, ids ->
                    listOfNotNull(ids[id], id).firstOrNull { ready[it]?.contains(feature) == true }
                }.filterNotNull().first()
            } ?: return SensorFailure.NotFound
            return work(polarId)
        } finally {
            quietly(log, "letting the sensor go") { api.disconnectFromDevice(id) }
        }
    }

    /**
     * [work] one at a time, with Bluetooth allowed and the live stream out of its way, a failure logged and
     * answered rather than thrown.
     */
    private suspend fun attempt(work: suspend () -> SensorFailure?): SensorFailure? {
        if (!supported || !mayUseBluetooth()) return SensorFailure.NoAccess
        return working.withLock {
            stream.take()
            try {
                // The SDK throws its own errors and the platform's alike; none may take the app down.
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

    /** Lets everything go as the container closes: the stream, and the SDK if it was ever made. */
    fun close() {
        scope.cancel()
        if (sdk.isInitialized()) quietly(log, "shutting the SDK down") { api.shutDown() }
    }

    companion object {
        /**
         * What the app must be allowed before any of this works. Core's `rememberBluetoothAccess` asks for the same
         * two from a list of its own, since core can't see this module: keep them in step.
         */
        val PERMISSIONS: List<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                emptyList()
            }

        private const val PREFERENCES = "class-sensor"

        /** A Verity Sense advertises as "Polar Sense" and its id, an H10 as "Polar H10" and its. */
        private const val POLAR = "Polar"
        private const val H10_NAME = "H10"
        private const val VERITY_NAME = "Sense"
        private const val SCAN_MILLIS = 20_000L

        /** How long the scan goes on once it has heard a sensor, for another beside it to be heard too. */
        private const val SETTLE_MILLIS = 4_000L
        private const val CONNECT_MILLIS = 30_000L
    }
}

/**
 * Which sensor is the app's, and since when it records a class, in preferences of its own: the app may well be
 * closed for the class.
 */
private class PairedSensor(private val preferences: SharedPreferences) {
    class Sensor(val id: String, val name: String)

    /** The sensor paired, null with none. */
    val sensor: Sensor?
        get() {
            val id = preferences.getString(KEY_ID, null) ?: return null
            val name = preferences.getString(KEY_NAME, null) ?: return null
            return Sensor(id, name)
        }

    /** When the sensor was set recording, null while it records nothing for the app. */
    var since: Long?
        get() = preferences.getLong(KEY_SINCE, 0L).takeIf { it > 0L }
        set(value) = preferences.edit { if (value == null) remove(KEY_SINCE) else putLong(KEY_SINCE, value) }

    /** The sensor's battery as it last said, a percentage; null until it has. */
    var battery: Int?
        get() = preferences.getInt(KEY_BATTERY, NO_BATTERY).takeIf { it in BATTERY_RANGE }
        set(value) = preferences.edit {
            if (value == null) remove(KEY_BATTERY) else putInt(KEY_BATTERY, value.coerceIn(BATTERY_RANGE))
        }

    /**
     * Takes the sensor [id] as the app's: whatever the last one recorded is no longer waited on, and its battery
     * was another sensor's, unless it is the same sensor kept by its address now ([keepBattery]).
     */
    fun pair(id: String, name: String, keepBattery: Boolean = false) = preferences.edit {
        putString(KEY_ID, id)
        putString(KEY_NAME, name)
        if (!keepBattery) {
            remove(KEY_SINCE)
            remove(KEY_BATTERY)
        }
    }

    fun clear() = preferences.edit { clear() }

    private companion object {
        const val KEY_ID = "id"
        const val KEY_NAME = "name"
        const val KEY_SINCE = "since"
        const val KEY_BATTERY = "battery"
        const val NO_BATTERY = -1
        val BATTERY_RANGE = 0..100
    }
}
