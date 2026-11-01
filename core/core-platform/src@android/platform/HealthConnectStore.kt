package dev.justinsmith.interval.platform

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * Health Connect as the app's [HealthStore]. Built into Android 14 and up; before that it is an app of its
 * own, which [HealthStatus.NotInstalled] says is missing. The app asks only to write exercise sessions and
 * their calories, never to read, so the permission sheet is short. The permissions can be taken back in
 * Health Connect's own settings at any time, so they are checked before every write, not just once.
 *
 * Each workout goes in as one exercise session, with a segment per interval, rest, pause or set, and
 * one total-calories record over the same time when there is an estimate. Both carry the workout's id as
 * their client record id, so the same workout written twice is one record in the store.
 */
class HealthConnectStore(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : HealthStore {
    private val log = Logger.withTag("HealthConnect")
    private val _status = MutableStateFlow(statusOfSdk())
    override val status: StateFlow<HealthStatus> = _status.asStateFlow()

    private val client: HealthConnectClient? by lazy {
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else {
            null
        }
    }

    init {
        refresh()
    }

    override fun refresh() {
        scope.launch { _status.value = currentStatus() }
    }

    override fun disconnect() {
        scope.launch {
            attempt("Couldn't disconnect") { client?.permissionController?.revokeAllPermissions() }
            _status.value = currentStatus()
        }
    }

    override fun record(workout: HealthWorkout) {
        scope.launch {
            if (!granted()) {
                _status.value = currentStatus()
                return@launch
            }
            attempt("Couldn't write ${workout.id}") { client?.insertRecords(workout.records()) }
        }
    }

    /** Installed or not, and if so whether the app may write: the status as it stands this moment. */
    private suspend fun currentStatus(): HealthStatus = when (val sdk = statusOfSdk()) {
        HealthStatus.Disconnected -> if (granted()) HealthStatus.Connected else HealthStatus.Disconnected
        else -> sdk
    }

    /** What the SDK says of the store on this device, as far as it goes: whether the app may write it can't say. */
    private fun statusOfSdk(): HealthStatus = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_UNAVAILABLE -> HealthStatus.Unavailable
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthStatus.NotInstalled
        else -> HealthStatus.Disconnected
    }

    private suspend fun granted(): Boolean {
        val controller = client?.permissionController ?: return false
        return attempt("Couldn't read the permissions") { controller.getGrantedPermissions() }
            ?.containsAll(PERMISSIONS) ?: false
    }

    /** Runs [block] against the store, which may be missing or refuse: a failure is logged as [what]. */
    @Suppress("detekt.TooGenericExceptionCaught")
    private suspend fun <T> attempt(what: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.w(e) { what }
        null
    }

    companion object {
        /** What the app asks for: to write exercise sessions and total calories, nothing to read. */
        val PERMISSIONS: Set<String> = setOf(
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
            HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class),
        )
    }
}

/** The records [this] goes into the store as: the session, and its calories when estimated. */
private fun HealthWorkout.records(): List<Record> {
    val start = Instant.ofEpochMilli(startedAtEpochMillis)
    val end = Instant.ofEpochMilli(endedAtEpochMillis)
    val offset = ZoneId.systemDefault().rules.getOffset(start)
    val session = ExerciseSessionRecord(
        startTime = start,
        startZoneOffset = offset,
        endTime = end,
        endZoneOffset = offset,
        metadata = metadataFor(id, version),
        exerciseType = kind.exerciseType,
        title = title,
        notes = notes,
        segments = segments.map { it.toSegment() },
    )
    val calories = calories?.let {
        TotalCaloriesBurnedRecord(
            startTime = start,
            startZoneOffset = offset,
            endTime = end,
            endZoneOffset = offset,
            energy = Energy.kilocalories(it.toDouble()),
            metadata = metadataFor("$id-calories", version),
        )
    }
    return listOfNotNull<Record>(session, calories)
}

private val HealthWorkoutKind.exerciseType: Int
    get() = when (this) {
        HealthWorkoutKind.Intervals -> ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING
        HealthWorkoutKind.Strength -> ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
    }

private fun HealthSegment.toSegment() = ExerciseSegment(
    startTime = Instant.ofEpochMilli(startEpochMillis),
    endTime = Instant.ofEpochMilli(endEpochMillis),
    segmentType = move.segmentType,
    repetitions = repetitions,
)

/**
 * The store's word for each move. An interval workout is a high-intensity session, which takes any
 * segment; a Lift session is strength training, which takes every lift here and the rests and pauses.
 */
private val HealthMove.segmentType: Int
    get() = when (this) {
        HealthMove.Work -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING
        HealthMove.Rest -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_REST
        HealthMove.Pause -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_PAUSE
        HealthMove.Biking -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_BIKING_STATIONARY
        HealthMove.Rowing -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_ROWING_MACHINE
        HealthMove.Running -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_RUNNING
        HealthMove.JumpRope -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_JUMP_ROPE
        HealthMove.KettlebellSwing -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_KETTLEBELL_SWING
        HealthMove.Weightlifting -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_WEIGHTLIFTING
        HealthMove.Burpee -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_BURPEE
        HealthMove.Squat -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_SQUAT
        HealthMove.Deadlift -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_DEADLIFT
        HealthMove.ShoulderPress -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_SHOULDER_PRESS
        HealthMove.BenchPress -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_BENCH_PRESS
        HealthMove.Row -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_ROW
        HealthMove.PullUp -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_PULL_UP
        HealthMove.Plank -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_PLANK
        HealthMove.Lunge -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_LUNGE
        HealthMove.MountainClimber -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_MOUNTAIN_CLIMBER
        HealthMove.HipThrust -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_HIP_THRUST
        HealthMove.Other -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_OTHER_WORKOUT
    }

/**
 * Recorded by the app as it ran, on this phone, under [clientRecordId]: a later write of a workout replaces the
 * first when its [version] is higher (Health Connect keeps the stored record otherwise).
 */
private fun metadataFor(clientRecordId: String, version: Long): Metadata = Metadata.activelyRecorded(
    clientRecordId = clientRecordId,
    clientRecordVersion = version,
    device = Device(type = Device.TYPE_PHONE),
)
