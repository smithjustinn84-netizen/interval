package dev.justinsmith.interval.data

import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.ConstructedBy
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.Update
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import kotlinx.coroutines.flow.Flow

/**
 * Workout history, the user's own presets and their planned workouts. Settings live in DataStore, not
 * here. Version 2 added [HistoryEntity.workActiveSeconds] and the activity of each workout and preset;
 * version 3 added [PlannedEntity].
 */
@Database(
    entities = [HistoryEntity::class, PresetEntity::class, PlannedEntity::class],
    version = 3,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    abstract fun presetDao(): PresetDao

    abstract fun plannedDao(): PlannedDao
}

/** Room generates the `actual` for each platform. */
@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

@Dao
interface HistoryDao {
    /** Newest first. */
    @Query("SELECT * FROM history ORDER BY finished_at DESC, id DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Insert
    suspend fun insert(entry: HistoryEntity)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM history")
    suspend fun clear()
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

    @Insert
    suspend fun insert(planned: List<PlannedEntity>)

    @Query("DELETE FROM planned WHERE id = :id")
    suspend fun delete(id: Long)
}

// Columns are flat rather than an embedded IntervalConfig, so the domain types carry no Room annotations.

@Entity(tableName = "history", indices = [Index("finished_at")])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "finished_at") val finishedAtEpochMillis: Long,
    @ColumnInfo(name = "prep_seconds") val prepSeconds: Int,
    @ColumnInfo(name = "work_seconds") val workSeconds: Int,
    @ColumnInfo(name = "rest_seconds") val restSeconds: Int,
    val rounds: Int,
    @ColumnInfo(name = "elapsed_seconds") val elapsedSeconds: Int,
    @ColumnInfo(name = "ended_early") val endedEarly: Boolean,
    /** Of [elapsedSeconds], the time spent in work intervals. Null in rows from version 1. */
    @ColumnInfo(name = "work_active_seconds") val workActiveSeconds: Int? = null,
    /** An [Activity] by name. Null in rows from version 1, which read as [Activity.General]. */
    val activity: String? = null,
)

@Entity(tableName = "preset")
data class PresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "prep_seconds") val prepSeconds: Int,
    @ColumnInfo(name = "work_seconds") val workSeconds: Int,
    @ColumnInfo(name = "rest_seconds") val restSeconds: Int,
    val rounds: Int,
    /** As [HistoryEntity.activity]. */
    val activity: String? = null,
)

/** A [PlannedWorkout]: exactly one of [weekday] (every week) and [epochDay] (once) is set. */
@Entity(tableName = "planned")
data class PlannedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weekday: Int?,
    @ColumnInfo(name = "epoch_day") val epochDay: Long?,
    val name: String,
    @ColumnInfo(name = "prep_seconds") val prepSeconds: Int,
    @ColumnInfo(name = "work_seconds") val workSeconds: Int,
    @ColumnInfo(name = "rest_seconds") val restSeconds: Int,
    val rounds: Int,
    /** As [HistoryEntity.activity]. */
    val activity: String?,
    @ColumnInfo(name = "preset_id") val presetId: Long?,
    @ColumnInfo(name = "created_at") val createdAtEpochMillis: Long,
)

internal fun HistoryEntity.toDomain() = HistoryEntry(
    finishedAtEpochMillis = finishedAtEpochMillis,
    config = IntervalConfig(prepSeconds, workSeconds, restSeconds, rounds, Activity.named(activity)),
    elapsedSeconds = elapsedSeconds,
    endedEarly = endedEarly,
    workActiveSeconds = workActiveSeconds,
    id = id,
)

internal fun HistoryEntry.toEntity() = HistoryEntity(
    id = id ?: 0,
    finishedAtEpochMillis = finishedAtEpochMillis,
    prepSeconds = config.prepSeconds,
    workSeconds = config.workSeconds,
    restSeconds = config.restSeconds,
    rounds = config.rounds,
    elapsedSeconds = elapsedSeconds,
    endedEarly = endedEarly,
    workActiveSeconds = workActiveSeconds,
    activity = config.activity.name,
)

internal fun PresetEntity.toDomain() =
    Preset(name, IntervalConfig(prepSeconds, workSeconds, restSeconds, rounds, Activity.named(activity)), id = id)

internal fun Preset.toEntity() = PresetEntity(
    id = id ?: 0,
    name = name,
    prepSeconds = config.prepSeconds,
    workSeconds = config.workSeconds,
    restSeconds = config.restSeconds,
    rounds = config.rounds,
    activity = config.activity.name,
)

/** A row with neither a weekday nor a day is unreadable: null, and left out. */
internal fun PlannedEntity.toDomain(): PlannedWorkout? {
    val schedule = when {
        weekday != null -> PlanSchedule.Weekly(weekday)
        epochDay != null -> PlanSchedule.Once(epochDay)
        else -> return null
    }
    val config = IntervalConfig(prepSeconds, workSeconds, restSeconds, rounds, Activity.named(activity))
    return PlannedWorkout(id, name, config, schedule, presetId, createdAtEpochMillis)
}

internal fun PlannedWorkout.toEntity() = PlannedEntity(
    id = id,
    weekday = (schedule as? PlanSchedule.Weekly)?.weekday,
    epochDay = (schedule as? PlanSchedule.Once)?.epochDay,
    name = name,
    prepSeconds = config.prepSeconds,
    workSeconds = config.workSeconds,
    restSeconds = config.restSeconds,
    rounds = config.rounds,
    activity = config.activity.name,
    presetId = presetId,
    createdAtEpochMillis = createdAtEpochMillis,
)
