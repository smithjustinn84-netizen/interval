package dev.justinsmith.interval.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import dev.justinsmith.interval.history.resources.Res
import dev.justinsmith.interval.history.resources.history_filter_clear
import dev.justinsmith.interval.history.resources.history_filter_empty
import dev.justinsmith.interval.history.resources.history_filter_prs
import dev.justinsmith.interval.history.resources.history_kind_conditioning
import dev.justinsmith.interval.history.resources.history_kind_mobility
import dev.justinsmith.interval.history.resources.history_kind_sport
import dev.justinsmith.interval.history.resources.history_kind_strength
import dev.justinsmith.interval.history.resources.history_search
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.defaultPresetName
import dev.justinsmith.interval.trends.recordMoments
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.NameField
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.lift.domain.CustomExercise
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.ui.Choice
import kotlinx.coroutines.flow.drop
import org.jetbrains.compose.resources.stringResource

private const val SEARCH_MAX_LENGTH = 80

/**
 * Returns the UIDs of finished Lift [sessions] (newest first) that set at least one personal record
 * against every session finished before them.
 */
internal fun prSessionUids(sessions: List<LiftSession>): Set<String> =
    recordMoments(sessions) { it }.mapTo(mutableSetOf()) { it.sessionUid }

/**
 * Narrows [sections] by [filter] and free-text [query], dropping days left with no matching rows.
 * [sessionsByUid] lets text search match exercise names inside Lift sessions.
 */
internal fun filterSections(
    sections: List<HistorySection>,
    filter: LogFilter?,
    query: String,
    sessionsByUid: Map<String, LiftSession>,
): List<HistorySection> {
    val q = query.trim().lowercase()
    return sections.mapNotNull { section ->
        val items = section.items.filter { item ->
            matchesFilter(item, filter) && (q.isEmpty() || matchesQuery(item, q, sessionsByUid))
        }
        val allowSport = filter == null || filter == LogFilter.Sport
        val sports = if (allowSport) {
            section.sports.filter { q.isEmpty() || matchesSportQuery(it, q) }
        } else {
            emptyList()
        }
        val classes = if (allowSport) {
            section.classes.filter { q.isEmpty() || matchesSportQuery(it.sport, q) }
        } else {
            emptyList()
        }
        if (items.isEmpty() && sports.isEmpty() && classes.isEmpty()) {
            null
        } else {
            section.copy(items = items, sports = sports, classes = classes)
        }
    }
}

private fun matchesFilter(item: HistoryItem, filter: LogFilter?): Boolean = when (filter) {
    null -> true
    LogFilter.Strength -> item.liftUid != null
    LogFilter.Conditioning -> item.liftUid == null && item.config.activity != Activity.Mobility
    LogFilter.Mobility -> item.liftUid == null && item.config.activity == Activity.Mobility
    LogFilter.Sport -> false
    LogFilter.Prs -> item.hasPr
}

private fun matchesQuery(item: HistoryItem, q: String, sessionsByUid: Map<String, LiftSession>): Boolean {
    if (item.presetName?.lowercase()?.contains(q) == true) return true
    if (item.note?.lowercase()?.contains(q) == true) return true
    if (item.liftUid == null && item.config.defaultPresetName().lowercase().contains(q)) return true
    val session = item.liftUid?.let { sessionsByUid[it] } ?: return false
    return session.sets.any { set ->
        val exercise = set.exercise
        exercise.key.replace('_', ' ').lowercase().contains(q) ||
            (exercise as? CustomExercise)?.name?.lowercase()?.contains(q) == true
    }
}

private fun matchesSportQuery(sport: Sport, q: String): Boolean {
    val searchable = when (sport) {
        Sport.Bjj -> "bjj jiu-jitsu class"
        Sport.Climbing -> "climbing climb"
        Sport.Running -> "running run"
        is Sport.Custom -> sport.name.lowercase()
    }
    return searchable.contains(q)
}

/**
 * Compact search box and filter chips (Strength / Conditioning / Mobility / Sport / PRs) for All time.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LogFilterBar(
    filter: LogFilter?,
    query: String,
    onFilter: (LogFilter?) -> Unit,
    onQuery: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val searchState = rememberTextFieldState(query)
    val queryChange by rememberUpdatedState(onQuery)
    LaunchedEffect(searchState) {
        snapshotFlow { searchState.text.toString() }.drop(1).collect { queryChange(it) }
    }
    LaunchedEffect(query) {
        if (searchState.text.toString() != query) {
            searchState.setTextAndPlaceCursorAtEnd(query)
        }
    }
    val placeholder = stringResource(Res.string.history_search)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        NameField(
            searchState,
            placeholder = placeholder,
            label = placeholder,
            maxLength = SEARCH_MAX_LENGTH,
            imeAction = ImeAction.Search,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
            verticalArrangement = Arrangement.spacedBy(Spacing.space8),
        ) {
            LogFilter.entries.forEach { option ->
                val selected = option == filter
                Choice(
                    text = filterLabel(option),
                    selected = selected,
                    onClick = { onFilter(if (selected) null else option) },
                    role = Role.Checkbox,
                )
            }
            if (filter != null || query.isNotBlank()) {
                QuietTextButton(
                    stringResource(Res.string.history_filter_clear),
                    onClick = {
                        searchState.clearText()
                        onClear()
                    },
                )
            }
        }
    }
}

@Composable
private fun filterLabel(filter: LogFilter): String = stringResource(
    when (filter) {
        LogFilter.Strength -> Res.string.history_kind_strength
        LogFilter.Conditioning -> Res.string.history_kind_conditioning
        LogFilter.Mobility -> Res.string.history_kind_mobility
        LogFilter.Sport -> Res.string.history_kind_sport
        LogFilter.Prs -> Res.string.history_filter_prs
    },
)

/** Shown when the user has logged history, but the active filter or search matched no entries. */
@Composable
internal fun FilteredEmptyCard(onClear: () -> Unit, modifier: Modifier = Modifier) {
    SummaryCard(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = Spacing.space4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
        ) {
            Text(
                stringResource(Res.string.history_filter_empty),
                style = IntervalTheme.type.body,
                color = IntervalTheme.palette.textDim,
                modifier = Modifier.weight(1f),
            )
            QuietTextButton(stringResource(Res.string.history_filter_clear), onClear)
        }
    }
}
