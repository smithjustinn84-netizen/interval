package dev.justinsmith.interval.data

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
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import kotlinx.coroutines.flow.Flow

/** Workout history and the user's own presets. Settings live in DataStore, not here. */
@Database(entities = [HistoryEntity::class, PresetEntity::class], version = 1)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    abstract fun presetDao(): PresetDao
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
)

@Entity(tableName = "preset")
data class PresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "prep_seconds") val prepSeconds: Int,
    @ColumnInfo(name = "work_seconds") val workSeconds: Int,
    @ColumnInfo(name = "rest_seconds") val restSeconds: Int,
    val rounds: Int,
)

internal fun HistoryEntity.toDomain() = HistoryEntry(
    finishedAtEpochMillis = finishedAtEpochMillis,
    config = IntervalConfig(prepSeconds, workSeconds, restSeconds, rounds),
    elapsedSeconds = elapsedSeconds,
    endedEarly = endedEarly,
)

internal fun HistoryEntry.toEntity() = HistoryEntity(
    finishedAtEpochMillis = finishedAtEpochMillis,
    prepSeconds = config.prepSeconds,
    workSeconds = config.workSeconds,
    restSeconds = config.restSeconds,
    rounds = config.rounds,
    elapsedSeconds = elapsedSeconds,
    endedEarly = endedEarly,
)

internal fun PresetEntity.toDomain() =
    Preset(name, IntervalConfig(prepSeconds, workSeconds, restSeconds, rounds), id = id)

internal fun Preset.toEntity() = PresetEntity(
    id = id ?: 0,
    name = name,
    prepSeconds = config.prepSeconds,
    workSeconds = config.workSeconds,
    restSeconds = config.restSeconds,
    rounds = config.rounds,
)
