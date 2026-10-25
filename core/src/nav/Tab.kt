package dev.justinsmith.interval.nav

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * A destination in the navigation bar. The back stack holds at most one, right above the Timer tab. Here,
 * under every feature, so Lift's tab (`bells`) and the app's own are one kind; not sealed, since a sealed
 * type can't be extended from another module.
 */
interface Tab : NavKey

/** Pushes [key] unless it is on top already, so a double tap can't stack two of a page. */
fun NavBackStack<NavKey>.push(key: NavKey) {
    if (lastOrNull() != key) add(key)
}
