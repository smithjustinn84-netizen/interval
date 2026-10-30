package dev.justinsmith.lift.programs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.lift.data.LiftRepository
import dev.justinsmith.lift.domain.DayDone
import dev.justinsmith.lift.domain.Followed
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.ProgramProgress
import dev.justinsmith.lift.domain.programProgress
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
 * goes back.
 */
data class ProgramProgressUiState(val progress: ProgramProgress? = null, val rows: List<DoneRow> = emptyList())

/** The program followed: its days done and left, and once it is over what it came to. */
class ProgramProgressViewModel(
    private val repository: LiftRepository,
    private val timestamps: TimestampFormatter,
) : ViewModel() {
    val state: StateFlow<ProgramProgressUiState> =
        combine(repository.followed, repository.sessions, ::build)
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                build(repository.followed.value, repository.sessions.value),
            )

    /**
     * Follows the program again from its first day. The run just done stays in History, and a session of it
     * still open belongs to that run.
     */
    fun followAgain() {
        repository.followed.value?.let { repository.follow(it.program.key) }
    }

    private fun build(followed: Followed?, sessions: List<LiftSession>): ProgramProgressUiState {
        val progress = followed?.let { programProgress(it, sessions) } ?: return ProgramProgressUiState()
        val rows = progress.done.map { day ->
            DoneRow(day, day.session?.let { timestamps.date(it.startedAtEpochMillis) }.orEmpty())
        }
        return ProgramProgressUiState(progress, rows)
    }
}
