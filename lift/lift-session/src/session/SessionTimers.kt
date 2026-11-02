package dev.justinsmith.lift.session

import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.lift.data.SetStore
import dev.justinsmith.lift.domain.Band
import dev.justinsmith.lift.domain.EmomClock
import dev.justinsmith.lift.domain.EmomState
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.domain.HoldPhase
import dev.justinsmith.lift.domain.HoldState
import dev.justinsmith.lift.domain.RestState
import dev.justinsmith.lift.domain.SavedTimer
import dev.justinsmith.lift.domain.Side
import dev.justinsmith.lift.platform.ActiveTimers
import dev.justinsmith.lift.platform.Beeper
import dev.justinsmith.lift.platform.SessionKeepAlive
import dev.justinsmith.lift.platform.TimerRun
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

/**
 * The two clocks a run of the session's timers reads: [now], monotonic time from [timeSource] (counting through the
 * device's sleep, and deaf to a change of the wall clock), which times it, and [epochNow], the [wall] clock, which
 * tells the platform when it ends.
 */
class RunClock(private val wall: WallClock, timeSource: TimeSource.WithComparableMarks) {
    private val origin = timeSource.markNow()

    /** Milliseconds since this clock was made. */
    fun now(): Long = origin.elapsedNow().inWholeMilliseconds

    fun epochNow(): Long = wall.nowMillis()
}

/** The session's timers at one tick: [now] on the [RunClock], and the rest, EMOM and hold, any of which may run. */
data class TimerState(
    val now: Long = 0L,
    val rest: RestRun? = null,
    val emom: EmomRun? = null,
    val hold: HoldTiming = HoldTiming(),
) {
    val restState: RestState? get() = rest?.let { it.clock.at(now - it.startedAtMillis) }
    val emomState: EmomState? get() = emom?.let { EmomClock(it.minutes).at(now - it.startedAtMillis, it.done) }
    val holdState: HoldState? get() = hold.run?.let { it.clock.at(now - it.startedAtMillis) }
}

/**
 * The session's timers: the rest after a set, an EMOM and a hold, one at a time. They live as long as the process,
 * not the session's screen, so they keep time, beep and log while the app is off screen; the platform keeps the
 * run going there ([keepAlive]). While a run goes they tick every [TICK_MILLIS] in [scope], on the main thread,
 * where the screen's actions on them run too; with none going nothing ticks.
 *
 * An EMOM's sets and a hold are logged here as they are done, a hold then resting as a set logged does; the screen
 * hears of a hold logged through [heldSets]. The session ends them ([stopAll]) as it finishes, is discarded or is
 * left, and Android's keep-alive as the app is swiped away.
 *
 * The run going is kept on the session in progress at each change of course ([SetStore.saveTimer]), so a
 * process killed in it can go on with it: the session's screen [restore]s it, timed by the wall clock.
 */
class SessionTimers(
    private val setStore: SetStore,
    clock: WallClock,
    timeSource: TimeSource.WithComparableMarks,
    beeper: Beeper,
    private val keepAlive: SessionKeepAlive,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : ActiveTimers {
    private val time = RunClock(clock, timeSource)

    private val rest = RestTimer(time, beeper)

    private val emom = EmomRunner(time, beeper) { run, minute ->
        setStore.logSet(run.exercise, run.bellKg, run.reps, run.sideOf(minute), run.double, band = run.band)
    }

    private val holds = HoldTimer(time, beeper) { set, seconds -> logHeld(set, seconds) }

    private val current = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = current.asStateFlow()

    private val held = MutableSharedFlow<SetDraft>(extraBufferCapacity = 1)

    /** Each hold logged, as the sheet had it. */
    val heldSets: SharedFlow<SetDraft> = held.asSharedFlow()

    /** The run the platform keeps, so it hears of each change once. */
    private var kept: TimerRun? = null

    /** The run as the session keeps it, so it is written at each change of course rather than each tick. */
    private var saved: SavedTimer? = null
    private var ticking: Job? = null

    /** Starts a rest of [seconds] from now, in place of any already running. */
    fun startRest(seconds: Int) {
        rest.start(seconds)
        changed()
    }

    /** Makes the rest running [seconds] long, from when it started. */
    fun adjustRest(seconds: Int) {
        rest.adjust(seconds)
        changed()
    }

    fun skipRest() {
        rest.skip()
        changed()
    }

    /** Starts an EMOM, ending the rest: the keep-alive goes on from the one to the other. */
    @Suppress("detekt.LongParameterList") // The EMOM's own.
    fun startEmom(
        exercise: Exercise,
        bellKg: Double,
        reps: Int,
        minutes: Int,
        firstSide: Side,
        double: Boolean,
        band: Band? = null,
    ) {
        rest.skip()
        emom.start(exercise, bellKg, reps, minutes, firstSide, double, band)
        changed()
    }

    /** Logs this minute's set: the EMOM rests out the minute. */
    fun emomSetDone() {
        emom.setDone()
        changed()
    }

    fun stopEmom() {
        emom.stop()
        changed()
    }

    /** Starts timing [set], ending the rest: the other side of the hold just done, if that is on offer. */
    fun startHold(set: SetDraft) {
        rest.skip()
        holds.start(set)
        changed()
    }

    /** Stops the hold early, logging the whole seconds held. */
    fun stopHold() {
        holds.stop()
        changed()
    }

    /** Stops the hold without logging it. */
    fun cancelHold() {
        holds.cancel()
        changed()
    }

    override fun stopAll() {
        emom.stop()
        holds.cancel()
        rest.skip()
        changed()
    }

    /**
     * Goes on with [timer], the one a process killed in the session left running, from where the wall clock says
     * it is now: the time away plays no cue. One already over is finished, never played out: it logs nothing and
     * beeps nothing, and the session keeps it no longer. Nothing while a run goes here already.
     */
    fun restore(timer: SavedTimer) {
        if (current.value.running != null) return
        val elapsed = (time.epochNow() - timer.startedAtEpochMillis).coerceAtLeast(0L)
        val startedAt = time.now() - elapsed
        saved = timer
        when (timer) {
            is SavedTimer.Rest -> RestRun(startedAt, timer.seconds, timer.startedAtEpochMillis)
                .takeUnless { it.clock.isDone(elapsed) }
                ?.let(rest::resume)

            is SavedTimer.Emom -> EmomRun(
                timer.exercise,
                timer.bellKg,
                timer.reps,
                timer.minutes,
                timer.firstSide,
                startedAt,
                timer.done,
                timer.double,
                timer.band,
                timer.startedAtEpochMillis,
            )
                .takeUnless { EmomClock(it.minutes).isOver(elapsed) }
                ?.let(emom::resume)

            is SavedTimer.Hold -> HoldRun(timer.draft(), startedAt, timer.firstSide, timer.startedAtEpochMillis)
                .takeUnless { it.clock.at(elapsed).phase == HoldPhase.Done }
                ?.let(holds::resume)
        }
        changed()
    }

    /** The process is going: nothing ticks again. */
    fun close() = scope.cancel()

    private fun logHeld(set: SetDraft, seconds: Int) {
        setStore.logSet(set.exercise, set.loadKg, 0, set.side, set.doubled, seconds = seconds, band = set.band)
        rest.start(setStore.restSeconds.value)
        held.tryEmit(set)
    }

    private fun tick() {
        val now = time.now()
        rest.tick(now)
        holds.tick(now)
        emom.tick(now)
        changed()
    }

    /** Publishes where the timers stand, tells the platform of a run started or ended, and ticks while one goes. */
    private fun changed() {
        val now = TimerState(time.now(), rest.state.value, emom.state.value, holds.state.value)
        current.value = now
        val keep = now.keeping
        if (keep != saved) {
            saved = keep
            setStore.saveTimer(keep)
        }
        val run = now.running
        if (run != kept) {
            kept = run
            if (run == null) keepAlive.stop() else keepAlive.start(run)
        }
        if (run == null) {
            ticking?.cancel()
            ticking = null
        } else if (ticking?.isActive != true) {
            ticking = scope.launch {
                while (isActive) {
                    delay(TICK_MILLIS)
                    tick()
                }
            }
        }
    }

    companion object {
        const val TICK_MILLIS = 250L
        internal const val MILLIS_PER_SECOND = 1_000L
    }
}

/** The Log sheet's set this hold logs. */
private fun SavedTimer.Hold.draft() = SetDraft(
    exercise = exercise,
    bellKg = bellKg,
    side = side,
    double = double,
    addedKg = addedKg,
    barKg = barKg,
    seconds = seconds,
    band = band,
)

/** The run going, as the platform keeps it; at most one is. */
private val TimerState.running: TimerRun? get() = rest?.let {
    TimerRun.Rest(it.startedAtEpochMillis, it.startedAtEpochMillis + it.seconds * SessionTimers.MILLIS_PER_SECOND)
} ?: emom?.let {
    TimerRun.Emom(it.startedAtEpochMillis, it.minutes)
} ?: hold.run?.let {
    TimerRun.Hold(it.startedAtEpochMillis, it.set.seconds)
}

/** The run going, as the session keeps it. */
private val TimerState.keeping: SavedTimer? get() = rest?.let {
    SavedTimer.Rest(it.startedAtEpochMillis, it.seconds)
} ?: emom?.let {
    SavedTimer.Emom(
        it.startedAtEpochMillis,
        it.exercise,
        it.bellKg,
        it.reps,
        it.minutes,
        it.firstSide,
        it.double,
        it.band,
        it.done,
    )
} ?: hold.run?.let {
    val set = it.set
    SavedTimer.Hold(
        it.startedAtEpochMillis,
        set.exercise,
        set.bellKg,
        set.addedKg,
        set.barKg,
        set.side,
        set.double,
        set.seconds,
        set.band,
        it.firstSide,
    )
}
