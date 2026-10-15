package dev.justinsmith.interval.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// Every destination is a NavKey. A new one must also be registered in `navConfiguration`
// (App.kt) and given an `entry<…>` in the entry provider, or it cannot be saved or shown.

@Serializable
data object Home : NavKey
