package dev.justinsmith.bells.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.bells.data.BellsRepository
import dev.justinsmith.bells.domain.Exercise
import dev.justinsmith.bells.domain.LiftSession
import dev.justinsmith.bells.domain.PersonalRecord
import dev.justinsmith.bells.domain.Side
import dev.justinsmith.bells.domain.newRecords
import dev.justinsmith.bells.domain.standardBells
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

/** The set the Log sheet will log, kept between sets so the next starts where the last left off. */
data class SetDraft(
    val exercise: Exercise = Exercise.TwoHandSwing,
    val bellKg: Double = DEFAULT_BELL_KG,
    val reps: Int = DEFAULT_REPS,
    val side: Side = Side.Left,
) {
    companion object {
        const val DEFAULT_BELL_KG = 16.0
        const val DEFAULT_REPS = 10
        const val MAX_REPS = 999
    }
}

/** A session just finished, for the summary: it as saved and the records it set. */
data class Finished(val session: LiftSession, val records: List<PersonalRecord>)

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
    val finished: Finished? = null,
)

/**
 * The session in progress: starts one when opened if none is, logs sets, and finishes or discards it.
 * [tickMillis] is how often the clocks move.
 */
class SessionViewModel(
    private val repository: BellsRepository,
    private val clock: WallClock,
    tickMillis: Long = TICK_MILLIS,
) : ViewModel() {
    private val draft = MutableStateFlow(SetDraft())
    private val finished = MutableStateFlow<Finished?>(null)

    private val ticks = flow {
        while (true) {
            emit(clock.nowMillis())
            delay(tickMillis)
        }
    }

    val state: StateFlow<SessionUiState> =
        combine(repository.active, repository.bells, draft, finished, ticks) { active, owned, draft, finished, now ->
            SessionUiState(
                session = active,
                elapsed = formatClock(active?.elapsedSeconds(now) ?: 0),
                sinceLast = active?.sets?.lastOrNull()?.let {
                    formatClock(((now - it.loggedAtEpochMillis) / MILLIS_PER_SECOND).toInt())
                },
                bells = owned.ifEmpty { standardBells },
                ownsBells = owned.isNotEmpty(),
                draft = draft,
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
    }

    fun updateDraft(transform: (SetDraft) -> SetDraft) {
        draft.update { transform(it).let { d -> d.copy(reps = d.reps.coerceIn(1, SetDraft.MAX_REPS)) } }
    }

    fun logSet() {
        val set = draft.value
        repository.logSet(set.exercise, set.bellKg, set.reps, set.side)
        // A one-sided lift usually goes on with the other side.
        if (set.exercise.perSide) draft.update { it.copy(side = if (it.side == Side.Left) Side.Right else Side.Left) }
    }

    fun removeSet(id: Long) {
        repository.removeSet(id)
    }

    /** Finishes the session as [title], keeping it here for the summary with the records it set. */
    fun finish(title: String) {
        val session = repository.active.value ?: return
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
        repository.discardSession()
    }

    private companion object {
        const val TICK_MILLIS = 1_000L
        const val MILLIS_PER_SECOND = 1_000L
    }
}
