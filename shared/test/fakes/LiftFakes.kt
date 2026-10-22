package dev.justinsmith.interval.fakes

import dev.justinsmith.bells.data.BellDao
import dev.justinsmith.bells.data.BellEntity
import dev.justinsmith.bells.data.BellsRepository
import dev.justinsmith.bells.data.CustomExerciseDao
import dev.justinsmith.bells.data.CustomExerciseEntity
import dev.justinsmith.bells.data.CustomProgramDao
import dev.justinsmith.bells.data.CustomProgramEntity
import dev.justinsmith.bells.data.FollowingDao
import dev.justinsmith.bells.data.FollowingEntity
import dev.justinsmith.bells.data.SessionDao
import dev.justinsmith.bells.data.SessionEntity
import dev.justinsmith.bells.data.SetEntity
import dev.justinsmith.bells.di.BellsStorageScope
import dev.justinsmith.bells.domain.EmomCue
import dev.justinsmith.bells.domain.ProgramKey
import dev.justinsmith.bells.platform.Beeper
import dev.justinsmith.interval.platform.WallClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module

// Lift's storage over lists, as Bells' own tests fake it: Amper doesn't share test sources between modules.

/** A [SessionDao] over lists, as the real queries order them. */
class FakeSessionDao : SessionDao {
    val sessions = MutableStateFlow(emptyList<SessionEntity>())
    val sets = MutableStateFlow(emptyList<SetEntity>())
    private var nextId = 1L

    override fun observeSessions(): Flow<List<SessionEntity>> =
        sessions.map { all -> all.filterNot { it.deleted }.sortedByDescending { it.startedAtEpochMillis } }

    override fun observeSets(): Flow<List<SetEntity>> = sets

    override suspend fun allSets() = sets.value

    override suspend fun active() = sessions.value.firstOrNull { it.finishedAtEpochMillis == null && !it.deleted }

    override suspend fun insert(session: SessionEntity) = sessions.update { it + session }

    override fun observeProgramDays(programId: String, since: Long): Flow<Int> = sessions.map { all ->
        all.count {
            it.programId == programId && it.finishedAtEpochMillis != null && !it.deleted &&
                it.startedAtEpochMillis >= since
        }
    }

    override suspend fun insert(set: SetEntity) = sets.update { it + set.copy(id = nextId++) }

    override suspend fun deleteSet(id: Long) = sets.update { all -> all.filterNot { it.id == id } }

    override suspend fun finish(uid: String, title: String, at: Long) = sessions.update { all ->
        all.map {
            if (it.uid ==
                uid
            ) {
                it.copy(finishedAtEpochMillis = at, title = title, updatedAtEpochMillis = at)
            } else {
                it
            }
        }
    }

    override suspend fun markDeleted(uid: String, now: Long) = sessions.update { all ->
        all.map { if (it.uid == uid) it.copy(deleted = true, updatedAtEpochMillis = now) else it }
    }

    override suspend fun discard(uid: String) = sessions.update { all -> all.filterNot { it.uid == uid } }

    override suspend fun discardSets(uid: String) = sets.update { all -> all.filterNot { it.sessionUid == uid } }
}

class FakeBellDao : BellDao {
    val rows = MutableStateFlow(emptyList<BellEntity>())

    override fun observeAll(): Flow<List<BellEntity>> = rows.map { all -> all.sortedBy { it.kg } }

    override suspend fun upsert(bell: BellEntity) = rows.update { all -> all.filterNot { it.kg == bell.kg } + bell }

    override suspend fun delete(kg: Double) = rows.update { all -> all.filterNot { it.kg == kg } }
}

class FakeFollowingDao : FollowingDao {
    val row = MutableStateFlow<FollowingEntity?>(null)

    override fun observe(): Flow<FollowingEntity?> = row

    override suspend fun upsert(following: FollowingEntity) {
        row.value = following
    }

    override suspend fun clear() {
        row.value = null
    }

    override suspend fun clearIfFollowing(programId: String) {
        if (row.value?.programId == programId) row.value = null
    }
}

class FakeCustomProgramDao : CustomProgramDao {
    val rows = MutableStateFlow(emptyList<CustomProgramEntity>())

    override fun observeAll(): Flow<List<CustomProgramEntity>> =
        rows.map { all -> all.sortedWith(compareBy({ it.name.lowercase() }, { it.uid })) }

    override suspend fun upsert(program: CustomProgramEntity) =
        rows.update { all -> all.filterNot { it.uid == program.uid } + program }

    override suspend fun delete(uid: String) = rows.update { all -> all.filterNot { it.uid == uid } }
}

class FakeCustomExerciseDao : CustomExerciseDao {
    val rows = MutableStateFlow(emptyList<CustomExerciseEntity>())

    override fun observeAll(): Flow<List<CustomExerciseEntity>> =
        rows.map { all -> all.sortedWith(compareBy({ it.name.lowercase() }, { it.uid })) }

    override suspend fun upsert(exercise: CustomExerciseEntity) =
        rows.update { all -> all.filterNot { it.uid == exercise.uid } + exercise }
}

/** Lift's storage in memory: no database, and writes run at once on the caller's thread. */
class FakeLiftStorage {
    val sessions = FakeSessionDao()
    val bells = FakeBellDao()
    val following = FakeFollowingDao()
    val programs = FakeCustomProgramDao()
    val exercises = FakeCustomExerciseDao()

    /** A repository over this storage, its writes landed by the time a call returns. */
    fun repository(now: WallClock = WallClock { 0L }): BellsRepository = BellsRepository(
        sessions,
        bells,
        following,
        programs,
        exercises,
        CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        now,
    )

    /**
     * Stores a finished session of [sets] (bell and reps of two-hand swings), as if logged in Lift: a day of
     * [program], or a free one.
     */
    fun finished(
        uid: String,
        title: String,
        startedAt: Long,
        finishedAt: Long,
        vararg sets: Pair<Double, Int>,
        program: ProgramKey? = null,
    ) {
        sessions.sessions.update {
            it + SessionEntity(
                uid,
                startedAt,
                finishedAt,
                title,
                updatedAtEpochMillis = finishedAt,
                programId = program?.stored,
            )
        }
        sessions.sets.update { all ->
            all + sets.mapIndexed { i, (kg, reps) ->
                SetEntity(0, uid, "TwoHandSwing", kg, reps, side = null, loggedAtEpochMillis = startedAt + i)
            }
        }
    }
}

/** The beeps an EMOM played, in order. */
class RecordingBeeper : Beeper {
    val cues = mutableListOf<EmomCue>()

    override fun beep(cue: EmomCue) {
        cues += cue
    }
}

/** Stands in for `rememberBellsPlatformModule()`, which opens a database file. */
fun fakeLiftPlatformModule(beeper: Beeper = RecordingBeeper()): Module = module {
    single<Beeper> { beeper }
}

/** Stands in for `bellsStorageModule`. */
fun fakeLiftStorageModule(storage: FakeLiftStorage = FakeLiftStorage()): Module = module {
    single<CoroutineScope>(BellsStorageScope) { CoroutineScope(SupervisorJob() + Dispatchers.Unconfined) }.withOptions {
        onClose { it?.cancel() }
    }
    single<SessionDao> { storage.sessions }
    single<BellDao> { storage.bells }
    single<FollowingDao> { storage.following }
    single<CustomProgramDao> { storage.programs }
    single<CustomExerciseDao> { storage.exercises }
}
