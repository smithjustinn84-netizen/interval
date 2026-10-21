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
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow

/**
 * Workout history, the user's own presets and their planned workouts. Settings live in DataStore, not
 * here. Version 2 added [HistoryEntity.workActiveSeconds] and the activity of each workout and preset;
 * version 3 added [PlannedEntity]; version 4 gave each workout a [HistoryEntity.uid], a change time and
 * a deleted flag, and a table of workouts shared by a sibling app, which version 5 dropped when that app
 * became part of this one.
 */
@Database(
    entities = [HistoryEntity::class, PresetEntity::class, PlannedEntity::class],
    version = 5,
    // 3 to 4 and 4 to 5 are MIGRATION_3_4 and MIGRATION_4_5, added by buildAppDatabase.
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

/**
 * Version 3 to 4, by hand: Room's own [AutoMigration] rebuilds `history` and creates its unique uid index
 * before an [androidx.room.migration.AutoMigrationSpec] can run, so with two workouts stored, both still
 * `''`, it would fail. Here every workout gets a uid of its own first, then uids are made unique. The
 * tables match `shared/schemas/.../4.json`, which Room checks on open.
 */
internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `history` ADD COLUMN `uid` TEXT NOT NULL DEFAULT ''")
        connection.execSQL("ALTER TABLE `history` ADD COLUMN `updated_at` INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE `history` ADD COLUMN `deleted` INTEGER NOT NULL DEFAULT 0")
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `sibling_session` (`uid` TEXT NOT NULL, `source` TEXT NOT NULL, " +
                "`finished_at` INTEGER NOT NULL, `elapsed_seconds` INTEGER NOT NULL, `active_seconds` INTEGER, " +
                "`activity` TEXT, `title` TEXT NOT NULL, `detail_json` TEXT NOT NULL, " +
                "`updated_at` INTEGER NOT NULL, PRIMARY KEY(`uid`))",
        )
        connection.execSQL("UPDATE history SET uid = lower(hex(randomblob(16))) WHERE uid = ''")
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_history_uid` ON `history` (`uid`)")
    }
}

/** Version 4 to 5: the sibling app's copied workouts go, now that its sessions live in this app. */
internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `sibling_session`")
    }
}

/**
 * Opens the database the way the app does: with every hand-written migration, the bundled SQLite and its
 * queries on IO. Tests open theirs through it too, so a new migration reaches both.
 */
internal fun RoomDatabase.Builder<AppDatabase>.buildAppDatabase(): AppDatabase =
    addMigrations(MIGRATION_3_4, MIGRATION_4_5)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

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

    /** Marks every workout not yet deleted as deleted at [now]. */
    @Query("UPDATE history SET deleted = 1, updated_at = :now WHERE deleted = 0")
    suspend fun clear(now: Long)
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

@Entity(tableName = "history", indices = [Index("finished_at"), Index(value = ["uid"], unique = true)])
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
    /** Who it is across devices and apps: 32 hex digits, never reused. */
    @ColumnInfo(defaultValue = "''") val uid: String,
    /** When it was recorded or last changed, deletion included. 0 in rows from before version 4. */
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAtEpochMillis: Long,
    /** A deleted workout stays as a tombstone, its row kept with when it went. */
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
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

internal fun HistoryEntry.toEntity(uid: String, updatedAtEpochMillis: Long) = HistoryEntity(
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
    uid = uid,
    updatedAtEpochMillis = updatedAtEpochMillis,
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
