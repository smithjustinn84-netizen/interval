package dev.justinsmith.interval.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.justinsmith.interval.model.AppSettings
import dev.justinsmith.interval.model.CueSettings
import dev.justinsmith.interval.model.ThemeMode
import dev.justinsmith.interval.platform.AppIcon
import dev.justinsmith.interval.platform.AppIconSwitcher
import dev.justinsmith.interval.platform.HealthStatus
import dev.justinsmith.interval.platform.HealthStore
import dev.justinsmith.interval.platform.NearbySensor
import dev.justinsmith.interval.platform.SensorFailure
import dev.justinsmith.interval.platform.SensorStatus
import dev.justinsmith.interval.platform.rememberBluetoothAccess
import dev.justinsmith.interval.platform.rememberFileOpener
import dev.justinsmith.interval.platform.rememberHealthConnector
import dev.justinsmith.interval.settings.resources.Res
import dev.justinsmith.interval.settings.resources.import_failed
import dev.justinsmith.interval.settings.resources.notice_sensor_failed
import dev.justinsmith.interval.settings.resources.notice_sensor_no_access
import dev.justinsmith.interval.settings.resources.notice_sensor_none_nearby
import dev.justinsmith.interval.settings.resources.settings_app_icon_confirm
import dev.justinsmith.interval.settings.resources.settings_app_icon_confirm_text
import dev.justinsmith.interval.settings.resources.settings_app_icon_confirm_title
import dev.justinsmith.interval.settings.resources.settings_cancel
import dev.justinsmith.interval.settings.resources.settings_clear_log_confirm
import dev.justinsmith.interval.settings.resources.settings_clear_log_text
import dev.justinsmith.interval.settings.resources.settings_clear_log_title
import dev.justinsmith.interval.settings.resources.settings_close_cd
import dev.justinsmith.interval.settings.resources.settings_sensor_choose_note
import dev.justinsmith.interval.settings.resources.settings_sensor_choose_title
import dev.justinsmith.interval.settings.resources.settings_sensor_forget
import dev.justinsmith.interval.settings.resources.settings_sensor_forget_text
import dev.justinsmith.interval.settings.resources.settings_sensor_forget_title
import dev.justinsmith.interval.settings.resources.settings_title
import dev.justinsmith.interval.timer.ConfigField
import dev.justinsmith.interval.timer.Effort
import dev.justinsmith.interval.timer.WeightUnit
import dev.justinsmith.interval.ui.BackAction
import dev.justinsmith.interval.ui.ConfirmDialog
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalSheet
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.LinkRow
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.LocalNotices
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.ScrollPage
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.TabGrid
import dev.justinsmith.interval.ui.ValueSheet
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** Resolves the screen's ViewModel and binds it to [SettingsScreen]. */
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onShowAbout: () -> Unit,
    onShowPlan: () -> Unit,
    modifier: Modifier = Modifier,
    inPane: Boolean = false,
    onShowSetup: () -> Unit = {},
) {
    val viewModel = koinViewModel<SettingsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val importing by viewModel.import.collectAsStateWithLifecycle()
    val pickFile = rememberFileOpener(viewModel::reviewImport)
    val health = rememberHealthConnector(koinInject<HealthStore>())
    // Read again on each opening: the permission may have been taken back in the store's own settings.
    LaunchedEffect(Unit) { viewModel.refreshHealth() }
    val sensors = koinViewModel<SensorSettingsViewModel>()
    val sensor by sensors.state.collectAsStateWithLifecycle()
    val bluetooth = rememberBluetoothAccess()
    val icons = koinInject<AppIconSwitcher>()
    val appIcon by icons.icon.collectAsStateWithLifecycle()
    val notices = LocalNotices.current
    val failures by rememberUpdatedState(SensorFailure.entries.associateWith { stringResource(it.words) })
    LaunchedEffect(sensors, notices) { sensors.failures.collect { notices.show(failures.getValue(it)) } }
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
        appIcon = AppIconRow(appIcon, icons.closesApp, icons::choose),
        inPane = inPane,
        onShowPlan = onShowPlan,
        onShowSetup = onShowSetup,
        onSetWeight = viewModel::setWeight,
        onSetWeightUnit = viewModel::setWeightUnit,
        onClearWeight = viewModel::clearWeight,
        onUseHealthWeight = viewModel::useHealthWeight,
        onSetMaxHeartRate = viewModel::setMaxHeartRate,
        onSetRestingHeartRate = viewModel::setRestingHeartRate,
        onSetEffort = viewModel::setEffort,
        onExport = viewModel::export,
        onExportSheet = viewModel::exportSheet,
        onClearLog = viewModel::clearLog,
        onConnectHealth = health::connect,
        onDisconnectHealth = viewModel::disconnectHealth,
        onInstallHealth = health::install,
        sensor = SensorRow(
            sensor.status,
            sensor.looking,
            battery = sensor.battery,
            // Looked for only once Bluetooth may be used; a refusal says so as a sensor out of reach does.
            onPair = {
                bluetooth.ask { granted ->
                    if (granted) sensors.pair() else notices.show(failures.getValue(SensorFailure.NoAccess))
                }
            },
            onForget = sensors::forget,
            choices = sensor.choices,
            onChoose = sensors::choose,
        ),
        importStep = importing,
        imports = ImportActions(
            // Providers label a .json file in more ways than one; what is in it decides, not its type.
            onPick = { pickFile(listOf("application/json", "application/octet-stream", "text/*")) },
            onConfirm = viewModel::confirmImport,
            onDismiss = viewModel::dismissImport,
        ),
    )
}

/**
 * The set-once preferences, out of the way of the workout itself, the plan's goal and reminder as they stand
 * ([onShowPlan] leads to where they are changed, [onShowSetup] to Set up), your data and the way to About.
 * [inPane] when it sits beside the Timer on a wide window: there it closes rather than going back. [appIcon] is
 * the icon the app wears on the Home Screen, picked under the theme where the platform has more than one.
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
    appIcon: AppIconRow = AppIconRow(),
    onShowPlan: () -> Unit = {},
    onShowSetup: () -> Unit = {},
    onSetWeight: (Int) -> Unit = {},
    onSetWeightUnit: (WeightUnit) -> Unit = {},
    onClearWeight: () -> Unit = {},
    onUseHealthWeight: () -> Unit = {},
    onSetMaxHeartRate: (Int?) -> Unit = {},
    onSetRestingHeartRate: (Int?) -> Unit = {},
    onSetEffort: (Effort) -> Unit = {},
    onExport: () -> Unit = {},
    onExportSheet: () -> Unit = {},
    onClearLog: () -> Unit = {},
    onConnectHealth: () -> Unit = {},
    onDisconnectHealth: () -> Unit = {},
    onInstallHealth: () -> Unit = {},
    sensor: SensorRow = SensorRow(),
    importStep: ImportStep? = null,
    imports: ImportActions = ImportActions(),
) {
    var editingPrep by rememberSaveable { mutableStateOf(false) }
    var editingWeight by rememberSaveable { mutableStateOf(false) }
    var editingMaxHeartRate by rememberSaveable { mutableStateOf(false) }
    var editingRestingHeartRate by rememberSaveable { mutableStateOf(false) }
    var clearingLog by rememberSaveable { mutableStateOf(false) }
    var forgettingSensor by rememberSaveable { mutableStateOf(false) }
    // Where the platform closes the app to change its icon, the pick waits here until that is agreed to.
    var changingIcon by rememberSaveable { mutableStateOf<AppIcon?>(null) }
    // A sensor with a class on it is forgotten only once that is confirmed: forgotten, the class can't be synced.
    val sensorRow = sensor.askingFirst { forgettingSensor = true }
    val pickIcon = { icon: AppIcon -> if (appIcon.closesApp) changingIcon = icon else appIcon.onPick(icon) }
    val groups = settingsGroups(
        state,
        onToggle,
        onEditPrep = { editingPrep = true },
        onToggleKeepScreenOn,
        onEditWeight = { editingWeight = true },
        onSetWeightUnit,
        onEditMaxHeartRate = { editingMaxHeartRate = true },
        onEditRestingHeartRate = { editingRestingHeartRate = true },
        onSetEffort,
        onSetTheme,
        AppIconRow(appIcon.icon, appIcon.closesApp, pickIcon),
        ExportActions(onExport, onExportSheet),
        imports.onPick,
        onClearLog = { clearingLog = true },
        HealthActions(onConnectHealth, onDisconnectHealth, onInstallHealth),
        sensorRow,
        onShowPlan,
        onShowSetup,
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
        ValueSheet(ConfigField.Prep, state.prepSeconds, null, onStepPrep, onSetPrep, onDismiss = {
            editingPrep = false
        })
    }
    ImportProgress(importStep, imports)
    if (clearingLog) ClearLogDialog(onClearLog) { clearingLog = false }
    if (forgettingSensor) ForgetSensorDialog(sensor.onForget) { forgettingSensor = false }
    changingIcon?.let { icon -> ChangeIconDialog(onChange = { appIcon.onPick(icon) }) { changingIcon = null } }
    if (sensor.choices.isNotEmpty()) SensorChoiceSheet(sensor.choices, sensor.onChoose)
    if (editingWeight) {
        WeightSheet(
            state.weight,
            state.body.unit,
            onSetWeight,
            onSetWeightUnit,
            onClearWeight,
            onDismiss = { editingWeight = false },
            state.healthWeight,
            onUseHealthWeight,
        )
    }
    if (editingMaxHeartRate) {
        MaxHeartRateSheet(state.body.maxHeartRate, onSetMaxHeartRate, onDismiss = { editingMaxHeartRate = false })
    }
    if (editingRestingHeartRate) {
        RestingHeartRateSheet(state.body.restingHeartRate, onSetRestingHeartRate) { editingRestingHeartRate = false }
    }
}

/** The row with its forgetting put behind [ask] while a class is on the sensor, and as it was otherwise. */
private fun SensorRow.askingFirst(ask: () -> Unit) = SensorRow(
    status,
    looking,
    onPair,
    onForget = { if (status is SensorStatus.Recording) ask() else onForget() },
    battery = battery,
    choices = choices,
    onChoose = onChoose,
)

/** Asks before the log is cleared: it can't be undone. */
@Composable
private fun ClearLogDialog(onClear: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = stringResource(Res.string.settings_clear_log_title),
        text = stringResource(Res.string.settings_clear_log_text),
        confirm = stringResource(Res.string.settings_clear_log_confirm),
        dismiss = stringResource(Res.string.settings_cancel),
        onConfirm = {
            onClear()
            onDismiss()
        },
        onDismiss = onDismiss,
        destructive = true,
    )
}

/** Asks before the app's icon is changed where the app closes to change it, as Android's does. */
@Composable
private fun ChangeIconDialog(onChange: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = stringResource(Res.string.settings_app_icon_confirm_title),
        text = stringResource(Res.string.settings_app_icon_confirm_text),
        confirm = stringResource(Res.string.settings_app_icon_confirm),
        dismiss = stringResource(Res.string.settings_cancel),
        onConfirm = {
            // Away first: the app may be gone before another frame.
            onDismiss()
            onChange()
        },
        onDismiss = onDismiss,
    )
}

/** Asks before a sensor with a class on it is forgotten: the class could no longer be synced. */
@Composable
private fun ForgetSensorDialog(onForget: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = stringResource(Res.string.settings_sensor_forget_title),
        text = stringResource(Res.string.settings_sensor_forget_text),
        confirm = stringResource(Res.string.settings_sensor_forget),
        dismiss = stringResource(Res.string.settings_cancel),
        onConfirm = {
            onForget()
            onDismiss()
        },
        onDismiss = onDismiss,
        destructive = true,
    )
}

/**
 * Asks which of the [sensors] found nearby is meant, the nearest first, each by the name it advertises (its
 * model and the id printed on it). [onChoose] hears the one tapped once the sheet is away, or null when it was
 * closed with none.
 */
@Composable
private fun SensorChoiceSheet(sensors: List<NearbySensor>, onChoose: (NearbySensor?) -> Unit) {
    val palette = IntervalTheme.palette
    var picked by remember { mutableStateOf<NearbySensor?>(null) }
    val title = stringResource(Res.string.settings_sensor_choose_title)
    IntervalSheet(title = title, onDismiss = { onChoose(picked) }) { hide ->
        Text(title, style = IntervalTheme.type.value, color = palette.text)
        Text(
            stringResource(Res.string.settings_sensor_choose_note),
            style = IntervalTheme.type.meta,
            color = palette.textDim,
        )
        ListCard {
            sensors.forEachIndexed { index, sensor ->
                if (index > 0) RowDivider()
                LinkRow(
                    sensor.name,
                    subtitle = null,
                    onClick = {
                        picked = sensor
                        hide()
                    },
                )
            }
        }
    }
}

/** The import under way: its review sheet, then a notice of what came in, or that it couldn't finish. */
@Composable
private fun ImportProgress(step: ImportStep?, actions: ImportActions) {
    if (step is ImportStep.Review) ImportSheet(step, actions)
    val notices = LocalNotices.current
    val said = when (step) {
        is ImportStep.Done -> importedText(step.added)
        ImportStep.Failed -> stringResource(Res.string.import_failed)
        else -> null
    }
    val dismiss by rememberUpdatedState(actions.onDismiss)
    LaunchedEffect(said) {
        if (said != null) {
            notices.show(said)
            dismiss()
        }
    }
}

/** A line along the start edge, drawn over the content: where a pane meets the one beside it. */
private fun Modifier.hairlineAtStart(color: Color): Modifier = drawWithContent {
    drawContent()
    val stroke = Sizes.hairline.toPx()
    val x = if (layoutDirection == LayoutDirection.Ltr) stroke / 2 else size.width - stroke / 2
    drawLine(color, Offset(x, 0f), Offset(x, size.height), stroke)
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

/** On a phone with a health store connected and a sensor paired: the body's group with both under it. */
@PreviewFormFactors
@Composable
private fun SettingsScreenConnectedPreview() {
    IntervalTheme {
        SettingsScreen(
            state = SettingsUiState(
                CueSettings(vibrate = false),
                prepSeconds = 10,
                app = AppSettings(),
                health = HealthStatus.Connected,
            ),
            onBack = {},
            onToggle = {},
            onStepPrep = {},
            onSetPrep = {},
            onToggleKeepScreenOn = {},
            onSetTheme = {},
            onShowAbout = {},
            sensor = SensorRow(SensorStatus.Paired("Polar H10 1A2B3C4D"), battery = 82),
        )
    }
}

/** What a failure to pair the sensor is said as. */
private val SensorFailure.words: StringResource
    get() = when (this) {
        SensorFailure.NoAccess -> Res.string.notice_sensor_no_access
        SensorFailure.NotFound -> Res.string.notice_sensor_none_nearby
        SensorFailure.Failed, SensorFailure.NothingRecorded -> Res.string.notice_sensor_failed
    }
