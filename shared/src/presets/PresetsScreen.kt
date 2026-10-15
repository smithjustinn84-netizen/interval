@file:OptIn(ExperimentalGridApi::class)

package dev.justinsmith.interval.presets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalGridApi
import androidx.compose.foundation.layout.Grid
import androidx.compose.foundation.layout.GridScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.builtInPresets
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.SavePresetDialog
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.Timeline
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
 * Every preset in two groups of two-up cards, each with its total and a small timeline of its
 * rounds. Tapping one makes it the next workout. The user's own presets carry a × to delete, and a
 * long press offers the same; both are also a screen-reader action.
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
    val palette = IntervalTheme.palette
    Box(modifier.fillMaxSize().background(palette.background), contentAlignment = Alignment.TopCenter) {
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
                        background = palette.accentTint,
                        tint = palette.accent,
                    )
                }
            }
            item {
                Column(Modifier.padding(horizontal = Spacing.space16)) {
                    GroupLabel("Built-in")
                    CardGrid {
                        state.builtIn.forEach { item ->
                            PresetCard(item, onClick = { onApply(item.preset) }, modifier = Modifier.gridItem())
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = Spacing.space16)) {
                    GroupLabel("Yours")
                    if (state.custom.isEmpty()) {
                        ListCard {
                            Text(
                                "Presets you save show up here.",
                                style = IntervalTheme.type.body,
                                color = palette.textDim,
                                modifier = Modifier.padding(horizontal = Spacing.space16, vertical = Spacing.space14),
                            )
                        }
                    }
                    CardGrid {
                        state.custom.forEach { item ->
                            // Keyed so a deleted card's menu state doesn't carry over to the card moving up.
                            key(item.preset) {
                                DeletablePresetCard(
                                    item,
                                    onClick = { onApply(item.preset) },
                                    onDelete = { onDelete(item.preset) },
                                    modifier = Modifier.gridItem(),
                                )
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

/** Two cards per row, as the design system lays presets out. */
@Composable
private fun CardGrid(content: @Composable GridScope.() -> Unit) {
    Grid(
        config = {
            column(1.fr)
            column(1.fr)
            gap(Spacing.space8)
        },
        content = content,
    )
}

@Composable
private fun DeletablePresetCard(
    item: PresetRowItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = IntervalTheme.palette
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        PresetCard(
            item,
            onClick = onClick,
            onLongClick = { menu = true },
            deletable = true,
            modifier = Modifier.semantics {
                customActions = listOf(
                    CustomAccessibilityAction("Delete preset ${item.preset.name}") {
                        onDelete()
                        true
                    },
                )
            },
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(Spacing.space4)
                .size(Sizes.delete)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onDelete)
                .semantics { contentDescription = "Delete preset ${item.preset.name}" },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "×",
                style = IntervalTheme.type.deleteGlyph,
                color = palette.textDim,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
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

/** A preset's name, total, shape and a small timeline; the selected one takes the accent. */
@Composable
private fun PresetCard(
    item: PresetRowItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    deletable: Boolean = false,
) {
    val palette = IntervalTheme.palette
    val shape = Corners.radius14
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (item.selected) palette.accentTint else palette.surface)
            .border(Sizes.border, if (item.selected) palette.accent else palette.line, shape)
            .combinedClickable(role = Role.Button, onLongClick = onLongClick, onClick = onClick)
            .semantics { selected = item.selected }
            .padding(start = Spacing.space14, end = Spacing.space14, top = Spacing.space14, bottom = Spacing.space12),
        verticalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.preset.name,
                style = IntervalTheme.type.cardTitle,
                color = palette.text,
                maxLines = 1,
                modifier = Modifier.weight(1f).padding(end = if (deletable) Sizes.delete - Spacing.space8 else 0.dp),
            )
            if (item.selected) {
                Icon(
                    IntervalIcons.Check,
                    contentDescription = "Selected",
                    tint = palette.accent,
                    modifier = Modifier.size(Sizes.icon),
                )
            }
        }
        Text(
            item.total,
            style = IntervalTheme.type.presetCardTotal,
            color = if (item.selected) palette.accent else palette.text,
        )
        Text(item.description, style = IntervalTheme.type.hint, color = palette.textDim, maxLines = 1)
        Timeline(item.preset.config, height = Sizes.timelineMini, gap = Spacing.space1, showPrep = false)
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
