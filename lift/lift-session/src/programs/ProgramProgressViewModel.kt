package dev.justinsmith.lift.programs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.lift.data.GearStore
import dev.justinsmith.lift.data.ProgramStore
import dev.justinsmith.lift.data.SessionStore
import dev.justinsmith.lift.data.SetStore
import dev.justinsmith.lift.domain.DayDone
import dev.justinsmith.lift.domain.Followed
import dev.justinsmith.lift.domain.Gear
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.Program
import dev.justinsmith.lift.domain.ProgramProgress
import dev.justinsmith.lift.domain.ProgramRecap
import dev.justinsmith.lift.domain.programProgress
import dev.justinsmith.lift.domain.programRecap
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * A day done as the page lists it, with the date its session was: blank for one done before the program was
 * followed.
 */
data class DoneRow(val day: DayDone, val date: String)

/**
 * Where the program followed stands and its days done; [progress] is null once none is followed, and the page
 * goes back. Once the program is over, its [recap]: the lifts as they moved and the next cycle.
 */
data class ProgramProgressUiState(
    val progress: ProgramProgress? = null,
    val rows: List<DoneRow> = emptyList(),
    val recap: ProgramRecap? = null,
)

/** The program followed: its days done and left, and once it is over what it came to. */
class ProgramProgressViewModel(
    private val programStore: ProgramStore,
    private val sessionStore: SessionStore,
    private val timestamps: TimestampFormatter,
    private val gearStore: GearStore,
    private val setStore: SetStore,
) : ViewModel() {
    private val gear = combine(gearStore.bells, gearStore.pairs, gearStore.bar) { bells, pairs, bar ->
        Gear(bells, pairs, bar)
    }

    val state: StateFlow<ProgramProgressUiState> =
        combine(programStore.followed, sessionStore.sessions, gear, setStore.restSeconds, ::build)
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                build(
                    programStore.followed.value,
                    sessionStore.sessions.value,
                    Gear(gearStore.bells.value, gearStore.pairs.value, gearStore.bar.value),
                    setStore.restSeconds.value,
                ),
            )

    /**
     * Follows the program again from its first day. The run just done stays in History, and a session of it
     * still open belongs to that run.
     */
    fun followAgain() {
        programStore.followed.value?.let { programStore.follow(it.program.key) }
    }

    /** Follows [program] from its first day: the recap's next program, moved on to. */
    fun follow(program: Program) {
        programStore.follow(program.key)
    }

    private fun build(
        followed: Followed?,
        sessions: List<LiftSession>,
        gear: Gear,
        restSeconds: Int,
    ): ProgramProgressUiState {
        val progress = followed?.let { programProgress(it, sessions) } ?: return ProgramProgressUiState()
        val rows = progress.done.map { day ->
            DoneRow(day, day.session?.let { timestamps.date(it.startedAtEpochMillis) }.orEmpty())
        }
        return ProgramProgressUiState(progress, rows, programRecap(progress, gear, restSeconds))
    }
}
