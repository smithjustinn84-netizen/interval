@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.bells.data

import co.touchlab.kermit.Logger
import dev.justinsmith.bells.domain.Band
import dev.justinsmith.bells.domain.BandColour
import dev.justinsmith.bells.domain.CustomExercise
import dev.justinsmith.bells.domain.Equipment
import dev.justinsmith.bells.domain.Exercise
import dev.justinsmith.bells.domain.ExerciseDraft
import dev.justinsmith.bells.domain.Focus
import dev.justinsmith.bells.domain.LiftSession
import dev.justinsmith.bells.domain.Measure
import dev.justinsmith.bells.domain.PlanChanges
import dev.justinsmith.bells.domain.Program
import dev.justinsmith.bells.domain.ProgramDay
import dev.justinsmith.bells.domain.ProgramDraft
import dev.justinsmith.bells.domain.ProgramKey
import dev.justinsmith.bells.domain.SaveScope
import dev.justinsmith.bells.domain.SessionPlan
import dev.justinsmith.bells.domain.SetLog
import dev.justinsmith.bells.domain.Side
import dev.justinsmith.bells.domain.UnknownExercise
import dev.justinsmith.bells.domain.bandOf
import dev.justinsmith.bells.domain.bands
import dev.justinsmith.bells.domain.builtInPrograms
import dev.justinsmith.bells.domain.nextBandPosition
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.storage.SerialWriter
import dev.justinsmith.interval.storage.orEmptyOnError
import dev.justinsmith.interval.storage.orOnError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val bandDao: BandDao,
    private val scope: CoroutineScope,
    private val clock: WallClock,
) {
    private val log = Logger.withTag("BellsRepository")
    private val writer = SerialWriter(scope, log)

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

    private val _bar = MutableStateFlow<Double?>(null)

    /** What the user's barbell weighs empty, or null until they say they have one. */
    val bar: StateFlow<Double?> = _bar.asStateFlow()

    private val _customExercises = MutableStateFlow(emptyList<CustomExercise>())

    /** The exercises the user added, archived ones too, by name. */
    val customExercises: StateFlow<List<CustomExercise>> = _customExercises.asStateFlow()

    private val _bands = MutableStateFlow(bands(emptyList()))

    /** The user's bands, archived ones too, lightest first: the five every user starts with, then theirs. */
    val bands: StateFlow<List<Band>> = _bands.asStateFlow()

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
        scope.launch {
            val sessionsLoaded = CompletableDeferred<Unit>()
            val bellsLoaded = CompletableDeferred<Unit>()
            val exercises = customExerciseDao.observeAll().orEmptyOnError("exercises", log).map { rows ->
                rows.mapNotNull { it.toDomain() }
            }
            val owned = bandDao.observeAll().orEmptyOnError("bands", log).map { rows ->
                bands(rows.map { it.toDomain() })
            }
            launch {
                // The user's exercises and bands come with the sets, which name them.
                combine(sessionDao.observeSessions(), sessionDao.observeSets(), exercises, owned) {
                        sessions,
                        sets,
                        made,
                        bands,
                    ->
                    val bySession = sets.groupBy({ it.sessionUid }, { it.toDomain(made, bands) })
                    Triple(made, bands, sessions.map { it.toDomain(bySession[it.uid].orEmpty(), made) })
                }.orOnError(
                    Triple(emptyList(), bands(emptyList()), emptyList()),
                    "sessions",
                    log,
                ).collect { (made, bands, all) ->
                    _customExercises.value = made
                    _bands.value = bands
                    _sessions.value = all.filter { it.finishedAtEpochMillis != null }
                    _active.value = all.firstOrNull { it.finishedAtEpochMillis == null }
                    sessionsLoaded.complete(Unit)
                }
            }
            launch {
                bellDao.observeAll().orEmptyOnError("bells", log).collect { rows ->
                    _bells.value = rows.map { it.kg }
                    bellsLoaded.complete(Unit)
                }
            }
            val barLoaded = CompletableDeferred<Unit>()
            launch {
                bellDao.observeBar().orOnError(null, "the bar", log).collect { row ->
                    _bar.value = row?.kg
                    barLoaded.complete(Unit)
                }
            }
            val customLoaded = CompletableDeferred<Unit>()
            // A program's blocks can name the user's exercises, archived ones too.
            val custom =
                combine(customProgramDao.observeAll().orEmptyOnError("programs", log), exercises) { rows, made ->
                    rows.mapNotNull { it.toProgram(made) }
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
                    row?.let { r -> resolve(r.programId, made)?.let { it to r } }
                }.flatMapLatest { followed ->
                    if (followed == null) {
                        flowOf(null to null)
                    } else {
                        val (program, row) = followed
                        sessionDao.observeProgramDays(program.key.stored, row.startedAtEpochMillis).map { done ->
                            program to program.dayAfter(row.daysBefore + done)
                        }
                    }
                }.orOnError(null to null, "the program", log).collect { (program, day) ->
                    _following.value = program
                    _programDay.value = day
                    followingLoaded.complete(Unit)
                }
            }
            followingLoaded.await()
            customLoaded.await()
            sessionsLoaded.await()
            bellsLoaded.await()
            barLoaded.await()
            _ready.value = true
        }
    }

    /** Starts a session, a day of [program] or a free one, unless one is in progress already. */
    @OptIn(ExperimentalUuidApi::class)
    fun startSession(program: ProgramKey? = null) {
        writer.persist {
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

    /** Follows [program] from its first day, or after [daysDone] of it, in place of any other. */
    fun follow(program: ProgramKey, daysDone: Int = 0) {
        val now = clock.nowMillis()
        val row = FollowingEntity(programId = program.stored, startedAtEpochMillis = now, daysBefore = daysDone)
        writer.persist { followingDao.upsert(row) }
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
        writer.persist { customProgramDao.upsert(row) }
        return id
    }

    /**
     * Keeps [changes] in their program, as far as [scope] reaches: in place, for one the user made, whose place
     * stays; for a built-in, in a copy named [copyName], which is followed from the day after while the built-in
     * is and goes on. Returns the program kept in.
     */
    fun saveChanges(changes: PlanChanges, scope: SaveScope, copyName: String): ProgramKey {
        val program = changes.program
        val key = program.key
        if (key is ProgramKey.Custom) {
            saveProgram(key.uid, changes.draft(scope, program.name.orEmpty()))
            return key
        }
        val copy = ProgramKey.Custom(saveProgram(null, changes.draft(scope, copyName)))
        if (changes.goesOn && _following.value?.key == key) follow(copy, daysDone = changes.dayNumber)
        return copy
    }

    /** Deletes the program [uid] made, and stops following it if it is the one followed. */
    fun deleteProgram(uid: String) {
        writer.persist {
            followingDao.clearIfFollowing(ProgramKey.Custom(uid).stored)
            customProgramDao.delete(uid)
        }
    }

    fun stopFollowing() {
        writer.persist { followingDao.clear() }
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
        band: Band? = null,
    ) {
        writer.persist {
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
                    band = band.takeIf { exercise.equipment == Equipment.Band }?.id,
                ),
            )
        }
    }

    /** Changes the day the session in progress is to [plan], or back to as programmed with null. */
    fun setSessionPlan(plan: SessionPlan?) {
        // Shown at once, so a change made straight after builds on this one.
        _active.value = _active.value?.copy(plan = plan)
        writer.persist {
            val active = sessionDao.active() ?: return@persist
            sessionDao.setPlan(active.uid, plan?.let(::encodePlan))
        }
    }

    /**
     * Changes the logged set [set.id] to [set]: its load, reps or seconds and side. It keeps its session and when
     * it was logged; a held exercise keeps seconds and a band one its band, each dropped from any other.
     */
    fun updateSet(set: SetLog) {
        writer.persist {
            val row = sessionDao.allSets().firstOrNull { it.id == set.id } ?: return@persist
            val exercise = set.exercise
            val doubled = exercise.doubled(set.double)
            sessionDao.update(
                row.copy(
                    bellKg = set.bellKg,
                    reps = set.reps,
                    side = set.side.takeIf { exercise.sided(doubled) }?.name,
                    doubled = doubled,
                    seconds = set.seconds.takeIf { exercise.measure == Measure.Time },
                    band = set.band.takeIf { exercise.equipment == Equipment.Band }?.id,
                ),
            )
        }
    }

    fun removeSet(id: Long) {
        writer.persist { sessionDao.deleteSet(id) }
    }

    /** Puts back [set], just removed, into the session in progress, in its place among the others. */
    fun restoreSet(set: SetLog) {
        writer.persist {
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
                    set.band?.id,
                ),
            )
        }
    }

    /** Finishes the session in progress under [title]; one with no sets is dropped instead. */
    fun finishSession(title: String) {
        writer.persist {
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
        writer.persist {
            val active = sessionDao.active() ?: return@persist
            sessionDao.discardSets(active.uid)
            sessionDao.discard(active.uid)
        }
    }

    /** Deletes a finished session, leaving its row behind as a tombstone. */
    fun deleteSession(uid: String) {
        val now = clock.nowMillis()
        writer.persist { sessionDao.markDeleted(uid, now) }
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
        writer.persist { customExerciseDao.upsert(made.toEntity(clock.nowMillis())) }
        return id
    }

    /**
     * Archives the user's exercise [uid], which the Log sheet then no longer offers, or with false brings it
     * back. Its sets keep it either way.
     */
    fun setArchived(uid: String, archived: Boolean) {
        val made = _customExercises.value.firstOrNull { it.uid == uid } ?: return
        writer.persist { customExerciseDao.upsert(made.copy(archived = archived).toEntity(clock.nowMillis())) }
    }

    /** Saves [band] as changed: renamed (blank for its starting words), recoloured or archived. */
    fun saveBand(band: Band) {
        saveBands(listOf(band.copy(name = band.name.trim())))
    }

    /** Saves the order [bands] are in, each band's position its place. */
    fun saveBands(bands: List<Band>) {
        val now = clock.nowMillis()
        writer.persist { bandDao.upsert(bands.map { it.toEntity(now) }) }
    }

    /** Adds a band named [name] in [colour], heaviest of all; returns its id. */
    @OptIn(ExperimentalUuidApi::class)
    fun addBand(name: String, colour: BandColour): String {
        val id = Uuid.random().toHexString()
        saveBand(Band(id, name, colour, nextBandPosition(_bands.value)))
        return id
    }

    /**
     * Removes [band]: one the user added with no sets of it goes, any other (one they started with, or one
     * with sets) is archived, so every set keeps its band.
     */
    fun removeBand(band: Band) {
        val used = (_sessions.value.flatMap { it.sets } + _active.value?.sets.orEmpty()).any { it.band?.id == band.id }
        if (band.level == null && !used) {
            writer.persist { bandDao.delete(band.id) }
        } else {
            saveBand(band.copy(archived = true))
        }
    }

    fun setOwned(kg: Double, owned: Boolean) {
        writer.persist { if (owned) bellDao.upsert(BellEntity(kg)) else bellDao.delete(kg) }
    }

    /** Sets the user's barbell to one weighing [kg] empty, or to none. */
    fun setBar(kg: Double?) {
        writer.persist { if (kg != null) bellDao.upsertBar(BarEntity(kg = kg)) else bellDao.clearBar() }
    }
}

/**
 * A set, naming one of [made] or a built-in; one this build can't name reads as an [UnknownExercise], so it
 * is never lost. A set logged before version 7 was done with a kettlebell. One of a lift only done with two
 * bells is read as such, though logged before sets had the column.
 */
private fun SetEntity.toDomain(made: List<CustomExercise>, bands: List<Band>): SetLog {
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
        band = band?.let { bandOf(it, bands) },
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

private fun BandEntity.toDomain() =
    Band(id, name, BandColour.entries.firstOrNull { it.name == colour } ?: BandColour.Grey, position, archived)

private fun Band.toEntity(updatedAtEpochMillis: Long) =
    BandEntity(id, name, colour.name, position, archived, updatedAtEpochMillis)

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

private fun SessionEntity.toDomain(sets: List<SetLog>, made: List<CustomExercise>) = LiftSession(
    uid,
    startedAtEpochMillis,
    finishedAtEpochMillis,
    title,
    sets,
    programId?.let(ProgramKey::parse),
    plan?.let { decodePlan(it, made) },
)

/** The program [stored] names: a built-in, or one of [made]; null for one gone or unknown. */
private fun resolve(stored: String, made: List<Program>): Program? = ProgramKey.parse(stored)?.let { key ->
    (builtInPrograms + made).firstOrNull { it.key == key }
}
