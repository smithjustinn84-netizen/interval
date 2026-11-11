package dev.justinsmith.interval.history

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.justinsmith.interval.data.FlowStore
import dev.justinsmith.interval.history.resources.Res
import dev.justinsmith.interval.history.resources.history_done_week
import dev.justinsmith.interval.history.resources.history_earlier
import dev.justinsmith.interval.history.resources.history_empty
import dev.justinsmith.interval.history.resources.history_empty_plan
import dev.justinsmith.interval.history.resources.history_empty_start
import dev.justinsmith.interval.history.resources.history_empty_title
import dev.justinsmith.interval.history.resources.history_plan_add_cd
import dev.justinsmith.interval.history.resources.history_title
import dev.justinsmith.interval.history.resources.history_today
import dev.justinsmith.interval.history.resources.history_tomorrow
import dev.justinsmith.interval.history.resources.history_yesterday
import dev.justinsmith.interval.history.resources.notice_undo
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.plan.AddToPlanSheet
import dev.justinsmith.interval.plan.LiftChoices
import dev.justinsmith.interval.plan.MovePlanSheet
import dev.justinsmith.interval.plan.PlanRepeat
import dev.justinsmith.interval.plan.dayChoices
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.builtInPresets
import dev.justinsmith.interval.timer.weekdayOf
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.LocalNotices
import dev.justinsmith.interval.ui.NoticeAction
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.SecondaryButton
import dev.justinsmith.interval.ui.SettingsAction
import dev.justinsmith.interval.ui.SetupLanes
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.TabGrid
import dev.justinsmith.interval.ui.TitleBar
import dev.justinsmith.interval.ui.fadeThroughContent
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.domain.ProgramKey
import dev.justinsmith.lift.domain.program
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Resolves the tab's ViewModel and binds it to [HistoryScreen]. Starting a workout puts it on the Timer, then
 * [onStartWorkout]; a flow starts as itself, by the key [onStartWorkout] is handed, and the Timer is left alone.
 * [onOpenWorkout] opens one finished workout, [onOpenLift] one kettlebell session, [onPlan] the plan,
 * [onShowTimer] the Timer, [onOpenSettings] Settings. Starting the lift program's day opens [onStartSession].
 * [selectedWorkout] or [selectedLift] is the one open beside the log, on a wide window.
 */
@Composable
fun HistoryRoute(
    onOpenWorkout: (Long) -> Unit,
    onOpenLift: (String) -> Unit,
    onPlan: () -> Unit,
    onStartWorkout: (flow: String?) -> Unit,
    onShowTimer: () -> Unit,
    onStartSession: () -> Unit,
    modifier: Modifier = Modifier,
    selectedWorkout: Long? = null,
    selectedLift: String? = null,
    onOpenSettings: () -> Unit = {},
) {
    val viewModel = koinViewModel<HistoryViewModel>()
    val flows = koinInject<FlowStore>()
    val locale = Locale.current.toLanguageTag()
    LaunchedEffect(locale) { viewModel.setLocale(locale) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The actions are remembered once, so they read the latest navigation callbacks rather than the first.
    val startWorkout by rememberUpdatedState(onStartWorkout)
    val openWorkout by rememberUpdatedState(onOpenWorkout)
    val openLift by rememberUpdatedState(onOpenLift)
    val plan by rememberUpdatedState(onPlan)
    val showTimer by rememberUpdatedState(onShowTimer)
    val startSession by rememberUpdatedState(onStartSession)
    val openSettings by rememberUpdatedState(onOpenSettings)
    val notices = LocalNotices.current
    val undo = stringResource(Res.string.notice_undo)
    val actions = remember(viewModel, notices, undo) {
        // Every way off the day sheet to another page closes it first, so it isn't open on the way back.
        fun leavingSheet(go: () -> Unit) {
            viewModel.openDay(null)
            go()
        }
        HistoryActions(
            onRange = viewModel::setRange,
            onShiftMonth = viewModel::shiftMonth,
            onOpenDay = viewModel::openDay,
            onStart = { name, config ->
                val flow = flowKeyOf(config, flows.flows.value)
                if (flow == null) viewModel.putOnTimer(name, config)
                leavingSheet { startWorkout(flow) }
            },
            onRemovePlanned = { planId, removedText ->
                viewModel.removePlanned(planId)?.let { plan ->
                    notices.show(removedText, NoticeAction(undo) { viewModel.restorePlanned(plan) })
                }
            },
            onOpenMove = viewModel::openMove,
            onCloseMove = { viewModel.openMove(null) },
            onMovePlanned = { toDay, movedText ->
                viewModel.movePlanned(toDay)?.let { move ->
                    notices.show(movedText, NoticeAction(undo) { viewModel.unmovePlanned(move) })
                }
            },
            onLogSport = { sport, day, loggedText ->
                viewModel.logSport(sport, day)
                notices.show(loggedText, NoticeAction(undo) { viewModel.unlogSport(sport, day) })
            },
            onLogWorkout = { config, day, loggedText ->
                val uid = viewModel.logWorkout(config, day)
                notices.show(loggedText, NoticeAction(undo) { viewModel.unlogWorkout(uid) })
            },
            onUnlogSport = { sport, day, removedText ->
                viewModel.unlogSport(sport, day)
                notices.show(removedText, NoticeAction(undo) { viewModel.logSport(sport, day) })
            },
            onPlanWeekly = viewModel::planWeekly,
            onPlanOnce = viewModel::planOnce,
            onOpenWorkout = { leavingSheet { openWorkout(it) } },
            onPlan = { leavingSheet(plan) },
            onShowTimer = { showTimer() },
            onOpenLift = { leavingSheet { openLift(it) } },
            onStartLift = { program ->
                // Lift starts nothing while a session is in progress, so with one under way this resumes it.
                viewModel.startLift(program)
                leavingSheet(startSession)
            },
            onOpenSettings = { openSettings() },
        )
    }
    HistoryScreen(state, actions, modifier, selectedWorkout, selectedLift)
}

/** What the History tab's controls do. Each defaults to nothing, so a preview or test sets only its own. */
@Immutable
class HistoryActions(
    val onRange: (TrainingRange) -> Unit = {},
    val onShiftMonth: (Int) -> Unit = {},
    /** Opens the day sheet on an epoch day, or closes it with null. */
    val onOpenDay: (Long?) -> Unit = {},
    /** Starts a workout of this name and shape now. */
    val onStart: (String, IntervalConfig) -> Unit = { _, _ -> },
    /** Takes a one-off off the plan, with the line saying so ("Removed Tabata"). */
    val onRemovePlanned: (planId: Long, removedText: String) -> Unit = { _, _ -> },
    /** Opens the move sheet on a plan, by its id, off an epoch day, as the day sheet closes. */
    val onOpenMove: (planId: Long, fromDay: Long) -> Unit = { _, _ -> },
    val onCloseMove: () -> Unit = {},
    /** Moves the move sheet's plan to an epoch day, with the line saying so ("Moved Tabata to Thursday"). */
    val onMovePlanned: (toDay: Long, movedText: String) -> Unit = { _, _ -> },
    /** Logs a day of this sport on an epoch day, with the line saying so ("BJJ class logged"). */
    val onLogSport: (sport: Sport, epochDay: Long, loggedText: String) -> Unit = { _, _, _ -> },
    /** Takes a day of this sport logged off its day, with the line saying so ("Removed BJJ class"). */
    val onUnlogSport: (sport: Sport, epochDay: Long, removedText: String) -> Unit = { _, _, _ -> },
    /**
     * Logs a workout of this shape, done away from the app, on an epoch day, with the line saying so ("Tabata
     * logged").
     */
    val onLogWorkout: (config: IntervalConfig, epochDay: Long, loggedText: String) -> Unit = { _, _, _ -> },
    val onPlanWeekly: (PlanWorkout, Set<Int>) -> Unit = { _, _ -> },
    val onPlanOnce: (PlanWorkout, Long) -> Unit = { _, _ -> },
    val onOpenWorkout: (Long) -> Unit = {},
    val onPlan: () -> Unit = {},
    val onShowTimer: () -> Unit = {},
    /** Opens a kettlebell session's page, by its uid. */
    val onOpenLift: (String) -> Unit = {},
    /** Opens Lifting: on a new day of this program, or with null a free session; either way, on one in progress. */
    val onStartLift: (ProgramKey?) -> Unit = {},
    val onOpenSettings: () -> Unit = {},
)

/**
 * The training log. A range picker leads to this week (goal, streak and a block per day), a month's
 * calendar, or all time; under it, what is planned next and every finished workout by day. A day opens
 * a sheet of its plans and workouts. The title bar's + plans a workout; clearing the log is Settings' (Your data).
 * The row of [selectedWorkout] or [selectedLift] shows it is the one open beside the log.
 *
 * One column on a phone. With the width for two columns (an upright tablet), the summary keeps a column of
 * its own beside the log, each scrolling by itself, rather than stretching across both over a log that
 * zig-zags between them.
 */
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    actions: HistoryActions,
    modifier: Modifier = Modifier,
    selectedWorkout: Long? = null,
    selectedLift: String? = null,
) {
    // The day the day sheet's Add workout is planning on, while its add sheet is open.
    var addingOn by rememberSaveable { mutableStateOf<Long?>(null) }
    // The day the day sheet's Log a workout is logging on, while its log sheet is open.
    var loggingOn by rememberSaveable { mutableStateOf<Long?>(null) }
    // Planning from the title bar's +, on any of the next two weeks' days, rather than one day's.
    var planning by rememberSaveable { mutableStateOf(false) }
    // The row of Coming up looked at before Start, by its place.
    var looking by rememberSaveable { mutableStateOf<Int?>(null) }
    val title = stringResource(Res.string.history_title)
    // Two actions, as the board draws them: the plan is a tap away in the body ("Edit plan", "Set a weekly
    // goal", the day sheet's "Open plan"), and clearing the log lives in Settings beside export.
    val titleActions: @Composable RowScope.() -> Unit = {
        IconAction(IntervalIcons.Add, stringResource(Res.string.history_plan_add_cd), { planning = true })
        SettingsAction(actions.onOpenSettings)
    }
    val selected = { item: HistoryItem ->
        (item.id != null && item.id == selectedWorkout) || (item.liftUid != null && item.liftUid == selectedLift)
    }
    val groups = remember(state.range, state.sections, state.weekStart) { state.logGroups() }
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth >= Sizes.listColumn * 2 && state.sections.isNotEmpty()) {
            SummaryBesideLog(title, titleActions, state, groups, actions, selected, onLook = { looking = it })
        } else {
            TabGrid(title, actions = titleActions) {
                // While loading, neither: no first run's card for a moment, and no summary of nothing.
                if (state.isEmpty) {
                    item(key = "empty", span = StaggeredGridItemSpan.FullLine) {
                        FirstRun(actions, Modifier.animateItem().then(Gutter))
                    }
                } else if (!state.loading) {
                    summaryItems(state, actions, onLook = { looking = it })
                }
                logItems(groups, actions, selected)
            }
        }
    }
    state.daySheet?.let { sheet ->
        DaySheetContent(
            sheet,
            actions,
            onAddWorkout = { addingOn = sheet.epochDay },
            onLogWorkout = { loggingOn = sheet.epochDay },
            programDay = state.programDay,
        )
    }
    state.moveSheet?.let { sheet ->
        MovePlanSheet(sheet, state.today, onMove = actions.onMovePlanned, onDismiss = actions.onCloseMove)
    }
    loggingOn?.let { day ->
        LogWorkoutSheet(
            choices = state.choices,
            flows = state.flows,
            epochDay = day,
            onLogWorkout = actions.onLogWorkout,
            onLogSport = actions.onLogSport,
            onDismiss = { loggingOn = null },
        )
    }
    addingOn?.let { day ->
        AddToPlanSheet(
            choices = state.choices,
            days = dayChoices(day, count = 1),
            onPlanWeekly = actions.onPlanWeekly,
            onPlanOnce = actions.onPlanOnce,
            onDismiss = { addingOn = null },
            repeat = PlanRepeat.Once,
            weekday = weekdayOf(day),
            epochDay = day,
            lift = LiftChoices(state.programDay),
            sports = true,
        )
    }
    LookAndPlanSheets(
        state,
        actions,
        looking = looking,
        onCloseLook = { looking = null },
        planning = planning,
        onClosePlan = { planning = false },
    )
}

/** A row of Coming up looked at before Start, by its place, and the title bar's + planning a workout. */
@Composable
private fun LookAndPlanSheets(
    state: HistoryUiState,
    actions: HistoryActions,
    looking: Int?,
    onCloseLook: () -> Unit,
    planning: Boolean,
    onClosePlan: () -> Unit,
) {
    looking?.let { state.upNext.getOrNull(it)?.preview() }?.let { preview ->
        PreviewHost(
            preview,
            today = state.today,
            onStartWorkout = { actions.onStart(it.name, it.config) },
            onStartLift = { actions.onStartLift(it?.program?.key) },
            onPlanWeekly = actions.onPlanWeekly,
            onPlanOnce = actions.onPlanOnce,
            onDone = onCloseLook,
        )
    }
    if (planning) {
        AddToPlanSheet(
            choices = state.choices,
            days = dayChoices(state.today),
            onPlanWeekly = actions.onPlanWeekly,
            onPlanOnce = actions.onPlanOnce,
            onDismiss = onClosePlan,
            weekday = weekdayOf(state.today),
            lift = LiftChoices(state.programDay),
            sports = true,
        )
    }
}

/** The page's side margins. */
private val Gutter = Modifier.padding(horizontal = Spacing.space16)

/**
 * The tab laid out in two: the title across the top, and under it the range and its summary in a column of
 * their own beside the log, a day after another down one column, newest first. Each scrolls by itself.
 */
@Composable
private fun SummaryBesideLog(
    title: String,
    titleActions: @Composable RowScope.() -> Unit,
    state: HistoryUiState,
    groups: List<Pair<LogGroup?, List<HistorySection>>>,
    actions: HistoryActions,
    selected: (HistoryItem) -> Boolean,
    onLook: (Int) -> Unit,
) {
    Box(Modifier.fillMaxSize().background(IntervalTheme.palette.background), contentAlignment = Alignment.TopCenter) {
        SetupLanes(Modifier.matchParentSize())
        Column(Modifier.widthIn(max = Sizes.listMax).fillMaxSize()) {
            TitleBar(title, actions = titleActions)
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.Center) {
                Column(
                    Modifier
                        .width(Sizes.listColumn)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = Spacing.space24),
                ) {
                    RangePicker(state.range, actions.onRange, Gutter.padding(bottom = Spacing.space16))
                    SummaryFor(state, actions, Gutter)
                    if (state.showsComingUp) {
                        ComingUp(
                            state.upNext,
                            actions,
                            onLook,
                            Gutter.padding(top = Spacing.space16),
                        )
                    }
                    if (state.showsUpNext) UpNextCard(state.upNext, actions, Gutter.padding(top = Spacing.space16))
                    if (state.showsMostDone) MostDone(state.mostDone, Gutter)
                }
                LazyColumn(
                    Modifier.weight(1f, fill = false).widthIn(max = Sizes.content).fillMaxHeight(),
                    contentPadding = PaddingValues(bottom = Spacing.space24),
                ) {
                    groups.forEach { (heading, sections) ->
                        heading?.let {
                            item(key = it.name, contentType = it) {
                                LogHeading(it, Modifier.animateItem().then(Gutter))
                            }
                        }
                        if (heading == LogGroup.ThisWeek) {
                            item(key = DONE_WEEK, contentType = DONE_WEEK) {
                                WeekDoneCard(sections, actions, selected, Modifier.animateItem().then(DoneWeekCard))
                            }
                        } else {
                            items(sections, key = { it.dayNumber }, contentType = { DAY_SECTION }) { section ->
                                DaySection(section, actions, selected, Modifier.animateItem().then(Gutter))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The log's days, in their groups under their headings on the week: this week's in one card across the line,
 * Earlier's and the other ranges' a card a day.
 */
private fun LazyStaggeredGridScope.logItems(
    groups: List<Pair<LogGroup?, List<HistorySection>>>,
    actions: HistoryActions,
    selected: (HistoryItem) -> Boolean,
) {
    groups.forEach { (heading, sections) ->
        heading?.let {
            item(key = it.name, contentType = it, span = StaggeredGridItemSpan.FullLine) {
                LogHeading(it, Modifier.animateItem().then(Gutter))
            }
        }
        if (heading == LogGroup.ThisWeek) {
            item(key = DONE_WEEK, contentType = DONE_WEEK, span = StaggeredGridItemSpan.FullLine) {
                WeekDoneCard(sections, actions, selected, Modifier.animateItem().then(DoneWeekCard))
            }
        } else {
            // A day is one cell, its label with its card, so a column never splits a day.
            items(sections, key = { it.dayNumber }, contentType = { DAY_SECTION }) { section ->
                DaySection(section, actions, selected, Modifier.animateItem().then(Gutter))
            }
        }
    }
}

/** The week's done card's key in the lists, and its place: in the gutters, under its heading's room. */
private const val DONE_WEEK = "doneWeek"
private const val DAY_SECTION = "day"
private val DoneWeekCard = Modifier.padding(horizontal = Spacing.space16).padding(top = Spacing.space8)

/** The range picker, its summary card, and Up next or the most done shapes under it. */
private fun LazyStaggeredGridScope.summaryItems(
    state: HistoryUiState,
    actions: HistoryActions,
    onLook: (Int) -> Unit,
) {
    item(key = "range", span = StaggeredGridItemSpan.FullLine) {
        RangePicker(
            state.range,
            actions.onRange,
            Modifier.animateItem().then(Gutter).padding(bottom = Spacing.space16),
        )
    }
    item(key = "summary", span = StaggeredGridItemSpan.FullLine) {
        SummaryFor(state, actions, Modifier.animateItem().then(Gutter))
    }
    if (state.showsComingUp) {
        item(key = "comingUp", span = StaggeredGridItemSpan.FullLine) {
            ComingUp(
                state.upNext,
                actions,
                onLook,
                Modifier.animateItem().then(Gutter).padding(top = Spacing.space16),
            )
        }
    }
    if (state.showsUpNext) {
        item(key = "upNext", span = StaggeredGridItemSpan.FullLine) {
            UpNextCard(
                state.upNext,
                actions,
                Modifier.animateItem().then(Gutter).padding(top = Spacing.space16),
            )
        }
    }
    if (state.showsMostDone) {
        item(key = "mostDone", span = StaggeredGridItemSpan.FullLine) {
            MostDone(state.mostDone, Modifier.animateItem().then(Gutter))
        }
    }
}

/** The summary card for the range: another range, or another month, fades through, one card's figures for the next. */
@Composable
private fun SummaryFor(state: HistoryUiState, actions: HistoryActions, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = state.range to state.month.title,
        modifier = modifier,
        transitionSpec = { fadeThroughContent() },
        label = "summary",
    ) { (range, _) ->
        // Each card takes only the figures it shows, so a day opened, or anything else the state carries, leaves
        // it as it was.
        when (range) {
            TrainingRange.Week -> WeekSummary(
                state.weekGoal,
                state.byKind,
                state.summary,
                state.week,
                { actions.onOpenDay(it) },
                actions.onPlan,
            )

            TrainingRange.Month -> MonthSummary(
                state.month,
                state.summary,
                actions.onShiftMonth,
                { actions.onOpenDay(it) },
            )

            TrainingRange.AllTime -> AllTimeSummary(state.summary, state.streak, state.goal, state.weeks)
        }
    }
}

/** The week lists what is coming up, every plan of the next seven days, while anything is planned. */
private val HistoryUiState.showsComingUp: Boolean
    get() = upNext.isNotEmpty() && range == TrainingRange.Week

/** The month shows what is up next under its calendar, while anything is planned. */
private val HistoryUiState.showsUpNext: Boolean
    get() = upNext.isNotEmpty() && range == TrainingRange.Month

/**
 * The log's days in their groups: on the week, this week's under "Done this week" and the rest under "Earlier";
 * on the month and all time, every day under no heading, as the summary above already says which.
 */
private fun HistoryUiState.logGroups(): List<Pair<LogGroup?, List<HistorySection>>> {
    if (range != TrainingRange.Week) return listOf(null to sections)
    val (week, earlier) = sections.partition { it.dayNumber >= weekStart }
    return listOfNotNull(
        week.takeIf { it.isNotEmpty() }?.let { LogGroup.ThisWeek to it },
        earlier.takeIf { it.isNotEmpty() }?.let { LogGroup.Earlier to it },
    )
}

/** A group of the log's days on the week. */
private enum class LogGroup { ThisWeek, Earlier }

/** A heading over a group of the log's days. */
@Composable
private fun LogHeading(group: LogGroup, modifier: Modifier = Modifier) {
    Text(
        stringResource(
            when (group) {
                LogGroup.ThisWeek -> Res.string.history_done_week
                LogGroup.Earlier -> Res.string.history_earlier
            },
        ),
        style = IntervalTheme.type.section,
        color = IntervalTheme.palette.text,
        modifier = modifier.padding(top = Spacing.space16).semantics { heading() },
    )
}

/** All time lists the shapes done most, once there are any. */
private val HistoryUiState.showsMostDone: Boolean
    get() = range == TrainingRange.AllTime && mostDone.isNotEmpty()

/** Before anything is done or planned: what History keeps, and the two ways to begin. */
@Composable
private fun FirstRun(actions: HistoryActions, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    SummaryCard(modifier) {
        Text(stringResource(Res.string.history_empty_title), style = IntervalTheme.type.title, color = palette.text)
        Text(stringResource(Res.string.history_empty), style = IntervalTheme.type.body, color = palette.textDim)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
            AccentButton(stringResource(Res.string.history_empty_plan), actions.onPlan, Modifier.fillMaxWidth())
            SecondaryButton(
                stringResource(Res.string.history_empty_start),
                actions.onShowTimer,
                Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A day's heading: "Today", "Yesterday", "Tomorrow", or the date. */
@Composable
internal fun dayHeading(day: HistoryDay): String = when (day) {
    HistoryDay.Today -> stringResource(Res.string.history_today)
    HistoryDay.Yesterday -> stringResource(Res.string.history_yesterday)
    HistoryDay.Tomorrow -> stringResource(Res.string.history_tomorrow)
    is HistoryDay.On -> day.date
}

/** How far into its program the preview's lift day is. */
private const val PREVIEW_DAYS_DONE = 3

/** Monday to Sunday, today a Thursday: two days done, one missed, and three still planned. */
private val PreviewWeek = listOf(
    DayState.Done to 240,
    DayState.Missed to 0,
    DayState.Done to 755,
    DayState.Planned to 0,
    DayState.Planned to 0,
    DayState.Rest to 0,
    DayState.Planned to 0,
).mapIndexed { index, (state, seconds) ->
    DayCell(20_738L + index, index + 1, 12 + index, state, seconds, today = index == 3)
}

@PreviewFormFactors
@Composable
private fun HistoryScreenPreview() {
    val (tabata, hiit) = builtInPresets
    IntervalTheme {
        HistoryScreen(
            state = HistoryUiState(
                sections = listOf(
                    HistorySection(
                        HistoryDay.On("Wednesday, Oct 14"),
                        dayNumber = 20_740,
                        listOf(
                            HistoryItem(
                                hiit.name,
                                hiit.config,
                                endedEarly = false,
                                "7:05 AM",
                                "9:40",
                                id = 2,
                                planned = true,
                            ),
                            HistoryItem(
                                null,
                                tabata.config.copy(workSeconds = 30),
                                endedEarly = false,
                                "6:15 PM",
                                "2:55",
                                id = 3,
                            ),
                        ),
                    ),
                ),
                summary = RangeSummary(
                    count = 3,
                    time = "16:35",
                    totalSeconds = 995,
                    calories = 149,
                    plannedDone = 2,
                    plannedDue = 3,
                ),
                weekCount = 3,
                goal = 4,
                streak = Streak(current = 5, best = 7),
                week = PreviewWeek,
                upNext = listOfNotNull(
                    program(ProgramId.EasyStrength).dayAfter(PREVIEW_DAYS_DONE)?.let {
                        UpNextItem.Lift(epochDay = 20_741, HistoryDay.Today, it)
                    },
                    UpNextItem.Planned(planId = 1, epochDay = 20_741, HistoryDay.Today, tabata.name, tabata.config),
                    UpNextItem.Planned(planId = 2, epochDay = 20_742, HistoryDay.Tomorrow, hiit.name, hiit.config),
                ),
                hasPlans = true,
            ),
            actions = HistoryActions(),
        )
    }
}
