package dev.justinsmith.interval.mobility

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.justinsmith.interval.mobility.resources.Res
import dev.justinsmith.interval.mobility.resources.flow_add_move
import dev.justinsmith.interval.mobility.resources.flow_clash
import dev.justinsmith.interval.mobility.resources.flow_copy_name
import dev.justinsmith.interval.mobility.resources.flow_delete
import dev.justinsmith.interval.mobility.resources.flow_deleted
import dev.justinsmith.interval.mobility.resources.flow_edit_title
import dev.justinsmith.interval.mobility.resources.flow_move_unnamed
import dev.justinsmith.interval.mobility.resources.flow_moves
import dev.justinsmith.interval.mobility.resources.flow_name_label
import dev.justinsmith.interval.mobility.resources.flow_name_placeholder
import dev.justinsmith.interval.mobility.resources.flow_new_title
import dev.justinsmith.interval.mobility.resources.flow_no_moves
import dev.justinsmith.interval.mobility.resources.flow_note_label
import dev.justinsmith.interval.mobility.resources.flow_note_placeholder
import dev.justinsmith.interval.mobility.resources.flow_save
import dev.justinsmith.interval.mobility.resources.flow_saved
import dev.justinsmith.interval.mobility.resources.flow_total
import dev.justinsmith.interval.mobility.resources.flow_undo
import dev.justinsmith.interval.mobility.resources.flow_use
import dev.justinsmith.interval.mobility.resources.flow_use_after
import dev.justinsmith.interval.mobility.resources.flow_use_before
import dev.justinsmith.interval.mobility.resources.mobility_move_time_sides
import dev.justinsmith.interval.timer.Flow
import dev.justinsmith.interval.timer.FlowDraft
import dev.justinsmith.interval.timer.FlowUse
import dev.justinsmith.interval.timer.Move
import dev.justinsmith.interval.timer.builtInFlows
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.timer.keyed
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.BackAction
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.LocalNotices
import dev.justinsmith.interval.ui.MoveThumb
import dev.justinsmith.interval.ui.NameField
import dev.justinsmith.interval.ui.NoticeAction
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.ScrollPage
import dev.justinsmith.interval.ui.SecondaryButton
import dev.justinsmith.interval.ui.SegmentedPicker
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.demo
import dev.justinsmith.interval.ui.pressScale
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Resolves the editor's ViewModel for the flow [id] (one of the user's), a copy of the shipped flow [copyOf], or a
 * new one, and binds it to [FlowEditorScreen]. [onDone] leaves it, saved, deleted or not.
 */
@Composable
fun FlowEditorRoute(id: Long?, copyOf: String?, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val copyName = copyOf?.let { key ->
        builtInFlows.keyed(key)?.let { stringResource(Res.string.flow_copy_name, it.name) }
    }
    val copy = copyOf?.let { key -> copyName?.let { FlowCopy(key, it) } }
    val viewModel = koinViewModel<FlowEditorViewModel>(key = "flow-$id-$copyOf") { parametersOf(id, copy) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val done by rememberUpdatedState(onDone)
    val notices = LocalNotices.current
    val saved = stringResource(Res.string.flow_saved)
    val deleted = stringResource(Res.string.flow_deleted)
    val undo = stringResource(Res.string.flow_undo)
    val actions = remember(viewModel, notices, saved, deleted, undo) {
        FlowEditorActions(
            onChange = viewModel::change,
            onSave = {
                if (viewModel.save()) {
                    notices.show(saved)
                    done()
                }
            },
            onDelete = {
                viewModel.delete()?.let { flow ->
                    notices.show(deleted, NoticeAction(undo) { viewModel.restore(flow) })
                }
                done()
            },
            onBack = { done() },
        )
    }
    FlowEditorScreen(state, actions, modifier)
}

/** What the editor's controls do. Each defaults to nothing, so a preview or test sets only its own. */
@Immutable
class FlowEditorActions(
    val onChange: ((FlowDraft) -> FlowDraft) -> Unit = {},
    val onSave: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/**
 * A flow being built: its name, when it is for and a note, then its moves in order, each opening on a sheet to
 * change it, and Add a move, which offers the library's mat moves and holds and one typed in. Save is there once it
 * is a flow and like no other; a draft with another's moves and times says which.
 */
@Composable
fun FlowEditorScreen(state: FlowEditorUiState, actions: FlowEditorActions, modifier: Modifier = Modifier) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<Int?>(null) }
    val draft = state.draft
    val title = stringResource(if (state.edits) Res.string.flow_edit_title else Res.string.flow_new_title)
    ScrollPage(title, modifier, navigation = { BackAction(actions.onBack) }) {
        FlowWords(draft, actions.onChange)
        GroupLabel(stringResource(Res.string.flow_moves))
        MoveList(draft.moves, onOpen = { editing = it })
        SecondaryButton(
            stringResource(Res.string.flow_add_move),
            { adding = true },
            Modifier.fillMaxWidth().padding(top = Spacing.space12),
            enabled = draft.roomForMore,
        )
        Footer(state, actions)
    }
    if (adding) {
        AddMoveSheet(
            onPick = { move ->
                adding = false
                actions.onChange { it.adding(move) }
                editing = draft.moves.size
            },
            onDismiss = { adding = false },
        )
    }
    editing?.let { index -> draft.moves.getOrNull(index) }?.let { move ->
        val index = checkNotNull(editing)
        MoveSheet(
            move,
            onChange = { changed -> actions.onChange { it.replacing(index, changed) } },
            actions = MoveActions(
                onUp = {
                    actions.onChange { it.moving(index, -1) }
                    editing = index - 1
                }.takeIf { index > 0 },
                onDown = {
                    actions.onChange { it.moving(index, 1) }
                    editing = index + 1
                }
                    .takeIf { index < draft.moves.lastIndex },
                onRemove = {
                    editing = null
                    actions.onChange { it.removing(index) }
                },
            ),
            onDismiss = { editing = null },
        )
    }
}

/** The flow's name, when it is for, and its note. */
@Composable
private fun FlowWords(draft: FlowDraft, onChange: ((FlowDraft) -> FlowDraft) -> Unit) {
    val name = rememberTextFieldState(draft.name)
    val note = rememberTextFieldState(draft.note)
    val change by rememberUpdatedState(onChange)
    LaunchedEffect(name, note) {
        snapshotFlow { name.text.toString() to note.text.toString() }.collect { (typedName, typedNote) ->
            change {
                if (it.name == typedName &&
                    it.note == typedNote
                ) {
                    it
                } else {
                    it.copy(name = typedName, note = typedNote)
                }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        NameField(
            name,
            placeholder = stringResource(Res.string.flow_name_placeholder),
            label = stringResource(Res.string.flow_name_label),
            maxLength = Flow.NAME_MAX_LENGTH,
            capitalization = KeyboardCapitalization.Sentences,
        )
        GroupLabel(stringResource(Res.string.flow_use))
        SegmentedPicker(
            FlowUse.entries,
            draft.use,
            label = {
                stringResource(
                    if (it ==
                        FlowUse.Before
                    ) {
                        Res.string.flow_use_before
                    } else {
                        Res.string.flow_use_after
                    },
                )
            },
            onSelect = { use -> change { it.copy(use = use) } },
            check = false,
            maxLines = 2,
        )
        NameField(
            note,
            placeholder = stringResource(Res.string.flow_note_placeholder),
            label = stringResource(Res.string.flow_note_label),
            maxLength = Flow.NOTE_MAX_LENGTH,
            modifier = Modifier.padding(top = Spacing.space8),
        )
    }
}

/** The moves in order, each a row that opens it; or, with none, a line saying so. */
@Composable
private fun MoveList(moves: List<Move>, onOpen: (Int) -> Unit) {
    val palette = IntervalTheme.palette
    if (moves.isEmpty()) {
        Text(stringResource(Res.string.flow_no_moves), style = IntervalTheme.type.meta, color = palette.textDim)
        return
    }
    ListCard {
        moves.forEachIndexed { index, move ->
            if (index > 0) RowDivider()
            EditorMoveRow(move, onOpen = { onOpen(index) })
        }
    }
}

@Composable
private fun EditorMoveRow(move: Move, onOpen: () -> Unit) {
    val palette = IntervalTheme.palette
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.listRow)
            .clickable(interaction, LocalIndication.current, role = Role.Button, onClick = onOpen)
            .pressScale(interaction)
            .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space12), verticalAlignment = Alignment.Top) {
                Text(
                    move.name.ifBlank { stringResource(Res.string.flow_move_unnamed) },
                    style = IntervalTheme.type.label,
                    color = if (move.name.isBlank()) palette.danger else palette.text,
                    modifier = Modifier.weight(1f),
                )
                val time = formatClock(move.seconds)
                Text(
                    if (move.sided) stringResource(Res.string.mobility_move_time_sides, time) else time,
                    style = IntervalTheme.type.label,
                    color = palette.textDim,
                )
            }
            if (move.cue.isNotBlank()) Text(move.cue, style = IntervalTheme.type.meta, color = palette.textDim)
        }
        move.demo?.let { MoveThumb(it) }
    }
}

/** How long it runs, what it clashes with, Save, and Delete for one already saved. */
@Composable
private fun Footer(state: FlowEditorUiState, actions: FlowEditorActions) {
    val palette = IntervalTheme.palette
    Column(Modifier.padding(top = Spacing.space16), verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        state.total?.let {
            Text(stringResource(Res.string.flow_total, it), style = IntervalTheme.type.meta, color = palette.textDim)
        }
        state.clash?.let {
            Text(
                stringResource(Res.string.flow_clash, it.name),
                style = IntervalTheme.type.meta,
                color = palette.danger,
            )
        }
        AccentButton(stringResource(Res.string.flow_save), actions.onSave, Modifier.fillMaxWidth(), state.canSave)
        if (state.edits) {
            QuietTextButton(
                stringResource(Res.string.flow_delete),
                actions.onDelete,
                Modifier.align(Alignment.CenterHorizontally),
                destructive = true,
            )
        }
    }
}
