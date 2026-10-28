package dev.justinsmith.bells.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.bells.data.BellsRepository
import dev.justinsmith.bells.data.ExerciseFilterStore
import dev.justinsmith.bells.data.IntervalActivity
import dev.justinsmith.bells.domain.Band
import dev.justinsmith.bells.domain.Block
import dev.justinsmith.bells.domain.BuiltInExercise
import dev.justinsmith.bells.domain.DEFAULT_BAR_KG
import dev.justinsmith.bells.domain.DEFAULT_BELL_KG
import dev.justinsmith.bells.domain.EmomClock
import dev.justinsmith.bells.domain.EmomState
import dev.justinsmith.bells.domain.Equipment
import dev.justinsmith.bells.domain.Exercise
import dev.justinsmith.bells.domain.ExerciseFilters
import dev.justinsmith.bells.domain.HoldState
import dev.justinsmith.bells.domain.LiftSession
import dev.justinsmith.bells.domain.MAX_BAR_KG
import dev.justinsmith.bells.domain.Measure
import dev.justinsmith.bells.domain.PersonalRecord
import dev.justinsmith.bells.domain.PlanChanges
import dev.justinsmith.bells.domain.PlanRow
import dev.justinsmith.bells.domain.PlannedSet
import dev.justinsmith.bells.domain.Program
import dev.justinsmith.bells.domain.ProgramDay
import dev.justinsmith.bells.domain.ProgramKey
import dev.justinsmith.bells.domain.RestClock
import dev.justinsmith.bells.domain.RestState
import dev.justinsmith.bells.domain.SaveScope
import dev.justinsmith.bells.domain.SessionPlan
import dev.justinsmith.bells.domain.SetLog
import dev.justinsmith.bells.domain.Side
import dev.justinsmith.bells.domain.bands
import dev.justinsmith.bells.domain.newRecords
import dev.justinsmith.bells.domain.nextPlanned
import dev.justinsmith.bells.domain.nextSet
import dev.justinsmith.bells.domain.offeredBands
import dev.justinsmith.bells.domain.offeredExercises
import dev.justinsmith.bells.domain.other
import dev.justinsmith.bells.domain.ownedEquipment
import dev.justinsmith.bells.domain.planChanges
import dev.justinsmith.bells.domain.planRows
import dev.justinsmith.bells.domain.plannedBand
import dev.justinsmith.bells.domain.progressions
import dev.justinsmith.bells.domain.recentExercises
import dev.justinsmith.bells.domain.recordSetIds
import dev.justinsmith.bells.domain.setsOldestFirst
import dev.justinsmith.bells.domain.standardBells
import dev.justinsmith.bells.domain.steppedBand
import dev.justinsmith.bells.domain.steppedKg
import dev.justinsmith.bells.domain.suggestedBand
import dev.justinsmith.bells.domain.suggestedBar
import dev.justinsmith.bells.domain.suggestedBell
import dev.justinsmith.bells.domain.suggestedLoad
import dev.justinsmith.bells.domain.toppedOut
import dev.justinsmith.bells.library.ExerciseDetailUiState
import dev.justinsmith.bells.library.LibraryList
import dev.justinsmith.bells.library.exerciseDetail
import dev.justinsmith.bells.platform.Beeper
import dev.justinsmith.bells.platform.RestAlerts
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.timer.WeekStanding
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.timer.weekStanding
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The set the Log sheet will log, kept between sets so the next starts where the last left off. Each kind of
 * load keeps its own value, so going from a bell to a band and back finds the bell where it was.
 */
data class SetDraft(
    val exercise: Exercise = BuiltInExercise.TwoHandSwing,
    val bellKg: Double = DEFAULT_BELL_KG,
    val reps: Int = DEFAULT_REPS,
    val side: Side = Side.Left,
    /** For an EMOM started from the sheet. */
    val minutes: Int = DEFAULT_MINUTES,
    /** Two bells asked for: kept while a lift that can't be doubled is picked, for the next that can. */
    val double: Boolean = false,
    /** Weight added to a bodyweight exercise; 0 for none. */
    val addedKg: Double = 0.0,
    /** The total on the bar, for a barbell lift. */
    val barKg: Double = DEFAULT_BAR_KG,
    /** How long a hold lasted. */
    val seconds: Int = DEFAULT_SECONDS,
    val band: Band = Band.Default,
) {
    /** Whether the set is done with two bells. */
    val doubled: Boolean get() = exercise.doubled(double)

    /** The weight the set is logged with: the bell, the bar as loaded, the weight added to bodyweight, or none. */
    val loadKg: Double get() = when (exercise.equipment) {
        Equipment.Kettlebell -> bellKg
        Equipment.Barbell -> barKg
        Equipment.Bodyweight -> addedKg
        Equipment.Band, Equipment.Mat -> 0.0
    }

    /** Whether the set is a hold, timed rather than counted. */
    val held: Boolean get() = exercise.measure == Measure.Time

    companion object {
        const val DEFAULT_REPS = 10
        const val DEFAULT_MINUTES = 10
        const val DEFAULT_SECONDS = 30
        const val MAX_REPS = 999
        const val MAX_MINUTES = 60

        /** A hold steps by five seconds, up to ten minutes. */
        const val SECONDS_STEP = 5
        const val MAX_SECONDS = 600
    }
}

/**
 * A session just finished, for the summary: it as saved and the records it set; for a program day changed,
 * the [changes] to offer to keep, the names the user's programs already have, and once kept, the program they
 * were [kept] in.
 */
data class Finished(
    val session: LiftSession,
    val records: List<PersonalRecord>,
    /** Where the week stands with this session in it. */
    val week: WeekStanding? = null,
    val changes: PlanChanges? = null,
    val taken: Set<String> = emptySet(),
    val kept: ProgramKey? = null,
    /** The user's bands, to name a band a change plans by. */
    val bands: List<Band> = emptyList(),
    /** The program this session finished, when it was its last day. */
    val lastDayOf: Program? = null,
    /** Its blocks done in full at the top of their range, with no heavier load to go to. */
    val toppedOut: List<Block> = emptyList(),
)

/**
 * The plan's next set: which of [total] it is, with its load (the bell, the bar as loaded, the weight added to
 * bodyweight, or none, and the [band] for band work), and whether it opens an EMOM of [emomMinutes].
 */
data class UpNext(
    val set: PlannedSet,
    val bellKg: Double,
    val number: Int,
    val total: Int,
    val emomMinutes: Int?,
    val band: Band? = null,
    /** Which of the day's blocks it is in, from 0. */
    val block: Int = 0,
    /** The last set of the lift in a session before this one, to go by; null the first time. */
    val lastTime: SetLog? = null,
)

data class SessionUiState(
    val session: LiftSession? = null,
    /** How long it has run: "14:32". */
    val elapsed: String = formatClock(0),
    /** Since the last set, for resting: "0:38"; null before the first. */
    val sinceLast: String? = null,
    /** The rest counting down after a set; null when none is. */
    val rest: RestState? = null,
    /** The bells the sheet offers: the user's own, or every standard size until they have said. */
    val bells: List<Double> = standardBells,
    val ownsBells: Boolean = false,
    /** What the user's barbell weighs empty; null until they have said. */
    val bar: Double? = null,
    /** The bands the sheet offers, lightest first. */
    val bands: List<Band> = offeredBands(bands(emptyList())),
    val draft: SetDraft = SetDraft(),
    /** Whether no program day lays the session out: the Next set card composes [draft]. */
    val free: Boolean = false,
    /**
     * In a free session: the last set of [draft]'s lift in a session before this one, to go by; null the
     * first time.
     */
    val draftLastTime: SetLog? = null,
    /** For a program day: what comes next, or null once its sets are done ([dayDone]). */
    val upNext: UpNext? = null,
    val dayDone: Boolean = false,
    /** For a program day: its blocks as today's plan has them. */
    val plan: List<PlanRow> = emptyList(),
    val emom: EmomRun? = null,
    val emomState: EmomState? = null,
    /** A hold being timed on the Log sheet. */
    val hold: HoldRun? = null,
    val holdState: HoldState? = null,
    /** The side to time next, for the same length, after the first side of a one-sided hold. */
    val otherSide: Side? = null,
    val finished: Finished? = null,
    /** A set logged, open on the Log sheet to change: the sheet's draft is it until saved or closed. */
    val editing: SetLog? = null,
    /** The sets of this session that beat a record standing as they were logged: see [recordSetIds]. */
    val recordSetIds: Set<Long> = emptySet(),
    /** The last set logged, when it is one of them: the one to announce. */
    val latestRecord: SetLog? = null,
    /** What the sheet's picker offers, the ones done lately first, and how it is narrowed. */
    val library: LibraryList = LibraryList(offeredExercises(emptyList())),
    /** The exercise whose page is open, and the user's history with it. */
    val detail: ExerciseDetailUiState? = null,
)

/**
 * The session in progress: starts one when opened if none is, logs sets (from the plan, the sheet or an
 * EMOM), and finishes or discards it. [tickMillis] is how often the clocks move and cues are checked.
 */
@Suppress("detekt.TooManyFunctions") // Every way a session changes, side by side.
class SessionViewModel(
    private val repository: BellsRepository,
    private val clock: WallClock,
    private val beeper: Beeper,
    private val alerts: RestAlerts,
    private val filters: ExerciseFilterStore,
    private val intervals: IntervalActivity,
    private val timestamps: TimestampFormatter,
    tickMillis: Long = TICK_MILLIS,
) : ViewModel() {
    private val draft = MutableStateFlow(SetDraft())

    /** Every set of the sessions before this one: what a set logged now has to beat to be a record. */
    private val setsBefore = repository.sessions.value.flatMap { it.sets }

    // The same sets oldest first, flattened once: the suggestions read them on every change of the draft.
    private val setsBeforeOldestFirst = repository.sessions.value.setsOldestFirst()
    private val finished = MutableStateFlow<Finished?>(null)

    /** Runs an EMOM, logging each minute's set as it is done. */
    private val emom = EmomRunner(clock, beeper) { run, minute ->
        repository.logSet(run.exercise, run.bellKg, run.reps, run.sideOf(minute), run.double, band = run.band)
    }

    /** Times a hold from the sheet, logging it when done. */
    private val holds = HoldTimer(clock, beeper) { set, seconds -> logSet(set, seconds) }

    /** Counts a rest down after each set logged here: an EMOM rests out its own minutes. */
    private val rest = RestTimer(clock, beeper, alerts)

    /** The time, once a tick: one clock for the clocks on screen and the cues alike. */
    private val ticks = flow {
        while (true) {
            emit(clock.nowMillis())
            delay(tickMillis)
        }
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)

    private val editing = MutableStateFlow<SetLog?>(null)

    /** The exercise whose page is open, by its key. */
    private val opened = MutableStateFlow<String?>(null)

    private val detail: Flow<ExerciseDetailUiState?> =
        combine(opened, repository.sessions, repository.customExercises) { key, sessions, made ->
            key?.let { Exercise.parse(it, made) }?.let { exerciseDetail(it, sessions, timestamps) }
        }

    /** What was up next when the Log sheet last opened on it. */
    private var sheetOpenedOn: UpNext? = null

    /** The sheet's draft for a new set, put aside while a set logged is changed. */
    private var freeDraft: SetDraft? = null

    private val runs = combine(emom.state, holds.state, editing, ::Triple)

    private val gear = combine(repository.bells, repository.bands, repository.bar, ::Triple)

    private val view = combine(draft, finished, runs, repository.customExercises, filters.filters) {
            draft,
            finished,
            (emom, timing, changing),
            made,
            narrowed,
        ->
        // The sheet never offers an archived exercise, whatever the library shows.
        View(draft, finished, emom, timing, offeredExercises(made), narrowed.copy(showArchived = false), changing)
    }

    /** Everything but the clocks, worked out again only when something changes, not every tick. */
    private val untimed: Flow<SessionUiState> =
        combine(repository.active, gear, repository.programDay, view) {
                active,
                (owned, bands, bar),
                day,
                chosen,
            ->
            val plan = active?.takeIf { it.program != null && it.program == day?.program?.key }
                ?.let { day?.copy(plan = it.plan) }
            val records = recordSetIds(setsBefore, active?.sets.orEmpty())
            SessionUiState(
                session = active,
                bells = owned.ifEmpty { standardBells },
                ownsBells = owned.isNotEmpty(),
                bar = bar,
                bands = offeredBands(bands),
                draft = chosen.draft,
                free = active != null && plan == null,
                draftLastTime = if (plan == null) lastTime(chosen.draft.exercise, chosen.draft.doubled) else null,
                upNext = plan?.let { upNext(it, active.sets) },
                dayDone = plan != null && nextPlanned(plan.plannedSets(), active.sets) == null,
                plan = plan?.planRows(active.sets).orEmpty(),
                emom = chosen.emom,
                hold = chosen.timing.run,
                otherSide = chosen.timing.offer
                    ?.takeIf { it.exercise == chosen.draft.exercise && it.side == chosen.draft.side }
                    ?.side,
                finished = chosen.finished,
                editing = chosen.editing,
                recordSetIds = records,
                latestRecord = active?.sets?.lastOrNull()?.takeIf { it.id in records },
                library = LibraryList(
                    chosen.exercises,
                    recentExercises(setsBeforeOldestFirst + active?.sets.orEmpty(), chosen.exercises),
                    chosen.filters,
                    ownedEquipment(owned, bands, bar),
                ),
            )
        }

    val state: StateFlow<SessionUiState> =
        combine(untimed, ticks, detail, rest.state) { state, now, page, resting ->
            state.at(now).copy(detail = page, rest = resting?.let { it.clock.at(now - it.startedAtMillis) })
        }
            .stateIn(viewModelScope, SharingStarted.Eagerly, SessionUiState())

    /** This state with its clocks read at [now]. */
    private fun SessionUiState.at(now: Long): SessionUiState = copy(
        elapsed = formatClock(session?.elapsedSeconds(now) ?: 0),
        sinceLast = session?.sets?.lastOrNull()?.let {
            formatClock(((now - it.loggedAtEpochMillis) / MILLIS_PER_SECOND).toInt())
        },
        emomState = emom?.let { EmomClock(it.minutes).at(now - it.startedAtMillis, it.done) },
        holdState = hold?.let { it.clock.at(now - it.startedAtMillis) },
    )

    /** The user's exercises when the sheet went to add one: the first not among them is the one added. */
    private var awaitingAdd: Set<String>? = null

    init {
        if (repository.active.value == null) repository.startSession()
        // Start from the user's own bells: the lightest they own at or above the default.
        repository.bells.value.let { owned ->
            if (owned.isNotEmpty()) {
                val bell = owned.firstOrNull { it >= DEFAULT_BELL_KG } ?: owned.last()
                draft.update { it.copy(bellKg = bell) }
            }
        }
        // A session resumed goes on where it left off: its last set, the other side for a one-sided lift.
        repository.active.value?.sets?.lastOrNull()?.let { last ->
            draft.update { it.from(last, flipSide = true) }
        }
        // Cues play from here, not the screen, so they keep time whatever is drawn.
        viewModelScope.launch {
            ticks.collect { now -> playCues(now) }
        }
        // An exercise added from the sheet's search is the one the sheet goes on with.
        viewModelScope.launch {
            repository.customExercises.collect { made ->
                val before = awaitingAdd ?: return@collect
                made.firstOrNull { it.uid !in before }?.let { added ->
                    awaitingAdd = null
                    updateDraft { it.copy(exercise = added) }
                }
            }
        }
    }

    /** The sheet is off to add an exercise: once it is saved, the sheet picks it. */
    fun expectNewExercise() {
        awaitingAdd = repository.customExercises.value.map { it.uid }.toSet()
    }

    /** Narrows the log sheet's exercise picker; the library page shares the same filters. */
    fun updateFilters(transform: (ExerciseFilters) -> ExerciseFilters) {
        filters.update(transform)
    }

    fun updateDraft(transform: (SetDraft) -> SetDraft) {
        draft.update { before ->
            transform(before).let { changed ->
                // A lift picked starts from what it was last done with: a snatch is no deadlift, a press no swing.
                if (changed.exercise != before.exercise) changed.suggested(history()) else changed
            }.let { d ->
                d.copy(
                    barKg = d.barKg.coerceIn(repository.bar.value ?: 0.0, MAX_BAR_KG),
                    reps = d.reps.coerceIn(1, SetDraft.MAX_REPS),
                    minutes = d.minutes.coerceIn(1, SetDraft.MAX_MINUTES),
                    seconds = d.seconds.coerceIn(SetDraft.SECONDS_STEP, SetDraft.MAX_SECONDS),
                )
            }
        }
    }

    /** This draft's load as its lift was last done, or as it is when the lift has no history. */
    private fun SetDraft.suggested(history: List<SetLog>): SetDraft = when (exercise.equipment) {
        Equipment.Kettlebell -> copy(bellKg = suggestedBell(exercise, doubled, history, fallback = bellKg))
        Equipment.Barbell -> copy(barKg = suggestedBar(exercise, history, repository.bar.value))
        Equipment.Bodyweight -> copy(addedKg = suggestedBell(exercise, double = false, history, fallback = 0.0))
        Equipment.Band -> copy(band = suggestedBand(exercise, history, offeredBands(repository.bands.value), band))
        Equipment.Mat -> this
    }

    /**
     * Steps the sheet's load one [step] (−1 lighter, +1 heavier): the next of the user's bells, the bar's smallest
     * pair of plates, the next weight added to bodyweight, or the next band. Nothing on the mat, or at either end.
     */
    fun stepDraftLoad(step: Int) {
        val bells = repository.bells.value.ifEmpty { standardBells }
        val bar = repository.bar.value
        val bands = offeredBands(repository.bands.value)
        updateDraft { d ->
            when (val equipment = d.exercise.equipment) {
                Equipment.Kettlebell -> steppedKg(equipment, d.bellKg, step, bells, bar)?.let { d.copy(bellKg = it) }
                Equipment.Barbell -> steppedKg(equipment, d.barKg, step, bells, bar)?.let { d.copy(barKg = it) }
                Equipment.Bodyweight -> steppedKg(equipment, d.addedKg, step, bells, bar)?.let { d.copy(addedKg = it) }
                Equipment.Band -> steppedBand(d.band, step, bands)?.let { d.copy(band = it) }
                Equipment.Mat -> null
            } ?: d
        }
    }

    /** Logs [set], by default the sheet's: a hold for [seconds], fewer when a timed one is stopped early. */
    fun logSet(set: SetDraft = draft.value, seconds: Int = set.seconds) {
        repository.logSet(
            set.exercise,
            set.loadKg,
            if (set.held) 0 else set.reps,
            set.side,
            set.doubled,
            seconds = seconds.takeIf { set.held },
            band = set.band,
        )
        // A one-sided lift usually goes on with the other side.
        if (set.exercise.sided(set.doubled)) draft.update { it.copy(side = set.side.other) }
        rest.start(repository.restSeconds.value)
    }

    /** Logs the plan's next set as planned, with its suggested load: a hold for its seconds. */
    fun logNext() {
        val next = state.value.upNext ?: return
        val set = next.set
        repository.logSet(
            set.exercise,
            next.bellKg,
            if (set.held) 0 else set.reps,
            set.side,
            set.double,
            seconds = set.reps.takeIf { set.held },
            band = next.band,
        )
        rest.start(repository.restSeconds.value)
    }

    /**
     * Makes rests a [step] (−1 or +1) of [RestClock.STEP_SECONDS] shorter or longer: the one running, if one is,
     * and every one after.
     */
    fun stepRest(step: Int) {
        val seconds = (repository.restSeconds.value + step * RestClock.STEP_SECONDS)
            .coerceIn(RestClock.MIN_SECONDS, RestClock.MAX_SECONDS)
        repository.setRestSeconds(seconds)
        rest.adjust(seconds)
    }

    /** Ends the rest early. */
    fun skipRest() {
        rest.skip()
    }

    /**
     * Steps the next set's load one [step] (−1 or +1): a bell, the bar's smallest pair of plates, or a band.
     * The rest of its block takes it, for today. Worked out from the plan as it is now, not as last drawn, so a
     * held + steps once a repeat.
     */
    fun stepNextLoad(step: Int) {
        val active = repository.active.value ?: return
        val (plan, _) = today() ?: return
        val next = programDay(active)?.let { upNext(it, active.sets) } ?: return
        val equipment = next.set.exercise.equipment
        val changed = if (equipment == Equipment.Band) {
            steppedBand(next.band, step, offeredBands(repository.bands.value))
                ?.let { band -> plan.change(next.block) { it.copy(band = band.id) } }
        } else {
            val bells = repository.bells.value.ifEmpty { standardBells }
            steppedKg(equipment, next.bellKg, step, bells, repository.bar.value)
                ?.let { kg -> plan.change(next.block) { it.copy(kg = kg) } }
        }
        changed?.let(repository::setSessionPlan)
    }

    /**
     * The Log sheet opening: on a program day it starts on the set up next, as planned, unless it was opened on
     * that same set before and may have been changed since.
     */
    fun openLogSheet() {
        val next = state.value.upNext ?: return
        if (editing.value != null || next == sheetOpenedOn) return
        sheetOpenedOn = next
        val set = next.set
        val load = next.bellKg
        draft.update { d ->
            d.copy(
                exercise = set.exercise,
                bellKg = if (set.exercise.equipment == Equipment.Kettlebell) load else d.bellKg,
                barKg = if (set.exercise.equipment == Equipment.Barbell) load else d.barKg,
                addedKg = if (set.exercise.equipment == Equipment.Bodyweight) load else d.addedKg,
                reps = if (set.held) d.reps else set.reps.coerceIn(1, SetDraft.MAX_REPS),
                seconds = if (set.held) set.reps.coerceIn(SetDraft.SECONDS_STEP, SetDraft.MAX_SECONDS) else d.seconds,
                side = set.side ?: d.side,
                double = set.double,
                band = next.band ?: d.band,
            )
        }
    }

    /** Opens the page of the exercise [key] names, or closes the one open with null. */
    fun openExercise(key: String?) {
        opened.value = key
    }

    /** Changes block [index] of today's plan by [transform]: its load, rounds or reps. */
    fun changeBlock(index: Int, transform: (Block) -> Block) {
        val (plan, _) = today() ?: return
        repository.setSessionPlan(plan.change(index, transform))
    }

    /** Skips the rest of block [index] of today's plan: what comes next moves on. */
    fun skipBlock(index: Int) {
        val (plan, done) = today() ?: return
        repository.setSessionPlan(plan.skip(index, done.getOrElse(index) { 0 }))
    }

    /** Plans block [index] of today's plan in full again. */
    fun unskipBlock(index: Int) {
        val (plan, _) = today() ?: return
        repository.setSessionPlan(plan.unskip(index))
    }

    /** Swaps [exercise] in for the sets of block [index] still to do. */
    fun swapBlock(index: Int, exercise: Exercise) {
        val (plan, done) = today() ?: return
        val count = done.getOrElse(index) { 0 }
        if (plan.canSwap(index, count)) repository.setSessionPlan(plan.swap(index, exercise, count))
    }

    /**
     * Today's plan as the repository has it now, not as last drawn, so a change made straight after another
     * builds on it, and the sets of each block done; null in a free session.
     */
    private fun today(): Pair<SessionPlan, List<Int>>? {
        val active = repository.active.value ?: return null
        val day = programDay(active) ?: return null
        return day.today to day.planRows(active.sets).map { it.done }
    }

    /** The program day [active] is, with today's plan as it has it; null in a free session. */
    private fun programDay(active: LiftSession): ProgramDay? =
        repository.programDay.value?.takeIf { active.program != null && it.program.key == active.program }
            ?.copy(plan = active.plan)

    /** Starts the plan's next block as an EMOM, if it is one. */
    fun startPlannedEmom() {
        val next = state.value.upNext ?: return
        val minutes = next.emomMinutes ?: return
        if (next.set.held) return
        rest.skip()
        emom.start(
            next.set.exercise,
            next.bellKg,
            next.set.reps,
            minutes,
            next.set.side ?: Side.Left,
            next.set.double,
            next.band,
        )
    }

    /** Starts timing the plan's next set when it is a hold, putting it on the sheet first. */
    fun startPlannedHold() {
        val next = state.value.upNext ?: return
        if (!next.set.held) return
        sheetOpenedOn = null
        openLogSheet()
        startHold()
    }

    /** Starts an EMOM of the sheet's lift, bell and reps for its minutes. */
    fun startDraftEmom() {
        val d = draft.value
        if (d.held) return
        rest.skip()
        emom.start(
            d.exercise,
            d.loadKg,
            d.reps,
            d.minutes,
            d.side,
            d.doubled,
            d.band.takeIf { d.exercise.equipment == Equipment.Band },
        )
    }

    /** Starts timing the sheet's hold: the other side of the one just done, if that is on offer. */
    fun startHold() {
        rest.skip()
        holds.start(draft.value)
    }

    /** Stops the hold early, logging the whole seconds held. */
    fun stopHold() {
        holds.stop()
    }

    /** Stops the hold without logging it. */
    fun cancelHold() {
        holds.cancel()
    }

    /** Logs this minute's set: the EMOM rests out the minute. */
    fun emomSetDone() {
        emom.setDone()
    }

    fun stopEmom() {
        emom.stop()
    }

    fun removeSet(id: Long) {
        repository.removeSet(id)
    }

    /** Opens set [id] on the sheet to change: the draft becomes it, the one for a new set put aside. */
    fun editSet(id: Long) {
        val set = repository.active.value?.sets?.firstOrNull { it.id == id } ?: return
        if (editing.value == null) freeDraft = draft.value
        editing.value = set
        draft.update { it.from(set, flipSide = false) }
    }

    /**
     * This draft as [set] has it, for its equipment; the rest stays. With [flipSide], the other side of a
     * one-sided lift, to go on with.
     */
    private fun SetDraft.from(set: SetLog, flipSide: Boolean): SetDraft {
        val side = set.side ?: side
        return copy(
            exercise = set.exercise,
            bellKg = if (set.equipment == Equipment.Kettlebell) set.bellKg else bellKg,
            barKg = if (set.equipment == Equipment.Barbell) set.bellKg else barKg,
            addedKg = if (set.equipment == Equipment.Bodyweight) set.bellKg else addedKg,
            reps = set.reps.coerceAtLeast(1),
            side = if (flipSide && set.exercise.sided(set.double)) side.other else side,
            double = set.double,
            seconds = set.seconds ?: seconds,
            band = set.band ?: band,
        )
    }

    /** Saves the set being changed as the sheet's draft now has it. */
    fun saveEdit() {
        val set = editing.value ?: return
        val d = draft.value
        repository.updateSet(
            set.copy(
                bellKg = d.loadKg,
                reps = if (d.held) 0 else d.reps,
                side = d.side,
                double = d.doubled,
                seconds = d.seconds.takeIf { d.held },
                band = d.band.takeIf { d.exercise.equipment == Equipment.Band },
            ),
        )
        endEdit()
    }

    /** Leaves the set being changed as it was; the draft for a new set comes back. */
    fun cancelEdit() {
        endEdit()
    }

    private fun endEdit() {
        if (editing.value == null) return
        editing.value = null
        freeDraft?.let { draft.value = it }
        freeDraft = null
    }

    /** Puts back a set just removed. */
    fun restoreSet(set: SetLog) {
        repository.restoreSet(set)
    }

    /**
     * Finishes the session as [title], keeping it here for the summary with the records it set. One with no
     * sets has nothing to save and is thrown away instead. True when it was saved, and the summary follows.
     */
    fun finish(title: String): Boolean {
        val session = repository.active.value ?: return false
        emom.stop()
        holds.cancel()
        rest.skip()
        if (session.sets.isEmpty()) {
            repository.discardSession()
            return false
        }
        val before = repository.sessions.value.flatMap { it.sets }
        val done = session.copy(finishedAtEpochMillis = clock.nowMillis(), title = title)
        // Taken now: once the session is saved, the program day moves on to the next.
        val bells = repository.bells.value.ifEmpty { standardBells }
        val bands = offeredBands(repository.bands.value)
        val day = repository.programDay.value?.takeIf { session.program != null && it.program.key == session.program }
        val asDone = day?.copy(plan = session.plan)
        val changes = asDone?.let { planChanges(it, progressions(it, session.sets, bells, bands)) }
        val taken = repository.customPrograms.value.mapNotNull { it.name }.toSet()
        finished.value = Finished(
            done,
            newRecords(before, done),
            weekWithThis(),
            changes,
            taken,
            bands = repository.bands.value,
            lastDayOf = day?.program?.takeIf { day.number >= it.totalDays },
            toppedOut = asDone?.let { toppedOut(it, session.sets, bells, bands) }.orEmpty(),
        )
        repository.finishSession(title)
        return true
    }

    /**
     * Keeps the finished session's changes, and the steps up at [nudges], in their program, as far as [scope]
     * reaches, or in a copy named [copyName]: see [BellsRepository.saveChanges].
     */
    fun keepChanges(scope: SaveScope, copyName: String, nudges: Set<Int> = emptySet()) {
        val done = finished.value ?: return
        val changes = done.changes?.withNudges(nudges)?.takeIf { it.changes.isNotEmpty() } ?: return
        if (done.kept != null) return
        finished.value = done.copy(kept = repository.saveChanges(changes, scope, copyName))
    }

    fun discard() {
        emom.stop()
        holds.cancel()
        rest.skip()
        repository.discardSession()
    }

    /** The week as it stands with this session in it, the interval workouts counted too, as Lift's week card counts. */
    private fun weekWithThis(): WeekStanding {
        val today = timestamps.dayNumber(clock.nowMillis())
        val days = repository.sessions.value.mapNotNull { it.finishedAtEpochMillis }.map(timestamps::dayNumber) +
            intervals.workouts.value.map { timestamps.dayNumber(it.finishedAtEpochMillis) } +
            today
        return weekStanding(days, today, intervals.weeklyGoal.value)
    }

    private fun playCues(now: Long) {
        rest.tick(now)
        holds.tick(now)
        emom.tick(now)
    }

    private fun upNext(day: ProgramDay, logged: List<SetLog>): UpNext? {
        val next = day.nextSet(logged) ?: return null
        val history = history(logged)
        return UpNext(
            set = next.set,
            bellKg = suggestedLoad(next.set, history, repository.bar.value, draft.value.bellKg),
            number = next.index + 1,
            total = next.total,
            emomMinutes = next.emomMinutes,
            band = plannedBand(next.set, history, offeredBands(repository.bands.value), draft.value.band),
            block = next.block,
            lastTime = lastTime(next.set.exercise, next.set.double),
        )
    }

    /** The last set of [exercise], with two bells or one ([double]), in a session before this one. */
    private fun lastTime(exercise: Exercise, double: Boolean): SetLog? =
        setsBeforeOldestFirst.lastOrNull { it.exercise.key == exercise.key && it.double == double }

    /** Every set logged, oldest first: those of the sessions finished, then [logged], by default this one's. */
    private fun history(logged: List<SetLog> = repository.active.value?.sets.orEmpty()): List<SetLog> =
        setsBeforeOldestFirst + logged

    /** What the screen chose, and the exercises it can choose from. */
    private data class View(
        val draft: SetDraft,
        val finished: Finished?,
        val emom: EmomRun?,
        val timing: HoldTiming,
        val exercises: List<Exercise>,
        val filters: ExerciseFilters,
        val editing: SetLog?,
    )

    private companion object {
        const val TICK_MILLIS = 250L
        const val MILLIS_PER_SECOND = 1_000L
    }
}
