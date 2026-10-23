package dev.justinsmith.interval.presets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
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
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.common_calories_estimate
import dev.justinsmith.interval.resources.common_cancel
import dev.justinsmith.interval.resources.common_joined
import dev.justinsmith.interval.resources.common_save
import dev.justinsmith.interval.resources.notice_preset_deleted
import dev.justinsmith.interval.resources.notice_undo
import dev.justinsmith.interval.resources.presets_built_in
import dev.justinsmith.interval.resources.presets_calories_estimate_as
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
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.builtInPresets
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.BackAction
import dev.justinsmith.interval.ui.ConfirmDialog
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.EmptyState
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.LocalNotices
import dev.justinsmith.interval.ui.MiniTimeline
import dev.justinsmith.interval.ui.NoticeAction
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.TabGrid
import dev.justinsmith.interval.ui.enterRising
import dev.justinsmith.interval.ui.labelRes
import dev.justinsmith.interval.ui.pressScale
import dev.justinsmith.interval.ui.rememberArrival
import dev.justinsmith.interval.ui.selectableCard
import dev.justinsmith.interval.ui.surfaceButton
import dev.justinsmith.interval.ui.workoutDescription
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.currentKoinScope

/**
 * Resolves the tab's ViewModel and binds it to [PresetsScreen]. A preset goes on the Timer either way;
 * Start workout then opens the workout, Put on Timer shows the Timer tab.
 */
@Composable
fun PresetsRoute(
    onBack: () -> Unit,
    onShowTimer: () -> Unit,
    onStartWorkout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = currentKoinScope()
    val viewModel = viewModel { PresetsViewModel(scope.get()) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    PresetsScreen(
        state = state,
        onStart = {
            viewModel.putOnTimer(it)
            onStartWorkout()
        },
        onPutOnTimer = {
            viewModel.putOnTimer(it)
            onShowTimer()
        },
        onSave = viewModel::save,
        onSaveCurrent = viewModel::saveCurrent,
        onDelete = viewModel::delete,
        onRestore = viewModel::restore,
        modifier = modifier,
        onBack = onBack,
    )
}

/**
 * Every preset in two groups of full-width cards, the user's own first, each with its total and a
 * timeline drawn to scale so lengths compare at a glance. Tapping one opens its [PresetSheet]: start
 * it, put it on the Timer, and for the user's own, edit or delete it (after asking). Settings that
 * match no preset sit on top with Save, which opens the [PresetEditor] on them and keeps whatever it
 * saves on the Timer ([onSaveCurrent]); + in the title bar starts a new preset from them instead,
 * leaving the Timer alone ([onSave]). While those settings are unsaved, a preset's sheet says that
 * starting or loading it replaces them. A deleted preset is offered back for a moment ([onRestore]).
 */
@Composable
fun PresetsScreen(
    state: PresetsUiState,
    onStart: (Preset) -> Unit,
    onPutOnTimer: (Preset) -> Unit,
    onSave: (Preset) -> Unit,
    onSaveCurrent: (Preset) -> Unit,
    onDelete: (Preset) -> Unit,
    modifier: Modifier = Modifier,
    onRestore: (Preset) -> Unit = {},
    onBack: (() -> Unit)? = null,
) {
    var open by rememberSaveable(stateSaver = PresetSaver) { mutableStateOf<Preset?>(null) }
    val notices = LocalNotices.current
    val undo = stringResource(Res.string.notice_undo)
    var draft by rememberPresetDraft()
    var deleting by rememberSaveable(stateSaver = PresetSaver) { mutableStateOf<Preset?>(null) }
    PresetsList(
        state = state,
        onOpen = { open = it },
        onNew = { draft = PresetDraft(EditorMode.New, name = "", config = state.current) },
        onSaveCurrent = { draft = PresetDraft.saveCurrent(state.current) },
        modifier = modifier,
        onBack = onBack,
    )
    // The sheet follows its preset through the state, so one edited or deleted elsewhere closes it.
    val openRow = open?.let { preset -> state.rows.firstOrNull { it.preset == preset } }
    if (openRow != null) {
        PresetSheet(
            row = openRow,
            prepSeconds = state.current.prepSeconds,
            replacesUnsaved = state.canSaveCurrent,
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
    PresetEditorHost(
        draft = draft,
        onDraftChange = { draft = it },
        prepSeconds = state.current.prepSeconds,
        presets = state.rows.map { it.preset },
        onSave = { opened, preset ->
            if (opened.mode == EditorMode.SaveCurrent) onSaveCurrent(preset) else onSave(preset)
        },
    )
    deleting?.let { preset ->
        val deletedText = stringResource(Res.string.notice_preset_deleted, preset.name)
        ConfirmDialog(
            title = stringResource(Res.string.presets_delete_title, preset.name),
            text = stringResource(Res.string.presets_delete_text),
            confirm = stringResource(Res.string.presets_delete),
            dismiss = stringResource(Res.string.common_cancel),
            onConfirm = {
                onDelete(preset)
                notices.show(deletedText, NoticeAction(undo) { onRestore(preset) })
                deleting = null
                open = null
            },
            onDismiss = { deleting = null },
        )
    }
}

/**
 * The title bar with + ([onNew]), the unsaved settings with their Save ([onSaveCurrent]) when there
 * are any, then the two groups of cards.
 */
@Composable
private fun PresetsList(
    state: PresetsUiState,
    onOpen: (Preset) -> Unit,
    onNew: () -> Unit,
    onSaveCurrent: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    // One column on a phone; wider windows put the two groups side by side.
    TabGrid(
        stringResource(Res.string.presets_title),
        modifier,
        navigation = onBack?.let { back -> { BackAction(back) } },
        actions = { IconAction(IntervalIcons.Add, stringResource(Res.string.presets_new_cd), onClick = onNew) },
    ) {
        // Keyed, so the card fades and the groups slide as the current shape stops or starts matching a preset.
        if (state.canSaveCurrent) {
            item(key = "current", span = StaggeredGridItemSpan.FullLine) {
                CurrentCard(
                    config = state.current,
                    share = state.currentShare,
                    calories = state.currentCalories,
                    onSave = onSaveCurrent,
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
                // Cards here at open stand still; one saved (or restored) since rises into the group.
                val atOpen = remember { state.custom.map { it.preset.id }.toSet() }
                CardColumn {
                    state.custom.forEach { item ->
                        key(item.preset.id) {
                            AnimatedVisibility(rememberArrival(item.preset.id !in atOpen), enter = enterRising()) {
                                PresetCard(item, onClick = { onOpen(item.preset) })
                            }
                        }
                    }
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

@Composable
private fun CardColumn(content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.space8)) { content() }
}

/** The settings on the Timer tab when they match no preset: their total, shape and a button to save them. */
@Composable
private fun CurrentCard(
    config: IntervalConfig,
    share: Float,
    calories: Int?,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                Text(
                    shapeWithCalories(config, calories),
                    style = IntervalTheme.type.meta,
                    color = palette.textDim,
                )
            }
            SaveButton(onSave)
        }
        MiniTimeline(config, Modifier.fillMaxWidth(share))
    }
}

/** "20s on · 10s off · ×8", then "≈ 32 kcal" once a body weight gives an estimate. */
@Composable
private fun shapeWithCalories(config: IntervalConfig, calories: Int?, estimatedAs: Activity? = null): String {
    val shape = workoutDescription(config)
    if (calories == null) return shape
    val estimate = if (estimatedAs == null) {
        stringResource(Res.string.common_calories_estimate, calories)
    } else {
        stringResource(Res.string.presets_calories_estimate_as, calories, stringResource(estimatedAs.labelRes))
    }
    return stringResource(Res.string.common_joined, shape, estimate)
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
            .surfaceButton(interaction, Corners.radius12, palette.accent, onClick)
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
            shapeWithCalories(item.preset.config, item.calories, item.estimatedAs),
            style = IntervalTheme.type.meta,
            color = palette.textDim,
        )
        MiniTimeline(item.preset.config, Modifier.padding(top = Spacing.space4).fillMaxWidth(item.share))
    }
}

// Which preset's sheet is open and which is being asked about survive a configuration change and
// process death, like a dialog's open state; so does the editor's draft (rememberPresetDraft).

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
            onSaveCurrent = {},
            onDelete = {},
        )
    }
}
