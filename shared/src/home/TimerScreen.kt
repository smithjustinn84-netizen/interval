package dev.justinsmith.interval.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.justinsmith.interval.timer.ConfigField
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.builtInPresets
import dev.justinsmith.interval.timer.format
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.timer.valueOf
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.SavePresetDialog
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.StartButton
import dev.justinsmith.interval.ui.TitleBar
import dev.justinsmith.interval.ui.ValueSheet
import dev.justinsmith.interval.ui.hint
import dev.justinsmith.interval.ui.label
import dev.justinsmith.interval.ui.stripe
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
 * Home: what the next workout is, at a glance, and Start within thumb reach. Each value opens an
 * edit sheet instead of carrying its own steppers; presets are one tap away in a scrolling row.
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
        val titleBar = @Composable {
            TitleBar("Timer") {
                if (state.canSavePreset) IconAction(IntervalIcons.Add, "Save as preset", onSave)
                IconAction(IntervalIcons.Settings, "Settings", onOpenSettings)
            }
        }
        if (maxWidth > maxHeight) {
            // Sideways: the summary and Start on the left, the choices scrolling on the right.
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center) {
                Column(Modifier.width(Sizes.setupColumnLandscape).fillMaxHeight()) {
                    titleBar()
                    Hero(state, Modifier.padding(horizontal = Spacing.space16))
                    Spacer(Modifier.weight(1f))
                    StartButton(onStart, Modifier.fillMaxWidth().padding(Spacing.space16))
                }
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .widthIn(max = Sizes.content)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(top = Spacing.space16, bottom = Spacing.space16),
                    verticalArrangement = Arrangement.spacedBy(Spacing.space16),
                ) {
                    PresetRow(state.presets, onApplyPreset, onSeeAllPresets)
                    Intervals(state.config, onEdit, Modifier.padding(horizontal = Spacing.space16))
                }
            }
        } else {
            Column(Modifier.align(Alignment.TopCenter).widthIn(max = Sizes.content).fillMaxSize()) {
                titleBar()
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.space16),
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
                            bottom = Spacing.space16,
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
            summary = state.summary,
            onSave = {
                onSavePreset(it)
                saving = false
            },
            onDismiss = { saving = false },
        )
    }
}

/** The session total, and the preset it matches (or Custom, which the top bar's + saves). */
@Composable
private fun Hero(state: TimerUiState, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Row(
        modifier
            .fillMaxWidth()
            .clip(Corners.radius18)
            .background(palette.surface)
            .padding(horizontal = Spacing.space20, vertical = Spacing.space16),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Total", style = IntervalTheme.type.hint, color = palette.textDim)
            Text(state.total, style = IntervalTheme.type.heroTotal, color = palette.text)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(state.presetName ?: "Custom", style = IntervalTheme.type.cardTitle, color = palette.text)
            Text(state.config.description, style = IntervalTheme.type.presetDescription, color = palette.textDim)
            if (state.prepLine != null) {
                Text(
                    state.prepLine,
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
                "Presets",
                style = IntervalTheme.type.sectionTitle,
                color = palette.text,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            TextButton(onClick = onSeeAll) {
                Text("See all", style = IntervalTheme.type.buttonLabel, color = palette.accent)
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

@Composable
private fun PresetCard(chip: PresetChip, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val shape = Corners.radius14
    Column(
        modifier
            .width(Sizes.presetChip)
            .heightIn(min = Sizes.listRow)
            .clip(shape)
            .background(if (chip.selected) palette.accentTint else palette.surface)
            .border(Sizes.border, if (chip.selected) palette.accent else palette.line, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { selected = chip.selected }
            .padding(horizontal = Spacing.space14, vertical = Spacing.space8),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(chip.preset.name, style = IntervalTheme.type.cardTitle, color = palette.text, maxLines = 1)
        Text(chip.description, style = IntervalTheme.type.presetDescription, color = palette.textDim, maxLines = 1)
    }
}

@Composable
private fun Intervals(config: IntervalConfig, onEdit: (ConfigField) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        Text(
            "Intervals",
            style = IntervalTheme.type.sectionTitle,
            color = IntervalTheme.palette.text,
            modifier = Modifier.semantics { heading() },
        )
        ListCard {
            TimerFields.forEachIndexed { index, field ->
                if (index > 0) RowDivider()
                IntervalRow(field, field.format(config.valueOf(field)), onClick = { onEdit(field) })
            }
        }
    }
}

/** One value: its phase stripe, name and hint, and the value with a chevron; tapping opens the sheet. */
@Composable
private fun IntervalRow(field: ConfigField, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .heightIn(min = Sizes.intervalRow)
            .clickable(role = Role.Button, onClickLabel = "Edit ${field.label.lowercase()}", onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space14),
    ) {
        Box(Modifier.width(Sizes.stripe).fillMaxHeight().background(field.stripe()))
        Column(Modifier.weight(1f).padding(vertical = Spacing.space10)) {
            Text(field.label, style = IntervalTheme.type.cardTitle, color = palette.text)
            Text(field.hint, style = IntervalTheme.type.hint, color = palette.textDim)
        }
        Text(
            if (field == ConfigField.Rounds) "×$value" else value,
            style = IntervalTheme.type.stepperValue,
            color = palette.text,
        )
        Icon(
            IntervalIcons.Chevron,
            contentDescription = null,
            tint = palette.textDim,
            modifier = Modifier.padding(end = Spacing.space12).size(Sizes.icon),
        )
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
                prepLine = "Starts with 0:10 to get ready",
                presets = builtInPresets.mapIndexed { i, preset ->
                    PresetChip(preset, preset.config.description, selected = i == 0)
                },
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

@Preview
@Composable
private fun TimerScreenPreview() {
    TimerPreview()
}

@Preview(widthDp = 844, heightDp = 390)
@Composable
private fun TimerScreenLandscapePreview() {
    TimerPreview()
}
