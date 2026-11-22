package dev.justinsmith.interval.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.justinsmith.interval.model.CueSettings
import dev.justinsmith.interval.model.ThemeMode
import dev.justinsmith.interval.platform.AppIcon
import dev.justinsmith.interval.platform.DEFAULT_MAX_BPM
import dev.justinsmith.interval.platform.HealthStatus
import dev.justinsmith.interval.platform.LOW_SENSOR_BATTERY
import dev.justinsmith.interval.platform.NearbySensor
import dev.justinsmith.interval.platform.SensorStatus
import dev.justinsmith.interval.platform.healthStoreName
import dev.justinsmith.interval.settings.resources.Res
import dev.justinsmith.interval.settings.resources.effort_easy
import dev.justinsmith.interval.settings.resources.effort_hard
import dev.justinsmith.interval.settings.resources.effort_max
import dev.justinsmith.interval.settings.resources.effort_steady
import dev.justinsmith.interval.settings.resources.settings_about_app
import dev.justinsmith.interval.settings.resources.settings_about_app_hint
import dev.justinsmith.interval.settings.resources.settings_app_icon
import dev.justinsmith.interval.settings.resources.settings_app_icon_hint
import dev.justinsmith.interval.settings.resources.settings_app_icon_kettlebell
import dev.justinsmith.interval.settings.resources.settings_app_icon_ring
import dev.justinsmith.interval.settings.resources.settings_appearance
import dev.justinsmith.interval.settings.resources.settings_calories
import dev.justinsmith.interval.settings.resources.settings_clear_log
import dev.justinsmith.interval.settings.resources.settings_clear_log_hint
import dev.justinsmith.interval.settings.resources.settings_cues
import dev.justinsmith.interval.settings.resources.settings_cues_all_off
import dev.justinsmith.interval.settings.resources.settings_data
import dev.justinsmith.interval.settings.resources.settings_effort
import dev.justinsmith.interval.settings.resources.settings_effort_hint
import dev.justinsmith.interval.settings.resources.settings_export
import dev.justinsmith.interval.settings.resources.settings_export_action
import dev.justinsmith.interval.settings.resources.settings_export_hint
import dev.justinsmith.interval.settings.resources.settings_goal
import dev.justinsmith.interval.settings.resources.settings_goal_hint
import dev.justinsmith.interval.settings.resources.settings_halfway
import dev.justinsmith.interval.settings.resources.settings_halfway_hint
import dev.justinsmith.interval.settings.resources.settings_halfway_needs_cue
import dev.justinsmith.interval.settings.resources.settings_health
import dev.justinsmith.interval.settings.resources.settings_health_install_hint
import dev.justinsmith.interval.settings.resources.settings_health_off_hint
import dev.justinsmith.interval.settings.resources.settings_health_on_hint
import dev.justinsmith.interval.settings.resources.settings_hr_alerts
import dev.justinsmith.interval.settings.resources.settings_hr_alerts_hint
import dev.justinsmith.interval.settings.resources.settings_import
import dev.justinsmith.interval.settings.resources.settings_import_action
import dev.justinsmith.interval.settings.resources.settings_import_hint
import dev.justinsmith.interval.settings.resources.settings_keep_screen_on
import dev.justinsmith.interval.settings.resources.settings_keep_screen_on_hint
import dev.justinsmith.interval.settings.resources.settings_max_hr
import dev.justinsmith.interval.settings.resources.settings_max_hr_edit
import dev.justinsmith.interval.settings.resources.settings_max_hr_hint
import dev.justinsmith.interval.settings.resources.settings_max_hr_hint_default
import dev.justinsmith.interval.settings.resources.settings_max_hr_value
import dev.justinsmith.interval.settings.resources.settings_plan
import dev.justinsmith.interval.settings.resources.settings_plan_open
import dev.justinsmith.interval.settings.resources.settings_reminder
import dev.justinsmith.interval.settings.resources.settings_reminder_hint
import dev.justinsmith.interval.settings.resources.settings_resting_hr
import dev.justinsmith.interval.settings.resources.settings_resting_hr_edit
import dev.justinsmith.interval.settings.resources.settings_resting_hr_hint
import dev.justinsmith.interval.settings.resources.settings_resting_hr_unset
import dev.justinsmith.interval.settings.resources.settings_sensor
import dev.justinsmith.interval.settings.resources.settings_sensor_battery
import dev.justinsmith.interval.settings.resources.settings_sensor_battery_low
import dev.justinsmith.interval.settings.resources.settings_sensor_forget
import dev.justinsmith.interval.settings.resources.settings_sensor_looking
import dev.justinsmith.interval.settings.resources.settings_sensor_pair
import dev.justinsmith.interval.settings.resources.settings_sensor_pair_hint
import dev.justinsmith.interval.settings.resources.settings_sensor_paired_hint
import dev.justinsmith.interval.settings.resources.settings_sensor_recording_hint
import dev.justinsmith.interval.settings.resources.settings_setup
import dev.justinsmith.interval.settings.resources.settings_setup_hint
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
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.SegmentedPicker
import dev.justinsmith.interval.ui.Sizes
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
 * The heart-rate sensor's row: how it stands, whether one is being looked for, and what pairing and forgetting do.
 * Unavailable, as it is by default, shows no row.
 */
class SensorRow(
    val status: SensorStatus = SensorStatus.Unavailable,
    val looking: Boolean = false,
    val onPair: () -> Unit = {},
    val onForget: () -> Unit = {},
    /** The sensor's battery as last heard, a percentage; null until it has said. */
    val battery: Int? = null,
    /** The sensors found nearby when there was more than one, for [onChoose] to take one of, or none with null. */
    val choices: List<NearbySensor> = emptyList(),
    val onChoose: (NearbySensor?) -> Unit = {},
)

/**
 * The app icon's row: the [icon] worn on the Home Screen, what picking another does, and whether the platform
 * [closesApp] as it switches, which the screen asks about first. No icon, as it is by default, shows no row.
 */
class AppIconRow(
    val icon: AppIcon? = null,
    val closesApp: Boolean = false,
    val onPick: (AppIcon) -> Unit = {},
)

/**
 * The groups in order: cues, the workout, calories, the health store where there is one, the heart-rate sensor
 * where the platform has one, the plan's goal and reminder, appearance (the theme, and the app's icon where the
 * platform has more than one), your data and, at the foot with no label, the way to About.
 */
@Suppress("detekt.LongParameterList") // One callback per setting, as the screen takes them.
internal fun settingsGroups(
    state: SettingsUiState,
    onToggle: (CueToggle) -> Unit,
    onEditPrep: () -> Unit,
    onToggleKeepScreenOn: () -> Unit,
    onEditWeight: () -> Unit,
    onEditMaxHeartRate: () -> Unit,
    onEditRestingHeartRate: () -> Unit,
    onSetEffort: (Effort) -> Unit,
    onSetTheme: (ThemeMode) -> Unit,
    appIcon: AppIcon?,
    onSetAppIcon: (AppIcon) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onClearLog: () -> Unit,
    health: HealthActions,
    sensor: SensorRow,
    onShowPlan: () -> Unit,
    onShowSetup: () -> Unit,
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
        CaloriesCard(state, onEditWeight, onEditMaxHeartRate, onEditRestingHeartRate, onSetEffort)
    },
    // After the calories its weight feeds, and only where the platform has a store.
    SettingsGroup("health") {
        GroupLabel(stringResource(Res.string.settings_health))
        ListCard { HealthRow(state.health, health) }
    }.takeIf { state.health != HealthStatus.Unavailable },
    sensorGroup(sensor),
    SettingsGroup("plan") {
        GroupLabel(stringResource(Res.string.settings_plan))
        PlanCard(state.app.weeklyGoal, state.reminderTime, onShowPlan, onShowSetup)
    },
    SettingsGroup("appearance") {
        GroupLabel(stringResource(Res.string.settings_appearance))
        AppearanceCard(state.app.theme, onSetTheme, appIcon, onSetAppIcon)
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
            RowDivider()
            // Beside export, the one way back: the screen asks before it clears.
            LinkRow(
                stringResource(Res.string.settings_clear_log),
                stringResource(Res.string.settings_clear_log_hint),
                onClick = onClearLog,
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

/** The heart-rate sensor's group, where the platform can reach one. */
private fun sensorGroup(sensor: SensorRow): SettingsGroup? = SettingsGroup("sensor") {
    GroupLabel(stringResource(Res.string.settings_sensor))
    ListCard { SensorRowContent(sensor) }
}.takeIf { sensor.status != SensorStatus.Unavailable }

/**
 * The sensor's row: with none paired, the way to pair the one nearby, saying so while it looks; with one, its
 * name and what it is doing beside Forget, a button of its own rather than a tap on the row, which reads as a
 * way somewhere.
 */
@Composable
private fun SensorRowContent(sensor: SensorRow, modifier: Modifier = Modifier) {
    when (val status = sensor.status) {
        is SensorStatus.Paired -> PairedSensorRow(
            status.name,
            when (val battery = sensor.battery) {
                null -> stringResource(Res.string.settings_sensor_paired_hint)
                in 0 until LOW_SENSOR_BATTERY -> stringResource(Res.string.settings_sensor_battery_low, battery)
                else -> stringResource(Res.string.settings_sensor_battery, battery)
            },
            sensor.onForget,
            modifier,
        )

        is SensorStatus.Recording -> PairedSensorRow(
            status.name,
            stringResource(Res.string.settings_sensor_recording_hint),
            sensor.onForget,
            modifier,
        )

        else -> LinkRow(
            stringResource(Res.string.settings_sensor_pair),
            stringResource(
                if (sensor.looking) Res.string.settings_sensor_looking else Res.string.settings_sensor_pair_hint,
            ),
            onClick = sensor.onPair,
            modifier = modifier,
        )
    }
}

/** A sensor paired: its name over how it stands, and Forget at the end. */
@Composable
private fun PairedSensorRow(name: String, hint: String, onForget: () -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.listRow)
            .padding(start = Spacing.space16, end = Spacing.space8, top = Spacing.space8, bottom = Spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            Text(name, style = IntervalTheme.type.label, color = palette.text)
            Text(hint, style = IntervalTheme.type.meta, color = palette.textDim)
        }
        QuietTextButton(stringResource(Res.string.settings_sensor_forget), onForget, destructive = true)
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
    RowDivider()
    ToggleRow(
        stringResource(Res.string.settings_hr_alerts),
        stringResource(Res.string.settings_hr_alerts_hint),
        cues.heartRateAlerts,
        onToggle = { onToggle(CueToggle.HeartRateAlerts) },
    )
}

/**
 * The plan's two settings as they stand, the weekly [goal] and the [reminderTime], each "Off" while it has none.
 * Both are changed on Plan, where they sit with what they are for, so either row leads there. Under them, the way
 * back to Set up, whose four answers build the week again.
 */
@Composable
private fun PlanCard(
    goal: Int?,
    reminderTime: String?,
    onShowPlan: () -> Unit,
    onShowSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val off = stringResource(Res.string.settings_value_off)
    val open = stringResource(Res.string.settings_plan_open)
    ListCard(modifier) {
        ValueRow(
            stringResource(Res.string.settings_goal),
            stringResource(Res.string.settings_goal_hint),
            value = goal?.toString() ?: off,
            onClick = onShowPlan,
            onClickLabel = open,
            dim = goal == null,
        )
        RowDivider()
        ValueRow(
            stringResource(Res.string.settings_reminder),
            stringResource(Res.string.settings_reminder_hint),
            value = reminderTime ?: off,
            onClick = onShowPlan,
            onClickLabel = open,
            dim = reminderTime == null,
        )
        RowDivider()
        LinkRow(
            stringResource(Res.string.settings_setup),
            stringResource(Res.string.settings_setup_hint),
            onClick = onShowSetup,
        )
    }
}

/** How the app looks: its theme, and under it the icon it wears where the platform has more than one. */
@Composable
private fun AppearanceCard(
    theme: ThemeMode,
    onSetTheme: (ThemeMode) -> Unit,
    appIcon: AppIcon?,
    onSetAppIcon: (AppIcon) -> Unit,
    modifier: Modifier = Modifier,
) {
    ListCard(modifier) {
        ChoiceRow(
            stringResource(Res.string.settings_theme),
            stringResource(Res.string.settings_theme_hint),
            ThemeMode.entries,
            theme,
            label = { stringResource(it.labelRes) },
            onSelect = onSetTheme,
        )
        if (appIcon != null) {
            RowDivider()
            ChoiceRow(
                stringResource(Res.string.settings_app_icon),
                stringResource(Res.string.settings_app_icon_hint),
                AppIcon.entries,
                appIcon,
                label = { stringResource(it.labelRes) },
                onSelect = onSetAppIcon,
            )
        }
    }
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
    onEditMaxHeartRate: () -> Unit,
    onEditRestingHeartRate: () -> Unit,
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
        val maxHr = state.body.maxHeartRate
        ValueRow(
            stringResource(Res.string.settings_max_hr),
            // The default is said in the line, not the value, which a phone has no room to widen.
            if (maxHr == null) {
                stringResource(Res.string.settings_max_hr_hint_default, DEFAULT_MAX_BPM)
            } else {
                stringResource(Res.string.settings_max_hr_hint)
            },
            value = stringResource(Res.string.settings_max_hr_value, maxHr ?: DEFAULT_MAX_BPM),
            onClick = onEditMaxHeartRate,
            onClickLabel = stringResource(Res.string.settings_max_hr_edit),
            dim = maxHr == null,
        )
        RowDivider()
        val resting = state.body.restingHeartRate
        ValueRow(
            stringResource(Res.string.settings_resting_hr),
            stringResource(Res.string.settings_resting_hr_hint),
            value = if (resting == null) {
                stringResource(Res.string.settings_resting_hr_unset)
            } else {
                stringResource(Res.string.settings_max_hr_value, resting)
            },
            onClick = onEditRestingHeartRate,
            onClickLabel = stringResource(Res.string.settings_resting_hr_edit),
            dim = resting == null,
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
private val AppIcon.labelRes: StringResource
    get() = when (this) {
        AppIcon.Kettlebell -> Res.string.settings_app_icon_kettlebell
        AppIcon.Ring -> Res.string.settings_app_icon_ring
    }

private val ThemeMode.labelRes: StringResource
    get() = when (this) {
        ThemeMode.System -> Res.string.theme_system
        ThemeMode.Light -> Res.string.theme_light
        ThemeMode.Dark -> Res.string.theme_dark
    }

@PreviewFormFactors
@Composable
private fun SensorGroupPreview() {
    IntervalTheme {
        Column(Modifier.padding(Spacing.space16), verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
            listOf(
                SensorRow(SensorStatus.Unpaired),
                SensorRow(SensorStatus.Unpaired, looking = true),
                SensorRow(SensorStatus.Paired("Polar Sense 1A2B3C4D")),
                SensorRow(SensorStatus.Paired("Polar H10 1A2B3C4D"), battery = 82),
                SensorRow(SensorStatus.Paired("Polar H10 1A2B3C4D"), battery = 14),
                SensorRow(SensorStatus.Recording("Polar H10 1A2B3C4D", sinceEpochMillis = 0)),
            ).forEach { sensor -> ListCard { SensorRowContent(sensor) } }
        }
    }
}
