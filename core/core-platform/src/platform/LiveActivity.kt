package dev.justinsmith.interval.platform

/**
 * What a Live Activity shows of a timer running off screen (iOS's Lock Screen and Dynamic Island), worded and
 * coloured already: the extension that draws it runs no Kotlin and reads no Compose resources. A suspended app
 * can't change it, so it holds spans of the wall clock that the system counts down by itself: the [interval] now
 * (the workout's phase, or a whole Lift run), and the [run] the interval is part of (the whole workout), which stays
 * right after the interval has ended. Past [interval]'s end the activity is stale, and shows [staleTitle] in place
 * of [title] and its clock.
 */
data class LiveActivityContent(
    val title: String,
    /** Under the title: the round, the set, the move; null for none. */
    val detail: String? = null,
    /** In place of the title once the interval is over and the app hasn't said what came next. */
    val staleTitle: String? = null,
    /** The title's colour as ARGB; null is the app's accent. */
    val colorArgb: Int? = null,
    /** Stopped: the clocks stand at [pausedClock] and [progress] and nothing counts. */
    val paused: Boolean = false,
    /** The interval's time left as it stopped, "m:ss". */
    val pausedClock: String? = null,
    val interval: LiveActivitySpan? = null,
    val run: LiveActivitySpan? = null,
    /** What [run]'s clock is of ("Workout"). */
    val runLabel: String? = null,
    /** How far through the run it is, 0 to 1, as of now: the bar while paused. */
    val progress: Double = 0.0,
    /** The bar under the clocks, end to end, each in its colour. */
    val segments: List<LiveActivitySegment> = emptyList(),
    /**
     * The words of its buttons, Pause (or Resume) and End, which act on the workout in the app's process
     * (`WorkoutIntents.swift`); null for none, as a Lift run and a finished workout have.
     */
    val toggleLabel: String? = null,
    val endLabel: String? = null,
)

/** From [startMillis] to [endMillis] on the wall clock. */
data class LiveActivitySpan(val startMillis: Long, val endMillis: Long)

/** A stretch of the bar, [length] in any unit the others share, in [colorArgb]; null is the title's colour. */
data class LiveActivitySegment(val length: Int, val colorArgb: Int? = null)

/**
 * The platform's Live Activities, one a [key] (`WORKOUT`, `LIFT`): iOS's, which Swift starts, updates and ends, and
 * nothing anywhere else. [show] starts one, which iOS allows only while the app is on screen, or changes the one
 * under [key].
 */
interface LiveActivities {
    fun show(key: String, content: LiveActivityContent)

    fun end(key: String)

    companion object {
        const val WORKOUT = "workout"
        const val LIFT = "lift"
    }
}

/** Shows nothing: Android (whose notifications are its Live Updates), desktop and tests. */
object NoLiveActivities : LiveActivities {
    override fun show(key: String, content: LiveActivityContent) = Unit

    override fun end(key: String) = Unit
}

/**
 * The [LiveActivities] the container hands out, passing each call to [target], which iOS's app sets once the Swift
 * side has given its own ([NoLiveActivities] until then): the container starts before Swift can be reached from it.
 */
class LiveActivityRelay : LiveActivities {
    var target: LiveActivities = NoLiveActivities

    override fun show(key: String, content: LiveActivityContent) = target.show(key, content)

    override fun end(key: String) = target.end(key)
}
