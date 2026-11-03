package dev.justinsmith.lift.data

import co.touchlab.kermit.Logger
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.storage.SerialWriter
import dev.justinsmith.interval.storage.orOnError
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.Load
import dev.justinsmith.lift.domain.Measure
import dev.justinsmith.lift.domain.RestClock
import dev.justinsmith.lift.domain.SavedTimer
import dev.justinsmith.lift.domain.SessionPlan
import dev.justinsmith.lift.domain.SetLog
import dev.justinsmith.lift.domain.Side
import dev.justinsmith.lift.domain.band
import dev.justinsmith.lift.domain.double
import dev.justinsmith.lift.domain.taking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What goes on in the session in progress, its sets, its plan and the timer running, and how long a rest
 * lasts: Lift's [SetStore]. Each write finds the session as stored behind every write before it, never through
 * [SessionRepository.active], which a read of the database can still show as it was. Written through [writer],
 * the queue every Lift repository shares. The rest's row sits beside the bells, in [bellDao].
 */
class SetRepository(
    private val sessionDao: SessionDao,
    private val bellDao: BellDao,
    private val exercises: ExerciseRepository,
    private val gear: GearRepository,
    private val writer: SerialWriter,
    private val clock: WallClock,
    scope: CoroutineScope,
) : SetStore {
    private val log = Logger.withTag("SetRepository")

    private val _restSeconds = MutableStateFlow(RestClock.DEFAULT_SECONDS)

    /** How long a rest between sets lasts, in seconds. */
    override val restSeconds: StateFlow<Int> = _restSeconds.asStateFlow()

    private val _ready = MutableStateFlow(false)

    /** True once the rest has been read. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init {
        scope.launch {
            bellDao.observeRest().orOnError(null, "the rest", log).collect { row ->
                _restSeconds.value = row?.seconds ?: RestClock.DEFAULT_SECONDS
                _ready.value = true
            }
        }
    }

    /**
     * Logs a set to the session in progress, its [load] as [exercise] takes it: with two bells only if it can
     * be, a band only for band work. A held exercise keeps its [seconds], dropped from any other.
     */
    override fun logSet(exercise: Exercise, load: Load, reps: Int, side: Side?, seconds: Int?) {
        writer.persist {
            val active = sessionDao.active() ?: return@persist
            val taken = exercise.taking(load)
            sessionDao.insert(
                SetEntity(
                    0,
                    active.uid,
                    exercise.key,
                    taken.kg,
                    reps,
                    side.takeIf { exercise.sided(taken.double) }?.name,
                    clock.nowMillis(),
                    taken.double,
                    equipment = taken.equipment.name,
                    seconds = seconds.takeIf { exercise.measure == Measure.Time },
                    band = taken.band?.id,
                ),
            )
        }
    }

    /**
     * Changes the logged set [set.id] to [set]: its load, reps or seconds and side. It keeps its session and when
     * it was logged; a held exercise keeps seconds and a band one its band, each dropped from any other.
     */
    override fun updateSet(set: SetLog) {
        writer.persist {
            val row = sessionDao.set(set.id) ?: return@persist
            val exercise = set.exercise
            val doubled = exercise.doubled(set.double)
            sessionDao.update(
                row.copy(
                    bellKg = set.load.kg,
                    reps = set.reps,
                    side = set.side.takeIf { exercise.sided(doubled) }?.name,
                    doubled = doubled,
                    seconds = set.seconds.takeIf { exercise.measure == Measure.Time },
                    band = set.load.band?.id,
                ),
            )
        }
    }

    override fun removeSet(id: Long) {
        writer.persist { sessionDao.deleteSet(id) }
    }

    /** Puts back [set], just removed, into the session in progress, in its place among the others. */
    override fun restoreSet(set: SetLog) {
        writer.persist {
            val active = sessionDao.active() ?: return@persist
            sessionDao.insert(set.toEntity(active.uid, id = set.id))
        }
    }

    /**
     * Changes the day the session in progress is to what [change] makes of it, or leaves it with null. Worked out
     * in the write, from the session as stored behind every write before it: so a change made straight after
     * another, or after a set, builds on both. [change] runs on the storage's thread.
     */
    override fun changeSessionPlan(change: (LiftSession) -> SessionPlan?) {
        writer.persist {
            val row = sessionDao.active() ?: return@persist
            val made = exercises.customExercises.value
            val bands = gear.bands.value
            val session = row.toDomain(sessionDao.sets(row.uid).map { it.toDomain(made, bands) }, made, bands)
            val plan = change(session) ?: return@persist
            if (plan != session.plan) sessionDao.setPlan(row.uid, encodePlan(plan))
        }
    }

    /**
     * Keeps [timer] as the one running in the session in progress, or none with null, so a process killed in it
     * goes on with it. Nothing without a session in progress.
     */
    override fun saveTimer(timer: SavedTimer?) {
        writer.persist {
            val row = sessionDao.active() ?: return@persist
            sessionDao.setTimer(row.uid, timer?.let(::encodeTimer))
        }
    }

    /** Sets how long a rest between sets lasts, in seconds. */
    override fun setRestSeconds(seconds: Int) {
        _restSeconds.value = seconds
        writer.persist { bellDao.upsertRest(RestEntity(seconds = seconds)) }
    }
}
