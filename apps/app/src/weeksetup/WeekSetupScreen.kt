package dev.justinsmith.interval.weeksetup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.setup_build
import dev.justinsmith.interval.resources.setup_built
import dev.justinsmith.interval.resources.setup_intro
import dev.justinsmith.interval.resources.setup_skip
import dev.justinsmith.interval.resources.setup_title
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.BackAction
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.LocalNotices
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.ScrollPage
import dev.justinsmith.interval.ui.Spacing
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
            onBell = viewModel::toggleBell,
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
    /** Ticks or unticks a bell, by its kg. */
    val onBell: (Double) -> Unit = {},
    val onMinutes: (Int) -> Unit = {},
    val onPrograms: () -> Unit = {},
    val onBuild: () -> Unit = {},
)

/**
 * Set up: what else you train, on which days, which bells you own and how long a session is, then the week
 * those come to and the button that builds it. The days are asked only of a sport; the week names the program
 * before anything is built, so nothing it does is a surprise.
 */
@Composable
fun WeekSetupScreen(state: WeekSetupUiState, actions: WeekSetupActions, modifier: Modifier = Modifier) {
    ScrollPage(stringResource(Res.string.setup_title), modifier, navigation = { BackAction(actions.onBack) }) {
        Text(
            stringResource(Res.string.setup_intro),
            style = IntervalTheme.type.body,
            color = IntervalTheme.palette.textDim,
            modifier = Modifier.padding(horizontal = Spacing.space4),
        )
        SportQuestion(state, actions)
        AnimatedVisibility(state.asksDays) { DaysQuestion(state.weekdays, actions.onWeekdays) }
        BellsQuestion(state, actions.onBell)
        MinutesQuestion(state.minutes, actions.onMinutes)
        YourWeek(state, actions.onPrograms)
        AccentButton(
            stringResource(Res.string.setup_build),
            actions.onBuild,
            Modifier.fillMaxWidth().padding(top = Spacing.space24),
            enabled = state.canBuild,
        )
        QuietTextButton(
            stringResource(Res.string.setup_skip),
            actions.onBack,
            Modifier.align(Alignment.CenterHorizontally).padding(top = Spacing.space8),
        )
    }
}
