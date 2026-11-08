package dev.justinsmith.interval.data

import co.touchlab.kermit.Logger
import dev.justinsmith.interval.model.HistoryEntry
import dev.justinsmith.interval.model.LoggedSport
import dev.justinsmith.interval.model.PerceivedEffort
import dev.justinsmith.interval.model.SnatchCount
import dev.justinsmith.interval.model.Sport
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The finished workouts and the sport days logged, which follow the database: the [HistoryStore]. Each workout is
 * stored under a uid from [ids]; a deleted one stays behind as a tombstone. A sport day is a row that comes and
 * goes. Written through [writer], the queue every interval repository shares, so how a workout felt never lands
 * before the workout.
 */
class HistoryRepository(
    private val historyDao: HistoryDao,
    private val sportLogDao: SportLogDao,
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

    private val _sportDays = MutableStateFlow(emptyList<LoggedSport>())

    override val sportDays: StateFlow<List<LoggedSport>> = _sportDays.asStateFlow()

    init {
        // Mapped upstream of its fallback, so a row that fails to map counts as a failed read.
        val workouts = historyDao.observeAll()
            .map { rows -> rows.map { it.toDomain() } }
            .orEmptyOnError("history", log)
        val sports = sportLogDao.observeAll()
            .map { rows -> rows.mapNotNull { it.toDomain() } }
            .orEmptyOnError("sport days", log)
        scope.launch {
            combine(workouts, sports) { entries, days -> entries to days }.collect { (entries, days) ->
                _history.value = entries
                _sportDays.value = days
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

    /** In order after the write that recorded the workout, as [keepHeartRate] is. */
    override fun countSnatches(uid: String, count: SnatchCount?) {
        val now = clock.nowMillis()
        writer.persist { historyDao.countSnatches(uid, count?.perSet, count?.bellKg, now) }
    }

    override fun countSnatchesOf(id: Long, count: SnatchCount?) {
        val now = clock.nowMillis()
        writer.persist { historyDao.countSnatchesOf(id, count?.perSet, count?.bellKg, now) }
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

    override fun logSport(sport: Sport, epochDay: Long) {
        val row = LoggedSport(id = 0, sport, epochDay, loggedAtEpochMillis = clock.nowMillis()).toEntity()
        writer.persist { sportLogDao.insert(listOf(row)) }
    }

    override fun unlogSport(sport: Sport, epochDay: Long) {
        writer.persist { sportLogDao.delete(epochDay, sport.key, sport.storedName) }
    }

    override fun logWorkout(config: IntervalConfig, finishedAtEpochMillis: Long): String {
        val now = clock.nowMillis()
        val uid = ids.newId()
        val entry = HistoryEntry.loggedByHand(config, finishedAtEpochMillis)
        writer.persist { historyDao.insert(entry.toEntity(uid = uid, updatedAtEpochMillis = now)) }
        return uid
    }

    /** In order after the write that logged it, so Undo never runs first. */
    override fun unlogWorkout(uid: String) {
        val now = clock.nowMillis()
        writer.persist { historyDao.deleteByUid(uid, now) }
    }

    /**
     * Removes every workout from the history, leaving each row marked deleted as [deleteWorkout] does, and every
     * sport day logged.
     */
    override fun clearHistory() {
        val now = clock.nowMillis()
        writer.persist {
            historyDao.clear(now)
            sportLogDao.clear()
        }
    }
}
