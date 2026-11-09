package dev.justinsmith.lift.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.lift.data.ExerciseFilterStore
import dev.justinsmith.lift.data.ExerciseStore
import dev.justinsmith.lift.data.GearStore
import dev.justinsmith.lift.data.IntervalActivity
import dev.justinsmith.lift.data.ProgramStore
import dev.justinsmith.lift.data.SessionStore
import dev.justinsmith.lift.data.SetStore
import dev.justinsmith.lift.data.snatchSets
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.domain.Load
import dev.justinsmith.lift.domain.Program
import dev.justinsmith.lift.domain.RestClock
import dev.justinsmith.lift.domain.Side
import dev.justinsmith.lift.domain.builtInPrograms
import dev.justinsmith.lift.domain.densityBest
import dev.justinsmith.lift.domain.firstAtBell
import dev.justinsmith.lift.domain.offeredBands
import dev.justinsmith.lift.domain.pickedDayNumber
import dev.justinsmith.lift.domain.sets
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
 * The session in progress, which whatever opens its page has started ([SessionStore.startSession]): it logs sets (from
 * the plan, the sheet, an EMOM or a density block), and finishes or discards it, and starts none itself. Its parts are
 * its own: the sheet's [draft], today's [plan] and the [summary]; its rest, EMOM and hold are the process's [timers],
 * which it starts and stops and shows. Its state follows what is stored as it changes, the sets before this session
 * flattened on [computation]; [tickMillis] is how often the session's own clocks (its time, the time since the last
 * set) move.
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
    val easierDay: EasierDay,
    private val timers: SessionTimers,
    intervals: IntervalActivity,
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

    /**
     * Whether the page came back from a killed process with no session behind it: the one it showed was finished
     * or dropped before the process went, so there is nothing here to go on with, and the page closes. Never
     * true of a page just opened, whose session may still be on its way to the store.
     */
    val sessionGone: Boolean = savedState.openedBefore() && sessionStore.active.value == null

    /** Whether the page is opening for the first time, not coming back from a killed process: it may speak at once. */
    private val openedFresh: Boolean = !savedState.openedBefore()

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
        combine(opened, sessionStore.sessions, exerciseStore.customExercises, intervals.workouts) {
                key,
                sessions,
                made,
                workouts,
            ->
            key?.let { Exercise.parse(it, made) }?.let { exerciseDetail(it, sessions, timestamps, workouts.snatchSets) }
        }

    // The records and the lifts done lately scan every set ever logged: worked out again as a set is logged, not
    // at each tap on the sheet.
    private val logged: Flow<Logged> =
        combine(before, sessionStore.active, exerciseStore.customExercises, Logged::of)

    private val inputs: Flow<Inputs> = combine(
        combine(logged, programStore.programDay, programStore.customPrograms, ::Triple),
        combine(gearStore.bells, gearStore.bands, gearStore.bar, setStore.restSeconds, ::Gear),
        combine(draft.draft, draft.editing, summary.finished, ::Triple),
        timers.state.map { Triple(it.emom, it.hold, it.density) }.distinctUntilChanged(),
        filters.filters,
    ) { (logged, day, custom), gear, (draft, editing, finished), runs, narrowed ->
        Inputs(logged, day, builtInPrograms + custom, gear, draft, editing, finished, runs, narrowed)
    }

    // On the main thread, so a tap on the sheet shows at once: what scans the history is worked out apart, above.
    val state: StateFlow<SessionUiState> =
        combine(inputs.map(Inputs::state), detail, rested, easierDay.offer) { state, page, rested, easier ->
            state.copy(detail = page, asksToShowNotifications = rested, easier = easier)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, inputsNow.state())

    /**
     * The set coming, as the timers hear of it: with its bell when it is a ballistic lift's first at a new one, by
     * every set before it, this session's and 15:15's snatches too.
     */
    private val comingSet: Flow<ComingSet?> = combine(
        state.map { it.setComing }.distinctUntilChanged(),
        before,
        sessionStore.active,
        intervals.workouts,
        gearStore.bells,
    ) { coming, before, active, workouts, owned ->
        coming?.let { (exercise, load) ->
            val history = before.oldestFirst + active?.sets.orEmpty() + workouts.snatchSets.sets
            val bell = (load as? Load.Bell)?.takeIf {
                exercise.firstAtBell(it, owned.ifEmpty { standardBells }, history)
            }
            ComingSet(exercise, bell)
        }
    }.distinctUntilChanged()

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
        savedState.markOpened()
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
        // The timers say the set coming in a rest's last seconds, the plan's next or the Next set card's, or a safety
        // line due in its place. A session opening fresh, before its first set, says the one due at once: a program
        // day's on its first set, a free one's only on a taxed day, since its card's lift may not be the one done.
        viewModelScope.launch {
            var opening = openedFresh
            // The session landing is an input of its own: on a device it reaches the store after the page opens,
            // and the set coming may read the same before and after.
            combine(
                comingSet,
                easierDay.taxed.distinctUntilChanged(),
                sessionStore.active.map { it != null }.distinctUntilChanged(),
                ::Triple,
            ).collect { (coming, taxed, _) ->
                timers.upNext(coming)
                timers.taxed(taxed)
                val active = sessionStore.active.value
                if (opening && active != null) {
                    opening = false
                    if (active.sets.isEmpty()) timers.opened(grooving = !state.value.free)
                }
            }
        }
        // An exercise added from the sheet's search is the one the sheet goes on with.
        viewModelScope.launch {
            exerciseStore.customExercises.collect(draft::exercisesChanged)
        }
    }

    /** Logs [set], by default the sheet's: a hold for [seconds], fewer when a timed one is stopped early. */
    fun logSet(set: SetDraft = draft.draft.value, seconds: Int = set.seconds) {
        val rest = upNextNow?.set?.takeIf { it.exercise.key == set.exercise.key }?.rest
        setStore.logSet(
            set.exercise,
            set.load,
            if (set.held) 0 else set.reps,
            set.side,
            seconds = seconds.takeIf { set.held },
        )
        draft.flipSide(set)
        timers.startRest(rest ?: setStore.restSeconds.value, after = set.exercise)
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
        timers.startRest(set.rest ?: setStore.restSeconds.value, after = set.exercise)
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

    /**
     * Makes this free session day [index] (from 0) of [program]: its sets are up next from here, on today's plan, and
     * the session is logged as the day [Program.pickedDayNumber] says, by where the program followed stands.
     */
    fun pickDay(program: Program, index: Int) {
        setStore.setProgramDay(program.key, program.pickedDayNumber(index, programStore.programDay.value))
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

    /**
     * Starts the plan's next block on its clock: an EMOM for its minutes, or rounds in a time cap against the
     * block's best. Nothing when the next set is neither, or a hold.
     */
    fun startPlannedClock() {
        val next = upNextNow?.takeUnless { it.set.held } ?: return
        val set = next.set
        val side = set.side ?: Side.Left
        val emom = next.emomMinutes
        if (emom != null) {
            timers.startEmom(set.exercise, next.load, set.reps, emom, side)
        } else {
            val minutes = next.densityMinutes ?: return
            val day = state.value.programDay
            val best = day?.today?.blocks?.getOrNull(next.block)?.let {
                densityBest(it.block, day.program, sessionStore.sessions.value)
            }
            timers.startDensity(set.exercise, next.load, set.reps, minutes, side, best)
        }
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
            programStore.programs,
            Gear(gearStore.bells.value, gearStore.bands.value, gearStore.bar.value, setStore.restSeconds.value),
            draft.draft.value,
            draft.editing.value,
            summary.finished.value,
            timers.state.value.let { Triple(it.emom, it.hold, it.density) },
            filters.filters.value,
        )

    private companion object {
        const val TICK_MILLIS = 250L
    }
}
