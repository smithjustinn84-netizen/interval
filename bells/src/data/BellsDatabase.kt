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
import kotlinx.coroutines.flow.Flow

/**
 * Bells' sessions and their sets, the user's bells, the program they follow, and Interval's workouts as it
 * last shared them. Version 2 added [FollowingEntity] and the program day a session was.
 */
@Database(
    entities = [
        SessionEntity::class,
        SetEntity::class,
        BellEntity::class,
        SiblingEntity::class,
        FollowingEntity::class,
    ],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@ConstructedBy(BellsDatabaseConstructor::class)
abstract class BellsDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    abstract fun bellDao(): BellDao

    abstract fun siblingDao(): SiblingDao

    abstract fun followingDao(): FollowingDao
}

/** Room generates the `actual` for each platform. */
@Suppress("KotlinNoActualForExpect")
expect object BellsDatabaseConstructor : RoomDatabaseConstructor<BellsDatabase> {
    override fun initialize(): BellsDatabase
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

    /** Every session, deleted ones included: what Interval is sent. */
    @Query("SELECT * FROM session")
    suspend fun allSessions(): List<SessionEntity>

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

    /** Marks a finished session deleted, keeping the row so Interval hears of it. */
    @Query("UPDATE session SET deleted = 1, updated_at = :now WHERE uid = :uid")
    suspend fun markDeleted(uid: String, now: Long)

    /** Drops a session never shared (one discarded before it finished) and its sets. */
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

@Dao
interface SiblingDao {
    @Query("SELECT * FROM sibling_session ORDER BY finished_at DESC")
    fun observeAll(): Flow<List<SiblingEntity>>

    @Query("SELECT * FROM sibling_session")
    suspend fun all(): List<SiblingEntity>

    @Upsert
    suspend fun upsert(session: SiblingEntity)

    @Query("DELETE FROM sibling_session WHERE uid = :uid")
    suspend fun delete(uid: String)
}

@Entity(tableName = "session", indices = [Index("started_at")])
data class SessionEntity(
    /** 32 hex digits, never reused: who it is to Interval too. */
    @PrimaryKey val uid: String,
    @ColumnInfo(name = "started_at") val startedAtEpochMillis: Long,
    /** Null while it is in progress. */
    @ColumnInfo(name = "finished_at") val finishedAtEpochMillis: Long?,
    val title: String,
    @ColumnInfo(name = "updated_at") val updatedAtEpochMillis: Long,
    val deleted: Boolean = false,
    /** The program it was a day of, a [dev.justinsmith.bells.domain.ProgramId] by name; null for a free session. */
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
)

/** The program the user follows, from [startedAtEpochMillis]: one row at most. */
@Entity(tableName = "following")
data class FollowingEntity(
    @PrimaryKey val id: Int = 0,
    @ColumnInfo(name = "program_id") val programId: String,
    @ColumnInfo(name = "started_at") val startedAtEpochMillis: Long,
)

/** A bell the user owns. */
@Entity(tableName = "bell")
data class BellEntity(@PrimaryKey val kg: Double)

/** One of Interval's workouts, as it last shared it. */
@Entity(tableName = "sibling_session")
data class SiblingEntity(
    @PrimaryKey val uid: String,
    val source: String,
    @ColumnInfo(name = "finished_at") val finishedAtEpochMillis: Long,
    @ColumnInfo(name = "elapsed_seconds") val elapsedSeconds: Int,
    val title: String,
    @ColumnInfo(name = "updated_at") val updatedAtEpochMillis: Long,
)
