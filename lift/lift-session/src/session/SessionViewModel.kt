package dev.justinsmith.lift.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.lift.data.ExerciseFilterStore
import dev.justinsmith.lift.data.ExerciseStore
import dev.justinsmith.lift.data.GearStore
import dev.justinsmith.lift.data.ProgramStore
import dev.justinsmith.lift.data.SessionStore
import dev.justinsmith.lift.data.SetStore
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.domain.RestClock
import dev.justinsmith.lift.domain.Side
import dev.justinsmith.lift.domain.offeredBands
import dev.justinsmith.lift.domain.standardBells
import dev.justinsmith.lift.library.ExerciseDetailUiState
import dev.justinsmith.lift.library.exerciseDetail
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The session in progress: starts one when opened if none is, logs sets (from the plan, the sheet or an EMOM),
 * and finishes or discards it. Its parts are its own: the sheet's [draft], today's [plan] and the [summary]; its
 * rest, EMOM and hold are the process's [timers], which it starts and stops and shows. Its state follows what is
 * stored as it changes, the sets before this session flattened on [computation]; [tickMillis] is how often the
 * session's own clocks (its time, the time since the last set) move.
 */
class SessionViewModel(
    private val sessionStore: SessionStore,
    private val setStore: SetStore,
    private val gearStore: GearStore,
    private val programStore: ProgramStore,
    private val exerciseStore: ExerciseStore,
    private val filters: ExerciseFilterStore,
    val plan: TodayPlanEditor,
    val summary: FinishSession,
    private val timers: SessionTimers,
    timestamps: TimestampFormatter,
    clock: WallClock,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    tickMillis: Long = TICK_MILLIS,
    computation: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    /**
     * The sets of the sessions finished before this one, as they are stored, not as when it opened: flattened off the
     * main thread, as the sessions change, the first time on the caller.
     */
    private val before: StateFlow<Before> = sessionStore.sessions.map(Before::of).flowOn(computation)
        .stateIn(viewModelScope, SharingStarted.Eagerly, Before.of(sessionStore.sessions.value))

    /** The Log sheet's draft, and a set logged opened on it to change. */
    val draft = SetDraftEditor {
        DraftContext(
            bells = gearStore.bells.value.ifEmpty { standardBells },
            bar = gearStore.bar.value,
            bands = offeredBands(gearStore.bands.value),
            history = before.value.oldestFirst + sessionStore.active.value?.sets.orEmpty(),
            logged = sessionStore.active.value?.sets.orEmpty(),
            made = exerciseStore.customExercises.value,
        )
    }.also {
        if (sessionStore.active.value == null) sessionStore.startSession()
        // A session resumed goes on where it left off: the sheet as it was, if a killed process left it half set,
        // else its last set, the other side for a one-sided lift.
        it.start(
            owned = gearStore.bells.value,
            restored = savedState.restoredDraft(exerciseStore.customExercises.value, gearStore.bands.value),
            last = sessionStore.active.value?.sets?.lastOrNull(),
        )
    }

    /** Whether a rest has started since this session opened here: it asks to show notifications from then on. */
    private val rested = MutableStateFlow(false)

    /** The exercise whose page is open, by its key. */
    private val opened = MutableStateFlow<String?>(null)

    private val detail: Flow<ExerciseDetailUiState?> =
        combine(opened, sessionStore.sessions, exerciseStore.customExercises) { key, sessions, made ->
            key?.let { Exercise.parse(it, made) }?.let { exerciseDetail(it, sessions, timestamps) }
        }

    // The records and the lifts done lately scan every set ever logged: worked out again as a set is logged, not
    // at each tap on the sheet.
    private val logged: Flow<Logged> =
        combine(before, sessionStore.active, exerciseStore.customExercises, Logged::of)

    private val inputs: Flow<Inputs> = combine(
        combine(logged, programStore.programDay, ::Pair),
        combine(gearStore.bells, gearStore.bands, gearStore.bar, ::Triple),
        combine(draft.draft, draft.editing, summary.finished, ::Triple),
        timers.state.map { it.emom to it.hold }.distinctUntilChanged(),
        filters.filters,
    ) { (logged, day), (owned, bands, bar), (draft, editing, finished), runs, narrowed ->
        Inputs(logged, day, owned, bands, bar, draft, editing, finished, runs, narrowed)
    }

    // On the main thread, so a tap on the sheet shows at once: what scans the history is worked out apart, above.
    val state: StateFlow<SessionUiState> =
        combine(inputs.map(Inputs::state), detail, rested) { state, page, rested ->
            state.copy(detail = page, asksToShowNotifications = rested)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, inputsNow.state())

    /** The wall clock, once a tick, for the session's own clocks: they move with no timer running. */
    private val ticks = flow {
        while (true) {
            emit(clock.nowMillis())
            delay(tickMillis)
        }
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)

    /** The clocks, read at each tick of the session's own and of its timers. */
    val clocks: StateFlow<SessionClocks> =
        combine(sessionStore.active, ticks, timers.state, ::clocksAt)
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Eagerly, SessionClocks())

    /** What is up next on today's plan as things stand now, not as last drawn; null in a free session. */
    private val upNextNow: UpNext?
        get() = plan.next(sessionStore.active.value, before.value.oldestFirst, draft.draft.value)

    init {
        // The timer a killed process left running goes on, read once: the session re-emits at each write of it.
        // Here, not as the timers are made, as only a screen on show may start Android's foreground service:
        // the process can come up behind a receiver or a service, where starting it would throw.
        sessionStore.active.value?.timer?.let(timers::restore)
        // The draft for a new set is kept, not one of a set being changed: the last kept is the new set's.
        viewModelScope.launch {
            draft.draft.collect { if (draft.editing.value == null) savedState.keep(it) }
        }
        // From the first rest on: rested once, the screen asks once.
        viewModelScope.launch {
            timers.state.collect { if (it.rest != null) rested.value = true }
        }
        // A hold the timers logged goes on with the other side, as a set logged here does.
        viewModelScope.launch {
            timers.heldSets.collect(draft::flipSide)
        }
        // An exercise added from the sheet's search is the one the sheet goes on with.
        viewModelScope.launch {
            exerciseStore.customExercises.collect(draft::exercisesChanged)
        }
    }

    /** Logs [set], by default the sheet's: a hold for [seconds], fewer when a timed one is stopped early. */
    fun logSet(set: SetDraft = draft.draft.value, seconds: Int = set.seconds) {
        setStore.logSet(
            set.exercise,
            set.load,
            if (set.held) 0 else set.reps,
            set.side,
            seconds = seconds.takeIf { set.held },
        )
        draft.flipSide(set)
        timers.startRest(setStore.restSeconds.value)
    }

    /** Logs the plan's next set as planned, with its suggested load: a hold for its seconds. */
    fun logNext() {
        val next = upNextNow ?: return
        val set = next.set
        setStore.logSet(
            set.exercise,
            next.load,
            if (set.held) 0 else set.reps,
            set.side,
            seconds = set.reps.takeIf { set.held },
        )
        timers.startRest(setStore.restSeconds.value)
    }

    /** Saves the set being changed as the sheet's draft now has it. */
    fun saveEdit() {
        draft.takeEdit()?.let(setStore::updateSet)
    }

    /**
     * Makes rests a [step] (−1 or +1) of [RestClock.STEP_SECONDS] shorter or longer: the one running, if one is,
     * and every one after.
     */
    fun stepRest(step: Int) {
        val seconds = (setStore.restSeconds.value + step * RestClock.STEP_SECONDS)
            .coerceIn(RestClock.MIN_SECONDS, RestClock.MAX_SECONDS)
        setStore.setRestSeconds(seconds)
        timers.adjustRest(seconds)
    }

    /** Steps the next set's load one [step] (−1 or +1), for the rest of its block today: see [TodayPlanEditor]. */
    fun stepNextLoad(step: Int) {
        plan.stepNextLoad(step, before.value.oldestFirst, draft.draft.value)
    }

    /**
     * The Log sheet opening: on a program day it starts on the set up next, as planned, unless it was opened on
     * that same set before and may have been changed since.
     */
    fun openLogSheet() {
        upNextNow?.let(draft::openOn)
    }

    /** Opens the page of the exercise [key] names, or closes the one open with null. */
    fun openExercise(key: String?) {
        opened.value = key
    }

    /** Starts the plan's next block as an EMOM, if it is one. */
    fun startPlannedEmom() {
        val next = upNextNow ?: return
        val minutes = next.emomMinutes ?: return
        if (next.set.held) return
        val set = next.set
        timers.startEmom(set.exercise, next.load, set.reps, minutes, set.side ?: Side.Left)
    }

    /** Starts timing the plan's next set when it is a hold, putting it on the sheet first. */
    fun startPlannedHold() {
        val next = upNextNow?.takeIf { it.set.held } ?: return
        draft.openOn(next, again = true)
        timers.startHold(draft.draft.value)
    }

    /** Starts an EMOM of the sheet's lift, load and reps for its minutes. */
    fun startDraftEmom() {
        val d = draft.draft.value
        if (d.held) return
        timers.startEmom(d.exercise, d.load, d.reps, d.minutes, d.side)
    }

    /** Starts timing the sheet's hold: the other side of the one just done, if that is on offer. */
    fun startHold() {
        timers.startHold(draft.draft.value)
    }

    /** Leaves the session for later: its timers stop, its sets stay. */
    fun leave() {
        timers.stopAll()
    }

    /**
     * Reads the finished session's heart rate for the summary, as it comes into view: each time, until one is found,
     * as a watch may sync after the session ends. Nothing before it is finished.
     */
    fun readHeartRate() {
        viewModelScope.launch { summary.readHeartRate() }
    }

    /**
     * Finishes the session for the [summary], as [title] (and [healthTitle] in the health store): see
     * [FinishSession.finish]. Its timers stop either way. True when it was saved, and the summary follows.
     */
    fun finish(title: String, healthTitle: String? = null): Boolean {
        timers.stopAll()
        return summary.finish(title, healthTitle)
    }

    fun discard() {
        timers.stopAll()
        sessionStore.discardSession()
    }

    /** What the state is worked out from, as stored now: the first state's. */
    private val inputsNow: Inputs
        get() = Inputs(
            Logged.of(before.value, sessionStore.active.value, exerciseStore.customExercises.value),
            programStore.programDay.value,
            gearStore.bells.value,
            gearStore.bands.value,
            gearStore.bar.value,
            draft.draft.value,
            draft.editing.value,
            summary.finished.value,
            timers.state.value.let { it.emom to it.hold },
            filters.filters.value,
        )

    private companion object {
        const val TICK_MILLIS = 250L
    }
}
