package dev.justinsmith.interval.platform

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import co.touchlab.kermit.Logger
import com.polar.androidcommunications.api.ble.model.DisInfo
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApi.PolarBleSdkFeature
import com.polar.sdk.api.PolarBleApi.PolarDeviceDataType
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.PolarBleDisconnectInfo
import com.polar.sdk.api.PolarH10OfflineExerciseApi.RecordingInterval
import com.polar.sdk.api.PolarH10OfflineExerciseApi.SampleType
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarHealthThermometerData
import com.polar.sdk.api.model.PolarOfflineRecordingData
import com.polar.sdk.api.model.PolarOfflineRecordingEntry
import com.polar.sdk.api.model.PolarSensorSetting
import com.polar.sdk.api.model.PolarSensorSetting.SettingType
import dev.justinsmith.interval.model.ClassRecording
import dev.justinsmith.interval.storage.catchingFailures
import dev.justinsmith.interval.timer.classSamples
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A Polar Verity Sense or H10, through Polar's SDK, one paired at a time, which streams the heart rate live and
 * records a class on its own memory, so the phone needs it in reach only to start and to sync. The armband records
 * the heart rate and its accelerometer (the SDK's offline recording); the chest strap its one exercise, the heart
 * rate a second alone (the SDK's H10 exercise recording), so its class's rounds go by the heart rate. Which sensor is
 * the app's, and since when it records, is kept in preferences of its own, since the app may well be closed for
 * the class. Android 12 brought Bluetooth permissions that ask nothing of location; below it this is unavailable
 * rather than ask for that.
 *
 * Run against a Verity Sense on 2026-11-18 (SDK 8.6.0): pairing, a recording of the heart rate, a reading a
 * second, and the accelerometer, and its sync. A stop before the start answers "already in state", passed over.
 * The H10's path has not yet been run on a real strap.
 */
internal class PolarClassSensor(private val context: Context, private val clock: WallClock) :
    ClassSensor,
    LiveHeartRate {
    private val log = Logger.withTag("PolarClassSensor")
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    /** What each sensor connected has said it is ready for. */
    private val ready = MutableStateFlow(emptyMap<String, Set<PolarBleSdkFeature>>())
    private val streamScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var streamJob: Job? = null

    /** One piece of work with the sensor at a time. */
    private val working = Mutex()

    private val api: PolarBleApi by lazy {
        PolarBleApiDefaultImpl.defaultImplementation(
            context,
            setOf(
                PolarBleSdkFeature.FEATURE_HR,
                PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING,
                PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING,
                PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP,
            ),
        ).also { api ->
            api.setApiCallback(object : PolarBleApiCallback() {
                override fun bleSdkFeatureReady(identifier: String, feature: PolarBleSdkFeature) {
                    ready.update { it + (identifier to (it[identifier].orEmpty() + feature)) }
                }

                override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo, info: PolarBleDisconnectInfo) {
                    ready.update { it - polarDeviceInfo.deviceId }
                    if (streamJob?.isActive == true) {
                        _reading.value = LiveHeartRateState.Disconnected
                    }
                }

                override fun disInformationReceived(identifier: String, disInfo: DisInfo) = Unit

                override fun htsNotificationReceived(identifier: String, data: PolarHealthThermometerData) = Unit
            })
        }
    }

    private val _status = MutableStateFlow(stored())
    override val status: StateFlow<SensorStatus> = _status.asStateFlow()

    private val _reading = MutableStateFlow(initialReading())
    override val reading: StateFlow<LiveHeartRateState> = _reading.asStateFlow()

    private fun initialReading(): LiveHeartRateState = when (stored()) {
        is SensorStatus.Paired -> LiveHeartRateState.Idle
        else -> LiveHeartRateState.Unavailable
    }

    private val supported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

    private fun stored(): SensorStatus {
        if (!supported) return SensorStatus.Unavailable
        val name = preferences.getString(KEY_NAME, null)
        val since = preferences.getLong(KEY_SINCE, 0L)
        return when {
            preferences.getString(KEY_ID, null) == null || name == null -> SensorStatus.Unpaired
            since > 0L -> SensorStatus.Recording(name, since)
            else -> SensorStatus.Paired(name)
        }
    }

    private fun mayUseBluetooth(): Boolean {
        val granted = PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        return granted && context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
    }

    override suspend fun pair(): SensorFailure? = attempt {
        val found = withTimeoutOrNull(SCAN_MILLIS) {
            api.searchForDevice().first { found -> SENSOR_NAMES.any { it in found.name } }
        } ?: return@attempt SensorFailure.NotFound
        preferences.edit {
            putString(KEY_ID, found.deviceId)
            putString(KEY_NAME, found.name)
            remove(KEY_SINCE)
        }
        _status.value = stored()
        _reading.value = initialReading()
        null
    }

    override fun forget() {
        stop()
        preferences.edit { clear() }
        _status.value = stored()
        _reading.value = LiveHeartRateState.Unavailable
    }

    override fun start() {
        if (streamJob?.isActive == true) return
        val id = preferences.getString(KEY_ID, null)
        val offlineActive = preferences.getLong(KEY_SINCE, 0L) > 0L
        val bluetoothReady = supported && mayUseBluetooth()
        if (!bluetoothReady || id == null || offlineActive) {
            _reading.value = initialReading()
            return
        }
        _reading.value = LiveHeartRateState.Connecting
        streamJob = streamScope.launch {
            try {
                catchingFailures(
                    onFailure = { e ->
                        log.w(e) { "Live HR streaming failed" }
                        _reading.value = LiveHeartRateState.Disconnected
                    },
                ) {
                    api.connectToDevice(id)
                    val readyHr = withTimeoutOrNull(CONNECT_MILLIS) { ready.first { it.has(id, FEATURE_HR) } }
                    if (readyHr == null) {
                        _reading.value = LiveHeartRateState.Disconnected
                        return@launch
                    }
                    api.startHrStreaming(id).collect { data ->
                        val sample = data.samples.lastOrNull() ?: return@collect
                        if (sample.hr in HeartRate.BPM_RANGE) {
                            _reading.value = LiveHeartRateState.Reading(
                                bpm = sample.hr,
                                contact = sample.contactStatus,
                            )
                        }
                    }
                }
            } finally {
                quietly { api.disconnectFromDevice(id) }
            }
        }
    }

    override fun stop() {
        streamJob?.cancel()
        streamJob = null
        _reading.value = initialReading()
    }

    override suspend fun startRecording(): SensorFailure? = attempt {
        val id = preferences.getString(KEY_ID, null) ?: return@attempt SensorFailure.NotFound
        if (model == Model.H10) {
            // The H10 holds one exercise: whatever is on it goes, and it records the heart rate a second. Status
            // Recording offers Sync rather than Record, so nothing on the strap now is a class still to sync.
            return@attempt connected(id, H10_EXERCISE) {
                quietly { api.stopRecording(id) }
                api.listExercises(id).toList().forEach { entry -> quietly { api.removeExercise(id, entry) } }
                api.startRecording(id, EXERCISE_ID, RecordingInterval.INTERVAL_1S, SampleType.HR)
                preferences.edit { putLong(KEY_SINCE, clock.nowMillis()) }
                _status.value = stored()
                null
            }
        }
        connected(id, OFFLINE_RECORDING) {
            api.setLocalTime(id, LocalDateTime.now())
            // A recording left running would refuse the new one.
            TYPES.forEach { type -> quietly { api.stopOfflineRecording(id, type) } }
            api.startOfflineRecording(id, PolarDeviceDataType.HR)
            // The class is worth having without its movement.
            quietly { api.startOfflineRecording(id, PolarDeviceDataType.ACC, accelerometerSettings(id)) }
            preferences.edit { putLong(KEY_SINCE, clock.nowMillis()) }
            _status.value = stored()
            null
        }
    }

    override suspend fun sync(): SensorSync {
        var synced: ClassRecording? = null
        val failure = attempt {
            val id = preferences.getString(KEY_ID, null) ?: return@attempt SensorFailure.NotFound
            if (model == Model.H10) {
                return@attempt syncExercise(id) { synced = it }
            }
            connected(id, OFFLINE_RECORDING) {
                TYPES.forEach { type -> quietly { api.stopOfflineRecording(id, type) } }
                val stoppedAt = clock.nowMillis()
                val entries = api.listOfflineRecordings(id).toList()
                val heart = entries.latest(PolarDeviceDataType.HR) ?: return@connected SensorFailure.NothingRecorded
                val recorded = api.getOfflineRecord(id, heart) as? PolarOfflineRecordingData.HrOfflineRecording
                    ?: return@connected SensorFailure.Failed
                val movement = entries.latest(PolarDeviceDataType.ACC)?.let { entry ->
                    quietly { api.getOfflineRecord(id, entry) as? PolarOfflineRecordingData.AccOfflineRecording }
                }
                synced = recording(recorded, movement, stoppedAt)
                // Only once it is safely here; every recording of the two kinds, so none is left to fill the sensor.
                entries.filter { it.type in TYPES }.forEach { entry -> quietly { api.removeOfflineRecord(id, entry) } }
                preferences.edit { remove(KEY_SINCE) }
                _status.value = stored()
                null
            }
        }
        return synced?.takeIf { failure == null }?.let(SensorSync::Synced)
            ?: SensorSync.Failed(failure ?: SensorFailure.Failed)
    }

    /**
     * Stops the H10's exercise and brings it over, handing it to [synced] before it is cleared. Its start is when
     * Record was tapped, which is when the strap began: its clock is never set, so the entry's date says nothing.
     */
    private suspend fun syncExercise(id: String, synced: (ClassRecording) -> Unit): SensorFailure? =
        connected(id, H10_EXERCISE) {
            quietly { api.stopRecording(id) }
            val since = preferences.getLong(KEY_SINCE, 0L)
            val entries = api.listExercises(id).toList()
            val entry = entries.maxByOrNull { it.date } ?: return@connected SensorFailure.NothingRecorded
            val data = api.fetchExercise(id, entry)
            val startedAt = if (since > 0L) since else entry.date.toInstant(ZoneOffset.UTC).toEpochMilli()
            synced(ClassRecording(startedAt, classSamples(data.hrSamples, bpmSeconds = data.recordingInterval)))
            entries.forEach { quietly { api.removeExercise(id, it) } }
            preferences.edit { remove(KEY_SINCE) }
            _status.value = stored()
            null
        }

    private val model: Model
        get() = if (preferences.getString(KEY_NAME, null).orEmpty().contains(H10_NAME)) Model.H10 else Model.VeritySense

    private fun recording(
        heart: PolarOfflineRecordingData.HrOfflineRecording,
        movement: PolarOfflineRecordingData.AccOfflineRecording?,
        stoppedAt: Long,
    ): ClassRecording {
        // The sensor's clock is set in local time, and a recording's start comes back in UTC all the same (seen on a
        // Verity Sense, SDK 8.6.0: a class started at 20:32 EST read 01:32).
        val startedAt = heart.startTime.toInstant(ZoneOffset.UTC).toEpochMilli()
        val bpm = heart.data.samples.map { it.hr }
        // The sensor's heart rate comes a reading a second as a rule; the recording's own length says.
        val seconds = ((stoppedAt - startedAt) / MILLIS_PER_SECOND).coerceAtLeast(1)
        val bpmSeconds = (seconds.toDouble() / bpm.size.coerceAtLeast(1)).roundToInt().coerceIn(1, MAX_BPM_SECONDS)
        val magnitudes = movement?.data?.samples.orEmpty().map { sample ->
            val x = sample.x.toFloat()
            val y = sample.y.toFloat()
            val z = sample.z.toFloat()
            sqrt(x * x + y * y + z * z) / MILLI_G
        }
        val hz = movement?.settings?.settings?.get(SettingType.SAMPLE_RATE)?.firstOrNull() ?: 0
        return ClassRecording(startedAt, classSamples(bpm, magnitudes, hz, bpmSeconds))
    }

    /** The slowest the accelerometer records at, which is plenty for how much an arm moves, at its widest range. */
    private suspend fun accelerometerSettings(id: String): PolarSensorSetting {
        val offered = api.requestOfflineRecordingSettings(id, PolarDeviceDataType.ACC).settings
        return PolarSensorSetting(
            offered.mapNotNull { (type, values) ->
                val value = if (type == SettingType.SAMPLE_RATE) values.minOrNull() else values.maxOrNull()
                value?.let { type to it }
            }.toMap(),
        )
    }

    /** [work] with the sensor connected and ready for [feature], or that it never came; let go after, either way. */
    private suspend fun connected(
        id: String,
        feature: PolarBleSdkFeature,
        work: suspend () -> SensorFailure?,
    ): SensorFailure? {
        api.connectToDevice(id)
        try {
            withTimeoutOrNull(CONNECT_MILLIS) { ready.first { it.has(id, feature) } } ?: return SensorFailure.NotFound
            return work()
        } finally {
            quietly { api.disconnectFromDevice(id) }
        }
    }

    /** [work] one at a time, with Bluetooth allowed, a failure logged and answered rather than thrown. */
    private suspend fun attempt(work: suspend () -> SensorFailure?): SensorFailure? {
        if (!supported || !mayUseBluetooth()) return SensorFailure.NoAccess
        return working.withLock {
            // The SDK throws its own errors and the platform's alike; none may take the app down.
            catchingFailures(
                onFailure = { e ->
                    log.w(e) { "The sensor failed" }
                    SensorFailure.Failed
                },
            ) { work() }
        }
    }

    private suspend fun <T> quietly(work: suspend () -> T): T? =
        catchingFailures(
            onFailure = { e ->
                log.i(e) { "A step with the sensor failed and was passed over" }
                null
            },
        ) { work() }

    /** The two sensors, which record a class each its own way. */
    private enum class Model { VeritySense, H10 }

    companion object {
        /** What the app must be allowed before any of this works; asked by core's `rememberBluetoothAccess`. */
        val PERMISSIONS: List<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                emptyList()
            }

        private val TYPES = listOf(PolarDeviceDataType.HR, PolarDeviceDataType.ACC)
        private const val PREFERENCES = "class-sensor"
        private const val KEY_ID = "id"
        private const val KEY_NAME = "name"
        private const val KEY_SINCE = "since"

        /** A Verity Sense advertises as "Polar Sense" and its id, an H10 as "Polar H10" and its. */
        private const val H10_NAME = "H10"
        private val SENSOR_NAMES = listOf("Sense", H10_NAME)
        private val FEATURE_HR = PolarBleSdkFeature.FEATURE_HR
        private val OFFLINE_RECORDING = PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING
        private val H10_EXERCISE = PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING

        /** The name the H10 keeps its one exercise under. */
        private const val EXERCISE_ID = "interval-class"
        private const val SCAN_MILLIS = 20_000L
        private const val CONNECT_MILLIS = 30_000L
        private const val MILLIS_PER_SECOND = 1000L
        private const val MAX_BPM_SECONDS = 5
        private const val MILLI_G = 1000f
    }
}

private fun Map<String, Set<PolarBleSdkFeature>>.has(id: String, feature: PolarBleSdkFeature) =
    this[id]?.contains(feature) == true

private fun List<PolarOfflineRecordingEntry>.latest(type: PolarDeviceDataType) =
    filter { it.type == type }.maxByOrNull { it.date }
