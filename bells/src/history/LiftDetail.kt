package dev.justinsmith.bells.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import dev.justinsmith.bells.resources.history_close_cd
import dev.justinsmith.bells.resources.history_delete
import dev.justinsmith.bells.resources.history_delete_text
import dev.justinsmith.bells.resources.history_delete_title
import dev.justinsmith.bells.session.SessionStats
import dev.justinsmith.bells.ui.liftName
import dev.justinsmith.bells.ui.setLine
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.ui.BackAction
import dev.justinsmith.interval.ui.ConfirmDialog
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.SecondaryButton
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.TabGrid
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

/** Resolves a finished session's ViewModel and binds it to [LiftDetailScreen]; [inPane] beside History's log. */
@Composable
fun LiftDetailRoute(uid: String, onBack: () -> Unit, modifier: Modifier = Modifier, inPane: Boolean = false) {
    val scope = currentKoinScope()
    val viewModel = viewModel(key = uid) { LiftDetailViewModel(uid, scope.get(), scope.get()) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LiftDetailScreen(state, onDelete = viewModel::delete, onBack = onBack, modifier = modifier, inPane = inPane)
}

/**
 * A finished session's page: its title and when, its numbers and records, every set by lift, and Delete
 * (after asking). On a wider window the lifts sit side by side. Once the session is gone, deleted from here
 * or elsewhere, the page goes back. Beside History's log ([inPane]) it closes rather than going back.
 */
@Composable
fun LiftDetailScreen(
    state: LiftDetailUiState,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    inPane: Boolean = false,
) {
    val session = state.session
    val back by rememberUpdatedState(onBack)
    LaunchedEffect(session == null) { if (session == null) back() }
    if (session == null) return
    val palette = IntervalTheme.palette
    var confirming by rememberSaveable { mutableStateOf(false) }
    val gutter = Modifier.padding(horizontal = Spacing.space16)
    // One column on a phone; a wider window puts the numbers in one column and the lifts beside them.
    TabGrid(
        session.title,
        modifier,
        navigation = { if (!inPane) BackAction(onBack) },
        actions = {
            if (inPane) IconAction(IntervalIcons.Close, stringResource(Res.string.history_close_cd), onBack)
        },
    ) {
        item(key = "when", span = StaggeredGridItemSpan.FullLine) {
            Text(
                stringResource(Res.string.common_joined, state.date, state.time),
                style = IntervalTheme.type.meta,
                color = palette.textDim,
                modifier = gutter.padding(bottom = Spacing.space16),
            )
        }
        // A cell of its own, so on a wide window the numbers keep a column's width and the lifts sit beside them.
        item(key = "numbers") { SessionStats(session, state.records, gutter.padding(bottom = Spacing.space16)) }
        session.sets.groupBy { it.exercise to it.double }.entries.forEachIndexed { i, (lift, sets) ->
            item(key = "lift-$i") {
                // Spaced below rather than above, so beside the numbers a lift's label lines up with their top.
                Column(gutter.padding(bottom = Spacing.space16)) {
                    GroupLabel(liftName(lift.first, lift.second))
                    ListCard {
                        sets.forEachIndexed { j, set ->
                            if (j > 0) RowDivider()
                            Text(
                                setLine(set),
                                style = IntervalTheme.type.body,
                                color = palette.text,
                                modifier = Modifier.padding(Spacing.space16),
                            )
                        }
                    }
                }
            }
        }
        item(key = "delete", span = StaggeredGridItemSpan.FullLine) {
            Column(
                gutter.fillMaxWidth().padding(top = Spacing.space8),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SecondaryButton(
                    stringResource(Res.string.history_delete),
                    { confirming = true },
                    Modifier.widthIn(max = Sizes.content).fillMaxWidth(),
                )
            }
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
