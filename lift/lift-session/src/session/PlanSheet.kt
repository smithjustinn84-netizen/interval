package dev.justinsmith.lift.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalSheet
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.SecondaryButton
import dev.justinsmith.interval.ui.SheetColumns
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.sheetIsSideways
import dev.justinsmith.lift.domain.Block
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.domain.ExerciseFilters
import dev.justinsmith.lift.domain.PlanRow
import dev.justinsmith.lift.domain.SessionPlan
import dev.justinsmith.lift.library.ExercisePicker
import dev.justinsmith.lift.programs.BlockChoices
import dev.justinsmith.lift.programs.BlockCounts
import dev.justinsmith.lift.programs.BlockLoad
import dev.justinsmith.lift.session.resources.Res
import dev.justinsmith.lift.session.resources.block_done
import dev.justinsmith.lift.session.resources.plan_block_title
import dev.justinsmith.lift.session.resources.plan_changed
import dev.justinsmith.lift.session.resources.plan_done_of
import dev.justinsmith.lift.session.resources.plan_hint
import dev.justinsmith.lift.session.resources.plan_skip_block
import dev.justinsmith.lift.session.resources.plan_skip_rest
import dev.justinsmith.lift.session.resources.plan_skipped
import dev.justinsmith.lift.session.resources.plan_title
import dev.justinsmith.lift.session.resources.plan_unskip
import dev.justinsmith.lift.ui.blockLine
import dev.justinsmith.lift.ui.liftName
import org.jetbrains.compose.resources.stringResource

/**
 * Today's plan while [planning], and block [changing] of it (-1 for none) open to change: opening a block
 * from the plan swaps one sheet for the other.
 */
@Composable
internal fun PlanSheets(
    state: SessionUiState,
    actions: SessionActions,
    planning: Boolean,
    changing: Int,
    onPlanning: (Boolean) -> Unit,
    onChanging: (Int) -> Unit,
) {
    if (planning) {
        PlanSheet(
            state,
            onOpen = {
                onPlanning(false)
                onChanging(it)
            },
            onDismiss = { onPlanning(false) },
        )
    }
    val row = state.plan.getOrNull(changing) ?: return
    PlanBlockSheet(
        row,
        canSwap = SessionPlan(state.plan.map { it.entry }).canSwap(changing, row.done),
        state,
        actions.onFilters,
        PlanBlockActions(
            onChange = { actions.onChangeBlock(changing, it) },
            onSwap = { actions.onSwapBlock(changing, it) },
            onSkip = { actions.onSkipBlock(changing) },
            onUnskip = { actions.onUnskipBlock(changing) },
        ),
        onDismiss = { onChanging(-1) },
    )
}

/** The choices a block of today's plan can be made of: the sheet's bells, bands and library. */
internal val SessionUiState.blockChoices: BlockChoices get() = BlockChoices(bells, bands, library, bar)

/**
 * Today's plan: each of the day's blocks with how many of its sets are done, changed or skipped ones marked.
 * A block with sets still to do, or skipped, opens on a tap to change for today.
 */
@Composable
internal fun PlanSheet(state: SessionUiState, onOpen: (Int) -> Unit, onDismiss: () -> Unit) {
    val palette = IntervalTheme.palette
    IntervalSheet(stringResource(Res.string.plan_title), onDismiss = onDismiss) {
        Column(
            Modifier.padding(vertical = Spacing.space8),
            verticalArrangement = Arrangement.spacedBy(Spacing.space8),
        ) {
            ListCard {
                state.plan.forEachIndexed { i, row ->
                    if (i > 0) RowDivider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Sizes.listRow)
                            .then(if (row.open) Modifier.clickable(role = Role.Button) { onOpen(i) } else Modifier)
                            .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                liftName(row.block.exercise, row.block.double),
                                style = IntervalTheme.type.label,
                                color = palette.text,
                            )
                            Text(
                                blockLine(row.block, state.bands),
                                style = IntervalTheme.type.meta,
                                color = palette.textDim,
                            )
                            Text(
                                stringResource(Res.string.plan_done_of, row.done, row.total),
                                style = IntervalTheme.type.meta,
                                color = palette.textDim,
                            )
                        }
                        PlanTag(row)
                        if (row.open) {
                            Icon(
                                IntervalIcons.Chevron,
                                null,
                                tint = palette.textDim,
                                modifier = Modifier.size(Sizes.icon),
                            )
                        }
                    }
                }
            }
            Text(stringResource(Res.string.plan_hint), style = IntervalTheme.type.meta, color = palette.textDim)
        }
    }
}

/** Skipped or Changed, beside a block of today's plan that is. */
@Composable
private fun PlanTag(row: PlanRow) {
    val palette = IntervalTheme.palette
    when {
        row.entry.skipped -> Text(
            stringResource(Res.string.plan_skipped),
            style = IntervalTheme.type.caption,
            color = palette.textDim,
        )

        row.changed -> Text(
            stringResource(Res.string.plan_changed),
            style = IntervalTheme.type.caption,
            color = palette.accent,
        )
    }
}

/** What a block of today's plan can do: change, take another exercise, skip its rest, or come back. */
internal data class PlanBlockActions(
    val onChange: ((Block) -> Block) -> Unit,
    val onSwap: (Exercise) -> Unit,
    val onSkip: () -> Unit,
    val onUnskip: () -> Unit,
)

/**
 * Block [row] of today's plan, to change for today: its load, rounds (no fewer than are done) and reps; Change
 * swaps another exercise in for the sets still to do; Skip drops them. A skipped block can be brought back.
 */
@Composable
internal fun PlanBlockSheet(
    row: PlanRow,
    canSwap: Boolean,
    state: SessionUiState,
    onFilters: ((ExerciseFilters) -> ExerciseFilters) -> Unit,
    actions: PlanBlockActions,
    onDismiss: () -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val sideways = sheetIsSideways()
    IntervalSheet(stringResource(Res.string.plan_block_title), onDismiss = onDismiss) { hide ->
        if (picking) {
            ExercisePicker(
                state.library,
                onFilters,
                Modifier.padding(vertical = Spacing.space8),
                selected = row.block.exercise,
                onPick = { e ->
                    actions.onSwap(e)
                    picking = false
                },
            )
            return@IntervalSheet
        }
        if (row.entry.skipped) {
            Column(
                Modifier.padding(vertical = Spacing.space8),
                verticalArrangement = Arrangement.spacedBy(Spacing.space8),
            ) {
                Text(
                    liftName(row.block.exercise, row.block.double),
                    style = IntervalTheme.type.section,
                    color = IntervalTheme.palette.text,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(Res.string.plan_done_of, row.done, row.entry.copy(kept = null).plannedSets().size),
                    style = IntervalTheme.type.meta,
                    color = IntervalTheme.palette.textDim,
                )
                AccentButton(
                    stringResource(Res.string.plan_unskip),
                    {
                        actions.onUnskip()
                        hide()
                    },
                    Modifier.fillMaxWidth().padding(top = Spacing.space16),
                )
            }
            return@IntervalSheet
        }
        // What the block is, then how much of it and the way out, side by side on a phone on its side.
        SheetColumns(
            sideways,
            first = {
                BlockLoad(row.block, state.blockChoices, actions.onChange, if (canSwap) ({ picking = true }) else null)
            },
            second = {
                BlockCounts(
                    row.block,
                    actions.onChange,
                    row.entry.roundsDone(row.done),
                    lockKind = row.done > 0,
                    rangeEditable = false,
                )
                AccentButton(
                    stringResource(Res.string.block_done),
                    onClick = hide,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.space16),
                )
                SecondaryButton(
                    stringResource(if (row.done > 0) Res.string.plan_skip_rest else Res.string.plan_skip_block),
                    {
                        actions.onSkip()
                        hide()
                    },
                    Modifier.fillMaxWidth(),
                )
            },
            modifier = Modifier.padding(vertical = Spacing.space8),
        )
    }
}
