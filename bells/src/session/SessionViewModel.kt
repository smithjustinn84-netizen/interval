package dev.justinsmith.bells.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.bells.data.BellsRepository
import dev.justinsmith.bells.data.ExerciseFilterStore
import dev.justinsmith.bells.domain.Band
import dev.justinsmith.bells.domain.Block
import dev.justinsmith.bells.domain.BlockKind
import dev.justinsmith.bells.domain.BuiltInExercise
import dev.justinsmith.bells.domain.EmomClock
import dev.justinsmith.bells.domain.EmomPhase
import dev.justinsmith.bells.domain.EmomState
import dev.justinsmith.bells.domain.Equipment
import dev.justinsmith.bells.domain.Exercise
import dev.justinsmith.bells.domain.ExerciseFilters
import dev.justinsmith.bells.domain.HoldState
import dev.justinsmith.bells.domain.LiftSession
import dev.justinsmith.bells.domain.Measure
import dev.justinsmith.bells.domain.PersonalRecord
import dev.justinsmith.bells.domain.PlanBlock
import dev.justinsmith.bells.domain.PlanChanges
import dev.justinsmith.bells.domain.PlannedSet
import dev.justinsmith.bells.domain.ProgramDay
import dev.justinsmith.bells.domain.ProgramKey
import dev.justinsmith.bells.domain.SaveScope
import dev.justinsmith.bells.domain.SessionPlan
import dev.justinsmith.bells.domain.SetLog
import dev.justinsmith.bells.domain.Side
import dev.justinsmith.bells.domain.bands
import dev.justinsmith.bells.domain.newRecords
import dev.justinsmith.bells.domain.nextPlanned
import dev.justinsmith.bells.domain.offeredBands
import dev.justinsmith.bells.domain.offeredExercises
import dev.justinsmith.bells.domain.other
import dev.justinsmith.bells.domain.ownedEquipment
import dev.justinsmith.bells.domain.planChanges
import dev.justinsmith.bells.domain.plannedDone
import dev.justinsmith.bells.domain.recentExercises
import dev.justinsmith.bells.domain.standardBells
import dev.justinsmith.bells.domain.suggestedBand
import dev.justinsmith.bells.domain.suggestedBell
import dev.justinsmith.bells.library.LibraryList
import dev.justinsmith.bells.platform.Beeper
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.timer.formatClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
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
    /** How long a hold lasted. */
    val seconds: Int = DEFAULT_SECONDS,
    val band: Band = Band.Default,
) {
    /** Whether the set is done with two bells. */
    val doubled: Boolean get() = exercise.doubled(double)

    /** The weight the set is logged with: the bell, the weight added to bodyweight, or none. */
    val loadKg: Double get() = when (exercise.equipment) {
        Equipment.Kettlebell -> bellKg
        Equipment.Bodyweight -> addedKg
        Equipment.Band, Equipment.Mat -> 0.0
    }

    /** Whether the set is a hold, timed rather than counted. */
    val held: Boolean get() = exercise.measure == Measure.Time

    companion object {
        const val DEFAULT_BELL_KG = 16.0
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
    val changes: PlanChanges? = null,
    val taken: Set<String> = emptySet(),
    val kept: ProgramKey? = null,
    /** The user's bands, to name a band a change plans by. */
    val bands: List<Band> = emptyList(),
)

/**
 * The plan's next set: which of [total] it is, with its load (the bell, the weight added to bodyweight, or none,
 * and the [band] for band work), and whether it opens an EMOM of [emomMinutes].
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
)

/**
 * One of the day's blocks as today's plan has it: [done] of its [total] sets done, and whether the session
 * [changed] it from the program (its load, its count or its exercise).
 */
data class PlanRow(val entry: PlanBlock, val done: Int, val total: Int, val changed: Boolean) {
    val block: Block get() = entry.block

    /** Whether something is left to change: not every set done, or skipped and so to bring back. */
    val open: Boolean get() = entry.skipped || done < total
}

/**
 * An EMOM under way: its lift, bell (or two, when [double]) and reps, when it started, and the minutes whose
 * set is done.
 */
data class EmomRun(
    val exercise: Exercise,
    val bellKg: Double,
    val reps: Int,
    val minutes: Int,
    val firstSide: Side,
    val startedAtMillis: Long,
    val done: Set<Int> = emptySet(),
    val double: Boolean = false,
    /** The band, for an exercise done with one. */
    val band: Band? = null,
) {
    /** The side of minute [minute] (from 1): a one-sided lift with one bell alternates. */
    fun sideOf(minute: Int): Side? = if (exercise.sided(double)) {
        Side.entries[(firstSide.ordinal + minute - 1) % Side.entries.size]
    } else {
        null
    }
}

data class SessionUiState(
    val session: LiftSession? = null,
    /** How long it has run: "14:32". */
    val elapsed: String = formatClock(0),
    /** Since the last set, for resting: "0:38"; null before the first. */
    val sinceLast: String? = null,
    /** The bells the sheet offers: the user's own, or every standard size until they have said. */
    val bells: List<Double> = standardBells,
    val ownsBells: Boolean = false,
    /** The bands the sheet offers, lightest first. */
    val bands: List<Band> = offeredBands(bands(emptyList())),
    val draft: SetDraft = SetDraft(),
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
    /** What the sheet's picker offers, the ones done lately first, and how it is narrowed. */
    val library: LibraryList = LibraryList(offeredExercises(emptyList())),
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
    private val filters: ExerciseFilterStore,
    tickMillis: Long = TICK_MILLIS,
) : ViewModel() {
    private val draft = MutableStateFlow(SetDraft())
    private val finished = MutableStateFlow<Finished?>(null)
    private val emom = MutableStateFlow<EmomRun?>(null)
    private var lastCueMillis = 0L

    /** Times a hold from the sheet, logging it when done. */
    val holds = HoldTimer(clock, beeper) { set, seconds -> logSet(set, seconds) }

    private val ticks = flow {
        while (true) {
            emit(clock.nowMillis())
            delay(tickMillis)
        }
    }

    private val runs = combine(emom, holds.state, ::Pair)

    private val gear = combine(repository.bells, repository.bands, ::Pair)

    private val view = combine(draft, finished, runs, repository.customExercises, filters.filters) {
            draft,
            finished,
            (emom, timing),
            made,
            narrowed,
        ->
        // The sheet never offers an archived exercise, whatever the library shows.
        View(draft, finished, emom, timing, offeredExercises(made), narrowed.copy(showArchived = false))
    }

    val state: StateFlow<SessionUiState> =
        combine(repository.active, gear, repository.programDay, view, ticks) {
                active,
                (owned, bands),
                day,
                chosen,
                now,
            ->
            val run = chosen.emom
            val emomState = run?.let { EmomClock(it.minutes).at(now - it.startedAtMillis, it.done) }
            val hold = chosen.timing.run
            val plan = active?.takeIf { it.program != null && it.program == day?.program?.key }
                ?.let { day?.copy(plan = it.plan) }
            SessionUiState(
                session = active,
                elapsed = formatClock(active?.elapsedSeconds(now) ?: 0),
                sinceLast = active?.sets?.lastOrNull()?.let {
                    formatClock(((now - it.loggedAtEpochMillis) / MILLIS_PER_SECOND).toInt())
                },
                bells = owned.ifEmpty { standardBells },
                ownsBells = owned.isNotEmpty(),
                bands = offeredBands(bands),
                draft = chosen.draft,
                upNext = plan?.let { upNext(it, active.sets) },
                dayDone = plan != null && nextPlanned(plan.plannedSets(), active.sets) == null,
                plan = plan?.let { planRows(it, active.sets) }.orEmpty(),
                emom = run,
                emomState = emomState,
                hold = hold,
                holdState = hold?.let { it.clock.at(now - it.startedAtMillis) },
                otherSide = chosen.timing.offer
                    ?.takeIf { it.exercise == chosen.draft.exercise && it.side == chosen.draft.side }
                    ?.side,
                finished = chosen.finished,
                library = LibraryList(
                    chosen.exercises,
                    recentExercises(
                        repository.sessions.value.reversed().flatMap { it.sets } + active?.sets.orEmpty(),
                        chosen.exercises,
                    ),
                    chosen.filters,
                    ownedEquipment(owned, bands),
                ),
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, SessionUiState())

    /** The user's exercises when the sheet went to add one: the first not among them is the one added. */
    private var awaitingAdd: Set<String>? = null

    init {
        if (repository.active.value == null) repository.startSession()
        // Start from the user's own bells: the lightest they own at or above the default.
        repository.bells.value.let { owned ->
            if (owned.isNotEmpty()) {
                val bell = owned.firstOrNull { it >= SetDraft.DEFAULT_BELL_KG } ?: owned.last()
                draft.update { it.copy(bellKg = bell) }
            }
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

    fun updateDraft(transform: (SetDraft) -> SetDraft) {
        draft.update {
            transform(it).let { d ->
                d.copy(
                    reps = d.reps.coerceIn(1, SetDraft.MAX_REPS),
                    minutes = d.minutes.coerceIn(1, SetDraft.MAX_MINUTES),
                    seconds = d.seconds.coerceIn(SetDraft.SECONDS_STEP, SetDraft.MAX_SECONDS),
                )
            }
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
        val day = repository.programDay.value?.takeIf { active.program != null && it.program.key == active.program }
            ?.copy(plan = active.plan) ?: return null
        return day.today to planRows(day, active.sets).map { it.done }
    }

    /** Starts the plan's next block as an EMOM, if it is one. */
    fun startPlannedEmom() {
        val next = state.value.upNext ?: return
        val minutes = next.emomMinutes ?: return
        if (next.set.held) return
        startEmom(
            next.set.exercise,
            next.bellKg,
            next.set.reps,
            minutes,
            next.set.side ?: Side.Left,
            next.set.double,
            next.band,
        )
    }

    /** Starts an EMOM of the sheet's lift, bell and reps for its minutes. */
    fun startDraftEmom() {
        val d = draft.value
        if (d.held) return
        startEmom(
            d.exercise,
            d.loadKg,
            d.reps,
            d.minutes,
            d.side,
            d.doubled,
            d.band.takeIf {
                d.exercise.equipment ==
                    Equipment.Band
            },
        )
    }

    /** Logs this minute's set: the EMOM rests out the minute. */
    fun emomSetDone() {
        val run = emom.value ?: return
        val now = state.value.emomState ?: return
        if (now.phase != EmomPhase.Work) return
        repository.logSet(run.exercise, run.bellKg, run.reps, run.sideOf(now.minute), run.double, band = run.band)
        emom.value = run.copy(done = run.done + now.minute)
    }

    fun stopEmom() {
        emom.value = null
    }

    fun removeSet(id: Long) {
        repository.removeSet(id)
    }

    /** Puts back a set just removed. */
    fun restoreSet(set: SetLog) {
        repository.restoreSet(set)
    }

    /** Finishes the session as [title], keeping it here for the summary with the records it set. */
    fun finish(title: String) {
        val session = repository.active.value ?: return
        emom.value = null
        holds.cancel()
        if (session.sets.isEmpty()) {
            repository.discardSession()
            return
        }
        val before = repository.sessions.value.flatMap { it.sets }
        val done = session.copy(finishedAtEpochMillis = clock.nowMillis(), title = title)
        // Taken now: once the session is saved, the program day moves on to the next.
        val changes = repository.programDay.value
            ?.takeIf { session.program != null && it.program.key == session.program }
            ?.copy(plan = session.plan)
            ?.let(::planChanges)
        val taken = repository.customPrograms.value.mapNotNull { it.name }.toSet()
        finished.value = Finished(done, newRecords(before, done), changes, taken, bands = repository.bands.value)
        repository.finishSession(title)
    }

    /**
     * Keeps the finished session's changes in their program, as far as [scope] reaches, or in a copy named
     * [copyName]: see [BellsRepository.saveChanges].
     */
    fun keepChanges(scope: SaveScope, copyName: String) {
        val done = finished.value ?: return
        val changes = done.changes ?: return
        if (done.kept != null) return
        finished.value = done.copy(kept = repository.saveChanges(changes, scope, copyName))
    }

    fun discard() {
        emom.value = null
        holds.cancel()
        repository.discardSession()
    }

    private fun startEmom(
        exercise: Exercise,
        bellKg: Double,
        reps: Int,
        minutes: Int,
        firstSide: Side,
        double: Boolean,
        band: Band? = null,
    ) {
        val now = clock.nowMillis()
        lastCueMillis = 0L
        emom.value = EmomRun(exercise, bellKg, reps, minutes, firstSide, now, double = double, band = band)
    }

    private fun playCues(now: Long) {
        holds.tick(now)
        val run = emom.value ?: return
        val clockOfRun = EmomClock(run.minutes)
        val elapsed = now - run.startedAtMillis
        clockOfRun.cuesBetween(lastCueMillis, elapsed).lastOrNull()?.let(beeper::beep)
        lastCueMillis = elapsed
        if (clockOfRun.at(elapsed, run.done).phase == EmomPhase.Done) emom.value = null
    }

    private fun upNext(day: ProgramDay, logged: List<SetLog>): UpNext? {
        val blocks = day.blockSets()
        val planned = blocks.flatten()
        val index = nextPlanned(planned, logged) ?: return null
        val set = planned[index]
        val history = repository.sessions.value.reversed().flatMap { it.sets } + logged
        // The block it falls in, to tell an EMOM's first minute from the rest: after a swap, the first
        // minute of the exercise swapped in.
        var start = 0
        val blockIndex = blocks.indexOfFirst { b -> (index < start + b.size).also { if (!it) start += b.size } }
        val entry = day.today.blocks[blockIndex]
        val emomMinutes = if (entry.block.kind == BlockKind.Emom && index == start + entry.firstOfBlock) {
            blocks[blockIndex].size - entry.firstOfBlock
        } else {
            null
        }
        // The plan's load, when it names one; else what was used last time. Weight added to bodyweight starts
        // at none; a band and the mat take no weight. A band the plan names that is archived or gone falls back.
        val load = when (set.exercise.equipment) {
            Equipment.Kettlebell ->
                set.kg ?: suggestedBell(set.exercise, set.double, history, draft.value.bellKg)

            Equipment.Bodyweight -> set.kg ?: suggestedBell(set.exercise, double = false, history, fallback = 0.0)

            Equipment.Band, Equipment.Mat -> 0.0
        }
        val band = if (set.exercise.equipment == Equipment.Band) {
            val offered = offeredBands(repository.bands.value)
            offered.firstOrNull { it.id == set.band }
                ?: suggestedBand(set.exercise, history, offered, draft.value.band)
        } else {
            null
        }
        return UpNext(set, load, index + 1, planned.size, emomMinutes, band, blockIndex)
    }

    /** [day]'s blocks as today's plan has them, with the sets of each [logged] has done. */
    private fun planRows(day: ProgramDay, logged: List<SetLog>): List<PlanRow> {
        val blocks = day.blockSets()
        val done = plannedDone(blocks.flatten(), logged)
        var start = 0
        return day.today.blocks.mapIndexed { i, entry ->
            val size = blocks[i].size
            val count = done.subList(start, start + size).count { it }
            start += size
            PlanRow(entry, count, size, changed = entry.block != day.blocks.getOrNull(i))
        }
    }

    /** What the screen chose, and the exercises it can choose from. */
    private data class View(
        val draft: SetDraft,
        val finished: Finished?,
        val emom: EmomRun?,
        val timing: HoldTiming,
        val exercises: List<Exercise>,
        val filters: ExerciseFilters,
    )

    private companion object {
        const val TICK_MILLIS = 250L
        const val MILLIS_PER_SECOND = 1_000L
    }
}
