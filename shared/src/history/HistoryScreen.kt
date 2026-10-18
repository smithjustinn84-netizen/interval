package dev.justinsmith.interval.history

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.intl.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.justinsmith.interval.plan.AddToPlanSheet
import dev.justinsmith.interval.plan.DayChoice
import dev.justinsmith.interval.plan.PlanRepeat
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.common_cancel
import dev.justinsmith.interval.resources.common_joined
import dev.justinsmith.interval.resources.history_calories_value
import dev.justinsmith.interval.resources.history_clear_confirm
import dev.justinsmith.interval.resources.history_clear_menu
import dev.justinsmith.interval.resources.history_clear_text
import dev.justinsmith.interval.resources.history_clear_title
import dev.justinsmith.interval.resources.history_edit_plan
import dev.justinsmith.interval.resources.history_empty
import dev.justinsmith.interval.resources.history_empty_plan
import dev.justinsmith.interval.resources.history_empty_start
import dev.justinsmith.interval.resources.history_empty_title
import dev.justinsmith.interval.resources.history_ended_early
import dev.justinsmith.interval.resources.history_more_options_cd
import dev.justinsmith.interval.resources.history_most_done
import dev.justinsmith.interval.resources.history_plan_cd
import dev.justinsmith.interval.resources.history_planned
import dev.justinsmith.interval.resources.history_start
import dev.justinsmith.interval.resources.history_then
import dev.justinsmith.interval.resources.history_times
import dev.justinsmith.interval.resources.history_title
import dev.justinsmith.interval.resources.history_today
import dev.justinsmith.interval.resources.history_tomorrow
import dev.justinsmith.interval.resources.history_up_next
import dev.justinsmith.interval.resources.history_yesterday
import dev.justinsmith.interval.resources.weekday_1
import dev.justinsmith.interval.resources.weekday_2
import dev.justinsmith.interval.resources.weekday_3
import dev.justinsmith.interval.resources.weekday_4
import dev.justinsmith.interval.resources.weekday_5
import dev.justinsmith.interval.resources.weekday_6
import dev.justinsmith.interval.resources.weekday_7
import dev.justinsmith.interval.resources.weekday_initial_1
import dev.justinsmith.interval.resources.weekday_initial_2
import dev.justinsmith.interval.resources.weekday_initial_3
import dev.justinsmith.interval.resources.weekday_initial_4
import dev.justinsmith.interval.resources.weekday_initial_5
import dev.justinsmith.interval.resources.weekday_initial_6
import dev.justinsmith.interval.resources.weekday_initial_7
import dev.justinsmith.interval.resources.weekday_short_1
import dev.justinsmith.interval.resources.weekday_short_2
import dev.justinsmith.interval.resources.weekday_short_3
import dev.justinsmith.interval.resources.weekday_short_4
import dev.justinsmith.interval.resources.weekday_short_5
import dev.justinsmith.interval.resources.weekday_short_6
import dev.justinsmith.interval.resources.weekday_short_7
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.builtInPresets
import dev.justinsmith.interval.timer.dateOfEpochDay
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.timer.weekdayOf
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.ConfirmDialog
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalMenu
import dev.justinsmith.interval.ui.IntervalMenuItem
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.SecondaryButton
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.StripeMark
import dev.justinsmith.interval.ui.TabGrid
import dev.justinsmith.interval.ui.pressScale
import dev.justinsmith.interval.ui.surfaceButton
import dev.justinsmith.interval.ui.workoutDescription
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.currentKoinScope

/**
 * Resolves the tab's ViewModel and binds it to [HistoryScreen]. Starting a workout puts it on the Timer,
 * then [onStartWorkout]; [onOpenWorkout] opens one finished workout, [onPlan] the plan, [onShowTimer] the
 * Timer tab.
 */
@Composable
fun HistoryRoute(
    onOpenWorkout: (Long) -> Unit,
    onPlan: () -> Unit,
    onStartWorkout: () -> Unit,
    onShowTimer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = currentKoinScope()
    val viewModel = viewModel { HistoryViewModel(scope.get(), scope.get(), scope.get()) }
    val locale = Locale.current.toLanguageTag()
    LaunchedEffect(locale) { viewModel.setLocale(locale) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = remember(viewModel) {
        HistoryActions(
            onClear = viewModel::clear,
            onRange = viewModel::setRange,
            onShiftMonth = viewModel::shiftMonth,
            onOpenDay = viewModel::openDay,
            onStart = { name, config ->
                viewModel.putOnTimer(name, config)
                viewModel.openDay(null)
                onStartWorkout()
            },
            onRemovePlanned = viewModel::removePlanned,
            onPlanWeekly = viewModel::planWeekly,
            onPlanOnce = viewModel::planOnce,
            onOpenWorkout = {
                viewModel.openDay(null)
                onOpenWorkout(it)
            },
            onPlan = {
                viewModel.openDay(null)
                onPlan()
            },
            onShowTimer = onShowTimer,
        )
    }
    HistoryScreen(state = state, actions = actions, modifier = modifier)
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
    val onRemovePlanned: (Long) -> Unit = {},
    val onPlanWeekly: (Preset, Set<Int>) -> Unit = { _, _ -> },
    val onPlanOnce: (Preset, Long) -> Unit = { _, _ -> },
    val onOpenWorkout: (Long) -> Unit = {},
    val onPlan: () -> Unit = {},
    val onShowTimer: () -> Unit = {},
)

/**
 * The training log. A range picker leads to this week (goal, streak and a block per day), a month's
 * calendar, or all time; under it, what is planned next and every finished workout by day. A day opens
 * a sheet of its plans and workouts. Clearing lives in the overflow menu, behind a confirmation.
 */
@Composable
fun HistoryScreen(state: HistoryUiState, actions: HistoryActions, modifier: Modifier = Modifier) {
    var menu by remember { mutableStateOf(false) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    // The day the day sheet's Add workout is planning on, while its add sheet is open.
    var addingOn by rememberSaveable { mutableStateOf<Long?>(null) }
    val gutter = Modifier.padding(horizontal = Spacing.space16)
    // One column on a phone; wider windows lay the days out side by side.
    TabGrid(
        stringResource(Res.string.history_title),
        modifier,
        actions = {
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
        },
    ) {
        if (state.isEmpty) {
            item(key = "empty", span = StaggeredGridItemSpan.FullLine) {
                FirstRun(actions, Modifier.animateItem().then(gutter))
            }
        } else {
            summaryItems(state, actions, gutter)
        }
        state.sections.forEach { section ->
            // A day is one cell, its label with its card, so a column never splits a day.
            item(key = section.dayNumber) {
                Column(Modifier.animateItem().then(gutter)) {
                    GroupLabel(dayHeading(section.day))
                    ListCard {
                        section.items.forEachIndexed { index, item ->
                            if (index > 0) RowDivider(Modifier.padding(start = Spacing.space20))
                            HistoryRow(item, actions.onOpenWorkout)
                        }
                    }
                }
            }
        }
    }
    state.daySheet?.let { sheet -> DaySheetContent(sheet, actions, onAddWorkout = { addingOn = sheet.epochDay }) }
    addingOn?.let { day ->
        AddToPlanSheet(
            choices = state.choices,
            days = listOf(DayChoice(day, weekdayOf(day), dateOfEpochDay(day).day)),
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

/** The range picker, its summary card, and Up next or the most done shapes under it. */
private fun LazyStaggeredGridScope.summaryItems(state: HistoryUiState, actions: HistoryActions, gutter: Modifier) {
    item(key = "range", span = StaggeredGridItemSpan.FullLine) {
        RangePicker(
            state.range,
            actions.onRange,
            Modifier.animateItem().then(gutter).padding(bottom = Spacing.space16),
        )
    }
    item(key = "summary", span = StaggeredGridItemSpan.FullLine) {
        val card = Modifier.animateItem().then(gutter)
        when (state.range) {
            TrainingRange.Week -> WeekSummary(state, { actions.onOpenDay(it) }, actions.onPlan, card)
            TrainingRange.Month -> MonthSummary(state, actions.onShiftMonth, { actions.onOpenDay(it) }, card)
            TrainingRange.AllTime -> AllTimeSummary(state, card)
        }
    }
    if (state.upNext.isNotEmpty() && state.range != TrainingRange.AllTime) {
        item(key = "upNext", span = StaggeredGridItemSpan.FullLine) {
            UpNextCard(
                state.upNext,
                actions,
                Modifier.animateItem().then(gutter).padding(top = Spacing.space16),
            )
        }
    }
    if (state.range == TrainingRange.AllTime && state.mostDone.isNotEmpty()) {
        item(key = "mostDone", span = StaggeredGridItemSpan.FullLine) {
            MostDone(state.mostDone, Modifier.animateItem().then(gutter))
        }
    }
}

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

/** The next plan with a way to start it, the two after it in a line, and the way to the plan. */
@Composable
private fun UpNextCard(upNext: List<UpNextItem>, actions: HistoryActions, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val next = upNext.first()
    ListCard(modifier) {
        Column(
            Modifier.padding(
                start = Spacing.space20,
                end = Spacing.space16,
                top = Spacing.space12,
                bottom = Spacing.space12,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.space8),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GroupLabelText(stringResource(Res.string.history_up_next, dayHeading(next.day)), Modifier.weight(1f))
                val interaction = remember { MutableInteractionSource() }
                Text(
                    stringResource(Res.string.history_edit_plan),
                    style = IntervalTheme.type.label,
                    color = palette.accent,
                    modifier = Modifier
                        .heightIn(min = Sizes.segment)
                        .clickable(interaction, LocalIndication.current, role = Role.Button, onClick = actions.onPlan)
                        .pressScale(interaction)
                        .padding(vertical = Spacing.space12),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
            ) {
                StripeMark(palette.workMark, height = Sizes.historyMarkHeight)
                Column(Modifier.weight(1f)) {
                    Text(next.name, style = IntervalTheme.type.label, color = palette.text)
                    Text(
                        stringResource(
                            Res.string.common_joined,
                            workoutDescription(next.config),
                            formatClock(next.config.totalSeconds),
                        ),
                        style = IntervalTheme.type.meta,
                        color = palette.textDim,
                    )
                }
                StartPill(primary = true, onClick = { actions.onStart(next.name, next.config) })
            }
            val later = upNext.drop(1).take(2)
            if (later.isNotEmpty()) {
                val parts = later.map {
                    stringResource(Res.string.common_joined, shortDay(it.day, it.epochDay), it.name)
                }
                Text(
                    stringResource(
                        Res.string.history_then,
                        parts.reduce { all, part ->
                            stringResource(Res.string.common_joined, all, part)
                        },
                    ),
                    style = IntervalTheme.type.meta,
                    color = palette.textDim,
                )
            }
        }
    }
}

/** A group heading's look, inside a card. */
@Composable
private fun GroupLabelText(text: String, modifier: Modifier = Modifier) {
    Text(text, style = IntervalTheme.type.caption, color = IntervalTheme.palette.textDim, modifier = modifier)
}

/** Start, as a small pill: accent when [primary], on a quiet fill otherwise. */
@Composable
internal fun StartPill(primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .heightIn(min = Sizes.segment)
            .surfaceButton(interaction, Corners.full, if (primary) palette.accent else palette.surfaceVariant, onClick)
            .padding(horizontal = Spacing.space16),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        val ink = if (primary) palette.onAccent else palette.text
        if (primary) Icon(IntervalIcons.Play, null, tint = ink, modifier = Modifier.size(Sizes.chipIcon))
        Text(stringResource(Res.string.history_start), style = IntervalTheme.type.label, color = ink)
    }
}

/** All time's most done shapes, by preset name where one matches, with how often. */
@Composable
private fun MostDone(rows: List<ShapeRow>, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Column(modifier.padding(top = Spacing.space8)) {
        GroupLabel(stringResource(Res.string.history_most_done))
        ListCard {
            rows.forEachIndexed { index, row ->
                if (index > 0) RowDivider(Modifier.padding(start = Spacing.space20))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Sizes.listRow)
                        .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
                ) {
                    StripeMark(palette.workMark, height = Sizes.historyMarkHeight)
                    Column(Modifier.weight(1f)) {
                        val shape = workoutDescription(row.config)
                        Text(row.name ?: shape, style = IntervalTheme.type.label, color = palette.text)
                        Text(
                            if (row.name !=
                                null
                            ) {
                                stringResource(Res.string.common_joined, shape, longTotal(row.totalSeconds))
                            } else {
                                longTotal(row.totalSeconds)
                            },
                            style = IntervalTheme.type.meta,
                            color = palette.textDim,
                        )
                    }
                    Text(
                        stringResource(Res.string.history_times, row.count),
                        style = IntervalTheme.type.value,
                        color = palette.text,
                    )
                }
            }
        }
    }
}

/** One finished workout. It opens when it has been stored, with a chevron saying so. */
@Composable
internal fun HistoryRow(item: HistoryItem, onOpen: (Long) -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val interaction = remember { MutableInteractionSource() }
    val id = item.id
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.listRow)
            .then(
                if (id == null) {
                    Modifier
                } else {
                    Modifier
                        .clickable(interaction, LocalIndication.current, role = Role.Button) { onOpen(id) }
                        .pressScale(interaction)
                },
            )
            .padding(start = Spacing.space16, end = Spacing.space12, top = Spacing.space12, bottom = Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        StripeMark(palette.workMark, height = Sizes.historyMarkHeight)
        Column(Modifier.weight(1f)) {
            val shape = workoutDescription(item.config)
            val detail = listOfNotNull(
                stringResource(Res.string.history_planned).takeIf { item.planned },
                shape.takeIf { item.presetName != null },
                stringResource(Res.string.history_ended_early).takeIf { item.endedEarly },
                item.calories?.let { stringResource(Res.string.history_calories_value, it) },
                item.time,
            )
            Text(item.presetName ?: shape, style = IntervalTheme.type.label, color = palette.text)
            Text(
                detail.reduce { joined, part -> stringResource(Res.string.common_joined, joined, part) },
                style = IntervalTheme.type.meta,
                color = palette.textDim,
            )
        }
        Text(item.total, style = IntervalTheme.type.value, color = palette.text)
        if (id != null) {
            Icon(IntervalIcons.Chevron, null, tint = palette.textDim, modifier = Modifier.size(Sizes.icon))
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

/** A day in a line of them: "Today", "Tomorrow", else its weekday, "Fri". */
@Composable
private fun shortDay(day: HistoryDay, epochDay: Long): String = when (day) {
    HistoryDay.Today, HistoryDay.Tomorrow, HistoryDay.Yesterday -> dayHeading(day)
    is HistoryDay.On -> stringResource(WeekdayShort[weekdayOf(epochDay) - 1])
}

/** Monday first, as [DayCell.weekday] counts. */
internal val WeekdayNames = listOf(
    Res.string.weekday_1,
    Res.string.weekday_2,
    Res.string.weekday_3,
    Res.string.weekday_4,
    Res.string.weekday_5,
    Res.string.weekday_6,
    Res.string.weekday_7,
)

internal val WeekdayInitials = listOf(
    Res.string.weekday_initial_1,
    Res.string.weekday_initial_2,
    Res.string.weekday_initial_3,
    Res.string.weekday_initial_4,
    Res.string.weekday_initial_5,
    Res.string.weekday_initial_6,
    Res.string.weekday_initial_7,
)

/** "Mon" to "Sun". */
internal val WeekdayShort = listOf(
    Res.string.weekday_short_1,
    Res.string.weekday_short_2,
    Res.string.weekday_short_3,
    Res.string.weekday_short_4,
    Res.string.weekday_short_5,
    Res.string.weekday_short_6,
    Res.string.weekday_short_7,
)

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
                upNext = listOf(
                    UpNextItem(planId = 1, epochDay = 20_741, HistoryDay.Today, tabata.name, tabata.config),
                    UpNextItem(planId = 2, epochDay = 20_742, HistoryDay.Tomorrow, hiit.name, hiit.config),
                ),
                hasPlans = true,
            ),
            actions = HistoryActions(),
        )
    }
}
