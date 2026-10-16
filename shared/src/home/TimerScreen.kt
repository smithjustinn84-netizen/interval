@file:OptIn(ExperimentalGridApi::class)

package dev.justinsmith.interval.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalGridApi
import androidx.compose.foundation.layout.Grid
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.common_custom
import dev.justinsmith.interval.resources.common_settings
import dev.justinsmith.interval.resources.preset_default_name
import dev.justinsmith.interval.resources.timer_intervals
import dev.justinsmith.interval.resources.timer_prep_line
import dev.justinsmith.interval.resources.timer_presets
import dev.justinsmith.interval.resources.timer_save_as_preset_cd
import dev.justinsmith.interval.resources.timer_see_all
import dev.justinsmith.interval.resources.timer_title
import dev.justinsmith.interval.resources.timer_total
import dev.justinsmith.interval.timer.ConfigField
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.builtInPresets
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.timer.valueOf
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.FieldRow
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.SavePresetDialog
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.StartButton
import dev.justinsmith.interval.ui.Timeline
import dev.justinsmith.interval.ui.TitleBar
import dev.justinsmith.interval.ui.ValueSheet
import dev.justinsmith.interval.ui.configDescription
import dev.justinsmith.interval.ui.configSummary
import dev.justinsmith.interval.ui.selectableCard
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.currentKoinScope

/** The values the Timer tab edits. Get ready is a setting, edited from Settings. */
private val TimerFields = listOf(ConfigField.Work, ConfigField.Rest, ConfigField.Rounds)

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
        onApplyPreset = viewModel::applyPreset,
        onSavePreset = viewModel::savePreset,
        onStart = onStart,
        onOpenSettings = onOpenSettings,
        onSeeAllPresets = onSeeAllPresets,
        modifier = modifier,
    )
}

/**
 * Home: what the next workout is, at a glance, and Start within thumb reach. The hero card draws
 * the workout as a timeline in the phase colours; each value opens an edit sheet instead of
 * carrying its own steppers; presets are one tap away in a scrolling row.
 */
@Composable
fun TimerScreen(
    state: TimerUiState,
    onStep: (ConfigField, Int) -> Unit,
    onSet: (ConfigField, Int) -> Unit,
    onApplyPreset: (Preset) -> Unit,
    onSavePreset: (String) -> Unit,
    onStart: () -> Unit,
    onOpenSettings: () -> Unit,
    onSeeAllPresets: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Saved by name: an enum is not saveable on every platform.
    var editingName by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = editingName?.let(ConfigField::valueOf)
    val onEdit = { field: ConfigField -> editingName = field.name }
    var saving by rememberSaveable { mutableStateOf(false) }
    val onSave = { saving = true }
    BoxWithConstraints(modifier.fillMaxSize().background(IntervalTheme.palette.background)) {
        if (maxWidth > maxHeight) {
            // Sideways: the summary and Start on the left, the choices scrolling on the right.
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center) {
                Column(Modifier.width(Sizes.setupColumnLandscape).fillMaxHeight()) {
                    TimerTitleBar(state.canSavePreset, onSave, onOpenSettings)
                    Hero(state, Modifier.padding(horizontal = Spacing.space16))
                    Spacer(Modifier.weight(1f))
                    StartButton(onStart, Modifier.fillMaxWidth().padding(Spacing.space16))
                }
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .widthIn(max = Sizes.contentWide)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(top = Spacing.space16, bottom = Spacing.space16),
                    verticalArrangement = Arrangement.spacedBy(Spacing.space14),
                ) {
                    PresetRow(state.presets, onApplyPreset, onSeeAllPresets)
                    Intervals(state.config, onEdit, Modifier.padding(horizontal = Spacing.space16))
                }
            }
        } else {
            // A tablet or unfolded foldable held upright widens the column, and the Intervals with it.
            Column(Modifier.align(Alignment.TopCenter).widthIn(max = Sizes.contentWide).fillMaxSize()) {
                TimerTitleBar(state.canSavePreset, onSave, onOpenSettings)
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.space14),
                ) {
                    Hero(state, Modifier.padding(horizontal = Spacing.space16))
                    PresetRow(state.presets, onApplyPreset, onSeeAllPresets)
                    Intervals(state.config, onEdit, Modifier.padding(horizontal = Spacing.space16))
                }
                StartButton(
                    onStart,
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = Spacing.space16,
                            end = Spacing.space16,
                            top = Spacing.space12,
                            bottom = Spacing.space14,
                        ),
                )
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
    if (saving) {
        SavePresetDialog(
            summary = configSummary(state.config),
            defaultName = stringResource(Res.string.preset_default_name, state.nextCustomNumber),
            onSave = {
                onSavePreset(it)
                saving = false
            },
            onDismiss = { saving = false },
        )
    }
}

/** The title, with + to save a shape no preset has yet, and the settings gear. */
@Composable
private fun TimerTitleBar(canSavePreset: Boolean, onSave: () -> Unit, onOpenSettings: () -> Unit) {
    TitleBar(stringResource(Res.string.timer_title)) {
        if (canSavePreset) IconAction(IntervalIcons.Add, stringResource(Res.string.timer_save_as_preset_cd), onSave)
        IconAction(IntervalIcons.Settings, stringResource(Res.string.common_settings), onOpenSettings)
    }
}

/**
 * The session total, the preset it matches (or Custom, which the top bar's + saves), and the
 * workout drawn as a timeline: get ready, then every work and rest, to scale.
 */
@Composable
private fun Hero(state: TimerUiState, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Column(
        modifier
            .fillMaxWidth()
            .clip(Corners.radius18)
            .background(palette.surface)
            .padding(start = Spacing.space20, end = Spacing.space20, top = Spacing.space18, bottom = Spacing.space16),
        verticalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.space12)) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.timer_total),
                    style = IntervalTheme.type.caption,
                    color = palette.textDim,
                )
                Text(state.total, style = IntervalTheme.type.heroTotal, color = palette.text)
            }
            Box(
                Modifier
                    .padding(top = Spacing.space2)
                    .height(Sizes.badgeHeight)
                    .clip(Corners.full)
                    .background(palette.accentTint)
                    .padding(horizontal = Spacing.space12),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    state.presetName ?: stringResource(Res.string.common_custom),
                    style = IntervalTheme.type.buttonLabel,
                    color = palette.accent,
                )
            }
        }
        Timeline(state.config)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
        ) {
            Text(
                configDescription(state.config),
                style = IntervalTheme.type.cardTitle,
                color = palette.text,
                modifier = Modifier.weight(1f),
            )
            if (state.config.prepSeconds > 0) {
                Text(
                    stringResource(Res.string.timer_prep_line, formatClock(state.config.prepSeconds)),
                    style = IntervalTheme.type.hint,
                    color = palette.textDim,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun PresetRow(
    presets: List<PresetChip>,
    onApply: (Preset) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = IntervalTheme.palette
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.space4)) {
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.space16, end = Spacing.space4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.timer_presets),
                style = IntervalTheme.type.sectionTitle,
                color = palette.text,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            TextButton(onClick = onSeeAll) {
                Text(
                    stringResource(Res.string.timer_see_all),
                    style = IntervalTheme.type.buttonLabel,
                    color = palette.accent,
                )
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.space16),
            horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
        ) {
            items(presets, key = { it.preset.name + it.preset.config }) { chip ->
                PresetCard(chip, onClick = { onApply(chip.preset) })
            }
        }
    }
}

/** A preset's name, total and shape, with a small timeline of its rounds. */
@Composable
private fun PresetCard(chip: PresetChip, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Column(
        modifier
            .width(Sizes.presetChip)
            .selectableCard(chip.selected)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.space14, vertical = Spacing.space12),
        verticalArrangement = Arrangement.spacedBy(Spacing.space6),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                chip.preset.name,
                style = IntervalTheme.type.cardTitle,
                color = palette.text,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                formatClock(chip.preset.config.totalSeconds),
                style = IntervalTheme.type.presetTotal,
                color = if (chip.selected) palette.accent else palette.text,
            )
        }
        Text(
            configDescription(chip.preset.config),
            style = IntervalTheme.type.hint,
            color = palette.textDim,
            maxLines = 1,
        )
        Timeline(chip.preset.config, height = Sizes.timelineMini, gap = Spacing.space1, showPrep = false)
    }
}

@Composable
private fun Intervals(config: IntervalConfig, onEdit: (ConfigField) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        Text(
            stringResource(Res.string.timer_intervals),
            style = IntervalTheme.type.sectionTitle,
            color = IntervalTheme.palette.text,
            modifier = Modifier.semantics { heading() },
        )
        BoxWithConstraints {
            if (maxWidth >= Sizes.intervalGridMin) {
                // Room for two columns: Work and Rest side by side, Rounds across both, each its own card.
                Grid(config = {
                    column(1.fr)
                    column(1.fr)
                    gap(Spacing.space8)
                }) {
                    TimerFields.forEach { field ->
                        FieldRow(
                            field,
                            config.valueOf(field),
                            onClick = { onEdit(field) },
                            modifier = Modifier
                                .gridItem(columnSpan = if (field == ConfigField.Rounds) 2 else 1)
                                .clip(Corners.radius18)
                                .background(IntervalTheme.palette.surface),
                        )
                    }
                }
            } else {
                ListCard {
                    TimerFields.forEachIndexed { index, field ->
                        if (index > 0) RowDivider()
                        FieldRow(field, config.valueOf(field), onClick = { onEdit(field) })
                    }
                }
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
                presets = builtInPresets.mapIndexed { i, preset -> PresetChip(preset, selected = i == 0) },
            ),
            onStep = { _, _ -> },
            onSet = { _, _ -> },
            onApplyPreset = {},
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
