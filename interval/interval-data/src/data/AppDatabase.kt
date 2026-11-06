package dev.justinsmith.interval.data

import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.ConstructedBy
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import dev.justinsmith.interval.model.PerceivedEffort
import dev.justinsmith.interval.model.PlannedWorkout
import dev.justinsmith.interval.timer.Activity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow

/**
 * Workout history, the user's own presets and their planned workouts. Settings live in DataStore, not
 * here. Version 2 added [HistoryEntity.workActiveSeconds] and the activity of each workout and preset (the
 * latter a day later without a new version: [AddActivityIfMissing]); version 3 added [PlannedEntity];
 * version 4 gave each workout a [HistoryEntity.uid], a change time and a deleted flag, and a table of
 * workouts shared by a sibling app, which version 5 dropped when that app became part of this one;
 * version 6 let a plan be a session on Lift ([PlannedEntity.lift]); version 7 kept how a workout felt
 * ([HistoryEntity.effort], [HistoryEntity.note]); version 8 gave every workout, preset and plan a warm-up and a
 * cool-down ([HistoryEntity.warmUpSeconds], [HistoryEntity.coolDownSeconds]), 0 in rows from before; version 9
 * kept when a workout began and its heart rate ([HistoryEntity.startedAtEpochMillis], [HistoryEntity.averageBpm]);
 * version 10 gave every workout, preset and plan its sets and the rest between them ([HistoryEntity.sets],
 * [HistoryEntity.setRestSeconds]), one set with none in rows from before; version 11 their ladder
 * ([HistoryEntity.workStepSeconds]), none in rows from before; version 12 the blocks of a sequence after its first
 * and the rest between them ([HistoryEntity.thenBlocks], [HistoryEntity.blockRestSeconds]), one block in rows from
 * before; version 13 let a plan be a day of a sport ([PlannedEntity.sport]) and added the sport days logged
 * ([SportLogEntity]).
 */
@Database(
    entities = [HistoryEntity::class, PresetEntity::class, PlannedEntity::class, SportLogEntity::class],
    version = 13,
    // 3 to 4 and 4 to 5 are MIGRATION_3_4 and MIGRATION_4_5, added by buildAppDatabase.
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3, spec = AddActivityIfMissing::class),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11),
        AutoMigration(from = 11, to = 12),
        AutoMigration(from = 12, to = 13),
    ],
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    abstract fun presetDao(): PresetDao

    abstract fun plannedDao(): PlannedDao

    abstract fun sportLogDao(): SportLogDao

    abstract fun importDao(): ImportDao
}

/** Room generates the `actual` for each platform. */
@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

/**
 * Version 2 to 3's extra step. On 18 October 2026 version 2 first had only [HistoryEntity.workActiveSeconds]
 * (`db61c16`), then gained `activity` on `history` and `preset` without a new version (`0a9d0f9`). A database
 * written in between is at version 2 with no `activity`, which the schema check after the migrations refuses.
 * Room's own statements for 2 to 3 only create `planned`; this adds the column where it is missing.
 */
internal class AddActivityIfMissing : AutoMigrationSpec {
    override fun onPostMigrate(connection: SQLiteConnection) {
        for (table in listOf("history", "preset")) {
            if (!connection.hasColumn(table, "activity")) {
                connection.execSQL("ALTER TABLE `$table` ADD COLUMN `activity` TEXT")
            }
        }
    }
}

/** Whether [table] has a column named [column], by `PRAGMA table_info`. */
private fun SQLiteConnection.hasColumn(table: String, column: String): Boolean =
    prepare("PRAGMA table_info(`$table`)").use { statement ->
        generateSequence { if (statement.step()) statement.getText(1) else null }.any { it == column }
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

    /** Brings a deleted workout back at [now]: Undo on [delete]. */
    @Query("UPDATE history SET deleted = 0, updated_at = :now WHERE id = :id")
    suspend fun restore(id: Long, now: Long)

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

/** What an import adds, all of it or none: one transaction over the four tables. */
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

    /**
     * Adds [history], [presets], [planned] and [sports] together. A plan naming a preset added here by
     * [presetNames] (in step with [planned]) takes the id it was just stored under.
     */
    @Transaction
    suspend fun importAll(
        history: List<HistoryEntity>,
        presets: List<PresetEntity>,
        planned: List<PlannedEntity>,
        presetNames: List<String?>,
        sports: List<SportLogEntity>,
    ) {
        insertHistory(history)
        insertSports(sports)
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
    /** How hard it felt, a [PerceivedEffort.level]; null until the user says, and in rows from before version 7. */
    val effort: Int? = null,
    /** A line on how it went; null for none. */
    val note: String? = null,
    /** 0 for none, and in rows from before version 8. */
    @ColumnInfo(name = "warm_up_seconds", defaultValue = "0") val warmUpSeconds: Int = 0,
    @ColumnInfo(name = "cool_down_seconds", defaultValue = "0") val coolDownSeconds: Int = 0,
    /** When it began; null in rows from before version 9, whose start is run back from [finishedAtEpochMillis]. */
    @ColumnInfo(name = "started_at") val startedAtEpochMillis: Long? = null,
    /** Its heart rate, average and peak in beats a minute, as read from the health store; null until read. */
    @ColumnInfo(name = "average_bpm") val averageBpm: Int? = null,
    @ColumnInfo(name = "peak_bpm") val peakBpm: Int? = null,
    /** 1 and 0, one set with no rest between, in rows from before version 10; no ladder before 11. */
    @ColumnInfo(defaultValue = "1") val sets: Int = 1,
    @ColumnInfo(name = "set_rest_seconds", defaultValue = "0") val setRestSeconds: Int = 0,
    @ColumnInfo(name = "work_step_seconds", defaultValue = "0") val workStepSeconds: Int = 0,
    /** A sequence's blocks after its first, a JSON list ([encodeBlocks]); "[]", one block, in rows from before 12. */
    @ColumnInfo(name = "then_blocks", defaultValue = "'[]'") val thenBlocks: String = "[]",
    @ColumnInfo(name = "block_rest_seconds", defaultValue = "0") val blockRestSeconds: Int = 0,
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
    /** As [HistoryEntity.warmUpSeconds]. */
    @ColumnInfo(name = "warm_up_seconds", defaultValue = "0") val warmUpSeconds: Int = 0,
    @ColumnInfo(name = "cool_down_seconds", defaultValue = "0") val coolDownSeconds: Int = 0,
    /** As [HistoryEntity.sets]. */
    @ColumnInfo(defaultValue = "1") val sets: Int = 1,
    @ColumnInfo(name = "set_rest_seconds", defaultValue = "0") val setRestSeconds: Int = 0,
    @ColumnInfo(name = "work_step_seconds", defaultValue = "0") val workStepSeconds: Int = 0,
    /** As [HistoryEntity.thenBlocks]. */
    @ColumnInfo(name = "then_blocks", defaultValue = "'[]'") val thenBlocks: String = "[]",
    @ColumnInfo(name = "block_rest_seconds", defaultValue = "0") val blockRestSeconds: Int = 0,
)

/**
 * A [PlannedWorkout]: exactly one of [weekday] (every week) and [epochDay] (once) is set. [lift] names a session
 * on Lift ([LIFT_PROGRAM_DAY] or [LIFT_ANY_SESSION]), whose row leaves the name and the shape empty; [sport] a day
 * of a sport (see [sportOf]), whose row leaves the shape empty and holds in [name] the user's own name for one they
 * named. With both null it is an interval workout.
 */
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
    val lift: String? = null,
    /** As [HistoryEntity.warmUpSeconds]. */
    @ColumnInfo(name = "warm_up_seconds", defaultValue = "0") val warmUpSeconds: Int = 0,
    @ColumnInfo(name = "cool_down_seconds", defaultValue = "0") val coolDownSeconds: Int = 0,
    /** As [HistoryEntity.sets]. */
    @ColumnInfo(defaultValue = "1") val sets: Int = 1,
    @ColumnInfo(name = "set_rest_seconds", defaultValue = "0") val setRestSeconds: Int = 0,
    @ColumnInfo(name = "work_step_seconds", defaultValue = "0") val workStepSeconds: Int = 0,
    /** As [HistoryEntity.thenBlocks]. */
    @ColumnInfo(name = "then_blocks", defaultValue = "'[]'") val thenBlocks: String = "[]",
    @ColumnInfo(name = "block_rest_seconds", defaultValue = "0") val blockRestSeconds: Int = 0,
    /** Null in rows from before version 13, none of which is a sport. */
    val sport: String? = null,
)

/**
 * A day of a sport the user logged: one of a sport a day, which the index keeps. [sport] and [name] are as
 * [PlannedEntity]'s, the name empty but for a sport the user named.
 */
@Entity(tableName = "sport_log", indices = [Index(value = ["epoch_day", "sport", "name"], unique = true)])
data class SportLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "epoch_day") val epochDay: Long,
    val sport: String,
    val name: String,
    @ColumnInfo(name = "logged_at") val loggedAtEpochMillis: Long,
)
