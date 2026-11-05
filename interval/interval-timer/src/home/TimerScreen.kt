package dev.justinsmith.interval.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.justinsmith.interval.home.resources.Res
import dev.justinsmith.interval.home.resources.timer_add_block
import dev.justinsmith.interval.presets.EditorMode
import dev.justinsmith.interval.presets.PresetDraft
import dev.justinsmith.interval.presets.PresetEditorHost
import dev.justinsmith.interval.presets.rememberPresetDraft
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.ConfigField
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.timer.valueOf
import dev.justinsmith.interval.timer.withBlockAdded
import dev.justinsmith.interval.ui.ActivitySheet
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.LadderField
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.SetupLanes
import dev.justinsmith.interval.ui.SheetField
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.ValueSheet
import dev.justinsmith.interval.ui.isLandscape
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Resolves the tab's ViewModel, scoped to its back-stack entry, and binds it to [TimerScreen]. */
@Composable
fun TimerRoute(
    onStart: () -> Unit,
    onOpenSettings: () -> Unit,
    onSeeAllPresets: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = koinViewModel<TimerViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    TimerScreen(
        state = state,
        onStep = viewModel::step,
        onSet = viewModel::set,
        onSavePreset = viewModel::savePreset,
        onStart = onStart,
        onOpenSettings = onOpenSettings,
        onSeeAllPresets = onSeeAllPresets,
        onSetActivity = viewModel::setActivity,
        onUseDescription = viewModel::useDescribed,
        modifier = modifier,
    )
}

/**
 * Home: the session total, then Work, Rest and Rounds as three tall rows, each with its own − and +
 * (hold to run), the sets, the warm-up and the cool-down as a row of chips under them, and Start within thumb
 * reach beside the way to the presets, all over the lanes. Tapping a value opens its edit sheet for the quick
 * picks, and for what changes it: the ladder on the work's, the rest between sets on the sets'. The title
 * bar opens [DescribeSheet], whose workout goes to [onUseDescription] or, through the preset editor,
 * [onSavePreset].
 */
@Composable
fun TimerScreen(
    state: TimerUiState,
    onStep: (ConfigField, Int) -> Unit,
    onSet: (ConfigField, Int) -> Unit,
    onSavePreset: (Preset) -> Unit,
    onStart: () -> Unit,
    onOpenSettings: () -> Unit,
    onSeeAllPresets: () -> Unit,
    modifier: Modifier = Modifier,
    onSetActivity: (Activity) -> Unit = {},
    onUseDescription: (IntervalConfig) -> Unit = {},
) {
    var choosingActivity by rememberSaveable { mutableStateOf(false) }
    val onEditActivity = { choosingActivity = true }
    // Saved by name: an enum is not saveable on every platform.
    var editingName by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = editingName?.let(ConfigField::valueOf)
    val onEdit = { field: ConfigField -> editingName = field.name }
    var draft by rememberPresetDraft()
    val onSave = { draft = PresetDraft.saveCurrent(state.config) }
    val onEditBlocks = { draft = PresetDraft.timer(state.config) }
    var describing by rememberSaveable { mutableStateOf(false) }
    val onDescribe = { describing = true }
    BoxWithConstraints(modifier.fillMaxSize().background(IntervalTheme.palette.background)) {
        if (isLandscape) {
            // Sideways: the total and Start on the left, the three rows on the right, the lanes under both.
            SetupLanes(Modifier.matchParentSize())
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center) {
                Column(Modifier.width(Sizes.setupColumnLandscape).fillMaxHeight()) {
                    TimerTitleBar(state.canSavePreset, onSave, onDescribe, onOpenSettings)
                    Total(state, onEditActivity, Modifier.padding(horizontal = Spacing.space20))
                    Spacer(Modifier.weight(1f))
                    StartRow(onStart, onSeeAllPresets, Modifier.padding(Spacing.space16))
                }
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .widthIn(max = Sizes.contentWide)
                        .fillMaxHeight()
                        .padding(top = Spacing.space16, bottom = Spacing.space16, end = Spacing.space16),
                ) {
                    if (state.config.isSequence) {
                        SequenceRows(state.config, onEditBlocks, onEdit)
                    } else {
                        StepperRows(state.config, onStep, onEdit)
                    }
                }
            }
        } else {
            // Upright: one column, as wide as a tablet's, over lanes that fill the window.
            SetupLanes(Modifier.matchParentSize())
            // Larger text needs more height for the same rows: below it they would be squeezed, so they scroll.
            val fontScale = LocalDensity.current.fontScale
            val expandRows =
                maxHeight >= COMPACT_PORTRAIT_HEIGHT * fontScale.coerceAtLeast(1f) && fontScale <= COMPACT_FONT_SCALE
            Box(Modifier.align(Alignment.TopCenter).widthIn(max = Sizes.contentWide).fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    TimerTitleBar(state.canSavePreset, onSave, onDescribe, onOpenSettings)
                    TimerBody(state, expandRows, onStep, onEdit, onEditActivity, onEditBlocks, Modifier.weight(1f))
                    StartRow(
                        onStart,
                        onSeeAllPresets,
                        Modifier.padding(start = Spacing.space16, end = Spacing.space16, bottom = Spacing.space16),
                    )
                }
            }
        }
    }
    editing?.let { field ->
        val addBlock = { draft = PresetDraft.timer(state.config.withBlockAdded()).also { editingName = null } }
        TimerValueSheet(field, state, onStep, onSet, onAddBlock = addBlock, onDismiss = { editingName = null })
    }
    if (choosingActivity) {
        ActivitySheet(
            selected = state.config.activity,
            onSelect = onSetActivity,
            onDismiss = { choosingActivity = false },
        )
    }
    if (describing) {
        DescribeSheet(state, onUseDescription, { draft = PresetDraft.saveCurrent(it) }) { describing = false }
    }
    PresetEditorHost(
        draft = draft,
        onDraftChange = { draft = it },
        prepSeconds = state.config.prepSeconds,
        presets = state.presets,
        // The Timer's own workout, shaped in the editor, goes back on the Timer as a described one does.
        onSave = { opened, preset ->
            if (opened.mode == EditorMode.Timer) onUseDescription(preset.config) else onSavePreset(preset)
        },
    )
}

/**
 * [field]'s edit sheet, with what changes that value under its quick picks rather than on the Timer: the ladder
 * on the work's, and the rest between sets on the sets' once there is more than one, and the way to a sequence,
 * Add a block, which opens the editor with a second through [onAddBlock].
 */
@Composable
private fun TimerValueSheet(
    field: ConfigField,
    state: TimerUiState,
    onStep: (ConfigField, Int) -> Unit,
    onSet: (ConfigField, Int) -> Unit,
    onAddBlock: () -> Unit,
    onDismiss: () -> Unit,
) {
    val config = state.config
    ValueSheet(
        field = field,
        value = config.valueOf(field),
        total = state.total,
        onStep = { onStep(field, it) },
        onPick = { onSet(field, it) },
        onDismiss = onDismiss,
    ) {
        when {
            field == ConfigField.Work -> LadderField(config, onStep = { onStep(ConfigField.WorkStep, it) })

            field == ConfigField.Sets -> Column {
                if (config.sets > 1) {
                    SheetField(ConfigField.SetRest, config.setRestSeconds, onStep = { onStep(ConfigField.SetRest, it) })
                }
                QuietTextButton(
                    stringResource(Res.string.timer_add_block),
                    onClick = onAddBlock,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

@Composable
private fun TimerBody(
    state: TimerUiState,
    expandRows: Boolean,
    onStep: (ConfigField, Int) -> Unit,
    onEdit: (ConfigField) -> Unit,
    onEditActivity: () -> Unit,
    onEditBlocks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = if (expandRows) Modifier else Modifier.verticalScroll(rememberScrollState())
    Column(modifier.then(scroll)) {
        Total(state, onEditActivity, Modifier.padding(horizontal = Spacing.space20))
        val rowModifier = if (expandRows) Modifier.weight(1f) else Modifier
        Column(rowModifier.padding(horizontal = Spacing.space16, vertical = Spacing.space12)) {
            if (state.config.isSequence) {
                SequenceRows(state.config, onEditBlocks, onEdit, fillHeight = expandRows)
            } else {
                StepperRows(state.config, onStep, onEdit, fillHeight = expandRows)
            }
        }
    }
}

@Composable
private fun TimerPreview() {
    val config = IntervalConfig(prepSeconds = 10, workSeconds = 20, restSeconds = 10, rounds = 8)
    IntervalTheme {
        TimerScreen(
            state = TimerUiState(
                config = config,
                total = formatClock(config.totalSeconds),
                presetName = "Tabata",
            ),
            onStep = { _, _ -> },
            onSet = { _, _ -> },
            onSavePreset = {},
            onStart = {},
            onOpenSettings = {},
            onSeeAllPresets = {},
        )
    }
}

@PreviewFormFactors
@Composable
private fun TimerScreenPreview() {
    TimerPreview()
}

/**
 * Below this portrait height (at a font scale of 1, grown with a larger one) or above [COMPACT_FONT_SCALE], the
 * upper fields scroll above a pinned Start row. It is the height the three rows need to share for each to hold
 * its name over its value at the smallest size (80 dp a row), with the row of chips and all that is fixed round
 * them; shorter, and sharing would clip the values.
 */
private val COMPACT_PORTRAIT_HEIGHT = 730.dp
private const val COMPACT_FONT_SCALE = 1.35f
