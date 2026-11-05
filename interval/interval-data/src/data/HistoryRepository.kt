package dev.justinsmith.interval.data

import co.touchlab.kermit.Logger
import dev.justinsmith.interval.model.HistoryEntry
import dev.justinsmith.interval.model.PerceivedEffort
import dev.justinsmith.interval.platform.HeartRate
import dev.justinsmith.interval.platform.IdGenerator
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.storage.SerialWriter
import dev.justinsmith.interval.storage.orEmptyOnError
import dev.justinsmith.interval.timer.IntervalConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The finished workouts, which follow the database: the [HistoryStore]. Each is stored under a uid from [ids]; a
 * deleted one stays behind as a tombstone. Written through [writer], the queue every interval repository shares,
 * so how a workout felt never lands before the workout.
 */
class HistoryRepository(
    private val historyDao: HistoryDao,
    private val writer: SerialWriter,
    private val clock: WallClock,
    private val ids: IdGenerator,
    scope: CoroutineScope,
) : HistoryStore {
    private val log = Logger.withTag("HistoryRepository")

    private val _ready = MutableStateFlow(false)

    /** True once the history has been read. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _history = MutableStateFlow(emptyList<HistoryEntry>())

    /** Newest first. */
    override val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    init {
        // Mapped upstream of its fallback, so a row that fails to map counts as a failed read.
        scope.launch {
            historyDao.observeAll()
                .map { rows -> rows.map { it.toDomain() } }
                .orEmptyOnError("history", log)
                .collect { entries ->
                    _history.value = entries
                    _ready.value = true
                }
        }
    }

    /**
     * Records a workout that ran for [elapsedSeconds], [workSeconds] of them working; [endedEarly] when it
     * stopped before the last round. Returns the uid it is stored under, for [reflectOnWorkout].
     */
    override fun recordWorkout(
        config: IntervalConfig,
        elapsedSeconds: Int,
        endedEarly: Boolean,
        workSeconds: Int?,
        startedAtEpochMillis: Long?,
    ): String {
        val now = clock.nowMillis()
        val uid = ids.newId()
        val entry =
            HistoryEntry(
                now,
                config,
                elapsedSeconds,
                endedEarly,
                workSeconds,
                startedAtEpochMillis = startedAtEpochMillis,
            )
        writer.persist { historyDao.insert(entry.toEntity(uid = uid, updatedAtEpochMillis = now)) }
        return uid
    }

    /**
     * Keeps how the workout [uid] felt, as said on its summary: [effort] and a [note], blank for none. In order
     * after the write that recorded it, so it never lands first.
     */
    override fun reflectOnWorkout(uid: String, effort: PerceivedEffort?, note: String) {
        val now = clock.nowMillis()
        val kept = note.trim().take(PerceivedEffort.NOTE_MAX_LENGTH).ifEmpty { null }
        writer.persist { historyDao.reflect(uid, effort?.level, kept, now) }
    }

    /** In order after the write that recorded the workout, so it never lands first. */
    override fun keepHeartRate(uid: String, heartRate: HeartRate) {
        val now = clock.nowMillis()
        writer.persist { historyDao.keepHeartRate(uid, heartRate.averageBpm, heartRate.peakBpm, now) }
    }

    override fun keepHeartRateOf(id: Long, heartRate: HeartRate) {
        val now = clock.nowMillis()
        writer.persist { historyDao.keepHeartRateOf(id, heartRate.averageBpm, heartRate.peakBpm, now) }
    }

    /**
     * Removes one workout from the history, by the id it is stored under. Its row stays behind, marked
     * deleted.
     */
    override fun deleteWorkout(id: Long) {
        val now = clock.nowMillis()
        writer.persist { historyDao.delete(id, now) }
    }

    /** Brings back a workout [deleteWorkout] removed: Undo. Its row was only marked, so nothing is lost. */
    override fun restoreWorkout(id: Long) {
        val now = clock.nowMillis()
        writer.persist { historyDao.restore(id, now) }
    }

    /** Removes every workout from the history, leaving each row marked deleted as [deleteWorkout] does. */
    override fun clearHistory() {
        val now = clock.nowMillis()
        writer.persist { historyDao.clear(now) }
    }
}
