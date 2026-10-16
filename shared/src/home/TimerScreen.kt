package dev.justinsmith.interval.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.common_custom
import dev.justinsmith.interval.resources.common_joined
import dev.justinsmith.interval.resources.common_settings
import dev.justinsmith.interval.resources.config_rounds_short
import dev.justinsmith.interval.resources.preset_default_name
import dev.justinsmith.interval.resources.timer_all_presets_cd
import dev.justinsmith.interval.resources.timer_prep_line
import dev.justinsmith.interval.resources.timer_save_as_preset_cd
import dev.justinsmith.interval.resources.timer_title
import dev.justinsmith.interval.resources.timer_total
import dev.justinsmith.interval.timer.ConfigField
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.builtInPresets
import dev.justinsmith.interval.timer.format
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.timer.valueOf
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.RepeatButton
import dev.justinsmith.interval.ui.SavePresetDialog
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.StartButton
import dev.justinsmith.interval.ui.TitleBar
import dev.justinsmith.interval.ui.ValueSheet
import dev.justinsmith.interval.ui.configSummary
import dev.justinsmith.interval.ui.editRes
import dev.justinsmith.interval.ui.labelRes
import dev.justinsmith.interval.ui.lessRes
import dev.justinsmith.interval.ui.moreRes
import dev.justinsmith.interval.ui.stripe
import dev.justinsmith.interval.ui.tickDialBehind
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
        onSavePreset = viewModel::savePreset,
        onStart = onStart,
        onOpenSettings = onOpenSettings,
        onSeeAllPresets = onSeeAllPresets,
        modifier = modifier,
    )
}

/**
 * Home: the session total over the Dial's stepping ticks, then Work, Rest and Rounds as three tall
 * rows, each with its own − and +
 * (hold to run), and Start within thumb reach beside the way to the presets. Tapping a value opens its
 * edit sheet for the quick picks.
 */
@Composable
fun TimerScreen(
    state: TimerUiState,
    onStep: (ConfigField, Int) -> Unit,
    onSet: (ConfigField, Int) -> Unit,
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
            // Sideways: the total and Start on the left, the three rows on the right.
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center) {
                Column(Modifier.width(Sizes.setupColumnLandscape).fillMaxHeight().clipToBounds().tickDialBehind()) {
                    TimerTitleBar(state.canSavePreset, onSave, onOpenSettings)
                    Total(state, Modifier.padding(horizontal = Spacing.space20))
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
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = Sizes.content)
                    .fillMaxSize()
                    .clipToBounds()
                    .tickDialBehind(),
            ) {
                TimerTitleBar(state.canSavePreset, onSave, onOpenSettings)
                Total(state, Modifier.padding(horizontal = Spacing.space20))
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

/** "Tabata · Total" over the session's length, and the get-ready countdown when there is one. */
@Composable
private fun Total(state: TimerUiState, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Column(modifier.fillMaxWidth()) {
        Text(
            stringResource(
                Res.string.common_joined,
                state.presetName ?: stringResource(Res.string.common_custom),
                stringResource(Res.string.timer_total),
            ),
            style = IntervalTheme.type.caption,
            color = palette.textDim,
        )
        Text(state.total, style = IntervalTheme.type.display, color = palette.text)
        if (state.config.prepSeconds > 0) {
            Text(
                stringResource(Res.string.timer_prep_line, formatClock(state.config.prepSeconds)),
                style = IntervalTheme.type.meta,
                color = palette.textDim,
            )
        }
    }
}

/** Work, Rest and Rounds, sharing the height they're given. */
@Composable
private fun ColumnScope.StepperRows(
    config: IntervalConfig,
    onStep: (ConfigField, Int) -> Unit,
    onEdit: (ConfigField) -> Unit,
) {
    TimerFields.forEachIndexed { index, field ->
        if (index > 0) Spacer(Modifier.size(Spacing.space8))
        StepperRow(
            field,
            config.valueOf(field),
            onStep = { onStep(field, it) },
            onEdit = { onEdit(field) },
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * One value as a card: its phase stripe down the start edge, its name in the stripe's colour over the
 * value as big as the row allows, and − and + at thumb reach. The name and value open the edit sheet.
 */
@Composable
private fun StepperRow(
    field: ConfigField,
    value: Int,
    onStep: (Int) -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = IntervalTheme.palette
    val stripe = field.stripe()
    val text = if (field == ConfigField.Rounds) {
        stringResource(Res.string.config_rounds_short, value)
    } else {
        field.format(value)
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.intervalRow)
            .clip(Corners.radius18)
            .background(palette.surface)
            .drawBehind { drawRect(stripe, size = Size(Sizes.stripe.toPx(), size.height)) }
            .padding(start = Sizes.stripe + Spacing.space16, end = Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(role = Role.Button, onClickLabel = stringResource(field.editRes), onClick = onEdit)
                .padding(vertical = Spacing.space8),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(field.labelRes), style = IntervalTheme.type.label, color = stripe)
            BasicText(
                text,
                style = IntervalTheme.type.display.copy(color = palette.text),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = VALUE_MIN_SIZE.sp, maxFontSize = VALUE_MAX_SIZE.sp),
            )
        }
        RepeatButton(
            "−",
            stringResource(field.lessRes),
            enabled = value > field.range.first,
            onStep = { onStep(-1) },
        )
        RepeatButton(
            "+",
            stringResource(field.moreRes),
            enabled = value < field.range.last,
            onStep = { onStep(1) },
        )
    }
}

/** Start workout, and beside it the way to every preset. */
@Composable
private fun StartRow(onStart: () -> Unit, onSeeAllPresets: () -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val description = stringResource(Res.string.timer_all_presets_cd)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        StartButton(onStart, Modifier.weight(1f))
        Box(
            Modifier
                .size(Sizes.start)
                .clip(Corners.radius18)
                .background(palette.surface)
                .clickable(role = Role.Button, onClick = onSeeAllPresets)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                IntervalIcons.Presets,
                contentDescription = null,
                tint = palette.text,
                modifier = Modifier.size(Sizes.icon),
            )
        }
    }
}

/** The stepper value's size range: as big as the row allows, never smaller than a stat. */
private const val VALUE_MIN_SIZE = 40
private const val VALUE_MAX_SIZE = 72

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
