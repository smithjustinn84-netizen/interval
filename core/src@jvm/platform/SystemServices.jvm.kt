package dev.justinsmith.interval.platform

import androidx.compose.runtime.Composable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Desktop displays follow the OS power settings; nothing to hold here. */
@Composable
actual fun KeepScreenOn() = Unit

/** Desktop windows have no system bars to restyle. */
@Composable
actual fun SystemBarIcons(lightIcons: Boolean) = Unit

/** Desktop has no system-wide reduce-motion setting that Compose can read. */
@Composable
actual fun prefersReducedMotion(): Boolean = false

actual val materialBackPreview: Boolean = false

actual val platformPageTransitions: Boolean = false

actual fun epochMillisNow(): Long = System.currentTimeMillis()

actual fun systemTimestampFormatter(): TimestampFormatter = JavaTimeFormatter()

private class JavaTimeFormatter : TimestampFormatter {
    // Templates rather than a fixed pattern, so each locale orders the parts its way. Desktop locales
    // don't change while the app runs, so these are built once.
    private val dateFormat = DateTimeFormatter.ofLocalizedPattern("MMMEEEEd")
    private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private val monthFormat = DateTimeFormatter.ofLocalizedPattern("yMMMM")

    override fun date(epochMillis: Long): String = dateFormat.format(zoned(epochMillis))

    override fun time(epochMillis: Long): String = timeFormat.format(zoned(epochMillis))

    override fun dayNumber(epochMillis: Long): Long = zoned(epochMillis).toLocalDate().toEpochDay()

    override fun dateOfDay(epochDay: Long): String = dateFormat.format(LocalDate.ofEpochDay(epochDay))

    override fun monthTitle(epochDay: Long): String = monthFormat.format(LocalDate.ofEpochDay(epochDay))

    private fun zoned(epochMillis: Long) = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
}
