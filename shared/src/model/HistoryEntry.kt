package dev.justinsmith.interval.model

import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.plannedWorkSeconds

/**
 * A finished workout that ran for [elapsedSeconds], pauses excluded, to the end or [endedEarly]. Of that,
 * [workActiveSeconds] were work; null for one recorded before work time was kept. [id] is the one it is
 * stored under, null until it has been.
 */
data class HistoryEntry(
    val finishedAtEpochMillis: Long,
    val config: IntervalConfig,
    val elapsedSeconds: Int,
    val endedEarly: Boolean,
    val workActiveSeconds: Int? = null,
    val id: Long? = null,
) {
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
