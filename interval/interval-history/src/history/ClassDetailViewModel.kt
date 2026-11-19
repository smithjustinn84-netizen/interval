package dev.justinsmith.interval.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.data.HistoryStore
import dev.justinsmith.interval.model.ClassRound
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.model.SportClass
import dev.justinsmith.interval.platform.DAY_CHECK_MILLIS
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.platform.days
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

/** The heart rate over a class, drawn: each reading's height from the lowest to the highest, and the rounds' spans. */
data class HeartLine(
    /** 0 to 1 a reading, null where the sensor read none. */
    val heights: List<Float?>,
    /** Each round's start and end, as shares of the class's length. */
    val rounds: List<ClosedFloatingPointRange<Float>>,
)

/** A class recorded on a sensor, as its page shows it. */
data class RecordedClass(
    val sport: Sport,
    val day: HistoryDay,
    val time: String,
    val length: String,
    val averageBpm: Int?,
    val peakBpm: Int?,
    val line: HeartLine,
    val rounds: List<RoundLine>,
)

/** [recorded] is null once the class is gone, deleted here or cleared with the log: the page leaves. */
data class ClassDetailUiState(val recorded: RecordedClass? = null)

/** The class that started at [startedAtEpochMillis]: its heart rate, its rounds, and the way to delete it. */
class ClassDetailViewModel(
    private val startedAtEpochMillis: Long,
    private val historyStore: HistoryStore,
    private val timestamps: TimestampFormatter,
    private val clock: WallClock,
    dayCheckMillis: Long = DAY_CHECK_MILLIS,
) : ViewModel() {
    // Read again each midnight, so "Today" turns to "Yesterday" with the page open.
    val state: StateFlow<ClassDetailUiState> =
        combine(historyStore.classes, clock.days(timestamps, dayCheckMillis)) { classes, _ -> buildState(classes) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, buildState(historyStore.classes.value))

    /** Removes the recording; the day it was logged on stays. */
    fun delete() {
        historyStore.removeClass(startedAtEpochMillis)
    }

    private fun buildState(classes: List<SportClass>): ClassDetailUiState {
        val recorded = classes.firstOrNull { it.startedAtEpochMillis == startedAtEpochMillis }
            ?: return ClassDetailUiState()
        val today = timestamps.dayNumber(clock.nowMillis())
        return ClassDetailUiState(
            RecordedClass(
                sport = recorded.sport,
                day = timestamps.heading(recorded.epochDay, today),
                time = timestamps.time(recorded.startedAtEpochMillis),
                length = formatClock(recorded.seconds),
                averageBpm = recorded.averageBpm,
                peakBpm = recorded.peakBpm,
                line = recorded.heartLine(),
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

/** The class's heart rate as a line to draw, flat in the middle when it never varied. */
internal fun SportClass.heartLine(): HeartLine {
    val read = samples.filter { it.read }.map { it.bpm }
    val low = read.minOrNull() ?: 0
    val span = ((read.maxOrNull() ?: 0) - low).takeIf { it > 0 }
    val length = seconds.coerceAtLeast(1).toFloat()
    return HeartLine(
        heights = samples.map { sample ->
            when {
                !sample.read -> null
                span == null -> FLAT
                else -> (sample.bpm - low).toFloat() / span
            }
        },
        rounds = rounds.map { it.startSecond / length..it.endSecond / length },
    )
}

private const val FLAT = 0.5f
