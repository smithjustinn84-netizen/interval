package dev.justinsmith.interval.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.ui.ConfirmDialog
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalMenu
import dev.justinsmith.interval.ui.IntervalMenuItem
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.PhaseColors
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.TitleBar
import org.koin.compose.currentKoinScope

/** Resolves the tab's ViewModel and binds it to [HistoryScreen]. */
@Composable
fun HistoryRoute(modifier: Modifier = Modifier) {
    val scope = currentKoinScope()
    val viewModel = viewModel { HistoryViewModel(scope.get(), scope.get(), scope.get<WallClock>()::nowMillis) }
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
    Box(modifier.fillMaxSize().background(IntervalTheme.palette.background), contentAlignment = Alignment.TopCenter) {
        // One column on a phone; wider windows lay the days out side by side.
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(Sizes.listColumn),
            modifier = Modifier.widthIn(max = Sizes.listMax).fillMaxSize(),
            contentPadding = PaddingValues(bottom = Spacing.space24),
        ) {
            item(span = StaggeredGridItemSpan.FullLine) {
                TitleBar("History") {
                    IntervalMenu(
                        expanded = menu,
                        onExpandedChange = { menu = it },
                        items = { IntervalMenuItem("Clear history", onClick = { confirming = true }) },
                    ) {
                        IconAction(
                            IntervalIcons.More,
                            "More options",
                            onClick = { menu = true },
                            enabled = !state.isEmpty,
                        )
                    }
                }
            }
            if (state.isEmpty) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    ListCard(Modifier.padding(horizontal = Spacing.space16)) {
                        Text(
                            "Finished workouts show up here.",
                            style = IntervalTheme.type.body,
                            color = IntervalTheme.palette.textDim,
                            modifier = Modifier.padding(horizontal = Spacing.space16, vertical = Spacing.space14),
                        )
                    }
                }
            } else {
                item(span = StaggeredGridItemSpan.FullLine) {
                    WeekSummary(state, Modifier.padding(horizontal = Spacing.space16))
                }
            }
            state.sections.forEach { section ->
                // A day is one cell, its label with its card, so a column never splits a day.
                item(key = section.day) {
                    Column(Modifier.padding(horizontal = Spacing.space16)) {
                        GroupLabel(section.day)
                        ListCard {
                            section.items.forEachIndexed { index, item ->
                                if (index > 0) RowDivider(Modifier.padding(start = Spacing.space18))
                                HistoryRow(item)
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirming) {
        ConfirmDialog(
            title = "Clear history?",
            text = "Every finished workout is removed. This can't be undone.",
            confirm = "Clear",
            dismiss = "Cancel",
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
            .padding(start = Spacing.space20, end = Spacing.space20, top = Spacing.space16, bottom = Spacing.space14),
        verticalArrangement = Arrangement.spacedBy(Spacing.space14),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
            Column(Modifier.weight(1f)) {
                Text("Last 7 days", style = IntervalTheme.type.caption, color = palette.textDim)
                Text(
                    buildAnnotatedString {
                        append(state.weekCount.toString())
                        withStyle(IntervalTheme.type.weekStatUnit.copy(color = palette.textDim).toSpanStyle()) {
                            append(if (state.weekCount == 1) " workout" else " workouts")
                        }
                    },
                    style = IntervalTheme.type.weekStat,
                    color = palette.text,
                )
            }
            Column(Modifier.weight(1f)) {
                Text("Time", style = IntervalTheme.type.caption, color = palette.textDim)
                Text(state.weekTotal, style = IntervalTheme.type.weekStat, color = palette.accent)
            }
        }
        WeekChart(state)
    }
}

/** One bar per day, oldest first, scaled to the longest day; today's initial takes the accent. */
@Composable
private fun WeekChart(state: HistoryUiState, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val max = state.weekMaxSeconds
    Row(
        modifier.fillMaxWidth().semantics { contentDescription = state.weekDescription },
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
        verticalAlignment = Alignment.Bottom,
    ) {
        state.week.forEach { day ->
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.space6),
            ) {
                Box(Modifier.fillMaxWidth().height(Sizes.weekBar), contentAlignment = Alignment.BottomCenter) {
                    val share = if (max > 0) day.seconds.toFloat() / max else 0f
                    val height = if (day.seconds > 0) Sizes.weekBar * share else Sizes.weekBarEmpty
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(height.coerceAtLeast(Sizes.weekBarEmpty))
                            .clip(Corners.radius10)
                            .background(if (day.seconds > 0) palette.accent else palette.surfaceVariant),
                    )
                }
                Text(
                    day.label,
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
            .padding(horizontal = Spacing.space16, vertical = Spacing.space10),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        Box(Modifier.size(Sizes.stripe, Sizes.historyMarkHeight).clip(Corners.full).background(PhaseColors.work))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = IntervalTheme.type.cardTitle, color = palette.text, maxLines = 1)
            Text(item.detail, style = IntervalTheme.type.hint, color = palette.textDim)
        }
        Text(item.total, style = IntervalTheme.type.historyTotal, color = palette.text)
    }
}

/** Thursday to Wednesday: a hangboard session, the two from yesterday, and today's Tabata. */
private val PreviewWeek = listOf(
    DayTally("T", "Thursday", seconds = 320, today = false),
    DayTally("F", "Friday", seconds = 0, today = false),
    DayTally("S", "Saturday", seconds = 0, today = false),
    DayTally("S", "Sunday", seconds = 0, today = false),
    DayTally("M", "Monday", seconds = 0, today = false),
    DayTally("T", "Tuesday", seconds = 860, today = false),
    DayTally("W", "Today", seconds = 240, today = true),
)

@PreviewFormFactors
@Composable
private fun HistoryScreenPreview() {
    IntervalTheme {
        HistoryScreen(
            state = HistoryUiState(
                sections = listOf(
                    HistorySection("Today", listOf(HistoryItem("Tabata", "20s on · 10s off · ×8 · 7:42 AM", "4:00"))),
                    HistorySection(
                        "Yesterday",
                        listOf(
                            HistoryItem("25s on · 10s off · ×8", "6:15 PM", "4:40"),
                            HistoryItem("HIIT 30/30", "30s on · 30s off · ×10 · 7:05 AM", "9:40"),
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
