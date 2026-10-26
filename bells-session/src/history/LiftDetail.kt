package dev.justinsmith.bells.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.justinsmith.bells.session.SessionStats
import dev.justinsmith.bells.session.resources.Res
import dev.justinsmith.bells.session.resources.common_cancel
import dev.justinsmith.bells.session.resources.common_joined
import dev.justinsmith.bells.session.resources.history_close_cd
import dev.justinsmith.bells.session.resources.history_delete
import dev.justinsmith.bells.session.resources.history_delete_text
import dev.justinsmith.bells.session.resources.history_delete_title
import dev.justinsmith.bells.session.resources.history_more_options_cd
import dev.justinsmith.bells.session.resources.history_program_day
import dev.justinsmith.bells.session.resources.notice_session_deleted
import dev.justinsmith.bells.session.resources.session_undo
import dev.justinsmith.bells.ui.liftName
import dev.justinsmith.bells.ui.programName
import dev.justinsmith.bells.ui.setLine
import dev.justinsmith.interval.ui.BackAction
import dev.justinsmith.interval.ui.ConfirmDialog
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalMenu
import dev.justinsmith.interval.ui.IntervalMenuItem
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.LocalNotices
import dev.justinsmith.interval.ui.NoticeAction
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.TabGrid
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.currentKoinScope

/** Resolves a finished session's ViewModel and binds it to [LiftDetailScreen]; [inPane] beside History's log. */
@Composable
fun LiftDetailRoute(uid: String, onBack: () -> Unit, modifier: Modifier = Modifier, inPane: Boolean = false) {
    val scope = currentKoinScope()
    val viewModel = viewModel(key = uid) { LiftDetailViewModel(uid, scope.get(), scope.get()) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notices = LocalNotices.current
    val undo = stringResource(Res.string.session_undo)
    val deleted = stringResource(Res.string.notice_session_deleted)
    LiftDetailScreen(
        state,
        onDelete = {
            viewModel.delete()
            notices.show(deleted, NoticeAction(undo, viewModel::restore))
        },
        onBack = onBack,
        modifier = modifier,
        inPane = inPane,
    )
}

/**
 * A finished session's page: its title, when and which program day it was, its numbers and records, every
 * set by lift, and Delete (from the overflow, behind a confirmation). On a wider window the lifts sit side by
 * side. Once the session is gone, deleted from here or elsewhere, the page goes back. Beside History's log
 * ([inPane]) it closes rather than going back.
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
    var menu by rememberSaveable { mutableStateOf(false) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    val gutter = Modifier.padding(horizontal = Spacing.space16)
    // One column on a phone; a wider window puts the numbers in one column and the lifts beside them.
    TabGrid(
        session.title,
        modifier,
        navigation = { if (!inPane) BackAction(onBack) },
        actions = {
            if (inPane) IconAction(IntervalIcons.Close, stringResource(Res.string.history_close_cd), onBack)
            IntervalMenu(
                expanded = menu,
                onExpandedChange = { menu = it },
                items = {
                    IntervalMenuItem(
                        stringResource(Res.string.history_delete),
                        onClick = { confirming = true },
                        destructive = true,
                    )
                },
            ) {
                IconAction(
                    IntervalIcons.More,
                    stringResource(Res.string.history_more_options_cd),
                    onClick = { menu = true },
                )
            }
        },
    ) {
        item(key = "when", span = StaggeredGridItemSpan.FullLine) {
            When(state, gutter.padding(bottom = Spacing.space16))
        }
        // A cell of its own, so on a wide window the numbers keep a column's width and the lifts sit beside them.
        item(key = "numbers") { SessionStats(session, state.records, gutter.padding(bottom = Spacing.space16)) }
        session.sets.groupBy { it.exercise to it.double }.forEach { (lift, sets) ->
            item(key = "lift-${lift.first.key}-${lift.second}") {
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
            destructive = true,
        )
    }
}

/** When the session was, and under it the program day it was, if it was one. */
@Composable
private fun When(state: LiftDetailUiState, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.space4)) {
        Text(
            stringResource(Res.string.common_joined, state.date, state.time),
            style = IntervalTheme.type.meta,
            color = palette.textDim,
        )
        val program = state.program
        val day = state.session?.day
        if (program != null && day != null) {
            Text(
                stringResource(Res.string.history_program_day, programName(program), day),
                style = IntervalTheme.type.meta,
                color = palette.textDim,
            )
        }
    }
}
