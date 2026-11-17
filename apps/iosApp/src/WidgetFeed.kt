package dev.justinsmith.interval

import dev.justinsmith.interval.data.WeekStandingSource
import dev.justinsmith.interval.data.snapshot
import dev.justinsmith.interval.data.state
import dev.justinsmith.interval.di.initAppKoin
import dev.justinsmith.interval.platform.ActiveWorkout
import dev.justinsmith.interval.platform.LiveActivities
import dev.justinsmith.interval.platform.LiveActivityContent
import dev.justinsmith.interval.platform.LiveActivityRelay
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.platform.WallClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import platform.Foundation.NSUserDefaults

/** The App Group the app and its widget share (both targets' entitlements). */
private const val APP_GROUP = "group.dev.justinsmith.interval"

/** Where in the group's defaults the week's snapshot lies: `WeekWidget.swift` reads the same key. */
private const val WEEK_KEY = "week"

private var feeding = false

/**
 * Keeps the Home Screen widget's week current, as Android's `IntervalApplication` keeps its widget drawn: each
 * standing [WeekStandingSource] works out is written to the App Group's defaults as a `WeekSnapshot`, then
 * [reload] (the scene delegate's, which asks `WidgetCenter` to redraw: WidgetKit has no Objective-C face for
 * Kotlin to call) runs on the main thread. Starts the container if nothing has yet. Once per process.
 *
 * The Live Activities go the same way: the workout's and Lift's timers' ([LiveActivities]) reach Swift's
 * `LiveActivities.swift` through [showActivity], with the key and the activity's state as JSON (`LiveActivityState`,
 * which `IntervalActivity.swift` decodes), and [endActivity], with the key. ActivityKit is Swift alone too.
 */
fun startWidgetFeed(
    reload: () -> Unit,
    showActivity: (key: String, json: String) -> Unit,
    endActivity: (key: String) -> Unit,
) {
    if (feeding) return
    feeding = true
    val koin = initAppKoin().koin
    koin.get<LiveActivityRelay>().target = object : LiveActivities {
        override fun show(key: String, content: LiveActivityContent) = showActivity(key, content.state().toJson())

        override fun end(key: String) = endActivity(key)
    }
    val clock = koin.get<WallClock>()
    val timestamps = koin.get<TimestampFormatter>()
    val defaults = NSUserDefaults(suiteName = APP_GROUP)
    CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
        koin.get<WeekStandingSource>().standing.filterNotNull().collect { standing ->
            val today = timestamps.dayNumber(clock.nowMillis())
            defaults.setObject(standing.snapshot(today).toJson(), forKey = WEEK_KEY)
            reload()
        }
    }
}

/**
 * The workout's Live Activity's Pause or Resume (`WorkoutIntents.swift`), run in the app's process on the main thread,
 * perhaps just woken from suspension: acts on the workout kept, as Android's notification's button does; nothing when
 * none is.
 */
fun toggleWorkoutPause() {
    initAppKoin().koin.get<ActiveWorkout>().togglePause()
}

/** The Live Activity's End: the workout ends as the notification's End ends it, its summary waiting in the app. */
fun endWorkout() {
    initAppKoin().koin.get<ActiveWorkout>().end()
}
