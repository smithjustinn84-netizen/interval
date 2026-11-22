package dev.justinsmith.interval.trends

import dev.justinsmith.interval.history.asLiftWorkouts
import dev.justinsmith.interval.model.HistoryEntry
import dev.justinsmith.interval.model.SportClass
import dev.justinsmith.interval.platform.RECOVERY_LONG_SECONDS
import dev.justinsmith.interval.platform.heartLoad
import dev.justinsmith.interval.platform.recovery
import dev.justinsmith.interval.timer.WeightUnit
import dev.justinsmith.interval.timer.weekStartOf
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.Load
import dev.justinsmith.lift.domain.PersonalRecord
import dev.justinsmith.lift.domain.SetLog
import dev.justinsmith.lift.domain.TestId
import dev.justinsmith.lift.domain.at
import dev.justinsmith.lift.domain.attempts
import dev.justinsmith.lift.domain.newRecords
import dev.justinsmith.lift.domain.strengthTests
import dev.justinsmith.lift.domain.tonnage
import kotlin.math.roundToInt

/** The weeks Trends draws, this one last: about a quarter, enough to see a block of training come and go. */
const val TREND_WEEKS = 12

/** The most workouts the recovery line goes back over. */
const val RECOVERY_POINTS = 12

/** The most records the timeline lists, newest first. */
const val RECORDS_LISTED = 8

/** The workouts at each end of the recovery line set against each other, to say which way it is going. */
private const val RECOVERY_ENDS = 3

/** How far apart, in beats a minute, the two ends' usual drops are before the line is said to move. */
private const val RECOVERY_CHANGE_BPM = 3

private const val DAYS_PER_WEEK = 7
private const val SECONDS_PER_MINUTE = 60

/**
 * One week of Trends: its Monday, the [minutes] trained (the interval workouts and Lift's sessions, as the Log's
 * time counts them: a workout logged by hand or a program day logged as planned by its estimate, a sport day by
 * nothing), the weight [lifted] in Lift's working sets in the unit the app weighs in, warm-ups left out, and the
 * [heartLoad] of everything a sensor read that week (a recorded class too), 0 with none.
 */
data class TrendWeek(
    val weekStart: Long,
    val minutes: Int,
    val lifted: Double,
    val heartLoad: Int,
    val current: Boolean,
)

/** A workout's recovery on the line: the day it was and how far the heart fell, on average, a minute into its rests. */
data class RecoveryPoint(val epochDay: Long, val drop: Int)

/** Which way the heart's recovery is going: the last few workouts' drops set against the first few's. */
enum class RecoveryDirection { Faster, Steady, Slower }

/** A record set: the day of the session it was set in and the record as it stood after it. */
data class RecordMoment(val epochDay: Long, val sessionUid: String, val record: PersonalRecord)

/**
 * A test's attempts at one [load], the latest attempt's, oldest first: a result is only set against another at the
 * same load, so an attempt at another bell is left off the line.
 */
data class TestTrend(val test: TestId, val load: Load, val results: List<Int>) {
    val first: Int get() = results.first()
    val latest: Int get() = results.last()
}

/** Everything Trends shows, worked out from what is logged, nothing of it stored. */
data class Trends(
    val weeks: List<TrendWeek> = emptyList(),
    /** Oldest first, a minute's drop only: a half minute's is another measure, left out rather than mixed in. */
    val recovery: List<RecoveryPoint> = emptyList(),
    /** Newest first, at most [RECORDS_LISTED]. */
    val records: List<RecordMoment> = emptyList(),
    /** The records set over [weeks]. */
    val recordsInWeeks: Int = 0,
    val tests: List<TestTrend> = emptyList(),
) {
    val hasTime: Boolean get() = weeks.any { it.minutes > 0 }
    val hasLifted: Boolean get() = weeks.any { it.lifted > 0 }
    val hasHeartLoad: Boolean get() = weeks.any { it.heartLoad > 0 }

    /** Nothing logged yet that any card draws. */
    val empty: Boolean
        get() = !hasTime && !hasLifted && !hasHeartLoad && recovery.isEmpty() && records.isEmpty() && tests.isEmpty()

    /** Which way recovery is going; null until there are enough workouts at both ends to say. */
    val recoveryDirection: RecoveryDirection?
        get() {
            if (recovery.size < 2 * RECOVERY_ENDS) return null
            val change = recovery.takeLast(RECOVERY_ENDS).meanDrop() - recovery.take(RECOVERY_ENDS).meanDrop()
            return when {
                change >= RECOVERY_CHANGE_BPM -> RecoveryDirection.Faster
                change <= -RECOVERY_CHANGE_BPM -> RecoveryDirection.Slower
                else -> RecoveryDirection.Steady
            }
        }
}

/**
 * A week's usual of [value] over the weeks before this one, from the first with anything in it: what this week is
 * set against. Null while there is no week before this one to go by.
 */
fun List<TrendWeek>.usual(value: (TrendWeek) -> Double): Double? {
    val past = filterNot { it.current }.dropWhile { value(it) <= 0 }
    return past.takeIf { it.isNotEmpty() }?.map(value)?.average()
}

/**
 * Trends from the interval workouts in [history], Lift's finished [sessions] (newest first, as Lift keeps them) and
 * the recorded [classes]: [TREND_WEEKS] weeks to the one [today] is in, by [dayOf] each moment's day, the weight
 * lifted in [unit], and the heart's load by [maxBpm] and [restingBpm] as the zones are.
 */
@Suppress("detekt.LongParameterList")
fun trends(
    history: List<HistoryEntry>,
    sessions: List<LiftSession>,
    classes: List<SportClass>,
    today: Long,
    dayOf: (Long) -> Long,
    unit: WeightUnit,
    maxBpm: Int,
    restingBpm: Int?,
): Trends {
    val finished = sessions.filter { it.finishedAtEpochMillis != null }
    val current = weekStartOf(today)
    val firstWeek = current - (TREND_WEEKS - 1) * DAYS_PER_WEEK
    fun weekOf(epochMillis: Long): Long = weekStartOf(dayOf(epochMillis))

    val seconds = mutableMapOf<Long, Int>()
    history.forEach { seconds.add(weekOf(it.finishedAtEpochMillis), it.elapsedSeconds) }
    finished.asLiftWorkouts().forEach { seconds.add(weekOf(it.finishedAtEpochMillis), it.elapsedSeconds) }

    val lifted = finished.groupBy { weekOf(it.finishedAtEpochMillis ?: 0) }
        .mapValues { (_, done) -> done.sumOf { session -> session.workingSets.sumOf { it.tonnage(unit) } } }

    val load = mutableMapOf<Long, Int>()
    fun addLoad(epochMillis: Long, samples: List<Int>?) {
        if (samples.isNullOrEmpty()) return
        load.add(weekOf(epochMillis), heartLoad(samples, maxBpm, restingBpm = restingBpm))
    }
    history.forEach { addLoad(it.finishedAtEpochMillis, it.heartRateSamples) }
    finished.forEach { addLoad(it.finishedAtEpochMillis ?: 0, it.heartRateSamples) }
    classes.forEach { recorded -> addLoad(recorded.startedAtEpochMillis, recorded.samples.map { it.bpm }) }

    val weeks = (0 until TREND_WEEKS).map { i ->
        val start = firstWeek + i * DAYS_PER_WEEK
        TrendWeek(
            weekStart = start,
            minutes = (seconds[start] ?: 0) / SECONDS_PER_MINUTE,
            lifted = lifted[start] ?: 0.0,
            heartLoad = load[start] ?: 0,
            current = start == current,
        )
    }

    val records = recordMoments(finished, dayOf)
    return Trends(
        weeks = weeks,
        recovery = recoveryPoints(history, dayOf),
        records = records.asReversed().take(RECORDS_LISTED),
        recordsInWeeks = records.count { weekStartOf(it.epochDay) >= firstWeek },
        tests = testTrends(finished),
    )
}

/**
 * Every record set in [sessions] (newest first, as Lift keeps them), oldest first: each session's records against
 * every session finished before it, as the summary and the Log's Records filter judge them.
 */
fun recordMoments(sessions: List<LiftSession>, dayOf: (Long) -> Long): List<RecordMoment> {
    val prior = mutableListOf<SetLog>()
    val moments = mutableListOf<RecordMoment>()
    for (session in sessions.asReversed()) {
        val finished = session.finishedAtEpochMillis ?: continue
        newRecords(prior, session).forEach { moments += RecordMoment(dayOf(finished), session.uid, it) }
        prior += session.sets
    }
    return moments
}

/** The workouts in [history] whose rests gave a minute's recovery, the latest [RECOVERY_POINTS], oldest first. */
private fun recoveryPoints(history: List<HistoryEntry>, dayOf: (Long) -> Long): List<RecoveryPoint> = history
    .sortedBy { it.finishedAtEpochMillis }
    .mapNotNull { entry ->
        val samples = entry.heartRateSamples ?: return@mapNotNull null
        val rests = entry.rests() ?: return@mapNotNull null
        recovery(samples, rests)
            ?.takeIf { it.seconds == RECOVERY_LONG_SECONDS }
            ?.let { RecoveryPoint(dayOf(entry.finishedAtEpochMillis), it.average) }
    }
    .takeLast(RECOVERY_POINTS)

/** Each test attempted, in the tests' order, with its attempts at the latest one's load. */
private fun testTrends(sessions: List<LiftSession>): List<TestTrend> = strengthTests.mapNotNull { test ->
    val done = attempts(test, sessions)
    val latest = done.lastOrNull() ?: return@mapNotNull null
    TestTrend(test.id, latest.load, done.at(latest.load).map { it.result })
}

/** Adds [amount] to [key]'s count, from 0: common code has no `merge`. */
private fun MutableMap<Long, Int>.add(key: Long, amount: Int) {
    this[key] = (this[key] ?: 0) + amount
}

private fun List<RecoveryPoint>.meanDrop(): Int = map { it.drop }.average().roundToInt()
