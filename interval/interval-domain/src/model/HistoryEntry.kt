package dev.justinsmith.interval.model

import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.MILLIS_PER_SECOND
import dev.justinsmith.interval.timer.plannedWorkSeconds

/**
 * A finished workout that ran for [elapsedSeconds], pauses excluded, to the end or [endedEarly]. Of that,
 * [workActiveSeconds] were work; null for one recorded before work time was kept. [id] is the one it is
 * stored under, null until it has been. [effort] and [note] are how it felt, as the user said on its summary;
 * null until they say. [startedAtEpochMillis] is when it began, null for one recorded before that was kept;
 * [averageBpm] and [peakBpm] its heart rate as a watch recorded it in the health store, null until read there.
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
) {
    /** When it began: as recorded, or for an older entry, its time run back from its finish, pauses left out. */
    val startedAt: Long get() = startedAtEpochMillis ?: (finishedAtEpochMillis - elapsedSeconds * MILLIS_PER_SECOND)

    /** How much of [elapsedSeconds] was work. An older entry's is estimated from its plan. */
    val workDoneSeconds: Int
        get() {
            val work = workActiveSeconds ?: config.plannedWorkSeconds(elapsedSeconds, endedEarly)
            return work.coerceAtMost(elapsedSeconds)
        }

    /** The calories it burned by [body], or null without a weight. */
    fun calories(body: BodySettings): Int? =
        body.calories(workDoneSeconds, elapsedSeconds - workDoneSeconds, config.activity)
}
