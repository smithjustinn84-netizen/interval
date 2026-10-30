package dev.justinsmith.interval.platform

import android.os.SystemClock
import android.text.format.DateFormat
import dev.justinsmith.interval.timer.MINUTES_PER_HOUR
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.AbstractLongTimeSource
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

actual fun epochMillisNow(): Long = System.currentTimeMillis()

actual val realElapsedTime: TimeSource.WithComparableMarks get() = ElapsedRealtime

/** `elapsedRealtime`: since boot, deep sleep included, which `uptimeMillis` and `System.nanoTime` leave out. */
private object ElapsedRealtime : AbstractLongTimeSource(DurationUnit.MILLISECONDS) {
    override fun read(): Long = SystemClock.elapsedRealtime()
}

actual fun systemTimestampFormatter(): TimestampFormatter = JavaTimeFormatter()

private class JavaTimeFormatter : TimestampFormatter {
    // Built again when the locale changes: the container outlives the Activity a language change recreates.
    private var formats: Formats? = null

    override fun date(epochMillis: Long): String = formats().date.format(zoned(epochMillis))

    override fun time(epochMillis: Long): String = formats().time.format(zoned(epochMillis))

    override fun dayNumber(epochMillis: Long): Long = zoned(epochMillis).toLocalDate().toEpochDay()

    override fun dateOfDay(epochDay: Long): String = formats().date.format(LocalDate.ofEpochDay(epochDay))

    override fun monthTitle(epochDay: Long): String = formats().month.format(LocalDate.ofEpochDay(epochDay))

    override fun timeOfDay(minuteOfDay: Int): String =
        formats().time.format(LocalTime.of(minuteOfDay / MINUTES_PER_HOUR, minuteOfDay % MINUTES_PER_HOUR))

    override fun minuteOfDay(epochMillis: Long): Int =
        zoned(epochMillis).toLocalTime().let { it.hour * MINUTES_PER_HOUR + it.minute }

    private fun zoned(epochMillis: Long) = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())

    private fun formats(): Formats {
        val locale = Locale.getDefault()
        return formats?.takeIf { it.locale == locale } ?: Formats(locale).also { formats = it }
    }

    // Skeletons rather than fixed patterns, so each locale orders the parts and picks 12 or 24 hours its way.
    private class Formats(val locale: Locale) {
        val date: DateTimeFormatter = localized("EEEEMMMd")
        val time: DateTimeFormatter = localized("jmm")
        val month: DateTimeFormatter = localized("yMMMM")

        private fun localized(skeleton: String): DateTimeFormatter =
            DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
    }
}
