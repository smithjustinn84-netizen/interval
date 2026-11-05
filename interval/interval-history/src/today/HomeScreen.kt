package dev.justinsmith.interval.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.justinsmith.interval.history.GoalRow
import dev.justinsmith.interval.history.StartPill
import dev.justinsmith.interval.history.SummaryCard
import dev.justinsmith.interval.history.UpNextItem
import dev.justinsmith.interval.history.UpNextLater
import dev.justinsmith.interval.history.WeekStrip
import dev.justinsmith.interval.history.dayHeading
import dev.justinsmith.interval.history.resources.Res
import dev.justinsmith.interval.history.resources.common_joined
import dev.justinsmith.interval.history.resources.history_edit_plan
import dev.justinsmith.interval.history.resources.history_empty_plan
import dev.justinsmith.interval.history.resources.history_lift_any
import dev.justinsmith.interval.history.resources.history_lift_session
import dev.justinsmith.interval.history.resources.history_up_next
import dev.justinsmith.interval.history.resources.home_done_text
import dev.justinsmith.interval.history.resources.home_done_title
import dev.justinsmith.interval.history.resources.home_empty_text
import dev.justinsmith.interval.history.resources.home_empty_title
import dev.justinsmith.interval.history.resources.home_follow_program
import dev.justinsmith.interval.history.resources.home_minutes
import dev.justinsmith.interval.history.resources.home_no_pick
import dev.justinsmith.interval.history.resources.home_no_pick_hint
import dev.justinsmith.interval.history.resources.home_pick_by_time
import dev.justinsmith.interval.history.resources.home_resume_session
import dev.justinsmith.interval.history.resources.home_start_day
import dev.justinsmith.interval.history.resources.home_start_pick_cd
import dev.justinsmith.interval.history.resources.home_start_session
import dev.justinsmith.interval.history.resources.home_title
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.EmptyState
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalSizes
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.LinkRow
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.SecondaryButton
import dev.justinsmith.interval.ui.SegmentedPicker
import dev.justinsmith.interval.ui.SettingsAction
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.StartButton
import dev.justinsmith.interval.ui.StripeMark
import dev.justinsmith.interval.ui.TabGrid
import dev.justinsmith.interval.ui.configDescription
import dev.justinsmith.interval.ui.joined
import dev.justinsmith.interval.ui.workoutDescription
import dev.justinsmith.lift.domain.ProgramKey
import dev.justinsmith.lift.ui.programDayLifts
import dev.justinsmith.lift.ui.programDayLine
import dev.justinsmith.lift.ui.programName
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The Today tab, the app's first: this week, what is up next with its Start, and the presets that fit a few
 * minutes. [onStartWorkout] opens the workout the Timer was just handed and [onStartSession] the session Lift
 * was just told to start; the rest lead to the plan, Lift's programs, the History and Timer tabs and Settings.
 */
@Composable
fun HomeRoute(
    onStartWorkout: () -> Unit,
    onStartSession: () -> Unit,
    onPlan: () -> Unit,
    onPrograms: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenTimer: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
) {
    val viewModel = koinViewModel<HomeViewModel>()
    val locale = Locale.current.toLanguageTag()
    LaunchedEffect(locale) { viewModel.setLocale(locale) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The actions are remembered once, so they read the latest navigation callbacks rather than the first.
    val startWorkout by rememberUpdatedState(onStartWorkout)
    val startSession by rememberUpdatedState(onStartSession)
    val plan by rememberUpdatedState(onPlan)
    val programs by rememberUpdatedState(onPrograms)
    val openHistory by rememberUpdatedState(onOpenHistory)
    val openTimer by rememberUpdatedState(onOpenTimer)
    val openSettings by rememberUpdatedState(onOpenSettings)
    val actions = remember(viewModel) {
        HomeActions(
            onStart = { name, config ->
                viewModel.putOnTimer(name, config)
                startWorkout()
            },
            onStartPick = { pick ->
                viewModel.putOnTimer(pick)
                startWorkout()
            },
            onStartLift = { program ->
                // Lift starts nothing while a session is in progress, so with one under way this resumes it.
                viewModel.startLift(program)
                startSession()
            },
            onMinutes = viewModel::setMinutes,
            onPlan = { plan() },
            onPrograms = { programs() },
            onOpenHistory = { openHistory() },
            onOpenTimer = { openTimer() },
            onOpenSettings = { openSettings() },
        )
    }
    HomeScreen(state, actions, modifier)
}

/** What the Today tab's controls do. Each defaults to nothing, so a preview or test sets only its own. */
@Immutable
class HomeActions(
    /** Starts a planned workout of this name and shape now. */
    val onStart: (String, IntervalConfig) -> Unit = { _, _ -> },
    /** Starts a preset picked by time now. */
    val onStartPick: (QuickPick) -> Unit = {},
    /** Opens Lifting: on a new day of this program, or with null a free session; either way, on one in progress. */
    val onStartLift: (ProgramKey?) -> Unit = {},
    val onMinutes: (Int) -> Unit = {},
    val onPlan: () -> Unit = {},
    val onPrograms: () -> Unit = {},
    val onOpenHistory: () -> Unit = {},
    val onOpenTimer: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
)

/**
 * Today: the week against the goal, then what is up next under the one raised Start, or the ways to a plan
 * while there is none, or a word that the plan is done for now; under them the presets that fit the minutes
 * picked, each with a Start of its own. A grid of cards as Lift's tab is, so a wide window sets them side by
 * side. Nothing shows until the first state is built.
 */
@Composable
fun HomeScreen(state: HomeUiState, actions: HomeActions, modifier: Modifier = Modifier) {
    val card = Modifier.padding(horizontal = Spacing.space16, vertical = Spacing.space4)
    TabGrid(
        stringResource(Res.string.home_title),
        modifier,
        actions = { SettingsAction(actions.onOpenSettings) },
    ) {
        if (state.loading) return@TabGrid
        item(key = "week") {
            SummaryCard(card) {
                GoalRow(state.weekGoal, actions.onPlan)
                // A day leads to the log, where its sheet opens.
                WeekStrip(state.week, onDay = { actions.onOpenHistory() })
            }
        }
        item(key = "next") {
            when {
                state.upNext.isNotEmpty() -> UpNext(state.upNext, actions, card)
                state.hasPlans -> NothingDue(actions, card)
                else -> NothingPlanned(actions, card)
            }
        }
        item(key = "picks") { Picks(state, actions, card) }
    }
}

/** What is up next, by its day: its name over what it is, its Start, and the two after it in a line. */
@Composable
private fun UpNext(upNext: List<UpNextItem>, actions: HomeActions, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val next = upNext.first()
    SummaryCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.space4)) {
            Text(
                stringResource(Res.string.history_up_next, dayHeading(next.day)),
                style = IntervalTheme.type.caption,
                color = palette.textDim,
            )
            Text(upNextTitle(next), style = IntervalTheme.type.value, color = palette.text)
            Text(upNextDetail(next), style = IntervalTheme.type.meta, color = palette.textDim)
        }
        when (next) {
            is UpNextItem.Planned ->
                StartButton({ actions.onStart(next.name, next.config) }, Modifier.fillMaxWidth())

            is UpNextItem.Lift -> {
                val day = next.programDay
                StartButton(
                    { actions.onStartLift(day?.program?.key.takeUnless { next.resume }) },
                    Modifier.fillMaxWidth(),
                    label = when {
                        next.resume -> stringResource(Res.string.home_resume_session)
                        day != null -> stringResource(Res.string.home_start_day, day.number)
                        else -> stringResource(Res.string.home_start_session)
                    },
                )
            }
        }
        UpNextLater(upNext)
    }
}

/** What leads Up next: the plan's name, the program's, or a session on Lift. */
@Composable
private fun upNextTitle(next: UpNextItem): String = when (next) {
    is UpNextItem.Planned -> next.name

    is UpNextItem.Lift -> next.programDay?.let { programName(it.program) }
        ?: stringResource(Res.string.history_lift_session)
}

/** The line under it: the workout's shape and length, or the program's day and its lifts. */
@Composable
private fun upNextDetail(next: UpNextItem): String = when (next) {
    is UpNextItem.Planned -> stringResource(
        Res.string.common_joined,
        workoutDescription(next.config),
        formatClock(next.config.totalSeconds),
    )

    is UpNextItem.Lift -> next.programDay?.let { joined(listOf(programDayLine(it), programDayLifts(it))) }
        ?: stringResource(Res.string.history_lift_any)
}

/** Nothing up next: what Today is waiting for, and the two ways to give it one. */
@Composable
private fun NothingPlanned(actions: HomeActions, modifier: Modifier = Modifier) {
    ListCard(modifier) {
        EmptyState(
            IntervalIcons.Plan,
            stringResource(Res.string.home_empty_title),
            stringResource(Res.string.home_empty_text),
        )
        Column(
            Modifier.padding(start = Spacing.space16, end = Spacing.space16, bottom = Spacing.space16),
            verticalArrangement = Arrangement.spacedBy(Spacing.space8),
        ) {
            AccentButton(stringResource(Res.string.history_empty_plan), actions.onPlan, Modifier.fillMaxWidth())
            SecondaryButton(
                stringResource(Res.string.home_follow_program),
                actions.onPrograms,
                Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A plan with nothing left of it for now: it says so, quietly, and leads to the plan to add to it. */
@Composable
private fun NothingDue(actions: HomeActions, modifier: Modifier = Modifier) {
    ListCard(modifier) {
        EmptyState(
            IntervalIcons.Check,
            stringResource(Res.string.home_done_title),
            stringResource(Res.string.home_done_text),
        )
        SecondaryButton(
            stringResource(Res.string.history_edit_plan),
            actions.onPlan,
            Modifier
                .fillMaxWidth()
                .padding(start = Spacing.space16, end = Spacing.space16, bottom = Spacing.space16),
        )
    }
}

/** Or pick by time: a length, then the presets that fit it, or the way to the Timer when none does. */
@Composable
private fun Picks(state: HomeUiState, actions: HomeActions, modifier: Modifier = Modifier) {
    Column(modifier) {
        GroupLabel(stringResource(Res.string.home_pick_by_time))
        SegmentedPicker(
            options = PICK_MINUTES,
            selected = state.minutes,
            label = { stringResource(Res.string.home_minutes, it) },
            onSelect = actions.onMinutes,
            check = false,
        )
        ListCard(Modifier.padding(top = Spacing.space8)) {
            if (state.picks.isEmpty()) {
                LinkRow(
                    stringResource(Res.string.home_no_pick),
                    stringResource(Res.string.home_no_pick_hint),
                    actions.onOpenTimer,
                )
            }
            state.picks.forEachIndexed { index, pick ->
                if (index > 0) RowDivider(Modifier.padding(start = Spacing.space4))
                PickRow(pick, onStart = { actions.onStartPick(pick) })
            }
        }
    }
}

/** A preset that fits: its name over its shape and length, and a Start that says which it starts. */
@Composable
private fun PickRow(pick: QuickPick, onStart: () -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.listRow)
            .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        StripeMark(palette.workMark, height = IntervalSizes.historyMarkHeight)
        Column(Modifier.weight(1f)) {
            Text(pick.preset.name, style = IntervalTheme.type.label, color = palette.text)
            Text(
                stringResource(
                    Res.string.common_joined,
                    configDescription(pick.preset.config),
                    formatClock(pick.totalSeconds),
                ),
                style = IntervalTheme.type.meta,
                color = palette.textDim,
            )
        }
        val starts = stringResource(Res.string.home_start_pick_cd, pick.preset.name)
        StartPill(primary = false, onClick = onStart, Modifier.semantics { contentDescription = starts })
    }
}
