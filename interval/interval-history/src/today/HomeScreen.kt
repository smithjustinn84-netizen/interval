package dev.justinsmith.interval.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.intl.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.justinsmith.interval.data.FlowStore
import dev.justinsmith.interval.history.HistoryDay
import dev.justinsmith.interval.history.PreviewHost
import dev.justinsmith.interval.history.StartPreview
import dev.justinsmith.interval.history.SummaryCard
import dev.justinsmith.interval.history.UpNextItem
import dev.justinsmith.interval.history.UpNextLater
import dev.justinsmith.interval.history.dayHeading
import dev.justinsmith.interval.history.flowKeyOf
import dev.justinsmith.interval.history.preview
import dev.justinsmith.interval.history.resources.Res
import dev.justinsmith.interval.history.resources.common_joined
import dev.justinsmith.interval.history.resources.history_edit_plan
import dev.justinsmith.interval.history.resources.history_empty_plan
import dev.justinsmith.interval.history.resources.history_kind_conditioning
import dev.justinsmith.interval.history.resources.history_kind_mobility
import dev.justinsmith.interval.history.resources.history_kind_strength
import dev.justinsmith.interval.history.resources.history_lift_any
import dev.justinsmith.interval.history.resources.history_lift_session
import dev.justinsmith.interval.history.resources.history_start
import dev.justinsmith.interval.history.resources.history_up_next
import dev.justinsmith.interval.history.resources.home_before_class
import dev.justinsmith.interval.history.resources.home_before_climbing
import dev.justinsmith.interval.history.resources.home_before_run
import dev.justinsmith.interval.history.resources.home_before_sport
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
import dev.justinsmith.interval.history.resources.home_setup
import dev.justinsmith.interval.history.resources.home_start_day
import dev.justinsmith.interval.history.resources.home_start_session
import dev.justinsmith.interval.history.resources.home_title
import dev.justinsmith.interval.history.resources.notice_undo
import dev.justinsmith.interval.history.resources.preview_open_cd
import dev.justinsmith.interval.history.resources.sport_counts
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.plan.sportTitle
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.EmptyState
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalSizes
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.LinkRow
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.LocalNotices
import dev.justinsmith.interval.ui.NoticeAction
import dev.justinsmith.interval.ui.QuietTextButton
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
import dev.justinsmith.lift.ui.estimateLine
import dev.justinsmith.lift.ui.programDayLifts
import dev.justinsmith.lift.ui.programDayLine
import dev.justinsmith.lift.ui.programName
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * The Today tab, the app's first: this week, what is up next with its Start, and the presets that fit a few
 * minutes. [onStartWorkout] opens the workout the Timer was just handed, or the flow it names, which the Timer
 * is not handed, and [onStartSession] the session Lift
 * was just told to start; the rest lead to the plan, Lift's programs, the History tab, the Timer, Settings and
 * Set up, which [onSetup] opens, and Train's Mobility pillar, which [onOpenMobility] does for the First week card.
 */
@Composable
fun HomeRoute(
    onStartWorkout: (flow: String?) -> Unit,
    onStartSession: () -> Unit,
    onPlan: () -> Unit,
    onPrograms: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenTimer: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    onSetup: () -> Unit = {},
    onOpenMobility: () -> Unit = {},
) {
    val viewModel = koinViewModel<HomeViewModel>()
    // Read as a start is tapped: a flow of the user's starts as itself, as a shipped one does.
    val flows = koinInject<FlowStore>()
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
    val setup by rememberUpdatedState(onSetup)
    val openMobility by rememberUpdatedState(onOpenMobility)
    val notices = LocalNotices.current
    val undo = stringResource(Res.string.notice_undo)
    val actions = remember(viewModel, notices, undo) {
        HomeActions(
            onStart = { name, config ->
                val flow = flowKeyOf(config, flows.flows.value)
                if (flow == null) viewModel.putOnTimer(name, config)
                startWorkout(flow)
            },
            onStartPick = { preset ->
                val flow = flowKeyOf(preset.config, flows.flows.value)
                if (flow == null) viewModel.putOnTimer(preset)
                startWorkout(flow)
            },
            onStartLift = { program ->
                // Lift starts nothing while a session is in progress, so with one under way this resumes it.
                viewModel.startLift(program)
                startSession()
            },
            onLogSport = { sport, loggedText ->
                val day = viewModel.logSport(sport)
                notices.show(loggedText, NoticeAction(undo) { viewModel.unlogSport(sport, day) })
            },
            onMinutes = viewModel::setMinutes,
            onPlanWeekly = viewModel::planWeekly,
            onPlanOnce = viewModel::planOnce,
            onPlan = { plan() },
            onPrograms = { programs() },
            onOpenHistory = { openHistory() },
            onOpenTimer = { openTimer() },
            onOpenSettings = { openSettings() },
            onSetup = { setup() },
            onOpenMobility = { openMobility() },
            onHideFirstWeek = viewModel::hideFirstWeek,
        )
    }
    HomeScreen(state, actions, modifier)
}

/** What the Today tab's controls do. Each defaults to nothing, so a preview or test sets only its own. */
@Immutable
class HomeActions(
    /** Starts a planned workout of this name and shape now. */
    val onStart: (String, IntervalConfig) -> Unit = { _, _ -> },
    /** Starts a preset picked by time, or a workout looked at before Start, now. */
    val onStartPick: (Preset) -> Unit = {},
    /** Opens Lifting: on a new day of this program, or with null a free session; either way, on one in progress. */
    val onStartLift: (ProgramKey?) -> Unit = {},
    /** Logs a day of this sport today, with the line saying so ("BJJ class logged"). */
    val onLogSport: (sport: Sport, loggedText: String) -> Unit = { _, _ -> },
    val onMinutes: (Int) -> Unit = {},
    /** Plans a workout looked at before Start, every week or once. */
    val onPlanWeekly: (PlanWorkout, Set<Int>) -> Unit = { _, _ -> },
    val onPlanOnce: (PlanWorkout, Long) -> Unit = { _, _ -> },
    val onPlan: () -> Unit = {},
    val onPrograms: () -> Unit = {},
    val onOpenHistory: () -> Unit = {},
    val onOpenTimer: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    /** Opens Set up, which builds a week from four answers. */
    val onSetup: () -> Unit = {},
    /** Opens Train's Mobility pillar, where the flows are. */
    val onOpenMobility: () -> Unit = {},
    /** Puts the First week card away for good. */
    val onHideFirstWeek: () -> Unit = {},
)

/**
 * Today: the week in a line and a dot a day, then what is up next under the one raised Start, or the ways to a plan
 * while there is none, or a word that the plan is done for now; on a day with a sport, what else the day holds
 * beside what leads it ([AlsoToday]); in a new install's first week, the few things to do once ([FirstWeekCard]);
 * under them the presets that fit the minutes picked, each with a Start of its
 * own. A grid of cards as Lift's tab is, so a wide window sets them side by side. Nothing shows until the first
 * state is built.
 */
@Composable
fun HomeScreen(state: HomeUiState, actions: HomeActions, modifier: Modifier = Modifier) {
    val card = Modifier.padding(horizontal = Spacing.space16, vertical = Spacing.space4)
    // What is being looked at before Start, by where it is: kept by place, so a rotation keeps it open.
    var looking by rememberSaveable { mutableStateOf<String?>(null) }
    TabGrid(
        stringResource(Res.string.home_title),
        modifier,
        actions = { SettingsAction(actions.onOpenSettings) },
    ) {
        if (state.loading) return@TabGrid
        item(key = "week") {
            SummaryCard(card) {
                WeekHeader(state.weekGoal, actions.onPlan)
                // A day leads to the log, where its sheet opens.
                WeekDots(state.week, onDay = { actions.onOpenHistory() })
            }
        }
        val agenda = state.agenda
        item(key = "next") {
            when {
                agenda.lead != null -> UpNext(agenda.lead, agenda.later, actions, { looking = LEAD }, card)
                state.hasPlans -> NothingDue(actions, card)
                else -> NothingPlanned(actions, card)
            }
        }
        if (agenda.also.isNotEmpty()) item(key = "also") { AlsoToday(agenda.also, actions, card) }
        state.firstWeek?.let { week ->
            // A flow or a sport's day in the lead has no workout under its Start: that one is picked by time.
            val leads = agenda.lead is UpNextItem.Planned || agenda.lead is UpNextItem.Lift
            item(key = "first-week") { FirstWeekCard(week, leads, actions, card) }
        }
        item(key = "picks") { Picks(state, actions, { looking = "$PICK$it" }, card) }
    }
    looking?.let { state.previewAt(it) }?.let { preview ->
        PreviewHost(
            preview,
            today = state.week.firstOrNull { it.today }?.epochDay ?: 0,
            onStartWorkout = actions.onStartPick,
            onStartLift = { actions.onStartLift(it?.program?.key) },
            onPlanWeekly = actions.onPlanWeekly,
            onPlanOnce = actions.onPlanOnce,
            onDone = { looking = null },
        )
    }
}

private const val LEAD = "lead"
private const val PICK = "pick:"

/** The program's day among the picks, by the place [Picks] hands on for it. */
private const val STRENGTH_PLACE = -1

/** What is looked at before Start at [place]: Up next's lead, or a pick by its place in the list. */
private fun HomeUiState.previewAt(place: String): StartPreview? = when {
    place == LEAD -> agenda.lead?.preview()

    place == "$PICK$STRENGTH_PLACE" -> strength?.let { StartPreview.Lift(it.day, it.minutes) }

    place.startsWith(PICK) -> picks.getOrNull(place.removePrefix(PICK).toInt())?.let {
        StartPreview.Workout(it.preset, it.totalSeconds, it.flow)
    }

    else -> null
}

/**
 * What is up next, by its day: its name over what it is, its Start, and the first of [later] in a line. A day of
 * a sport has nothing to start: on its day it is logged, and before it only named.
 */
@Composable
private fun UpNext(
    next: UpNextItem,
    later: List<UpNextItem>,
    actions: HomeActions,
    onLook: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = IntervalTheme.palette
    SummaryCard(modifier) {
        // What it is opens a look at it; Start, under it, starts it at once.
        val look = next.preview()?.let { onLook }
        val described = stringResource(Res.string.preview_open_cd, upNextTitle(next))
        Column(
            Modifier
                .fillMaxWidth()
                .then(
                    if (look != null) {
                        Modifier.clickable(onClickLabel = described, onClick = look)
                    } else {
                        Modifier
                    },
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.space4),
        ) {
            Text(upNextCaption(next), style = IntervalTheme.type.caption, color = palette.textDim)
            Text(upNextTitle(next), style = IntervalTheme.type.value, color = palette.text)
            Text(upNextDetail(next), style = IntervalTheme.type.meta, color = palette.textDim)
        }
        // The card names what it starts, so its Start says no more: "Start", "Start day 3", "Resume session".
        val start = stringResource(Res.string.history_start)
        when (next) {
            is UpNextItem.Planned ->
                StartButton({ actions.onStart(next.name, next.config) }, Modifier.fillMaxWidth(), label = start)

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

            is UpNextItem.SportDay ->
                if (next.day == HistoryDay.Today) LogSportButton(next.sport, actions, Modifier.fillMaxWidth())

            // Started as any workout is, and known for the flow it is by its shape.
            is UpNextItem.Flow -> StartButton(
                { actions.onStart(next.flow.name, next.flow.config) },
                Modifier.fillMaxWidth(),
                label = start,
            )
        }
        // The next thing alone: Log's Coming up has the rest.
        UpNextLater(later, count = 1)
    }
}

/** The line over what leads: the day it is up next on, or, for a flow suggested, the sport it comes before. */
@Composable
private fun upNextCaption(next: UpNextItem): String = when (next) {
    is UpNextItem.Flow -> when (val sport = next.sport) {
        Sport.Bjj -> stringResource(Res.string.home_before_class)
        Sport.Climbing -> stringResource(Res.string.home_before_climbing)
        Sport.Running -> stringResource(Res.string.home_before_run)
        is Sport.Custom -> stringResource(Res.string.home_before_sport, sport.name)
    }

    else -> stringResource(Res.string.history_up_next, dayHeading(next.day))
}

/** What leads Up next: the plan's name, the program's, a session on Lift, a sport's day or a flow. */
@Composable
private fun upNextTitle(next: UpNextItem): String = when (next) {
    is UpNextItem.Planned -> next.name

    is UpNextItem.Flow -> next.flow.name

    is UpNextItem.Lift -> next.programDay?.let { programName(it.program) }
        ?: stringResource(Res.string.history_lift_session)

    is UpNextItem.SportDay -> sportTitle(next.sport)
}

/** The line under it: the workout's shape and length, or about how long the program's day takes, its day and lifts. */
@Composable
private fun upNextDetail(next: UpNextItem): String = when (next) {
    is UpNextItem.Planned -> stringResource(
        Res.string.common_joined,
        workoutDescription(next.config),
        formatClock(next.config.totalSeconds),
    )

    is UpNextItem.Lift -> next.programDay?.let { day ->
        // How long comes first: it is what Today is asked.
        joined(listOfNotNull(next.minutes?.let { estimateLine(it) }, programDayLine(day), programDayLifts(day)))
    } ?: stringResource(Res.string.history_lift_any)

    is UpNextItem.SportDay -> stringResource(Res.string.sport_counts)

    is UpNextItem.Flow -> joined(
        listOfNotNull(workoutDescription(next.flow.config), next.flow.note, formatClock(next.totalSeconds)),
    )
}

/** Nothing up next: what Today is waiting for, and the ways to give it one, Set up's four answers first. */
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
            AccentButton(stringResource(Res.string.home_setup), actions.onSetup, Modifier.fillMaxWidth())
            SecondaryButton(stringResource(Res.string.history_empty_plan), actions.onPlan, Modifier.fillMaxWidth())
            QuietTextButton(
                stringResource(Res.string.home_follow_program),
                actions.onPrograms,
                Modifier.align(Alignment.CenterHorizontally),
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
private fun Picks(state: HomeUiState, actions: HomeActions, onLook: (Int) -> Unit, modifier: Modifier = Modifier) {
    // The program's day is looked at by a place of its own, before the presets' from 0.
    Column(modifier) {
        GroupLabel(stringResource(Res.string.home_pick_by_time))
        // Five lengths on a phone's width: at a large font a label breaks over two lines rather than losing "min".
        SegmentedPicker(
            options = PICK_MINUTES,
            selected = state.minutes,
            label = { stringResource(Res.string.home_minutes, it) },
            onSelect = actions.onMinutes,
            check = false,
            maxLines = 2,
        )
        ListCard(Modifier.padding(top = Spacing.space8)) {
            state.strength?.let { strength ->
                StrengthRow(strength, onLook = { onLook(STRENGTH_PLACE) })
                if (state.picks.isNotEmpty()) RowDivider(Modifier.padding(start = Spacing.space4))
            }
            if (state.picks.isEmpty() && state.strength == null) {
                LinkRow(
                    stringResource(Res.string.home_no_pick),
                    stringResource(Res.string.home_no_pick_hint),
                    actions.onOpenTimer,
                )
            }
            state.picks.forEachIndexed { index, pick ->
                if (index > 0) RowDivider(Modifier.padding(start = Spacing.space4))
                PickRow(pick, onLook = { onLook(index) })
            }
        }
    }
}

/**
 * The program's next day, which fits: its name over its kind and day ("Strength · Day 3 of 16 · week 2"), about
 * how long, opening a look at it.
 */
@Composable
private fun StrengthRow(pick: StrengthPick, onLook: () -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val name = programName(pick.day.program)
    val described = stringResource(Res.string.preview_open_cd, name)
    val detail = joined(listOf(stringResource(Res.string.history_kind_strength), programDayLine(pick.day)))
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClickLabel = described, onClick = onLook)
            .heightIn(min = Sizes.listRow)
            .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        // The accent, as Lift's sessions are marked everywhere.
        StripeMark(palette.accent, height = IntervalSizes.historyMarkHeight)
        Column(Modifier.weight(1f)) {
            Text(name, style = IntervalTheme.type.label, color = palette.text)
            Text(detail, style = IntervalTheme.type.meta, color = palette.textDim)
        }
        Text(
            stringResource(Res.string.home_minutes, pick.minutes),
            style = IntervalTheme.type.value,
            color = palette.text,
        )
        Icon(IntervalIcons.Chevron, null, tint = palette.textDim, modifier = Modifier.size(Sizes.chipIcon))
    }
}

/**
 * A preset or a flow that fits: its name over its kind and shape ("Conditioning · 20s on · 10s off · ×8",
 * "Mobility · 6 moves") and its length, opening a look at it before Start. The kind is written, not left to the
 * stripe's colour alone.
 */
@Composable
private fun PickRow(pick: QuickPick, onLook: () -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val described = stringResource(Res.string.preview_open_cd, pick.preset.name)
    val kind = stringResource(
        if (pick.mobility) Res.string.history_kind_mobility else Res.string.history_kind_conditioning,
    )
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClickLabel = described, onClick = onLook)
            .heightIn(min = Sizes.listRow)
            .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        // A flow takes the rest mark, as mobility does on Train and in History.
        StripeMark(if (pick.mobility) palette.restMark else palette.workMark, height = IntervalSizes.historyMarkHeight)
        Column(Modifier.weight(1f)) {
            Text(pick.preset.name, style = IntervalTheme.type.label, color = palette.text)
            Text(
                joined(listOf(kind, configDescription(pick.preset.config))),
                style = IntervalTheme.type.meta,
                color = palette.textDim,
            )
        }
        Text(formatClock(pick.totalSeconds), style = IntervalTheme.type.value, color = palette.text)
        Icon(IntervalIcons.Chevron, null, tint = palette.textDim, modifier = Modifier.size(Sizes.chipIcon))
    }
}
