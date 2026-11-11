package dev.justinsmith.interval.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
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
import dev.justinsmith.interval.history.PreviewHost
import dev.justinsmith.interval.history.StartPreview
import dev.justinsmith.interval.history.SummaryCard
import dev.justinsmith.interval.history.UpNextItem
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
import dev.justinsmith.interval.history.resources.home_pick_time
import dev.justinsmith.interval.history.resources.home_setup
import dev.justinsmith.interval.history.resources.home_something_else
import dev.justinsmith.interval.history.resources.home_title
import dev.justinsmith.interval.history.resources.notice_undo
import dev.justinsmith.interval.history.resources.preview_open_cd
import dev.justinsmith.interval.history.resources.sport_counts
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.plan.MovePlanSheet
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
            onStartLiftEasier = { program ->
                viewModel.startLift(program, easier = true)
                startSession()
            },
            onLogSport = { sport, loggedText ->
                val day = viewModel.logSport(sport)
                notices.show(loggedText, NoticeAction(undo) { viewModel.unlogSport(sport, day) })
            },
            onNotToday = viewModel::openMove,
            onCloseMove = { viewModel.openMove(null) },
            onMovePlanned = { toDay, movedText ->
                viewModel.movePlanned(toDay)?.let { move ->
                    notices.show(movedText, NoticeAction(undo) { viewModel.unmovePlanned(move) })
                }
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
    /** Opens Lifting on a new day of this program with its plan eased, as Lift offers on a taxed day. */
    val onStartLiftEasier: (ProgramKey) -> Unit = {},
    /** Logs a day of this sport today, with the line saying so ("BJJ class logged"). */
    val onLogSport: (sport: Sport, loggedText: String) -> Unit = { _, _ -> },
    /** Opens the move sheet on today's plan, by its id: "Not today". */
    val onNotToday: (planId: Long) -> Unit = {},
    val onCloseMove: () -> Unit = {},
    /** Moves the move sheet's plan to an epoch day, with the line saying so ("Moved Tabata to Thursday"). */
    val onMovePlanned: (toDay: Long, movedText: String) -> Unit = { _, _ -> },
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
 * Today, laid out as a coach would: a line saying what kind of day it is and the one thing worth saying of it
 * ([CoachLine]), the week in a line and a dot a day, then the day in order ([DayCard]: what is done ticked off, the
 * step to do now under the tab's one raised Start, the steps after it behind quiet buttons). Once today's steps
 * are done it says so ([DoneCard]), a day with none is a rest day ([RestCard]), and what comes after stands back
 * with a quiet button ([NextCard]); with nothing to come, the ways to a plan, or a word that the plan is done for
 * now. In a new install's first week, the few things to do once ([FirstWeekCard]). Under them the picks by time,
 * folded to a row on a day that has its plan. A grid of cards as Lift's tab is, so a wide window sets them side by
 * side. Nothing shows until the first state is built.
 */
@Composable
fun HomeScreen(state: HomeUiState, actions: HomeActions, modifier: Modifier = Modifier) {
    val card = Modifier.padding(horizontal = Spacing.space16, vertical = Spacing.space4)
    // What is being looked at before Start, by where it is: kept by place, so a rotation keeps it open.
    var looking by rememberSaveable { mutableStateOf<String?>(null) }
    // Whether the picks by time are unfolded, on a day whose plan keeps them to a row.
    var picking by rememberSaveable { mutableStateOf(false) }
    // Kept while what is up next is: the agenda is worked out again with every state, its lists new each time,
    // which a screen comparing them by instance (iOS) would compose again.
    val agenda = remember(state.upNext) { state.agenda }
    val plan = state.plan
    // The line over the day, once there is a day to speak of: a plan, or something done.
    val coached = !state.loading && (state.hasPlans || plan.done.isNotEmpty())
    TabGrid(
        stringResource(Res.string.home_title),
        modifier,
        actions = { SettingsAction(actions.onOpenSettings) },
        header = if (coached) ({ Coach(state) }) else null,
    ) {
        if (state.loading) return@TabGrid
        item(key = "week") {
            SummaryCard(card) {
                WeekHeader(state.weekGoal, actions.onPlan)
                // A day leads to the log, where its sheet opens.
                WeekDots(state.week, onDay = { actions.onOpenHistory() })
            }
        }
        dayItems(state, actions, card, onLook = { looking = LEAD })
        state.firstWeek?.let { week ->
            // A flow or a sport's day in the lead has no workout under its Start, and another day's has no Start:
            // that one is picked by time.
            val first = plan.steps.firstOrNull()
            val leads = first is UpNextItem.Planned || first is UpNextItem.Lift
            item(key = "first-week") { FirstWeekCard(week, leads, actions, card) }
        }
        item(key = "picks") {
            if (plan.settled && !picking) {
                SomethingElse(state.minutes, { picking = true }, card)
            } else {
                Picks(state.minutes, state.strength, state.picks, actions, { looking = "$PICK$it" }, card)
            }
        }
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
    state.moveSheet?.let { sheet ->
        MovePlanSheet(sheet, today = sheet.fromDay, onMove = actions.onMovePlanned, onDismiss = actions.onCloseMove)
    }
}

/** The line over the day: its kind, and what there is to say of it by the week's goal and Lift's reading. */
@Composable
private fun Coach(state: HomeUiState) {
    val goal = state.weekGoal.goal
    CoachLine(
        state.week.firstOrNull { it.today }?.weekday,
        state.plan.kind,
        state.plan.note(state.taxed, goalMet = goal != null && state.weekGoal.count >= goal),
        Modifier.padding(horizontal = Spacing.space20).padding(bottom = Spacing.space8),
    )
}

/**
 * The day's cards: its steps while any is left, else that it is done or a rest day; and with no step left, what
 * comes after standing back, or with nothing to come the ways to a plan.
 */
private fun LazyStaggeredGridScope.dayItems(
    state: HomeUiState,
    actions: HomeActions,
    card: Modifier,
    onLook: () -> Unit,
) {
    val plan = state.plan
    val next = plan.next
    when {
        plan.steps.isNotEmpty() -> item(key = "day") { DayCard(plan, state.easier, actions, onLook, card) }
        plan.done.isNotEmpty() -> item(key = "day") { DoneCard(plan.done, card) }
        plan.resting && next != null -> item(key = "day") { RestCard(next, actions, card) }
    }
    if (plan.steps.isNotEmpty()) return
    item(key = "next") {
        when {
            next != null -> NextCard(next, plan.later, plan.waiting, actions, onLook, card)
            state.hasPlans -> NothingDue(actions, card)
            else -> NothingPlanned(actions, card)
        }
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

/** The line over what leads: the day it is up next on, or, for a flow suggested, the sport it comes before. */
@Composable
internal fun upNextCaption(next: UpNextItem): String = when (next) {
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
internal fun upNextTitle(next: UpNextItem): String = when (next) {
    is UpNextItem.Planned -> next.name

    is UpNextItem.Flow -> next.flow.name

    is UpNextItem.Lift -> next.programDay?.let { programName(it.program) }
        ?: stringResource(Res.string.history_lift_session)

    is UpNextItem.SportDay -> sportTitle(next.sport)
}

/** The line under it: the workout's shape and length, or about how long the program's day takes, its day and lifts. */
@Composable
internal fun upNextDetail(next: UpNextItem): String = when (next) {
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

/** The picks by time, folded to a row on a day that has its plan: the length picked, opening them. */
@Composable
private fun SomethingElse(minutes: Int, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    ListCard(modifier) {
        LinkRow(
            stringResource(Res.string.home_something_else),
            stringResource(Res.string.home_pick_time, stringResource(Res.string.home_minutes, minutes)),
            onOpen,
        )
    }
}

/** Or pick by time: a length, then the presets that fit it, or the way to the Timer when none does. */
@Composable
private fun Picks(
    minutes: Int,
    strength: StrengthPick?,
    picks: List<QuickPick>,
    actions: HomeActions,
    onLook: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The program's day is looked at by a place of its own, before the presets' from 0.
    Column(modifier) {
        GroupLabel(stringResource(Res.string.home_pick_by_time))
        // Five lengths on a phone's width: at a large font a label breaks over two lines rather than losing "min".
        SegmentedPicker(
            options = PICK_MINUTES,
            selected = minutes,
            label = { stringResource(Res.string.home_minutes, it) },
            onSelect = actions.onMinutes,
            check = false,
            maxLines = 2,
        )
        ListCard(Modifier.padding(top = Spacing.space8)) {
            strength?.let { strength ->
                StrengthRow(strength, onLook = { onLook(STRENGTH_PLACE) })
                if (picks.isNotEmpty()) RowDivider(Modifier.padding(start = Spacing.space4))
            }
            if (picks.isEmpty() && strength == null) {
                LinkRow(
                    stringResource(Res.string.home_no_pick),
                    stringResource(Res.string.home_no_pick_hint),
                    actions.onOpenTimer,
                )
            }
            picks.forEachIndexed { index, pick ->
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
