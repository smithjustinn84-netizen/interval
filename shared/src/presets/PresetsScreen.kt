package dev.justinsmith.interval.presets

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.common_cancel
import dev.justinsmith.interval.resources.common_save
import dev.justinsmith.interval.resources.presets_built_in
import dev.justinsmith.interval.resources.presets_current_unsaved
import dev.justinsmith.interval.resources.presets_delete
import dev.justinsmith.interval.resources.presets_delete_text
import dev.justinsmith.interval.resources.presets_delete_title
import dev.justinsmith.interval.resources.presets_empty
import dev.justinsmith.interval.resources.presets_empty_title
import dev.justinsmith.interval.resources.presets_new_cd
import dev.justinsmith.interval.resources.presets_on_timer
import dev.justinsmith.interval.resources.presets_title
import dev.justinsmith.interval.resources.presets_yours
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.builtInPresets
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.ConfirmDialog
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.EmptyState
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.SetupLanes
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.Timeline
import dev.justinsmith.interval.ui.TitleBar
import dev.justinsmith.interval.ui.configDescription
import dev.justinsmith.interval.ui.pressScale
import dev.justinsmith.interval.ui.selectableCard
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.currentKoinScope

/**
 * Resolves the tab's ViewModel and binds it to [PresetsScreen]. A preset goes on the Timer either way;
 * Start workout then opens the workout, Put on Timer shows the Timer tab.
 */
@Composable
fun PresetsRoute(onShowTimer: () -> Unit, onStartWorkout: () -> Unit, modifier: Modifier = Modifier) {
    val scope = currentKoinScope()
    val viewModel = viewModel { PresetsViewModel(scope.get()) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    PresetsScreen(
        state = state,
        onStart = {
            viewModel.apply(it)
            onStartWorkout()
        },
        onPutOnTimer = {
            viewModel.apply(it)
            onShowTimer()
        },
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        modifier = modifier,
    )
}

/**
 * Every preset in two groups of full-width cards, the user's own first, each with its total and a
 * timeline drawn to scale so lengths compare at a glance. Tapping one opens its [PresetSheet]: start
 * it, put it on the Timer, and for the user's own, edit or delete it (after asking). Settings that
 * match no preset sit on top with Save, which opens the [PresetEditor] on them, as + in the title
 * bar does.
 */
@Composable
fun PresetsScreen(
    state: PresetsUiState,
    onStart: (Preset) -> Unit,
    onPutOnTimer: (Preset) -> Unit,
    onSave: (Preset) -> Unit,
    onDelete: (Preset) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable(stateSaver = PresetSaver) { mutableStateOf<Preset?>(null) }
    var draft by rememberSaveable(stateSaver = PresetDraftSaver) { mutableStateOf<PresetDraft?>(null) }
    var deleting by rememberSaveable(stateSaver = PresetSaver) { mutableStateOf<Preset?>(null) }
    PresetsList(
        state = state,
        onOpen = { open = it },
        onNew = { draft = PresetDraft(EditorMode.New, name = "", config = state.current) },
        modifier = modifier,
    )
    // The sheet follows its preset through the state, so one edited or deleted elsewhere closes it.
    val openRow = open?.let { preset -> state.rows.firstOrNull { it.preset == preset } }
    if (openRow != null) {
        PresetSheet(
            row = openRow,
            prepSeconds = state.current.prepSeconds,
            onStart = {
                open = null
                onStart(openRow.preset)
            },
            onPutOnTimer = {
                open = null
                onPutOnTimer(openRow.preset)
            },
            onEdit = {
                open = null
                draft = it
            },
            onDelete = { deleting = openRow.preset },
            onDismiss = { open = null },
        )
    }
    draft?.let { current ->
        PresetEditor(
            draft = current,
            prepSeconds = state.current.prepSeconds,
            onChangeShape = { transform -> draft = draft?.let { it.copy(config = transform(it.config)) } },
            onSave = onSave,
            onDismiss = { draft = null },
        )
    }
    deleting?.let { preset ->
        ConfirmDialog(
            title = stringResource(Res.string.presets_delete_title, preset.name),
            text = stringResource(Res.string.presets_delete_text),
            confirm = stringResource(Res.string.presets_delete),
            dismiss = stringResource(Res.string.common_cancel),
            onConfirm = {
                onDelete(preset)
                deleting = null
                open = null
            },
            onDismiss = { deleting = null },
        )
    }
}

/** The title bar with + ([onNew]), the unsaved settings when there are any, then the two groups of cards. */
@Composable
private fun PresetsList(
    state: PresetsUiState,
    onOpen: (Preset) -> Unit,
    onNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                TitleBar(stringResource(Res.string.presets_title)) {
                    IconAction(IntervalIcons.Add, stringResource(Res.string.presets_new_cd), onClick = onNew)
                }
            }
            // Keyed, so the card fades and the groups slide as the current shape stops or starts matching a preset.
            if (state.canSaveCurrent) {
                item(key = "current", span = StaggeredGridItemSpan.FullLine) {
                    CurrentCard(
                        config = state.current,
                        share = state.currentShare,
                        onSave = onNew,
                        modifier = Modifier
                            .animateItem()
                            .padding(horizontal = Spacing.space16)
                            .padding(bottom = Spacing.space4),
                    )
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
                        state.custom.forEach { item -> PresetCard(item, onClick = { onOpen(item.preset) }) }
                    }
                }
            }
            item(key = "builtIn") {
                Column(Modifier.animateItem().padding(horizontal = Spacing.space16)) {
                    GroupLabel(stringResource(Res.string.presets_built_in))
                    CardColumn {
                        state.builtIn.forEach { item -> PresetCard(item, onClick = { onOpen(item.preset) }) }
                    }
                }
            }
        }
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

/** The accent pill that takes the current settings to the editor. */
@Composable
private fun SaveButton(onClick: () -> Unit) {
    val palette = IntervalTheme.palette
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .heightIn(min = Sizes.pillHeight)
            .widthIn(min = Sizes.pillWidth)
            .pressScale(interaction)
            .clip(Corners.radius12)
            .background(palette.accent)
            .clickable(interaction, LocalIndication.current, role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.space20),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(Res.string.common_save), style = IntervalTheme.type.label, color = palette.onAccent)
    }
}

/**
 * A preset's name and total on one line, its shape under them, and its timeline at [PresetRowItem.share]
 * of the card's width; the one on the Timer says so over its name and takes the accent.
 */
@Composable
private fun PresetCard(item: PresetRowItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .selectableCard(item.selected)
            .clickable(interaction, LocalIndication.current, role = Role.Button, onClick = onClick)
            .padding(start = Spacing.space16, end = Spacing.space16, top = Spacing.space12, bottom = Spacing.space16),
        verticalArrangement = Arrangement.spacedBy(Spacing.space4),
    ) {
        if (item.selected) {
            Text(
                stringResource(Res.string.presets_on_timer),
                style = IntervalTheme.type.caption,
                color = palette.accent,
            )
        }
        Row(
            Modifier.fillMaxWidth(),
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

// Which preset's sheet is open, which is being asked about, and what the editor holds survive a
// configuration change and process death, like a dialog's open state. Each is saved by its values.

private val PresetSaver: Saver<Preset?, Any> = mapSaver(
    save = { preset ->
        if (preset ==
            null
        ) {
            emptyMap()
        } else {
            mapOf(NAME to preset.name, ID to preset.id) + preset.config.saved()
        }
    },
    restore = { Preset(it[NAME] as String, it.restoredConfig(), it[ID] as Long?) },
)

private val PresetDraftSaver: Saver<PresetDraft?, Any> = mapSaver(
    save = { draft ->
        if (draft == null) {
            emptyMap()
        } else {
            mapOf(MODE to draft.mode.name, NAME to draft.name, ID to draft.id) + draft.config.saved()
        }
    },
    restore = {
        PresetDraft(EditorMode.valueOf(it[MODE] as String), it[NAME] as String, it.restoredConfig(), it[ID] as Long?)
    },
)

private fun IntervalConfig.saved(): Map<String, Any?> =
    mapOf(PREP to prepSeconds, WORK to workSeconds, REST to restSeconds, ROUNDS to rounds)

private fun Map<String, Any?>.restoredConfig(): IntervalConfig =
    IntervalConfig(this[PREP] as Int, this[WORK] as Int, this[REST] as Int, this[ROUNDS] as Int)

private const val MODE = "mode"
private const val NAME = "name"
private const val ID = "id"
private const val PREP = "prep"
private const val WORK = "work"
private const val REST = "rest"
private const val ROUNDS = "rounds"

@PreviewFormFactors
@Composable
private fun PresetsScreenPreview() {
    val current = builtInPresets.first().config.copy(workSeconds = 25)
    val longest = builtInPresets.maxOf { it.config.totalSeconds }.toFloat()
    fun row(preset: Preset, selected: Boolean = false) =
        PresetRowItem(preset, formatClock(preset.config.totalSeconds), selected, preset.config.totalSeconds / longest)
    IntervalTheme {
        PresetsScreen(
            state = PresetsUiState(
                custom = listOf(
                    row(Preset("Leg day 40/20", current.copy(workSeconds = 40, restSeconds = 20, rounds = 6), id = 1)),
                ),
                builtIn = builtInPresets.map { row(it) },
                canSaveCurrent = true,
                current = current,
                currentShare = current.totalSeconds / longest,
            ),
            onStart = {},
            onPutOnTimer = {},
            onSave = {},
            onDelete = {},
        )
    }
}
