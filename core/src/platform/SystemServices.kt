package dev.justinsmith.interval.platform

import androidx.compose.runtime.Composable

/** Formats past moments for the workout history, in the device's time zone. */
interface TimestampFormatter {
    /** The moment's date, as a history section heads it: "Sunday, Oct 11". */
    fun date(epochMillis: Long): String

    /** The time of day: "7:42 PM". */
    fun time(epochMillis: Long): String

    /**
     * The moment's local calendar day as an epoch day (days since 1 January 1970), the count every plan,
     * history day and calendar cell uses. Its day of the week is `weekdayOf(dayNumber)`.
     */
    fun dayNumber(epochMillis: Long): Long

    /** An epoch day's date, as [date] words it: "Sunday, Oct 11". */
    fun dateOfDay(epochDay: Long): String

    /** The month and year an epoch day falls in: "October 2026". */
    fun monthTitle(epochDay: Long): String
}

/** What the installed build says about itself, for the About screen. */
interface AppInfo {
    /**
     * The version the store shows, such as "0.1.0", read from the package or bundle; null when the
     * build carries none, as a desktop run from source does.
     */
    val version: String?
}

/** Keeps the display awake while this is in the composition. */
@Composable
expect fun KeepScreenOn()

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

/** Milliseconds since the Unix epoch. */
expect fun epochMillisNow(): Long

/** The wall clock, injected so that tests can fix the time. */
fun interface WallClock {
    fun nowMillis(): Long
}
