package dev.justinsmith.interval.platform

/**
 * A [HealthWorkout] as HealthKit takes it, worked out before anything is written. HealthKit holds a workout's
 * stretches as activities of the workout's own kind (only a multisport workout mixes kinds), so each segment
 * that isn't a pause is a [stretch][HealthKitStretch] carrying its move and reps in its metadata; a pause is a
 * pause and a resume, which HealthKit leaves out of the workout's duration. [metadata] is the workout's: its sync
 * identifier and version, which make a later write of the same workout replace the first, and the title and notes
 * HealthKit has no field for. The calories, when estimated, go in as a sample of their own under [caloriesSyncId].
 */
internal data class HealthKitWorkout(
    val kind: HealthWorkoutKind,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long,
    val stretches: List<HealthKitStretch>,
    val pauses: List<HealthKitPause>,
    val calories: Int?,
    val metadata: Map<String, Any>,
    val caloriesSyncId: String,
    val version: Long,
)

/** One stretch of a workout that isn't a pause: when, and in its metadata, on what and how many reps. */
internal data class HealthKitStretch(
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val metadata: Map<String, Any>,
)

/** A pause in a workout, from when it was paused to when it went on. */
internal data class HealthKitPause(val startEpochMillis: Long, val endEpochMillis: Long)

/** What [this] is written to HealthKit as. */
internal fun HealthWorkout.toHealthKit(): HealthKitWorkout {
    val (paused, ran) = segments.partition { it.move == HealthMove.Pause }
    return HealthKitWorkout(
        kind = kind,
        startedAtEpochMillis = startedAtEpochMillis,
        endedAtEpochMillis = endedAtEpochMillis,
        stretches = ran.map { segment ->
            HealthKitStretch(
                startEpochMillis = segment.startEpochMillis,
                endEpochMillis = segment.endEpochMillis,
                metadata = buildMap {
                    put(HealthKitKeys.MOVE, segment.move.name)
                    if (segment.repetitions > 0) put(HealthKitKeys.REPETITIONS, segment.repetitions)
                },
            )
        },
        pauses = paused.map { HealthKitPause(it.startEpochMillis, it.endEpochMillis) },
        calories = calories,
        metadata = buildMap {
            put(HealthKitKeys.SYNC_IDENTIFIER, id)
            put(HealthKitKeys.SYNC_VERSION, version)
            title?.let { put(HealthKitKeys.TITLE, it) }
            notes?.let { put(HealthKitKeys.NOTES, it) }
        },
        caloriesSyncId = "$id-calories",
        version = version,
    )
}

/**
 * The metadata keys the app writes: HealthKit's own for the sync identifier and version (the values of
 * `HKMetadataKeySyncIdentifier` and `HKMetadataKeySyncVersion`, kept here so the mapping is tested without
 * HealthKit), and the app's for what HealthKit has no key for. An app's own keys must not start with "HK".
 */
internal object HealthKitKeys {
    const val SYNC_IDENTIFIER = "HKMetadataKeySyncIdentifier"
    const val SYNC_VERSION = "HKMetadataKeySyncVersion"
    const val TITLE = "IntervalTitle"
    const val NOTES = "IntervalNotes"
    const val MOVE = "IntervalMove"
    const val REPETITIONS = "IntervalRepetitions"
}
