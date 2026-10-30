package dev.justinsmith.interval.platform

import androidx.compose.runtime.Composable

/**
 * Keeps the display awake while this is in the composition. Several callers may hold it at once (a page on
 * its way out and the one replacing it): the display may sleep again only once the last one leaves.
 */
@Composable
expect fun KeepScreenOn()

/**
 * Counts the [KeepScreenOn]s holding one display awake, so one caller leaving doesn't let the screen sleep
 * under another. Main thread only, as composition effects are.
 */
internal class AwakeHolds {
    private var count = 0

    /** Takes a hold; true when it is the first, and the display should now be kept awake. */
    fun acquire(): Boolean = count++ == 0

    /** Gives a hold back; true when it was the last, and the display may sleep again. */
    fun release(): Boolean {
        check(count > 0) { "Released a hold that was never acquired" }
        return --count == 0
    }
}

/**
 * Sets the system bars' icons to suit what is drawn under them: [lightIcons] for dark content (the
 * dark theme, the workout, the intro), whatever the system appearance. Android and iOS; desktop's
 * title bar follows the system.
 */
@Composable
expect fun SystemBarIcons(lightIcons: Boolean)

/**
 * Whether a back swipe previews the page underneath the Material way, shrinking and rounding the
 * page as it follows the finger (Android). Elsewhere navigation keeps its platform default, the
 * native slide on iOS.
 */
expect val materialBackPreview: Boolean

/**
 * Whether pages keep the platform's own push and pop transitions (iOS's native slide). Elsewhere
 * the app's run: Android's default is a slow fade and desktop's is none at all.
 */
expect val platformPageTransitions: Boolean

/**
 * Whether the user asked the system for less motion: Android's "Remove animations" (animator scale
 * 0) or iOS's Reduce Motion. Decorative animation stands still then.
 */
@Composable
expect fun prefersReducedMotion(): Boolean
