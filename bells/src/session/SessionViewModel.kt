package dev.justinsmith.bells.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.bells.data.BellsRepository
import dev.justinsmith.bells.domain.BlockKind
import dev.justinsmith.bells.domain.EmomClock
import dev.justinsmith.bells.domain.EmomPhase
import dev.justinsmith.bells.domain.EmomState
import dev.justinsmith.bells.domain.Exercise
import dev.justinsmith.bells.domain.LiftSession
import dev.justinsmith.bells.domain.PersonalRecord
import dev.justinsmith.bells.domain.PlannedSet
import dev.justinsmith.bells.domain.ProgramDay
import dev.justinsmith.bells.domain.Side
import dev.justinsmith.bells.domain.newRecords
import dev.justinsmith.bells.domain.nextPlanned
import dev.justinsmith.bells.domain.standardBells
import dev.justinsmith.bells.domain.suggestedBell
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

/** The set the Log sheet will log, kept between sets so the next starts where the last left off. */
data class SetDraft(
    val exercise: Exercise = Exercise.TwoHandSwing,
    val bellKg: Double = DEFAULT_BELL_KG,
    val reps: Int = DEFAULT_REPS,
    val side: Side = Side.Left,
    /** For an EMOM started from the sheet. */
    val minutes: Int = DEFAULT_MINUTES,
) {
    companion object {
        const val DEFAULT_BELL_KG = 16.0
        const val DEFAULT_REPS = 10
        const val DEFAULT_MINUTES = 10
        const val MAX_REPS = 999
        const val MAX_MINUTES = 60
    }
}

/** A session just finished, for the summary: it as saved and the records it set. */
data class Finished(val session: LiftSession, val records: List<PersonalRecord>)

/** The plan's next set: which of [total] it is, with the bell to use, and whether it opens an EMOM of [emomMinutes]. */
data class UpNext(val set: PlannedSet, val bellKg: Double, val number: Int, val total: Int, val emomMinutes: Int?)

/** An EMOM under way: its lift, bell and reps, when it started, and the minutes whose set is done. */
data class EmomRun(
    val exercise: Exercise,
    val bellKg: Double,
    val reps: Int,
    val minutes: Int,
    val firstSide: Side,
    val startedAtMillis: Long,
    val done: Set<Int> = emptySet(),
) {
    /** The side of minute [minute] (from 1): a one-sided lift alternates. */
    fun sideOf(minute: Int): Side? =
        if (exercise.perSide) Side.entries[(firstSide.ordinal + minute - 1) % Side.entries.size] else null
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
    val draft: SetDraft = SetDraft(),
    /** For a program day: what comes next, or null once its sets are done ([dayDone]). */
    val upNext: UpNext? = null,
    val dayDone: Boolean = false,
    val emom: EmomRun? = null,
    val emomState: EmomState? = null,
    val finished: Finished? = null,
)

/**
 * The session in progress: starts one when opened if none is, logs sets (from the plan, the sheet or an
 * EMOM), and finishes or discards it. [tickMillis] is how often the clocks move and cues are checked.
 */
class SessionViewModel(
    private val repository: BellsRepository,
    private val clock: WallClock,
    private val beeper: Beeper,
    tickMillis: Long = TICK_MILLIS,
) : ViewModel() {
    private val draft = MutableStateFlow(SetDraft())
    private val finished = MutableStateFlow<Finished?>(null)
    private val emom = MutableStateFlow<EmomRun?>(null)
    private var lastCueMillis = 0L

    private val ticks = flow {
        while (true) {
            emit(clock.nowMillis())
            delay(tickMillis)
        }
    }

    private val view = combine(draft, finished, emom) { draft, finished, emom -> Triple(draft, finished, emom) }

    val state: StateFlow<SessionUiState> =
        combine(repository.active, repository.bells, repository.programDay, view, ticks) {
                active,
                owned,
                day,
                (draft, finished, run),
                now,
            ->
            val emomState = run?.let { EmomClock(it.minutes).at(now - it.startedAtMillis, it.done) }
            val plan = active?.takeIf { it.program != null && it.program == day?.program?.key }?.let { day }
            SessionUiState(
                session = active,
                elapsed = formatClock(active?.elapsedSeconds(now) ?: 0),
                sinceLast = active?.sets?.lastOrNull()?.let {
                    formatClock(((now - it.loggedAtEpochMillis) / MILLIS_PER_SECOND).toInt())
                },
                bells = owned.ifEmpty { standardBells },
                ownsBells = owned.isNotEmpty(),
                draft = draft,
                upNext = plan?.let { upNext(it, active.sets) },
                dayDone = plan != null && nextPlanned(plan.plannedSets(), active.sets) == null,
                emom = run,
                emomState = emomState,
                finished = finished,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, SessionUiState())

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
    }

    fun updateDraft(transform: (SetDraft) -> SetDraft) {
        draft.update {
            transform(it).let { d ->
                d.copy(
                    reps = d.reps.coerceIn(1, SetDraft.MAX_REPS),
                    minutes = d.minutes.coerceIn(1, SetDraft.MAX_MINUTES),
                )
            }
        }
    }

    fun logSet() {
        val set = draft.value
        repository.logSet(set.exercise, set.bellKg, set.reps, set.side)
        // A one-sided lift usually goes on with the other side.
        if (set.exercise.perSide) draft.update { it.copy(side = if (it.side == Side.Left) Side.Right else Side.Left) }
    }

    /** Logs the plan's next set as planned, with its suggested bell. */
    fun logNext() {
        val next = state.value.upNext ?: return
        repository.logSet(next.set.exercise, next.bellKg, next.set.reps, next.set.side)
    }

    /** Starts the plan's next block as an EMOM, if it is one. */
    fun startPlannedEmom() {
        val next = state.value.upNext ?: return
        val minutes = next.emomMinutes ?: return
        startEmom(next.set.exercise, next.bellKg, next.set.reps, minutes, next.set.side ?: Side.Left)
    }

    /** Starts an EMOM of the sheet's lift, bell and reps for its minutes. */
    fun startDraftEmom() {
        val d = draft.value
        startEmom(d.exercise, d.bellKg, d.reps, d.minutes, d.side)
    }

    /** Logs this minute's set: the EMOM rests out the minute. */
    fun emomSetDone() {
        val run = emom.value ?: return
        val now = state.value.emomState ?: return
        if (now.phase != EmomPhase.Work) return
        repository.logSet(run.exercise, run.bellKg, run.reps, run.sideOf(now.minute))
        emom.value = run.copy(done = run.done + now.minute)
    }

    fun stopEmom() {
        emom.value = null
    }

    fun removeSet(id: Long) {
        repository.removeSet(id)
    }

    /** Finishes the session as [title], keeping it here for the summary with the records it set. */
    fun finish(title: String) {
        val session = repository.active.value ?: return
        emom.value = null
        if (session.sets.isEmpty()) {
            repository.discardSession()
            return
        }
        val before = repository.sessions.value.flatMap { it.sets }
        val done = session.copy(finishedAtEpochMillis = clock.nowMillis(), title = title)
        finished.value = Finished(done, newRecords(before, done))
        repository.finishSession(title)
    }

    fun discard() {
        emom.value = null
        repository.discardSession()
    }

    private fun startEmom(exercise: Exercise, bellKg: Double, reps: Int, minutes: Int, firstSide: Side) {
        val now = clock.nowMillis()
        lastCueMillis = 0L
        emom.value = EmomRun(exercise, bellKg, reps, minutes, firstSide, now)
    }

    private fun playCues(now: Long) {
        val run = emom.value ?: return
        val clockOfRun = EmomClock(run.minutes)
        val elapsed = now - run.startedAtMillis
        clockOfRun.cuesBetween(lastCueMillis, elapsed).lastOrNull()?.let(beeper::beep)
        lastCueMillis = elapsed
        if (clockOfRun.at(elapsed, run.done).phase == EmomPhase.Done) emom.value = null
    }

    private fun upNext(day: ProgramDay, logged: List<dev.justinsmith.bells.domain.SetLog>): UpNext? {
        val planned = day.plannedSets()
        val index = nextPlanned(planned, logged) ?: return null
        val set = planned[index]
        val history = repository.sessions.value.reversed().flatMap { it.sets } + logged
        // The block it falls in, to tell an EMOM's first minute from the rest.
        var start = 0
        val block = day.blocks.first { b ->
            val size = b.plannedSets().size
            (index < start + size).also { if (!it) start += size }
        }
        val emomMinutes = if (block.kind == BlockKind.Emom && index == start) block.rounds else null
        return UpNext(
            set,
            suggestedBell(set.exercise, history, draft.value.bellKg),
            index + 1,
            planned.size,
            emomMinutes,
        )
    }

    private companion object {
        const val TICK_MILLIS = 250L
        const val MILLIS_PER_SECOND = 1_000L
    }
}
