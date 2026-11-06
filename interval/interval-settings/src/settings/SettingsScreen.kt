package dev.justinsmith.interval.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import dev.justinsmith.interval.platform.HealthStore
import dev.justinsmith.interval.platform.rememberFileOpener
import dev.justinsmith.interval.platform.rememberHealthConnector
import dev.justinsmith.interval.settings.resources.Res
import dev.justinsmith.interval.settings.resources.import_failed
import dev.justinsmith.interval.settings.resources.settings_close_cd
import dev.justinsmith.interval.settings.resources.settings_title
import dev.justinsmith.interval.timer.ConfigField
import dev.justinsmith.interval.timer.Effort
import dev.justinsmith.interval.timer.WeightUnit
import dev.justinsmith.interval.ui.BackAction
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.LocalNotices
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.ScrollPage
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.TabGrid
import dev.justinsmith.interval.ui.ValueSheet
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
        onShowPlan = onShowPlan,
        onShowSetup = onShowSetup,
        onSetWeight = viewModel::setWeight,
        onSetWeightUnit = viewModel::setWeightUnit,
        onClearWeight = viewModel::clearWeight,
        onUseHealthWeight = viewModel::useHealthWeight,
        onSetEffort = viewModel::setEffort,
        onExport = viewModel::export,
        onConnectHealth = health::connect,
        onDisconnectHealth = viewModel::disconnectHealth,
        onInstallHealth = health::install,
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
 * [inPane] when it sits beside the Timer on a wide window: there it closes rather than going back.
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
    onShowPlan: () -> Unit = {},
    onShowSetup: () -> Unit = {},
    onSetWeight: (Int) -> Unit = {},
    onSetWeightUnit: (WeightUnit) -> Unit = {},
    onClearWeight: () -> Unit = {},
    onUseHealthWeight: () -> Unit = {},
    onSetEffort: (Effort) -> Unit = {},
    onExport: () -> Unit = {},
    onConnectHealth: () -> Unit = {},
    onDisconnectHealth: () -> Unit = {},
    onInstallHealth: () -> Unit = {},
    importStep: ImportStep? = null,
    imports: ImportActions = ImportActions(),
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
        onExport,
        imports.onPick,
        HealthActions(onConnectHealth, onDisconnectHealth, onInstallHealth),
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
        ValueSheet(
            field = ConfigField.Prep,
            value = state.prepSeconds,
            total = null,
            onStep = onStepPrep,
            onPick = onSetPrep,
            onDismiss = { editingPrep = false },
        )
    }
    ImportProgress(importStep, imports)
    if (editingWeight) {
        WeightSheet(
            weight = state.weight,
            unit = state.body.unit,
            onSetWeight = onSetWeight,
            onSetUnit = onSetWeightUnit,
            onClear = onClearWeight,
            onDismiss = { editingWeight = false },
            healthWeight = state.healthWeight,
            onUseHealthWeight = onUseHealthWeight,
        )
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
