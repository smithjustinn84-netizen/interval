package dev.justinsmith.interval.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.data.HistoryStore
import dev.justinsmith.interval.data.SettingsStore
import dev.justinsmith.interval.platform.DAY_CHECK_MILLIS
import dev.justinsmith.interval.platform.DEFAULT_MAX_BPM
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.platform.days
import dev.justinsmith.interval.timer.WeightUnit
import dev.justinsmith.lift.data.LiftActivity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/** A record on the timeline, dated as the Log dates a day. */
data class RecordLine(val date: String, val moment: RecordMoment)

/**
 * Trends as the page shows it: [trends] with the weight in [unit], [since] the first week's Monday as a date, and
 * each of [records] dated. [loading] until the first is built.
 */
data class TrendsUiState(
    val trends: Trends = Trends(),
    val unit: WeightUnit = WeightUnit.Kg,
    val since: String = "",
    val records: List<RecordLine> = emptyList(),
    val loading: Boolean = true,
)

/**
 * The Log's Trends: built again from the interval workouts, the recorded classes, Lift's sessions and the body's
 * settings as any of them changes, and at midnight, so the weeks move on with the page open. The build walks the
 * whole of both logs, so it runs on [computation].
 */
class TrendsViewModel(
    historyStore: HistoryStore,
    settingsStore: SettingsStore,
    lift: LiftActivity,
    private val timestamps: TimestampFormatter,
    clock: WallClock,
    dayCheckMillis: Long = DAY_CHECK_MILLIS,
    computation: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    val state: StateFlow<TrendsUiState> = combine(
        historyStore.history,
        historyStore.classes,
        lift.sessions,
        settingsStore.appSettings,
        clock.days(timestamps, dayCheckMillis),
    ) { history, classes, sessions, app, today ->
        val trends = trends(
            history = history,
            sessions = sessions,
            classes = classes,
            today = today,
            dayOf = timestamps::dayNumber,
            unit = app.body.unit,
            maxBpm = app.body.maxHeartRate ?: DEFAULT_MAX_BPM,
            restingBpm = app.body.restingHeartRate,
        )
        TrendsUiState(
            trends = trends,
            unit = app.body.unit,
            since = trends.weeks.firstOrNull()?.let { timestamps.dateOfDay(it.weekStart) }.orEmpty(),
            records = trends.records.map { RecordLine(timestamps.dateOfDay(it.epochDay), it) },
            loading = false,
        )
    }
        .flowOn(computation)
        .stateIn(viewModelScope, SharingStarted.Eagerly, TrendsUiState())
}
