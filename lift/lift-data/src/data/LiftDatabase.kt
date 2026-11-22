package dev.justinsmith.lift.data

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Lift's sessions and their sets, the user's bells and the program they follow. Version 2 added
 * [FollowingEntity] and the program day a session was; version 3 settings shared with Interval, then a
 * sibling app; version 4 dropped them, and the copy of Interval's workouts, once Bells (now Lift) became part of it;
 * version 5 added the programs the user makes, [CustomProgramEntity]; version 6 sets done with two bells;
 * version 7 sets of any equipment, held or with a band, and the exercises the user adds,
 * [CustomExerciseEntity]; version 8 the user's changes to their bands and the bands they add, [BandEntity];
 * version 9 a session's day as it changed it, and the days done of a program before following it; version 10
 * the user's barbell, [BarEntity]; version 11 which day of its program a session was; version 12 how long the
 * user rests between sets, [RestEntity]; version 13 how a session felt ([SessionEntity.effort], [SessionEntity.note]);
 * version 14 the timer running in the session in progress, [SessionEntity.timer]; version 15 a finished session's
 * heart rate ([SessionEntity.averageBpm], [SessionEntity.peakBpm]); version 16 a set's session as a foreign key,
 * so a set goes with its session ([MIGRATION_15_16]); version 17 which bells the user owns two of
 * ([BellEntity.pair]), none in rows from before; version 20 which sets were warm-ups ([SetEntity.warmUp]), none
 * of those logged before.
 * Every version adds; none rewrites a row, and only 16 drops one: a set whose session was already gone.
 */
@Database(
    entities = [
        SessionEntity::class,
        SetEntity::class,
        BellEntity::class,
        FollowingEntity::class,
        CustomProgramEntity::class,
        CustomExerciseEntity::class,
        BandEntity::class,
        BarEntity::class,
        RestEntity::class,
    ],
    version = 20,
    // 3 to 4 and 15 to 16 are by hand, [LIFT_MIGRATIONS], added in liftStorageModule.
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 4, to = 5),
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
        AutoMigration(from = 16, to = 17),
        AutoMigration(from = 17, to = 18),
        AutoMigration(from = 18, to = 19),
        AutoMigration(from = 19, to = 20),
    ],
)
@ConstructedBy(LiftDatabaseConstructor::class)
abstract class LiftDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    abstract fun bellDao(): BellDao

    abstract fun followingDao(): FollowingDao

    abstract fun customProgramDao(): CustomProgramDao

    abstract fun customExerciseDao(): CustomExerciseDao

    abstract fun bandDao(): BandDao

    abstract fun importDao(): LiftImportDao
}

/** Room generates the `actual` for each platform. */
@Suppress("KotlinNoActualForExpect")
expect object LiftDatabaseConstructor : RoomDatabaseConstructor<LiftDatabase> {
    override fun initialize(): LiftDatabase
}

/**
 * Version 15 to 16: a set's session becomes a foreign key, deleting its sets with it. SQLite adds one only by
 * building the table again, and a set whose session is gone (a crash between a discard's two deletes, before they
 * were one transaction) can't be copied into it, so it is left behind: no screen ever showed it.
 */
internal val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `_new_lift_set` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `session_uid` TEXT NOT NULL, " +
                "`exercise` TEXT NOT NULL, `bell_kg` REAL NOT NULL, `reps` INTEGER NOT NULL, " +
                "`side` TEXT, `logged_at` INTEGER NOT NULL, `doubled` INTEGER NOT NULL DEFAULT 0, " +
                "`equipment` TEXT, `seconds` INTEGER, `band` TEXT, " +
                "FOREIGN KEY(`session_uid`) REFERENCES `session`(`uid`) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        // By name: a table first made at version 1 has its later columns in the order their versions added them.
        val columns = "`id`, `session_uid`, `exercise`, `bell_kg`, `reps`, `side`, `logged_at`, `doubled`, " +
            "`equipment`, `seconds`, `band`"
        connection.execSQL(
            "INSERT INTO `_new_lift_set` ($columns) SELECT $columns FROM `lift_set` " +
                "WHERE `session_uid` IN (SELECT `uid` FROM `session`)",
        )
        connection.execSQL("DROP TABLE `lift_set`")
        connection.execSQL("ALTER TABLE `_new_lift_set` RENAME TO `lift_set`")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_lift_set_session_uid` ON `lift_set` (`session_uid`)")
    }
}

/** Version 3 to 4: Interval's workouts and the shared settings, which Interval itself now holds, go. */
internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `sibling_session`")
        connection.execSQL("DROP TABLE IF EXISTS `shared_settings`")
    }
}

/** The migrations Room can't work out itself, for every builder of the database. */
internal val LIFT_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_3_4, MIGRATION_15_16)
