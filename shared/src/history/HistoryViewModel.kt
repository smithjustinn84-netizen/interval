package dev.justinsmith.interval.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.data.HistoryEntry
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.timer.DAYS_PER_WEEK
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.timer.matching
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
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

/**
 * The workout history by day, and the last seven days' tally. Today is worked out from [clock], and
 * checked every [dayCheckMillis] so that the headings and the week move on at midnight with the screen open.
 */
class HistoryViewModel(
    private val repository: IntervalRepository,
    private val timestamps: TimestampFormatter,
    private val clock: WallClock,
    dayCheckMillis: Long = DAY_CHECK_MILLIS,
) : ViewModel() {
    private val days = flow {
        while (true) {
            emit(timestamps.dayNumber(clock.nowMillis()))
            delay(dayCheckMillis)
        }
    }.distinctUntilChanged()

    private val locale = MutableStateFlow<String?>(null)

    val state: StateFlow<HistoryUiState> =
        combine(repository.history, repository.presets, days, locale) { history, presets, _, _ ->
            buildState(history, presets)
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = buildState(repository.history.value, repository.presets.value),
            )

    fun clear() {
        repository.clearHistory()
    }

    /**
     * The app's locale, as a language tag. The dates and times are formatted in this ViewModel, which
     * outlives a language change, so they are formatted again when it changes.
     */
    fun setLocale(languageTag: String) {
        locale.value = languageTag
    }

    private fun buildState(history: List<HistoryEntry>, presets: List<Preset>): HistoryUiState {
        val nowMillis = clock.nowMillis()
        val today = timestamps.dayNumber(nowMillis)
        // History is newest first, so grouping keeps both the days and the rows within them in order.
        val byDay = history.groupBy { timestamps.dayNumber(it.finishedAtEpochMillis) }
        val sections = byDay.map { (dayNumber, entries) ->
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
        val thisWeek = byDay.filterKeys { it in (today - DAYS_PER_WEEK + 1)..today }.values.flatten()
        return HistoryUiState(
            sections = sections,
            weekCount = thisWeek.size,
            weekTotal = formatClock(thisWeek.sumOf { it.elapsedSeconds }),
            week = weekTally(
                secondsByDay = byDay.mapValues { (_, entries) -> entries.sumOf { it.elapsedSeconds } },
                today = today,
                todayWeekday = timestamps.weekday(nowMillis),
            ),
        )
    }

    private companion object {
        const val DAY_CHECK_MILLIS = 60_000L
    }
}

/**
 * The week chart's seven bars, oldest first and ending [today], from the seconds worked out on each day
 * number. [todayWeekday] is today's day of the week, 1 for Monday to 7.
 */
internal fun weekTally(secondsByDay: Map<Long, Int>, today: Long, todayWeekday: Int): List<DayTally> =
    (DAYS_PER_WEEK - 1 downTo 0).map { daysAgo ->
        DayTally(
            weekday = (todayWeekday - 1 - daysAgo).mod(DAYS_PER_WEEK) + 1,
            seconds = secondsByDay[today - daysAgo] ?: 0,
            today = daysAgo == 0,
        )
    }
