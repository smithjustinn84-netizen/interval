package dev.justinsmith.interval.presets

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.preset_default_name
import dev.justinsmith.interval.resources.presets_built_in
import dev.justinsmith.interval.resources.presets_current_unsaved
import dev.justinsmith.interval.resources.presets_delete
import dev.justinsmith.interval.resources.presets_delete_named_cd
import dev.justinsmith.interval.resources.presets_empty
import dev.justinsmith.interval.resources.presets_empty_title
import dev.justinsmith.interval.resources.presets_more_named_cd
import dev.justinsmith.interval.resources.presets_save_current
import dev.justinsmith.interval.resources.presets_selected_cd
import dev.justinsmith.interval.resources.presets_title
import dev.justinsmith.interval.resources.presets_yours
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.builtInPresets
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.EmptyState
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalMenu
import dev.justinsmith.interval.ui.IntervalMenuItem
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.SavePresetDialog
import dev.justinsmith.interval.ui.SetupLanes
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.Timeline
import dev.justinsmith.interval.ui.TitleBar
import dev.justinsmith.interval.ui.configDescription
import dev.justinsmith.interval.ui.configSummary
import dev.justinsmith.interval.ui.pressScale
import dev.justinsmith.interval.ui.selectableCard
import org.jetbrains.compose.resources.stringResource
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
 * Every preset in two groups of full-width cards, each with its total and a timeline drawn to scale,
 * so lengths compare at a glance. Tapping one makes it the next workout. Settings that match no preset
 * sit on top with a button to save them. The user's own presets open a menu to delete them from ⋯ or
 * a long press; deleting is also a screen-reader action.
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
        SetupLanes(Modifier.widthIn(max = Sizes.listMax).fillMaxSize())
        // One column on a phone; wider windows put the two groups side by side.
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(Sizes.listColumn),
            modifier = Modifier.widthIn(max = Sizes.listMax).fillMaxSize(),
            contentPadding = PaddingValues(bottom = Spacing.space24),
        ) {
            item(key = "title", span = StaggeredGridItemSpan.FullLine) {
                TitleBar(stringResource(Res.string.presets_title))
            }
            // Keyed, so the card fades and the groups slide as the current shape stops or starts matching a preset.
            if (state.canSaveCurrent) {
                item(key = "current", span = StaggeredGridItemSpan.FullLine) {
                    CurrentCard(
                        config = state.current,
                        share = state.currentShare,
                        onSave = { saving = true },
                        modifier = Modifier
                            .animateItem()
                            .padding(horizontal = Spacing.space16)
                            .padding(bottom = Spacing.space4),
                    )
                }
            }
            item(key = "builtIn") {
                Column(Modifier.animateItem().padding(horizontal = Spacing.space16)) {
                    GroupLabel(stringResource(Res.string.presets_built_in))
                    CardColumn {
                        state.builtIn.forEach { item ->
                            PresetCard(item, onClick = { onApply(item.preset) })
                        }
                    }
                }
            }
            item(key = "custom") {
                // The group closes up smoothly over a deleted card.
                Column(Modifier.animateItem().padding(horizontal = Spacing.space16).animateContentSize()) {
                    GroupLabel(stringResource(Res.string.presets_yours))
                    if (state.custom.isEmpty()) {
                        ListCard {
                            EmptyState(
                                IntervalIcons.Presets,
                                stringResource(Res.string.presets_empty_title),
                                stringResource(Res.string.presets_empty),
                            )
                        }
                    }
                    CardColumn {
                        state.custom.forEach { item ->
                            // Keyed so a deleted card's menu state doesn't carry over to the card moving up.
                            key(item.preset) {
                                DeletablePresetCard(
                                    item,
                                    onClick = { onApply(item.preset) },
                                    onDelete = { onDelete(item.preset) },
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
            summary = configSummary(state.current),
            defaultName = stringResource(Res.string.preset_default_name, state.nextCustomNumber),
            onSave = {
                onSaveCurrent(it)
                saving = false
            },
            onDismiss = { saving = false },
        )
    }
}

@Composable
private fun CardColumn(content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.space8)) { content() }
}

/** The settings on the Timer tab when they match no preset: their total, shape and a button to save them. */
@Composable
private fun CurrentCard(config: IntervalConfig, share: Float, onSave: () -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Column(
        modifier
            .fillMaxWidth()
            .selectableCard(selected = false)
            .padding(start = Spacing.space16, end = Spacing.space16, top = Spacing.space12, bottom = Spacing.space16),
        verticalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        Text(
            stringResource(Res.string.presets_current_unsaved),
            style = IntervalTheme.type.caption,
            color = palette.textDim,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
        ) {
            Column(Modifier.weight(1f)) {
                Text(formatClock(config.totalSeconds), style = IntervalTheme.type.stat, color = palette.text)
                Text(configDescription(config), style = IntervalTheme.type.meta, color = palette.textDim, maxLines = 1)
            }
            SaveButton(onSave)
        }
        Timeline(
            config,
            modifier = Modifier.fillMaxWidth(share),
            height = Sizes.timelineMini,
            gap = Sizes.timelineMiniGap,
            showPrep = false,
        )
    }
}

@Composable
private fun SaveButton(onClick: () -> Unit) {
    val palette = IntervalTheme.palette
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .heightIn(min = Sizes.pillHeight)
            .pressScale(interaction)
            .clip(Corners.radius12)
            .background(palette.accent)
            .clickable(interaction, LocalIndication.current, role = Role.Button, onClick = onClick)
            .padding(start = Spacing.space12, end = Spacing.space16),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        Icon(
            IntervalIcons.Add,
            contentDescription = null,
            tint = palette.onAccent,
            modifier = Modifier.size(Sizes.icon),
        )
        Text(
            stringResource(Res.string.presets_save_current),
            style = IntervalTheme.type.label,
            color = palette.onAccent,
        )
    }
}

@Composable
private fun DeletablePresetCard(
    item: PresetRowItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    val deleteLabel = stringResource(Res.string.presets_delete_named_cd, item.preset.name)
    Box(modifier) {
        PresetCard(
            item,
            onClick = onClick,
            onLongClick = { menu = true },
            endInset = Sizes.iconButton,
            modifier = Modifier.semantics {
                customActions = listOf(
                    CustomAccessibilityAction(deleteLabel) {
                        onDelete()
                        true
                    },
                )
            },
        )
        // The menu opens from ⋯, and a long press on the card opens the same one.
        IntervalMenu(
            expanded = menu,
            onExpandedChange = { menu = it },
            modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.space4),
            items = { IntervalMenuItem(stringResource(Res.string.presets_delete), onClick = onDelete) },
        ) {
            IconAction(
                IntervalIcons.More,
                stringResource(Res.string.presets_more_named_cd, item.preset.name),
                onClick = { menu = true },
                tint = IntervalTheme.palette.textDim,
            )
        }
    }
}

/**
 * A preset's name and total on one line, its shape under them, and its timeline at [PresetRowItem.share]
 * of the card's width; the selected one takes the accent. [endInset] keeps the top line clear of ⋯.
 */
@Composable
private fun PresetCard(
    item: PresetRowItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    endInset: Dp = 0.dp,
) {
    val palette = IntervalTheme.palette
    Column(
        modifier
            .fillMaxWidth()
            .selectableCard(item.selected)
            .combinedClickable(role = Role.Button, onLongClick = onLongClick, onClick = onClick)
            .padding(start = Spacing.space16, end = Spacing.space16, top = Spacing.space12, bottom = Spacing.space16),
        verticalArrangement = Arrangement.spacedBy(Spacing.space4),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(end = endInset),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
        ) {
            Text(
                item.preset.name,
                style = IntervalTheme.type.label,
                color = palette.text,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (item.selected) {
                Icon(
                    IntervalIcons.Check,
                    contentDescription = stringResource(Res.string.presets_selected_cd),
                    tint = palette.accent,
                    modifier = Modifier.size(Sizes.icon),
                )
            }
            Text(
                item.total,
                style = IntervalTheme.type.value,
                color = if (item.selected) palette.accent else palette.text,
            )
        }
        Text(
            configDescription(item.preset.config),
            style = IntervalTheme.type.meta,
            color = palette.textDim,
            maxLines = 1,
        )
        Timeline(
            item.preset.config,
            modifier = Modifier.padding(top = Spacing.space4).fillMaxWidth(item.share),
            height = Sizes.timelineMini,
            gap = Sizes.timelineMiniGap,
            showPrep = false,
        )
    }
}

@PreviewFormFactors
@Composable
private fun PresetsScreenPreview() {
    val current = builtInPresets.first().config.copy(workSeconds = 25)
    val longest = builtInPresets.maxOf { it.config.totalSeconds }.toFloat()
    fun row(preset: Preset) =
        PresetRowItem(preset, formatClock(preset.config.totalSeconds), false, preset.config.totalSeconds / longest)
    IntervalTheme {
        PresetsScreen(
            state = PresetsUiState(
                builtIn = builtInPresets.map(::row),
                custom = listOf(
                    row(Preset("Leg day 40/20", current.copy(workSeconds = 40, restSeconds = 20, rounds = 6))),
                ),
                canSaveCurrent = true,
                current = current,
                currentShare = current.totalSeconds / longest,
            ),
            onApply = {},
            onSaveCurrent = {},
            onDelete = {},
        )
    }
}
