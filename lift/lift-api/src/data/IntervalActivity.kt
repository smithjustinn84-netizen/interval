package dev.justinsmith.lift.data

import dev.justinsmith.lift.domain.IntervalSetGroup
import dev.justinsmith.lift.domain.IntervalSnatches
import dev.justinsmith.lift.domain.intervalSets
import kotlinx.coroutines.flow.StateFlow

/**
 * A day a sport was logged on, [epochDay] (see `CalendarDate`): [grappling] for BJJ, whose gripping in the gi
 * counts against the grip as Lift's coaching weighs it.
 */
data class SportDay(val epochDay: Long, val grappling: Boolean)

/**
 * One of the app's interval workouts, as Lift counts it in the week, and its [snatches] as the lifter counted them,
 * null for a workout of none or one not counted.
 */
data class IntervalWorkout(
    val uid: String,
    val finishedAtEpochMillis: Long,
    val elapsedSeconds: Int,
    val snatches: IntervalSnatches? = null,
)

/** The sets of snatches these workouts' counts come to, a group a workout counted: [intervalSets]. */
val List<IntervalWorkout>.snatchSets: List<IntervalSetGroup> get() = intervalSets(mapNotNull { it.snatches })

/**
 * What Lift reads of the rest of the app, which it can't see: the interval workouts, newest first, the days of a
 * sport logged ([sportDays], which count in the week as a workout does, each saying whether it was grappling), the
 * weekly goal, set on the Plan page, null for none, the body weight set in Settings ([bodyWeightKg], null for
 * none), whether the voice is on ([voice], the switch in Settings that speaks the interval side's cues and Lift's
 * coaching alike), and the calorie estimate the app makes for a session from the body weight. The app binds it over
 * its own repository.
 */
interface IntervalActivity {
    val workouts: StateFlow<List<IntervalWorkout>>
    val sportDays: StateFlow<List<SportDay>>
    val weeklyGoal: StateFlow<Int?>
    val bodyWeightKg: StateFlow<Double?>
    val voice: StateFlow<Boolean>

    /** The calories a session of [elapsedSeconds] is estimated to have burned, or null without a body weight. */
    fun sessionCalories(elapsedSeconds: Int): Int?
}
