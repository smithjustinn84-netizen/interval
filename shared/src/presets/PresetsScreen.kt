package dev.justinsmith.interval.presets

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.builtInPresets
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.PhaseColors
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.SavePresetDialog
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.TitleBar
import org.koin.compose.currentKoinScope

/** Resolves the tab's ViewModel and binds it to [PresetsScreen]. A picked preset shows on the Timer tab. */
@Composable
fun PresetsRoute(onShowTimer: () -> Unit, modifier: Modifier = Modifier) {
    val scope = currentKoinScope()
    val viewModel = viewModel { PresetsViewModel(scope.get()) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    PresetsScreen(
        state = state,
        onApply = {
            viewModel.apply(it)
            onShowTimer()
        },
        onSaveCurrent = viewModel::saveCurrent,
        onDelete = viewModel::delete,
        modifier = modifier,
    )
}

/**
 * Every preset in two groups. Tapping one makes it the next workout. The user's own presets delete
 * with a swipe to the left (iOS) or a long press (Android); both are also a screen-reader action.
 */
@Composable
fun PresetsScreen(
    state: PresetsUiState,
    onApply: (Preset) -> Unit,
    onSaveCurrent: (String) -> Unit,
    onDelete: (Preset) -> Unit,
    modifier: Modifier = Modifier,
) {
    var saving by rememberSaveable { mutableStateOf(false) }
    Box(modifier.fillMaxSize().background(IntervalTheme.palette.background), contentAlignment = Alignment.TopCenter) {
        // One column on a phone; wider windows put the two groups side by side.
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(Sizes.listColumn),
            modifier = Modifier.widthIn(max = Sizes.listMax).fillMaxSize(),
            contentPadding = PaddingValues(bottom = Spacing.space24),
        ) {
            item(span = StaggeredGridItemSpan.FullLine) {
                TitleBar("Presets") {
                    IconAction(
                        IntervalIcons.Add,
                        "Save current settings as a preset",
                        onClick = { saving = true },
                        enabled = state.canSaveCurrent,
                    )
                }
            }
            item {
                Column(Modifier.padding(horizontal = Spacing.space16)) {
                    GroupLabel("Built-in")
                    ListCard {
                        state.builtIn.forEachIndexed { index, item ->
                            if (index > 0) RowDivider()
                            PresetRow(item, onClick = { onApply(item.preset) })
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = Spacing.space16)) {
                    GroupLabel("Yours")
                    ListCard {
                        if (state.custom.isEmpty()) {
                            Text(
                                "Presets you save show up here.",
                                style = IntervalTheme.type.body,
                                color = IntervalTheme.palette.textDim,
                                modifier = Modifier.padding(horizontal = Spacing.space16, vertical = Spacing.space14),
                            )
                        }
                        state.custom.forEachIndexed { index, item ->
                            if (index > 0) RowDivider()
                            // Keyed so a deleted row's swipe state doesn't carry over to the row moving up.
                            key(item.preset) {
                                DeletablePresetRow(item, onClick = {
                                    onApply(item.preset)
                                }, onDelete = { onDelete(item.preset) })
                            }
                        }
                    }
                }
            }
        }
    }
    if (saving) {
        SavePresetDialog(
            summary = state.currentSummary,
            onSave = {
                onSaveCurrent(it)
                saving = false
            },
            onDismiss = { saving = false },
        )
    }
}

@Composable
private fun DeletablePresetRow(
    item: PresetRowItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val swipe = rememberSwipeToDismissBoxState()
    var menu by remember { mutableStateOf(false) }
    SwipeToDismissBox(
        state = swipe,
        enableDismissFromStartToEnd = false,
        onDismiss = { if (it == SwipeToDismissBoxValue.EndToStart) onDelete() },
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(PhaseColors.work).padding(horizontal = Spacing.space20),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text("Delete", style = IntervalTheme.type.control, color = PhaseColors.onPhase)
            }
        },
        modifier = modifier,
    ) {
        Box {
            PresetRow(
                item,
                onClick = onClick,
                onLongClick = { menu = true },
                modifier = Modifier
                    .background(IntervalTheme.palette.surface)
                    .semantics {
                        customActions = listOf(
                            CustomAccessibilityAction("Delete preset ${item.preset.name}") {
                                onDelete()
                                true
                            },
                        )
                    },
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Delete", style = IntervalTheme.type.body) },
                    onClick = {
                        menu = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun PresetRow(
    item: PresetRowItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val palette = IntervalTheme.palette
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.listRow)
            .combinedClickable(role = Role.Button, onLongClick = onLongClick, onClick = onClick)
            .semantics { selected = item.selected }
            .padding(start = Spacing.space16, end = Spacing.space12, top = Spacing.space8, bottom = Spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.preset.name, style = IntervalTheme.type.cardTitle, color = palette.text, maxLines = 1)
            Text(item.description, style = IntervalTheme.type.presetDescription, color = palette.textDim)
        }
        Text(item.total, style = IntervalTheme.type.sectionTitle, color = palette.text)
        // The check's slot is kept on every row so the totals line up.
        Box(Modifier.size(Sizes.icon)) {
            if (item.selected) {
                Icon(IntervalIcons.Check, contentDescription = "Selected", tint = palette.accent)
            }
        }
    }
}

@PreviewFormFactors
@Composable
private fun PresetsScreenPreview() {
    fun row(preset: Preset, selected: Boolean = false) =
        PresetRowItem(preset, preset.config.description, formatClock(preset.config.totalSeconds), selected)
    IntervalTheme {
        PresetsScreen(
            state = PresetsUiState(
                builtIn = builtInPresets.mapIndexed { i, preset -> row(preset, selected = i == 0) },
                custom = listOf(row(Preset("Morning 25/10", builtInPresets.first().config.copy(workSeconds = 25)))),
                canSaveCurrent = false,
                currentSummary = "",
            ),
            onApply = {},
            onSaveCurrent = {},
            onDelete = {},
        )
    }
}
