package dev.justinsmith.interval.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.justinsmith.interval.data.AppSettings
import dev.justinsmith.interval.data.CueSettings
import dev.justinsmith.interval.data.ThemeMode
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.settings_about
import dev.justinsmith.interval.resources.settings_about_app
import dev.justinsmith.interval.resources.settings_about_app_hint
import dev.justinsmith.interval.resources.settings_appearance
import dev.justinsmith.interval.resources.settings_close_cd
import dev.justinsmith.interval.resources.settings_cues
import dev.justinsmith.interval.resources.settings_cues_all_off
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
import dev.justinsmith.interval.resources.settings_workout
import dev.justinsmith.interval.resources.theme_dark
import dev.justinsmith.interval.resources.theme_light
import dev.justinsmith.interval.resources.theme_system
import dev.justinsmith.interval.timer.ConfigField
import dev.justinsmith.interval.timer.WorkoutEngine
import dev.justinsmith.interval.ui.BackAction
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.FieldRow
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.LinkRow
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.Radius
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.ScrollPage
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.ToggleRow
import dev.justinsmith.interval.ui.ValueSheet
import dev.justinsmith.interval.ui.settleSpring
import dev.justinsmith.interval.ui.tint
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
) {
    var editingPrep by rememberSaveable { mutableStateOf(false) }
    ScrollPage(
        stringResource(Res.string.settings_title),
        modifier = modifier,
        navigation = { if (!inPane) BackAction(onBack) },
        actions = {
            if (inPane) IconAction(IntervalIcons.Close, stringResource(Res.string.settings_close_cd), onBack)
        },
    ) {
        GroupLabel(stringResource(Res.string.settings_cues))
        CuesCard(state.cues, onToggle)
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
        GroupLabel(stringResource(Res.string.settings_about))
        ListCard {
            LinkRow(
                stringResource(Res.string.settings_about_app),
                stringResource(Res.string.settings_about_app_hint),
                onClick = onShowAbout,
            )
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
            enter = fadeIn(tween(Motion.STATE_MILLIS)) + expandVertically(tween(Motion.STATE_MILLIS)),
            exit = fadeOut(tween(Motion.STATE_MILLIS)) + shrinkVertically(tween(Motion.STATE_MILLIS)),
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

/**
 * System / Light / Dark as a segmented control: the chosen option is a white card on the variant
 * ground. The card slides to a newly chosen option, its check growing in once it has settled under it.
 */
@Composable
private fun ThemePicker(selected: ThemeMode, onSelect: (ThemeMode) -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val haptics = LocalHapticFeedback.current
    val modes = ThemeMode.entries
    // The card is drawn, not laid out, at the option's index along the row: it needs no size of its own.
    val slide by animateFloatAsState(modes.indexOf(selected).toFloat(), settleSpring(), label = "indicator")
    Row(
        modifier
            .fillMaxWidth()
            .clip(Corners.radius18)
            .background(palette.surfaceVariant)
            .padding(Spacing.space4)
            .drawBehind {
                val gap = Spacing.space4.toPx()
                val width = (size.width - gap * (modes.size - 1)) / modes.size
                drawRoundRect(
                    palette.surface,
                    topLeft = Offset((width + gap) * slide, 0f),
                    size = Size(width, size.height),
                    cornerRadius = CornerRadius(Radius.radius12.toPx()),
                )
            }
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.space4),
    ) {
        modes.forEach { mode ->
            val isSelected = mode == selected
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = Sizes.segment)
                    .clip(Corners.radius12)
                    .selectable(selected = isSelected, role = Role.RadioButton) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(mode)
                    },
                horizontalArrangement = Arrangement.spacedBy(Spacing.space8, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedVisibility(
                    isSelected,
                    enter = fadeIn(tween(Motion.STATE_MILLIS, Motion.STATE_MILLIS)) +
                        expandHorizontally(tween(Motion.STATE_MILLIS, Motion.STATE_MILLIS)),
                    exit = fadeOut(tween(Motion.STATE_MILLIS)) + shrinkHorizontally(tween(Motion.STATE_MILLIS)),
                ) {
                    Icon(
                        IntervalIcons.Check,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(Sizes.icon),
                    )
                }
                Text(stringResource(mode.labelRes), style = IntervalTheme.type.label, color = palette.text)
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
            onShowAbout = {},
        )
    }
}
