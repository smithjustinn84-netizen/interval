package dev.justinsmith.bells.data

import co.touchlab.kermit.Logger
import dev.justinsmith.bells.domain.Exercise
import dev.justinsmith.bells.domain.LiftSession
import dev.justinsmith.bells.domain.SetLog
import dev.justinsmith.bells.domain.Side
import dev.justinsmith.core.sync.AppId
import dev.justinsmith.core.sync.FeedSnapshot
import dev.justinsmith.core.sync.SessionDetail
import dev.justinsmith.core.sync.SessionRecord
import dev.justinsmith.core.sync.SetRecord
import dev.justinsmith.core.sync.mirror
import dev.justinsmith.interval.platform.WallClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** One of Interval's workouts, as Bells shows it: read-only, counted in the week. */
data class IntervalWorkout(
    val uid: String,
    val finishedAtEpochMillis: Long,
    val elapsedSeconds: Int,
    val title: String,
)

/**
 * Bells' saved state: its sessions and their sets, the bells the user owns, and Interval's workouts. The
 * one way into storage. Everything is read into memory before [ready] turns true; writes run one at a time
 * on [scope], in the order they were made.
 */
@Suppress("detekt.TooManyFunctions") // The one way into storage, every write side by side.
class BellsRepository(
    private val sessionDao: SessionDao,
    private val bellDao: BellDao,
    private val siblingDao: SiblingDao,
    private val scope: CoroutineScope,
    private val clock: WallClock,
) {
    private val log = Logger.withTag("BellsRepository")
    private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _sessions = MutableStateFlow(emptyList<LiftSession>())

    /** Finished sessions, newest first. */
    val sessions: StateFlow<List<LiftSession>> = _sessions.asStateFlow()

    private val _active = MutableStateFlow<LiftSession?>(null)

    /** The session in progress, if one is. */
    val active: StateFlow<LiftSession?> = _active.asStateFlow()

    private val _bells = MutableStateFlow(emptyList<Double>())

    /** The bells the user owns, lightest first. */
    val bells: StateFlow<List<Double>> = _bells.asStateFlow()

    private val _interval = MutableStateFlow(emptyList<IntervalWorkout>())

    /** Interval's workouts, newest first. */
    val interval: StateFlow<List<IntervalWorkout>> = _interval.asStateFlow()

    init {
        scope.launch { for (write in writes) runLogged(write) }
        scope.launch {
            val sessionsLoaded = CompletableDeferred<Unit>()
            val bellsLoaded = CompletableDeferred<Unit>()
            val intervalLoaded = CompletableDeferred<Unit>()
            launch {
                combine(sessionDao.observeSessions(), sessionDao.observeSets()) { sessions, sets ->
                    val bySession = sets.mapNotNull { it.toDomain()?.let { set -> it.sessionUid to set } }
                        .groupBy({ it.first }, { it.second })
                    sessions.map { it.toDomain(bySession[it.uid].orEmpty()) }
                }.orEmpty("sessions").collect { all ->
                    _sessions.value = all.filter { it.finishedAtEpochMillis != null }
                    _active.value = all.firstOrNull { it.finishedAtEpochMillis == null }
                    sessionsLoaded.complete(Unit)
                }
            }
            launch {
                bellDao.observeAll().orEmpty("bells").collect { rows ->
                    _bells.value = rows.map { it.kg }
                    bellsLoaded.complete(Unit)
                }
            }
            launch {
                siblingDao.observeAll().orEmpty("Interval's workouts").collect { rows ->
                    _interval.value =
                        rows.map { IntervalWorkout(it.uid, it.finishedAtEpochMillis, it.elapsedSeconds, it.title) }
                    intervalLoaded.complete(Unit)
                }
            }
            sessionsLoaded.await()
            bellsLoaded.await()
            intervalLoaded.await()
            _ready.value = true
        }
    }

    /** Starts a session, unless one is in progress already. */
    @OptIn(ExperimentalUuidApi::class)
    fun startSession() {
        persist {
            if (sessionDao.active() == null) {
                val now = clock.nowMillis()
                sessionDao.insert(
                    SessionEntity(Uuid.random().toHexString(), now, null, title = "", updatedAtEpochMillis = now),
                )
            }
        }
    }

    /** Logs a set to the session in progress. */
    fun logSet(exercise: Exercise, bellKg: Double, reps: Int, side: Side?) {
        persist {
            val active = sessionDao.active() ?: return@persist
            val sided = side.takeIf { exercise.perSide }
            sessionDao.insert(SetEntity(0, active.uid, exercise.name, bellKg, reps, sided?.name, clock.nowMillis()))
        }
    }

    fun removeSet(id: Long) {
        persist { sessionDao.deleteSet(id) }
    }

    /** Finishes the session in progress under [title]; one with no sets is dropped instead. */
    fun finishSession(title: String) {
        persist {
            val active = sessionDao.active() ?: return@persist
            if (sessionDao.allSets().none { it.sessionUid == active.uid }) {
                sessionDao.discard(active.uid)
            } else {
                sessionDao.finish(active.uid, title, clock.nowMillis())
            }
        }
    }

    /** Drops the session in progress and its sets. It was never shared, so nothing else needs telling. */
    fun discardSession() {
        persist {
            val active = sessionDao.active() ?: return@persist
            sessionDao.discardSets(active.uid)
            sessionDao.discard(active.uid)
        }
    }

    /** Deletes a finished session, leaving a tombstone so Interval drops its copy too. */
    fun deleteSession(uid: String) {
        val now = clock.nowMillis()
        persist { sessionDao.markDeleted(uid, now) }
    }

    fun setOwned(kg: Double, owned: Boolean) {
        persist { if (owned) bellDao.upsert(BellEntity(kg)) else bellDao.delete(kg) }
    }

    /** Every finished session, deleted ones as tombstones, as Interval is sent them. */
    suspend fun sharedSessions(): List<SessionRecord> {
        val done = CompletableDeferred<List<SessionRecord>>()
        persist {
            runCatching {
                val sets = sessionDao.allSets().groupBy { it.sessionUid }
                sessionDao.allSessions().filter { it.finishedAtEpochMillis != null }
                    .map { it.toRecord(sets[it.uid].orEmpty()) }
            }.onSuccess { done.complete(it) }.onFailure { done.completeExceptionally(it) }
        }
        return done.await()
    }

    /** Brings the stored copy of Interval's workouts in step with [snapshot]. */
    fun importSibling(snapshot: FeedSnapshot) {
        persist {
            val local = siblingDao.all().associate { it.uid to it.updatedAtEpochMillis }
            val plan = mirror(local, snapshot, expected = AppId.Bells.sibling)
            plan.deletes.forEach { siblingDao.delete(it) }
            plan.upserts.forEach {
                siblingDao.upsert(
                    SiblingEntity(
                        it.uid,
                        it.source.name,
                        it.finishedAtEpochMillis,
                        it.elapsedSeconds,
                        it.title,
                        it.updatedAtEpochMillis,
                    ),
                )
            }
        }
    }

    private fun <T> Flow<List<T>>.orEmpty(what: String): Flow<List<T>> = catch { e ->
        log.e(e) { "Couldn't read $what" }
        emit(emptyList())
    }

    private fun persist(write: suspend () -> Unit) {
        writes.trySend(write)
    }

    // A failed write is logged, never thrown: losing one save must not crash a session.
    @Suppress("detekt.TooGenericExceptionCaught")
    private suspend fun runLogged(write: suspend () -> Unit) {
        try {
            write()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.e(e) { "Couldn't save" }
        }
    }
}

/** A set of an exercise this build doesn't know is left out. */
private fun SetEntity.toDomain(): SetLog? {
    val lift = Exercise.named(exercise) ?: return null
    return SetLog(
        id,
        lift,
        bellKg,
        reps,
        side?.let { s ->
            Side.entries.firstOrNull { it.name == s }
        },
        loggedAtEpochMillis,
    )
}

private fun SessionEntity.toDomain(sets: List<SetLog>) =
    LiftSession(uid, startedAtEpochMillis, finishedAtEpochMillis, title, sets)

private fun SessionEntity.toRecord(sets: List<SetEntity>): SessionRecord {
    val finished = finishedAtEpochMillis ?: startedAtEpochMillis
    return SessionRecord(
        uid = uid,
        source = AppId.Bells,
        finishedAtEpochMillis = finished,
        elapsedSeconds = ((finished - startedAtEpochMillis) / MILLIS_PER_SECOND).toInt(),
        activity = KETTLEBELL_ACTIVITY,
        title = title,
        detail = SessionDetail.Kettlebell(
            sets = sets.map { SetRecord(it.exercise, it.bellKg, it.reps, it.side) },
            tonnageKg = sets.sumOf { it.bellKg * it.reps },
        ).toJson(),
        updatedAtEpochMillis = updatedAtEpochMillis,
        deleted = deleted,
    )
}

/** Interval's activity for a session, so it estimates calories at a kettlebell's effort. */
private const val KETTLEBELL_ACTIVITY = "Kettlebell"
private const val MILLIS_PER_SECOND = 1000L
