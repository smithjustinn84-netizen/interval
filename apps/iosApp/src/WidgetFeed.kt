package dev.justinsmith.interval

import dev.justinsmith.interval.data.WeekStandingSource
import dev.justinsmith.interval.data.snapshot
import dev.justinsmith.interval.di.initAppKoin
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
 */
fun startWidgetFeed(reload: () -> Unit) {
    if (feeding) return
    feeding = true
    val koin = initAppKoin().koin
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
