package dev.justinsmith.interval.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// Every destination is a NavKey. A new one must also be registered in `navConfiguration`
// (App.kt) and given an `entry<…>` in the entry provider, or it cannot be saved or shown.

/** Configure intervals, pick or save presets, see recent workouts. */
@Serializable
data object Setup : NavKey

/** The running timer for the configuration saved on the setup screen. */
@Serializable
data object Workout : NavKey
