package dev.justinsmith.bells.data

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
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import kotlinx.coroutines.flow.Flow

/**
 * Bells' sessions and their sets, the user's bells and the program they follow. Version 2 added
 * [FollowingEntity] and the program day a session was; version 3 settings shared with Interval, then a
 * sibling app; version 4 dropped them, and the copy of Interval's workouts, once Bells became part of it;
 * version 5 added the programs the user makes, [CustomProgramEntity]; version 6 sets done with two bells.
 */
@Database(
    entities = [
        SessionEntity::class,
        SetEntity::class,
        BellEntity::class,
        FollowingEntity::class,
        CustomProgramEntity::class,
    ],
    version = 6,
    // 3 to 4 is MIGRATION_3_4, added in bellsStorageModule.
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
    ],
)
@ConstructedBy(BellsDatabaseConstructor::class)
abstract class BellsDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    abstract fun bellDao(): BellDao

    abstract fun followingDao(): FollowingDao

    abstract fun customProgramDao(): CustomProgramDao
}

/** Room generates the `actual` for each platform. */
@Suppress("KotlinNoActualForExpect")
expect object BellsDatabaseConstructor : RoomDatabaseConstructor<BellsDatabase> {
    override fun initialize(): BellsDatabase
}

/** Version 3 to 4: Interval's workouts and the shared settings, which Interval itself now holds, go. */
internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `sibling_session`")
        connection.execSQL("DROP TABLE IF EXISTS `shared_settings`")
    }
}

// Sessions and their sets change together, so one DAO holds both.
@Suppress("detekt.TooManyFunctions")
@Dao
interface SessionDao {
    /** Every session not deleted, the one in progress included, newest first. */
    @Query("SELECT * FROM session WHERE deleted = 0 ORDER BY started_at DESC")
    fun observeSessions(): Flow<List<SessionEntity>>

    /** Every set of every session, in the order logged. */
    @Query("SELECT * FROM lift_set ORDER BY logged_at, id")
    fun observeSets(): Flow<List<SetEntity>>

    @Query("SELECT * FROM lift_set ORDER BY logged_at, id")
    suspend fun allSets(): List<SetEntity>

    @Query("SELECT * FROM session WHERE finished_at IS NULL AND deleted = 0 LIMIT 1")
    suspend fun active(): SessionEntity?

    @Insert
    suspend fun insert(session: SessionEntity)

    /** How many sessions of [programId] finished since [since]: the days of it done. */
    @Query(
        "SELECT COUNT(*) FROM session WHERE program_id = :programId AND finished_at IS NOT NULL " +
            "AND deleted = 0 AND started_at >= :since",
    )
    fun observeProgramDays(programId: String, since: Long): Flow<Int>

    @Insert
    suspend fun insert(set: SetEntity)

    @Query("DELETE FROM lift_set WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Query("UPDATE session SET finished_at = :at, title = :title, updated_at = :at WHERE uid = :uid")
    suspend fun finish(uid: String, title: String, at: Long)

    /** Marks a finished session deleted, keeping the row as a tombstone. */
    @Query("UPDATE session SET deleted = 1, updated_at = :now WHERE uid = :uid")
    suspend fun markDeleted(uid: String, now: Long)

    /** Drops a session discarded before it finished. */
    @Query("DELETE FROM session WHERE uid = :uid")
    suspend fun discard(uid: String)

    @Query("DELETE FROM lift_set WHERE session_uid = :uid")
    suspend fun discardSets(uid: String)
}

@Dao
interface FollowingDao {
    @Query("SELECT * FROM following WHERE id = 0")
    fun observe(): Flow<FollowingEntity?>

    @Upsert
    suspend fun upsert(following: FollowingEntity)

    @Query("DELETE FROM following")
    suspend fun clear()

    /** Stops following [programId] (as stored), if it is the one followed. */
    @Query("DELETE FROM following WHERE program_id = :programId")
    suspend fun clearIfFollowing(programId: String)
}

@Dao
interface CustomProgramDao {
    @Query("SELECT * FROM custom_program ORDER BY name COLLATE NOCASE, uid")
    fun observeAll(): Flow<List<CustomProgramEntity>>

    @Upsert
    suspend fun upsert(program: CustomProgramEntity)

    @Query("DELETE FROM custom_program WHERE uid = :uid")
    suspend fun delete(uid: String)
}

@Dao
interface BellDao {
    @Query("SELECT * FROM bell ORDER BY kg")
    fun observeAll(): Flow<List<BellEntity>>

    @Upsert
    suspend fun upsert(bell: BellEntity)

    @Query("DELETE FROM bell WHERE kg = :kg")
    suspend fun delete(kg: Double)
}

@Entity(tableName = "session", indices = [Index("started_at")])
data class SessionEntity(
    /** 32 hex digits, never reused: how History and its detail page name it. */
    @PrimaryKey val uid: String,
    @ColumnInfo(name = "started_at") val startedAtEpochMillis: Long,
    /** Null while it is in progress. */
    @ColumnInfo(name = "finished_at") val finishedAtEpochMillis: Long?,
    val title: String,
    @ColumnInfo(name = "updated_at") val updatedAtEpochMillis: Long,
    val deleted: Boolean = false,
    /** The program it was a day of, a [dev.justinsmith.bells.domain.ProgramKey] as stored; null for a free session. */
    @ColumnInfo(name = "program_id") val programId: String? = null,
)

@Entity(tableName = "lift_set", indices = [Index("session_uid")])
data class SetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_uid") val sessionUid: String,
    /** An [dev.justinsmith.bells.domain.Exercise] by name. */
    val exercise: String,
    @ColumnInfo(name = "bell_kg") val bellKg: Double,
    val reps: Int,
    /** A [dev.justinsmith.bells.domain.Side] by name, or null for a two-sided lift. */
    val side: String?,
    @ColumnInfo(name = "logged_at") val loggedAtEpochMillis: Long,
    /** Done with a bell in each hand, both of [bellKg]. */
    @ColumnInfo(defaultValue = "0") val doubled: Boolean = false,
)

/**
 * The program the user follows, a [dev.justinsmith.bells.domain.ProgramKey] as stored, from
 * [startedAtEpochMillis]: one row at most.
 */
@Entity(tableName = "following")
data class FollowingEntity(
    @PrimaryKey val id: Int = 0,
    @ColumnInfo(name = "program_id") val programId: String,
    @ColumnInfo(name = "started_at") val startedAtEpochMillis: Long,
)

/** A bell the user owns. */
@Entity(tableName = "bell")
data class BellEntity(@PrimaryKey val kg: Double)

/** A program the user made. [days] is its days' blocks as JSON, in [encodeDays]'s shape. */
@Entity(tableName = "custom_program")
data class CustomProgramEntity(
    /** 32 hex digits, never reused. */
    @PrimaryKey val uid: String,
    val name: String,
    @ColumnInfo(name = "days_per_week") val daysPerWeek: Int,
    val weeks: Int,
    val days: String,
    @ColumnInfo(name = "updated_at") val updatedAtEpochMillis: Long,
)
