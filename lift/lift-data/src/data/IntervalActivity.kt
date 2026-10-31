package dev.justinsmith.lift.data

import kotlinx.coroutines.flow.StateFlow

/** One of the app's interval workouts, as Lift counts it in the week. */
data class IntervalWorkout(val uid: String, val finishedAtEpochMillis: Long, val elapsedSeconds: Int)

/**
 * What Lift reads of the rest of the app, which it can't see: the interval workouts, newest first, the
 * weekly goal, set on the Plan page, null for none, and the calorie estimate the app makes for a session,
 * from the body weight set in Settings. The app binds it over its own repository.
 */
interface IntervalActivity {
    val workouts: StateFlow<List<IntervalWorkout>>
    val weeklyGoal: StateFlow<Int?>

    /** The calories a session of [elapsedSeconds] is estimated to have burned, or null without a body weight. */
    fun sessionCalories(elapsedSeconds: Int): Int?
}
