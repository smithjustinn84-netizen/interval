package dev.justinsmith.interval.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.justinsmith.interval.model.CueSettings
import dev.justinsmith.interval.model.ThemeMode
import dev.justinsmith.interval.platform.HealthStatus
import dev.justinsmith.interval.platform.healthStoreName
import dev.justinsmith.interval.settings.resources.Res
import dev.justinsmith.interval.settings.resources.effort_easy
import dev.justinsmith.interval.settings.resources.effort_hard
import dev.justinsmith.interval.settings.resources.effort_max
import dev.justinsmith.interval.settings.resources.effort_steady
import dev.justinsmith.interval.settings.resources.settings_about_app
import dev.justinsmith.interval.settings.resources.settings_about_app_hint
import dev.justinsmith.interval.settings.resources.settings_appearance
import dev.justinsmith.interval.settings.resources.settings_calories
import dev.justinsmith.interval.settings.resources.settings_cues
import dev.justinsmith.interval.settings.resources.settings_cues_all_off
import dev.justinsmith.interval.settings.resources.settings_data
import dev.justinsmith.interval.settings.resources.settings_effort
import dev.justinsmith.interval.settings.resources.settings_effort_hint
import dev.justinsmith.interval.settings.resources.settings_export
import dev.justinsmith.interval.settings.resources.settings_export_action
import dev.justinsmith.interval.settings.resources.settings_export_hint
import dev.justinsmith.interval.settings.resources.settings_halfway
import dev.justinsmith.interval.settings.resources.settings_halfway_hint
import dev.justinsmith.interval.settings.resources.settings_halfway_needs_cue
import dev.justinsmith.interval.settings.resources.settings_health
import dev.justinsmith.interval.settings.resources.settings_health_install_hint
import dev.justinsmith.interval.settings.resources.settings_health_off_hint
import dev.justinsmith.interval.settings.resources.settings_health_on_hint
import dev.justinsmith.interval.settings.resources.settings_import
import dev.justinsmith.interval.settings.resources.settings_import_action
import dev.justinsmith.interval.settings.resources.settings_import_hint
import dev.justinsmith.interval.settings.resources.settings_keep_screen_on
import dev.justinsmith.interval.settings.resources.settings_keep_screen_on_hint
import dev.justinsmith.interval.settings.resources.settings_sound
import dev.justinsmith.interval.settings.resources.settings_sound_hint
import dev.justinsmith.interval.settings.resources.settings_theme
import dev.justinsmith.interval.settings.resources.settings_theme_hint
import dev.justinsmith.interval.settings.resources.settings_value_off
import dev.justinsmith.interval.settings.resources.settings_vibrate
import dev.justinsmith.interval.settings.resources.settings_vibrate_hint
import dev.justinsmith.interval.settings.resources.settings_voice
import dev.justinsmith.interval.settings.resources.settings_voice_hint
import dev.justinsmith.interval.settings.resources.settings_weight
import dev.justinsmith.interval.settings.resources.settings_weight_edit
import dev.justinsmith.interval.settings.resources.settings_weight_hint
import dev.justinsmith.interval.settings.resources.settings_weight_unset
import dev.justinsmith.interval.settings.resources.settings_weight_value
import dev.justinsmith.interval.settings.resources.settings_workout
import dev.justinsmith.interval.settings.resources.theme_dark
import dev.justinsmith.interval.settings.resources.theme_light
import dev.justinsmith.interval.settings.resources.theme_system
import dev.justinsmith.interval.timer.ConfigField
import dev.justinsmith.interval.timer.Effort
import dev.justinsmith.interval.timer.WorkoutEngine
import dev.justinsmith.interval.ui.FieldRow
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.LinkRow
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.SegmentedPicker
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.ToggleRow
import dev.justinsmith.interval.ui.ValueRow
import dev.justinsmith.interval.ui.enterExpanding
import dev.justinsmith.interval.ui.exitShrinking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** One group of settings, its label over its card, keyed for the grid. */
internal class SettingsGroup(val key: String, val content: @Composable () -> Unit)

/** What the health store's row does: connect, disconnect, or fetch the store where it is missing. */
internal class HealthActions(val onConnect: () -> Unit, val onDisconnect: () -> Unit, val onInstall: () -> Unit)

/**
 * The groups in order: cues, the workout, calories, the health store where there is one, appearance, your data
 * and, at the foot with no label, the way to About.
 */
@Suppress("detekt.LongParameterList") // One callback per setting, as the screen takes them.
internal fun settingsGroups(
    state: SettingsUiState,
    onToggle: (CueToggle) -> Unit,
    onEditPrep: () -> Unit,
    onToggleKeepScreenOn: () -> Unit,
    onEditWeight: () -> Unit,
    onSetEffort: (Effort) -> Unit,
    onSetTheme: (ThemeMode) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    health: HealthActions,
    onShowAbout: () -> Unit,
): List<SettingsGroup> = listOfNotNull(
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
    // After the calories its weight feeds, and only where the platform has a store.
    SettingsGroup("health") {
        GroupLabel(stringResource(Res.string.settings_health))
        ListCard { HealthRow(state.health, health) }
    }.takeIf { state.health != HealthStatus.Unavailable },
    SettingsGroup("appearance") {
        GroupLabel(stringResource(Res.string.settings_appearance))
        ListCard {
            ChoiceRow(
                stringResource(Res.string.settings_theme),
                stringResource(Res.string.settings_theme_hint),
                ThemeMode.entries,
                state.app.theme,
                label = { stringResource(it.labelRes) },
                onSelect = onSetTheme,
            )
        }
    },
    SettingsGroup("data") {
        GroupLabel(stringResource(Res.string.settings_data))
        ListCard {
            LinkRow(
                stringResource(Res.string.settings_export),
                stringResource(Res.string.settings_export_hint),
                onClick = onExport,
                onClickLabel = stringResource(Res.string.settings_export_action),
            )
            RowDivider()
            LinkRow(
                stringResource(Res.string.settings_import),
                stringResource(Res.string.settings_import_hint),
                onClick = onImport,
                onClickLabel = stringResource(Res.string.settings_import_action),
            )
        }
    },
    // The page's foot rather than a group of one: no label, set apart from the settings above it.
    SettingsGroup("about") {
        ListCard(Modifier.padding(top = Spacing.space24)) {
            LinkRow(
                stringResource(Res.string.settings_about_app),
                stringResource(Res.string.settings_about_app_hint),
                onClick = onShowAbout,
            )
        }
    },
)

/**
 * The health store's row, a group of its own: a switch while the store is there, which opens its permission
 * sheet or gives the permissions back; a link to fetch it while it is missing.
 */
@Composable
private fun HealthRow(status: HealthStatus, actions: HealthActions, modifier: Modifier = Modifier) {
    when (status) {
        HealthStatus.NotInstalled -> LinkRow(
            stringResource(healthStoreName),
            stringResource(Res.string.settings_health_install_hint),
            onClick = actions.onInstall,
            modifier = modifier,
        )

        else -> {
            val connected = status == HealthStatus.Connected
            val hint = if (connected) Res.string.settings_health_on_hint else Res.string.settings_health_off_hint
            ToggleRow(
                stringResource(healthStoreName),
                stringResource(hint),
                checked = connected,
                onToggle = if (connected) actions.onDisconnect else actions.onConnect,
                modifier = modifier,
            )
        }
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

/**
 * A setting picked from a few, as a card's row: its [title] and [hint] over a [SegmentedPicker] of the [options].
 * The picker's group takes the title as its name, so a screen reader says what the options are options of.
 * [check] off where the options are too many for the card's row to keep both their words and the check.
 */
@Composable
private fun <T> ChoiceRow(
    title: String,
    hint: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    check: Boolean = true,
) {
    Column(
        modifier.padding(horizontal = Spacing.space16, vertical = Spacing.space12),
        verticalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        Column(Modifier.semantics(mergeDescendants = true) {}) {
            Text(title, style = IntervalTheme.type.label, color = IntervalTheme.palette.text)
            Text(hint, style = IntervalTheme.type.meta, color = IntervalTheme.palette.textDim)
        }
        SegmentedPicker(
            options,
            selected,
            label,
            onSelect,
            Modifier.semantics { contentDescription = title },
            check = check,
        )
    }
}

/** The calorie estimates' inputs, in one card: the body weight, which opens its sheet, and the effort of the work. */
@Composable
private fun CaloriesCard(
    state: SettingsUiState,
    onEditWeight: () -> Unit,
    onSetEffort: (Effort) -> Unit,
    modifier: Modifier = Modifier,
) {
    val weight = state.weight
    ListCard(modifier) {
        ValueRow(
            stringResource(Res.string.settings_weight),
            stringResource(
                if (weight == null) Res.string.settings_weight_unset else Res.string.settings_weight_hint,
            ),
            value = if (weight == null) {
                stringResource(Res.string.settings_value_off)
            } else {
                stringResource(Res.string.settings_weight_value, weight, stringResource(state.body.unit.labelRes))
            },
            onClick = onEditWeight,
            onClickLabel = stringResource(Res.string.settings_weight_edit),
            dim = weight == null,
        )
        RowDivider()
        ChoiceRow(
            stringResource(Res.string.settings_effort),
            stringResource(Res.string.settings_effort_hint),
            Effort.entries,
            state.body.effort,
            label = { stringResource(it.labelRes) },
            onSelect = onSetEffort,
            // Four options inside the card: the chosen one's card says which, and "Hard" keeps its letters.
            check = false,
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
