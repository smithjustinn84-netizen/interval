package dev.justinsmith.interval.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.justinsmith.interval.platform.epochMillisNow
import dev.justinsmith.interval.ui.ConfirmDialog
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.TitleBar
import org.koin.compose.currentKoinScope

/** Resolves the tab's ViewModel and binds it to [HistoryScreen]. */
@Composable
fun HistoryRoute(modifier: Modifier = Modifier) {
    val scope = currentKoinScope()
    val viewModel = viewModel { HistoryViewModel(scope.get(), scope.get(), ::epochMillisNow) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    HistoryScreen(state = state, onClear = viewModel::clear, modifier = modifier)
}

/**
 * Finished workouts by day, the last week's tally on top. Clearing lives in the overflow menu,
 * behind a confirmation.
 */
@Composable
fun HistoryScreen(state: HistoryUiState, onClear: () -> Unit, modifier: Modifier = Modifier) {
    var menu by remember { mutableStateOf(false) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    Box(modifier.fillMaxSize().background(IntervalTheme.palette.background), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = Sizes.content).fillMaxSize(),
            contentPadding = PaddingValues(bottom = Spacing.space24),
        ) {
            item {
                TitleBar("History") {
                    Box {
                        IconAction(IntervalIcons.More, "More options", onClick = {
                            menu = true
                        }, enabled = !state.isEmpty)
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Clear history", style = IntervalTheme.type.body) },
                                onClick = {
                                    menu = false
                                    confirming = true
                                },
                            )
                        }
                    }
                }
            }
            if (state.isEmpty) {
                item {
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
                item { WeekSummary(state, Modifier.padding(horizontal = Spacing.space16)) }
            }
            state.sections.forEach { section ->
                item(key = section.day) { GroupLabel(section.day, Modifier.padding(horizontal = Spacing.space16)) }
                item(key = "${section.day} rows") {
                    ListCard(Modifier.padding(horizontal = Spacing.space16)) {
                        section.items.forEachIndexed { index, item ->
                            if (index > 0) RowDivider()
                            HistoryRow(item)
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

@Composable
private fun WeekSummary(state: HistoryUiState, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Row(
        modifier
            .fillMaxWidth()
            .clip(Corners.radius18)
            .background(palette.surface)
            .padding(horizontal = Spacing.space16, vertical = Spacing.space14),
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Last 7 days", style = IntervalTheme.type.hint, color = palette.textDim)
            Text(
                "${state.weekCount} workout${if (state.weekCount == 1) "" else "s"}",
                style = IntervalTheme.type.stepperValue,
                color = palette.text,
            )
        }
        Column(Modifier.weight(1f)) {
            Text("Time", style = IntervalTheme.type.hint, color = palette.textDim)
            Text(state.weekTotal, style = IntervalTheme.type.stepperValue, color = palette.text)
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
            .padding(horizontal = Spacing.space16, vertical = Spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.title, style = IntervalTheme.type.total, color = palette.text)
            Text(item.time, style = IntervalTheme.type.hint, color = palette.textDim)
        }
        Text(item.total, style = IntervalTheme.type.sectionTitle, color = palette.text)
    }
}

@Preview
@Composable
private fun HistoryScreenPreview() {
    IntervalTheme {
        HistoryScreen(
            state = HistoryUiState(
                sections = listOf(
                    HistorySection("Today", listOf(HistoryItem("Tabata · 20s on · 10s off · ×8", "7:42 AM", "4:00"))),
                    HistorySection(
                        "Yesterday",
                        listOf(
                            HistoryItem("25s on · 10s off · ×8", "6:15 PM", "4:40"),
                            HistoryItem("HIIT 30/30 · 30s on · 30s off · ×10", "7:05 AM", "9:40"),
                        ),
                    ),
                ),
                weekCount = 3,
                weekTotal = "18:20",
            ),
            onClear = {},
        )
    }
}
