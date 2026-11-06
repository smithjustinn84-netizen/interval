package dev.justinsmith.interval

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.justinsmith.interval.nav.ConditioningTab
import dev.justinsmith.interval.nav.Presets
import dev.justinsmith.interval.nav.Settings
import dev.justinsmith.interval.nav.Shortcut
import dev.justinsmith.interval.nav.Tab
import dev.justinsmith.interval.nav.Timer
import dev.justinsmith.interval.nav.TodayTab
import dev.justinsmith.interval.nav.Workout
import dev.justinsmith.interval.nav.WorkoutDetail
import dev.justinsmith.interval.nav.push
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.lift.nav.LiftDetail
import dev.justinsmith.lift.nav.LiftTab
import dev.justinsmith.lift.nav.Lifting

// What the app does to its one back stack (`App.kt` holds the stack and every entry): showing a tab, the Timer or
// a finished workout, opening a launcher shortcut, and putting right a stack saved by an older build.

/**
 * Opens [shortcut] over its tab. A workout already running is kept rather than replaced or popped (leaving its
 * page ends it): every shortcut returns to it. A Lift session is not lost by popping it, it resumes: [startLift]
 * starts one before its page opens, as the Lift tab's button does, and nothing with one in progress.
 */
internal fun NavBackStack<NavKey>.open(shortcut: Shortcut, clock: WallClock, startLift: () -> Unit) {
    if (any { it is Workout }) {
        while (lastOrNull() !is Workout) removeLastOrNull()
        return
    }
    when (shortcut) {
        Shortcut.StartWorkout -> {
            showTimer()
            startWorkout(clock)
        }

        Shortcut.LiftSession -> {
            selectTab(LiftTab)
            startLift()
            push(Lifting)
        }
    }
}

/**
 * Starts a workout now, by [clock], unless one is on top already: a double tap on Start must not stack two. It is
 * the Timer's own, or the mobility flow [flow] names, which leaves the Timer as it is.
 */
internal fun NavBackStack<NavKey>.startWorkout(clock: WallClock, flow: String? = null) {
    if (lastOrNull() !is Workout) add(Workout(clock.nowMillis(), flow))
}

/** Settings pairs with the Timer alone: over another tab it is a page of its own, whatever the window. */
internal val NavBackStack<NavKey>.settingsOverTimer: Boolean
    get() = getOrNull(indexOf(Settings) - 1) == Timer

/** A finished workout or session opened from History: on a wide window it sits beside the log. */
internal val NavKey?.opensBesideHistory: Boolean
    get() = this is WorkoutDetail || (this is LiftDetail && fromHistory)

/**
 * Opens [key] beside History's log, in place of whatever was open there, so Back closes it rather than walking
 * through every workout looked at. On a phone nothing is open over History when a row is tapped.
 */
internal fun NavBackStack<NavKey>.openDetail(key: NavKey) {
    if (lastOrNull() == key) return
    while (lastOrNull().opensBesideHistory) removeLastOrNull()
    add(key)
}

/**
 * Shows the Timer where it lives: over Train's Conditioning pillar, in place of whatever was above Today. Already
 * on top, it stays as it is.
 */
internal fun NavBackStack<NavKey>.showTimer() {
    if (lastOrNull() == Timer) return
    selectTab(ConditioningTab)
    add(Timer)
}

/** Leaves the Timer for the page under it, taking Settings with it if that is open beside it. */
internal fun NavBackStack<NavKey>.leaveTimer() {
    val at = lastIndexOf(Timer)
    if (at > 0) while (size > at) removeLastOrNull()
}

/** Shows [tab]: Today is the root, so it pops back to it; any other tab replaces what is above Today. */
internal fun NavBackStack<NavKey>.selectTab(tab: Tab) {
    while (size > 1) removeLastOrNull()
    if (tab != TodayTab) add(tab)
}

/**
 * Puts Today under a stack that has another root: one saved by a build whose first tab was the Timer comes back
 * as it was saved, and without this Back would stop at the Timer with no way to Today but its tab.
 */
internal fun MutableList<NavKey>.rootAtToday() {
    if (firstOrNull() != TodayTab) add(0, TodayTab)
}

/**
 * Puts the Timer where it now lives in a stack saved while it was a tab: over Train's Conditioning pillar, which
 * is what the Presets page pushed from it became. A Timer left under another tab (the root of a build older
 * still, with History selected over it) goes: nothing shows it there and Back would stop on it.
 */
internal fun MutableList<NavKey>.timerUnderTrain() {
    removeAll { it == Presets }
    val at = indexOf(Timer)
    when {
        at < 0 || getOrNull(at - 1) == ConditioningTab -> Unit
        drop(at + 1).any { it is Tab } -> removeAt(at)
        else -> add(at, ConditioningTab)
    }
}
