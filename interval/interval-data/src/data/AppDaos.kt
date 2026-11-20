package dev.justinsmith.interval.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// The workouts and their undo, by id or, before one is known, by uid.
@Suppress("detekt.TooManyFunctions")
@Dao
interface HistoryDao {
    /** Newest first, deleted ones left out. */
    @Query("SELECT * FROM history WHERE deleted = 0 ORDER BY finished_at DESC, id DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    /** Every row, deleted ones included, in the order stored. */
    @Query("SELECT * FROM history ORDER BY id")
    suspend fun all(): List<HistoryEntity>

    @Insert
    suspend fun insert(entry: HistoryEntity)

    /** Marks one workout deleted at [now], keeping the row as a tombstone. */
    @Query("UPDATE history SET deleted = 1, updated_at = :now WHERE id = :id")
    suspend fun delete(id: Long, now: Long)

    /** Brings a deleted workout back at [now]: Undo on [delete]. */
    @Query("UPDATE history SET deleted = 0, updated_at = :now WHERE id = :id")
    suspend fun restore(id: Long, now: Long)

    /** As [delete], by the uid a workout was stored under: Undo on one logged, before its id is known. */
    @Query("UPDATE history SET deleted = 1, updated_at = :now WHERE uid = :uid")
    suspend fun deleteByUid(uid: String, now: Long)

    /** Sets how the workout [uid] felt, at [now]: an effort level, 1 to 5, and a note, either null for none. */
    @Query("UPDATE history SET effort = :effort, note = :note, updated_at = :now WHERE uid = :uid")
    suspend fun reflect(uid: String, effort: Int?, note: String?, now: Long)

    /** Keeps the heart rate of the workout [uid], at [now], unless it has one: the first found stays. */
    @Query(
        "UPDATE history SET average_bpm = :average, peak_bpm = :peak, updated_at = :now " +
            "WHERE uid = :uid AND average_bpm IS NULL",
    )
    suspend fun keepHeartRate(uid: String, average: Int, peak: Int, now: Long)

    /** As [keepHeartRate], for the workout stored under [id]. */
    @Query(
        "UPDATE history SET average_bpm = :average, peak_bpm = :peak, updated_at = :now " +
            "WHERE id = :id AND average_bpm IS NULL",
    )
    suspend fun keepHeartRateOf(id: Long, average: Int, peak: Int, now: Long)

    /** Sets about how many snatches a set the workout [uid] held and the bell, at [now]; both null for none. */
    @Query(
        "UPDATE history SET snatches_per_set = :perSet, snatch_bell_kg = :bellKg, updated_at = :now " +
            "WHERE uid = :uid",
    )
    suspend fun countSnatches(uid: String, perSet: Int?, bellKg: Double?, now: Long)

    /** As [countSnatches], for the workout stored under [id]. */
    @Query(
        "UPDATE history SET snatches_per_set = :perSet, snatch_bell_kg = :bellKg, updated_at = :now " +
            "WHERE id = :id",
    )
    suspend fun countSnatchesOf(id: Long, perSet: Int?, bellKg: Double?, now: Long)

    /** Marks every workout not yet deleted as deleted at [now]. */
    @Query("UPDATE history SET deleted = 1, updated_at = :now WHERE deleted = 0")
    suspend fun clear(now: Long)
}

@Dao
interface SportLogDao {
    /** The sport days logged, the latest day first. */
    @Query("SELECT * FROM sport_log ORDER BY epoch_day DESC, id DESC")
    fun observeAll(): Flow<List<SportLogEntity>>

    /** Adds [rows], leaving out any whose sport is logged on its day already. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(rows: List<SportLogEntity>)

    @Query("DELETE FROM sport_log WHERE epoch_day = :epochDay AND sport = :sport AND name = :name")
    suspend fun delete(epochDay: Long, sport: String, name: String)

    @Query("DELETE FROM sport_log")
    suspend fun clear()
}

@Dao
interface SportClassDao {
    /** The classes recorded, the latest first. */
    @Query("SELECT * FROM sport_class ORDER BY started_at DESC")
    fun observeAll(): Flow<List<SportClassEntity>>

    /** Adds [row]; a class that started at the same moment is the same one synced again, and is replaced. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: SportClassEntity)

    @Query("DELETE FROM sport_class WHERE started_at = :startedAt")
    suspend fun delete(startedAt: Long)

    @Query("DELETE FROM sport_class")
    suspend fun clear()
}

/** What an import adds, all of it or none: one transaction over the six tables. */
@Dao
interface ImportDao {
    @Insert
    suspend fun insertHistory(rows: List<HistoryEntity>)

    @Insert
    suspend fun insertPresets(rows: List<PresetEntity>): List<Long>

    @Insert
    suspend fun insertPlanned(rows: List<PlannedEntity>)

    /** As [SportLogDao.insert]: a sport logged on its day already stays as it is. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSports(rows: List<SportLogEntity>)

    @Insert
    suspend fun insertFlows(rows: List<FlowEntity>)

    /** A class that started at a moment one here did is here already, and stays as it is. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertClasses(rows: List<SportClassEntity>)

    /**
     * Adds [history], [presets], [planned], [sports], [flows] and [classes] together. A plan naming a preset added
     * here by
     * [presetNames] (in step with [planned]) takes the id it was just stored under.
     */
    @Transaction
    suspend fun importAll(
        history: List<HistoryEntity>,
        presets: List<PresetEntity>,
        planned: List<PlannedEntity>,
        presetNames: List<String?>,
        sports: List<SportLogEntity>,
        flows: List<FlowEntity>,
        classes: List<SportClassEntity> = emptyList(),
    ) {
        insertHistory(history)
        insertSports(sports)
        insertFlows(flows)
        insertClasses(classes)
        val ids = presets.map { it.name }.zip(insertPresets(presets)).toMap()
        insertPlanned(
            planned.zip(presetNames) { row, name ->
                name?.let(ids::get)?.let { row.copy(presetId = it) }
                    ?: row
            },
        )
    }
}

@Dao
interface FlowDao {
    /** In the order they were saved. */
    @Query("SELECT * FROM flow ORDER BY id")
    fun observeAll(): Flow<List<FlowEntity>>

    /** What is stored now, in save order: for a write that must see every write before it. */
    @Query("SELECT * FROM flow ORDER BY id")
    suspend fun all(): List<FlowEntity>

    @Insert
    suspend fun insert(flow: FlowEntity): Long

    /** Rewrites the row with [flow]'s id. */
    @Update
    suspend fun update(flow: FlowEntity)

    @Query("DELETE FROM flow WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface PresetDao {
    /** In the order they were saved. */
    @Query("SELECT * FROM preset ORDER BY id")
    fun observeAll(): Flow<List<PresetEntity>>

    /** What is stored now, in save order: for a write that must see every write before it. */
    @Query("SELECT * FROM preset ORDER BY id")
    suspend fun all(): List<PresetEntity>

    @Insert
    suspend fun insert(preset: PresetEntity): Long

    /** Rewrites the row with [preset]'s id. */
    @Update
    suspend fun update(preset: PresetEntity)

    @Query("DELETE FROM preset WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface PlannedDao {
    /** In the order they were planned. */
    @Query("SELECT * FROM planned ORDER BY id")
    fun observeAll(): Flow<List<PlannedEntity>>

    /** As [observeAll], once. */
    @Query("SELECT * FROM planned ORDER BY id")
    suspend fun all(): List<PlannedEntity>

    @Insert
    suspend fun insert(planned: List<PlannedEntity>)

    @Update
    suspend fun update(planned: PlannedEntity)

    @Query("DELETE FROM planned WHERE id = :id")
    suspend fun delete(id: Long)
}
