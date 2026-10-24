package dev.justinsmith.interval.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.justinsmith.interval.data.AppSettings
import dev.justinsmith.interval.data.CueSettings
import dev.justinsmith.interval.data.ThemeMode
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.effort_easy
import dev.justinsmith.interval.resources.effort_hard
import dev.justinsmith.interval.resources.effort_max
import dev.justinsmith.interval.resources.effort_steady
import dev.justinsmith.interval.resources.settings_about
import dev.justinsmith.interval.resources.settings_about_app
import dev.justinsmith.interval.resources.settings_about_app_hint
import dev.justinsmith.interval.resources.settings_appearance
import dev.justinsmith.interval.resources.settings_calories
import dev.justinsmith.interval.resources.settings_close_cd
import dev.justinsmith.interval.resources.settings_cues
import dev.justinsmith.interval.resources.settings_cues_all_off
import dev.justinsmith.interval.resources.settings_effort_hint
import dev.justinsmith.interval.resources.settings_halfway
import dev.justinsmith.interval.resources.settings_halfway_hint
import dev.justinsmith.interval.resources.settings_halfway_needs_cue
import dev.justinsmith.interval.resources.settings_keep_screen_on
import dev.justinsmith.interval.resources.settings_keep_screen_on_hint
import dev.justinsmith.interval.resources.settings_sound
import dev.justinsmith.interval.resources.settings_sound_hint
import dev.justinsmith.interval.resources.settings_title
import dev.justinsmith.interval.resources.settings_vibrate
import dev.justinsmith.interval.resources.settings_vibrate_hint
import dev.justinsmith.interval.resources.settings_voice
import dev.justinsmith.interval.resources.settings_voice_hint
import dev.justinsmith.interval.resources.settings_weight
import dev.justinsmith.interval.resources.settings_weight_unset
import dev.justinsmith.interval.resources.settings_weight_value
import dev.justinsmith.interval.resources.settings_workout
import dev.justinsmith.interval.resources.theme_dark
import dev.justinsmith.interval.resources.theme_light
import dev.justinsmith.interval.resources.theme_system
import dev.justinsmith.interval.timer.ConfigField
import dev.justinsmith.interval.timer.Effort
import dev.justinsmith.interval.timer.WeightUnit
import dev.justinsmith.interval.timer.WorkoutEngine
import dev.justinsmith.interval.ui.BackAction
import dev.justinsmith.interval.ui.FieldRow
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.LinkRow
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.ScrollPage
import dev.justinsmith.interval.ui.SegmentedPicker
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.TabGrid
import dev.justinsmith.interval.ui.ToggleRow
import dev.justinsmith.interval.ui.ValueSheet
import dev.justinsmith.interval.ui.enterExpanding
import dev.justinsmith.interval.ui.exitShrinking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.currentKoinScope

/** Resolves the screen's ViewModel and binds it to [SettingsScreen]. */
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onShowAbout: () -> Unit,
    modifier: Modifier = Modifier,
    inPane: Boolean = false,
) {
    val scope = currentKoinScope()
    val viewModel = viewModel { SettingsViewModel(scope.get(), scope.get()) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        onBack = onBack,
        onToggle = viewModel::toggle,
        onStepPrep = viewModel::stepPrep,
        onSetPrep = viewModel::setPrep,
        onToggleKeepScreenOn = viewModel::toggleKeepScreenOn,
        onSetTheme = viewModel::setTheme,
        onShowAbout = onShowAbout,
        modifier = modifier,
        inPane = inPane,
        onSetWeight = viewModel::setWeight,
        onSetWeightUnit = viewModel::setWeightUnit,
        onClearWeight = viewModel::clearWeight,
        onSetEffort = viewModel::setEffort,
    )
}

/**
 * The set-once preferences, out of the way of the workout itself, and the way to About. [inPane]
 * when it sits beside the Timer tab on a wide window: there it closes rather than going back.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onToggle: (CueToggle) -> Unit,
    onStepPrep: (Int) -> Unit,
    onSetPrep: (Int) -> Unit,
    onToggleKeepScreenOn: () -> Unit,
    onSetTheme: (ThemeMode) -> Unit,
    onShowAbout: () -> Unit,
    modifier: Modifier = Modifier,
    inPane: Boolean = false,
    onSetWeight: (Int) -> Unit = {},
    onSetWeightUnit: (WeightUnit) -> Unit = {},
    onClearWeight: () -> Unit = {},
    onSetEffort: (Effort) -> Unit = {},
) {
    var editingPrep by rememberSaveable { mutableStateOf(false) }
    var editingWeight by rememberSaveable { mutableStateOf(false) }
    val groups = settingsGroups(
        state,
        onToggle,
        onEditPrep = { editingPrep = true },
        onToggleKeepScreenOn,
        onEditWeight = { editingWeight = true },
        onSetEffort,
        onSetTheme,
        onShowAbout,
    )
    if (inPane) {
        // Beside the Timer: one column, a line along the edge it shares with the Timer.
        ScrollPage(
            stringResource(Res.string.settings_title),
            modifier = modifier.hairlineAtStart(IntervalTheme.palette.line),
            actions = { IconAction(IntervalIcons.Close, stringResource(Res.string.settings_close_cd), onBack) },
        ) {
            groups.forEach { group -> group.content() }
        }
    } else {
        // Full screen: one column on a phone, the groups side by side as the window widens.
        TabGrid(
            stringResource(Res.string.settings_title),
            modifier,
            navigation = { BackAction(onBack) },
        ) {
            groups.forEach { group ->
                item(key = group.key) { Column(Modifier.padding(horizontal = Spacing.space16)) { group.content() } }
            }
        }
    }
    if (editingPrep) {
        ValueSheet(
            field = ConfigField.Prep,
            value = state.prepSeconds,
            total = null,
            onStep = onStepPrep,
            onPick = onSetPrep,
            onDismiss = { editingPrep = false },
        )
    }
    if (editingWeight) {
        WeightSheet(
            weight = state.weight,
            unit = state.body.unit,
            onSetWeight = onSetWeight,
            onSetUnit = onSetWeightUnit,
            onClear = onClearWeight,
            onDismiss = { editingWeight = false },
        )
    }
}

/** One group of settings, its label over its card, keyed for the grid. */
private class SettingsGroup(val key: String, val content: @Composable () -> Unit)

/** The groups in order: cues, the workout, calories, appearance and About. */
@Suppress("detekt.LongParameterList") // One callback per setting, as the screen takes them.
private fun settingsGroups(
    state: SettingsUiState,
    onToggle: (CueToggle) -> Unit,
    onEditPrep: () -> Unit,
    onToggleKeepScreenOn: () -> Unit,
    onEditWeight: () -> Unit,
    onSetEffort: (Effort) -> Unit,
    onSetTheme: (ThemeMode) -> Unit,
    onShowAbout: () -> Unit,
): List<SettingsGroup> = listOf(
    SettingsGroup("cues") {
        GroupLabel(stringResource(Res.string.settings_cues))
        CuesCard(state.cues, onToggle)
    },
    SettingsGroup("workout") {
        GroupLabel(stringResource(Res.string.settings_workout))
        ListCard {
            FieldRow(ConfigField.Prep, state.prepSeconds, onClick = onEditPrep, compact = true)
            RowDivider()
            ToggleRow(
                stringResource(Res.string.settings_keep_screen_on),
                stringResource(Res.string.settings_keep_screen_on_hint),
                state.app.keepScreenOn,
                onToggle = onToggleKeepScreenOn,
            )
        }
    },
    SettingsGroup("calories") {
        GroupLabel(stringResource(Res.string.settings_calories))
        CaloriesCard(state, onEditWeight = onEditWeight, onSetEffort = onSetEffort)
    },
    SettingsGroup("appearance") {
        GroupLabel(stringResource(Res.string.settings_appearance))
        SegmentedPicker(ThemeMode.entries, state.app.theme, label = { stringResource(it.labelRes) }, onSetTheme)
    },
    SettingsGroup("about") {
        GroupLabel(stringResource(Res.string.settings_about))
        ListCard {
            LinkRow(
                stringResource(Res.string.settings_about_app),
                stringResource(Res.string.settings_about_app_hint),
                onClick = onShowAbout,
            )
        }
    },
)

/** A line along the start edge, drawn over the content: where a pane meets the one beside it. */
private fun Modifier.hairlineAtStart(color: Color): Modifier = drawWithContent {
    drawContent()
    val stroke = Sizes.hairline.toPx()
    val x = if (layoutDirection == LayoutDirection.Ltr) stroke / 2 else size.width - stroke / 2
    drawLine(color, Offset(x, 0f), Offset(x, size.height), stroke)
}

/**
 * The four cue switches: which of the beeps, voice, halfway alert and vibration play. A switch turned
 * on plays its cue once. The halfway alert is a beep or spoken, so it dims while sound and voice are
 * both off, keeping its own setting for when one comes back; with every cue off, a line says so.
 */
@Composable
private fun CuesCard(cues: CueSettings, onToggle: (CueToggle) -> Unit, modifier: Modifier = Modifier) {
    val halfwayAudible = cues.sound || cues.voice
    Column(modifier) {
        ListCard { CueRows(cues, halfwayAudible, onToggle) }
        AnimatedVisibility(
            !halfwayAudible && !cues.vibrate,
            enter = enterExpanding(),
            exit = exitShrinking(),
        ) {
            Text(
                stringResource(Res.string.settings_cues_all_off),
                style = IntervalTheme.type.meta,
                color = IntervalTheme.palette.textDim,
                modifier = Modifier.padding(start = Spacing.space4, top = Spacing.space8),
            )
        }
    }
}

/** The rows of [CuesCard]'s card, in its column. */
@Suppress("detekt.MultipleEmitters", "detekt.ModifierMissing") // The card's rows, laid out by the ListCard.
@Composable
private fun ColumnScope.CueRows(cues: CueSettings, halfwayAudible: Boolean, onToggle: (CueToggle) -> Unit) {
    ToggleRow(
        stringResource(Res.string.settings_sound),
        stringResource(Res.string.settings_sound_hint),
        cues.sound,
        onToggle = { onToggle(CueToggle.Sound) },
    )
    RowDivider()
    ToggleRow(
        stringResource(Res.string.settings_voice),
        stringResource(Res.string.settings_voice_hint),
        cues.voice,
        onToggle = { onToggle(CueToggle.Voice) },
    )
    RowDivider()
    ToggleRow(
        stringResource(Res.string.settings_halfway),
        if (halfwayAudible) {
            stringResource(Res.string.settings_halfway_hint, WorkoutEngine.HALFWAY_MIN_SECONDS)
        } else {
            stringResource(Res.string.settings_halfway_needs_cue)
        },
        cues.halfway && halfwayAudible,
        onToggle = { onToggle(CueToggle.Halfway) },
        enabled = halfwayAudible,
    )
    RowDivider()
    ToggleRow(
        stringResource(Res.string.settings_vibrate),
        stringResource(Res.string.settings_vibrate_hint),
        cues.vibrate,
        onToggle = { onToggle(CueToggle.Vibrate) },
    )
}

/** The calorie estimates' inputs: the body weight, which opens its sheet, and the effort of the work. */
@Composable
private fun CaloriesCard(
    state: SettingsUiState,
    onEditWeight: () -> Unit,
    onSetEffort: (Effort) -> Unit,
    modifier: Modifier = Modifier,
) {
    val weight = state.weight
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        ListCard {
            LinkRow(
                stringResource(Res.string.settings_weight),
                if (weight == null) {
                    stringResource(Res.string.settings_weight_unset)
                } else {
                    stringResource(Res.string.settings_weight_value, weight, stringResource(state.body.unit.labelRes))
                },
                onClick = onEditWeight,
            )
        }
        SegmentedPicker(Effort.entries, state.body.effort, label = { stringResource(it.labelRes) }, onSetEffort)
        Text(
            stringResource(Res.string.settings_effort_hint),
            style = IntervalTheme.type.meta,
            color = IntervalTheme.palette.textDim,
            modifier = Modifier.padding(start = Spacing.space4),
        )
    }
}

/** How the effort picker names each level; [Effort.name] is what is stored. */
private val Effort.labelRes: StringResource
    get() = when (this) {
        Effort.Easy -> Res.string.effort_easy
        Effort.Steady -> Res.string.effort_steady
        Effort.Hard -> Res.string.effort_hard
        Effort.Max -> Res.string.effort_max
    }

/** How the theme picker names each mode; [ThemeMode.name] is what is stored. */
private val ThemeMode.labelRes: StringResource
    get() = when (this) {
        ThemeMode.System -> Res.string.theme_system
        ThemeMode.Light -> Res.string.theme_light
        ThemeMode.Dark -> Res.string.theme_dark
    }

@PreviewFormFactors
@Composable
private fun SettingsScreenPreview() {
    IntervalTheme {
        SettingsScreen(
            state = SettingsUiState(CueSettings(vibrate = false), prepSeconds = 10, app = AppSettings()),
            onBack = {},
            onToggle = {},
            onStepPrep = {},
            onSetPrep = {},
            onToggleKeepScreenOn = {},
            onSetTheme = {},
            onShowAbout = {},
        )
    }
}
