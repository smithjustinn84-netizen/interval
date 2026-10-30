package dev.justinsmith.interval.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// Every destination is a NavKey. A new one must also be registered in `navConfiguration`
// (App.kt) and given an `entry<…>` in the entry provider, or it cannot be saved or shown. Lift's keys
// live in `lift-nav` (`dev.justinsmith.bells.nav`); `Tab` in core.

/** Home: the current workout's shape as three steppers, Start, and the way to the presets. */
@Serializable
data object TimerTab : Tab

/** The training log: what was done, what is planned, and the weekly goal. */
@Serializable
data object HistoryTab : Tab

/** Cues, the get-ready countdown, keep screen on, theme. Pushed from the Timer tab's gear. */
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

/** One finished workout, by the id it is stored under: repeat it, plan it again, or delete it. Pushed from History. */
@Serializable
data class WorkoutDetail(val id: Long) : NavKey

/** The weekly goal, the routine by weekday and one-offs on dates. Pushed from History. */
@Serializable
data object Plan : NavKey

/**
 * Built-in and saved presets: start one, put it on the Timer, and save, edit or delete the user's own.
 * Pushed from Timer.
 */
@Serializable
data object Presets : NavKey
