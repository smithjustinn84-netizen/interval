package dev.justinsmith.bells.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.bells.data.BellsRepository
import dev.justinsmith.bells.domain.LiftSession
import dev.justinsmith.bells.domain.PersonalRecord
import dev.justinsmith.bells.domain.Program
import dev.justinsmith.bells.domain.builtInPrograms
import dev.justinsmith.bells.domain.newRecords
import dev.justinsmith.interval.platform.TimestampFormatter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** One finished session, or null once it is gone (deleted here or never there), and when it finished. */
data class LiftDetailUiState(
    val session: LiftSession? = null,
    /** As the platform formats them: "Sunday, Oct 11" and "7:42 AM". */
    val date: String = "",
    val time: String = "",
    /** The records it set, against every session finished before it. */
    val records: List<PersonalRecord> = emptyList(),
    /** The program it was a day of, while that program is still there. */
    val program: Program? = null,
)

/** The session stored under [uid], as History opens it, with what it set and a way to delete it. */
class LiftDetailViewModel(
    private val uid: String,
    private val repository: BellsRepository,
    private val timestamps: TimestampFormatter,
) : ViewModel() {
    val state: StateFlow<LiftDetailUiState> = combine(repository.sessions, repository.customPrograms, ::build)
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            build(repository.sessions.value, repository.customPrograms.value),
        )

    fun delete() {
        repository.deleteSession(uid)
    }

    fun restore() {
        repository.restoreSession(uid)
    }

    private fun build(sessions: List<LiftSession>, custom: List<Program>): LiftDetailUiState {
        val session = sessions.firstOrNull { it.uid == uid } ?: return LiftDetailUiState()
        val at = session.finishedAtEpochMillis ?: session.startedAtEpochMillis
        // Newest first, so the ones finished before it are those after it in the list.
        val before = sessions.dropWhile { it.uid != uid }.drop(1).flatMap { it.sets }
        return LiftDetailUiState(
            session = session,
            date = timestamps.date(at),
            time = timestamps.time(at),
            records = newRecords(before, session),
            program = session.program?.let { key -> (builtInPrograms + custom).firstOrNull { it.key == key } },
        )
    }
}
