package dev.justinsmith.lift.data

import co.touchlab.kermit.Logger
import dev.justinsmith.interval.platform.HeartRate
import dev.justinsmith.interval.platform.IdGenerator
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.storage.SerialWriter
import dev.justinsmith.interval.storage.orEmptyOnError
import dev.justinsmith.lift.domain.Band
import dev.justinsmith.lift.domain.CustomExercise
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.PerceivedEffort
import dev.justinsmith.lift.domain.ProgramDay
import dev.justinsmith.lift.domain.ProgramKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Lift's sessions, those finished and the one in progress, with their sets, and starting, finishing or dropping
 * one: its [SessionStore], and the [LiftActivity] the interval side reads. A set names one of the user's
 * [exercises] or [gear] (its band), so the sessions are read with them; a day of the program followed is started
 * as the day [programs] says is next. Written through [writer], the queue every Lift repository shares.
 */
class SessionRepository(
    private val sessionDao: SessionDao,
    exercises: ExerciseRepository,
    gear: GearRepository,
    private val programs: ProgramRepository,
    private val writer: SerialWriter,
    private val clock: WallClock,
    private val ids: IdGenerator,
    scope: CoroutineScope,
) : LiftActivity, SessionStore {
    private val log = Logger.withTag("SessionRepository")

    private val _ready = MutableStateFlow(false)

    /** True once the sessions and the program's day have been read. */
    override val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _sessions = MutableStateFlow(emptyList<LiftSession>())

    /** Finished sessions, newest first. */
    override val sessions: StateFlow<List<LiftSession>> = _sessions.asStateFlow()

    private val _active = MutableStateFlow<LiftSession?>(null)

    /** The session in progress, if one is. */
    override val active: StateFlow<LiftSession?> = _active.asStateFlow()

    /** The day of the program followed to do next, as [programs] keeps it. */
    override val programDay: StateFlow<ProgramDay?> get() = programs.programDay

    init {
        scope.launch {
            // The user's exercises and bands come with the sets, which name them.
            val mapping = SessionMapping()
            combine(sessionDao.observeSessions(), sessionDao.observeSets(), exercises.made, gear.owned, mapping::map)
                .orEmptyOnError("sessions", log).collect { all ->
                    _sessions.value = all.filter { it.finishedAtEpochMillis != null }
                    _active.value = all.firstOrNull { it.finishedAtEpochMillis == null }
                    if (!_ready.value) {
                        programs.ready.first { it }
                        _ready.value = true
                    }
                }
        }
    }

    /**
     * Starts a session, a day of [program] or a free one, unless one is in progress already. A day of the
     * program followed is logged as the day it is.
     */
    override fun startSession(program: ProgramKey?) {
        val day = programs.programDay.value?.takeIf { program != null && it.program.key == program }?.number
        writer.persist {
            if (sessionDao.active() == null) {
                val now = clock.nowMillis()
                sessionDao.insert(
                    SessionEntity(
                        ids.newId(),
                        now,
                        null,
                        title = "",
                        updatedAtEpochMillis = now,
                        programId = program?.stored,
                        programDay = day,
                    ),
                )
            }
        }
    }

    /** Finishes the session in progress under [title]; one with no sets is dropped instead. */
    override fun finishSession(title: String) {
        writer.persist { sessionDao.finishActive(title, clock.nowMillis()) }
    }

    /**
     * Keeps how the finished session [uid] felt, as said on its summary: [effort] and a [note], blank for none. In
     * order after the write that finished it, so it never lands first.
     */
    override fun reflectOnSession(uid: String, effort: PerceivedEffort?, note: String) {
        val now = clock.nowMillis()
        val kept = note.trim().take(PerceivedEffort.NOTE_MAX_LENGTH).ifEmpty { null }
        writer.persist { sessionDao.reflect(uid, effort?.level, kept, now) }
    }

    override fun keepHeartRate(uid: String, heartRate: HeartRate) {
        val now = clock.nowMillis()
        writer.persist { sessionDao.keepHeartRate(uid, heartRate.averageBpm, heartRate.peakBpm, now) }
    }

    /** Drops the session in progress and its sets. */
    override fun discardSession() {
        writer.persist { sessionDao.discardActive() }
    }

    /** Deletes a finished session, leaving its row behind as a tombstone. */
    override fun deleteSession(uid: String) {
        val now = clock.nowMillis()
        writer.persist { sessionDao.markDeleted(uid, now) }
    }

    /** Brings back a finished session deleted with [deleteSession]. */
    override fun restoreSession(uid: String) {
        val now = clock.nowMillis()
        writer.persist { sessionDao.unmarkDeleted(uid, now) }
    }
}

/**
 * The sessions as the domain reads them, each mapped again only when its row, its sets or what they name (the
 * user's exercises and bands) changed. A set logged maps its own session, not the whole history; a session that
 * didn't change is the one it was, so whatever compares the history after finds it the same at once.
 */
internal class SessionMapping {
    private class Mapped(val row: SessionEntity, val sets: List<SetEntity>, val session: LiftSession)

    private var made: List<CustomExercise> = emptyList()
    private var bands: List<Band> = emptyList()
    private var mapped: Map<String, Mapped> = emptyMap()

    /** [sessions] with their [sets], which name [made] and [bands], in [sessions]' order. */
    fun map(
        sessions: List<SessionEntity>,
        sets: List<SetEntity>,
        made: List<CustomExercise>,
        bands: List<Band>,
    ): List<LiftSession> {
        val kept = if (made == this.made && bands == this.bands) mapped else emptyMap()
        val bySession = sets.groupBy { it.sessionUid }
        val next = sessions.associate { row ->
            val rows = bySession[row.uid].orEmpty()
            val same = kept[row.uid]?.takeIf { it.row == row && it.sets == rows }
            row.uid to (same ?: Mapped(row, rows, row.toDomain(rows.map { it.toDomain(made, bands) }, made, bands)))
        }
        this.made = made
        this.bands = bands
        mapped = next
        return sessions.map { next.getValue(it.uid).session }
    }
}
