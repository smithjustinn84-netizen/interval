package dev.justinsmith.interval.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.data.HistoryEntry
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.timer.matching
import dev.justinsmith.interval.timer.presetEntries
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HistoryItem(
    /** The preset's name, or the shape itself for a workout no preset has. */
    val title: String,
    /** The shape (when the title is a name), whether it ended early, and the time of day. */
    val detail: String,
    val total: String,
)

/** One day's workouts, under its heading ("Today", "Yesterday", "Sunday, Oct 11"). */
data class HistorySection(val day: String, val items: List<HistoryItem>)

/** One bar of the week chart: the day's initial, how long was worked out, and whether it is today. */
data class DayTally(val label: String, val seconds: Int, val today: Boolean)

data class HistoryUiState(
    val sections: List<HistorySection>,
    /** Workouts over the last seven days (today and the six before), and their combined time. */
    val weekCount: Int,
    val weekTotal: String,
    /** Those seven days, oldest first, for the week chart. */
    val week: List<DayTally>,
) {
    val isEmpty: Boolean get() = sections.isEmpty()

    /** The longest day's total, for scaling the week chart's bars. */
    val weekMaxSeconds: Int get() = week.maxOfOrNull { it.seconds } ?: 0
}

class HistoryViewModel(
    private val repository: IntervalRepository,
    private val timestamps: TimestampFormatter,
    private val now: () -> Long,
) : ViewModel() {
    val state: StateFlow<HistoryUiState> = combine(repository.history, repository.customPresets, ::buildState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = buildState(repository.history.value, repository.customPresets.value),
        )

    fun clear() {
        repository.clearHistory()
    }

    private fun buildState(history: List<HistoryEntry>, custom: List<Preset>): HistoryUiState {
        val presets = presetEntries(custom)
        val nowMillis = now()
        // History is newest first, so grouping keeps both the days and the rows within them in order.
        val sections = history
            .groupBy { timestamps.day(it.finishedAtEpochMillis, nowMillis) }
            .map { (day, entries) ->
                HistorySection(
                    day,
                    entries.map { entry ->
                        val name = presets.matching(entry.config)?.name
                        val ended = if (entry.endedEarly) "ended early" else null
                        val time = timestamps.time(entry.finishedAtEpochMillis)
                        HistoryItem(
                            title = name ?: entry.config.description,
                            detail = listOfNotNull(entry.config.description.takeIf { name != null }, ended, time)
                                .joinToString(" · "),
                            total = formatClock(entry.durationSeconds),
                        )
                    },
                )
            }
        val today = timestamps.dayNumber(nowMillis)
        val todayWeekday = timestamps.weekday(nowMillis)
        val byDay = history.groupBy { timestamps.dayNumber(it.finishedAtEpochMillis) }
        val week = (DAYS_PER_WEEK - 1 downTo 0).map { daysAgo ->
            val weekday = (todayWeekday - 1 - daysAgo).mod(DAYS_PER_WEEK)
            DayTally(
                label = WEEKDAY_INITIALS[weekday].toString(),
                seconds = byDay[today - daysAgo]?.sumOf { it.durationSeconds } ?: 0,
                today = daysAgo == 0,
            )
        }
        val thisWeek = byDay.filterKeys { it in (today - DAYS_PER_WEEK + 1)..today }.values.flatten()
        return HistoryUiState(
            sections = sections,
            weekCount = thisWeek.size,
            weekTotal = formatClock(thisWeek.sumOf { it.durationSeconds }),
            week = week,
        )
    }

    private companion object {
        const val DAYS_PER_WEEK = 7
        const val WEEKDAY_INITIALS = "MTWTFSS"
    }
}
