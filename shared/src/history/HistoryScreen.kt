package dev.justinsmith.interval.history

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.common_cancel
import dev.justinsmith.interval.resources.duration_minutes
import dev.justinsmith.interval.resources.duration_seconds
import dev.justinsmith.interval.resources.history_clear_confirm
import dev.justinsmith.interval.resources.history_clear_menu
import dev.justinsmith.interval.resources.history_clear_text
import dev.justinsmith.interval.resources.history_clear_title
import dev.justinsmith.interval.resources.history_empty
import dev.justinsmith.interval.resources.history_empty_title
import dev.justinsmith.interval.resources.history_ended_early
import dev.justinsmith.interval.resources.history_last_7_days
import dev.justinsmith.interval.resources.history_more_options_cd
import dev.justinsmith.interval.resources.history_time
import dev.justinsmith.interval.resources.history_title
import dev.justinsmith.interval.resources.history_today
import dev.justinsmith.interval.resources.history_week_cd
import dev.justinsmith.interval.resources.history_week_day_cd
import dev.justinsmith.interval.resources.history_week_empty_cd
import dev.justinsmith.interval.resources.history_workouts_unit
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
import dev.justinsmith.interval.timer.SpokenDuration
import dev.justinsmith.interval.timer.builtInPresets
import dev.justinsmith.interval.timer.spokenDuration
import dev.justinsmith.interval.ui.ConfirmDialog
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.EmptyState
import dev.justinsmith.interval.ui.EnterEasing
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalMenu
import dev.justinsmith.interval.ui.IntervalMenuItem
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.StripeMark
import dev.justinsmith.interval.ui.TabGrid
import dev.justinsmith.interval.ui.configDescription
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.currentKoinScope

/** Resolves the tab's ViewModel and binds it to [HistoryScreen]. */
@Composable
fun HistoryRoute(modifier: Modifier = Modifier) {
    val scope = currentKoinScope()
    val viewModel = viewModel { HistoryViewModel(scope.get(), scope.get(), scope.get()) }
    val locale = Locale.current.toLanguageTag()
    LaunchedEffect(locale) { viewModel.setLocale(locale) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    HistoryScreen(state = state, onClear = viewModel::clear, modifier = modifier)
}

/**
 * Finished workouts by day, the last week's tally and a bar per day on top. Clearing lives in the
 * overflow menu, behind a confirmation.
 */
@Composable
fun HistoryScreen(state: HistoryUiState, onClear: () -> Unit, modifier: Modifier = Modifier) {
    var menu by remember { mutableStateOf(false) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    // One column on a phone; wider windows lay the days out side by side.
    TabGrid(
        stringResource(Res.string.history_title),
        modifier,
        actions = {
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
                    enabled = !state.isEmpty,
                )
            }
        },
    ) {
        if (state.isEmpty) {
            item(key = "empty", span = StaggeredGridItemSpan.FullLine) {
                ListCard(Modifier.animateItem().padding(horizontal = Spacing.space16)) {
                    EmptyState(
                        IntervalIcons.History,
                        stringResource(Res.string.history_empty_title),
                        stringResource(Res.string.history_empty),
                    )
                }
            }
        } else {
            item(key = "week", span = StaggeredGridItemSpan.FullLine) {
                WeekSummary(state, Modifier.animateItem().padding(horizontal = Spacing.space16))
            }
        }
        state.sections.forEach { section ->
            // A day is one cell, its label with its card, so a column never splits a day.
            item(key = section.dayNumber) {
                Column(Modifier.animateItem().padding(horizontal = Spacing.space16)) {
                    GroupLabel(dayHeading(section.day))
                    ListCard {
                        section.items.forEachIndexed { index, item ->
                            if (index > 0) RowDivider(Modifier.padding(start = Spacing.space20))
                            HistoryRow(item)
                        }
                    }
                }
            }
        }
    }
    if (confirming) {
        ConfirmDialog(
            title = stringResource(Res.string.history_clear_title),
            text = stringResource(Res.string.history_clear_text),
            confirm = stringResource(Res.string.history_clear_confirm),
            dismiss = stringResource(Res.string.common_cancel),
            onConfirm = {
                onClear()
                confirming = false
            },
            onDismiss = { confirming = false },
        )
    }
}

/** The week's count and time, over a bar for each of its seven days. */
@Composable
private fun WeekSummary(state: HistoryUiState, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Column(
        modifier
            .fillMaxWidth()
            .clip(Corners.radius18)
            .background(palette.surface)
            .padding(start = Spacing.space20, end = Spacing.space20, top = Spacing.space16, bottom = Spacing.space16),
        verticalArrangement = Arrangement.spacedBy(Spacing.space16),
    ) {
        val unit = pluralStringResource(Res.plurals.history_workouts_unit, state.weekCount)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.history_last_7_days),
                    style = IntervalTheme.type.caption,
                    color = palette.textDim,
                )
                Text(
                    buildAnnotatedString {
                        append(state.weekCount.toString())
                        withStyle(IntervalTheme.type.section.copy(color = palette.textDim).toSpanStyle()) {
                            append(" $unit")
                        }
                    },
                    style = IntervalTheme.type.stat,
                    color = palette.text,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.history_time),
                    style = IntervalTheme.type.caption,
                    color = palette.textDim,
                )
                Text(state.weekTotal, style = IntervalTheme.type.stat, color = palette.accent)
            }
        }
        WeekChart(state)
    }
}

/**
 * One bar per day, oldest first, scaled to the longest day; today's initial takes the accent. The
 * bars grow up from the baseline as the tab opens, one after another, and again as the week changes.
 */
@Composable
private fun WeekChart(state: HistoryUiState, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val max = state.weekMaxSeconds
    val description = weekDescription(state.week)
    Row(
        modifier.fillMaxWidth().semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
        verticalAlignment = Alignment.Bottom,
    ) {
        state.week.forEachIndexed { index, day ->
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.space8),
            ) {
                Box(Modifier.fillMaxWidth().height(Sizes.weekBar), contentAlignment = Alignment.BottomCenter) {
                    val share = if (max > 0) day.seconds.toFloat() / max else 0f
                    val grown = remember { Animatable(0f) }
                    LaunchedEffect(share) {
                        grown.animateTo(share, tween(Motion.ENTER_MILLIS, index * Motion.STAGGER_MILLIS, EnterEasing))
                    }
                    val height = if (day.seconds > 0) Sizes.weekBar * grown.value else Sizes.weekBarEmpty
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(height.coerceAtLeast(Sizes.weekBarEmpty))
                            .clip(Corners.radius12)
                            .background(if (day.seconds > 0) palette.accent else palette.surfaceVariant),
                    )
                }
                Text(
                    stringResource(WeekdayInitials[day.weekday - 1]),
                    style = IntervalTheme.type.caption,
                    color = if (day.today) palette.accent else palette.textDim,
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(item: HistoryItem, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.listRow)
            .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        StripeMark(IntervalTheme.palette.workMark, height = Sizes.historyMarkHeight)
        Column(Modifier.weight(1f)) {
            val shape = configDescription(item.config)
            val detail = listOfNotNull(
                shape.takeIf { item.presetName != null },
                stringResource(Res.string.history_ended_early).takeIf { item.endedEarly },
                item.time,
            )
            Text(item.presetName ?: shape, style = IntervalTheme.type.label, color = palette.text, maxLines = 1)
            Text(detail.joinToString(" · "), style = IntervalTheme.type.meta, color = palette.textDim)
        }
        Text(item.total, style = IntervalTheme.type.value, color = palette.text)
    }
}

/** A section's heading: "Today", "Yesterday", or the date. */
@Composable
private fun dayHeading(day: HistoryDay): String = when (day) {
    HistoryDay.Today -> stringResource(Res.string.history_today)
    HistoryDay.Yesterday -> stringResource(Res.string.history_yesterday)
    is HistoryDay.On -> day.date
}

/**
 * The week chart for screen readers: the days with any time, by name, each with its time in seconds
 * under a minute and minutes from one. "Time per day this week: Tuesday 45 seconds, Today 4 minutes".
 */
@Composable
internal fun weekDescription(week: List<DayTally>): String {
    val days = week.filter { it.seconds > 0 }
    if (days.isEmpty()) return stringResource(Res.string.history_week_empty_cd)
    val parts = days.map { day ->
        val name = if (day.today) Res.string.history_today else WeekdayNames[day.weekday - 1]
        val time = when (val duration = spokenDuration(day.seconds)) {
            is SpokenDuration.Seconds -> pluralStringResource(
                Res.plurals.duration_seconds,
                duration.seconds,
                duration.seconds,
            )

            is SpokenDuration.Minutes -> pluralStringResource(
                Res.plurals.duration_minutes,
                duration.minutes,
                duration.minutes,
            )
        }
        stringResource(Res.string.history_week_day_cd, stringResource(name), time)
    }
    return stringResource(Res.string.history_week_cd, parts.joinToString(", "))
}

/** Monday first, as [DayTally.weekday] counts. */
private val WeekdayNames = listOf(
    Res.string.weekday_1,
    Res.string.weekday_2,
    Res.string.weekday_3,
    Res.string.weekday_4,
    Res.string.weekday_5,
    Res.string.weekday_6,
    Res.string.weekday_7,
)

private val WeekdayInitials = listOf(
    Res.string.weekday_initial_1,
    Res.string.weekday_initial_2,
    Res.string.weekday_initial_3,
    Res.string.weekday_initial_4,
    Res.string.weekday_initial_5,
    Res.string.weekday_initial_6,
    Res.string.weekday_initial_7,
)

/** Thursday to Wednesday: a hangboard session, the two from yesterday, and today's Tabata. */
private val PreviewWeek = listOf(
    DayTally(weekday = 4, seconds = 320, today = false),
    DayTally(weekday = 5, seconds = 0, today = false),
    DayTally(weekday = 6, seconds = 0, today = false),
    DayTally(weekday = 7, seconds = 0, today = false),
    DayTally(weekday = 1, seconds = 0, today = false),
    DayTally(weekday = 2, seconds = 860, today = false),
    DayTally(weekday = 3, seconds = 240, today = true),
)

@PreviewFormFactors
@Composable
private fun HistoryScreenPreview() {
    val (tabata, hiit) = builtInPresets
    IntervalTheme {
        HistoryScreen(
            state = HistoryUiState(
                sections = listOf(
                    HistorySection(
                        HistoryDay.Today,
                        dayNumber = 2,
                        listOf(HistoryItem(tabata.name, tabata.config, endedEarly = false, "7:42 AM", "4:00")),
                    ),
                    HistorySection(
                        HistoryDay.Yesterday,
                        dayNumber = 1,
                        listOf(
                            HistoryItem(
                                null,
                                tabata.config.copy(workSeconds = 25),
                                endedEarly = false,
                                "6:15 PM",
                                "4:40",
                            ),
                            HistoryItem(hiit.name, hiit.config, endedEarly = true, "7:05 AM", "6:20"),
                        ),
                    ),
                ),
                weekCount = 3,
                weekTotal = "18:20",
                week = PreviewWeek,
            ),
            onClear = {},
        )
    }
}
