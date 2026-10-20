package dev.justinsmith.bells.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.justinsmith.bells.data.BellsRepository
import dev.justinsmith.bells.data.IntervalWorkout
import dev.justinsmith.bells.domain.LiftSession
import dev.justinsmith.bells.domain.formatKg
import dev.justinsmith.bells.resources.Res
import dev.justinsmith.bells.resources.common_joined
import dev.justinsmith.bells.resources.kg_value
import dev.justinsmith.bells.resources.nav_today
import dev.justinsmith.bells.resources.sets_count
import dev.justinsmith.bells.resources.today_last
import dev.justinsmith.bells.resources.today_legend_bells
import dev.justinsmith.bells.resources.today_legend_interval
import dev.justinsmith.bells.resources.today_none_text
import dev.justinsmith.bells.resources.today_none_title
import dev.justinsmith.bells.resources.today_resume
import dev.justinsmith.bells.resources.today_start
import dev.justinsmith.bells.resources.today_this_week
import dev.justinsmith.bells.resources.today_week_count
import dev.justinsmith.bells.resources.weekday_cd
import dev.justinsmith.bells.resources.weekday_counts
import dev.justinsmith.bells.resources.weekday_initial_1
import dev.justinsmith.bells.resources.weekday_initial_2
import dev.justinsmith.bells.resources.weekday_initial_3
import dev.justinsmith.bells.resources.weekday_initial_4
import dev.justinsmith.bells.resources.weekday_initial_5
import dev.justinsmith.bells.resources.weekday_initial_6
import dev.justinsmith.bells.resources.weekday_initial_7
import dev.justinsmith.bells.resources.weekday_rest
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.timer.DAYS_PER_WEEK
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.timer.weekStartOf
import dev.justinsmith.interval.timer.weekdayOf
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.EmptyState
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
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

/** One day of this week: how many sessions each app logged on it. */
data class WeekDay(
    val epochDay: Long,
    val weekday: Int,
    val bells: Int,
    val interval: Int,
    val today: Boolean,
    val date: String,
)

data class TodayUiState(
    val week: List<WeekDay> = emptyList(),
    val last: LiftSession? = null,
    /** When [last] finished: "7:42 AM" today, else its date. */
    val lastWhen: String = "",
    val inProgress: Boolean = false,
) {
    val weekCount: Int get() = week.sumOf { it.bells + it.interval }
    val intervalCount: Int get() = week.sumOf { it.interval }
}

/** This week across both apps, the last session, and whether one is in progress. */
class TodayViewModel(
    private val repository: BellsRepository,
    private val timestamps: TimestampFormatter,
    private val clock: WallClock,
) : ViewModel() {
    val state: StateFlow<TodayUiState> =
        combine(repository.sessions, repository.interval, repository.active) { sessions, interval, active ->
            build(sessions, interval, active)
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            build(repository.sessions.value, repository.interval.value, repository.active.value),
        )

    private fun build(
        sessions: List<LiftSession>,
        interval: List<IntervalWorkout>,
        active: LiftSession?,
    ): TodayUiState {
        val today = timestamps.dayNumber(clock.nowMillis())
        val bellsDays = sessions.mapNotNull { it.finishedAtEpochMillis?.let(timestamps::dayNumber) }
        val intervalDays = interval.map { timestamps.dayNumber(it.finishedAtEpochMillis) }
        val start = weekStartOf(today)
        val last = sessions.firstOrNull()
        return TodayUiState(
            week = (start until start + DAYS_PER_WEEK).map { day ->
                WeekDay(
                    epochDay = day,
                    weekday = weekdayOf(day),
                    bells = bellsDays.count { it == day },
                    interval = intervalDays.count { it == day },
                    today = day == today,
                    date = timestamps.dateOfDay(day),
                )
            },
            last = last,
            lastWhen = last?.finishedAtEpochMillis?.let { at ->
                if (timestamps.dayNumber(at) == today) timestamps.time(at) else timestamps.date(at)
            }.orEmpty(),
            inProgress = active != null,
        )
    }
}

@Composable
fun TodayRoute(onStartSession: () -> Unit) {
    val scope = currentKoinScope()
    val viewModel = viewModel { TodayViewModel(scope.get(), scope.get(), scope.get()) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    TodayScreen(state, onStartSession)
}

/** The Today tab: the week across both apps, the last session, and Start (or Resume) session. */
@Composable
fun TodayScreen(state: TodayUiState, onStartSession: () -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val card = Modifier.padding(horizontal = Spacing.space16, vertical = Spacing.space4)
    TabGrid(stringResource(Res.string.nav_today), modifier) {
        item(key = "week", span = StaggeredGridItemSpan.FullLine) {
            ListCard(card) {
                Column(Modifier.padding(Spacing.space16), verticalArrangement = Arrangement.spacedBy(Spacing.space12)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(Res.string.today_this_week),
                            style = IntervalTheme.type.caption,
                            color = palette.textDim,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            pluralStringResource(
                                Res.plurals.today_week_count,
                                state.weekCount,
                                state.weekCount,
                                state.intervalCount,
                            ),
                            style = IntervalTheme.type.meta,
                            color = palette.textDim,
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        state.week.forEach { day -> DayMark(day) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space16)) {
                        Legend(palette.accent, stringResource(Res.string.today_legend_bells))
                        Legend(palette.workMark, stringResource(Res.string.today_legend_interval))
                    }
                }
            }
        }
        item(key = "last", span = StaggeredGridItemSpan.FullLine) {
            ListCard(card) {
                val last = state.last
                if (last == null) {
                    EmptyState(
                        IntervalIcons.Bell,
                        stringResource(Res.string.today_none_title),
                        stringResource(Res.string.today_none_text),
                    )
                } else {
                    Column(
                        Modifier.padding(Spacing.space16),
                        verticalArrangement = Arrangement.spacedBy(Spacing.space4),
                    ) {
                        Text(
                            stringResource(Res.string.today_last),
                            style = IntervalTheme.type.caption,
                            color = palette.textDim,
                        )
                        Text(last.title, style = IntervalTheme.type.stat, color = palette.text)
                        val detail = listOf(
                            pluralStringResource(Res.plurals.sets_count, last.sets.size, last.sets.size),
                            stringResource(Res.string.kg_value, formatKg(last.tonnageKg)),
                            formatClock(last.elapsedSeconds(0)),
                            state.lastWhen,
                        ).reduce { joined, part -> stringResource(Res.string.common_joined, joined, part) }
                        Text(detail, style = IntervalTheme.type.meta, color = palette.textDim)
                    }
                }
            }
        }
        item(key = "start", span = StaggeredGridItemSpan.FullLine) {
            AccentButton(
                stringResource(if (state.inProgress) Res.string.today_resume else Res.string.today_start),
                onClick = onStartSession,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.space16, vertical = Spacing.space12),
            )
        }
    }
}

/** A day of the week strip: filled in the accent for a Bells session, in the work mark for Interval only. */
@Composable
private fun DayMark(day: WeekDay) {
    val palette = IntervalTheme.palette
    val fill: Color? = when {
        day.bells > 0 -> palette.accent
        day.interval > 0 -> palette.workMark
        else -> null
    }
    val counts = if (day.bells + day.interval == 0) {
        stringResource(Res.string.weekday_rest)
    } else {
        stringResource(Res.string.weekday_counts, day.bells, day.interval)
    }
    val description = stringResource(Res.string.weekday_cd, day.date, counts)
    Column(
        Modifier.clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.space4),
    ) {
        Box(
            Modifier
                .size(Sizes.weekBarMark)
                .clip(Corners.full)
                .background(fill ?: palette.surfaceVariant)
                .then(if (day.today) Modifier.border(Sizes.border, palette.text, Corners.full) else Modifier),
        )
        Text(
            stringResource(weekdayInitials[day.weekday - 1]),
            style = IntervalTheme.type.caption,
            color = if (day.today) palette.text else palette.textDim,
        )
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.space4)) {
        Box(Modifier.size(Sizes.legendSwatch).clip(Corners.full).background(color))
        Text(text, style = IntervalTheme.type.caption, color = IntervalTheme.palette.textDim)
    }
}

private val weekdayInitials = listOf(
    Res.string.weekday_initial_1,
    Res.string.weekday_initial_2,
    Res.string.weekday_initial_3,
    Res.string.weekday_initial_4,
    Res.string.weekday_initial_5,
    Res.string.weekday_initial_6,
    Res.string.weekday_initial_7,
)
