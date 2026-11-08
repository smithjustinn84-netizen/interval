package dev.justinsmith.interval.platform

import java.util.Locale
import java.util.TimeZone
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class JavaTimeFormatterTest {
    private val before = Locale.getDefault(Locale.Category.FORMAT)
    private val zone = TimeZone.getDefault()
    private val formatter = JavaTimeFormatter()

    // Sunday 11 October 2026.
    private val sunday = java.time.LocalDate.of(2026, 10, 11).toEpochDay()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(Locale.Category.FORMAT, before)

    @AfterTest
    fun restoreZone() = TimeZone.setDefault(zone)

    @Test
    fun `a date and a month read as Android words them`() {
        Locale.setDefault(Locale.Category.FORMAT, Locale.US)
        assertEquals("Sunday, Oct 11", formatter.dateOfDay(sunday))
        assertEquals("October 2026", formatter.monthTitle(sunday))
    }

    @Test
    fun `a change of language is followed`() {
        Locale.setDefault(Locale.Category.FORMAT, Locale.US)
        formatter.dateOfDay(sunday)
        Locale.setDefault(Locale.Category.FORMAT, Locale.GERMANY)
        assertEquals(true, formatter.dateOfDay(sunday).startsWith("Sonntag"))
    }

    @Test
    fun `midday of a day stays on it where its midnight does not exist`() {
        // Chile springs forward at midnight: Sunday 8 September 2024 has no 00:00.
        TimeZone.setDefault(TimeZone.getTimeZone("America/Santiago"))
        val springForward = java.time.LocalDate.of(2024, 9, 8).toEpochDay()
        val midday = formatter.middayOf(springForward)
        assertEquals(springForward, formatter.dayNumber(midday))
        assertEquals(12 * 60, formatter.minuteOfDay(midday))

        TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))
        assertEquals(sunday, formatter.dayNumber(formatter.middayOf(sunday)))
    }

    @Test
    fun `the time of day takes the locale's clock`() {
        Locale.setDefault(Locale.Category.FORMAT, Locale.GERMANY)
        val evening = java.time.LocalDate.of(2026, 10, 11).atTime(19, 42)
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals("19:42", formatter.time(evening))
    }
}
