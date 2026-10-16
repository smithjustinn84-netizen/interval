package dev.justinsmith.interval.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.justinsmith.interval.data.AppSettings
import dev.justinsmith.interval.data.CueSettings
import dev.justinsmith.interval.data.ThemeMode
import dev.justinsmith.interval.timer.ConfigField
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.FieldRow
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.TitleBar
import dev.justinsmith.interval.ui.ToggleRow
import dev.justinsmith.interval.ui.ValueSheet
import dev.justinsmith.interval.ui.tint
import org.koin.compose.currentKoinScope

/** Resolves the screen's ViewModel and binds it to [SettingsScreen]. */
@Composable
fun SettingsRoute(onBack: () -> Unit, modifier: Modifier = Modifier, inPane: Boolean = false) {
    val scope = currentKoinScope()
    val viewModel = viewModel { SettingsViewModel(scope.get()) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        onBack = onBack,
        onToggle = viewModel::toggle,
        onStepPrep = viewModel::stepPrep,
        onSetPrep = viewModel::setPrep,
        onToggleKeepScreenOn = viewModel::toggleKeepScreenOn,
        onSetTheme = viewModel::setTheme,
        modifier = modifier,
        inPane = inPane,
    )
}

/**
 * The set-once preferences, out of the way of the workout itself. [inPane] when it sits beside the
 * Timer tab on a wide window: there it closes rather than going back.
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
    modifier: Modifier = Modifier,
    inPane: Boolean = false,
) {
    var editingPrep by rememberSaveable { mutableStateOf(false) }
    Box(modifier.fillMaxSize().background(IntervalTheme.palette.background), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = Sizes.content)
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            TitleBar(
                "Settings",
                navigation = { if (!inPane) IconAction(IntervalIcons.Back, "Back", onBack) },
                actions = { if (inPane) IconAction(IntervalIcons.Close, "Close settings", onBack) },
            )
            Column(Modifier.padding(start = Spacing.space16, end = Spacing.space16, bottom = Spacing.space24)) {
                GroupLabel("Cues")
                ListCard {
                    ToggleRow(
                        "Sound cues",
                        "Beeps at each phase change",
                        state.cues.sound,
                        onToggle = { onToggle(CueToggle.Sound) },
                    )
                    RowDivider()
                    ToggleRow(
                        "Voice",
                        "Spoken countdown and phase names",
                        state.cues.voice,
                        onToggle = { onToggle(CueToggle.Voice) },
                    )
                    RowDivider()
                    ToggleRow(
                        "Halfway alert",
                        "A cue in the middle of intervals over 30s",
                        state.cues.halfway,
                        onToggle = { onToggle(CueToggle.Halfway) },
                    )
                    RowDivider()
                    ToggleRow(
                        "Vibrate",
                        "Buzz at each phase change",
                        state.cues.vibrate,
                        onToggle = { onToggle(CueToggle.Vibrate) },
                    )
                }
                GroupLabel("Workout")
                ListCard {
                    FieldRow(ConfigField.Prep, state.prepSeconds, onClick = { editingPrep = true }, compact = true)
                    RowDivider()
                    ToggleRow(
                        "Keep screen on",
                        "While a workout is running",
                        state.app.keepScreenOn,
                        onToggle = onToggleKeepScreenOn,
                    )
                }
                GroupLabel("Appearance")
                ThemePicker(state.app.theme, onSetTheme)
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
}

/** System / Light / Dark as a segmented control: the chosen option is a white card on the variant ground. */
@Composable
private fun ThemePicker(selected: ThemeMode, onSelect: (ThemeMode) -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Row(
        modifier
            .fillMaxWidth()
            .clip(Corners.radius14)
            .background(palette.surfaceVariant)
            .padding(Spacing.space4)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.space4),
    ) {
        ThemeMode.entries.forEach { mode ->
            val isSelected = mode == selected
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = Sizes.toggleRow - Spacing.space12)
                    .clip(Corners.radius10)
                    .background(if (isSelected) palette.surface else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(mode) }),
                horizontalArrangement = Arrangement.spacedBy(Spacing.space6, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isSelected) {
                    Icon(
                        IntervalIcons.Check,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(Sizes.icon),
                    )
                }
                Text(mode.name, style = IntervalTheme.type.buttonLabel, color = palette.text)
            }
        }
    }
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
        )
    }
}
