package dev.justinsmith.interval.weeksetup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.setup_build
import dev.justinsmith.interval.resources.setup_built
import dev.justinsmith.interval.resources.setup_intro
import dev.justinsmith.interval.resources.setup_skip
import dev.justinsmith.interval.resources.setup_title
import dev.justinsmith.interval.timer.WeightUnit
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.BackAction
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.LocalNotices
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.TitleBar
import dev.justinsmith.interval.ui.fadeWhenMoreBelow
import dev.justinsmith.interval.ui.weekdayMask
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.domain.program
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Resolves the ViewModel and binds it to [WeekSetupScreen]. [onBack] leaves with nothing built, as Skip does;
 * [onShowWeek] goes where the week built shows, after the notice saying so; [onPrograms] opens Lift's programs, to
 * follow another than the one Set up would.
 */
@Composable
fun WeekSetupRoute(onBack: () -> Unit, onShowWeek: () -> Unit, onPrograms: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<WeekSetupViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The actions are remembered once, so they read the latest navigation callbacks rather than the first.
    val back by rememberUpdatedState(onBack)
    val showWeek by rememberUpdatedState(onShowWeek)
    val programs by rememberUpdatedState(onPrograms)
    val notices = LocalNotices.current
    val builtText = stringResource(Res.string.setup_built)
    val actions = remember(viewModel, notices, builtText) {
        WeekSetupActions(
            onBack = { back() },
            onSport = viewModel::pickSport,
            onSportName = viewModel::nameSport,
            onWeekdays = viewModel::setWeekdays,
            onEquipment = viewModel::toggleEquipment,
            onBell = viewModel::toggleBell,
            onPair = viewModel::togglePair,
            onBar = viewModel::setBar,
            onUnit = viewModel::setUnit,
            onMinutes = viewModel::setMinutes,
            onPrograms = { programs() },
            onBuild = {
                if (viewModel.build()) {
                    notices.show(builtText)
                    showWeek()
                }
            },
        )
    }
    WeekSetupScreen(state, actions, modifier)
}

/** What Set up's controls do. Each defaults to nothing, so a preview or test sets only its own. */
@Immutable
class WeekSetupActions(
    /** Leaves with nothing built: Back, and Skip for now. */
    val onBack: () -> Unit = {},
    /** The first answer: a place among the sports named, [Sport.OTHER] or [SPORT_NONE]. */
    val onSport: (Int) -> Unit = {},
    val onSportName: (String) -> Unit = {},
    /** The sport's days, a bit per day. */
    val onWeekdays: (Int) -> Unit = {},
    /** Ticks or unticks an equipment option. */
    val onEquipment: (SetupEquipment) -> Unit = {},
    /** Ticks or unticks a bell, by its kg. */
    val onBell: (Double) -> Unit = {},
    /** Ticks or unticks owning two of a bell, by its kg. */
    val onPair: (Double) -> Unit = {},
    /** Picks the empty barbell weight, in kg. */
    val onBar: (Double) -> Unit = {},
    /** Switches the weight unit shown for bells and bar (and saved on build). */
    val onUnit: (WeightUnit) -> Unit = {},
    val onMinutes: (Int) -> Unit = {},
    val onPrograms: () -> Unit = {},
    val onBuild: () -> Unit = {},
)

/**
 * Set up: what else you train, on which days, what you train with (and bell/barbell details when picked), and
 * how long a session is, then the week those come to and the button that builds it.
 */
@Composable
fun WeekSetupScreen(state: WeekSetupUiState, actions: WeekSetupActions, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val scroll = rememberScrollState()
    Box(modifier.fillMaxSize().background(palette.background), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = Sizes.content)
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            TitleBar(
                stringResource(Res.string.setup_title),
                navigation = { BackAction(actions.onBack) },
                actions = { QuietTextButton(stringResource(Res.string.setup_skip), actions.onBack) },
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .fadeWhenMoreBelow(scroll)
                    .verticalScroll(scroll)
                    .padding(horizontal = Spacing.space16)
                    .padding(bottom = Spacing.space24),
            ) {
                Text(
                    stringResource(Res.string.setup_intro),
                    style = IntervalTheme.type.body,
                    color = palette.textDim,
                    modifier = Modifier.padding(horizontal = Spacing.space4),
                )
                SportQuestion(state, actions)
                AnimatedVisibility(state.asksDays) { DaysQuestion(state.weekdays, actions.onWeekdays) }
                EquipmentQuestion(state.equipment, actions.onEquipment)
                AnimatedVisibility(state.hasKettlebells) {
                    BellsQuestion(state, actions.onBell, actions.onUnit)
                }
                AnimatedVisibility(state.hasKettlebells && state.bells.isNotEmpty()) {
                    PairsQuestion(state, actions.onPair)
                }
                AnimatedVisibility(state.hasBarbell) {
                    BarbellQuestion(state, actions.onBar, actions.onUnit, showUnit = !state.hasKettlebells)
                }
                MinutesQuestion(state.minutes, actions.onMinutes)
                YourWeek(state, actions.onPrograms)
            }
            HorizontalDivider(color = palette.line, thickness = Sizes.hairline)
            AccentButton(
                stringResource(Res.string.setup_build),
                actions.onBuild,
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
                enabled = state.canBuild,
            )
        }
    }
}

private val previewSportDays = setOf(1, 3, 5)
private val previewLiftDays = setOf(2, 4, 6)
private val previewMinutes = 15..15
private val previewBells = setOf(16.0)

@PreviewFormFactors
@Composable
private fun WeekSetupScreenPreview() {
    IntervalTheme {
        WeekSetupScreen(
            state = WeekSetupUiState(
                sport = 0,
                weekdays = weekdayMask(previewSportDays),
                equipment = setOf(SetupEquipment.Kettlebells),
                bells = previewBells,
                minutes = previewMinutes.first,
                week = BuiltWeek(
                    sport = Sport.Bjj,
                    sportDays = previewSportDays,
                    program = program(ProgramId.FirstHundredSwings),
                    liftDays = previewLiftDays,
                    goal = previewSportDays.size + previewLiftDays.size,
                ),
                programMinutes = previewMinutes,
            ),
            actions = WeekSetupActions(),
        )
    }
}
