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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.justinsmith.interval.presets.PresetDraft
import dev.justinsmith.interval.presets.PresetEditorHost
import dev.justinsmith.interval.presets.rememberPresetDraft
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.ConfigField
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.timer.valueOf
import dev.justinsmith.interval.ui.ActivitySheet
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.SetupLanes
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.ValueSheet
import dev.justinsmith.interval.ui.isLandscape
import org.koin.compose.currentKoinScope

/** Resolves the tab's ViewModel, scoped to its back-stack entry, and binds it to [TimerScreen]. */
@Composable
fun TimerRoute(
    onStart: () -> Unit,
    onOpenSettings: () -> Unit,
    onSeeAllPresets: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = currentKoinScope()
    val viewModel = viewModel { TimerViewModel(scope.get()) }
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
        modifier = modifier,
    )
}

/**
 * Home: the session total, then Work, Rest and Rounds as three tall rows, each with its own − and +
 * (hold to run), and Start within thumb reach beside the way to the presets, all over the lanes.
 * Tapping a value opens its edit sheet for the quick picks.
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
) {
    var choosingActivity by rememberSaveable { mutableStateOf(false) }
    val onEditActivity = { choosingActivity = true }
    // Saved by name: an enum is not saveable on every platform.
    var editingName by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = editingName?.let(ConfigField::valueOf)
    val onEdit = { field: ConfigField -> editingName = field.name }
    var draft by rememberPresetDraft()
    val onSave = { draft = PresetDraft.saveCurrent(state.config) }
    BoxWithConstraints(modifier.fillMaxSize().background(IntervalTheme.palette.background)) {
        if (isLandscape) {
            // Sideways: the total and Start on the left, the three rows on the right, the lanes under both.
            SetupLanes(Modifier.matchParentSize())
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center) {
                Column(Modifier.width(Sizes.setupColumnLandscape).fillMaxHeight()) {
                    TimerTitleBar(state.canSavePreset, onSave, onOpenSettings)
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
                    StepperRows(state.config, onStep, onEdit)
                }
            }
        } else {
            Box(Modifier.align(Alignment.TopCenter).widthIn(max = Sizes.content).fillMaxSize()) {
                SetupLanes(Modifier.matchParentSize())
                Column(Modifier.fillMaxSize()) {
                    TimerTitleBar(state.canSavePreset, onSave, onOpenSettings)
                    Total(state, onEditActivity, Modifier.padding(horizontal = Spacing.space20))
                    Column(Modifier.weight(1f).padding(horizontal = Spacing.space16, vertical = Spacing.space12)) {
                        StepperRows(state.config, onStep, onEdit)
                    }
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
        ValueSheet(
            field = field,
            value = state.config.valueOf(field),
            total = state.total,
            onStep = { onStep(field, it) },
            onPick = { onSet(field, it) },
            onDismiss = { editingName = null },
        )
    }
    if (choosingActivity) {
        ActivitySheet(
            selected = state.config.activity,
            onSelect = onSetActivity,
            onDismiss = { choosingActivity = false },
        )
    }
    PresetEditorHost(
        draft = draft,
        onDraftChange = { draft = it },
        prepSeconds = state.config.prepSeconds,
        presets = state.presets,
        onSave = { _, preset -> onSavePreset(preset) },
    )
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
