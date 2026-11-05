package dev.justinsmith.interval.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Every destination is a NavKey. A new one must also be registered in `navConfiguration`
// (App.kt) and given an `entry<…>` in the entry provider, or it cannot be saved or shown. Lift's keys
// live in `lift-nav` (`dev.justinsmith.lift.nav`); `Tab` in core.

/**
 * Home, the first tab and the root of the back stack: this week, what is up next with its Start, and the presets
 * that fit a few minutes.
 */
@Serializable
data object TodayTab : Tab

/**
 * The current workout's shape as three steppers, Start, and the way back to the presets. Pushed from Train's
 * Conditioning pillar. It was a tab, `TimerTab`, the name a saved back stack still knows it by.
 */
@Serializable
@SerialName("dev.justinsmith.interval.nav.TimerTab")
data object Timer : NavKey

/**
 * Train's Conditioning pillar: the presets, as a tab's page, and the way to the [Timer]. Train is one
 * destination of the bar in two parts, each a tab of its own picked on its page; the other, Strength, is Lift's
 * `LiftTab`.
 */
@Serializable
data object ConditioningTab : Tab

/** The training log: what was done, what is planned, and the weekly goal. */
@Serializable
data object HistoryTab : Tab

/**
 * The set-once preferences, your data and the way to About. Pushed from every tab's gear and the Timer's; beside
 * the Timer on a wide window when opened from there.
 */
@Serializable
data object Settings : NavKey

/**
 * The running timer, full screen, and its summary when it ends. [id] is the moment it was started, which
 * tells its saved progress from an older workout's when a killed process comes back to it.
 */
@Serializable
data class Workout(val id: Long) : NavKey

/** The app's mark, version and maker, and the way to its licences. Pushed from Settings. */
@Serializable
data object About : NavKey

/** The licences of the fonts and libraries the app ships with. Pushed from About. */
@Serializable
data object Licenses : NavKey

/** The privacy policy. Pushed from About; Android also opens it on its own for Health Connect. */
@Serializable
data object Privacy : NavKey

/** One finished workout, by the id it is stored under: repeat it, plan it again, or delete it. Pushed from History. */
@Serializable
data class WorkoutDetail(val id: Long) : NavKey

/** The weekly goal, the routine by weekday and one-offs on dates. Pushed from History. */
@Serializable
data object Plan : NavKey

/**
 * The presets as a page pushed from the Timer, while the Timer was a tab. They are Train's Conditioning pillar
 * now ([ConditioningTab]) and nothing pushes this; it stays so a back stack saved with it still reads, and
 * `timerUnderTrain` takes it out.
 */
@Serializable
data object Presets : NavKey
