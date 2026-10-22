@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.bells.data

import co.touchlab.kermit.Logger
import dev.justinsmith.bells.domain.BandLevel
import dev.justinsmith.bells.domain.CustomExercise
import dev.justinsmith.bells.domain.Equipment
import dev.justinsmith.bells.domain.Exercise
import dev.justinsmith.bells.domain.ExerciseDraft
import dev.justinsmith.bells.domain.Focus
import dev.justinsmith.bells.domain.LiftSession
import dev.justinsmith.bells.domain.Measure
import dev.justinsmith.bells.domain.Program
import dev.justinsmith.bells.domain.ProgramDay
import dev.justinsmith.bells.domain.ProgramDraft
import dev.justinsmith.bells.domain.ProgramKey
import dev.justinsmith.bells.domain.SetLog
import dev.justinsmith.bells.domain.Side
import dev.justinsmith.bells.domain.UnknownExercise
import dev.justinsmith.bells.domain.builtInPrograms
import dev.justinsmith.interval.platform.WallClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Bells' saved state: its sessions and their sets, the bells the user owns, the programs they made and the
 * one followed. The one way into storage. Everything is read into memory before [ready] turns true; writes
 * run one at a time on [scope], in the order they were made.
 */
@Suppress("detekt.TooManyFunctions") // The one way into storage, every write side by side.
class BellsRepository(
    private val sessionDao: SessionDao,
    private val bellDao: BellDao,
    private val followingDao: FollowingDao,
    private val customProgramDao: CustomProgramDao,
    private val customExerciseDao: CustomExerciseDao,
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

    private val _customExercises = MutableStateFlow(emptyList<CustomExercise>())

    /** The exercises the user added, archived ones too, by name. */
    val customExercises: StateFlow<List<CustomExercise>> = _customExercises.asStateFlow()

    private val _customPrograms = MutableStateFlow(emptyList<Program>())

    /** The programs the user made, by name. */
    val customPrograms: StateFlow<List<Program>> = _customPrograms.asStateFlow()

    private val _programDay = MutableStateFlow<ProgramDay?>(null)
    private val _following = MutableStateFlow<Program?>(null)

    /** The program the user follows, if any, as it stands: a change to one they made shows at once. */
    val following: StateFlow<Program?> = _following.asStateFlow()

    /**
     * The day of [following] to do next, counted by the sessions of it finished; null when none is followed or
     * it is over.
     */
    val programDay: StateFlow<ProgramDay?> = _programDay.asStateFlow()

    init {
        scope.launch { for (write in writes) runLogged(write) }
        scope.launch {
            val sessionsLoaded = CompletableDeferred<Unit>()
            val bellsLoaded = CompletableDeferred<Unit>()
            val exercises = customExerciseDao.observeAll().orEmpty("exercises").map { rows ->
                rows.mapNotNull { it.toDomain() }
            }
            launch {
                // The user's exercises come with the sets, which name them.
                combine(sessionDao.observeSessions(), sessionDao.observeSets(), exercises) { sessions, sets, made ->
                    val bySession = sets.groupBy({ it.sessionUid }, { it.toDomain(made) })
                    made to sessions.map { it.toDomain(bySession[it.uid].orEmpty()) }
                }.catch { e ->
                    log.e(e) { "Couldn't read sessions" }
                    emit(emptyList<CustomExercise>() to emptyList())
                }.collect { (made, all) ->
                    _customExercises.value = made
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
            val customLoaded = CompletableDeferred<Unit>()
            val custom = customProgramDao.observeAll().orEmpty("programs").map { rows ->
                rows.mapNotNull { it.toProgram() }
            }
            launch {
                custom.collect {
                    _customPrograms.value = it
                    customLoaded.complete(Unit)
                }
            }
            val followingLoaded = CompletableDeferred<Unit>()
            launch {
                combine(followingDao.observe(), custom) { row, made ->
                    row?.let { r -> resolve(r.programId, made)?.let { it to r.startedAtEpochMillis } }
                }.flatMapLatest { followed ->
                    if (followed == null) {
                        flowOf(null to null)
                    } else {
                        val (program, since) = followed
                        sessionDao.observeProgramDays(program.key.stored, since).map { done ->
                            program to program.dayAfter(done)
                        }
                    }
                }.catch { e ->
                    log.e(e) { "Couldn't read the program" }
                    emit(null to null)
                }.collect { (program, day) ->
                    _following.value = program
                    _programDay.value = day
                    followingLoaded.complete(Unit)
                }
            }
            followingLoaded.await()
            customLoaded.await()
            sessionsLoaded.await()
            bellsLoaded.await()
            _ready.value = true
        }
    }

    /** Starts a session, a day of [program] or a free one, unless one is in progress already. */
    @OptIn(ExperimentalUuidApi::class)
    fun startSession(program: ProgramKey? = null) {
        persist {
            if (sessionDao.active() == null) {
                val now = clock.nowMillis()
                sessionDao.insert(
                    SessionEntity(
                        Uuid.random().toHexString(),
                        now,
                        null,
                        title = "",
                        updatedAtEpochMillis = now,
                        programId = program?.stored,
                    ),
                )
            }
        }
    }

    /** Follows [program] from its first day, in place of any other. */
    fun follow(program: ProgramKey) {
        val now = clock.nowMillis()
        persist { followingDao.upsert(FollowingEntity(programId = program.stored, startedAtEpochMillis = now)) }
    }

    /**
     * Saves [draft] as the program [uid], or as a new one with null; returns its uid. A program followed
     * keeps its place: the days done still count, against the program as it now is.
     */
    @OptIn(ExperimentalUuidApi::class)
    fun saveProgram(uid: String?, draft: ProgramDraft): String {
        val id = uid ?: Uuid.random().toHexString()
        val now = clock.nowMillis()
        val row = CustomProgramEntity(
            id,
            draft.name.trim(),
            draft.daysPerWeek,
            draft.weeks,
            encodeDays(draft.days),
            updatedAtEpochMillis = now,
        )
        persist { customProgramDao.upsert(row) }
        return id
    }

    /** Deletes the program [uid] made, and stops following it if it is the one followed. */
    fun deleteProgram(uid: String) {
        persist {
            followingDao.clearIfFollowing(ProgramKey.Custom(uid).stored)
            customProgramDao.delete(uid)
        }
    }

    fun stopFollowing() {
        persist { followingDao.clear() }
    }

    /**
     * Logs a set to the session in progress, with two bells when [double] and [exercise] can be. A held
     * exercise keeps its [seconds], a band one its [band]; each is dropped from any other.
     */
    fun logSet(
        exercise: Exercise,
        bellKg: Double,
        reps: Int,
        side: Side?,
        double: Boolean = false,
        seconds: Int? = null,
        band: BandLevel? = null,
    ) {
        persist {
            val active = sessionDao.active() ?: return@persist
            val doubled = exercise.doubled(double)
            sessionDao.insert(
                SetEntity(
                    0,
                    active.uid,
                    exercise.key,
                    bellKg,
                    reps,
                    side.takeIf { exercise.sided(doubled) }?.name,
                    clock.nowMillis(),
                    doubled,
                    equipment = exercise.equipment.name,
                    seconds = seconds.takeIf { exercise.measure == Measure.Time },
                    band = band.takeIf { exercise.equipment == Equipment.Band }?.name,
                ),
            )
        }
    }

    fun removeSet(id: Long) {
        persist { sessionDao.deleteSet(id) }
    }

    /** Puts back [set], just removed, into the session in progress, in its place among the others. */
    fun restoreSet(set: SetLog) {
        persist {
            val active = sessionDao.active() ?: return@persist
            sessionDao.insert(
                SetEntity(
                    set.id,
                    active.uid,
                    set.exercise.key,
                    set.bellKg,
                    set.reps,
                    set.side?.name,
                    set.loggedAtEpochMillis,
                    set.double,
                    set.equipment.name,
                    set.seconds,
                    set.band?.name,
                ),
            )
        }
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

    /** Drops the session in progress and its sets. */
    fun discardSession() {
        persist {
            val active = sessionDao.active() ?: return@persist
            sessionDao.discardSets(active.uid)
            sessionDao.discard(active.uid)
        }
    }

    /** Deletes a finished session, leaving its row behind as a tombstone. */
    fun deleteSession(uid: String) {
        val now = clock.nowMillis()
        persist { sessionDao.markDeleted(uid, now) }
    }

    /**
     * Saves [draft] as the exercise [uid], or as a new one with null; returns its uid. Its name is kept as
     * written, trimmed, and an archived one stays archived.
     */
    @OptIn(ExperimentalUuidApi::class)
    fun saveExercise(uid: String?, draft: ExerciseDraft): String {
        val id = uid ?: Uuid.random().toHexString()
        val made = CustomExercise(
            id,
            draft.name.trim(),
            draft.equipment,
            draft.measure,
            draft.perSide,
            draft.focus,
            draft.cues.trim(),
            archived = _customExercises.value.any { it.uid == id && it.archived },
        )
        persist { customExerciseDao.upsert(made.toEntity(clock.nowMillis())) }
        return id
    }

    /**
     * Archives the user's exercise [uid], which the Log sheet then no longer offers, or with false brings it
     * back. Its sets keep it either way.
     */
    fun setArchived(uid: String, archived: Boolean) {
        val made = _customExercises.value.firstOrNull { it.uid == uid } ?: return
        persist { customExerciseDao.upsert(made.copy(archived = archived).toEntity(clock.nowMillis())) }
    }

    fun setOwned(kg: Double, owned: Boolean) {
        persist { if (owned) bellDao.upsert(BellEntity(kg)) else bellDao.delete(kg) }
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

/**
 * A set, naming one of [made] or a built-in; one this build can't name reads as an [UnknownExercise], so it
 * is never lost. A set logged before version 7 was done with a kettlebell. One of a lift only done with two
 * bells is read as such, though logged before sets had the column.
 */
private fun SetEntity.toDomain(made: List<CustomExercise>): SetLog {
    val done = equipment?.let { e -> Equipment.entries.firstOrNull { it.name == e } } ?: Equipment.Kettlebell
    val lift = Exercise.parse(exercise, made)
        ?: UnknownExercise(exercise, done, if (seconds != null) Measure.Time else Measure.Reps)
    return SetLog(
        id,
        lift,
        bellKg,
        reps,
        side?.let { s -> Side.entries.firstOrNull { it.name == s } },
        loggedAtEpochMillis,
        lift.doubled(doubled),
        equipment = done,
        seconds = seconds,
        band = band?.let { b -> BandLevel.entries.firstOrNull { it.name == b } },
    )
}

/** An exercise the user added; one whose kinds this build doesn't know is left out. */
private fun CustomExerciseEntity.toDomain(): CustomExercise? = CustomExercise(
    uid,
    name,
    equipment = Equipment.entries.firstOrNull { it.name == equipment } ?: return null,
    measure = Measure.entries.firstOrNull { it.name == measure } ?: return null,
    perSide = perSide,
    focus = Focus.entries.firstOrNull { it.name == focus } ?: return null,
    cues = cues,
    archived = archived,
)

private fun CustomExercise.toEntity(updatedAtEpochMillis: Long) = CustomExerciseEntity(
    uid,
    name,
    equipment.name,
    measure.name,
    perSide,
    focus.name,
    cues,
    archived,
    updatedAtEpochMillis,
)

private fun SessionEntity.toDomain(sets: List<SetLog>) = LiftSession(
    uid,
    startedAtEpochMillis,
    finishedAtEpochMillis,
    title,
    sets,
    programId?.let(ProgramKey::parse),
)

/** The program [stored] names: a built-in, or one of [made]; null for one gone or unknown. */
private fun resolve(stored: String, made: List<Program>): Program? = ProgramKey.parse(stored)?.let { key ->
    (builtInPrograms + made).firstOrNull { it.key == key }
}
