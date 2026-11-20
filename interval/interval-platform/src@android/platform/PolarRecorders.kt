package dev.justinsmith.interval.platform

import co.touchlab.kermit.Logger
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApi.PolarBleSdkFeature
import com.polar.sdk.api.PolarBleApi.PolarDeviceDataType
import com.polar.sdk.api.PolarH10OfflineExerciseApi.RecordingInterval
import com.polar.sdk.api.PolarH10OfflineExerciseApi.SampleType
import com.polar.sdk.api.model.PolarOfflineRecordingData
import com.polar.sdk.api.model.PolarOfflineRecordingEntry
import com.polar.sdk.api.model.PolarSensorSetting
import com.polar.sdk.api.model.PolarSensorSetting.SettingType
import dev.justinsmith.interval.model.ClassRecording
import dev.justinsmith.interval.storage.catchingFailures
import dev.justinsmith.interval.timer.classSamples
import kotlinx.coroutines.flow.toList
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * How one of Polar's sensors records a class on its own memory, once it is connected and ready for [feature]:
 * each of the two does it its own way. Whatever a step throws is the caller's to answer.
 */
internal interface PolarRecorder {
    /** What the sensor must say it is ready for before any of this. */
    val feature: PolarBleSdkFeature

    /** Sets the sensor [id] recording, at [nowMillis]; whatever it was recording before is stopped. */
    suspend fun start(id: String, nowMillis: Long)

    /**
     * Stops the sensor [id] and brings its recording over; null when it holds none. [sinceMillis] is when it was
     * set recording, if the app knows, and [nowMillis] reads the clock.
     */
    suspend fun fetch(id: String, sinceMillis: Long?, nowMillis: () -> Long): Fetched?
}

/** A [recording] brought over, and the way to [clear] it from the sensor once it is safely kept. */
internal class Fetched(val recording: ClassRecording, val clear: suspend () -> Unit)

/**
 * The Verity Sense: the SDK's offline recording, of the heart rate and, when it will, the accelerometer, so its
 * class says how much the arm moved.
 */
internal class VerityRecorder(private val api: PolarBleApi, private val log: Logger) : PolarRecorder {
    override val feature = PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING

    override suspend fun start(id: String, nowMillis: Long) {
        api.setLocalTime(id, LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), ZoneId.systemDefault()))
        // A recording left running would refuse the new one.
        stop(id)
        api.startOfflineRecording(id, PolarDeviceDataType.HR)
        // The class is worth having without its movement.
        quietly(log, "starting the accelerometer") {
            api.startOfflineRecording(id, PolarDeviceDataType.ACC, accelerometerSettings(id))
        }
    }

    override suspend fun fetch(id: String, sinceMillis: Long?, nowMillis: () -> Long): Fetched? {
        stop(id)
        val stoppedAt = nowMillis()
        val entries = api.listOfflineRecordings(id).toList()
        val heart = entries.latest(PolarDeviceDataType.HR) ?: return null
        val recorded = api.getOfflineRecord(id, heart) as? PolarOfflineRecordingData.HrOfflineRecording
            ?: error("The heart-rate recording came back as something else")
        val movement = entries.latest(PolarDeviceDataType.ACC)?.let { entry ->
            quietly(log, "fetching the accelerometer") {
                api.getOfflineRecord(id, entry) as? PolarOfflineRecordingData.AccOfflineRecording
            }
        }
        // Every recording of the two kinds goes, so none is left to fill the sensor.
        return Fetched(recording(recorded, movement, stoppedAt)) {
            entries.filter { it.type in RECORDED }.forEach { entry ->
                quietly(log, "removing a recording") { api.removeOfflineRecord(id, entry) }
            }
        }
    }

    /** A stop before any start answers "already in state", passed over. */
    private suspend fun stop(id: String) = RECORDED.forEach { type ->
        quietly(log, "stopping a recording", expected = true) { api.stopOfflineRecording(id, type) }
    }

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

    private companion object {
        val RECORDED = listOf(PolarDeviceDataType.HR, PolarDeviceDataType.ACC)
        const val MILLIS_PER_SECOND = 1000L
        const val MAX_BPM_SECONDS = 5
        const val MILLI_G = 1000f
    }
}

/**
 * The H10: the SDK's exercise recording, the strap's one exercise, the heart rate a second alone, so its class's
 * rounds go by the heart rate.
 */
internal class H10Recorder(private val api: PolarBleApi, private val log: Logger) : PolarRecorder {
    override val feature = PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING

    /**
     * The H10 holds one exercise: whatever is on it goes. The app offers Sync rather than Record while a class of
     * its own is on the strap, so nothing there now is a class still to sync.
     */
    override suspend fun start(id: String, nowMillis: Long) {
        stop(id)
        api.listExercises(id).toList().forEach { entry ->
            quietly(log, "removing an exercise") { api.removeExercise(id, entry) }
        }
        api.startRecording(id, EXERCISE_ID, RecordingInterval.INTERVAL_1S, SampleType.HR)
    }

    /**
     * Its start is when Record was tapped ([sinceMillis]), which is when the strap began: its clock is never set,
     * so the entry's own date stands in only where the app no longer knows.
     */
    override suspend fun fetch(id: String, sinceMillis: Long?, nowMillis: () -> Long): Fetched? {
        stop(id)
        val entries = api.listExercises(id).toList()
        val entry = entries.maxByOrNull { it.date } ?: return null
        val data = api.fetchExercise(id, entry)
        val startedAt = sinceMillis ?: entry.date.toInstant(ZoneOffset.UTC).toEpochMilli()
        val recording = ClassRecording(startedAt, classSamples(data.hrSamples, bpmSeconds = data.recordingInterval))
        return Fetched(recording) {
            entries.forEach { quietly(log, "removing an exercise") { api.removeExercise(id, it) } }
        }
    }

    private suspend fun stop(id: String) =
        quietly(log, "stopping the exercise", expected = true) { api.stopRecording(id) }

    private companion object {
        /** The name the H10 keeps its one exercise under. */
        const val EXERCISE_ID = "interval-class"
    }
}

/**
 * [work], its failure logged as a failure in [what] and passed over, null in its place: for a step the rest can go
 * on without. One [expected] to fail as often as not (a stop with nothing running) is logged quietly.
 */
internal inline fun <T> quietly(log: Logger, what: String, expected: Boolean = false, work: () -> T): T? =
    catchingFailures(
        onFailure = { e ->
            if (expected) {
                log.i(e) {
                    "Passed over a failure in $what"
                }
            } else {
                log.w(e) { "Passed over a failure in $what" }
            }
            null
        },
    ) { work() }

private fun List<PolarOfflineRecordingEntry>.latest(type: PolarDeviceDataType) =
    filter { it.type == type }.maxByOrNull { it.date }
