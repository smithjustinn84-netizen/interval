package dev.justinsmith.interval.platform

import dev.justinsmith.interval.timer.MINUTES_PER_HOUR
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale
import kotlin.time.TimeSource

actual fun epochMillisNow(): Long = System.currentTimeMillis()

actual val realElapsedTime: TimeSource.WithComparableMarks = TimeSource.Monotonic

actual fun systemTimestampFormatter(): TimestampFormatter = JavaTimeFormatter()

internal class JavaTimeFormatter : TimestampFormatter {
    // Built again when the locale changes, as Android's are, so the two word a date alike.
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
        val locale = Locale.getDefault(Locale.Category.FORMAT)
        return formats?.takeIf { it.locale == locale } ?: Formats(locale).also { formats = it }
    }

    // Android's skeletons, so each locale orders the parts and picks 12 or 24 hours its way. Java takes a
    // skeleton's letters in a fixed order ("MMMEEEEd" for Android's "EEEEMMMd", "jm" for its "jmm") and, unlike
    // Android, keeps the closest pattern's own field widths: "Sun" where the skeleton asks for "Sunday". So the
    // weekday is widened again, as Android does.
    private class Formats(val locale: Locale) {
        val date: DateTimeFormatter = localized("MMMEEEEd") { it.replace(SHORT_WEEKDAY, "EEEE") }
        val time: DateTimeFormatter = localized("jm")
        val month: DateTimeFormatter = localized("yMMMM")

        private fun localized(skeleton: String, adjust: (String) -> String = { it }): DateTimeFormatter {
            val pattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(skeleton, IsoChronology.INSTANCE, locale)
            return DateTimeFormatter.ofPattern(adjust(pattern), locale)
        }
    }

    private companion object {
        val SHORT_WEEKDAY = Regex("E{1,3}(?!E)")
    }
}
