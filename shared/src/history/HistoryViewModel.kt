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
    val title: String,
    val time: String,
    val total: String,
)

/** One day's workouts, under its heading ("Today", "Yesterday", "Sunday, Oct 11"). */
data class HistorySection(val day: String, val items: List<HistoryItem>)

data class HistoryUiState(
    val sections: List<HistorySection>,
    /** Workouts in the last seven days, and their combined time. */
    val weekCount: Int,
    val weekTotal: String,
) {
    val isEmpty: Boolean get() = sections.isEmpty()
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
        val week = history.filter { nowMillis - it.finishedAtEpochMillis < WEEK_MILLIS }
        // History is newest first, so grouping keeps both the days and the rows within them in order.
        val sections = history
            .groupBy { timestamps.day(it.finishedAtEpochMillis, nowMillis) }
            .map { (day, entries) ->
                HistorySection(
                    day,
                    entries.map { entry ->
                        val name = presets.matching(entry.config)?.name
                        val ended = if (entry.endedEarly) "ended early" else null
                        HistoryItem(
                            title = listOfNotNull(name, entry.config.description, ended).joinToString(" · "),
                            time = timestamps.time(entry.finishedAtEpochMillis),
                            total = formatClock(entry.durationSeconds),
                        )
                    },
                )
            }
        return HistoryUiState(
            sections = sections,
            weekCount = week.size,
            weekTotal = formatClock(week.sumOf { it.durationSeconds }),
        )
    }

    private companion object {
        const val WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
