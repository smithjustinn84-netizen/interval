package dev.justinsmith.bells.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
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
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.justinsmith.bells.data.BellsRepository
import dev.justinsmith.bells.data.IntervalWorkout
import dev.justinsmith.bells.domain.LiftSession
import dev.justinsmith.bells.domain.formatKg
import dev.justinsmith.bells.resources.Res
import dev.justinsmith.bells.resources.common_cancel
import dev.justinsmith.bells.resources.common_joined
import dev.justinsmith.bells.resources.day_today
import dev.justinsmith.bells.resources.day_yesterday
import dev.justinsmith.bells.resources.history_delete
import dev.justinsmith.bells.resources.history_delete_text
import dev.justinsmith.bells.resources.history_delete_title
import dev.justinsmith.bells.resources.history_empty_text
import dev.justinsmith.bells.resources.history_empty_title
import dev.justinsmith.bells.resources.history_from_interval
import dev.justinsmith.bells.resources.history_open_interval
import dev.justinsmith.bells.resources.history_title
import dev.justinsmith.bells.resources.kg_value
import dev.justinsmith.bells.resources.sets_count
import dev.justinsmith.core.sync.AppId
import dev.justinsmith.core.sync.SiblingFeed
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.platform.rememberOpenUrl
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.ConfirmDialog
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.EmptyState
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.TabGrid
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.currentKoinScope

/** A day's heading: "Today", "Yesterday", or its date. */
sealed interface DayHeading {
    data object Today : DayHeading

    data object Yesterday : DayHeading

    data class On(val date: String) : DayHeading
}

/** One row of the history: a Bells session, which can be deleted here, or an Interval workout, read-only. */
sealed interface HistoryRow {
    val time: String
    val total: String

    data class Bells(
        val uid: String,
        val title: String,
        val sets: Int,
        val tonnageKg: Double,
        override val time: String,
        override val total: String,
    ) : HistoryRow

    data class Interval(val title: String, override val time: String, override val total: String) : HistoryRow
}

data class HistoryDay(val heading: DayHeading, val rows: List<HistoryRow>)

/** Both apps' workouts by day, newest first. */
class BellsHistoryViewModel(
    private val repository: BellsRepository,
    private val timestamps: TimestampFormatter,
    private val clock: WallClock,
) : ViewModel() {
    val days: StateFlow<List<HistoryDay>> =
        combine(repository.sessions, repository.interval) { sessions, interval -> build(sessions, interval) }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                build(repository.sessions.value, repository.interval.value),
            )

    fun delete(uid: String) {
        repository.deleteSession(uid)
    }

    private fun build(sessions: List<LiftSession>, interval: List<IntervalWorkout>): List<HistoryDay> {
        val today = timestamps.dayNumber(clock.nowMillis())
        val rows = sessions.mapNotNull { session ->
            val at = session.finishedAtEpochMillis ?: return@mapNotNull null
            at to HistoryRow.Bells(
                session.uid,
                session.title,
                session.sets.size,
                session.tonnageKg,
                timestamps.time(at),
                formatClock(session.elapsedSeconds(0)),
            )
        } + interval.map {
            it.finishedAtEpochMillis to
                HistoryRow.Interval(it.title, timestamps.time(it.finishedAtEpochMillis), formatClock(it.elapsedSeconds))
        }
        return rows.sortedByDescending { it.first }
            .groupBy { timestamps.dayNumber(it.first) }
            .map { (day, entries) ->
                val heading = when (day) {
                    today -> DayHeading.Today
                    today - 1 -> DayHeading.Yesterday
                    else -> DayHeading.On(timestamps.dateOfDay(day))
                }
                HistoryDay(heading, entries.map { it.second })
            }
    }
}

@Composable
fun BellsHistoryRoute() {
    val scope = currentKoinScope()
    val viewModel = viewModel { BellsHistoryViewModel(scope.get(), scope.get(), scope.get()) }
    val days by viewModel.days.collectAsStateWithLifecycle()
    val openUrl = rememberOpenUrl()
    val opensInterval = remember(scope) { scope.get<SiblingFeed>().canOpenSibling }
    BellsHistoryScreen(
        days,
        onDelete = viewModel::delete,
        onOpenInterval = if (opensInterval) ({ openUrl(AppId.Interval.openUrl) }) else null,
    )
}

/**
 * Bells' sessions and Interval's workouts by day. [onOpenInterval] opens Interval from one of its; null
 * where it can't.
 */
@Composable
fun BellsHistoryScreen(
    days: List<HistoryDay>,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenInterval: (() -> Unit)? = null,
) {
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    val card = Modifier.padding(horizontal = Spacing.space16)
    TabGrid(stringResource(Res.string.history_title), modifier) {
        if (days.isEmpty()) {
            item(key = "empty", span = StaggeredGridItemSpan.FullLine) {
                ListCard(card) {
                    EmptyState(
                        IntervalIcons.History,
                        stringResource(Res.string.history_empty_title),
                        stringResource(Res.string.history_empty_text),
                    )
                }
            }
        }
        items(days, key = { it.heading.toString() }) { day ->
            Column(card) {
                GroupLabel(
                    when (val heading = day.heading) {
                        DayHeading.Today -> stringResource(Res.string.day_today)
                        DayHeading.Yesterday -> stringResource(Res.string.day_yesterday)
                        is DayHeading.On -> heading.date
                    },
                )
                ListCard {
                    day.rows.forEachIndexed { i, row ->
                        if (i > 0) RowDivider()
                        HistoryRowItem(
                            row,
                            onClick = when (row) {
                                is HistoryRow.Bells -> ({ deleting = row.uid })
                                is HistoryRow.Interval -> onOpenInterval
                            },
                        )
                    }
                }
            }
        }
    }
    deleting?.let { uid ->
        ConfirmDialog(
            title = stringResource(Res.string.history_delete_title),
            text = stringResource(Res.string.history_delete_text),
            confirm = stringResource(Res.string.history_delete),
            dismiss = stringResource(Res.string.common_cancel),
            onConfirm = {
                deleting = null
                onDelete(uid)
            },
            onDismiss = { deleting = null },
        )
    }
}

/** A Bells session opens its delete dialog; an Interval workout, Interval's to change, opens Interval. */
@Composable
private fun HistoryRowItem(row: HistoryRow, onClick: (() -> Unit)?) {
    val palette = IntervalTheme.palette
    val (title, detail) = when (row) {
        is HistoryRow.Bells -> row.title to listOf(
            pluralStringResource(Res.plurals.sets_count, row.sets, row.sets),
            stringResource(Res.string.kg_value, formatKg(row.tonnageKg)),
            row.time,
        )

        is HistoryRow.Interval -> row.title to listOf(stringResource(Res.string.history_from_interval), row.time)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.listRow)
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier.clickable(
                        onClickLabel = stringResource(Res.string.history_open_interval).takeIf {
                            row is HistoryRow.Interval
                        },
                        role = Role.Button,
                        onClick = onClick,
                    )
                },
            )
            .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        // Bells draws its own sessions in the accent and Interval's in the work mark, as Interval does in reverse.
        Box(
            Modifier
                .size(Sizes.stripe, Sizes.historyMarkHeight)
                .clip(Corners.full)
                .background(if (row is HistoryRow.Bells) palette.accent else palette.workMark),
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = IntervalTheme.type.label, color = palette.text)
            Text(
                detail.reduce { joined, part -> stringResource(Res.string.common_joined, joined, part) },
                style = IntervalTheme.type.meta,
                color = palette.textDim,
            )
        }
        Text(row.total, style = IntervalTheme.type.value, color = palette.text)
    }
}
