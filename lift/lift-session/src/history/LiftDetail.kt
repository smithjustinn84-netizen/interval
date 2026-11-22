package dev.justinsmith.lift.history

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.justinsmith.interval.platform.Sharer
import dev.justinsmith.interval.ui.BackAction
import dev.justinsmith.interval.ui.ConfirmDialog
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.HowWasIt
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalMenu
import dev.justinsmith.interval.ui.IntervalMenuItem
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.LocalNotices
import dev.justinsmith.interval.ui.NoticeAction
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.TabGrid
import dev.justinsmith.interval.ui.effortWord
import dev.justinsmith.interval.ui.pressScale
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.MuscleMap
import dev.justinsmith.lift.domain.PerceivedEffort
import dev.justinsmith.lift.domain.SetLog
import dev.justinsmith.lift.domain.newRecords
import dev.justinsmith.lift.session.SessionStats
import dev.justinsmith.lift.session.resources.Res
import dev.justinsmith.lift.session.resources.common_cancel
import dev.justinsmith.lift.session.resources.common_joined
import dev.justinsmith.lift.session.resources.detail_how_it_went
import dev.justinsmith.lift.session.resources.detail_muscles_worked
import dev.justinsmith.lift.session.resources.history_close_cd
import dev.justinsmith.lift.session.resources.history_delete
import dev.justinsmith.lift.session.resources.history_delete_text
import dev.justinsmith.lift.session.resources.history_delete_title
import dev.justinsmith.lift.session.resources.history_more_options_cd
import dev.justinsmith.lift.session.resources.history_program_day
import dev.justinsmith.lift.session.resources.history_share
import dev.justinsmith.lift.session.resources.notice_session_deleted
import dev.justinsmith.lift.session.resources.session_edit_set_cd
import dev.justinsmith.lift.session.resources.session_set_removed
import dev.justinsmith.lift.session.resources.session_undo
import dev.justinsmith.lift.session.resources.summary_note_placeholder
import dev.justinsmith.lift.session.sessionShareText
import dev.justinsmith.lift.ui.MusclesWorkedCard
import dev.justinsmith.lift.ui.liftName
import dev.justinsmith.lift.ui.programName
import dev.justinsmith.lift.ui.setLine
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Resolves a finished session's ViewModel and binds it to [LiftDetailScreen]; [inPane] beside History's log. */
@Composable
fun LiftDetailRoute(uid: String, onBack: () -> Unit, modifier: Modifier = Modifier, inPane: Boolean = false) {
    val sharer = koinInject<Sharer>()
    val viewModel = koinViewModel<LiftDetailViewModel>(key = uid) { parametersOf(uid) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Each time the page comes into view without a heart rate: a watch may have synced since the summary.
    val unread = state.session?.averageBpm == null
    LifecycleStartEffect(unread) {
        if (unread) viewModel.readHeartRate()
        onStopOrDispose {}
    }
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
        onShare = sharer::shareText,
        onEffortChange = viewModel::setEffort,
        onNoteChange = viewModel::setNote,
        onUpdateSet = viewModel::updateSet,
        onRemoveSet = { set, removedText ->
            viewModel.removeSet(set.id)?.let { removed ->
                notices.show(removedText, NoticeAction(undo) { viewModel.restoreSet(removed) })
            }
        },
    )
}

/**
 * A finished session's page: its title, when and which program day it was, its numbers and records, inline
 * editing for effort, notes, and every logged set by lift, and from the overflow Share, in words, and Delete
 * (behind a confirmation). On a wider window the lifts sit side by side. Once the session is gone, deleted
 * from here or elsewhere, the page goes back. Beside History's log ([inPane]) it closes rather than going back.
 */
@Composable
fun LiftDetailScreen(
    state: LiftDetailUiState,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    inPane: Boolean = false,
    onShare: (String) -> Unit = {},
    onEffortChange: (PerceivedEffort?) -> Unit = {},
    onNoteChange: (String) -> Unit = {},
    onUpdateSet: (SetLog) -> Unit = {},
    onRemoveSet: (SetLog, removedText: String) -> Unit = { _, _ -> },
) {
    val session = state.session
    val back by rememberUpdatedState(onBack)
    LaunchedEffect(session == null) { if (session == null) back() }
    if (session == null) return
    val shareText =
        sessionShareText(session, state.records, stringResource(Res.string.common_joined, state.date, state.time))
    var menu by rememberSaveable { mutableStateOf(false) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    var editingSetId by rememberSaveable { mutableStateOf<Long?>(null) }
    val editingSet = editingSetId?.let { id -> session.sets.firstOrNull { it.id == id } }
    val muscles = remember(session) { MuscleMap.of(session) }
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
                    DetailMenuItems(
                        onShare = {
                            menu = false
                            onShare(shareText)
                        },
                        onDelete = { confirming = true },
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
        item(key = "numbers") { Numbers(session, state, gutter.padding(bottom = Spacing.space16)) }
        item(key = "how") {
            HowItWent(session, onEffortChange, onNoteChange, gutter.padding(bottom = Spacing.space16))
        }
        session.sets.groupBy { it.exercise to it.double }.forEach { (lift, sets) ->
            item(key = "lift-${lift.first.key}-${lift.second}") {
                // Spaced below rather than above, so beside the numbers a lift's label lines up with their top.
                LiftSets(
                    liftName(lift.first, lift.second),
                    sets,
                    onEditSet = { editingSetId = it.id },
                    modifier = gutter.padding(bottom = Spacing.space16),
                )
            }
        }
        if (!muscles.isEmpty) {
            item(key = "muscles") { MusclesWorked(muscles, gutter.padding(bottom = Spacing.space16)) }
        }
    }
    LiftDetailOverlays(
        state,
        session,
        editingSet,
        confirming,
        onUpdateSet,
        onRemoveSet,
        onDismissEdit = { editingSetId = null },
        onConfirmDelete = {
            confirming = false
            onDelete()
        },
        onDismissDelete = { confirming = false },
    )
}

@Composable
private fun LiftDetailOverlays(
    state: LiftDetailUiState,
    session: LiftSession,
    editingSet: SetLog?,
    confirming: Boolean,
    onUpdateSet: (SetLog) -> Unit,
    onRemoveSet: (SetLog, removedText: String) -> Unit,
    onDismissEdit: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
) {
    editingSet?.let { set ->
        val removedText = stringResource(Res.string.session_set_removed, setLine(set))
        EditSetSheet(
            set = set,
            state = state,
            canRemove = session.sets.size > 1,
            onSave = onUpdateSet,
            onRemove = { onRemoveSet(it, removedText) },
            onDismiss = onDismissEdit,
        )
    }
    if (confirming) {
        ConfirmDialog(
            title = stringResource(Res.string.history_delete_title),
            text = stringResource(Res.string.history_delete_text),
            confirm = stringResource(Res.string.history_delete),
            dismiss = stringResource(Res.string.common_cancel),
            onConfirm = onConfirmDelete,
            onDismiss = onDismissDelete,
            destructive = true,
        )
    }
}

/** The overflow's choices: Share, and Delete in the danger colour. */
@Composable
private fun DetailMenuItems(onShare: () -> Unit, onDelete: () -> Unit) {
    IntervalMenuItem(stringResource(Res.string.history_share), onClick = onShare)
    IntervalMenuItem(stringResource(Res.string.history_delete), onClick = onDelete, destructive = true)
}

/** The session's numbers, its heart rate's zones by the max heart rate set in Settings. */
@Composable
private fun Numbers(session: LiftSession, state: LiftDetailUiState, modifier: Modifier = Modifier) {
    SessionStats(session, state.records, modifier, maxBpm = state.maxHeartRate)
}

/** One lift's logged sets in a card under its name; tapping a set opens it for editing. */
@Composable
private fun LiftSets(
    name: String,
    sets: List<SetLog>,
    onEditSet: (SetLog) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = IntervalTheme.palette
    Column(modifier) {
        GroupLabel(name)
        ListCard {
            sets.forEachIndexed { j, set ->
                if (j > 0) RowDivider()
                val line = setLine(set)
                val editCd = stringResource(Res.string.session_edit_set_cd, line)
                val interaction = remember { MutableInteractionSource() }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Sizes.listRow)
                        .clickable(
                            interaction,
                            LocalIndication.current,
                            onClickLabel = editCd,
                            role = Role.Button,
                            onClick = { onEditSet(set) },
                        )
                        .pressScale(interaction)
                        .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
                ) {
                    Text(
                        line,
                        style = IntervalTheme.type.body,
                        color = palette.text,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(IntervalIcons.Chevron, null, tint = palette.textDim, modifier = Modifier.size(Sizes.icon))
                }
            }
        }
    }
}

/** The muscles the session worked, as a body map over their names. */
@Composable
private fun MusclesWorked(muscles: MuscleMap, modifier: Modifier = Modifier) {
    Column(modifier) {
        GroupLabel(stringResource(Res.string.detail_muscles_worked))
        MusclesWorkedCard(muscles)
    }
}

/** How hard it felt and the note on it, editable inline. */
@Composable
private fun HowItWent(
    session: LiftSession,
    onEffortChange: (PerceivedEffort?) -> Unit,
    onNoteChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        ListCard {
            key(session.uid) {
                HowWasIt(
                    title = stringResource(Res.string.detail_how_it_went),
                    efforts = PerceivedEffort.entries,
                    effort = session.effort,
                    effortLabel = { effortWord(it.level) },
                    onEffortChange = { picked -> onEffortChange(if (picked == session.effort) null else picked) },
                    note = session.note.orEmpty(),
                    onNoteChange = onNoteChange,
                    notePlaceholder = stringResource(Res.string.summary_note_placeholder),
                    noteMaxLength = PerceivedEffort.NOTE_MAX_LENGTH,
                    modifier = Modifier.padding(Spacing.space16),
                )
            }
        }
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

/** A session of swings then goblet squats, a set every two minutes, for the preview. */
private val previewSets = List(6) { index ->
    val exercise = if (index < 4) BuiltInExercise.TwoHandSwing else BuiltInExercise.GobletSquat
    SetLog.of(index + 1L, exercise, kg = if (index < 4) 24.0 else 16.0, reps = 10, side = null, index * 120_000L)
}

@PreviewFormFactors
@Composable
private fun LiftDetailScreenPreview() {
    val session = LiftSession(
        "s",
        startedAtEpochMillis = 0,
        finishedAtEpochMillis = 1_500_000,
        title = "Swings and squats",
        sets = previewSets,
        effort = PerceivedEffort.Hard,
        note = "Swings felt snappy. Squats slow out of the hole.",
        averageBpm = 132,
        peakBpm = 164,
    )
    IntervalTheme {
        LiftDetailScreen(
            LiftDetailUiState(session, "Saturday, Nov 14", "7:42 AM", newRecords(emptyList(), session)),
            onDelete = {},
            onBack = {},
        )
    }
}
