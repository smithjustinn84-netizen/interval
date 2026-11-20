package dev.justinsmith.lift.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// Sessions and their sets change together, so one DAO holds both.
@Suppress("detekt.TooManyFunctions")
@Dao
interface SessionDao {
    /** Every session not deleted, the one in progress included, newest first. */
    @Query("SELECT * FROM session WHERE deleted = 0 ORDER BY started_at DESC")
    fun observeSessions(): Flow<List<SessionEntity>>

    /** Every set of every session not deleted, in the order logged: a deleted session's stay stored, unread. */
    @Query(
        "SELECT lift_set.* FROM lift_set JOIN session ON session.uid = lift_set.session_uid " +
            "WHERE session.deleted = 0 ORDER BY lift_set.logged_at, lift_set.id",
    )
    fun observeSets(): Flow<List<SetEntity>>

    /** Every set stored, those of deleted sessions too, in the order logged. */
    @Query("SELECT * FROM lift_set ORDER BY logged_at, id")
    suspend fun allSets(): List<SetEntity>

    @Query("SELECT * FROM lift_set WHERE id = :id")
    suspend fun set(id: Long): SetEntity?

    /** The sets of session [uid], in the order logged. */
    @Query("SELECT * FROM lift_set WHERE session_uid = :uid ORDER BY logged_at, id")
    suspend fun sets(uid: String): List<SetEntity>

    /** How many sets session [uid] has. */
    @Query("SELECT COUNT(*) FROM lift_set WHERE session_uid = :uid")
    suspend fun countSets(uid: String): Int

    @Query("SELECT * FROM session WHERE finished_at IS NULL AND deleted = 0 LIMIT 1")
    suspend fun active(): SessionEntity?

    @Insert
    suspend fun insert(session: SessionEntity)

    /** The sessions of [programId] finished since [since], oldest first: the days of it done, each as logged. */
    @Query(
        "SELECT * FROM session WHERE program_id = :programId AND finished_at IS NOT NULL " +
            "AND deleted = 0 AND started_at >= :since ORDER BY started_at",
    )
    fun observeProgramSessions(programId: String, since: Long): Flow<List<SessionEntity>>

    @Insert
    suspend fun insert(set: SetEntity)

    @Query("DELETE FROM lift_set WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Update
    suspend fun update(set: SetEntity)

    @Query("UPDATE session SET plan = :plan WHERE uid = :uid")
    suspend fun setPlan(uid: String, plan: String?)

    /** Makes the session [uid] day [day] of the program stored as [programId], its plan as programmed again. */
    @Query("UPDATE session SET program_id = :programId, program_day = :day, plan = NULL WHERE uid = :uid")
    suspend fun setProgramDay(uid: String, programId: String, day: Int)

    /** Sets the timer running in the session [uid], in [encodeTimer]'s shape; null for none. */
    @Query("UPDATE session SET timer = :timer WHERE uid = :uid")
    suspend fun setTimer(uid: String, timer: String?)

    /** Finishes the session [uid]: a finished session keeps no timer. */
    @Query("UPDATE session SET finished_at = :at, title = :title, updated_at = :at, timer = NULL WHERE uid = :uid")
    suspend fun finish(uid: String, title: String, at: Long)

    /** Sets how the session [uid] felt, at [now]: an effort level, 1 to 5, and a note, either null for none. */
    @Query("UPDATE session SET effort = :effort, note = :note, updated_at = :now WHERE uid = :uid")
    suspend fun reflect(uid: String, effort: Int?, note: String?, now: Long)

    /** Keeps the heart rate of the session [uid], at [now], unless it has one: the first found stays. */
    @Query(
        "UPDATE session SET average_bpm = :average, peak_bpm = :peak, heart_rate_samples = :samples, " +
            "updated_at = :now WHERE uid = :uid AND average_bpm IS NULL",
    )
    suspend fun keepHeartRate(uid: String, average: Int, peak: Int, samples: String?, now: Long)

    /** Marks a finished session deleted, keeping the row as a tombstone. */
    @Query("UPDATE session SET deleted = 1, updated_at = :now WHERE uid = :uid")
    suspend fun markDeleted(uid: String, now: Long)

    /** Restores a soft-deleted finished session. */
    @Query("UPDATE session SET deleted = 0, updated_at = :now WHERE uid = :uid")
    suspend fun unmarkDeleted(uid: String, now: Long)

    /** Drops a session discarded before it finished, and its sets with it (the foreign key's cascade). */
    @Query("DELETE FROM session WHERE uid = :uid")
    suspend fun discard(uid: String)

    /** Finishes the session in progress under [title] at [at], or drops it if it has no sets: one or the other. */
    @Transaction
    suspend fun finishActive(title: String, at: Long) {
        val active = active() ?: return
        if (countSets(active.uid) == 0) discard(active.uid) else finish(active.uid, title, at)
    }

    /** Drops the session in progress, its sets going with it. */
    @Transaction
    suspend fun discardActive() {
        val active = active() ?: return
        discard(active.uid)
    }
}

/** What an import adds, all of it or none: one transaction over every table it touches. */
@Dao
interface LiftImportDao {
    @Insert
    suspend fun insertSessions(rows: List<SessionEntity>)

    @Insert
    suspend fun insertSets(rows: List<SetEntity>)

    @Insert
    suspend fun insertExercises(rows: List<CustomExerciseEntity>)

    @Insert
    suspend fun insertPrograms(rows: List<CustomProgramEntity>)

    @Upsert
    suspend fun upsertBands(rows: List<BandEntity>)

    @Upsert
    suspend fun upsertBells(rows: List<BellEntity>)

    @Upsert
    suspend fun upsertBar(bar: BarEntity)

    @Upsert
    suspend fun upsertFollowing(following: FollowingEntity)

    @Upsert
    suspend fun upsertRest(rest: RestEntity)

    /** Adds the rows of [rows] together; a [LiftImportRows.bar], following or rest that is null is left as it is. */
    @Transaction
    suspend fun importAll(rows: LiftImportRows) {
        insertExercises(rows.exercises)
        upsertBands(rows.bands)
        upsertBells(rows.bells)
        insertPrograms(rows.programs)
        insertSessions(rows.sessions)
        insertSets(rows.sets)
        rows.bar?.let { upsertBar(it) }
        rows.following?.let { upsertFollowing(it) }
        rows.rest?.let { upsertRest(it) }
    }
}

/** The rows an import writes, as [LiftImportDao.importAll] takes them. */
data class LiftImportRows(
    val sessions: List<SessionEntity> = emptyList(),
    val sets: List<SetEntity> = emptyList(),
    val exercises: List<CustomExerciseEntity> = emptyList(),
    val programs: List<CustomProgramEntity> = emptyList(),
    val bands: List<BandEntity> = emptyList(),
    val bells: List<BellEntity> = emptyList(),
    val bar: BarEntity? = null,
    val following: FollowingEntity? = null,
    val rest: RestEntity? = null,
)

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
interface CustomProgramDao {
    @Query("SELECT * FROM custom_program ORDER BY name COLLATE NOCASE, uid")
    fun observeAll(): Flow<List<CustomProgramEntity>>

    @Upsert
    suspend fun upsert(program: CustomProgramEntity)

    @Query("DELETE FROM custom_program WHERE uid = :uid")
    suspend fun delete(uid: String)

    /** Stops following [programId] (as stored), if it is the one followed. */
    @Query("DELETE FROM following WHERE program_id = :programId")
    suspend fun unfollow(programId: String)

    /** Deletes the program [uid], stored as [programId], and stops following it if it is followed: both or neither. */
    @Transaction
    suspend fun deleteProgram(uid: String, programId: String) {
        unfollow(programId)
        delete(uid)
    }
}

@Dao
interface BandDao {
    @Query("SELECT * FROM band ORDER BY position, id")
    fun observeAll(): Flow<List<BandEntity>>

    @Upsert
    suspend fun upsert(bands: List<BandEntity>)

    @Query("DELETE FROM band WHERE id = :id")
    suspend fun delete(id: String)

    /** Whether any set was done with the band [id]. */
    @Query("SELECT EXISTS(SELECT 1 FROM lift_set WHERE band = :id)")
    suspend fun inUse(id: String): Boolean

    /** Keeps [archived] (a band, archived) if a set was done with it, else deletes it, as one step. */
    @Transaction
    suspend fun removeOrArchive(archived: BandEntity) {
        if (inUse(archived.id)) upsert(listOf(archived)) else delete(archived.id)
    }
}

@Dao
interface CustomExerciseDao {
    @Query("SELECT * FROM custom_exercise ORDER BY name COLLATE NOCASE, uid")
    fun observeAll(): Flow<List<CustomExerciseEntity>>

    @Upsert
    suspend fun upsert(exercise: CustomExerciseEntity)
}

/** The bells the user owns, their barbell, and how long they rest between sets. */
@Dao
interface BellDao {
    @Query("SELECT * FROM bell ORDER BY kg")
    fun observeAll(): Flow<List<BellEntity>>

    @Upsert
    suspend fun upsert(bell: BellEntity)

    @Query("DELETE FROM bell WHERE kg = :kg")
    suspend fun delete(kg: Double)

    @Query("UPDATE bell SET pair = :pair WHERE kg = :kg")
    suspend fun setPair(kg: Double, pair: Boolean)

    @Query("SELECT * FROM bar WHERE id = 0")
    fun observeBar(): Flow<BarEntity?>

    @Upsert
    suspend fun upsertBar(bar: BarEntity)

    @Query("DELETE FROM bar")
    suspend fun clearBar()

    @Query("SELECT * FROM rest WHERE id = 0")
    fun observeRest(): Flow<RestEntity?>

    @Upsert
    suspend fun upsertRest(rest: RestEntity)
}
