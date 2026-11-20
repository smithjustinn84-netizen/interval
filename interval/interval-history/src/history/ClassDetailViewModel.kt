package dev.justinsmith.interval.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.data.HistoryStore
import dev.justinsmith.interval.data.SettingsStore
import dev.justinsmith.interval.model.BodySettings
import dev.justinsmith.interval.model.ClassRound
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.model.SportClass
import dev.justinsmith.interval.platform.DAY_CHECK_MILLIS
import dev.justinsmith.interval.platform.DEFAULT_MAX_BPM
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.platform.days
import dev.justinsmith.interval.platform.heartLoad
import dev.justinsmith.interval.timer.formatClock
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** A round of a recorded class as its row says it: its place, its length as a clock, its heart rate and recovery. */
data class RoundLine(
    val number: Int,
    val length: String,
    val averageBpm: Int,
    val peakBpm: Int,
    /** How far the heart rate fell in the minute after, or failing that the half minute; null with neither. */
    val drop: Int?,
    /** The seconds [drop] is over: 60 or 30. */
    val dropSeconds: Int,
)

/** A class recorded on a sensor, as its page shows it. */
data class RecordedClass(
    val sport: Sport,
    val day: HistoryDay,
    val time: String,
    val length: String,
    val averageBpm: Int?,
    val peakBpm: Int?,
    /** Its heart rate, a sample every five seconds, 0 where the sensor read none. */
    val samples: List<Int>,
    /** Each round's start and end as shares of the class's length, shaded behind the line. */
    val marks: List<ClosedFloatingPointRange<Float>>,
    val rounds: List<RoundLine>,
    /** The max heart rate its zones are worked out from: the one set in Settings, or the default. */
    val maxHeartRate: Int = DEFAULT_MAX_BPM,
    /** What the class cost the heart, minutes in each zone weighed by the zone; null with nothing read. */
    val heartLoad: Int? = null,
)

/** [recorded] is null once the class is gone, deleted here or cleared with the log: the page leaves. */
data class ClassDetailUiState(val recorded: RecordedClass? = null)

/** The class that started at [startedAtEpochMillis]: its heart rate, its rounds, and the way to delete it. */
class ClassDetailViewModel(
    private val startedAtEpochMillis: Long,
    private val historyStore: HistoryStore,
    private val settingsStore: SettingsStore,
    private val timestamps: TimestampFormatter,
    private val clock: WallClock,
    dayCheckMillis: Long = DAY_CHECK_MILLIS,
) : ViewModel() {
    // Read again each midnight, so "Today" turns to "Yesterday" with the page open.
    val state: StateFlow<ClassDetailUiState> =
        combine(historyStore.classes, settingsStore.appSettings, clock.days(timestamps, dayCheckMillis)) {
                classes,
                app,
                _,
            ->
            buildState(classes, app.body)
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            buildState(historyStore.classes.value, settingsStore.appSettings.value.body),
        )

    /** Removes the recording; the day it was logged on stays. */
    fun delete() {
        historyStore.removeClass(startedAtEpochMillis)
    }

    private fun buildState(classes: List<SportClass>, body: BodySettings): ClassDetailUiState {
        val recorded = classes.firstOrNull { it.startedAtEpochMillis == startedAtEpochMillis }
            ?: return ClassDetailUiState()
        val today = timestamps.dayNumber(clock.nowMillis())
        val samples = recorded.samples.map { it.bpm }
        val maxBpm = body.maxHeartRate ?: DEFAULT_MAX_BPM
        return ClassDetailUiState(
            RecordedClass(
                sport = recorded.sport,
                day = timestamps.heading(recorded.epochDay, today),
                time = timestamps.time(recorded.startedAtEpochMillis),
                length = formatClock(recorded.seconds),
                averageBpm = recorded.averageBpm,
                peakBpm = recorded.peakBpm,
                samples = samples,
                marks = recorded.roundMarks(),
                maxHeartRate = maxBpm,
                heartLoad = heartLoad(samples, maxBpm, restingBpm = body.restingHeartRate).takeIf { it > 0 },
                rounds = recorded.rounds.mapIndexed { index, round -> round.line(index + 1) },
            ),
        )
    }
}

private const val LONG_DROP_SECONDS = 60
private const val SHORT_DROP_SECONDS = 30

private fun ClassRound.line(number: Int) = RoundLine(
    number = number,
    length = formatClock(seconds),
    averageBpm = averageBpm,
    peakBpm = peakBpm,
    drop = drop60 ?: drop30,
    dropSeconds = if (drop60 != null) LONG_DROP_SECONDS else SHORT_DROP_SECONDS,
)

/** Each round's start and end as shares of the class's length. */
internal fun SportClass.roundMarks(): List<ClosedFloatingPointRange<Float>> {
    val length = seconds.coerceAtLeast(1).toFloat()
    return rounds.map { it.startSecond / length..it.endSecond / length }
}
