package dev.justinsmith.bells.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** A destination of the navigation bar or rail. */
sealed interface BellsTab : NavKey

@Serializable
data object TodayTab : BellsTab

@Serializable
data object HistoryTab : BellsTab

@Serializable
data object BellsInventoryTab : BellsTab

/** The session in progress, and its summary once it finishes. */
@Serializable
data object Session : NavKey

/** The built-in programs, to follow one. */
@Serializable
data object Programs : NavKey
