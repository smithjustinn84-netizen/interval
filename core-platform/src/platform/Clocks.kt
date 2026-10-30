package dev.justinsmith.interval.platform

import kotlin.time.TimeSource

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

    /** A time of day given in minutes after midnight, as [time] words one: "7:30 AM". */
    fun timeOfDay(minuteOfDay: Int): String

    /** The moment's minutes after local midnight, the count [timeOfDay] takes. */
    fun minuteOfDay(epochMillis: Long): Int
}

/** The platform's [TimestampFormatter], in the device's time zone and locale. */
expect fun systemTimestampFormatter(): TimestampFormatter

/** Milliseconds since the Unix epoch. */
expect fun epochMillisNow(): Long

/**
 * Time that keeps counting while the device sleeps, for a timer that must be right when the screen comes
 * back on. Kotlin's own monotonic clock stops with the CPU on Android (`System.nanoTime`), so each platform
 * names the clock that doesn't: Android's `elapsedRealtime`, Darwin's `CLOCK_MONOTONIC`; the desktop JVM
 * keeps the monotonic clock, as a desktop asleep is not a workout in progress.
 */
expect val realElapsedTime: TimeSource.WithComparableMarks

/** The wall clock, injected so that tests can fix the time. */
fun interface WallClock {
    fun nowMillis(): Long
}
