package dev.justinsmith.lift.data

import co.touchlab.kermit.Logger
import dev.justinsmith.interval.platform.IdGenerator
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.storage.SerialWriter
import dev.justinsmith.interval.storage.StorageError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Lift's saved state, the one way into storage: a repository per kind of thing kept, [sessions] (the
 * [LiftActivity] the interval side reads), [sets], [programs], [exercises] and [gear], over the one database, each
 * the store its screens take. Their writes go through one queue, in the order they were made, so a save of one
 * kind can't overtake an earlier one of another: a band removed lands behind the set just logged with it.
 * Everything is read into memory before [ready] turns true. What only the app does is here: [importAll],
 * [writeErrors] and [close].
 */
class LiftRepository(
    sessionDao: SessionDao,
    bellDao: BellDao,
    followingDao: FollowingDao,
    customProgramDao: CustomProgramDao,
    customExerciseDao: CustomExerciseDao,
    bandDao: BandDao,
    private val importDao: LiftImportDao,
    scope: CoroutineScope,
    private val clock: WallClock,
    ids: IdGenerator = IdGenerator.Random,
) {
    private val log = Logger.withTag("LiftRepository")
    private val writer = SerialWriter(scope, log)

    /** Each save that failed, as it fails, for the app to say so: the write itself is dropped. */
    val writeErrors: SharedFlow<StorageError> get() = writer.errors

    /** The exercises the user added. */
    val exercises = ExerciseRepository(customExerciseDao, writer, clock, ids, scope)

    /** The bells the user owns, their bar and their bands. */
    val gear = GearRepository(bellDao, bandDao, writer, clock, ids, scope)

    /** The programs the user made, the one followed and its next day. */
    val programs =
        ProgramRepository(customProgramDao, followingDao, sessionDao, exercises, gear, writer, clock, ids, scope)

    /** The sessions, finished and in progress, and what the interval side reads of Lift. */
    val sessions = SessionRepository(sessionDao, exercises, gear, programs, writer, clock, ids, scope)

    /** The session in progress's sets, plan and timer, and the rest. */
    val sets = SetRepository(sessionDao, bellDao, exercises, gear, writer, clock, scope)

    private val _ready = MutableStateFlow(false)

    /** True once every repository has read what it keeps. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init {
        scope.launch {
            listOf(exercises.ready, gear.ready, programs.ready, sessions.ready, sets.ready).forEach { read ->
                read.first { it }
            }
            _ready.value = true
        }
    }

    /**
     * Takes no more writes and returns once those already made have landed: the container's last step before it
     * closes the database and cancels the scope, which would otherwise drop them.
     */
    suspend fun close() {
        writer.close()
    }

    /**
     * Adds what an import brings, all of it or none (one transaction), behind every write before it, and returns
     * once it has landed; a failure is thrown, nothing having been written. Sessions keep their uids, exercises
     * and programs theirs; sets are stored afresh under their session.
     */
    suspend fun importAll(import: LiftImport) {
        val now = clock.nowMillis()
        val rows = LiftImportRows(
            sessions = import.sessions.map { it.importRow(now) },
            sets = import.sessions.flatMap { session -> session.sets.map { it.toEntity(session.uid) } },
            exercises = import.exercises.map { it.toEntity(now) },
            programs = import.programs.mapNotNull { it.importRow(now) },
            bands = import.bands.map { it.toEntity(now) },
            bells = import.bellsKg.map(::BellEntity),
            bar = import.barKg?.let { BarEntity(kg = it) },
            following = import.following?.let {
                FollowingEntity(
                    programId = it.program.key.stored,
                    startedAtEpochMillis = it.startedAtEpochMillis,
                    daysBefore = it.daysBefore,
                )
            },
            rest = import.restSeconds?.let { RestEntity(seconds = it) },
        )
        writer.await { importDao.importAll(rows) }
    }
}
