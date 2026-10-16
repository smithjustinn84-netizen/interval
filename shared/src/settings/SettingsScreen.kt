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
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.common_back
import dev.justinsmith.interval.resources.settings_appearance
import dev.justinsmith.interval.resources.settings_close_cd
import dev.justinsmith.interval.resources.settings_cues
import dev.justinsmith.interval.resources.settings_halfway
import dev.justinsmith.interval.resources.settings_halfway_hint
import dev.justinsmith.interval.resources.settings_keep_screen_on
import dev.justinsmith.interval.resources.settings_keep_screen_on_hint
import dev.justinsmith.interval.resources.settings_sound
import dev.justinsmith.interval.resources.settings_sound_hint
import dev.justinsmith.interval.resources.settings_title
import dev.justinsmith.interval.resources.settings_vibrate
import dev.justinsmith.interval.resources.settings_vibrate_hint
import dev.justinsmith.interval.resources.settings_voice
import dev.justinsmith.interval.resources.settings_voice_hint
import dev.justinsmith.interval.resources.settings_workout
import dev.justinsmith.interval.resources.theme_dark
import dev.justinsmith.interval.resources.theme_light
import dev.justinsmith.interval.resources.theme_system
import dev.justinsmith.interval.timer.ConfigField
import dev.justinsmith.interval.timer.WorkoutEngine
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
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
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
                stringResource(Res.string.settings_title),
                navigation = {
                    if (!inPane) IconAction(IntervalIcons.Back, stringResource(Res.string.common_back), onBack)
                },
                actions = {
                    if (inPane) IconAction(IntervalIcons.Close, stringResource(Res.string.settings_close_cd), onBack)
                },
            )
            Column(Modifier.padding(start = Spacing.space16, end = Spacing.space16, bottom = Spacing.space24)) {
                GroupLabel(stringResource(Res.string.settings_cues))
                ListCard {
                    ToggleRow(
                        stringResource(Res.string.settings_sound),
                        stringResource(Res.string.settings_sound_hint),
                        state.cues.sound,
                        onToggle = { onToggle(CueToggle.Sound) },
                    )
                    RowDivider()
                    ToggleRow(
                        stringResource(Res.string.settings_voice),
                        stringResource(Res.string.settings_voice_hint),
                        state.cues.voice,
                        onToggle = { onToggle(CueToggle.Voice) },
                    )
                    RowDivider()
                    ToggleRow(
                        stringResource(Res.string.settings_halfway),
                        stringResource(Res.string.settings_halfway_hint, WorkoutEngine.HALFWAY_MIN_SECONDS),
                        state.cues.halfway,
                        onToggle = { onToggle(CueToggle.Halfway) },
                    )
                    RowDivider()
                    ToggleRow(
                        stringResource(Res.string.settings_vibrate),
                        stringResource(Res.string.settings_vibrate_hint),
                        state.cues.vibrate,
                        onToggle = { onToggle(CueToggle.Vibrate) },
                    )
                }
                GroupLabel(stringResource(Res.string.settings_workout))
                ListCard {
                    FieldRow(ConfigField.Prep, state.prepSeconds, onClick = { editingPrep = true }, compact = true)
                    RowDivider()
                    ToggleRow(
                        stringResource(Res.string.settings_keep_screen_on),
                        stringResource(Res.string.settings_keep_screen_on_hint),
                        state.app.keepScreenOn,
                        onToggle = onToggleKeepScreenOn,
                    )
                }
                GroupLabel(stringResource(Res.string.settings_appearance))
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
                Text(stringResource(mode.labelRes), style = IntervalTheme.type.buttonLabel, color = palette.text)
            }
        }
    }
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
        )
    }
}
