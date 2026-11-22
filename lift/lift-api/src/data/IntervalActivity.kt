package dev.justinsmith.lift.data

import dev.justinsmith.interval.timer.WeightUnit
import dev.justinsmith.lift.domain.HeartLoad
import dev.justinsmith.lift.domain.IntervalSetGroup
import dev.justinsmith.lift.domain.IntervalSnatches
import dev.justinsmith.lift.domain.PerceivedEffort
import dev.justinsmith.lift.domain.WorkoutEffort
import dev.justinsmith.lift.domain.intervalSets
import kotlinx.coroutines.flow.StateFlow

/**
 * A day a sport was logged on, [epochDay] (see `CalendarDate`): [grappling] for BJJ, whose gripping in the gi
 * counts against the grip as Lift's coaching weighs it.
 */
data class SportDay(
    val epochDay: Long,
    val grappling: Boolean,
    /** For a day whose class a sensor recorded: the seconds its rounds came to, and what it cost the heart. */
    val matSeconds: Int? = null,
    val heartLoad: Int? = null,
)

/** The grappling days a sensor recorded the class on, each with the seconds its rounds came to: [gripLoad]'s. */
val List<SportDay>.matSeconds: Map<Long, Int>
    get() = filter { it.grappling }.mapNotNull { day -> day.matSeconds?.let { day.epochDay to it } }.toMap()

/**
 * What the interval side's training cost the heart, a day each: the [workouts] a sensor read, each on the day
 * [dayOf] its finish falls on, and the [sportDays]' recorded classes. Readiness reads them beside Lift's own.
 */
fun heartLoads(workouts: List<IntervalWorkout>, sportDays: List<SportDay>, dayOf: (Long) -> Long): List<HeartLoad> =
    workouts.mapNotNull { workout -> workout.heartLoad?.let { HeartLoad(dayOf(workout.finishedAtEpochMillis), it) } } +
        sportDays.mapNotNull { day -> day.heartLoad?.let { HeartLoad(day.epochDay, it) } }

/**
 * One of the app's interval workouts, as Lift counts it in the week, its [snatches] as the lifter counted them,
 * null for a workout of none or one not counted, its [effort] as said on its summary, null until said, and its
 * [heartLoad].
 */
data class IntervalWorkout(
    val uid: String,
    val finishedAtEpochMillis: Long,
    val elapsedSeconds: Int,
    val snatches: IntervalSnatches? = null,
    val effort: PerceivedEffort? = null,
    /** What it cost the heart as a sensor read it, minutes in each zone weighed by the zone; null with none read. */
    val heartLoad: Int? = null,
)

/** The sets of snatches these workouts' counts come to, a group a workout counted: [intervalSets]. */
val List<IntervalWorkout>.snatchSets: List<IntervalSetGroup> get() = intervalSets(mapNotNull { it.snatches })

/** The efforts these workouts were said to have felt, which readiness reads beside Lift's own sessions. */
val List<IntervalWorkout>.efforts: List<WorkoutEffort>
    get() = mapNotNull { workout -> workout.effort?.let { WorkoutEffort(workout.finishedAtEpochMillis, it) } }

/**
 * What Lift reads of the rest of the app, which it can't see: the interval workouts, newest first, the days of a
 * sport logged ([sportDays], which count in the week as a workout does, each saying whether it was grappling), the
 * weekly goal, set on the Plan page, null for none, the body weight set in Settings ([bodyWeightKg], null for
 * none), whether the voice is on ([voice], the switch in Settings that speaks the interval side's cues and Lift's
 * coaching alike), the max heart rate set there, which the zones are worked out from ([maxHeartRate], null for
 * none set), whether a change of zone is to be chimed ([heartRateAlerts]: the alerts switch and Sound both on, as
 * the interval workout requires), and the calorie estimate the app makes for a session from the body weight. The
 * app binds it over its own repository.
 */
interface IntervalActivity {
    val workouts: StateFlow<List<IntervalWorkout>>
    val sportDays: StateFlow<List<SportDay>>

    /** Whether the lifter trains on the mat or the wall: BJJ or climbing logged on a day, or planned. */
    val matOrWall: StateFlow<Boolean>
    val weeklyGoal: StateFlow<Int?>
    val bodyWeightKg: StateFlow<Double?>

    /** The weight unit set in Settings (kg or lb): governs barbell plate steps and progression. */
    val weightUnit: StateFlow<WeightUnit>
    val voice: StateFlow<Boolean>
    val maxHeartRate: StateFlow<Int?>

    /** The resting heart rate set there, which with the max gives the reserve the zones are cut from; null for none. */
    val restingHeartRate: StateFlow<Int?>
    val heartRateAlerts: StateFlow<Boolean>

    /** The calories a session of [elapsedSeconds] is estimated to have burned, or null without a body weight. */
    fun sessionCalories(elapsedSeconds: Int): Int?
}
