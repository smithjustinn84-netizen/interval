package dev.justinsmith.bells.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.justinsmith.bells.data.BellsRepository
import dev.justinsmith.bells.domain.LiftSession
import dev.justinsmith.bells.domain.PersonalRecord
import dev.justinsmith.bells.domain.newRecords
import dev.justinsmith.bells.resources.Res
import dev.justinsmith.bells.resources.common_cancel
import dev.justinsmith.bells.resources.common_joined
import dev.justinsmith.bells.resources.history_delete
import dev.justinsmith.bells.resources.history_delete_text
import dev.justinsmith.bells.resources.history_delete_title
import dev.justinsmith.bells.session.SessionStats
import dev.justinsmith.bells.session.setLine
import dev.justinsmith.bells.ui.liftName
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.ui.BackAction
import dev.justinsmith.interval.ui.ConfirmDialog
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.ScrollPage
import dev.justinsmith.interval.ui.SecondaryButton
import dev.justinsmith.interval.ui.Spacing
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.currentKoinScope

/** One finished session, or null once it is gone (deleted here or never there), and when it finished. */
data class LiftDetailUiState(
    val session: LiftSession? = null,
    /** As the platform formats them: "Sunday, Oct 11" and "7:42 AM". */
    val date: String = "",
    val time: String = "",
    /** The records it set, against every session finished before it. */
    val records: List<PersonalRecord> = emptyList(),
)

/** The session stored under [uid], as History opens it, with what it set and a way to delete it. */
class LiftDetailViewModel(
    private val uid: String,
    private val repository: BellsRepository,
    private val timestamps: TimestampFormatter,
) : ViewModel() {
    val state: StateFlow<LiftDetailUiState> = repository.sessions
        .map(::build)
        .stateIn(viewModelScope, SharingStarted.Eagerly, build(repository.sessions.value))

    fun delete() {
        repository.deleteSession(uid)
    }

    private fun build(sessions: List<LiftSession>): LiftDetailUiState {
        val session = sessions.firstOrNull { it.uid == uid } ?: return LiftDetailUiState()
        val at = session.finishedAtEpochMillis ?: session.startedAtEpochMillis
        // Newest first, so the ones finished before it are those after it in the list.
        val before = sessions.dropWhile { it.uid != uid }.drop(1).flatMap { it.sets }
        return LiftDetailUiState(
            session = session,
            date = timestamps.date(at),
            time = timestamps.time(at),
            records = newRecords(before, session),
        )
    }
}

@Composable
fun LiftDetailRoute(uid: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val scope = currentKoinScope()
    val viewModel = viewModel(key = uid) { LiftDetailViewModel(uid, scope.get(), scope.get()) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LiftDetailScreen(state, onDelete = viewModel::delete, onBack = onBack, modifier = modifier)
}

/**
 * A finished session's page: its title and when, its numbers and records, every set by lift, and Delete
 * (after asking). Once the session is gone, deleted from here or elsewhere, the page goes back.
 */
@Composable
fun LiftDetailScreen(
    state: LiftDetailUiState,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = state.session
    val back by rememberUpdatedState(onBack)
    LaunchedEffect(session == null) { if (session == null) back() }
    if (session == null) return
    val palette = IntervalTheme.palette
    var confirming by rememberSaveable { mutableStateOf(false) }
    ScrollPage(session.title, modifier, navigation = { BackAction(onBack) }) {
        Text(
            stringResource(Res.string.common_joined, state.date, state.time),
            style = IntervalTheme.type.meta,
            color = palette.textDim,
            modifier = Modifier.padding(bottom = Spacing.space16),
        )
        SessionStats(session, state.records)
        session.sets.groupBy { it.exercise to it.double }.forEach { (lift, sets) ->
            GroupLabel(liftName(lift.first, lift.second), Modifier.padding(top = Spacing.space16))
            ListCard {
                sets.forEachIndexed { i, set ->
                    if (i > 0) RowDivider()
                    Text(
                        setLine(set.bellKg, set.reps, set.side, set.double),
                        style = IntervalTheme.type.body,
                        color = palette.text,
                        modifier = Modifier.padding(Spacing.space16),
                    )
                }
            }
        }
        Column(Modifier.padding(top = Spacing.space24)) {
            SecondaryButton(stringResource(Res.string.history_delete), { confirming = true }, Modifier.fillMaxWidth())
        }
    }
    if (confirming) {
        ConfirmDialog(
            title = stringResource(Res.string.history_delete_title),
            text = stringResource(Res.string.history_delete_text),
            confirm = stringResource(Res.string.history_delete),
            dismiss = stringResource(Res.string.common_cancel),
            onConfirm = {
                confirming = false
                onDelete()
            },
            onDismiss = { confirming = false },
        )
    }
}
