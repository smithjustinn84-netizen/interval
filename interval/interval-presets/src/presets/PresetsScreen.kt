package dev.justinsmith.interval.presets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.justinsmith.interval.presets.resources.Res
import dev.justinsmith.interval.presets.resources.common_cancel
import dev.justinsmith.interval.presets.resources.notice_preset_deleted
import dev.justinsmith.interval.presets.resources.notice_undo
import dev.justinsmith.interval.presets.resources.preset_save_title
import dev.justinsmith.interval.presets.resources.presets_built_in
import dev.justinsmith.interval.presets.resources.presets_delete
import dev.justinsmith.interval.presets.resources.presets_delete_text
import dev.justinsmith.interval.presets.resources.presets_delete_title
import dev.justinsmith.interval.presets.resources.presets_new_cd
import dev.justinsmith.interval.presets.resources.presets_on_timer
import dev.justinsmith.interval.presets.resources.presets_open_cd
import dev.justinsmith.interval.presets.resources.presets_save_current_hint
import dev.justinsmith.interval.presets.resources.presets_timer
import dev.justinsmith.interval.presets.resources.presets_title
import dev.justinsmith.interval.presets.resources.presets_yours
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.builtInPresets
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.BackAction
import dev.justinsmith.interval.ui.ConfirmDialog
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalSizes
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.LinkRow
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.LocalNotices
import dev.justinsmith.interval.ui.NoticeAction
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.SettingsAction
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.StripeMark
import dev.justinsmith.interval.ui.TabGrid
import dev.justinsmith.interval.ui.pressScale
import dev.justinsmith.interval.ui.rememberArrival
import dev.justinsmith.interval.ui.risingEnter
import dev.justinsmith.interval.ui.workoutDescription
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The presets as a tab's page rather than one pushed with a Back: Train's Conditioning pillar. It goes under the
 * tab's [title], with [header] across the top (the picker of Train's pillars), and its actions end in the gear,
 * [onOpenSettings], as every tab's do. Its first row opens the Timer ([onOpenTimer]), for a workout no preset
 * has.
 */
class PresetsTab(
    val title: String,
    val onOpenSettings: () -> Unit,
    val onOpenTimer: () -> Unit,
    val header: @Composable () -> Unit,
)

/**
 * Resolves the ViewModel and binds it to [PresetsScreen]. A preset goes on the Timer either way;
 * Start workout then opens the workout, Put on Timer shows the Timer. Pushed, it has a Back ([onBack]); as a
 * tab's page ([tab]) it has none.
 */
@Composable
fun PresetsRoute(
    onShowTimer: () -> Unit,
    onStartWorkout: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    tab: PresetsTab? = null,
) {
    val viewModel = koinViewModel<PresetsViewModel>()
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
        tab = tab,
    )
}

/**
 * Every preset as a row of its group's card, shortest first, the user's own group over the built-ins'
 * (and left out while it is empty): a name over its shape, its total at the end. Tapping one opens its
 * [PresetSheet]: start it, put it on the Timer, and for the user's own, edit or delete it (after asking).
 * The Timer row on top says what is on the Timer; settings that match no preset put "Save as preset" under
 * it, which opens the [PresetEditor] on them and keeps whatever it saves on the Timer ([onSaveCurrent]); +
 * in the title bar starts a new preset from them instead, leaving the Timer alone ([onSave]). While those
 * settings are unsaved, a preset's sheet says that starting or loading it replaces them. A deleted preset
 * is offered back for a moment ([onRestore]).
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
    tab: PresetsTab? = null,
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
        tab = tab,
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
            destructive = true,
        )
    }
}

/**
 * The title bar with + ([onNew]), the Timer card (with Save as preset, [onSaveCurrent], while the settings
 * on it are no preset's), then a card a group. As a [tab]'s page it takes the tab's title, header and gear;
 * pushed, it has no Timer card, since the Timer is what pushed it.
 */
@Composable
private fun PresetsList(
    state: PresetsUiState,
    onOpen: (Preset) -> Unit,
    onNew: () -> Unit,
    onSaveCurrent: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    tab: PresetsTab? = null,
) {
    // One column on a phone; wider windows put the two groups side by side.
    TabGrid(
        tab?.title ?: stringResource(Res.string.presets_title),
        modifier,
        navigation = onBack?.let { back -> { BackAction(back) } },
        actions = {
            IconAction(IntervalIcons.Add, stringResource(Res.string.presets_new_cd), onClick = onNew)
            tab?.let { SettingsAction(it.onOpenSettings) }
        },
        header = tab?.header,
    ) {
        tab?.let {
            item(key = "timer", span = StaggeredGridItemSpan.FullLine) {
                TimerCard(
                    state = state,
                    onOpenTimer = it.onOpenTimer,
                    onSaveCurrent = onSaveCurrent,
                    modifier = Modifier.padding(horizontal = Spacing.space16, vertical = Spacing.space4),
                )
            }
        }
        // The group comes and goes with its first and last preset, so a new install sees the built-ins alone.
        if (state.custom.isNotEmpty()) {
            item(key = "custom") {
                PresetGroup(
                    label = stringResource(Res.string.presets_yours),
                    items = state.custom,
                    onOpen = onOpen,
                    modifier = Modifier.animateItem(),
                    rising = true,
                )
            }
        }
        item(key = "builtIn") {
            PresetGroup(
                label = stringResource(Res.string.presets_built_in),
                items = state.builtIn,
                onOpen = onOpen,
                modifier = Modifier.animateItem(),
            )
        }
    }
}

/**
 * A group's label over one card of its rows. When [rising], a row saved (or restored) since the page opened
 * rises into the card; the card closes up smoothly over a deleted one.
 */
@Composable
private fun PresetGroup(
    label: String,
    items: List<PresetRowItem>,
    onOpen: (Preset) -> Unit,
    modifier: Modifier = Modifier,
    rising: Boolean = false,
) {
    Column(modifier.padding(horizontal = Spacing.space16, vertical = Spacing.space4).animateContentSize()) {
        GroupLabel(label)
        val atOpen = remember { items.map { it.preset.id }.toSet() }
        ListCard {
            items.forEachIndexed { index, item ->
                key(item.preset.id ?: item.preset.name) {
                    AnimatedVisibility(rememberArrival(rising && item.preset.id !in atOpen), enter = risingEnter()) {
                        Column {
                            if (index > 0) RowDivider(Modifier.padding(start = Spacing.space20))
                            PresetRow(item, onClick = { onOpen(item.preset) })
                        }
                    }
                }
            }
        }
    }
}

/**
 * One preset: a stripe in Conditioning's colour, its name over its shape, and its total at the end of the
 * row, which is the button. The one on the Timer says so over its name and takes the accent.
 */
@Composable
private fun PresetRow(item: PresetRowItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.listRow)
            .clickable(
                interaction,
                LocalIndication.current,
                role = Role.Button,
                onClickLabel = stringResource(Res.string.presets_open_cd),
                onClick = onClick,
            )
            .pressScale(interaction)
            .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        StripeMark(if (item.selected) palette.accent else palette.workMark, height = IntervalSizes.historyMarkHeight)
        Column(Modifier.weight(1f)) {
            if (item.selected) {
                Text(
                    stringResource(Res.string.presets_on_timer),
                    style = IntervalTheme.type.caption,
                    color = palette.accent,
                )
            }
            Text(
                item.preset.name,
                style = IntervalTheme.type.label,
                color = palette.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                workoutDescription(item.preset.config),
                style = IntervalTheme.type.meta,
                color = palette.textDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(item.total, style = IntervalTheme.type.value, color = if (item.selected) palette.accent else palette.text)
    }
}

/**
 * The way to the Timer, saying what is on it: the preset's name when a preset is on it, else the shape. While
 * the shape is no preset's, a second row offers to save it ([onSaveCurrent]), since settings nothing holds
 * are a tap from being lost; the card grows and shrinks with it.
 */
@Composable
private fun TimerCard(
    state: PresetsUiState,
    onOpenTimer: () -> Unit,
    onSaveCurrent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListCard(modifier.animateContentSize()) {
        TimerRow(state, onOpenTimer)
        if (state.canSaveCurrent) {
            RowDivider()
            LinkRow(
                stringResource(Res.string.preset_save_title),
                stringResource(Res.string.presets_save_current_hint),
                onClick = onSaveCurrent,
            )
        }
    }
}

/**
 * "Timer" over what is on it: the preset's name when a preset is (its shape is on its own row below), else the
 * shape, which can run to two lines with a warm-up or sets; its total at the end, then a chevron.
 */
@Composable
private fun TimerRow(state: PresetsUiState, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val interaction = remember { MutableInteractionSource() }
    val onTimer = state.rows.firstOrNull { it.selected }?.preset?.name
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.listRow)
            .clickable(interaction, LocalIndication.current, role = Role.Button, onClick = onOpen)
            .pressScale(interaction)
            .padding(start = Spacing.space16, end = Spacing.space12, top = Spacing.space8, bottom = Spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(Res.string.presets_timer), style = IntervalTheme.type.label, color = palette.text)
            Text(
                onTimer ?: workoutDescription(state.current),
                style = IntervalTheme.type.meta,
                color = palette.textDim,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(formatClock(state.current.totalSeconds), style = IntervalTheme.type.value, color = palette.text)
        Icon(
            IntervalIcons.Chevron,
            contentDescription = null,
            tint = palette.textDim,
            modifier = Modifier.size(Sizes.icon),
        )
    }
}

// Which preset's sheet is open and which is being asked about survive a configuration change and
// process death, like a dialog's open state; so does the editor's draft (rememberPresetDraft).

@PreviewFormFactors
@Composable
private fun PresetsScreenPreview() {
    val current = builtInPresets.first().config.copy(workSeconds = 25)
    fun row(preset: Preset, selected: Boolean = false) =
        PresetRowItem(preset, formatClock(preset.config.totalSeconds), selected)
    IntervalTheme {
        PresetsScreen(
            state = PresetsUiState(
                custom = listOf(
                    row(Preset("Leg day 40/20", current.copy(workSeconds = 40, restSeconds = 20, rounds = 6), id = 1)),
                ),
                builtIn = builtInPresets.sortedBy { it.config.totalSeconds }.map { row(it) },
                canSaveCurrent = true,
                current = current,
            ),
            tab = PresetsTab("Train", onOpenSettings = {}, onOpenTimer = {}) {},
            onStart = {},
            onPutOnTimer = {},
            onSave = {},
            onSaveCurrent = {},
            onDelete = {},
        )
    }
}
