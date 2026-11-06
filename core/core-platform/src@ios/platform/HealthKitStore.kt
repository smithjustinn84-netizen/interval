@file:OptIn(ExperimentalForeignApi::class)

package dev.justinsmith.interval.platform

import co.touchlab.kermit.Logger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSDate
import platform.Foundation.NSDateInterval
import platform.Foundation.NSError
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSSortDescriptor
import platform.Foundation.NSUserDefaults
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.numberWithInt
import platform.Foundation.numberWithLongLong
import platform.Foundation.timeIntervalSince1970
import platform.HealthKit.HKAuthorizationRequestStatusShouldRequest
import platform.HealthKit.HKAuthorizationStatusSharingAuthorized
import platform.HealthKit.HKDevice
import platform.HealthKit.HKHealthStore
import platform.HealthKit.HKMetadataKeySyncIdentifier
import platform.HealthKit.HKMetadataKeySyncVersion
import platform.HealthKit.HKMetricPrefixKilo
import platform.HealthKit.HKObjectType
import platform.HealthKit.HKQuantity
import platform.HealthKit.HKQuantitySample
import platform.HealthKit.HKQuantityType
import platform.HealthKit.HKQuantityTypeIdentifierActiveEnergyBurned
import platform.HealthKit.HKQuantityTypeIdentifierBodyMass
import platform.HealthKit.HKQuantityTypeIdentifierHeartRate
import platform.HealthKit.HKQuery
import platform.HealthKit.HKQueryOptionStrictStartDate
import platform.HealthKit.HKSampleQuery
import platform.HealthKit.HKSampleSortIdentifierEndDate
import platform.HealthKit.HKStatistics
import platform.HealthKit.HKStatisticsOptionDiscreteAverage
import platform.HealthKit.HKStatisticsOptionDiscreteMax
import platform.HealthKit.HKStatisticsQuery
import platform.HealthKit.HKUnit
import platform.HealthKit.HKWorkoutActivity
import platform.HealthKit.HKWorkoutActivityType
import platform.HealthKit.HKWorkoutActivityTypeFlexibility
import platform.HealthKit.HKWorkoutActivityTypeHighIntensityIntervalTraining
import platform.HealthKit.HKWorkoutActivityTypeTraditionalStrengthTraining
import platform.HealthKit.HKWorkoutBuilder
import platform.HealthKit.HKWorkoutConfiguration
import platform.HealthKit.HKWorkoutEvent
import platform.HealthKit.HKWorkoutEventTypePause
import platform.HealthKit.HKWorkoutEventTypeResume
import platform.HealthKit.countUnit
import platform.HealthKit.gramUnitWithMetricPrefix
import platform.HealthKit.kilocalorieUnit
import platform.HealthKit.minuteUnit
import platform.HealthKit.predicateForSamplesWithStartDate
import platform.HealthKit.unitDividedByUnit
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import kotlin.coroutines.resume

/**
 * Apple Health as the app's [HealthStore], through HealthKit. The app asks to write workouts and their active
 * energy and to read the body weight and the heart rate, nothing more. Writing is what connects; HealthKit never
 * says whether a read was allowed, so a read the user refused comes back empty, as one with nothing to find does.
 *
 * iOS lets no app give its permissions back: those live in the Health app, under the app's name. So turning the
 * store off in Settings is the app's own switch, kept in the user defaults, which stops every write and read
 * until the user connects again; connecting turns it back on, and asks HealthKit only what it hasn't been asked.
 * The status is read again as the app comes to the front, since the permissions may have changed in Health.
 *
 * Each workout goes in through a [HKWorkoutBuilder] dated to when it ran: a stretch per interval, rest or set, a
 * pause and a resume per pause, and an active-energy sample when there is an estimate. The workout and the sample
 * each carry a sync identifier (the workout's id) and version, so the same workout written again replaces the first.
 */
class HealthKitStore(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : HealthStore {
    private val log = Logger.withTag("HealthKit")
    private val store: HKHealthStore? = if (HKHealthStore.isHealthDataAvailable()) HKHealthStore() else null
    private val defaults = NSUserDefaults.standardUserDefaults
    private val _status = MutableStateFlow(currentStatus())
    override val status: StateFlow<HealthStatus> = _status.asStateFlow()

    init {
        // Never removed: the store is a single that lives as long as the process.
        NSNotificationCenter.defaultCenter.addObserverForName(
            UIApplicationDidBecomeActiveNotification,
            null,
            NSOperationQueue.mainQueue,
        ) { _ -> refresh() }
    }

    override fun refresh() {
        _status.value = currentStatus()
    }

    /** Turns the app's own switch off: HealthKit keeps its permissions, which only the Health app takes back. */
    override fun disconnect() {
        defaults.setBool(true, TURNED_OFF)
        refresh()
    }

    /**
     * Turns the app's switch on and asks HealthKit for what it hasn't been asked, on its own sheet. True when
     * the writes are refused and the sheet can't ask again, which only the Health app can change now.
     */
    suspend fun connect(): Boolean {
        val store = store ?: return false
        defaults.setBool(false, TURNED_OFF)
        val asks = attempt("Couldn't ask for the permissions") { store.shouldRequest() } == true
        if (asks) attempt("Couldn't ask for the permissions") { store.request() }
        refresh()
        // Refused on the sheet just now is the user's answer; only one the sheet can't ask again goes to Health.
        return !asks && status.value != HealthStatus.Connected
    }

    override fun record(workout: HealthWorkout) {
        val store = store ?: return
        scope.launch {
            refresh()
            if (status.value != HealthStatus.Connected) return@launch
            attempt("Couldn't write ${workout.id}") { store.write(workout.toHealthKit()) }
        }
    }

    override suspend fun latestWeightKg(): Double? {
        val store = store?.takeIf { readable() } ?: return null
        val now = NSDate()
        val since = NSDate.dateWithTimeIntervalSince1970(now.timeIntervalSince1970 - WEIGHT_WINDOW_SECONDS)
        val sample = attempt("Couldn't read the weight") {
            store.latest(quantityType(HKQuantityTypeIdentifierBodyMass), since, now)
        } ?: return null
        return sample.quantity.doubleValueForUnit(HKUnit.gramUnitWithMetricPrefix(HKMetricPrefixKilo))
    }

    override suspend fun heartRate(startEpochMillis: Long, endEpochMillis: Long): HeartRate? {
        val store = store?.takeIf { readable() } ?: return null
        if (endEpochMillis <= startEpochMillis) return null
        val statistics = attempt("Couldn't read the heart rate") {
            store.statistics(
                quantityType(HKQuantityTypeIdentifierHeartRate),
                dateOf(startEpochMillis),
                dateOf(endEpochMillis),
            )
        } ?: return null
        val perMinute = HKUnit.countUnit().unitDividedByUnit(HKUnit.minuteUnit())
        val average = statistics.averageQuantity()?.doubleValueForUnit(perMinute)?.toInt() ?: return null
        val peak = statistics.maximumQuantity()?.doubleValueForUnit(perMinute)?.toInt() ?: return null
        return HeartRate(average, peak).takeIf { average in HeartRate.BPM_RANGE && peak in HeartRate.BPM_RANGE }
    }

    /** Whether HealthKit is here, and if so whether the app may write and the user left it on. */
    private fun currentStatus(): HealthStatus {
        val store = store ?: return HealthStatus.Unavailable
        val writes = WRITE.all { store.authorizationStatusForType(it) == HKAuthorizationStatusSharingAuthorized }
        return if (writes && !defaults.boolForKey(TURNED_OFF)) HealthStatus.Connected else HealthStatus.Disconnected
    }

    /** Whether to read at all: HealthKit hides a refused read, so only the app's own switch can say no. */
    private fun readable(): Boolean = !defaults.boolForKey(TURNED_OFF)

    /** Runs [block] against HealthKit, which may refuse: a failure is logged as [what]. */
    @Suppress("detekt.TooGenericExceptionCaught")
    private suspend fun <T> attempt(what: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.w(e) { what }
        null
    }

    private companion object {
        /** The user defaults key of the app's own switch: true once turned off in Settings. */
        const val TURNED_OFF = "healthKitTurnedOff"

        /** How far back a weight is looked for: an older one says little of today's. */
        const val WEIGHT_WINDOW_SECONDS = 30.0 * 24 * 60 * 60

        /** The leave to write workouts and their active energy: holding it is being connected. */
        val WRITE: Set<HKObjectType> = setOf(
            HKObjectType.workoutType(),
            quantityType(HKQuantityTypeIdentifierActiveEnergyBurned),
        )

        /** The leave to read the body weight, for Settings' weight sheet, and the heart rate, for a summary. */
        val READ: Set<HKObjectType> = setOf(
            quantityType(HKQuantityTypeIdentifierBodyMass),
            quantityType(HKQuantityTypeIdentifierHeartRate),
        )
    }

    private suspend fun HKHealthStore.shouldRequest(): Boolean = suspendCancellableCoroutine { done ->
        getRequestStatusForAuthorizationToShareTypes(WRITE, READ) { status, error ->
            if (error !=
                null
            ) {
                done.resumeWith(Result.failure(HealthKitException(error)))
            } else {
                done.resume(status == HKAuthorizationRequestStatusShouldRequest)
            }
        }
    }

    private suspend fun HKHealthStore.request(): Unit = suspendCancellableCoroutine { done ->
        requestAuthorizationToShareTypes(WRITE, READ) { _, error -> done.resumeOrFail(Unit, error) }
    }
}

/** Writes [workout] through a builder dated to when it ran, then finishes it; a step that fails drops the lot. */
private suspend fun HKHealthStore.write(workout: HealthKitWorkout) {
    val configuration = HKWorkoutConfiguration().apply { activityType = workout.kind.activityType }
    val builder = HKWorkoutBuilder(this, configuration, HKDevice.localDevice())
    try {
        step { builder.beginCollectionWithStartDate(dateOf(workout.startedAtEpochMillis), it) }
        for (stretch in workout.stretches) {
            val activity = HKWorkoutActivity(
                workoutConfiguration = configuration,
                startDate = dateOf(stretch.startEpochMillis),
                endDate = dateOf(stretch.endEpochMillis),
                metadata = stretch.metadata.asNumbers(),
            )
            step { builder.addWorkoutActivity(activity, it) }
        }
        val events = workout.pauses.flatMap {
            listOf(
                event(HKWorkoutEventTypePause, it.startEpochMillis),
                event(HKWorkoutEventTypeResume, it.endEpochMillis),
            )
        }
        if (events.isNotEmpty()) step { builder.addWorkoutEvents(events, it) }
        workout.calories?.let { calories ->
            val sample = HKQuantitySample.quantitySampleWithType(
                quantityType(HKQuantityTypeIdentifierActiveEnergyBurned),
                HKQuantity.quantityWithUnit(HKUnit.kilocalorieUnit(), calories.toDouble()),
                dateOf(workout.startedAtEpochMillis),
                dateOf(workout.endedAtEpochMillis),
                syncMetadata(workout.caloriesSyncId, workout.version),
            )
            step { builder.addSamples(listOf(sample), it) }
        }
        step { builder.addMetadata(workout.metadata.withSyncKeys(), it) }
        step { builder.endCollectionWithEndDate(dateOf(workout.endedAtEpochMillis), it) }
        suspendCancellableCoroutine<Unit> { done ->
            builder.finishWorkoutWithCompletion { _, error -> done.resumeOrFail(Unit, error) }
        }
    } catch (e: HealthKitException) {
        builder.discardWorkout()
        throw e
    }
}

/** Runs one of the builder's steps, which answers through a callback that it went or why not. */
private suspend fun step(call: ((Boolean, NSError?) -> Unit) -> Unit): Unit = suspendCancellableCoroutine { done ->
    call { _, error -> done.resumeOrFail(Unit, error) }
}

/** The latest sample of [type] between [start] and [end], or null for none. */
private suspend fun HKHealthStore.latest(type: HKQuantityType, start: NSDate, end: NSDate): HKQuantitySample? =
    suspendCancellableCoroutine { done ->
        val query = HKSampleQuery(
            sampleType = type,
            predicate = HKQuery.predicateForSamplesWithStartDate(start, end, HKQueryOptionStrictStartDate),
            limit = 1u,
            sortDescriptors = listOf(NSSortDescriptor(key = HKSampleSortIdentifierEndDate, ascending = false)),
        ) { _, samples, error -> done.resumeOrFail(samples?.firstOrNull() as? HKQuantitySample, error) }
        executeQuery(query)
    }

/** The average and the peak of [type] between [start] and [end]: none recorded is no statistics. */
private suspend fun HKHealthStore.statistics(type: HKQuantityType, start: NSDate, end: NSDate): HKStatistics? =
    suspendCancellableCoroutine { done ->
        val query = HKStatisticsQuery(
            quantityType = type,
            quantitySamplePredicate = HKQuery.predicateForSamplesWithStartDate(start, end, 0u),
            options = HKStatisticsOptionDiscreteAverage or HKStatisticsOptionDiscreteMax,
        ) { _, statistics, error ->
            // HealthKit answers "no data" with an error: nothing recorded then is no heart rate, not a failure.
            done.resume(statistics.takeIf { error == null })
        }
        executeQuery(query)
    }

private fun <T> kotlinx.coroutines.CancellableContinuation<T>.resumeOrFail(value: T, error: NSError?) {
    if (error != null) resumeWith(Result.failure(HealthKitException(error))) else resume(value)
}

/** HealthKit's refusal, with what it said. */
private class HealthKitException(error: NSError) : Exception(error.localizedDescription)

private fun event(type: Long, atEpochMillis: Long): HKWorkoutEvent =
    HKWorkoutEvent.workoutEventWithType(type, NSDateInterval(dateOf(atEpochMillis), 0.0), null)

private fun syncMetadata(id: String, version: Long): Map<Any?, Any> =
    mapOf(HKMetadataKeySyncIdentifier to id, HKMetadataKeySyncVersion to NSNumber.numberWithLongLong(version))

/** [this] as HealthKit takes metadata: a number as an `NSNumber`, made here rather than left to the bridge. */
private fun Map<String, Any>.asNumbers(): Map<Any?, Any> = mapValues { (_, value) ->
    when (value) {
        is Int -> NSNumber.numberWithInt(value)
        is Long -> NSNumber.numberWithLongLong(value)
        else -> value
    }
}

/** [this] with HealthKit's own sync keys under their real names, and the version as the number HealthKit wants. */
private fun Map<String, Any>.withSyncKeys(): Map<Any?, Any> = asNumbers().mapKeys { (key, _) ->
    when (key) {
        HealthKitKeys.SYNC_IDENTIFIER -> HKMetadataKeySyncIdentifier
        HealthKitKeys.SYNC_VERSION -> HKMetadataKeySyncVersion
        else -> key
    }
}

private fun quantityType(identifier: String?): HKQuantityType = HKQuantityType.quantityTypeForIdentifier(identifier!!)!!

private fun dateOf(epochMillis: Long): NSDate = NSDate.dateWithTimeIntervalSince1970(epochMillis / MILLIS_PER_SECOND)

private const val MILLIS_PER_SECOND = 1000.0

private val HealthWorkoutKind.activityType: HKWorkoutActivityType
    get() = when (this) {
        HealthWorkoutKind.Intervals -> HKWorkoutActivityTypeHighIntensityIntervalTraining
        HealthWorkoutKind.Strength -> HKWorkoutActivityTypeTraditionalStrengthTraining
        HealthWorkoutKind.Mobility -> HKWorkoutActivityTypeFlexibility
    }
