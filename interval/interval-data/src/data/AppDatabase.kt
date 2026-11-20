package dev.justinsmith.interval.data

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

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
 * ([SportLogEntity]); version 14 the mobility flows the user built ([FlowEntity]); version 15 whether a preset
 * switches hands each work ([PresetEntity.alternateHands]), not in rows from before; version 16 about how many
 * snatches a set a workout held and the bell ([HistoryEntity.snatchesPerSet], [HistoryEntity.snatchBellKg]), none
 * in rows from before; version 17 the day a one-off plan was moved from ([PlannedEntity.movedFromDay]), none in
 * rows from before; version 18 whether a workout's, preset's or plan's first block is a time cap
 * ([HistoryEntity.capped]) and the rounds done in a workout's caps ([HistoryEntity.capRounds]), neither in rows
 * from before; version 19 the classes recorded on a heart-rate sensor ([SportClassEntity]); version 20 a workout's
 * heart rate as a sensor read it live ([HistoryEntity.heartRateSamples]), none in rows from before.
 */
@Database(
    entities = [
        HistoryEntity::class,
        PresetEntity::class,
        PlannedEntity::class,
        SportLogEntity::class,
        FlowEntity::class,
        SportClassEntity::class,
    ],
    version = 20,
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
        AutoMigration(from = 13, to = 14),
        AutoMigration(from = 14, to = 15),
        AutoMigration(from = 15, to = 16),
        AutoMigration(from = 16, to = 17),
        AutoMigration(from = 17, to = 18),
        AutoMigration(from = 18, to = 19),
        AutoMigration(from = 19, to = 20),
    ],
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    abstract fun presetDao(): PresetDao

    abstract fun plannedDao(): PlannedDao

    abstract fun sportLogDao(): SportLogDao

    abstract fun flowDao(): FlowDao

    abstract fun sportClassDao(): SportClassDao

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
