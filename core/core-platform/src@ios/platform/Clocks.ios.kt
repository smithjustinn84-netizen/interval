@file:OptIn(ExperimentalForeignApi::class)

package dev.justinsmith.interval.platform

import dev.justinsmith.interval.timer.MINUTES_PER_HOUR
import dev.justinsmith.interval.timer.dateOfEpochDay
import dev.justinsmith.interval.timer.epochDayOf
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.autoupdatingCurrentLocale
import platform.Foundation.currentLocale
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localTimeZone
import platform.Foundation.localeIdentifier
import platform.Foundation.timeIntervalSince1970
import platform.posix.CLOCK_MONOTONIC
import platform.posix.clock_gettime_nsec_np
import kotlin.time.AbstractLongTimeSource
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

actual fun epochMillisNow(): Long = NSDate().timeIntervalSince1970.seconds.inWholeMilliseconds

actual val realElapsedTime: TimeSource.WithComparableMarks get() = MonotonicThroughSleep

/**
 * Darwin's `CLOCK_MONOTONIC` keeps counting while the device sleeps (its `CLOCK_UPTIME_RAW`, behind
 * `mach_absolute_time`, does not), so a workout that ran on through a locked phone's sleep is right on waking.
 */
private object MonotonicThroughSleep : AbstractLongTimeSource(DurationUnit.NANOSECONDS) {
    override fun read(): Long = clock_gettime_nsec_np(CLOCK_MONOTONIC.toUInt()).toLong()
}

actual fun systemTimestampFormatter(): TimestampFormatter = FoundationTimestampFormatter()

private const val NOON_HOUR = 12L
private const val TIME_ONLY_YEAR = 2000L
private class FoundationTimestampFormatter : TimestampFormatter {
    private var formats: Formats? = null

    override fun date(epochMillis: Long): String = formats().date.stringFromDate(nsDate(epochMillis))

    override fun time(epochMillis: Long): String = formats().time.stringFromDate(nsDate(epochMillis))

    // Gregorian, whichever calendar the user reads dates in, so the day count is the epoch day the rest of
    // the app (and the other platforms) use. Its time zone follows the device's as it changes.
    private val gregorian = requireNotNull(NSCalendar.calendarWithIdentifier(NSCalendarIdentifierGregorian)).apply {
        timeZone = NSTimeZone.localTimeZone
    }

    override fun dayNumber(epochMillis: Long): Long {
        val parts = gregorian.components(
            NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay,
            fromDate = nsDate(epochMillis),
        )
        return epochDayOf(parts.year.toInt(), parts.month.toInt(), parts.day.toInt())
    }

    override fun dateOfDay(epochDay: Long): String = formats().date.stringFromDate(noonOn(epochDay))

    override fun monthTitle(epochDay: Long): String = formats().month.stringFromDate(noonOn(epochDay))

    // Any day will do for the time's words; one with no daylight-saving change, so the minutes are kept.
    override fun timeOfDay(minuteOfDay: Int): String {
        val parts = NSDateComponents().apply {
            year = TIME_ONLY_YEAR
            month = 1
            day = 1
            hour = (minuteOfDay / MINUTES_PER_HOUR).toLong()
            minute = (minuteOfDay % MINUTES_PER_HOUR).toLong()
        }
        return formats().time.stringFromDate(requireNotNull(gregorian.dateFromComponents(parts)))
    }

    override fun minuteOfDay(epochMillis: Long): Int {
        val parts = gregorian.components(NSCalendarUnitHour or NSCalendarUnitMinute, fromDate = nsDate(epochMillis))
        return parts.hour.toInt() * MINUTES_PER_HOUR + parts.minute.toInt()
    }

    // Noon rather than midnight, which a daylight-saving change can skip.
    private fun noonOn(epochDay: Long): NSDate {
        val date = dateOfEpochDay(epochDay)
        val parts = NSDateComponents().apply {
            year = date.year.toLong()
            month = date.month.toLong()
            day = date.day.toLong()
            hour = NOON_HOUR
        }
        return requireNotNull(gregorian.dateFromComponents(parts))
    }

    private fun nsDate(epochMillis: Long): NSDate =
        NSDate.dateWithTimeIntervalSince1970(epochMillis.milliseconds.toDouble(DurationUnit.SECONDS))

    // A template is turned into a format once, for one locale: a region change (12 to 24 hours, say)
    // needs new formatters, not just a new locale on the old ones.
    private fun formats(): Formats {
        val locale = NSLocale.currentLocale.localeIdentifier
        return formats?.takeIf { it.locale == locale } ?: Formats(locale).also { formats = it }
    }

    // Templates rather than fixed formats, so each locale orders the parts and picks 12 or 24 hours its way.
    private class Formats(val locale: String) {
        val date = formatter("EEEEMMMd")
        val time = formatter("jmm")
        val month = formatter("yMMMM")

        private fun formatter(template: String) = NSDateFormatter().apply {
            setLocale(NSLocale.autoupdatingCurrentLocale)
            setTimeZone(NSTimeZone.localTimeZone)
            setLocalizedDateFormatFromTemplate(template)
        }
    }
}
