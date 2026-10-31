package dev.justinsmith.interval.model

import dev.justinsmith.interval.timer.Activity

/**
 * A finished kettlebell session from Lift, as History, the week, the streak and the goal count it. [title]
 * is the session's own, in the user's words or the lifts it did. interval-history makes them from Lift's
 * sessions (`asLiftWorkouts`), so this domain needs nothing of Lift's.
 */
data class LiftWorkout(
    val uid: String,
    val finishedAtEpochMillis: Long,
    val elapsedSeconds: Int,
    val title: String,
) {
    /**
     * The estimated calories burned, or null without a body weight. A session's rests are short, so all of
     * it counts as work.
     */
    fun calories(body: BodySettings): Int? = body.calories(elapsedSeconds, 0, Activity.Kettlebell)
}
