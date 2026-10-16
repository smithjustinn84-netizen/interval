package dev.justinsmith.interval.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.data.HistoryEntry
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.PresetEntry
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.timer.matching
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** The heading over one day's workouts. */
sealed interface HistoryDay {
    data object Today : HistoryDay

    data object Yesterday : HistoryDay

    /** Any earlier day, as the platform formats its date: "Sunday, Oct 11". */
    data class On(val date: String) : HistoryDay
}

data class HistoryItem(
    /** The matching preset's name, or null for a workout no preset has; the row then leads with its shape. */
    val presetName: String?,
    val config: IntervalConfig,
    val endedEarly: Boolean,
    /** The time of day it finished: "7:42 PM". */
    val time: String,
    val total: String,
)

/** One day's workouts. [dayNumber] identifies the day, as [TimestampFormatter.dayNumber] counts them. */
data class HistorySection(val day: HistoryDay, val dayNumber: Long, val items: List<HistoryItem>)

/**
 * One bar of the week chart: the day of the week (1 for Monday to 7), how long was worked out, and
 * whether it is today.
 */
data class DayTally(val weekday: Int, val seconds: Int, val today: Boolean)

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
    val state: StateFlow<HistoryUiState> = combine(repository.history, repository.presets, ::buildState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = buildState(repository.history.value, repository.presets.value),
        )

    fun clear() {
        repository.clearHistory()
    }

    private fun buildState(history: List<HistoryEntry>, presets: List<PresetEntry>): HistoryUiState {
        val nowMillis = now()
        val today = timestamps.dayNumber(nowMillis)
        // History is newest first, so grouping keeps both the days and the rows within them in order.
        val sections = history
            .groupBy { timestamps.dayNumber(it.finishedAtEpochMillis) }
            .map { (dayNumber, entries) ->
                HistorySection(
                    day = when (dayNumber) {
                        today -> HistoryDay.Today
                        today - 1 -> HistoryDay.Yesterday
                        else -> HistoryDay.On(timestamps.date(entries.first().finishedAtEpochMillis))
                    },
                    dayNumber = dayNumber,
                    items = entries.map { entry ->
                        HistoryItem(
                            presetName = presets.matching(entry.config)?.name,
                            config = entry.config,
                            endedEarly = entry.endedEarly,
                            time = timestamps.time(entry.finishedAtEpochMillis),
                            total = formatClock(entry.elapsedSeconds),
                        )
                    },
                )
            }
        val todayWeekday = timestamps.weekday(nowMillis)
        val byDay = history.groupBy { timestamps.dayNumber(it.finishedAtEpochMillis) }
        val week = (DAYS_PER_WEEK - 1 downTo 0).map { daysAgo ->
            DayTally(
                weekday = (todayWeekday - 1 - daysAgo).mod(DAYS_PER_WEEK) + 1,
                seconds = byDay[today - daysAgo]?.sumOf { it.elapsedSeconds } ?: 0,
                today = daysAgo == 0,
            )
        }
        val thisWeek = byDay.filterKeys { it in (today - DAYS_PER_WEEK + 1)..today }.values.flatten()
        return HistoryUiState(
            sections = sections,
            weekCount = thisWeek.size,
            weekTotal = formatClock(thisWeek.sumOf { it.elapsedSeconds }),
            week = week,
        )
    }

    private companion object {
        const val DAYS_PER_WEEK = 7
    }
}

private const val SECONDS_PER_MINUTE = 60

/** A day's time as the week chart reads it out: in seconds under a minute, in whole minutes from one. */
sealed interface SpokenDuration {
    data class Seconds(val seconds: Int) : SpokenDuration

    data class Minutes(val minutes: Int) : SpokenDuration
}

/** [seconds] as [SpokenDuration]; minutes are rounded to the nearest. */
internal fun spokenDuration(seconds: Int): SpokenDuration = if (seconds < SECONDS_PER_MINUTE) {
    SpokenDuration.Seconds(seconds)
} else {
    SpokenDuration.Minutes((seconds + SECONDS_PER_MINUTE / 2) / SECONDS_PER_MINUTE)
}
