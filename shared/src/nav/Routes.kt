package dev.justinsmith.interval.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// Every destination is a NavKey. A new one must also be registered in `navConfiguration`
// (App.kt) and given an `entry<…>` in the entry provider, or it cannot be saved or shown.

/** A destination in the navigation bar. The back stack holds at most one, right above [TimerTab]. */
sealed interface Tab : NavKey

/** Home: the current workout's shape as three steppers, Start, and the way to the presets. */
@Serializable
data object TimerTab : Tab

/** Kettlebell training: this week, the last session, the program followed, and Start session. */
@Serializable
data object LiftTab : Tab

/** The training log: what was done, what is planned, and the weekly goal. */
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

/**
 * The kettlebell session in progress, full screen, an EMOM's timer included, and its summary. Pushed from
 * Lift.
 */
@Serializable
data object Lifting : NavKey

/** The built-in programs and the user's own, to follow one. Pushed from Lift. */
@Serializable
data object Programs : NavKey

/** A program the user makes, or changes by its [uid]. Pushed from Programs. */
@Serializable
data class ProgramEditor(val uid: String? = null) : NavKey

/** The bells the user owns and the records set with them. Pushed from Lift. */
@Serializable
data object OwnedBells : NavKey

/** Every exercise there is to log: built-in and the user's own, to search and narrow. Pushed from Lift. */
@Serializable
data object ExerciseLibrary : NavKey

/**
 * An exercise of the user's: a new one, named [name] to start (from a search that found nothing), or theirs
 * by [uid] to change. Pushed from the library, or from a session's Log sheet.
 */
@Serializable
data class ExerciseEditor(val uid: String? = null, val name: String = "") : NavKey

/** One finished kettlebell session, by its uid: its sets and records, and Delete. Pushed from History. */
@Serializable
data class LiftDetail(val uid: String) : NavKey
