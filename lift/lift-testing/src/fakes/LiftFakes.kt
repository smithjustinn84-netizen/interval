package dev.justinsmith.lift.fakes

import dev.justinsmith.interval.fakes.FixedTimestampFormatter
import dev.justinsmith.interval.fakes.RecordingSharer
import dev.justinsmith.interval.fakes.fakeCorePlatformModule
import dev.justinsmith.interval.platform.IdGenerator
import dev.justinsmith.interval.platform.Sharer
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
import dev.justinsmith.lift.data.LiftImportDao
import dev.justinsmith.lift.data.LiftRepository
import dev.justinsmith.lift.data.RestEntity
import dev.justinsmith.lift.data.SessionDao
import dev.justinsmith.lift.data.SessionEntity
import dev.justinsmith.lift.data.SetEntity
import dev.justinsmith.lift.data.SportDay
import dev.justinsmith.lift.di.LiftStorageScope
import dev.justinsmith.lift.di.liftModules
import dev.justinsmith.lift.domain.EmomCue
import dev.justinsmith.lift.domain.ProgramKey
import dev.justinsmith.lift.platform.Beeper
import dev.justinsmith.lift.platform.SessionKeepAlive
import dev.justinsmith.lift.platform.TimerRun
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource
import kotlin.time.TimeSource

/** [SessionDao] over lists, as the real queries order them. */
private val loggedOrder = compareBy<SetEntity>({ it.loggedAtEpochMillis }, { it.id })

// One function per query, as the DAO has them.
@Suppress("detekt.TooManyFunctions")
class FakeSessionDao : SessionDao {
    val sessions = MutableStateFlow(emptyList<SessionEntity>())
    val sets = MutableStateFlow(emptyList<SetEntity>())
    private var nextId = 1L

    /** While on, every set logged fails as a full disk would. */
    var failWrites = false

    override fun observeSessions(): Flow<List<SessionEntity>> =
        sessions.map { all -> all.filterNot { it.deleted }.sortedByDescending { it.startedAtEpochMillis } }

    // As the query has them: those of sessions not deleted, as logged.
    override fun observeSets(): Flow<List<SetEntity>> = combine(sessions, sets) { sessions, sets ->
        val live = sessions.filterNot { it.deleted }.map { it.uid }.toSet()
        sets.filter { it.sessionUid in live }.sortedWith(loggedOrder)
    }

    override suspend fun allSets() = sets.value.sortedWith(loggedOrder)

    override suspend fun set(id: Long) = sets.value.firstOrNull { it.id == id }

    override suspend fun sets(uid: String) = sets.value.filter { it.sessionUid == uid }.sortedWith(loggedOrder)

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

    // As Room: an id of 0 is generated, any other is kept (a set restored to its place); and as the foreign key, a
    // set needs its session.
    override suspend fun insert(set: SetEntity) = sets.update {
        check(!failWrites) { "disk full" }
        check(sessions.value.any { session -> session.uid == set.sessionUid }) { "FOREIGN KEY constraint failed" }
        val id = if (set.id == 0L) nextId++ else set.id.also { nextId = maxOf(nextId, it + 1) }
        it + set.copy(id = id)
    }

    override suspend fun deleteSet(id: Long) = sets.update { all -> all.filterNot { it.id == id } }

    override suspend fun update(set: SetEntity) = sets.update { all -> all.map { if (it.id == set.id) set else it } }

    override suspend fun setPlan(uid: String, plan: String?) = sessions.update { all ->
        all.map { if (it.uid == uid) it.copy(plan = plan) else it }
    }

    override suspend fun setTimer(uid: String, timer: String?) = sessions.update { all ->
        all.map { if (it.uid == uid) it.copy(timer = timer) else it }
    }

    override suspend fun reflect(uid: String, effort: Int?, note: String?, now: Long) = sessions.update { all ->
        all.map { if (it.uid == uid) it.copy(effort = effort, note = note, updatedAtEpochMillis = now) else it }
    }

    override suspend fun keepHeartRate(uid: String, average: Int, peak: Int, now: Long) = sessions.update { all ->
        all.map {
            if (it.uid == uid && it.averageBpm == null) {
                it.copy(averageBpm = average, peakBpm = peak, updatedAtEpochMillis = now)
            } else {
                it
            }
        }
    }

    override suspend fun finish(uid: String, title: String, at: Long) = sessions.update { all ->
        all.map {
            if (it.uid ==
                uid
            ) {
                it.copy(finishedAtEpochMillis = at, title = title, updatedAtEpochMillis = at, timer = null)
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

    // As the foreign key's cascade: the session's sets go with it.
    override suspend fun discard(uid: String) {
        sessions.update { all -> all.filterNot { it.uid == uid } }
        sets.update { all -> all.filterNot { it.sessionUid == uid } }
    }
}

class FakeBellDao : BellDao {
    val rows = MutableStateFlow(emptyList<BellEntity>())

    override fun observeAll(): Flow<List<BellEntity>> = rows.map { all -> all.sortedBy { it.kg } }

    override suspend fun upsert(bell: BellEntity) = rows.update { all -> all.filterNot { it.kg == bell.kg } + bell }

    override suspend fun delete(kg: Double) = rows.update { all -> all.filterNot { it.kg == kg } }

    override suspend fun setPair(kg: Double, pair: Boolean) =
        rows.update { all -> all.map { if (it.kg == kg) it.copy(pair = pair) else it } }

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
}

/** [CustomProgramDao] over a list; it stops [following] a program deleted, as the real one's query does. */
class FakeCustomProgramDao(private val following: FakeFollowingDao) : CustomProgramDao {
    val rows = MutableStateFlow(emptyList<CustomProgramEntity>())

    override fun observeAll(): Flow<List<CustomProgramEntity>> =
        rows.map { all -> all.sortedWith(compareBy({ it.name.lowercase() }, { it.uid })) }

    override suspend fun upsert(program: CustomProgramEntity) =
        rows.update { all -> all.filterNot { it.uid == program.uid } + program }

    override suspend fun delete(uid: String) = rows.update { all -> all.filterNot { it.uid == uid } }

    override suspend fun unfollow(programId: String) {
        if (following.row.value?.programId == programId) following.row.value = null
    }
}

class FakeCustomExerciseDao : CustomExerciseDao {
    val rows = MutableStateFlow(emptyList<CustomExerciseEntity>())

    override fun observeAll(): Flow<List<CustomExerciseEntity>> =
        rows.map { all -> all.sortedWith(compareBy({ it.name.lowercase() }, { it.uid })) }

    override suspend fun upsert(exercise: CustomExerciseEntity) =
        rows.update { all -> all.filterNot { it.uid == exercise.uid } + exercise }
}

/** [BandDao] over a list; a band is in use when [sessions] holds a set of it, as the real one's query asks. */
class FakeBandDao(private val sessions: FakeSessionDao) : BandDao {
    val rows = MutableStateFlow(emptyList<BandEntity>())

    override fun observeAll(): Flow<List<BandEntity>> = rows.map { all ->
        all.sortedWith(compareBy({ it.position }, { it.id }))
    }

    override suspend fun upsert(bands: List<BandEntity>) =
        rows.update { all -> all.filterNot { row -> bands.any { it.id == row.id } } + bands }

    override suspend fun delete(id: String) = rows.update { all -> all.filterNot { it.id == id } }

    override suspend fun inUse(id: String) = sessions.sets.value.any { it.band == id }
}

/** The rest of the app as Lift sees it: interval workouts and a weekly goal a test sets. */
class FakeIntervalActivity : IntervalActivity {
    override val workouts = MutableStateFlow(emptyList<IntervalWorkout>())
    override val sportDays = MutableStateFlow(emptyList<SportDay>())
    override val weeklyGoal = MutableStateFlow<Int?>(null)
    override val bodyWeightKg = MutableStateFlow<Double?>(null)

    /** The estimate the app would make, or null as with no body weight set; a test sets it. */
    var caloriesPerSecond: Double? = null

    override fun sessionCalories(elapsedSeconds: Int): Int? = caloriesPerSecond?.let { (it * elapsedSeconds).toInt() }
}

/** The beeps an EMOM played, in order. */
class RecordingBeeper : Beeper {
    val cues = mutableListOf<EmomCue>()

    override fun beep(cue: EmomCue) {
        cues += cue
    }
}

/** A [LiftImportDao] over the other fakes: its transaction is [LiftImportDao.importAll]'s own, with no rollback. */
class FakeLiftImportDao(
    private val sessions: FakeSessionDao,
    private val bells: FakeBellDao,
    private val following: FakeFollowingDao,
    private val programs: FakeCustomProgramDao,
    private val exercises: FakeCustomExerciseDao,
    private val bands: FakeBandDao,
) : LiftImportDao {
    override suspend fun insertSessions(rows: List<SessionEntity>) = rows.forEach { sessions.insert(it) }

    override suspend fun insertSets(rows: List<SetEntity>) = rows.forEach { sessions.insert(it) }

    override suspend fun insertExercises(rows: List<CustomExerciseEntity>) = rows.forEach { exercises.upsert(it) }

    override suspend fun insertPrograms(rows: List<CustomProgramEntity>) = rows.forEach { programs.upsert(it) }

    override suspend fun upsertBands(rows: List<BandEntity>) = bands.upsert(rows)

    override suspend fun upsertBells(rows: List<BellEntity>) = rows.forEach { bells.upsert(it) }

    override suspend fun upsertBar(bar: BarEntity) = bells.upsertBar(bar)

    override suspend fun upsertFollowing(following: FollowingEntity) = this.following.upsert(following)

    override suspend fun upsertRest(rest: RestEntity) = bells.upsertRest(rest)
}

/** Lift's storage over the fakes: no database, and writes run at once on the caller's thread. */
class FakeLiftStorage {
    val sessions = FakeSessionDao()
    val bells = FakeBellDao()
    val following = FakeFollowingDao()
    val programs = FakeCustomProgramDao(following)
    val exercises = FakeCustomExerciseDao()
    val bands = FakeBandDao(sessions)
    val imports = FakeLiftImportDao(sessions, bells, following, programs, exercises, bands)

    /** A repository over this storage, its writes landed by the time a call returns; what it makes is under [ids]. */
    fun repository(ids: IdGenerator = IdGenerator.Random, now: WallClock = WallClock { 0L }): LiftRepository =
        LiftRepository(
            sessions,
            bells,
            following,
            programs,
            exercises,
            bands,
            imports,
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            now,
            ids,
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

/** Records the runs the platform was asked to keep, in order, and how many times it was let go. */
class RecordingKeepAlive : SessionKeepAlive {
    val runs = mutableListOf<TimerRun>()
    var stops = 0

    override fun start(run: TimerRun) {
        runs += run
    }

    override fun stop() {
        stops++
    }
}

/** Stands in for `liftPlatformModule`. Tests never load the real one: it writes a database file. */
fun fakeLiftPlatformModule(
    beeper: Beeper = RecordingBeeper(),
    keepAlive: SessionKeepAlive = RecordingKeepAlive(),
): Module = module {
    single<Beeper> { beeper }
    single<SessionKeepAlive> { keepAlive }
}

/**
 * A Lift test's time: the wall clock, which [get] reads, and the monotonic time the session's timers count,
 * [elapsed]. [set] moves both by the same step, so a test that moves the wall clock moves the timers too; a step
 * back moves the wall clock alone, as a clock change would.
 */
class LiftTestClock(start: Long = 0L) {
    @Volatile
    private var millis = start
    val elapsed = TestTimeSource()

    fun get(): Long = millis

    fun set(to: Long) {
        val step = to - millis
        millis = to
        if (step > 0) elapsed += step.milliseconds
    }
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
    single<LiftImportDao> { storage.imports }
}

/**
 * Lift's part of the container over fakes, with what core and the app would supply: a clock reading [now], the
 * timers' [timeSource], the [FixedTimestampFormatter], [sharer] and [intervals]. [features] are the feature modules
 * whose ViewModels the test's Routes resolve: `lift-nav`'s `liftFeatureModules` (this module sees no feature).
 */
fun liftTestKoin(
    storage: FakeLiftStorage = FakeLiftStorage(),
    platformModule: Module = fakeLiftPlatformModule(),
    now: () -> Long = { 0L },
    timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
    intervals: IntervalActivity = FakeIntervalActivity(),
    sharer: Sharer = RecordingSharer(),
    features: List<Module> = emptyList(),
): KoinApplication = koinApplication {
    val app = module {
        single<IntervalActivity> { intervals }
    }
    modules(
        listOf(fakeCorePlatformModule(WallClock(now), sharer = sharer, timeSource = timeSource)) +
            liftModules(platformModule, fakeLiftStorageModule(storage)) +
            features +
            app,
    )
}
