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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.intl.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.justinsmith.bells.domain.ProgramId
import dev.justinsmith.bells.domain.ProgramKey
import dev.justinsmith.bells.domain.program
import dev.justinsmith.interval.history.resources.Res
import dev.justinsmith.interval.history.resources.common_cancel
import dev.justinsmith.interval.history.resources.history_clear_confirm
import dev.justinsmith.interval.history.resources.history_clear_menu
import dev.justinsmith.interval.history.resources.history_clear_text
import dev.justinsmith.interval.history.resources.history_clear_title
import dev.justinsmith.interval.history.resources.history_empty
import dev.justinsmith.interval.history.resources.history_empty_plan
import dev.justinsmith.interval.history.resources.history_empty_start
import dev.justinsmith.interval.history.resources.history_empty_title
import dev.justinsmith.interval.history.resources.history_more_options_cd
import dev.justinsmith.interval.history.resources.history_plan_cd
import dev.justinsmith.interval.history.resources.history_title
import dev.justinsmith.interval.history.resources.history_today
import dev.justinsmith.interval.history.resources.history_tomorrow
import dev.justinsmith.interval.history.resources.history_yesterday
import dev.justinsmith.interval.history.resources.notice_undo
import dev.justinsmith.interval.plan.AddToPlanSheet
import dev.justinsmith.interval.plan.PlanRepeat
import dev.justinsmith.interval.plan.dayChoices
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.builtInPresets
import dev.justinsmith.interval.timer.weekdayOf
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.ConfirmDialog
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalMenu
import dev.justinsmith.interval.ui.IntervalMenuItem
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.LocalNotices
import dev.justinsmith.interval.ui.NoticeAction
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.SecondaryButton
import dev.justinsmith.interval.ui.SetupLanes
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.TabGrid
import dev.justinsmith.interval.ui.TitleBar
import dev.justinsmith.interval.ui.fadeThroughContent
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.currentKoinScope

/**
 * Resolves the tab's ViewModel and binds it to [HistoryScreen]. Starting a workout puts it on the Timer,
 * then [onStartWorkout]; [onOpenWorkout] opens one finished workout, [onOpenLift] one kettlebell session,
 * [onPlan] the plan, [onShowTimer] the Timer tab. Starting the lift program's day opens [onStartSession].
 * [selectedWorkout] or [selectedLift] is the one open beside the log, on a wide window.
 */
@Composable
fun HistoryRoute(
    onOpenWorkout: (Long) -> Unit,
    onOpenLift: (String) -> Unit,
    onPlan: () -> Unit,
    onStartWorkout: () -> Unit,
    onShowTimer: () -> Unit,
    onStartSession: () -> Unit,
    modifier: Modifier = Modifier,
    selectedWorkout: Long? = null,
    selectedLift: String? = null,
) {
    val scope = currentKoinScope()
    val viewModel = viewModel { HistoryViewModel(scope.get(), scope.get(), scope.get(), scope.get()) }
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
    val notices = LocalNotices.current
    val undo = stringResource(Res.string.notice_undo)
    val actions = remember(viewModel, notices, undo) {
        // Every way off the day sheet to another page closes it first, so it isn't open on the way back.
        fun leavingSheet(go: () -> Unit) {
            viewModel.openDay(null)
            go()
        }
        HistoryActions(
            onClear = viewModel::clear,
            onRange = viewModel::setRange,
            onShiftMonth = viewModel::shiftMonth,
            onOpenDay = viewModel::openDay,
            onStart = { name, config ->
                viewModel.putOnTimer(name, config)
                leavingSheet(startWorkout)
            },
            onRemovePlanned = { planId, removedText ->
                viewModel.removePlanned(planId)?.let { plan ->
                    notices.show(removedText, NoticeAction(undo) { viewModel.restorePlanned(plan) })
                }
            },
            onPlanWeekly = viewModel::planWeekly,
            onPlanOnce = viewModel::planOnce,
            onOpenWorkout = { leavingSheet { openWorkout(it) } },
            onPlan = { leavingSheet(plan) },
            onShowTimer = { showTimer() },
            onOpenLift = { leavingSheet { openLift(it) } },
            onStartLift = { program ->
                program?.let(viewModel::startLift)
                leavingSheet(startSession)
            },
        )
    }
    HistoryScreen(state, actions, modifier, selectedWorkout, selectedLift)
}

/** What the History tab's controls do. Each defaults to nothing, so a preview or test sets only its own. */
@Immutable
class HistoryActions(
    val onClear: () -> Unit = {},
    val onRange: (TrainingRange) -> Unit = {},
    val onShiftMonth: (Int) -> Unit = {},
    /** Opens the day sheet on an epoch day, or closes it with null. */
    val onOpenDay: (Long?) -> Unit = {},
    /** Starts a workout of this name and shape now. */
    val onStart: (String, IntervalConfig) -> Unit = { _, _ -> },
    /** Takes a one-off off the plan, with the line saying so ("Removed Tabata"). */
    val onRemovePlanned: (planId: Long, removedText: String) -> Unit = { _, _ -> },
    val onPlanWeekly: (Preset, Set<Int>) -> Unit = { _, _ -> },
    val onPlanOnce: (Preset, Long) -> Unit = { _, _ -> },
    val onOpenWorkout: (Long) -> Unit = {},
    val onPlan: () -> Unit = {},
    val onShowTimer: () -> Unit = {},
    /** Opens a kettlebell session's page, by its uid. */
    val onOpenLift: (String) -> Unit = {},
    /** Opens Lifting: on a new day of this program, or with null on the session in progress. */
    val onStartLift: (ProgramKey?) -> Unit = {},
)

/**
 * The training log. A range picker leads to this week (goal, streak and a block per day), a month's
 * calendar, or all time; under it, what is planned next and every finished workout by day. A day opens
 * a sheet of its plans and workouts. Clearing lives in the overflow menu, behind a confirmation. The row of
 * [selectedWorkout] or [selectedLift] shows it is the one open beside the log.
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
    var menu by remember { mutableStateOf(false) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    // The day the day sheet's Add workout is planning on, while its add sheet is open.
    var addingOn by rememberSaveable { mutableStateOf<Long?>(null) }
    val title = stringResource(Res.string.history_title)
    val titleActions: @Composable RowScope.() -> Unit = {
        IconAction(IntervalIcons.Plan, stringResource(Res.string.history_plan_cd), actions.onPlan)
        IntervalMenu(
            expanded = menu,
            onExpandedChange = { menu = it },
            items = {
                IntervalMenuItem(stringResource(Res.string.history_clear_menu), onClick = { confirming = true })
            },
        ) {
            IconAction(
                IntervalIcons.More,
                stringResource(Res.string.history_more_options_cd),
                onClick = { menu = true },
                enabled = state.sections.isNotEmpty(),
            )
        }
    }
    val selected = { item: HistoryItem ->
        (item.id != null && item.id == selectedWorkout) || (item.liftUid != null && item.liftUid == selectedLift)
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth >= Sizes.listColumn * 2 && state.sections.isNotEmpty()) {
            SummaryBesideLog(title, titleActions, state, actions, selected)
        } else {
            TabGrid(title, actions = titleActions) {
                if (state.isEmpty) {
                    item(key = "empty", span = StaggeredGridItemSpan.FullLine) {
                        FirstRun(actions, Modifier.animateItem().then(Gutter))
                    }
                } else {
                    summaryItems(state, actions)
                }
                state.sections.forEach { section ->
                    // A day is one cell, its label with its card, so a column never splits a day.
                    item(key = section.dayNumber) {
                        DaySection(section, actions, selected, Modifier.animateItem().then(Gutter))
                    }
                }
            }
        }
    }
    state.daySheet?.let { sheet -> DaySheetContent(sheet, actions, onAddWorkout = { addingOn = sheet.epochDay }) }
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
        )
    }
    if (confirming) {
        ConfirmDialog(
            title = stringResource(Res.string.history_clear_title),
            text = stringResource(Res.string.history_clear_text),
            confirm = stringResource(Res.string.history_clear_confirm),
            dismiss = stringResource(Res.string.common_cancel),
            onConfirm = {
                actions.onClear()
                confirming = false
            },
            onDismiss = { confirming = false },
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
    actions: HistoryActions,
    selected: (HistoryItem) -> Boolean,
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
                    if (state.showsUpNext) UpNextCard(state.upNext, actions, Gutter.padding(top = Spacing.space16))
                    if (state.showsMostDone) MostDone(state.mostDone, Gutter)
                }
                LazyColumn(
                    Modifier.weight(1f, fill = false).widthIn(max = Sizes.content).fillMaxHeight(),
                    contentPadding = PaddingValues(bottom = Spacing.space24),
                ) {
                    items(state.sections, key = { it.dayNumber }) { section ->
                        DaySection(section, actions, selected, Modifier.animateItem().then(Gutter))
                    }
                }
            }
        }
    }
}

/** The range picker, its summary card, and Up next or the most done shapes under it. */
private fun LazyStaggeredGridScope.summaryItems(state: HistoryUiState, actions: HistoryActions) {
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
        when (range) {
            TrainingRange.Week -> WeekSummary(state, { actions.onOpenDay(it) }, actions.onPlan)
            TrainingRange.Month -> MonthSummary(state, actions.onShiftMonth, { actions.onOpenDay(it) })
            TrainingRange.AllTime -> AllTimeSummary(state)
        }
    }
}

/** Up next shows under the week and the month, while anything is planned. */
private val HistoryUiState.showsUpNext: Boolean
    get() = upNext.isNotEmpty() && range != TrainingRange.AllTime

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
