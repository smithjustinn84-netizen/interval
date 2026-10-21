package dev.justinsmith.bells.programs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.justinsmith.bells.data.BellsRepository
import dev.justinsmith.bells.domain.Block
import dev.justinsmith.bells.domain.BlockKind
import dev.justinsmith.bells.domain.Doubles
import dev.justinsmith.bells.domain.Exercise
import dev.justinsmith.bells.domain.ProgramDraft
import dev.justinsmith.bells.domain.ProgramKey
import dev.justinsmith.bells.domain.asKind
import dev.justinsmith.bells.domain.withExercise
import dev.justinsmith.bells.resources.Res
import dev.justinsmith.bells.resources.block_add_rung
import dev.justinsmith.bells.resources.block_done
import dev.justinsmith.bells.resources.block_kind
import dev.justinsmith.bells.resources.block_kind_emom
import dev.justinsmith.bells.resources.block_kind_ladder
import dev.justinsmith.bells.resources.block_kind_sets
import dev.justinsmith.bells.resources.block_remove
import dev.justinsmith.bells.resources.block_remove_rung
import dev.justinsmith.bells.resources.block_reps
import dev.justinsmith.bells.resources.block_rounds_emom
import dev.justinsmith.bells.resources.block_rounds_ladder
import dev.justinsmith.bells.resources.block_rounds_sets
import dev.justinsmith.bells.resources.block_rung
import dev.justinsmith.bells.resources.block_rungs
import dev.justinsmith.bells.resources.block_title
import dev.justinsmith.bells.resources.common_cancel
import dev.justinsmith.bells.resources.editor_add_block
import dev.justinsmith.bells.resources.editor_add_day
import dev.justinsmith.bells.resources.editor_day
import dev.justinsmith.bells.resources.editor_days_hint
import dev.justinsmith.bells.resources.editor_days_per_week
import dev.justinsmith.bells.resources.editor_delete
import dev.justinsmith.bells.resources.editor_delete_confirm
import dev.justinsmith.bells.resources.editor_delete_text
import dev.justinsmith.bells.resources.editor_delete_title
import dev.justinsmith.bells.resources.editor_edit_title
import dev.justinsmith.bells.resources.editor_fewer_cd
import dev.justinsmith.bells.resources.editor_more_cd
import dev.justinsmith.bells.resources.editor_name_label
import dev.justinsmith.bells.resources.editor_name_placeholder
import dev.justinsmith.bells.resources.editor_new_title
import dev.justinsmith.bells.resources.editor_remove_day
import dev.justinsmith.bells.resources.editor_save
import dev.justinsmith.bells.resources.editor_schedule
import dev.justinsmith.bells.resources.editor_weeks
import dev.justinsmith.bells.resources.lift_double
import dev.justinsmith.bells.resources.log_exercise
import dev.justinsmith.bells.session.SetDraft
import dev.justinsmith.bells.ui.ChoiceRow
import dev.justinsmith.bells.ui.blockLine
import dev.justinsmith.bells.ui.exerciseName
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
import dev.justinsmith.interval.ui.NameField
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.RepeatButton
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.ScrollPage
import dev.justinsmith.interval.ui.SecondaryButton
import dev.justinsmith.interval.ui.SegmentedPicker
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.ToggleRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.currentKoinScope

/** The program being made or changed, and whether it is already saved (so it can be deleted). */
data class ProgramEditorUiState(val draft: ProgramDraft = ProgramDraft(), val saved: Boolean = false)

/** Makes a program, or changes the one the user made under [uid], until it is saved or deleted. */
class ProgramEditorViewModel(private val repository: BellsRepository, uid: String?) : ViewModel() {
    // A program deleted meanwhile (on another page of the back stack) is made afresh.
    private val existing =
        uid?.let { id -> repository.customPrograms.value.firstOrNull { it.key == ProgramKey.Custom(id) } }
    private val uid = (existing?.key as? ProgramKey.Custom)?.uid
    private val _state = MutableStateFlow(
        ProgramEditorUiState(existing?.let(ProgramDraft::of) ?: ProgramDraft(), saved = existing != null),
    )
    val state: StateFlow<ProgramEditorUiState> = _state.asStateFlow()

    fun change(transform: (ProgramDraft) -> ProgramDraft) {
        _state.update { it.copy(draft = transform(it.draft)) }
    }

    /** Saves the draft under [name]; false, and nothing saved, while it can't be. */
    fun save(name: String): Boolean {
        val draft = _state.value.draft.copy(name = name)
        if (!draft.canSave) return false
        repository.saveProgram(uid, draft)
        return true
    }

    fun delete() {
        uid?.let(repository::deleteProgram)
    }
}

/** What the editor does: change the draft, save it under a name, delete it, or go back. */
data class ProgramEditorActions(
    val onChange: ((ProgramDraft) -> ProgramDraft) -> Unit = {},
    val onSave: (name: String) -> Unit = {},
    val onDelete: () -> Unit = {},
    val onBack: () -> Unit = {},
)

@Composable
fun ProgramEditorRoute(uid: String?, onBack: () -> Unit) {
    val scope = currentKoinScope()
    val viewModel = viewModel { ProgramEditorViewModel(scope.get(), uid) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProgramEditorScreen(
        state,
        ProgramEditorActions(
            onChange = viewModel::change,
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
    // Day and block of the one open in the sheet, -1 for none: plain numbers save on every platform.
    var editingDay by rememberSaveable { mutableIntStateOf(-1) }
    var editingBlock by rememberSaveable { mutableIntStateOf(-1) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(if (state.saved) Res.string.editor_edit_title else Res.string.editor_new_title)
    ScrollPage(title, modifier, navigation = { BackAction(actions.onBack) }) {
        NameField(
            name,
            placeholder = stringResource(Res.string.editor_name_placeholder),
            onDone = {},
            label = stringResource(Res.string.editor_name_label),
            maxLength = ProgramDraft.MAX_NAME_LENGTH,
        )
        ScheduleCard(draft, actions.onChange)
        draft.days.forEachIndexed { day, blocks ->
            DayCard(
                day,
                blocks,
                canRemove = draft.days.size > 1,
                onOpen = { index ->
                    editingDay = day
                    editingBlock = index
                },
                onAdd = {
                    actions.onChange { it.addBlock(day) }
                    editingDay = day
                    editingBlock = blocks.size
                },
                onRemove = { actions.onChange { it.removeDay(day) } },
            )
        }
        if (draft.days.size < ProgramDraft.MAX_DAYS) {
            SecondaryButton(
                stringResource(Res.string.editor_add_day),
                { actions.onChange { it.addDay() } },
                Modifier.fillMaxWidth().padding(top = Spacing.space16),
            )
        }
        // Save is offered once there is something to save: a name, and a block on every day.
        if (draft.copy(name = name.text.toString()).canSave) {
            AccentButton(
                stringResource(Res.string.editor_save),
                { actions.onSave(name.text.toString()) },
                Modifier.fillMaxWidth().padding(top = Spacing.space8),
            )
        }
        if (state.saved) {
            QuietTextButton(
                stringResource(Res.string.editor_delete),
                { deleting = true },
                Modifier.padding(top = Spacing.space12),
            )
        }
    }
    draft.days.getOrNull(editingDay)?.getOrNull(editingBlock)?.let { block ->
        val day = editingDay
        val index = editingBlock
        BlockSheet(
            block,
            onChange = { transform -> actions.onChange { it.changeBlock(day, index, transform) } },
            onRemove = { actions.onChange { it.removeBlock(day, index) } },
            onDismiss = {
                editingDay = -1
                editingBlock = -1
            },
        )
    }
    if (deleting) {
        DeleteDialog(
            onConfirm = {
                deleting = false
                actions.onDelete()
            },
            onDismiss = { deleting = false },
        )
    }
}

@Composable
private fun DeleteDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = stringResource(Res.string.editor_delete_title),
        text = stringResource(Res.string.editor_delete_text),
        confirm = stringResource(Res.string.editor_delete_confirm),
        dismiss = stringResource(Res.string.common_cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
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
    canRemove: Boolean,
    onOpen: (Int) -> Unit,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
    val palette = IntervalTheme.palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        GroupLabel(stringResource(Res.string.editor_day, day + 1), Modifier.weight(1f))
        if (canRemove) QuietTextButton(stringResource(Res.string.editor_remove_day), onRemove)
    }
    ListCard {
        blocks.forEachIndexed { i, block ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Sizes.listRow)
                    .clickable { onOpen(i) }
                    .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(liftName(block.exercise, block.double), style = IntervalTheme.type.label, color = palette.text)
                    Text(blockLine(block), style = IntervalTheme.type.meta, color = palette.textDim)
                }
                Icon(IntervalIcons.Chevron, null, tint = palette.textDim, modifier = Modifier.size(Sizes.icon))
            }
            RowDivider()
        }
        LinkRow(stringResource(Res.string.editor_add_block), null, onClick = onAdd)
    }
}

/** One block: its lift (with two bells or one), how it runs, its rounds, and its reps or a ladder's rungs. */
@Composable
private fun BlockSheet(
    block: Block,
    onChange: ((Block) -> Block) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Removed once the sheet has slid away, so it never shows a block that is gone.
    var removing by remember { mutableStateOf(false) }
    IntervalSheet(stringResource(Res.string.block_title), onDismiss = {
        if (removing) onRemove()
        onDismiss()
    }) { hide ->
        Column(
            Modifier.padding(horizontal = Spacing.space20, vertical = Spacing.space8),
            verticalArrangement = Arrangement.spacedBy(Spacing.space8),
        ) {
            GroupLabel(stringResource(Res.string.log_exercise))
            ChoiceRow(Exercise.entries, block.exercise, {
                exerciseName(it)
            }, { e -> onChange { it.withExercise(e) } })
            if (block.exercise.doubles == Doubles.Optional) {
                ListCard {
                    ToggleRow(
                        stringResource(Res.string.lift_double),
                        null,
                        checked = block.double,
                        onToggle = { onChange { it.copy(double = !it.double) } },
                    )
                }
            }
            GroupLabel(stringResource(Res.string.block_kind))
            SegmentedPicker(BlockKind.entries, block.kind, { kindName(it) }, { k -> onChange { it.asKind(k) } })
            StepperRow(
                roundsName(block.kind),
                block.rounds,
                1..maxRounds(block.kind),
                onSet = { n -> onChange { it.copy(rounds = n) } },
            )
            if (block.kind == BlockKind.Ladder) {
                RungsEditor(block.rungs, onSet = { rungs -> onChange { it.copy(rungs = rungs) } })
            } else {
                StepperRow(
                    stringResource(Res.string.block_reps),
                    block.reps,
                    1..SetDraft.MAX_REPS,
                    onSet = { n -> onChange { it.copy(reps = n) } },
                )
            }
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
            )
        }
    }
}

/** A ladder's rungs, a stepper each, with a rung to add at the end or take off it. */
@Composable
private fun RungsEditor(rungs: List<Int>, onSet: (List<Int>) -> Unit) {
    GroupLabel(stringResource(Res.string.block_rungs))
    rungs.forEachIndexed { i, rung ->
        StepperRow(
            stringResource(Res.string.block_rung, i + 1),
            rung,
            1..SetDraft.MAX_REPS,
            onSet = { n -> onSet(rungs.mapIndexed { j, r -> if (j == i) n else r }) },
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        if (rungs.size < ProgramDraft.MAX_RUNGS) {
            SecondaryButton(
                stringResource(Res.string.block_add_rung),
                { onSet(rungs + (rungs.last() + 1)) },
                Modifier.weight(1f),
            )
        }
        if (rungs.size > 1) {
            SecondaryButton(
                stringResource(Res.string.block_remove_rung),
                { onSet(rungs.dropLast(1)) },
                Modifier.weight(1f),
            )
        }
    }
}

/** A number to step within [range]: its [label], the value, and − and +. */
@Composable
private fun StepperRow(
    label: String,
    value: Int,
    range: IntRange,
    onSet: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = IntervalTheme.palette
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        Text(label, style = IntervalTheme.type.label, color = palette.text, modifier = Modifier.weight(1f))
        Text(value.toString(), style = IntervalTheme.type.value, color = palette.text)
        RepeatButton(
            "−",
            stringResource(Res.string.editor_fewer_cd, label),
            enabled = value > range.first,
            onStep = { onSet((value - 1).coerceIn(range)) },
        )
        RepeatButton(
            "+",
            stringResource(Res.string.editor_more_cd, label),
            enabled = value < range.last,
            onStep = { onSet((value + 1).coerceIn(range)) },
        )
    }
}

@Composable
private fun kindName(kind: BlockKind): String = stringResource(
    when (kind) {
        BlockKind.Sets -> Res.string.block_kind_sets
        BlockKind.Emom -> Res.string.block_kind_emom
        BlockKind.Ladder -> Res.string.block_kind_ladder
    },
)

@Composable
private fun roundsName(kind: BlockKind): String = stringResource(
    when (kind) {
        BlockKind.Sets -> Res.string.block_rounds_sets
        BlockKind.Emom -> Res.string.block_rounds_emom
        BlockKind.Ladder -> Res.string.block_rounds_ladder
    },
)

/** An EMOM runs no longer than the timer allows; sets and ladders stop at [ProgramDraft.MAX_ROUNDS]. */
private fun maxRounds(kind: BlockKind): Int =
    if (kind == BlockKind.Emom) SetDraft.MAX_MINUTES else ProgramDraft.MAX_ROUNDS
