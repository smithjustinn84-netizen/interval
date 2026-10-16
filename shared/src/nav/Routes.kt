package dev.justinsmith.interval.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// Every destination is a NavKey. A new one must also be registered in `navConfiguration`
// (App.kt) and given an `entry<…>` in the entry provider, or it cannot be saved or shown.

/** A destination in the navigation bar. The back stack holds at most one, right above [TimerTab]. */
sealed interface Tab : NavKey

/** Home: the current workout's shape, presets to pick from, and Start. */
@Serializable
data object TimerTab : Tab

/** Built-in and saved presets: apply, save, delete. */
@Serializable
data object PresetsTab : Tab

/** Finished workouts, by day. */
@Serializable
data object HistoryTab : Tab

/** Cues, the get-ready countdown, keep screen on, theme. Pushed from the Timer tab's gear. */
@Serializable
data object Settings : NavKey

/** The running timer, full screen, and its summary when it ends. */
@Serializable
data object Workout : NavKey

/** The app's mark, version and maker, and the way to its licences. Pushed from Settings. */
@Serializable
data object About : NavKey

/** The licences of the fonts and libraries the app ships with. Pushed from About. */
@Serializable
data object Licenses : NavKey
