package dev.justinsmith.lift.fakes

import dev.justinsmith.interval.fakes.FixedTimestampFormatter
import dev.justinsmith.interval.fakes.RecordingSharer
import dev.justinsmith.interval.platform.Sharer
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.lift.data.BandDao
import dev.justinsmith.lift.data.BandEntity
import dev.justinsmith.lift.data.BarEntity
import dev.justinsmith.lift.data.BellDao
import dev.justinsmith.lift.data.BellEntity
import dev.justinsmith.lift.data.CustomExerciseDao
import dev.justinsmith.lift.data.CustomExerciseEntity
import dev.justinsmith.lift.data.CustomProgramDao
import dev.justinsmith.lift.data.CustomProgramEntity
import dev.justinsmith.lift.data.FollowingDao
import dev.justinsmith.lift.data.FollowingEntity
import dev.justinsmith.lift.data.IntervalActivity
import dev.justinsmith.lift.data.IntervalWorkout
import dev.justinsmith.lift.data.LiftRepository
import dev.justinsmith.lift.data.RestEntity
import dev.justinsmith.lift.data.SessionDao
import dev.justinsmith.lift.data.SessionEntity
import dev.justinsmith.lift.data.SetEntity
import dev.justinsmith.lift.di.LiftStorageScope
import dev.justinsmith.lift.di.liftModules
import dev.justinsmith.lift.domain.EmomCue
import dev.justinsmith.lift.domain.ProgramKey
import dev.justinsmith.lift.platform.Beeper
import dev.justinsmith.lift.platform.RestAlerts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/** [SessionDao] over lists, as the real queries order them. */
private val loggedOrder = compareBy<SetEntity>({ it.loggedAtEpochMillis }, { it.id })

// One function per query, as the DAO has them.
@Suppress("detekt.TooManyFunctions")
class FakeSessionDao : SessionDao {
    val sessions = MutableStateFlow(emptyList<SessionEntity>())
    val sets = MutableStateFlow(emptyList<SetEntity>())
    private var nextId = 1L

    override fun observeSessions(): Flow<List<SessionEntity>> =
        sessions.map { all -> all.filterNot { it.deleted }.sortedByDescending { it.startedAtEpochMillis } }

    // As the queries order them: as logged.
    override fun observeSets(): Flow<List<SetEntity>> = sets.map { it.sortedWith(loggedOrder) }

    override suspend fun allSets() = sets.value.sortedWith(loggedOrder)

    override suspend fun set(id: Long) = sets.value.firstOrNull { it.id == id }

    override suspend fun countSets(uid: String) = sets.value.count { it.sessionUid == uid }

    override suspend fun active() = sessions.value.firstOrNull { it.finishedAtEpochMillis == null && !it.deleted }

    override suspend fun insert(session: SessionEntity) = sessions.update { it + session }

    override fun observeProgramSessions(programId: String, since: Long): Flow<List<SessionEntity>> =
        sessions.map { all ->
            all.filter {
                it.programId == programId && it.finishedAtEpochMillis != null && !it.deleted &&
                    it.startedAtEpochMillis >= since
            }.sortedBy { it.startedAtEpochMillis }
        }

    // As Room: an id of 0 is generated, any other is kept (a set restored to its place).
    override suspend fun insert(set: SetEntity) = sets.update {
        val id = if (set.id == 0L) nextId++ else set.id.also { nextId = maxOf(nextId, it + 1) }
        it + set.copy(id = id)
    }

    override suspend fun deleteSet(id: Long) = sets.update { all -> all.filterNot { it.id == id } }

    override suspend fun update(set: SetEntity) = sets.update { all -> all.map { if (it.id == set.id) set else it } }

    override suspend fun setPlan(uid: String, plan: String?) = sessions.update { all ->
        all.map { if (it.uid == uid) it.copy(plan = plan) else it }
    }

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

    override suspend fun unmarkDeleted(uid: String, now: Long) = sessions.update { all ->
        all.map { if (it.uid == uid) it.copy(deleted = false, updatedAtEpochMillis = now) else it }
    }

    override suspend fun discard(uid: String) = sessions.update { all -> all.filterNot { it.uid == uid } }

    override suspend fun discardSets(uid: String) = sets.update { all -> all.filterNot { it.sessionUid == uid } }
}

class FakeBellDao : BellDao {
    val rows = MutableStateFlow(emptyList<BellEntity>())

    override fun observeAll(): Flow<List<BellEntity>> = rows.map { all -> all.sortedBy { it.kg } }

    override suspend fun upsert(bell: BellEntity) = rows.update { all -> all.filterNot { it.kg == bell.kg } + bell }

    override suspend fun delete(kg: Double) = rows.update { all -> all.filterNot { it.kg == kg } }

    val bar = MutableStateFlow<BarEntity?>(null)

    override fun observeBar(): Flow<BarEntity?> = bar

    override suspend fun upsertBar(bar: BarEntity) {
        this.bar.value = bar
    }

    override suspend fun clearBar() {
        bar.value = null
    }

    val rest = MutableStateFlow<RestEntity?>(null)

    override fun observeRest(): Flow<RestEntity?> = rest

    override suspend fun upsertRest(rest: RestEntity) {
        this.rest.value = rest
    }
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

class FakeBandDao : BandDao {
    val rows = MutableStateFlow(emptyList<BandEntity>())

    override fun observeAll(): Flow<List<BandEntity>> = rows.map { all ->
        all.sortedWith(compareBy({ it.position }, { it.id }))
    }

    override suspend fun upsert(bands: List<BandEntity>) =
        rows.update { all -> all.filterNot { row -> bands.any { it.id == row.id } } + bands }

    override suspend fun delete(id: String) = rows.update { all -> all.filterNot { it.id == id } }
}

/** The rest of the app as Lift sees it: interval workouts and a weekly goal a test sets. */
class FakeIntervalActivity : IntervalActivity {
    override val workouts = MutableStateFlow(emptyList<IntervalWorkout>())
    override val weeklyGoal = MutableStateFlow<Int?>(null)
}

/** The beeps an EMOM played, in order. */
class RecordingBeeper : Beeper {
    val cues = mutableListOf<EmomCue>()

    override fun beep(cue: EmomCue) {
        cues += cue
    }
}

/** Lift's storage over the fakes: no database, and writes run at once on the caller's thread. */
class FakeLiftStorage {
    val sessions = FakeSessionDao()
    val bells = FakeBellDao()
    val following = FakeFollowingDao()
    val programs = FakeCustomProgramDao()
    val exercises = FakeCustomExerciseDao()
    val bands = FakeBandDao()

    /** A repository over this storage, its writes landed by the time a call returns. */
    fun repository(now: WallClock = WallClock { 0L }): LiftRepository = LiftRepository(
        sessions,
        bells,
        following,
        programs,
        exercises,
        bands,
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

/** Records the rests the platform was asked to keep: each end it was given, and how many times it was stopped. */
class RecordingRestAlerts : RestAlerts {
    val ends = mutableListOf<Long>()
    var stops = 0

    override fun start(endAtEpochMillis: Long) {
        ends += endAtEpochMillis
    }

    override fun stop() {
        stops++
    }
}

/** Stands in for `rememberLiftPlatformModule()`. Tests never load the real one: it writes a database file. */
fun fakeLiftPlatformModule(beeper: Beeper = RecordingBeeper(), alerts: RestAlerts = RecordingRestAlerts()): Module =
    module {
        single<Beeper> { beeper }
        single<RestAlerts> { alerts }
    }

fun fakeLiftStorageModule(storage: FakeLiftStorage = FakeLiftStorage()): Module = module {
    single<CoroutineScope>(LiftStorageScope) { CoroutineScope(SupervisorJob() + Dispatchers.Unconfined) }.withOptions {
        onClose { it?.cancel() }
    }
    single<SessionDao> { storage.sessions }
    single<BellDao> { storage.bells }
    single<FollowingDao> { storage.following }
    single<CustomProgramDao> { storage.programs }
    single<CustomExerciseDao> { storage.exercises }
    single<BandDao> { storage.bands }
}

/**
 * Lift's part of the container over fakes, with what the app would supply: a clock reading [now], the
 * [FixedTimestampFormatter] and [intervals].
 */
fun liftTestKoin(
    storage: FakeLiftStorage = FakeLiftStorage(),
    platformModule: Module = fakeLiftPlatformModule(),
    now: () -> Long = { 0L },
    intervals: IntervalActivity = FakeIntervalActivity(),
    sharer: Sharer = RecordingSharer(),
): KoinApplication = koinApplication {
    val app = module {
        single { WallClock(now) }
        single<TimestampFormatter> { FixedTimestampFormatter }
        single<IntervalActivity> { intervals }
        single { sharer }
    }
    modules(liftModules(platformModule, fakeLiftStorageModule(storage)) + app)
}
