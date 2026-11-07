package dev.justinsmith.lift.data

import kotlinx.coroutines.flow.StateFlow

/**
 * A day a sport was logged on, [epochDay] (see `CalendarDate`): [grappling] for BJJ, whose gripping in the gi
 * counts against the grip as Lift's coaching weighs it.
 */
data class SportDay(val epochDay: Long, val grappling: Boolean)

/** One of the app's interval workouts, as Lift counts it in the week. */
data class IntervalWorkout(val uid: String, val finishedAtEpochMillis: Long, val elapsedSeconds: Int)

/**
 * What Lift reads of the rest of the app, which it can't see: the interval workouts, newest first, the days of a
 * sport logged ([sportDays], which count in the week as a workout does, each saying whether it was grappling), the
 * weekly goal, set on the Plan page, null for none, the body weight set in Settings ([bodyWeightKg], null for
 * none), and the calorie estimate the app makes for a session from it. The app binds it over its own repository.
 */
interface IntervalActivity {
    val workouts: StateFlow<List<IntervalWorkout>>
    val sportDays: StateFlow<List<SportDay>>
    val weeklyGoal: StateFlow<Int?>
    val bodyWeightKg: StateFlow<Double?>

    /** The calories a session of [elapsedSeconds] is estimated to have burned, or null without a body weight. */
    fun sessionCalories(elapsedSeconds: Int): Int?
}
