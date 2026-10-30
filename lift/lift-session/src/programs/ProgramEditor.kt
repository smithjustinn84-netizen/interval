package dev.justinsmith.bells.programs

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import dev.justinsmith.bells.domain.Band
import dev.justinsmith.bells.domain.Block
import dev.justinsmith.bells.domain.ExerciseFilters
import dev.justinsmith.bells.domain.ProgramDraft
import dev.justinsmith.bells.domain.withExercise
import dev.justinsmith.bells.library.ExercisePicker
import dev.justinsmith.bells.session.resources.Res
import dev.justinsmith.bells.session.resources.block_done
import dev.justinsmith.bells.session.resources.block_remove
import dev.justinsmith.bells.session.resources.block_title
import dev.justinsmith.bells.session.resources.common_cancel
import dev.justinsmith.bells.session.resources.editor_add_block
import dev.justinsmith.bells.session.resources.editor_add_day
import dev.justinsmith.bells.session.resources.editor_day
import dev.justinsmith.bells.session.resources.editor_days_hint
import dev.justinsmith.bells.session.resources.editor_days_per_week
import dev.justinsmith.bells.session.resources.editor_delete
import dev.justinsmith.bells.session.resources.editor_delete_confirm
import dev.justinsmith.bells.session.resources.editor_delete_text
import dev.justinsmith.bells.session.resources.editor_delete_title
import dev.justinsmith.bells.session.resources.editor_discard_confirm
import dev.justinsmith.bells.session.resources.editor_discard_text
import dev.justinsmith.bells.session.resources.editor_discard_title
import dev.justinsmith.bells.session.resources.editor_edit_title
import dev.justinsmith.bells.session.resources.editor_keep_editing
import dev.justinsmith.bells.session.resources.editor_name_label
import dev.justinsmith.bells.session.resources.editor_name_placeholder
import dev.justinsmith.bells.session.resources.editor_new_title
import dev.justinsmith.bells.session.resources.editor_remove_day
import dev.justinsmith.bells.session.resources.editor_remove_day_cd
import dev.justinsmith.bells.session.resources.editor_save
import dev.justinsmith.bells.session.resources.editor_save_hint
import dev.justinsmith.bells.session.resources.editor_schedule
import dev.justinsmith.bells.session.resources.editor_weeks
import dev.justinsmith.bells.session.resources.notice_block_removed
import dev.justinsmith.bells.session.resources.notice_day_removed
import dev.justinsmith.bells.session.resources.notice_program_saved
import dev.justinsmith.bells.session.resources.session_undo
import dev.justinsmith.bells.ui.blockLine
import dev.justinsmith.bells.ui.liftName
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.BackAction
import dev.justinsmith.interval.ui.ConfirmDialog
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalSheet
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.LinkRow
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.LocalNotices
import dev.justinsmith.interval.ui.NameField
import dev.justinsmith.interval.ui.NoticeAction
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.ScrollPage
import dev.justinsmith.interval.ui.SecondaryButton
import dev.justinsmith.interval.ui.SheetColumns
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.pressScale
import dev.justinsmith.interval.ui.sheetIsSideways
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.currentKoinScope

/** What the editor does: change the draft or narrow its exercises, save it under a name, delete it, or go back. */
data class ProgramEditorActions(
    val onChange: ((ProgramDraft) -> ProgramDraft) -> Unit = {},
    val onFilters: ((ExerciseFilters) -> ExerciseFilters) -> Unit = {},
    val onSave: (name: String) -> Unit = {},
    val onDelete: () -> Unit = {},
    val onBack: () -> Unit = {},
)

@Composable
fun ProgramEditorRoute(uid: String?, onBack: () -> Unit) {
    val scope = currentKoinScope()
    val viewModel = viewModel { ProgramEditorViewModel(scope.get(), scope.get(), uid, createSavedStateHandle()) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProgramEditorScreen(
        state,
        ProgramEditorActions(
            onChange = viewModel::change,
            onFilters = viewModel::filter,
            onSave = { if (viewModel.save(it)) onBack() },
            onDelete = {
                viewModel.delete()
                onBack()
            },
            onBack = onBack,
        ),
    )
}

/** A program's name, schedule and days of blocks; a block opens in a sheet to change. */
@Composable
fun ProgramEditorScreen(state: ProgramEditorUiState, actions: ProgramEditorActions, modifier: Modifier = Modifier) {
    val draft = state.draft
    val name = rememberTextFieldState(draft.name)
    val onChange by rememberUpdatedState(actions.onChange)
    val currentDraftName by rememberUpdatedState(draft.name)
    LaunchedEffect(name.text) {
        val typed = name.text.toString()
        if (typed != currentDraftName) onChange { it.copy(name = typed) }
    }
    // Day and block of the one open in the sheet, -1 for none: plain numbers save on every platform.
    var editingDay by rememberSaveable { mutableIntStateOf(-1) }
    var editingBlock by rememberSaveable { mutableIntStateOf(-1) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var discarding by rememberSaveable { mutableStateOf(false) }
    val typedName = name.text.toString()
    val currentDraft = draft.copy(name = typedName)
    val dirty = currentDraft.copy(name = typedName.trim()) !=
        state.initialDraft.copy(name = state.initialDraft.name.trim())
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = dirty,
        onBackCompleted = { discarding = true },
    )
    val bodyActions = actions.copy(
        onBack = { if (dirty) discarding = true else actions.onBack() },
        onDelete = { deleting = true },
    )
    Box(modifier) {
        ProgramEditorBody(
            state = state,
            name = name,
            actions = bodyActions,
            onOpenBlock = { day, index ->
                editingDay = day
                editingBlock = index
            },
        )
        EditingBlock(
            state,
            actions,
            editingDay,
            editingBlock,
            onRestore = { snapshot -> actions.onChange { snapshot.copy(name = name.text.toString()) } },
            onClose = {
                editingDay = -1
                editingBlock = -1
            },
        )
    }
    if (deleting) {
        ConfirmDialog(
            title = stringResource(Res.string.editor_delete_title),
            text = stringResource(Res.string.editor_delete_text),
            confirm = stringResource(Res.string.editor_delete_confirm),
            dismiss = stringResource(Res.string.common_cancel),
            onConfirm = {
                deleting = false
                actions.onDelete()
            },
            onDismiss = { deleting = false },
            destructive = true,
        )
    }
    if (discarding) {
        ConfirmDialog(
            title = stringResource(Res.string.editor_discard_title),
            text = stringResource(Res.string.editor_discard_text),
            confirm = stringResource(Res.string.editor_discard_confirm),
            dismiss = stringResource(Res.string.editor_keep_editing),
            onConfirm = {
                discarding = false
                actions.onBack()
            },
            onDismiss = { discarding = false },
            destructive = true,
        )
    }
}

@Composable
private fun ProgramEditorBody(
    state: ProgramEditorUiState,
    name: TextFieldState,
    actions: ProgramEditorActions,
    onOpenBlock: (day: Int, block: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = state.draft
    val typedName = name.text.toString()
    val currentDraft = draft.copy(name = typedName)
    val notices = LocalNotices.current
    val undo = stringResource(Res.string.session_undo)
    val title = stringResource(if (state.saved) Res.string.editor_edit_title else Res.string.editor_new_title)
    ScrollPage(
        title,
        modifier,
        navigation = { BackAction(actions.onBack) },
    ) {
        NameField(
            state = name,
            placeholder = stringResource(Res.string.editor_name_placeholder),
            label = stringResource(Res.string.editor_name_label),
            maxLength = ProgramDraft.MAX_NAME_LENGTH,
            capitalization = KeyboardCapitalization.Words,
        )
        ScheduleCard(draft, actions.onChange)
        draft.days.forEachIndexed { day, blocks ->
            val removedDayText = stringResource(Res.string.notice_day_removed, day + 1)
            DayCard(
                day,
                blocks,
                state.choices.bands,
                canRemove = draft.days.size > 1,
                onOpen = { index -> onOpenBlock(day, index) },
                onAdd = {
                    actions.onChange { it.addBlock(day) }
                    onOpenBlock(day, blocks.size)
                },
                onRemove = {
                    val snapshot = currentDraft
                    actions.onChange { it.removeDay(day) }
                    notices.show(
                        removedDayText,
                        NoticeAction(undo) { actions.onChange { snapshot.copy(name = name.text.toString()) } },
                    )
                },
            )
        }
        if (draft.days.size < ProgramDraft.MAX_DAYS) {
            SecondaryButton(
                stringResource(Res.string.editor_add_day),
                { actions.onChange { it.addDay() } },
                Modifier.fillMaxWidth().padding(top = Spacing.space16),
            )
        }
        EditorFooter(
            saved = state.saved,
            canSave = currentDraft.canSave,
            programName = typedName,
            onSave = actions.onSave,
            onDelete = actions.onDelete,
        )
    }
}

@Composable
private fun EditorFooter(
    saved: Boolean,
    canSave: Boolean,
    programName: String,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val notices = LocalNotices.current
    val savedText = stringResource(Res.string.notice_program_saved, programName.trim())
    Column(modifier.fillMaxWidth()) {
        AccentButton(
            stringResource(Res.string.editor_save),
            {
                notices.show(savedText)
                onSave(programName)
            },
            Modifier.fillMaxWidth().padding(top = Spacing.space8),
            enabled = canSave,
        )
        if (!canSave) {
            Text(
                stringResource(Res.string.editor_save_hint),
                style = IntervalTheme.type.meta,
                color = IntervalTheme.palette.textDim,
                modifier = Modifier.padding(horizontal = Spacing.space4, vertical = Spacing.space4),
            )
        }
        if (saved) {
            QuietTextButton(
                stringResource(Res.string.editor_delete),
                onDelete,
                Modifier.padding(top = Spacing.space12),
                destructive = true,
            )
        }
    }
}

/** Block [index] of day [day], open in its sheet while there is one. */
@Composable
private fun EditingBlock(
    state: ProgramEditorUiState,
    actions: ProgramEditorActions,
    day: Int,
    index: Int,
    onRestore: (ProgramDraft) -> Unit,
    onClose: () -> Unit,
) {
    val block = state.draft.days.getOrNull(day)?.getOrNull(index) ?: return
    val notices = LocalNotices.current
    val undo = stringResource(Res.string.session_undo)
    val removedText = stringResource(Res.string.notice_block_removed, liftName(block.exercise, block.double))
    BlockSheet(
        block,
        state.choices,
        actions.onFilters,
        onChange = { transform -> actions.onChange { it.changeBlock(day, index, transform) } },
        onRemove = {
            val snapshot = state.draft
            actions.onChange { it.removeBlock(day, index) }
            notices.show(removedText, NoticeAction(undo) { onRestore(snapshot) })
        },
        onDismiss = onClose,
    )
}

/** How often and how long: days a week and weeks, then how the days follow. */
@Composable
private fun ScheduleCard(draft: ProgramDraft, onChange: ((ProgramDraft) -> ProgramDraft) -> Unit) {
    GroupLabel(stringResource(Res.string.editor_schedule))
    ListCard {
        StepperRow(
            stringResource(Res.string.editor_days_per_week),
            draft.daysPerWeek,
            1..ProgramDraft.MAX_DAYS_PER_WEEK,
            onSet = { n -> onChange { it.copy(daysPerWeek = n) } },
            modifier = Modifier.padding(Spacing.space16),
        )
        RowDivider()
        StepperRow(
            stringResource(Res.string.editor_weeks),
            draft.weeks,
            1..ProgramDraft.MAX_WEEKS,
            onSet = { n -> onChange { it.copy(weeks = n) } },
            modifier = Modifier.padding(Spacing.space16),
        )
    }
    Text(
        stringResource(Res.string.editor_days_hint),
        style = IntervalTheme.type.meta,
        color = IntervalTheme.palette.textDim,
        modifier = Modifier.padding(start = Spacing.space4, top = Spacing.space12),
    )
}

/** Day [day] (from 0) of the program: its blocks, each opening on a tap, and Add block. */
@Composable
private fun DayCard(
    day: Int,
    blocks: List<Block>,
    bands: List<Band>,
    canRemove: Boolean,
    onOpen: (Int) -> Unit,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
    val palette = IntervalTheme.palette
    val removeDayCd = stringResource(Res.string.editor_remove_day_cd, day + 1)
    Row(verticalAlignment = Alignment.CenterVertically) {
        GroupLabel(stringResource(Res.string.editor_day, day + 1), Modifier.weight(1f))
        if (canRemove) {
            QuietTextButton(
                stringResource(Res.string.editor_remove_day),
                onRemove,
                Modifier.semantics { contentDescription = removeDayCd },
            )
        }
    }
    ListCard {
        blocks.forEachIndexed { i, block ->
            val interaction = remember { MutableInteractionSource() }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Sizes.listRow)
                    .clickable(interaction, LocalIndication.current, role = Role.Button) { onOpen(i) }
                    .pressScale(interaction)
                    .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(liftName(block.exercise, block.double), style = IntervalTheme.type.label, color = palette.text)
                    Text(blockLine(block, bands), style = IntervalTheme.type.meta, color = palette.textDim)
                }
                Icon(IntervalIcons.Chevron, null, tint = palette.textDim, modifier = Modifier.size(Sizes.icon))
            }
            RowDivider()
        }
        LinkRow(stringResource(Res.string.editor_add_block), null, onClick = onAdd)
    }
}

/**
 * One block: its exercise, the load it names, how it runs, its rounds, and its reps, a hold's length or a
 * ladder's rungs. Change swaps the sheet for the library until an exercise is picked.
 */
@Composable
private fun BlockSheet(
    block: Block,
    choices: BlockChoices,
    onFilters: ((ExerciseFilters) -> ExerciseFilters) -> Unit,
    onChange: ((Block) -> Block) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Removed once the sheet has slid away, so it never shows a block that is gone.
    var removing by remember { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val sideways = sheetIsSideways()
    IntervalSheet(stringResource(Res.string.block_title), onDismiss = {
        if (removing) onRemove()
        onDismiss()
    }) { hide ->
        if (picking) {
            ExercisePicker(
                choices.library,
                onFilters,
                Modifier.padding(vertical = Spacing.space8),
                selected = block.exercise,
                onPick = { e ->
                    onChange { it.withExercise(e) }
                    picking = false
                },
            )
            return@IntervalSheet
        }
        SheetColumns(
            sideways,
            first = { BlockLoad(block, choices, onChange, onPickExercise = { picking = true }) },
            second = {
                BlockCounts(block, onChange)
                AccentButton(
                    stringResource(Res.string.block_done),
                    onClick = hide,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.space16),
                )
                QuietTextButton(
                    stringResource(Res.string.block_remove),
                    {
                        removing = true
                        hide()
                    },
                    Modifier.align(Alignment.CenterHorizontally),
                    destructive = true,
                )
            },
            modifier = Modifier.padding(vertical = Spacing.space8),
        )
    }
}
