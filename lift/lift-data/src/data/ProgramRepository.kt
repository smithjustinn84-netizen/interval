@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.lift.data

import co.touchlab.kermit.Logger
import dev.justinsmith.interval.platform.IdGenerator
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.storage.SerialWriter
import dev.justinsmith.interval.storage.orEmptyOnError
import dev.justinsmith.interval.storage.orOnError
import dev.justinsmith.lift.domain.Followed
import dev.justinsmith.lift.domain.PlanChanges
import dev.justinsmith.lift.domain.Program
import dev.justinsmith.lift.domain.ProgramDay
import dev.justinsmith.lift.domain.ProgramDraft
import dev.justinsmith.lift.domain.ProgramKey
import dev.justinsmith.lift.domain.SaveScope
import dev.justinsmith.lift.domain.lastDayDone
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

/**
 * The programs the user made, the one followed and its day to do next: Lift's [ProgramStore]. A program's
 * blocks can name the user's exercises and bands, so they are read with [exercises] and [gear]; its next day is
 * counted from the sessions of it in [sessionDao]. Written through [writer], the queue every Lift repository shares.
 */
class ProgramRepository(
    private val customProgramDao: CustomProgramDao,
    private val followingDao: FollowingDao,
    sessionDao: SessionDao,
    exercises: ExerciseRepository,
    gear: GearRepository,
    private val writer: SerialWriter,
    private val clock: WallClock,
    private val ids: IdGenerator,
    scope: CoroutineScope,
) : ProgramStore {
    private val log = Logger.withTag("ProgramRepository")

    private val _customPrograms = MutableStateFlow(emptyList<Program>())

    /** The programs the user made, by name. */
    override val customPrograms: StateFlow<List<Program>> = _customPrograms.asStateFlow()

    private val _programDay = MutableStateFlow<ProgramDay?>(null)
    private val _following = MutableStateFlow<Program?>(null)
    private val _followed = MutableStateFlow<Followed?>(null)

    /** The program the user follows, if any, as it stands: a change to one they made shows at once. */
    override val following: StateFlow<Program?> = _following.asStateFlow()

    /** [following] with when it was followed from and the days of it counted done before; null for none. */
    override val followed: StateFlow<Followed?> = _followed.asStateFlow()

    /**
     * The day of [following] to do next: the one after the last day done, as its sessions were logged (see
     * [lastDayDone]); null when none is followed or it is over.
     */
    override val programDay: StateFlow<ProgramDay?> = _programDay.asStateFlow()

    private val _ready = MutableStateFlow(false)

    /** True once the programs made and the one followed have been read. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init {
        scope.launch {
            val customLoaded = CompletableDeferred<Unit>()
            // A program's blocks can name the user's exercises, archived ones too, and their bands.
            val custom = combine(
                customProgramDao.observeAll().orEmptyOnError("programs", log),
                exercises.made,
                gear.owned,
            ) { rows, made, bands -> rows.mapNotNull { it.toProgram(made, bands) } }
            launch {
                custom.collect {
                    _customPrograms.value = it
                    customLoaded.complete(Unit)
                }
            }
            val followingLoaded = CompletableDeferred<Unit>()
            launch {
                combine(followingDao.observe(), custom) { row, made ->
                    row?.let { r ->
                        resolveProgram(r.programId, made)?.let { Followed(it, r.startedAtEpochMillis, r.daysBefore) }
                    }
                }.flatMapLatest { followed ->
                    if (followed == null) {
                        flowOf(null to null)
                    } else {
                        val program = followed.program
                        sessionDao.observeProgramSessions(
                            program.key.stored,
                            followed.startedAtEpochMillis,
                        ).map { rows ->
                            followed to program.dayAfter(lastDayDone(rows.map { it.programDay }, followed.daysBefore))
                        }
                    }
                }.orOnError(null to null, "the program", log).collect { (followed, day) ->
                    _followed.value = followed
                    _following.value = followed?.program
                    _programDay.value = day
                    followingLoaded.complete(Unit)
                }
            }
            customLoaded.await()
            followingLoaded.await()
            _ready.value = true
        }
    }

    /** Follows [program] from its first day, or after [daysDone] of it, in place of any other. */
    override fun follow(program: ProgramKey, daysDone: Int) {
        val now = clock.nowMillis()
        val row = FollowingEntity(programId = program.stored, startedAtEpochMillis = now, daysBefore = daysDone)
        writer.persist { followingDao.upsert(row) }
    }

    /**
     * Saves [draft] as the program [uid], or as a new one with null; returns its uid. A program followed
     * keeps its place: the days done still count, against the program as it now is.
     */
    override fun saveProgram(uid: String?, draft: ProgramDraft): String {
        val id = uid ?: ids.newId()
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
    override fun saveChanges(changes: PlanChanges, scope: SaveScope, copyName: String): ProgramKey {
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

    /**
     * Saves [draft] as a new program, a copy of the built-in [of]; returns its uid. While [of] is followed and
     * goes on, the copy is followed from the day it was at, its days done counted done.
     */
    override fun saveCopy(of: ProgramKey.BuiltIn, draft: ProgramDraft): String {
        val uid = saveProgram(null, draft)
        val day = _programDay.value?.takeIf { _following.value?.key == of && it.program.key == of }
        if (day != null) follow(ProgramKey.Custom(uid), daysDone = day.number - 1)
        return uid
    }

    /** Deletes the program [uid] made, and stops following it if it is the one followed. */
    override fun deleteProgram(uid: String) {
        writer.persist { customProgramDao.deleteProgram(uid, ProgramKey.Custom(uid).stored) }
    }

    override fun stopFollowing() {
        writer.persist { followingDao.clear() }
    }
}
