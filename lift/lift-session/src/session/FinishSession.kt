package dev.justinsmith.lift.session

import dev.justinsmith.interval.platform.HealthStore
import dev.justinsmith.interval.platform.HealthWorkout
import dev.justinsmith.interval.platform.HeartRate
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.timer.WeekStanding
import dev.justinsmith.interval.timer.weekStanding
import dev.justinsmith.lift.data.GearStore
import dev.justinsmith.lift.data.IntervalActivity
import dev.justinsmith.lift.data.ProgramStore
import dev.justinsmith.lift.data.SessionStore
import dev.justinsmith.lift.data.SetStore
import dev.justinsmith.lift.data.snatchSets
import dev.justinsmith.lift.domain.ABC_MINUTES
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.PerceivedEffort
import dev.justinsmith.lift.domain.SNATCH_TEST_MINUTES
import dev.justinsmith.lift.domain.SaveScope
import dev.justinsmith.lift.domain.SetLog
import dev.justinsmith.lift.domain.crossedMilestone
import dev.justinsmith.lift.domain.densityDone
import dev.justinsmith.lift.domain.gripLoad
import dev.justinsmith.lift.domain.lifetimeReps
import dev.justinsmith.lift.domain.mostInWindow
import dev.justinsmith.lift.domain.newPaces
import dev.justinsmith.lift.domain.newRecords
import dev.justinsmith.lift.domain.offeredBands
import dev.justinsmith.lift.domain.planChanges
import dev.justinsmith.lift.domain.progressions
import dev.justinsmith.lift.domain.sets
import dev.justinsmith.lift.domain.snatchTest
import dev.justinsmith.lift.domain.standardBells
import dev.justinsmith.lift.domain.testOf
import dev.justinsmith.lift.domain.testResult
import dev.justinsmith.lift.domain.toppedOut
import dev.justinsmith.lift.domain.wantsHandCare
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * A session finishing, and its summary: [finish] saves it with the records it set, the changes a program day
 * offers to keep, where the week stands and what topped out, and writes it to the health store; then what the
 * summary says of it (how hard it felt, a note) is kept with it, and its plan changes in their program.
 */
class FinishSession(
    private val sessionStore: SessionStore,
    private val programStore: ProgramStore,
    private val gearStore: GearStore,
    private val setStore: SetStore,
    private val intervals: IntervalActivity,
    private val timestamps: TimestampFormatter,
    private val clock: WallClock,
    private val health: SessionHealth,
) {
    private val current = MutableStateFlow<Finished?>(null)

    /** The session just finished, for the summary; null until one is. */
    val finished: StateFlow<Finished?> = current.asStateFlow()

    /**
     * Finishes the session in progress as [title], keeping it for the summary with the records it set; the health
     * store has it as [healthTitle] when there is one (a program's day, by its name). One with no sets has nothing
     * to save and is thrown away instead. True when it was saved, and the summary follows.
     */
    fun finish(title: String, healthTitle: String? = null): Boolean {
        val session = sessionStore.active.value ?: return false
        if (session.sets.isEmpty()) {
            sessionStore.discardSession()
            return false
        }
        val done = session.copy(finishedAtEpochMillis = clock.nowMillis(), title = title)
        // Taken now: once the session is saved, the program day moves on to the next.
        val bells = gearStore.bells.value.ifEmpty { standardBells }
        val bands = offeredBands(gearStore.bands.value)
        val pairs = gearStore.pairs.value
        val day = programDay(programStore.programDay.value, programStore.programs, session)
        // A test's attempt is said as its own line, against the last at its load: it changes no program, and the
        // marks its clock would set are that line's to say.
        val test = testOf(session.program)
        val changes = day?.takeIf { test == null }?.let {
            planChanges(
                it,
                progressions(
                    it,
                    session.sets,
                    bells,
                    bands,
                    pairs,
                    setStore.restSeconds.value,
                    before = sessionStore.sessions.value,
                ),
            )
        }
        // 15:15's snatches, counted on the interval side, count beside the sessions' sets.
        val alongside = intervals.workouts.value.snatchSets
        // The weeks before this one: the session is not yet among the finished.
        val grip = gripLoad(
            sessionStore.sessions.value.flatMap { it.sets } + alongside.sets,
            intervals.sportDays.value.filter { it.grappling }.map { it.epochDay },
            timestamps.dayNumber(done.finishedAtEpochMillis ?: clock.nowMillis()),
            timestamps::dayNumber,
        )
        current.value = Finished(
            done,
            newRecords(sessionStore.sessions.value.flatMap { it.sets }, done),
            weekWithThis(),
            changes,
            taken = programStore.customPrograms.value.mapNotNull { it.name }.toSet(),
            bands = gearStore.bands.value,
            // Only the program followed ends: a day of another, taken in a free session, finishes nothing.
            lastDayOf = day?.program?.takeIf {
                it.key == programStore.following.value?.key && day.number >= it.totalDays
            },
            toppedOut = day?.let {
                toppedOut(it, session.sets, bells, bands, pairs, setStore.restSeconds.value)
            }.orEmpty(),
            heavyGrip = grip.heavyDay(done.gripLoad),
            handCare = done.wantsHandCare,
            paces = newPaces(sessionStore.sessions.value, done, alongside),
            milestones = milestonesIn(sessionStore.sessions.value, done, alongside.sets),
            snatchTest = if (test != null) {
                null
            } else {
                newBest(
                    BuiltInExercise.Snatch,
                    SNATCH_TEST_MINUTES,
                    done,
                    before = snatchTest(sessionStore.sessions.value, alongside)?.best,
                )
            },
            armorBuilding = if (test != null) {
                null
            } else {
                newBest(BuiltInExercise.ArmorBuildingComplex, ABC_MINUTES, done)
            },
            densities = day?.takeIf { test == null }
                ?.let { densityDone(it, session.sets, sessionStore.sessions.value) }.orEmpty(),
            test = test?.let { testResult(it, session.sets, sessionStore.sessions.value) },
        )
        sessionStore.finishSession(title)
        val now = done.finishedAtEpochMillis ?: clock.nowMillis()
        health.record(
            done.healthWorkout(now, intervals.sessionCalories(done.elapsedSeconds(now)), healthTitle ?: title),
        )
        return true
    }

    /** How hard the finished session felt, from the summary; kept with it at once. */
    fun setEffort(effort: PerceivedEffort?) {
        reflect { it.copy(session = it.session.copy(effort = effort)) }
    }

    /** The summary's note as typed; kept with the session at once, trimmed. */
    fun setNote(note: String) {
        val typed = note.take(PerceivedEffort.NOTE_MAX_LENGTH)
        reflect { it.copy(note = typed, session = it.session.copy(note = typed.trim().ifEmpty { null })) }
    }

    /** The summary's effort and note in words, for the health store's note: the screen words them. */
    fun describeForHealth(notes: String?) {
        health.describe(notes)
    }

    /**
     * Reads the finished session's heart rate from the health store, for its summary, unless it has it; kept with
     * the session once found. The screen asks as the summary comes into view: a watch may not have synced yet, and
     * the session's page asks again.
     */
    suspend fun readHeartRate() {
        val done = current.value?.session?.takeIf { it.averageBpm == null } ?: return
        val end = done.finishedAtEpochMillis ?: return
        val heartRate = health.heartRate(done.startedAtEpochMillis, end) ?: return
        current.value = current.value?.takeIf { it.session.uid == done.uid }?.let {
            it.copy(session = it.session.copy(averageBpm = heartRate.averageBpm, peakBpm = heartRate.peakBpm))
        } ?: return
        sessionStore.keepHeartRate(done.uid, heartRate)
    }

    /**
     * Keeps the finished session's changes, and the steps up at [nudges], in their program, as far as [scope]
     * reaches, or in a copy named [copyName]: see [ProgramStore.saveChanges].
     */
    fun keepChanges(scope: SaveScope, copyName: String, nudges: Set<Int> = emptySet()) {
        val done = current.value ?: return
        val changes = done.changes?.withNudges(nudges)?.takeIf { it.changes.isNotEmpty() } ?: return
        if (done.kept != null) return
        current.value = done.copy(kept = programStore.saveChanges(changes, scope, copyName))
    }

    private fun reflect(change: (Finished) -> Finished) {
        val done = current.value?.let(change) ?: return
        current.value = done
        sessionStore.reflectOnSession(done.session.uid, done.session.effort, done.note)
    }

    /**
     * The most reps of [exercise] in any [minutes] of [done], when it beat the best [before] it (every session's, by
     * default); else null.
     */
    private fun newBest(
        exercise: Exercise,
        minutes: Int,
        done: LiftSession,
        before: Int? = mostInWindow(exercise, sessionStore.sessions.value, minutes),
    ): Int? {
        val now = mostInWindow(exercise, listOf(done), minutes) ?: return null
        return now.takeIf { it > (before ?: 0) }
    }

    /** The week as it stands with this session in it, the interval workouts counted too, as Lift's week card counts. */
    private fun weekWithThis(): WeekStanding {
        val today = timestamps.dayNumber(clock.nowMillis())
        val days = sessionStore.sessions.value.mapNotNull { it.finishedAtEpochMillis }.map(timestamps::dayNumber) +
            intervals.workouts.value.map { timestamps.dayNumber(it.finishedAtEpochMillis) } +
            intervals.sportDays.value.map { it.epochDay } +
            today
        return weekStanding(days, today, intervals.weeklyGoal.value)
    }
}

/**
 * The health store's copy of the session last finished, for the life of the process rather than of its summary:
 * written as it finishes ([record]), then again with the summary's words for it a second after they stop changing
 * ([describe]), whether or not the summary is still open; only the process ending within that second loses them.
 * A store keeps a record written again under its id only at a higher version: each is written at the time it is.
 */
class SessionHealth(
    private val health: HealthStore,
    private val clock: WallClock,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val settleMillis: Long = SETTLE_MILLIS,
) {
    private var recorded: HealthWorkout? = null
    private var describing: Job? = null

    fun record(workout: HealthWorkout) {
        describing?.cancel()
        recorded = workout
        health.record(workout)
    }

    /** The finished session's [notes] as the summary words them; written once they stop changing. */
    fun describe(notes: String?) {
        val workout = recorded ?: return
        describing?.cancel()
        if (notes == workout.notes) return
        describing = scope.launch {
            delay(settleMillis)
            val described = workout.copy(notes = notes, version = clock.nowMillis())
            recorded = described
            health.record(described)
        }
    }

    /** The heart rate the store has between [startEpochMillis] and [endEpochMillis]: see [HealthStore.heartRate]. */
    suspend fun heartRate(startEpochMillis: Long, endEpochMillis: Long): HeartRate? =
        health.heartRate(startEpochMillis, endEpochMillis)

    fun close() = scope.cancel()

    private companion object {
        /** Long enough for a note being typed to settle, short enough that leaving straight after keeps it. */
        const val SETTLE_MILLIS = 1_000L
    }
}

/**
 * The lifts of [done] whose reps ever crossed a milestone in it over the [before] sessions and the sets [alongside]
 * them (15:15's snatches), a swing's once.
 */
private fun milestonesIn(
    before: List<LiftSession>,
    done: LiftSession,
    alongside: List<SetLog>,
): List<Pair<Exercise, Int>> {
    val earlier = before.flatMap { it.sets } + alongside
    return done.sets.map { it.exercise }.distinctBy {
        if (it ==
            BuiltInExercise.OneHandSwing
        ) {
            BuiltInExercise.TwoHandSwing
        } else {
            it
        }
    }
        .mapNotNull { exercise ->
            crossedMilestone(lifetimeReps(exercise, earlier), lifetimeReps(exercise, earlier + done.sets))
                ?.let { exercise to it }
        }
}
