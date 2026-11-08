package dev.justinsmith.interval.model

import dev.justinsmith.interval.timer.Course
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.MILLIS_PER_SECOND
import dev.justinsmith.interval.timer.Phase
import dev.justinsmith.interval.timer.plannedWorkSeconds

/**
 * A finished workout that ran for [elapsedSeconds], pauses excluded, to the end or [endedEarly]. Of that,
 * [workActiveSeconds] were work; null for one recorded before work time was kept. [id] is the one it is
 * stored under, null until it has been. [effort] and [note] are how it felt, as the user said on its summary;
 * null until they say. [startedAtEpochMillis] is when it began, null for one recorded before that was kept;
 * [averageBpm] and [peakBpm] its heart rate as a watch recorded it in the health store, null until read there.
 * [snatches] is about how many snatches a set it held and the bell, null until the lifter counts them.
 *
 * One done away from the app, or without the Timer, is logged after the fact by [loggedByHand]: run in full, its
 * work estimated from its plan and its start run back from the finish it was given.
 */
data class HistoryEntry(
    val finishedAtEpochMillis: Long,
    val config: IntervalConfig,
    val elapsedSeconds: Int,
    val endedEarly: Boolean,
    val workActiveSeconds: Int? = null,
    val id: Long? = null,
    val effort: PerceivedEffort? = null,
    val note: String? = null,
    val startedAtEpochMillis: Long? = null,
    val averageBpm: Int? = null,
    val peakBpm: Int? = null,
    val snatches: SnatchCount? = null,
) {
    /** When it began: as recorded, or for an older entry, its time run back from its finish, pauses left out. */
    val startedAt: Long get() = startedAtEpochMillis ?: (finishedAtEpochMillis - elapsedSeconds * MILLIS_PER_SECOND)

    /**
     * Whether it was logged after the fact rather than timed ([loggedByHand]). A timed run always keeps its work
     * time, and an older entry kept neither its work time nor its start, so a start kept with no work time is the
     * mark; nothing more is stored.
     */
    val loggedByHand: Boolean get() = workActiveSeconds == null && startedAtEpochMillis != null

    /** How much of [elapsedSeconds] was work. An older entry's is estimated from its plan. */
    val workDoneSeconds: Int
        get() {
            val work = workActiveSeconds ?: config.plannedWorkSeconds(elapsedSeconds, endedEarly)
            return work.coerceAtMost(elapsedSeconds)
        }

    /** The calories it burned by [body], or null without a weight. */
    fun calories(body: BodySettings): Int? =
        body.calories(workDoneSeconds, elapsedSeconds - workDoneSeconds, config.activity)

    /**
     * The moment each work interval it got through ended, in order: walked along its [Course] from [startedAt], so
     * one [endedEarly] holds only the work done within [elapsedSeconds]. A pause moves them later than they were,
     * never apart.
     */
    fun workEnds(): List<Long> {
        var at = 0L
        return Course.of(config).mapNotNull { interval ->
            at += interval.seconds
            (startedAt + at * MILLIS_PER_SECOND).takeIf {
                interval.phase == Phase.Work &&
                    (!endedEarly || at <= elapsedSeconds)
            }
        }
    }

    companion object {
        /**
         * A workout of [config] done away from the app, or without the Timer, logged after the fact as finished at
         * [finishedAtEpochMillis]: run in full, so its time is the config's whole course, its work estimated from its
         * plan (no [workActiveSeconds]) and its start that long before. Nothing of it is timed, so the health store
         * is never given one.
         */
        fun loggedByHand(config: IntervalConfig, finishedAtEpochMillis: Long): HistoryEntry {
            val elapsed = config.totalSeconds
            return HistoryEntry(
                finishedAtEpochMillis = finishedAtEpochMillis,
                config = config,
                elapsedSeconds = elapsed,
                endedEarly = false,
                startedAtEpochMillis = finishedAtEpochMillis - elapsed * MILLIS_PER_SECOND,
            )
        }
    }
}
